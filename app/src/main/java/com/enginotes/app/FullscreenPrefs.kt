package com.enginotes.app

import android.app.Activity
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Shared across every Activity in the app (notes list/home screens as well as the note editor
// itself) so "Settings" only needs one toggle, not a separate one per screen — and so it applies
// immediately everywhere rather than only to whichever screen happened to be open when it was
// changed. Uses the exact same status-bar-hiding mechanism as MainActivity's existing manual
// per-note "enter fullscreen" button (WindowInsetsController, no runtime permission needed —
// hiding system bars isn't a permission-gated operation), just driven by a persisted setting and
// re-applied in onResume instead of needing a tap every time.

private const val FULLSCREEN_PREF_KEY = "fullscreen_always"

fun Activity.isAlwaysFullscreenEnabled(): Boolean =
    getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE).getBoolean(FULLSCREEN_PREF_KEY, false)

fun Activity.setAlwaysFullscreenEnabled(enabled: Boolean) {
    getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE).edit().putBoolean(FULLSCREEN_PREF_KEY, enabled).apply()
}

/** Hides (or, if the setting is off, makes sure to show) the real system status bar for this
 * Activity's window, based on the persisted "always fullscreen" setting. Call from onResume (not
 * just onCreate) so it's re-applied whenever a screen becomes visible again — both because
 * returning from the background can reset transient system-bar state, and because it's what
 * makes toggling the setting on one screen take effect immediately on every other screen you
 * navigate to afterward, without needing to force-close and reopen the app. */
fun Activity.applyStatusBarFullscreenPreference() {
    val hide = isAlwaysFullscreenEnabled()
    WindowCompat.setDecorFitsSystemWindows(window, !hide)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (hide) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Same reasoning as the existing manual fullscreen button: without this, a notched/punch-
        // hole phone still reserves the status bar's strip as solid black even once its icons are
        // hidden, instead of letting content extend up under the cutout.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }
}
