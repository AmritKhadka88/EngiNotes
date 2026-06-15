package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BooksActivity : AppCompatActivity() {

    private lateinit var recentContainer: LinearLayout
    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyView: TextView
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

        // Top bar
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(android.graphics.Color.parseColor("#FF6200EE"))
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12))
        topBar.gravity = Gravity.CENTER_VERTICAL
        val topLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        topLp.gravity = Gravity.TOP
        root.addView(topBar, topLp)

        val appTitle = TextView(this)
        appTitle.text = "📚 EngiNotes"
        appTitle.textSize = 22f
        appTitle.setTextColor(android.graphics.Color.WHITE)
        appTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        appTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topBar.addView(appTitle)

        fun topBtn(emoji: String, action: () -> Unit) {
            val b = Button(this); b.text = emoji; b.textSize = 18f
            b.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            b.setTextColor(android.graphics.Color.WHITE)
            b.minWidth = 0; b.minimumWidth = 0
            b.setPadding(dp(8), 0, dp(8), 0)
            b.setOnClickListener { action() }
            topBar.addView(b)
        }

        topBtn("🔍") { showSearchDialog() }
        topBtn("⚙") { showSettingsDialog() }

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
        recentHeader.text = "🕐 RECENT NOTES"
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

        // Books section
        val booksHeader = TextView(this)
        booksHeader.text = "📚 MY BOOKS"
        booksHeader.textSize = 11f
        booksHeader.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        booksHeader.typeface = android.graphics.Typeface.DEFAULT_BOLD
        booksHeader.setPadding(dp(4), 0, 0, dp(8))
        content.addView(booksHeader)

        booksContainer = LinearLayout(this)
        booksContainer.orientation = LinearLayout.VERTICAL
        content.addView(booksContainer)

        emptyView = TextView(this)
        emptyView.text = "No books yet.\nTap + to create your first book!"
        emptyView.textSize = 16f
        emptyView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, dp(40), 0, 0)
        content.addView(emptyView)

        // FAB
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
                setColor(android.graphics.Color.parseColor("#FF6200EE"))
            }
        }
        fab.setOnClickListener { showCreateBookDialog() }
        root.addView(fab, fabLp)

        setContentView(root)
        ensureDefaultBook()
        refresh()
    }

    private fun ensureDefaultBook() {
        val defaultBook = File(getBooksRoot(), "General")
        if (!defaultBook.exists()) defaultBook.mkdirs()
    }

    private fun getBooksRoot(): File {
        val f = File(filesDir, "books"); if (!f.exists()) f.mkdirs(); return f
    }

    private fun getAllPages(): List<Pair<File, String>> {
        // Returns list of (pageFile, bookName) sorted by lastModified
        val result = mutableListOf<Pair<File, String>>()
        getBooksRoot().listFiles()?.filter { it.isDirectory }?.forEach { book ->
            book.listFiles()?.filter { it.extension == "eng" }?.forEach { page ->
                result.add(page to book.name)
            }
        }
        return result.sortedByDescending { it.first.lastModified() }
    }

    private fun refresh() {
        refreshRecent()
        refreshBooks()
    }

    private fun refreshRecent() {
        recentContainer.removeAllViews()
        val recent = getAllPages().take(3)
        if (recent.isEmpty()) {
            val tv = TextView(this); tv.text = "No recent notes"
            tv.textSize = 14f; tv.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            tv.setPadding(dp(4), 0, 0, 0)
            recentContainer.addView(tv); return
        }
        for ((file, bookName) in recent) {
            val card = makePageCard(file, bookName)
            recentContainer.addView(card)
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

            val icon = TextView(this); icon.text = "📖"; icon.textSize = 32f
            icon.setPadding(0, 0, dp(16), 0); row.addView(icon)

            val info = LinearLayout(this); info.orientation = LinearLayout.VERTICAL
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val nameView = TextView(this); nameView.text = book.name; nameView.textSize = 17f
            nameView.setTextColor(android.graphics.Color.BLACK)
            nameView.typeface = android.graphics.Typeface.DEFAULT_BOLD; info.addView(nameView)

            val metaView = TextView(this)
            metaView.text = "${pages.size} page${if (pages.size != 1) "s" else ""} · $lastModStr"
            metaView.textSize = 13f; metaView.setTextColor(android.graphics.Color.parseColor("#757575"))
            info.addView(metaView); row.addView(info)

            val arrow = TextView(this); arrow.text = "›"; arrow.textSize = 24f
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

        val icon = TextView(this); icon.text = "📄"; icon.textSize = 24f
        icon.setPadding(0, 0, dp(12), 0); row.addView(icon)

        val info = LinearLayout(this); info.orientation = LinearLayout.VERTICAL
        info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val nameView = TextView(this); nameView.text = file.nameWithoutExtension; nameView.textSize = 15f
        nameView.setTextColor(android.graphics.Color.BLACK)
        nameView.typeface = android.graphics.Typeface.DEFAULT_BOLD; info.addView(nameView)

        val metaView = TextView(this)
        metaView.text = "📖 $bookName · ${dateFormat.format(Date(file.lastModified()))}"
        metaView.textSize = 12f; metaView.setTextColor(android.graphics.Color.parseColor("#757575"))
        info.addView(metaView); row.addView(info)

        val arrow = TextView(this); arrow.text = "›"; arrow.textSize = 20f
        arrow.setTextColor(android.graphics.Color.parseColor("#BDBDBD")); row.addView(arrow)

        card.addView(row)
        card.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("filename", file.nameWithoutExtension)
                .putExtra("book_name", bookName))
        }
        card.setOnLongClickListener {
            showPageQuickOptions(file, bookName); true
        }
        return card
    }

    private fun showPageQuickOptions(file: File, bookName: String) {
        val options = arrayOf("✏️ Open", "📦 Move", "📋 Copy", "🗑️ Delete")
        AlertDialog.Builder(this).setTitle(file.nameWithoutExtension)
            .setItems(options) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, MainActivity::class.java)
                        .putExtra("filename", file.nameWithoutExtension)
                        .putExtra("book_name", bookName))
                    1 -> showMovePageDialog(file, bookName)
                    2 -> {
                        val copy = File(file.parentFile, "${file.nameWithoutExtension}_copy.eng")
                        file.copyTo(copy, overwrite = true)
                        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                    3 -> AlertDialog.Builder(this).setTitle("Delete?")
                        .setMessage("Delete ${file.nameWithoutExtension}?")
                        .setPositiveButton("Delete") { _, _ -> file.delete(); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun showMovePageDialog(file: File, currentBook: String) {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != currentBook } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other books", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Move to book...")
            .setItems(books.map { it.name }.toTypedArray()) { _, i ->
                val target = File(books[i], file.name)
                file.copyTo(target, overwrite = true); file.delete()
                Toast.makeText(this, "Moved to ${books[i].name}", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showBookOptions(book: File) {
        val options = arrayOf("✏️ Rename", "📋 Copy Book", "🔀 Merge into another book", "🗑️ Delete", "📤 Share all pages")
        AlertDialog.Builder(this).setTitle(book.name)
            .setItems(options) { _, i ->
                when (i) {
                    0 -> showRenameBookDialog(book)
                    1 -> copyBook(book)
                    2 -> showMergeBookDialog(book)
                    3 -> confirmDeleteBook(book)
                    4 -> Toast.makeText(this, "Share coming soon!", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun showRenameBookDialog(book: File) {
        val input = EditText(this); input.setText(book.name); input.selectAll()
        AlertDialog.Builder(this).setTitle("Rename Book").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank() || newName == book.name) return@setPositiveButton
                val newBook = File(getBooksRoot(), newName)
                if (newBook.exists()) { Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                book.renameTo(newBook); refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun copyBook(book: File) {
        val input = EditText(this); input.setText("${book.name} (copy)")
        AlertDialog.Builder(this).setTitle("Copy Book").setView(input)
            .setPositiveButton("Copy") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { "${book.name} (copy)" }
                val newBook = File(getBooksRoot(), newName); newBook.mkdirs()
                book.listFiles()?.forEach { it.copyTo(File(newBook, it.name), overwrite = true) }
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show(); refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showMergeBookDialog(book: File) {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != book.name } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other books", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Merge '${book.name}' into...")
            .setItems(books.map { it.name }.toTypedArray()) { _, i ->
                book.listFiles()?.forEach { page ->
                    var dest = File(books[i], page.name)
                    if (dest.exists()) dest = File(books[i], "${page.nameWithoutExtension}_merged.${page.extension}")
                    page.copyTo(dest, overwrite = false)
                }
                book.deleteRecursively()
                Toast.makeText(this, "Merged into ${books[i].name}", Toast.LENGTH_SHORT).show(); refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteBook(book: File) {
        val pageCount = book.listFiles()?.filter { it.extension == "eng" }?.size ?: 0
        AlertDialog.Builder(this).setTitle("Delete '${book.name}'?")
            .setMessage("This will delete $pageCount pages. Cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                book.deleteRecursively()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showCreateBookDialog() {
        val input = EditText(this); input.hint = "Book name"
        AlertDialog.Builder(this).setTitle("📖 New Book").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Book ${System.currentTimeMillis()}" }
                val book = File(getBooksRoot(), name)
                if (book.exists()) { Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                book.mkdirs(); refresh()
                startActivity(Intent(this, HomeActivity::class.java).putExtra("book_name", name))
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSearchDialog() {
        val input = EditText(this); input.hint = "Search notes and books..."
        AlertDialog.Builder(this).setTitle("🔍 Search").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim().lowercase()
                if (query.isBlank()) { refresh(); return@setPositiveButton }
                booksContainer.removeAllViews(); recentContainer.removeAllViews()
                val results = getAllPages().filter { it.first.nameWithoutExtension.lowercase().contains(query) }
                if (results.isEmpty()) {
                    val tv = TextView(this); tv.text = "No results for \"$query\""
                    tv.textSize = 15f; tv.setTextColor(android.graphics.Color.GRAY)
                    tv.gravity = Gravity.CENTER; tv.setPadding(0, dp(40), 0, 0)
                    recentContainer.addView(tv)
                } else {
                    for ((file, bookName) in results) recentContainer.addView(makePageCard(file, bookName))
                }
            }.setNegativeButton("Cancel") { _, _ -> refresh() }.show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(8), dp(20), dp(8))

        fun header(text: String) {
            val tv = TextView(this); tv.text = text; tv.textSize = 11f
            tv.setTextColor(android.graphics.Color.parseColor("#7B61FF"))
            tv.setPadding(0, dp(10), 0, dp(2))
            tv.typeface = android.graphics.Typeface.DEFAULT_BOLD; container.addView(tv)
        }

        header("GENERAL")
        val confirmCb = android.widget.CheckBox(this)
        confirmCb.text = "Confirm before exit or clear canvas"
        confirmCb.isChecked = prefs.getBoolean("confirm_exit_clear", true)
        container.addView(confirmCb)

        val autosaveCb = android.widget.CheckBox(this)
        autosaveCb.text = "Autosave notes"
        autosaveCb.isChecked = prefs.getBoolean("autosave", true)
        container.addView(autosaveCb)

        header("DEFAULT BOOK")
        val bookNames = getBooksRoot().listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        val currentDefault = prefs.getString("default_book", "General") ?: "General"
        val bookSpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bookNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bookSpinner.adapter = adapter
        bookSpinner.setSelection(bookNames.indexOf(currentDefault).coerceAtLeast(0))
        container.addView(bookSpinner)

        val scroll = ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("⚙ Settings").setView(scroll)
            .setPositiveButton("Done") { _, _ ->
                prefs.edit()
                    .putBoolean("confirm_exit_clear", confirmCb.isChecked)
                    .putBoolean("autosave", autosaveCb.isChecked)
                    .putString("default_book", bookNames.getOrElse(bookSpinner.selectedItemPosition) { "General" })
                    .apply()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
