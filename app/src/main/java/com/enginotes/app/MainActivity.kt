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
            val engFile = currentFileName?.let { File(getDrawingsFolder(),"$it.eng") } ?: run { Toast.makeText(this,"Save first!",Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
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
            val imgFile=File(cacheDir,"export_tmp.png")
            FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG,90,it) }
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
        ip.topMargin = dp(4); ip.rightMargin = dp(4)
        canvasContainer.addView(tvActiveTool, ip)

        drawingView.onTextEditRequest = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTableCellEditRequest = { table, row, col, sx, sy -> dismissCellEditor(); showTableCellEditor(table,row,col,sx,sy) }
        drawingView.onExportWindowSelected = { l,t,r,b -> exportWindowBitmap = drawingView.exportWindow(l,t,r,b); showExportWindowDialog() }

        drawingView.onAudioItemTap = { audioFile ->
            AudioHelper.togglePlay(audioFile) { drawingView.invalidate() }
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
                    tableToolbarOverlay != null -> { tableToolbarOverlay?.let { canvasContainer.removeView(it) }; tableToolbarOverlay = null }
                    drawingView.currentTool != Tool.SELECT -> setActiveTool(null, Tool.SELECT, "Select")
                    else -> confirmThenExit()
                }
            }
        })

        autosaveHandler.postDelayed(autosaveRunnable, 10_000L)
    }

    private fun setupBottomToolbar() {
        findViewById<Button>(R.id.btnUndo).setOnClickListener { closeInlineEditor(true); drawingView.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { closeInlineEditor(true); drawingView.redo() }

        findViewById<Button>(R.id.btnDraw).setOnClickListener {
            closeInlineEditor(true)
            setActiveTool(it as Button, Tool.PEN, "Pen")
        }
        findViewById<Button>(R.id.btnDraw).setOnLongClickListener {
            showDrawPicker(it as Button)
            true
        }

        findViewById<Button>(R.id.btnQuickEraser).setOnClickListener {
            closeInlineEditor(true); setActiveTool(it as Button, Tool.ERASER, "Eraser")
        }

        val btnSelect = findViewById<Button?>(R.id.btnSelect)
        btnSelect?.setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.SELECT, "Select") }

        findViewById<Button>(R.id.btnText).setOnClickListener {
            closeInlineEditor(true); setActiveTool(it as Button, Tool.TEXT, "Text")
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

        val btnArc = findViewById<Button?>(R.id.btnArc)
        btnArc?.setOnClickListener { closeInlineEditor(true); setActiveTool(it as Button, Tool.ARC, "Arc") }

        val btnAutoSelect = findViewById<Button?>(R.id.btnAutoSelect)
        btnAutoSelect?.setOnClickListener { showAutoSelectModeDialog(); setActiveToolbarBtn(it as Button) }

        val btnShapes = findViewById<Button?>(R.id.btnShapes)
        btnShapes?.setOnClickListener { showShapesPicker(it as Button) }

        val btnQuickColor = findViewById<Button?>(R.id.btnQuickColor)
        btnQuickColor?.setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c } }

        val btnQuickSize = findViewById<Button?>(R.id.btnQuickSize)
        btnQuickSize?.setOnClickListener { showSizePicker() }

        val btnQuickFill = findViewById<Button?>(R.id.btnQuickFill)
        btnQuickFill?.setOnClickListener {
            showColorGridDialog { c -> drawingView.fillColor = c }
            setActiveTool(null, Tool.FILL, "Fill")
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Layout operations
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
    //  Inline Rich-Text System
    // ──────────────────────────────────────────────────────────────

    private fun showInlineTextEditor(item: TextItem?, sx: Float, sy: Float, wx: Float, wy: Float) {
        if (isSwitchingTextEditor) return
        closeInlineEditor(commit = true)
        editingItem = item
        editWorldX = wx
        editWorldY = wy
        editRotation = item?.rotation ?: 0f
        editColor = item?.color ?: drawingView.currentColor
        editSize = item?.textSize ?: (14f * PT_TO_PX)

        if (item != null) {
            pendingBold = item.isBold
            pendingItalic = item.isItalic
            pendingUnderline = item.isUnderline
            pendingHighlight = item.highlightColor
        } else {
            pendingBold = false
            pendingItalic = false
            pendingUnderline = false
            pendingHighlight = null
        }

        val container = RelativeLayout(this).apply { id = View.generateViewId() }
        val et = EditText(this).apply {
            id = View.generateViewId()
            background = null
            setPadding(dp(4), dp(4), dp(4), dp(4))
            minWidth = dp(120)
            setTextColor(editColor)
            textSize = editSize / PT_TO_PX
            gravity = Gravity.START or Gravity.TOP
            imeOptions = EditorInfo.IME_ACTION_DONE
            setRawInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            if (item != null) {
                setText(item.text)
                setSelection(item.text.length)
            }
        }
        activeEditText = et

        applyFormattingSpans(et)

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s != null) {
                    isSwitchingTextEditor = true
                    val savedSelectionStart = et.selectionStart
                    val savedSelectionEnd = et.selectionEnd
                    applyFormattingSpans(et)
                    et.setSelection(savedSelectionStart, savedSelectionEnd)
                    isSwitchingTextEditor = false
                }
            }
        })

        val tb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            setPadding(dp(2), dp(2), dp(2), dp(2))
            elevation = dp(4).toFloat()
        }
        activeToolbar = tb

        val formats = arrayOf("B", "I", "U", "🎨", "🖍", "✂")
        formats.forEach { form ->
            val b = Button(this).apply {
                text = form
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(36)).apply { setMargins(dp(2), 0, dp(2), 0) }
                setPadding(0, 0, 0, 0)
                textSize = 12f
                if (form == "B" && pendingBold) setBackgroundColor(Color.LTGRAY)
                if (form == "I" && pendingItalic) setBackgroundColor(Color.LTGRAY)
                if (form == "U" && pendingUnderline) setBackgroundColor(Color.LTGRAY)
            }
            b.setOnClickListener {
                when (form) {
                    "B" -> { pendingBold = !pendingBold; b.setBackgroundColor(if (pendingBold) Color.LTGRAY else Color.TRANSPARENT) }
                    "I" -> { pendingItalic = !pendingItalic; b.setBackgroundColor(if (pendingItalic) Color.LTGRAY else Color.TRANSPARENT) }
                    "U" -> { pendingUnderline = !pendingUnderline; b.setBackgroundColor(if (pendingUnderline) Color.LTGRAY else Color.TRANSPARENT) }
                    "🎨" -> showColorGridDialog { c -> editColor = c; et.setTextColor(c) }
                    "🖍" -> showColorGridDialog { c -> pendingHighlight = if (c == Color.TRANSPARENT) null else c }
                    "✂" -> { closeInlineEditor(commit = false); return@setOnClickListener }
                }
                applyFormattingSpans(et)
            }
            tb.addView(b)
        }

        val lpEt = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        }
        container.addView(et, lpEt)

        val lpTb = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.BELOW, et.id)
            addRule(RelativeLayout.ALIGN_LEFT, et.id)
        }
        container.addView(tb, lpTb)

        val parentLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = sx.toInt().coerceAtLeast(0)
            topMargin = sy.toInt().coerceAtLeast(0)
        }
        canvasContainer.addView(container, parentLp)

        et.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun applyFormattingSpans(et: EditText) {
        val txt = et.text.toString()
        val ssb = SpannableStringBuilder(txt)
        if (txt.isNotEmpty()) {
            val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            if (pendingBold && pendingItalic) {
                ssb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, txt.length, flag)
            } else if (pendingBold) {
                ssb.setSpan(StyleSpan(Typeface.BOLD), 0, txt.length, flag)
            } else if (pendingItalic) {
                ssb.setSpan(StyleSpan(Typeface.ITALIC), 0, txt.length, flag)
            }
            if (pendingUnderline) {
                ssb.setSpan(UnderlineSpan(), 0, txt.length, flag)
            }
            pendingHighlight?.let { hl ->
                ssb.setSpan(BackgroundColorSpan(hl), 0, txt.length, flag)
            }
        }
    }

    private fun closeInlineEditor(commit: Boolean) {
        val et = activeEditText ?: return
        val textStr = et.text.toString()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken, 0)

        val container = et.parent as? RelativeLayout
        if (container != null) canvasContainer.removeView(container)

        activeEditText = null
        activeToolbar = null

        if (commit && textStr.isNotBlank()) {
            val item = editingItem
            if (item != null) {
                item.text = textStr
                item.color = editColor
                item.isBold = pendingBold
                item.isItalic = pendingItalic
                item.isUnderline = pendingUnderline
                item.highlightColor = pendingHighlight
            } else {
                drawingView.addRichTextItem(textStr, editWorldX, editWorldY, editColor, editSize, pendingBold, pendingItalic, pendingUnderline, pendingHighlight)
            }
        } else if (commit && textStr.isBlank() && editingItem != null) {
            drawingView.removeItem(editingItem!!)
        }
        editingItem = null
        drawingView.invalidate()
    }

    // ──────────────────────────────────────────────────────────────
    //  Table Inline Cells Editor System
    // ──────────────────────────────────────────────────────────────

    private fun showTableCellEditor(table: TableItem, row: Int, col: Int, sx: Float, sy: Float) {
        val cell = table.cells[row][col]
        val cellContainer = RelativeLayout(this).apply { id = View.generateViewId() }

        val et = EditText(this).apply {
            id = View.generateViewId()
            background = null
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setTextColor(cell.textColor)
            textSize = cell.textSize
            minWidth = dp(80)
            gravity = when (cell.alignment) {
                1 -> Gravity.CENTER
                2 -> Gravity.END or Gravity.TOP
                else -> Gravity.START or Gravity.TOP
            }
            imeOptions = EditorInfo.IME_ACTION_DONE
            setRawInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            setText(cell.text)
            setSelection(cell.text.length)
        }
        activeCellEditText = et

        val tb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            elevation = dp(6).toFloat()
        }
        activeCellToolbar = tb

        val tableActions = arrayOf("🎨 Text", "🪣 Bg", "📐 Size", "⌸ Merge", "⌺ Unmerge", "Done")
        tableActions.forEach { act ->
            val b = Button(this).apply {
                text = act
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { setMargins(dp(2), 0, dp(2), 0) }
                setPadding(dp(6), 0, dp(6), 0)
            }
            b.setOnClickListener {
                when (act) {
                    "🎨 Text" -> showColorGridDialog { c -> cell.textColor = c; et.setTextColor(c) }
                    "🪣 Bg" -> showColorGridDialog { c -> cell.bgColor = c }
                    "📐 Size" -> {
                        val sizes = arrayOf("12", "14", "16", "18", "24")
                        AlertDialog.Builder(this@MainActivity).setTitle("Cell Font Size")
                            .setItems(sizes) { _, idx -> cell.textSize = sizes[idx].toFloat(); et.textSize = cell.textSize }.show()
                    }
                    "⌸ Merge" -> {
                        val inputs = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
                        val rowSpanIn = EditText(this@MainActivity).apply { hint = "Row span count (e.g. 2)"; setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER) }
                        val colSpanIn = EditText(this@MainActivity).apply { hint = "Col span count (e.g. 2)"; setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER) }
                        inputs.addView(rowSpanIn); inputs.addView(colSpanIn)
                        AlertDialog.Builder(this@MainActivity).setTitle("Merge Dimensions").setView(inputs)
                            .setPositiveButton("Merge") { _, _ ->
                                val rs = rowSpanIn.text.toString().toIntOrNull() ?: 1
                                val cs = colSpanIn.text.toString().toIntOrNull() ?: 1
                                table.mergeCells(row, col, rs, cs)
                                dismissCellEditor()
                            }.setNegativeButton("Cancel", null).show()
                    }
                    "⌺ Unmerge" -> { table.unmergeCells(row, col); dismissCellEditor() }
                    "Done" -> dismissCellEditor()
                }
            }
            tb.addView(b)
        }

        cellContainer.addView(et, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT))
        val lpTb = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.BELOW, et.id)
        }
        cellContainer.addView(tb, lpTb)

        val flp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = sx.toInt().coerceAtLeast(0)
            topMargin = sy.toInt().coerceAtLeast(0)
        }
        canvasContainer.addView(cellContainer, flp)

        et.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)

        setupTableToolbarOverlay(table)
    }

    private fun setupTableToolbarOverlay(table: TableItem) {
        if (tableToolbarOverlay != null) canvasContainer.removeView(tableToolbarOverlay)
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#DDFFFFFF"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        tableToolbarOverlay = overlay

        val structOps = arrayOf("+Row", "-Row", "+Col", "-Col", "🗑 Delete")
        structOps.forEach { op ->
            val b = Button(this).apply {
                text = op; textSize = 10f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { setMargins(dp(2), 0, dp(2), 0) }
            }
            b.setOnClickListener {
                when (op) {
                    "+Row" -> table.insertRow(table.rows)
                    "-Row" -> if (table.rows > 1) table.removeRow(table.rows - 1)
                    "+Col" -> table.insertColumn(table.cols)
                    "-Col" -> if (table.cols > 1) table.removeColumn(table.cols - 1)
                    "🗑 Delete" -> { drawingView.removeItem(table); dismissCellEditor() }
                }
                drawingView.invalidate()
            }
            overlay.addView(b)
        }
        val flp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(60)
        }
        canvasContainer.addView(overlay, flp)
    }

    private fun dismissCellEditor() {
        val et = activeCellEditText ?: return
        val txt = et.text.toString()
        val parentLayout = et.parent as? RelativeLayout
        if (parentLayout != null) canvasContainer.removeView(parentLayout)

        tableToolbarOverlay?.let { canvasContainer.removeView(it) }
        tableToolbarOverlay = null

        activeCellEditText = null
        activeCellToolbar = null

        drawingView.activeTableEditCell?.let { (table, cellPair) ->
            table.cells[cellPair.first][cellPair.second].text = txt
        }
        drawingView.clearTableEditState()
    }

    // ──────────────────────────────────────────────────────────────
    //  Tool Pickers & Helpers
    // ──────────────────────────────────────────────────────────────

    private fun showColorGridDialog(onColorSelected: (Int) -> Unit) {
        val colors = intArrayOf(
            Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.WHITE,
            Color.RED, Color.parseColor("#FF5722"), Color.parseColor("#FF9800"), Color.YELLOW,
            Color.GREEN, Color.parseColor("#4CAF50"), Color.parseColor("#009688"), Color.CYAN,
            Color.BLUE, Color.parseColor("#3F51B5"), Color.parseColor("#673AB7"), Color.parseColor("#E91E63"),
            Color.TRANSPARENT
        )
        val grid = GridView(this).apply {
            numColumns = 5
            setPadding(dp(16), dp(16), dp(16), dp(16))
            horizontalSpacing = dp(12)
            verticalSpacing = dp(12)
        }
        val dlg = AlertDialog.Builder(this).setTitle("Pick Color").setView(grid).create()
        grid.adapter = object : BaseAdapter() {
            override fun getCount(): Int = colors.size
            override fun getItem(p: Int): Any = colors[p]
            override fun getItemId(p: Int): Long = p.toLong()
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup?): View {
                return View(this@MainActivity).apply {
                    layoutParams = AbsListView.LayoutParams(dp(44), dp(44))
                    if (colors[p] == Color.TRANSPARENT) {
                        setBackgroundColor(Color.LTGRAY)
                    } else {
                        setBackgroundColor(colors[p])
                    }
                    setOnClickListener { onColorSelected(colors[p]); dlg.dismiss(); drawingView.invalidate() }
                }
            }
        }
        dlg.show()
    }

    private fun showSizePicker() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val tv = TextView(this).apply { text = "Stroke Size: ${drawingView.currentStrokeWidth.toInt()} px"; textSize = 14f }
        val sb = SeekBar(this).apply {
            max = 60
            progress = drawingView.currentStrokeWidth.toInt().coerceIn(1, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    val actual = p.coerceAtLeast(1)
                    tv.text = "Stroke Size: $actual px"
                    drawingView.currentStrokeWidth = actual.toFloat()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(tv); layout.addView(sb)
        AlertDialog.Builder(this).setTitle("Line Width").setView(layout).setPositiveButton("OK", null).show()
    }

    private fun showAutoSelectModeDialog() {
        AlertDialog.Builder(this).setTitle("AutoSelect Configuration")
            .setItems(arrayOf("Shape: Rectangle", "Shape: Freeform Lasso", "Strategy: Whole Elements", "Strategy: Slice Intersecting Lines")) { _, idx ->
                when (idx) {
                    0 -> { drawingView.autoSelectShapeMode = AutoSelectShape.RECTANGLE; Toast.makeText(this, "Rectangle lasso ready", Toast.LENGTH_SHORT).show() }
                    1 -> { drawingView.autoSelectShapeMode = AutoSelectShape.FREEFORM; Toast.makeText(this, "Freeform lasso ready", Toast.LENGTH_SHORT).show() }
                    2 -> { drawingView.autoSelectDivideMode = AutoSelectDivide.WHOLE; Toast.makeText(this, "Selects entire stroke components", Toast.LENGTH_SHORT).show() }
                    3 -> { drawingView.autoSelectDivideMode = AutoSelectDivide.DIVIDED; Toast.makeText(this, "Slices items at lasso edge boundaries", Toast.LENGTH_SHORT).show() }
                }
                drawingView.currentTool = Tool.AUTOSELECT
                tvActiveTool.text = "AutoSel"
            }.show()
    }

    private fun showExportWindowDialog() {
        val bmp = exportWindowBitmap ?: return
        val iv = ImageView(this).apply { setImageBitmap(bmp); setPadding(dp(8), dp(8), dp(8), dp(8)); adjustViewBounds = true }
        AlertDialog.Builder(this).setTitle("Snip Region Action")
            .setView(iv)
            .setPositiveButton("Save Image") { _, _ ->
                pendingExportBitmap = bmp
                pendingExportFormat = "png"
                saveImageLauncher.launch("snip_${System.currentTimeMillis()}.png")
            }
            .setNegativeButton("Share Region") { _, _ ->
                try {
                    val cacheF = File(cacheDir, "snip_share.png")
                    FileOutputStream(cacheF).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val u = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheF)
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(android.content.Intent.EXTRA_STREAM, u)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share Snipped Window"))
                } catch (e: Exception) { Toast.makeText(this, "Sharing failed", Toast.LENGTH_SHORT).show() }
            }
            .setNeutralButton("Cancel", null).show()
    }

    private fun showTableInsertDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val rIn = EditText(this).apply { hint = "Rows (e.g. 4)"; setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText("3") }
        val cIn = EditText(this).apply { hint = "Columns (e.g. 3)"; setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText("3") }
        layout.addView(rIn); layout.addView(cIn)
        AlertDialog.Builder(this).setTitle("Insert Dynamic Grid").setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val rs = rIn.text.toString().toIntOrNull() ?: 3
                val cs = cIn.text.toString().toIntOrNull() ?: 3
                drawingView.addTableItem(drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), rs, cs)
                Toast.makeText(this, "Table structured", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    // ──────────────────────────────────────────────────────────────
    //  Multimedia Actions (Audio, Image, Camera)
    // ──────────────────────────────────────────────────────────────

    private fun showAudioRecordDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val tvStatus = TextView(this).apply { text = "Ready to record audio note"; textSize = 14f; setPadding(0, 0, 0, dp(12)) }
        val btnToggle = Button(this).apply { text = "🎙 Start Recording" }

        btnToggle.setOnClickListener {
            if (!isRecording) {
                try {
                    val audioF = File(getDrawingsFolder(), "audio_${System.currentTimeMillis()}.mp3")
                    recordingFile = audioF
                    AudioHelper.startRecording(audioF)
                    isRecording = true
                    recordingStartMs = System.currentTimeMillis()
                    text = "⏹ Stop Recording"
                    tvStatus.text = "Recording audio memo ongoing..."
                } catch (e: Exception) {
                    tvStatus.text = "Mic error: ${e.message}"
                }
            } else {
                AudioHelper.stopRecording()
                isRecording = false
                val file = recordingFile
                if (file != null && file.exists()) {
                    drawingView.addAudioItem(file.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY())
                    Toast.makeText(this@MainActivity, "Audio element generated on page", Toast.LENGTH_SHORT).show()
                }
                btnToggle.text = "🎙 Start Recording"
                tvStatus.text = "Audio recorded successfully."
            }
        }

        layout.addView(tvStatus)
        layout.addView(btnToggle)
        AlertDialog.Builder(this).setTitle("Audio Recorder").setView(layout)
            .setOnDismissListener { if (isRecording) { AudioHelper.stopRecording(); isRecording = false } }
            .setPositiveButton("Dismiss", null).show()
    }

    private fun launchCamera() {
        try {
            val f = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraImageFile = f
            val u = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            takePictureLauncher.launch(u)
        } catch (e: Exception) { Toast.makeText(this, "Camera init failed", Toast.LENGTH_SHORT).show() }
    }

    private fun insertImage(uri: Uri) {
        try {
            val cacheF = File(cacheDir, "inserted_${System.currentTimeMillis()}.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheF).use { output -> input.copyTo(output) }
            }
            addImageFromFile(cacheF)
        } catch (e: Exception) { Toast.makeText(this, "Failed loading image structural stream", Toast.LENGTH_SHORT).show() }
    }

    private fun addImageFromFile(file: File) {
        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        if (bmp == null) { Toast.makeText(this, "Corrupt local asset format", Toast.LENGTH_SHORT).show(); return }
        val r = bmp.width.toFloat() / bmp.height
        val targetW = drawingView.width / drawingView.getScaleFactor() * 0.6f
        val targetH = targetW / r
        drawingView.addImage(file.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), targetW, targetH)
    }

    private fun writeBmp(bmp: Bitmap, out: java.io.OutputStream) {
        val w = bmp.width; val h = bmp.height
        val bmpData = IntArray(w * h)
        bmp.getPixels(bmpData, 0, w, 0, 0, w, h)
        val size = w * h * 3 + 54
        val header = ByteArray(54).apply {
            this[0] = 'B'.toByte(); this[1] = 'M'.toByte()
            this[2] = (size and 0xFF).toByte(); this[3] = ((size shr 8) and 0xFF).toByte()
            this[4] = ((size shr 16) and 0xFF).toByte(); this[5] = ((size shr 24) and 0xFF).toByte()
            this[10] = 54.toByte(); this[14] = 40.toByte()
            this[18] = (w and 0xFF).toByte(); this[19] = ((w shr 8) and 0xFF).toByte()
            this[20] = ((w shr 16) and 0xFF).toByte(); this[21] = ((w shr 24) and 0xFF).toByte()
            this[22] = (h and 0xFF).toByte(); this[23] = ((h shr 8) and 0xFF).toByte()
            this[24] = ((h shr 16) and 0xFF).toByte(); this[25] = ((h shr 24) and 0xFF).toByte()
            this[26] = 1.toByte(); this[28] = 24.toByte()
        }
        out.write(header)
        val row = ByteArray(w * 3)
        for (i in h - 1 downTo 0) {
            var ptr = 0
            for (j in 0 until w) {
                val c = bmpData[i * w + j]
                row[ptr++] = (c and 0xFF).toByte()
                row[ptr++] = ((c shr 8) and 0xFF).toByte()
                row[ptr++] = ((c shr 16) and 0xFF).toByte()
            }
            out.write(row)
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Three-Dot Core Engineering Context Actions Menu
    // ──────────────────────────────────────────────────────────────

    fun onMenuClick(v: View) {
        closeInlineEditor(true)
        val popup = PopupMenu(this, v)
        val items = listOf("Save", "Save As", "Export", "Export Window", "Clear Canvas")
        items.forEach { popup.menu.add(it) }
        if (currentFileName != null) popup.menu.add("Delete This Note")
        popup.menu.add("Add to Book")
        val externals = listOf("📄 Open PDF", "📊 Chart Builder", "✍ Handwriting to Text", "⚙ Settings", "Exit")
        externals.forEach { popup.menu.add(it) }

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Save" -> saveCurrent()
                "Save As" -> saveAsNew()
                "Export" -> showExportDialog()
                "Export Window" -> { drawingView.currentTool = Tool.EXPORT_WINDOW; tvActiveTool.text = "SnipWindow" }
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

    private fun showExportDialog() {
        val opts = arrayOf("PNG Image Layout", "JPEG Image Compact", "Windows BMP Bitmap", "Adobe PDF Document", "Plain Text Extract (TXT)", "Microsoft Word Document (DOCX)")
        AlertDialog.Builder(this).setTitle("Export Workspace")
            .setItems(opts) { _, idx ->
                val bmp = drawingView.exportBitmap()
                pendingExportBitmap = bmp
                val timestamp = System.currentTimeMillis()
                when (idx) {
                    0 -> { pendingExportFormat = "png"; saveImageLauncher.launch("export_$timestamp.png") }
                    1 -> { pendingExportFormat = "jpg"; saveImageLauncher.launch("export_$timestamp.jpg") }
                    2 -> { pendingExportFormat = "bmp"; saveImageLauncher.launch("export_$timestamp.bmp") }
                    3 -> savePdfLauncher.launch("export_$timestamp.pdf")
                    4 -> saveTxtLauncher.launch("export_$timestamp.txt")
                    5 -> saveDocxLauncher.launch("export_$timestamp.docx")
                }
            }.show()
    }

    private fun saveCurrent() {
        val name = currentFileName
        if (name == null) {
            saveAsNew()
        } else {
            val file = File(getDrawingsFolder(), "$name.eng")
            val outputStr = drawingView.serialize()
            file.writeText(outputStr)
            lastSavedContent = outputStr
            Toast.makeText(this, "Saved $name successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsNew() {
        val input = EditText(this).apply { hint = "Enter file structural profile label" }
        AlertDialog.Builder(this).setTitle("Save Note Layout").setView(input)
            .setPositiveButton("Save Profile") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotBlank()) {
                    currentFileName = txt
                    tvTitle.text = txt
                    saveCurrent()
                } else {
                    Toast.makeText(this@MainActivity, "Profile title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun autoSave() {
        val name = currentFileName ?: "Autosave_${System.currentTimeMillis()}"
        val content = drawingView.serialize()
        if (content == lastSavedContent || !drawingView.hasContent()) return
        try {
            val file = File(getDrawingsFolder(), "$name.eng")
            file.writeText(content)
            lastSavedContent = content
            if (currentFileName == null) {
                currentFileName = name
                tvTitle.text = name
            }
        } catch (e: Exception) {}
    }

    private fun deleteCurrentNote() {
        val name = currentFileName ?: return
        AlertDialog.Builder(this).setTitle("Confirm permanent deletion").setMessage("Delete $name fully from persistent disk?")
            .setPositiveButton("Delete Asset") { _, _ ->
                val f = File(getDrawingsFolder(), "$name.eng")
                if (f.exists()) f.delete()
                drawingView.clearAll()
                currentFileName = null
                tvTitle.text = "New Note"
                lastSavedContent = ""
                Toast.makeText(this@MainActivity, "Deleted profile asset", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showAddToBookDialog() {
        val folder = File(filesDir, "books").apply { if (!exists()) mkdirs() }
        val books = folder.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        AlertDialog.Builder(this).setTitle("Assign context directory path")
            .setItems(books.toTypedArray()) { _, idx ->
                val bName = books[idx]
                val currentFile = currentFileName?.let { File(getDrawingsFolder(), "$it.eng") }
                if (currentFile != null && currentFile.exists()) {
                    val destB = File(File(filesDir, "books"), bName)
                    val targetF = File(destB, currentFile.name)
                    currentFile.copyTo(targetF, overwrite = true)
                    Toast.makeText(this@MainActivity, "Linked safely into book workspace: $bName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Save note context locally first", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun showSettingsDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16)) }
        val prefs = getPrefs()

        val cbClear = CheckBox(this).apply { text = "Confirm exit/clear canvas alert logs"; isChecked = prefs.getBoolean("confirm_exit_clear", true) }
        val cbAuto = CheckBox(this).apply { text = "Enable background persistent Autosave loops"; isChecked = prefs.getBoolean("autosave", true) }

        val tvArc = TextView(this).apply { text = "Arc segment density subdivision profile count:"; setPadding(0, dp(8), 0, dp(4)) }
        val etArc = EditText(this).apply { setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText(drawingView.arcDivisions.toString()) }

        container.addView(cbClear); container.addView(cbAuto)
        container.addView(tvArc); container.addView(etArc)

        AlertDialog.Builder(this).setTitle("System Settings Control").setView(container)
            .setPositiveButton("Apply Modifications") { _, _ ->
                val arcVal = etArc.text.toString().toIntOrNull() ?: 3
                drawingView.arcDivisions = arcVal.coerceIn(2, 12)
                prefs.edit()
                    .putBoolean("confirm_exit_clear", cbClear.isChecked)
                    .putBoolean("autosave", cbAuto.isChecked)
                    .putInt("arc_divisions", drawingView.arcDivisions)
                    .apply()
                Toast.makeText(this@MainActivity, "Configuration committed successfully", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmThenExit() {
        val changed = drawingView.serialize() != lastSavedContent && drawingView.hasContent()
        if (!changed) { finish(); return }
        if (getPrefs().getBoolean("autosave", true)) { autoSave(); finish(); return }
        if (getPrefs().getBoolean("confirm_exit_clear", true)) {
            AlertDialog.Builder(this).setTitle("Unsaved Engine Progress").setMessage("Save layout modifications before exit execution?")
                .setPositiveButton("Commit Profile Save") { _, _ -> saveCurrent(); finish() }
                .setNeutralButton("Discard Changes") { _, _ -> finish() }
                .setNegativeButton("Cancel Transaction", null).show()
        } else { autoSave(); finish() }
    }

    private fun confirmThenClear() {
        if (getPrefs().getBoolean("confirm_exit_clear", true) && drawingView.hasContent()) {
            AlertDialog.Builder(this).setTitle("Clear Workspace Graphics?").setMessage("This will wipe all vector tracking nodes completely.")
                .setPositiveButton("Wipe Workspace Canvas") { _, _ -> drawingView.clearAll() }.setNegativeButton("Retain Graphics", null).show()
        } else drawingView.clearAll()
    }

    // ──────────────────────────────────────────────────────────────
    //  Lifecycle Hooks
    // ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        closeInlineEditor(commit = true)
        dismissCellEditor()
        autoSave()
        if (isRecording) { AudioHelper.stopRecording(); isRecording = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacks(autosaveRunnable)
        AudioHelper.releaseAll()
    }

    private fun getDrawingsFolder(): File = File(filesDir, "drawings").apply { if (!exists()) mkdirs() }
}
