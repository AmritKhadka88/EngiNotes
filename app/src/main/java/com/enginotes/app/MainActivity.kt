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
    private var hwrAutoEnabled = false  // real-time handwriting-to-text toggle
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
    private val recentFonts = mutableListOf("sans-serif", "serif", "monospace")

    // Returns a short display name for a font family string (system name or file path)
    private fun fontDisplayName(family: String): String {
        if (!family.startsWith("/")) return family  // system font — return as-is
        // Custom font — extract name from filename
        return java.io.File(family).nameWithoutExtension.replace("-", " ").replace("_", " ")
    }

    // Resolves font family to Typeface — handles both system names and file paths
    private fun typefaceFromFamily(family: String): android.graphics.Typeface {
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
    private var pendingShapeTool: Tool? = null  // shape tool to return to after select-after-draw
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
        tvTitle.setOnClickListener { showRenameDialog() }
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle)

        val prefs = getPrefs()
        try { drawingView.paperType = PaperType.valueOf(prefs.getString("default_paper","LINED") ?: "LINED") } catch(e:Exception){}
        pendingFontFamily = prefs.getString("last_font", "sans-serif") ?: "sans-serif"

        val fileName = intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName; tvTitle.text = fileName
            val file = File(getDrawingsFolder(),"$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else { tvTitle.text = "New Note" }

        drawingView.migrateOldNotes(filesDir)
        lastSavedContent = drawingView.serialize()
        drawingView.arcDivisions = prefs.getInt("arc_divisions",3)
        drawingView.defaultDimFontSize = prefs.getFloat("dim_font_size", 11f)
        drawingView.defaultDimArrowSize = prefs.getFloat("dim_arrow_size", 9f)

        applyConvenientLayout()

        drawingView.onTextEditRequest       = { item, sx, sy, wx, wy -> showInlineTextEditor(item,sx,sy,wx,wy) }
        drawingView.onTextSelectRequest     = { item, sx, sy, rawX, rawY -> showTextSelectionBox(item, sx, sy, rawX, rawY) }
        drawingView.onTextDeselectRequest   = { dismissTextSelectionBox() }
        drawingView.onEmptyAreaTap          = {
            // If we're in select-after-shape mode, return to the shape tool
            val shapeTool = pendingShapeTool
            val handledByShape = shapeTool != null && drawingView.currentTool == Tool.SELECT && drawingView.selectedItem == null
            if (handledByShape) {
                pendingShapeTool = null
                runOnUiThread { setActiveTool(null, shapeTool!!); rebuildContextBar() }
            }
            pendingShapeTool = null
            if (!handledByShape) {
            // Tapping genuinely empty canvas is the "I'm done" signal: commit and close whatever
            // editor is open (text or table cell), and bring the bottom toolbar back if a table
            // editor had hidden it.
            if (activeEditText != null) closeInlineEditor(true)
            if (activeCellEditText != null) dismissCellEditor()
            dismissTextSelectionBox()
            setBottomBarVisible(true)
            } // end if (!handledByShape)
        }
        drawingView.onLinkTap               = { target -> navigateToLink(target) }
        drawingView.onPageSwipe             = { dir -> drawingView.scrollPage(dir) }
        drawingView.onDimensionCreated      = { dim -> showDimensionLabelDialog(dim) }
        drawingView.onDimensionEdit         = { dim -> showDimensionStylePanel(dim) }
        drawingView.onDrawingStarted        = {
            // Hide both bars while drawing for more canvas space — tap to bring back
            if (drawingView.isDrawingTool()) {
                runOnUiThread {
                    val anim = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
                    anim.duration = 150
                    findViewById<View?>(R.id.primaryToolbarScroll)?.startAnimation(anim)
                    findViewById<View?>(R.id.primaryToolbarScroll)?.visibility = View.GONE
                    findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.startAnimation(anim)
                    findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.GONE
                }
            }
        }
        drawingView.onShapeCompleted        = { item ->
            // After drawing a shape: briefly enter SELECT mode so user can resize/move/delete.
            // Tapping outside (onEmptyAreaTap) returns to the shape tool to draw another.
            pendingShapeTool = drawingView.currentTool
            runOnUiThread { setActiveTool(null, Tool.SELECT) }
        }
        drawingView.onDrawingEnded          = {
            runOnUiThread {
                val anim = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                anim.duration = 200
                android.os.Handler(mainLooper).postDelayed({
                    findViewById<View?>(R.id.primaryToolbarScroll)?.let { v -> v.visibility = View.VISIBLE; v.startAnimation(anim) }
                    if (penOptionsPanel == null && eraserOptionsPanel == null && highlighterOptionsPanel == null && brushOptionsPanel == null) {
                        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.let { v -> v.visibility = View.VISIBLE; v.startAnimation(anim) }
                    }
                }, 300)
                // Auto handwriting-to-text if toggle is on
            }
        }
        drawingView.onItemSelected          = { item ->
            layerToolbar?.let { canvasContainer.removeView(it) }; layerToolbar = null
            if (item != null && item !is TextItem) {
                val tb = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.parseColor("#DDDDDD")) }
                    elevation = dp(4).toFloat()
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.TOP or Gravity.END; it.topMargin = dp(56); it.rightMargin = dp(8) }
                }
                fun lBtn(label: String, action: () -> Unit) = TextView(this).apply {
                    text = label; textSize = 13f; gravity = Gravity.CENTER
                    val pad = dp(8); setPadding(pad, dp(6), pad, dp(6))
                    setOnClickListener { action() }; tb.addView(this)
                }
                lBtn("⬆⬆") { drawingView.bringToFront(item) }
                lBtn("⬆") { drawingView.bringForward(item) }
                lBtn("⬇") { drawingView.sendBackward(item) }
                lBtn("⬇⬇") { drawingView.sendToBack(item) }
                canvasContainer.addView(tb)
                layerToolbar = tb
            }
        }
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
        findViewById<ImageButton>(R.id.btnText).setOnLongClickListener { showTextOptionsPanel(); true }
        findViewById<ImageButton>(R.id.btnInsert).setOnClickListener { showInsertMenu() }

        // Handwriting-to-text realtime toggle (button removed from toolbar)
        findViewById<ImageButton>(R.id.btnTools).setOnClickListener { showShapesPicker(it as ImageButton) }

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
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnClickListener { btn -> closeInlineEditor(true); setActiveTool(btn as ImageButton, Tool.HIGHLIGHTER) }
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnLongClickListener { closeInlineEditor(true); setActiveTool(it as ImageButton, Tool.HIGHLIGHTER); true }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnClickListener { btn -> closeInlineEditor(true); setActiveTool(btn as ImageButton, Tool.BRUSH) }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnLongClickListener { closeInlineEditor(true); setActiveTool(it as ImageButton, Tool.BRUSH); true }
        findViewById<ImageButton?>(R.id.btnQuickFill)?.setOnClickListener { btn -> closeInlineEditor(true); setActiveTool(btn as ImageButton, Tool.FILL) }
        findViewById<ImageButton?>(R.id.btnQuickFill)?.setOnLongClickListener { showHatchPicker(); true }
        // Long-press on draw/eraser now just activates the tool and shows context bar (no floating panel)
        findViewById<ImageButton>(R.id.btnDraw).setOnLongClickListener { closeInlineEditor(true); showPenOptionsPanel(); true }
        findViewById<ImageButton>(R.id.btnQuickEraser).setOnLongClickListener { showEraserOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnHighlighter)?.setOnLongClickListener { showHighlighterOptionsPanel(); true }
        findViewById<ImageButton?>(R.id.btnBrush)?.setOnLongClickListener { showBrushOptionsPanel(); true }

        findViewById<ImageButton?>(R.id.btnMenu)?.setOnClickListener { onMenuClick(it) }
        findViewById<ImageButton?>(R.id.btnLink)?.setOnClickListener { closeInlineEditor(true); showLinkPickerDialog() }
        findViewById<ImageButton?>(R.id.btnBack)?.setOnClickListener { confirmThenExit() }
        btnLayoutToggle.setOnClickListener { showLayoutMenu(it) }

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

    private fun setActiveTool(btn: ImageButton?, tool: Tool) {
        drawingView.currentTool = tool; setActiveToolbarBtn(btn)
        dismissPenOptionsPanel(); dismissEraserOptionsPanel(); dismissHighlighterOptionsPanel(); dismissBrushOptionsPanel(); dismissShapesPicker()
        contextBarPage = 0
        rebuildContextBar()
    }
    private fun setActiveToolbarBtn(btn: ImageButton?) { activeToolbarButton?.isSelected = false; activeToolbarButton = btn; btn?.isSelected = true }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rebuildContextBar() {
        val contextBar = findViewById<HorizontalScrollView>(R.id.toolbarScroll) ?: return
        val row = (contextBar.getChildAt(0) as? LinearLayout) ?: LinearLayout(this).also {
            it.orientation = LinearLayout.HORIZONTAL; it.gravity = Gravity.CENTER_VERTICAL
            it.setPadding(dp(8), 0, dp(8), 0); contextBar.addView(it)
        }
        row.removeAllViews()
        val BAR_H = dp(38)
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
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(if (active) Color.parseColor("#1C1C1E") else Color.parseColor("#ECEAE7")); cornerRadius = dp(11).toFloat() }
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#3C3C3E"))
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
        val allBrushTypes = listOf("Round" to BrushStyle.ROUND, "Flat" to BrushStyle.FLAT, "Texture" to BrushStyle.TEXTURE, "Ink" to BrushStyle.INK, "Watercolor" to BrushStyle.WATERCOLOR, "Crayon" to BrushStyle.CRAYON, "Charcoal" to BrushStyle.CHARCOAL, "Airbrush" to BrushStyle.AIRBRUSH, "Spray" to BrushStyle.SPRAY, "Stipple" to BrushStyle.STIPPLE, "Splatter" to BrushStyle.SPLATTER, "Neon" to BrushStyle.NEON, "Marker" to BrushStyle.MARKER, "Dry Brush" to BrushStyle.DRY_BRUSH, "Scatter" to BrushStyle.SCATTER, "Fur" to BrushStyle.FUR, "Grass" to BrushStyle.GRASS, "Smoke" to BrushStyle.SMOKE, "Fill Spray" to BrushStyle.FILL_SPRAY, "Glitter" to BrushStyle.GLITTER, "Confetti" to BrushStyle.CONFETTI, "Fire" to BrushStyle.FIRE, "Lightning" to BrushStyle.LIGHTNING)
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
                opacityButton(drawingView.eraserOpacity) { drawingView.eraserOpacity = it; drawingView.invalidate() }
            }
            Tool.FILL -> {
                eightColors(drawingView.fillColor) { c -> drawingView.fillColor = c; drawingView.pendingHatchPattern = null }
            }
            Tool.TEXT -> {
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
                sizeButton(editSize, 120) { v -> editSize = v; activeEditText?.textSize = v / resources.displayMetrics.density; textSelectionItem?.let { it.size = v; drawingView.invalidate() } }
                opacityButton(editOpacity) { v -> editOpacity = v; activeEditText?.alpha = v / 255f; textSelectionItem?.let { it.opacity = v; drawingView.invalidate() } }
                divider()
                eightColors(editColor) { c -> editColor = c; activeEditText?.setTextColor(c); textSelectionItem?.let { it.color = c; drawingView.invalidate() } }
            }
            Tool.SELECT, Tool.LASSO, Tool.AUTOSELECT -> {
                // Select: rectangle icon, Lasso: lasso icon, Rectangle (was Auto): dashed rect icon
                data class SM(val label: String, val drawIcon: (Canvas, Paint, android.graphics.RectF) -> Unit, val tool: Tool)
                val modes = listOf(
                    SM("Select", { c, p, r -> p.style = Paint.Style.STROKE; p.strokeWidth = 3f; c.drawRect(r, p) }, Tool.SELECT),
                    SM("Lasso", { c, p, r -> p.style = Paint.Style.STROKE; p.strokeWidth = 3f; val path = android.graphics.Path(); path.moveTo(r.centerX(), r.top); path.cubicTo(r.right, r.top, r.right, r.bottom, r.centerX(), r.bottom); path.cubicTo(r.left, r.bottom, r.left, r.top, r.centerX(), r.top); c.drawPath(path, p) }, Tool.LASSO),
                    SM("Rect", { c, p, r -> p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 5f), 0f); c.drawRect(r, p) }, Tool.AUTOSELECT)
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
                        val lp = LinearLayout.LayoutParams(dp(50), BAR_H); lp.setMargins(dp(3),0,dp(3),0); layoutParams = lp
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
        listOf("Save","Save As","Export","Export Window","Clear Canvas").forEach { popup.menu.add(it) }
        if (currentFileName != null) popup.menu.add("Delete This Note")
        popup.menu.add("Add to Book")
        listOf("Open PDF","Chart Builder","Handwriting to Text","Settings","About","Exit").forEach { popup.menu.add(it) }
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
                item.title == "Open PDF" -> pickPdfLauncher.launch("application/pdf")
                item.title == "Chart Builder" -> chartLauncher.launch(android.content.Intent(this, ChartActivity::class.java))
                item.title == "Handwriting to Text" -> convertHandwritingInPlace()
                item.title == "Settings" -> showSettingsDialog()
                item.title == "About" -> showAboutDialog()
                item.title == "Exit" -> confirmThenExit()
            }
            true
        }
        popup.show()
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
        }
        contextBar.scrollTo(0, 0)
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

    private fun showHatchPicker() {
        val categories = linkedMapOf(
            "Lines" to listOf("45° Lines" to HatchPattern.HATCH_45, "135° Lines" to HatchPattern.HATCH_135, "Vertical" to HatchPattern.HATCH_90, "Horizontal" to HatchPattern.HATCH_0, "Cross" to HatchPattern.HATCH_CROSS, "Diagonal Cross" to HatchPattern.HATCH_DIAGONAL_CROSS),
            "Civil/Structural" to listOf("Concrete" to HatchPattern.CONCRETE, "Steel" to HatchPattern.STEEL, "Earth" to HatchPattern.EARTH, "Sand" to HatchPattern.SAND, "Rock" to HatchPattern.ROCK, "Gravel" to HatchPattern.GRAVEL, "Compacted Fill" to HatchPattern.COMPACTED_FILL, "Loose Fill" to HatchPattern.LOOSE_FILL, "Clay" to HatchPattern.CLAY, "Silt" to HatchPattern.SILT, "Peat" to HatchPattern.PEAT, "Chalk" to HatchPattern.CHALK, "Asphalt" to HatchPattern.ASPHALT, "Rebar" to HatchPattern.REBAR, "Concrete Precast" to HatchPattern.CONCRETE_PRECAST),
            "Wood/Building" to listOf("Wood Grain" to HatchPattern.WOOD_GRAIN, "Wood End" to HatchPattern.WOOD_END, "Brick" to HatchPattern.BRICK, "Block" to HatchPattern.BLOCK, "Plywood" to HatchPattern.PLYWOOD, "Drywall" to HatchPattern.DRYWALL, "Insulation" to HatchPattern.INSULATION),
            "Metal" to listOf("Aluminum" to HatchPattern.ALUMINUM, "Copper" to HatchPattern.COPPER, "Iron" to HatchPattern.IRON, "Bronze" to HatchPattern.BRONZE, "Titanium" to HatchPattern.TITANIUM, "Gold" to HatchPattern.GOLD_HATCH),
            "Material" to listOf("Glass" to HatchPattern.GLASS, "Rubber" to HatchPattern.RUBBER, "Plastic" to HatchPattern.PLASTIC, "Ceramic" to HatchPattern.CERAMIC, "Fiberglass" to HatchPattern.FIBERGLASS, "Foam" to HatchPattern.FOAM, "Membrane" to HatchPattern.MEMBRANE),
            "Patterns" to listOf("Dots Fine" to HatchPattern.DOTS_FINE, "Dots Coarse" to HatchPattern.DOTS_COARSE, "Stipple" to HatchPattern.STIPPLE, "Honeycomb" to HatchPattern.HONEYCOMB, "Basket Weave" to HatchPattern.BASKET_WEAVE, "Diamond Grid" to HatchPattern.DIAMOND_GRID, "Zigzag" to HatchPattern.ZIGZAG, "Wave" to HatchPattern.WAVE, "Herringbone" to HatchPattern.HERRINGBONE, "Scale" to HatchPattern.SCALE, "Chain Link" to HatchPattern.CHAIN_LINK, "Contour" to HatchPattern.CONTOUR, "Water" to HatchPattern.WATER)
        )
        val allItems = mutableListOf<String>(); val allPatterns = mutableListOf<HatchPattern>()
        categories.forEach { (cat, items) -> allItems.add("── $cat ──"); allPatterns.add(HatchPattern.HATCH_45); items.forEach { (name, pat) -> allItems.add("  $name"); allPatterns.add(pat) } }

        AlertDialog.Builder(this).setTitle("Hatch Pattern (long-press area to fill)")
            .setItems(allItems.toTypedArray()) { _, i ->
                val selected = allPatterns[i]; val label = allItems[i]
                if (label.startsWith("──")) return@setItems
                // Set fill tool with hatch — next tap fills the tapped area with this hatch
                drawingView.pendingHatchPattern = selected
                drawingView.pendingHatchColor = drawingView.currentColor
                setActiveTool(null, Tool.FILL)
                android.widget.Toast.makeText(this, "Tap area to fill with hatch", android.widget.Toast.LENGTH_SHORT).show()
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

        div(); hdr("TOOLBAR")
        val barSizeLabels = arrayOf("Small (36dp)", "Medium (44dp)", "Large (52dp)", "Extra Large (60dp)")
        val barSizeValues = arrayOf(36, 44, 52, 60)
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

        val scroll = ScrollView(this).apply{ addView(container) }
        AlertDialog.Builder(this).setTitle("Settings").setView(scroll)
            .setPositiveButton("Done") { _,_ ->
                prefs.edit()
                    .putBoolean("confirm_exit_clear",confirmCb.isChecked)
                    .putBoolean("autosave",autosaveCb.isChecked)
                    .putString("default_paper",selPaper)
                    .putInt("arc_divisions",(arcInput.text.toString().toIntOrNull()?:3).coerceIn(2,12))
                    .putInt("bar_icon_size", selBarSize)
                    .putFloat("dim_font_size", dimFontSz)
                    .putFloat("dim_arrow_size", dimArrowSz)
                    .apply()
                drawingView.arcDivisions = prefs.getInt("arc_divisions",3)
                drawingView.defaultDimFontSize = dimFontSz
                drawingView.defaultDimArrowSize = dimArrowSz
                try { drawingView.paperType = PaperType.valueOf(selPaper) } catch(e:Exception){}
                drawingView.invalidate()
                val sz = dp(selBarSize)
                val primaryBar = findViewById<HorizontalScrollView?>(R.id.primaryToolbarScroll)
                (primaryBar?.getChildAt(0) as? LinearLayout)?.let { ll ->
                    for (i in 0 until ll.childCount) {
                        val child = ll.getChildAt(i) as? ImageButton ?: continue
                        val lp = child.layoutParams as LinearLayout.LayoutParams
                        lp.width = sz; lp.height = sz; child.layoutParams = lp
                    }
                }
                rebuildContextBar()
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
    private var contextBarPage = 0 // 0 = default, increments on swipe-up

    private var textOptionsPanel: View? = null
    private fun dismissTextOptionsPanel() {
        textOptionsPanel?.let { canvasContainer.removeView(it) }; textOptionsPanel = null
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

        // Size slider
        panel.addView(TextView(this).apply { text = "Size: ${editSize.toInt()}pt"; textSize = 13f; setTextColor(Color.parseColor("#8A8580")); setPadding(0, dp(12), 0, dp(4)) })
        panel.addView(SeekBar(this).apply {
            max = 120; progress = editSize.toInt().coerceIn(8, 120)
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#26A69A"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#26A69A"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { if(f) { editSize = v.coerceAtLeast(8).toFloat(); rebuildContextBar() } }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

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
    }

    private fun dismissPenOptionsPanel() {
        penOptionsPanel?.let { canvasContainer.removeView(it) }; penOptionsPanel = null
        findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE
    }
    private fun dismissEraserOptionsPanel() { eraserOptionsPanel?.let { canvasContainer.removeView(it) }; eraserOptionsPanel = null; findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE }

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
    private fun dismissHighlighterOptionsPanel() { highlighterOptionsPanel?.let { canvasContainer.removeView(it) }; highlighterOptionsPanel = null; findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE }

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
    }

    private var brushOptionsPanel: View? = null
    private fun dismissBrushOptionsPanel() { brushOptionsPanel?.let { canvasContainer.removeView(it) }; brushOptionsPanel = null; findViewById<HorizontalScrollView?>(R.id.toolbarScroll)?.visibility = View.VISIBLE }

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
    private var layerToolbar: View? = null

    private fun dismissTextSelectionBox() {
        textSelectionBox?.let { canvasContainer.removeView(it) }
        textSelectionHandles.forEach { canvasContainer.removeView(it) }
        textSelectionHandles = emptyList()
        if (textSelectionBox != null) drawingView.onCanvasTransformed = null
        textSelectionBox = null; textSelectionItem = null
        drawingView.isTextSelected = false
        drawingView.selectedItem = null; drawingView.invalidate()
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
        // Use WORLD units (item.size) exactly like drawTextItem does, then scale to screen.
        // Previously used screenSizePx directly which gave different layout than canvas rendering.
        val scale = screenSizePx / item.size.coerceAtLeast(1f)
        val tp = android.text.TextPaint(); tp.textSize = item.size; tp.isAntiAlias = true
        try { tp.typeface = Typeface.create(item.fontFamily, Typeface.NORMAL) } catch (e: Exception) {}
        // Mirror textWrapWidth: maxWidth in world units, else unconstrained
        val wrapWidth = if (item.maxWidth > 0f) item.maxWidth.toInt().coerceAtLeast(40) else 4000
        val layout = android.text.StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, wrapWidth).setIncludePad(true).build()
        val measuredW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: (item.size * 2f)
        // Scale world dimensions to screen pixels, add small padding
        val w = (measuredW * scale + dp(12)).toInt().coerceAtLeast(dp(40))
        val h = (layout.height * scale + dp(8).toFloat()).coerceAtLeast(dp(30).toFloat()).toInt()
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

    private fun showTextSelectionBox(item: TextItem, screenX: Float, screenY: Float, initialRawX: Float = -1f, initialRawY: Float = -1f) {
        if (textSelectionItem === item) return
        dismissTextSelectionBox()
        closeInlineEditor(true)
        dismissCellEditor()
        drawingView.isTextSelected = true

        val useActualSize = drawingView.canvasMode != CanvasMode.INFINITE && drawingView.canvasMode != CanvasMode.CONVENIENT
        val convenientBoost = if (drawingView.canvasMode == CanvasMode.CONVENIENT) 1.6f else 1f
        val screenSizePx = (if (useActualSize) item.size else item.size * drawingView.getScaleFactor()) * convenientBoost

        // Use a touch surface sized to the text item (not full-screen).
        // Always returns true on ACTION_DOWN so the gesture is never dropped mid-sequence.
        // Taps outside fall through naturally to DrawingView below.
        val anchorScreenX = drawingView.worldToScreenX(item.x)
        val anchorScreenY = drawingView.worldToScreenY(item.y)
        val (measW, measH) = measureTextBoxSize(item, screenSizePx)
        var boxW = measW; var boxH = measH

        // Declare toolbar first so it's in scope for moveSurface touch listener and keyboard listener
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#DDDDDD"))
            }
            elevation = dp(4).toFloat()
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        val moveSurface = View(this)
        var moveStartRawX = 0f; var moveStartRawY = 0f; var moveStartLeft = 0; var moveStartTop = 0
        var isDraggingRotate = false; var rotStartRawX2 = 0f; var rotStartRotation2 = 0f
        moveSurface.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val rotHandleSx = drawingView.worldToScreenX(item.x)
                    val rotHandleSy = drawingView.worldToScreenY(item.y) - dp(90)
                    val dist = kotlin.math.hypot((ev.rawX - rotHandleSx).toDouble(), (ev.rawY - rotHandleSy).toDouble()).toFloat()
                    if (dist < dp(56)) {
                        isDraggingRotate = true; rotStartRawX2 = ev.rawX; rotStartRotation2 = item.rotation
                    } else {
                        isDraggingRotate = false
                        moveStartRawX = ev.rawX; moveStartRawY = ev.rawY
                        val lp = moveSurface.layoutParams as FrameLayout.LayoutParams
                        moveStartLeft = lp.leftMargin; moveStartTop = lp.topMargin
                    }
                    true // ALWAYS true — never drop the touch sequence
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (isDraggingRotate) {
                        item.rotation = rotStartRotation2 - (ev.rawX - rotStartRawX2) * 0.5f
                        drawingView.invalidate()
                    } else {
                        val dx = ev.rawX - moveStartRawX; val dy = ev.rawY - moveStartRawY
                        val lp = moveSurface.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = (moveStartLeft + dx).toInt().coerceAtLeast(0)
                        lp.topMargin = (moveStartTop + dy).toInt().coerceAtLeast(0)
                        moveSurface.layoutParams = lp
                        item.x = drawingView.screenToWorldX(lp.leftMargin.toFloat())
                        item.y = drawingView.screenToWorldY(lp.topMargin.toFloat() + boxH)
                        drawingView.invalidate()
                        // Move toolbar with the text box
                        val tbLp = toolbar.layoutParams as? FrameLayout.LayoutParams
                        if (tbLp != null) {
                            tbLp.leftMargin = lp.leftMargin.coerceIn(dp(4), canvasContainer.width - dp(200))
                            tbLp.topMargin = (lp.topMargin - dp(56)).coerceAtLeast(dp(4))
                            toolbar.layoutParams = tbLp
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isDraggingRotate = false; true
                }
                else -> true
            }
        }
        val surfaceLp = FrameLayout.LayoutParams(boxW, boxH)
        surfaceLp.leftMargin = anchorScreenX.toInt().coerceAtLeast(0)
        surfaceLp.topMargin = (anchorScreenY - boxH).toInt().coerceAtLeast(0)
        moveSurface.layoutParams = surfaceLp
        canvasContainer.addView(moveSurface)

        // Floating toolbar: Delete | Done
        fun tbBtn(label: String, tint: Int? = null) = TextView(this).apply {
            text = label; textSize = 16f; gravity = Gravity.CENTER
            val pad = dp(10); setPadding(pad, dp(8), pad, dp(8))
            tint?.let { setTextColor(it) }
        }
        val btnDel = tbBtn("\uD83D\uDDD1", Color.parseColor("#D32F2F"))
        btnDel.setOnClickListener { drawingView.removeTextItem(item); dismissTextSelectionBox(); drawingView.invalidate() }
        val btnDone = tbBtn("\u2713", Color.parseColor("#388E3C"))
        btnDone.setOnClickListener { dismissTextSelectionBox() }
        toolbar.addView(btnDel); toolbar.addView(btnDone)

        canvasContainer.addView(toolbar)
        textSelectionBox = moveSurface; textSelectionItem = item
        textSelectionHandles = listOf(toolbar)

        fun updateToolbarPos() {
            val sx = drawingView.worldToScreenX(item.x)
            val sy = drawingView.worldToScreenY(item.y)
            val lp = toolbar.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin = sx.toInt().coerceIn(dp(4), canvasContainer.width - dp(200))
            lp.topMargin = (sy - dp(100).toFloat()).coerceAtLeast(dp(4).toFloat()).toInt()
            toolbar.layoutParams = lp
        }
        updateToolbarPos()
        drawingView.onCanvasTransformed = { updateToolbarPos() }
    }

    private fun setupKeyboardAutoScroll(editingBox: View) {
        val rootView = window.decorView.rootView
        rootView.setOnApplyWindowInsetsListener { view, insets ->
            val keyboardHeight = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            val systemBarsHeight = insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom
            val visibleObstructedSpace = keyboardHeight.coerceAtLeast(systemBarsHeight)
            if (keyboardHeight > 0) {
                val location = IntArray(2)
                editingBox.getLocationOnScreen(location)
                val boxBottomY = location[1] + editingBox.height
                val screenBoundary = rootView.height - visibleObstructedSpace
                if (boxBottomY > screenBoundary) {
                    val shift = boxBottomY - screenBoundary + dp(24)
                    canvasContainer.animate().translationY(-shift.toFloat()).setDuration(150).start()
                }
            } else {
                canvasContainer.animate().translationY(0f).setDuration(150).start()
            }
            insets
        }
    }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        dismissTextSelectionBox()
        if (activeEditText != null && editingItem === item) return
        if (activeEditText != null) { isSwitchingTextEditor=true; closeInlineEditor(true); isSwitchingTextEditor=false; drawingView.post{ showInlineTextEditor(item,screenX,screenY,worldX,worldY) }; return }
        dismissCellEditor()
        dismissAllFloatingPanels()
        drawingView.isTextEditorOpen = true
        // Switch toolbar to TEXT tool so the correct context bar shows
        setActiveTool(findViewById(R.id.btnText), Tool.TEXT)
        pendingBold=false; pendingItalic=false; pendingUnderline=false; pendingHighlight=null
        // Default font size: 50pt in Convenient layout (large, comfortable writing feel),
        // 12pt in Print/Infinite layouts (true-to-scale, matches standard document text).
        val layoutDefaultSize = if (drawingView.canvasMode == CanvasMode.CONVENIENT) 50f * PT_TO_PX else 12f * PT_TO_PX
        editingItem=item; editWorldX=item?.x?:worldX; editWorldY=item?.y?:worldY; editRotation=item?.rotation?:0f; editColor=item?.color?:drawingView.currentColor; editSize=item?.size?:layoutDefaultSize
        editOpacity = item?.opacity ?: 255
        // For new text: use last-saved font from prefs (most reliable — survives any intermediate resets)
        // For existing text: load that item's own font
        pendingFontFamily = item?.fontFamily ?: (getPrefs().getString("last_font", pendingFontFamily) ?: pendingFontFamily)
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
        et.typeface = typefaceFromFamily(pendingFontFamily)
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
        setupKeyboardAutoScroll(boxContainer)

        // adjustResize (set in manifest) shrinks the canvas when keyboard opens — no extra handling needed

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
        // Clear keyboard scroll listener and reset canvas position
        window.decorView.rootView.setOnApplyWindowInsetsListener(null)
        canvasContainer.animate().translationY(0f).setDuration(150).start()
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
