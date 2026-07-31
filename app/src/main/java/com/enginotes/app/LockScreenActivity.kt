package com.enginotes.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.util.concurrent.Executor

class LockScreenActivity : FragmentActivity() {

    private lateinit var security: SecurityManager
    private lateinit var root: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var dotsRow: LinearLayout
    private var enteredPin = StringBuilder()
    private var lockoutTimer: CountDownTimer? = null

    // A plain background-thread executor is fine here — BiometricPrompt's own callback always
    // marshals back to the main thread before invoking onAuthenticationSucceeded/Error, so this
    // executor's only job is receiving that initial callback, not doing any UI work itself.
    // by lazy defers this until first actual use (inside tryBiometric()) — calling this as a
    // Context here works fine by then, since onCreate() has already run. As a plain property
    // initializer it ran during the Activity's constructor, before Android attaches the Activity
    // to its Context/system services at all — which crashed on every single launch.
    private val bgExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    // registerForActivityResult IS safe as a class-level property initializer, unlike the
    // bgExecutor bug above — it only registers a callback with the Activity's result registry,
    // it doesn't need a fully-attached Context the way getMainExecutor() does. This is the
    // standard, documented way to use this API.
    private var pendingCameraUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) return@registerForActivityResult
        val uri = pendingCameraUri ?: return@registerForActivityResult
        try {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            handleScannedBitmap(bmp)
        } catch (e: Exception) { Toast.makeText(this, "Couldn't read the captured photo", Toast.LENGTH_SHORT).show() }
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            handleScannedBitmap(bmp)
        } catch (e: Exception) { Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_SHORT).show() }
    }

    private fun handleScannedBitmap(bmp: Bitmap) {
        security.decodeQrFromBitmap(bmp) { code ->
            if (code == null) { Toast.makeText(this, "No QR code found in that image", Toast.LENGTH_SHORT).show(); return@decodeQrFromBitmap }
            submitRecoveryCode(code)
        }
    }

    private fun launchCameraForQr() {
        try {
            val photoFile = File.createTempFile("qr_scan_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) { Toast.makeText(this, "Couldn't open camera: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        security = SecurityManager(this)

        // Nothing to unlock — shouldn't normally reach this screen at all, but bail safely
        // rather than trap the user on a lock screen for a lock that isn't actually active.
        if (!security.isSecurityEnabled()) { proceedUnlocked(); return }

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#1A1A2E")) }
        setContentView(root)
        renderCurrentState()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Re-renders the whole screen based on current SecurityManager state — simpler and far less
    // error-prone than trying to keep several different view hierarchies individually in sync
    // with a state machine that has this many transitions (biometric → PIN → timeout → permanent
    // lock, plus biometric-disabled as an orthogonal flag on top of all of them).
    private fun renderCurrentState() {
        root.removeAllViews()
        lockoutTimer?.cancel()

        if (security.isPermanentlyLocked()) { renderPermanentlyLocked(); return }
        if (security.isInRecoveryMode()) { renderRecoveryMode(); return }
        if (security.isTimedOut()) { renderTimedLockout(); return }
        security.clearExpiredLockout() // no-op if not actually expired; safe to call unconditionally here

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        root.addView(container, lp)

        container.addView(TextView(this).apply {
            text = "\uD83D\uDD12"; textSize = 48f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        container.addView(TextView(this).apply {
            text = "EngiNotes Locked"; textSize = 20f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        statusText = TextView(this).apply {
            textSize = 13f; setTextColor(Color.parseColor("#BBBBBB")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        container.addView(statusText)

        // PIN dot indicator — 6 dots, filled as digits are entered. Built fresh each render
        // rather than tracked as separate persistent views, since it's cheap and avoids any
        // risk of drift between enteredPin's actual length and what's shown.
        dotsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24)) }
        container.addView(dotsRow)
        refreshDots()

        // Numeric keypad — 1-9, then a blank spacer / 0 / backspace on the last row.
        val padRows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","\u232B"))
        for (row in padRows) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (key in row) {
                val btn = TextView(this).apply {
                    text = key; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).also { it.setMargins(dp(8), dp(8), dp(8), dp(8)) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(Color.parseColor("#2A2A45"))
                    }
                    if (key.isNotEmpty()) setOnClickListener { onKeyPress(key) }
                }
                rowLayout.addView(btn)
            }
            container.addView(rowLayout)
        }

        if (!security.isBiometricDisabled()) {
            val bioBtn = TextView(this).apply {
                text = "Use biometric instead"; textSize = 14f; setTextColor(Color.parseColor("#8AB4F8"))
                gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0)
                setOnClickListener { tryBiometric() }
            }
            container.addView(bioBtn)
            // Offer biometric immediately on entry — one less tap for the common case — but
            // only if it hasn't already been disabled by prior failures.
            tryBiometric()
        }
    }

    private fun refreshDots() {
        dotsRow.removeAllViews()
        for (i in 0 until 6) {
            dotsRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).also { it.setMargins(dp(6), 0, dp(6), 0) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (i < enteredPin.length) Color.parseColor("#8AB4F8") else Color.parseColor("#3A3A55"))
                }
            })
        }
        val remaining = security.pinAttemptsRemaining()
        statusText.text = if (remaining < SecurityManager.MAX_PIN_ATTEMPTS) "Enter PIN — $remaining attempt(s) remaining" else "Enter PIN"
    }

    private fun onKeyPress(key: String) {
        if (key == "\u232B") { if (enteredPin.isNotEmpty()) enteredPin.deleteCharAt(enteredPin.length - 1); refreshDots(); return }
        if (enteredPin.length >= 6) return
        enteredPin.append(key)
        refreshDots()
        if (enteredPin.length == 6) submitPin()
    }

    private fun submitPin() {
        val pin = enteredPin.toString()
        enteredPin.clear()
        if (security.attemptPinUnlock(pin)) { proceedUnlocked(); return }
        // Re-render fully rather than just refreshDots() — a failed attempt might have just
        // crossed into a timeout or permanent-lock state, which needs the whole screen to change,
        // not just the dot indicator.
        renderCurrentState()
        if (!security.isTimedOut() && !security.isPermanentlyLocked()) {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryBiometric() {
        val cipher = security.getBiometricCipherForUnlock() ?: return
        val prompt = BiometricPrompt(this, bgExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                if (security.completeBiometricUnlock(c)) proceedUnlocked()
            }
            override fun onAuthenticationFailed() {
                security.recordBiometricFailure()
                if (security.isBiometricDisabled()) renderCurrentState()
                else if (::statusText.isInitialized) statusText.text = "Biometric not recognised — ${security.biometricAttemptsRemaining()} attempt(s) left, or use PIN"
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User-cancelled/timeout errors (e.g. tapping away from the system prompt) are
                // not failed attempts and must not count against the 5-strike limit — only an
                // actual wrong fingerprint/face should count, via onAuthenticationFailed above.
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock EngiNotes")
            .setNegativeButtonText("Use PIN")
            .build()
        try { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) } catch (e: Exception) { }
    }

    private fun renderTimedLockout() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(32), dp(32), dp(32), dp(32)) }
        root.addView(container, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(TextView(this).apply { text = "\u23F3"; textSize = 48f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        container.addView(TextView(this).apply {
            text = "Too many attempts"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        })
        val countdownLbl = TextView(this).apply { textSize = 15f; setTextColor(Color.parseColor("#BBBBBB")); gravity = Gravity.CENTER }
        container.addView(countdownLbl)
        val remainingMs = security.lockoutRemainingMs()
        lockoutTimer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(msLeft: Long) { countdownLbl.text = "Try again in ${(msLeft / 1000) + 1}s" }
            override fun onFinish() { renderCurrentState() }
        }.start()
    }

    private fun renderRecoveryMode() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28)) }
        root.addView(container, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        container.addView(TextView(this).apply { text = "\uD83D\uDD11"; textSize = 44f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12)) })
        container.addView(TextView(this).apply {
            text = "Recovery Needed"; textSize = 19f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        })

        val exhausted = security.isRecoveryExhausted()
        val remaining = security.recoveryAttemptsRemaining()
        container.addView(TextView(this).apply {
            text = if (exhausted)
                "Recovery attempts are used up. There is no way to unlock this app from here anymore — the only option left is deleting all notes permanently."
            else
                "Too many incorrect PIN attempts. Enter your 16-digit recovery code, or scan its QR code, to get back in. $remaining attempt(s) remaining before this option is gone too."
            textSize = 13f; setTextColor(Color.parseColor("#BBBBBB")); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(20))
        })

        if (!exhausted) {
            val codeInput = EditText(this).apply {
                hint = "16-digit recovery code"; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#888888"))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(android.text.InputFilter.LengthFilter(16))
                gravity = Gravity.CENTER
            }
            container.addView(codeInput)
            val unlockBtn = Button(this).apply { text = "Unlock with Code"; setPadding(0, dp(8), 0, 0) }
            container.addView(unlockBtn)
            unlockBtn.setOnClickListener {
                val code = codeInput.text.toString()
                if (code.length != 16) { Toast.makeText(this, "Code must be 16 digits", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                submitRecoveryCode(code)
            }

            val scanBtn = Button(this).apply { text = "Scan QR (Camera)"; setPadding(0, dp(16), 0, 0) }
            container.addView(scanBtn)
            scanBtn.setOnClickListener { launchCameraForQr() }

            val importBtn = Button(this).apply { text = "Import QR (Photo)"; setPadding(0, dp(8), 0, 0) }
            container.addView(importBtn)
            importBtn.setOnClickListener { galleryLauncher.launch("image/*") }
        }

        val deleteBtn = Button(this).apply {
            text = "Delete All Notes Permanently"
            setBackgroundColor(Color.parseColor("#C62828")); setTextColor(Color.WHITE)
            setPadding(0, dp(24), 0, 0)
        }
        container.addView(deleteBtn)
        deleteBtn.setOnClickListener { confirmPermanentDelete() }
    }

    private fun submitRecoveryCode(code: String) {
        if (security.attemptRecoveryUnlock(code)) { proceedUnlocked(); return }
        renderCurrentState() // re-render fully — a failed attempt might have just exhausted recovery entirely
        if (!security.isPermanentlyLocked()) {
            Toast.makeText(this, "Incorrect code — ${security.recoveryAttemptsRemaining()} attempt(s) remaining", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderPermanentlyLocked() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(32), dp(32), dp(32), dp(32)) }
        root.addView(container, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(TextView(this).apply { text = "\uD83D\uDEAB"; textSize = 48f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        container.addView(TextView(this).apply {
            text = "App Permanently Locked"; textSize = 19f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12))
        })
        container.addView(TextView(this).apply {
            text = "Too many incorrect PIN attempts, twice over. There is no way to unlock this app from here — that's by design, the same protection that makes this worth turning on in the first place. The only way forward is deleting all notes permanently."
            textSize = 13f; setTextColor(Color.parseColor("#BBBBBB")); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24))
        })
        val deleteBtn = Button(this).apply {
            text = "Delete All Notes Permanently"
            setBackgroundColor(Color.parseColor("#C62828")); setTextColor(Color.WHITE)
        }
        container.addView(deleteBtn)
        deleteBtn.setOnClickListener { confirmPermanentDelete() }
    }

    private fun confirmPermanentDelete() {
        AlertDialog.Builder(this)
            .setTitle("This cannot be undone")
            .setMessage("Every note in this app will be permanently, irreversibly destroyed — not moved to any trash, not recoverable by any tool, ever. Are you certain?")
            .setPositiveButton("Yes, delete everything") { _, _ ->
                val notesRoot = File(filesDir, "books").takeIf { it.exists() } ?: filesDir
                security.permanentlyDeleteAllSecurityData(notesRoot)
                Toast.makeText(this, "All notes deleted. App is now unlocked and empty.", Toast.LENGTH_LONG).show()
                proceedUnlocked()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun proceedUnlocked() {
        startActivity(Intent(this, BooksActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        // This screen is a gate, not a normal navigable screen — there's nothing "behind" it
        // that should ever become visible by pressing back. Exits the app instead of finishing
        // this Activity, which would otherwise fall through to whatever launched it.
        finishAffinity()
    }
}
