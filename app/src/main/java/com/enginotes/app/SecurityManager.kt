package com.enginotes.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Core security layer for optional app-lock + at-rest note encryption.
 *
 * ARCHITECTURE (why it's built this way, not simpler):
 * There is exactly ONE real encryption key that ever touches note files — the DEK (data
 * encryption key), a random 256-bit AES key generated once when security is first turned on.
 * That same DEK is stored twice, each time "wrapped" (encrypted) under a different unlock
 * method:
 *   - "dek_wrapped_biometric" — encrypted under a key that lives inside Android's own hardware
 *     Keystore, gated so it can only be used right after a successful biometric check. The app
 *     itself never sees this key's raw bytes, ever — only the OS/hardware can use it, and only
 *     after real biometric auth. This is the same trust model iOS's Secure Enclave uses.
 *   - "dek_wrapped_pin" — encrypted under a key derived from the user's 6-digit PIN via
 *     PBKDF2-HMAC-SHA256 (210,000 iterations, per current OWASP guidance) with a random salt.
 *
 * Either successful unlock path recovers the IDENTICAL DEK. This is deliberate: the PIN is
 * never used to encrypt notes directly (a 6-digit PIN is only 1,000,000 possible values —
 * trivial to brute-force offline if it were the actual file key), and biometric-only would lock
 * the user out entirely if biometrics ever stop working. Two independent unlock paths to one
 * strong random key is the standard shape this kind of system takes (the same idea BitLocker/
 * FileVault use for "multiple ways to unlock one real key").
 *
 * "Permanent delete" works by destroying every wrapped copy of the DEK (see
 * permanentlyDeleteAllSecurityData below) rather than by trying to physically scrub flash
 * storage — see the comment there for why that's the technique that actually works reliably on
 * modern phone storage, unlike repeated-overwrite techniques.
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "enginotes_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "enginotes_security"
        private const val PBKDF2_ITERATIONS = 210_000
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val AES_KEY_BYTES = 32 // 256-bit DEK
        const val MAX_BIOMETRIC_ATTEMPTS = 5
        const val MAX_PIN_ATTEMPTS = 5
        const val LOCKOUT_MS = 60_000L

        // Shared across every SecurityManager instance in this process — each Activity creates
        // its own instance (SecurityManager(this)), but "unlocked this session" needs to be one
        // single truth for the whole app, not private per-instance state that would silently
        // reset every time a new Activity is created. Cleared automatically on process death,
        // which is the correct behavior — a killed app should require re-unlocking.
        @Volatile private var inMemoryDek: ByteArray? = null
    }

    private val prefs: SharedPreferences get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─────────────────────────────── State queries ───────────────────────────────

    fun isSecurityEnabled(): Boolean = prefs.getBoolean("enabled", false)
    fun isBiometricDisabled(): Boolean = prefs.getBoolean("biometric_disabled", false)
    fun isPermanentlyLocked(): Boolean = prefs.getBoolean("permanently_locked", false)
    fun isUnlockedThisSession(): Boolean = inMemoryDek != null
    fun biometricAttemptsRemaining(): Int = (MAX_BIOMETRIC_ATTEMPTS - prefs.getInt("biometric_fail_count", 0)).coerceAtLeast(0)
    fun pinAttemptsRemaining(): Int = (MAX_PIN_ATTEMPTS - prefs.getInt("pin_fail_count", 0)).coerceAtLeast(0)
    fun lockoutRemainingMs(): Long {
        val until = prefs.getLong("lockout_until", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }
    fun isTimedOut(): Boolean = lockoutRemainingMs() > 0L

    // ─────────────────────────────── Setup (turning security on) ───────────────────────────────

    /** Called once, when the user sets their PIN and turns security on for the first time.
     *  The PIN path is set up first and works entirely on its own — biometric is attempted
     *  afterward as a bonus, and if this device has no biometric hardware or nothing enrolled
     *  at the PHONE's own Settings level (a prerequisite no app can set up on your behalf),
     *  that failure is caught separately and just leaves biometric off, rather than failing
     *  the whole setup. Returns (success, biometricEnabled) so the caller can tell the user
     *  specifically why biometric wasn't offered, rather than silently going PIN-only. */
    fun setupSecurity(pin: String): Pair<Boolean, Boolean> {
        return try {
            val dek = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP)).apply()
            val pinKey = deriveKeyFromPin(pin, salt)
            encryptAndStore(dek, pinKey, "dek_wrapped_pin")

            var biometricAvailable = false
            try {
                val keystoreKey = getOrCreateKeystoreKey()
                encryptAndStore(dek, keystoreKey, "dek_wrapped_biometric")
                biometricAvailable = true
            } catch (e: Exception) {
                // No biometric hardware, nothing enrolled at the phone's own Settings level, or
                // some other device limitation — not a failure of security setup overall, just
                // means biometric starts off.
            }

            prefs.edit()
                .putBoolean("enabled", true)
                .putBoolean("biometric_disabled", !biometricAvailable)
                .putBoolean("permanently_locked", false)
                .putBoolean("has_timed_out_once", false)
                .putInt("biometric_fail_count", 0)
                .putInt("pin_fail_count", 0)
                .putLong("lockout_until", 0L)
                .apply()
            inMemoryDek = dek
            Pair(true, biometricAvailable)
        } catch (e: Exception) { Pair(false, false) }
    }

    /** Turning security off entirely (user's own choice, while already unlocked) — decrypts
     *  notes back to plaintext on disk first (via caller), then wipes all key material. */
    fun disableSecurity() {
        prefs.edit().clear().apply()
        inMemoryDek = null
    }

    // ─────────────────────────────── Biometric unlock path ───────────────────────────────

    /** Returns a Cipher, ready to hand to BiometricPrompt's CryptoObject. The OS/hardware
     *  refuses to let this Cipher actually decrypt anything until real biometric auth just
     *  succeeded — that enforcement happens below the app, not something this code can fake. */
    fun getBiometricCipherForUnlock(): Cipher? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE); ks.load(null)
            val key = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey ?: return null
            val ivB64 = prefs.getString("dek_wrapped_biometric_iv", null) ?: return null
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher
        } catch (e: Exception) { null }
    }

    /** Called from BiometricPrompt's success callback with the now-authenticated cipher. */
    fun completeBiometricUnlock(authenticatedCipher: Cipher): Boolean {
        return try {
            val blob = Base64.decode(prefs.getString("dek_wrapped_biometric", null) ?: return false, Base64.NO_WRAP)
            val dek = authenticatedCipher.doFinal(blob)
            inMemoryDek = dek
            prefs.edit().putInt("biometric_fail_count", 0).apply()
            true
        } catch (e: Exception) {
            recordBiometricFailure()
            false
        }
    }

    fun recordBiometricFailure() {
        val n = prefs.getInt("biometric_fail_count", 0) + 1
        val editor = prefs.edit().putInt("biometric_fail_count", n)
        if (n >= MAX_BIOMETRIC_ATTEMPTS) editor.putBoolean("biometric_disabled", true)
        editor.apply()
    }

    // ─────────────────────────────── PIN unlock path ───────────────────────────────

    /** Returns true (and unlocks) if the PIN is correct. GCM's built-in authentication tag is
     *  what actually verifies correctness here — a wrong PIN derives a wrong key, which fails
     *  to authenticate the ciphertext and throws, rather than needing a separately stored PIN
     *  hash to compare against. */
    fun attemptPinUnlock(pin: String): Boolean {
        if (isTimedOut() || isPermanentlyLocked()) return false
        return try {
            val saltB64 = prefs.getString("pin_salt", null) ?: return false
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val pinKey = deriveKeyFromPin(pin, salt)
            val ivB64 = prefs.getString("dek_wrapped_pin_iv", null) ?: return false
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val blob = Base64.decode(prefs.getString("dek_wrapped_pin", null) ?: return false, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, pinKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val dek = cipher.doFinal(blob)
            inMemoryDek = dek
            prefs.edit().putInt("pin_fail_count", 0).putLong("lockout_until", 0L).apply()
            true
        } catch (e: Exception) {
            recordPinFailure()
            false
        }
    }

    /** First five-strike escalation happens here: the first lockout is a 1-minute timeout, with
     *  a genuine second chance afterward. Only failing that SECOND round of five permanently
     *  locks the app — tracked via a dedicated "already timed out once" flag, independent of the
     *  current lockout_until value (which needs to go back to 0 once the timeout passes, or
     *  isTimedOut() would never report "not locked out" again). */
    private fun recordPinFailure() {
        val n = prefs.getInt("pin_fail_count", 0) + 1
        val editor = prefs.edit().putInt("pin_fail_count", n)
        if (n >= MAX_PIN_ATTEMPTS) {
            if (prefs.getBoolean("has_timed_out_once", false)) {
                editor.putBoolean("permanently_locked", true)
            } else {
                editor.putBoolean("has_timed_out_once", true)
                editor.putLong("lockout_until", System.currentTimeMillis() + LOCKOUT_MS)
            }
        }
        editor.apply()
    }

    /** Call once the lockout timer has actually elapsed, to reset the counter for the second
     *  round of attempts. Resets lockout_until back to 0 as part of this — a one-time
     *  transition, not something that should keep re-firing on every subsequent render (that
     *  was the actual bug: it previously reset the fail counter on every render forever, since
     *  lockout_until was never cleared back to 0 after the timeout passed). has_timed_out_once
     *  deliberately does NOT get reset here — that's what makes the next five failures
     *  permanent instead of triggering a second timeout. */
    fun clearExpiredLockout() {
        if (prefs.getLong("lockout_until", 0L) > 0L && !isTimedOut()) {
            prefs.edit().putInt("pin_fail_count", 0).putLong("lockout_until", 0L).apply()
        }
    }

    /** Reads a note file, transparently handling both encrypted and legacy-plaintext files —
     *  if there's no magic header, it's a plaintext file from before encryption was ever turned
     *  on (or security is simply off), and this returns its content directly. This is what keeps
     *  every existing note readable with no manual migration step required. */
    fun readNoteFile(file: File): String {
        val bytes = file.readBytes()
        if (!hasEncryptionHeader(bytes)) return String(bytes, Charsets.UTF_8)
        val decrypted = decryptNoteBytes(bytes.copyOfRange(4, bytes.size))
            ?: throw IllegalStateException("This note is encrypted and the app isn't unlocked")
        return String(decrypted, Charsets.UTF_8)
    }

    /** Writes a note file — encrypted (with the magic header) if security is currently enabled
     *  AND unlocked this session, otherwise plain text exactly as before. This is what makes
     *  turning security on start protecting new writes immediately, without needing to touch
     *  every existing plaintext note first — they get encrypted the next time they're saved. */
    fun writeNoteFile(file: File, content: String) {
        if (isSecurityEnabled() && isUnlockedThisSession()) {
            val enc = encryptNoteBytes(content.toByteArray(Charsets.UTF_8))
            if (enc != null) { file.writeBytes(byteArrayOf(0x45, 0x4E, 0x47, 0x01) + enc); return }
        }
        file.writeText(content)
    }

    // ─────────────────────────────── Note file encryption (uses the in-memory DEK) ───────────────────────────────

    /** Encrypts arbitrary bytes (a serialized note, an asset, etc.) with the current session's
     *  DEK. Format: [12-byte IV][ciphertext + 16-byte GCM tag] — self-contained, no separate IV
     *  storage needed per file. Returns null if not currently unlocked. */
    fun encryptNoteBytes(plaintext: ByteArray): ByteArray? {
        val dek = inMemoryDek ?: return null
        return try {
            val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            iv + cipher.doFinal(plaintext)
        } catch (e: Exception) { null }
    }

    fun decryptNoteBytes(ciphertext: ByteArray): ByteArray? {
        val dek = inMemoryDek ?: return null
        if (ciphertext.size < GCM_IV_BYTES) return null
        return try {
            val iv = ciphertext.copyOfRange(0, GCM_IV_BYTES)
            val body = ciphertext.copyOfRange(GCM_IV_BYTES, ciphertext.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(body)
        } catch (e: Exception) { null }
    }

    /** A quick, cheap way for a file browser (BooksActivity's own list) to tell "is this an
     *  EngiNotes-encrypted file" from "is this garbage/foreign" without attempting a full
     *  decrypt — checks for a fixed magic-byte header written at encryption time. */
    fun hasEncryptionHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x45.toByte() && bytes[1] == 0x4E.toByte() && bytes[2] == 0x47.toByte() && bytes[3] == 0x01.toByte()

    // ─────────────────────────────── Permanent delete (crypto-shred) ───────────────────────────────

    /**
     * The actually-reliable way to make encrypted data permanently unrecoverable on modern flash
     * storage. Repeatedly overwriting a file's logical bytes is NOT reliable on phone storage —
     * the flash controller's wear-leveling can silently relocate data to different physical
     * cells behind the app's back, meaning an app-level overwrite may never touch the physical
     * cells that still hold old data (this is documented, measured behavior on SSD/flash media,
     * not a theoretical concern).
     *
     * What actually works: destroy every copy of the key that could ever decrypt the data.
     * Without ANY wrapped-DEK entry and without the Keystore key or PIN-derived key that could
     * unwrap them, the DEK cannot be reconstructed by any means — at that point it doesn't
     * matter whether encrypted bytes physically still exist somewhere on the flash chip, because
     * there's nothing left, ever, that could turn them back into plaintext. This is why the key
     * destruction below is what actually delivers the "unrecoverable" guarantee — the file
     * overwrite afterward is real defense-in-depth, but it is not where the guarantee comes from.
     */
    fun permanentlyDeleteAllSecurityData(notesRoot: File?) {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE); ks.load(null)
            if (ks.containsAlias(KEYSTORE_ALIAS)) ks.deleteEntry(KEYSTORE_ALIAS)
        } catch (e: Exception) { }
        prefs.edit().clear().apply()
        inMemoryDek = null
        if (notesRoot != null) {
            try {
                notesRoot.walkTopDown().filter { it.isFile }.forEach { f ->
                    try {
                        val len = f.length()
                        if (len > 0) {
                            val rnd = ByteArray(len.toInt()); SecureRandom().nextBytes(rnd)
                            f.writeBytes(rnd)
                        }
                        f.delete()
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) { }
        }
    }

    // ─────────────────────────────── Internal helpers ───────────────────────────────

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE); ks.load(null)
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .build()
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(spec)
        return kg.generateKey()
    }

    private fun deriveKeyFromPin(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BYTES * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = skf.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun encryptAndStore(dek: ByteArray, key: SecretKey, prefKey: String) {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val blob = cipher.doFinal(dek)
        prefs.edit()
            .putString(prefKey, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString("${prefKey}_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }
}
