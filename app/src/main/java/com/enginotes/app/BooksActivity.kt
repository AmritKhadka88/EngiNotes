package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Top-level (not nested in a companion object) so it's referenced simply as ThemeSpec from any
// file in this package — a nested class declared inside a companion object isn't accessible via
// OuterClass.ClassName the same simple way properties/functions are, which is what broke the
// build when MainActivity.kt tried to reference BooksActivity.ThemeSpec.
data class ThemeSpec(val toolbar: String, val bg: String, val button: String, val isGradient: Boolean = false)

// Combines the current color theme's button color with the existing Original/Transparent/Glass
// Appearance setting — e.g. Forest theme + Glass appearance = a green-tinted glass button,
// instead of buttons always looking flat-solid regardless of which Appearance mode is active.
// Mirrors MainActivity's themedPillDrawable's per-mode visual treatment (opaque/translucent/
// glass sheen), just parameterized by the theme's own color instead of a fixed hardcoded one.
// Does NOT attempt the bottom toolbar's real behind-content blur for Glass mode — that relies on
// capturing the canvas's own rendered bitmap (updateBlurBackdrops), which doesn't extend cleanly
// to a FAB sitting over a scrolling list or other arbitrary content; this uses the same gradient
// sheen + stroke look instead, which is what actually reads as "glass" visually.
fun themedButtonDrawable(context: Context, appearanceMode: String, baseColorHex: String, oval: Boolean = false): android.graphics.drawable.Drawable {
    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    val base = android.graphics.Color.parseColor(baseColorHex)
    val radius = dp(20).toFloat()
    fun android.graphics.drawable.GradientDrawable.applyShape() { if (oval) shape = android.graphics.drawable.GradientDrawable.OVAL else cornerRadius = radius }
    return when (appearanceMode) {
        "TRANSLUCENT" -> android.graphics.drawable.GradientDrawable().apply {
            setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(base, 140))
            applyShape()
            setStroke(dp(2), androidx.core.graphics.ColorUtils.setAlphaComponent(base, 210))
        }
        "GLASS" -> {
            val lighter = androidx.core.graphics.ColorUtils.blendARGB(base, android.graphics.Color.WHITE, 0.55f)
            val top = androidx.core.graphics.ColorUtils.setAlphaComponent(lighter, 150)
            val bottom = androidx.core.graphics.ColorUtils.setAlphaComponent(base, 70)
            android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)
            ).apply {
                applyShape()
                setStroke(dp(2), androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, 220))
            }
        }
        else -> android.graphics.drawable.GradientDrawable().apply {
            setColor(base)
            applyShape()
            setStroke(dp(1), androidx.core.graphics.ColorUtils.blendARGB(base, android.graphics.Color.BLACK, 0.2f))
        }
    }
}

class BooksActivity : AppCompatActivity() {

