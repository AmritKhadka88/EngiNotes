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
    private var editWorldX = 0f; private var editWorldY = 0f
    private var editRotation = 0f; private var editColor = Color.BLACK
    private var editSize = 12f * 1.333f
    private var pendingBold = false; private var pendingItalic = false
    private var pendingUnderline = false; private var pendingHighlight: Int? = null
    private var cameraImageFile: File? = null
    private var activeToolbarButton: Button? = null
    private var isSwitchingTextEditor = false
    private var exportWindowBitmap: Bitmap? = null
    private var pendingExportBitmap: Bitmap? = null
    private var pendingExportFormat: String = "png"

    private var activeCellEditText: EditText? = null
    private var activeCellToolbar: LinearLayout? = null
    private var tableToolbarOverlay: LinearLayout? = null

    private var isRecording = false
    private var recordingFile: File? = null
    private var recordingStartMs = 0L

    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = object : Runnable {
        override fun run() {
            if (getPrefs().getBoolean("autosave", true)) autoSave()
            autosaveHandler.postDelayed(this, 10_000L)
        }
    }

    // ── Launchers ─────────────────────────────────────────────────

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

    private val snipLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("snip_image_path") ?: return@registerForActivityResult
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return@registerForActivityResult
            val ratio = bmp.width.toFloat() / bmp.height
            val w = drawingView.width / drawingView.getScaleFactor() * 0.75f
            val h = w / ratio
            drawingView.addImage(path, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
            Toast.makeText(this, "PDF snip added!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) snipLauncher.launch(android.content.Intent(this, PdfViewerActivity::class.java).putExtra("pdf_uri", uri.toString()))
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
            val pw = (bmp.width*scale).toInt().coerceAtLeast(1); val ph = (bmp.height*scale).toInt().coerceAtLeast(1)
            val sb = if (scale < 1f) Bitmap.createScaledBitmap(bmp,pw,ph,true) else bmp
            val doc = PdfDocument(); val pi = PdfDocument.PageInfo.Builder(pw,ph,1).create()
            val page = doc.startPage(pi); page.canvas.drawBitmap(sb,0f,0f,Paint()); doc.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }; doc.close()
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
            val engFile = currentFileName?.let { File(getDrawingsFolder(),"$it.eng") } ?: run { Toast.makeText(this,"Save first!",Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
            val sb = StringBuilder(); sb.append("=== $currentFileName ===\n\n")
            for (line in engFile.readLines()) if (line.startsWith("TEXT\u0001")) { val p=line.split("\u0001"); if(p.size>7) sb.append(p.last().replace("\u0002","\n")).append("\n") }
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
            val bmp=drawingView.exportBitmap(); val imgFile=File(cacheDir,"export_tmp.png"); FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG,90,it) }
            doc.createParagraph().createRun().addPicture(imgFile.inputStream(),org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,"canvas.png",org.apache.poi.util.Units.toEMU(400.0),org.apache.poi.util.Units.toEMU(300.0))
            contentResolver.openOutputStream(uri)?.use { doc.write(it) }; doc.close()
            Toast.makeText(this,"DOCX saved!",Toast.LENGTH_SHORT).show()
        } catch(e:Exception){ Toast.makeText(this,"DOCX failed: ${e.message}",Toast.LENGTH_LONG).show() }
    }

    // ── onCreate ──────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView     = findViewById(R.id.drawingView)
        canvasContainer = findViewById(R.id.canvasContainer)
        tvTitle         = findViewById(R.id.tvTitle)
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle)

        val prefs = getPrefs()
        try { drawingView.paperType = PaperType.valueOf(prefs.getString("default_paper","LINED") ?: "LINED") } catch(e:Exception){}

        val fileName = intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName; tvTitle.text = fileName
            val file = File(getDrawingsFolder(),"$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else { tvTitle.text = "New Note" }

        drawingView.migrateOldNotes(filesDir)
        lastSavedContent = drawingView.serialize()
        drawingView.arcDivisions = prefs.getInt("arc_divisions",3)

        applyConvenientLayout()

        tvActiveTool = TextView(this).apply {
            textSize = 9f; setTextColor(Color.parseColor("#CCFFFFFF"))
            setBackgroundColor(Color.parseColor("#55000000"))
            setPadding(dp(3),0,dp(3),dp(1)); text = "Select"
        }
        val ip = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        ip.gravity = Gravity.TOP or Gravity.END; ip.topMargin = dp(4); ip.rightMargin = dp(4)
        canvasContainer.addView(tvActiveTool, ip)

        drawingView.onTextEditRequest       = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTableCellEditRequest  = { table, row, col, sx, sy -> dismissCellEditor(); showTableCellEditor(table,row,col,sx,sy) }
        drawingView.onExportWindowSelected  = { l,t,r,b -> exportWindowBitmap = drawingView.exportWindow(l,t,r,b); showExportWindowDialog() }
        drawingView.onAudioItemTap          = { item -> AudioHelper.togglePlay(item) { drawingView.invalidate() }; drawingView.invalidate() }

        btnLayoutToggle.setOnClickListener { toggleLayout() }
        setupBottomToolbar()
        setActiveTool(null, Tool.SELECT, "Select")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activeEditText != null -> closeInlineEditor(true)
                    activeCellEditText != null -> dismissCellEditor()
                    tableToolbarOverlay != null -> { tableToolbarOverlay?.let { canvasContainer.removeView(it) }; tableToolbarOverlay = null }
                    drawingView.currentTool != Tool.SELECT -> setActiveTool(null, Tool.SELECT, "Select")
                    else -> confirmThenExit()
                }
            }
        })

        autosaveHandler.postDelayed(autosaveRunnable, 10_000L)
    }

    // ── Bottom toolbar ────────────────────────────────────────────

    private fun setupBottomToolbar() {
        findViewById<Button>(R.id.btnUndo).setOnClickListener { closeInlineEditor(true); drawingView.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { closeInlineEditor(true); drawingView.redo() }
        findViewById<Button>(R.id.btnDraw).setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.PEN, "Pen") }
        findViewById<Button>(R.id.btnDraw).setOnLongClickListener { showDrawPicker(it as Button); true }
        findViewById<Button>(R.id.btnQuickEraser).setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.ERASER, "Eraser") }
        findViewById<Button?>(R.id.btnSelect)?.setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.SELECT, "Select") }
        findViewById<Button>(R.id.btnText).setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.TEXT, "Text") }
        findViewById<Button>(R.id.btnInsert).setOnClickListener { showInsertMenu() }
        findViewById<Button>(R.id.btnTools).setOnClickListener { showToolsMenu() }

        val toolbarScroll = findViewById<View>(R.id.toolbarScroll)
        val btnExpand = findViewById<Button>(R.id.btnExpand)
        btnExpand.setOnClickListener {
            val show = toolbarScroll.visibility != View.VISIBLE
            toolbarScroll.visibility = if (show) View.VISIBLE else View.GONE
            btnExpand.text = if (show) "∧" else "∨"
        }

        findViewById<Button?>(R.id.btnArc)?.setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.ARC, "Arc") }
        findViewById<Button?>(R.id.btnAutoSelect)?.setOnClickListener { showAutoSelectModeDialog(); setActiveToolbarBtn(it as Button) }
        findViewById<Button?>(R.id.btnShapes)?.setOnClickListener { showShapesPicker(it as Button) }
        findViewById<Button?>(R.id.btnQuickColor)?.setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c } }
        findViewById<Button?>(R.id.btnQuickSize)?.setOnClickListener { showSizePicker() }
        findViewById<Button?>(R.id.btnQuickFill)?.setOnClickListener { showColorGridDialog { c -> drawingView.fillColor = c }; setActiveTool(null, Tool.FILL, "Fill") }

        val btnMenu = findViewById<Button?>(R.id.btnMenu)
        btnMenu?.setOnClickListener { onMenuClick(it) }
    }

    // ── Layout switching ──────────────────────────────────────────

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
                .setMessage("Print layout uses real A4 sizes. Text items will appear smaller.\n\nRearrange text to fit?")
                .setPositiveButton("Rearrange") { _, _ -> applyPrintLayout(); drawingView.rearrangeTextForPrint() }
                .setNegativeButton("Just Switch") { _, _ -> applyPrintLayout() }
                .setNeutralButton("Cancel", null).show()
        } else { applyConvenientLayout() }
    }

    // ── Tool helpers ──────────────────────────────────────────────

    private fun setActiveTool(btn: Button?, tool: Tool, label: String) { drawingView.currentTool = tool; tvActiveTool.text = label; setActiveToolbarBtn(btn) }
    private fun setActiveToolbarBtn(btn: Button?) { activeToolbarButton?.isSelected = false; activeToolbarButton = btn; btn?.isSelected = true }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    // ── Menu actions ──────────────────────────────────────────────

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
                    if(drawingView.canvasMode == CanvasMode.INFINITE || drawingView.canvasMode == CanvasMode.CONVENIENT) {
                        Toast.makeText(this,"Draw a rectangle to select export area",Toast.LENGTH_SHORT).show()
                        setActiveTool(null,Tool.EXPORT_WINDOW,"ExportWin")
                    } else Toast.makeText(this,"Switch to Infinite/Convenient canvas for window export",Toast.LENGTH_SHORT).show()
                }
                "Clear Canvas" -> confirmThenClear()
                "Delete This Note" -> deleteCurrentNote()
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

    private fun showAddToBookDialog() {
        val books = File(filesDir,"books").listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        AlertDialog.Builder(this).setTitle("Add to Book")
            .setItems(books.toTypedArray()) { _, i ->
                val targetBook = books[i]
                val name = currentFileName ?: "Note_${System.currentTimeMillis()}"
                currentFileName = name; tvTitle.text = name
                val targetFolder = File(File(filesDir,"books"), targetBook).also { it.mkdirs() }
                File(targetFolder,"$name.eng").writeText(drawingView.serialize())
                lastSavedContent = drawingView.serialize()
                Toast.makeText(this,"Added to $targetBook",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun showDrawPicker(anchor: Button) {
        AlertDialog.Builder(this).setTitle("Draw Tool")
            .setItems(arrayOf("👆 Select","✏ Pen","⌇ Arc","🪣 Fill","🔲 AutoSelect")) { _, i ->
                when(i) {
                    0 -> setActiveTool(anchor, Tool.SELECT, "Select")
                    1 -> setActiveTool(anchor, Tool.PEN, "Pen")
                    2 -> setActiveTool(anchor, Tool.ARC, "Arc")
                    3 -> { showColorGridDialog { c -> drawingView.fillColor=c }; setActiveTool(anchor, Tool.FILL, "Fill") }
                    4 -> { showAutoSelectModeDialog(); setActiveToolbarBtn(anchor) }
                }
            }.show()
    }

    private fun showShapesPicker(anchor: Button) {
        AlertDialog.Builder(this).setTitle("Shapes")
            .setItems(shapeSymbols.toTypedArray()) { _, i -> setActiveTool(anchor, shapeTools[i], shapeSymbols[i].take(2)) }.show()
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
                        Toast.makeText(this, if (drawingView.eraserMode==EraserMode.OBJECT) "Object Eraser" else "Area Eraser", Toast.LENGTH_SHORT).show()
                    } else setActiveTool(null, Tool.ERASER, "Eraser")
                    1 -> showColorGridDialog { c -> drawingView.currentColor = c; if(drawingView.currentTool==Tool.ERASER) drawingView.currentTool=Tool.PEN }
                    2 -> { drawingView.fillShapes = !drawingView.fillShapes; Toast.makeText(this, if(drawingView.fillShapes)"Fill: On" else "Fill: Off",Toast.LENGTH_SHORT).show() }
                    3 -> showSizePicker()
                }
            }.show()
    }

    // ── Settings ──────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val prefs = getPrefs()
        val container = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        fun hdr(t:String){ container.addView(TextView(this).apply{ text=t;textSize=11f;setTextColor(Color.parseColor("#7B61FF"));setPadding(0,dp(10),0,dp(2));typeface=Typeface.DEFAULT_BOLD }) }
        fun div(){ container.addView(View(this).apply{ layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1)).also{it.setMargins(0,dp(8),0,dp(4))};setBackgroundColor(Color.LTGRAY) }) }

        hdr("GENERAL")
        val confirmCb = CheckBox(this).apply{ text="Confirm before exit or clear canvas"; isChecked=prefs.getBoolean("confirm_exit_clear",true) }; container.addView(confirmCb)
        val autosaveCb = CheckBox(this).apply{ text="Autosave every 10 seconds"; isChecked=prefs.getBoolean("autosave",true) }; container.addView(autosaveCb)

        div(); hdr("PAPER")
        val paperLabels = arrayOf("Blank","Lined","Graph Grid","Dot Grid","Engineering","Coloured")
        val paperValues = arrayOf("BLANK","LINED","GRID","DOTS","ENGINEERING","BLANK_COLORED")
        var selPaper = prefs.getString("default_paper","LINED") ?: "LINED"
        val paperLbl = TextView(this).apply{ textSize=15f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(8),0,dp(8)) }
        fun refP(){ paperLbl.text = "Default: ${paperLabels[paperValues.indexOf(selPaper).coerceAtLeast(0)]}  (tap)" }
        refP(); paperLbl.setOnClickListener{ AlertDialog.Builder(this).setTitle("Default Paper").setItems(paperLabels){ _,i-> selPaper=paperValues[i]; refP() }.show() }
        container.addView(paperLbl)

        div(); hdr("DRAWING")
        val arcDivPref = prefs.getInt("arc_divisions",3)
        val arcRow = LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(8),0,dp(8)) }
        val arcLbl = TextView(this).apply{ text="Arc divisions"; textSize=15f; layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
        val arcInput = EditText(this).apply{ inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText(arcDivPref.toString()); layoutParams=LinearLayout.LayoutParams(dp(60),LinearLayout.LayoutParams.WRAP_CONTENT); gravity=Gravity.CENTER }
        arcRow.addView(arcLbl); arcRow.addView(arcInput); container.addView(arcRow)

        div(); hdr("PAGE SETUP")
        val modeLbl = TextView(this); val sizeLbl = TextView(this); val orientLbl = TextView(this)
        fun refPage(){
            modeLbl.text = if(isConvenientLayout) "Layout: Convenient  (use toggle in top bar)" else "Layout: Print (A4)  (use toggle in top bar)"
            sizeLbl.text = "Size: ${drawingView.paperSize.name}  (tap)"
            orientLbl.text = "Orientation: ${if(drawingView.pageOrientation==Orientation.PORTRAIT)"Portrait" else "Landscape"}  (tap)"
            sizeLbl.visibility = if(!isConvenientLayout) View.VISIBLE else View.GONE
            orientLbl.visibility = sizeLbl.visibility
        }
        refPage()
        for(lbl in listOf(modeLbl,sizeLbl,orientLbl)){ lbl.textSize=15f; lbl.setTextColor(Color.parseColor("#1565C0")); lbl.setPadding(0,dp(8),0,dp(8)); container.addView(lbl) }
        sizeLbl.setOnClickListener{ AlertDialog.Builder(this).setTitle("Paper Size").setItems(PaperSizeOption.values().map{it.name}.toTypedArray()){ _,i-> drawingView.paperSize=PaperSizeOption.values()[i]; drawingView.invalidate(); refPage() }.show() }
        orientLbl.setOnClickListener{ AlertDialog.Builder(this).setTitle("Orientation").setItems(arrayOf("Portrait","Landscape")){ _,i-> drawingView.pageOrientation=if(i==0)Orientation.PORTRAIT else Orientation.LANDSCAPE; drawingView.invalidate(); refPage() }.show() }

        val scroll = ScrollView(this).apply{ addView(container) }
        AlertDialog.Builder(this).setTitle("⚙ Settings").setView(scroll)
            .setPositiveButton("Done") { _,_ ->
                prefs.edit()
                    .putBoolean("confirm_exit_clear",confirmCb.isChecked)
                    .putBoolean("autosave",autosaveCb.isChecked)
                    .putString("default_paper",selPaper)
                    .putInt("arc_divisions",(arcInput.text.toString().toIntOrNull()?:3).coerceIn(2,12))
                    .apply()
                drawingView.arcDivisions = prefs.getInt("arc_divisions",3)
                try { drawingView.paperType = PaperType.valueOf(selPaper) } catch(e:Exception){}
                drawingView.invalidate()
            }.show()
    }

    // ── Audio recording ───────────────────────────────────────────

    private fun showAudioRecordDialog() {
        if (isRecording) { stopRecordingAndPlace(); return }

        val container = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(16),dp(24),dp(16)); gravity=Gravity.CENTER }
        val tvStatus = TextView(this).apply { text="🎙 Ready to record"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Color.parseColor("#212121")) }
        val tvTimer  = TextView(this).apply { text="00:00"; textSize=32f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(Color.parseColor("#6200EE")) }
        val titleInput = EditText(this).apply { hint="Audio title (optional)"; textSize=14f; setText("Recording ${System.currentTimeMillis()/1000%10000}") }
        container.addView(tvStatus); container.addView(tvTimer); container.addView(titleInput)

        var timerHandler: Handler? = null
        var startMs = 0L

        val dialog = AlertDialog.Builder(this).setTitle("Record Audio").setView(container)
            .setPositiveButton("⏺ Start") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ ->
                timerHandler?.removeCallbacksAndMessages(null)
                if(isRecording) { AudioHelper.stopRecording(); isRecording = false }
            }.create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!isRecording) {
                val folder = File(filesDir,"audio").also{ it.mkdirs() }
                val outFile = File(folder,"rec_${System.currentTimeMillis()}.m4a")
                if (AudioHelper.startRecording(outFile)) {
                    isRecording = true; recordingFile = outFile; recordingStartMs = System.currentTimeMillis()
                    tvStatus.text = "🔴 Recording…"; tvStatus.setTextColor(Color.RED)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "⏹ Stop & Place"
                    startMs = System.currentTimeMillis()
                    timerHandler = Handler(Looper.getMainLooper())
                    val tick = object : Runnable {
                        override fun run() {
                            val elapsed = (System.currentTimeMillis()-startMs)/1000
                            tvTimer.text = "%02d:%02d".format(elapsed/60, elapsed%60)
                            timerHandler?.postDelayed(this,500)
                        }
                    }
                    timerHandler?.post(tick)
                } else { Toast.makeText(this,"Could not start recording. Check microphone permission.",Toast.LENGTH_SHORT).show() }
            } else {
                timerHandler?.removeCallbacksAndMessages(null)
                dialog.dismiss()
                stopRecordingAndPlace(titleInput.text.toString().trim().ifEmpty { "Audio" })
            }
        }
    }

    private fun stopRecordingAndPlace(title: String = "Audio") {
        AudioHelper.stopRecording(); isRecording = false
        val file = recordingFile ?: return
        var durationMs = 0L
        try {
            val mmr = MediaMetadataRetriever(); mmr.setDataSource(file.absolutePath)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
        } catch(e:Exception){}
        drawingView.addAudioItem(file.absolutePath, title, durationMs)
        Toast.makeText(this,"Audio placed on canvas. Tap to play.",Toast.LENGTH_SHORT).show()
        recordingFile = null
    }

    // ── Export helpers ────────────────────────────────────────────

    private fun showExportWindowDialog() {
        val bmp = exportWindowBitmap ?: return
        val name = (currentFileName ?: "EngiNote_${System.currentTimeMillis()}").replace(" ","_")
        AlertDialog.Builder(this).setTitle("Export Selection as…")
            .setItems(arrayOf("📄 PDF","🖼 JPG","🖼 PNG","🖼 BMP")) { _,i ->
                pendingExportBitmap = bmp
                when(i){ 0->savePdfLauncher.launch("${name}_window.pdf"); 1->{ pendingExportFormat="jpg"; saveImageLauncher.launch("${name}_window.jpg") }; 2->{ pendingExportFormat="png"; saveImageLauncher.launch("${name}_window.png") }; 3->{ pendingExportFormat="bmp"; saveImageLauncher.launch("${name}_window.bmp") } }
            }.setNegativeButton("Cancel"){ _,_ -> exportWindowBitmap=null }.show()
    }

    private fun showExportDialog() {
        val name = (currentFileName ?: "EngiNote_${System.currentTimeMillis()}").replace(" ","_")
        AlertDialog.Builder(this).setTitle("Export as…")
            .setItems(arrayOf("📄 PDF","🖼 JPG","🖼 PNG","🖼 BMP","📝 TXT","📝 DOCX")) { _,i ->
                pendingExportBitmap = drawingView.exportBitmap()
                when(i){ 0->savePdfLauncher.launch("$name.pdf"); 1->{ pendingExportFormat="jpg"; saveImageLauncher.launch("$name.jpg") }; 2->{ pendingExportFormat="png"; saveImageLauncher.launch("$name.png") }; 3->{ pendingExportFormat="bmp"; saveImageLauncher.launch("$name.bmp") }; 4->saveTxtLauncher.launch("$name.txt"); 5->saveDocxLauncher.launch("$name.docx") }
            }.show()
    }

    private fun writeBmp(bmp: Bitmap, fos: java.io.OutputStream) {
        val w=bmp.width; val h=bmp.height; val pixels=IntArray(w*h); bmp.getPixels(pixels,0,w,0,0,w,h)
        val rowSize=(w*3+3)/4*4; val pixelArraySize=rowSize*h; val fileSize=54+pixelArraySize
        fun wi(v:Int)= fos.write(byteArrayOf(v.toByte(),(v shr 8).toByte(),(v shr 16).toByte(),(v shr 24).toByte()))
        fun ws(v:Int)= fos.write(byteArrayOf(v.toByte(),(v shr 8).toByte()))
        fos.write('B'.code); fos.write('M'.code); wi(fileSize); wi(0); wi(54); wi(40); wi(w); wi(h); ws(1); ws(24); wi(0); wi(pixelArraySize); wi(2835); wi(2835); wi(0); wi(0)
        val row=ByteArray(rowSize)
        for(y in h-1 downTo 0){ for(x in 0 until w){ val p=pixels[y*w+x]; row[x*3]=(p and 0xFF).toByte(); row[x*3+1]=((p shr 8) and 0xFF).toByte(); row[x*3+2]=((p shr 16) and 0xFF).toByte() }; fos.write(row) }
    }

    // ── Color / size pickers ──────────────────────────────────────

    private fun showColorGridDialog(onPicked: (Int) -> Unit) {
        val grid = GridLayout(this).apply{ columnCount=10; setPadding(dp(10),dp(10),dp(10),dp(10)) }
        lateinit var dialog: AlertDialog; var popup: android.widget.PopupWindow? = null
        fun addSwatch(color:Int){ val s=View(this); val p=GridLayout.LayoutParams(); p.width=dp(28); p.height=dp(28); p.setMargins(dp(2),dp(2),dp(2),dp(2)); s.layoutParams=p; s.setBackgroundColor(color)
            s.setOnTouchListener{ v,e -> when(e.actionMasked){ android.view.MotionEvent.ACTION_DOWN->{ val pv=View(this); pv.setBackgroundColor(color); val pw=android.widget.PopupWindow(pv,dp(40),dp(40)); pw.showAsDropDown(v,0,-dp(70)); popup=pw }; android.view.MotionEvent.ACTION_UP->{ popup?.dismiss(); popup=null; onPicked(color); dialog.dismiss() }; android.view.MotionEvent.ACTION_CANCEL->{ popup?.dismiss(); popup=null } }; true }; grid.addView(s) }
        for(i in 0..9) addSwatch(Color.HSVToColor(floatArrayOf(0f,0f,1f-i/9f)))
        for(value in listOf(1.0f,0.85f,0.7f,0.5f,0.3f)) for(hue in listOf(0,30,60,90,120,150,180,210,240,270,300,330)) addSwatch(Color.HSVToColor(floatArrayOf(hue.toFloat(),0.65f,value)))
        val scroll=ScrollView(this); scroll.addView(grid)
        dialog=AlertDialog.Builder(this).setTitle("Color").setView(scroll).create(); dialog.show()
    }

    private fun showSizePicker() {
        val tool=drawingView.currentTool
        val current: Float; val maxSize: Int; val label: String
        when(tool){ Tool.ERASER->{ current=drawingView.eraserSize; maxSize=200; label="Eraser Size" }; Tool.TEXT->{ current=drawingView.defaultTextSize/PT_TO_PX; maxSize=144; label="Font Size (pt)" }; else->{ current=drawingView.currentStrokeWidth; maxSize=100; label="Stroke Width" } }
        val container=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(50,30,50,10) }
        val tv=TextView(this).apply{ text="$label: ${current.toInt()}"; textSize=16f }; container.addView(tv)
        val seek=SeekBar(this).apply{ max=maxSize; progress=current.toInt().coerceIn(1,maxSize); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(sb:SeekBar?,v:Int,f:Boolean){ val vv=v.coerceAtLeast(1); tv.text="$label: $vv"; when(tool){ Tool.ERASER->drawingView.eraserSize=vv.toFloat(); Tool.TEXT->drawingView.defaultTextSize=vv*PT_TO_PX; else->drawingView.currentStrokeWidth=vv.toFloat() } }; override fun onStartTrackingTouch(sb:SeekBar?){}; override fun onStopTrackingTouch(sb:SeekBar?){} }) }; container.addView(seek)
        AlertDialog.Builder(this).setTitle(label).setView(container).setPositiveButton("Done",null).show()
    }

    private fun showAutoSelectModeDialog() {
        val modes = arrayOf("▭ Rectangle – Whole","▭ Rectangle – Divided","✏ Freeform – Whole","✏ Freeform – Divided")
        AlertDialog.Builder(this).setTitle("AutoSelect Mode").setItems(modes){ _,i ->
            when(i){ 0->{ drawingView.autoSelectShape=AutoSelectShape.RECTANGLE; drawingView.autoSelectDivide=AutoSelectDivide.WHOLE }; 1->{ drawingView.autoSelectShape=AutoSelectShape.RECTANGLE; drawingView.autoSelectDivide=AutoSelectDivide.DIVIDED }; 2->{ drawingView.autoSelectShape=AutoSelectShape.FREEFORM; drawingView.autoSelectDivide=AutoSelectDivide.WHOLE }; else->{ drawingView.autoSelectShape=AutoSelectShape.FREEFORM; drawingView.autoSelectDivide=AutoSelectDivide.DIVIDED } }
            drawingView.currentTool=Tool.AUTOSELECT; tvActiveTool.text="AutoSel"
            Toast.makeText(this,"Draw a region to select",Toast.LENGTH_SHORT).show()
        }.show()
    }

    // ── Table ─────────────────────────────────────────────────────

    private fun showTableInsertDialog() {
        val container=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(20),dp(20),dp(10)) }
        val rowLbl=TextView(this).apply{ text="Rows:"; textSize=15f }; container.addView(rowLbl)
        val rowInput=EditText(this).apply{ inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText("3") }; container.addView(rowInput)
        val colLbl=TextView(this).apply{ text="Columns:"; textSize=15f; setPadding(0,dp(12),0,0) }; container.addView(colLbl)
        val colInput=EditText(this).apply{ inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText("3") }; container.addView(colInput)
        AlertDialog.Builder(this).setTitle("⊞ Insert Table").setView(container)
            .setPositiveButton("Insert"){ _,_ ->
                val rows=(rowInput.text.toString().toIntOrNull()?:3).coerceIn(1,20)
                val cols=(colInput.text.toString().toIntOrNull()?:3).coerceIn(1,20)
                val wx=drawingView.screenCenterWorldX()-(drawingView.width/drawingView.getScaleFactor()/4f)
                val wy=drawingView.screenCenterWorldY()-90f
                drawingView.addTable(rows,cols,wx,wy,drawingView.width.toFloat())
                showTableToolbar()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun showTableToolbar() {
        tableToolbarOverlay?.let { canvasContainer.removeView(it) }
        val toolbar=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#CC333333")); setPadding(dp(4),dp(4),dp(4),dp(4)) }

        val table = drawingView.getActiveTable()
        val (selStart, selEnd) = drawingView.getTableSelection()
        val hasRange = selStart != null && selEnd != null
        val isMerged = if (selStart != null && table != null) table.mergeSpans.containsKey(selStart) else false

        fun toolbarBtn(label: String, isActive: Boolean = false, action: () -> Unit) {
            val b=Button(this); b.text=label; b.textSize=12f
            b.setTextColor(if(isActive) Color.parseColor("#FFD700") else Color.WHITE)
            b.typeface=if(isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            b.setBackgroundColor(if(isActive) Color.parseColor("#774CAF50") else Color.parseColor("#55FFFFFF"))
            val p=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(dp(2),0,dp(2),0); b.layoutParams=p
            b.setPadding(dp(8),dp(4),dp(8),dp(4)); b.minWidth=0; b.minimumWidth=0
            b.setOnClickListener{ action(); showTableToolbar() }; toolbar.addView(b)
        }

        toolbarBtn("+ Row"){ drawingView.addTableRow(selStart?.first?:((table?.rows?:1)-1)) }
        toolbarBtn("+ Col"){ drawingView.addTableCol(selStart?.second?:((table?.cols?:1)-1)) }
        toolbarBtn("- Row"){ drawingView.removeTableRow(selStart?.first?:((table?.rows?:1)-1)) }
        toolbarBtn("- Col"){ drawingView.removeTableCol(selStart?.second?:((table?.cols?:1)-1)) }
        toolbarBtn("Merge", isActive=hasRange){ if(hasRange) drawingView.mergeCellSelection() }
        toolbarBtn("Unmerge", isActive=isMerged){ drawingView.unmergeCellSelection() }
        toolbarBtn("Style", isActive=selStart!=null){
            val t=drawingView.getActiveTable()?:return@toolbarBtn
            val (ss,se)=drawingView.getTableSelection()
            val r=(ss?.first?:0).coerceIn(0,t.rows-1); val c=(ss?.second?:0).coerceIn(0,t.cols-1)
            showCellStyleDialog(t,r,c,se)
        }
        toolbarBtn("📊 Chart"){
            val t=drawingView.getActiveTable()?:return@toolbarBtn
            launchChartFromTable(t)
        }

        val doneBtn=Button(this).apply{ text="✓ Done"; textSize=12f; setTextColor(Color.parseColor("#FFD700")); typeface=Typeface.DEFAULT_BOLD; setBackgroundColor(Color.parseColor("#55FFFFFF")) }
        val dp2=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT); dp2.setMargins(dp(2),0,dp(2),0); doneBtn.layoutParams=dp2
        doneBtn.setPadding(dp(8),dp(4),dp(8),dp(4)); doneBtn.minWidth=0; doneBtn.minimumWidth=0
        doneBtn.setOnClickListener{ tableToolbarOverlay?.let{ canvasContainer.removeView(it) }; tableToolbarOverlay=null }
        toolbar.addView(doneBtn)

        val tp=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP or Gravity.CENTER_HORIZONTAL); tp.topMargin=dp(4)
        canvasContainer.addView(toolbar,tp); tableToolbarOverlay=toolbar
    }

    private fun launchChartFromTable(table: TableItem) {
        val rows = table.rows; val cols = table.cols
        if (rows < 2 || cols < 2) { Toast.makeText(this,"Table needs at least 2 rows and 2 columns",Toast.LENGTH_SHORT).show(); return }
        val sb = StringBuilder()
        for (r in 0 until rows) { sb.append((0 until cols).map { c -> table.getCellPublic(r,c).text.trim() }.joinToString(",")); sb.append("\n") }
        chartLauncher.launch(android.content.Intent(this, ChartActivity::class.java).putExtra("table_csv", sb.toString()))
    }

    private fun showCellStyleDialog(table: TableItem, row: Int, col: Int, selEnd: Pair<Int,Int>?) {
        val cell = table.getCellPublic(row,col)
        val minR=minOf(row,selEnd?.first?:row); val maxR=maxOf(row,selEnd?.first?:row).coerceIn(0,table.rows-1)
        val minC=minOf(col,selEnd?.second?:col); val maxC=maxOf(col,selEnd?.second?:col).coerceIn(0,table.cols-1)
        fun applyToSel(action:(TableCell)->Unit){ for(r in minR..maxR) for(c in minC..maxC) action(table.getCellPublic(r,c)); drawingView.invalidate() }

        val container=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        fun lbl(t:String){ container.addView(TextView(this).apply{ text=t;textSize=13f;setTextColor(Color.parseColor("#7B61FF"));setPadding(0,dp(10),0,dp(4)) }) }

        lbl("Layout Preset")
        val presetRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
        fun presetBtn(name:String,action:()->Unit){ val b=Button(this); b.text=name; b.textSize=11f; b.setBackgroundColor(Color.parseColor("#EDE7F6")); b.setTextColor(Color.parseColor("#4527A0")); val p=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f); p.setMargins(dp(2),0,dp(2),0); b.layoutParams=p; b.setPadding(dp(4),dp(6),dp(4),dp(6)); b.minWidth=0; b.minimumWidth=0; b.setOnClickListener{ action(); drawingView.invalidate() }; presetRow.addView(b) }
        presetBtn("Header\nRow"){ for(c in 0 until table.cols){ table.getCellPublic(0,c).also{ it.bgColor=Color.parseColor("#37474F"); it.textColor=Color.WHITE; it.borderColor=Color.parseColor("#263238"); it.borderWidth=3f } }; for(r in 1 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.bgColor=Color.WHITE; it.textColor=Color.BLACK; it.borderColor=Color.DKGRAY; it.borderWidth=1.5f } }
        presetBtn("Zebra\nRows"){ for(r in 0 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.bgColor=if(r%2==0)Color.WHITE else Color.parseColor("#F5F5F5"); it.textColor=Color.BLACK; it.borderColor=Color.parseColor("#BDBDBD"); it.borderWidth=1f } }
        presetBtn("Bold\nBorders"){ for(r in 0 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.borderColor=Color.BLACK; it.borderWidth=3f } }
        presetBtn("No\nBorders"){ for(r in 0 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.borderColor=Color.TRANSPARENT; it.borderWidth=0f } }
        container.addView(presetRow)

        val div2=View(this); div2.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1)).also{it.setMargins(0,dp(12),0,dp(4))}; div2.setBackgroundColor(Color.LTGRAY); container.addView(div2)

        val selDesc=if(selEnd!=null) "R${minR+1}C${minC+1}→R${maxR+1}C${maxC+1}" else "R${row+1}C${col+1}"
        lbl("Cell Style ($selDesc)")

        lbl("Text Color"); val tcBtn=Button(this).apply{ text="Pick Text Color"; textSize=13f; setBackgroundColor(cell.textColor); setTextColor(Color.WHITE); setOnClickListener{ showColorGridDialog{ c->applyToSel{it.textColor=c}; setBackgroundColor(c) } } }; container.addView(tcBtn)
        lbl("Background Color"); val bgBtn=Button(this).apply{ text="Pick Background"; textSize=13f; setBackgroundColor(cell.bgColor); setOnClickListener{ showColorGridDialog{ c->applyToSel{it.bgColor=c}; setBackgroundColor(c) } } }; container.addView(bgBtn)
        lbl("Border Color"); val bcBtn=Button(this).apply{ text="Pick Border Color"; textSize=13f; setBackgroundColor(cell.borderColor); setTextColor(Color.WHITE); setOnClickListener{ showColorGridDialog{ c->applyToSel{it.borderColor=c}; setBackgroundColor(c) } } }; container.addView(bcBtn)

        lbl("Border Width")
        val bwRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
        val bwSeek=SeekBar(this).apply{ max=20; progress=cell.borderWidth.toInt() }
        fun bwQ(lv:String,w:Float){ Button(this).apply{ text=lv; textSize=12f; setBackgroundColor(Color.parseColor("#EDE7F6")); setTextColor(Color.parseColor("#4527A0")); val p=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(dp(2),0,dp(2),0); layoutParams=p; setPadding(dp(8),dp(4),dp(8),dp(4)); minWidth=0; minimumWidth=0; setOnClickListener{ applyToSel{it.borderWidth=w}; bwSeek.progress=w.toInt() }; bwRow.addView(this) } }
        bwQ("None",0f); bwQ("Thin",1f); bwQ("Normal",2f); bwQ("Bold",4f); container.addView(bwRow)
        bwSeek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(sb:SeekBar?,v:Int,f:Boolean){ applyToSel{it.borderWidth=v.toFloat()} }; override fun onStartTrackingTouch(sb:SeekBar?){}; override fun onStopTrackingTouch(sb:SeekBar?){} }); container.addView(bwSeek)

        lbl("Text Size"); val tsSeek=SeekBar(this).apply{ max=60; progress=cell.textSize.toInt(); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(sb:SeekBar?,v:Int,f:Boolean){ applyToSel{it.textSize=v.toFloat().coerceAtLeast(8f)} }; override fun onStartTrackingTouch(sb:SeekBar?){}; override fun onStopTrackingTouch(sb:SeekBar?){} }) }; container.addView(tsSeek)

        lbl("Alignment"); val alignRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL }
        for((idx,al) in listOf("Left","Center","Right").withIndex()){ Button(this).apply{ text=al; textSize=12f; setBackgroundColor(if(cell.alignment==idx)Color.parseColor("#2196F3") else Color.LTGRAY); setOnClickListener{ applyToSel{it.alignment=idx} }; layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f); alignRow.addView(this) } }
        container.addView(alignRow)

        val scroll=ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("Table Style").setView(scroll).setPositiveButton("Done",null).show()
    }

    private fun dismissCellEditor() {
        val et=activeCellEditText?:return
        try{ canvasContainer.removeView(et) }catch(e:Exception){}
        activeCellEditText=null; activeCellToolbar=null
        val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken,0); drawingView.invalidate()
    }

    private fun showTableCellEditor(table:TableItem,row:Int,col:Int,screenX:Float,screenY:Float) {
        val cell=table.cells[row][col]
        val et=EditText(this).apply{
            setText(cell.text); setTextColor(cell.textColor)
            textSize=(cell.textSize*drawingView.getScaleFactor()/resources.displayMetrics.density).coerceAtLeast(8f)
            setBackgroundColor(Color.parseColor("#EEFFFFFF")); setPadding(dp(6),dp(4),dp(6),dp(4)); minWidth=dp(80)
            isSingleLine=false; imeOptions=EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object:TextWatcher{ override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){} override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ cell.text=s?.toString()?:""; drawingView.invalidate() } override fun afterTextChanged(s:Editable?){} })
        }
        val params=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin=screenX.toInt().coerceIn(0,canvasContainer.width-dp(100))
        params.topMargin=(screenY+dp(4)).toInt().coerceIn(dp(40),canvasContainer.height-dp(80))
        canvasContainer.addView(et,params); activeCellEditText=et; activeCellToolbar=null
        et.requestFocus(); et.setSelection(et.text.length)
        val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(et,InputMethodManager.SHOW_FORCED)
    }

    // ── Text editing (inline) ─────────────────────────────────────

    private fun toggleStyleOnSelection(et:EditText,styleFlag:Int){ val s=et.selectionStart;val e=et.selectionEnd; if(s==e){Toast.makeText(this,"Select text first",Toast.LENGTH_SHORT).show();return}; val from=minOf(s,e);val to=maxOf(s,e); val ed=et.text; val ex=ed.getSpans(from,to,StyleSpan::class.java).filter{it.style==styleFlag&&ed.getSpanStart(it)<=from&&ed.getSpanEnd(it)>=to}; if(ex.isNotEmpty()) for(sp in ex) ed.removeSpan(sp) else ed.setSpan(StyleSpan(styleFlag),from,to,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun applyColorToSelection(et:EditText,color:Int){ val s=et.selectionStart;val e=et.selectionEnd; if(s==e){editColor=color;et.setTextColor(color);return}; et.text.setSpan(ForegroundColorSpan(color),minOf(s,e),maxOf(s,e),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun toggleUnderlineOnSelection(et:EditText){ val s=et.selectionStart;val e=et.selectionEnd; val from=minOf(s,e);val to=maxOf(s,e); val ed=et.text; val ex=ed.getSpans(from,to,UnderlineSpan::class.java).filter{ed.getSpanStart(it)<=from&&ed.getSpanEnd(it)>=to}; if(ex.isNotEmpty()) for(sp in ex) ed.removeSpan(sp) else ed.setSpan(UnderlineSpan(),from,to,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun applyHighlightToSelection(et:EditText,color:Int){ et.text.setSpan(BackgroundColorSpan(color),minOf(et.selectionStart,et.selectionEnd),maxOf(et.selectionStart,et.selectionEnd),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        if (activeEditText != null && editingItem === item) return
        if (activeEditText != null) { isSwitchingTextEditor=true; closeInlineEditor(true); isSwitchingTextEditor=false; drawingView.post{ showInlineTextEditor(item,screenX,screenY,worldX,worldY) }; return }
        pendingBold=false; pendingItalic=false; pendingUnderline=false; pendingHighlight=null
        editingItem=item; editWorldX=item?.x?:worldX; editWorldY=item?.y?:worldY; editRotation=item?.rotation?:0f; editColor=item?.color?:drawingView.currentColor; editSize=item?.size?:drawingView.defaultTextSize
        val density=resources.displayMetrics.density
        val useActualSize = drawingView.canvasMode != CanvasMode.INFINITE && drawingView.canvasMode != CanvasMode.CONVENIENT
        val screenSizePx=if(useActualSize) editSize else editSize*drawingView.getScaleFactor()
        val et=EditText(this)
        val spannable=SpannableStringBuilder(item?.text?:"")
        item?.spans?.forEach{ sp-> val s=sp.start.coerceIn(0,spannable.length);val e=sp.end.coerceIn(s,spannable.length); if(s<e) when(sp.type){ 'S'->spannable.setSpan(StyleSpan(sp.value),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 'C'->spannable.setSpan(ForegroundColorSpan(sp.value),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 'U'->spannable.setSpan(UnderlineSpan(),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 'H'->spannable.setSpan(BackgroundColorSpan(sp.value),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) } }
        et.setText(spannable,TextView.BufferType.SPANNABLE); et.setTextColor(editColor); et.textSize=(screenSizePx/density).coerceAtLeast(8f); et.setBackgroundColor(Color.TRANSPARENT); et.setPadding(dp(4),dp(4),dp(4),dp(4)); et.minWidth=dp(120)
        if(!useActualSize) et.rotation=editRotation
        et.addTextChangedListener(object:TextWatcher{ override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ if(count>0){ val e2=et.text;val end=start+count; if(pendingBold) e2.setSpan(StyleSpan(Typeface.BOLD),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); if(pendingItalic) e2.setSpan(StyleSpan(Typeface.ITALIC),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); if(pendingUnderline) e2.setSpan(UnderlineSpan(),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); pendingHighlight?.let{ e2.setSpan(BackgroundColorSpan(it),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) } } }; override fun afterTextChanged(s:Editable?){} })
        val params=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT); params.leftMargin=screenX.toInt(); params.topMargin=(screenY-screenSizePx).toInt().coerceAtLeast(0); canvasContainer.addView(et,params)
        val toolbar=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#AA222222")); setPadding(dp(4),dp(4),dp(4),dp(4)) }
        fun tbtn(label:String,action:(Button)->Unit):Button{ val b=Button(this);b.text=label;b.textSize=14f;b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.parseColor("#55FFFFFF"));val p=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);p.setMargins(dp(2),0,dp(2),0);b.layoutParams=p;b.setPadding(dp(10),dp(6),dp(10),dp(6));b.minWidth=0;b.minimumWidth=0;b.setOnClickListener{action(b)};toolbar.addView(b);return b }
        val aC=Color.parseColor("#2196F3");val iC=Color.parseColor("#55FFFFFF")
        tbtn("B"){btn-> if(et.selectionStart!=et.selectionEnd) toggleStyleOnSelection(et,Typeface.BOLD) else{ pendingBold=!pendingBold;btn.setBackgroundColor(if(pendingBold)aC else iC) } }
        tbtn("I"){btn-> if(et.selectionStart!=et.selectionEnd) toggleStyleOnSelection(et,Typeface.ITALIC) else{ pendingItalic=!pendingItalic;btn.setBackgroundColor(if(pendingItalic)aC else iC) } }
        tbtn("U"){btn-> if(et.selectionStart!=et.selectionEnd) toggleUnderlineOnSelection(et) else{ pendingUnderline=!pendingUnderline;btn.setBackgroundColor(if(pendingUnderline)aC else iC) } }
        tbtn("\uD83D\uDD8D"){btn-> showColorGridDialog{ color-> if(et.selectionStart!=et.selectionEnd) applyHighlightToSelection(et,color) else{ pendingHighlight=color;btn.setBackgroundColor(color) } } }
        tbtn("\uD83C\uDFA8"){ showColorGridDialog{ color->applyColorToSelection(et,color) } }
        val ptSize=(editSize/PT_TO_PX).toInt().coerceIn(1,144)
        tbtn(ptSize.toString()){btn-> AlertDialog.Builder(this).setTitle("Font Size (pt)").setItems((1..144).map{it.toString()}.toTypedArray()){ _,idx-> val pt=idx+1;editSize=pt*PT_TO_PX; val nss=if(useActualSize) editSize else editSize*drawingView.getScaleFactor(); et.textSize=(nss/density).coerceAtLeast(8f); btn.text=pt.toString() }.show() }
        tbtn("\u21ba"){ editRotation-=15f;if(!useActualSize) et.rotation=editRotation }
        tbtn("\u21bb"){ editRotation+=15f;if(!useActualSize) et.rotation=editRotation }
        tbtn("\u2713"){ closeInlineEditor(true) }
        tbtn("\uD83D\uDDD1"){ closeInlineEditor(false,delete=true) }
        val tp=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL); canvasContainer.addView(toolbar,tp)
        fun updateET(){ val scale=drawingView.getScaleFactor();val nsp=editSize*scale; et.textSize=(nsp/density).coerceAtLeast(8f); val sx=drawingView.worldToScreenX(editWorldX);val sy=drawingView.worldToScreenY(editWorldY)-nsp; val lp=et.layoutParams as FrameLayout.LayoutParams; lp.leftMargin=sx.toInt(); lp.topMargin=sy.toInt(); et.layoutParams=lp }
        drawingView.onScaleChanged={ updateET() }; drawingView.onCanvasTransformed={ updateET() }
        activeEditText=et; activeToolbar=toolbar; et.requestFocus()
        et.post{ val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.showSoftInput(et,InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeInlineEditor(commit:Boolean, delete:Boolean=false) {
        val et=activeEditText?:return; val tb=activeToolbar
        val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.hideSoftInputFromWindow(et.windowToken,0)
        val text=et.text.toString(); val spans=mutableListOf<TextSpanData>(); val ed=et.text
        for(span in ed.getSpans(0,ed.length,Any::class.java)){ val s=ed.getSpanStart(span);val e=ed.getSpanEnd(span); if(s<0||e<0||s>=e) continue; when(span){ is StyleSpan->spans.add(TextSpanData(s,e,'S',span.style)); is ForegroundColorSpan->spans.add(TextSpanData(s,e,'C',span.foregroundColor)); is UnderlineSpan->spans.add(TextSpanData(s,e,'U',0)); is BackgroundColorSpan->spans.add(TextSpanData(s,e,'H',span.backgroundColor)) } }
        canvasContainer.removeView(et); if(tb!=null) canvasContainer.removeView(tb)
        val item=editingItem
        if(commit&&!delete&&text.isNotBlank()){ drawingView.defaultTextSize=editSize; if(item!=null){ item.text=text;item.color=editColor;item.size=editSize;item.rotation=editRotation;item.spans=spans;item.isEditing=false } else drawingView.addText(text,editWorldX,editWorldY,editSize,editRotation,editColor,spans) } else { if(item!=null) drawingView.removeTextItem(item) }
        if(!isSwitchingTextEditor) drawingView.invalidate()
        drawingView.onScaleChanged=null;drawingView.onCanvasTransformed=null; activeEditText=null;activeToolbar=null;editingItem=null
    }

    // ── Image helpers ─────────────────────────────────────────────

    private fun launchCamera() {
        val folder=File(filesDir,"images").also{it.mkdirs()}
        val pf=File(folder,"camera_${System.currentTimeMillis()}.jpg"); cameraImageFile=pf
        takePictureLauncher.launch(FileProvider.getUriForFile(this,"$packageName.fileprovider",pf))
    }

    private fun addImageFromFile(file:File) {
        try {
            val opts=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}; android.graphics.BitmapFactory.decodeFile(file.absolutePath,opts)
            val ratio=opts.outWidth.toFloat().coerceAtLeast(1f)/opts.outHeight.toFloat().coerceAtLeast(1f)
            val w=drawingView.width/drawingView.getScaleFactor()*0.85f
            drawingView.addImage(file.absolutePath,drawingView.screenCenterWorldX(),drawingView.screenCenterWorldY(),w,(w/ratio).coerceAtMost(drawingView.height/drawingView.getScaleFactor()*0.85f))
        } catch(e:Exception){ Toast.makeText(this,"Photo failed: ${e.message}",Toast.LENGTH_LONG).show() }
    }

    private fun insertImage(uri:Uri) {
        try {
            val bOpts=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}; contentResolver.openInputStream(uri)?.use{ android.graphics.BitmapFactory.decodeStream(it,null,bOpts) }
            val ratio=bOpts.outWidth.toFloat().coerceAtLeast(1f)/bOpts.outHeight.toFloat().coerceAtLeast(1f)
            val maxDim=1920; var sample=1; var tw=bOpts.outWidth; var th=bOpts.outHeight; while(tw>maxDim||th>maxDim){sample*=2;tw/=2;th/=2}
            val decOpts=android.graphics.BitmapFactory.Options().apply{inSampleSize=sample;inJustDecodeBounds=false;inPreferredConfig=Bitmap.Config.RGB_565}
            val bmp=contentResolver.openInputStream(uri)?.use{ android.graphics.BitmapFactory.decodeStream(it,null,decOpts) }?:return
            val folder=File(filesDir,"images").also{it.mkdirs()}; val out=File(folder,"img_${System.currentTimeMillis()}.jpg"); FileOutputStream(out).use{ bmp.compress(Bitmap.CompressFormat.JPEG,85,it) }; bmp.recycle()
            val w=drawingView.width/drawingView.getScaleFactor()*0.85f
            drawingView.addImage(out.absolutePath,drawingView.screenCenterWorldX(),drawingView.screenCenterWorldY(),w,(w/ratio).coerceAtMost(drawingView.height/drawingView.getScaleFactor()*0.85f))
        } catch(e:Exception){ e.printStackTrace(); Toast.makeText(this,"Image failed: ${e.message}",Toast.LENGTH_LONG).show() }
    }

    // ── File ops ──────────────────────────────────────────────────

    private fun getDrawingsFolder(): File {
        val bookName=intent.getStringExtra("book_name")?:"General"
        val f=File(File(filesDir,"books"),bookName); if(!f.exists()) f.mkdirs(); return f
    }

    private fun writeCurrentFile() {
        val name=currentFileName?:return
        File(getDrawingsFolder(),"$name.eng").writeText(drawingView.serialize())
        lastSavedContent=drawingView.serialize()
    }

    private fun autoSave() {
        if(!drawingView.hasContent()) return
        if(currentFileName==null){ val name="AutoSave_${System.currentTimeMillis()}"; currentFileName=name; tvTitle.text=name }
        writeCurrentFile()
    }

    private fun saveCurrent() {
        if(currentFileName==null){
            val input=EditText(this).apply{hint="Note name"}
            AlertDialog.Builder(this).setTitle("Save Note").setView(input)
                .setPositiveButton("Save"){ _,_ -> val name=input.text.toString().trim().ifEmpty{"Note_${System.currentTimeMillis()}"}; currentFileName=name; tvTitle.text=name; writeCurrentFile(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show() }
                .setNegativeButton("Cancel",null).show()
        } else { writeCurrentFile(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show() }
    }

    private fun saveAsNew() {
        val input=EditText(this).apply{hint="New note name"}
        AlertDialog.Builder(this).setTitle("Save as New").setView(input)
            .setPositiveButton("Save"){ _,_ -> val name=input.text.toString().trim().ifEmpty{"Note_${System.currentTimeMillis()}"}; currentFileName=name; tvTitle.text=name; writeCurrentFile(); Toast.makeText(this,"Saved as $name",Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Cancel",null).show()
    }

    private fun deleteCurrentNote() {
        val name=currentFileName?:return
        AlertDialog.Builder(this).setTitle("Delete Note").setMessage("Delete \"$name\"? Cannot be undone.")
            .setPositiveButton("Delete"){ _,_ -> File(getDrawingsFolder(),"$name.eng").delete(); finish() }
            .setNegativeButton("Cancel",null).show()
    }

    private fun confirmThenExit() {
        closeInlineEditor(true)
        val changed=drawingView.serialize()!=lastSavedContent&&drawingView.hasContent()
        if(!changed){ finish(); return }
        if(getPrefs().getBoolean("autosave",true)){ autoSave(); finish(); return }
        if(getPrefs().getBoolean("confirm_exit_clear",true)){
            AlertDialog.Builder(this).setTitle("Unsaved Changes").setMessage("Save before leaving?")
                .setPositiveButton("Save"){ _,_ -> saveCurrent(); finish() }
                .setNeutralButton("Don't Save"){ _,_ -> finish() }
                .setNegativeButton("Cancel",null).show()
        } else { autoSave(); finish() }
    }

    private fun confirmThenClear() {
        if(getPrefs().getBoolean("confirm_exit_clear",true)&&drawingView.hasContent()){
            AlertDialog.Builder(this).setTitle("Clear Canvas?").setMessage("This will remove everything.")
                .setPositiveButton("Clear"){ _,_ -> drawingView.clearAll() }.setNegativeButton("Cancel",null).show()
        } else drawingView.clearAll()
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        closeInlineEditor(true)
        autoSave()
        if(isRecording){ AudioHelper.stopRecording(); isRecording=false }
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacks(autosaveRunnable)
        AudioHelper.releaseAll()
    }
}
