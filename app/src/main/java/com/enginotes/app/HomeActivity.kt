package com.enginotes.app

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

class HomeActivity : AppCompatActivity() {

    private lateinit var pagesContainer: LinearLayout
    private lateinit var emptyView: TextView
    private var bookName: String = "General"
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookName = intent.getStringExtra("book_name") ?: "General"

        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

        // Top bar
        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(android.graphics.Color.parseColor("#FF6200EE"))
        topBar.setPadding(dp(4), dp(10), dp(16), dp(10))
        topBar.gravity = Gravity.CENTER_VERTICAL
        val topLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        topLp.gravity = Gravity.TOP
        root.addView(topBar, topLp)

        // Back button
        val backBtn = Button(this)
        backBtn.text = "←"
        backBtn.textSize = 20f
        backBtn.setTextColor(android.graphics.Color.WHITE)
        backBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        backBtn.minWidth = 0; backBtn.minimumWidth = 0
        backBtn.setPadding(dp(8), 0, dp(8), 0)
        backBtn.setOnClickListener { finish() }
        topBar.addView(backBtn)

        val titleView = TextView(this)
        titleView.text = "📖 $bookName"
        titleView.textSize = 20f
        titleView.setTextColor(android.graphics.Color.WHITE)
        titleView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        titleView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        topBar.addView(titleView)

        val searchBtn = Button(this)
        searchBtn.text = "🔍"
        searchBtn.textSize = 18f
        searchBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        searchBtn.setTextColor(android.graphics.Color.WHITE)
        searchBtn.minWidth = 0; searchBtn.minimumWidth = 0
        searchBtn.setPadding(dp(8), 0, dp(8), 0)
        searchBtn.setOnClickListener { showSearchDialog() }
        topBar.addView(searchBtn)

