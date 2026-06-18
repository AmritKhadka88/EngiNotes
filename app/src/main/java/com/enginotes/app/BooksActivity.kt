package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Home / launcher screen.
 * Modified to bypass immediately into a new note on first cold launch,
 * while maintaining the complete, rich UI dashboard for subsequent management.
 */
class BooksActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var notesContainer: LinearLayout
    private lateinit var emptyView: TextView
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultBook()
        setContentView(buildUI())
        
        // Check if this is a fresh cold boot launch of the application
        val isFirstLaunch = intent.getBooleanExtra("BypassedToCanvas", false)
        if (!isFirstLaunch) {
            // Instantly trigger a new canvas session under the "General" environment
            val book = getDefaultBook()
            File(getBooksRoot(), book).mkdirs()
            val name = "Note_${System.currentTimeMillis()}"
            
            val directCanvasIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("filename", name)
                putExtra("book_name", book)
            }
            
            // Mark the intent so when they hit "back" out of the canvas, they return to this dashboard smoothly
            intent.putExtra("BypassedToCanvas", true)
            startActivity(directCanvasIntent)
        }

        refresh()
    }

    // ──────────────────────────────────────────────────────────────
    //  UI build (pure code — completely preserved)
    // ──────────────────────────────────────────────────────────────

    private fun buildUI(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#F7F7F7"))

        // ── Top bar ──
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(4).toFloat()
            setPadding(dp(12), dp(10), dp(8), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.TOP })

        val logoText = TextView(this).apply {
            text = "EngiNotes"
            textSize = 20f
            setTextColor(Color.parseColor("#212121"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(logoText)

        // Search icon
        iconBtn(topBar, "🔍") { toggleSearch() }
        // Settings icon
        iconBtn(topBar, "⚙") { showSettingsDialog() }

        // ── Search bar (hidden by default) ──
        searchInput = EditText(this).apply {
            hint = "Search notes…"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh(s?.toString() ?: "") }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        val searchLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.TOP; it.topMargin = dp(56) }
        root.addView(searchInput, searchLp)

        // ── Scroll content ──
        val scroll = ScrollView(this)
        val scrollLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).also { it.topMargin = dp(56); it.bottomMargin = dp(88) }
        root.addView(scroll, scrollLp)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        scroll.addView(content)

        val sectionLbl = TextView(this).apply {
            text = "NOTES"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, dp(8))
        }
        content.addView(sectionLbl)

        notesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(notesContainer)

        emptyView = TextView(this).apply {
            text = "No notes yet.\nTap + to create your first note!"
            textSize = 15f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, dp(80), 0, 0)
        }
        content.addView(emptyView)

        // ── FAB (+ Option directly routing to canvas inside default General container) ──
        val fab = Button(this).apply {
            text = "+"
            textSize = 30f
            setTextColor(Color.WHITE)
            elevation = dp(6).toFloat()
            setPadding(0, 0, 0, 0)
            post {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#6200EE"))
                }
            }
            setOnClickListener { createNewNote() }
        }
        val fabLp = FrameLayout.LayoutParams(dp(60), dp(60)).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(24); it.rightMargin = dp(24)
        }
        root.addView(fab, fabLp)

        return root
    }

    private fun iconBtn(parent: LinearLayout, emoji: String, action: () -> Unit) {
        Button(this).apply {
            text = emoji; textSize = 18f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#424242"))
            minWidth = 0; minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action() }
            parent.addView(this)
        }
    }

    private fun toggleSearch() {
        searchInput.visibility = if (searchInput.visibility == View.GONE) View.VISIBLE else View.GONE
        if (searchInput.visibility == View.GONE) { searchInput.setText(""); refresh() }
        else searchInput.requestFocus()
    }

    // ──────────────────────────────────────────────────────────────
    //  Data helpers
    // ──────────────────────────────────────────────────────────────

    private fun getBooksRoot(): File = File(filesDir, "books").also { it.mkdirs() }
    private fun ensureDefaultBook() { File(getBooksRoot(), "General").mkdirs() }
    private fun getDefaultBook(): String =
        getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
            .getString("default_book", "General") ?: "General"

    private fun getAllNotes(): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        getBooksRoot().listFiles()?.filter { it.isDirectory }?.forEach { book ->
            book.listFiles()?.filter { it.extension == "eng" }?.forEach { page ->
                result.add(page to book.name)
            }
        }
        return result.sortedByDescending { it.first.lastModified() }
    }

    // ──────────────────────────────────────────────────────────────
    //  Refresh
    // ──────────────────────────────────────────────────────────────

    private fun refresh(query: String = "") {
        notesContainer.removeAllViews()
        val notes = getAllNotes().let { all ->
            if (query.isBlank()) all
            else all.filter { it.first.nameWithoutExtension.contains(query, ignoreCase = true) }
        }
        emptyView.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        for ((file, bookName) in notes) notesContainer.addView(makeNoteCard(file, bookName))
    }

    private fun makeNoteCard(file: File, bookName: String): View {
        val card = FrameLayout(this).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(2).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp(10)) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Icon
        TextView(this).apply {
            text = "📄"; textSize = 26f
            setPadding(0, 0, dp(14), 0)
            row.addView(this)
        }

        // Info column
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        TextView(this).apply {
            text = file.nameWithoutExtension; textSize = 16f
            setTextColor(Color.parseColor("#212121")); typeface = Typeface.DEFAULT_BOLD
            info.addView(this)
        }
        TextView(this).apply {
            text = "📖 $bookName  ·  ${dateFormat.format(Date(file.lastModified()))}"
            textSize = 12f; setTextColor(Color.parseColor("#757575"))
            info.addView(this)
        }
        row.addView(info)

        // Arrow
        TextView(this).apply {
            text = "›"; textSize = 22f; setTextColor(Color.parseColor("#BDBDBD"))
            row.addView(this)
        }

        card.addView(row)
        card.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("filename", file.nameWithoutExtension)
                .putExtra("book_name", bookName))
        }
        card.setOnLongClickListener { showNoteOptions(file, bookName); true }
        return card
    }

    // ──────────────────────────────────────────────────────────────
    //  Actions
    // ──────────────────────────────────────────────────────────────

    private fun createNewNote() {
        val book = getDefaultBook()
        File(getBooksRoot(), book).mkdirs()
        val name = "Note_${System.currentTimeMillis()}"
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra("filename", name)
            .putExtra("book_name", book))
    }

    private fun showNoteOptions(file: File, bookName: String) {
        val opts = arrayOf("✏️ Open", "🔖 Rename", "📋 Duplicate", "📦 Move to Book", "🗑️ Delete")
        AlertDialog.Builder(this).setTitle(file.nameWithoutExtension)
            .setItems(opts) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, MainActivity::class.java)
                        .putExtra("filename", file.nameWithoutExtension)
                        .putExtra("book_name", bookName))
                    1 -> renameNote(file)
                    2 -> { file.copyTo(File(file.parentFile, "${file.nameWithoutExtension}_copy.eng"), true); refresh() }
                    3 -> moveNote(file, bookName)
                    4 -> AlertDialog.Builder(this).setTitle("Delete?")
                        .setMessage("Delete '${file.nameWithoutExtension}'? Cannot be undone.")
                        .setPositiveButton("Delete") { _, _ -> file.delete(); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun renameNote(file: File) {
        val input = EditText(this).apply { setText(file.nameWithoutExtension); selectAll() }
        AlertDialog.Builder(this).setTitle("Rename").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val n = input.text.toString().trim().ifEmpty { return@setPositiveButton }
                val dest = File(file.parentFile, "$n.eng")
                if (!dest.exists()) file.renameTo(dest)
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun moveNote(file: File, currentBook: String) {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != currentBook } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other books", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Move to book…")
            .setItems(books.map { it.name }.toTypedArray()) { _, i ->
                file.copyTo(File(books[i], file.name), true); file.delete()
                Toast.makeText(this, "Moved to ${books[i].name}", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    // ──────────────────────────────────────────────────────────────
    //  Settings dialog — fully intact
    // ──────────────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        fun header(text: String) {
            container.addView(TextView(this).apply {
                this.text = text; textSize = 11f
                setTextColor(Color.parseColor("#7B61FF"))
                setPadding(0, dp(12), 0, dp(4))
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        fun divider() {
            container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).also { it.setMargins(0, dp(8), 0, dp(4)) }
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            })
        }

        header("GENERAL")
        val confirmCb = CheckBox(this).apply {
            text = "Confirm before exit or clear canvas"
            isChecked = prefs.getBoolean("confirm_exit_clear", true)
        }
        container.addView(confirmCb)

        val autosaveCb = CheckBox(this).apply {
            text = "Autosave every 10 seconds"
            isChecked = prefs.getBoolean("autosave", true)
        }
        container.addView(autosaveCb)

        divider(); header("DEFAULT PAPER STYLE")
        val paperLabels = arrayOf("Blank", "Lined", "Graph Grid", "Dot Grid", "Engineering Grid", "Coloured")
        val paperValues = arrayOf("BLANK", "LINED", "GRID", "DOTS", "ENGINEERING", "BLANK_COLORED")
        val currentPaper = prefs.getString("default_paper", "LINED") ?: "LINED"
        var selPaper = currentPaper
        val paperLbl = TextView(this).apply {
            textSize = 15f; setTextColor(Color.parseColor("#1565C0"))
            setPadding(0, dp(8), 0, dp(8))
        }
        fun refreshPaperLbl() { paperLbl.text = "Default: ${paperLabels[paperValues.indexOf(selPaper).coerceAtLeast(0)]}  (tap)" }
        refreshPaperLbl()
        paperLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Default Paper Style")
                .setItems(paperLabels) { _, i -> selPaper = paperValues[i]; refreshPaperLbl() }.show()
        }
        container.addView(paperLbl)

        divider(); header("DEFAULT BOOK FOR NEW NOTES")
        val bookNames = getBooksRoot().listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        val currentDefault = prefs.getString("default_book", "General") ?: "General"
        val bookSpinner = Spinner(this).apply {
            val adpt = ArrayAdapter(this@BooksActivity, android.R.layout.simple_spinner_item, bookNames)
            adpt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = adpt
            setSelection(bookNames.indexOf(currentDefault).coerceAtLeast(0))
        }
        container.addView(bookSpinner)

        divider(); header("BOOK MANAGEMENT")
        val newBookBtn = Button(this).apply {
            text = "+ Create New Book"; textSize = 13f
            setBackgroundColor(Color.parseColor("#EDE7F6"))
            setTextColor(Color.parseColor("#4527A0"))
            setOnClickListener { createBookDialog { refresh() } }
        }
        container.addView(newBookBtn)

        // List existing books with rename/delete
        getBooksRoot().listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { book ->
            val bookRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            bookRow.addView(TextView(this).apply {
                text = "📖 ${book.name}"
                textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            Button(this).apply {
                text = "✏"; textSize = 13f; minWidth = 0; minimumWidth = 0
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setBackgroundColor(Color.parseColor("#EEE")); setTextColor(Color.parseColor("#212121"))
                setOnClickListener { renameBookDialog(book) { refresh() } }
                bookRow.addView(this)
            }
            Button(this).apply {
                text = "🗑"; textSize = 13f; minWidth = 0; minimumWidth = 0
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setBackgroundColor(Color.parseColor("#FFEBEE")); setTextColor(Color.parseColor("#C62828"))
                setOnClickListener {
                    AlertDialog.Builder(this@BooksActivity)
                        .setTitle("Delete '${book.name}'?")
                        .setMessage("All pages in this book will be deleted.")
                        .setPositiveButton("Delete") { _, _ -> book.deleteRecursively(); refresh() }
                        .setNegativeButton("Cancel", null).show()
                }
                bookRow.addView(this)
            }
            container.addView(bookRow)
        }

        val scroll = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this).setTitle("⚙ Settings").setView(scroll)
            .setPositiveButton("Done") { _, _ ->
                val selectedBook = bookNames.getOrElse(bookSpinner.selectedItemPosition) { "General" }
                prefs.edit()
                    .putBoolean("confirm_exit_clear", confirmCb.isChecked)
                    .putBoolean("autosave", autosaveCb.isChecked)
                    .putString("default_book", selectedBook)
                    .putString("default_paper", selPaper)
                    .apply()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun createBookDialog(onCreated: () -> Unit) {
        val input = EditText(this).apply { hint = "Book name" }
        AlertDialog.Builder(this).setTitle("New Book").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim().ifEmpty { "Book_${System.currentTimeMillis()}" }
                File(getBooksRoot(), n).mkdirs(); onCreated()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renameBookDialog(book: File, onDone: () -> Unit) {
        val input = EditText(this).apply { setText(book.name); selectAll() }
        AlertDialog.Builder(this).setTitle("Rename Book").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val n = input.text.toString().trim().ifEmpty { return@setPositiveButton }
                val dest = File(getBooksRoot(), n)
                if (!dest.exists()) book.renameTo(dest)
                onDone()
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
