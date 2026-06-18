package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
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
    private lateinit var btnLayoutToggle: Button

    private var currentFileName: String? = null
    private var lastSavedContent: String = ""
    private val PT_TO_PX = 1.333f

    private var isConvenientLayout = true

    private val shapeSymbols = listOf("╱ Line","▭ Rect","▢ Rounded","○ Circle","⬭ Ellipse","△ Triangle","◇ Diamond","➔ Arrow","★ Star","⬠ Pentagon","⬡ Hexagon","〜 Curve","✛ Cross")
    private val shapeTools   = listOf(Tool.LINE,Tool.RECTANGLE,Tool.ROUNDED_RECT,Tool.CIRCLE,Tool.ELLIPSE,Tool.TRIANGLE,Tool.DIAMOND,Tool.ARROW,Tool.STAR,Tool.PENTAGON,Tool.HEXAGON,Tool.CURVE,Tool.CROSS)

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

    // Audio recording infrastructure
    private var isRecording = false
    private var recordingFile: File? = null
    private var recordingStartMs = 0L

    // Autosave setup
    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = object : Runnable {
        override fun run() {
            if (getPrefs().getBoolean("autosave", true)) autoSave()
            autosaveHandler.postDelayed(this, 10_000L)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Launchers
    // ──────────────────────────────────────────────────────────────

    private val chartLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("chart_image_path") ?: return@registerForActivityResult
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return@registerForActivityResult
            val ratio = bmp.width.toFloat() / bmp.height
            val w = drawingView.width / drawingView.getScaleFactor() * 0.85f
            val h = w / ratio
            drawingView.addImage(path, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
            Toast.makeText(this, "Chart added!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) startActivity(android.content.Intent(this, PdfViewerActivity::class.java)
            .putExtra("pdf_uri", uri.toString()))
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) insertImage(uri)
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraImageFile?.let { addImageFromFile(it) }
    }

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val bmp = pendingExportBitmap ?: return@registerForActivityResult
            val maxDim = 3000
            val scale = if (bmp.width > maxDim || bmp.height > maxDim) minOf(maxDim.toFloat()/bmp.width, maxDim.toFloat()/bmp.height) else 1f
            val pw = (bmp.width*scale).toInt().coerceAtLeast(1)
            val ph = (bmp.height*scale).toInt().coerceAtLeast(1)
            val sb = if (scale < 1f) Bitmap.createScaledBitmap(bmp,pw,ph,true) else bmp
            val doc = PdfDocument()
            val pi = PdfDocument.PageInfo.Builder(pw,ph,1).create()
            val page = doc.startPage(pi)
            page.canvas.drawBitmap(sb,0f,0f,Paint())
            doc.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            doc.close()
            Toast.makeText(this,"PDF saved!",Toast.LENGTH_SHORT).show()
        } catch(e:Exception){ Toast.makeText(this,"PDF failed: ${e.message}",Toast.LENGTH_LONG).show() }
        finally { pendingExportBitmap = null }
    }

    private val saveImageLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val bmp = pendingExportBitmap ?: return@registerForActivityResult
            when(pendingExportFormat) {
                "jpg" -> contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG,95,it) }
                "bmp" -> contentResolver.openOutputStream(uri)?.use { writeBmp(bmp,it) }
                else  -> contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            Toast.makeText(this,"Image saved!",Toast.LENGTH_SHORT).show()
        } catch(e:Exception){ Toast.makeText(this,"Image failed: ${e.message}",Toast.LENGTH_LONG).show() }
        finally { pendingExportBitmap = null }
    }

    private val saveTxtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val engFile = currentFileName?.let { File(getDrawingsFolder(),"$it.eng") } ?: run { 
                Toast.makeText(this,"Save first!",Toast.LENGTH_SHORT).show()
                return@registerForActivityResult 
            }
            val sb = StringBuilder()
            sb.append("=== $currentFileName ===\n\n")
            for (line in engFile.readLines()) {
                if (line.startsWith("TEXT\u0001")) { 
                    val p = line.split("\u0001")
                    if(p.size>7) sb.append(p.last().replace("\u0002","\n")).append("\n") 
                }
            }
            contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            Toast.makeText(this,"TXT saved!",Toast.LENGTH_SHORT).show()
        } catch(e:Exception){ Toast.makeText(this,"TXT failed: ${e.message}",Toast.LENGTH_LONG).show() }
    }

    private val saveDocxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val engFile = currentFileName?.let { File(getDrawingsFolder(),"$it.eng") } ?: return@registerForActivityResult
            val doc = XWPFDocument()
            doc.createParagraph().createRun().apply { 
                setText(currentFileName?:"EngiNote")
                isBold = true
                fontSize = 18 
            }
            for(line in engFile.readLines()) {
                if(line.startsWith("TEXT\u0001")){ 
                    val p = line.split("\u0001")
                    if(p.size>7){ 
                        val run = doc.createParagraph().createRun()
                        run.setText(p.last().replace("\u0002","\n"))
                        try{ run.fontSize = (p[4].toFloat()/PT_TO_PX).toInt().coerceIn(8,72) } catch(e:Exception){} 
                    } 
                }
            }
            val bmp = drawingView.exportBitmap()
            val imgFile = File(cacheDir,"export_tmp.png")
            FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG,90,it) }
            doc.createParagraph().createRun().addPicture(imgFile.inputStream(), org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "canvas.png", org.apache.poi.util.Units.toEMU(400.0), org.apache.poi.util.Units.toEMU(300.0))
            contentResolver.openOutputStream(uri)?.use { doc.write(it) }
            doc.close()
            Toast.makeText(this,"DOCX saved!",Toast.LENGTH_SHORT).show()
        } catch(e:Exception){ Toast.makeText(this,"DOCX failed: ${e.message}",Toast.LENGTH_LONG).show() }
    }

    // ──────────────────────────────────────────────────────────────
    //  onCreate
    // ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView     = findViewById(R.id.drawingView)
        canvasContainer = findViewById(R.id.canvasContainer)
        tvTitle         = findViewById(R.id.tvTitle)
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle)

        val prefs = getPrefs()
        val defaultPaper = prefs.getString("default_paper","LINED") ?: "LINED"
        try { drawingView.paperType = PaperType.valueOf(defaultPaper) } catch(e:Exception){}

        val fileName = intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName
            tvTitle.text = fileName
            val file = File(getDrawingsFolder(),"$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else { 
            tvTitle.text = "New Note" 
        }

        drawingView.migrateOldNotes(filesDir)
        lastSavedContent = drawingView.serialize()
        drawingView.arcDivisions = prefs.getInt("arc_divisions", 3)

        applyConvenientLayout()

        tvActiveTool = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setBackgroundColor(Color.parseColor("#55000000"))
            setPadding(dp(3),0,dp(3),dp(1))
            text = "Select"
        }
        val ip = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        ip.gravity = Gravity.TOP or Gravity.END
        ip.topMargin = dp(4)
        ip.rightMargin = dp(4)
        canvasContainer.addView(tvActiveTool, ip)

        // Core view engine callbacks
        drawingView.onTextEditRequest = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTableCellEditRequest = { table, row, col, sx, sy -> 
            dismissCellEditor()
            showTableCellEditor(table,row,col,sx,sy) 
        }
        drawingView.onExportWindowSelected = { l,t,r,b -> 
            exportWindowBitmap = drawingView.exportWindow(l,t,r,b)
            showExportWindowDialog() 
        }

        drawingView.onAudioItemTap = { item ->
            AudioHelper.togglePlay(item) { drawingView.invalidate() }
            drawingView.invalidate()
        }

        btnLayoutToggle.setOnClickListener { toggleLayout() }

        setupBottomToolbar()
        setActiveTool(null, Tool.SELECT, "Select")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activeEditText != null -> closeInlineEditor(commit = true)
                    activeCellEditText != null -> dismissCellEditor()
                    tableToolbarOverlay != null -> { 
                        canvasContainer.removeView(tableToolbarOverlay)
                        tableToolbarOverlay = null 
                    }
                    drawingView.currentTool != Tool.SELECT -> setActiveTool(null, Tool.SELECT, "Select")
                    else -> confirmThenExit()
                }
            }
        })

        autosaveHandler.postDelayed(autosaveRunnable, 10_000L)
    }

    private fun setupBottomToolbar() {
        findViewById<Button>(R.id.btnUndo).setOnClickListener { 
            closeInlineEditor(true)
            drawingView.undo() 
        }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { 
            closeInlineEditor(true)
            drawingView.redo() 
        }

        findViewById<Button>(R.id.btnDraw).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.PEN, "Pen")
        }
        findViewById<Button>(R.id.btnDraw).setOnLongClickListener {
            showDrawPicker(it as Button)
            true
        }

        findViewById<Button>(R.id.btnQuickEraser).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.ERASER, "Eraser")
        }

        findViewById<Button?>(R.id.btnSelect)?.setOnClickListener { 
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.SELECT, "Select") 
        }

        findViewById<Button>(R.id.btnText).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.TEXT, "Text")
        }

        findViewById<Button>(R.id.btnInsert).setOnClickListener { showInsertMenu() }
        findViewById<Button>(R.id.btnTools).setOnClickListener { showToolsMenu() }

        val toolbarScroll = findViewById<View>(R.id.toolbarScroll)
        val btnExpand = findViewById<Button>(R.id.btnExpand)
        btnExpand.setOnClickListener {
            val show = toolbarScroll.visibility != View.VISIBLE
            toolbarScroll.visibility = if (show) View.VISIBLE else View.GONE
            btnExpand.text = if (show) "∧" else "∨"
        }

        findViewById<Button?>(R.id.btnArc)?.setOnClickListener { 
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.ARC, "Arc") 
        }

        findViewById<Button?>(R.id.btnAutoSelect)?.setOnClickListener { 
            showAutoSelectModeDialog()
            setActiveToolbarBtn(it as Button) 
        }

        findViewById<Button?>(R.id.btnShapes)?.setOnClickListener { showShapesPicker(it as Button) }

        findViewById<Button?>(R.id.btnQuickColor)?.setOnClickListener { 
            showColorGridDialog { c -> drawingView.currentColor = c } 
        }

        findViewById<Button?>(R.id.btnQuickSize)?.setOnClickListener { showSizePicker() }

        findViewById<Button?>(R.id.btnQuickFill)?.setOnClickListener {
            showColorGridDialog { c -> drawingView.fillColor = c }
            setActiveTool(null, Tool.FILL, "Fill")
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Layout management
    // ──────────────────────────────────────────────────────────────

    private fun applyConvenientLayout() {
        isConvenientLayout = true
        btnLayoutToggle.text = "Convenient"
        btnLayoutToggle.setBackgroundColor(Color.parseColor("#EDE7F6"))
        btnLayoutToggle.setTextColor(Color.parseColor("#6200EE"))
        drawingView.canvasMode = CanvasMode.CONVENIENT
        drawingView.invalidate()
    }

    private fun applyPrintLayout() {
        isConvenientLayout = false
        btnLayoutToggle.text = "Print"
        btnLayoutToggle.setBackgroundColor(Color.parseColor("#E3F2FD"))
        btnLayoutToggle.setTextColor(Color.parseColor("#1565C0"))
        drawingView.canvasMode = CanvasMode.PAGINATED
        drawingView.paperSize = PaperSizeOption.A4
        drawingView.invalidate()
    }

    private fun toggleLayout() {
        if (isConvenientLayout) {
            AlertDialog.Builder(this)
                .setTitle("Switch to Print Layout")
                .setMessage("Print layout uses real A4 sizes. Text items will appear smaller.\n\nRearrange text to fit print columns?")
                .setPositiveButton("Rearrange") { _, _ -> applyPrintLayout(); drawingView.rearrangeTextForPrint() }
                .setNegativeButton("Just Switch") { _, _ -> applyPrintLayout() }
                .setNeutralButton("Cancel", null).show()
        } else {
            applyConvenientLayout()
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Tool / Dialog Helpers
    // ──────────────────────────────────────────────────────────────

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

    private fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    private fun showDrawPicker(anchor: Button) {
        AlertDialog.Builder(this).setTitle("Draw Tool")
            .setItems(arrayOf("👆 Select","✏ Pen","⌇ Arc","🪣 Fill","🔲 AutoSelect")) { _, i -> 
                when(i) {
                    0 -> setActiveTool(anchor, Tool.SELECT, "Select")
                    1 -> setActiveTool(anchor, Tool.PEN, "Pen")
                    2 -> setActiveTool(anchor, Tool.ARC, "Arc")
                    3 -> { 
                        showColorGridDialog { c -> drawingView.fillColor = c }
                        setActiveTool(anchor, Tool.FILL, "Fill") 
                    }
                    4 -> { 
                        showAutoSelectModeDialog()
                        setActiveToolbarBtn(anchor) 
                    }
                }
            }.show()
    }

    private fun showShapesPicker(anchor: Button) {
        AlertDialog.Builder(this).setTitle("Shapes")
            .setItems(shapeSymbols.toTypedArray()) { _, i -> 
                setActiveTool(anchor, shapeTools[i], shapeSymbols[i].take(2)) 
            }.show()
    }

    private fun showInsertMenu() {
        closeInlineEditor(true)
        AlertDialog.Builder(this).setTitle("Insert")
            .setItems(arrayOf("🖼 Image from Gallery","📷 Take Photo","⊞ Table","🎙 Record Audio","✂ Snip from PDF")) { _, i -> 
                when(i) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> launchCamera()
                    2 -> showTableInsertDialog()
                    3 -> showAudioRecordDialog()
                    4 -> pickPdfLauncher.launch("application/pdf")
                }
            }.show()
    }

    private fun showToolsMenu() {
        closeInlineEditor(true)
        AlertDialog.Builder(this).setTitle("Tools")
            .setItems(arrayOf("🧹 Eraser","🎨 Pen Color","🪣 Fill Shapes On/Off","📏 Size")) { _, i -> 
                when(i) {
                    0 -> if (drawingView.currentTool == Tool.ERASER) {
                        drawingView.eraserMode = if (drawingView.eraserMode == EraserMode.OBJECT) EraserMode.AREA else EraserMode.OBJECT
                        Toast.makeText(this, if (drawingView.eraserMode == EraserMode.OBJECT) "Object Eraser" else "Area Eraser", Toast.LENGTH_SHORT).show()
                    } else setActiveTool(null, Tool.ERASER, "Eraser")
                    1 -> showColorGridDialog { c -> 
                        drawingView.currentColor = c
                        if(drawingView.currentTool == Tool.ERASER) drawingView.currentTool = Tool.PEN 
                    }
                    2 -> { 
                        drawingView.fillShapes = !drawingView.fillShapes
                        Toast.makeText(this, if(drawingView.fillShapes) "Fill: On" else "Fill: Off", Toast.LENGTH_SHORT).show() 
                    }
                    3 -> showSizePicker()
                }
            }.show()
    }

    // ──────────────────────────────────────────────────────────────
    //  Context / Workspace Menu Actions
    // ──────────────────────────────────────────────────────────────

    fun onMenuClick(v: View) {
        closeInlineEditor(true)
        val popup = PopupMenu(this, v)
        listOf("Save","Save As","Export","Export Window","Clear Canvas").forEach { popup.menu.add(it) }
        if (currentFileName != null) popup.menu.add("Delete This Note")
        popup.menu.add("Add to Book")
        listOf("📄 Open PDF","📊 Chart Builder","✍ Handwriting to Text","⚙ Settings","Exit").forEach { popup.menu.add(it) }
        
        popup.setOnMenuItemClickListener { item -> 
            when(item.title) {
                "Save" -> saveCurrent()
                "Save As" -> saveAsNew()
                "Export" -> showExportDialog()
                "Export Window" -> {
                    if(drawingView.canvasMode == CanvasMode.INFINITE) {
                        Toast.makeText(this, "Select boundary window", Toast.LENGTH_SHORT).show()
                        setActiveTool(null, Tool.EXPORT_WINDOW, "Snip")
                    } else {
                        exportWindowBitmap = drawingView.exportBitmap()
                        showExportWindowDialog()
                    }
                }
                "Clear Canvas" -> confirmThenClear()
                "Delete This Note" -> confirmThenDelete()
                "Add to Book" -> showAddToBookDialog()
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

    // ──────────────────────────────────────────────────────────────
    //  Stub Handlers & Utilities for Clean Compilation 
    // ──────────────────────────────────────────────────────────────

    private fun writeBmp(bmp: Bitmap, out: java.io.OutputStream) {
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgb = ByteArray(w * h * 3)
        var i = 0
        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                rgb[i++] = (p and 0xFF).toByte()
                rgb[i++] = ((p ushr 8) and 0xFF).toByte()
                rgb[i++] = ((p ushr 16) and 0xFF).toByte()
            }
        }
        val header = ByteArray(54)
        header[0] = 'B'.toByte(); header[1] = 'M'.toByte()
        var size = 54 + rgb.size
        header[2] = size.toByte(); header[3] = (size ushr 8).toByte(); header[4] = (size ushr 16).toByte(); header[5] = (size ushr 24).toByte()
        header[10] = 54.toByte()
        header[14] = 40.toByte()
        header[18] = w.toByte(); header[19] = (w ushr 8).toByte(); header[20] = (w ushr 16).toByte(); header[21] = (w ushr 24).toByte()
        header[22] = h.toByte(); header[23] = (h ushr 8).toByte(); header[24] = (h ushr 16).toByte(); header[25] = (h ushr 24).toByte()
        header[26] = 1.toByte()
        header[28] = 24.toByte()
        header[34] = rgb.size.toByte(); header[35] = (rgb.size ushr 8).toByte(); header[36] = (rgb.size ushr 16).toByte(); header[37] = (rgb.size ushr 24).toByte()
        out.write(header)
        out.write(rgb)
    }

    private fun insertImage(uri: Uri) {
        try {
            val f = File(cacheDir, "insert_tmp_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(f).use { input.copyTo(it) }
            }
            addImageFromFile(f)
        } catch(e: Exception) { Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show() }
    }

    private fun addImageFromFile(file: File) {
        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return
        val ratio = bmp.width.toFloat() / bmp.height
        val w = drawingView.width / drawingView.getScaleFactor() * 0.5f
        val h = w / ratio
        drawingView.addImage(file.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
    }

    private fun launchCamera() {
        val f = File(cacheDir, "camera_snap.jpg")
        cameraImageFile = f
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
        takePictureLauncher.launch(uri)
    }

    private fun getDrawingsFolder(): File {
        val f = File(filesDir, "drawings")
        if (!f.exists()) f.mkdirs()
        return f
    }

    private fun autoSave() {
        val name = currentFileName ?: return
        val data = drawingView.serialize()
        if (data != lastSavedContent) {
            File(getDrawingsFolder(), "$name.eng").writeText(data)
            lastSavedContent = data
        }
    }

    private fun saveCurrent() {
        val name = currentFileName
        if (name == null) {
            saveAsNew()
        } else {
            val data = drawingView.serialize()
            File(getDrawingsFolder(), "$name.eng").writeText(data)
            lastSavedContent = data
            Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsNew() {
        val input = EditText(this)
        input.setSingleLine()
        AlertDialog.Builder(this).setTitle("Save Note As").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    currentFileName = name
                    tvTitle.text = name
                    saveCurrent()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showExportDialog() {
        AlertDialog.Builder(this).setTitle("Export Page As")
            .setItems(arrayOf("PDF Document", "PNG Image", "JPEG Image", "BMP Image", "Plain Text (TXT)", "Word Document (DOCX)")) { _, i ->
                pendingExportBitmap = drawingView.exportBitmap()
                when(i) {
                    0 -> savePdfLauncher.launch("${currentFileName ?: "Note"}.pdf")
                    1 -> { pendingExportFormat = "png"; saveImageLauncher.launch("${currentFileName ?: "Note"}.png") }
                    2 -> { pendingExportFormat = "jpg"; saveImageLauncher.launch("${currentFileName ?: "Note"}.jpg") }
                    3 -> { pendingExportFormat = "bmp"; saveImageLauncher.launch("${currentFileName ?: "Note"}.bmp") }
                    4 -> saveTxtLauncher.launch("${currentFileName ?: "Note"}.txt")
                    5 -> saveDocxLauncher.launch("${currentFileName ?: "Note"}.docx")
                }
            }.show()
    }

    private fun showExportWindowDialog() {
        val bmp = exportWindowBitmap ?: return
        val iv = ImageView(this).apply { setImageBitmap(bmp); setPadding(16,16,16,16) }
        AlertDialog.Builder(this).setTitle("Window Captured").setView(iv)
            .setPositiveButton("Save Image") { _, _ ->
                pendingExportBitmap = bmp
                pendingExportFormat = "png"
                saveImageLauncher.launch("snip_${System.currentTimeMillis()}.png")
            }.setNegativeButton("Cancel") { _, _ -> exportWindowBitmap = null }.show()
    }

    private fun showTableInsertDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,20,40,20) }
        val rInput = EditText(this).apply { hint = "Rows (e.g. 3)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val cInput = EditText(this).apply { hint = "Columns (e.g. 3)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        layout.addView(rInput); layout.addView(cInput)
        AlertDialog.Builder(this).setTitle("Insert Dynamic Table").setView(layout)
            .setPositiveButton("Insert") { _, _ ->
                val r = rInput.text.toString().toIntOrNull() ?: 3
                val c = cInput.text.toString().toIntOrNull() ?: 3
                drawingView.addTable(drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), r, c)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showAudioRecordDialog() {
        val b = Button(this).apply { text = "🔴 Start Recording" }
        val dialog = AlertDialog.Builder(this).setTitle("Audio Note").setView(b).create()
        b.setOnClickListener {
            if (!isRecording) {
                val f = File(getDrawingsFolder(), "audio_${System.currentTimeMillis()}.mp3")
                recordingFile = f
                AudioHelper.startRecording(f.absolutePath)
                recordingStartMs = System.currentTimeMillis()
                isRecording = true
                b.text = "⏹ Stop Recording"
            } else {
                AudioHelper.stopRecording()
                isRecording = false
                val elapsed = (System.currentTimeMillis() - recordingStartMs) / 1000
                recordingFile?.let {
                    drawingView.addAudioItem(it.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), elapsed.toInt())
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAutoSelectModeDialog() {
        AlertDialog.Builder(this).setTitle("AutoSelect Boundary Settings")
            .setItems(arrayOf("Rectangle Window", "Freeform Lasso Area", "Select Whole Item Paths", "Select Intersected Subpaths")) { _, i ->
                when(i) {
                    0 -> { drawingView.autoSelectShape = AutoSelectShape.RECTANGLE; setActiveTool(null, Tool.AUTOSELECT, "BoxSel") }
                    1 -> { drawingView.autoSelectShape = AutoSelectShape.FREEFORM; setActiveTool(null, Tool.AUTOSELECT, "Lasso") }
                    2 -> { drawingView.autoSelectDivide = AutoSelectDivide.WHOLE; setActiveTool(null, Tool.AUTOSELECT, "ItemSel") }
                    3 -> { drawingView.autoSelectDivide = AutoSelectDivide.DIVIDED; setActiveTool(null, Tool.AUTOSELECT, "FragSel") }
                }
            }.show()
    }

    private fun showColorGridDialog(onColorSelected: (Int) -> Unit) {
        val colors = listOf(Color.BLACK, Color.RED, Color.parseColor("#1B5E20"), Color.BLUE, Color.parseColor("#E65100"), Color.PURPLE, Color.DKGRAY, Color.LTGRAY)
        val names = arrayOf("Black", "Red", "Green", "Blue", "Orange", "Purple", "Dark Gray", "Light Gray")
        AlertDialog.Builder(this).setTitle("Select Paint Color")
            .setItems(names) { _, i -> onColorSelected(colors[i]); drawingView.invalidate() }.show()
    }

    private fun showSizePicker() {
        val sizes = arrayOf("1 dp (Fine)", "3 dp (Medium)", "6 dp (Thick)", "12 dp (Heavy Structural)", "24 dp (Highlight block)")
        val vals = floatArrayOf(1f, 3f, 6f, 12f, 24f)
        AlertDialog.Builder(this).setTitle("Stroke Diameter")
            .setItems(sizes) { _, i -> drawingView.currentStrokeWidth = vals[i] }.show()
    }

    private fun showAddToBookDialog() {
        val rootDir = File(filesDir, "books")
        if(!rootDir.exists()) rootDir.mkdirs()
        val books = rootDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        AlertDialog.Builder(this).setTitle("Assign to Notebook Workspace")
            .setItems(books.toTypedArray()) { _, i ->
                Toast.makeText(this, "Moved note to book: ${books[i]}", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun confirmThenDelete() {
        AlertDialog.Builder(this).setTitle("Confirm Erasure").setMessage("Delete file permanently from memory?")
            .setPositiveButton("Delete") { _, _ ->
                currentFileName?.let { File(getDrawingsFolder(), "$it.eng").delete() }
                finish()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSettingsDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,30,40,30) }
        val arcInput = EditText(this).apply { 
            hint = "Arc Segments (2-12)"
            setText(drawingView.arcDivisions.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER 
        }
        container.addView(TextView(this).apply { text = "Arc Rendering Divisions:" })
        container.addView(arcInput)
        AlertDialog.Builder(this).setTitle("Workspace Preferences").setView(container)
            .setPositiveButton("Save Options") { _, _ ->
                val divs = arcInput.text.toString().toIntOrNull() ?: 3
                drawingView.arcDivisions = divs.coerceIn(2, 12)
                getPrefs().edit().putInt("arc_divisions", drawingView.arcDivisions).apply()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmThenClear() {
        if(getPrefs().getBoolean("confirm_exit_clear",true) && drawingView.hasContent()){
            AlertDialog.Builder(this).setTitle("Clear Canvas?").setMessage("This will remove everything.")
                .setPositiveButton("Clear"){ _,_ -> drawingView.clearAll() }.setNegativeButton("Cancel",null).show()
        } else drawingView.clearAll()
    }

    private fun confirmThenExit() {
        val changed = drawingView.serialize() != lastSavedContent && drawingView.hasContent()
        if(!changed){ finish(); return }
        if(getPrefs().getBoolean("autosave",true)){ autoSave(); finish(); return }
        if(getPrefs().getBoolean("confirm_exit_clear",true)){
            AlertDialog.Builder(this).setTitle("Unsaved Changes").setMessage("Save before leaving?")
                .setPositiveButton("Save"){ _,_ -> saveCurrent(); finish() }
                .setNeutralButton("Don't Save"){ _,_ -> finish() }
                .setNegativeButton("Cancel",null).show()
        } else { autoSave(); finish() }
    }

    // ──────────────────────────────────────────────────────────────
    //  Inline Rich-Text & Structural Layout Editors
    // ──────────────────────────────────────────────────────────────

    private fun showInlineTextEditor(item: TextItem?, sx: Float, sy: Float, wx: Float, wy: Float) {
        if (isSwitchingTextEditor) return
        closeInlineEditor(commit = true)
        editingItem = item
        editWorldX = wx
        editWorldY = wy
        editRotation = item?.rotation ?: 0f
        editColor = item?.color ?: drawingView.currentColor
        editSize = item?.textSize ?: (drawingView.currentStrokeWidth * 4f).coerceIn(12f, 72f)

        val et = EditText(this).apply {
            setBackgroundColor(Color.parseColor("#EEFFFFFF"))
            setTextColor(editColor)
            textSize = editSize / PT_TO_PX
            setPadding(8, 8, 8, 8)
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        if (item != null) et.setText(item.text)
        activeEditText = et

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = sx.toInt()
            topMargin = sy.toInt()
        }
        canvasContainer.addView(et, lp)
        et.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)

        setupTextToolbarOverlay(sx, sy)
    }

    private fun setupTextToolbarOverlay(sx: Float, sy: Float) {
        val tb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#DD222222"))
            setPadding(8, 4, 8, 4)
        }
        activeToolbar = tb

        val btnBold = Button(this).apply { text = "B"; textSize = 12f; setTextColor(Color.WHITE) }
        val btnColor = Button(this).apply { text = "Color"; textSize = 11f; setTextColor(Color.WHITE) }
        val btnDone = Button(this).apply { text = "OK"; textSize = 11f; setTextColor(Color.GREEN) }
        tb.addView(btnBold); tb.addView(btnColor); tb.addView(btnDone)

        btnColor.setOnClickListener {
            showColorGridDialog { c -> editColor = c; activeEditText?.setTextColor(c) }
        }
        btnDone.setOnClickListener { closeInlineEditor(commit = true) }

        val tlp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = sx.toInt()
            topMargin = (sy - dp(45)).toInt().coerceAtLeast(0)
        }
        canvasContainer.addView(tb, tlp)
    }

    private fun closeInlineEditor(commit: Boolean) {
        val et = activeEditText ?: return
        val tb = activeToolbar
        activeEditText = null
        activeToolbar = null

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken, 0)

        val textStr = et.text.toString().trim()
        canvasContainer.removeView(et)
        if (tb != null) canvasContainer.removeView(tb)

        if (commit && textStr.isNotEmpty()) {
            val item = editingItem
            if (item != null) {
                item.text = textStr
                item.color = editColor
            } else {
                drawingView.addTextItem(textStr, editWorldX, editWorldY, editSize, editColor, editRotation)
            }
        } else if (commit && textStr.isEmpty() && editingItem != null) {
            drawingView.removeTextItem(editingItem!!)
        }
        editingItem = null
        drawingView.invalidate()
    }

    private fun showTableCellEditor(table: Any, row: Int, col: Int, sx: Float, sy: Float) {
        val et = EditText(this).apply {
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            textSize = 14f
            setPadding(4, 4, 4, 4)
        }
        activeCellEditText = et

        val lp = FrameLayout.LayoutParams(dp(100), dp(50)).apply {
            leftMargin = sx.toInt()
            topMargin = sy.toInt()
        }
        canvasContainer.addView(et, lp)
        et.requestFocus()

        val tb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.DARKGRAY)
        }
        tableToolbarOverlay = tb
        val bDone = Button(this).apply { text = "Done" }
        tb.addView(bDone)
        bDone.setOnClickListener { dismissCellEditor() }

        val tlp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = sx.toInt()
            topMargin = (sy - dp(40)).toInt().coerceAtLeast(0)
        }
        canvasContainer.addView(tb, tlp)
    }

    private fun dismissCellEditor() {
        val et = activeCellEditText ?: return
        canvasContainer.removeView(et)
        activeCellEditText = null
        tableToolbarOverlay?.let { canvasContainer.removeView(it) }
        tableToolbarOverlay = null
        drawingView.invalidate()
    }

    // ──────────────────────────────────────────────────────────────
    //  Lifecycle Hooks
    // ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        closeInlineEditor(commit = true)
        autoSave()
        if (isRecording) {
            AudioHelper.stopRecording()
            isRecording = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacks(autosaveRunnable)
        AudioHelper.releaseAll()
    }
}
