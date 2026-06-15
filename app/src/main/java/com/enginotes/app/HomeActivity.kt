package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var pagesContainer: LinearLayout
    private lateinit var emptyView: TextView
    private var bookName: String = "General"
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private var pendingExportFile: File? = null
    private var pendingExportFormat: String = "pdf"

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val intent = Intent(this, PdfViewerActivity::class.java)
            intent.putExtra("pdf_uri", uri.toString())
            startActivity(intent)
        }
    }

    private val chartLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("chart_image_path")
            if (path != null) Toast.makeText(this, "Chart saved: $path", Toast.LENGTH_SHORT).show()
        }
    }

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@registerForActivityResult
        val file = pendingExportFile ?: return@registerForActivityResult
        try {
            val content = file.readText()
            // Create a simple PDF with text content
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val paint = Paint(); paint.textSize = 14f; paint.color = Color.BLACK
            val canvas = page.canvas
            canvas.drawText("=== ${file.nameWithoutExtension} ===", 40f, 60f, paint)
            var y = 100f; paint.textSize = 12f
            for (line in content.lines()) {
                if (line.startsWith("TEXT\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size > 7) {
                        val text = parts.last().replace("\u0002", "\n")
                        for (tl in text.lines()) {
                            canvas.drawText(tl.take(80), 40f, y, paint)
                            y += 20f
                            if (y > 800f) break
                        }
                    }
                }
                if (y > 800f) break
            }
            doc.finishPage(page); contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }; doc.close()
            Toast.makeText(this, "PDF saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private val saveImageLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        uri ?: return@registerForActivityResult
        Toast.makeText(this, "Open the note to export as image with full canvas rendering", Toast.LENGTH_LONG).show()
    }

    private val saveTxtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@registerForActivityResult
        val file = pendingExportFile ?: return@registerForActivityResult
        try {
            val content = file.readText(); val sb = StringBuilder()
            sb.append("=== ${file.nameWithoutExtension} ===\n\n")
            for (line in content.lines()) {
                if (line.startsWith("TEXT\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size > 7) sb.append(parts.last().replace("\u0002", "\n")).append("\n")
                }
            }
            contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            Toast.makeText(this, "TXT saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookName = intent.getStringExtra("book_name") ?: "General"

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#F5F5F5"))

        // Top bar
        val topBar = LinearLayout(this); topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(Color.parseColor("#FF6200EE"))
        topBar.setPadding(dp(4), dp(10), dp(4), dp(10))
        topBar.gravity = Gravity.CENTER_VERTICAL
        val topLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        topLp.gravity = Gravity.TOP
        root.addView(topBar, topLp)

        fun topBtn(emoji: String, action: () -> Unit) {
            val b = Button(this); b.text = emoji; b.textSize = 18f
            b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.TRANSPARENT)
            b.minWidth = 0; b.minimumWidth = 0; b.setPadding(dp(8), 0, dp(8), 0)
            b.setOnClickListener { action() }; topBar.addView(b)
        }

        topBtn("←") { finish() }

        val titleView = TextView(this); titleView.text = "📖 $bookName"; titleView.textSize = 20f
        titleView.setTextColor(Color.WHITE); titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topBar.addView(titleView)

        topBtn("🔍") { showSearchDialog() }
        topBtn("📄") { pickPdfLauncher.launch("application/pdf") }
        topBtn("📊") { chartLauncher.launch(Intent(this, ChartActivity::class.java)) }
        topBtn("⚙") { showSettingsDialog() }

        // Scroll
        val scroll = ScrollView(this)
        val scrollLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        scrollLp.topMargin = dp(56); scrollLp.bottomMargin = dp(80)
        root.addView(scroll, scrollLp)

        val content = LinearLayout(this); content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(16))
        scroll.addView(content)

        val sectionHeader = TextView(this); sectionHeader.text = "PAGES"
        sectionHeader.textSize = 11f; sectionHeader.setTextColor(Color.parseColor("#9E9E9E"))
        sectionHeader.typeface = Typeface.DEFAULT_BOLD; sectionHeader.setPadding(dp(4), 0, 0, dp(8))
        content.addView(sectionHeader)

        pagesContainer = LinearLayout(this); pagesContainer.orientation = LinearLayout.VERTICAL
        content.addView(pagesContainer)

        emptyView = TextView(this); emptyView.text = "No pages yet.\nTap + to create your first page!"
        emptyView.textSize = 16f; emptyView.setTextColor(Color.parseColor("#9E9E9E"))
        emptyView.gravity = Gravity.CENTER; emptyView.setPadding(0, dp(80), 0, 0)
        content.addView(emptyView)

        // FAB
        val fab = Button(this); fab.text = "+"; fab.textSize = 28f; fab.setTextColor(Color.WHITE)
        val fabLp = FrameLayout.LayoutParams(dp(60), dp(60))
        fabLp.gravity = Gravity.BOTTOM or Gravity.END
        fabLp.bottomMargin = dp(24); fabLp.rightMargin = dp(24)
        fab.setPadding(0, 0, 0, 0); fab.elevation = dp(6).toFloat()
        fab.post {
            fab.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#FF6200EE"))
            }
        }
        fab.setOnClickListener { createNewPage() }
        root.addView(fab, fabLp)

        setContentView(root)
        ensureBookFolder()
        refreshPages()
    }

    private fun ensureBookFolder() { getBookFolder().mkdirs() }
    private fun getBooksRoot(): File { val f = File(filesDir, "books"); if (!f.exists()) f.mkdirs(); return f }
    private fun getBookFolder(): File = File(getBooksRoot(), bookName)

    private fun refreshPages() {
        pagesContainer.removeAllViews()
        val pages = getBookFolder().listFiles()?.filter { it.extension == "eng" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        emptyView.visibility = if (pages.isEmpty()) View.VISIBLE else View.GONE

        for (file in pages) {
            val card = FrameLayout(this)
            card.setPadding(dp(16), dp(16), dp(16), dp(16))
            card.elevation = dp(2).toFloat()
            val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cardLp.setMargins(0, 0, 0, dp(12)); card.layoutParams = cardLp
            card.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(Color.WHITE); cornerRadius = dp(12).toFloat()
            }

            val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL

            val icon = TextView(this); icon.text = "📄"; icon.textSize = 28f
            icon.setPadding(0, 0, dp(16), 0); row.addView(icon)

            val info = LinearLayout(this); info.orientation = LinearLayout.VERTICAL
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val nameView = TextView(this); nameView.text = file.nameWithoutExtension; nameView.textSize = 17f
            nameView.setTextColor(Color.BLACK); nameView.typeface = Typeface.DEFAULT_BOLD; info.addView(nameView)

            val metaView = TextView(this)
            metaView.text = "Edited " + dateFormat.format(Date(file.lastModified()))
            metaView.textSize = 12f; metaView.setTextColor(Color.parseColor("#757575")); info.addView(metaView)

            row.addView(info)

            val arrow = TextView(this); arrow.text = "›"; arrow.textSize = 24f
            arrow.setTextColor(Color.parseColor("#BDBDBD")); row.addView(arrow)

            card.addView(row)
            card.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java)
                    .putExtra("filename", file.nameWithoutExtension)
                    .putExtra("book_name", bookName))
            }
            card.setOnLongClickListener { showPageOptions(file); true }
            pagesContainer.addView(card)
        }
    }

    private fun showPageOptions(file: File) {
        val options = arrayOf("✏️ Rename", "📋 Copy Page", "📦 Move to another book", "🗑️ Delete", "📤 Export")
        AlertDialog.Builder(this).setTitle(file.nameWithoutExtension)
            .setItems(options) { _, i ->
                when (i) {
                    0 -> showRenamePageDialog(file)
                    1 -> showCopyPageDialog(file)
                    2 -> showMovePageDialog(file)
                    3 -> confirmDeletePage(file)
                    4 -> showExportDialog(file)
                }
            }.show()
    }

    private fun showRenamePageDialog(file: File) {
        val input = EditText(this); input.setText(file.nameWithoutExtension); input.selectAll()
        AlertDialog.Builder(this).setTitle("Rename Page").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank() || newName == file.nameWithoutExtension) return@setPositiveButton
                val newFile = File(getBookFolder(), "$newName.eng")
                if (newFile.exists()) { Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                file.renameTo(newFile); refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showCopyPageDialog(file: File) {
        val input = EditText(this); input.setText("${file.nameWithoutExtension} (copy)")
        AlertDialog.Builder(this).setTitle("Copy Page").setView(input)
            .setPositiveButton("Copy") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { "${file.nameWithoutExtension} (copy)" }
                file.copyTo(File(getBookFolder(), "$newName.eng"), overwrite = true)
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show(); refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showMovePageDialog(file: File) {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != bookName } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other books", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Move to book...")
            .setItems(books.map { it.name }.toTypedArray()) { _, i ->
                file.copyTo(File(books[i], file.name), overwrite = true); file.delete()
                Toast.makeText(this, "Moved to ${books[i].name}", Toast.LENGTH_SHORT).show(); refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeletePage(file: File) {
        AlertDialog.Builder(this).setTitle("Delete '${file.nameWithoutExtension}'?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                file.delete(); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showExportDialog(file: File) {
        pendingExportFile = file
        val name = file.nameWithoutExtension
        val formats = arrayOf("📄 PDF", "🖼 JPG", "🖼 PNG", "🖼 BMP", "📝 TXT")
        AlertDialog.Builder(this).setTitle("Export '$name' as...")
            .setItems(formats) { _, i ->
                when (i) {
                    0 -> savePdfLauncher.launch("$name.pdf")
                    1 -> { pendingExportFormat = "jpg"; saveImageLauncher.launch("$name.jpg") }
                    2 -> { pendingExportFormat = "png"; saveImageLauncher.launch("$name.png") }
                    3 -> { pendingExportFormat = "bmp"; saveImageLauncher.launch("$name.png") }
                    4 -> saveTxtLauncher.launch("$name.txt")
                }
            }.show()
    }

    private fun createNewPage() {
        val input = EditText(this); input.hint = "Page name"
        AlertDialog.Builder(this).setTitle("📄 New Page").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Page_${System.currentTimeMillis()}" }
                startActivity(Intent(this, MainActivity::class.java)
                    .putExtra("filename", name).putExtra("book_name", bookName))
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSearchDialog() {
        val input = EditText(this); input.hint = "Search pages..."
        AlertDialog.Builder(this).setTitle("🔍 Search").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim().lowercase()
                if (query.isBlank()) { refreshPages(); return@setPositiveButton }
                pagesContainer.removeAllViews()
                val pages = getBookFolder().listFiles()?.filter {
                    it.extension == "eng" && it.nameWithoutExtension.lowercase().contains(query)
                } ?: emptyList()
                if (pages.isEmpty()) {
                    val tv = TextView(this); tv.text = "No pages found for \"$query\""
                    tv.textSize = 15f; tv.setTextColor(Color.GRAY)
                    tv.gravity = Gravity.CENTER; tv.setPadding(0, dp(40), 0, 0)
                    pagesContainer.addView(tv)
                } else {
                    emptyView.visibility = View.GONE
                    for (page in pages) {
                        val btn = Button(this); btn.text = page.nameWithoutExtension
                        btn.setOnClickListener {
                            startActivity(Intent(this, MainActivity::class.java)
                                .putExtra("filename", page.nameWithoutExtension)
                                .putExtra("book_name", bookName))
                        }
                        pagesContainer.addView(btn)
                    }
                }
            }.setNegativeButton("Cancel") { _, _ -> refreshPages() }.show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(8), dp(20), dp(8))

        fun header(text: String) {
            val tv = TextView(this); tv.text = text; tv.textSize = 11f
            tv.setTextColor(Color.parseColor("#7B61FF")); tv.setPadding(0, dp(10), 0, dp(2))
            tv.typeface = Typeface.DEFAULT_BOLD; container.addView(tv)
        }
        fun divider() {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            lp.setMargins(0, dp(8), 0, dp(4)); v.layoutParams = lp
            v.setBackgroundColor(Color.LTGRAY); container.addView(v)
        }

        header("GENERAL")
        val confirmCb = CheckBox(this); confirmCb.text = "Confirm before exit or clear canvas"
        confirmCb.isChecked = prefs.getBoolean("confirm_exit_clear", true); container.addView(confirmCb)

        val autosaveCb = CheckBox(this); autosaveCb.text = "Autosave notes"
        autosaveCb.isChecked = prefs.getBoolean("autosave", true); container.addView(autosaveCb)

        divider(); header("DEFAULT BOOK FOR NEW NOTES")
        val bookNames = getBooksRoot().listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        val currentDefault = prefs.getString("default_book", "General") ?: "General"
        val bookSpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bookNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bookSpinner.adapter = adapter
        bookSpinner.setSelection(bookNames.indexOf(currentDefault).coerceAtLeast(0))
        container.addView(bookSpinner)

        divider(); header("DRAWING")
        val arcDivPref = prefs.getInt("arc_divisions", 3)
        val arcRow = LinearLayout(this); arcRow.orientation = LinearLayout.HORIZONTAL
        arcRow.gravity = Gravity.CENTER_VERTICAL; arcRow.setPadding(0, dp(8), 0, dp(8))
        val arcLbl = TextView(this); arcLbl.text = "Arc divisions"; arcLbl.textSize = 15f
        arcLbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val arcInput = EditText(this); arcInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        arcInput.setText(arcDivPref.toString())
        arcInput.layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
        arcInput.gravity = Gravity.CENTER
        arcRow.addView(arcLbl); arcRow.addView(arcInput); container.addView(arcRow)

        divider(); header("PAPER")
        val paperTypes = arrayOf("Blank White", "Blank Coloured", "Lined", "Graph Grid", "Dot Grid", "Engineering Grid")
        val paperValues = arrayOf("BLANK", "BLANK_COLORED", "LINED", "GRID", "DOTS", "ENGINEERING")
        val currentPaper = prefs.getString("default_paper", "GRID") ?: "GRID"
        val paperLbl = TextView(this); paperLbl.textSize = 15f
        paperLbl.setTextColor(Color.parseColor("#1565C0")); paperLbl.setPadding(0, dp(10), 0, dp(10))
        var selectedPaper = currentPaper
        fun refreshPaperLbl() { paperLbl.text = "Default paper: ${paperTypes[paperValues.indexOf(selectedPaper).coerceAtLeast(0)]}  (tap)" }
        refreshPaperLbl()
        paperLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Default Paper Style").setItems(paperTypes) { _, i ->
                selectedPaper = paperValues[i]; refreshPaperLbl()
            }.show()
        }
        container.addView(paperLbl)

        val scroll = ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("⚙ Settings").setView(scroll)
            .setPositiveButton("Done") { _, _ ->
                prefs.edit()
                    .putBoolean("confirm_exit_clear", confirmCb.isChecked)
                    .putBoolean("autosave", autosaveCb.isChecked)
                    .putString("default_book", bookNames.getOrElse(bookSpinner.selectedItemPosition) { "General" })
                    .putInt("arc_divisions", (arcInput.text.toString().toIntOrNull() ?: 3).coerceIn(2, 12))
                    .putString("default_paper", selectedPaper)
                    .apply()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); refreshPages() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
