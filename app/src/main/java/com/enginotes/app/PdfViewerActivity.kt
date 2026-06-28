package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var pdfCanvas: PdfAnnotationView
    private lateinit var tvPageInfo: TextView
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage = 0
    private var totalPages = 0
    private var pdfFile: File? = null
    private val annotationFiles = mutableMapOf<Int, String>()

    private var isSnipMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#EDEAE3"))

        pdfCanvas = PdfAnnotationView(this)
        root.addView(pdfCanvas, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val snipOverlay = SnipOverlayView(this)
        snipOverlay.visibility = View.GONE
        root.addView(snipOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            gravity = Gravity.CENTER_VERTICAL
        }

        val toolButtons = mutableMapOf<PdfTool, TextView>()
        var activeToolBtn: TextView? = null
        fun iconBtn(emoji: String, tool: PdfTool? = null, action: () -> Unit): TextView {
            return TextView(this).apply {
                text = emoji; textSize = 18f; setTextColor(Color.parseColor("#4A4A4A"))
                gravity = Gravity.CENTER
                val p = LinearLayout.LayoutParams(dp(38), dp(38)); p.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = p
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener {
                    action()
                    if (tool != null) {
                        // Clear previous active, highlight this one
                        activeToolBtn?.setBackgroundColor(Color.TRANSPARENT)
                        setBackgroundColor(Color.parseColor("#E3F2FD"))
                        activeToolBtn = this
                    }
                    // Non-tool buttons (save, close) don't change the active tool highlight
                }
                toolbar.addView(this)
                if (tool != null) toolButtons[tool] = this
            }
        }

        iconBtn("‹") { if (currentPage > 0) { saveCurrentAnnotations(); currentPage--; loadPage() } }
        tvPageInfo = TextView(this).apply { setTextColor(Color.parseColor("#4A4A4A")); textSize = 13f; setPadding(dp(4), 0, dp(4), 0); gravity = Gravity.CENTER }
        toolbar.addView(tvPageInfo)
        iconBtn("›") { if (currentPage < totalPages - 1) { saveCurrentAnnotations(); currentPage++; loadPage() } }

        val btnSelect = iconBtn("☞", PdfTool.SELECT) { pdfCanvas.currentTool = PdfTool.SELECT; setSnipMode(false, snipOverlay) }
        iconBtn("✎", PdfTool.PEN) { pdfCanvas.currentTool = PdfTool.PEN; setSnipMode(false, snipOverlay) }
        iconBtn("✒", PdfTool.HIGHLIGHT) { pdfCanvas.currentTool = PdfTool.HIGHLIGHT; setSnipMode(false, snipOverlay) }
        iconBtn("⌫", PdfTool.ERASER) { pdfCanvas.currentTool = PdfTool.ERASER; setSnipMode(false, snipOverlay) }

        // Default to SELECT highlighted
        btnSelect.setBackgroundColor(Color.parseColor("#E3F2FD"))
        activeToolBtn = btnSelect

        iconBtn("✂") {
            val entering = !isSnipMode
            setSnipMode(entering, snipOverlay)
            if (entering) Toast.makeText(this, "Draw a rectangle to snip, then tap ✓ Confirm", Toast.LENGTH_LONG).show()
        }

        iconBtn("💾") { saveCurrentAnnotations(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        iconBtn("✕") { saveCurrentAnnotations(); finish() }

        val tlp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        root.addView(toolbar, tlp)

        setContentView(root)

        // Wire page swipe from SELECT mode pan gesture
        pdfCanvas.onPageSwipe = { dir ->
            val target = currentPage + dir
            if (target in 0 until totalPages) { saveCurrentAnnotations(); currentPage = target; loadPage() }
        }

        // Snip overlay: confirmed selection -> crop at FULL PDF render resolution for crisp quality,
        // matching the on-screen rectangle's aspect ratio and relative size exactly.
        snipOverlay.onSnipSelected = { rect ->
            val pageScreenRect = pdfCanvas.getPageScreenRect()
            val pageBmp = pdfCanvas.getPageBitmap()
            if (pageBmp != null && pageScreenRect != null && pageScreenRect.width() > 0 && pageScreenRect.height() > 0) {
                // Map screen rect to normalized page coords (0..1)
                val relLeft   = ((rect.left   - pageScreenRect.left) / pageScreenRect.width()).coerceIn(0f, 1f)
                val relTop    = ((rect.top    - pageScreenRect.top)  / pageScreenRect.height()).coerceIn(0f, 1f)
                val relRight  = ((rect.right  - pageScreenRect.left) / pageScreenRect.width()).coerceIn(0f, 1f)
                val relBottom = ((rect.bottom - pageScreenRect.top)  / pageScreenRect.height()).coerceIn(0f, 1f)

                val isOcr = intent.getBooleanExtra("ocr_mode", false)
                if (isOcr) {
                    // OCR: re-render only the cropped page region at 3x for ML Kit accuracy.
                    // Re-rendering just the sub-region is fast because PdfRenderer clips to the
                    // destination bitmap size — no need to render the full page at 3x.
                    setSnipMode(false, snipOverlay)
                    Thread {
                        try {
                            val renderer = pdfRenderer ?: return@Thread
                            val page = renderer.openPage(currentPage)
                            val cropScale = 3f
                            val outW = ((relRight - relLeft) * page.width * cropScale).toInt().coerceAtLeast(1)
                            val outH = ((relBottom - relTop) * page.height * cropScale).toInt().coerceAtLeast(1)
                            val outBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                            Canvas(outBmp).drawColor(Color.WHITE)
                            // Transform matrix: render just the sub-rect of the page into outBmp
                            val matrix = android.graphics.Matrix()
                            matrix.setScale(cropScale, cropScale)
                            matrix.postTranslate(-relLeft * page.width * cropScale, -relTop * page.height * cropScale)
                            page.render(outBmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            runOnUiThread { sendSnipToNote(outBmp) }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this, "Snip failed: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }.start()
                } else {
                    // Regular snip-to-canvas: use the already-rendered 2x bitmap (instant)
                    val sx = (relLeft * pageBmp.width).toInt().coerceIn(0, pageBmp.width - 1)
                    val sy = (relTop * pageBmp.height).toInt().coerceIn(0, pageBmp.height - 1)
                    val sw = ((relRight - relLeft) * pageBmp.width).toInt().coerceAtLeast(1).coerceAtMost(pageBmp.width - sx)
                    val sh = ((relBottom - relTop) * pageBmp.height).toInt().coerceAtLeast(1).coerceAtMost(pageBmp.height - sy)
                    val cropped = Bitmap.createBitmap(pageBmp, sx, sy, sw, sh)
                    sendSnipToNote(cropped)
                    setSnipMode(false, snipOverlay)
                }
            } else {
                setSnipMode(false, snipOverlay)
            }
        }

        val uriString = intent.getStringExtra("pdf_uri")
        val filePath = intent.getStringExtra("pdf_path")
        when {
            filePath != null -> openPdf(File(filePath))
            uriString != null -> copyAndOpenPdf(Uri.parse(uriString))
        }
    }

    private fun setSnipMode(on: Boolean, overlay: SnipOverlayView) {
        isSnipMode = on
        overlay.visibility = if (on) View.VISIBLE else View.GONE
        pdfCanvas.isEnabled = !on
        if (!on) overlay.reset()
    }

    private fun copyAndOpenPdf(uri: Uri) {
        // Large PDFs (thousands of pages / hundreds of MB) take real time to copy. Doing this on
        // the main thread would freeze the UI and risk an ANR crash, and with no progress shown
        // it would look like the app had simply failed. This runs the copy on a background thread
        // with a progress dialog, and PdfRenderer itself only ever decodes ONE page bitmap at a
        // time regardless of page count, so total page count was never actually the bottleneck -
        // the blocking synchronous file copy was.
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Loading PDF..."); setCancelable(false); show()
        }
        Thread {
            try {
                val folder = File(filesDir, "pdfs").also { it.mkdirs() }
                val out = File(folder, "pdf_${System.currentTimeMillis()}.pdf")
                contentResolver.openInputStream(uri)?.use { ins ->
                    FileOutputStream(out).use { fos ->
                        val buffer = ByteArray(1 shl 20) // 1MB buffer for fast large-file copying
                        var read: Int
                        while (ins.read(buffer).also { read = it } != -1) fos.write(buffer, 0, read)
                    }
                }
                runOnUiThread {
                    progressDialog.dismiss()
                    pdfFile = out; openPdf(out)
                }
            } catch (e: OutOfMemoryError) {
                runOnUiThread { progressDialog.dismiss(); Toast.makeText(this, "PDF is too large for available memory", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { progressDialog.dismiss(); Toast.makeText(this, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun openPdf(file: File) {
        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fd)
            totalPages = pdfRenderer!!.pageCount; currentPage = 0
            if (pdfCanvas.width > 0) {
                loadPage()
            } else {
                // View not laid out yet (width=0) - wait for first layout pass before rendering,
                // otherwise the page bitmap is computed at 0 scale and appears blank.
                pdfCanvas.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (pdfCanvas.width > 0) {
                            pdfCanvas.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            loadPage()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPage() {
        val renderer = pdfRenderer ?: return
        val pageToLoad = currentPage
        val canvasWidth = pdfCanvas.width
        Thread {
            try {
                // PdfRenderer is not thread-safe for concurrent page opens, but since each page
                // turn waits for the previous render to fully finish before starting the next
                // (page.close() happens before this thread exits), sequential background renders
                // are safe and keep the UI thread free during the (potentially slow, for large or
                // image-heavy pages) decode + draw work.
                val page = renderer.openPage(pageToLoad)
                val renderScale = (canvasWidth.toFloat() / page.width) * 2f  // 2x: crisp display without excessive memory
                val bmpW = (page.width * renderScale).toInt().coerceAtLeast(1)
                val bmpH = (page.height * renderScale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                runOnUiThread {
                    if (pageToLoad == currentPage) {
                        pdfCanvas.setPageBitmap(bmp)
                        annotationFiles[currentPage]?.let { pdfCanvas.loadAnnotations(it) } ?: pdfCanvas.clearAnnotations()
                        tvPageInfo.text = "${currentPage + 1} / $totalPages"
                    } else {
                        // User already navigated away before this render finished - discard it.
                        bmp.recycle()
                    }
                }
            } catch (e: OutOfMemoryError) {
                runOnUiThread { Toast.makeText(this, "Page too large to render", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Failed to render page: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun saveCurrentAnnotations() {
        val folder = File(filesDir, "pdf_annotations").also { it.mkdirs() }
        val name = pdfFile?.nameWithoutExtension ?: "pdf"
        val f = File(folder, "${name}_page${currentPage}.eng")
        pdfCanvas.saveAnnotations(f.absolutePath)
        annotationFiles[currentPage] = f.absolutePath
    }

    private fun sendSnipToNote(bmp: Bitmap) {
        try {
            val folder = File(filesDir, "images").also { it.mkdirs() }
            val out = File(folder, "snip_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val intent = Intent()
            intent.putExtra("snip_image_path", out.absolutePath)
            intent.putExtra("snip_width", bmp.width)
            intent.putExtra("snip_height", bmp.height)
            setResult(RESULT_OK, intent)
            Toast.makeText(this, "Snip sent to note!", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Snip failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { super.onDestroy(); pdfRenderer?.close() }
}

// ──────────────────────────────────────────────────────────────────
//  Snip overlay: user draws AND resizes a rectangle before confirming
// ──────────────────────────────────────────────────────────────────
class SnipOverlayView(context: Context) : View(context) {
    var onSnipSelected: ((RectF) -> Unit)? = null

    private var left = 0f; private var top = 0f; private var right = 0f; private var bottom = 0f
    private var hasSelection = false
    private var dragging = false
    private var activeHandle = -1 // -1=none, 0=TL,1=TR,2=BL,3=BR, 4=move, 5=creating new
    private var lastX = 0f; private var lastY = 0f

    private val dimPaint = Paint().apply { color = Color.parseColor("#88000000"); style = Paint.Style.FILL }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#8D6E63"); style = Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 6f), 0f)
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    private val handleBorderPaint = Paint().apply { color = Color.parseColor("#8D6E63"); style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true }
    private val instructPaint = Paint().apply {
        color = Color.WHITE; textSize = 32f; isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val confirmBgPaint = Paint().apply { color = Color.parseColor("#8D6E63"); style = Paint.Style.FILL; isAntiAlias = true }
    private val confirmTextPaint = Paint().apply { color = Color.WHITE; textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

    private val handleRadius = 14f
    private val handleTouchRadius = 50f // large invisible touch target

    fun reset() { hasSelection = false; dragging = false; activeHandle = -1; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasSelection) {
            canvas.drawColor(Color.parseColor("#44000000"))
            canvas.drawText("Draw a rectangle to select an area", 40f, height / 2f, instructPaint)
            return
        }
        val l = minOf(left, right); val t = minOf(top, bottom)
        val r = maxOf(left, right); val b = maxOf(top, bottom)

        canvas.drawRect(0f, 0f, width.toFloat(), t, dimPaint)
        canvas.drawRect(0f, b, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, t, l, b, dimPaint)
        canvas.drawRect(r, t, width.toFloat(), b, dimPaint)
        canvas.drawRect(l, t, r, b, borderPaint)

        for ((hx, hy) in listOf(l to t, r to t, l to b, r to b)) {
            canvas.drawCircle(hx, hy, handleRadius, handlePaint)
            canvas.drawCircle(hx, hy, handleRadius, handleBorderPaint)
        }

        if (!dragging && r - l > 30f && b - t > 30f) {
            val cx = (l + r) / 2f
            val btnY = (b + 24f).coerceAtMost(height - 90f)
            canvas.drawRoundRect(RectF(cx - 110f, btnY, cx + 110f, btnY + 72f), 36f, 36f, confirmBgPaint)
            canvas.drawText("✓  Confirm", cx, btnY + 48f, confirmTextPaint)
        }
    }

    private fun hitHandle(x: Float, y: Float): Int {
        val l = minOf(left, right); val t = minOf(top, bottom); val r = maxOf(left, right); val b = maxOf(top, bottom)
        if (dist(x, y, l, t) <= handleTouchRadius) return 0
        if (dist(x, y, r, t) <= handleTouchRadius) return 1
        if (dist(x, y, l, b) <= handleTouchRadius) return 2
        if (dist(x, y, r, b) <= handleTouchRadius) return 3
        return -1
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hasSelection) {
                    val h = hitHandle(x, y)
                    if (h >= 0) { activeHandle = h; dragging = true; lastX = x; lastY = y; return true }
                    val l = minOf(left, right); val t = minOf(top, bottom); val r = maxOf(left, right); val b = maxOf(top, bottom)
                    // Confirm button area
                    val cx = (l + r) / 2f; val btnY = (b + 24f).coerceAtMost(height - 90f)
                    if (x in (cx - 110f)..(cx + 110f) && y in btnY..(btnY + 72f)) {
                        onSnipSelected?.invoke(RectF(l, t, r, b)); return true
                    }
                    if (x in l..r && y in t..b) { activeHandle = 4; dragging = true; lastX = x; lastY = y; return true }
                }
                // Start a new selection
                left = x; top = y; right = x; bottom = y; hasSelection = true; dragging = true; activeHandle = 5
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                when (activeHandle) {
                    5 -> { right = x; bottom = y }
                    0 -> { left = x; top = y }
                    1 -> { right = x; top = y }
                    2 -> { left = x; bottom = y }
                    3 -> { right = x; bottom = y }
                    4 -> { val dx = x - lastX; val dy = y - lastY; left += dx; top += dy; right += dx; bottom += dy; lastX = x; lastY = y }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> { dragging = false; activeHandle = -1; invalidate() }
        }
        return true
    }
}

// ──────────────────────────────────────────────────────────────────
//  PdfAnnotationView - tracks exact page screen rect for snip mapping
// ──────────────────────────────────────────────────────────────────
enum class PdfTool { SELECT, PEN, HIGHLIGHT, TEXT, ERASER }
data class PdfStroke(val points: MutableList<Float>, val color: Int, val width: Float, val alpha: Int)

class PdfAnnotationView(context: Context) : View(context) {
    var currentTool = PdfTool.SELECT
    var onPageSwipe: ((Int) -> Unit)? = null // +1 next, -1 prev
    private var pageBitmap: Bitmap? = null
    private val strokes = mutableListOf<PdfStroke>()
    private var currentStroke: PdfStroke? = null
    private var scaleFactor = 1f
    private var translateX = 0f; private var translateY = 0f
    private var prevFocusX = 0f; private var prevFocusY = 0f
    private var twoFingerActive = false
    private var swipeStartX = 0f; private var swipeStartY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { prevFocusX = d.focusX; prevFocusY = d.focusY; return true }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val ns = (scaleFactor * d.scaleFactor).coerceIn(0.5f, 5f); val f = ns / scaleFactor
            translateX = d.focusX - (d.focusX - translateX) * f; translateY = d.focusY - (d.focusY - translateY) * f; scaleFactor = ns
            translateX += d.focusX - prevFocusX; translateY += d.focusY - prevFocusY
            prevFocusX = d.focusX; prevFocusY = d.focusY; invalidate(); return true
        }
    })

    fun setPageBitmap(bmp: Bitmap) {
        // Recycle the previous page's bitmap before dropping the reference - without this,
        // paging through a very large PDF (thousands of pages) over a long session accumulates
        // un-recycled bitmaps faster than garbage collection can keep up, eventually causing an
        // OutOfMemoryError.
        val old = pageBitmap
        pageBitmap = bmp; scaleFactor = 1f; translateX = 0f; translateY = 0f; invalidate()
        if (old != null && old !== bmp && !old.isRecycled) old.recycle()
    }
    fun clearAnnotations() { strokes.clear(); invalidate() }
    fun getPageBitmap(): Bitmap? = pageBitmap

    // Returns the page's current on-screen rectangle (accounting for pan/zoom), used to map snip coords precisely
    fun getPageScreenRect(): RectF? {
        val bmp = pageBitmap ?: return null
        // Page is drawn at base scale = width/bmp.width (to fit view width) then further scaleFactor/translate applied
        val baseScale = width.toFloat() / bmp.width
        val totalScale = baseScale * scaleFactor
        val pageW = bmp.width * totalScale
        val pageH = bmp.height * totalScale
        return RectF(translateX, translateY, translateX + pageW, translateY + pageH)
    }

    fun saveAnnotations(path: String) {
        File(path).writeText(strokes.joinToString("\n") { "${it.color}|${it.width}|${it.alpha}|${it.points.joinToString(",")}" })
    }

    fun loadAnnotations(path: String) {
        strokes.clear()
        try {
            File(path).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val p = line.split("|"); if (p.size < 4) return@forEachLine
                val pts = if (p[3].isBlank()) mutableListOf() else p[3].split(",").mapNotNull { it.toFloatOrNull() }.toMutableList()
                strokes.add(PdfStroke(pts, p[0].toInt(), p[1].toFloat(), p[2].toInt()))
            }
        } catch (e: Exception) {}
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#EDEAE3"))
        val bmp = pageBitmap
        if (bmp != null) {
            val baseScale = width.toFloat() / bmp.width
            canvas.save()
            canvas.translate(translateX, translateY)
            canvas.scale(baseScale * scaleFactor, baseScale * scaleFactor)
            canvas.drawBitmap(bmp, 0f, 0f, null)
            for (s in strokes) drawStroke(canvas, s)
            currentStroke?.let { drawStroke(canvas, it) }
            canvas.restore()
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: PdfStroke) {
        if (stroke.points.size < 4) return
        val p = Paint(); p.color = stroke.color; p.alpha = stroke.alpha; p.strokeWidth = stroke.width; p.style = Paint.Style.STROKE; p.isAntiAlias = true; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path(); path.moveTo(stroke.points[0], stroke.points[1]); var i = 2; while (i + 1 < stroke.points.size) { path.lineTo(stroke.points[i], stroke.points[i + 1]); i += 2 }
        canvas.drawPath(path, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            twoFingerActive = true
            // Cancel any in-progress stroke immediately
            currentStroke = null; invalidate()
            scaleDetector.onTouchEvent(event)
            // Two-finger pan
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val fx = (event.getX(0) + event.getX(1)) / 2f
                val fy = (event.getY(0) + event.getY(1)) / 2f
                if (prevFocusX != 0f) { translateX += fx - prevFocusX; translateY += fy - prevFocusY; invalidate() }
                prevFocusX = fx; prevFocusY = fy
            } else { prevFocusX = 0f; prevFocusY = 0f }
            return true
        }
        // Keep blocking until all fingers lifted after two-finger gesture
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            twoFingerActive = false
        }
        if (twoFingerActive) return true

        val bmp = pageBitmap ?: return true
        val baseScale = width.toFloat() / bmp.width
        val totalScale = baseScale * scaleFactor
        val wx = (event.x - translateX) / totalScale; val wy = (event.y - translateY) / totalScale

        // SELECT mode: single-finger pans the page
        if (currentTool == PdfTool.SELECT) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { swipeStartX = event.x; swipeStartY = event.y; prevFocusX = event.x; prevFocusY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    translateX += event.x - prevFocusX; translateY += event.y - prevFocusY
                    prevFocusX = event.x; prevFocusY = event.y; invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - swipeStartX; val dy = event.y - swipeStartY
                    if (kotlin.math.abs(dx) > 80f && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                        onPageSwipe?.invoke(if (dx < 0) 1 else -1)
                    }
                    prevFocusX = 0f; prevFocusY = 0f
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (currentTool == PdfTool.ERASER) { eraseAt(wx, wy, totalScale); invalidate(); return true }
                val color = when (currentTool) { PdfTool.HIGHLIGHT -> Color.parseColor("#FFEB3B"); else -> Color.parseColor("#3B5BDB") }
                val width2 = when (currentTool) { PdfTool.HIGHLIGHT -> 20f; else -> 4f }
                val alpha = if (currentTool == PdfTool.HIGHLIGHT) 100 else 255
                currentStroke = PdfStroke(mutableListOf(wx, wy), color, width2, alpha)
            }
            MotionEvent.ACTION_MOVE -> { if (currentTool == PdfTool.ERASER) { eraseAt(wx, wy, totalScale); invalidate(); return true }; currentStroke?.points?.addAll(listOf(wx, wy)); invalidate() }
            MotionEvent.ACTION_UP -> { currentStroke?.let { strokes.add(it) }; currentStroke = null; invalidate() }
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float, totalScale: Float) {
        val r = 30f / totalScale
        strokes.removeAll { s -> var i = 0; while (i + 1 < s.points.size) { val dx = s.points[i] - x; val dy = s.points[i + 1] - y; if (dx * dx + dy * dy <= r * r) return@removeAll true; i += 2 }; false }
    }
}
