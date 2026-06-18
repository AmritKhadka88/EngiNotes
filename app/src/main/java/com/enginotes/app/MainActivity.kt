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

    // Convenient layout: page width = screen width, page height = screen height
    // Print layout: real A4/A3 sizes (existing Fixed/Paginated behaviour)
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

    // Audio recording state
    private var isRecording = false
    private var recordingFile: File? = null
    private var recordingStartMs = 0L

    // Autosave
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
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
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
            val page = doc.startPage(pi); page.canvas.drawBitmap(sb,0f,0f,Paint())
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
            val engFile = currentFileName?.let { File(getDrawingsFolder(),"$it.eng") } ?: run { Toast.makeText(this,"Save first!",Toast.LENGTH_SHORT).show()
                return@registerForActivityResult }
            val sb = StringBuilder()
            sb.append("=== $currentFileName ===\n\n")
            for (line in engFile.readLines()) if (line.startsWith("TEXT\u0001")) { val p=line.split("\u0001")
                if(p.size>7) sb.append(p.last().replace("\u0002","\n")).append("\n") }
            contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
            Toast.makeText(this,"TXT saved!",Toast.LENGTH_SHORT).show()
        } catch(e:Exception){ Toast.makeText(this,"TXT failed: ${e.message}",Toast.LENGTH_LONG).show() }
    }

    private val saveDocxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val engFile = currentFileName?.let { File(getDrawingsFolder(),"$it.eng") } ?: return@registerForActivityResult
            val doc = XWPFDocument()
            doc.createParagraph().createRun().apply { setText(currentFileName?:"EngiNote"); isBold=true; fontSize=18 }
            for(line in engFile.readLines()) if(line.startsWith("TEXT\u0001")){ val p=line.split("\u0001")
                if(p.size>7){ val run=doc.createParagraph().createRun(); run.setText(p.last().replace("\u0002","\n")); try{ run.fontSize=(p[4].toFloat()/PT_TO_PX).toInt().coerceIn(8,72) }catch(e:Exception){} } }
            val bmp=drawingView.exportBitmap()
            val imgFile=File(cacheDir,"export_tmp.png"); FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG,90,it) }
            doc.createParagraph().createRun().addPicture(imgFile.inputStream(),org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,"canvas.png",org.apache.poi.util.Units.toEMU(400.0),org.apache.poi.util.Units.toEMU(300.0))
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

        drawingView    = findViewById(R.id.drawingView)
        canvasContainer= findViewById(R.id.canvasContainer)
        tvTitle        = findViewById(R.id.tvTitle)
        btnLayoutToggle= findViewById(R.id.btnLayoutToggle)

        // Apply default paper from prefs
        val prefs = getPrefs()
        val defaultPaper = prefs.getString("default_paper","LINED") ?: "LINED"
        try { drawingView.paperType = PaperType.valueOf(defaultPaper) } catch(e:Exception){}

        val fileName = intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName; tvTitle.text = fileName
            val file = File(getDrawingsFolder(),"$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else { tvTitle.text = "New Note" }

        drawingView.migrateOldNotes(filesDir)
        lastSavedContent = drawingView.serialize()
        drawingView.arcDivisions = prefs.getInt("arc_divisions",3)

        // Default to convenient layout (paginated, screen-sized pages)
        applyConvenientLayout()

        // Active tool indicator (top-right overlay)
        tvActiveTool = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setBackgroundColor(Color.parseColor("#55000000"))
            setPadding(dp(3),0,dp(3),dp(1))
            text = "Select"
        }
        val ip = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        ip.gravity = Gravity.TOP or Gravity.END
        ip.topMargin = dp(4); ip.rightMargin = dp(4)
        canvasContainer.addView(tvActiveTool, ip)

        // DrawingView callbacks
        drawingView.onTextEditRequest = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTableCellEditRequest = { table, row, col, sx, sy -> dismissCellEditor()
            showTableCellEditor(table,row,col,sx,sy) }
        drawingView.onExportWindowSelected = { l,t,r,b -> exportWindowBitmap = drawingView.exportWindow(l,t,r,b)
            showExportWindowDialog() }

        // Audio item tap callback
        drawingView.onAudioItemTap = { item ->
            AudioHelper.togglePlay(item) { drawingView.invalidate() }
            drawingView.invalidate()
        }

        // Layout toggle button
        btnLayoutToggle.setOnClickListener { toggleLayout() }

        // ── Bottom toolbar buttons ──
        setupBottomToolbar()

        setActiveTool(null, Tool.SELECT, "Select")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activeEditText != null -> closeInlineEditor(commit = true)
                    activeCellEditText != null -> dismissCellEditor()
                    tableToolbarOverlay != null -> { tableToolbarOverlay?.let { canvasContainer.removeView(it) }; tableToolbarOverlay = null }
                    drawingView.currentTool != Tool.SELECT -> setActiveTool(null, Tool.SELECT, "Select")
                    else -> confirmThenExit()
                }
            }
        })

        // Start autosave loop
        autosaveHandler.postDelayed(autosaveRunnable, 10_000L)
    }

    private fun setupBottomToolbar() {
        findViewById<Button>(R.id.btnUndo).setOnClickListener { closeInlineEditor(true)
            drawingView.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { closeInlineEditor(true)
            drawingView.redo() }

        // Pen / Draw button → open pen sub-menu
        findViewById<Button>(R.id.btnDraw).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.PEN, "Pen")
        }
        // Long-press draw → show draw picker
        findViewById<Button>(R.id.btnDraw).setOnLongClickListener {
            showDrawPicker(it as Button)
            true
        }

        findViewById<Button>(R.id.btnQuickEraser).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.ERASER, "Eraser")
        }

        // Select button
        val btnSelect = findViewById<Button?>(R.id.btnSelect)
        btnSelect?.setOnClickListener { closeInlineEditor(true)
            setActiveTool(it as Button, Tool.SELECT, "Select") }

        findViewById<Button>(R.id.btnText).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.TEXT, "Text")
        }

        // Insert: image, camera, table, audio, PDF snip
        findViewById<Button>(R.id.btnInsert).setOnClickListener { showInsertMenu() }

        // Tools: color, size, fill
        findViewById<Button>(R.id.btnTools).setOnClickListener { showToolsMenu() }

        // Expand toggle shows/hides extra row
        val toolbarScroll = findViewById<View>(R.id.toolbarScroll)
        val btnExpand = findViewById<Button>(R.id.btnExpand)
        btnExpand.setOnClickListener {
            val show = toolbarScroll.visibility != View.VISIBLE
            toolbarScroll.visibility = if (show) View.VISIBLE else View.GONE
            btnExpand.text = if (show) "∧" else "∨"
        }

        // Extra row buttons
        val btnArc = findViewById<Button?>(R.id.btnArc)
        btnArc?.setOnClickListener { closeInlineEditor(true)
            setActiveTool(it as Button, Tool.ARC, "Arc") }

        val btnAutoSelect = findViewById<Button?>(R.id.btnAutoSelect)
        btnAutoSelect?.setOnClickListener { showAutoSelectModeDialog()
            setActiveToolbarBtn(it as Button) }

        val btnShapes = findViewById<Button?>(R.id.btnShapes)
        btnShapes?.setOnClickListener { showShapesPicker(it as Button) }

        val btnQuickColor = findViewById<Button?>(R.id.btnQuickColor)
        btnQuickColor?.setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c } }

        val btnQuickSize = findViewById<Button?>(R.id.btnQuickSize)
        btnQuickSize?.setOnClickListener { showSizePicker() }

        val btnQuickFill = findViewById<Button?>(R.id.btnQuickFill)
        btnQuickFill?.setOnClickListener { showColorGridDialog { c -> drawingView.fillColor = c }
            setActiveTool(null, Tool.FILL, "Fill") }
    }

    // ──────────────────────────────────────────────────────────────
    //  Layout switching
    // ──────────────────────────────────────────────────────────────

    private fun applyConvenientLayout() {
        isConvenientLayout = true
        btnLayoutToggle.text = "Convenient"
        btnLayoutToggle.setBackgroundColor(Color.parseColor("#EDE7F6"))
        btnLayoutToggle.setTextColor(Color.parseColor("#6200EE"))
        // Convenient = CONVENIENT view rendering logic inside CanvasMode mapping
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
    //  Tool helpers
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
                        setActiveTool(anchor, Tool.AUTOSELECT, "AutoSelect")
                    }
                }
            }.show()
    }

    private fun showShapesPicker(anchor: Button) {
        AlertDialog.Builder(this).setTitle("Insert Shape")
            .setItems(shapeSymbols.toTypedArray()) { _, i ->
                closeInlineEditor(true)
                setActiveTool(anchor, shapeTools[i], shapeSymbols[i].substring(2))
            }.show()
    }

    private fun showAutoSelectModeDialog() {
        val options = arrayOf("Rectangle Mode", "Freeform Mode", "Divide: Whole Shape", "Divide: Individual Strokes")
        AlertDialog.Builder(this).setTitle("Auto-Select Mode")
            .setItems(options) { _, i ->
                when(i) {
                    0 -> { drawingView.autoSelectShapeMode = AutoSelectShape.RECTANGLE; Toast.makeText(this,"Rectangle selection active",Toast.LENGTH_SHORT).show() }
                    1 -> { drawingView.autoSelectShapeMode = AutoSelectShape.FREEFORM; Toast.makeText(this,"Freeform selection active",Toast.LENGTH_SHORT).show() }
                    2 -> { drawingView.autoSelectDivideMode = AutoSelectDivide.WHOLE; Toast.makeText(this,"Will select whole elements",Toast.LENGTH_SHORT).show() }
                    3 -> { drawingView.autoSelectDivideMode = AutoSelectDivide.DIVIDED; Toast.makeText(this,"Will split intersected strokes",Toast.LENGTH_SHORT).show() }
                }
                drawingView.currentTool = Tool.AUTOSELECT
                tvActiveTool.text = "AutoSelect"
            }.show()
    }

    private fun showInsertMenu() {
        val popup = PopupMenu(this, findViewById(R.id.btnInsert))
        popup.menu.add("🖼 Image Gallery")
        popup.menu.add("📷 Take Photo")
        popup.menu.add("📊 Insert Chart/Graph")
        popup.menu.add("📅 Insert Grid Table")
        popup.menu.add("🎙 Record Audio Attachment")
        popup.menu.add("✂ Import PDF Snip")
        popup.setOnMenuItemClickListener { item ->
            closeInlineEditor(true)
            when(item.title) {
                "🖼 Image Gallery" -> pickImageLauncher.launch("image/*")
                "📷 Take Photo" -> launchCamera()
                "📊 Insert Chart/Graph" -> {
                    val intent = android.content.Intent(this, ChartActivity::class.java)
                    chartLauncher.launch(intent)
                }
                "📅 Insert Grid Table" -> showAddTableDialog()
                "🎙 Record Audio Attachment" -> toggleAudioRecording()
                "✂ Import PDF Snip" -> pickPdfLauncher.launch("application/pdf")
            }
            true
        }
        popup.show()
    }

    private fun showToolsMenu() {
        val popup = PopupMenu(this, findViewById(R.id.btnTools))
        popup.menu.add("🎨 Stroke Color")
        popup.menu.add("📐 Stroke Width")
        popup.menu.add("🪣 Shape Fill Color")
        popup.menu.add("📄 Background Paper Style")
        popup.menu.add("🧹 Clear Entire Canvas")
        popup.menu.add("💾 Manual Export As...")
        popup.menu.add("⚙ Structural Options")
        popup.setOnMenuItemClickListener { item ->
            when(item.title) {
                "🎨 Stroke Color" -> showColorGridDialog { c -> drawingView.currentColor = c }
                "📐 Stroke Width" -> showSizePicker()
                "🪣 Shape Fill Color" -> {
                    AlertDialog.Builder(this).setTitle("Shape Fill Style")
                        .setItems(arrayOf("Transparent / None", "Choose Color...")) { _, idx ->
                            if (idx == 0) drawingView.fillColor = Color.TRANSPARENT
                            else showColorGridDialog { c -> drawingView.fillColor = c }
                        }.show()
                }
                "📄 Background Paper Style" -> showPaperPicker()
                "🧹 Clear Entire Canvas" -> confirmThenClear()
                "💾 Manual Export As..." -> showExportMenu()
                "⚙ Structural Options" -> showStructuralSettings()
            }
            true
        }
        popup.show()
    }

    private fun showColorGridDialog(onColorSelected: (Int) -> Unit) {
        val colors = intArrayOf(Color.BLACK, Color.RED, Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"),
            Color.parseColor("#FF9800"), Color.parseColor("#9C27B0"), Color.parseColor("#795548"), Color.GRAY, Color.LTGRAY, Color.WHITE)
        val names = arrayOf("Black", "Red", "Blue", "Green", "Orange", "Purple", "Brown", "Grey", "Light Grey", "White")
        AlertDialog.Builder(this).setTitle("Select Color")
            .setItems(names) { _, i -> onColorSelected(colors[i]) }.show()
    }

    private fun showSizePicker() {
        val sizes = arrayOf("1 dp (Fine)", "2 dp (Normal)", "4 dp (Medium)", "7 dp (Thick)", "12 dp (Heavy Structural)")
        val vals = floatArrayOf(1f, 2f, 4f, 7f, 12f)
        AlertDialog.Builder(this).setTitle("Stroke Width")
            .setItems(sizes) { _, i -> drawingView.strokeWidth = vals[i] }.show()
    }

    private fun showPaperPicker() {
        val types = arrayOf("Blank White", "Blank Colored", "Lined Paper", "Grid Layout", "Dot Matrix", "Engineering Grid")
        val enums = arrayOf(PaperType.BLANK, PaperType.BLANK_COLORED, PaperType.LINED, PaperType.GRID, PaperType.DOTS, PaperType.ENGINEERING)
        AlertDialog.Builder(this).setTitle("Paper Background Style")
            .setItems(types) { _, i -> drawingView.paperType = enums[i] }.show()
    }

    private fun showExportMenu() {
        val options = arrayOf("Export PNG Image", "Export JPEG Image", "Export Windows Bitmap (BMP)", "Export Adobe PDF Document", "Export Editable Text (.txt)", "Export Word Document (.docx)")
        AlertDialog.Builder(this).setTitle("Export Drawing Note")
            .setItems(options) { _, idx ->
                val bmp = drawingView.exportBitmap() ?: return@setItems
                pendingExportBitmap = bmp
                val fallbackName = currentFileName ?: "Untitled_Note"
                when(idx) {
                    0 -> { pendingExportFormat = "png"; saveImageLauncher.launch("$fallbackName.png") }
                    1 -> { pendingExportFormat = "jpg"; saveImageLauncher.launch("$fallbackName.jpg") }
                    2 -> { pendingExportFormat = "bmp"; saveImageLauncher.launch("$fallbackName.bmp") }
                    3 -> { savePdfLauncher.launch("$fallbackName.pdf") }
                    4 -> { saveTxtLauncher.launch("$fallbackName.txt") }
                    5 -> { saveDocxLauncher.launch("$fallbackName.docx") }
                }
            }.show()
    }

    private fun showStructuralSettings() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val label = TextView(this).apply { text = "Arc Segments Dividers (2-12):"; textSize = 14f }
        val input = EditText(this).apply {
            setText(drawingView.arcDivisions.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        container.addView(label); container.addView(input)
        AlertDialog.Builder(this).setTitle("Structural Options").setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val divs = input.text.toString().toIntOrNull() ?: 3
                drawingView.arcDivisions = divs.coerceIn(2, 12)
                getPrefs().edit().putInt("arc_divisions", drawingView.arcDivisions).apply()
            }.setNegativeButton("Cancel", null).show()
    }

    // ──────────────────────────────────────────────────────────────
    //  Images Placement Architecture
    // ──────────────────────────────────────────────────────────────

    private fun launchCamera() {
        try {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "cam_${System.currentTimeMillis()}.jpg")
            cameraImageFile = file
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            takePictureLauncher.launch(uri)
        } catch(e:Exception){ Toast.makeText(this,"Camera failed: ${e.message}",Toast.LENGTH_SHORT).show() }
    }

    private fun insertImage(uri: Uri) {
        try {
            val file = File(cacheDir, "img_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            addImageFromFile(file)
        } catch(e:Exception){ Toast.makeText(this,"Insertion failed: ${e.message}",Toast.LENGTH_SHORT).show() }
    }

    private fun addImageFromFile(file: File) {
        val path = file.absolutePath
        val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return
        val ratio = bmp.width.toFloat() / bmp.height
        val w = drawingView.width / drawingView.getScaleFactor() * 0.5f
        val h = w / ratio
        drawingView.addImage(path, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
    }

    // ──────────────────────────────────────────────────────────────
    //  Table State Architecture Logic
    // ──────────────────────────────────────────────────────────────

    private fun showAddTableDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER
        }
        val rInput = EditText(this).apply { hint = "Rows"; setText("3"); inputType = android.text.InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val cInput = EditText(this).apply { hint = "Cols"; setText("3"); inputType = android.text.InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        container.addView(rInput); container.addView(TextView(this).apply { text = " × " }); container.addView(cInput)

        AlertDialog.Builder(this).setTitle("Insert Dynamic Table").setView(container)
            .setPositiveButton("Create") { _, _ ->
                val rows = rInput.text.toString().toIntOrNull() ?: 3
                val cols = cInput.text.toString().toIntOrNull() ?: 3
                drawingView.addTableItem(drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), rows.coerceIn(1,20), cols.coerceIn(1,20))
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showTableCellEditor(table: TableItem, row: Int, col: Int, screenX: Float, screenY: Float) {
        if (tableToolbarOverlay != null) { canvasContainer.removeView(tableToolbarOverlay); tableToolbarOverlay = null }
        val cell = table.cells[row][col]

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            elevation = dp(6).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun tbtn(label: String, onClick: () -> Unit) {
            val b = Button(this).apply { text = label; textSize = 11f; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)) }
            b.setOnClickListener { onClick() }
            row1.addView(b)
        }

        tbtn("🎨 Color") {
            showColorGridDialog { color -> cell.textColor = color; activeCellEditText?.setTextColor(color); drawingView.invalidate() }
        }
        tbtn("🪣 BG") {
            showColorGridDialog { color -> cell.bgColor = color; activeCellEditText?.setBackgroundColor(color); drawingView.invalidate() }
        }
        tbtn("➕ Row") { table.insertRow(row + 1); dismissCellEditor() }
        tbtn("➖ Row") { table.removeRow(row); dismissCellEditor() }
        tbtn("➕ Col") { table.insertColumn(col + 1); dismissCellEditor() }
        tbtn("➖ Col") { table.removeColumn(col); dismissCellEditor() }
        tbtn("🔗 Merge") { showMergeDialog(table, row, col) }

        container.addView(row1)

        val cellEt = EditText(this).apply {
            setText(cell.text)
            textSize = cell.textSize / PT_TO_PX
            setTextColor(cell.textColor)
            setBackgroundColor(cell.bgColor)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            gravity = when(cell.alignment) { 1 -> Gravity.CENTER; 2 -> Gravity.END; else -> Gravity.START }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
        }

        cellEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                cell.text = s?.toString() ?: ""
                drawingView.invalidate()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        container.addView(cellEt)
        activeCellEditText = cellEt
        activeCellToolbar = container
        tableToolbarOverlay = container

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(8); rightMargin = dp(8); bottomMargin = dp(8)
        }
        canvasContainer.addView(container, lp)

        cellEt.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(cellEt, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showMergeDialog(table: TableItem, row: Int, col: Int) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16),dp(16),dp(16),dp(16)); gravity = Gravity.CENTER }
        val rInput = EditText(this).apply { hint = "RowSpan"; setText("1"); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val cInput = EditText(this).apply { hint = "ColSpan"; setText("1"); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        container.addView(rInput); container.addView(TextView(this).apply { text = " × " }); container.addView(cInput)

        AlertDialog.Builder(this).setTitle("Merge Spans").setView(container)
            .setPositiveButton("Merge") { _, _ ->
                val rs = rInput.text.toString().toIntOrNull() ?: 1
                val cs = cInput.text.toString().toIntOrNull() ?: 1
                table.mergeCells(row, col, rs, cs)
                dismissCellEditor()
            }.setNegativeButton("Split/Clear") { _, _ ->
                table.clearMerge(row, col)
                dismissCellEditor()
            }.show()
    }

    private fun dismissCellEditor() {
        activeCellEditText?.let { et ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
        }
        activeCellToolbar?.let { canvasContainer.removeView(it) }
        tableToolbarOverlay?.let { canvasContainer.removeView(it) }
        activeCellEditText = null
        activeCellToolbar = null
        tableToolbarOverlay = null
        drawingView.invalidate()
    }

    // ──────────────────────────────────────────────────────────────
    //  Rich Text Picker / Formatting Setup
    // ──────────────────────────────────────────────────────────────

    private fun showInlineTextEditor(item: TextItem, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        if (isSwitchingTextEditor) return
        closeInlineEditor(commit = true)

        editingItem = item
        editWorldX = worldX; editWorldY = worldY
        editRotation = item.rotation; editColor = item.color; editSize = item.textSize
        
        // Decouple styles from initial snapshot
        pendingBold = item.isBold; pendingItalic = item.isItalic; pendingUnderline = item.isUnderline; pendingHighlight = item.highlightColor

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            setPadding(dp(4), dp(2), dp(4), dp(2))
            elevation = dp(4).toFloat()
            gravity = Gravity.CENTER_VERTICAL
        }

        fun addButton(label: String, onClick: (Button) -> Unit): Button {
            val b = Button(this).apply {
                text = label; textSize = 11f; minWidth = 0; minHeight = 0
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(4) }
            }
            b.setOnClickListener { onClick(b) }
            toolbar.addView(b)
            return b
        }

        val btnB = addButton("B") { b -> pendingBold = !pendingBold; b.isSelected = pendingBold; applyRichFormattingLive() }
        val btnI = addButton("I") { b -> pendingItalic = !pendingItalic; b.isSelected = pendingItalic; applyRichFormattingLive() }
        val btnU = addButton("U") { b -> pendingUnderline = !pendingUnderline; b.isSelected = pendingUnderline; applyRichFormattingLive() }
        
        btnB.isSelected = pendingBold
        btnI.isSelected = pendingItalic
        btnU.isSelected = pendingUnderline

        addButton("🎨 Color") {
            showColorGridDialog { color -> editColor = color; activeEditText?.setTextColor(color); applyRichFormattingLive() }
        }

        addButton("✏ Highlight") {
            val colors = intArrayOf(Color.TRANSPARENT, Color.YELLOW, Color.CYAN, Color.GREEN, Color.parseColor("#FFCDD2"))
            val names = arrayOf("Clear", "Yellow", "Cyan", "Green", "Red-Tint")
            AlertDialog.Builder(this).setTitle("Text Highlight")
                .setItems(names) { _, i -> pendingHighlight = if (i == 0) null else colors[i]; applyRichFormattingLive() }.show()
        }

        addButton("📐 Size") {
            val sizes = arrayOf("10 pt", "12 pt", "14 pt", "18 pt", "24 pt", "36 pt")
            val vals = floatArrayOf(10f, 12f, 14f, 18f, 24f, 36f)
            AlertDialog.Builder(this).setTitle("Font Size")
                .setItems(sizes) { _, i -> editSize = vals[i] * PT_TO_PX; activeEditText?.textSize = vals[i]; applyRichFormattingLive() }.show()
        }

        addButton("🔄 Rot") {
            val rotations = arrayOf("0°", "45°", "90°", "180°", "270°")
            val vals = floatArrayOf(0f, 45f, 90f, 180f, 270f)
            AlertDialog.Builder(this).setTitle("Structural Rotation")
                .setItems(rotations) { _, i -> editRotation = vals[i]; item.rotation = vals[i]; drawingView.invalidate() }.show()
        }

        addButton("🗑 Remove") {
            drawingView.removeTextItem(item)
            isSwitchingTextEditor = true
            closeInlineEditor(commit = false)
            isSwitchingTextEditor = false
        }

        addButton("✓") { closeInlineEditor(commit = true) }

        val et = EditText(this).apply {
            setText(item.text)
            textSize = item.textSize / PT_TO_PX
            setTextColor(item.color)
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(false)
            elevation = dp(4).toFloat()
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                item.text = s?.toString() ?: ""
                applyRichFormattingLive()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        et.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { closeInlineEditor(commit = true); true } else false
        }

        val scrollToolbar = HorizontalScrollView(this).apply {
            addView(toolbar)
            setHorizontalScrollBarEnabled(false)
        }

        val editorWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollToolbar)
            addView(et)
        }

        activeEditText = et
        activeToolbar = editorWrapper

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(8); rightMargin = dp(8); bottomMargin = dp(8)
        }
        canvasContainer.addView(editorWrapper, lp)

        et.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun applyRichFormattingLive() {
        val item = editingItem ?: return
        item.color = editColor
        item.textSize = editSize
        item.isBold = pendingBold
        item.isItalic = pendingItalic
        item.isUnderline = pendingUnderline
        item.highlightColor = pendingHighlight ?: Color.TRANSPARENT
        drawingView.invalidate()
    }

    private fun closeInlineEditor(commit: Boolean) {
        val et = activeEditText ?: return
        val item = editingItem
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken, 0)

        canvasContainer.removeView(activeToolbar)
        activeEditText = null
        activeToolbar = null
        editingItem = null

        if (item != null) {
            if (commit) {
                item.text = et.text.toString()
                item.color = editColor
                item.textSize = editSize
                item.isBold = pendingBold
                item.isItalic = pendingItalic
                item.isUnderline = pendingUnderline
                item.highlightColor = pendingHighlight ?: Color.TRANSPARENT
                if (item.text.isBlank()) drawingView.removeTextItem(item)
            }
        }
        drawingView.invalidate()
    }

    // ──────────────────────────────────────────────────────────────
    //  Audio Capture Mechanisms
    // ──────────────────────────────────────────────────────────────

    private fun toggleAudioRecording() {
        if (!isRecording) {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "rec_${System.currentTimeMillis()}.mp3")
            recordingFile = file
            recordingStartMs = System.currentTimeMillis()
            AudioHelper.startRecording(file.absolutePath)
            isRecording = true
            Toast.makeText(this, "🎙 Recording structural log...", Toast.LENGTH_SHORT).show()
        } else {
            AudioHelper.stopRecording()
            isRecording = false
            val duration = (System.currentTimeMillis() - recordingStartMs).toInt()
            recordingFile?.let {
                drawingView.addAudioItem(it.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), duration)
                Toast.makeText(this, "Audio attachment embedded!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Window Selection Subscreen Overlay
    // ──────────────────────────────────────────────────────────────

    private fun showExportWindowDialog() {
        val bmp = exportWindowBitmap ?: return
        val iv = ImageView(this).apply { setImageBitmap(bmp); setAdjustViewBounds(true) }
        val options = arrayOf("Save Window Screen as PNG Image", "Save Window Screen as Adobe PDF Document")
        
        AlertDialog.Builder(this)
            .setTitle("Captured Blueprint Window")
            .setView(iv)
            .setItems(options) { _, idx ->
                pendingExportBitmap = bmp
                val fallbackName = "Window_Snip_${System.currentTimeMillis()}"
                if (idx == 0) { pendingExportFormat = "png"; saveImageLauncher.launch("$fallbackName.png") }
                else { savePdfLauncher.launch("$fallbackName.pdf") }
            }
            .setNegativeButton("Dismiss") { _, _ -> exportWindowBitmap = null }
            .show()
    }

    // ──────────────────────────────────────────────────────────────
    //  Persistence Engine Operations
    // ──────────────────────────────────────────────────────────────

    private fun getDrawingsFolder(): File {
        val folder = File(filesDir, "drawings")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun autoSave() {
        val name = currentFileName ?: return
        val currentStr = drawingView.serialize()
        if (currentStr == lastSavedContent) return
        try {
            val file = File(getDrawingsFolder(), "$name.eng")
            FileOutputStream(file).use { it.write(currentStr.toByteArray()) }
            lastSavedContent = currentStr
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveCurrent() {
        if (currentFileName == null) {
            val input = EditText(this).apply { hint = "Note Title Layout Name" }
            AlertDialog.Builder(this).setTitle("Save Note Drawing").setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val title = input.text.toString().trim()
                    if (title.isNotEmpty()) {
                        currentFileName = title
                        tvTitle.text = title
                        autoSave()
                        Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton("Cancel", null).show()
        } else {
            autoSave()
            Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmThenExit() {
        val changed = drawingView.serialize() != lastSavedContent && drawingView.hasContent()
        if (!changed) { finish(); return }
        if (getPrefs().getBoolean("autosave", true)) { autoSave(); finish(); return }
        if (getPrefs().getBoolean("confirm_exit_clear", true)) {
            AlertDialog.Builder(this).setTitle("Unsaved Changes").setMessage("Save changes before leaving?")
                .setPositiveButton("Save") { _, _ -> saveCurrent(); finish() }
                .setNeutralButton("Don't Save") { _, _ -> finish() }
                .setNegativeButton("Cancel", null).show()
        } else { autoSave(); finish() }
    }

    private fun confirmThenClear() {
        if (getPrefs().getBoolean("confirm_exit_clear", true) && drawingView.hasContent()) {
            AlertDialog.Builder(this).setTitle("Clear Canvas Elements?").setMessage("This will permanently remove everything.")
                .setPositiveButton("Clear Everything") { _, _ -> drawingView.clearAll() }.setNegativeButton("Cancel", null).show()
        } else drawingView.clearAll()
    }

    private fun writeBmp(bitmap: Bitmap, outputStream: java.io.OutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        val mDataBuffer = IntArray(width * height)
        bitmap.getPixels(mDataBuffer, 0, width, 0, 0, width, height)
        
        val buffer = ByteArray(54 + width * height * 3)
        buffer[0] = 'B'.toByte(); buffer[1] = 'M'.toByte()
        val size = buffer.size
        buffer[2] = (size and 0xff).toByte()
        buffer[3] = ((size shr 8) and 0xff).toByte()
        buffer[4] = ((size shr 16) and 0xff).toByte()
        buffer[5] = ((size shr 24) and 0xff).toByte()
        buffer[10] = 54
        buffer[14] = 40
        buffer[15] = 0; buffer[16] = 0; buffer[17] = 0
        buffer[18] = (width and 0xff).toByte()
        buffer[19] = ((width shr 8) and 0xff).toByte()
        buffer[20] = ((width shr 16) and 0xff).toByte()
        buffer[21] = ((width shr 24) and 0xff).toByte()
        buffer[22] = (height and 0xff).toByte()
        buffer[23] = ((height shr 8) and 0xff).toByte()
        buffer[24] = ((height shr 16) and 0xff).toByte()
        buffer[25] = ((height shr 24) and 0xff).toByte()
        buffer[26] = 1
        buffer[28] = 24
        
        var p = 54
        for (i in height - 1 downTo 0) {
            for (j in 0 until width) {
                val c = mDataBuffer[i * width + j]
                buffer[p] = (c and 0xff).toByte()
                buffer[p + 1] = ((c shr 8) and 0xff).toByte()
                buffer[p + 2] = ((c shr 16) and 0xff).toByte()
                p += 3
            }
        }
        outputStream.write(buffer)
    }

    // ──────────────────────────────────────────────────────────────
    //  Lifecycle Hooks
    // ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        closeInlineEditor(true)
        autoSave()
        if (isRecording) { AudioHelper.stopRecording(); isRecording = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacks(autosaveRunnable)
        AudioHelper.releaseAll()
    }
}
