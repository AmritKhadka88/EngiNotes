package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class BooksActivity : AppCompatActivity() {

    private lateinit var recentContainer: LinearLayout
    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyView: TextView
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val driveManager by lazy { DriveManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.parseColor("#FAF6EF"))

        // Top bar
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(android.graphics.Color.parseColor("#8D6E63"))
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12))
        topBar.gravity = Gravity.CENTER_VERTICAL
        val topLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        topLp.gravity = Gravity.TOP
        root.addView(topBar, topLp)

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
            fab.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#8D6E63"))
            }
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
    }

    private fun ensureDefaultBook() {
        val defaultBook = File(getBooksRoot(), "General")
        if (!defaultBook.exists()) defaultBook.mkdirs()
    }

    override fun onResume() {
        super.onResume()
        refresh()
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

    // The "v4" here is a cache-format version marker, not part of the note's identity — bump it
    // any time renderThumbnail's actual output changes, so every previously-cached thumbnail is
    // treated as missing and regenerated fresh instead of silently keeping old/incorrect cached
    // PNGs until each note is next edited. (v3 → v4: Convenient-mode notes were still showing a
    // vertical band past the page's actual right edge — the page is only 82% as wide as the view
    // it's measured in, but the render was filling the entire view width.)
    private fun thumbnailFileFor(note: File): File =
        File(thumbnailCacheDir(), "${note.nameWithoutExtension}_${note.lastModified()}_v4.png")

    // Renders a note's first page to a bitmap by loading it into an off-screen DrawingView and
    // drawing that View directly — reuses all of DrawingView's actual rendering logic (strokes,
    // tables, text, hatch fills, everything) instead of a second, simplified renderer that would
    // inevitably drift out of sync with what the note actually looks like when opened for real.
    private fun renderThumbnail(note: File, maxWidthPx: Int, maxHeightPx: Int): Bitmap? {
        val dv = DrawingView(this)
        dv.loadFromString(note.readText())
        val convenient = dv.canvasMode == CanvasMode.CONVENIENT
        if (convenient) {
            // Convenient-mode notes (the default canvas mode) don't have a fixed intrinsic page
            // size — DrawingView.onLayout defines their "page" as 82% of whatever View WIDTH and
            // 110% of whatever View HEIGHT they're CURRENTLY shown in. Height isn't a problem:
            // 110% is taller than the view, so the full view height is always legitimately
            // inside the page (Convenient pages are meant to scroll — this just shows the top).
            // Width IS a problem: 82% is NARROWER than the view, so filling the whole view width
            // showed the ~18% margin area past the page's actual right edge — that's the visible
            // vertical band. Fixed by cropping the rendered bitmap to the page's real width in
            // post-processing (one single layout pass, then plain bitmap sub-region extraction)
            // instead of re-measuring the view a second time — a second pass would just retrigger
            // the same page-size recalculation with the new (narrower) dimensions and drift
            // again, the same class of bug the earlier aspect-ratio fix ran into.
            dv.measure(View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.EXACTLY))
            dv.layout(0, 0, maxWidthPx, maxHeightPx)
            val anchor = dv.firstContentAnchor()
            dv.resetViewForThumbnail(1f, anchor?.first ?: 0f, anchor?.second ?: 0f)
            val fullBmp = Bitmap.createBitmap(maxWidthPx, maxHeightPx, Bitmap.Config.ARGB_8888)
            dv.draw(android.graphics.Canvas(fullBmp))
            val cropW = dv.pageWidthPx().toInt().coerceIn(1, maxWidthPx)
            val cropped = if (cropW < maxWidthPx) Bitmap.createBitmap(fullBmp, 0, 0, cropW, maxHeightPx) else fullBmp
            applyEdgeFade(cropped)
            return cropped
        }
        // Paper-size notes (Fixed/Paginated/Infinite canvas modes): page dimensions come from
        // paperSize + orientation, completely independent of view size — reliable regardless of
        // what dimensions this probe pass uses, so a second layout pass to refine toward the
        // exact page aspect ratio is safe here (no recalculation-drift risk like Convenient mode).
        val probeSpec = View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.EXACTLY)
        dv.measure(probeSpec, probeSpec)
        dv.layout(0, 0, maxWidthPx, maxWidthPx)
        val pageAspect = dv.pageWidthPx().coerceAtLeast(1f) / dv.pageHeightPx().coerceAtLeast(1f)
        var w = maxWidthPx; var h = (w / pageAspect).toInt()
        if (h > maxHeightPx) { h = maxHeightPx; w = (h * pageAspect).toInt() }
        val finalW = w.coerceAtLeast(1); val finalH = h.coerceAtLeast(1)
        dv.measure(View.MeasureSpec.makeMeasureSpec(finalW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(finalH, View.MeasureSpec.EXACTLY))
        dv.layout(0, 0, finalW, finalH)
        // Anchor to wherever the note's actual content starts — an Infinite-canvas note could
        // have its first real content drawn well away from page-1 origin. The scale still comes
        // from the page-fit calculation either way, so this still reads as "one page's worth of
        // content," just panned to where the content actually begins instead of always (0,0).
        val anchor = dv.firstContentAnchor()
        val scale = minOf(finalW / dv.pageWidthPx(), finalH / dv.pageHeightPx())
        dv.resetViewForThumbnail(if (scale.isFinite() && scale > 0f) scale else 1f, anchor?.first ?: 0f, anchor?.second ?: 0f)
        val bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        dv.draw(android.graphics.Canvas(bmp))
        applyEdgeFade(bmp)
        return bmp
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
                } catch (e: Exception) {}
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
        card.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE); cornerRadius = dp(10).toFloat()
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

        val openBtn = TextView(this); openBtn.text = "\u203a"; openBtn.textSize = 24f
        openBtn.setTextColor(android.graphics.Color.parseColor("#BDBDBD")); row.addView(openBtn)

        card.addView(row)
        card.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("book_name", bookName)
                .putExtra("filename", file.nameWithoutExtension))
        }
        card.setOnLongClickListener {
            showPageOptions(file, bookName); true
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
        val thumbMaxW = dp(200); val thumbMaxH = dp(260)
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), android.graphics.Color.parseColor("#E0E0E0"))
            }
            // Without this, the rendered thumbnail bitmap — a plain rectangle — poked past the
            // rounded corners of the white background/border underneath it instead of being
            // cropped to match.
            clipToOutline = true
            // Was dp(1) — barely visible. Bumped up for a real, visible card shadow (matching
            // the Notewise reference) rather than something you'd only notice if you looked.
            elevation = dp(4).toFloat()
        }
        card.addView(imageView)
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
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("book_name", bookName)
                .putExtra("filename", file.nameWithoutExtension))
        }
        card.setOnLongClickListener { showPageOptions(file, bookName); true }
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
                    "New Note in this Book" -> startActivity(Intent(this, MainActivity::class.java).putExtra("book_name", book.name))
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

    private fun showPageOptions(file: File, bookName: String) {
        AlertDialog.Builder(this).setTitle(file.nameWithoutExtension)
            .setItems(arrayOf("Open", "Rename", "Move to Book", "Delete")) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, MainActivity::class.java).putExtra("book_name", bookName).putExtra("filename", file.nameWithoutExtension))
                    1 -> {
                        val input = EditText(this).apply { setText(file.nameWithoutExtension) }
                        AlertDialog.Builder(this).setTitle("Rename Note").setView(input)
                            .setPositiveButton("Rename") { _, _ ->
                                val n = input.text.toString().trim()
                                if (n.isNotEmpty()) { file.renameTo(File(file.parentFile, "$n.eng")); refresh() }
                            }.setNegativeButton("Cancel", null).show()
                    }
                    2 -> {
                        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != bookName }?.map { it.name } ?: emptyList()
                        if (books.isEmpty()) { Toast.makeText(this, "No other books", Toast.LENGTH_SHORT).show(); return@setItems }
                        AlertDialog.Builder(this).setTitle("Move to Book").setItems(books.toTypedArray()) { _, bi ->
                            val dest = File(File(getBooksRoot(), books[bi]), file.name)
                            file.copyTo(dest, overwrite = true); file.delete(); refresh()
                            Toast.makeText(this, "Moved to ${books[bi]}", Toast.LENGTH_SHORT).show()
                        }.show()
                    }
                    3 -> AlertDialog.Builder(this).setTitle("Delete '${file.nameWithoutExtension}'?")
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
                }.show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }

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

        val autosaveCb = CheckBox(this).apply { text = "Autosave every 10 seconds"; isChecked = prefs.getBoolean("autosave", true) }
        container.addView(autosaveCb)
        val confirmCb = CheckBox(this).apply { text = "Confirm before exit or clear"; isChecked = prefs.getBoolean("confirm_exit_clear", true) }
        container.addView(confirmCb)

        AlertDialog.Builder(this).setTitle("\u2699 Settings").setView(container)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("default_paper", selPaper)
                    .putBoolean("autosave", autosaveCb.isChecked)
                    .putBoolean("confirm_exit_clear", confirmCb.isChecked)
                    .apply()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
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
            val assetPaths = extractAssetPaths(destFile.readText())
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
