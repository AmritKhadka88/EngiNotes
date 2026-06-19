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
        appTitle.text = "\uD83D\uDCDA EngiNotes"
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

        topBtn("\uD83D\uDD0D") { showSearchDialog() }
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
