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
    private lateinit var btnLayoutToggle: ImageButton

    private var currentFileName: String? = null
    private var lastSavedContent: String = ""
    private val PT_TO_PX = 1.333f
    private var isConvenientLayout = true

    private val shapeEntries: List<Pair<Int, Tool>> = listOf(
        R.drawable.ic_shape_line to Tool.LINE,
        R.drawable.ic_shape_rectangle to Tool.RECTANGLE,
        R.drawable.ic_shape_rounded_rect to Tool.ROUNDED_RECT,
        R.drawable.ic_shape_circle to Tool.CIRCLE,
        R.drawable.ic_shape_ellipse to Tool.ELLIPSE,
        R.drawable.ic_shape_triangle to Tool.TRIANGLE,
        R.drawable.ic_shape_triangle_down to Tool.TRIANGLE_DOWN,
        R.drawable.ic_shape_diamond to Tool.DIAMOND,
        R.drawable.ic_shape_arrow to Tool.ARROW,
        R.drawable.ic_shape_double_arrow to Tool.DOUBLE_ARROW,
        R.drawable.ic_shape_star to Tool.STAR,
        R.drawable.ic_shape_pentagon to Tool.PENTAGON,
        R.drawable.ic_shape_hexagon to Tool.HEXAGON,
        R.drawable.ic_shape_heptagon to Tool.HEPTAGON,
        R.drawable.ic_shape_octagon to Tool.OCTAGON,
        R.drawable.ic_shape_nonagon to Tool.NONAGON,
        R.drawable.ic_shape_decagon to Tool.DECAGON,
        R.drawable.ic_shape_curve to Tool.CURVE,
        R.drawable.ic_shape_cross to Tool.CROSS,
        R.drawable.ic_arc to Tool.ARC,
        R.drawable.ic_shape_trapezoid to Tool.TRAPEZOID,
        R.drawable.ic_shape_parallelogram to Tool.PARALLELOGRAM,
        R.drawable.ic_shape_right_triangle to Tool.RIGHT_TRIANGLE,
        R.drawable.ic_shape_isosceles to Tool.ISOSCELES_TRIANGLE,
        R.drawable.ic_shape_semicircle to Tool.SEMICIRCLE,
        R.drawable.ic_shape_half_ellipse to Tool.HALF_ELLIPSE,
        R.drawable.ic_shape_teardrop to Tool.TEARDROP,
        R.drawable.ic_shape_heart to Tool.HEART,
        R.drawable.ic_shape_plus_thick to Tool.PLUS_THICK,
        R.drawable.ic_shape_bracket_l to Tool.BRACKET_L,
        R.drawable.ic_shape_bracket_r to Tool.BRACKET_R,
        R.drawable.ic_shape_cloud to Tool.CLOUD,
        R.drawable.ic_shape_speech_bubble to Tool.SPEECH_BUBBLE,
        R.drawable.ic_shape_lightning to Tool.LIGHTNING,
        R.drawable.ic_shape_moon to Tool.MOON,
        R.drawable.ic_shape_chevron_right to Tool.CHEVRON_RIGHT,
        R.drawable.ic_shape_chevron_left to Tool.CHEVRON_LEFT,
        R.drawable.ic_shape_chevron_up to Tool.CHEVRON_UP,
        R.drawable.ic_shape_chevron_down to Tool.CHEVRON_DOWN,
        R.drawable.ic_shape_rhombus_tall to Tool.RHOMBUS_TALL,
        R.drawable.ic_shape_gear to Tool.GEAR,
        R.drawable.ic_shape_shield to Tool.SHIELD,
        R.drawable.ic_shape_ring to Tool.RING,
        R.drawable.ic_shape_block_arrow_right to Tool.BLOCK_ARROW_RIGHT,
        R.drawable.ic_shape_block_arrow_left to Tool.BLOCK_ARROW_LEFT,
        R.drawable.ic_shape_block_arrow_up to Tool.BLOCK_ARROW_UP,
        R.drawable.ic_shape_block_arrow_down to Tool.BLOCK_ARROW_DOWN,
        R.drawable.ic_shape_square_small to Tool.SQUARE_ROUNDED_SMALL,
        R.drawable.ic_shape_burst to Tool.BURST,
        R.drawable.ic_shape_five_burst to Tool.FIVE_POINT_BURST,
        R.drawable.ic_shape_frame to Tool.FRAME,
        R.drawable.ic_shape_plaque to Tool.PLAQUE,
        R.drawable.ic_shape_octagon_stop to Tool.OCTAGON_STOP
    )

    private var activeEditText: EditText? = null
    private var activeToolbar: View? = null
    private var activeEditBox: View? = null
    private var activeEditorHandles: List<View> = emptyList()
    private var editingItem: TextItem? = null
    private var editWorldX = 0f; private var editWorldY = 0f
    private var editRotation = 0f; private var editColor = Color.BLACK
    private var editSize = 12f * 1.333f
    private var editOpacity = 255
    private var pendingBold = false; private var pendingItalic = false
    private var pendingUnderline = false; private var pendingHighlight: Int? = null
    private var pendingFontFamily: String = "sans-serif"
    private var cameraImageFile: File? = null
    private var activeToolbarButton: ImageButton? = null
    private var isSwitchingTextEditor = false
    private var exportWindowBitmap: Bitmap? = null
    private var pendingExportBitmap: Bitmap? = null
    private var pendingExportFormat: String = "png"
    private var shapesPickerOverlay: LinearLayout? = null

    private var activeCellEditText: EditText? = null
    private var activeCellToolbar: View? = null
    private var tableToolbarOverlay: View? = null

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

    // Only Android system typefaces that are genuinely visually distinct are listed here -
    // earlier versions included several names (Notosans, Source Sans, Comic, Serif Light,
    // Elegant Script) that all silently resolved to the same underlying font as something else
    // already in the list, so picking them looked identical. Each entry below maps to a real,
    // different system typeface.
    private val availableFonts = listOf(
        "Default (Sans)" to "sans-serif",
        "Serif" to "serif",
        "Monospace (LaTeX-style)" to "monospace",
        "Sans Condensed" to "sans-serif-condensed",
        "Sans Light" to "sans-serif-light",
        "Sans Thin" to "sans-serif-thin",
        "Sans Medium" to "sans-serif-medium",
        "Sans Black (Bold)" to "sans-serif-black",
        "Serif Monospace" to "serif-monospace",
        "Casual / Handwriting" to "casual",
        "Cursive / Script" to "cursive",
        "Small Caps" to "sans-serif-smallcaps",
        "Sans Condensed Light" to "sans-serif-condensed-light",
        "Sans Condensed Medium" to "sans-serif-condensed-medium",
        "Sans Narrow" to "sans-serif-condensed"
    )

    private fun showFontPickerDialog(et: EditText) {
        // Render each font name IN its own typeface so the list is a genuine live preview,
        // not just text labels - per request, you should be able to see the actual writing
        // style of each font, not just its name in the default font.
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
        lateinit var dialog: AlertDialog
        for ((label, family) in availableFonts) {
            val row = TextView(this).apply {
                text = label
                textSize = 18f
                setTextColor(Color.parseColor("#212121"))
                setPadding(dp(20), dp(14), dp(20), dp(14))
                try { typeface = Typeface.create(family, Typeface.NORMAL) } catch (e: Exception) {}
                setOnClickListener {
                    pendingFontFamily = family
                    editingItem?.let { it.fontFamily = family }
                    try { et.typeface = Typeface.create(family, Typeface.NORMAL) } catch (e: Exception) {}
                    dialog.dismiss()
                }
            }
            container.addView(row)
            container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); setBackgroundColor(Color.parseColor("#EEEEEE")) })
        }
        scroll.addView(container)
        dialog = AlertDialog.Builder(this).setTitle("Font").setView(scroll).setNegativeButton("Cancel", null).create()
        dialog.show()
    }

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
            val density = resources.displayMetrics.density
            var w = bmp.width / density
            var h = bmp.height / density
            val maxW = drawingView.width / drawingView.getScaleFactor() * 0.9f
            val maxH = drawingView.height / drawingView.getScaleFactor() * 0.9f
            if (w > maxW) { val s = maxW / w; w *= s; h *= s }
            if (h > maxH) { val s = maxH / h; w *= s; h *= s }
            drawingView.addImage(path, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
            Toast.makeText(this, "PDF snip added - drag corners to resize", Toast.LENGTH_SHORT).show()
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

    // OCR-specific launchers - separate from the normal image-insert launchers above because the
    // result should be run through text recognition and shown for review, not dropped onto the
    // canvas as a picture.
    private val pickImageForOcrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runOcrOnUri(uri)
    }
    private val pickPdfForOcrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) ocrSnipLauncher.launch(android.content.Intent(this, PdfViewerActivity::class.java).putExtra("pdf_uri", uri.toString()))
    }
    private val ocrSnipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("snip_image_path")
            if (path != null) runOcrOnUri(Uri.fromFile(File(path)))
        }
    }
    private var ocrCameraFile: File? = null
    private val takePictureForOcrLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) ocrCameraFile?.let { runOcrOnUri(Uri.fromFile(it)) }
    }

    private fun launchCameraForOcr() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val folder = File(filesDir, "images").also { it.mkdirs() }
                val pf = File(folder, "ocr_camera_${System.currentTimeMillis()}.jpg"); ocrCameraFile = pf
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", pf)
                takePictureForOcrLauncher.launch(uri)
            } catch (e: Exception) { Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show() }
        } else {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Runs ML Kit's on-device Text Recognition (free, fully offline once the model is bundled
    // with the app - no network call, no per-use cost) on the given image and shows the result
    // in an editable dialog before inserting it as a text item on the canvas.
    //
    // IMPORTANT LIMITATION: this recognizes general printed/handwritten TEXT character-by-
    // character. It is NOT a math-aware OCR engine - it has no understanding of equation
    // structure (fractions, exponents, square roots, summations, matrices, etc.) and will read
    // math notation as a flat left-to-right sequence of characters/symbols. True structured math
    // recognition (the kind that outputs real LaTeX for fractions, exponents, etc.) is a
    // significantly harder problem that, as far as free/offline-capable options go, doesn't have
    // a good solution at the moment - the well-known accurate engines (e.g. Mathpix) are
    // cloud-based paid services. Simple expressions with no special structure (e.g. "x + 2 = 5")
    // will often come through fine.
    private fun runOcrOnUri(uri: Uri) {
        val progress = android.app.ProgressDialog(this).apply { setMessage("Reading text..."); setCancelable(false); show() }
        try {
            val image = com.google.mlkit.vision.common.InputImage.fromFilePath(this, uri)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    progress.dismiss()
                    showOcrResultDialog(result.text)
                }
                .addOnFailureListener { e ->
                    progress.dismiss()
                    Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progress.dismiss()
            Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOcrResultDialog(text: String) {
        if (text.isBlank()) { Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show(); return }
        val et = EditText(this).apply {
            setText(text); minLines = 4; maxLines = 14; gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val scroll = ScrollView(this).apply { addView(et) }
        AlertDialog.Builder(this).setTitle("Extracted Text (edit if needed)").setView(scroll)
            .setPositiveButton("Insert") { _, _ ->
                val finalText = et.text.toString()
                if (finalText.isNotBlank()) {
                    drawingView.addText(finalText, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), drawingView.defaultTextSize, 0f, drawingView.currentColor)
                    Toast.makeText(this, "Text inserted", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraInternal()
        else Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showAudioRecordDialog()
        else Toast.makeText(this, "Microphone permission is required to record audio", Toast.LENGTH_LONG).show()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView     = findViewById(R.id.drawingView)
        canvasContainer = findViewById(R.id.canvasContainer)
        canvasContainer.clipChildren = false
        canvasContainer.clipToPadding = false
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

        drawingView.onTextEditRequest       = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTextSelectRequest     = { item, sx, sy -> showTextSelectionBox(item, sx, sy) }
        drawingView.onTextDeselectRequest   = { dismissTextSelectionBox() }
        drawingView.onEmptyAreaTap          = {
            // Tapping genuinely empty canvas is the "I'm done" signal: commit and close whatever
            // editor is open (text or table cell), and bring the bottom toolbar back if a table
            // editor had hidden it.
            if (activeEditText != null) closeInlineEditor(true)
            if (activeCellEditText != null) dismissCellEditor()
            dismissTextSelectionBox()
            setBottomBarVisible(true)
        }
        drawingView.onLinkTap               = { target -> navigateToLink(target) }
        drawingView.onTableCellEditRequest  = { table, row, col, sx, sy -> closeInlineEditor(true); dismissCellEditor(); showTableCellEditor(table,row,col,sx,sy) }
        drawingView.onExportWindowSelected  = { l,t,r,b -> exportWindowBitmap = drawingView.exportWindow(l,t,r,b); showExportWindowDialog() }
        drawingView.onAudioItemTap          = { item -> AudioHelper.togglePlay(item) { drawingView.invalidate() }; drawingView.invalidate() }

        setupBottomToolbar()
        setActiveTool(null, Tool.SELECT)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    activeEditText != null -> closeInlineEditor(true)
                    activeCellEditText != null -> dismissCellEditor()
                    tableToolbarOverlay != null -> { tableToolbarOverlay?.let { canvasContainer.removeView(it) }; tableToolbarOverlay = null }
                    drawingView.currentTool != Tool.SELECT -> setActiveTool(null, Tool.SELECT)
                    else -> confirmThenExit()
                }
            }
        })

        autosaveHandler.postDelayed(autosaveRunnable, 10_000L)
    }

    private fun setupBottomToolbar() {
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { closeInlineEditor(true); drawingView.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { closeInlineEditor(true); drawingView.redo() }
        findViewById<ImageButton>(R.id.btnDraw).setOnClickListener { closeInlineEditor(true); setActiveTool(it as ImageButton, Tool.PEN) }
        findViewById<ImageButton>(R.id.btnDraw).setOnLongClickListener { showPenOptionsPanel(); true }
        findViewById<ImageButton>(R.id.btnQuickEraser).setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.ERASER) showEraserModePopup(btn)
            else setActiveTool(btn as ImageButton, Tool.ERASER)
        }
        findViewById<ImageButton>(R.id.btnQuickEraser).setOnLongClickListener { showEraserOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnSelect)?.setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.SELECT) showSelectModePopup(btn)
            else setActiveTool(btn as ImageButton, Tool.SELECT)
        }
        findViewById<ImageButton>(R.id.btnText).setOnClickListener { closeInlineEditor(true); setActiveTool(it as ImageButton, Tool.TEXT) }
        findViewById<ImageButton>(R.id.btnInsert).setOnClickListener { showInsertMenu() }
        findViewById<ImageButton>(R.id.btnTools).setOnClickListener { showShapesPicker(it as ImageButton) }

        val toolbarScroll = findViewById<View>(R.id.toolbarScroll)
        val btnExpand = findViewById<ImageButton>(R.id.btnExpand)
        btnExpand.setOnClickListener {
            val show = toolbarScroll.visibility != View.VISIBLE
            toolbarScroll.visibility = if (show) View.VISIBLE else View.GONE
            btnExpand.rotation = if (show) 180f else 0f
        }

        findViewById<ImageButton?>(R.id.btnQuickColor)?.setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c } }
        findViewById<ImageButton?>(R.id.btnQuickSize)?.setOnClickListener { showSizePicker() }
        findViewById<ImageButton?>(R.id.btnQuickFill)?.setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.FILL) showFillModePopup(btn)
            else { setActiveTool(btn as ImageButton, Tool.FILL) }
        }
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnClickListener { btn -> closeInlineEditor(true); setActiveTool(btn as ImageButton, Tool.HIGHLIGHTER) }
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnLongClickListener { showHighlighterOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnClickListener { btn -> closeInlineEditor(true); setActiveTool(btn as ImageButton, Tool.BRUSH) }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnLongClickListener { showBrushOptionsPanel(); true }

        findViewById<ImageButton?>(R.id.btnMenu)?.setOnClickListener { onMenuClick(it) }
        findViewById<ImageButton?>(R.id.btnLink)?.setOnClickListener { closeInlineEditor(true); showLinkPickerDialog() }
        findViewById<ImageButton?>(R.id.btnBack)?.setOnClickListener { confirmThenExit() }
        btnLayoutToggle.setOnClickListener { showLayoutMenu(it) }
    }

    private fun applyConvenientLayout() {
        isConvenientLayout = true
        drawingView.canvasMode = CanvasMode.CONVENIENT
        drawingView.invalidate()
    }

    private fun applyPrintLayout() {
        isConvenientLayout = false
        drawingView.canvasMode = CanvasMode.PAGINATED
        drawingView.paperSize = PaperSizeOption.A4
        drawingView.invalidate()
    }

    private fun applyInfiniteLayout() {
        isConvenientLayout = false
        drawingView.canvasMode = CanvasMode.INFINITE
        drawingView.invalidate()
    }

    private fun showLayoutMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Convenient")
        popup.menu.add("Print (A4)")
        popup.menu.add("Infinite Canvas")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Convenient" -> applyConvenientLayout()
                "Print (A4)" -> {
                    AlertDialog.Builder(this)
                        .setTitle("Switch to Print Layout")
                        .setMessage("Print layout uses real A4 size. How should existing text be handled?")
                        .setPositiveButton("Rearrange (wrap to fit)") { _, _ -> applyPrintLayout(); drawingView.rearrangeTextForPrint() }
                        .setNegativeButton("Keep as is") { _, _ -> applyPrintLayout(); drawingView.keepTextAsIs() }
                        .setNeutralButton("Cancel", null).show()
                }
                "Infinite Canvas" -> applyInfiniteLayout()
            }
            true
        }
        popup.show()
    }

    private fun setActiveTool(btn: ImageButton?, tool: Tool) { drawingView.currentTool = tool; setActiveToolbarBtn(btn); dismissPenOptionsPanel(); dismissEraserOptionsPanel(); dismissHighlighterOptionsPanel(); dismissBrushOptionsPanel(); dismissShapesPicker() }
    private fun setActiveToolbarBtn(btn: ImageButton?) { activeToolbarButton?.isSelected = false; activeToolbarButton = btn; btn?.isSelected = true }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Hides the primary bottom toolbar while typing in a table cell (so the keyboard has more
    // room and the toolbar doesn't visually compete with it), and brings it back when the user
    // taps outside to dismiss the keyboard.
    private fun setBottomBarVisible(visible: Boolean) {
        findViewById<View?>(R.id.primaryToolbarScroll)?.visibility = if (visible) View.VISIBLE else View.GONE
    }
    private fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    private fun showEraserModePopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Object Eraser")
        popup.menu.add("Area Eraser")
        popup.setOnMenuItemClickListener { item ->
            drawingView.eraserMode = if (item.title == "Object Eraser") EraserMode.OBJECT else EraserMode.AREA
            Toast.makeText(this, item.title, Toast.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun showSelectModePopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Select (default)")
        popup.menu.add("Box Select (drag a rectangle)")
        popup.menu.add("Lasso (freeform loop)")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Select (default)" -> setActiveTool(null, Tool.SELECT)
                "Box Select (drag a rectangle)" -> {
                    setActiveTool(null, Tool.AUTOSELECT)
                    Toast.makeText(this, "Drag left-to-right to select fully enclosed items, right-to-left to select anything touched", Toast.LENGTH_LONG).show()
                }
                "Lasso (freeform loop)" -> {
                    setActiveTool(null, Tool.LASSO)
                    Toast.makeText(this, "Trace a loop around items to select them", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showFillModePopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Pick Fill Color")
        popup.menu.add(if (drawingView.fillShapes) "Auto-fill: On" else "Auto-fill: Off")
        popup.setOnMenuItemClickListener { item ->
            when {
                item.title == "Pick Fill Color" -> showColorGridDialog { c -> drawingView.fillColor = c }
                item.title.toString().startsWith("Auto-fill") -> { drawingView.fillShapes = !drawingView.fillShapes; Toast.makeText(this, if (drawingView.fillShapes) "Auto-fill: On" else "Auto-fill: Off", Toast.LENGTH_SHORT).show() }
            }
            true
        }
        popup.show()
    }

    fun onMenuClick(v: View) {
        closeInlineEditor(true)
        val popup = PopupMenu(this, v)
        popup.menu.add("Note: ${currentFileName ?: "Untitled"}")
        popup.menu.add("Rename Note")
        listOf("Save","Save As","Export","Export Window","Clear Canvas").forEach { popup.menu.add(it) }
        if (currentFileName != null) popup.menu.add("Delete This Note")
        popup.menu.add("Add to Book")
        listOf("Open PDF","Chart Builder","Handwriting to Text","Settings","About","Exit").forEach { popup.menu.add(it) }
        popup.setOnMenuItemClickListener { item ->
            when {
                item.title.toString().startsWith("Note:") -> {}
                item.title == "Rename Note" -> showRenameDialog()
                item.title == "Save" -> saveCurrent()
                item.title == "Save As" -> saveAsNew()
                item.title == "Export" -> showExportDialog()
                item.title == "Export Window" -> {
                    if(drawingView.canvasMode == CanvasMode.INFINITE || drawingView.canvasMode == CanvasMode.CONVENIENT) {
                        Toast.makeText(this,"Draw a rectangle to select export area",Toast.LENGTH_SHORT).show()
                        setActiveTool(null, Tool.EXPORT_WINDOW)
                    } else Toast.makeText(this,"Switch to Infinite/Convenient canvas for window export",Toast.LENGTH_SHORT).show()
                }
                item.title == "Clear Canvas" -> confirmThenClear()
                item.title == "Delete This Note" -> deleteCurrentNote()
                item.title == "Add to Book" -> showAddToBookDialog()
                item.title == "Open PDF" -> pickPdfLauncher.launch("application/pdf")
                item.title == "Chart Builder" -> chartLauncher.launch(android.content.Intent(this, ChartActivity::class.java))
                item.title == "Handwriting to Text" -> startActivity(android.content.Intent(this, HandwritingActivity::class.java))
                item.title == "Settings" -> showSettingsDialog()
                item.title == "About" -> showAboutDialog()
                item.title == "Exit" -> confirmThenExit()
            }
            true
        }
        popup.show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply { setText(currentFileName ?: nextAutoName()) }
        AlertDialog.Builder(this).setTitle("Rename Note").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentFileName) {
                    val oldFile = currentFileName?.let { File(getDrawingsFolder(), "$it.eng") }
                    currentFileName = newName; tvTitle.text = newName
                    writeCurrentFile()
                    if (oldFile != null && oldFile.exists() && oldFile.nameWithoutExtension != newName) oldFile.delete()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun nextAutoName(): String {
        val existing = getDrawingsFolder().listFiles()
            ?.mapNotNull { f ->
                val n = f.nameWithoutExtension
                if (n.startsWith("Note ")) n.removePrefix("Note ").trim().toIntOrNull() else null
            }?.toSet() ?: emptySet()
        var i = 1
        while (existing.contains(i)) i++
        return "Note $i"
    }

    private fun showAddToBookDialog() {
        val books = File(filesDir,"books").listFiles()?.filter { it.isDirectory }?.map { it.name } ?: listOf("General")
        AlertDialog.Builder(this).setTitle("Add to Book")
            .setItems(books.toTypedArray()) { _, i ->
                val targetBook = books[i]
                val name = currentFileName ?: nextAutoName()
                currentFileName = name; tvTitle.text = name
                val targetFolder = File(File(filesDir,"books"), targetBook).also { it.mkdirs() }
                File(targetFolder,"$name.eng").writeText(drawingView.serialize())
                lastSavedContent = drawingView.serialize()
                Toast.makeText(this,"Added to $targetBook",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel",null).show()
    }

    // ── Linking: pick a book, then a note within it, to create a tappable link ──────

    private fun showLinkPickerDialog() {
        val booksFolder = File(filesDir, "books")
        val books = booksFolder.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
        if (books.isEmpty()) { Toast.makeText(this, "No other notes to link to yet", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Link to Book")
            .setItems(books.toTypedArray()) { _, i -> showLinkNotePickerDialog(books[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showLinkNotePickerDialog(bookName: String) {
        val folder = File(File(filesDir, "books"), bookName)
        val notes = folder.listFiles()?.filter { it.extension == "eng" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
        if (notes.isEmpty()) { Toast.makeText(this, "No notes in $bookName yet", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Link to Note in $bookName")
            .setItems(notes.toTypedArray()) { _, i ->
                val noteName = notes[i]
                val target = "$bookName/$noteName"
                insertOrConvertToLink(target, noteName)
            }
            .setNegativeButton("Cancel", null).show()
    }

    // If a text item is currently selected, converts it into a link to the chosen target.
    // Otherwise creates a new link text item at the center of the screen showing the note's name.
    private fun insertOrConvertToLink(target: String, displayName: String) {
        val sel = textSelectionItem
        if (sel != null) {
            sel.linkTarget = target
            dismissTextSelectionBox()
            drawingView.invalidate()
            Toast.makeText(this, "Linked to $displayName", Toast.LENGTH_SHORT).show()
        } else {
            val item = TextItem(displayName, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), Color.parseColor("#1565C0"), drawingView.defaultTextSize, 0f)
            item.linkTarget = target
            drawingView.addLinkText(item)
            Toast.makeText(this, "Link inserted", Toast.LENGTH_SHORT).show()
        }
    }

    // Navigates to a linked note. The CURRENT note (book + filename, or "unsaved" if it hasn't
    // been saved yet) is pushed onto a back-stack carried via Intent extras, so pressing the
    // system back button on the linked note returns to exactly where the link was tapped from -
    // not to the home/book-list screen. This chains correctly through multiple link hops too,
    // since each new activity instance just appends itself to the stack it received.
    private fun navigateToLink(target: String) {
        val parts = target.split("/")
        if (parts.size < 2) { Toast.makeText(this, "Broken link", Toast.LENGTH_SHORT).show(); return }
        val targetBook = parts[0]; val targetNote = parts[1]
        val targetFile = File(File(File(filesDir, "books"), targetBook), "$targetNote.eng")
        if (!targetFile.exists()) { Toast.makeText(this, "Linked note no longer exists", Toast.LENGTH_SHORT).show(); return }

        // Autosave the current note before leaving so nothing is lost, and so a future "back"
        // navigation can find it.
        if (drawingView.hasContent()) autoSave()

        val currentBook = intent.getStringExtra("book_name") ?: "General"
        val currentNote = currentFileName
        val backStack = intent.getStringArrayListExtra("link_back_stack") ?: ArrayList()
        if (currentNote != null) {
            // Only push a real, saved note onto the stack - an unsaved/untitled note can't be
            // navigated back to meaningfully.
            backStack.add("$currentBook/$currentNote")
        }

        val newIntent = android.content.Intent(this, MainActivity::class.java)
        newIntent.putExtra("book_name", targetBook)
        newIntent.putExtra("filename", targetNote)
        newIntent.putStringArrayListExtra("link_back_stack", backStack)
        startActivity(newIntent)
        // Don't finish() this activity - rely on the natural Android back-stack (this activity
        // instance stays underneath) so "back" returns here automatically. This also means each
        // hop in a multi-link chain naturally unwinds in reverse order.
    }

    private fun showShapesPicker(anchor: ImageButton) {
        // Toggle: if already showing, just close it
        if (shapesPickerOverlay != null) {
            dismissShapesPicker()
            return
        }
        dismissAllFloatingPanels()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(6).toFloat()
            setPadding(0, dp(6), 0, dp(6))
        }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6),0,dp(6),0); gravity = Gravity.CENTER_VERTICAL }
        for ((iconRes, tool) in shapeEntries) {
            val b = ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(7), dp(7), dp(7), dp(7))
                setBackgroundResource(R.drawable.btn_toolbar_selector)
                val p = LinearLayout.LayoutParams(dp(40), dp(40)); p.setMargins(dp(2),0,dp(2),0)
                layoutParams = p
                setOnClickListener {
                    setActiveTool(null, tool)
                    dismissShapesPicker()
                }
            }
            row.addView(b)
        }
        scroll.addView(row)
        container.addView(scroll)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(54), Gravity.BOTTOM)
        canvasContainer.addView(container, lp)
        shapesPickerOverlay = container
    }

    private fun dismissShapesPicker() {
        shapesPickerOverlay?.let { canvasContainer.removeView(it) }
        shapesPickerOverlay = null
    }

    // Closes any open floating panel (shapes picker, pen options, eraser options) -
    // called whenever the user picks a different tool so panels never get stuck open.
    private fun dismissAllFloatingPanels() {
        dismissShapesPicker()
        dismissPenOptionsPanel()
        dismissEraserOptionsPanel()
        dismissHighlighterOptionsPanel()
        dismissBrushOptionsPanel()
    }

    private fun showInsertMenu() {
        closeInlineEditor(true)
        AlertDialog.Builder(this).setTitle("Insert")
            .setItems(arrayOf("Image from Gallery","Take Photo","Table","Record Audio","Snip from PDF","Extract Text (OCR)")) { _, i ->
                // Whatever tool was active before opening Insert (e.g. Eraser) must not remain
                // active afterward - otherwise the next tap on the canvas silently erases content.
                setActiveTool(null, Tool.SELECT)
                when(i) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> launchCamera()
                    2 -> showTableInsertDialog()
                    3 -> checkAndRecordAudio()
                    4 -> pickPdfLauncher.launch("application/pdf")
                    5 -> showOcrSourceDialog()
                }
            }.show()
    }

    private fun showOcrSourceDialog() {
        AlertDialog.Builder(this).setTitle("Extract Text From")
            .setItems(arrayOf("Photo (camera)", "Image from Gallery", "Snip from PDF")) { _, i ->
                when (i) {
                    0 -> launchCameraForOcr()
                    1 -> pickImageForOcrLauncher.launch("image/*")
                    2 -> pickPdfForOcrLauncher.launch("application/pdf")
                }
            }.show()
    }

    private fun showAboutDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)); gravity = Gravity.CENTER_HORIZONTAL }
        try {
            val icon = ImageView(this).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(80))
            }
            container.addView(icon)
        } catch (e: Exception) {}
        container.addView(TextView(this).apply {
            text = "EngiNotes"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2A2A2A")); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        if (!versionName.isNullOrBlank()) {
            container.addView(TextView(this).apply {
                text = "Version $versionName"; textSize = 13f; setTextColor(Color.parseColor("#9E9E9E")); gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            })
        }
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)); setBackgroundColor(Color.parseColor("#EEEEEE")) })
        container.addView(TextView(this).apply {
            text = "Developed by Amrit Khadka"; textSize = 15f; setTextColor(Color.parseColor("#2A2A2A")); gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        })
        container.addView(TextView(this).apply {
            text = "Contributor: Avinash Khadgi"; textSize = 14f; setTextColor(Color.parseColor("#5A5A5A")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        AlertDialog.Builder(this).setView(container).setPositiveButton("Close", null).show()
    }

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
            modeLbl.text = if(isConvenientLayout) "Layout: Convenient  (use icon in top bar)" else "Layout: ${drawingView.canvasMode}  (use icon in top bar)"
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
        AlertDialog.Builder(this).setTitle("Settings").setView(scroll)
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

    private fun checkAndRecordAudio() {
        when {
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED -> showAudioRecordDialog()
            else -> requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showAudioRecordDialog() {
        if (isRecording) { stopRecordingAndPlace(); return }

        val container = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(16),dp(24),dp(16)); gravity=Gravity.CENTER }
        val tvStatus = TextView(this).apply { text="Ready to record"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Color.parseColor("#212121")) }
        val tvTimer  = TextView(this).apply { text="00:00"; textSize=32f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(Color.parseColor("#8D6E63")) }
        val titleInput = EditText(this).apply { hint="Audio title (optional)"; textSize=14f; setText("Recording ${System.currentTimeMillis()/1000%10000}") }
        container.addView(tvStatus); container.addView(tvTimer); container.addView(titleInput)

        var timerHandler: Handler? = null
        var startMs = 0L

        val dialog = AlertDialog.Builder(this).setTitle("Record Audio").setView(container)
            .setPositiveButton("Start") { _, _ -> }
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
                    tvStatus.text = "Recording..."; tvStatus.setTextColor(Color.RED)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Stop & Place"
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
        Toast.makeText(this,"Audio placed on canvas. Tap to select, tap again to play.",Toast.LENGTH_SHORT).show()
        recordingFile = null
    }

    private fun showExportWindowDialog() {
        val bmp = exportWindowBitmap ?: return
        val name = (currentFileName ?: "EngiNote_${System.currentTimeMillis()}").replace(" ","_")
        AlertDialog.Builder(this).setTitle("Export Selection as...")
            .setItems(arrayOf("PDF","JPG","PNG","BMP")) { _,i ->
                pendingExportBitmap = bmp
                when(i){ 0->savePdfLauncher.launch("${name}_window.pdf"); 1->{ pendingExportFormat="jpg"; saveImageLauncher.launch("${name}_window.jpg") }; 2->{ pendingExportFormat="png"; saveImageLauncher.launch("${name}_window.png") }; 3->{ pendingExportFormat="bmp"; saveImageLauncher.launch("${name}_window.bmp") } }
            }.setNegativeButton("Cancel"){ _,_ -> exportWindowBitmap=null }.show()
    }

    private fun showExportDialog() {
        val name = (currentFileName ?: "EngiNote_${System.currentTimeMillis()}").replace(" ","_")
        AlertDialog.Builder(this).setTitle("Export as...")
            .setItems(arrayOf("PDF","JPG","PNG","BMP","TXT","DOCX")) { _,i ->
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

    // ── Pen / Eraser options panels (Notewise-style long-press panels) ──

    private var penOptionsPanel: View? = null
    private var eraserOptionsPanel: LinearLayout? = null

    private fun dismissPenOptionsPanel() { penOptionsPanel?.let { canvasContainer.removeView(it) }; penOptionsPanel = null }
    private fun dismissEraserOptionsPanel() { eraserOptionsPanel?.let { canvasContainer.removeView(it) }; eraserOptionsPanel = null }

    private fun showPenOptionsPanel() {
        if (penOptionsPanel != null) { dismissPenOptionsPanel(); return }
        dismissAllFloatingPanels()
        setActiveTool(findViewById(R.id.btnDraw), Tool.PEN)

        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Pen"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "\u2715"; textSize = 18f; setTextColor(Color.parseColor("#888888")); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { dismissPenOptionsPanel() }
        })
        panel.addView(header)

        // Pen type row: Fountain / Ball / Pencil / Calligraphy / Marker
        fun sectionLabel(text: String) {
            panel.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#8D6E63")); setPadding(0, dp(14), 0, dp(6)) })
        }
        sectionLabel("Pen Type")
        val typeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val penTypes = listOf("Fountain" to PenStyle.FOUNTAIN, "Ball" to PenStyle.BALL, "Pencil" to PenStyle.PENCIL, "Calligraphy" to PenStyle.CALLIGRAPHY, "Marker" to PenStyle.MARKER)
        val typeButtons = mutableListOf<TextView>()
        for ((label, style) in penTypes) {
            val b = TextView(this).apply {
                text = label; textSize = 12f; gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                setOnClickListener {
                    drawingView.currentPenStyle = style
                    typeButtons.forEach { tb -> tb.setBackgroundColor(Color.parseColor("#F0EBE0")); tb.setTextColor(Color.parseColor("#4A4A4A")) }
                    setBackgroundColor(Color.parseColor("#8D6E63")); setTextColor(Color.WHITE)
                }
            }
            if (style == drawingView.currentPenStyle) { b.setBackgroundColor(Color.parseColor("#8D6E63")); b.setTextColor(Color.WHITE) }
            else { b.setBackgroundColor(Color.parseColor("#F0EBE0")); b.setTextColor(Color.parseColor("#4A4A4A")) }
            typeButtons.add(b); typeRow.addView(b)
        }
        panel.addView(typeRow)

        // Thickness slider
        sectionLabel("Thickness: ${drawingView.currentStrokeWidth.toInt()}")
        val thickLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val thickSeek = SeekBar(this).apply {
            max = 60; progress = drawingView.currentStrokeWidth.toInt().coerceIn(1, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(1); drawingView.currentStrokeWidth = vv.toFloat(); thickLbl.text = "Thickness: $vv" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(thickSeek)

        // Opacity slider
        sectionLabel("Opacity: ${(drawingView.currentOpacity * 100 / 255)}%")
        val opLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val opSeek = SeekBar(this).apply {
            max = 100; progress = drawingView.currentOpacity * 100 / 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(5); drawingView.currentOpacity = (vv * 255 / 100); opLbl.text = "Opacity: $vv%" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(opSeek)

        // Color row
        sectionLabel("Color")
        val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickColors = listOf(Color.BLACK, Color.RED, Color.parseColor("#03A9F4"), Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"), Color.parseColor("#FF9800"), Color.parseColor("#9C27B0"), Color.parseColor("#1A237E"))
        for (c in quickColors) {
            val sw = View(this).apply {
                val p = LinearLayout.LayoutParams(dp(34), dp(34)); p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(if (c == drawingView.currentColor) dp(3) else 0, Color.parseColor("#8D6E63")) }
                setOnClickListener { drawingView.currentColor = c; dismissPenOptionsPanel(); showPenOptionsPanel() }
            }
            colorRow.addView(sw)
        }
        val morePicker = TextView(this).apply {
            text = "+"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#4A4A4A"))
            val p = LinearLayout.LayoutParams(dp(34), dp(34)); p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
            setBackgroundColor(Color.parseColor("#F0EBE0"))
            setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c; dismissPenOptionsPanel(); showPenOptionsPanel() } }
        }
        colorRow.addView(morePicker)
        colorScroll.addView(colorRow)
        panel.addView(colorScroll)

        scroll.addView(panel)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        canvasContainer.addView(scroll, lp)
        penOptionsPanel = scroll
    }

    private fun showEraserOptionsPanel() {
        if (eraserOptionsPanel != null) { dismissEraserOptionsPanel(); return }
        dismissAllFloatingPanels()
        setActiveTool(findViewById(R.id.btnQuickEraser), Tool.ERASER)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Eraser"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "\u2715"; textSize = 18f; setTextColor(Color.parseColor("#888888")); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { dismissEraserOptionsPanel() }
        })
        panel.addView(header)

        fun sectionLabel(text: String) {
            panel.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#8D6E63")); setPadding(0, dp(14), 0, dp(6)) })
        }

        sectionLabel("Mode")
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modeButtons = mutableListOf<TextView>()
        for ((label, mode) in listOf("Object" to EraserMode.OBJECT, "Area" to EraserMode.AREA)) {
            val b = TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                setOnClickListener {
                    drawingView.eraserMode = mode
                    modeButtons.forEach { tb -> tb.setBackgroundColor(Color.parseColor("#F0EBE0")); tb.setTextColor(Color.parseColor("#4A4A4A")) }
                    setBackgroundColor(Color.parseColor("#8D6E63")); setTextColor(Color.WHITE)
                }
            }
            if (mode == drawingView.eraserMode) { b.setBackgroundColor(Color.parseColor("#8D6E63")); b.setTextColor(Color.WHITE) }
            else { b.setBackgroundColor(Color.parseColor("#F0EBE0")); b.setTextColor(Color.parseColor("#4A4A4A")) }
            modeButtons.add(b); modeRow.addView(b)
        }
        panel.addView(modeRow)

        sectionLabel("Shape")
        val shapeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val shapeButtons = mutableListOf<TextView>()
        for ((label, shp) in listOf("Round" to EraserShape.ROUND, "Square" to EraserShape.SQUARE)) {
            val b = TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                setOnClickListener {
                    drawingView.eraserShape = shp
                    shapeButtons.forEach { tb -> tb.setBackgroundColor(Color.parseColor("#F0EBE0")); tb.setTextColor(Color.parseColor("#4A4A4A")) }
                    setBackgroundColor(Color.parseColor("#8D6E63")); setTextColor(Color.WHITE)
                }
            }
            if (shp == drawingView.eraserShape) { b.setBackgroundColor(Color.parseColor("#8D6E63")); b.setTextColor(Color.WHITE) }
            else { b.setBackgroundColor(Color.parseColor("#F0EBE0")); b.setTextColor(Color.parseColor("#4A4A4A")) }
            shapeButtons.add(b); shapeRow.addView(b)
        }
        panel.addView(shapeRow)

        sectionLabel("Size: ${drawingView.eraserSize.toInt()}")
        val sizeLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val sizeSeek = SeekBar(this).apply {
            max = 200; progress = drawingView.eraserSize.toInt().coerceIn(1, 200)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(1); drawingView.eraserSize = vv.toFloat(); sizeLbl.text = "Size: $vv" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(sizeSeek)

        val clearBtn = TextView(this).apply {
            text = "Clear current page"; textSize = 14f; setTextColor(Color.parseColor("#D32F2F"))
            setPadding(0, dp(16), 0, dp(6))
            setOnClickListener { dismissEraserOptionsPanel(); confirmThenClear() }
        }
        panel.addView(clearBtn)

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        canvasContainer.addView(panel, lp)
        eraserOptionsPanel = panel
    }

    private var highlighterOptionsPanel: View? = null
    private fun dismissHighlighterOptionsPanel() { highlighterOptionsPanel?.let { canvasContainer.removeView(it) }; highlighterOptionsPanel = null }

    private fun showHighlighterOptionsPanel() {
        if (highlighterOptionsPanel != null) { dismissHighlighterOptionsPanel(); return }
        dismissAllFloatingPanels()
        setActiveTool(findViewById(R.id.btnHighlighter), Tool.HIGHLIGHTER)

        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Highlighter"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "\u2715"; textSize = 18f; setTextColor(Color.parseColor("#888888")); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { dismissHighlighterOptionsPanel() }
        })
        panel.addView(header)

        fun sectionLabel(text: String) {
            panel.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#8D6E63")); setPadding(0, dp(14), 0, dp(6)) })
        }

        sectionLabel("Thickness: ${drawingView.highlighterThickness.toInt()}")
        val thickLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val thickSeek = SeekBar(this).apply {
            max = 60; progress = drawingView.highlighterThickness.toInt().coerceIn(4, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(4); drawingView.highlighterThickness = vv.toFloat(); thickLbl.text = "Thickness: $vv" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(thickSeek)

        sectionLabel("Opacity: ${drawingView.highlighterOpacity}%")
        val opLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val opSeek = SeekBar(this).apply {
            max = 100; progress = drawingView.highlighterOpacity.coerceIn(5, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(5); drawingView.highlighterOpacity = vv; opLbl.text = "Opacity: $vv%" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(opSeek)

        sectionLabel("Color")
        val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickColors = listOf(Color.parseColor("#FFEB3B"), Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"), Color.parseColor("#03A9F4"), Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"), Color.BLACK, Color.RED)
        for (c in quickColors) {
            val sw = View(this).apply {
                val p = LinearLayout.LayoutParams(dp(34), dp(34)); p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(if (c == drawingView.currentColor) dp(3) else 0, Color.parseColor("#8D6E63")) }
                setOnClickListener { drawingView.currentColor = c; dismissHighlighterOptionsPanel(); showHighlighterOptionsPanel() }
            }
            colorRow.addView(sw)
        }
        val morePicker = TextView(this).apply {
            text = "+"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#4A4A4A"))
            val p = LinearLayout.LayoutParams(dp(34), dp(34)); p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
            setBackgroundColor(Color.parseColor("#F0EBE0"))
            setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c; dismissHighlighterOptionsPanel(); showHighlighterOptionsPanel() } }
        }
        colorRow.addView(morePicker)
        colorScroll.addView(colorRow)
        panel.addView(colorScroll)

        scroll.addView(panel)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        canvasContainer.addView(scroll, lp)
        highlighterOptionsPanel = scroll
    }

    private var brushOptionsPanel: View? = null
    private fun dismissBrushOptionsPanel() { brushOptionsPanel?.let { canvasContainer.removeView(it) }; brushOptionsPanel = null }

    private fun showBrushOptionsPanel() {
        if (brushOptionsPanel != null) { dismissBrushOptionsPanel(); return }
        dismissAllFloatingPanels()
        setActiveTool(findViewById(R.id.btnBrush), Tool.BRUSH)

        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Brush"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "\u2715"; textSize = 18f; setTextColor(Color.parseColor("#888888")); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { dismissBrushOptionsPanel() }
        })
        panel.addView(header)

        fun sectionLabel(text: String) {
            panel.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(Color.parseColor("#8D6E63")); setPadding(0, dp(14), 0, dp(6)) })
        }

        sectionLabel("Brush Type")
        val typeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val typeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val brushTypes = listOf("Round" to BrushStyle.ROUND, "Flat" to BrushStyle.FLAT, "Texture" to BrushStyle.TEXTURE, "Ink" to BrushStyle.INK, "Watercolor" to BrushStyle.WATERCOLOR, "Crayon" to BrushStyle.CRAYON, "Charcoal" to BrushStyle.CHARCOAL, "Airbrush" to BrushStyle.AIRBRUSH)
        val typeButtons = mutableListOf<TextView>()
        for ((label, style) in brushTypes) {
            val b = TextView(this).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                setOnClickListener {
                    drawingView.currentBrushStyle = style
                    typeButtons.forEach { tb -> tb.setBackgroundColor(Color.parseColor("#F0EBE0")); tb.setTextColor(Color.parseColor("#4A4A4A")) }
                    setBackgroundColor(Color.parseColor("#8D6E63")); setTextColor(Color.WHITE)
                }
            }
            if (style == drawingView.currentBrushStyle) { b.setBackgroundColor(Color.parseColor("#8D6E63")); b.setTextColor(Color.WHITE) }
            else { b.setBackgroundColor(Color.parseColor("#F0EBE0")); b.setTextColor(Color.parseColor("#4A4A4A")) }
            typeButtons.add(b); typeRow.addView(b)
        }
        typeScroll.addView(typeRow)
        panel.addView(typeScroll)

        sectionLabel("Thickness: ${drawingView.brushThickness.toInt()}")
        val thickLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val thickSeek = SeekBar(this).apply {
            max = 60; progress = drawingView.brushThickness.toInt().coerceIn(1, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(1); drawingView.brushThickness = vv.toFloat(); thickLbl.text = "Thickness: $vv" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(thickSeek)

        sectionLabel("Opacity: ${(drawingView.brushOpacity * 100 / 255)}%")
        val opLbl = panel.getChildAt(panel.childCount - 1) as TextView
        val opSeek = SeekBar(this).apply {
            max = 100; progress = drawingView.brushOpacity * 100 / 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(5); drawingView.brushOpacity = (vv * 255 / 100); opLbl.text = "Opacity: $vv%" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(opSeek)

        sectionLabel("Color")
        val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickColors = listOf(Color.BLACK, Color.RED, Color.parseColor("#03A9F4"), Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"), Color.parseColor("#FF9800"), Color.parseColor("#9C27B0"), Color.parseColor("#1A237E"))
        for (c in quickColors) {
            val sw = View(this).apply {
                val p = LinearLayout.LayoutParams(dp(34), dp(34)); p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(if (c == drawingView.currentColor) dp(3) else 0, Color.parseColor("#8D6E63")) }
                setOnClickListener { drawingView.currentColor = c; dismissBrushOptionsPanel(); showBrushOptionsPanel() }
            }
            colorRow.addView(sw)
        }
        val morePicker = TextView(this).apply {
            text = "+"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#4A4A4A"))
            val p = LinearLayout.LayoutParams(dp(34), dp(34)); p.setMargins(dp(4), 0, dp(4), 0); layoutParams = p
            setBackgroundColor(Color.parseColor("#F0EBE0"))
            setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c; dismissBrushOptionsPanel(); showBrushOptionsPanel() } }
        }
        colorRow.addView(morePicker)
        colorScroll.addView(colorRow)
        panel.addView(colorScroll)

        scroll.addView(panel)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        canvasContainer.addView(scroll, lp)
        brushOptionsPanel = scroll
    }

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

    private fun showTableInsertDialog() {
        val container=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(20),dp(20),dp(10)) }
        val rowLbl=TextView(this).apply{ text="Rows:"; textSize=15f }; container.addView(rowLbl)
        val rowInput=EditText(this).apply{ inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText("3") }; container.addView(rowInput)
        val colLbl=TextView(this).apply{ text="Columns:"; textSize=15f; setPadding(0,dp(12),0,0) }; container.addView(colLbl)
        val colInput=EditText(this).apply{ inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText("3") }; container.addView(colInput)
        AlertDialog.Builder(this).setTitle("Insert Table").setView(container)
            .setPositiveButton("Insert"){ _,_ ->
                val rows=(rowInput.text.toString().toIntOrNull()?:3).coerceIn(1,20)
                val cols=(colInput.text.toString().toIntOrNull()?:3).coerceIn(1,20)
                val wx=drawingView.screenCenterWorldX()-(drawingView.width/drawingView.getScaleFactor()/4f)
                val wy=drawingView.screenCenterWorldY()-90f
                // Force SELECT tool so the freshly-inserted table is safe to tap/move and isn't
                // immediately erased or drawn over by whatever tool was active beforehand.
                setActiveTool(null, Tool.SELECT)
                drawingView.addTable(rows,cols,wx,wy,drawingView.width.toFloat())
            }.setNegativeButton("Cancel",null).show()
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

        val selDesc=if(selEnd!=null) "R${minR+1}C${minC+1} to R${maxR+1}C${maxC+1}" else "R${row+1}C${col+1}"
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
        for((idx,al) in listOf("Left","Center","Right").withIndex()){ Button(this).apply{ text=al; textSize=12f; setBackgroundColor(if(cell.alignment==idx)Color.parseColor("#8D6E63") else Color.LTGRAY); setOnClickListener{ applyToSel{it.alignment=idx} }; layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f); alignRow.addView(this) } }
        container.addView(alignRow)

        val scroll=ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("Table Style").setView(scroll).setPositiveButton("Done",null).show()
    }

    private fun dismissCellEditor() {
        val et=activeCellEditText?:return
        try{ canvasContainer.removeView(activeCellToolbar) }catch(e:Exception){}
        try{ canvasContainer.removeView(tableToolbarOverlay) }catch(e:Exception){}
        try{ canvasContainer.removeView(et) }catch(e:Exception){}
        activeCellEditText=null; activeCellToolbar=null; tableToolbarOverlay=null
        val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken,0)
        drawingView.exitTableEditMode()
        drawingView.onCanvasTransformed = null
        setBottomBarVisible(true)
    }

    // True in-place cell editor: the EditText is positioned and sized to sit exactly on top of
    // the table cell on the canvas (not a separate bottom-sheet panel), so typing happens directly
    // "inside" the cell the way Excel's in-cell editing works. A tiny actions strip floats just
    // above the cell for row/col/merge/style/chart - Done is implicit (tap elsewhere / Done button).
    private fun showTableCellEditor(table:TableItem,row:Int,col:Int,screenX:Float,screenY:Float) {
        setBottomBarVisible(false)
        val cell=table.cells[row][col]
        val density = resources.displayMetrics.density

        fun cellScreenRect(): RectF {
            val rect = table.cellRect(row, col)
            val sx0 = drawingView.worldToScreenX(rect.left); val sy0 = drawingView.worldToScreenY(rect.top)
            val sx1 = drawingView.worldToScreenX(rect.right); val sy1 = drawingView.worldToScreenY(rect.bottom)
            return RectF(sx0, sy0, sx1, sy1)
        }

        fun repositionToCellFn(et: EditText) {
            val r = cellScreenRect()
            val scale = drawingView.getScaleFactor()
            et.textSize = (cell.textSize * scale / density).coerceAtLeast(8f)
            val lp = et.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            lp.width = (r.width()).toInt().coerceAtLeast(dp(40)); lp.height = (r.height()).toInt().coerceAtLeast(dp(28))
            lp.leftMargin = r.left.toInt(); lp.topMargin = r.top.toInt()
            et.layoutParams = lp
        }

        val et = EditText(this).apply {
            setText(cell.text)
            setTextColor(cell.textColor)
            setBackgroundColor(Color.parseColor("#FFF8E1")) // soft highlight so it's clear this cell is being edited
            setPadding(dp(6), dp(4), dp(6), dp(4))
            gravity = when (cell.alignment) { 1 -> Gravity.CENTER; 2 -> Gravity.END or Gravity.CENTER_VERTICAL; else -> Gravity.START or Gravity.CENTER_VERTICAL }
            isSingleLine = false
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        et.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                cell.text=s?.toString()?:""
                // Auto-grow column width / row height live as the user types, unless the
                // column was manually resized or this is a merged cell.
                table.recalcCellSize(row, col)
                drawingView.invalidate()
                repositionToCellFn(et)
            }
            override fun afterTextChanged(s:Editable?){}
        })
        fun repositionToCell() { repositionToCellFn(et) }

        val initialRect = cellScreenRect()
        val initParams = FrameLayout.LayoutParams(initialRect.width().toInt().coerceAtLeast(dp(40)), initialRect.height().toInt().coerceAtLeast(dp(28)))
        initParams.leftMargin = initialRect.left.toInt(); initParams.topMargin = initialRect.top.toInt()
        et.textSize = (cell.textSize * drawingView.getScaleFactor() / density).coerceAtLeast(8f)
        canvasContainer.addView(et, initParams)

        // Compact floating actions strip, positioned just above the cell
        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE); elevation = dp(6).toFloat(); setPadding(dp(4),dp(4),dp(4),dp(4)) }
        fun actionBtn(label: String, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label; textSize = 11f; setTextColor(Color.parseColor("#4A4A4A"))
                setBackgroundColor(Color.parseColor("#F0EBE0"))
                setPadding(dp(8), dp(5), dp(8), dp(5))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(dp(2), 0, dp(2), 0); layoutParams = p
                setOnClickListener { action(); dismissCellEditor() }
            }
            actionsRow.addView(b)
        }
        val (selStart, selEnd) = drawingView.getTableSelection()
        actionBtn("+Row") { drawingView.addTableRow(row) }
        actionBtn("+Col") { drawingView.addTableCol(col) }
        actionBtn("-Row") { drawingView.removeTableRow(row) }
        actionBtn("-Col") { drawingView.removeTableCol(col) }
        if (selEnd != null) actionBtn("Merge") { drawingView.mergeCellSelection() }
        if (table.mergeSpans.containsKey(Pair(row, col))) actionBtn("Unmerge") { drawingView.unmergeCellSelection() }
        actionBtn("Style") { showCellStyleDialog(table, row, col, selEnd) }
        actionBtn("Chart") { launchChartFromTable(table) }
        actionBtn("Done") { dismissCellEditor() }

        val actionsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actionsRow) }
        val toolbarHeightEstimate = dp(40)
        val actionsLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        actionsLp.leftMargin = initialRect.left.toInt().coerceAtMost((canvasContainer.width - dp(240)).coerceAtLeast(0))
        actionsLp.topMargin = (initialRect.top.toInt() - toolbarHeightEstimate).coerceAtLeast(0)
        canvasContainer.addView(actionsScroll, actionsLp)

        // Keep the editor and actions strip glued to the cell as the user pans/zooms the canvas
        drawingView.onCanvasTransformed = {
            repositionToCell()
            val r = cellScreenRect()
            val alp = actionsScroll.layoutParams as FrameLayout.LayoutParams
            alp.leftMargin = r.left.toInt().coerceAtMost((canvasContainer.width - dp(240)).coerceAtLeast(0))
            alp.topMargin = (r.top.toInt() - toolbarHeightEstimate).coerceAtLeast(0)
            actionsScroll.layoutParams = alp
        }

        activeCellEditText=et; activeCellToolbar=actionsScroll; tableToolbarOverlay=actionsScroll

        et.requestFocus(); et.setSelection(et.text.length)
        et.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun toggleStyleOnSelection(et:EditText,styleFlag:Int){ val s=et.selectionStart;val e=et.selectionEnd; if(s==e){Toast.makeText(this,"Select text first",Toast.LENGTH_SHORT).show();return}; val from=minOf(s,e);val to=maxOf(s,e); val ed=et.text; val ex=ed.getSpans(from,to,StyleSpan::class.java).filter{it.style==styleFlag&&ed.getSpanStart(it)<=from&&ed.getSpanEnd(it)>=to}; if(ex.isNotEmpty()) for(sp in ex) ed.removeSpan(sp) else ed.setSpan(StyleSpan(styleFlag),from,to,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun applyColorToSelection(et:EditText,color:Int){ val s=et.selectionStart;val e=et.selectionEnd; if(s==e){editColor=color;et.setTextColor(color);return}; et.text.setSpan(ForegroundColorSpan(color),minOf(s,e),maxOf(s,e),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun toggleUnderlineOnSelection(et:EditText){ val s=et.selectionStart;val e=et.selectionEnd; val from=minOf(s,e);val to=maxOf(s,e); val ed=et.text; val ex=ed.getSpans(from,to,UnderlineSpan::class.java).filter{ed.getSpanStart(it)<=from&&ed.getSpanEnd(it)>=to}; if(ex.isNotEmpty()) for(sp in ex) ed.removeSpan(sp) else ed.setSpan(UnderlineSpan(),from,to,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun applyHighlightToSelection(et:EditText,color:Int){ et.text.setSpan(BackgroundColorSpan(color),minOf(et.selectionStart,et.selectionEnd),maxOf(et.selectionStart,et.selectionEnd),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }

    private var textSelectionBox: View? = null
    private var textSelectionItem: TextItem? = null
    private var textSelectionHandles: List<View> = emptyList()

    private fun dismissTextSelectionBox() {
        textSelectionBox?.let { canvasContainer.removeView(it) }
        textSelectionHandles.forEach { canvasContainer.removeView(it) }
        textSelectionHandles = emptyList()
        if (textSelectionBox != null) drawingView.onCanvasTransformed = null
        textSelectionBox = null; textSelectionItem = null
    }

    // Lightweight selection box for a single tap on text: shows the same border + move/resize/
    // rotate/delete handles as the full editor, but with NO EditText/keyboard. Double-tapping the
    // box (handled by DrawingView's gesture detector, which calls showInlineTextEditor directly)
    // is what actually opens it for typing. This satisfies "single tap = select + resize, double
    // tap = edit, drag inside = move."
    // Recomputes and applies the selection box's width/height/position in place - cheap enough
    // to call on every ACTION_MOVE during a resize/move drag, unlike tearing down and rebuilding
    // the whole view hierarchy (which is what made resizing feel rough/jumpy before).
    // Measures the text item's ACTUAL rendered width/height using StaticLayout, the same way
    // DrawingView itself renders it - replaces the old character-count * fixed-width heuristic,
    // which badly overestimated width for normal text (e.g. "Amrit Khadka" was computing a box
    // hundreds of pixels wider than the real text), pushing the right-side corner/edge handles
    // off-screen and making them untappable.
    private fun measureTextBoxSize(item: TextItem, screenSizePx: Float): Pair<Int, Int> {
        val tp = android.text.TextPaint(); tp.textSize = screenSizePx; tp.isAntiAlias = true
        try { tp.typeface = Typeface.create(item.fontFamily, Typeface.NORMAL) } catch (e: Exception) {}
        val wrapWidth = if (item.maxWidth > 0f) {
            // maxWidth is stored in world units - convert to current screen pixels
            (item.maxWidth * (screenSizePx / item.size.coerceAtLeast(1f))).toInt().coerceAtLeast(dp(60))
        } else 4000
        val layout = android.text.StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, wrapWidth).setIncludePad(true).build()
        val measuredW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: (screenSizePx * 2f)
        val w = (measuredW + dp(16)).toInt().coerceAtLeast(dp(60))
        val h = (layout.height + dp(8)).coerceAtLeast(dp(36))
        return Pair(w, h)
    }

    private fun updateTextSelectionBoxSize(box: FrameLayout, moveSurface: View, item: TextItem): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val useActualSize = drawingView.canvasMode != CanvasMode.INFINITE && drawingView.canvasMode != CanvasMode.CONVENIENT
        val convenientBoost = if (drawingView.canvasMode == CanvasMode.CONVENIENT) 1.6f else 1f
        val screenSizePx = (if (useActualSize) item.size else item.size * drawingView.getScaleFactor()) * convenientBoost
        val (boxW, boxH) = measureTextBoxSize(item, screenSizePx)

        val lp = box.layoutParams as FrameLayout.LayoutParams
        // Keep the box's top-left anchored where it currently is rather than re-deriving from
        // screenX/screenY each time, so the box grows/shrinks from its current position smoothly
        // instead of snapping back to its original spawn point.
        lp.width = boxW; lp.height = boxH
        box.layoutParams = lp
        val mlp = moveSurface.layoutParams; mlp.width = boxW; mlp.height = boxH; moveSurface.layoutParams = mlp
        return Pair(boxW, boxH)
    }

    private fun showTextSelectionBox(item: TextItem, screenX: Float, screenY: Float) {
        if (textSelectionItem === item) return
        dismissTextSelectionBox()
        closeInlineEditor(true)
        dismissCellEditor()

        val density = resources.displayMetrics.density
        val useActualSize = drawingView.canvasMode != CanvasMode.INFINITE && drawingView.canvasMode != CanvasMode.CONVENIENT
        val convenientBoost = if (drawingView.canvasMode == CanvasMode.CONVENIENT) 1.6f else 1f
        val screenSizePx = (if (useActualSize) item.size else item.size * drawingView.getScaleFactor()) * convenientBoost

        // Anchor the box on the TEXT ITEM'S OWN world position (item.x, item.y - its baseline
        // origin, same convention used everywhere else for TextItem), converted to screen
        // coordinates - NOT the raw tap point that was passed in. Using the tap point was the bug:
        // tapping anywhere along a wide word (e.g. partway through "Amrit") anchored the box at
        // that arbitrary touch location instead of the text's actual position, producing a box
        // that floated off to one side with no relation to where the text actually is.
        val anchorScreenX = drawingView.worldToScreenX(item.x)
        val anchorScreenY = drawingView.worldToScreenY(item.y)

        val box = FrameLayout(this)
        box.clipChildren = false
        box.clipToPadding = false
        box.background = android.graphics.drawable.GradientDrawable().apply {
            setStroke(dp(2), Color.parseColor("#2196F3")); setColor(Color.parseColor("#08000000"))
        }
        val (measW, measH) = measureTextBoxSize(item, screenSizePx)
        var boxW = measW; var boxH = measH

        // A transparent touch-target filling the box: dragging it moves the text item.
        val moveSurface = View(this).apply { layoutParams = FrameLayout.LayoutParams(boxW, boxH) }
        var moveStartRawX = 0f; var moveStartRawY = 0f; var moveStartLeft = 0; var moveStartTop = 0
        var onMoveSurfaceDrag: (() -> Unit)? = null
        moveSurface.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    moveStartRawX = ev.rawX; moveStartRawY = ev.rawY
                    val lp = box.layoutParams as FrameLayout.LayoutParams
                    moveStartLeft = lp.leftMargin; moveStartTop = lp.topMargin
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - moveStartRawX); val dy = (ev.rawY - moveStartRawY)
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                        val lp = box.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = (moveStartLeft + dx).toInt().coerceAtLeast(0); lp.topMargin = (moveStartTop + dy).toInt().coerceAtLeast(0)
                        box.layoutParams = lp
                        item.x = drawingView.screenToWorldX(lp.leftMargin.toFloat() + dp(6))
                        item.y = drawingView.screenToWorldY(lp.topMargin.toFloat() + boxH + dp(6))
                        drawingView.invalidate()
                        onMoveSurfaceDrag?.invoke()
                        true
                    } else false
                }
                else -> false
            }
        }
        box.addView(moveSurface)

        // All handles are direct children of canvasContainer (NOT of box) so they're never
        // clipped by box's bounds. Touch hit-testing in Android only works reliably when the
        // touch target view is fully within its parent's bounds - children outside parent bounds
        // are clipped from touch dispatch even with clipChildren=false. By making handles siblings
        // of box in canvasContainer, we guarantee their layoutParams.leftMargin/topMargin always
        // map to real screen positions that Android's hit-testing will find correctly.
        val handleViews = mutableListOf<Pair<View, Pair<Float, Float>>>() // view → (fracX, fracY) of box
        fun handle(colorHex: String, fx: Float, fy: Float): View {
            val visibleSz = dp(16); val touchSz = dp(32)
            val h = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(touchSz, touchSz) }
            val dot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(visibleSz, visibleSz).also { it.gravity = Gravity.CENTER }
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(2), Color.parseColor(colorHex)) }
            }
            h.addView(dot)
            canvasContainer.addView(h)
            handleViews.add(h to (fx to fy))
            return h
        }

        // Pivot = centre of box, matching canvas.rotate(rotation, w/2, h/2)
        fun pivotScreenX() = (box.layoutParams as FrameLayout.LayoutParams).leftMargin + boxW / 2f
        fun pivotScreenY() = (box.layoutParams as FrameLayout.LayoutParams).topMargin + boxH / 2f

        fun rotatePoint(px: Float, py: Float, cx: Float, cy: Float, angleDeg: Float): Pair<Float, Float> {
            if (angleDeg == 0f) return Pair(px, py)
            val rad = Math.toRadians(angleDeg.toDouble())
            val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
            val dx = px - cx; val dy = py - cy
            return Pair(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
        }

        fun layoutHandles() {
            val px = pivotScreenX(); val py = pivotScreenY()
            val w = boxW.toFloat(); val hgt = boxH.toFloat(); val half = dp(16).toFloat()
            val rot = item.rotation
            val boxLeft = px - w / 2f; val boxTop = py - hgt / 2f
            for ((view, frac) in handleViews) {
                val rawX = boxLeft + frac.first * w
                val rawY = boxTop + frac.second * hgt
                val (rx, ry) = rotatePoint(rawX, rawY, px, py, rot)
                val lp = view.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = (rx - half).toInt(); lp.topMargin = (ry - half).toInt()
                view.layoutParams = lp
            }
        }

        lateinit var rotateHandle: FrameLayout
        lateinit var deleteHandle: FrameLayout
        fun layoutTopHandles() {
            val px = pivotScreenX(); val py = pivotScreenY()
            val w = boxW.toFloat(); val hgt = boxH.toFloat()
            val rot = item.rotation
            val boxTop = py - hgt / 2f
            val (rRx, rRy) = rotatePoint(px, boxTop - dp(28), px, py, rot)
            val rlp = rotateHandle.layoutParams as FrameLayout.LayoutParams
            rlp.leftMargin = (rRx - dp(16)).toInt(); rlp.topMargin = (rRy - dp(16)).coerceAtLeast(0f).toInt()
            rotateHandle.layoutParams = rlp
            val (dRx, dRy) = rotatePoint(px + w / 2f, boxTop - dp(28), px, py, rot)
            val dlp = deleteHandle.layoutParams as FrameLayout.LayoutParams
            dlp.leftMargin = (dRx - dp(16)).toInt(); dlp.topMargin = (dRy - dp(16)).coerceAtLeast(0f).toInt()
            deleteHandle.layoutParams = dlp
        }

        // 8 resize handles around the full perimeter (corners + edge midpoints)
        onMoveSurfaceDrag = { layoutHandles(); layoutTopHandles() }
        val tl = handle("#2196F3", 0f, 0f)
        val tm = handle("#2196F3", 0.5f, 0f)
        val tr = handle("#2196F3", 1f, 0f)
        val ml = handle("#2196F3", 0f, 0.5f)
        val mr = handle("#2196F3", 1f, 0.5f)
        val bl = handle("#2196F3", 0f, 1f)
        val bm = handle("#2196F3", 0.5f, 1f)
        val br = handle("#2196F3", 1f, 1f)

        fun wireCornerResize(h: View) {
            var startRawY = 0f; var startSize = 0f
            h.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { startRawY = ev.rawY; startSize = item.size; true }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dy = ev.rawY - startRawY
                        item.size = (startSize + dy * 0.6f).coerceIn(8f, 400f)
                        drawingView.invalidate()
                        val newDims = updateTextSelectionBoxSize(box, moveSurface, item)
                        boxW = newDims.first; boxH = newDims.second
                        layoutHandles(); layoutTopHandles()
                        true
                    }
                    else -> true
                }
            }
        }
        fun wireMiddleReflow(h: View, isRight: Boolean) {
            var startRawX = 0f; var startWidth = 0f
            h.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startRawX = ev.rawX
                        startWidth = if (item.maxWidth > 0f) item.maxWidth else (box.layoutParams as FrameLayout.LayoutParams).width.toFloat()
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = if (isRight) (ev.rawX - startRawX) else (startRawX - ev.rawX)
                        val tp = android.text.TextPaint(); tp.textSize = item.size
                        try { tp.typeface = Typeface.create(item.fontFamily, Typeface.NORMAL) } catch (e: Exception) {}
                        val longestWord = item.text.split(Regex("\\s+")).maxOfOrNull { tp.measureText(it) } ?: 40f
                        val compactFloor = (longestWord + 24f).coerceAtLeast(dp(60).toFloat())
                        item.maxWidth = (startWidth + dx * 2f).coerceAtLeast(compactFloor)
                        drawingView.invalidate()
                        val newDims = updateTextSelectionBoxSize(box, moveSurface, item)
                        boxW = newDims.first; boxH = newDims.second
                        layoutHandles(); layoutTopHandles()
                        true
                    }
                    else -> true
                }
            }
        }
        wireCornerResize(tl); wireCornerResize(tr); wireCornerResize(bl); wireCornerResize(br)
        wireMiddleReflow(ml, false); wireMiddleReflow(mr, true)

        rotateHandle = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)) }
        rotateHandle.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).also { it.gravity = Gravity.CENTER }
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(2), Color.parseColor("#4CAF50")) }
        })
        canvasContainer.addView(rotateHandle)
        var rotStartRawX = 0f; var rotStartRotation = 0f
        rotateHandle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { rotStartRawX = ev.rawX; rotStartRotation = item.rotation; true }
                android.view.MotionEvent.ACTION_MOVE -> { item.rotation = rotStartRotation + (ev.rawX - rotStartRawX) * 0.5f; if (!useActualSize) box.rotation = item.rotation; drawingView.invalidate(); layoutHandles(); layoutTopHandles(); true }
                else -> true
            }
        }

        deleteHandle = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)) }
        deleteHandle.addView(ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_text_delete)
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(2), Color.parseColor("#D32F2F")) }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        })
        deleteHandle.setOnClickListener { drawingView.removeTextItem(item); dismissTextSelectionBox(); drawingView.invalidate() }
        canvasContainer.addView(deleteHandle)

        val lp = FrameLayout.LayoutParams(boxW, boxH)
        // Pivot is now centre of text block, matching canvas.rotate(rotation, w/2, h/2)
        lp.leftMargin = (anchorScreenX - dp(6)).toInt().coerceAtLeast(0)
        lp.topMargin = (anchorScreenY - boxH - dp(6)).toInt().coerceAtLeast(0)
        box.pivotX = boxW / 2f
        box.pivotY = boxH / 2f
        box.rotation = item.rotation
        canvasContainer.addView(box, lp)
        textSelectionBox = box; textSelectionItem = item
        // Register all handles (perimeter + rotate + delete) so dismissTextSelectionBox() can
        // remove them from canvasContainer - they're siblings of box now, not children, so box
        // removal alone won't clean them up.
        textSelectionHandles = handleViews.map { it.first } + listOf(rotateHandle, deleteHandle)
        layoutHandles(); layoutTopHandles()
        box.post { layoutHandles(); layoutTopHandles() }

        // Keep box and all handles glued to the text item as the user pans/zooms
        fun followCanvasTransform() {
            val newAnchorX = drawingView.worldToScreenX(item.x)
            val newAnchorY = drawingView.worldToScreenY(item.y)
            val newDims = updateTextSelectionBoxSize(box, moveSurface, item)
            boxW = newDims.first; boxH = newDims.second
            val newLp = box.layoutParams as FrameLayout.LayoutParams
            newLp.leftMargin = (newAnchorX - dp(6)).toInt().coerceAtLeast(0)
            newLp.topMargin = (newAnchorY - boxH - dp(6)).toInt().coerceAtLeast(0)
            newLp.width = boxW; newLp.height = boxH
            box.layoutParams = newLp
            box.pivotX = boxW / 2f; box.pivotY = boxH / 2f
            box.rotation = item.rotation
            layoutHandles(); layoutTopHandles()
        }
        drawingView.onCanvasTransformed = { followCanvasTransform() }
    }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        dismissTextSelectionBox()
        if (activeEditText != null && editingItem === item) return
        if (activeEditText != null) { isSwitchingTextEditor=true; closeInlineEditor(true); isSwitchingTextEditor=false; drawingView.post{ showInlineTextEditor(item,screenX,screenY,worldX,worldY) }; return }
        dismissCellEditor()
        dismissAllFloatingPanels()
        drawingView.isTextEditorOpen = true
        pendingBold=false; pendingItalic=false; pendingUnderline=false; pendingHighlight=null
        // Default font size: 50pt in Convenient layout (large, comfortable writing feel),
        // 12pt in Print/Infinite layouts (true-to-scale, matches standard document text).
        val layoutDefaultSize = if (drawingView.canvasMode == CanvasMode.CONVENIENT) 50f * PT_TO_PX else 12f * PT_TO_PX
        editingItem=item; editWorldX=item?.x?:worldX; editWorldY=item?.y?:worldY; editRotation=item?.rotation?:0f; editColor=item?.color?:drawingView.currentColor; editSize=item?.size?:layoutDefaultSize
        editOpacity = item?.opacity ?: 255
        pendingFontFamily = item?.fontFamily ?: "sans-serif"
        val density=resources.displayMetrics.density
        val useActualSize = drawingView.canvasMode != CanvasMode.INFINITE && drawingView.canvasMode != CanvasMode.CONVENIENT
        // Convenient layout gets a generous size boost so typing feels big and comfortable,
        // matching the large-font feel from the reference screenshot. Print/Infinite stay true-to-scale.
        val convenientBoost = if (drawingView.canvasMode == CanvasMode.CONVENIENT) 1.6f else 1f
        val screenSizePx=(if(useActualSize) editSize else editSize*drawingView.getScaleFactor()) * convenientBoost

        // Bordered editing box (matches the blue-bordered selection rectangle from the reference)
        val boxContainer = FrameLayout(this)
        boxContainer.clipChildren = false
        boxContainer.clipToPadding = false
        val et=EditText(this)
        val spannable=SpannableStringBuilder(item?.text?:"")
        item?.spans?.forEach{ sp-> val s=sp.start.coerceIn(0,spannable.length);val e=sp.end.coerceIn(s,spannable.length); if(s<e) when(sp.type){ 'S'->spannable.setSpan(StyleSpan(sp.value),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 'C'->spannable.setSpan(ForegroundColorSpan(sp.value),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 'U'->spannable.setSpan(UnderlineSpan(),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 'H'->spannable.setSpan(BackgroundColorSpan(sp.value),s,e,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) } }
        val maxEditorWidthPx = (canvasContainer.width - screenX - dp(24)).toInt().coerceAtLeast(dp(140))
        et.setText(spannable,TextView.BufferType.SPANNABLE)
        et.setTextColor(editColor); et.alpha = editOpacity / 255f
        et.textSize=(screenSizePx/density).coerceAtLeast(8f)
        et.setBackgroundColor(Color.TRANSPARENT)
        et.setPadding(dp(8),dp(8),dp(8),dp(8)); et.minWidth=dp(140); et.maxWidth=maxEditorWidthPx
        try { et.typeface = Typeface.create(pendingFontFamily, Typeface.NORMAL) } catch (e: Exception) {}
        if(!useActualSize) et.rotation=editRotation
        et.addTextChangedListener(object:TextWatcher{ override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ if(count>0){ val e2=et.text;val end=start+count; if(pendingBold) e2.setSpan(StyleSpan(Typeface.BOLD),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); if(pendingItalic) e2.setSpan(StyleSpan(Typeface.ITALIC),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); if(pendingUnderline) e2.setSpan(UnderlineSpan(),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); pendingHighlight?.let{ e2.setSpan(BackgroundColorSpan(it),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) } } }; override fun afterTextChanged(s:Editable?){} })

        // Visible border frame around the editable box, drawn via a GradientDrawable stroke.
        // Bigger padding and a thicker, more visible border than before per request.
        boxContainer.background = android.graphics.drawable.GradientDrawable().apply {
            setStroke(dp(2), Color.parseColor("#2196F3"))
            setColor(Color.parseColor("#08000000"))
        }
        boxContainer.addView(et, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val params=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin=(screenX - dp(6)).toInt().coerceAtLeast(0); params.topMargin=(screenY-screenSizePx-dp(6)).toInt().coerceAtLeast(0)
        canvasContainer.addView(boxContainer,params)

        // Move handle: a small drag grip on the TOP-LEFT corner of the box. Dragging this moves
        // the whole box (and the underlying text item's world position) without needing to leave
        // the editor or tap elsewhere - works both while actively typing and after.
        // All four handles below are positioned via setX/setY (absolute pixels, applied once the
        // box's real WRAP_CONTENT size is known via an OnLayoutChangeListener) rather than
        // negative margins + gravity, which proved unreliable for views meant to sit outside
        // their parent's own bounds.
        val moveHandle = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)) }
        moveHandle.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).also { it.gravity = Gravity.CENTER }
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#2196F3")); setStroke(dp(2), Color.WHITE) }
        })
        var moveStartRawX = 0f; var moveStartRawY = 0f; var moveStartLeft = 0; var moveStartTop = 0
        // Forward reference: layoutEditorHandles is defined below but called from here
        var onBoxMoved: (() -> Unit)? = null
        moveHandle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    moveStartRawX = ev.rawX; moveStartRawY = ev.rawY
                    val lp = boxContainer.layoutParams as FrameLayout.LayoutParams
                    moveStartLeft = lp.leftMargin; moveStartTop = lp.topMargin
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - moveStartRawX).toInt(); val dy = (ev.rawY - moveStartRawY).toInt()
                    val lp = boxContainer.layoutParams as FrameLayout.LayoutParams
                    lp.leftMargin = (moveStartLeft + dx).coerceAtLeast(0); lp.topMargin = (moveStartTop + dy).coerceAtLeast(0)
                    boxContainer.layoutParams = lp
                    val newScreenX = lp.leftMargin + dp(6); val newScreenY = lp.topMargin + dp(6) + screenSizePx
                    editWorldX = drawingView.screenToWorldX(newScreenX.toFloat()); editWorldY = drawingView.screenToWorldY(newScreenY)
                    onBoxMoved?.invoke()
                    true
                }
                else -> true
            }
        }
        // All 4 editor handles are direct children of canvasContainer (not boxContainer) so they
        // are never clipped by boxContainer's bounds - same fix applied to the selection box handles.
        fun containerLeft() = (boxContainer.layoutParams as FrameLayout.LayoutParams).leftMargin.toFloat()
        fun containerTop() = (boxContainer.layoutParams as FrameLayout.LayoutParams).topMargin.toFloat()

        val resizeHandle = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)) }
        resizeHandle.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).also { it.gravity = Gravity.CENTER }
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(2), Color.parseColor("#2196F3")) }
        })
        var resizeStartX = 0f; var resizeStartWidth = 0
        resizeHandle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { resizeStartX = ev.rawX; resizeStartWidth = et.width.takeIf { it > 0 } ?: maxEditorWidthPx; true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - resizeStartX).toInt()
                    val newWidth = (resizeStartWidth + dx).coerceIn(dp(80), maxEditorWidthPx)
                    et.maxWidth = newWidth; et.minWidth = newWidth
                    et.requestLayout()
                    true
                }
                else -> true
            }
        }

        val rotateHandle = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)) }
        rotateHandle.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).also { it.gravity = Gravity.CENTER }
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#4CAF50")); setStroke(dp(2), Color.WHITE) }
        })
        var rotateStartRawX = 0f; var rotateStartRawY = 0f; var rotateStartRotation = 0f
        rotateHandle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { rotateStartRawX = ev.rawX; rotateStartRawY = ev.rawY; rotateStartRotation = editRotation; true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - rotateStartRawX
                    editRotation = rotateStartRotation + dx * 0.5f
                    if (!useActualSize) { boxContainer.rotation = editRotation }
                    true
                }
                else -> true
            }
        }

        val deleteHandle = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)) }
        deleteHandle.addView(ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_text_delete)
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(2), Color.parseColor("#D32F2F")) }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        })
        deleteHandle.setOnClickListener { closeInlineEditor(false, delete = true) }

        canvasContainer.addView(moveHandle)
        canvasContainer.addView(resizeHandle)
        canvasContainer.addView(rotateHandle)
        canvasContainer.addView(deleteHandle)

        fun layoutEditorHandles() {
            val bx = containerLeft(); val by = containerTop()
            val w = boxContainer.width.toFloat(); val h = boxContainer.height.toFloat()
            val half = dp(16)
            val mlp = moveHandle.layoutParams as FrameLayout.LayoutParams
            mlp.leftMargin = (bx - half).toInt().coerceAtLeast(0); mlp.topMargin = (by - half).toInt().coerceAtLeast(0)
            moveHandle.layoutParams = mlp
            val rlp = resizeHandle.layoutParams as FrameLayout.LayoutParams
            rlp.leftMargin = (bx + w - half).toInt(); rlp.topMargin = (by + h / 2f - half).toInt()
            resizeHandle.layoutParams = rlp
            val rolp = rotateHandle.layoutParams as FrameLayout.LayoutParams
            rolp.leftMargin = (bx + w - half).toInt(); rolp.topMargin = (by + h - half).toInt()
            rotateHandle.layoutParams = rolp
            val dlp = deleteHandle.layoutParams as FrameLayout.LayoutParams
            dlp.leftMargin = (bx + w - half).toInt(); dlp.topMargin = (by - half).toInt().coerceAtLeast(0)
            deleteHandle.layoutParams = dlp
        }
        boxContainer.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) layoutEditorHandles()
        }
        boxContainer.post { layoutEditorHandles() }
        onBoxMoved = { layoutEditorHandles() }

        // Options toolbar positioned directly above the editing box (not pinned to screen bottom)
        val toolbar=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE); elevation = dp(6).toFloat(); setPadding(dp(6),dp(6),dp(6),dp(6)) }
        fun ibtn(iconRes:Int,action:(ImageView)->Unit):ImageView{
            val b=ImageView(this); b.setImageResource(iconRes); b.scaleType=ImageView.ScaleType.CENTER_INSIDE
            val p=LinearLayout.LayoutParams(dp(34),dp(34));p.setMargins(dp(2),0,dp(2),0);b.layoutParams=p
            b.setPadding(dp(6),dp(6),dp(6),dp(6))
            b.setBackgroundColor(Color.parseColor("#F0EBE0"));b.setOnClickListener{action(b)};toolbar.addView(b);return b
        }
        fun tbtnText(label:String,action:(TextView)->Unit):TextView{ val b=TextView(this);b.text=label;b.textSize=14f;b.setTextColor(Color.parseColor("#4A4A4A"));b.gravity=Gravity.CENTER;val p=LinearLayout.LayoutParams(dp(34),dp(34));p.setMargins(dp(2),0,dp(2),0);b.layoutParams=p;b.setBackgroundColor(Color.parseColor("#F0EBE0"));b.setOnClickListener{action(b)};toolbar.addView(b);return b }
        val activeBg=Color.parseColor("#8D6E63"); val inactiveBg=Color.parseColor("#F0EBE0")
        fun setToggleStateIcon(btn: ImageView, active: Boolean) { btn.setBackgroundColor(if(active) activeBg else inactiveBg); btn.setColorFilter(if(active) Color.WHITE else Color.parseColor("#4A4A4A")) }
        ibtn(R.drawable.ic_text_bold){btn-> if(et.selectionStart!=et.selectionEnd) toggleStyleOnSelection(et,Typeface.BOLD) else{ pendingBold=!pendingBold; setToggleStateIcon(btn,pendingBold) } }
        ibtn(R.drawable.ic_text_italic){btn-> if(et.selectionStart!=et.selectionEnd) toggleStyleOnSelection(et,Typeface.ITALIC) else{ pendingItalic=!pendingItalic; setToggleStateIcon(btn,pendingItalic) } }
        ibtn(R.drawable.ic_text_underline){btn-> if(et.selectionStart!=et.selectionEnd) toggleUnderlineOnSelection(et) else{ pendingUnderline=!pendingUnderline; setToggleStateIcon(btn,pendingUnderline) } }
        ibtn(R.drawable.ic_text_highlight){btn-> showColorGridDialog{ color-> if(et.selectionStart!=et.selectionEnd) applyHighlightToSelection(et,color) else{ pendingHighlight=color;btn.setColorFilter(color) } } }
        ibtn(R.drawable.ic_text_color){btn-> showColorGridDialog{ color->applyColorToSelection(et,color); editColor=color; btn.setColorFilter(color) } }
        val ptSize=(editSize/PT_TO_PX).toInt().coerceIn(1,144)
        tbtnText(ptSize.toString()){btn-> AlertDialog.Builder(this).setTitle("Font Size (pt)").setItems((1..144).map{it.toString()}.toTypedArray()){ _,idx-> val pt=idx+1;editSize=pt*PT_TO_PX; val nss=(if(useActualSize) editSize else editSize*drawingView.getScaleFactor())*convenientBoost; et.textSize=(nss/density).coerceAtLeast(8f); btn.text=pt.toString() }.show() }
        ibtn(R.drawable.ic_text_font){ showFontPickerDialog(et) }
        ibtn(R.drawable.ic_text_opacity){
            val seek = SeekBar(this).apply { max = 100; progress = editOpacity * 100 / 255 }
            val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(0)); addView(seek) }
            seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(5); editOpacity = vv * 255 / 100; et.alpha = editOpacity / 255f }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            AlertDialog.Builder(this).setTitle("Opacity").setView(wrap).setPositiveButton("Done", null).show()
        }
        ibtn(R.drawable.ic_text_rotate_ccw){ editRotation-=15f;if(!useActualSize) et.rotation=editRotation }
        ibtn(R.drawable.ic_text_rotate_cw){ editRotation+=15f;if(!useActualSize) et.rotation=editRotation }
        ibtn(R.drawable.ic_text_check){ closeInlineEditor(true) }
        ibtn(R.drawable.ic_text_delete){ closeInlineEditor(false,delete=true) }

        // Position the toolbar's horizontal scroll bar just above the box (falls back to top of screen if no room)
        val toolbarScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(toolbar) }
        val toolbarHeightEstimate = dp(46)
        val tp=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT)
        tp.leftMargin = params.leftMargin.coerceAtMost((canvasContainer.width - dp(260)).coerceAtLeast(0))
        tp.topMargin = (params.topMargin - toolbarHeightEstimate).coerceAtLeast(0)
        canvasContainer.addView(toolbarScroll,tp)

        fun updateET(){
            val scale=drawingView.getScaleFactor();val nsp=editSize*scale*convenientBoost
            et.textSize=(nsp/density).coerceAtLeast(8f)
            val sx=drawingView.worldToScreenX(editWorldX);val sy=drawingView.worldToScreenY(editWorldY)-nsp
            val lp=boxContainer.layoutParams as FrameLayout.LayoutParams; lp.leftMargin=(sx-dp(6)).toInt().coerceAtLeast(0); lp.topMargin=(sy-dp(6)).toInt().coerceAtLeast(0); boxContainer.layoutParams=lp
            val tlp=toolbarScroll.layoutParams as FrameLayout.LayoutParams; tlp.leftMargin=lp.leftMargin; tlp.topMargin=(lp.topMargin-toolbarHeightEstimate).coerceAtLeast(0); toolbarScroll.layoutParams=tlp
            layoutEditorHandles()
        }
        drawingView.onScaleChanged={ updateET() }; drawingView.onCanvasTransformed={ updateET() }
        activeEditText=et; activeToolbar=toolbarScroll; activeEditBox=boxContainer
        activeEditorHandles = listOf(moveHandle, resizeHandle, rotateHandle, deleteHandle)
        et.requestFocus()
        et.post{ val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.showSoftInput(et,InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeInlineEditor(commit:Boolean, delete:Boolean=false) {
        val et=activeEditText?:return; val tb=activeToolbar; val box=activeEditBox
        val imm=getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.hideSoftInputFromWindow(et.windowToken,0)
        val text=et.text.toString(); val spans=mutableListOf<TextSpanData>(); val ed=et.text
        for(span in ed.getSpans(0,ed.length,Any::class.java)){ val s=ed.getSpanStart(span);val e=ed.getSpanEnd(span); if(s<0||e<0||s>=e) continue; when(span){ is StyleSpan->spans.add(TextSpanData(s,e,'S',span.style)); is ForegroundColorSpan->spans.add(TextSpanData(s,e,'C',span.foregroundColor)); is UnderlineSpan->spans.add(TextSpanData(s,e,'U',0)); is BackgroundColorSpan->spans.add(TextSpanData(s,e,'H',span.backgroundColor)) } }
        if(box!=null) canvasContainer.removeView(box) else canvasContainer.removeView(et)
        if(tb!=null) canvasContainer.removeView(tb)
        activeEditorHandles.forEach { canvasContainer.removeView(it) }; activeEditorHandles = emptyList()
        val item=editingItem
        if(commit&&!delete&&text.isNotBlank()){
            drawingView.defaultTextSize=editSize
            if(item!=null){ item.text=text;item.color=editColor;item.size=editSize;item.rotation=editRotation;item.spans=spans;item.isEditing=false;item.fontFamily=pendingFontFamily;item.opacity=editOpacity; drawingView.clampTextItemToPage(item) }
            else drawingView.addText(text,editWorldX,editWorldY,editSize,editRotation,editColor,spans,pendingFontFamily,editOpacity)
        } else { if(item!=null) drawingView.removeTextItem(item) }
        if(!isSwitchingTextEditor) drawingView.invalidate()
        drawingView.onScaleChanged=null;drawingView.onCanvasTransformed=null; activeEditText=null;activeToolbar=null;activeEditBox=null;editingItem=null
        if (!isSwitchingTextEditor) drawingView.isTextEditorOpen = false
    }

    private fun launchCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCameraInternal()
        } else {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraInternal() {
        try {
            val folder=File(filesDir,"images").also{it.mkdirs()}
            val pf=File(folder,"camera_${System.currentTimeMillis()}.jpg"); cameraImageFile=pf
            val uri = FileProvider.getUriForFile(this,"$packageName.fileprovider",pf)
            takePictureLauncher.launch(uri)
        } catch(e: Exception) {
            Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        if(currentFileName==null){ val name=nextAutoName(); currentFileName=name; tvTitle.text=name }
        writeCurrentFile()
    }

    private fun saveCurrent() {
        if(currentFileName==null){
            val input=EditText(this).apply{ setText(nextAutoName()); selectAll() }
            AlertDialog.Builder(this).setTitle("Save Note").setView(input)
                .setPositiveButton("Save"){ _,_ -> val name=input.text.toString().trim().ifEmpty{nextAutoName()}; currentFileName=name; tvTitle.text=name; writeCurrentFile(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show() }
                .setNegativeButton("Cancel",null).show()
        } else { writeCurrentFile(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show() }
    }

    private fun saveAsNew() {
        val input=EditText(this).apply{ setText(nextAutoName()); selectAll() }
        AlertDialog.Builder(this).setTitle("Save as New").setView(input)
            .setPositiveButton("Save"){ _,_ -> val name=input.text.toString().trim().ifEmpty{nextAutoName()}; currentFileName=name; tvTitle.text=name; writeCurrentFile(); Toast.makeText(this,"Saved as $name",Toast.LENGTH_SHORT).show() }
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
