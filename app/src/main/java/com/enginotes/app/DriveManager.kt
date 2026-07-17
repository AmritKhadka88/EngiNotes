package com.enginotes.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Handles Google Sign-In and all Google Drive read/write for EngiNotes.
 *
 * Uses DriveScopes.DRIVE_FILE — a "non-sensitive" scope restricted to files this
 * app itself created, rather than full Drive access. That means no separate
 * Google verification review is required, which matters while the OAuth
 * consent screen is still in Testing mode.
 *
 * All notes are kept inside one dedicated "EngiNotes" folder in the user's own
 * Drive, created automatically on first use — visible to them like any other
 * Drive folder, not hidden app data.
 *
 * Every Drive call is a blocking network call, so all of it runs on a
 * background executor here; results are always delivered back via
 * activity.runOnUiThread(...), matching the existing pattern used for the
 * Gemini API calls elsewhere in this app (see aiExecutor in MainActivity.kt).
 */
class DriveManager(private val activity: AppCompatActivity) {

    companion object {
        const val RC_SIGN_IN = 9301
        private const val APP_FOLDER_NAME = "EngiNotes"
    }

    private val executor = Executors.newCachedThreadPool()
    private var driveService: Drive? = null
    private var appFolderId: String? = null

    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(activity, gso)
    }

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    /** Launches the account picker / consent screen. Result arrives via handleSignInResult(). */
    fun signIn() {
        activity.startActivityForResult(signInClient.signInIntent, RC_SIGN_IN)
    }

    fun signOut(onDone: (() -> Unit)? = null) {
        signInClient.signOut().addOnCompleteListener {
            driveService = null
            appFolderId = null
            onDone?.invoke()
        }
    }

    /**
     * Call this from the hosting Activity's onActivityResult(). Returns true if
     * this requestCode belonged to the sign-in flow (so the caller knows
     * whether to also check other request codes).
     */
    fun handleSignInResult(requestCode: Int, data: Intent?, onResult: (success: Boolean, error: String?) -> Unit): Boolean {
        if (requestCode != RC_SIGN_IN) return false
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            buildDriveService(account)
            onResult(true, null)
        } catch (e: ApiException) {
            onResult(false, "Sign-in failed (code ${e.statusCode})")
        }
        return true
    }

    /** Resumes a previous sign-in silently (no UI) if one exists. Call once on app start. */
    fun trySilentSignIn(onResult: (Boolean) -> Unit) {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        if (account != null) {
            buildDriveService(account)
            onResult(true)
        } else {
            onResult(false)
        }
    }

    private fun buildDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(activity, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        driveService = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("EngiNotes")
            .build()
    }

    // ---- Everything below does network I/O on a background thread ----

    private fun ensureAppFolder(onResult: (folderId: String?, error: String?) -> Unit) {
        val service = driveService ?: return onResult(null, "Not signed in")
        val cached = appFolderId
        if (cached != null) return onResult(cached, null)
        executor.execute {
            try {
                val existing = service.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and name='$APP_FOLDER_NAME' and trashed=false")
                    .setSpaces("drive")
                    .execute()
                val folderId = existing.files.firstOrNull()?.id ?: run {
                    val folderMeta = DriveFile().apply {
                        name = APP_FOLDER_NAME
                        mimeType = "application/vnd.google-apps.folder"
                    }
                    service.files().create(folderMeta).setFields("id").execute().id
                }
                appFolderId = folderId
                activity.runOnUiThread { onResult(folderId, null) }
            } catch (e: Exception) {
                activity.runOnUiThread { onResult(null, e.message ?: "Couldn't reach Drive") }
            }
        }
    }

    /** Creates or updates [driveFileName] inside the EngiNotes Drive folder with the contents of [localFile]. */
    fun uploadFile(localFile: File, driveFileName: String, mimeType: String = "text/plain", onResult: (Boolean, String?) -> Unit) {
        val service = driveService ?: return onResult(false, "Not signed in")
        ensureAppFolder { folderId, err ->
            if (folderId == null) return@ensureAppFolder onResult(false, err)
            executor.execute {
                try {
                    val existing = service.files().list()
                        .setQ("name='$driveFileName' and '$folderId' in parents and trashed=false")
                        .setSpaces("drive")
                        .execute()
                    val content = FileContent(mimeType, localFile)
                    if (existing.files.isNotEmpty()) {
                        service.files().update(existing.files[0].id, null, content).execute()
                    } else {
                        val meta = DriveFile().apply {
                            name = driveFileName
                            parents = listOf(folderId)
                        }
                        service.files().create(meta, content).execute()
                    }
                    activity.runOnUiThread { onResult(true, null) }
                } catch (e: Exception) {
                    activity.runOnUiThread { onResult(false, e.message ?: "Upload failed") }
                }
            }
        }
    }

    /** Downloads [driveFileName] from the EngiNotes Drive folder into [destFile]. */
    fun downloadFile(driveFileName: String, destFile: File, onResult: (Boolean, String?) -> Unit) {
        val service = driveService ?: return onResult(false, "Not signed in")
        ensureAppFolder { folderId, err ->
            if (folderId == null) return@ensureAppFolder onResult(false, err)
            executor.execute {
                try {
                    val existing = service.files().list()
                        .setQ("name='$driveFileName' and '$folderId' in parents and trashed=false")
                        .setSpaces("drive")
                        .execute()
                    val fileId = existing.files.firstOrNull()?.id
                    if (fileId == null) {
                        activity.runOnUiThread { onResult(false, "Not found on Drive") }
                        return@execute
                    }
                    FileOutputStream(destFile).use { out ->
                        service.files().get(fileId).executeMediaAndDownloadTo(out)
                    }
                    activity.runOnUiThread { onResult(true, null) }
                } catch (e: Exception) {
                    activity.runOnUiThread { onResult(false, e.message ?: "Download failed") }
                }
            }
        }
    }

    /** Lists every file currently stored in the EngiNotes Drive folder. */
    fun listFiles(onResult: (files: List<DriveFile>?, error: String?) -> Unit) {
        val service = driveService ?: return onResult(null, "Not signed in")
        ensureAppFolder { folderId, err ->
            if (folderId == null) return@ensureAppFolder onResult(null, err)
            executor.execute {
                try {
                    val result = service.files().list()
                        .setQ("'$folderId' in parents and trashed=false")
                        .setSpaces("drive")
                        .setFields("files(id,name,modifiedTime)")
                        .execute()
                    activity.runOnUiThread { onResult(result.files, null) }
                } catch (e: Exception) {
                    activity.runOnUiThread { onResult(null, e.message ?: "Couldn't list files") }
                }
            }
        }
    }
}
