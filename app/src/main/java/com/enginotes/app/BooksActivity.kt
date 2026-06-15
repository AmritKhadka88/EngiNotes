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

    private lateinit var booksContainer: LinearLayout
    private lateinit var emptyView: TextView
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

        // Top bar
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(android.graphics.Color.parseColor("#FF6200EE"))
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12))
        topBar.gravity = android.view.Gravity.CENTER_VERTICAL
        val topLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        topLp.gravity = Gravity.TOP
        root.addView(topBar, topLp)

        val appTitle = TextView(this)
        appTitle.text = "📚 EngiNotes"
        appTitle.textSize = 22f
        appTitle.setTextColor(android.graphics.Color.WHITE)
        appTitle.typeface = android.graphics.Typeface.DEFAULT_BOLD
        appTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topBar.addView(appTitle)

        val searchBtn = Button(this)
        searchBtn.text = "🔍"
        searchBtn.textSize = 18f
        searchBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        searchBtn.setTextColor(android.graphics.Color.WHITE)
        searchBtn.minWidth = 0; searchBtn.minimumWidth = 0
        searchBtn.setPadding(dp(8), 0, dp(8), 0)
        searchBtn.setOnClickListener { showSearchDialog() }
        topBar.addView(searchBtn)

        // Scroll content
        val scroll = ScrollView(this)
        val scrollLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        scrollLp.topMargin = dp(56)
        scrollLp.bottomMargin = dp(80)
        root.addView(scroll, scrollLp)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(16))
        scroll.addView(content)

        // Section header
        val sectionHeader = TextView(this)
        sectionHeader.text = "MY BOOKS"
        sectionHeader.textSize = 11f
        sectionHeader.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        sectionHeader.typeface = android.graphics.Typeface.DEFAULT_BOLD
        sectionHeader.setPadding(dp(4), 0, 0, dp(8))
        content.addView(sectionHeader)

        booksContainer = LinearLayout(this)
        booksContainer.orientation = LinearLayout.VERTICAL
        content.addView(booksContainer)

        emptyView = TextView(this)
        emptyView.text = "No books yet.\nTap + to create your first book!"
        emptyView.textSize = 16f
        emptyView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        emptyView.gravity = android.view.Gravity.CENTER
        emptyView.setPadding(0, dp(80), 0, 0)
        content.addView(emptyView)

        // FAB
        val fab = Button(this)
        fab.text = "+"
        fab.textSize = 28f
        fab.setTextColor(android.graphics.Color.WHITE)
        fab.setBackgroundColor(android.graphics.Color.parseColor("#FF6200EE"))
        val fabLp = FrameLayout.LayoutParams(dp(60), dp(60))
        fabLp.gravity = Gravity.BOTTOM or Gravity.END
        fabLp.bottomMargin = dp(24); fabLp.rightMargin = dp(24)
        fab.setPadding(0, 0, 0, 0)
        fab.elevation = dp(6).toFloat()
        fab.setOnClickListener { showCreateBookDialog() }

        // Make FAB circular
        fab.post {
            fab.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#FF6200EE"))
            }
        }
        root.addView(fab, fabLp)

        setContentView(root)

        // Ensure default book exists
        ensureDefaultBook()
        refreshBooks()
    }

    private fun ensureDefaultBook() {
        val defaultBook = File(getBooksRoot(), "General")
        if (!defaultBook.exists()) defaultBook.mkdirs()
    }

    private fun getBooksRoot(): File {
        val f = File(filesDir, "books"); if (!f.exists()) f.mkdirs(); return f
    }

    private fun refreshBooks() {
        booksContainer.removeAllViews()
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        emptyView.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE

        for (book in books) {
            val pages = book.listFiles()?.filter { it.extension == "eng" } ?: emptyList()
            val lastModified = pages.maxOfOrNull { it.lastModified() }
            val lastModStr = if (lastModified != null) dateFormat.format(Date(lastModified)) else "Empty"

            val card = FrameLayout(this)
            card.setBackgroundColor(android.graphics.Color.WHITE)
            card.setPadding(dp(16), dp(16), dp(16), dp(16))
            card.elevation = dp(2).toFloat()
            val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cardLp.setMargins(0, 0, 0, dp(12))
            card.layoutParams = cardLp

            val cardContent = LinearLayout(this)
            cardContent.orientation = LinearLayout.HORIZONTAL
            cardContent.gravity = android.view.Gravity.CENTER_VERTICAL

            // Book icon
            val icon = TextView(this)
            icon.text = "📖"
            icon.textSize = 32f
            icon.setPadding(0, 0, dp(16), 0)
            cardContent.addView(icon)

            // Book info
            val info = LinearLayout(this)
            info.orientation = LinearLayout.VERTICAL
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val nameView = TextView(this)
            nameView.text = book.name
            nameView.textSize = 17f
            nameView.setTextColor(android.graphics.Color.BLACK)
            nameView.typeface = android.graphics.Typeface.DEFAULT_BOLD
            info.addView(nameView)

            val metaView = TextView(this)
            metaView.text = "${pages.size} page${if (pages.size != 1) "s" else ""} · $lastModStr"
            metaView.textSize = 13f
            metaView.setTextColor(android.graphics.Color.parseColor("#757575"))
            info.addView(metaView)

            cardContent.addView(info)

            // Arrow
            val arrow = TextView(this)
            arrow.text = "›"
            arrow.textSize = 24f
            arrow.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
            cardContent.addView(arrow)

            card.addView(cardContent)

            // Round corners
            card.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }
            card.elevation = dp(2).toFloat()

            card.setOnClickListener {
                val intent = Intent(this, HomeActivity::class.java)
                intent.putExtra("book_name", book.name)
                startActivity(intent)
            }

            card.setOnLongClickListener {
                showBookOptions(book)
                true
            }

            booksContainer.addView(card)
        }
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
                    4 -> shareBook(book)
                }
            }.show()
    }

    private fun showRenameBookDialog(book: File) {
        val input = EditText(this); input.setText(book.name)
        input.selectAll()
        AlertDialog.Builder(this).setTitle("Rename Book").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank() || newName == book.name) return@setPositiveButton
                val newBook = File(getBooksRoot(), newName)
                if (newBook.exists()) { Toast.makeText(this, "A book with that name already exists", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                book.renameTo(newBook)
                refreshBooks()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun copyBook(book: File) {
        val input = EditText(this); input.setText("${book.name} (copy)")
        AlertDialog.Builder(this).setTitle("Copy Book").setView(input)
            .setPositiveButton("Copy") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { "${book.name} (copy)" }
                val newBook = File(getBooksRoot(), newName)
                newBook.mkdirs()
                book.listFiles()?.forEach { it.copyTo(File(newBook, it.name), overwrite = true) }
                Toast.makeText(this, "Copied to $newName", Toast.LENGTH_SHORT).show()
                refreshBooks()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showMergeBookDialog(book: File) {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != book.name } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other books to merge into", Toast.LENGTH_SHORT).show(); return }
        val names = books.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Merge '${book.name}' into...")
            .setItems(names) { _, i ->
                val target = books[i]
                book.listFiles()?.forEach { page ->
                    var dest = File(target, page.name)
                    if (dest.exists()) dest = File(target, "${page.nameWithoutExtension}_merged.${page.extension}")
                    page.copyTo(dest, overwrite = false)
                }
                book.deleteRecursively()
                Toast.makeText(this, "Merged into ${target.name}", Toast.LENGTH_SHORT).show()
                refreshBooks()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteBook(book: File) {
        val pageCount = book.listFiles()?.filter { it.extension == "eng" }?.size ?: 0
        AlertDialog.Builder(this).setTitle("Delete '${book.name}'?")
            .setMessage("This will permanently delete $pageCount page${if (pageCount != 1) "s" else ""}. Cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                book.deleteRecursively()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                refreshBooks()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun shareBook(book: File) {
        Toast.makeText(this, "Share feature coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun showCreateBookDialog() {
        val input = EditText(this); input.hint = "Book name"
        AlertDialog.Builder(this).setTitle("📖 New Book").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Book ${System.currentTimeMillis()}" }
                val book = File(getBooksRoot(), name)
                if (book.exists()) { Toast.makeText(this, "Book already exists", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                book.mkdirs()
                refreshBooks()
                // Open the new book immediately
                val intent = Intent(this, HomeActivity::class.java)
                intent.putExtra("book_name", name)
                startActivity(intent)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSearchDialog() {
        val input = EditText(this); input.hint = "Search books..."
        AlertDialog.Builder(this).setTitle("🔍 Search").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim().lowercase()
                if (query.isBlank()) { refreshBooks(); return@setPositiveButton }
                booksContainer.removeAllViews()
                val books = getBooksRoot().listFiles()?.filter {
                    it.isDirectory && it.name.lowercase().contains(query)
                } ?: emptyList()
                if (books.isEmpty()) {
                    val tv = TextView(this); tv.text = "No books found for \"$query\""
                    tv.textSize = 15f; tv.setTextColor(android.graphics.Color.GRAY)
                    tv.gravity = android.view.Gravity.CENTER; tv.setPadding(0, dp(40), 0, 0)
                    booksContainer.addView(tv)
                } else {
                    emptyView.visibility = View.GONE
                    for (book in books) {
                        val btn = Button(this); btn.text = book.name
                        btn.setOnClickListener {
                            startActivity(Intent(this, HomeActivity::class.java).putExtra("book_name", book.name))
                        }
                        booksContainer.addView(btn)
                    }
                }
            }.setNegativeButton("Cancel") { _, _ -> refreshBooks() }.show()
    }

    override fun onResume() { super.onResume(); refreshBooks() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
