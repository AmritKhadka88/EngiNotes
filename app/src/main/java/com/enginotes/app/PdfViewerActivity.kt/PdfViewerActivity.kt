package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
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
    private var annotationFiles = mutableMapOf<Int, String>() // page -> annotation file path

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        pdfCanvas = PdfAnnotationView(this)
        root.addView(pdfCanvas, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Bottom toolbar
        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#CC222222"))
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4))
        toolbar.gravity = Gravity.CENTER_VERTICAL

        fun btn(label: String, action: () -> Unit): Button {
            val b = Button(this); b.text = label; b.textSize = 13f; b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(3), 0, dp(3), 0); b.layoutParams = p
            b.setPadding(dp(10), dp(6), dp(10), dp(6)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { action() }; toolbar.addView(b); return b
        }

        btn("◀") { if (currentPage > 0) { saveCurrentAnnotations(); currentPage--; loadPage() } }
        tvPageInfo = TextView(this); tvPageInfo.setTextColor(Color.WHITE); tvPageInfo.textSize = 13f
        tvPageInfo.setPadding(dp(8), 0, dp(8), 0)
        toolbar.addView(tvPageInfo)
        btn("▶") { if (currentPage < totalPages - 1) { saveCurrentAnnotations(); currentPage++; loadPage() } }

        btn("✏ Pen") { pdfCanvas.currentTool = PdfTool.PEN }
        btn("🖊 Hi") { pdfCanvas.currentTool = PdfTool.HIGHLIGHT }
        btn("✍ Text") { pdfCanvas.currentTool = PdfTool.TEXT }
        btn("⌫ Erase") { pdfCanvas.currentTool = PdfTool.ERASER }
        btn("💾 Save") { saveCurrentAnnotations(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        btn("✕") { saveCurrentAnnotations(); finish() }

        val tlp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        root.addView(toolbar, tlp)

        setContentView(root)

        val uriString = intent.getStringExtra("pdf_uri")
        val filePath = intent.getStringExtra("pdf_path")

        if (filePath != null) {
            pdfFile = File(filePath)
            openPdf(pdfFile!!)
        } else if (uriString != null) {
            val uri = Uri.parse(uriString)
            copyAndOpenPdf(uri)
        }
    }

    private fun copyAndOpenPdf(uri: Uri) {
        try {
            val folder = File(filesDir, "pdfs"); if (!folder.exists()) folder.mkdirs()
            val outFile = File(folder, "pdf_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            pdfFile = outFile
            openPdf(outFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPdf(file: File) {
        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fd)
            totalPages = pdfRenderer!!.pageCount
            currentPage = 0
            loadPage()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPage() {
        val renderer = pdfRenderer ?: return
        val page = renderer.openPage(currentPage)
        val scale = pdfCanvas.width.toFloat() / page.width
        val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
        val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        pdfCanvas.setPageBitmap(bmp)

        // Load saved annotations for this page
        val annotFile = annotationFiles[currentPage]
        if (annotFile != null) pdfCanvas.loadAnnotations(annotFile)
        else pdfCanvas.clearAnnotations()

        tvPageInfo.text = "${currentPage + 1} / $totalPages"
    }

    private fun saveCurrentAnnotations() {
        val folder = File(filesDir, "pdf_annotations"); if (!folder.exists()) folder.mkdirs()
        val pdfName = pdfFile?.nameWithoutExtension ?: "pdf"
        val annotFile = File(folder, "${pdfName}_page${currentPage}.eng")
        pdfCanvas.saveAnnotations(annotFile.absolutePath)
        annotationFiles[currentPage] = annotFile.absolutePath
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
    }
}

enum class PdfTool { PEN, HIGHLIGHT, TEXT, ERASER }

data class PdfStroke(val points: MutableList<Float>, val color: Int, val width: Float, val alpha: Int)

class PdfAnnotationView(context: Context) : View(context) {
    var currentTool = PdfTool.PEN
    private var pageBitmap: Bitmap? = null
    private val strokes = mutableListOf<PdfStroke>()
    private var currentStroke: PdfStroke? = null
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var prevFocusX = 0f; private var prevFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { prevFocusX = d.focusX; prevFocusY = d.focusY; return true }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val ns = (scaleFactor * d.scaleFactor).coerceIn(0.5f, 5f)
            val f = ns / scaleFactor
            translateX = d.focusX - (d.focusX - translateX) * f
            translateY = d.focusY - (d.focusY - translateY) * f
            scaleFactor = ns
            translateX += d.focusX - prevFocusX; translateY += d.focusY - prevFocusY
            prevFocusX = d.focusX; prevFocusY = d.focusY
            invalidate(); return true
        }
    })

    fun setPageBitmap(bmp: Bitmap) { pageBitmap = bmp; scaleFactor = 1f; translateX = 0f; translateY = 0f; invalidate() }
    fun clearAnnotations() { strokes.clear(); invalidate() }

    fun saveAnnotations(path: String) {
        val sb = StringBuilder()
        for (s in strokes) sb.append("${s.color}|${s.width}|${s.alpha}|${s.points.joinToString(",")}\n")
        File(path).writeText(sb.toString())
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
        canvas.drawColor(Color.parseColor("#AAAAAA"))
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        pageBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (stroke in strokes) drawStroke(canvas, stroke)
        currentStroke?.let { drawStroke(canvas, it) }
        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, stroke: PdfStroke) {
        if (stroke.points.size < 4) return
        val p = Paint(); p.color = stroke.color; p.alpha = stroke.alpha
        p.strokeWidth = stroke.width; p.style = Paint.Style.STROKE; p.isAntiAlias = true
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = android.graphics.Path()
        path.moveTo(stroke.points[0], stroke.points[1])
        var i = 2; while (i + 1 < stroke.points.size) { path.lineTo(stroke.points[i], stroke.points[i + 1]); i += 2 }
        canvas.drawPath(path, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) { scaleDetector.onTouchEvent(event); return true }
        val wx = (event.x - translateX) / scaleFactor
        val wy = (event.y - translateY) / scaleFactor
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val color = when (currentTool) { PdfTool.PEN -> Color.BLUE; PdfTool.HIGHLIGHT -> Color.YELLOW; else -> Color.RED }
                val width = when (currentTool) { PdfTool.HIGHLIGHT -> 20f; PdfTool.ERASER -> 30f; else -> 4f }
                val alpha = if (currentTool == PdfTool.HIGHLIGHT) 100 else 255
                if (currentTool == PdfTool.ERASER) { eraseAt(wx, wy); invalidate(); return true }
                currentStroke = PdfStroke(mutableListOf(wx, wy), color, width, alpha)
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == PdfTool.ERASER) { eraseAt(wx, wy); invalidate(); return true }
                currentStroke?.points?.addAll(listOf(wx, wy)); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentStroke?.let { strokes.add(it) }; currentStroke = null; invalidate()
            }
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = 30f / scaleFactor
        strokes.removeAll { stroke ->
            var i = 0
            while (i + 1 < stroke.points.size) {
                val dx = stroke.points[i] - x; val dy = stroke.points[i + 1] - y
                if (kotlin.math.sqrt(dx * dx + dy * dy) <= r) return@removeAll true
                i += 2
            }
            false
        }
    }
}
