package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var canvasContainer: FrameLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvActiveTool: TextView

    private var currentFileName: String? = null
    private var lastSavedContent: String = ""
    private val PT_TO_PX = 1.333f

    private val shapeSymbols = listOf("╱ Line", "▭ Rectangle", "▢ Rounded Rect", "○ Circle", "⬭ Ellipse", "△ Triangle", "◇ Diamond", "➔ Arrow", "★ Star", "⬠ Pentagon", "⬡ Hexagon", "〜 Curve", "✛ Cross")
    private val shapeTools = listOf(Tool.LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE, Tool.TRIANGLE, Tool.DIAMOND, Tool.ARROW, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CURVE, Tool.CROSS)

    private var activeEditText: EditText? = null
    private var activeToolbar: LinearLayout? = null
    private var editingItem: TextItem? = null
    private var editWorldX = 0f
    private var editWorldY = 0f
    private var editRotation = 0f
    private var editColor = Color.BLACK
    private var editSize = 12f * 1.333f
    private var pendingBold = false
    private var pendingItalic = false
    private var pendingUnderline = false
    private var pendingHighlight: Int? = null
    private var cameraImageFile: File? = null
    private var activeToolbarButton: Button? = null
    private var isSwitchingTextEditor = false
    private var exportWindowBitmap: Bitmap? = null
    private var pendingExportBitmap: Bitmap? = null
    private var pendingExportFormat: String = "png"

    private var activeCellEditText: EditText? = null
    private var activeCellToolbar: LinearLayout? = null
    private var tableToolbarOverlay: LinearLayout? = null

    // ---------- Launchers ----------

    private val chartLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("chart_image_path")
            if (path != null) {
                val bmp = android.graphics.BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val screenW = drawingView.width / drawingView.getScaleFactor()
                    val screenH = drawingView.height / drawingView.getScaleFactor()
                    val w = screenW * 0.85f
                    val h = w / ratio
                    drawingView.addImage(path, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
                    drawingView.addImage(path, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
                    Toast.makeText(this, "Chart added to note!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            startActivity(android.content.Intent(this, PdfViewerActivity::class.java).putExtra("pdf_uri", uri.toString()))
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) insertImage(uri)
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraImageFile?.let { addImageFromFile(it) }
    }

    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val bmp = pendingExportBitmap ?: return@registerForActivityResult
            val maxDim = 3000
            val scale = if (bmp.width > maxDim || bmp.height > maxDim)
                minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height) else 1f
            val pw = (bmp.width * scale).toInt().coerceAtLeast(1)
            val ph = (bmp.height * scale).toInt().coerceAtLeast(1)
            val scaledBmp = if (scale < 1f) Bitmap.createScaledBitmap(bmp, pw, ph, true) else bmp
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, 1).create()
            val page = doc.startPage(pageInfo)
            page.canvas.drawBitmap(scaledBmp, 0f, 0f, Paint())
            doc.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            doc.close()
            Toast.makeText(this, "PDF saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally { pendingExportBitmap = null }
    }

    private val saveImageLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val bmp = pendingExportBitmap ?: return@registerForActivityResult
            when (pendingExportFormat) {
                "bmp" -> {
                    contentResolver.openOutputStream(uri)?.use { fos ->
                        val width = bmp.width; val height = bmp.height
                        val pixels = IntArray(width * height)
                        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
                        val rowSize = (width * 3 + 3) / 4 * 4
                        val pixelArraySize = rowSize * height
                        val fileSize = 54 + pixelArraySize
                        fun writeInt(v: Int) = fos.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
                        fun writeShort(v: Int) = fos.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
                        fos.write('B'.code); fos.write('M'.code)
                        writeInt(fileSize); writeInt(0); writeInt(54)
                        writeInt(40); writeInt(width); writeInt(height)
                        writeShort(1); writeShort(24); writeInt(0); writeInt(pixelArraySize)
                        writeInt(2835); writeInt(2835); writeInt(0); writeInt(0)
                        val row = ByteArray(rowSize)
                        for (y in height - 1 downTo 0) {
                            for (x in 0 until width) {
                                val p = pixels[y * width + x]
                                row[x * 3] = (p and 0xFF).toByte()
                                row[x * 3 + 1] = ((p shr 8) and 0xFF).toByte()
                                row[x * 3 + 2] = ((p shr 16) and 0xFF).toByte()
                            }
                            fos.write(row)
                        }
                    }
                }
                "jpg" -> contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                else -> contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Toast.makeText(this, "Image saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Image failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally { pendingExportBitmap = null }
    }

    private val saveTxtLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val engFile = currentFileName?.let { File(getDrawingsFolder(), "$it.eng") }
                ?: run { Toast.makeText(this, "Save the note first!", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
            val sb = StringBuilder()
            sb.append("=== ${currentFileName} ===\n\n")
            for (line in engFile.readLines()) {
                if (line.startsWith("TEXT\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size > 7) sb.append(parts.last().replace("\u0002", "\n")).append("\n")
                }
            }
            contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            Toast.makeText(this, "TXT saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "TXT failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val saveDocxLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val engFile = currentFileName?.let { File(getDrawingsFolder(), "$it.eng") }
                ?: run { Toast.makeText(this, "Save the note first!", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
            val doc = XWPFDocument()
            val titleRun = doc.createParagraph().createRun()
            titleRun.setText(currentFileName ?: "EngiNote"); titleRun.isBold = true; titleRun.fontSize = 18
            for (line in engFile.readLines()) {
                if (line.startsWith("TEXT\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size > 7) {
                        val run = doc.createParagraph().createRun()
                        run.setText(parts.last().replace("\u0002", "\n"))
                        try { run.fontSize = (parts[4].toFloat() / PT_TO_PX).toInt().coerceIn(8, 72) } catch (e: Exception) {}
                    }
                }
            }
            val bmp = drawingView.exportBitmap()
            val imgFile = File(cacheDir, "export_tmp.png")
            FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            val imgRun = doc.createParagraph().createRun()
            imgRun.addPicture(imgFile.inputStream(), org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                "canvas.png", org.apache.poi.util.Units.toEMU(400.0), org.apache.poi.util.Units.toEMU(300.0))
            contentResolver.openOutputStream(uri)?.use { doc.write(it) }
            doc.close()
            Toast.makeText(this, "DOCX saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "DOCX failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        canvasContainer = findViewById(R.id.canvasContainer)
        tvTitle = findViewById(R.id.tvTitle)

        val fileName = intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName
            tvTitle.text = fileName
            val file = File(getDrawingsFolder(), "$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else {
            tvTitle.text = "New Note"
        }
        drawingView.migrateOldNotes(filesDir)
        lastSavedContent = drawingView.serialize()
        drawingView.arcDivisions = getPrefs().getInt("arc_divisions", 3)

        tvActiveTool = TextView(this)
        tvActiveTool.textSize = 9f
        tvActiveTool.setTextColor(Color.parseColor("#CCFFFFFF"))
        tvActiveTool.setBackgroundColor(Color.parseColor("#55000000"))
        tvActiveTool.setPadding(dp(3), 0, dp(3), dp(1))
        tvActiveTool.text = "Select"
        val ip = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        ip.gravity = Gravity.TOP or Gravity.END
        ip.topMargin = dp(28); ip.rightMargin = dp(4)
        canvasContainer.addView(tvActiveTool, ip)

        drawingView.onTextEditRequest = { item, screenX, screenY, worldX, worldY ->
            showInlineTextEditor(item, screenX, screenY, worldX, worldY)
        }

        drawingView.onTableCellEditRequest = { table, row, col, screenX, screenY ->
            showTableCellEditor(table, row, col, screenX, screenY)
        }

        drawingView.onExportWindowSelected = { left, top, right, bottom ->
            exportWindowBitmap = drawingView.exportWindow(left, top, right, bottom)
            showExportWindowDialog()
        }

        for (id in listOf(R.id.btnBack, R.id.btnMenu, R.id.btnText, R.id.btnDraw, R.id.btnTools, R.id.btnInsert, R.id.btnUndo, R.id.btnRedo)) {
            val btn = findViewById<Button>(id)
            btn.setBackgroundResource(R.drawable.top_button_selector)
            btn.setTextColor(Color.BLACK)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { confirmThenExit() }

        findViewById<Button>(R.id.btnText).setOnClickListener {
            closeInlineEditor(commit = true)
            setActiveTool(it as Button, Tool.TEXT, "Text")
        }

        findViewById<Button>(R.id.btnDraw).setOnClickListener { btn ->
            closeInlineEditor(commit = true)
            val options = listOf("👆 Select", "🪣 Fill", "✏ Pen", "⌇ Arc", "🔲 AutoSelect") + shapeSymbols
            AlertDialog.Builder(this).setTitle("Draw")
                .setItems(options.toTypedArray()) { _, i ->
                    when (i) {
                        0 -> setActiveTool(btn as Button, Tool.SELECT, "Select")
                        1 -> { showColorGridDialog { c -> drawingView.fillColor = c }; setActiveTool(btn as Button, Tool.FILL, "Fill") }
                        2 -> { setActiveTool(btn as Button, Tool.PEN, "Pen"); collapseToolbar() }
                        3 -> setActiveTool(btn as Button, Tool.ARC, "Arc")
                        4 -> { showAutoSelectModeDialog(); setActiveToolbarBtn(btn as Button) }
                        else -> setActiveTool(btn as Button, shapeTools[i - 5], shapeSymbols[i - 5].take(2))
                    }
                }.show()
        }

        findViewById<Button>(R.id.btnTools).setOnClickListener { btn ->
            closeInlineEditor(commit = true)
            AlertDialog.Builder(this).setTitle("Tools")
                .setItems(arrayOf("🧹 Eraser", "🎨 Color", "🪣 Fill Shapes", "📏 Size")) { _, i ->
                    when (i) {
                        0 -> if (drawingView.currentTool == Tool.ERASER) {
                            drawingView.eraserMode = if (drawingView.eraserMode == EraserMode.OBJECT) EraserMode.AREA else EraserMode.OBJECT
                            Toast.makeText(this, if (drawingView.eraserMode == EraserMode.OBJECT) "Object Eraser" else "Area Eraser", Toast.LENGTH_SHORT).show()
                        } else setActiveTool(btn as Button, Tool.ERASER, "Eraser")
                        1 -> showColorGridDialog { c -> drawingView.currentColor = c; if (drawingView.currentTool == Tool.ERASER) drawingView.currentTool = Tool.PEN }
                        2 -> { drawingView.fillShapes = !drawingView.fillShapes; Toast.makeText(this, if (drawingView.fillShapes) "Fill: On" else "Fill: Off", Toast.LENGTH_SHORT).show() }
                        3 -> showSizePicker()
                    }
                }.show()
        }

        findViewById<Button>(R.id.btnInsert).setOnClickListener {
            closeInlineEditor(commit = true)
            AlertDialog.Builder(this).setTitle("Insert")
                .setItems(arrayOf("🖼 Image from Gallery", "📷 Take Photo", "⊞ Table")) { _, i ->
                    when (i) {
                        0 -> pickImageLauncher.launch("image/*")
                        1 -> launchCamera()
                        2 -> showTableInsertDialog()
                    }
                }.show()
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener { closeInlineEditor(commit = true); drawingView.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { closeInlineEditor(commit = true); drawingView.redo() }

        findViewById<Button>(R.id.btnExpand).setOnClickListener { expandToolbar() }
        findViewById<Button>(R.id.btnQuickColor).setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c } }
        findViewById<Button>(R.id.btnQuickSize).setOnClickListener { showSizePicker() }
        findViewById<Button>(R.id.btnQuickEraser).setOnClickListener { setActiveTool(null, Tool.ERASER, "Eraser") }
        findViewById<Button>(R.id.btnQuickFill)?.setOnClickListener {
            showColorGridDialog { c -> drawingView.fillColor = c }
            setActiveTool(null, Tool.FILL, "Fill")
        }

        findViewById<Button>(R.id.btnMenu).setOnClickListener { anchor ->
            closeInlineEditor(commit = true)
            val popup = PopupMenu(this, anchor)
            listOf("Save", "Save As", "Export", "Export Window", "Clear Canvas").forEach { popup.menu.add(it) }
            if (currentFileName != null) popup.menu.add("Delete This Note")
            listOf("📄 Open PDF", "📊 Chart Builder", "✍ Handwriting to Text", "⚙ Settings", "Exit").forEach { popup.menu.add(it) }
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Save" -> saveCurrent()
                    "Save As" -> saveAsNew()
                    "Export" -> showExportDialog()
                    "Export Window" -> {
                        if (drawingView.canvasMode != CanvasMode.INFINITE) {
                            Toast.makeText(this, "Window export is for Infinite Canvas only", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Draw a rectangle to select export area", Toast.LENGTH_SHORT).show()
                            setActiveTool(null, Tool.EXPORT_WINDOW, "ExportWin")
                        }
                    }
                    "Clear Canvas" -> confirmThenClear()
                    "Delete This Note" -> deleteCurrentNote()
                    "📄 Open PDF" -> pickPdfLauncher.launch("application/pdf")
                    "📊 Chart Builder" -> chartLauncher.launch(android.content.Intent(this, ChartActivity::class.java))
                    "✍ Handwriting to Text" -> startActivity(android.content.Intent(this, HandwritingActivity::class.java))
                    "⚙ Settings" -> showSettingsDialog()
                    "Exit" -> confirmThenExit()
                }
                true
            }
            popup.show()
        }

        setActiveTool(null, Tool.SELECT, "Select")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activeEditText != null -> closeInlineEditor(commit = true)
                    tableToolbarOverlay != null -> {
                        tableToolbarOverlay?.let { canvasContainer.removeView(it) }
                        tableToolbarOverlay = null
                    }
                    drawingView.currentTool != Tool.SELECT -> setActiveTool(null, Tool.SELECT, "Select")
                    else -> confirmThenExit()
                }
            }
        })
    }

    private fun setActiveTool(btn: Button?, tool: Tool, label: String) {
        drawingView.currentTool = tool
        tvActiveTool.text = label
        setActiveToolbarBtn(btn)
    }

    private fun setActiveToolbarBtn(btn: Button?) {
        activeToolbarButton?.isSelected = false
        activeToolbarButton = btn
        btn?.isSelected = true
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun collapseToolbar() {
        findViewById<View>(R.id.toolbarScroll).visibility = View.GONE
        findViewById<View>(R.id.collapsedBar).visibility = View.VISIBLE
    }

    private fun expandToolbar() {
        findViewById<View>(R.id.toolbarScroll).visibility = View.VISIBLE
        findViewById<View>(R.id.collapsedBar).visibility = View.GONE
    }

    private fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    // ---------- Export ----------

    private fun showExportWindowDialog() {
        val bmp = exportWindowBitmap ?: return
        val name = (currentFileName ?: "EngiNote_${System.currentTimeMillis()}").replace(" ", "_")
        val formats = arrayOf("📄 PDF", "🖼 JPG", "🖼 PNG", "🖼 BMP")
        AlertDialog.Builder(this).setTitle("Export Selection as...")
            .setItems(formats) { _, i ->
                pendingExportBitmap = bmp
                when (i) {
                    0 -> savePdfLauncher.launch("${name}_window.pdf")
                    1 -> { pendingExportFormat = "jpg"; saveImageLauncher.launch("${name}_window.jpg") }
                    2 -> { pendingExportFormat = "png"; saveImageLauncher.launch("${name}_window.png") }
                    3 -> { pendingExportFormat = "bmp"; saveImageLauncher.launch("${name}_window.bmp") }
                }
            }
            .setNegativeButton("Cancel") { _, _ -> exportWindowBitmap = null }
            .show()
    }

    private fun showExportDialog() {
        val name = (currentFileName ?: "EngiNote_${System.currentTimeMillis()}").replace(" ", "_")
        val formats = arrayOf("📄 PDF", "🖼 JPG", "🖼 PNG", "🖼 BMP", "📝 TXT", "📝 DOCX")
        AlertDialog.Builder(this).setTitle("Export as...")
            .setItems(formats) { _, i ->
                pendingExportBitmap = drawingView.exportBitmap()
                when (i) {
                    0 -> savePdfLauncher.launch("$name.pdf")
                    1 -> { pendingExportFormat = "jpg"; saveImageLauncher.launch("$name.jpg") }
                    2 -> { pendingExportFormat = "png"; saveImageLauncher.launch("$name.png") }
                    3 -> { pendingExportFormat = "bmp"; saveImageLauncher.launch("$name.bmp") }
                    4 -> saveTxtLauncher.launch("$name.txt")
                    5 -> saveDocxLauncher.launch("$name.docx")
                }
            }.show()
    }

    private fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
            intent.type = mimeType
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(android.content.Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Settings ----------

    private fun showSettingsDialog() {
        val prefs = getPrefs()
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(8), dp(20), dp(8))

        fun divider() {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            lp.setMargins(0, dp(10), 0, dp(4)); v.layoutParams = lp
            v.setBackgroundColor(Color.LTGRAY); container.addView(v)
        }

        fun header(text: String) {
            val tv = TextView(this); tv.text = text; tv.textSize = 11f
            tv.setTextColor(Color.parseColor("#7B61FF"))
            tv.setPadding(0, dp(10), 0, dp(2))
            tv.typeface = Typeface.DEFAULT_BOLD; container.addView(tv)
        }

        header("GENERAL")
        val confirmPref = prefs.getBoolean("confirm_exit_clear", true)
        val checkbox = CheckBox(this)
        checkbox.text = "Confirm before exit or clear canvas"
        checkbox.isChecked = confirmPref
        container.addView(checkbox)

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

        val eraserTypes = arrayOf("Object Eraser (whole strokes)", "Area Eraser (partial strokes)")
        var selectedEraserIdx = if (drawingView.eraserMode == EraserMode.OBJECT) 0 else 1
        val eraserLbl = TextView(this); eraserLbl.textSize = 15f
        eraserLbl.setTextColor(Color.parseColor("#1565C0"))
        eraserLbl.setPadding(0, dp(10), 0, dp(10))
        fun refreshEraserLbl() { eraserLbl.text = "Eraser: ${eraserTypes[selectedEraserIdx].substringBefore(" (")}  (tap)" }
        refreshEraserLbl()
        eraserLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Eraser Type").setItems(eraserTypes) { _, i ->
                selectedEraserIdx = i; refreshEraserLbl()
            }.show()
        }
        container.addView(eraserLbl)

        divider(); header("PAPER")

        val paperTypes = arrayOf("Blank White", "Blank Coloured", "Lined", "Graph Grid", "Dot Grid", "Engineering Grid")
        val paperValues = arrayOf(PaperType.BLANK, PaperType.BLANK_COLORED, PaperType.LINED, PaperType.GRID, PaperType.DOTS, PaperType.ENGINEERING)
        val paperLbl = TextView(this); paperLbl.textSize = 15f
        paperLbl.setTextColor(Color.parseColor("#1565C0"))
        paperLbl.setPadding(0, dp(10), 0, dp(10))
        fun refreshPaperLbl() { paperLbl.text = "Style: ${paperTypes[paperValues.indexOf(drawingView.paperType).coerceAtLeast(0)]}  (tap)" }
        refreshPaperLbl()
        paperLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Paper Style").setItems(paperTypes) { _, i ->
                if (paperValues[i] == PaperType.BLANK_COLORED) {
                    showColorGridDialog { c -> drawingView.paperColor = c; drawingView.paperType = PaperType.BLANK_COLORED; drawingView.invalidate(); refreshPaperLbl() }
                } else { drawingView.paperType = paperValues[i]; drawingView.invalidate(); refreshPaperLbl() }
            }.show()
        }
        container.addView(paperLbl)

        divider(); header("PAGE SETUP")

        val modeLbl = TextView(this); val sizeLbl = TextView(this); val orientLbl = TextView(this)
        fun refreshPageLbls() {
            modeLbl.text = "Canvas: ${when (drawingView.canvasMode) { CanvasMode.INFINITE -> "Infinite"; CanvasMode.FIXED -> "Fixed Page"; else -> "Paginated" }}  (tap)"
            sizeLbl.text = "Size: ${drawingView.paperSize.name}  (tap)"
            orientLbl.text = "Orientation: ${if (drawingView.pageOrientation == Orientation.PORTRAIT) "Portrait" else "Landscape"}  (tap)"
            val showPageOptions = drawingView.canvasMode != CanvasMode.INFINITE
            sizeLbl.visibility = if (showPageOptions) View.VISIBLE else View.GONE
            orientLbl.visibility = if (showPageOptions) View.VISIBLE else View.GONE
        }
        refreshPageLbls()
        for (lbl in listOf(modeLbl, sizeLbl, orientLbl)) {
            lbl.textSize = 15f; lbl.setTextColor(Color.parseColor("#1565C0"))
            lbl.setPadding(0, dp(8), 0, dp(8)); container.addView(lbl)
        }
        modeLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Canvas Mode")
                .setItems(arrayOf("Infinite Canvas", "Fixed Page", "Paginated")) { _, i ->
                    drawingView.canvasMode = when (i) { 0 -> CanvasMode.INFINITE; 1 -> CanvasMode.FIXED; else -> CanvasMode.PAGINATED }
                    drawingView.invalidate(); refreshPageLbls()
                }.show()
        }
        sizeLbl.setOnClickListener {
            val sizes = PaperSizeOption.values()
            AlertDialog.Builder(this).setTitle("Paper Size")
                .setItems(sizes.map { it.name }.toTypedArray()) { _, i ->
                    drawingView.paperSize = sizes[i]; drawingView.invalidate(); refreshPageLbls()
                }.show()
        }
        orientLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Orientation")
                .setItems(arrayOf("Portrait", "Landscape")) { _, i ->
                    drawingView.pageOrientation = if (i == 0) Orientation.PORTRAIT else Orientation.LANDSCAPE
                    drawingView.invalidate(); refreshPageLbls()
                }.show()
        }

        val scroll = ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("⚙ Settings").setView(scroll)
            .setPositiveButton("Done") { _, _ ->
                prefs.edit().putBoolean("confirm_exit_clear", checkbox.isChecked).apply()
                val n = (arcInput.text.toString().toIntOrNull() ?: 3).coerceIn(2, 12)
                prefs.edit().putInt("arc_divisions", n).apply()
                drawingView.arcDivisions = n
                drawingView.eraserMode = if (selectedEraserIdx == 0) EraserMode.OBJECT else EraserMode.AREA
            }.show()
    }

    private fun confirmThenExit() {
        closeInlineEditor(commit = true)
        val changed = drawingView.serialize() != lastSavedContent && drawingView.hasContent()
        if (!changed) { finish(); return }
        if (getPrefs().getBoolean("autosave", true)) { autoSave(); finish(); return }
        if (getPrefs().getBoolean("confirm_exit_clear", true)) {
            AlertDialog.Builder(this).setTitle("Unsaved Changes")
                .setMessage("Save before leaving?")
                .setPositiveButton("Save") { _, _ -> saveCurrent(); finish() }
                .setNeutralButton("Don't Save") { _, _ -> finish() }
                .setNegativeButton("Cancel", null).show()
        } else { autoSave(); finish() }
    }

    private fun confirmThenClear() {
        if (getPrefs().getBoolean("confirm_exit_clear", true) && drawingView.hasContent()) {
            AlertDialog.Builder(this).setTitle("Clear Canvas?").setMessage("This will remove everything.")
                .setPositiveButton("Clear") { _, _ -> drawingView.clearAll() }.setNegativeButton("Cancel", null).show()
        } else drawingView.clearAll()
    }

    private fun showColorGridDialog(onPicked: (Int) -> Unit) {
        val grid = GridLayout(this); grid.columnCount = 10
        grid.setPadding(dp(10), dp(10), dp(10), dp(10))
        lateinit var dialog: AlertDialog
        var previewPopup: android.widget.PopupWindow? = null

        fun addSwatch(color: Int) {
            val swatch = View(this)
            val p = GridLayout.LayoutParams()
            p.width = dp(28); p.height = dp(28); p.setMargins(dp(2), dp(2), dp(2), dp(2))
            swatch.layoutParams = p; swatch.setBackgroundColor(color)
            swatch.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        val prev = View(this); prev.setBackgroundColor(color)
                        val pw = android.widget.PopupWindow(prev, dp(40), dp(40))
                        pw.showAsDropDown(v, 0, -dp(70)); previewPopup = pw
                    }
                    android.view.MotionEvent.ACTION_UP -> { previewPopup?.dismiss(); previewPopup = null; onPicked(color); dialog.dismiss() }
                    android.view.MotionEvent.ACTION_CANCEL -> { previewPopup?.dismiss(); previewPopup = null }
                }
                true
            }
            grid.addView(swatch)
        }

        for (i in 0..9) addSwatch(Color.HSVToColor(floatArrayOf(0f, 0f, 1f - i / 9f)))
        for (value in listOf(1.0f, 0.85f, 0.7f, 0.5f, 0.3f))
            for (hue in listOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330))
                addSwatch(Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.65f, value)))

        val scroll = ScrollView(this); scroll.addView(grid)
        dialog = AlertDialog.Builder(this).setTitle("Color").setView(scroll).create()
        dialog.show()
    }

    private fun showSizePicker() {
        val tool = drawingView.currentTool
        val current: Float; val maxSize: Int; val label: String
        when (tool) {
            Tool.ERASER -> { current = drawingView.eraserSize; maxSize = 200; label = "Eraser Size" }
            Tool.TEXT -> { current = drawingView.defaultTextSize / PT_TO_PX; maxSize = 144; label = "Font Size (pt)" }
            else -> { current = drawingView.currentStrokeWidth; maxSize = 100; label = "Stroke Width" }
        }
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(50, 30, 50, 10)
        val tv = TextView(this); tv.text = "$label: ${current.toInt()}"; tv.textSize = 16f
        container.addView(tv)
        val seekBar = SeekBar(this); seekBar.max = maxSize
        seekBar.progress = current.toInt().coerceIn(1, maxSize)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                val v = value.coerceAtLeast(1); tv.text = "$label: $v"
                when (tool) {
                    Tool.ERASER -> drawingView.eraserSize = v.toFloat()
                    Tool.TEXT -> drawingView.defaultTextSize = v * PT_TO_PX
                    else -> drawingView.currentStrokeWidth = v.toFloat()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(seekBar)
        AlertDialog.Builder(this).setTitle(label).setView(container).setPositiveButton("Done", null).show()
    }

    private fun showAutoSelectModeDialog() {
        val modes = arrayOf("▭ Rectangle – Whole", "▭ Rectangle – Divided", "✏ Freeform – Whole", "✏ Freeform – Divided")
        AlertDialog.Builder(this).setTitle("AutoSelect Mode").setItems(modes) { _, i ->
            when (i) {
                0 -> { drawingView.autoSelectShape = AutoSelectShape.RECTANGLE; drawingView.autoSelectDivide = AutoSelectDivide.WHOLE }
                1 -> { drawingView.autoSelectShape = AutoSelectShape.RECTANGLE; drawingView.autoSelectDivide = AutoSelectDivide.DIVIDED }
                2 -> { drawingView.autoSelectShape = AutoSelectShape.FREEFORM; drawingView.autoSelectDivide = AutoSelectDivide.WHOLE }
                else -> { drawingView.autoSelectShape = AutoSelectShape.FREEFORM; drawingView.autoSelectDivide = AutoSelectDivide.DIVIDED }
            }
            drawingView.currentTool = Tool.AUTOSELECT
            tvActiveTool.text = "AutoSel"
            Toast.makeText(this, "Draw a region to select", Toast.LENGTH_SHORT).show()
        }.show()
    }

    // ---------- Table ----------

    private fun showTableInsertDialog() {
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(20), dp(20), dp(10))
        val rowLabel = TextView(this); rowLabel.text = "Rows:"; rowLabel.textSize = 15f; container.addView(rowLabel)
        val rowInput = EditText(this); rowInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER; rowInput.setText("3"); container.addView(rowInput)
        val colLabel = TextView(this); colLabel.text = "Columns:"; colLabel.textSize = 15f; colLabel.setPadding(0, dp(12), 0, 0); container.addView(colLabel)
        val colInput = EditText(this); colInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER; colInput.setText("3"); container.addView(colInput)
        AlertDialog.Builder(this).setTitle("⊞ Insert Table").setView(container)
            .setPositiveButton("Insert") { _, _ ->
                val rows = (rowInput.text.toString().toIntOrNull() ?: 3).coerceIn(1, 20)
                val cols = (colInput.text.toString().toIntOrNull() ?: 3).coerceIn(1, 20)
                val wx = drawingView.screenCenterWorldX() - (drawingView.width / drawingView.getScaleFactor() / 4f)
                val wy = drawingView.screenCenterWorldY() - 90f
                drawingView.addTable(rows, cols, wx, wy, drawingView.width.toFloat())
                showTableToolbar()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showTableToolbar() {
        tableToolbarOverlay?.let { canvasContainer.removeView(it) }
        val toolbar = LinearLayout(this); toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#CC333333"))
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4))

        fun btn(label: String, action: () -> Unit) {
            val b = Button(this); b.text = label; b.textSize = 12f; b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
            b.setPadding(dp(8), dp(4), dp(8), dp(4)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { action() }; toolbar.addView(b)
        }

        btn("+ Row") {
            val afterRow = drawingView.getTableSelection().first?.first ?: ((drawingView.getActiveTable()?.rows ?: 1) - 1)
            drawingView.addTableRow(afterRow)
        }
        btn("+ Col") {
            val afterCol = drawingView.getTableSelection().first?.second ?: ((drawingView.getActiveTable()?.cols ?: 1) - 1)
            drawingView.addTableCol(afterCol)
        }
        btn("- Row") {
            val delRow = drawingView.getTableSelection().first?.first ?: ((drawingView.getActiveTable()?.rows ?: 1) - 1)
            drawingView.removeTableRow(delRow)
        }
        btn("- Col") {
            val delCol = drawingView.getTableSelection().first?.second ?: ((drawingView.getActiveTable()?.cols ?: 1) - 1)
            drawingView.removeTableCol(delCol)
        }
        btn("Merge") { drawingView.mergeCellSelection() }
        btn("Unmerge") { drawingView.unmergeCellSelection() }
        btn("Style") {
            val table = drawingView.getActiveTable() ?: return@btn
            val sel = drawingView.getTableSelection().first ?: return@btn
            val r = sel.first.coerceIn(0, table.rows - 1)
            val c = sel.second.coerceIn(0, table.cols - 1)
            showCellStyleDialog(table, r, c, table.cells[r][c])
        }
        btn("✓ Done") {
            tableToolbarOverlay?.let { canvasContainer.removeView(it) }
            tableToolbarOverlay = null
        }

        val tp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        tp.topMargin = dp(4)
        canvasContainer.addView(toolbar, tp)
        tableToolbarOverlay = toolbar
    }

    private fun showCellStyleDialog(table: TableItem, row: Int, col: Int, cell: TableCell) {
    val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
    container.setPadding(dp(16), dp(8), dp(16), dp(8))

    fun label(text: String) {
        val tv = TextView(this); tv.text = text; tv.textSize = 13f
        tv.setTextColor(Color.parseColor("#7B61FF"))
        tv.setPadding(0, dp(10), 0, dp(4))
        container.addView(tv)
    }

    // ── Layout Presets ──────────────────────────────────────────
    label("Layout Preset")
    val presetRow = LinearLayout(this); presetRow.orientation = LinearLayout.HORIZONTAL
    presetRow.setPadding(0, dp(4), 0, dp(4))

    fun presetBtn(name: String, action: () -> Unit) {
        val b = Button(this); b.text = name; b.textSize = 11f
        b.setBackgroundColor(Color.parseColor("#EDE7F6"))
        b.setTextColor(Color.parseColor("#4527A0"))
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
        b.setPadding(dp(4), dp(6), dp(4), dp(6)); b.minWidth = 0; b.minimumWidth = 0
        b.setOnClickListener { action(); drawingView.invalidate() }
        presetRow.addView(b)
    }

    presetBtn("Header\nRow") {
        // First row: dark bg, white bold text, thick border
        for (c in 0 until table.cols) {
            val hc = table.getCellPublic(0, c)
            hc.bgColor = Color.parseColor("#37474F")
            hc.textColor = Color.WHITE
            hc.borderColor = Color.parseColor("#263238")
            hc.borderWidth = 3f
        }
        // Rest: normal
        for (r in 1 until table.rows) for (c in 0 until table.cols) {
            val nc = table.getCellPublic(r, c)
            nc.bgColor = Color.WHITE; nc.textColor = Color.BLACK
            nc.borderColor = Color.DKGRAY; nc.borderWidth = 1.5f
        }
    }
    presetBtn("Zebra\nRows") {
        for (r in 0 until table.rows) for (c in 0 until table.cols) {
            val nc = table.getCellPublic(r, c)
            nc.bgColor = if (r % 2 == 0) Color.WHITE else Color.parseColor("#F5F5F5")
            nc.textColor = Color.BLACK; nc.borderColor = Color.parseColor("#BDBDBD"); nc.borderWidth = 1f
        }
    }
    presetBtn("Bold\nBorders") {
        for (r in 0 until table.rows) for (c in 0 until table.cols) {
            val nc = table.getCellPublic(r, c)
            nc.borderColor = Color.BLACK; nc.borderWidth = 3f
        }
    }
    presetBtn("No\nBorders") {
        for (r in 0 until table.rows) for (c in 0 until table.cols) {
            val nc = table.getCellPublic(r, c)
            nc.borderColor = Color.TRANSPARENT; nc.borderWidth = 0f
        }
    }
    container.addView(presetRow)

    // Divider
    val div = View(this)
    val dlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    dlp.setMargins(0, dp(12), 0, dp(4)); div.layoutParams = dlp
    div.setBackgroundColor(Color.LTGRAY); container.addView(div)

    // ── Per-cell style ──────────────────────────────────────────
    label("Cell Style  (R${row+1} C${col+1})")

    label("Text Color")
    val textColorBtn = Button(this); textColorBtn.text = "Pick Text Color"; textColorBtn.textSize = 13f
    textColorBtn.setBackgroundColor(cell.textColor); textColorBtn.setTextColor(Color.WHITE)
    textColorBtn.setOnClickListener { showColorGridDialog { c -> cell.textColor = c; textColorBtn.setBackgroundColor(c); drawingView.invalidate() } }
    container.addView(textColorBtn)

    label("Background Color")
    val bgBtn = Button(this); bgBtn.text = "Pick Background"; bgBtn.textSize = 13f
    bgBtn.setBackgroundColor(cell.bgColor)
    bgBtn.setOnClickListener { showColorGridDialog { c -> cell.bgColor = c; bgBtn.setBackgroundColor(c); drawingView.invalidate() } }
    container.addView(bgBtn)

    label("Border Color")
    val borderBtn = Button(this); borderBtn.text = "Pick Border Color"; borderBtn.textSize = 13f
    borderBtn.setBackgroundColor(cell.borderColor); borderBtn.setTextColor(Color.WHITE)
    borderBtn.setOnClickListener { showColorGridDialog { c -> cell.borderColor = c; borderBtn.setBackgroundColor(c); drawingView.invalidate() } }
    container.addView(borderBtn)

    // Border thickness with thin/normal/bold quick buttons
    label("Border Width")
    val bwRow = LinearLayout(this); bwRow.orientation = LinearLayout.HORIZONTAL
    bwRow.setPadding(0, dp(4), 0, dp(4))
    val bwSeek = SeekBar(this); bwSeek.max = 20; bwSeek.progress = cell.borderWidth.toInt()

    fun bwQuickBtn(label: String, weight: Float) {
        val b = Button(this); b.text = label; b.textSize = 12f
        b.setBackgroundColor(Color.parseColor("#EDE7F6")); b.setTextColor(Color.parseColor("#4527A0"))
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
        b.setPadding(dp(8), dp(4), dp(8), dp(4)); b.minWidth = 0; b.minimumWidth = 0
        b.setOnClickListener { cell.borderWidth = weight; bwSeek.progress = weight.toInt(); drawingView.invalidate() }
        bwRow.addView(b)
    }
    bwQuickBtn("None", 0f); bwQuickBtn("Thin", 1f); bwQuickBtn("Normal", 2f); bwQuickBtn("Bold", 4f)
    container.addView(bwRow)

    bwSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { cell.borderWidth = v.toFloat(); drawingView.invalidate() }
        override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {}
    })
    container.addView(bwSeek)

    label("Text Size (${cell.textSize.toInt()}pt)")
    val tsSeek = SeekBar(this); tsSeek.max = 60; tsSeek.progress = cell.textSize.toInt()
    tsSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { cell.textSize = v.toFloat().coerceAtLeast(8f); drawingView.invalidate() }
        override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {}
    })
    container.addView(tsSeek)

    label("Alignment")
    val alignRow = LinearLayout(this); alignRow.orientation = LinearLayout.HORIZONTAL
    for ((idx, lbl) in listOf("Left", "Center", "Right").withIndex()) {
        val b = Button(this); b.text = lbl; b.textSize = 12f
        b.setBackgroundColor(if (cell.alignment == idx) Color.parseColor("#2196F3") else Color.LTGRAY)
        b.setOnClickListener { cell.alignment = idx; drawingView.invalidate() }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); b.layoutParams = lp
        alignRow.addView(b)
    }
    container.addView(alignRow)

    val scroll = ScrollView(this); scroll.addView(container)
    AlertDialog.Builder(this).setTitle("Table Style").setView(scroll).setPositiveButton("Done", null).show()
    }

    private fun showTableCellEditor(table: TableItem, row: Int, col: Int, screenX: Float, screenY: Float) {
    activeCellEditText?.let { canvasContainer.removeView(it) }
    activeCellToolbar?.let { canvasContainer.removeView(it) }

    val cell = table.cells[row][col]
    val editText = EditText(this)
    editText.setText(cell.text)
    editText.setTextColor(cell.textColor)
    editText.textSize = (cell.textSize * drawingView.getScaleFactor() / resources.displayMetrics.density).coerceAtLeast(8f)
    editText.setBackgroundColor(Color.parseColor("#EEFFFFFF"))
    editText.setPadding(dp(6), dp(4), dp(6), dp(4))
    editText.minWidth = dp(80)

    // Auto-commit on every text change — no confirm button needed
    editText.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            cell.text = s?.toString() ?: ""
            drawingView.invalidate()
        }
    })

    // Dismiss when focus is lost (user taps outside)
    editText.setOnFocusChangeListener { _, hasFocus ->
        if (!hasFocus) {
            cell.text = editText.text.toString()
            canvasContainer.removeView(editText)
            activeCellEditText = null
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
            drawingView.invalidate()
        }
    }

    val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    params.leftMargin = screenX.toInt().coerceIn(0, canvasContainer.width - dp(100))
    params.topMargin = (screenY + dp(4)).toInt().coerceIn(dp(40), canvasContainer.height - dp(80))
    canvasContainer.addView(editText, params)

    activeCellEditText = editText
    activeCellToolbar = null  // no toolbar needed anymore
    editText.requestFocus()
    editText.selectAll()
    editText.post {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
    }

    // ---------- Text editing ----------

    private fun toggleStyleOnSelection(editText: EditText, styleFlag: Int) {
        val start = editText.selectionStart; val end = editText.selectionEnd
        if (start == end) { Toast.makeText(this, "Select text first", Toast.LENGTH_SHORT).show(); return }
        val from = minOf(start, end); val to = maxOf(start, end)
        val editable = editText.text
        val existing = editable.getSpans(from, to, StyleSpan::class.java)
            .filter { it.style == styleFlag && editable.getSpanStart(it) <= from && editable.getSpanEnd(it) >= to }
        if (existing.isNotEmpty()) for (sp in existing) editable.removeSpan(sp)
        else editable.setSpan(StyleSpan(styleFlag), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyColorToSelection(editText: EditText, color: Int) {
        val start = editText.selectionStart; val end = editText.selectionEnd
        if (start == end) { editColor = color; editText.setTextColor(color); return }
        editText.text.setSpan(ForegroundColorSpan(color), minOf(start, end), maxOf(start, end), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun toggleUnderlineOnSelection(editText: EditText) {
        val start = editText.selectionStart; val end = editText.selectionEnd
        val from = minOf(start, end); val to = maxOf(start, end)
        val editable = editText.text
        val existing = editable.getSpans(from, to, UnderlineSpan::class.java)
            .filter { editable.getSpanStart(it) <= from && editable.getSpanEnd(it) >= to }
        if (existing.isNotEmpty()) for (sp in existing) editable.removeSpan(sp)
        else editable.setSpan(UnderlineSpan(), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyHighlightToSelection(editText: EditText, color: Int) {
        editText.text.setSpan(BackgroundColorSpan(color),
            minOf(editText.selectionStart, editText.selectionEnd),
            maxOf(editText.selectionStart, editText.selectionEnd),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        if (activeEditText != null && editingItem === item) return
        if (activeEditText != null) {
            isSwitchingTextEditor = true
            closeInlineEditor(commit = true)
            isSwitchingTextEditor = false
            drawingView.post { showInlineTextEditor(item, screenX, screenY, worldX, worldY) }
            return
        }

        pendingBold = false; pendingItalic = false; pendingUnderline = false; pendingHighlight = null
        editingItem = item
        editWorldX = item?.x ?: worldX; editWorldY = item?.y ?: worldY
        editRotation = item?.rotation ?: 0f
        editColor = item?.color ?: drawingView.currentColor
        editSize = item?.size ?: drawingView.defaultTextSize

        val density = resources.displayMetrics.density
        val useActualSize = drawingView.canvasMode != CanvasMode.INFINITE
        val screenSizePx = if (useActualSize) editSize else editSize * drawingView.getScaleFactor()

        val editText = EditText(this)
        val spannable = SpannableStringBuilder(item?.text ?: "")
        item?.spans?.forEach { sp ->
            val s = sp.start.coerceIn(0, spannable.length); val e = sp.end.coerceIn(s, spannable.length)
            if (s < e) when (sp.type) {
                'S' -> spannable.setSpan(StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'C' -> spannable.setSpan(ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'U' -> spannable.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'H' -> spannable.setSpan(BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        editText.setText(spannable, TextView.BufferType.SPANNABLE)
        editText.setTextColor(editColor)
        editText.textSize = (screenSizePx / density).coerceAtLeast(8f)
        editText.setBackgroundColor(Color.TRANSPARENT)
        editText.setPadding(dp(4), dp(4), dp(4), dp(4))
        editText.minWidth = dp(120)
        if (!useActualSize) editText.rotation = editRotation

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0) {
                    val e = editText.text; val end = start + count
                    if (pendingBold) e.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (pendingItalic) e.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (pendingUnderline) e.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    pendingHighlight?.let { e.setSpan(BackgroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin = screenX.toInt()
        params.topMargin = (screenY - screenSizePx).toInt().coerceAtLeast(0)
        canvasContainer.addView(editText, params)

        val toolbar = LinearLayout(this); toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#AA222222"))
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4))

        fun toolBtn(label: String, action: (Button) -> Unit): Button {
            val b = Button(this); b.text = label; b.textSize = 14f; b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
            b.setPadding(dp(10), dp(6), dp(10), dp(6)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { action(b) }; toolbar.addView(b); return b
        }

        val activeC = Color.parseColor("#2196F3"); val inactiveC = Color.parseColor("#55FFFFFF")

        toolBtn("B") { btn ->
            if (editText.selectionStart != editText.selectionEnd) toggleStyleOnSelection(editText, Typeface.BOLD)
            else { pendingBold = !pendingBold; btn.setBackgroundColor(if (pendingBold) activeC else inactiveC) }
        }
        toolBtn("I") { btn ->
            if (editText.selectionStart != editText.selectionEnd) toggleStyleOnSelection(editText, Typeface.ITALIC)
            else { pendingItalic = !pendingItalic; btn.setBackgroundColor(if (pendingItalic) activeC else inactiveC) }
        }
        toolBtn("U") { btn ->
            if (editText.selectionStart != editText.selectionEnd) toggleUnderlineOnSelection(editText)
            else { pendingUnderline = !pendingUnderline; btn.setBackgroundColor(if (pendingUnderline) activeC else inactiveC) }
        }
        toolBtn("🖍") { btn ->
            showColorGridDialog { color ->
                if (editText.selectionStart != editText.selectionEnd) applyHighlightToSelection(editText, color)
                else { pendingHighlight = color; btn.setBackgroundColor(color) }
            }
        }
        toolBtn("🎨") { showColorGridDialog { color -> applyColorToSelection(editText, color) } }
        val ptSize = (editSize / PT_TO_PX).toInt().coerceIn(1, 144)
        toolBtn(ptSize.toString()) { btn ->
            AlertDialog.Builder(this).setTitle("Font Size (pt)")
                .setItems((1..144).map { it.toString() }.toTypedArray()) { _, idx ->
                    val pt = idx + 1; editSize = pt * PT_TO_PX
                    val newScreenSize = if (useActualSize) editSize else editSize * drawingView.getScaleFactor()
                    editText.textSize = (newScreenSize / density).coerceAtLeast(8f)
                    btn.text = pt.toString()
                    drawingView.onScaleChanged?.invoke(drawingView.getScaleFactor())
                }.show()
        }
        toolBtn("↺") { editRotation -= 15f; if (!useActualSize) editText.rotation = editRotation }
        toolBtn("↻") { editRotation += 15f; if (!useActualSize) editText.rotation = editRotation }
        toolBtn("✓") { closeInlineEditor(commit = true) }
        toolBtn("🗑") { closeInlineEditor(commit = false, delete = true) }

        val tp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        canvasContainer.addView(toolbar, tp)

        fun updateEditorTransform() {
            val scale = drawingView.getScaleFactor()
            val newSizePx = editSize * scale
            editText.textSize = (newSizePx / density).coerceAtLeast(8f)
            val sx = drawingView.worldToScreenX(editWorldX)
            val sy = drawingView.worldToScreenY(editWorldY) - newSizePx
            val lp = editText.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin = sx.toInt()
            lp.topMargin = sy.toInt()
            editText.layoutParams = lp
        }
        drawingView.onScaleChanged = { updateEditorTransform() }
        drawingView.onCanvasTransformed = { updateEditorTransform() }

        activeEditText = editText; activeToolbar = toolbar
        editText.requestFocus()
        editText.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeInlineEditor(commit: Boolean, delete: Boolean = false) {
        val editText = activeEditText ?: return
        val toolbar = activeToolbar
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)

        val text = editText.text.toString()
        val spans = mutableListOf<TextSpanData>()
        val editable = editText.text
        for (span in editable.getSpans(0, editable.length, Any::class.java)) {
            val s = editable.getSpanStart(span); val e = editable.getSpanEnd(span)
            if (s < 0 || e < 0 || s >= e) continue
            when (span) {
                is StyleSpan -> spans.add(TextSpanData(s, e, 'S', span.style))
                is ForegroundColorSpan -> spans.add(TextSpanData(s, e, 'C', span.foregroundColor))
                is UnderlineSpan -> spans.add(TextSpanData(s, e, 'U', 0))
                is BackgroundColorSpan -> spans.add(TextSpanData(s, e, 'H', span.backgroundColor))
            }
        }
        canvasContainer.removeView(editText)
        if (toolbar != null) canvasContainer.removeView(toolbar)

        val item = editingItem
        if (commit && !delete && text.isNotBlank()) {
            drawingView.defaultTextSize = editSize
            if (item != null) {
                item.text = text; item.color = editColor; item.size = editSize
                item.rotation = editRotation; item.spans = spans; item.isEditing = false
            } else {
                drawingView.addText(text, editWorldX, editWorldY, editSize, editRotation, editColor, spans)
            }
        } else { if (item != null) drawingView.removeTextItem(item) }

        if (!isSwitchingTextEditor) drawingView.invalidate()
        drawingView.onScaleChanged = null
        drawingView.onCanvasTransformed = null
        activeEditText = null; activeToolbar = null; editingItem = null
    }

    // ---------- Image ----------

    private fun launchCamera() {
        val folder = File(filesDir, "images"); if (!folder.exists()) folder.mkdirs()
        val photoFile = File(folder, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageFile = photoFile
        takePictureLauncher.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile))
    }

    private fun addImageFromFile(file: File) {
        try {
            // Read dimensions only — don't decode full bitmap on main thread
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            val srcW = opts.outWidth.toFloat().coerceAtLeast(1f)
            val srcH = opts.outHeight.toFloat().coerceAtLeast(1f)
            val ratio = srcW / srcH
            val screenW = drawingView.width / drawingView.getScaleFactor()
            val screenH = drawingView.height / drawingView.getScaleFactor()
            val w = screenW * 0.85f
            val h = (w / ratio).coerceAtMost(screenH * 0.85f)
            drawingView.addImage(file.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
        } catch (e: Exception) { Toast.makeText(this, "Photo failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun insertImage(uri: Uri) {
        try {
            // Step 1: read dimensions without decoding full image
            val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts) }
            val srcW = boundsOpts.outWidth.toFloat().coerceAtLeast(1f)
            val srcH = boundsOpts.outHeight.toFloat().coerceAtLeast(1f)
            val ratio = srcW / srcH

            // Step 2: decode with aggressive downsampling — max 1920px
            val maxDim = 1920
            var sample = 1
            var tw = srcW.toInt(); var th = srcH.toInt()
            while (tw > maxDim || th > maxDim) { sample *= 2; tw /= 2; th /= 2 }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return

            // Step 3: save to file
            val folder = File(filesDir, "images"); if (!folder.exists()) folder.mkdirs()
            val out = File(folder, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()

            // Step 4: insert at 85% screen width
            val screenW = drawingView.width / drawingView.getScaleFactor()
            val screenH = drawingView.height / drawingView.getScaleFactor()
            val w = screenW * 0.85f
            val h = (w / ratio).coerceAtMost(screenH * 0.85f)
            drawingView.addImage(out.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Image failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- File ops ----------

    private fun getDrawingsFolder(): File {
        val bookName = intent.getStringExtra("book_name") ?: "General"
        val f = File(File(filesDir, "books"), bookName)
        if (!f.exists()) f.mkdirs(); return f
    }

    private fun writeCurrentFile() {
        val name = currentFileName ?: return
        File(getDrawingsFolder(), "$name.eng").writeText(drawingView.serialize())
        lastSavedContent = drawingView.serialize()
    }

    private fun autoSave() {
        if (!getPrefs().getBoolean("autosave", true)) return
        if (!drawingView.hasContent()) return
        if (currentFileName == null) {
            val name = "AutoSave_${System.currentTimeMillis()}"
            currentFileName = name; tvTitle.text = name
        }
        writeCurrentFile()
    }

    private fun saveCurrent() {
        if (currentFileName == null) {
            val input = EditText(this); input.hint = "Note name"
            AlertDialog.Builder(this).setTitle("Save Note").setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim().ifEmpty { "Note_${System.currentTimeMillis()}" }
                    currentFileName = name; tvTitle.text = name; writeCurrentFile()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null).show()
        } else { writeCurrentFile(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
    }

    private fun saveAsNew() {
        val input = EditText(this); input.hint = "New note name"
        AlertDialog.Builder(this).setTitle("Save as New").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Note_${System.currentTimeMillis()}" }
                currentFileName = name; tvTitle.text = name; writeCurrentFile()
                Toast.makeText(this, "Saved as $name", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteCurrentNote() {
        val name = currentFileName ?: return
        AlertDialog.Builder(this).setTitle("Delete Note").setMessage("Delete \"$name\"? Cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> File(getDrawingsFolder(), "$name.eng").delete(); finish() }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onPause() {
        super.onPause()
        closeInlineEditor(commit = true)
        autoSave()
    }
}