        // Scroll
        val scroll = ScrollView(this)
        val scrollLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        scrollLp.topMargin = dp(56); scrollLp.bottomMargin = dp(80)
        root.addView(scroll, scrollLp)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(16))
        scroll.addView(content)

        val sectionHeader = TextView(this)
        sectionHeader.text = "PAGES"
        sectionHeader.textSize = 11f
        sectionHeader.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        sectionHeader.typeface = android.graphics.Typeface.DEFAULT_BOLD
        sectionHeader.setPadding(dp(4), 0, 0, dp(8))
        content.addView(sectionHeader)

        pagesContainer = LinearLayout(this)
        pagesContainer.orientation = LinearLayout.VERTICAL
        content.addView(pagesContainer)

        emptyView = TextView(this)
        emptyView.text = "No pages yet.\nTap + to create your first page!"
        emptyView.textSize = 16f
        emptyView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, dp(80), 0, 0)
        content.addView(emptyView)

        // FAB
        val fab = Button(this)
        fab.text = "+"
        fab.textSize = 28f
        fab.setTextColor(android.graphics.Color.WHITE)
        val fabLp = FrameLayout.LayoutParams(dp(60), dp(60))
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
        fab.setOnClickListener { createNewPage() }
        root.addView(fab, fabLp)

        setContentView(root)
        ensureBookFolder()
        refreshPages()
    }

    private fun ensureBookFolder() {
        getBookFolder().mkdirs()
    }

    private fun getBooksRoot(): File {
        val f = File(filesDir, "books"); if (!f.exists()) f.mkdirs(); return f
    }

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
            cardLp.setMargins(0, 0, 0, dp(12))
            card.layoutParams = cardLp
            card.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            // Page icon
            val icon = TextView(this)
            icon.text = "📄"
            icon.textSize = 28f
            icon.setPadding(0, 0, dp(16), 0)
            row.addView(icon)

            val info = LinearLayout(this)
            info.orientation = LinearLayout.VERTICAL
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val nameView = TextView(this)
            nameView.text = file.nameWithoutExtension
            nameView.textSize = 17f
            nameView.setTextColor(android.graphics.Color.BLACK)
            nameView.typeface = android.graphics.Typeface.DEFAULT_BOLD
            info.addView(nameView)

            val metaView = TextView(this)
            metaView.text = "Edited " + dateFormat.format(Date(file.lastModified()))
            metaView.textSize = 12f
            metaView.setTextColor(android.graphics.Color.parseColor("#757575"))
            info.addView(metaView)

            row.addView(info)

            val arrow = TextView(this)
            arrow.text = "›"
            arrow.textSize = 24f
            arrow.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
            row.addView(arrow)

            card.addView(row)

            card.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("filename", file.nameWithoutExtension)
                intent.putExtra("book_name", bookName)
                startActivity(intent)
            }

            card.setOnLongClickListener {
                showPageOptions(file)
                true
            }

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
                if (newFile.exists()) { Toast.makeText(this, "A page with that name already exists", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                file.renameTo(newFile)
                refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showCopyPageDialog(file: File) {
        val input = EditText(this); input.setText("${file.nameWithoutExtension} (copy)")
        AlertDialog.Builder(this).setTitle("Copy Page").setView(input)
            .setPositiveButton("Copy") { _, _ ->
                val newName = input.text.toString().trim().ifEmpty { "${file.nameWithoutExtension} (copy)" }
                val newFile = File(getBookFolder(), "$newName.eng")
                file.copyTo(newFile, overwrite = true)
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
                refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showMovePageDialog(file: File) {
        val books = getBooksRoot().listFiles()?.filter { it.isDirectory && it.name != bookName } ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other books to move to", Toast.LENGTH_SHORT).show(); return }
        val names = books.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Move to book...")
            .setItems(names) { _, i ->
                val target = File(books[i], file.name)
                file.copyTo(target, overwrite = true)
                file.delete()
                Toast.makeText(this, "Moved to ${books[i].name}", Toast.LENGTH_SHORT).show()
                refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeletePage(file: File) {
        AlertDialog.Builder(this).setTitle("Delete '${file.nameWithoutExtension}'?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                file.delete()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                refreshPages()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showExportDialog(file: File) {
        val formats = arrayOf("📄 PDF", "🖼️ JPG", "🖼️ PNG", "🖼️ BMP", "📝 TXT")
        AlertDialog.Builder(this).setTitle("Export '${file.nameWithoutExtension}' as...")
            .setItems(formats) { _, i ->
                when (i) {
                    0 -> Toast.makeText(this, "Open the note and use ⋮ → Export as PDF", Toast.LENGTH_LONG).show()
                    1 -> Toast.makeText(this, "Open the note and use ⋮ → Export as Image", Toast.LENGTH_LONG).show()
                    2 -> Toast.makeText(this, "Open the note and use ⋮ → Export as Image", Toast.LENGTH_LONG).show()
                    3 -> Toast.makeText(this, "Open the note and use ⋮ → Export as Image", Toast.LENGTH_LONG).show()
                    4 -> exportAsTxt(file)
                }
            }.show()
    }

    private fun exportAsTxt(file: File) {
        try {
            val content = file.readText()
            val lines = content.lines()
            val sb = StringBuilder()
            sb.append("=== ${file.nameWithoutExtension} ===\n\n")
            for (line in lines) {
                if (line.startsWith("TEXT\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size > 7) sb.append(parts.last().replace("\u0002", "\n")).append("\n")
                }
            }
            val outFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "${file.nameWithoutExtension}.txt")
            outFile.writeText(sb.toString())
            Toast.makeText(this, "Saved to ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNewPage() {
        val input = EditText(this); input.hint = "Page name"
        AlertDialog.Builder(this).setTitle("📄 New Page").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Page_${System.currentTimeMillis()}" }
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("filename", name)
                intent.putExtra("book_name", bookName)
                startActivity(intent)
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
                    tv.textSize = 15f; tv.setTextColor(android.graphics.Color.GRAY)
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

    override fun onResume() { super.onResume(); refreshPages() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
