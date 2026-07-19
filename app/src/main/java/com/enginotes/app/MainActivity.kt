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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    internal lateinit var drawingView: DrawingView
    internal lateinit var canvasContainer: FrameLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnLayoutToggle: ImageButton

    private var currentFileName: String? = null
    private var hwrAutoEnabled = false  // real-time handwriting-to-text toggle
    private var lastSavedContent: String = ""
    internal val PT_TO_PX = 1.333f
    private var isConvenientLayout = true

    private val driveManager by lazy { DriveManager(this) }

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

    internal var activeEditText: EditText? = null
    internal var activeToolbar: View? = null
    internal var activeEditBox: View? = null
    internal var activeEditorKeyboardListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
    // The correct, safe mechanism for "keep this overlay positioned in sync with the canvas
    // every frame" — fires right before each draw, which (unlike onDraw itself) is a point in
    // the Android lifecycle where it's actually safe to change a View's layout position. An
    // earlier attempt hooked this from inside DrawingView's onDraw() directly, which silently
    // failed to reliably apply position changes — this is why.
    internal var editorPreDrawListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
    internal var activeEditorKeyboardObserver: android.view.ViewTreeObserver? = null
    internal var onImeBottomChanged: ((Int) -> Unit)? = null  // piggybacked onto the working content-view insets listener
    internal var activeEditorHandles: List<View> = emptyList()
    internal var editingItem: TextItem? = null
    internal var editWorldX = 0f; internal var editWorldY = 0f; internal var editTopAnchorY = 0f
    internal var editRotation = 0f; internal var editColor = Color.BLACK
    internal var editSize = 12f * 1.333f
    internal var editOpacity = 255
    internal var pendingBold = false; internal var pendingItalic = false
    internal var pendingUnderline = false; internal var pendingHighlight: Int? = null
    internal var pendingFontFamily: String = "sans-serif"
    // These four are read/written across many separate function calls (showTextSelectionBox,
    // dismissTextSelectionBox, the shared context bar, etc.), so — unlike the functions that use
    // them — they can't move into TextEditingExtensions.kt as part of the split. An extension
    // function can't hold its own stored state; there's no way to add real storage to a class
    // from outside it in Kotlin. These have to stay actual MainActivity properties.
    internal var textSelectionBox: View? = null
    internal var textSelectionItem: TextItem? = null
    internal var textSelectionHandles: List<View> = emptyList()
    internal var layerToolbar: View? = null
    private val recentFonts = mutableListOf("sans-serif", "serif", "monospace")

    // Returns a short display name for a font family string (system name or file path)
    private fun fontDisplayName(family: String): String {
        if (!family.startsWith("/")) return family  // system font — return as-is
        // Custom font — extract name from filename
        return java.io.File(family).nameWithoutExtension.replace("-", " ").replace("_", " ")
    }

    // Resolves font family to Typeface — handles both system names and file paths
    internal fun typefaceFromFamily(family: String): android.graphics.Typeface {
        return try {
            if (family.startsWith("/") && java.io.File(family).exists())
                android.graphics.Typeface.createFromFile(family)
            else
                android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
        } catch (e: Exception) { android.graphics.Typeface.DEFAULT }
    }
    private val recentPenStyles = mutableListOf(PenStyle.FOUNTAIN, PenStyle.BALL, PenStyle.PENCIL)
    private val recentBrushStyles = mutableListOf(BrushStyle.ROUND, BrushStyle.SPRAY, BrushStyle.WATERCOLOR)
    private var cameraImageFile: File? = null
    private var activeToolbarButton: ImageButton? = null
    private var lastShapeTool: Tool? = null  // restored after tapping outside a just-drawn shape
    internal var isSwitchingTextEditor = false
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
    // Custom fonts imported by the user — persisted across sessions in app storage
    private val customFontDir get() = File(filesDir, "fonts").also { it.mkdirs() }
    private val customFonts = mutableListOf<Pair<String, String>>()

    private fun loadCustomFonts() {
        customFonts.clear()
        customFontDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.extension.lowercase() in listOf("ttf", "otf", "ttc")) {
                customFonts.add(f.nameWithoutExtension.replace("_", " ").replace("-", " ") to f.absolutePath)
            }
        }
    }

    private val fontFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            // Get the real filename from content resolver metadata (lastPathSegment gives a numeric ID for content:// URIs)
            var name = "font_${System.currentTimeMillis()}.ttf"
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: name
                }
            }
            val ext = name.substringAfterLast(".", "").lowercase()
            if (ext !in listOf("ttf", "otf", "ttc")) {
                // Try to load it anyway — some pickers strip the extension; trust the file content
                // Copy to a temp file with .ttf extension and try loading it
                val tempName = name.substringBeforeLast(".").ifEmpty { name } + ".ttf"
                val temp = File(customFontDir, tempName)
                contentResolver.openInputStream(uri)?.use { it.copyTo(temp.outputStream()) }
                try {
                    val tf = android.graphics.Typeface.createFromFile(temp)
                    if (tf == android.graphics.Typeface.DEFAULT) { temp.delete(); Toast.makeText(this, "Not a valid font file", Toast.LENGTH_SHORT).show() }
                    else { loadCustomFonts(); Toast.makeText(this, "Font imported: ${temp.nameWithoutExtension}", Toast.LENGTH_SHORT).show(); activeEditText?.let { showFontPickerDialog(it) } }
                } catch (e: Exception) { temp.delete(); Toast.makeText(this, "Only .ttf, .otf, .ttc font files are supported", Toast.LENGTH_LONG).show() }
                return@registerForActivityResult
            }
            val dest = File(customFontDir, name)
            contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
            // Verify by actually loading — createFromFile throws on truly broken files
            try {
                android.graphics.Typeface.createFromFile(dest)
            } catch (e: Exception) {
                dest.delete(); Toast.makeText(this, "Invalid font file: ${e.message}", Toast.LENGTH_LONG).show(); return@registerForActivityResult
            }
            loadCustomFonts()
            Toast.makeText(this, "Font ${dest.nameWithoutExtension} imported!", Toast.LENGTH_SHORT).show()
            activeEditText?.let { showFontPickerDialog(it) }
        } catch (e: Exception) { Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

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
        loadCustomFonts()
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
        lateinit var dialog: AlertDialog

        fun sectionHeader(title: String) = container.addView(TextView(this).apply {
            text = title; textSize = 11f; setTextColor(Color.parseColor("#7B61FF"))
            setPadding(dp(20), dp(12), dp(20), dp(2)); setTypeface(null, android.graphics.Typeface.BOLD)
        })
        fun divider() = container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        })
        fun addFontRow(label: String, family: String, tf: android.graphics.Typeface?) {
            container.addView(TextView(this).apply {
                text = label; textSize = 18f; setTextColor(Color.parseColor("#212121"))
                setPadding(dp(20), dp(14), dp(20), dp(14))
                if (tf != null) typeface = tf
                if (pendingFontFamily == family) setBackgroundColor(Color.parseColor("#EDE7F6"))
                setOnClickListener {
                    pendingFontFamily = family
                    editingItem?.let { it.fontFamily = family }
                    textSelectionItem?.let { it.fontFamily = family; drawingView.invalidate() }
                    et.typeface = tf ?: Typeface.DEFAULT
                    recentFonts.remove(family); recentFonts.add(0, family)
                    getPrefs().edit().putString("last_font", family).apply()
                    rebuildContextBar(); dialog.dismiss()
                }
            }); divider()
        }

        sectionHeader("Built-in Fonts (${availableFonts.size})")
        for ((label, family) in availableFonts) {
            addFontRow(label, family, try { Typeface.create(family, Typeface.NORMAL) } catch (e: Exception) { null })
        }
        if (customFonts.isNotEmpty()) {
            sectionHeader("Imported Fonts (${customFonts.size})")
            for ((label, path) in customFonts) {
                addFontRow(label, path, try { android.graphics.Typeface.createFromFile(path) } catch (e: Exception) { null })
            }
        }
        container.addView(TextView(this).apply {
            text = "+ Import Font  (.ttf / .otf / .ttc)"; textSize = 15f
            setTextColor(Color.parseColor("#1565C0")); gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setOnClickListener { dialog.dismiss(); fontFileLauncher.launch("*/*") }  // */* needed: no standard font MIME type
        })
        scroll.addView(container)
        dialog = AlertDialog.Builder(this).setTitle("Choose Font").setView(scroll).setNegativeButton("Cancel", null).create()
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
            setActiveTool(findViewById(R.id.btnSelect), Tool.SELECT)
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
            setActiveTool(findViewById(R.id.btnSelect), Tool.SELECT)
            Toast.makeText(this, "PDF snip added — use handles to resize/move", Toast.LENGTH_SHORT).show()
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
    internal val pickImageForOcrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runOcrOnUri(uri)
    }
    internal val pickPdfForOcrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) ocrSnipLauncher.launch(android.content.Intent(this, PdfViewerActivity::class.java).putExtra("pdf_uri", uri.toString()))
    }
    private val ocrSnipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("snip_image_path")
            if (path != null) runOcrOnUri(Uri.fromFile(File(path)))
        }
    }
    internal var ocrCameraFile: File? = null
    internal val takePictureForOcrLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) ocrCameraFile?.let { runOcrOnUri(Uri.fromFile(it)) }
    }
    internal val pickImageForCustomHatchLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) addCustomHatchFromUri(uri)
    }

    // launchCameraForOcr, runOcrOnUri, and showOcrResultDialog moved to OcrExtensions.kt.


    internal val requestCameraPermission = registerForActivityResult(
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        drawingView     = findViewById(R.id.drawingView)
        drawingView.inputMode = try { InputMode.valueOf(getPrefs().getString("input_mode", "AUTO") ?: "AUTO") } catch (e: Exception) { InputMode.AUTO }

        applyToolbarTheme()
        repositionContextBar()

        // Apply iOS-style tactile press feedback to every primary toolbar button.
        for (id in listOf(R.id.btnSelect, R.id.btnText, R.id.btnDraw, R.id.btnHighlighter, R.id.btnBrush,
                           R.id.btnQuickEraser, R.id.btnQuickFill, R.id.btnTools, R.id.btnInsert,
                           R.id.btnUndo, R.id.btnRedo)) {
            findViewById<View?>(id)?.addPressAnimation()
        }
        canvasContainer = findViewById(R.id.canvasContainer)
        canvasContainer.clipChildren = false
        canvasContainer.clipToPadding = false
        tvTitle         = findViewById(R.id.tvTitle)
        tvTitle.setOnClickListener { showRenameDialog() }
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle)

        // Keep the static bottom toolbars (context row + primary tool dock) above the keyboard.
        // These are separate from the floating per-edit toolbar handled elsewhere - they're
        // pinned in activity_main.xml and were getting covered by the IME since adjustNothing
        // doesn't resize/pan the layout for them.
        // Uses bottomMargin (not translationY) on the last child so the LinearLayout actually
        // reflows - canvasContainer (weight=1) shrinks to make room, avoiding a visual gap.
        run {
            val primaryBar = findViewById<View?>(R.id.primaryToolbarScroll)
            val contextBar = findViewById<View?>(R.id.toolbarScroll)
            // Each bar's own starting bottomMargin, captured once before any keyboard adjustment.
            // Every subsequent update sets margin = thisBar'sOwnBaseline + keyboardHeight,
            // independently per bar. This can't double-shift or lose relative spacing no matter
            // how they're actually nested/related in the layout — each bar only ever knows about
            // its own original position, never inferred from the other bar's current state.
            val baseMargins = HashMap<View, Int>()
            for (bar in listOf(primaryBar, contextBar)) {
                val lp0 = bar?.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                if (bar != null && lp0 != null) baseMargins[bar] = lp0.bottomMargin
            }
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
                val imeBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                val navBarBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                // imeBottom only reflects the portion of the keyboard actually overlapping the
                // app's content — a floating/split keyboard that doesn't cover this area reports
                // 0 here, so these bars correctly stay put in that case with no extra handling needed.
                val extraForKeyboard = (imeBottom - navBarBottom).coerceAtLeast(0)
                // Guard: only update bottomMargin when value changes — prevents layout
                // thrashing and the blinking/lag caused by firing on every tiny inset update.
                for (bar in listOf(primaryBar, contextBar)) {
                    val base = baseMargins[bar] ?: continue
                    val lp = bar?.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                    val target = base + extraForKeyboard
                    if (lp != null && lp.bottomMargin != target) {
                        lp.bottomMargin = target
                        bar.layoutParams = lp
                    }
                }
                onImeBottomChanged?.invoke(imeBottom)  // notify inline editor keyboard listener
                insets
            }
            // WindowInsetsAnimationCallback: fires reliably on Android 11+ with adjustNothing,
            // even when OnApplyWindowInsetsListener doesn't get called during IME animation.
            // Ensures onImeBottomChanged fires at the END of keyboard open/close animation.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val rootView = window.decorView
                androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(
                    rootView,
                    object : androidx.core.view.WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                        override fun onProgress(
                            insets: androidx.core.view.WindowInsetsCompat,
                            runningAnimations: MutableList<androidx.core.view.WindowInsetsAnimationCompat>
                        ): androidx.core.view.WindowInsetsCompat = insets

                        override fun onEnd(animation: androidx.core.view.WindowInsetsAnimationCompat) {
                            super.onEnd(animation)
                            if ((animation.typeMask and androidx.core.view.WindowInsetsCompat.Type.ime()) != 0) {
                                val imeBottom = androidx.core.view.ViewCompat.getRootWindowInsets(rootView)
                                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
                                onImeBottomChanged?.invoke(imeBottom)
                            }
                        }
                    }
                )
            }
        }

        val prefs = getPrefs()
        try { drawingView.paperType = PaperType.valueOf(prefs.getString("default_paper","LINED") ?: "LINED") } catch(e:Exception){}
        if (prefs.contains("paper_color")) drawingView.paperColor = prefs.getInt("paper_color", drawingView.paperColor)
        if (prefs.contains("hatch_scale")) drawingView.pendingHatchScale = prefs.getFloat("hatch_scale", drawingView.pendingHatchScale)
        pendingFontFamily = prefs.getString("last_font", "sans-serif") ?: "sans-serif"

        // Checked BEFORE the intent extra: if this Activity instance is being recreated after
        // a configuration change (e.g. screen rotation) rather than freshly launched, the
        // ORIGINAL intent (still "no filename" for a note that was brand new/unsaved at the
        // time it was first opened) gets redelivered unchanged — onPause()'s autoSave() may
        // have since assigned this note a real filename and written it to disk, but without
        // this check onCreate() would have no way to know that happened, would take the "New
        // Note" branch below, and load nothing — even though the content is sitting safely on
        // disk. This is what was actually causing "rotating the screen erases the canvas".
        val restoredFileName = savedInstanceState?.getString("pending_file_name")
        val fileName = restoredFileName ?: intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName; tvTitle.text = fileName
            val file = File(getDrawingsFolder(),"$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else { tvTitle.text = "New Note" }

        drawingView.migrateOldNotes(filesDir)
        lastSavedContent = drawingView.serialize()
        driveManager.trySilentSignIn { }
        drawingView.arcDivisions = prefs.getInt("arc_divisions",3)
        drawingView.defaultDimFontSize = prefs.getFloat("dim_font_size", 11f)
        drawingView.defaultDimArrowSize = prefs.getFloat("dim_arrow_size", 9f)
        // Apply bottom toolbar visibility preference
        if (!prefs.getBoolean("show_bottom_toolbar", true)) setBottomToolbarVisible(false)

        applyConvenientLayout()

        drawingView.onTextEditRequest       = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTextSelectRequest     = { item, sx, sy, rawX, rawY -> showTextSelectionBox(item, sx, sy, rawX, rawY) }
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
        drawingView.onOcrSnipSelected       = { bmp, l, t, r, b -> handleGeminiImageSnip(bmp, l, t, r, b) }
        drawingView.onPageSwipe             = { dir -> drawingView.scrollPage(dir) }
        drawingView.onDimensionCreated      = { dim -> showDimensionLabelDialog(dim) }
        drawingView.onDimensionEdit         = { dim -> showDimensionStylePanel(dim) }
        drawingView.onDrawingStarted        = {}
        drawingView.onDrawingEnded          = {}
        drawingView.onPolylineUpdated = {
            runOnUiThread { updatePolylineBar() }
        }
        drawingView.onShapeCompleted        = { _ ->
            lastShapeTool = drawingView.currentTool
            // Switch to SELECT so handles are interactive. Safe now — itemsInViewport
            // always includes the last stroke regardless of spatial grid timing.
            drawingView.post { setActiveTool(null, Tool.SELECT) }
            showDimButton()
        }
        drawingView.onInternalToolChange = { tool ->
            runOnUiThread {
                if (tool == Tool.SELECT) setActiveToolbarBtn(findViewById(R.id.btnSelect))
            }
        }
        drawingView.onItemSelected          = { item ->
            layerToolbar?.let { canvasContainer.removeView(it) }; layerToolbar = null
            // Show/hide lock button based on selection
            val lockBtn = findViewById<TextView>(R.id.btnLock)
            if (item != null) {
                lockBtn?.visibility = View.VISIBLE
                val locked = drawingView.isSelectionLocked()
                lockBtn?.text = if (locked) "🔒" else "🔓"
                lockBtn?.setTextColor(Color.WHITE)
                lockBtn?.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (locked) Color.parseColor("#C62828") else Color.parseColor("#2E7D32"))
                    cornerRadius = dp(8).toFloat()
                }
            } else {
                lockBtn?.visibility = View.GONE
                updateGroupModeToggle(false)
            }
            // Refresh dimension overlay for newly selected item
            if (dimModeEnabled) {
                if (item != null) showDimOverlayForSelected() else clearDimOverlay()
            }
            if (item == null) {
                // Tapped outside a shape — restore the last shape tool so user can keep drawing
                val restore = lastShapeTool
                if (restore != null) { lastShapeTool = null; setActiveTool(null, restore) }
            } else if (item !is TextItem) {
                val tb = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val scroll = HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
                    elevation = dp(5).toFloat()
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
                        it.gravity = Gravity.TOP or Gravity.END; it.topMargin = dp(56); it.rightMargin = dp(8)
                    }
                    addView(tb)
                }
                val iconSize = dp(30)
                fun sep() = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(18)).also { it.setMargins(dp(3), dp(6), dp(3), dp(6)) }
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                    tb.addView(this)
                }
                fun iconBtn(glyph: String, action: () -> Unit): TextView {
                    val v = TextView(this).apply {
                        text = glyph; textSize = 13f; gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#2A2A2A"))
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also { it.setMargins(dp(1), dp(5), dp(1), dp(5)) }
                        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F2F2F2")) }
                        setOnClickListener { action() }
                    }
                    tb.addView(v); return v
                }
                fun lBtn(label: String, action: () -> Unit) = TextView(this).apply {
                    text = label; textSize = 10f; gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#2A2A2A"))
                    val pad = dp(6); setPadding(pad, dp(5), pad, dp(5))
                    setOnClickListener { action() }; tb.addView(this)
                }
                iconBtn("⤒") { drawingView.bringToFront(item) }
                iconBtn("↑") { drawingView.bringForward(item) }
                iconBtn("↓") { drawingView.sendBackward(item) }
                iconBtn("⤓") { drawingView.sendToBack(item) }
                if (item is ImageItem) {
                    sep()
                    iconBtn("⇋") { drawingView.flipImageHorizontal(item) }
                    iconBtn("⇵") { drawingView.flipImageVertical(item) }
                }
                if (item is StrokeItem) {
                    sep()
                    lBtn("Offset") { showOffsetDialog(item) }
                    lBtn("Explode") {
                        val pieces = drawingView.explodeItem(item)
                        if (pieces == null) {
                            Toast.makeText(this, "Can't explode this — curves and simple lines aren't explodable", Toast.LENGTH_SHORT).show()
                        } else {
                            drawingView.applyExplodeResult(item, pieces)
                        }
                    }
                }
                if (item !is TableItem) {
                    sep()
                    iconBtn("⧉") {
                        val copy = drawingView.duplicateAnyItem(item)
                        if (copy != null) drawingView.applyDuplicateAnyResult(copy)
                    }
                }
                iconBtn("🗑") {
                    drawingView.deleteAnyItem(item)
                    canvasContainer.removeView(scroll); layerToolbar = null
                }
                canvasContainer.addView(scroll)
                layerToolbar = scroll
            }
        }
        drawingView.onTableCellEditRequest = { table, row, col, sx, sy -> showTableCellEditor(table, row, col, sx, sy) }
        drawingView.onTableActiveChanged = { table -> updateTableButton(table) }
        drawingView.onMultiSelectionChanged = { items ->
            runOnUiThread {
                val lockBtn = findViewById<TextView>(R.id.btnLock)
                if (items.isNotEmpty()) {
                    lockBtn?.visibility = View.VISIBLE
                    val locked = drawingView.isSelectionLocked()
                    lockBtn?.text = if (locked) "🔒" else "🔓"
                    lockBtn?.setTextColor(Color.WHITE)
                    lockBtn?.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (locked) Color.parseColor("#C62828") else Color.parseColor("#2E7D32"))
                        cornerRadius = dp(8).toFloat()
                    }
                } else {
                    lockBtn?.visibility = View.GONE
                }
                updateGroupModeToggle(items.isNotEmpty())
            }
        }
        drawingView.onExportWindowSelected  = { l,t,r,b -> exportWindowBitmap = drawingView.exportWindow(l,t,r,b); showExportWindowDialog() }
        drawingView.onAudioItemTap          = { item -> AudioHelper.togglePlay(item) { drawingView.invalidate() }; drawingView.invalidate() }

        setupBottomToolbar()
        setActiveTool(null, Tool.SELECT)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // Dismiss any open full-screen panel first
                    penOptionsPanel != null -> { dismissPenOptionsPanel(); return }
                    shapeOptionsPanel != null -> { dismissShapeOptionsPanel(); return }
                    snapOptionsPanel != null -> { dismissSnapOptionsPanel(); return }
                    dimScalePanel != null -> { dimScalePanel?.let { canvasContainer.removeView(it) }; dimScalePanel = null; return }
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
        findViewById<ImageButton>(R.id.btnDraw).setOnClickListener {
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.PEN) showPenOptionsPanel()
            else setActiveTool(it as ImageButton, Tool.PEN)
        }
        findViewById<ImageButton>(R.id.btnDraw).setOnLongClickListener { showPenOptionsPanel(); true }
        findViewById<ImageButton>(R.id.btnQuickEraser).setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.ERASER) showEraserModePopup(btn)
            else setActiveTool(btn as ImageButton, Tool.ERASER)
        }
        findViewById<ImageButton>(R.id.btnQuickEraser).setOnLongClickListener { showEraserOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnSelect)?.setOnClickListener { btn ->
            closeInlineEditor(true)
            lastShapeTool = null  // user explicitly chose SELECT — don't auto-restore shape tool
            showDimButton()
            if (drawingView.currentTool == Tool.SELECT) showSelectModePopup(btn)
            else setActiveTool(btn as ImageButton, Tool.SELECT)
        }
        findViewById<ImageButton>(R.id.btnText).setOnClickListener {
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.TEXT) showTextOptionsPanel()
            else setActiveTool(it as ImageButton, Tool.TEXT)
        }
        findViewById<ImageButton>(R.id.btnText).setOnLongClickListener { showTextOptionsPanel(); true }
        findViewById<ImageButton>(R.id.btnInsert).setOnClickListener { showInsertMenu() }

        // Handwriting-to-text realtime toggle (button removed from toolbar)
        findViewById<ImageButton>(R.id.btnTools).setOnClickListener { showShapesPicker(it as ImageButton) }
        findViewById<ImageButton>(R.id.btnTools).setOnLongClickListener { closeInlineEditor(true); showShapeOptionsPanel(); true }

        // Touch/Pan toggle
        var touchModeIsPan = false
        val btnTouchToggle = findViewById<ImageButton?>(R.id.btnTouchToggle)
        btnTouchToggle?.setImageResource(R.drawable.ic_finger)
        btnTouchToggle?.alpha = 0.35f
        btnTouchToggle?.setOnClickListener {
            touchModeIsPan = !touchModeIsPan
            drawingView.fingerPanMode = touchModeIsPan
            btnTouchToggle.alpha = if (touchModeIsPan) 1f else 0.35f
            btnTouchToggle.background = if (touchModeIsPan)
                android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#1C1C1E")); cornerRadius = dp(18).toFloat() }
            else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            btnTouchToggle.setColorFilter(if (touchModeIsPan) Color.WHITE else Color.parseColor("#1C1C1E"))
            btnTouchToggle.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80)
                .withEndAction { btnTouchToggle.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }.start()
        }

        // Page scroll thumb — touch and drag on right edge moves canvas
        val scrollThumb = findViewById<View?>(R.id.pageScrollThumb)
        scrollThumb?.let { thumb ->
            var dragStartRawY = 0f; var dragStartThumbY = 0f
            thumb.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        dragStartRawY = ev.rawY; dragStartThumbY = thumb.y; true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val parent = thumb.parent as android.view.View
                        val trackH = (parent.height - thumb.height).toFloat().coerceAtLeast(1f)
                        val newY = (dragStartThumbY + (ev.rawY - dragStartRawY)).coerceIn(0f, trackH)
                        thumb.y = newY
                        drawingView.scrollToPercent(newY / trackH)
                        true
                    }
                    else -> false
                }
            }
            drawingView.onScrollPercentChanged = { pct ->
                thumb.post {
                    val parent = thumb.parent as? android.view.View ?: return@post
                    val trackH = (parent.height - thumb.height).toFloat().coerceAtLeast(1f)
                    thumb.y = pct * trackH
                }
            }
        }

        val contextBar = findViewById<HorizontalScrollView>(R.id.toolbarScroll)
        contextBar.visibility = View.VISIBLE // always visible now

        // Remove old secondary bar buttons - context bar is rebuilt dynamically
        (contextBar.getChildAt(0) as? LinearLayout)?.removeAllViews()

        val btnExpand = findViewById<ImageButton>(R.id.btnExpand)
        btnExpand.visibility = View.GONE // no longer needed

        // Old secondary bar buttons wired below via rebuildContextBar
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.HIGHLIGHTER) showHighlighterOptionsPanel()
            else setActiveTool(btn as ImageButton, Tool.HIGHLIGHTER)
        }
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnLongClickListener { closeInlineEditor(true); setActiveTool(it as ImageButton, Tool.HIGHLIGHTER); true }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.BRUSH) showBrushOptionsPanel()
            else setActiveTool(btn as ImageButton, Tool.BRUSH)
        }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnLongClickListener { closeInlineEditor(true); setActiveTool(it as ImageButton, Tool.BRUSH); true }
        findViewById<ImageButton?>(R.id.btnQuickFill)?.setOnClickListener { btn ->
            closeInlineEditor(true)
            if (drawingView.currentTool == Tool.FILL) showHatchPicker()
            else setActiveTool(btn as ImageButton, Tool.FILL)
        }
        findViewById<ImageButton?>(R.id.btnQuickFill)?.setOnLongClickListener { showHatchPicker(); true }
        // Long-press on draw/eraser now just activates the tool and shows context bar (no floating panel)
        findViewById<ImageButton>(R.id.btnDraw).setOnLongClickListener { closeInlineEditor(true); showPenOptionsPanel(); true }
        findViewById<ImageButton>(R.id.btnQuickEraser).setOnLongClickListener { showEraserOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnLongClickListener { showHighlighterOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnLongClickListener { showBrushOptionsPanel(); true }

        findViewById<ImageButton?>(R.id.btnMenu)?.setOnClickListener { onMenuClick(it) }
        setupLayersButton()
        findViewById<ImageButton?>(R.id.btnLink)?.setOnClickListener { closeInlineEditor(true); showLinkPickerDialog() }
        findViewById<ImageButton?>(R.id.btnBack)?.setOnClickListener {
            // Close any open full-screen panel first; only exit if nothing is open
            when {
                penOptionsPanel != null -> dismissPenOptionsPanel()
                shapeOptionsPanel != null -> dismissShapeOptionsPanel()
                snapOptionsPanel != null -> dismissSnapOptionsPanel()
                dimScalePanel != null -> { dimScalePanel?.let { canvasContainer.removeView(it) }; dimScalePanel = null }
                else -> confirmThenExit()
            }
        }
        btnLayoutToggle.setOnClickListener { showLayoutMenu(it) }
        addFullscreenToggleButton()

        // Scale ratio button — always visible in top bar
        val btnScaleRatio = findViewById<TextView>(R.id.btnScaleRatio)
        btnScaleRatio?.setOnClickListener { showScaleRatioPopup(it) }

        // Dim mode button in top bar
        val btnDimToggle = findViewById<TextView>(R.id.btnDimToggle)
        btnDimToggle?.setOnClickListener {
            dimModeEnabled = !dimModeEnabled
            btnDimToggle.setTextColor(if (dimModeEnabled) Color.parseColor("#FF9800") else Color.parseColor("#3C3C3E"))
            if (dimModeEnabled) showDimOverlayForSelected() else clearDimOverlay()
        }

        // Lock button — appears when item(s) selected
        val btnLock = findViewById<TextView>(R.id.btnLock)
        fun updateLockBtn() {
            val locked = drawingView.isSelectionLocked()
            btnLock?.text = if (locked) "🔒" else "🔓"
            btnLock?.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (locked) Color.parseColor("#C62828") else Color.parseColor("#2E7D32"))
                cornerRadius = dp(8).toFloat()
            }
            btnLock?.setTextColor(Color.WHITE)
        }
        btnLock?.setOnClickListener {
            if (drawingView.isSelectionLocked()) drawingView.unlockSelectedItems()
            else drawingView.lockSelectedItems()
            updateLockBtn()
        }

        rebuildContextBar()
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

    internal fun setActiveTool(btn: ImageButton?, tool: Tool) {
        // Commit any open text edit before switching tools — same principle already applied to
        // Polyline just below (finalize before switching away). Without this, switching tools
        // while typing left the text editor fully open and interactive while the newly selected
        // tool ALSO went live underneath it — e.g. tapping a shape tool while still in an active
        // text edit left both the keyboard/editor AND the shape tool simultaneously active.
        if (activeEditText != null && tool != Tool.TEXT) closeInlineEditor(true)
        // Same principle, the other text state: tapping an already-committed text item to
        // select it (move/rotate handles, no active cursor) is a SEPARATE state from actively
        // typing — closeInlineEditor above doesn't touch it. Switching tools left that selection
        // overlay fully visible and interactive on top of whatever tool you just switched to.
        if (textSelectionItem != null && tool != Tool.TEXT) dismissTextSelectionBox()
        if (tool != Tool.POLYLINE) { drawingView.finalizePolyline(false); polylineBar?.let { canvasContainer.removeView(it) }; polylineBar = null }
        drawingView.currentTool = tool; setActiveToolbarBtn(btn)
        dismissPenOptionsPanel(); dismissEraserOptionsPanel(); dismissHighlighterOptionsPanel(); dismissBrushOptionsPanel(); dismissShapesPicker(); dismissShapeOptionsPanel()
        contextBarPage = 0
        rebuildContextBar()
    }
    private fun setActiveToolbarBtn(btn: ImageButton?) {
        val theme = currentAppTheme()
        activeToolbarButton?.let { it.isSelected = false; it.background = null; it.elevation = 0f }
        activeToolbarButton = btn
        btn?.let { it.isSelected = true; it.background = themedPillDrawable(theme, selected = true); it.elevation = themedPillElevation(theme) }
    }
    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Keeps the context (color/size) bar glued to the top edge of the primary toolbar, no
    // matter what size the primary toolbar's buttons currently are. Previously this gap was
    // a fixed 58dp set once in the XML, which only matched the DEFAULT medium icon size —
    // shrinking or growing icons via Settings > Toolbar > Icon size left this margin
    // unchanged, so the two bars drifted apart (small icons) or started overlapping (large
    // icons). Also forces the context bar's own height to wrap its (now size-aware) content
    // instead of a fixed 46dp, so it visually shrinks/grows in step with the primary bar.
    private fun repositionContextBar() {
        val primary = findViewById<View?>(R.id.primaryToolbarScroll) ?: return
        val context = findViewById<HorizontalScrollView?>(R.id.toolbarScroll) ?: return
        (context.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.height != FrameLayout.LayoutParams.WRAP_CONTENT) {
                lp.height = FrameLayout.LayoutParams.WRAP_CONTENT; context.layoutParams = lp
            }
        }
        primary.post {
            val lp = context.layoutParams as? FrameLayout.LayoutParams ?: return@post
            val gap = 0
            val newMargin = (primary.height.takeIf { it > 0 } ?: dp(54)) + gap
            if (lp.bottomMargin != newMargin) { lp.bottomMargin = newMargin; context.layoutParams = lp }
        }
    }

    // iOS-style capsule toolbar with soft shadow, in one of three user-selectable themes.
    // GLASS uses a real frosted blur (RenderEffect) on Android 12+ and falls back to a more
    // translucent capsule on older versions where live background blur isn't available.
    private fun currentAppTheme(): String = getPrefs().getString("app_theme", "ORIGINAL") ?: "ORIGINAL"

    private fun applyToolbarTheme() {
        val theme = currentAppTheme()
        // Each bar now gets its OWN capsule "shell" background (visible fill + edge stroke),
        // so the row of buttons reads as one enclosed navigation bar — like an iOS Control
        // Center grouping — instead of icons floating with nothing behind them.
        for (barId in listOf(R.id.primaryToolbarScroll, R.id.toolbarScroll)) {
            findViewById<View?>(barId)?.apply {
                background = themedBarShellDrawable(theme)
                elevation = themedPillElevation(theme)
                // Without this, the row's rectangular content just paints over the shell's
                // rounded corners instead of being cropped by them — which is why buttons and
                // color chips were poking straight past the curved ends of the bar.
                clipToOutline = true
            }
            // Give the inner row enough start/end padding that a button's own corner never sits
            // inside the shell's curved zone (the 8dp set in XML was tuned for the old
            // square/borderless bars, not this rounded shell).
            // toolbarScroll holds text-label pills (Select/Lasso/Rect/Multi) and needs real
            // vertical room or the labels clip; primaryToolbarScroll is icon-only and can run
            // much tighter — using the same value for both was wrong in both directions at once.
            val vPad = if (barId == R.id.toolbarScroll) dp(10) else dp(2)
            ((findViewById<View?>(barId) as? HorizontalScrollView)?.getChildAt(0) as? LinearLayout)?.let { row ->
                row.setPadding(dp(14), vPad, dp(14), vPad)
            }
        }
        val primaryIds = listOf(R.id.btnSelect, R.id.btnText, R.id.btnDraw, R.id.btnHighlighter, R.id.btnBrush,
                                 R.id.btnQuickEraser, R.id.btnQuickFill, R.id.btnTools, R.id.btnInsert,
                                 R.id.btnUndo, R.id.btnRedo)
        for (id in primaryIds) {
            val isActive = activeToolbarButton?.id == id
            findViewById<View?>(id)?.apply {
                // Only the ACTIVE tool gets its own filled pill now (matching the Control
                // Center reference: one shell holding plain icons, with the selected one
                // highlighted). Unselected buttons sit transparently on top of the shell
                // instead of stacking a second faint pill on top of it.
                background = if (isActive) themedPillDrawable(theme, selected = true) else null
                elevation = if (isActive) themedPillElevation(theme) else 0f
                (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                    lp.marginStart = dp(3); lp.marginEnd = dp(3); layoutParams = lp
                }
                // The default ImageButton style reserves generous padding AND a minimum
                // touch-target size (~48dp) around the icon graphic, regardless of the button's
                // own explicit width/height — that minimum-size floor is almost certainly why
                // reducing padding alone didn't shrink the visible empty space. Zeroing both
                // out here lets the button's actual configured size (from bar_icon_size) be the
                // real, final size, with just a little breathing room from padding.
                minimumWidth = 0; minimumHeight = 0
                // Proportional (not fixed) padding — fixed padding is a much smaller share of a
                // big button than a small one, so icons ended up nearly filling the whole button
                // at Small/Medium/Large while only Extra Large had real breathing room. This keeps
                // the icon-to-button ratio consistent at every size.
                val btnPx = getPrefs().getInt("bar_icon_size", 44)
                val pad = dp((btnPx * 0.22f).toInt())
                setPadding(pad, pad, pad, pad)
                (this as? ImageButton)?.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        // Top bar (back/undo-redo/link/palm/menu row) — previously untouched by theming since
        // it had no view ID to target from code. Styled as a single bar (matching its existing
        // flat-bar look) rather than per-button pills, since its icons/spacing are different
        // from the main toolbar.
        findViewById<View?>(R.id.topBarContainer)?.apply {
            background = when (theme) {
                "TRANSLUCENT" -> android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#4DFFFFFF")) }
                "GLASS" -> android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#66E1ECFB"), Color.parseColor("#1A7792B5"))
                )
                else -> android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#FFFFFF")) }
            }
            elevation = themedPillElevation(theme)
        }
        if (theme == "GLASS") scheduleBlurUpdate() else clearBlurBackdrops()
    }

    // ── Real backdrop blur (Glass theme only, API 31+) ──────────────────────────
    // Android has no built-in "blur what's behind this view" for standard XML/View UI — the
    // RenderEffect API blurs a view's OWN rendered content (including its children), which is
    // why applying it directly to a toolbar earlier blurred the icons themselves into unusable
    // blobs. The correct approach: manually snapshot the canvas content that's actually behind
    // each bar into a plain Bitmap, blur THAT bitmap via an ImageView with no children (safe,
    // since there's nothing else in it to blur), and place it as a backdrop layer behind the
    // bar's real buttons. Updates are throttled (250ms) since re-snapshotting and re-blurring on
    // every touch/draw event would be wasteful — a real drawing surface doesn't need to look
    // freshly blurred within milliseconds of a pan/zoom, a brief lag behind is imperceptible.
    private var blurBackdropTop: ImageView? = null
    private var blurBackdropPrimary: ImageView? = null
    private var blurBackdropContext: ImageView? = null
    private val blurHandler = Handler(Looper.getMainLooper())
    private var blurUpdateScheduled = false

    private fun scheduleBlurUpdate() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        if (blurUpdateScheduled) return
        blurUpdateScheduled = true
        blurHandler.postDelayed({
            blurUpdateScheduled = false
            if (currentAppTheme() == "GLASS") { updateBlurBackdrops(); scheduleBlurUpdate() }
        }, 400L)
    }

    private fun clearBlurBackdrops() {
        blurBackdropTop?.let { canvasContainer.removeView(it) }; blurBackdropTop = null
        blurBackdropPrimary?.let { canvasContainer.removeView(it) }; blurBackdropPrimary = null
        blurBackdropContext?.let { canvasContainer.removeView(it) }; blurBackdropContext = null
    }

    private fun updateBlurBackdrops() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        try {
            val dv = drawingView
            if (dv.width <= 0 || dv.height <= 0) return
            val full = Bitmap.createBitmap(dv.width, dv.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(full)
            dv.draw(c)
            val dvLoc = IntArray(2); dv.getLocationInWindow(dvLoc)

            fun applyBackdrop(target: View?, existing: ImageView?): ImageView? {
                target ?: return existing
                if (target.width <= 0 || target.height <= 0) return existing
                val loc = IntArray(2); target.getLocationInWindow(loc)
                val relY = (loc[1] - dvLoc[1]).coerceIn(0, (full.height - 1).coerceAtLeast(0))
                val h = target.height.coerceAtMost((full.height - relY).coerceAtLeast(1))
                val w = target.width.coerceAtMost(full.width)
                if (h <= 0 || w <= 0) return existing
                val cropped = Bitmap.createBitmap(full, 0, relY, w, h)
                val iv = existing ?: ImageView(this).also {
                    it.scaleType = ImageView.ScaleType.FIT_XY
                    val targetIndex = canvasContainer.indexOfChild(target).coerceAtLeast(0)
                    canvasContainer.addView(it, targetIndex, FrameLayout.LayoutParams(target.width, target.height))
                }
                (iv.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.width = target.width; lp.height = target.height
                    lp.gravity = (target.layoutParams as? FrameLayout.LayoutParams)?.gravity ?: 0
                    lp.topMargin = (target.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0
                    lp.bottomMargin = (target.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin ?: 0
                    iv.layoutParams = lp
                }
                iv.setImageBitmap(cropped)
                iv.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP))
                return iv
            }
            blurBackdropTop = applyBackdrop(findViewById(R.id.topBarContainer), blurBackdropTop)
            blurBackdropPrimary = applyBackdrop(findViewById(R.id.primaryToolbarScroll), blurBackdropPrimary)
            blurBackdropContext = applyBackdrop(findViewById(R.id.toolbarScroll), blurBackdropContext)
        } catch (e: Exception) {
            // Any failure here (view not laid out yet, OOM on a huge canvas, etc.) — silently
            // fall back to the existing tint-only glass look rather than crash the app over a
            // decorative effect.
            clearBlurBackdrops()
        }
    }

    // Builds a pill-shaped background whose LOOK actually differs per theme, not just its alpha:
    // ORIGINAL is a solid, opaque, popped-out button (like a real physical key). TRANSLUCENT is a
    // flat, uniform semi-transparent pill (canvas shows through evenly). GLASS adds a diagonal
    // gradient sheen (lighter top-left fading to more transparent bottom-right) plus a fine top
    // highlight, approximating a glossy glass surface without relying on a real background blur
    // (which isn't achievable this way — RenderEffect blurs the view's own content, not what's
    // behind it, which is what broke the buttons last time).
    private fun themedPillDrawable(theme: String, selected: Boolean = false): android.graphics.drawable.Drawable {
        val radius = dp(20).toFloat()
        if (selected) {
            // Clear accent-colored highlight for the active tool, regardless of theme — this
            // replaces the old StateListDrawable-based highlight, which relied on isSelected
            // driving a selector background. Plain GradientDrawables (used for per-button pills
            // since the theme rework) don't respond to that, so the active tool now gets an
            // explicit, unmistakable highlighted background instead.
            return android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#7B61FF"))
                cornerRadius = radius
                setStroke(dp(1), Color.parseColor("#5B3FE0"))
            }
        }
        return when (theme) {
            "TRANSLUCENT" -> android.graphics.drawable.GradientDrawable().apply {
                // Much lower opacity than before — the canvas should clearly show through
                setColor(Color.parseColor("#4DFFFFFF"))  // ~30% white
                cornerRadius = radius
                setStroke(dp(2), Color.parseColor("#CCFFFFFF"))  // thicker, brighter edge
            }
            "GLASS" -> android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                // Restored the blue-tinted glass sheen (the neutral gray-white version read as
                // too washed out), but softened from the original saturation — and back to a
                // single uniform-color stroke instead of a two-tone bevel, since the bevel's
                // highlight/shadow arcs on two closely-stacked bars were creating a visible
                // seam line right in the gap between them.
                intArrayOf(Color.parseColor("#66E1ECFB"), Color.parseColor("#1A7792B5"))
            ).apply {
                cornerRadius = radius
                setStroke(dp(2), Color.parseColor("#E0FFFFFF"))
            }
            else -> android.graphics.drawable.GradientDrawable().apply {
                // Light warm gray (not pure white) so it visibly stands out from a white/cream canvas
                setColor(Color.parseColor("#FAF7F2"))
                cornerRadius = radius
                setStroke(dp(1), Color.parseColor("#D8D2C4"))
            }
        }
    }
    private fun themedPillElevation(theme: String): Float = when (theme) {
        "TRANSLUCENT" -> dp(2).toFloat()
        "GLASS" -> dp(3).toFloat()
        else -> dp(5).toFloat()
    }

    // Outer "shell" background for a whole toolbar row — gives the row itself a visible,
    // enclosed navigation-bar boundary (rounded capsule + edge stroke), independent of
    // whatever the individual button pills inside it are doing. This is what was missing
    // before: bars had no background at all, so only isolated icons were visible.
    private fun themedBarShellDrawable(theme: String): android.graphics.drawable.Drawable {
        val radius = dp(24).toFloat()
        return when (theme) {
            "TRANSLUCENT" -> android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#59FFFFFF"))
                cornerRadius = radius
                setStroke(dp(2), Color.parseColor("#B3FFFFFF"))
            }
            "GLASS" -> android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#59E1ECFB"), Color.parseColor("#2E7792B5"))
            ).apply {
                cornerRadius = radius
                setStroke(dp(2), Color.parseColor("#D9FFFFFF"))
            }
            else -> android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FAF7F2"))
                cornerRadius = radius
                setStroke(dp(1), Color.parseColor("#D8D2C4"))
            }
        }
    }

    // Mode-picker chips (Select/Lasso/Rect/Multi, pen type, etc. in the context bar) previously
    // ignored the app theme entirely — always a flat solid black/gray box, regardless of GLASS
    // or Translucent being active. That's why they looked like opaque tiles sitting on top of
    // the glass shell instead of belonging to it. These give the unselected chip state a
    // translucent glass-consistent look per theme, while the selected chip stays a clear,
    // unambiguous accent color in all themes (it needs to stay readable, not blend in).
    private fun themedChipDrawable(theme: String, active: Boolean): android.graphics.drawable.Drawable {
        val radius = dp(11).toFloat()
        if (active) {
            return android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#7B61FF")); cornerRadius = radius
                if (theme != "ORIGINAL") setStroke(dp(1), Color.parseColor("#5B3FE0"))
            }
        }
        return when (theme) {
            "TRANSLUCENT" -> android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#4DFFFFFF")); cornerRadius = radius
                setStroke(dp(1), Color.parseColor("#80FFFFFF"))
            }
            "GLASS" -> android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF")); cornerRadius = radius
                setStroke(dp(1), Color.parseColor("#66FFFFFF"))
            }
            else -> android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#ECEAE7")); cornerRadius = radius
            }
        }
    }
    private fun themedChipTextColor(theme: String, active: Boolean): Int {
        if (active) return Color.WHITE
        return if (theme == "GLASS" || theme == "TRANSLUCENT") Color.parseColor("#2A2A2E") else Color.parseColor("#3C3C3E")
    }

    private fun setAppTheme(theme: String) {
        getPrefs().edit().putString("app_theme", theme).apply()
        applyToolbarTheme()
    }

    private fun showThemePickerDialog() {
        val options = listOf("ORIGINAL" to "Original", "TRANSLUCENT" to "Transparent", "GLASS" to "Glass")
        val current = currentAppTheme()
        val labels = options.map { it.second }.toTypedArray()
        val checkedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("App theme")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which -> setAppTheme(options[which].first); dialog.dismiss() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // iOS-style press feedback: scales down slightly on touch-down, springs back on release.
    // Applied broadly to toolbar buttons for a tactile, "butter smooth" feel instead of the
    // flat instant on/off of a default Android click.
    private fun View.addPressAnimation() {
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(90)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
                }
            }
            false  // never consume — let the normal click/long-click listeners still fire
        }
    }

    // Fade + slide-up entrance for floating panels, matching iOS sheet-presentation feel.
    private fun animatePanelIn(panel: View) {
        panel.alpha = 0f; panel.translationY = dp(24).toFloat()
        panel.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
    }

    // Fade + slide-down exit; calls onEnd (typically the actual removeView) once the animation
    // finishes so the view doesn't just vanish instantly.
    private fun animatePanelOut(panel: View, onEnd: () -> Unit) {
        panel.animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(160)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { onEnd() }.start()
    }

    private fun rebuildContextBar() {
        val contextBar = findViewById<HorizontalScrollView>(R.id.toolbarScroll) ?: return
        val row = (contextBar.getChildAt(0) as? LinearLayout) ?: LinearLayout(this).also {
            it.orientation = LinearLayout.HORIZONTAL; it.gravity = Gravity.CENTER_VERTICAL
            contextBar.addView(it)
        }
        // Set every rebuild (not just on first creation) so it stays correct even if this bar's
        // row existed before the rounded shell background was applied. Matches the padding used
        // for the primary toolbar so content never sits inside the shell's curved corner zone.
        row.setPadding(dp(14), 0, dp(14), 0)
        row.removeAllViews()
        // Scales with the user's icon-size preference instead of a fixed 38dp, so the
        // context (color/size) row grows and shrinks in step with the primary toolbar above it.
        val BAR_H = (dp(getPrefs().getInt("bar_icon_size", 44)) * 0.86f).toInt().coerceAtLeast(dp(30))
        val CHIP_H = BAR_H - dp(6)

        fun divider() { row.addView(View(this).apply {
            val lp = LinearLayout.LayoutParams(dp(1), dp(20)); lp.setMargins(dp(4),0,dp(4),0); layoutParams = lp
            setBackgroundColor(Color.parseColor("#DDD9D4"))
        })}

        // chipScrollRow: horizontal HorizontalScrollView of chips — used for ALL option sets
        // Only steals touch for horizontal movement; vertical passes through to outer bar
        fun chipScrollRow(items: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
            val hs = HorizontalScrollView(this).apply {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, BAR_H); layoutParams = lp
                isHorizontalScrollBarEnabled = false; overScrollMode = android.view.View.OVER_SCROLL_NEVER
            }
            val fr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2),0,dp(2),0) }
            var downX2 = 0f; var downY2 = 0f
            hs.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX2 = ev.x; downY2 = ev.y; false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx2 = kotlin.math.abs(ev.x - downX2); val dy2 = kotlin.math.abs(ev.y - downY2)
                        if (dx2 > dy2 && dx2 > dp(4)) hs.parent?.requestDisallowInterceptTouchEvent(true); false
                    }
                    else -> { hs.parent?.requestDisallowInterceptTouchEvent(false); false }
                }
            }
            items.forEachIndexed { i, (lbl, active) ->
                fr.addView(TextView(this).apply {
                    text = lbl; textSize = 10.5f; gravity = Gravity.CENTER
                    setPadding(dp(10), dp(3), dp(10), dp(3))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, CHIP_H); lp.setMargins(dp(2),dp(3),dp(2),dp(3)); layoutParams = lp
                    val theme = currentAppTheme()
                    background = themedChipDrawable(theme, active)
                    setTextColor(themedChipTextColor(theme, active))
                    setOnClickListener { onSelect(i) }
                })
            }
            hs.addView(fr); row.addView(hs)
        }

        // fontRow: 3 chips visible, swipe right to see more fonts
        fun fontRow(fonts: List<Pair<String, String>>, selectedFam: String, onSelect: (String) -> Unit) {
            val hs = HorizontalScrollView(this).apply {
                val lp = LinearLayout.LayoutParams(dp(66) * 3, BAR_H); layoutParams = lp
                isHorizontalScrollBarEnabled = false; overScrollMode = android.view.View.OVER_SCROLL_NEVER
            }
            val fr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2),0,dp(2),0) }
            var downX2 = 0f; var downY2 = 0f; var decided = false; var isHoriz = false
            hs.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX2 = ev.x; downY2 = ev.y; decided = false; isHoriz = false; false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (!decided) {
                            val dx2 = kotlin.math.abs(ev.x - downX2); val dy2 = kotlin.math.abs(ev.y - downY2)
                            if (dx2 > dp(5) || dy2 > dp(5)) { decided = true; isHoriz = dx2 >= dy2 }
                        }
                        if (isHoriz) v.parent?.requestDisallowInterceptTouchEvent(true)
                        else v.parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    else -> { v.parent?.requestDisallowInterceptTouchEvent(false); false }
                }
            }
            fonts.forEach { (lbl, fam) ->
                val active = selectedFam == fam
                fr.addView(TextView(this).apply {
                    text = lbl; textSize = 10.5f; gravity = Gravity.CENTER
                    typeface = try { android.graphics.Typeface.create(fam, android.graphics.Typeface.NORMAL) } catch (e: Exception) { android.graphics.Typeface.DEFAULT }
                    setPadding(dp(6), dp(3), dp(6), dp(3))
                    val lp = LinearLayout.LayoutParams(dp(62), CHIP_H); lp.setMargins(dp(2),dp(3),dp(2),dp(3)); layoutParams = lp
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(if (active) Color.parseColor("#1C1C1E") else Color.parseColor("#ECEAE7")); cornerRadius = dp(11).toFloat() }
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#3C3C3E"))
                    setOnClickListener { onSelect(fam); rebuildContextBar() }
                })
            }
            hs.addView(fr); row.addView(hs)
        }

        fun colorGrid(colors: List<Int>, selected: Int, page: Int, onPage: (Int) -> Unit, onPick: (Int) -> Unit) {
            // Measure available width dynamically by posting layout
            val DOT = dp(28); val M = dp(3); val MORE_W = dp(26)
            // Calculate how many dots fit in available space (will be finalized on first layout)
            val colorRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(0, BAR_H, 1f); layoutParams = lp
            }
            var downX2 = 0f; var isSwipe2 = false
            colorRow.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX2 = ev.x; isSwipe2 = false; false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx2 = ev.x - downX2
                        if (!isSwipe2 && kotlin.math.abs(dx2) > dp(30)) {
                            isSwipe2 = true
                            val numPages = kotlin.math.ceil(colors.size.toDouble() / 8).toInt().coerceAtLeast(1)
                            if (dx2 < 0) onPage((page + 1) % numPages) else onPage(((page - 1 + numPages) % numPages))
                        }; false
                    }
                    else -> false
                }
            }
            // Populate dots after layout so we know actual width
            colorRow.post {
                colorRow.removeAllViews()
                val availW = colorRow.width - MORE_W - M * 2
                val dotsPerPage = ((availW) / (DOT + M * 2)).coerceAtLeast(4)
                val numPages = kotlin.math.ceil(colors.size.toDouble() / dotsPerPage).toInt().coerceAtLeast(1)
                val start = (page % numPages) * dotsPerPage
                val visible = colors.subList(start, (start + dotsPerPage).coerceAtMost(colors.size))
                visible.forEachIndexed { i, color ->
                    val sel = color == selected
                    colorRow.addView(View(this).apply {
                        val lp = LinearLayout.LayoutParams(DOT, DOT); lp.setMargins(M,0,M,0); layoutParams = lp
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color)
                            setStroke(if (sel) dp(2) else dp(1), if (sel) Color.parseColor("#1C1C1E") else Color.parseColor("#D0CCC8"))
                        }
                        setOnClickListener { onPick(colors[start + i]); rebuildContextBar() }
                    })
                }
                // Page dots + more button in a small column at end
                val nav = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(MORE_W, BAR_H); layoutParams = lp
                }
                if (numPages > 1) for (i in 0 until numPages.coerceAtMost(5)) nav.addView(View(this).apply {
                    val lp = LinearLayout.LayoutParams(dp(4), dp(4)); lp.setMargins(0,dp(1),0,dp(1)); layoutParams = lp
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (i == page%numPages) Color.parseColor("#5C5856") else Color.parseColor("#D0CCC8")) }
                })
                nav.addView(TextView(this).apply {
                    text = "···"; textSize = 11f; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(MORE_W, dp(20)); lp.setMargins(0,dp(1),0,0); layoutParams = lp
                    setTextColor(Color.parseColor("#5C5856"))
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(10).toFloat() }
                    setOnClickListener { showColorGridDialog { c -> onPick(c); rebuildContextBar() } }
                })
                colorRow.addView(nav)
            }
            row.addView(colorRow)
        }

        fun sizeButton(currentSize: Float, max: Int, onChange: (Float) -> Unit) {
            var currentVal = currentSize
            val btn = FrameLayout(this).apply {
                val lp = LinearLayout.LayoutParams(BAR_H, BAR_H); lp.setMargins(dp(2),0,dp(2),0); layoutParams = lp
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(11).toFloat() }
            }
            val preview = object : android.view.View(this) {
                override fun onDraw(c: Canvas) {
                    val r = (currentVal / max * (width / 2f - 3f)).coerceIn(2f, width / 2f - 3f)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = Color.parseColor("#1C1C1E"); p.style = Paint.Style.FILL
                    c.drawCircle(width / 2f, height / 2f, r, p)
                }
            }
            btn.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            btn.setOnClickListener { v ->
                val popup = android.widget.PopupWindow(this)
                val pLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
                }
                val sliderView = object : android.view.View(this) {
                    val trackH = dp(14).toFloat()
                    override fun onDraw(c: Canvas) {
                        val tw = width.toFloat(); val ty = (height - trackH) / 2f
                        val p = Paint(Paint.ANTI_ALIAS_FLAG)
                        p.color = Color.parseColor("#E0E0E0"); c.drawRoundRect(android.graphics.RectF(0f,ty,tw,ty+trackH), trackH/2f, trackH/2f, p)
                        val prog = currentVal / max * tw
                        p.color = Color.parseColor("#1C1C1E"); c.drawRoundRect(android.graphics.RectF(0f,ty,prog,ty+trackH), trackH/2f, trackH/2f, p)
                        p.color = Color.WHITE; c.drawCircle(prog, height/2f, trackH/2f+dp(3).toFloat(), p)
                        p.color = Color.parseColor("#1C1C1E"); p.style = Paint.Style.STROKE; p.strokeWidth = dp(1).toFloat(); c.drawCircle(prog, height/2f, trackH/2f+dp(3).toFloat(), p)
                    }
                    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN || e.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                            currentVal = (e.x / width * max).coerceIn(1f, max.toFloat()); onChange(currentVal); preview.invalidate(); invalidate()
                        }; return true
                    }
                }
                sliderView.layoutParams = LinearLayout.LayoutParams(dp(220), dp(40))
                pLayout.addView(sliderView)
                popup.contentView = pLayout; popup.width = dp(256); popup.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                popup.isOutsideTouchable = true; popup.isFocusable = true; popup.elevation = dp(8).toFloat()
                popup.showAsDropDown(v, -dp(100), -dp(90))
            }
            row.addView(btn)
        }

        // Text-specific size picker: MS-Word-style scrollable list of point sizes plus an
        // editable box for a custom value — replaces the generic slider (sizeButton above) just
        // for the Text tool, since "drag a dot along a bar" doesn't give the precision or the
        // familiar reference points (12, 14, 18...) that choosing an exact font size calls for.
        // Displays/accepts POINTS; converts to/from the internal pixel unit via PT_TO_PX so the
        // physical size matches MS Word's own convention when actually printed.
        fun textSizeButton(currentSizePx: Float, onChange: (Float) -> Unit) {
            val standardPoints = listOf(6f,7f,8f,9f,10f,10.5f,11f,12f,13f,14f,16f,18f,20f,22f,24f,26f,28f,32f,36f,40f,44f,48f,54f,60f,66f,72f,80f,88f,96f,108f,120f,132f,144f,160f)
            // Rounds to 1 decimal place and drops a trailing ".0" for whole numbers — px/PT_TO_PX
            // is a floating-point division and routinely lands a hair off an exact value (4.999998
            // instead of 5), which showed up raw in the UI. Point sizes only ever need one decimal
            // of precision (the standard list itself only goes to the half-point, e.g. 10.5), so
            // rounding here throws away exactly the noise and nothing real.
            fun ptLabel(pt: Float): String {
                val r = kotlin.math.round(pt * 10f) / 10f
                return if (r == kotlin.math.round(r)) r.toInt().toString() else r.toString()
            }
            val btn = TextView(this).apply {
                text = ptLabel(currentSizePx / PT_TO_PX)
                textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#1C1C1E"))
                val lp = LinearLayout.LayoutParams(dp(44), BAR_H); lp.setMargins(dp(2), 0, dp(2), 0); layoutParams = lp
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(11).toFloat() }
            }
            btn.setOnClickListener { v ->
                val popup = android.widget.PopupWindow(this)
                val pLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
                }
                // Editable custom-value box at the top
                val customRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(8)) }
                val customEdit = android.widget.EditText(this).apply {
                    hint = "Custom pt"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(ptLabel(currentSizePx / PT_TO_PX))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val setBtn = TextView(this).apply {
                    text = "Set"; setTextColor(Color.parseColor("#1565C0")); setPadding(dp(12), dp(6), dp(4), dp(6))
                    setOnClickListener {
                        val pts = customEdit.text.toString().toFloatOrNull()
                        if (pts != null && pts > 0f) { onChange((pts * PT_TO_PX).coerceIn(4f, 1000f)); btn.text = ptLabel(pts); popup.dismiss() }
                    }
                }
                customRow.addView(customEdit); customRow.addView(setBtn)
                pLayout.addView(customRow)
                pLayout.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E1DC")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)) })
                // Scrollable list of standard sizes — at least 30 entries, matching the familiar
                // reference points from MS Word's own font-size dropdown.
                val scroll = android.widget.ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(140), dp(280)) }
                val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val currentPts = currentSizePx / PT_TO_PX
                for (pt in standardPoints) {
                    list.addView(TextView(this).apply {
                        text = ptLabel(pt)
                        textSize = 16f; setPadding(dp(16), dp(10), dp(16), dp(10))
                        if (kotlin.math.abs(pt - currentPts) < 0.01f) { setBackgroundColor(Color.parseColor("#E3EEFB")); setTextColor(Color.parseColor("#1565C0")) } else setTextColor(Color.parseColor("#1C1C1E"))
                        setOnClickListener { onChange(pt * PT_TO_PX); btn.text = ptLabel(pt); popup.dismiss() }
                    })
                }
                scroll.addView(list); pLayout.addView(scroll)
                popup.contentView = pLayout; popup.width = dp(180); popup.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                popup.isOutsideTouchable = true; popup.isFocusable = true; popup.elevation = dp(8).toFloat()
                popup.showAsDropDown(v, -dp(60), -dp(90))
            }
            row.addView(btn)
        }

        fun opacityButton(currentOpacity: Int, onChange: (Int) -> Unit) {
            var currentVal = currentOpacity
            val btn = object : android.view.View(this) {
                override fun onDraw(c: Canvas) {
                    val cx = width/2f; val cy = height/2f; val r = minOf(cx,cy) - dp(5)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.color = Color.parseColor("#1C1C1E"); c.drawArc(cx-r, cy-r, cx+r, cy+r, 90f, 180f, true, p)
                    p.color = Color.WHITE; c.drawArc(cx-r, cy-r, cx+r, cy+r, 270f, 180f, true, p)
                    p.color = Color.parseColor("#C8C4BE"); p.style = Paint.Style.STROKE; p.strokeWidth = dp(1).toFloat(); c.drawCircle(cx, cy, r, p)
                }
            }.apply {
                val lp = LinearLayout.LayoutParams(BAR_H, BAR_H); lp.setMargins(dp(2),0,dp(2),0); layoutParams = lp
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(11).toFloat() }
            }
            btn.setOnClickListener { v ->
                val popup = android.widget.PopupWindow(this)
                val pLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
                }
                val sliderView = object : android.view.View(this) {
                    val trackH = dp(14).toFloat()
                    override fun onDraw(c: Canvas) {
                        val tw = width.toFloat(); val ty = (height - trackH) / 2f
                        val rr = android.graphics.RectF(0f, ty, tw, ty + trackH)
                        val p = Paint(Paint.ANTI_ALIAS_FLAG)
                        val sq = trackH / 2f; p.color = Color.parseColor("#C0C0C0")
                        for (i in 0 until (tw/sq).toInt()+1) for (j in 0..1) { if((i+j)%2==0) c.drawRect(i*sq, ty+j*sq, (i+1)*sq, ty+(j+1)*sq, p) }
                        p.shader = android.graphics.LinearGradient(0f,0f,tw,0f,Color.TRANSPARENT,Color.parseColor("#1C1C1E"),android.graphics.Shader.TileMode.CLAMP)
                        c.drawRoundRect(rr, trackH/2f, trackH/2f, p); p.shader = null
                        val tx = currentVal / 255f * tw
                        p.color = Color.WHITE; p.style = Paint.Style.FILL; c.drawCircle(tx, height/2f, trackH/2f+dp(3).toFloat(), p)
                        p.color = Color.parseColor("#888888"); p.style = Paint.Style.STROKE; p.strokeWidth = dp(1).toFloat(); c.drawCircle(tx, height/2f, trackH/2f+dp(3).toFloat(), p)
                    }
                    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN || e.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                            currentVal = (e.x / width * 255).toInt().coerceIn(0,255); onChange(currentVal); btn.invalidate(); invalidate()
                        }; return true
                    }
                }
                sliderView.layoutParams = LinearLayout.LayoutParams(dp(220), dp(40))
                pLayout.addView(sliderView)
                popup.contentView = pLayout; popup.width = dp(256); popup.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                popup.isOutsideTouchable = true; popup.isFocusable = true; popup.elevation = dp(8).toFloat()
                popup.showAsDropDown(v, -dp(100), -dp(90))
            }
            row.addView(btn)
        }
        val allColors = listOf(
            Color.parseColor("#1C1C1E"), Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"), Color.parseColor("#FFCC00"),
            Color.parseColor("#34C759"), Color.parseColor("#007AFF"), Color.parseColor("#5856D6"), Color.parseColor("#AF52DE"),
            Color.WHITE, Color.parseColor("#FF2D55"), Color.parseColor("#FF6B35"), Color.parseColor("#30D158"),
            Color.parseColor("#64D2FF"), Color.parseColor("#0A84FF"), Color.parseColor("#BF5AF2"), Color.parseColor("#8E8E93"),
            Color.parseColor("#3A3A3C"), Color.parseColor("#636366"), Color.parseColor("#48484A"), Color.parseColor("#FFD60A"),
            Color.parseColor("#FF375F"), Color.parseColor("#5AC8FA"), Color.parseColor("#34AADC"), Color.parseColor("#4CD964")
        )
        val quickColors = listOf(
            Color.parseColor("#1C1C1E"), Color.parseColor("#FF3B30"), Color.parseColor("#FF9500"), Color.parseColor("#FFCC00"),
            Color.parseColor("#34C759"), Color.parseColor("#007AFF"), Color.parseColor("#5856D6"), Color.parseColor("#AF52DE")
        )
        // 8 fixed colors + swipe for more
        fun eightColors(selected: Int, onPick: (Int) -> Unit) {
            val DOT = dp(28); val M = dp(2)
            quickColors.forEach { color ->
                val sel = color == selected
                row.addView(View(this).apply {
                    val lp = LinearLayout.LayoutParams(DOT, DOT); lp.setMargins(M,0,M,0); layoutParams = lp
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color)
                        setStroke(if (sel) dp(2) else dp(1), if (sel) Color.parseColor("#1C1C1E") else Color.parseColor("#D0CCC8"))
                    }
                    setOnClickListener { onPick(color); rebuildContextBar() }
                })
            }
            row.addView(TextView(this).apply {
                text = "···"; textSize = 11f; gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(dp(24), dp(24)); lp.setMargins(dp(2),0,dp(2),0); layoutParams = lp
                setTextColor(Color.parseColor("#5C5856"))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(10).toFloat() }
                setOnClickListener { showColorGridDialog { c -> onPick(c); rebuildContextBar() } }
            })
        }

        val allPenTypes = listOf("Fountain" to PenStyle.FOUNTAIN, "Ball" to PenStyle.BALL, "Pencil" to PenStyle.PENCIL, "Calligraphy" to PenStyle.CALLIGRAPHY, "Marker" to PenStyle.MARKER)
        val allBrushTypes = listOf("Round" to BrushStyle.ROUND, "Ink" to BrushStyle.INK, "Watercolor" to BrushStyle.WATERCOLOR, "Crayon" to BrushStyle.CRAYON, "Charcoal" to BrushStyle.CHARCOAL, "Neon" to BrushStyle.NEON, "Dry Brush" to BrushStyle.DRY_BRUSH, "Spray" to BrushStyle.SPRAY, "Fire" to BrushStyle.FIRE, "Grass" to BrushStyle.GRASS)
        val allFontFamilies = listOf(
            "Default" to "sans-serif", "Serif" to "serif", "Monospace" to "monospace",
            "Sans Condensed" to "sans-serif-condensed", "Sans Light" to "sans-serif-light",
            "Sans Thin" to "sans-serif-thin", "Sans Medium" to "sans-serif-medium",
            "Sans Black" to "sans-serif-black", "Serif Mono" to "serif-monospace",
            "Casual" to "casual", "Cursive" to "cursive", "Small Caps" to "sans-serif-smallcaps",
            "Condensed Light" to "sans-serif-condensed-light",
            "Condensed Medium" to "sans-serif-condensed-medium", "Fantasy" to "fantasy"
        )

        when (drawingView.currentTool) {
            Tool.PEN -> {
                // Show 3 recently used pen types
                val recent = recentPenStyles.take(3)
                val recentLabels = recent.map { style -> (allPenTypes.firstOrNull { it.second == style }?.first ?: style.name) to (drawingView.currentPenStyle == style) }
                chipScrollRow(recentLabels) { i ->
                    val chosen = recent[i]; drawingView.currentPenStyle = chosen
                    recentPenStyles.remove(chosen); recentPenStyles.add(0, chosen)
                    rebuildContextBar()
                }
                divider()
                sizeButton(drawingView.currentStrokeWidth, 60) { drawingView.currentStrokeWidth = it }
                opacityButton(drawingView.brushOpacity) { drawingView.brushOpacity = it; drawingView.invalidate() }
                divider()
                eightColors(drawingView.currentColor) { c -> drawingView.currentColor = c }
            }
            Tool.HIGHLIGHTER -> {
                sizeButton(drawingView.highlighterThickness, 60) { drawingView.highlighterThickness = it }
                opacityButton(drawingView.highlighterOpacity * 255 / 100) { drawingView.highlighterOpacity = it * 100 / 255; drawingView.invalidate() }
                divider()
                eightColors(drawingView.currentColor) { c -> drawingView.currentColor = c }
            }
            Tool.BRUSH -> {
                // Show 3 recently used brush types
                val recent = recentBrushStyles.take(3)
                val recentLabels = recent.map { style -> (allBrushTypes.firstOrNull { it.second == style }?.first ?: style.name) to (drawingView.currentBrushStyle == style) }
                chipScrollRow(recentLabels) { i ->
                    val chosen = recent[i]; drawingView.currentBrushStyle = chosen
                    recentBrushStyles.remove(chosen); recentBrushStyles.add(0, chosen)
                    rebuildContextBar()
                }
                divider()
                sizeButton(drawingView.currentStrokeWidth, 60) { drawingView.currentStrokeWidth = it }
                opacityButton(drawingView.brushOpacity) { drawingView.brushOpacity = it; drawingView.invalidate() }
                divider()
                eightColors(drawingView.currentColor) { c -> drawingView.currentColor = c }
            }
            Tool.ERASER -> {
                chipScrollRow(listOf("Object" to (drawingView.eraserMode == EraserMode.OBJECT), "Area" to (drawingView.eraserMode == EraserMode.AREA))) { i ->
                    drawingView.eraserMode = if (i == 0) EraserMode.OBJECT else EraserMode.AREA; rebuildContextBar()
                }
                divider()
                sizeButton(drawingView.eraserSize, 120) { drawingView.eraserSize = it }
            }
            Tool.FILL -> {
                eightColors(drawingView.fillColor) { c -> drawingView.fillColor = c; drawingView.pendingHatchPattern = null; drawingView.pendingCustomHatchPath = null }
            }
            Tool.TEXT -> {
                // A specific existing item is selected (not actively being typed into) — sync the
                // panel's working variables from its REAL stored values first. Without this, the
                // panel just showed whatever editSize/editColor/etc were last left at (often the
                // 12pt default from app init), with zero connection to this item's actual size —
                // so it could display "12" while the item itself was really 50pt, and merely
                // opening/closing the panel without picking a new value left it that way forever.
                textSelectionItem?.let { sel ->
                    editSize = sel.size; editColor = sel.color; editOpacity = sel.opacity; pendingFontFamily = sel.fontFamily
                }
                // Show 3 recently used fonts (no scrollable row — just 3 chips)
                val recent = recentFonts.take(3)
                val recentLabels = recent.map { fam -> (allFontFamilies.firstOrNull { it.second == fam }?.first ?: fontDisplayName(fam)) to (pendingFontFamily == fam) }
                chipScrollRow(recentLabels) { i ->
                    val fam = recent[i]; pendingFontFamily = fam
                    recentFonts.remove(fam); recentFonts.add(0, fam)
                    textSelectionItem?.let { it.fontFamily = fam; drawingView.invalidate() }
                    activeEditText?.typeface = typefaceFromFamily(fam)
                    getPrefs().edit().putString("last_font", fam).apply()
                    rebuildContextBar()
                }
                // "All Fonts" chip — opens full font picker
                row.addView(TextView(this).apply {
                    text = "All Fonts ›"; textSize = 12f
                    setTextColor(Color.parseColor("#1565C0"))
                    setPadding(dp(10), 0, dp(10), 0)
                    gravity = Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
                    lp.setMargins(dp(4), 0, 0, 0); layoutParams = lp
                    setOnClickListener {
                        // Use activeEditText if open, otherwise a dummy that routes through pendingFontFamily
                        val et = activeEditText ?: android.widget.EditText(this@MainActivity).also { dummy ->
                            dummy.typeface = typefaceFromFamily(pendingFontFamily)
                        }
                        showFontPickerDialog(et)
                    }
                })
                divider()
                textSizeButton(editSize) { v -> editSize = v; activeEditText?.textSize = v / resources.displayMetrics.density; textSelectionItem?.let { it.size = v; drawingView.invalidate() } }
                opacityButton(editOpacity) { v -> editOpacity = v; activeEditText?.alpha = v / 255f; textSelectionItem?.let { it.opacity = v; drawingView.invalidate() } }
                divider()
                eightColors(editColor) { c -> editColor = c; activeEditText?.setTextColor(c); textSelectionItem?.let { it.color = c; drawingView.invalidate() } }
                divider()
                // B / I / U / Delete / Confirm — appended to this SAME shared context bar rather
                // than a separate floating one, since this is the bar that's actually always
                // visible and reachable while the Text tool is active, whether you're currently
                // typing (activeEditText != null) or have tapped a committed item to select/move
                // it (textSelectionItem != null, no live cursor). For the latter case there's no
                // selection to apply formatting to, so B/I/U toggle the whole item's text at once.
                fun ctxTextBtn(iconRes: Int, action: () -> Unit) {
                    row.addView(ImageView(this).apply {
                        setImageResource(iconRes); scaleType = ImageView.ScaleType.CENTER_INSIDE
                        val lp = LinearLayout.LayoutParams(dp(34), dp(34)); lp.setMargins(dp(3), 0, dp(3), 0); layoutParams = lp
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(8).toFloat() }
                        setColorFilter(Color.parseColor("#4A4A4A"))
                        setOnClickListener { action() }
                    })
                }
                ctxTextBtn(R.drawable.ic_text_bold) {
                    val et = activeEditText
                    if (et != null) { if (et.selectionStart != et.selectionEnd) toggleStyleOnSelection(et, Typeface.BOLD) else pendingBold = !pendingBold }
                    else textSelectionItem?.let { toggleFullItemSpan(it, 'S', Typeface.BOLD); drawingView.invalidate() }
                }
                ctxTextBtn(R.drawable.ic_text_italic) {
                    val et = activeEditText
                    if (et != null) { if (et.selectionStart != et.selectionEnd) toggleStyleOnSelection(et, Typeface.ITALIC) else pendingItalic = !pendingItalic }
                    else textSelectionItem?.let { toggleFullItemSpan(it, 'S', Typeface.ITALIC); drawingView.invalidate() }
                }
                ctxTextBtn(R.drawable.ic_text_underline) {
                    val et = activeEditText
                    if (et != null) { if (et.selectionStart != et.selectionEnd) toggleUnderlineOnSelection(et) else pendingUnderline = !pendingUnderline }
                    else textSelectionItem?.let { toggleFullItemSpan(it, 'U', 0); drawingView.invalidate() }
                }
                ctxTextBtn(R.drawable.ic_text_delete) {
                    if (activeEditText != null) closeInlineEditor(false, delete = true)
                    else textSelectionItem?.let { drawingView.removeTextItem(it); dismissTextSelectionBox(); drawingView.invalidate() }
                }
                ctxTextBtn(R.drawable.ic_text_check) {
                    if (activeEditText != null) closeInlineEditor(true)
                    else dismissTextSelectionBox()
                }
            }
            Tool.SELECT, Tool.LASSO, Tool.AUTOSELECT, Tool.MULTISELECT -> {
                // Select: rectangle icon, Lasso: lasso icon, Rectangle (was Auto): dashed rect icon
                data class SM(val label: String, val drawIcon: (Canvas, Paint, android.graphics.RectF) -> Unit, val tool: Tool)
                val modes = listOf(
                    SM("Select", { c, p, r -> p.style = Paint.Style.STROKE; p.strokeWidth = 3f; c.drawRect(r, p) }, Tool.SELECT),
                    SM("Lasso", { c, p, r -> p.style = Paint.Style.STROKE; p.strokeWidth = 3f; val path = android.graphics.Path(); path.moveTo(r.centerX(), r.top); path.cubicTo(r.right, r.top, r.right, r.bottom, r.centerX(), r.bottom); path.cubicTo(r.left, r.bottom, r.left, r.top, r.centerX(), r.top); c.drawPath(path, p) }, Tool.LASSO),
                    SM("Rect", { c, p, r -> p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 5f), 0f); c.drawRect(r, p) }, Tool.AUTOSELECT),
                    SM("Multi", { c, p, r ->
                        // 3 stacked rectangles — back two show only top+right edges
                        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f
                        val w = r.width(); val h = r.height(); val off = w * 0.22f
                        // Back rect (only top + right visible)
                        p.alpha = if (drawingView.currentTool == Tool.MULTISELECT) 255 else 140
                        c.drawLine(r.left + off*2, r.top, r.right, r.top, p)               // top
                        c.drawLine(r.right, r.top, r.right, r.bottom - off*2, p)           // right
                        // Middle rect (only top + right visible)
                        p.alpha = if (drawingView.currentTool == Tool.MULTISELECT) 255 else 190
                        c.drawLine(r.left + off, r.top + off, r.right - off, r.top + off, p)    // top
                        c.drawLine(r.right - off, r.top + off, r.right - off, r.bottom - off, p) // right
                        // Front rect (fully visible)
                        p.alpha = 255
                        c.drawRect(r.left, r.top + off*2, r.right - off*2, r.bottom, p)
                    }, Tool.MULTISELECT)
                )
                modes.forEach { mode ->
                    val active = drawingView.currentTool == mode.tool
                    val iconView = object : android.view.View(this) {
                        override fun onDraw(c: Canvas) {
                            val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = if (active) Color.WHITE else Color.parseColor("#1C1C1E")
                            val r = android.graphics.RectF(dp(6).toFloat(), dp(6).toFloat(), (width - dp(6)).toFloat(), (height - dp(6)).toFloat())
                            mode.drawIcon(c, p, r)
                        }
                    }
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        // This column holds an icon AND a text label stacked vertically — it
                        // needs more room than a plain icon button, so it can't just inherit
                        // BAR_H's tight minimum (30dp), which is why the label was clipping at
                        // the Small/Medium bar-size presets.
                        val lp = LinearLayout.LayoutParams(dp(50), BAR_H.coerceAtLeast(dp(46))); lp.setMargins(dp(3),0,dp(3),0); layoutParams = lp
                        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat(); setColor(if (active) Color.parseColor("#1C1C1E") else Color.parseColor("#ECEAE7")) }
                        setOnClickListener { setActiveTool(null, mode.tool) }
                    }
                    col.addView(iconView, LinearLayout.LayoutParams(dp(28), dp(28)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
                    col.addView(TextView(this).apply { text = mode.label; textSize = 8f; gravity = Gravity.CENTER; setTextColor(if (active) Color.WHITE else Color.parseColor("#5C5856")) })
                    row.addView(col)
                }
            }
            else -> {
                sizeButton(drawingView.currentStrokeWidth, 60) { drawingView.currentStrokeWidth = it }
                divider()
                eightColors(drawingView.currentColor) { c -> drawingView.currentColor = c }
            }
        }
    }

    // Hides the primary bottom toolbar while typing in a table cell (so the keyboard has more
    // room and the toolbar doesn't visually compete with it), and brings it back when the user
    // taps outside to dismiss the keyboard.
    private fun setBottomBarVisible(visible: Boolean) {
        findViewById<View?>(R.id.primaryToolbarScroll)?.visibility = if (visible) View.VISIBLE else View.GONE
    }
    private fun setBottomToolbarVisible(visible: Boolean) = setBottomBarVisible(visible)

    // Replaces the old auto-hide-while-drawing behavior with a manual toggle: nothing hides
    // or shows itself automatically anymore, the person decides. Whatever tool was active
    // when this is tapped stays active — this only ever touches view visibility, never
    // drawingView.currentTool, so there's nothing here that could reset it.
    private var fullscreenRestoreBtn: View? = null
    private fun addFullscreenToggleButton() {
        val topBar = findViewById<LinearLayout?>(R.id.topBarContainer) ?: return
        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        val btn = TextView(this).apply {
            text = "⛶"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setBackgroundResource(outValue.resourceId)
            contentDescription = "Fullscreen"
            setOnClickListener { enterFullscreen() }
        }
        val menuBtn = findViewById<View?>(R.id.btnMenu)
        val idx = if (menuBtn != null) topBar.indexOfChild(menuBtn) else topBar.childCount
        topBar.addView(btn, idx.coerceAtLeast(0))
    }
    private fun enterFullscreen() {
        findViewById<View?>(R.id.topBarContainer)?.visibility = View.GONE
        findViewById<View?>(R.id.primaryToolbarScroll)?.visibility = View.GONE
        findViewById<View?>(R.id.toolbarScroll)?.visibility = View.GONE
        // Hides the actual phone status bar (battery/time/etc), not just this app's own top bar —
        // previously "fullscreen" only ever hid EngiNotes's own UI, leaving the real system bar
        // untouched, which isn't true fullscreen.
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreenRestoreBtn != null) return
        val btn = TextView(this).apply {
            text = "⛶"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#991C1C1E")); cornerRadius = dp(20).toFloat()
            }
            elevation = dp(6).toFloat()
            contentDescription = "Exit fullscreen"
            setOnClickListener { exitFullscreen() }
        }
        val lp = FrameLayout.LayoutParams(dp(40), dp(40))
        lp.gravity = Gravity.TOP or Gravity.END
        lp.topMargin = dp(14); lp.rightMargin = dp(14)
        canvasContainer.addView(btn, lp)
        fullscreenRestoreBtn = btn
    }
    private fun exitFullscreen() {
        findViewById<View?>(R.id.topBarContainer)?.visibility = View.VISIBLE
        if (getPrefs().getBoolean("show_bottom_toolbar", true)) findViewById<View?>(R.id.primaryToolbarScroll)?.visibility = View.VISIBLE
        if (penOptionsPanel == null && eraserOptionsPanel == null && highlighterOptionsPanel == null && brushOptionsPanel == null) {
            findViewById<View?>(R.id.toolbarScroll)?.visibility = View.VISIBLE
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        fullscreenRestoreBtn?.let { canvasContainer.removeView(it) }
        fullscreenRestoreBtn = null
    }

    internal fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    private fun showOffsetDialog(item: StrokeItem) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        container.addView(TextView(this).apply {
            text = "Offset distance"; textSize = 13f; setTextColor(Color.parseColor("#6A6A6A"))
        })
        val input = android.widget.EditText(this).apply {
            hint = "e.g. 20"; setText("20")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        container.addView(input)
        val dirRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        var outward = true
        val outBtn = TextView(this).apply {
            text = "Outward"; setPadding(dp(14), dp(8), dp(14), dp(8)); textSize = 13f
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#FF9800")); cornerRadius = dp(14).toFloat() }
            setTextColor(Color.WHITE)
        }
        val inBtn = TextView(this).apply {
            text = "Inward"; setPadding(dp(14), dp(8), dp(14), dp(8)); textSize = 13f
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#ECEAE7")); cornerRadius = dp(14).toFloat() }
            setTextColor(Color.parseColor("#3C3C3E"))
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(8)
        }
        fun refreshDirButtons() {
            outBtn.background = android.graphics.drawable.GradientDrawable().apply { setColor(if (outward) Color.parseColor("#FF9800") else Color.parseColor("#ECEAE7")); cornerRadius = dp(14).toFloat() }
            outBtn.setTextColor(if (outward) Color.WHITE else Color.parseColor("#3C3C3E"))
            inBtn.background = android.graphics.drawable.GradientDrawable().apply { setColor(if (!outward) Color.parseColor("#FF9800") else Color.parseColor("#ECEAE7")); cornerRadius = dp(14).toFloat() }
            inBtn.setTextColor(if (!outward) Color.WHITE else Color.parseColor("#3C3C3E"))
        }
        outBtn.setOnClickListener { outward = true; refreshDirButtons() }
        inBtn.setOnClickListener { outward = false; refreshDirButtons() }
        val lp1 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.marginStart = dp(8)
        dirRow.addView(outBtn, lp1); dirRow.addView(inBtn, lp2)
        container.addView(dirRow)

        AlertDialog.Builder(this)
            .setTitle("Offset")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val mag = input.text.toString().toFloatOrNull()
                if (mag != null && mag > 0f) {
                    val dist = if (outward) mag else -mag
                    val result = drawingView.offsetItem(item, dist)
                    if (result == null) {
                        Toast.makeText(this, "Offset too large for this shape, or unsupported (curves)", Toast.LENGTH_SHORT).show()
                    } else {
                        drawingView.applyOffsetResult(item, result)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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

    private var layersToggleBtn: ImageButton? = null

    // No XML layout file available in this workflow to add a new icon button declaratively, so
    // this creates one at runtime and inserts it into the same toolbar as the three-dot menu,
    // right before it — matching "second rightmost icon." Visibility is controlled by the
    // Settings toggle; when off, Layers is still reachable via the three-dot menu itself.
    private fun setupLayersButton() {
        val btnMenu = findViewById<ImageButton?>(R.id.btnMenu) ?: return
        val parent = btnMenu.parent as? android.view.ViewGroup ?: return
        val index = parent.indexOfChild(btnMenu)
        val btnLayers = ImageButton(this).apply {
            background = btnMenu.background
            setPadding(btnMenu.paddingLeft, btnMenu.paddingTop, btnMenu.paddingRight, btnMenu.paddingBottom)
            contentDescription = "Layers"
            setImageDrawable(fiveBarsDrawable())
            id = View.generateViewId()
            layoutParams = btnMenu.layoutParams
            setOnClickListener { showLayersPanel() }
        }
        parent.addView(btnLayers, index)
        layersToggleBtn = btnLayers
        updateLayersButtonVisibility()
    }

    internal fun updateLayersButtonVisibility() {
        layersToggleBtn?.visibility = if (getPrefs().getBoolean("layers_button_always_visible", false)) View.VISIBLE else View.GONE
    }

    // Drawn directly instead of depending on a vector drawable resource, since there's no XML/
    // resource-file access in this workflow to add one cleanly.
    private fun fiveBarsDrawable(): android.graphics.drawable.Drawable {
        val size = dp(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333"); strokeWidth = dp(2).toFloat(); strokeCap = Paint.Cap.ROUND
        }
        val barCount = 5
        val gap = size.toFloat() / (barCount + 1)
        for (i in 1..barCount) { val y = gap * i; canvas.drawLine(size * 0.12f, y, size * 0.88f, y, paint) }
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    // Lists every item currently on the canvas, top-of-stack (frontmost, rendered last) shown
    // FIRST — standard layer-panel convention. Reuses the existing bringToFront/bringForward/
    // sendBackward/sendToBack rather than introducing a second way to reorder items, so this
    // panel and the per-shape layer buttons (in the shape selection toolbar) always agree.
    private fun showLayersPanel() {
        var dlg: AlertDialog? = null
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }

        fun renameLayerDialog(layerId: Int, currentName: String) {
            val input = android.widget.EditText(this).apply { setText(currentName); setSelection(text.length) }
            AlertDialog.Builder(this).setTitle("Rename layer").setView(input)
                .setPositiveButton("Rename") { _, _ ->
                    val n = input.text.toString().trim()
                    if (n.isNotEmpty()) drawingView.renameLayer(layerId, n)
                    showLayersPanel(); dlg?.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }

        fun refresh() {
            list.removeAllViews()
            // layers is already top-of-list = frontmost, matching the request directly —
            // no reversal needed here, unlike the old per-item version.
            for (layer in drawingView.layers) {
                val count = drawingView.actions.count { drawingView.itemLayerId(it) == layer.id }
                val isActive = layer.id == drawingView.currentLayerId
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    if (isActive) setBackgroundColor(Color.parseColor("#E3EEFB"))
                }
                row.addView(TextView(this).apply {
                    text = "${layer.name}  ($count)"
                    textSize = 14f; setTextColor(if (layer.visible) Color.parseColor("#1C1C1E") else Color.parseColor("#B0AAA2"))
                    if (isActive) setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    // Tap = make this the active layer (new items go here). Long-press = rename.
                    setOnClickListener { drawingView.currentLayerId = layer.id; refresh() }
                    setOnLongClickListener { renameLayerDialog(layer.id, layer.name); true }
                })
                fun iconBtn(label: String, color: String = "#1565C0", action: () -> Unit) = TextView(this).apply {
                    text = label; textSize = 15f; setPadding(dp(8), dp(6), dp(8), dp(6)); setTextColor(Color.parseColor(color))
                    setOnClickListener { action(); refresh() }
                }
                row.addView(iconBtn(if (layer.visible) "👁" else "🚫") { drawingView.setLayerVisible(layer.id, !layer.visible) })
                row.addView(iconBtn("⬆") { drawingView.moveLayerUp(layer.id) })
                row.addView(iconBtn("⬇") { drawingView.moveLayerDown(layer.id) })
                row.addView(iconBtn("🗑", "#D32F2F") {
                    if (drawingView.layers.size <= 1) {
                        Toast.makeText(this, "Can't delete the only layer", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Delete \"${layer.name}\"?")
                            .setMessage(if (count > 0) "This deletes everything in it too — $count item${if (count == 1) "" else "s"}." else "This layer is empty.")
                            .setPositiveButton("Delete") { _, _ -> drawingView.deleteLayer(layer.id); refresh() }
                            .setNegativeButton("Cancel", null).show()
                    }
                })
                list.addView(row)
                list.addView(View(this).apply { setBackgroundColor(Color.parseColor("#EEEEEE")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)) })
            }
        }
        refresh()
        val scroll = ScrollView(this).apply { addView(list) }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(scroll) }
        dlg = AlertDialog.Builder(this).setTitle("Layers (tap = active, long-press = rename)")
            .setView(outer)
            .setNeutralButton("+ New Layer") { _, _ ->
                val input = android.widget.EditText(this).apply { hint = "Layer name" }
                AlertDialog.Builder(this).setTitle("New layer").setView(input)
                    .setPositiveButton("Create") { _, _ ->
                        val n = input.text.toString().trim().ifEmpty { "Layer ${drawingView.layers.size + 1}" }
                        drawingView.createLayer(n)
                        showLayersPanel()
                    }.setNegativeButton("Cancel", null).show()
            }
            .setPositiveButton("Done", null).show()
    }

    fun onMenuClick(v: View) {
        closeInlineEditor(true)
        val popup = PopupMenu(this, v)
        popup.menu.add("Note: ${currentFileName ?: "Untitled"}")
        listOf("Save","Save As","Export","Export Window","Clear Canvas").forEach { popup.menu.add(it) }
        if (currentFileName != null) popup.menu.add("Delete This Note")
        popup.menu.add("Add to Book")
        popup.menu.add("Layers")
        popup.menu.add("Back Up to Drive")
        listOf("Open PDF","Chart Builder","Handwriting to Text","Ask Gemini about Drawing","Settings","Exit").forEach { popup.menu.add(it) }
        popup.setOnMenuItemClickListener { item ->
            when {
                item.title.toString().startsWith("Note:") -> showRenameDialog()
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
                item.title == "Layers" -> showLayersPanel()
                item.title == "Open PDF" -> pickPdfLauncher.launch("application/pdf")
                item.title == "Chart Builder" -> chartLauncher.launch(android.content.Intent(this, ChartActivity::class.java))
                item.title == "Handwriting to Text" -> convertHandwritingInPlace()
                item.title == "Ask Gemini about Drawing" -> {
                    Toast.makeText(this,"Drag a box around the area to ask about",Toast.LENGTH_SHORT).show()
                    setActiveTool(null, Tool.OCR_SNIP)
                }
                item.title == "Settings" -> showSettingsDialog()
                item.title == "Exit" -> confirmThenExit()
                item.title == "Back Up to Drive" -> {
                    if (!driveManager.isSignedIn()) {
                        Toast.makeText(this, "Sign in with Google from the home screen first", Toast.LENGTH_SHORT).show()
                    } else {
                        currentFileName?.let { backUpNoteToDrive(it, silent = false) }
                    }
                }
            }
            true
        }
        popup.show()
    }

    // ---- Drive backup: the note's own .eng file plus every image/audio/custom-font it references ----

    /**
     * Pulls out every local file path a note's serialized content refers to — image files,
     * audio recordings, custom hatch bitmaps, and custom font files — so they can be synced
     * to Drive alongside the note itself. Parses the same pipe/\u0001-delimited line format
     * that DrawingView.serialize()/loadFromString() use; see DrawingView.kt for the field order
     * of each line type.
     */
    private fun extractAssetPaths(content: String): List<String> {
        val paths = linkedSetOf<String>()
        content.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\u0001')
            when (parts.getOrNull(0)) {
                "IMAGE" -> parts.getOrNull(1)?.let { if (it.isNotBlank()) paths.add(it) }
                "AUDIO" -> parts.getOrNull(1)?.let { if (it.isNotBlank()) paths.add(it) }
                "FILL" -> {
                    parts.getOrNull(1)?.let { if (it.isNotBlank()) paths.add(it) }
                    parts.getOrNull(6)?.let { if (it.isNotBlank()) paths.add(it) } // customHatchPath
                }
                "TEXT" -> {
                    val font = parts.getOrNull(9)
                    if (!font.isNullOrBlank() && font.startsWith("/")) paths.add(font) // custom font file, not a built-in family name
                }
            }
        }
        return paths.toList()
    }

    /** Saves [name], then uploads it and every asset it references to Drive. Set [silent] to skip toasts on success (used by auto-backup). */
    private fun backUpNoteToDrive(name: String, silent: Boolean) {
        if (name == currentFileName) writeCurrentFile()
        val engFile = File(getDrawingsFolder(), "$name.eng")
        if (!engFile.exists()) {
            if (!silent) Toast.makeText(this, "Couldn't find the saved note file", Toast.LENGTH_SHORT).show()
            return
        }
        if (!silent) Toast.makeText(this, "Backing up to Drive…", Toast.LENGTH_SHORT).show()
        val assetPaths = extractAssetPaths(engFile.readText())
        uploadAssetsThenNote(assetPaths, 0, engFile, name, silent)
    }

    private fun uploadAssetsThenNote(paths: List<String>, index: Int, engFile: File, noteName: String, silent: Boolean) {
        if (index >= paths.size) {
            driveManager.uploadFile(engFile, "$noteName.eng", "text/plain") { success, error ->
                if (!silent) Toast.makeText(this, if (success) "Backed up to Drive!" else "Backup failed: $error", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val assetFile = File(paths[index])
        if (!assetFile.exists()) { uploadAssetsThenNote(paths, index + 1, engFile, noteName, silent); return }
        driveManager.uploadAsset(assetFile) { _, _ -> uploadAssetsThenNote(paths, index + 1, engFile, noteName, silent) }
    }

    /** Shows every .eng note currently backed up on Drive, letting the user pick one to restore. */
    private fun showRestoreFromDriveDialog() {
        if (!driveManager.isSignedIn()) { Toast.makeText(this, "Sign in with Google first", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Checking Drive…", Toast.LENGTH_SHORT).show()
        driveManager.listFiles { files, error ->
            if (files == null) { Toast.makeText(this, error ?: "Couldn't reach Drive", Toast.LENGTH_SHORT).show(); return@listFiles }
            val notes = files.filter { it.name.endsWith(".eng") }
            if (notes.isEmpty()) { Toast.makeText(this, "No notes found on Drive", Toast.LENGTH_SHORT).show(); return@listFiles }
            val names = notes.map { it.name.removeSuffix(".eng") }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Restore a note from Drive")
                .setItems(names) { _, which -> confirmAndRestore(notes[which]) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Warns before overwriting a local note that's newer than the Drive copy being restored. */
    private fun confirmAndRestore(driveFile: com.google.api.services.drive.model.File) {
        val name = driveFile.name.removeSuffix(".eng")
        val localFile = File(getDrawingsFolder(), driveFile.name)
        val driveModified = driveFile.modifiedTime?.value ?: 0L
        if (localFile.exists() && localFile.lastModified() > driveModified) {
            AlertDialog.Builder(this)
                .setTitle("Local version is newer")
                .setMessage("Your local copy of \"$name\" is newer than the one on Drive. Restoring will overwrite your local changes with the older Drive version. Continue?")
                .setPositiveButton("Overwrite local") { _, _ -> doRestore(driveFile, localFile) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            doRestore(driveFile, localFile)
        }
    }

    private fun doRestore(driveFile: com.google.api.services.drive.model.File, localFile: File) {
        Toast.makeText(this, "Restoring…", Toast.LENGTH_SHORT).show()
        driveManager.downloadFile(driveFile.name, localFile) { success, error ->
            if (!success) { Toast.makeText(this, "Restore failed: $error", Toast.LENGTH_SHORT).show(); return@downloadFile }
            val content = localFile.readText()
            val assetPaths = extractAssetPaths(content)
            restoreAssets(assetPaths, 0) {
                Toast.makeText(this, "Restored! Open the note to view it.", Toast.LENGTH_SHORT).show()
                if (currentFileName == localFile.nameWithoutExtension) drawingView.loadFromString(content)
            }
        }
    }

    private fun restoreAssets(paths: List<String>, index: Int, onDone: () -> Unit) {
        if (index >= paths.size) { onDone(); return }
        val destFile = File(paths[index])
        if (destFile.exists()) { restoreAssets(paths, index + 1, onDone); return }
        destFile.parentFile?.mkdirs()
        driveManager.downloadAsset(destFile.name, destFile) { _, _ -> restoreAssets(paths, index + 1, onDone) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        driveManager.handleSignInResult(requestCode, data) { success, error ->
            Toast.makeText(this, if (success) "Signed in with Google!" else (error ?: "Sign-in cancelled"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertHandwritingInPlace() {
        // Render only the pen strokes visible on screen to a bitmap, run ML Kit OCR,
        // then place the recognised text as a TextItem at the strokes' centroid and remove the strokes.
        val bmp = drawingView.renderVisibleStrokesOnly()
        if (bmp == null) { Toast.makeText(this, "No strokes visible", Toast.LENGTH_SHORT).show(); return }
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isEmpty()) { Toast.makeText(this, "No text recognised", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
                // Place text at centre of screen in world coords
                val wx = drawingView.screenCenterWorldX(); val wy = drawingView.screenCenterWorldY()
                drawingView.addText(text, wx, wy, drawingView.defaultTextSize, 0f, Color.BLACK)
                drawingView.clearRecentPenStrokes()
                Toast.makeText(this, "Converted!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { Toast.makeText(this, "OCR failed: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    // ═══════════════════════════ Ask Gemini ═══════════════════════════
    // Bring-your-own-API-key: each student pastes their OWN free key from Google AI Studio
    // (Settings > AI Assistant). No server, no shared quota, no cost to us.
    private val aiExecutor = java.util.concurrent.Executors.newCachedThreadPool()
    private fun geminiApiKey(): String = getPrefs().getString("gemini_api_key", "") ?: ""
    // "gemini-flash-latest" is a Google-maintained ALIAS, not a pinned version — Google
    // "hot-swaps" it to whichever current Flash model is live, so this never goes stale the
    // way a specific version number (like the old "gemini-2.5-flash" default) eventually will.
    // Anyone already saved on that old pinned default gets silently moved to the alias too.
    private fun geminiModel(): String {
        val saved = getPrefs().getString("gemini_model", "gemini-flash-latest")?.trim()?.ifBlank { "gemini-flash-latest" } ?: "gemini-flash-latest"
        return if (saved == "gemini-2.5-flash") "gemini-flash-latest" else saved
    }
    private fun geminiBaseUrl(): String = getPrefs().getString("gemini_base_url", "https://generativelanguage.googleapis.com/v1beta")?.trim()?.ifBlank { "https://generativelanguage.googleapis.com/v1beta" } ?: "https://generativelanguage.googleapis.com/v1beta"
    private fun geminiResponsePath(): String = getPrefs().getString("gemini_response_path", "candidates.0.content.parts.0.text")?.trim()?.ifBlank { "candidates.0.content.parts.0.text" } ?: "candidates.0.content.parts.0.text"

    // Points at a single JSON file in the app's own GitHub repo (not a separate server) — lets
    // model name / base URL / response-parsing path be corrected for every existing install
    // without a Play Store update, if Google renames something or reshapes the API.
    // IMPORTANT: base_url is only ever accepted if it's a *.googleapis.com address (checked
    // below) — this is what stops a compromised GitHub account from turning this into a way
    // to redirect requests (and everyone's real API key) to some other server. The file itself
    // has nothing sensitive in it, but it DOES control where a real credential gets sent at
    // request time, so that check matters regardless of what's visible in the repo.
    private val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/AmritKhadka88/EngiNotes/main/gemini-config.json"
    private fun refreshGeminiConfigFromRemote(onResult: (success: Boolean, message: String) -> Unit) {
        aiExecutor.execute {
            try {
                val conn = (java.net.URL(REMOTE_CONFIG_URL).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
                val json = org.json.JSONObject(text)
                val newModel = json.optString("model", "").trim()
                val newBaseUrl = json.optString("base_url", "").trim()
                val newResponsePath = json.optString("response_path", "").trim()
                val safeBaseUrl = if (newBaseUrl.matches(Regex("^https://[a-zA-Z0-9.\\-]+\\.googleapis\\.com(/.*)?$"))) newBaseUrl else null
                if (newModel.isBlank() && safeBaseUrl == null && newResponsePath.isBlank()) {
                    runOnUiThread { onResult(false, "Update file was empty or invalid.") }
                    return@execute
                }
                val editor = getPrefs().edit()
                if (newModel.isNotBlank()) editor.putString("gemini_model", newModel)
                if (safeBaseUrl != null) editor.putString("gemini_base_url", safeBaseUrl)
                if (newBaseUrl.isNotBlank() && safeBaseUrl == null) { /* rejected - not a googleapis.com address, silently ignored, keep old value */ }
                if (newResponsePath.isNotBlank()) editor.putString("gemini_response_path", newResponsePath)
                editor.apply()
                runOnUiThread { onResult(true, "Updated — now using model: ${geminiModel()}") }
            } catch (e: Exception) {
                runOnUiThread { onResult(false, "Couldn't reach the update file. Check your connection.") }
            }
        }
    }

    // Walks a JSON structure by a dot-separated path (numeric segments = array index) — lets
    // the response's answer field live wherever geminiResponsePath() says, instead of that
    // location being hardcoded, so a response-shape change is also just a remote-config fix.
    private fun resolveJsonPath(root: org.json.JSONObject, path: String): String? {
        var current: Any? = root
        for (seg in path.split(".")) {
            current = when (val c = current) {
                is org.json.JSONObject -> if (c.has(seg)) c.get(seg) else return null
                is org.json.JSONArray -> { val idx = seg.toIntOrNull() ?: return null; if (idx < c.length()) c.get(idx) else return null }
                else -> return null
            }
        }
        return current?.toString()
    }

    // code: 0 = ok, 1 = not configured, 2 = model not found, 3 = other error, 4 = key invalid, 5 = quota hit
    // code passed to onDone: 0 = ok, 1 = not configured, 2 = model not found, 3 = other error,
    // 4 = key invalid, 5 = quota hit, 6 = got a response but couldn't parse any of it
    private fun askGeminiStreaming(prompt: String, imageBytes: ByteArray?, onChunk: (String) -> Unit, onDone: (code: Int, finishReason: String?, errorDetail: String?) -> Unit) {
        val key = geminiApiKey()
        if (key.isBlank()) { onDone(1, null, null); return }
        val model = geminiModel()
        aiExecutor.execute {
            var conn: java.net.HttpURLConnection? = null
            try {
                // streamGenerateContent + alt=sse (as a URL query param, not a header — Google
                // silently returns the old one-shot-JSON behavior without it, no error, just no
                // streaming) is what actually delivers text as it's generated instead of only
                // once the full answer is ready. This is the real fix for "why does it take so
                // long to appear" — the total generation time doesn't change, but the FIRST
                // words show up almost immediately instead of everything appearing at once at
                // the very end.
                val url = java.net.URL("${geminiBaseUrl()}/models/$model:streamGenerateContent?alt=sse")
                conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", key)
                    setRequestProperty("Accept", "text/event-stream")
                    // Android's HttpURLConnection has a known history of misbehaving on
                    // long-lived streaming responses when it tries to cache them or reuse a
                    // pooled keep-alive connection for the next request — neither makes sense
                    // for a one-shot SSE stream, and disabling both removes a real, documented
                    // source of streaming connections silently failing partway through.
                    useCaches = false
                    setRequestProperty("Connection", "close")
                    connectTimeout = 20000; readTimeout = 30000
                }
                val parts = org.json.JSONArray()
                parts.put(org.json.JSONObject().put("text",
                    "Write the answer as a single flowing paragraph of clean study notes — the way a focused " +
                    "student would jot the essential answer straight into their own notebook, not the way an AI " +
                    "assistant replies in a chat window. No greetings, no \"Sure, here is...\", no meta-commentary " +
                    "about being an AI, no bullet points or headers unless the question itself is a literal list " +
                    "request, and no closing offer of further help. Just the direct, cohesive substance a student " +
                    "would actually want written down.\n\nQuestion: $prompt"))
                if (imageBytes != null) {
                    val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                    parts.put(org.json.JSONObject().put("inline_data", org.json.JSONObject().apply {
                        put("mime_type", "image/jpeg"); put("data", b64)
                    }))
                }
                val payload = org.json.JSONObject()
                    .put("contents", org.json.JSONArray().put(org.json.JSONObject().put("parts", parts)))
                    .put("generationConfig", org.json.JSONObject()
                        .put("thinkingConfig", org.json.JSONObject().put("thinkingLevel", "low"))
                        // Was 400 — too tight in practice. Thinking tokens draw from this same
                        // budget even at "low", so a chunk of it was being spent before any
                        // visible answer text even started, leaving genuinely normal questions
                        // (e.g. "what is photosynthesis") cut off mid-sentence instead of just
                        // guarding against a runaway-long response, which was the actual intent.
                        .put("maxOutputTokens", 800))
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val resultCode = when (code) { 404 -> 2; 401, 403 -> 4; 429 -> 5; else -> 3 }
                    val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(150)
                    runOnUiThread { onDone(resultCode, null, "HTTP $code${if (!errBody.isNullOrBlank()) ": $errBody" else ""}") }
                    return@execute
                }
                // Each SSE line is "data: {...}" holding one incremental piece of the answer, in
                // the exact same shape a normal (non-streaming) response has — so the same
                // geminiResponsePath() config, and the same Check-for-Update recovery path,
                // covers a response-shape change here too, not just the non-streaming call.
                var gotAny = false
                // finishReason ("STOP" normally) shows up in the final chunk of a candidate.
                // Anything else — most commonly "SAFETY" for sensitive topics, but also
                // "RECITATION"/"PROHIBITED_CONTENT"/"OTHER" — means the model was cut off before
                // it actually finished, which otherwise looks exactly like a bug: a sentence
                // that just stops mid-word with no explanation.
                var finishReason: String? = null
                conn.inputStream.bufferedReader().forEachLine { line ->
                    if (line.startsWith("data: ")) {
                        try {
                            val chunkJson = org.json.JSONObject(line.removePrefix("data: "))
                            val piece = resolveJsonPath(chunkJson, geminiResponsePath())
                            if (!piece.isNullOrEmpty()) { gotAny = true; runOnUiThread { onChunk(piece) } }
                            val fr = resolveJsonPath(chunkJson, "candidates.0.finishReason")
                            if (!fr.isNullOrEmpty() && fr != "null") finishReason = fr
                        } catch (e: Exception) { /* one malformed chunk shouldn't kill the whole stream */ }
                    }
                }
                runOnUiThread { onDone(if (gotAny) 0 else 6, finishReason, null) }
            } catch (e: java.io.IOException) {
                // Covers UnknownHostException (no internet / DNS failure), a missing INTERNET
                // permission, and a connection that dies mid-stream. Real exception type+message
                // included now instead of a generic guess, so a repeat failure is diagnosable
                // instead of a mystery.
                runOnUiThread { onDone(3, null, "${e.javaClass.simpleName}: ${e.message}") }
            } catch (e: Exception) {
                runOnUiThread { onDone(3, null, "${e.javaClass.simpleName}: ${e.message}") }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // Handles the whole "select something -> get an answer inserted below it" flow: places a
    // placeholder, then grows it live as text streams in, rather than waiting for the whole
    // answer and dropping it in all at once.
    internal fun runGeminiQuery(prompt: String, imageBytes: ByteArray?, worldX: Float, worldY: Float) {
        if (geminiApiKey().isBlank()) { showGeminiSetupDialog { runGeminiQuery(prompt, imageBytes, worldX, worldY) }; return }
        // worldY is meant to be the desired TOP of the answer (just below the question) — but
        // TextItem.y means the BOTTOM of the text everywhere in DrawingView (drawTextItem/
        // getBounds/findTextItemAt all compute top = item.y - height). Left alone, that
        // mismatch means the rendered top keeps climbing back UP toward (and past) the
        // question as the streamed answer grows, since the same fixed "bottom" now has more
        // height above it every time a new chunk arrives — that's the actual "text goes up /
        // out of bounds" bug. Fix: after every change, explicitly reposition using the real
        // current height so the TOP stays where it should and the bottom is what moves.
        val desiredTopY = worldY
        val placeholder = drawingView.addText("✨", worldX, worldY, drawingView.defaultTextSize, 0f, Color.GRAY) ?: return
        drawingView.repositionTextItemTop(placeholder, desiredTopY)
        val accumulated = StringBuilder()
        askGeminiStreaming(prompt, imageBytes,
            onChunk = { piece ->
                accumulated.append(piece)
                placeholder.text = accumulated.toString()
                placeholder.color = Color.BLACK
                drawingView.repositionTextItemTop(placeholder, desiredTopY)
                drawingView.invalidate()
            },
            onDone = { code, finishReason, errorDetail ->
                if (code == 0 && accumulated.isNotEmpty()) {
                    var finalText = accumulated.toString()
                    // finishReason other than STOP means the model was cut off before it
                    // actually finished — most often "SAFETY" for sensitive topics (even
                    // legitimate historical/factual ones). Without this note, a response like
                    // that just looks like the app broke mid-sentence for no reason.
                    if (finishReason != null && finishReason != "STOP") {
                        val why = when (finishReason) {
                            "SAFETY", "PROHIBITED_CONTENT" -> "Gemini's content filter"
                            "RECITATION" -> "a copyright/citation check"
                            "MAX_TOKENS" -> "the response length limit"
                            else -> "Gemini ($finishReason)"
                        }
                        finalText += "\n\n[Cut short by $why — try rephrasing the question if you need the full answer.]"
                    }
                    insertGeminiAnswer(placeholder, finalText)
                    drawingView.repositionTextItemTop(placeholder, desiredTopY)
                } else if (code != 0) {
                    val baseMsg = when (code) {
                        2 -> "⚠️ Gemini model not found — it may have been renamed. Open Settings > AI Assistant and tap \"Check for Update\", or update the model name manually."
                        4 -> "⚠️ Your API key isn't working. Open Settings > AI Assistant and check or replace it."
                        5 -> "⚠️ You've used up today's free Gemini limit. It resets at midnight Pacific time — try again after that."
                        6 -> "⚠️ Got a response from Gemini but couldn't read it — Google may have changed something. Open Settings > AI Assistant and tap \"Check for Update\"."
                        else -> "⚠️ Couldn't reach Gemini. Check your connection and try again."
                    }
                    // If any text HAD already streamed in before things failed, keep it instead
                    // of silently throwing it away — the old behavior replaced whatever was
                    // showing with just the error, which looked like the answer never existed
                    // even when part of it genuinely did arrive.
                    val preserved = if (accumulated.isNotEmpty()) "\n\n---\n${accumulated}" else ""
                    // Real exception detail appended (small, at the end) instead of only a
                    // guessed category — makes a repeat failure actually diagnosable.
                    val detail = if (!errorDetail.isNullOrBlank()) "\n($errorDetail)" else ""
                    placeholder.text = baseMsg + detail + preserved
                    placeholder.color = Color.parseColor("#C62828")
                    drawingView.repositionTextItemTop(placeholder, desiredTopY)
                    drawingView.invalidate()
                }
            }
        )
    }

    // Splits out any Markdown image link Gemini included in its answer, shows the text part
    // immediately, and tries to fetch the image in the background. LLMs frequently include
    // links to images that don't actually exist (hallucinated URLs) — if the fetch fails,
    // this fails silently and just leaves the text answer as-is, rather than showing a
    // broken-image placeholder or blocking on it.
    private fun insertGeminiAnswer(placeholder: TextItem, rawAnswer: String) {
        val imgRegex = Regex("""!\[(.*?)\]\((https?://[^\s)]+)\)""")
        val match = imgRegex.find(rawAnswer)
        val textOnly = rawAnswer.replace(imgRegex, "").trim().ifBlank { "(see image below)" }
        placeholder.text = textOnly; placeholder.color = Color.BLACK
        drawingView.invalidate()
        val imageUrl = match?.groupValues?.get(2) ?: return
        aiExecutor.execute {
            try {
                val conn = (java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 20000 }
                val bytes = conn.inputStream.use { it.readBytes() }; conn.disconnect()
                val folder = File(filesDir, "images").also { it.mkdirs() }
                val file = File(folder, "gemini_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { it.write(bytes) }
                runOnUiThread {
                    val w = (drawingView.width / drawingView.getScaleFactor() * 0.4f).coerceAtLeast(60f)
                    drawingView.addImage(file.absolutePath, placeholder.x + w / 2f, placeholder.y + w * 0.33f + 24f, w, w * 0.66f)
                }
            } catch (e: Exception) { /* hallucinated/broken link - text answer is already shown, nothing to fall back to */ }
        }
    }

    // First-run (or "update my key") setup — kept to the minimum a student needs: paste key,
    // done. The "Get a free key" link opens Google AI Studio's key page directly.
    private fun showGeminiSetupDialog(onConfigured: () -> Unit) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)) }
        container.addView(TextView(this).apply {
            text = "Paste a free Gemini API key to turn on \"Ask Gemini\" in your notes. It's tied to your own Google account — free, and only you use it."
            textSize = 13f; setTextColor(Color.parseColor("#4A4A4A")); setPadding(0, 0, 0, dp(12))
        })
        val keyInput = EditText(this).apply { hint = "Paste API key here"; setText(geminiApiKey()) }
        container.addView(keyInput)
        container.addView(TextView(this).apply {
            text = "Get a free key →"; textSize = 13f; setTextColor(Color.parseColor("#1565C0")); setPadding(0, dp(10), 0, 0)
            setOnClickListener { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
        })
        AlertDialog.Builder(this).setTitle("Set Up Gemini").setView(container)
            .setPositiveButton("Save") { _, _ ->
                val k = keyInput.text.toString().trim()
                if (k.isNotEmpty()) { getPrefs().edit().putString("gemini_api_key", k).apply(); onConfigured() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // Entry point for "ask about a drawing/diagram/handwritten note" — reuses the existing
    // region-snip tool (Tool.OCR_SNIP) purely for its "drag a box, get a cropped bitmap back"
    // mechanics; onOcrSnipSelected was declared but never actually wired up anywhere before this.
    private fun handleGeminiImageSnip(bmp: Bitmap, left: Float, top: Float, right: Float, bottom: Float) {
        val stream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        runGeminiQuery(
            "Explain what's shown in this image (diagram, equation, or handwritten note) clearly for a student's notes.",
            stream.toByteArray(), (left + right) / 2f, bottom + 30f
        )
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
        val currentFile = currentFileName  // e.g. "MyNote" — the current open note
        val notes = folder.listFiles()?.filter { it.extension == "eng" && it.nameWithoutExtension != currentFile }
            ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
        if (notes.isEmpty()) { Toast.makeText(this, "No other notes in $bookName to link to", Toast.LENGTH_SHORT).show(); return }
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
        // Inject shapes into context bar instead of floating overlay
        setActiveTool(anchor, Tool.RECTANGLE) // default shape
        val contextBar = findViewById<HorizontalScrollView>(R.id.toolbarScroll) ?: return
        val row = contextBar.getChildAt(0) as? LinearLayout ?: return
        row.removeAllViews()
        for ((iconRes, tool) in shapeEntries) {
            row.addView(ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setBackgroundResource(R.drawable.btn_toolbar_selector)
                val p = LinearLayout.LayoutParams(dp(42), dp(42)); p.setMargins(dp(2),0,dp(2),0); layoutParams = p
                setOnClickListener { setActiveTool(null, tool) }
            })
            // Polyline sits immediately beside the Line icon — same family of tool (connected
            // straight segments), so users find it right where they'd expect it.
            if (tool == Tool.LINE) {
                row.addView(object : View(this) {
                    // Draws a zigzag polyline glyph: three straight segments at different
                    // angles (not a smooth tilde/wave) so it visually reads as "connected
                    // straight lines" rather than a curve.
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val density = resources.displayMetrics.density
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#3C3C3E"); style = Paint.Style.STROKE
                            strokeWidth = 2.4f * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                        }
                        val w = width.toFloat(); val h = height.toFloat()
                        val path = android.graphics.Path()
                        path.moveTo(w*0.18f, h*0.72f)
                        path.lineTo(w*0.40f, h*0.30f)
                        path.lineTo(w*0.62f, h*0.62f)
                        path.lineTo(w*0.84f, h*0.24f)
                        canvas.drawPath(path, p)
                        // Small dots at each vertex to emphasize discrete connected points
                        val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3C3C3E"); style = Paint.Style.FILL }
                        for ((vx, vy) in listOf(w*0.18f to h*0.72f, w*0.40f to h*0.30f, w*0.62f to h*0.62f, w*0.84f to h*0.24f)) {
                            canvas.drawCircle(vx, vy, 1.6f * density, dotP)
                        }
                    }
                }.apply {
                    setBackgroundResource(R.drawable.btn_toolbar_selector)
                    val p = LinearLayout.LayoutParams(dp(42), dp(42)); p.setMargins(dp(2),0,dp(2),0); layoutParams = p
                    setOnClickListener { setActiveTool(null, Tool.POLYLINE) }
                })
            }
        }
        contextBar.scrollTo(0, 0)
    }

    private fun dismissShapesPicker() {
        shapesPickerOverlay?.let { canvasContainer.removeView(it) }
        shapesPickerOverlay = null
    }

    // Closes any open floating panel (shapes picker, pen options, eraser options) -
    // called whenever the user picks a different tool so panels never get stuck open.
    internal fun dismissAllFloatingPanels() {
        dismissShapesPicker()
        dismissPenOptionsPanel()
        dismissShapeOptionsPanel()
        dismissSnapOptionsPanel()
        dismissEraserOptionsPanel()
        dismissHighlighterOptionsPanel()
        dismissBrushOptionsPanel()
        dismissTextOptionsPanel()
    }

    private fun showInsertMenu() {
        closeInlineEditor(true)
        AlertDialog.Builder(this).setTitle("Insert")
            .setItems(arrayOf("Image from Gallery","Take Photo","Table","Record Audio","Snip from PDF","Dimension Tool")) { _, i ->
                setActiveTool(null, Tool.SELECT)
                when(i) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> launchCamera()
                    2 -> showTableInsertDialog()
                    3 -> checkAndRecordAudio()
                    4 -> pickPdfLauncher.launch("application/pdf")
                    5 -> showDimensionModeDialog()
                }
            }.show()
    }

    private fun showDimensionModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dimension Tool")
            .setItems(arrayOf(
                "Linear — Auto (draw reference, enter length)",
                "Linear — Manual (type each label)",
                "Angular — tap 3 points (vertex, arm1, arm2)"
            )) { _, i ->
                when (i) {
                    0 -> {
                        drawingView.dimMode = DimMode.AUTO
                        drawingView.autoRefPixelLen = 0f
                        setActiveTool(null, Tool.DIMENSION)
                        android.widget.Toast.makeText(this, "Draw a line on a known length", android.widget.Toast.LENGTH_LONG).show()
                    }
                    1 -> {
                        drawingView.dimMode = DimMode.MANUAL
                        drawingView.dimAngular = false
                        setActiveTool(null, Tool.DIMENSION)
                        android.widget.Toast.makeText(this, "Tap two points to place dimension", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        drawingView.dimMode = DimMode.MANUAL
                        drawingView.dimAngular = true
                        setActiveTool(null, Tool.DIMENSION)
                        android.widget.Toast.makeText(this, "Tap: 1=vertex, 2=first arm, 3=second arm", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun showDimensionLabelDialog(dim: DimensionItem) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(8)) }

        if (dim.mode == DimMode.AUTO && drawingView.autoRefPixelLen == 0f) {
            // This is the first/reference dimension — ask user what length this line represents
            layout.addView(TextView(this).apply {
                text = "What is the real-world length of this line?"; textSize = 14f; setPadding(0,0,0,dp(10))
            })
            val lenInput = android.widget.EditText(this).apply {
                hint = "Length (e.g. 3.5)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                requestFocus()
            }
            val unitInput = android.widget.EditText(this).apply { hint = "Unit (e.g. m, mm, ft)"; setText("m") }
            layout.addView(lenInput)
            layout.addView(TextView(this).apply { text = "Unit:"; textSize = 13f; setPadding(0,dp(8),0,dp(4)) })
            layout.addView(unitInput)
            AlertDialog.Builder(this).setTitle("Set Reference Length").setView(layout)
                .setPositiveButton("Set") { _, _ ->
                    val realLen = lenInput.text.toString().toFloatOrNull() ?: 1f
                    val unit = unitInput.text.toString().ifBlank { "m" }
                    drawingView.autoRefPixelLen = dim.len   // this line's pixel length = reference
                    drawingView.autoRefRealLen = realLen
                    drawingView.autoRefUnit = unit
                    dim.refLength = realLen; dim.unit = unit
                    dim.label = "%.2f %s".format(realLen, unit)
                    drawingView.invalidate()
                }
                .setNegativeButton("Cancel") { _, _ -> drawingView.removeDimensionItem(dim) }
                .show()
            lenInput.postDelayed({ val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager; imm.showSoftInput(lenInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }, 200)
            return
        }

        if (dim.mode == DimMode.AUTO) {
            // Subsequent auto dimensions — label computed automatically, no dialog needed
            dim.label = dim.displayLabel(drawingView.autoRefPixelLen)
            drawingView.invalidate(); return
        }

        // MANUAL mode — show text input with cursor
        layout.addView(TextView(this).apply { text = "Enter label for this dimension:"; textSize = 14f; setPadding(0,0,0,dp(8)) })
        val labelInput = android.widget.EditText(this).apply {
            hint = "e.g. 3.5m or 45°"; setText(dim.label); requestFocus()
            selectAll()
        }
        layout.addView(labelInput)
        AlertDialog.Builder(this).setTitle("Dimension Label").setView(layout)
            .setPositiveButton("OK") { _, _ -> dim.label = labelInput.text.toString().trim(); drawingView.invalidate() }
            .setNegativeButton("Cancel") { _, _ -> drawingView.removeDimensionItem(dim) }
            .show()
        labelInput.postDelayed({ val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager; imm.showSoftInput(labelInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }, 200)
    }

    private fun showDimensionStylePanel(dim: DimensionItem) {
        dismissAllFloatingPanels()
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); elevation = dp(10).toFloat(); setPadding(dp(16),dp(12),dp(16),dp(20)) }

        fun lbl(t: String) = TextView(this).apply { text=t; textSize=12f; setTextColor(Color.parseColor("#8A8580")); setPadding(0,dp(10),0,dp(4)) }
        fun seekRow(label: String, max: Int, current: Int, onChange: (Int)->Unit) {
            val lbl2 = lbl(label); panel.addView(lbl2)
            panel.addView(SeekBar(this).apply {
                this.max=max; progress=current
                progressTintList=android.content.res.ColorStateList.valueOf(Color.parseColor("#1565C0"))
                thumbTintList=android.content.res.ColorStateList.valueOf(Color.parseColor("#1565C0"))
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(s:SeekBar?,v:Int,f:Boolean) {
                        onChange(v); drawingView.invalidate()
                        lbl2.text = label.substringBefore(":") + ": $v"
                    }
                    override fun onStartTrackingTouch(s:SeekBar?){}
                    override fun onStopTrackingTouch(s:SeekBar?){}
                })
            })
        }

        // Title row
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply { text = "Dimension Style"; textSize = 17f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(Color.parseColor("#1C1C1E")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        val scrollRef = arrayOfNulls<ScrollView>(1)
        titleRow.addView(TextView(this).apply { text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#8A8580")); gravity = Gravity.CENTER; val lp2 = LinearLayout.LayoutParams(dp(36),dp(36)); layoutParams=lp2; setOnClickListener { scrollRef[0]?.let { canvasContainer.removeView(it) }; drawingView.selectedItem=null; drawingView.invalidate() } })
        panel.addView(titleRow)

        // Label edit
        panel.addView(lbl("Label (blank = auto)"))
        val labelEdit = android.widget.EditText(this).apply { setText(dim.label); hint="Auto" }
        panel.addView(labelEdit)

        // Line color
        panel.addView(lbl("Line & Arrow Color"))
        val lineColorRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val dimColors = listOf(Color.parseColor("#1565C0"),Color.BLACK,Color.RED,Color.parseColor("#388E3C"),Color.parseColor("#F57C00"),Color.parseColor("#7B1FA2"),Color.WHITE)
        dimColors.forEach { c -> lineColorRow.addView(View(this).apply { val lp2=LinearLayout.LayoutParams(dp(30),dp(30));lp2.setMargins(0,0,dp(8),0);layoutParams=lp2; background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(c);setStroke(if(c==dim.color)dp(3) else dp(1),Color.parseColor("#333333"))}; setOnClickListener{dim.color=c;drawingView.invalidate()} }) }
        panel.addView(lineColorRow)

        // Text color
        panel.addView(lbl("Text Color"))
        val textColorRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        dimColors.forEach { c -> textColorRow.addView(View(this).apply { val lp2=LinearLayout.LayoutParams(dp(30),dp(30));lp2.setMargins(0,0,dp(8),0);layoutParams=lp2; background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(c);setStroke(if(c==dim.textColor)dp(3) else dp(1),Color.parseColor("#333333"))}; setOnClickListener{dim.textColor=c;drawingView.invalidate()} }) }
        panel.addView(textColorRow)

        // For angular dims: flip supplementary + arc radius
        if (dim.isAngular) {
            panel.addView(TextView(this).apply { text="↔ Flip to supplementary angle"; textSize=14f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(10),0,dp(4))
                setOnClickListener {
                    val parts = dim.unit.split(",").toMutableList()
                    val cur = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
                    while (parts.size < 3) parts.add("false")
                    parts[2] = (!cur).toString()
                    dim.unit = parts.joinToString(",")
                    drawingView.invalidate()
                }
            })
            seekRow("Arc Radius", 200, dim.offset.toInt().coerceIn(0,200)) { dim.offset = it.toFloat() }
        }
        seekRow("Arrow Size: ${dim.arrowSize.toInt()}", 40, dim.arrowSize.toInt().coerceIn(4,40)) { dim.arrowSize=it.coerceAtLeast(4).toFloat() }
        seekRow("Line Thickness: ${dim.strokeW.toInt()}", 12, dim.strokeW.toInt().coerceIn(1,12)) { dim.strokeW=it.coerceAtLeast(1).toFloat() }

        // Delete
        panel.addView(TextView(this).apply { text="🗑 Delete dimension"; textSize=14f; setTextColor(Color.RED); setPadding(0,dp(12),0,0); setOnClickListener { drawingView.removeDimensionItem(dim); scrollRef[0]?.let { canvasContainer.removeView(it) } } })

        // Done
        panel.addView(TextView(this).apply { text="✓ Done"; textSize=15f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(16),dp(12),dp(16),dp(12))
            background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.parseColor("#1565C0"));cornerRadius=dp(12).toFloat()}
            val lp2=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp2.setMargins(0,dp(12),0,0);layoutParams=lp2
            setOnClickListener { dim.label=labelEdit.text.toString().trim(); drawingView.selectedItem=null; drawingView.invalidate(); scrollRef[0]?.let{canvasContainer.removeView(it)} } })

        val scroll = ScrollView(this).apply { addView(panel) }
        scrollRef[0] = scroll
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        canvasContainer.addView(scroll, lp)
    }

    // customHatchDir, listCustomHatches, addCustomHatchFromUri, applyCustomHatch, startHatchSnip,
    // and showHatchPicker have moved to HatchExtensions.kt (as extension functions on
    // MainActivity) — part of splitting this file into smaller, topic-focused pieces. Nothing
    // about their behavior changed; they're called exactly the same way from everywhere else in
    // this file, since Kotlin resolves an extension function through an implicit receiver the
    // same way it resolves a member function.

    // showOcrSourceDialog moved to OcrExtensions.kt.

    private fun showSettingsDialog() {
        val prefs = getPrefs()
        val accent = Color.parseColor("#7B61FF")
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(14), dp(22), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(20).toFloat() }
        }
        // Section header: was 11f with no visual weight — genuinely easy to miss scrolling past.
        // Bumped up, bolder, small colored accent bar on the left for a bit of "premium" polish
        // instead of just being plain gray caps text.
        fun hdr(t: String) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(18), 0, dp(6)) }
            row.addView(View(this).apply { setBackgroundColor(accent); layoutParams = LinearLayout.LayoutParams(dp(4), dp(16)).also { it.setMargins(0, 0, dp(8), 0) } })
            row.addView(TextView(this).apply { text = t; textSize = 15f; setTextColor(Color.parseColor("#2A2A2A")); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.02f })
            container.addView(row)
        }
        // Much lighter than before, and given real breathing room on both sides — a heavy full-
        // width rule between every single section is exactly what made this feel cramped/busy.
        fun div() { container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).also { it.setMargins(0, dp(14), 0, 0) }; setBackgroundColor(Color.parseColor("#EDEAE4")) }) }

        div(); hdr("LOCK BEHAVIOUR")
        val lockEraseCb = CheckBox(this).apply { text="Locked items: prevent erasing"; isChecked=prefs.getBoolean("lock_prevent_erase",true); setOnCheckedChangeListener { _,on -> prefs.edit().putBoolean("lock_prevent_erase",on).apply() } }; container.addView(lockEraseCb)
        val lockMoveCb = CheckBox(this).apply { text="Locked items: prevent moving/resizing"; isChecked=prefs.getBoolean("lock_prevent_move",true); setOnCheckedChangeListener { _,on -> prefs.edit().putBoolean("lock_prevent_move",on).apply() } }; container.addView(lockMoveCb)
        val lockColorCb = CheckBox(this).apply { text="Locked items: prevent colour changes"; isChecked=prefs.getBoolean("lock_prevent_color",true); setOnCheckedChangeListener { _,on -> prefs.edit().putBoolean("lock_prevent_color",on).apply() } }; container.addView(lockColorCb)

        div(); hdr("GENERAL")
        val confirmCb = CheckBox(this).apply{ text="Confirm before exit or clear canvas"; isChecked=prefs.getBoolean("confirm_exit_clear",true) }; container.addView(confirmCb)
        val autosaveCb = CheckBox(this).apply{ text="Autosave every 10 seconds"; isChecked=prefs.getBoolean("autosave",true) }; container.addView(autosaveCb)
        val layersBtnCb = CheckBox(this).apply {
            text = "Always show Layers button in toolbar"
            isChecked = prefs.getBoolean("layers_button_always_visible", false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("layers_button_always_visible", on).apply(); updateLayersButtonVisibility() }
        }; container.addView(layersBtnCb)
        container.addView(TextView(this).apply {
            text = "When off, Layers is still reachable from the ⋮ menu."
            textSize = 11f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0, dp(2), 0, dp(4))
        })

        div(); hdr("APPEARANCE")
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }
        val themeButtons = mutableListOf<TextView>()
        for ((key, label) in listOf("ORIGINAL" to "Original", "TRANSLUCENT" to "Transparent", "GLASS" to "Glass")) {
            val b = TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                setOnClickListener {
                    setAppTheme(key)
                    themeButtons.forEach { tb -> tb.setBackgroundColor(Color.parseColor("#F0EBE0")); tb.setTextColor(Color.parseColor("#4A4A4A")) }
                    setBackgroundColor(Color.parseColor("#7B61FF")); setTextColor(Color.WHITE)
                }
            }
            if (key == currentAppTheme()) { b.setBackgroundColor(Color.parseColor("#7B61FF")); b.setTextColor(Color.WHITE) }
            else { b.setBackgroundColor(Color.parseColor("#F0EBE0")); b.setTextColor(Color.parseColor("#4A4A4A")) }
            themeButtons.add(b); themeRow.addView(b)
        }
        container.addView(themeRow)
        container.addView(TextView(this).apply {
            text = "Glass gives a real frosted-blur toolbar on Android 12+; older versions fall back to a translucent look."
            textSize = 11f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0, dp(4), 0, 0)
        })

        div(); hdr("INPUT")
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }
        val inputButtons = mutableListOf<TextView>()
        for ((label, mode) in listOf("Auto" to InputMode.AUTO, "Stylus only" to InputMode.STYLUS_ONLY, "Finger only" to InputMode.FINGER_ONLY)) {
            val b = TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(10))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                setOnClickListener {
                    drawingView.inputMode = mode
                    prefs.edit().putString("input_mode", mode.name).apply()
                    inputButtons.forEach { tb -> tb.setBackgroundColor(Color.parseColor("#F0EBE0")); tb.setTextColor(Color.parseColor("#4A4A4A")) }
                    setBackgroundColor(Color.parseColor("#7B61FF")); setTextColor(Color.WHITE)
                }
            }
            if (mode == drawingView.inputMode) { b.setBackgroundColor(Color.parseColor("#7B61FF")); b.setTextColor(Color.WHITE) }
            else { b.setBackgroundColor(Color.parseColor("#F0EBE0")); b.setTextColor(Color.parseColor("#4A4A4A")) }
            inputButtons.add(b); inputRow.addView(b)
        }
        container.addView(inputRow)
        container.addView(TextView(this).apply {
            text = "Auto allows both; palm gets rejected only while the stylus is actively down."
            textSize = 11f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0, dp(4), 0, 0)
        })

        div(); hdr("PAPER")
        val paperLabels = arrayOf("Blank","Lined","Graph Grid","Dot Grid","Engineering","Coloured")
        val paperValues = arrayOf("BLANK","LINED","GRID","DOTS","ENGINEERING","BLANK_COLORED")
        var selPaper = prefs.getString("default_paper","LINED") ?: "LINED"
        var selPaperColor = prefs.getInt("paper_color", drawingView.paperColor)
        val paperLbl = TextView(this).apply{ textSize=15f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(8),0,dp(8)) }
        fun refP(){ paperLbl.text = "Default: ${paperLabels[paperValues.indexOf(selPaper).coerceAtLeast(0)]}  (tap)" }
        refP(); container.addView(paperLbl)

        // Colour swatch + label — only relevant (and only shown) once "Coloured" is picked.
        // Previously choosing "Coloured" just applied a single fixed default colour with no
        // way to change it; this lets the person actually pick which colour.
        val paperColorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
        val paperColorSwatch = View(this).apply {
            val p = LinearLayout.LayoutParams(dp(28), dp(28)); p.setMargins(0, 0, dp(10), 0); layoutParams = p
            background = android.graphics.drawable.GradientDrawable().apply { setColor(selPaperColor); cornerRadius = dp(6).toFloat(); setStroke(dp(1), Color.parseColor("#D8D2C4")) }
        }
        val paperColorLbl = TextView(this).apply { text = "Page colour  (tap swatch to change)"; textSize = 14f; setTextColor(Color.parseColor("#4A4A4A")) }
        fun refPaperColorRow() {
            (paperColorSwatch.background as? android.graphics.drawable.GradientDrawable)?.setColor(selPaperColor)
            paperColorRow.visibility = if (selPaper == "BLANK_COLORED") View.VISIBLE else View.GONE
        }
        paperColorSwatch.setOnClickListener { showColorGridDialog { c -> selPaperColor = c; refPaperColorRow() } }
        paperColorRow.addView(paperColorSwatch); paperColorRow.addView(paperColorLbl)
        refPaperColorRow()
        container.addView(paperColorRow)

        paperLbl.setOnClickListener{
            AlertDialog.Builder(this).setTitle("Default Paper").setItems(paperLabels){ _,i->
                selPaper=paperValues[i]; refP(); refPaperColorRow()
                // Picking "Coloured" with nothing chosen yet should immediately prompt for a
                // colour rather than silently falling back to whatever the old fixed default was.
                if (selPaper == "BLANK_COLORED") showColorGridDialog { c -> selPaperColor = c; refPaperColorRow() }
            }.show()
        }

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

        div(); hdr("HATCHING")
        // hatchScale is a spacing MULTIPLIER (see `s = 8f * hatchScale` in drawHatchPattern),
        // so smaller = tighter spacing = more lines drawn per area = more expensive to
        // first-render (after that it's cached — see drawHatchPattern's caching fix — but the
        // one-time render cost for a large area with tight spacing is still real).
        val hatchLabels = arrayOf("Extra Fine", "Fine", "Normal", "Medium", "Coarse", "Extra Coarse", "Broad", "Ultra Broad")
        val hatchValues = arrayOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 5f, 8f)
        var selHatchScale = prefs.getFloat("hatch_scale", 1f)
        val hatchLbl = TextView(this).apply { textSize=15f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(8),0,dp(8)) }
        fun closestHatchIndex() = hatchValues.indices.minByOrNull { kotlin.math.abs(hatchValues[it] - selHatchScale) } ?: 1
        fun refHatch() { hatchLbl.text = "Density: ${hatchLabels[closestHatchIndex()]}  (tap)" }
        refHatch()
        hatchLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Hatch Density").setItems(hatchLabels) { _, i ->
                val applyChoice = { selHatchScale = hatchValues[i]; refHatch() }
                if (hatchValues[i] < 1f) {
                    // Fine hatches on a large filled area mean a lot of lines get drawn the
                    // first time that fill is rendered — warn before committing to it as the
                    // default rather than let it be a surprise on a big table/hatch later.
                    AlertDialog.Builder(this).setTitle("Fine Hatching")
                        .setMessage("Fine, densely-spaced hatches take longer to render the first time a large filled area is drawn. Once drawn, it's cached and stays fast — but the initial render on a big area may be noticeably slower. Use Fine hatching?")
                        .setPositiveButton("Use Fine") { _, _ -> applyChoice() }
                        .setNegativeButton("Cancel", null).show()
                } else applyChoice()
            }.show()
        }
        container.addView(hatchLbl)

        div(); hdr("AI ASSISTANT")
        container.addView(TextView(this).apply {
            text = "Bring your own free Gemini API key — nothing is shared between students, nothing costs you anything."
            textSize = 12f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0,0,0,dp(6))
        })
        val keyLbl = TextView(this).apply { textSize=15f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(6),0,dp(4)) }
        fun refKeyLbl() { keyLbl.text = if (geminiApiKey().isBlank()) "API Key: not set  (tap)" else "API Key: •••• saved  (tap to change)" }
        refKeyLbl()
        keyLbl.setOnClickListener { showGeminiSetupDialog { refKeyLbl() } }
        container.addView(keyLbl)
        val modelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(4),0,dp(4)) }
        modelRow.addView(TextView(this).apply { text = "Model:"; textSize=14f; setTextColor(Color.parseColor("#4A4A4A")) })
        val modelInput = EditText(this).apply { setText(geminiModel()); textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        modelRow.addView(modelInput)
        container.addView(modelRow)
        container.addView(TextView(this).apply {
            text = "If \"Ask Gemini\" stops working after a Google update, this is usually why — check aistudio.google.com/models for the current free model name and paste it above."
            textSize = 11f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0,0,0,dp(4))
        })
        val updateLbl = TextView(this).apply { text = "Check for Update"; textSize=15f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(6),0,dp(4)) }
        updateLbl.setOnClickListener {
            updateLbl.text = "Checking..."
            refreshGeminiConfigFromRemote { success, message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                updateLbl.text = "Check for Update"
                if (success) modelInput.setText(geminiModel())
            }
        }
        container.addView(updateLbl)
        container.addView(TextView(this).apply {
            text = "Pulls the current known-good settings from EngiNotes' GitHub repo — safe to tap anytime, does nothing if everything's already up to date."
            textSize = 11f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0,0,0,dp(4))
        })

        div(); hdr("TOOLBAR")
        val barSizeLabels = arrayOf("Small (36dp)", "Medium (40dp)", "Large (44dp)", "Extra Large (52dp)")
        val barSizeValues = arrayOf(36, 40, 44, 52)
        var selBarSize = prefs.getInt("bar_icon_size", 44)
        val barSizeLbl = TextView(this).apply { textSize=15f; setTextColor(Color.parseColor("#1565C0")); setPadding(0,dp(8),0,dp(8)) }
        fun refBarSize() { barSizeLbl.text = "Icon size: ${barSizeLabels[barSizeValues.indexOf(selBarSize).coerceAtLeast(0)]}  (tap)" }
        refBarSize()
        barSizeLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Icon Size").setItems(barSizeLabels) { _, i ->
                selBarSize = barSizeValues[i]; refBarSize()
            }.show()
        }
        container.addView(barSizeLbl)

        div(); hdr("DIMENSION")
        var dimFontSz = prefs.getFloat("dim_font_size", 11f)
        var dimArrowSz = prefs.getFloat("dim_arrow_size", 9f)
        container.addView(TextView(this).apply { text = "Default Font Size: ${dimFontSz.toInt()}sp"; textSize=13f; setTextColor(Color.parseColor("#1C1C1E")); setPadding(0,dp(4),0,dp(4)) }.also { lbl ->
            container.addView(SeekBar(this).apply { max=40; progress=dimFontSz.toInt().coerceIn(6,40)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(s:SeekBar?,v:Int,f:Boolean){if(f){dimFontSz=v.coerceAtLeast(6).toFloat();lbl.text="Default Font Size: ${dimFontSz.toInt()}sp"}}; override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} }) })
        })
        container.addView(TextView(this).apply { text = "Default Arrow Size: ${dimArrowSz.toInt()}"; textSize=13f; setTextColor(Color.parseColor("#1C1C1E")); setPadding(0,dp(4),0,dp(4)) }.also { lbl ->
            container.addView(SeekBar(this).apply { max=40; progress=dimArrowSz.toInt().coerceIn(4,40)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(s:SeekBar?,v:Int,f:Boolean){if(f){dimArrowSz=v.coerceAtLeast(4).toFloat();lbl.text="Default Arrow Size: ${dimArrowSz.toInt()}"}}; override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} }) })
        })

        // Consistent, more readable styling applied to every checkbox at once here, rather than
        // editing each of the 7 individual checkbox creation sites above (lower risk of missing
        // one or ending up with inconsistent styling between them).
        fun styleCheckboxesIn(vg: android.view.ViewGroup) {
            for (idx in 0 until vg.childCount) {
                val child = vg.getChildAt(idx)
                if (child is CheckBox) {
                    child.textSize = 14f; child.setTextColor(Color.parseColor("#2A2A2A"))
                    child.buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                    child.setPadding(dp(6), dp(4), 0, dp(4))
                } else if (child is android.view.ViewGroup) styleCheckboxesIn(child)
            }
        }
        styleCheckboxesIn(container)

        val scroll = ScrollView(this).apply{ addView(container) }
        val titleView = TextView(this).apply {
            text = "Settings"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1C1C1E")); setPadding(dp(24), dp(20), dp(24), dp(8))
        }
        AlertDialog.Builder(this).setCustomTitle(titleView).setView(scroll)
            .setPositiveButton("Done") { _,_ ->
                prefs.edit()
                    .putBoolean("confirm_exit_clear",confirmCb.isChecked)
                    .putBoolean("autosave",autosaveCb.isChecked)
                    .putString("default_paper",selPaper)
                    .putInt("paper_color", selPaperColor)
                    .putFloat("hatch_scale", selHatchScale)
                    .putString("gemini_model", modelInput.text.toString().trim().ifBlank { "gemini-flash-latest" })
                    .putInt("arc_divisions",(arcInput.text.toString().toIntOrNull()?:3).coerceIn(2,12))
                    .putInt("bar_icon_size", selBarSize)
                    .putFloat("dim_font_size", dimFontSz)
                    .putFloat("dim_arrow_size", dimArrowSz)
                    .apply()
                drawingView.arcDivisions = prefs.getInt("arc_divisions",3)
                drawingView.defaultDimFontSize = dimFontSz
                drawingView.defaultDimArrowSize = dimArrowSz
                drawingView.pendingHatchScale = selHatchScale
                try { drawingView.paperType = PaperType.valueOf(selPaper) } catch(e:Exception){}
                drawingView.paperColor = selPaperColor
                drawingView.invalidate()
                val sz = dp(selBarSize)
                val primaryBar = findViewById<HorizontalScrollView?>(R.id.primaryToolbarScroll)
                (primaryBar?.getChildAt(0) as? LinearLayout)?.let { ll ->
                    for (i in 0 until ll.childCount) {
                        val child = ll.getChildAt(i) as? ImageButton ?: continue
                        val lp = child.layoutParams as LinearLayout.LayoutParams
                        lp.width = sz; lp.height = sz; child.layoutParams = lp
                        // ImageButton defaults to ScaleType.CENTER — draws the icon at its own
                        // fixed intrinsic size and just re-centers it, ignoring the view's actual
                        // bounds entirely. That's the real reason resizing the button never
                        // visibly resized the icon. FIT_CENTER scales the icon to genuinely fill
                        // the available space (minus padding), preserving aspect ratio.
                        child.scaleType = ImageView.ScaleType.FIT_CENTER
                        val pad = dp((selBarSize * 0.22f).toInt())
                        child.setPadding(pad, pad, pad, pad)
                    }
                }
                rebuildContextBar()
                repositionContextBar()
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
    private var shapeOptionsPanel: View? = null
    private var snapOptionsPanel: View? = null
    private var snapOptionsButton: View? = null
    private var polylineBar: View? = null

    private fun updatePolylineBar() {
        if (drawingView.currentTool != Tool.POLYLINE || drawingView.polylinePoints.isEmpty()) {
            polylineBar?.let { canvasContainer.removeView(it) }; polylineBar = null; return
        }
        if (polylineBar != null) return
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat(); setPadding(dp(8),dp(6),dp(8),dp(6))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(20).toFloat(); setStroke(1, Color.parseColor("#E0E0E0")) }
        }
        bar.addView(TextView(this).apply {
            text = "Close"; setTextColor(Color.parseColor("#2196F3")); textSize = 14f; setPadding(dp(12),dp(6),dp(12),dp(6))
            setOnClickListener { drawingView.finalizePolyline(true); updatePolylineBar() }
        })
        bar.addView(TextView(this).apply {
            text = "Done"; setTextColor(Color.parseColor("#34C759")); textSize = 14f; setPadding(dp(12),dp(6),dp(12),dp(6))
            setOnClickListener { drawingView.finalizePolyline(false); updatePolylineBar() }
        })
        bar.addView(TextView(this).apply {
            text = "Undo"; setTextColor(Color.parseColor("#FF9800")); textSize = 14f; setPadding(dp(12),dp(6),dp(12),dp(6))
            setOnClickListener { drawingView.undoLastPolylineVertex(); if (drawingView.polylinePoints.isEmpty()) updatePolylineBar() }
        })
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(8)
        canvasContainer.addView(bar, lp); polylineBar = bar
    }
    private var dimModeEnabled: Boolean = false
    private var dimButton: View? = null
    private var dimOverlayViews: List<View> = emptyList()
    private var dimScalePanel: View? = null
    private var groupModeToggleBtn: TextView? = null
    private var tableButton: TextView? = null

    private fun updateTableButton(table: TableItem?) {
        if (table == null) {
            tableButton?.let { canvasContainer.removeView(it) }; tableButton = null; return
        }
        if (tableButton != null) return
        val btn = TextView(this).apply {
            text = "\u2637 Table"; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#5D4037")); cornerRadius = dp(16).toFloat()
            }
            elevation = dp(10).toFloat()
            setOnClickListener { drawingView.getActiveTable()?.let { showTablePropertiesDialog(it) } }
        }
        // Sits in the bottom toolbar area, above the Select/Lasso/Rect/Multi pill and the tool
        // icon row beneath it — this is the "bottom upper navigation bar" spot.
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
        lp.bottomMargin = dp(180); lp.leftMargin = dp(8)
        canvasContainer.addView(btn, lp)
        tableButton = btn
    }

    private fun updateGroupModeToggle(show: Boolean) {
        if (!show) {
            groupModeToggleBtn?.let { canvasContainer.removeView(it) }; groupModeToggleBtn = null; return
        }
        val existing = groupModeToggleBtn
        if (existing != null) {
            existing.text = if (drawingView.multiSelectIndividual) "⚙ Indiv" else "⚙ Group"
            existing.setTextColor(Color.WHITE)
            return
        }
        val btn = TextView(this).apply {
            text = if (drawingView.multiSelectIndividual) "⚙ Indiv" else "⚙ Group"
            textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#9C27B0")); cornerRadius = dp(16).toFloat()
            }
            elevation = dp(10).toFloat()
            setOnClickListener {
                drawingView.multiSelectIndividual = !drawingView.multiSelectIndividual
                text = if (drawingView.multiSelectIndividual) "⚙ Indiv" else "⚙ Group"
                drawingView.invalidate()
            }
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        lp.topMargin = dp(56); lp.leftMargin = dp(8)
        canvasContainer.addView(btn, lp)
        groupModeToggleBtn = btn
    }
    private var eraserOptionsPanel: LinearLayout? = null
    private var contextBarPage = 0 // 0 = default, increments on swipe-up

    private var textOptionsPanel: View? = null
    private fun dismissTextOptionsPanel() {
        val p = textOptionsPanel ?: return
        textOptionsPanel = null
        animatePanelOut(p) { canvasContainer.removeView(p) }
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE
    }

    private fun showTextOptionsPanel() {
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
        if (textOptionsPanel != null) { dismissTextOptionsPanel(); return }
        dismissAllFloatingPanels()
        setActiveTool(findViewById(R.id.btnText), Tool.TEXT)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        // Title + close
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply { text = "Text"; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(Color.parseColor("#1C1C1E")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        val closeBtn = TextView(this).apply { text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#8A8580")); gravity = Gravity.CENTER; val lp = LinearLayout.LayoutParams(dp(36), dp(36)); layoutParams = lp; setOnClickListener { dismissTextOptionsPanel() } }
        titleRow.addView(closeBtn); panel.addView(titleRow)

        // Font family — scrollable chip row + "All Fonts" button
        val fontHeaderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fontHeaderRow.addView(TextView(this).apply {
            text = "Font"; textSize = 13f; setTextColor(Color.parseColor("#8A8580"))
            setPadding(0, dp(12), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        fontHeaderRow.addView(TextView(this).apply {
            text = "All Fonts ›"; textSize = 13f; setTextColor(Color.parseColor("#1565C0"))
            setPadding(0, dp(12), 0, dp(6))
            setOnClickListener {
                // Use a dummy EditText to satisfy signature; changes go through pendingFontFamily
                val dummy = android.widget.EditText(this@MainActivity)
                dummy.typeface = typefaceFromFamily(pendingFontFamily)
                showFontPickerDialog(dummy)
            }
        })
        panel.addView(fontHeaderRow)

        val fontScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val fontRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
        fontScroll.addView(fontRow)

        fun updateFontChips(row: LinearLayout) {
            row.removeAllViews()
            availableFonts.forEach { (lbl, fam) ->
                row.addView(TextView(this).apply {
                    text = lbl; textSize = 13f; gravity = Gravity.CENTER
                    typeface = try { android.graphics.Typeface.create(fam, android.graphics.Typeface.NORMAL) } catch (e: Exception) { android.graphics.Typeface.DEFAULT }
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, dp(8), 0); layoutParams = lp
                    val isSelected = pendingFontFamily == fam
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(if (isSelected) Color.parseColor("#6D4C41") else Color.parseColor("#F0EBE0")); cornerRadius = dp(8).toFloat() }
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#4A4A4A"))
                    setOnClickListener {
                        pendingFontFamily = fam
                        recentFonts.remove(fam); recentFonts.add(0, fam)
                        updateFontChips(row); rebuildContextBar()
                    }
                })
            }
        }
        updateFontChips(fontRow)
        panel.addView(fontScroll)

        // Size: numeric input matching MS Word's point-size convention, not a slider —
        // direct entry lets the user type any exact size (including sizing down further than a
        // slider's granularity would comfortably allow), with -/+ steppers for quick adjustment.
        panel.addView(TextView(this).apply { text = "Size (pt)"; textSize = 13f; setTextColor(Color.parseColor("#8A8580")); setPadding(0, dp(12), 0, dp(4)) })
        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sizeInput = android.widget.EditText(this).apply {
            setText(editSize.toInt().toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun applySizeInput() {
            val v = sizeInput.text.toString().toIntOrNull()?.coerceIn(1, 400) ?: editSize.toInt()
            editSize = v.toFloat(); sizeInput.setText(v.toString()); rebuildContextBar()
        }
        sizeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { applySizeInput(); true } else false
        }
        sizeInput.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus -> if (!hasFocus) applySizeInput() }
        val minusBtn = TextView(this).apply {
            text = "−"; textSize = 20f; gravity = Gravity.CENTER; setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { editSize = (editSize - 1f).coerceAtLeast(1f); sizeInput.setText(editSize.toInt().toString()); rebuildContextBar() }
        }
        val plusBtn = TextView(this).apply {
            text = "+"; textSize = 20f; gravity = Gravity.CENTER; setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { editSize = (editSize + 1f).coerceAtMost(400f); sizeInput.setText(editSize.toInt().toString()); rebuildContextBar() }
        }
        sizeRow.addView(minusBtn); sizeRow.addView(sizeInput); sizeRow.addView(plusBtn)
        panel.addView(sizeRow)

        // Opacity slider
        panel.addView(TextView(this).apply { text = "Opacity: ${editOpacity * 100 / 255}%"; textSize = 13f; setTextColor(Color.parseColor("#8A8580")); setPadding(0, dp(10), 0, dp(4)) })
        panel.addView(SeekBar(this).apply {
            max = 255; progress = editOpacity
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#26A69A"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#26A69A"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { if(f) { editOpacity = v; rebuildContextBar() } }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // Color row
        panel.addView(TextView(this).apply { text = "Color"; textSize = 13f; setTextColor(Color.parseColor("#8A8580")); setPadding(0, dp(10), 0, dp(6)) })
        val colorRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickCols = listOf(Color.parseColor("#1C1C1E"), Color.parseColor("#FF3B30"), Color.parseColor("#007AFF"), Color.parseColor("#34C759"), Color.parseColor("#FF9500"), Color.parseColor("#5856D6"), Color.parseColor("#AF52DE"), Color.WHITE)
        quickCols.forEach { c ->
            colorRow2.addView(View(this).apply {
                val lp = LinearLayout.LayoutParams(dp(32), dp(32)); lp.setMargins(0, 0, dp(8), 0); layoutParams = lp
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(if (c == editColor) dp(3) else dp(1), if (c == editColor) Color.parseColor("#1C1C1E") else Color.parseColor("#C8C4BE")) }
                setOnClickListener { editColor = c; activeEditText?.setTextColor(c); dismissTextOptionsPanel(); showTextOptionsPanel() }
            })
        }
        panel.addView(colorRow2)

        val scroll = ScrollView(this)
        scroll.addView(panel)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        canvasContainer.addView(scroll, lp)
        textOptionsPanel = scroll
        animatePanelIn(scroll)
    }

    private fun dismissPenOptionsPanel() {
        val p = penOptionsPanel ?: return
        penOptionsPanel = null
        animatePanelOut(p) { canvasContainer.removeView(p) }
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE
    }
    private fun dismissShapeOptionsPanel() {
        val p = shapeOptionsPanel ?: return
        shapeOptionsPanel = null
        animatePanelOut(p) { canvasContainer.removeView(p) }
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE
    }
    private fun dismissSnapOptionsPanel() {
        val p = snapOptionsPanel ?: return
        snapOptionsPanel = null
        animatePanelOut(p) { canvasContainer.removeView(p) }
    }

    // Shows/hides the floating "Snap ⚙" button above the bottom toolbar based on snapEnabled
    private fun updateSnapOptionsButton() {
        if (drawingView.snapEnabled) {
            if (snapOptionsButton != null) return  // already showing
            val btn = TextView(this).apply {
                text = "Snap ⚙"; textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2196F3"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#2196F3")); cornerRadius = dp(16).toFloat()
                }
                elevation = dp(6).toFloat()
                setOnClickListener { if (snapOptionsPanel != null) dismissSnapOptionsPanel() else showSnapOptionsPanel() }
            }
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            lp.bottomMargin = dp(8); lp.leftMargin = dp(12)
            canvasContainer.addView(btn, lp)
            snapOptionsButton = btn
        } else {
            snapOptionsButton?.let { canvasContainer.removeView(it) }; snapOptionsButton = null
            dismissSnapOptionsPanel()
        }
    }

    private fun showSnapOptionsPanel() {
        dismissSnapOptionsPanel()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(10).toFloat()
                setStroke(1, Color.parseColor("#E0E0E0"))
            }
        }

        data class SnapOption(val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit)
        val options = listOf(
            SnapOption("☑ Endpoint",      { drawingView.snapEndpoint },      { drawingView.snapEndpoint = it }),
            SnapOption("☑ Midpoint",      { drawingView.snapMidpoint },      { drawingView.snapMidpoint = it }),
            SnapOption("☑ Intersection",  { drawingView.snapIntersection },  { drawingView.snapIntersection = it }),
            SnapOption("☑ Center",        { drawingView.snapCenter },        { drawingView.snapCenter = it }),
            SnapOption("☑ Nearest",       { drawingView.snapNearest },       { drawingView.snapNearest = it }),
            SnapOption("☑ Perpendicular", { drawingView.snapPerpendicular }, { drawingView.snapPerpendicular = it }),
            SnapOption("☑ Tangent",       { drawingView.snapTangent },      { drawingView.snapTangent = it }),
            SnapOption("☑ Parallel",      { drawingView.snapParallel },      { drawingView.snapParallel = it }),
            SnapOption("☑ Grid",          { drawingView.snapGrid },          { drawingView.snapGrid = it }),
            SnapOption("⚡ Auto-connect",  { drawingView.snapAutoConnect },   { drawingView.snapAutoConnect = it }),
        )

        for (opt in options) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            val lbl = TextView(this).apply { text = opt.label.replace("☑ ", "").replace("⚡ ", ""); textSize = 13f; setTextColor(Color.parseColor("#2A2A2A")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val cb = android.widget.CheckBox(this).apply { isChecked = opt.get(); setOnCheckedChangeListener { _, v -> opt.set(v) } }
            row.addView(lbl); row.addView(cb); panel.addView(row)
        }

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
        lp.bottomMargin = dp(50); lp.leftMargin = dp(12)
        canvasContainer.addView(panel, lp)
        snapOptionsPanel = panel
        animatePanelIn(panel)
    }
    private fun dismissEraserOptionsPanel() { val p = eraserOptionsPanel ?: return; eraserOptionsPanel = null; animatePanelOut(p) { canvasContainer.removeView(p) }; findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE }

    // Adds a "Line Type" section into an existing panel LinearLayout.
    // onSelect is called whenever the user picks a type — caller updates drawingView.currentLineType.
    private fun addLineTypeSection(panel: LinearLayout, sectionLabel: (String) -> Unit, onSelect: (LineType) -> Unit) {
        sectionLabel("Line Type")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lineTypes = LineType.values()
        val btnRefs = mutableListOf<View>()

        // Render each type as a horizontal row: preview canvas on left, name on right
        for (lt in lineTypes) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (lt == drawingView.currentLineType) Color.parseColor("#EDE0D4") else Color.TRANSPARENT)
                    cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    onSelect(lt)
                    btnRefs.forEach { v ->
                        (v as LinearLayout).background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.TRANSPARENT); cornerRadius = dp(6).toFloat()
                        }
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor("#EDE0D4")); cornerRadius = dp(6).toFloat()
                    }
                }
            }
            btnRefs.add(row)

            // Mini canvas preview of the line type
            val preview = object : android.view.View(this) {
                override fun onDraw(c: android.graphics.Canvas) {
                    val p = android.graphics.Paint().apply {
                        color = Color.parseColor("#2A2A2A")
                        strokeWidth = dp(2).toFloat()
                        style = android.graphics.Paint.Style.STROKE
                        isAntiAlias = true
                        if (lt.intervals != null) {
                            val scale = dp(2).toFloat() / 3f
                            val scaled = lt.intervals.map { it * scale }.toFloatArray()
                            pathEffect = android.graphics.DashPathEffect(scaled, 0f)
                        }
                        if (lt.cap != android.graphics.Paint.Cap.BUTT) strokeCap = lt.cap
                    }
                    val cy = height / 2f
                    c.drawLine(dp(4).toFloat(), cy, (width - dp(4)).toFloat(), cy, p)
                }
            }
            val previewLp = LinearLayout.LayoutParams(dp(80), dp(28))
            previewLp.setMargins(dp(4), 0, dp(8), 0)
            preview.layoutParams = previewLp

            val label = TextView(this).apply {
                text = lt.label; textSize = 12f
                setTextColor(Color.parseColor("#4A4A4A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(preview); row.addView(label)
            grid.addView(row)
        }
        panel.addView(grid)
    }

    private fun showShapeOptionsPanel() {
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
        if (shapeOptionsPanel != null) { dismissShapeOptionsPanel(); return }
        dismissAllFloatingPanels()

        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Shape Options"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "\u2715"; textSize = 18f; setTextColor(Color.parseColor("#888888")); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { dismissShapeOptionsPanel() }
        })
        panel.addView(header)

        fun sectionLabel(text: String) {
            panel.addView(TextView(this).apply {
                this.text = text; textSize = 13f
                setTextColor(Color.parseColor("#8D6E63")); setPadding(0, dp(14), 0, dp(6))
            })
        }

        // Stroke thickness
        sectionLabel("Thickness: ${drawingView.currentStrokeWidth.toInt()}")
        val thickLbl = panel.getChildAt(panel.childCount - 1) as TextView
        panel.addView(SeekBar(this).apply {
            max = 60; progress = drawingView.currentStrokeWidth.toInt().coerceIn(1, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { val vv = v.coerceAtLeast(1); drawingView.currentStrokeWidth = vv.toFloat(); thickLbl.text = "Thickness: $vv" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // Line type section
        addLineTypeSection(panel, { sectionLabel(it) }) { lt -> drawingView.currentLineType = lt; dismissShapeOptionsPanel() }

        // Arc settings (only relevant when Arc tool is active, but shown always for quick access)
        sectionLabel("Arc Control Points")
        val arcLbl = TextView(this).apply { text = "Points: ${drawingView.arcDivisions}"; textSize = 12f; setTextColor(Color.parseColor("#6A6A6A")) }
        panel.addView(arcLbl)
        panel.addView(SeekBar(this).apply {
            max = 19; progress = drawingView.arcDivisions - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) {
                    val pts = (v + 1).coerceIn(1, 20)
                    drawingView.arcDivisions = pts
                    arcLbl.text = "Points: $pts"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // Snap to endpoints toggle
        sectionLabel("Snap to Endpoints")
        val snapRowS = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val snapDescS = TextView(this).apply { text = "Lines snap to nearby endpoints"; textSize = 12f; setTextColor(Color.parseColor("#6A6A6A")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val snapSwitchS = android.widget.Switch(this).apply { isChecked = drawingView.snapEnabled; setOnCheckedChangeListener { _, on -> drawingView.snapEnabled = on; updateSnapOptionsButton() } }
        snapRowS.addView(snapDescS); snapRowS.addView(snapSwitchS); panel.addView(snapRowS)

        // Color row
        sectionLabel("Color")
        val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quickColors = listOf(Color.BLACK, Color.RED, Color.parseColor("#03A9F4"), Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"), Color.parseColor("#FF9800"), Color.parseColor("#9C27B0"), Color.parseColor("#1A237E"))
        for (c in quickColors) {
            val swatch = android.view.View(this).apply {
                val lp = LinearLayout.LayoutParams(dp(32), dp(32)); lp.setMargins(dp(3), 0, dp(3), 0); layoutParams = lp
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c) }
                setOnClickListener { drawingView.currentColor = c }
            }
            colorRow.addView(swatch)
        }
        colorScroll.addView(colorRow); panel.addView(colorScroll)

        scroll.addView(panel)
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = android.view.Gravity.BOTTOM
        canvasContainer.addView(scroll, lp)
        shapeOptionsPanel = scroll
        animatePanelIn(scroll)
    }

    // ── Dimension mode ─────────────────────────────────────────────────────
    private fun toggleDimMode() {
        dimModeEnabled = !dimModeEnabled
        updateDimButton()
        if (dimModeEnabled) showDimOverlayForSelected()
        else clearDimOverlay()
    }

    private fun updateDimButton() {
        val color = if (dimModeEnabled) Color.parseColor("#FF9800") else Color.parseColor("#3C3C3E")
        findViewById<TextView>(R.id.btnDimToggle)?.setTextColor(color)
    }

    fun showDimButton() { /* Dim button is always in top bar — nothing to show */ }
    fun hideDimButton() { clearDimOverlay() }

    private fun clearDimOverlay() {
        dimOverlayViews.forEach { canvasContainer.removeView(it) }
        dimOverlayViews = emptyList()
    }

    fun showDimOverlayForSelected() {
        clearDimOverlay()
        if (!dimModeEnabled) return
        val item = drawingView.selectedItem as? StrokeItem ?: return
        val pts = item.data.points; if (pts.size < 4) return
        val type = item.data.type
        val views = mutableListOf<View>()

        fun makeInput(label: String, currentMm: Float, onSet: (Float) -> Unit): View {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.WHITE); cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.parseColor("#E0E0E0"))
                }
                elevation = dp(8).toFloat()
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            container.addView(TextView(this).apply {
                text = label; textSize = 9f; setTextColor(Color.parseColor("#888888"))
            })
            val et = android.widget.EditText(this).apply {
                setText(if (currentMm >= 1000f) "${"%.3f".format(currentMm/1000f)}m" else "${"%.1f".format(currentMm)}mm")
                textSize = 13f; setTextColor(Color.parseColor("#1A1A1A"))
                background = null
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { _, _, _ ->
                    val mm = drawingView.parseRealWorldMm(text.toString())
                    if (mm != null && mm > 0f) onSet(mm)
                    clearFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(windowToken, 0)
                    true
                }
            }
            container.addView(et)
            return container
        }

        // Compute current dimensions in world units then convert to real mm
        val x1=pts[0]; val y1=pts[1]; val x2=pts[pts.size-2]; val y2=pts[pts.size-1]
        val l=minOf(x1,x2); val r=maxOf(x1,x2); val t=minOf(y1,y2); val b=maxOf(y1,y2)
        val wW=r-l; val wH=b-t

        fun placeAt(v: View, wx: Float, wy: Float) {
            val sx=drawingView.worldToScreenX(wx); val sy=drawingView.worldToScreenY(wy)
            val lp2=FrameLayout.LayoutParams(dp(100), FrameLayout.LayoutParams.WRAP_CONTENT)
            lp2.leftMargin=sx.toInt()-dp(50); lp2.topMargin=sy.toInt()-dp(20)
            canvasContainer.addView(v, lp2); views.add(v)
        }

        fun resizeShape(newWmm: Float?, newHmm: Float?) {
            val newWworld = newWmm?.let { drawingView.realMmToWorld(it) }
            val newHworld = newHmm?.let { drawingView.realMmToWorld(it) }
            val cx=(x1+x2)/2f; val cy=(y1+y2)/2f
            val nW=newWworld?:wW; val nH=newHworld?:wH
            pts[0]=cx-nW/2f; pts[1]=cy-nH/2f; pts[pts.size-2]=cx+nW/2f; pts[pts.size-1]=cy+nH/2f
            item.path=item.data.buildPath(); drawingView.markSpatialDirtyAndInvalidate()
            clearDimOverlay(); showDimOverlayForSelected()
        }

        when (type) {
            Tool.LINE, Tool.ARROW -> {
                val len = kotlin.math.hypot((x2-x1).toDouble(),(y2-y1).toDouble()).toFloat()
                val lenMm = drawingView.worldToRealMm(len)
                val v = makeInput("Length", lenMm) { mm ->
                    val newLen = drawingView.realMmToWorld(mm)
                    val dx=x2-x1; val dy=y2-y1; val oldLen=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(1f)
                    val ux=dx/oldLen; val uy=dy/oldLen
                    val midX=(x1+x2)/2f; val midY=(y1+y2)/2f
                    pts[0]=midX-ux*newLen/2f; pts[1]=midY-uy*newLen/2f
                    pts[pts.size-2]=midX+ux*newLen/2f; pts[pts.size-1]=midY+uy*newLen/2f
                    item.path=item.data.buildPath(); drawingView.markSpatialDirtyAndInvalidate()
                    clearDimOverlay(); showDimOverlayForSelected()
                }
                placeAt(v, (x1+x2)/2f, (y1+y2)/2f)
            }
            Tool.CIRCLE -> {
                val rad=kotlin.math.hypot((x2-x1).toDouble(),(y2-y1).toDouble()).toFloat()
                val radMm=drawingView.worldToRealMm(rad)
                val v = makeInput("Radius", radMm) { mm ->
                    val newRad=drawingView.realMmToWorld(mm)
                    pts[pts.size-2]=x1+newRad; pts[pts.size-1]=y1
                    item.path=item.data.buildPath(); drawingView.markSpatialDirtyAndInvalidate()
                    clearDimOverlay(); showDimOverlayForSelected()
                }
                placeAt(v, x1, y1)
            }
            else -> {
                // Width and height for all bbox shapes
                val wInput = makeInput("W", drawingView.worldToRealMm(wW)) { mm -> resizeShape(mm, null) }
                val hInput = makeInput("H", drawingView.worldToRealMm(wH)) { mm -> resizeShape(null, mm) }
                placeAt(wInput, l+wW/2f, t-drawingView.realMmToWorld(5f))
                placeAt(hInput, r+drawingView.realMmToWorld(5f), t+wH/2f)
            }
        }
        dimOverlayViews = views
    }

    private fun showDimScalePanel() {
        dimScalePanel?.let { canvasContainer.removeView(it) }
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat(); setPadding(dp(16), dp(12), dp(16), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(10).toFloat()
                setStroke(1, Color.parseColor("#E0E0E0"))
            }
        }

        fun sLbl(t: String) = panel.addView(TextView(this).apply {
            text = t; textSize = 13f; setTextColor(Color.parseColor("#8D6E63")); setPadding(0, dp(12), 0, dp(4))
        })

        // Header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Drawing Scale"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#2A2A2A")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(TextView(this).apply { text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#888")); setPadding(dp(10),dp(6),dp(10),dp(6)); setOnClickListener { dimScalePanel?.let { canvasContainer.removeView(it) }; dimScalePanel = null } })
        panel.addView(header)

        // Paper info
        val isInfinite = drawingView.canvasMode == CanvasMode.INFINITE
        if (!isInfinite) {
            panel.addView(TextView(this).apply {
                val ps = drawingView.paperSize
                text = "Paper: ${ps.name} (${ps.widthMM.toInt()}×${ps.heightMM.toInt()}mm)"
                textSize = 12f; setTextColor(Color.parseColor("#6A6A6A")); setPadding(0, dp(6), 0, 0)
            })
        }

        // Scale ratio picker
        sLbl("Scale Ratio (paper:real)")
        val ratios = listOf(1f to "1:1", 2f to "1:2", 5f to "1:5", 10f to "1:10", 20f to "1:20",
            50f to "1:50", 100f to "1:100", 200f to "1:200", 500f to "1:500", 1000f to "1:1000")
        val ratioRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val ratioScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        for ((v, label) in ratios) {
            ratioRow.addView(TextView(this).apply {
                text = label; textSize = 12f
                val active = kotlin.math.abs(drawingView.paperScale - v) < 0.01f
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (active) Color.parseColor("#FF9800") else Color.parseColor("#ECEAE7")); cornerRadius = dp(12).toFloat()
                }
                setTextColor(if (active) Color.WHITE else Color.parseColor("#3C3C3E"))
                val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp2.setMargins(dp(3), 0, dp(3), 0); layoutParams = lp2; setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    drawingView.paperScale = v
                    dimScalePanel?.let { canvasContainer.removeView(it) }; dimScalePanel = null
                    showDimScalePanel()
                }
            })
        }
        ratioScroll.addView(ratioRow); panel.addView(ratioScroll)

        // For infinite canvas: grid real size
        if (isInfinite) {
            sLbl("Grid Square = ? mm (real world)")
            val gridInput = android.widget.EditText(this).apply {
                setText("%.0f".format(drawingView.gridRealSizeMm))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 14f; setPadding(dp(8), dp(6), dp(8), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#F5F5F5")); cornerRadius = dp(6).toFloat() }
                hint = "mm per grid square"
                setOnEditorActionListener { _, _, _ ->
                    val v2 = text.toString().toFloatOrNull()
                    if (v2 != null && v2 > 0) drawingView.gridRealSizeMm = v2
                    true
                }
            }
            panel.addView(gridInput)
        }

        // Current effective scale info
        panel.addView(TextView(this).apply {
            val oneUnit = drawingView.worldToRealMm(1f)
            text = "1 world unit = ${"%.3f".format(oneUnit)}mm real"
            textSize = 11f; setTextColor(Color.parseColor("#9E9E9E")); setPadding(0, dp(8), 0, 0)
        })

        scroll.addView(panel)
        val lp2 = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp2.gravity = android.view.Gravity.BOTTOM; lp2.bottomMargin = dp(120)
        lp2.leftMargin = dp(12); lp2.rightMargin = dp(12)
        canvasContainer.addView(scroll, lp2)
        dimScalePanel = scroll
    }

    private fun updateScaleRatioButton() {
        val label = if (drawingView.paperScale == 1f) "1:1"
                    else "1:${drawingView.paperScale.toInt()}"
        findViewById<TextView>(R.id.btnScaleRatio)?.text = label
    }

    private fun showScaleRatioPopup(anchor: View) {
        val predefined = listOf(
            1f to "1:1", 2f to "1:2", 5f to "1:5", 10f to "1:10",
            20f to "1:20", 50f to "1:50", 100f to "1:100",
            200f to "1:200", 500f to "1:500", 1000f to "1:1000"
        )
        val popup = android.widget.PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
            setPadding(0, dp(4), 0, dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(8).toFloat()
                setStroke(1, Color.parseColor("#E0E0E0"))
            }
        }

        fun row(label: String, scale: Float?, isCustom: Boolean = false) {
            val active = !isCustom && scale != null && kotlin.math.abs(drawingView.paperScale - scale) < 0.01f
            container.addView(TextView(this).apply {
                text = label; textSize = 14f
                setPadding(dp(18), dp(11), dp(32), dp(11))
                setTextColor(if (active) Color.parseColor("#FF9800") else Color.parseColor("#1A1A1A"))
                if (active) setTypeface(null, Typeface.BOLD)
                setOnClickListener {
                    popup.dismiss()
                    if (isCustom) {
                        val input = android.widget.EditText(this@MainActivity).apply {
                            hint = "e.g. 25 for 1:25"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            setPadding(dp(12), dp(8), dp(12), dp(8))
                        }
                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Custom Scale (1:?)")
                            .setView(input)
                            .setPositiveButton("Set") { _, _ ->
                                val v = input.text.toString().toFloatOrNull()
                                if (v != null && v > 0f) {
                                    drawingView.paperScale = v
                                    updateScaleRatioButton()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else if (scale != null) {
                        drawingView.paperScale = scale
                        updateScaleRatioButton()
                    }
                }
            })
        }

        predefined.forEach { (scale, label) -> row(label, scale) }
        // Divider
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        })
        row("Custom…", null, isCustom = true)

        popup.contentView = container
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.width = dp(170)
        popup.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
        popup.elevation = dp(10).toFloat()
        popup.showAsDropDown(anchor, 0, dp(4))
    }

    private fun showPenOptionsPanel() {
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
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

        // Snap to endpoints toggle
        sectionLabel("Snap to Endpoints")
        val snapRowP = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val snapDescP = TextView(this).apply { text = "Lines snap to nearby endpoints"; textSize = 12f; setTextColor(Color.parseColor("#6A6A6A")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val snapSwitchP = android.widget.Switch(this).apply { isChecked = drawingView.snapEnabled; setOnCheckedChangeListener { _, on -> drawingView.snapEnabled = on; updateSnapOptionsButton() } }
        snapRowP.addView(snapDescP); snapRowP.addView(snapSwitchP); panel.addView(snapRowP)

        // Line type section
        addLineTypeSection(panel, { sectionLabel(it) }) { lt -> drawingView.currentLineType = lt; dismissPenOptionsPanel() }

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
        animatePanelIn(scroll)
    }

    private fun showEraserOptionsPanel() { findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
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

        // Toggle: whether the eraser also touches colour fills
        val fillToggleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        fillToggleRow.addView(TextView(this).apply {
            text = "Erase colour fills too"; textSize = 13f; setTextColor(Color.parseColor("#4A4A4A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        fillToggleRow.addView(android.widget.Switch(this).apply {
            isChecked = drawingView.eraserAffectsFill
            setOnCheckedChangeListener { _, isOn -> drawingView.eraserAffectsFill = isOn }
        })
        panel.addView(fillToggleRow)

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
        animatePanelIn(panel)
    }

    private var highlighterOptionsPanel: View? = null
    private fun dismissHighlighterOptionsPanel() { val p = highlighterOptionsPanel ?: return; highlighterOptionsPanel = null; animatePanelOut(p) { canvasContainer.removeView(p) }; findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE }

    private fun showHighlighterOptionsPanel() { findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
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
        animatePanelIn(scroll)
    }

    private var brushOptionsPanel: View? = null
    private fun dismissBrushOptionsPanel() { val p = brushOptionsPanel ?: return; brushOptionsPanel = null; animatePanelOut(p) { canvasContainer.removeView(p) }; findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE }

    private fun showBrushOptionsPanel() { findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
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
        val brushTypes = listOf("Round" to BrushStyle.ROUND, "Ink" to BrushStyle.INK, "Watercolor" to BrushStyle.WATERCOLOR, "Crayon" to BrushStyle.CRAYON, "Charcoal" to BrushStyle.CHARCOAL, "Neon" to BrushStyle.NEON, "Dry Brush" to BrushStyle.DRY_BRUSH, "Spray" to BrushStyle.SPRAY, "Fire" to BrushStyle.FIRE, "Grass" to BrushStyle.GRASS)
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
        animatePanelIn(scroll)
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
        when(tool){ Tool.ERASER->{ current=drawingView.eraserSize; maxSize=200; label="Eraser Size" }; Tool.TEXT->{ current=drawingView.defaultTextSize/PT_TO_PX; maxSize=144; label="Font Size (pt)" }; Tool.BRUSH->{ current=drawingView.brushThickness; maxSize=100; label="Brush Size" }; else->{ current=drawingView.currentStrokeWidth; maxSize=100; label="Stroke Width" } }
        val container=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(50,30,50,10) }
        if (tool == Tool.TEXT) {
            // Numeric input matching MS Word's point-size convention — type any exact size,
            // with -/+ steppers, instead of a slider that can't express precise or very small values.
            container.addView(TextView(this).apply { text = "$label"; textSize = 16f })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
            val input = android.widget.EditText(this).apply {
                setText(current.toInt().toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER
                gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            fun apply() { val v = input.text.toString().toIntOrNull()?.coerceIn(1, 400) ?: current.toInt(); drawingView.defaultTextSize = v * PT_TO_PX; input.setText(v.toString()) }
            input.setOnEditorActionListener { _, actionId, _ -> if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { apply(); true } else false }
            input.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }
            val minus = TextView(this).apply { text = "−"; textSize = 22f; gravity = Gravity.CENTER; setPadding(dp(18), dp(8), dp(18), dp(8))
                setOnClickListener { val v = (input.text.toString().toIntOrNull() ?: current.toInt()) - 1; val cv = v.coerceAtLeast(1); drawingView.defaultTextSize = cv * PT_TO_PX; input.setText(cv.toString()) } }
            val plus = TextView(this).apply { text = "+"; textSize = 22f; gravity = Gravity.CENTER; setPadding(dp(18), dp(8), dp(18), dp(8))
                setOnClickListener { val v = (input.text.toString().toIntOrNull() ?: current.toInt()) + 1; val cv = v.coerceAtMost(400); drawingView.defaultTextSize = cv * PT_TO_PX; input.setText(cv.toString()) } }
            row.addView(minus); row.addView(input); row.addView(plus)
            container.addView(row)
            AlertDialog.Builder(this).setTitle(label).setView(container).setPositiveButton("Done", null).show()
            return
        }
        val tv=TextView(this).apply{ text="$label: ${current.toInt()}"; textSize=16f }; container.addView(tv)
        val seek=SeekBar(this).apply{ max=maxSize; progress=current.toInt().coerceIn(1,maxSize); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(sb:SeekBar?,v:Int,f:Boolean){ val vv=v.coerceAtLeast(1); tv.text="$label: $vv"; when(tool){ Tool.ERASER->drawingView.eraserSize=vv.toFloat(); Tool.BRUSH->drawingView.brushThickness=vv.toFloat(); else->drawingView.currentStrokeWidth=vv.toFloat() } }; override fun onStartTrackingTouch(sb:SeekBar?){}; override fun onStopTrackingTouch(sb:SeekBar?){} }) }; container.addView(seek)
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

    private fun showTablePropertiesDialog(table: TableItem) {
        val (selStartRaw, selEnd) = drawingView.getTableSelection()
        val selStart = selStartRaw ?: Pair(0, 0)
        val row = selStart.first; val col = selStart.second
        val cell = table.getCellPublic(row,col)
        val minR=minOf(row,selEnd?.first?:row); val maxR=maxOf(row,selEnd?.first?:row).coerceIn(0,table.rows-1)
        val minC=minOf(col,selEnd?.second?:col); val maxC=maxOf(col,selEnd?.second?:col).coerceIn(0,table.cols-1)
        fun applyToSel(action:(TableCell)->Unit){ for(r in minR..maxR) for(c in minC..maxC) action(table.getCellPublic(r,c)); drawingView.invalidate() }

        val container=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        fun lbl(t:String){ container.addView(TextView(this).apply{ text=t;textSize=13f;setTextColor(Color.parseColor("#7B61FF"));setPadding(0,dp(10),0,dp(4)) }) }
        lateinit var dialog: AlertDialog

        val selDesc=if(selEnd!=null) "R${minR+1}C${minC+1} to R${maxR+1}C${maxC+1}" else "R${row+1}C${col+1}"
        lbl("Selection: $selDesc")

        lbl("Text Style")
        val styleRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
        fun styleBtn(label:String, pressed:Boolean, action:()->Unit): Button = Button(this).apply{
            text=label; textSize=13f
            setBackgroundColor(if(pressed) Color.parseColor("#8D6E63") else Color.parseColor("#EDE7F6"))
            setTextColor(if(pressed) Color.WHITE else Color.parseColor("#4527A0"))
            val p=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f); p.setMargins(dp(2),0,dp(2),0); layoutParams=p
            setPadding(dp(4),dp(6),dp(4),dp(6)); minWidth=0; minimumWidth=0
            setOnClickListener{ action(); dialog.dismiss(); showTablePropertiesDialog(table) } // rebuild to reflect new pressed state
        }
        styleRow.addView(styleBtn("B", cell.bold){ val v=!cell.bold; applyToSel{it.bold=v} })
        styleRow.addView(styleBtn("I", cell.italic){ val v=!cell.italic; applyToSel{it.italic=v} })
        styleRow.addView(styleBtn("U", cell.underline){ val v=!cell.underline; applyToSel{it.underline=v} })
        container.addView(styleRow)
        Button(this).apply{ text="Fonts"; textSize=13f; setBackgroundColor(Color.parseColor("#EDE7F6")); setTextColor(Color.parseColor("#4527A0"))
            setOnClickListener{ loadCustomFonts()
                val scroll=ScrollView(this@MainActivity); val fc=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
                lateinit var fdlg: AlertDialog
                fun row(label:String, family:String, tf:android.graphics.Typeface?){ fc.addView(TextView(this@MainActivity).apply{
                    text=label; textSize=17f; setPadding(dp(20),dp(12),dp(20),dp(12)); if(tf!=null) typeface=tf
                    setOnClickListener{ applyToSel{it.fontFamily=family}; fdlg.dismiss(); dialog.dismiss(); showTablePropertiesDialog(table) }
                }) }
                for((label,family) in availableFonts) row(label, family, try{Typeface.create(family,Typeface.NORMAL)}catch(e:Exception){null})
                for((label,path) in customFonts) row(label, path, try{android.graphics.Typeface.createFromFile(path)}catch(e:Exception){null})
                scroll.addView(fc)
                fdlg=AlertDialog.Builder(this@MainActivity).setTitle("Font").setView(scroll).setNegativeButton("Cancel",null).create(); fdlg.show()
            }
        }.also{ container.addView(it) }

        lbl("Layout Preset")
        val presetRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
        fun presetBtn(name:String,action:()->Unit){ val b=Button(this); b.text=name; b.textSize=11f; b.setBackgroundColor(Color.parseColor("#EDE7F6")); b.setTextColor(Color.parseColor("#4527A0")); val p=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f); p.setMargins(dp(2),0,dp(2),0); b.layoutParams=p; b.setPadding(dp(4),dp(6),dp(4),dp(6)); b.minWidth=0; b.minimumWidth=0; b.setOnClickListener{ action(); drawingView.invalidate() }; presetRow.addView(b) }
        presetBtn("Header\nRow"){ for(c in 0 until table.cols){ table.getCellPublic(0,c).also{ it.bgColor=Color.parseColor("#37474F"); it.textColor=Color.WHITE; it.borderColor=Color.parseColor("#263238"); it.borderWidth=3f } }; for(r in 1 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.bgColor=Color.WHITE; it.textColor=Color.BLACK; it.borderColor=Color.DKGRAY; it.borderWidth=1.5f } }
        presetBtn("Zebra\nRows"){ for(r in 0 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.bgColor=if(r%2==0)Color.WHITE else Color.parseColor("#F5F5F5"); it.textColor=Color.BLACK; it.borderColor=Color.parseColor("#BDBDBD"); it.borderWidth=1f } }
        presetBtn("Bold\nBorders"){ for(r in 0 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.borderColor=Color.BLACK; it.borderWidth=3f } }
        presetBtn("No\nBorders"){ for(r in 0 until table.rows) for(c in 0 until table.cols) table.getCellPublic(r,c).also{ it.borderColor=Color.TRANSPARENT; it.borderWidth=0f } }
        container.addView(presetRow)

        val div2=View(this); div2.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1)).also{it.setMargins(0,dp(12),0,dp(4))}; div2.setBackgroundColor(Color.LTGRAY); container.addView(div2)

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

        val div3=View(this); div3.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1)).also{it.setMargins(0,dp(12),0,dp(4))}; div3.setBackgroundColor(Color.LTGRAY); container.addView(div3)

        // Row/col/merge only make sense once an actual range is selected
        if (selEnd != null) {
            lbl("Rows & Columns (applies to selection anchor)")
            val rcRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
            fun rcBtn(label:String, action:()->Unit){ Button(this).apply{ text=label; textSize=11f; setBackgroundColor(Color.parseColor("#EDE7F6")); setTextColor(Color.parseColor("#4527A0")); val p=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f); p.setMargins(dp(2),0,dp(2),0); layoutParams=p; setPadding(dp(4),dp(6),dp(4),dp(6)); minWidth=0; minimumWidth=0; setOnClickListener{ action(); dialog.dismiss() }; rcRow.addView(this) } }
            rcBtn("+Row"){ drawingView.addTableRow(row) }; rcBtn("+Col"){ drawingView.addTableCol(col) }
            rcBtn("-Row"){ drawingView.removeTableRow(row) }; rcBtn("-Col"){ drawingView.removeTableCol(col) }
            container.addView(rcRow)
            Button(this).apply{ text="Merge Selection"; textSize=13f; setBackgroundColor(Color.parseColor("#EDE7F6")); setTextColor(Color.parseColor("#4527A0")); setOnClickListener{ drawingView.mergeCellSelection(); dialog.dismiss() } }.also{ container.addView(it) }
        }
        if (table.mergeSpans.containsKey(Pair(row, col))) {
            Button(this).apply{ text="Unmerge"; textSize=13f; setBackgroundColor(Color.parseColor("#EDE7F6")); setTextColor(Color.parseColor("#4527A0")); setOnClickListener{ drawingView.unmergeCellSelection(); dialog.dismiss() } }.also{ container.addView(it) }
        }

        val div4=View(this); div4.layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1)).also{it.setMargins(0,dp(12),0,dp(4))}; div4.setBackgroundColor(Color.LTGRAY); container.addView(div4)
        Button(this).apply{ text="Insert Chart from Table"; textSize=13f; setBackgroundColor(Color.parseColor("#E3F2FD")); setTextColor(Color.parseColor("#1565C0")); setOnClickListener{ dialog.dismiss(); launchChartFromTable(table) } }.also{ container.addView(it) }
        Button(this).apply{ text="Delete Whole Table"; textSize=13f; setBackgroundColor(Color.parseColor("#FFEBEE")); setTextColor(Color.parseColor("#C62828")); setOnClickListener{
            AlertDialog.Builder(this@MainActivity).setTitle("Delete table?").setMessage("This removes the whole table.")
                .setPositiveButton("Delete"){ _,_ -> drawingView.deleteAnyItem(table); dialog.dismiss() }.setNegativeButton("Cancel",null).show()
        } }.also{ container.addView(it) }

        val scroll=ScrollView(this); scroll.addView(container)
        dialog=AlertDialog.Builder(this).setTitle("Table Properties").setView(scroll).setPositiveButton("Done",null).show()
    }

    internal fun dismissCellEditor() {
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
    private fun showTableFontPicker(cell: TableCell, et: EditText, onPicked: () -> Unit) {
        loadCustomFonts()
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
        lateinit var dialog: AlertDialog
        fun addFontRow(label: String, family: String, tf: android.graphics.Typeface?) {
            container.addView(TextView(this).apply {
                text = label; textSize = 18f; setTextColor(Color.parseColor("#212121"))
                setPadding(dp(20), dp(14), dp(20), dp(14))
                if (tf != null) typeface = tf
                if (cell.fontFamily == family) setBackgroundColor(Color.parseColor("#EDE7F6"))
                setOnClickListener {
                    cell.fontFamily = family; et.typeface = tf ?: Typeface.DEFAULT
                    onPicked(); dialog.dismiss()
                }
            })
        }
        for ((label, family) in availableFonts) addFontRow(label, family, try { Typeface.create(family, Typeface.NORMAL) } catch (e: Exception) { null })
        for ((label, path) in customFonts) addFontRow(label, path, try { android.graphics.Typeface.createFromFile(path) } catch (e: Exception) { null })
        scroll.addView(container)
        dialog = AlertDialog.Builder(this).setTitle("Cell Font").setView(scroll).setNegativeButton("Cancel", null).create()
        dialog.show()
    }

    private fun showTableCellEditor(table:TableItem,row:Int,col:Int,screenX:Float,screenY:Float) {
        dismissCellEditor() // a previous cell's editor+toolbar must be fully torn down first, or they stack
        setBottomBarVisible(false)
        val cell=table.cells[row][col]
        val density = resources.displayMetrics.density

        fun cellScreenRect(): RectF {
            val rect = table.cellRect(row, col)
            val sx0 = drawingView.worldToScreenX(rect.left); val sy0 = drawingView.worldToScreenY(rect.top)
            val sx1 = drawingView.worldToScreenX(rect.right); val sy1 = drawingView.worldToScreenY(rect.bottom)
            return RectF(sx0, sy0, sx1, sy1)
        }
        // Toolbar position tracks the TABLE's top edge, not the individual cell — otherwise it
        // jumps to sit on top of whichever cell you're currently in, which is what was reported.
        fun tableScreenTopLeft(): Pair<Float, Float> = Pair(drawingView.worldToScreenX(table.x), drawingView.worldToScreenY(table.y))

        fun applyTypefaceToEt(et: EditText) {
            val style = when { cell.bold && cell.italic -> Typeface.BOLD_ITALIC; cell.bold -> Typeface.BOLD; cell.italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
            et.typeface = if (cell.fontFamily != null) (try { Typeface.create(cell.fontFamily, style) } catch (e: Exception) { Typeface.create(Typeface.DEFAULT, style) }) else Typeface.create(Typeface.DEFAULT, style)
            et.paintFlags = if (cell.underline) (et.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG) else (et.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv())
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
        applyTypefaceToEt(et)
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

        // Floating actions strip, positioned just above the whole TABLE (not the cell)
        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE); elevation = dp(6).toFloat(); setPadding(dp(4),dp(4),dp(4),dp(4)) }
        lateinit var refreshToolbar: () -> Unit
        fun actionBtn(label: String, pressed: Boolean = false, action: () -> Unit): TextView {
            val normalBg = if (pressed) Color.parseColor("#8D6E63") else Color.parseColor("#F0EBE0")
            val flashBg = if (pressed) Color.parseColor("#6D4C41") else Color.parseColor("#D7CCC8")
            val b = TextView(this).apply {
                text = label; textSize = 11f
                setTextColor(if (pressed) Color.WHITE else Color.parseColor("#4A4A4A"))
                setBackgroundColor(normalBg)
                setPadding(dp(8), dp(5), dp(8), dp(5))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(dp(2), 0, dp(2), 0); layoutParams = p
                // Immediate visible flash on touch-down, reverted on release — a plain
                // setBackgroundColor() has no built-in ripple, so without this a tap gave no
                // feedback at all and it was impossible to tell if it registered.
                setOnTouchListener { v, ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> v.setBackgroundColor(flashBg)
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.setBackgroundColor(normalBg)
                    }
                    false
                }
                setOnClickListener { action() }
            }
            actionsRow.addView(b); return b
        }
        fun ptLabel(pt: Float): String {
            val r = kotlin.math.round(pt * 10f) / 10f
            return if (r == kotlin.math.round(r)) r.toInt().toString() else r.toString()
        }
        fun applySizeChangePx(newPx: Float) {
            cell.textSize = newPx.coerceIn(6f * PT_TO_PX, 160f * PT_TO_PX)
            repositionToCellFn(et); table.recalcCellSize(row,col); drawingView.invalidate(); refreshToolbar()
        }
        fun openSizePicker(anchor: View) {
            val standardPoints = listOf(6f,7f,8f,9f,10f,10.5f,11f,12f,13f,14f,16f,18f,20f,22f,24f,26f,28f,32f,36f,40f,44f,48f,54f,60f,66f,72f,80f,88f,96f,108f,120f,132f,144f,160f)
            val popup = android.widget.PopupWindow(this)
            val pLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
            }
            val customRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(8)) }
            val customEdit = EditText(this).apply {
                hint = "Custom pt"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(ptLabel(cell.textSize / PT_TO_PX))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val setBtn = TextView(this).apply {
                text = "Set"; setTextColor(Color.parseColor("#1565C0")); setPadding(dp(12), dp(6), dp(4), dp(6))
                setOnClickListener {
                    val pts = customEdit.text.toString().toFloatOrNull()
                    if (pts != null && pts > 0f) { applySizeChangePx(pts * PT_TO_PX); popup.dismiss() }
                }
            }
            customRow.addView(customEdit); customRow.addView(setBtn)
            pLayout.addView(customRow)
            pLayout.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E1DC")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)) })
            val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(140), dp(280)) }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val currentPts = cell.textSize / PT_TO_PX
            for (pt in standardPoints) {
                list.addView(TextView(this).apply {
                    text = ptLabel(pt)
                    textSize = 16f; setPadding(dp(16), dp(10), dp(16), dp(10))
                    if (kotlin.math.abs(pt - currentPts) < 0.01f) { setBackgroundColor(Color.parseColor("#E3EEFB")); setTextColor(Color.parseColor("#1565C0")) } else setTextColor(Color.parseColor("#1C1C1E"))
                    setOnClickListener { applySizeChangePx(pt * PT_TO_PX); popup.dismiss() }
                })
            }
            scroll.addView(list); pLayout.addView(scroll)
            popup.contentView = pLayout; popup.width = dp(180); popup.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            popup.isOutsideTouchable = true; popup.isFocusable = true; popup.elevation = dp(8).toFloat()
            popup.showAsDropDown(anchor, -dp(60), -dp(300))
        }
        fun buildToolbar() {
            actionsRow.removeAllViews()
            actionBtn("B", pressed = cell.bold) { cell.bold = !cell.bold; applyTypefaceToEt(et); table.recalcCellSize(row,col); drawingView.invalidate(); refreshToolbar() }
            actionBtn("I", pressed = cell.italic) { cell.italic = !cell.italic; applyTypefaceToEt(et); table.recalcCellSize(row,col); drawingView.invalidate(); refreshToolbar() }
            actionBtn("U", pressed = cell.underline) { cell.underline = !cell.underline; applyTypefaceToEt(et); drawingView.invalidate(); refreshToolbar() }
            actionBtn("A-") { applySizeChangePx(cell.textSize - PT_TO_PX) }
            val sizeChip = actionBtn(ptLabel(cell.textSize / PT_TO_PX)) {}
            sizeChip.setOnClickListener { openSizePicker(sizeChip) }
            actionBtn("A+") { applySizeChangePx(cell.textSize + PT_TO_PX) }
            actionBtn("Del") {
                cell.text = ""; et.setText(""); table.recalcCellSize(row,col); drawingView.invalidate()
            }
        }
        val actionsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actionsRow) }
        val toolbarHeightEstimate = dp(40)
        fun repositionToolbar() {
            val (tsx, tsy) = tableScreenTopLeft()
            val alp = actionsScroll.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            alp.leftMargin = tsx.toInt().coerceIn(0, (canvasContainer.width - dp(240)).coerceAtLeast(0))
            alp.topMargin = (tsy.toInt() - toolbarHeightEstimate).coerceAtLeast(0)
            actionsScroll.layoutParams = alp
        }
        refreshToolbar = { buildToolbar(); repositionToolbar() }
        buildToolbar()
        val actionsLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        canvasContainer.addView(actionsScroll, actionsLp)
        repositionToolbar()

        // Keep the editor and toolbar glued to the table/cell as the user pans/zooms the canvas
        drawingView.onCanvasTransformed = { repositionToCell(); repositionToolbar() }

        activeCellEditText=et; activeCellToolbar=actionsScroll; tableToolbarOverlay=actionsScroll

        et.requestFocus(); et.setSelection(et.text.length)
        et.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // toggleStyleOnSelection, applyColorToSelection, toggleUnderlineOnSelection,
    // toggleFullItemSpan, applyHighlightToSelection, dismissTextSelectionBox,
    // measureTextBoxSize, updateTextSelectionBoxSize, angleDegrees, showTextSelectionBox,
    // showInlineTextEditor, and closeInlineEditor have all moved to
    // TextEditingExtensions.kt (as extension functions on MainActivity) — the single
    // largest, most-frequently-fixed cluster of code in this app, now living in its own
    // file instead of buried among unrelated features. Every call site elsewhere in this
    // file is unchanged — Kotlin resolves an extension function the same way it resolves
    // a member function, through the implicit receiver.


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
            drawingView.addImage(file.absolutePath,drawingView.screenCenterWorldX(),drawingView.screenCenterWorldY(),w,(w/ratio).coerceAtMost(drawingView.height/drawingView.getScaleFactor()*0.85f)); setActiveTool(findViewById(R.id.btnSelect), Tool.SELECT)
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
            drawingView.addImage(out.absolutePath,drawingView.screenCenterWorldX(),drawingView.screenCenterWorldY(),w,(w/ratio).coerceAtMost(drawingView.height/drawingView.getScaleFactor()*0.85f)); setActiveTool(findViewById(R.id.btnSelect), Tool.SELECT)
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
        if (getPrefs().getBoolean("auto_backup_drive", false) && driveManager.isSignedIn()) {
            backUpNoteToDrive(name, silent = true)
        }
    }

    private fun autoSave() {
        if(!drawingView.hasContent()) return
        if(currentFileName==null){ val name=nextAutoName(); currentFileName=name; tvTitle.text=name }
        // Keep the Activity's own intent in sync with whatever filename this note now has.
        // Belt-and-suspenders alongside onSaveInstanceState below: if a config change ever
        // recreates this Activity from the intent rather than a saved Bundle for any reason,
        // this ensures it still resolves to the file that was actually just written to disk
        // instead of quietly treating a now-saved note as a blank "New Note" again.
        intent.putExtra("filename", currentFileName)
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
        blurHandler.removeCallbacksAndMessages(null); blurUpdateScheduled = false
    }

    // Saved BEFORE the system tears this Activity down for a configuration change (rotation,
    // etc.) — onPause() above has already written the current content to disk by this point
    // (autoSave runs there), but this note's filename only exists in memory as currentFileName.
    // Without persisting it here too, the recreated Activity would have no way to find the
    // file it just saved and would come back up as a blank "New Note" — this is the fix for
    // "rotating mid-note erases the canvas".
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentFileName?.let { outState.putString("pending_file_name", it) }
    }

    override fun onResume() {
        super.onResume()
        if (currentAppTheme() == "GLASS") scheduleBlurUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        autosaveHandler.removeCallbacks(autosaveRunnable)
        AudioHelper.releaseAll()
    }
}