    // Six themes total (Classic = the original look, five new ones). Each is just a toolbar
    // color + a background color — kept simple and reliable rather than a deep per-element
    // theming system, so this can't introduce visual inconsistencies elsewhere in the app.
    companion object {
        // Button color chosen to match each theme's own hue family — a lighter/brighter shade
        // of the same color the theme is named after, rather than a complementary contrasting
        // color (Ocean's button is blue, not amber; Forest's is green, not orange; etc.), so the
        // whole theme reads as one consistent color rather than two different ones competing.
        val THEMES = linkedMapOf(
            "Classic" to ThemeSpec("#8D6E63", "#FAF6EF", "#6D4C41"),
            "Ocean" to ThemeSpec("#0277BD", "#E1F5FE", "#29B6F6"),
            "Forest" to ThemeSpec("#2E7D32", "#F1F8E9", "#7CB342"),
            "Sunset" to ThemeSpec("#E64A19", "#FFF3E0", "#FB8C00"),
            "Minimal Mono" to ThemeSpec("#212121", "#FAFAFA", "#000000"),
            // No hard edge between the top bar and the page below it — the top bar itself is a
            // gradient that fades from its own color down into the exact same shade as the page
            // background, so the seam disappears instead of being a visible contrast line.
            "Gradient" to ThemeSpec("#5C6BC0", "#ECEFF1", "#7E57C2", isGradient = true)
        )
        // Only Android's own built-in system animation resources — guaranteed to exist on every
        // API level since these ship with the framework itself, unlike custom XML anim resources
        // which would need to be added as separate resource files.
        val ANIMATIONS = linkedMapOf(
            "None" to Pair(0, 0),
            "Fade" to Pair(android.R.anim.fade_in, android.R.anim.fade_out),
            "Slide" to Pair(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        )
    }

    private lateinit var recentContainer: LinearLayout
    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyView: TextView
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val driveManager by lazy { DriveManager(this) }
    private val security by lazy { SecurityManager(this) }

    // Multi-select state for the Recent Notes list — selectedNotes holds the actual note files;
    // each file's own parentFile gives its book name directly, so no separate book-tracking map
    // is needed for move/delete/etc.
    private var selectionMode = false
    private val selectedNotes = mutableSetOf<File>()
    private lateinit var topBar: LinearLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCountLbl: TextView

    private fun currentThemeSpec(): ThemeSpec {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("app_color_theme", "Classic") ?: "Classic"
        return THEMES[name] ?: THEMES["Classic"]!!
    }
    private fun currentThemeColors(): Pair<String, String> {
        val spec = currentThemeSpec()
        return Pair(spec.toolbar, spec.bg)
    }
    private fun currentThemeButtonColorHex(): String = currentThemeSpec().button
    private fun currentThemeIsGradient(): Boolean = currentThemeSpec().isGradient

    // Applies the currently selected transition animation — call this immediately after any
    // startActivity() that opens MainActivity. Kept as a standalone helper (not wrapped around
    // intent-building) since call sites build their intents differently (opening an existing
    // note vs. creating a new one), so this can be appended after any of them unchanged.
    private fun applyNoteTransition() {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val animName = prefs.getString("app_animation", "None") ?: "None"
        val (enter, exit) = ANIMATIONS[animName] ?: ANIMATIONS["None"]!!
        if (enter != 0 || exit != 0) @Suppress("DEPRECATION") overridePendingTransition(enter, exit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock gate — checked before anything else happens, including reading a single note
        // file. If security is on and this process hasn't been unlocked yet (a fresh launch,
        // or returning after the process was killed), redirect to the lock screen immediately
        // and skip the rest of this Activity's setup entirely — there's nothing safe to show
        // until that resolves.
        if (security.isSecurityEnabled() && !security.isUnlockedThisSession()) {
            startActivity(Intent(this, LockScreenActivity::class.java))
            finish()
            return
        }

        val (themeToolbar, themeBg) = currentThemeColors()

        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.parseColor(themeBg))

        // Top bar
        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12))
        topBar.gravity = Gravity.CENTER_VERTICAL
        if (currentThemeIsGradient()) {
            // Fades from the toolbar color down to the EXACT same color as the page background
            // below it — that's what makes the seam disappear rather than just softening it.
            topBar.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(android.graphics.Color.parseColor(themeToolbar), android.graphics.Color.parseColor(themeBg))
            )
        } else {
            topBar.setBackgroundColor(android.graphics.Color.parseColor(themeToolbar))
        }
        val topLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        topLp.gravity = Gravity.TOP
        root.addView(topBar, topLp)

        // Selection-mode action bar — swapped in for topBar while selectionMode is active.
        // Icons only (no text), matching the request: Move / Copy / Delete / Share, plus a
        // cancel (X) button and a "N selected" count.
        selectionBar = LinearLayout(this)
        selectionBar.orientation = LinearLayout.HORIZONTAL
        selectionBar.setBackgroundColor(androidx.core.graphics.ColorUtils.blendARGB(android.graphics.Color.parseColor(themeToolbar), android.graphics.Color.BLACK, 0.25f))
        selectionBar.setPadding(dp(16), dp(12), dp(16), dp(12))
        selectionBar.gravity = Gravity.CENTER_VERTICAL
        selectionBar.visibility = View.GONE
        root.addView(selectionBar, topLp)

        val cancelSelBtn = Button(this).apply {
            text = "\u2715"; textSize = 18f; setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.WHITE); minWidth = 0; minimumWidth = 0; setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { exitSelectionMode() }
        }
        selectionBar.addView(cancelSelBtn)
        selectionCountLbl = TextView(this).apply {
            textSize = 16f; setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        selectionBar.addView(selectionCountLbl)
        fun selBtn(emoji: String, action: () -> Unit) {
            val b = Button(this); b.text = emoji; b.textSize = 18f
            b.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            b.setTextColor(android.graphics.Color.WHITE)
            b.minWidth = 0; b.minimumWidth = 0
            b.setPadding(dp(10), 0, dp(10), 0)
            b.setOnClickListener { action() }
            selectionBar.addView(b)
        }
        selBtn("\uD83D\uDCE6") { moveSelectedNotes() }   // Move (box)
        selBtn("\uD83D\uDCCB") { copySelectedNotes() }   // Copy (clipboard)
        selBtn("\uD83D\uDDD1") { deleteSelectedNotes() } // Delete (trash)
        selBtn("\uD83D\uDCE4") { shareSelectedNotes() }  // Share (outbox tray)

        val appTitle = TextView(this)
        appTitle.text = "\uD83D\uDCDA EngiNotes  \u25BE"
        appTitle.textSize = 22f
        appTitle.setTextColor(android.graphics.Color.WHITE)
        appTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        appTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        appTitle.setOnClickListener { showAppMenu(it) }
        topBar.addView(appTitle)

        fun topBtn(emoji: String, action: () -> Unit): Button {
            val b = Button(this); b.text = emoji; b.textSize = 18f
            b.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            b.setTextColor(android.graphics.Color.WHITE)
            b.minWidth = 0; b.minimumWidth = 0
            b.setPadding(dp(8), 0, dp(8), 0)
            b.setOnClickListener { action() }
            topBar.addView(b)
            return b
        }

        topBtn("\uD83D\uDD0D") { showSearchDialog() }
        // Icon shown is the mode you'd SWITCH TO (grid icon while in list mode, and vice versa),
        // matching how this kind of toggle usually reads in other apps. Persisted so it survives
        // restarting the app, not just this session.
        lateinit var layoutToggleBtn: Button
        layoutToggleBtn = topBtn(if (layoutMode == 0) "\u25A6" else "\u2261") {
            layoutMode = if (layoutMode == 0) 1 else 0
            layoutToggleBtn.text = if (layoutMode == 0) "\u25A6" else "\u2261"
            refreshRecent()
        }
        topBtn("\uD83D\uDCDA") { showBooksManagerDialog() }
        topBtn("\u2699") { showSettingsDialog() }

        // Scroll content
        val scroll = ScrollView(this)
        val scrollLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        scrollLp.topMargin = dp(56); scrollLp.bottomMargin = dp(80)
        root.addView(scroll, scrollLp)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(16))
        scroll.addView(content)

        // Recent notes section
        val recentHeader = TextView(this)
        recentHeader.text = "\uD83D\uDD50 RECENT NOTES"
        recentHeader.textSize = 11f
        recentHeader.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        recentHeader.typeface = android.graphics.Typeface.DEFAULT_BOLD
        recentHeader.setPadding(dp(4), 0, 0, dp(8))
        content.addView(recentHeader)

        recentContainer = LinearLayout(this)
        recentContainer.orientation = LinearLayout.VERTICAL
        content.addView(recentContainer)

        // Divider
        val div = View(this)
        val divLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        divLp.setMargins(0, dp(16), 0, dp(16)); div.layoutParams = divLp
        div.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
        content.addView(div)

        // Books section header row with "Manage" button
        val booksHeaderRow = LinearLayout(this)
        booksHeaderRow.orientation = LinearLayout.HORIZONTAL
        booksHeaderRow.gravity = Gravity.CENTER_VERTICAL

        val booksHeader = TextView(this)
        booksHeader.text = "\uD83D\uDCDA MY BOOKS"
        booksHeader.textSize = 11f
        booksHeader.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        booksHeader.typeface = android.graphics.Typeface.DEFAULT_BOLD
        booksHeader.setPadding(dp(4), 0, 0, dp(8))
        booksHeader.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        booksHeaderRow.addView(booksHeader)
        content.addView(booksHeaderRow)

        booksContainer = LinearLayout(this)
        booksContainer.orientation = LinearLayout.VERTICAL
        content.addView(booksContainer)

        emptyView = TextView(this)
        emptyView.text = "No books yet.\nTap + to start your first note!"
        emptyView.textSize = 16f
        emptyView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, dp(40), 0, 0)
        content.addView(emptyView)

        // FAB — directly opens new note in General
        val fab = Button(this)
        fab.text = "+"
        fab.textSize = 28f
        fab.setTextColor(android.graphics.Color.WHITE)
        val fabLp = android.widget.FrameLayout.LayoutParams(dp(60), dp(60))
        fabLp.gravity = Gravity.BOTTOM or Gravity.END
        fabLp.bottomMargin = dp(24); fabLp.rightMargin = dp(24)
        fab.setPadding(0, 0, 0, 0)
        fab.elevation = dp(6).toFloat()
        fab.post {
            val appearanceMode = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE).getString("app_theme", "ORIGINAL") ?: "ORIGINAL"
            fab.background = themedButtonDrawable(this, appearanceMode, currentThemeButtonColorHex(), oval = true)
        }
        // Direct: create new note in General book
        fab.setOnClickListener { openNewNoteInGeneral() }
        root.addView(fab, fabLp)

        setContentView(root)
        ensureDefaultBook()
        refresh()
    }

    private fun openNewNoteInGeneral() {
        ensureDefaultBook()
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("book_name", "General")
        // No filename = new note
        startActivity(intent)
        applyNoteTransition()
    }

    private fun ensureDefaultBook() {
        val defaultBook = File(getBooksRoot(), "General")
        if (!defaultBook.exists()) defaultBook.mkdirs()
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarFullscreenPreference()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarFullscreenPreference()
    }

    private fun getBooksRoot(): File {
        val f = File(filesDir, "books"); if (!f.exists()) f.mkdirs(); return f
    }

    private fun getAllPages(): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        getBooksRoot().listFiles()?.filter { it.isDirectory }?.forEach { book ->
            book.listFiles()?.filter { it.extension == "eng" }?.forEach { page ->
                result.add(page to book.name)
            }
        }
        return result.sortedByDescending { it.first.lastModified() }
    }

    // 0 = list (current row style), 1 = grid (thumbnail cards). Persisted so it survives
    // restarting the app.
    private var layoutMode: Int
        get() = getPrefs().getInt("home_layout_mode", 0)
        set(v) { getPrefs().edit().putInt("home_layout_mode", v).apply() }

    private fun thumbnailCacheDir(): File {
        val d = File(cacheDir, "thumbnails"); if (!d.exists()) d.mkdirs(); return d
    }

    // The "v5" here is a cache-format version marker, not part of the note's identity — bump it
    // any time renderThumbnail's actual output changes, so every previously-cached thumbnail is
    // treated as missing and regenerated fresh instead of silently keeping old/incorrect cached
    // PNGs until each note is next edited. (v4 → v5: dropped anchoring to "the first object" —
    // creation order in `actions` isn't the same as visual/spatial "first," which is why some
    // thumbnails showed a garbled slice of content instead of the actual top of the page. Always
    // anchors to page 1 at world origin now, matching a simpler, predictable model.)
    private fun thumbnailFileFor(note: File): File =
        File(thumbnailCacheDir(), "${note.nameWithoutExtension}_${note.lastModified()}_v5.png")

    // Renders a note's first page to a bitmap by loading it into an off-screen DrawingView and
    // drawing that View directly — reuses all of DrawingView's actual rendering logic (strokes,
    // tables, text, hatch fills, everything) instead of a second, simplified renderer that would
    // inevitably drift out of sync with what the note actually looks like when opened for real.
    private fun renderThumbnail(note: File, maxWidthPx: Int, maxHeightPx: Int): Bitmap? {
        return try {
            val dv = DrawingView(this)
            dv.loadFromString(security.readNoteFile(note))
            val convenient = dv.canvasMode == CanvasMode.CONVENIENT
            if (convenient) {
                // Convenient-mode notes (the default canvas mode) don't have a fixed intrinsic page
                // size — DrawingView.onLayout defines their "page" as 82% of whatever View WIDTH and
                // 110% of whatever View HEIGHT they're CURRENTLY shown in — always taller than one
                // screen by design (meant to scroll), so there's no achievable "full page height" to
                // match a thumbnail boundary to at all. Width IS boundable (82% < 100% of the view):
                // lay out once at a fixed reference size, then crop the resulting bitmap to the
                // page's real width — its own actual right edge, Notewise-style — and take a
                // maxHeightPx-tall slice off the top for height, representing "the visible top
                // portion" of a page that's inherently taller than any static preview could show.
                dv.measure(View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.EXACTLY))
                dv.layout(0, 0, maxWidthPx, maxHeightPx)
                dv.resetViewForThumbnail(1f, 0f, 0f)
                val fullBmp = Bitmap.createBitmap(maxWidthPx, maxHeightPx, Bitmap.Config.ARGB_8888)
                dv.draw(android.graphics.Canvas(fullBmp))
                val cropW = dv.pageWidthPx().toInt().coerceIn(1, maxWidthPx)
                val cropped = if (cropW < maxWidthPx) Bitmap.createBitmap(fullBmp, 0, 0, cropW, maxHeightPx) else fullBmp
                applyEdgeFade(cropped)
                return cropped
            }
            // Paper-size notes (Fixed/Paginated/Infinite canvas modes): page dimensions come from
            // paperSize + orientation ALONE — no dependency on view size at all — so pageWidthPx()/
            // pageHeightPx() are readable immediately after loadFromString(), before any layout pass,
            // and there's a real, fixed page boundary to match the bitmap to directly.
            val pageAspect = dv.pageWidthPx().coerceAtLeast(1f) / dv.pageHeightPx().coerceAtLeast(1f)
            var w = maxWidthPx; var h = (w / pageAspect).toInt()
            if (h > maxHeightPx) { h = maxHeightPx; w = (h * pageAspect).toInt() }
            val finalW = w.coerceAtLeast(1); val finalH = h.coerceAtLeast(1)
            dv.measure(View.MeasureSpec.makeMeasureSpec(finalW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(finalH, View.MeasureSpec.EXACTLY))
            dv.layout(0, 0, finalW, finalH)
            val scale = minOf(finalW / dv.pageWidthPx(), finalH / dv.pageHeightPx())
            dv.resetViewForThumbnail(if (scale.isFinite() && scale > 0f) scale else 1f, 0f, 0f)
            val bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
            dv.draw(android.graphics.Canvas(bmp))
            applyEdgeFade(bmp)
            bmp
        } catch (t: Throwable) {
            // Deliberately Throwable, not just Exception — this runs once per note on the
            // notes-list screen. A single corrupted/unreadable file, or an OOM on a very large
            // note, must never take down the whole list; null already means "no thumbnail" to
            // every caller of this function.
            null
        }
    }

    // Soft edge fade on all 4 sides (like Samsung Notes' thumbnails) — draws a thin
    // white-to-transparent gradient inward from each edge directly onto the rendered bitmap,
    // so the page appears to gently fade into the card's white background rather than having a
    // hard cutoff at the crop boundary.
    private fun applyEdgeFade(bmp: Bitmap) {
        val fade = dp(14).coerceAtMost(minOf(bmp.width, bmp.height) / 4)
        if (fade <= 0) return
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint()
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat(); val f = fade.toFloat()
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, f, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, f, paint)
        paint.shader = android.graphics.LinearGradient(0f, h, 0f, h - f, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - f, w, h, paint)
        paint.shader = android.graphics.LinearGradient(0f, 0f, f, 0f, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, f, h, paint)
        paint.shader = android.graphics.LinearGradient(w, 0f, w - f, 0f, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(w - f, 0f, w, h, paint)
    }

    // Cached thumbnails decode off the main thread (pure bitmap decoding — no View involved, so
    // this part is safe to background). An uncached thumbnail renders synchronously on the main
    // thread instead: Android Views aren't safe to construct, measure, or draw from a background
    // thread even when never attached to a window, so that part can't be backgrounded. Only 5
    // "Recent Notes" render at a time, so the one-time cost per note-version should stay small;
    // every render after the first hits the disk cache instead.
    private fun getOrCreateThumbnail(note: File, widthPx: Int, heightPx: Int, onReady: (Bitmap?) -> Unit) {
        val cached = thumbnailFileFor(note)
        if (cached.exists()) {
            Thread {
                val bmp = try { android.graphics.BitmapFactory.decodeFile(cached.absolutePath) } catch (e: Exception) { null }
                runOnUiThread { onReady(bmp) }
            }.start()
            return
        }
        val bmp = try { renderThumbnail(note, widthPx, heightPx) } catch (e: Exception) { null }
        onReady(bmp)
        if (bmp != null) {
            Thread {
                try {
                    // Clear any stale cached file(s) for this note (old mtime baked into the old filename)
                    thumbnailCacheDir().listFiles()?.filter { it.name.startsWith("${note.nameWithoutExtension}_") }?.forEach { it.delete() }
                    FileOutputStream(cached).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                } catch (e: Exception) { Log.w("EngiNotes", "Thumbnail cache write failed for ${note.name}", e) }
            }.start()
        }
    }

    private fun refresh() {
        refreshRecent()
        refreshBooks()
    }

    private fun refreshRecent() {
        recentContainer.removeAllViews()
        val recent = getAllPages().take(5)
        if (recent.isEmpty()) {
            val tv = TextView(this); tv.text = "No recent notes — tap + to create one!"
            tv.textSize = 14f; tv.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            tv.setPadding(dp(4), 0, 0, 0)
            recentContainer.addView(tv); return
        }
        if (layoutMode == 0) {
            for ((file, bookName) in recent) recentContainer.addView(makePageCard(file, bookName))
        } else {
            val cols = 3
            var i = 0
            while (i < recent.size) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until cols) {
                    if (i < recent.size) {
                        val (file, bookName) = recent[i]; row.addView(makeThumbnailCard(file, bookName)); i++
                    } else {
                        // Empty spacer so a short last row doesn't stretch its cards wider than the rest
                        row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
                    }
                }
                recentContainer.addView(row)
            }
        }
    }

    private fun refreshBooks() {
        booksContainer.removeAllViews()
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        emptyView.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE

        for (book in books) {
            val pages = book.listFiles()?.filter { it.extension == "eng" } ?: emptyList()
            val lastModified = pages.maxOfOrNull { it.lastModified() }
            val lastModStr = if (lastModified != null) dateFormat.format(Date(lastModified)) else "Empty"

            val card = android.widget.FrameLayout(this)
            card.setPadding(dp(16), dp(16), dp(16), dp(16))
            card.elevation = dp(2).toFloat()
            val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cardLp.setMargins(0, 0, 0, dp(12)); card.layoutParams = cardLp
            card.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.WHITE); cornerRadius = dp(12).toFloat()
            }

            val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL

            val icon = TextView(this); icon.text = "\uD83D\uDCD6"; icon.textSize = 32f
            icon.setPadding(0, 0, dp(16), 0); row.addView(icon)

            val info = LinearLayout(this); info.orientation = LinearLayout.VERTICAL
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val nameView = TextView(this); nameView.text = book.name; nameView.textSize = 17f
            nameView.setTextColor(android.graphics.Color.BLACK)
            nameView.typeface = android.graphics.Typeface.DEFAULT_BOLD; info.addView(nameView)

            val metaView = TextView(this)
            metaView.text = "${pages.size} page${if (pages.size != 1) "s" else ""} \u00b7 $lastModStr"
            metaView.textSize = 13f; metaView.setTextColor(android.graphics.Color.parseColor("#757575"))
            info.addView(metaView); row.addView(info)

            val arrow = TextView(this); arrow.text = "\u203a"; arrow.textSize = 24f
            arrow.setTextColor(android.graphics.Color.parseColor("#BDBDBD")); row.addView(arrow)

            card.addView(row)
            card.setOnClickListener {
                startActivity(Intent(this, HomeActivity::class.java).putExtra("book_name", book.name))
            }
            card.setOnLongClickListener { showBookOptions(book); true }
            booksContainer.addView(card)
        }
    }

    private fun makePageCard(file: File, bookName: String): View {
        val card = android.widget.FrameLayout(this)
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.elevation = dp(2).toFloat()
        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(0, 0, 0, dp(8)); card.layoutParams = cardLp
        val isSelected = selectionMode && selectedNotes.contains(file)
        card.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(if (isSelected) android.graphics.Color.parseColor("#EFE4DC") else android.graphics.Color.WHITE)
            cornerRadius = dp(10).toFloat()
            if (isSelected) setStroke(dp(2), android.graphics.Color.parseColor("#8D6E63"))
        }

        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL

        val icon = TextView(this); icon.text = "\uD83D\uDCC4"; icon.textSize = 24f
        icon.setPadding(0, 0, dp(12), 0); row.addView(icon)

        val info = LinearLayout(this); info.orientation = LinearLayout.VERTICAL
        info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val nameView = TextView(this); nameView.text = file.nameWithoutExtension; nameView.textSize = 15f
        nameView.setTextColor(android.graphics.Color.BLACK)
        nameView.typeface = android.graphics.Typeface.DEFAULT_BOLD; info.addView(nameView)

        val metaView = TextView(this)
        metaView.text = "$bookName \u00b7 ${dateFormat.format(Date(file.lastModified()))}"
        metaView.textSize = 12f; metaView.setTextColor(android.graphics.Color.parseColor("#9E9E9E")); info.addView(metaView)
        row.addView(info)

        // Checkmark in selection mode (replacing the ">" open-indicator), plain chevron otherwise.
        val openBtn = TextView(this)
        if (selectionMode) {
            openBtn.text = if (isSelected) "\u2705" else "\u2B55"; openBtn.textSize = 18f
        } else {
            openBtn.text = "\u203a"; openBtn.textSize = 24f; openBtn.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
        }
        row.addView(openBtn)

        card.addView(row)
        card.setOnClickListener {
            if (selectionMode) { toggleNoteSelection(file); return@setOnClickListener }
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("book_name", bookName)
                .putExtra("filename", file.nameWithoutExtension))
            applyNoteTransition()
        }
        card.setOnLongClickListener {
            if (selectionMode) toggleNoteSelection(file) else enterSelectionMode(file)
            true
        }
        return card
    }

    private fun makeThumbnailCard(file: File, bookName: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(dp(4), 0, dp(4), dp(14))
            }
        }
        val isSelected = selectionMode && selectedNotes.contains(file)
        val thumbMaxW = dp(200); val thumbMaxH = dp(260)
        // Wrapped in a FrameLayout so a checkmark badge can overlay the thumbnail in selection mode.
        val thumbFrame = android.widget.FrameLayout(this)
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE); cornerRadius = dp(10).toFloat()
                setStroke(if (isSelected) dp(3) else dp(1), if (isSelected) android.graphics.Color.parseColor("#8D6E63") else android.graphics.Color.parseColor("#E0E0E0"))
            }
            // Without this, the rendered thumbnail bitmap — a plain rectangle — poked past the
            // rounded corners of the white background/border underneath it instead of being
            // cropped to match.
            clipToOutline = true
            // Was dp(1) — barely visible. Bumped up for a real, visible card shadow (matching
            // the Notewise reference) rather than something you'd only notice if you looked.
            elevation = dp(4).toFloat()
        }
        thumbFrame.addView(imageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140)))
        if (selectionMode) {
            val badge = TextView(this).apply {
                text = if (isSelected) "\u2705" else "\u2B55"; textSize = 16f
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.WHITE)
                }
                // Must be higher than imageView's elevation (dp(4)) — overlapping siblings in the
                // same FrameLayout composite by elevation, not just add-order, so despite being
                // added AFTER the image, the badge was rendering underneath it and never showing
                // at all, even though this code always ran whenever selectionMode was on.
                elevation = dp(8).toFloat()
            }
            val badgeLp = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT)
            badgeLp.gravity = Gravity.TOP or Gravity.END
            badgeLp.topMargin = dp(6); badgeLp.rightMargin = dp(6)
            thumbFrame.addView(badge, badgeLp)
        }
        card.addView(thumbFrame)
        val nameView = TextView(this).apply {
            text = file.nameWithoutExtension; textSize = 12f
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(android.graphics.Color.parseColor("#2A2A2A"))
            setPadding(dp(2), dp(4), dp(2), 0)
        }
        card.addView(nameView)
        val metaView = TextView(this).apply {
            text = dateFormat.format(Date(file.lastModified()))
            textSize = 10f; setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            setPadding(dp(2), dp(1), dp(2), 0)
        }
        card.addView(metaView)

        getOrCreateThumbnail(file, thumbMaxW, thumbMaxH) { bmp -> if (bmp != null) imageView.setImageBitmap(bmp) }

        card.setOnClickListener {
            if (selectionMode) { toggleNoteSelection(file); return@setOnClickListener }
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("book_name", bookName)
                .putExtra("filename", file.nameWithoutExtension))
            applyNoteTransition()
        }
        card.setOnLongClickListener {
            if (selectionMode) toggleNoteSelection(file) else enterSelectionMode(file)
            true
        }
        return card
    }

    private fun showBooksManagerDialog() {
        AlertDialog.Builder(this).setTitle("Books")
            .setItems(arrayOf("Create New Book", "Rename a Book", "Delete a Book")) { _, i ->
                when (i) {
                    0 -> showCreateBookDialog()
                    1 -> showRenameBookDialog()
                    2 -> showDeleteBookDialog()
                }
            }.show()
    }

    private fun showCreateBookDialog() {
        val input = EditText(this).apply { hint = "Book name" }
        AlertDialog.Builder(this).setTitle("New Book").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    File(getBooksRoot(), name).mkdirs(); refresh()
                    Toast.makeText(this, "Book '$name' created", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showRenameBookDialog() {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory }?.map { it.name } ?: return
        AlertDialog.Builder(this).setTitle("Rename Book")
            .setItems(books.toTypedArray()) { _, i ->
                val input = EditText(this).apply { setText(books[i]) }
                AlertDialog.Builder(this).setTitle("Rename '${books[i]}'").setView(input)
                    .setPositiveButton("Rename") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty() && newName != books[i]) {
                            File(getBooksRoot(), books[i]).renameTo(File(getBooksRoot(), newName)); refresh()
                        }
                    }.setNegativeButton("Cancel", null).show()
            }.show()
    }

    private fun showDeleteBookDialog() {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != "General" }?.map { it.name } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No books to delete (General is protected)", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Delete Book")
            .setItems(books.toTypedArray()) { _, i ->
                AlertDialog.Builder(this).setTitle("Delete '${books[i]}'?")
                    .setMessage("All notes inside will be permanently deleted.")
                    .setPositiveButton("Delete") { _, _ -> File(getBooksRoot(), books[i]).deleteRecursively(); refresh() }
                    .setNegativeButton("Cancel", null).show()
            }.show()
    }

    private fun showBookOptions(book: File) {
        val items = mutableListOf("Open", "New Note in this Book")
        if (book.name != "General") items.add("Rename"); items.add("Delete")
        AlertDialog.Builder(this).setTitle(book.name)
            .setItems(items.toTypedArray()) { _, i ->
                when (items[i]) {
                    "Open" -> startActivity(Intent(this, HomeActivity::class.java).putExtra("book_name", book.name))
                    "New Note in this Book" -> { startActivity(Intent(this, MainActivity::class.java).putExtra("book_name", book.name)); applyNoteTransition() }
                    "Rename" -> {
                        val input = EditText(this).apply { setText(book.name) }
                        AlertDialog.Builder(this).setTitle("Rename").setView(input)
                            .setPositiveButton("Rename") { _, _ -> val n = input.text.toString().trim(); if (n.isNotEmpty()) { book.renameTo(File(getBooksRoot(), n)); refresh() } }
                            .setNegativeButton("Cancel", null).show()
                    }
                    "Delete" -> AlertDialog.Builder(this).setTitle("Delete '${book.name}'?")
                        .setMessage("All notes inside will be permanently deleted.")
                        .setPositiveButton("Delete") { _, _ -> book.deleteRecursively(); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun enterSelectionMode(initial: File) {
        selectionMode = true
        selectedNotes.clear(); selectedNotes.add(initial)
        topBar.visibility = View.GONE
        selectionBar.visibility = View.VISIBLE
        updateSelectionCount()
        refresh()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedNotes.clear()
        topBar.visibility = View.VISIBLE
        selectionBar.visibility = View.GONE
        refresh()
    }

    private fun updateSelectionCount() {
        selectionCountLbl.text = "${selectedNotes.size} selected"
    }

    private fun toggleNoteSelection(file: File) {
        if (!selectedNotes.remove(file)) selectedNotes.add(file)
        if (selectedNotes.isEmpty()) { exitSelectionMode(); return }
        updateSelectionCount()
        refresh()
    }

    private fun moveSelectedNotes() {
        if (selectedNotes.isEmpty()) return
        val currentBooks = selectedNotes.mapNotNull { it.parentFile?.name }.toSet()
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No books available", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Move ${selectedNotes.size} note(s) to Book").setItems(books.toTypedArray()) { _, bi ->
            val destBook = books[bi]
            var moved = 0
            for (file in selectedNotes.toList()) {
                if (file.parentFile?.name == destBook) continue
                val dest = File(File(getBooksRoot(), destBook), file.name)
                try { file.copyTo(dest, overwrite = true); file.delete(); moved++ } catch (e: Exception) { Log.w("EngiNotes", "Failed to move ${file.name} to $destBook", e) }
            }
            Toast.makeText(this, "Moved $moved note(s) to $destBook", Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }.show()
    }

    private fun copySelectedNotes() {
        if (selectedNotes.isEmpty()) return
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No books available", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Copy ${selectedNotes.size} note(s) to Book").setItems(books.toTypedArray()) { _, bi ->
            val destBook = books[bi]
            var copied = 0
            for (file in selectedNotes.toList()) {
                val destDir = File(getBooksRoot(), destBook)
                var dest = File(destDir, file.name)
                // Avoid silently overwriting an existing note of the same name in the
                // destination book — append a numeric suffix instead, same way most file
                // managers handle a copy-into-same-name conflict.
                var n = 1
                while (dest.exists()) { dest = File(destDir, "${file.nameWithoutExtension} (${n})${if (file.extension.isNotEmpty()) "." + file.extension else ""}"); n++ }
                try { file.copyTo(dest); copied++ } catch (e: Exception) { Log.w("EngiNotes", "Failed to copy ${file.name} to $destBook", e) }
            }
            Toast.makeText(this, "Copied $copied note(s) to $destBook", Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }.show()
    }

    private fun deleteSelectedNotes() {
        if (selectedNotes.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Delete ${selectedNotes.size} note(s)?")
            .setMessage("This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                var deleted = 0
                for (file in selectedNotes.toList()) {
                    try { if (file.delete()) deleted++ } catch (e: Exception) { Log.w("EngiNotes", "Failed to delete ${file.name}", e) }
                }
                val msg = if (deleted == selectedNotes.size) "Deleted $deleted note(s)" else "Deleted $deleted of ${selectedNotes.size} note(s) — check storage/permissions"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                exitSelectionMode()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun shareSelectedNotes() {
        if (selectedNotes.isEmpty()) return
        try {
            val uris = ArrayList<android.net.Uri>()
            for (file in selectedNotes) {
                uris.add(androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file))
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/octet-stream"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${selectedNotes.size} note(s)"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPageOptions(file: File, bookName: String) {
        AlertDialog.Builder(this).setTitle(file.nameWithoutExtension)
            .setItems(arrayOf("Open", "Select", "Rename", "Move to Book", "Delete")) { _, i ->
                when (i) {
                    0 -> { startActivity(Intent(this, MainActivity::class.java).putExtra("book_name", bookName).putExtra("filename", file.nameWithoutExtension)); applyNoteTransition() }
                    1 -> enterSelectionMode(file)
                    2 -> {
                        val input = EditText(this).apply { setText(file.nameWithoutExtension) }
                        AlertDialog.Builder(this).setTitle("Rename Note").setView(input)
                            .setPositiveButton("Rename") { _, _ ->
                                val n = input.text.toString().trim()
                                if (n.isNotEmpty()) { file.renameTo(File(file.parentFile, "$n.eng")); refresh() }
                            }.setNegativeButton("Cancel", null).show()
                    }
                    3 -> {
                        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != bookName }?.map { it.name } ?: emptyList()
                        if (books.isEmpty()) { Toast.makeText(this, "No other books", Toast.LENGTH_SHORT).show(); return@setItems }
                        AlertDialog.Builder(this).setTitle("Move to Book").setItems(books.toTypedArray()) { _, bi ->
                            val dest = File(File(getBooksRoot(), books[bi]), file.name)
                            file.copyTo(dest, overwrite = true); file.delete(); refresh()
                            Toast.makeText(this, "Moved to ${books[bi]}", Toast.LENGTH_SHORT).show()
                        }.show()
                    }
                    4 -> AlertDialog.Builder(this).setTitle("Delete '${file.nameWithoutExtension}'?")
                        .setPositiveButton("Delete") { _, _ -> file.delete(); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "Search notes..." }
        AlertDialog.Builder(this).setTitle("\uD83D\uDD0D Search Notes").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim().lowercase()
                if (query.isEmpty()) return@setPositiveButton
                val results = getAllPages().filter { it.first.nameWithoutExtension.lowercase().contains(query) }
                if (results.isEmpty()) { Toast.makeText(this, "No results for '$query'", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val names = results.map { "${it.first.nameWithoutExtension} (${it.second})" }.toTypedArray()
                AlertDialog.Builder(this).setTitle("Results").setItems(names) { _, i ->
                    startActivity(Intent(this, MainActivity::class.java)
                        .putExtra("book_name", results[i].second)
                        .putExtra("filename", results[i].first.nameWithoutExtension))
                    applyNoteTransition()
                }.show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val (_, themeBgSettings) = currentThemeColors()
        val bgColorInt = android.graphics.Color.parseColor(themeBgSettings)
        // Simple luminance check so text stays readable on a dark theme's background — not a
        // full per-element recolor (this dialog has many child views with their own hardcoded
        // colors), just making sure default/unstyled text isn't invisible against a dark
        // background specifically.
        val isDark = android.graphics.Color.red(bgColorInt) * 0.299 + android.graphics.Color.green(bgColorInt) * 0.587 + android.graphics.Color.blue(bgColorInt) * 0.114 < 140
        val defaultTextColor = if (isDark) android.graphics.Color.parseColor("#E8E8E8") else android.graphics.Color.parseColor("#2A2A2A")
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8))
            setBackgroundColor(bgColorInt)
        }

        val paperLabels = arrayOf("Blank", "Lined", "Graph Grid", "Dot Grid", "Engineering", "Coloured")
        val paperValues = arrayOf("BLANK", "LINED", "GRID", "DOTS", "ENGINEERING", "BLANK_COLORED")
        var selPaper = prefs.getString("default_paper", "LINED") ?: "LINED"
        val paperLbl = TextView(this).apply { textSize = 15f; setTextColor(android.graphics.Color.parseColor("#1565C0")); setPadding(0, dp(8), 0, dp(8)) }
        fun updatePaperLbl() { paperLbl.text = "Default paper: ${paperLabels[paperValues.indexOf(selPaper).coerceAtLeast(0)]}" }
        updatePaperLbl(); container.addView(paperLbl)
        val paperBtn = Button(this).apply { text = "Change Paper Style" }
        paperBtn.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Default Paper").setItems(paperLabels) { _, i ->
                selPaper = paperValues[i]; updatePaperLbl()
            }.show()
        }
        container.addView(paperBtn)

        val autosaveCb = CheckBox(this).apply { text = "Autosave every 10 seconds"; isChecked = prefs.getBoolean("autosave", true); setTextColor(defaultTextColor) }
        container.addView(autosaveCb)
        val confirmCb = CheckBox(this).apply { text = "Confirm before exit or clear"; isChecked = prefs.getBoolean("confirm_exit_clear", true); setTextColor(defaultTextColor) }
        container.addView(confirmCb)

        val themeHdr = TextView(this).apply { text = "APP THEME"; textSize = 13f; setTextColor(android.graphics.Color.parseColor("#8A8580")); setPadding(0, dp(16), 0, dp(4)) }
        container.addView(themeHdr)
        var selTheme = prefs.getString("app_color_theme", "Classic") ?: "Classic"
        val themeLbl = TextView(this).apply { textSize = 15f; setTextColor(android.graphics.Color.parseColor("#1565C0")); setPadding(0, dp(4), 0, dp(8)) }
        fun updateThemeLbl() { themeLbl.text = "Theme: $selTheme" }
        updateThemeLbl(); container.addView(themeLbl)
        val themeBtn = Button(this).apply { text = "Change Theme" }
        themeBtn.setOnClickListener {
            val names = THEMES.keys.toTypedArray()
            AlertDialog.Builder(this).setTitle("App Theme").setItems(names) { _, i ->
                selTheme = names[i]; updateThemeLbl()
            }.show()
        }
        container.addView(themeBtn)

        val animHdr = TextView(this).apply { text = "NOTE-OPEN ANIMATION"; textSize = 13f; setTextColor(android.graphics.Color.parseColor("#8A8580")); setPadding(0, dp(16), 0, dp(4)) }
        container.addView(animHdr)
        var selAnim = prefs.getString("app_animation", "None") ?: "None"
        val animLbl = TextView(this).apply { textSize = 15f; setTextColor(android.graphics.Color.parseColor("#1565C0")); setPadding(0, dp(4), 0, dp(8)) }
        fun updateAnimLbl() { animLbl.text = "Animation: $selAnim" }
        updateAnimLbl(); container.addView(animLbl)
        val animBtn = Button(this).apply { text = "Change Animation" }
        animBtn.setOnClickListener {
            val names = ANIMATIONS.keys.toTypedArray()
            AlertDialog.Builder(this).setTitle("Note-Open Animation").setItems(names) { _, i ->
                selAnim = names[i]; updateAnimLbl()
            }.show()
        }
        container.addView(animBtn)

        val secHdr = TextView(this).apply { text = "APP SECURITY"; textSize = 13f; setTextColor(android.graphics.Color.parseColor("#8A8580")); setPadding(0, dp(16), 0, dp(4)) }
        container.addView(secHdr)
        val secLbl = TextView(this).apply { textSize = 15f; setTextColor(android.graphics.Color.parseColor("#1565C0")); setPadding(0, dp(4), 0, dp(8)) }
        fun updateSecLbl() { secLbl.text = if (security.isSecurityEnabled()) "App Lock: ON" else "App Lock: OFF" }
        updateSecLbl(); container.addView(secLbl)
        val secBtn = Button(this)
        fun updateSecBtn() { secBtn.text = if (security.isSecurityEnabled()) "Disable App Lock" else "Enable App Lock" }
        updateSecBtn()
        secBtn.setOnClickListener {
            if (security.isSecurityEnabled()) {
                AlertDialog.Builder(this).setTitle("Disable App Lock?")
                    .setMessage("Notes will no longer require a PIN or biometric to open.")
                    .setPositiveButton("Disable") { _, _ ->
                        security.disableSecurity()
                        Toast.makeText(this, "App Lock disabled", Toast.LENGTH_SHORT).show()
                        updateSecLbl(); updateSecBtn()
                    }.setNegativeButton("Cancel", null).show()
            } else {
                showSecurityWarningDialog { showPinSetupDialog(security) { updateSecLbl(); updateSecBtn() } }
            }
        }
        container.addView(secBtn)

        AlertDialog.Builder(this).setTitle("\u2699 Settings").setView(container)
            .setPositiveButton("Save") { _, _ ->
                val themeChanged = selTheme != (prefs.getString("app_color_theme", "Classic") ?: "Classic")
                prefs.edit().putString("default_paper", selPaper)
                    .putBoolean("autosave", autosaveCb.isChecked)
                    .putBoolean("confirm_exit_clear", confirmCb.isChecked)
                    .putString("app_color_theme", selTheme)
                    .putString("app_animation", selAnim)
                    .apply()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                // Theme colors are only ever applied once, in onCreate() — recreate() is what
                // makes a newly picked theme actually take effect immediately, rather than
                // silently only applying the next time the app happens to restart.
                if (themeChanged) recreate()
            }.setNegativeButton("Cancel", null).show()
            // AlertDialog's own title/chrome area is a separate region from the container we
            // themed above — setView() only colors the body, so without this the title strip
            // stayed the default system white no matter which theme (or how dark) was picked.
            // Setting the WINDOW's background covers that chrome too, so the whole dialog reads
            // as one consistent surface instead of a themed body with a white cap on top.
            .also { dlg -> dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColorInt)) }
    }

    private fun showSecurityWarningDialog(onAcknowledged: () -> Unit) {
        val msg = "Read this before turning on App Lock.\n\n" +
            "• Your notes will be encrypted and protected by biometric unlock and a 6-digit PIN.\n\n" +
            "• 5 wrong biometric attempts disables biometric — PIN only from then on.\n\n" +
            "• 5 wrong PIN attempts locks the app for 1 minute.\n\n" +
            "• 5 more wrong attempts after that PERMANENTLY locks the app. The only way back in is deleting every note, permanently and irreversibly — not recoverable by any tool, including professional data recovery.\n\n" +
            "• If you forget your PIN and lose biometric access, this is also the outcome. There is no password reset, no support recovery, no backdoor. That's what makes this actually secure — but it means forgetting your PIN has the same result as an attacker trying to force their way in.\n\n" +
            "Do not turn this on unless you're comfortable with that trade-off."
        AlertDialog.Builder(this).setTitle("\u26A0 Before You Continue")
            .setMessage(msg)
            .setPositiveButton("I Understand, Continue") { _, _ -> onAcknowledged() }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun showRecoveryCodeDialog(security: SecurityManager, result: SecurityManager.SetupResult, onDone: () -> Unit) {
        val code = result.recoveryCode ?: run { onDone(); return }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }

        container.addView(TextView(this).apply {
            text = "Save Your Recovery Code Now"
            textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8))
        })
        container.addView(TextView(this).apply {
            text = "This is shown only once. If you ever forget your PIN and lose biometric access, this code (or its QR code below) is the only way back into your notes without deleting everything. Copy the code somewhere safe, and screenshot the QR code too."
            textSize = 13f; setPadding(0, 0, 0, dp(16))
        })

        val codeDisplay = TextView(this).apply {
            text = code; textSize = 20f; typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER; setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
        }
        container.addView(codeDisplay)

        val copyBtn = Button(this).apply { text = "Copy Code" }
        copyBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("EngiNotes Recovery Code", code))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        container.addView(copyBtn)

        val qrView = ImageView(this).apply {
            setImageBitmap(security.generateQrBitmap(code))
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).also { it.gravity = Gravity.CENTER; it.topMargin = dp(16) }
        }
        val qrWrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        qrWrapper.addView(qrView)
        container.addView(qrWrapper)

        val ackCb = CheckBox(this).apply {
            text = "I've saved this code and/or screenshotted the QR code"
            setPadding(0, dp(16), 0, 0)
        }
        container.addView(ackCb)

        val dlg = AlertDialog.Builder(this).setTitle("\u26A0 Recovery Code").setView(container)
            .setPositiveButton("Done", null)
            .setCancelable(false)
            .show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!ackCb.isChecked) { Toast.makeText(this, "Please confirm you've saved the code first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            dlg.dismiss()
            onDone()
        }
    }

    private fun showPinSetupDialog(security: SecurityManager, onDone: () -> Unit) {
        val input1 = EditText(this).apply {
            hint = "Enter 6-digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        AlertDialog.Builder(this).setTitle("Set Your PIN").setView(input1)
            .setPositiveButton("Next", null) // overridden below so a bad PIN doesn't dismiss the dialog
            .setNegativeButton("Cancel", null)
            .show().also { dlg ->
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val pin = input1.text.toString()
                    if (pin.length != 6) { Toast.makeText(this, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    dlg.dismiss()
                    // Confirmation step — re-entering catches typos before they get baked into
                    // the actual encryption key derivation, which would otherwise silently lock
                    // the user out with a PIN that isn't the one they meant to set.
                    val input2 = EditText(this).apply {
                        hint = "Re-enter PIN to confirm"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        filters = arrayOf(android.text.InputFilter.LengthFilter(6))
                    }
                    AlertDialog.Builder(this).setTitle("Confirm Your PIN").setView(input2)
                        .setPositiveButton("Confirm", null)
                        .setNegativeButton("Cancel", null)
                        .show().also { dlg2 ->
                            dlg2.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                if (input2.text.toString() != pin) { Toast.makeText(this, "PINs didn't match — try again", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                                dlg2.dismiss()
                                val result = security.setupSecurity(pin)
                                if (!result.success) {
                                    Toast.makeText(this, "Something went wrong setting up App Lock. Please try again.", Toast.LENGTH_LONG).show()
                                    onDone()
                                    return@setOnClickListener
                                }
                                showRecoveryCodeDialog(security, result) {
                                    if (result.biometricEnabled) {
                                        Toast.makeText(this, "App Lock enabled with PIN + biometric", Toast.LENGTH_LONG).show()
                                    } else {
                                        val reason = result.biometricUnavailableReason ?: "Fingerprint/face wasn't available."
                                        Toast.makeText(this, "App Lock enabled with PIN. $reason", Toast.LENGTH_LONG).show()
                                    }
                                    onDone()
                                }
                            }
                        }
                }
            }
    }

    private fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    private fun showAppMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add(if (driveManager.isSignedIn()) "Sign Out of Google" else "Sign in with Google")
        if (driveManager.isSignedIn()) {
            popup.menu.add("Restore from Drive")
            popup.menu.add("Auto-Backup: ${if (getPrefs().getBoolean("auto_backup_drive", false)) "On" else "Off"}")
        }
        popup.menu.add("About")
        popup.setOnMenuItemClickListener { item ->
            when {
                item.title == "Sign in with Google" -> driveManager.signIn()
                item.title == "Sign Out of Google" -> driveManager.signOut {
                    Toast.makeText(this, "Signed out of Google", Toast.LENGTH_SHORT).show()
                }
                item.title == "Restore from Drive" -> showRestoreFromDriveDialog()
                item.title.toString().startsWith("Auto-Backup:") -> {
                    val newValue = !getPrefs().getBoolean("auto_backup_drive", false)
                    getPrefs().edit().putBoolean("auto_backup_drive", newValue).apply()
                    Toast.makeText(this, if (newValue) "Auto-backup turned on" else "Auto-backup turned off", Toast.LENGTH_SHORT).show()
                }
                item.title == "About" -> showAboutDialog()
            }
            true
        }
        popup.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        driveManager.handleSignInResult(requestCode, data) { success, error ->
            Toast.makeText(this, if (success) "Signed in with Google!" else (error ?: "Sign-in cancelled"), Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Restore from Drive: browse every note backed up on Drive and pull one down ----

    /**
     * Same parsing as MainActivity's extractAssetPaths() — pulls every local file path (images,
     * audio, custom fonts) a note's serialized content refers to, so restore can pull those down
     * too, not just the .eng text itself. Kept as a separate copy here since this Activity has no
     * open note / DrawingView to delegate to; see DrawingView.kt's serialize() for the line format.
     */
    private fun extractAssetPaths(content: String): List<String> {
        val paths = linkedSetOf<String>()
        content.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\u0001')
            when (parts.getOrNull(0)) {
                "IMAGE" -> parts.getOrNull(1)?.let { if (it.isNotBlank()) paths.add(it) }
                "AUDIO" -> parts.getOrNull(1)?.let { if (it.isNotBlank()) paths.add(it) }
                "FILL" -> {
                    parts.getOrNull(1)?.let { if (it.isNotBlank()) paths.add(it) }
                    parts.getOrNull(6)?.let { if (it.isNotBlank()) paths.add(it) }
                }
                "TEXT" -> {
                    val font = parts.getOrNull(9)
                    if (!font.isNullOrBlank() && font.startsWith("/")) paths.add(font)
                }
            }
        }
        return paths.toList()
    }

    private fun showRestoreFromDriveDialog() {
        Toast.makeText(this, "Checking Drive…", Toast.LENGTH_SHORT).show()
        driveManager.listFiles { files, error ->
            if (files == null) { Toast.makeText(this, error ?: "Couldn't reach Drive", Toast.LENGTH_SHORT).show(); return@listFiles }
            val notes = files.filter { it.name.endsWith(".eng") }
            if (notes.isEmpty()) { Toast.makeText(this, "No notes found on Drive", Toast.LENGTH_SHORT).show(); return@listFiles }
            val names = notes.map { it.name.removeSuffix(".eng") }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Restore a note from Drive")
                .setItems(names) { _, which -> confirmAndRestore(notes[which]) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Finds where a note of this name already lives locally (if anywhere), so restore lands back in the same book instead of always defaulting to General. */
    private fun confirmAndRestore(driveFile: com.google.api.services.drive.model.File) {
        val name = driveFile.name.removeSuffix(".eng")
        val existingLocal = getAllPages().firstOrNull { it.first.nameWithoutExtension == name }
        val destFile = existingLocal?.first ?: run {
            ensureDefaultBook()
            File(File(getBooksRoot(), "General"), driveFile.name)
        }
        val driveModified = driveFile.modifiedTime?.value ?: 0L
        if (destFile.exists() && destFile.lastModified() > driveModified) {
            AlertDialog.Builder(this)
                .setTitle("Local version is newer")
                .setMessage("Your local copy of \"$name\" is newer than the one on Drive. Restoring will overwrite your local changes with the older Drive version. Continue?")
                .setPositiveButton("Overwrite local") { _, _ -> doRestore(driveFile, destFile) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            doRestore(driveFile, destFile)
        }
    }

    private fun doRestore(driveFile: com.google.api.services.drive.model.File, destFile: File) {
        Toast.makeText(this, "Restoring…", Toast.LENGTH_SHORT).show()
        driveManager.downloadFile(driveFile.name, destFile) { success, error ->
            if (!success) { Toast.makeText(this, "Restore failed: $error", Toast.LENGTH_SHORT).show(); return@downloadFile }
            val assetPaths = extractAssetPaths(security.readNoteFile(destFile))
            restoreAssets(assetPaths, 0) {
                Toast.makeText(this, "Restored \"${destFile.nameWithoutExtension}\"!", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    private fun restoreAssets(paths: List<String>, index: Int, onDone: () -> Unit) {
        if (index >= paths.size) { onDone(); return }
        val destFile = File(paths[index])
        if (destFile.exists()) { restoreAssets(paths, index + 1, onDone); return }
        destFile.parentFile?.mkdirs()
        driveManager.downloadAsset(destFile.name, destFile) { _, _ -> restoreAssets(paths, index + 1, onDone) }
    }

    private fun showAboutDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)); gravity = Gravity.CENTER_HORIZONTAL }
        try {
            val icon = ImageView(this).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(80))
            }
            container.addView(icon)
        } catch (e: Exception) {}
        container.addView(TextView(this).apply {
            text = "EngiNotes"; textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#2A2A2A")); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        if (!versionName.isNullOrBlank()) {
            container.addView(TextView(this).apply {
                text = "Version $versionName"; textSize = 13f; setTextColor(android.graphics.Color.parseColor("#9E9E9E")); gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            })
        }
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); setBackgroundColor(android.graphics.Color.parseColor("#EEEEEE")) })
        container.addView(TextView(this).apply {
            text = "Developed by Amrit Khadka"; textSize = 15f; setTextColor(android.graphics.Color.parseColor("#2A2A2A")); gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        })
        container.addView(TextView(this).apply {
            text = "Contributor: Avinash Khadgi"; textSize = 14f; setTextColor(android.graphics.Color.parseColor("#5A5A5A")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        AlertDialog.Builder(this).setView(container).setPositiveButton("Close", null).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
