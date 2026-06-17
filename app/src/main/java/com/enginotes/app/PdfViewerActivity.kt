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
    private val annotationFiles = mutableMapOf<Int, String>()

    // Snipping state
    private var isSnipMode = false
    private var snipStart: PointF? = null
    private var snipEnd: PointF? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        pdfCanvas = PdfAnnotationView(this)
        root.addView(pdfCanvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Snip overlay (drawn on top of PDF during snip mode)
        val snipOverlay = SnipOverlayView(this)
        snipOverlay.visibility = View.GONE
        root.addView(snipOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Bottom toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC1A1A2E"))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }

        fun btn(label: String, action: () -> Unit): Button {
            return Button(this).apply {
                text = label; textSize = 12f; setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#55FFFFFF"))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(dp(2), 0, dp(2), 0); layoutParams = p
                setPadding(dp(8), dp(4), dp(8), dp(4)); minWidth = 0; minimumWidth = 0
                setOnClickListener { action() }
                toolbar.addView(this)
            }
        }

        btn("◀") { if (currentPage > 0) { saveCurrentAnnotations(); currentPage--; loadPage() } }
        tvPageInfo = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; setPadding(dp(8), 0, dp(8), 0)
            toolbar.addView(this)
        }
        btn("▶") { if (currentPage < totalPages - 1) { saveCurrentAnnotations(); currentPage++; loadPage() } }

        val penBtn    = btn("✏ Pen")  { pdfCanvas.currentTool = PdfTool.PEN;       setSnipMode(false, snipOverlay) }
        val hiBtn     = btn("🖊 Hi")   { pdfCanvas.currentTool = PdfTool.HIGHLIGHT;  setSnipMode(false, snipOverlay) }
        val txtBtn    = btn("T Text") { pdfCanvas.currentTool = PdfTool.TEXT;      setSnipMode(false, snipOverlay) }
        val erBtn     = btn("⌫ Erase"){ pdfCanvas.currentTool = PdfTool.ERASER;    setSnipMode(false, snipOverlay) }

        // Snip button
        val snipBtn = btn("✂ Snip") {
            val entering = !isSnipMode
            setSnipMode(entering, snipOverlay)
            if (entering) Toast.makeText(this, "Draw a rectangle over the area to snip", Toast.LENGTH_SHORT).show()
        }

        btn("💾 Save") { saveCurrentAnnotations(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        btn("✕") { saveCurrentAnnotations(); finish() }

        val tlp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        root.addView(toolbar, tlp)

        setContentView(root)

        // Wire snip overlay touch
        snipOverlay.onSnipSelected = { rect ->
            // rect is in screen coords over the PDF canvas
            val pageBmp = pdfCanvas.getPageBitmap() ?: return@onSnipSelected
            val scale = pdfCanvas.getPageScale()
            val sx = (rect.left / scale).toInt().coerceIn(0, pageBmp.width)
            val sy = (rect.top  / scale).toInt().coerceIn(0, pageBmp.height)
            val sw = ((rect.width())  / scale).toInt().coerceAtLeast(1).coerceAtMost(pageBmp.width  - sx)
            val sh = ((rect.height()) / scale).toInt().coerceAtLeast(1).coerceAtMost(pageBmp.height - sy)
            val cropped = Bitmap.createBitmap(pageBmp, sx, sy, sw, sh)
            sendSnipToNote(cropped)
            setSnipMode(false, snipOverlay)
        }

        val uriString = intent.getStringExtra("pdf_uri")
        val filePath  = intent.getStringExtra("pdf_path")
        when {
            filePath != null -> openPdf(File(filePath))
            uriString != null -> copyAndOpenPdf(Uri.parse(uriString))
        }
    }

    private fun setSnipMode(on: Boolean, overlay: SnipOverlayView) {
        isSnipMode = on
        overlay.visibility = if (on) View.VISIBLE else View.GONE
        pdfCanvas.isEnabled = !on
    }

    private fun copyAndOpenPdf(uri: Uri) {
        try {
            val folder = File(filesDir, "pdfs").also { it.mkdirs() }
            val out = File(folder, "pdf_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)?.use { ins -> FileOutputStream(out).use { ins.copyTo(it) } }
            pdfFile = out; openPdf(out)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPdf(file: File) {
        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fd)
            totalPages = pdfRenderer!!.pageCount; currentPage = 0; loadPage()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPage() {
        val renderer = pdfRenderer ?: return
        val page = renderer.openPage(currentPage)
        val scale = pdfCanvas.width.toFloat() / page.width
        val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
        val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        pdfCanvas.setPageBitmap(bmp, scale)
        annotationFiles[currentPage]?.let { pdfCanvas.loadAnnotations(it) } ?: pdfCanvas.clearAnnotations()
        tvPageInfo.text = "${currentPage + 1} / $totalPages"
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
            // Return to whoever launched us (MainActivity's chartLauncher or pickPdfLauncher)
            val intent = Intent()
            intent.putExtra("snip_image_path", out.absolutePath)
            setResult(RESULT_OK, intent)
            Toast.makeText(this, "Snip sent to note! Close PDF to place it.", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Snip failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { super.onDestroy(); pdfRenderer?.close() }
}

// ──────────────────────────────────────────────────────────────────
//  Snip overlay: user draws a rectangle, callback fires with RectF
// ──────────────────────────────────────────────────────────────────
class SnipOverlayView(context: Context) : View(context) {
    var onSnipSelected: ((RectF) -> Unit)? = null

    private var startX = 0f; private var startY = 0f
    private var endX   = 0f; private var endY   = 0f
    private var dragging = false

    private val dimPaint = Paint().apply { color = Color.parseColor("#66000000"); style = Paint.Style.FILL }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#2196F3"); style = Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 6f), 0f)
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val instructPaint = Paint().apply {
        color = Color.WHITE; textSize = 36f; isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!dragging && startX == endX) {
            // Show instruction
            canvas.drawColor(Color.parseColor("#44000000"))
            canvas.drawText("Draw to select area", 40f, height / 2f, instructPaint)
            return
        }
        val l = minOf(startX, endX); val t = minOf(startY, endY)
        val r = maxOf(startX, endX); val b = maxOf(startY, endY)
        // Dim everything outside selection
        canvas.drawRect(0f, 0f, width.toFloat(), t, dimPaint)
        canvas.drawRect(0f, b, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, t, l, b, dimPaint)
        canvas.drawRect(r, t, width.toFloat(), b, dimPaint)
        // Selection border
        canvas.drawRect(l, t, r, b, borderPaint)
        // Corner handles
        for ((hx, hy) in listOf(l to t, r to t, l to b, r to b)) {
            canvas.drawCircle(hx, hy, 10f, handlePaint); canvas.drawCircle(hx, hy, 10f, borderPaint)
        }
        if (!dragging && r - l > 20f && b - t > 20f) {
            val tp = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true; setShadowLayer(3f,0f,0f,Color.BLACK) }
            canvas.drawText("Tap inside to snip", l + 8f, t - 12f, tp)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // If we already have a selection and user taps inside it → confirm snip
                if (!dragging && startX != endX) {
                    val l = minOf(startX,endX); val t2 = minOf(startY,endY)
                    val r = maxOf(startX,endX); val b = maxOf(startY,endY)
                    if (event.x in l..r && event.y in t2..b) {
                        onSnipSelected?.invoke(RectF(l,t2,r,b)); return true
                    }
                }
                startX = event.x; startY = event.y
                endX = event.x; endY = event.y; dragging = true; invalidate()
            }
            MotionEvent.ACTION_MOVE -> { endX = event.x; endY = event.y; invalidate() }
            MotionEvent.ACTION_UP -> { endX = event.x; endY = event.y; dragging = false; invalidate() }
        }
        return true
    }
}

// ──────────────────────────────────────────────────────────────────
//  PdfAnnotationView (unchanged from original + getPageBitmap/getPageScale)
// ──────────────────────────────────────────────────────────────────
enum class PdfTool { PEN, HIGHLIGHT, TEXT, ERASER }
data class PdfStroke(val points: MutableList<Float>, val color: Int, val width: Float, val alpha: Int)

class PdfAnnotationView(context: Context) : View(context) {
    var currentTool = PdfTool.PEN
    private var pageBitmap: Bitmap? = null
    private var pageScale: Float = 1f
    private val strokes = mutableListOf<PdfStroke>()
    private var currentStroke: PdfStroke? = null
    private var scaleFactor = 1f
    private var translateX = 0f; private var translateY = 0f
    private var prevFocusX = 0f; private var prevFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { prevFocusX=d.focusX; prevFocusY=d.focusY; return true }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val ns=(scaleFactor*d.scaleFactor).coerceIn(0.5f,5f); val f=ns/scaleFactor
            translateX=d.focusX-(d.focusX-translateX)*f; translateY=d.focusY-(d.focusY-translateY)*f; scaleFactor=ns
            translateX+=d.focusX-prevFocusX; translateY+=d.focusY-prevFocusY
            prevFocusX=d.focusX; prevFocusY=d.focusY; invalidate(); return true
        }
    })

    fun setPageBitmap(bmp: Bitmap, scale: Float=1f) { pageBitmap=bmp; pageScale=scale; scaleFactor=1f; translateX=0f; translateY=0f; invalidate() }
    fun clearAnnotations() { strokes.clear(); invalidate() }
    fun getPageBitmap(): Bitmap? = pageBitmap
    fun getPageScale(): Float = pageScale

    fun saveAnnotations(path: String) {
        File(path).writeText(strokes.joinToString("\n") { "${it.color}|${it.width}|${it.alpha}|${it.points.joinToString(",")}" })
    }

    fun loadAnnotations(path: String) {
        strokes.clear()
        try {
            File(path).forEachLine { line ->
                if(line.isBlank()) return@forEachLine
                val p=line.split("|"); if(p.size<4) return@forEachLine
                val pts=if(p[3].isBlank()) mutableListOf() else p[3].split(",").mapNotNull{it.toFloatOrNull()}.toMutableList()
                strokes.add(PdfStroke(pts,p[0].toInt(),p[1].toFloat(),p[2].toInt()))
            }
        } catch(e:Exception){}
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#AAAAAA"))
        canvas.save(); canvas.translate(translateX,translateY); canvas.scale(scaleFactor,scaleFactor)
        pageBitmap?.let{ canvas.drawBitmap(it,0f,0f,null) }
        for(s in strokes) drawStroke(canvas,s); currentStroke?.let{ drawStroke(canvas,it) }
        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, stroke: PdfStroke) {
        if(stroke.points.size<4) return
        val p=Paint(); p.color=stroke.color; p.alpha=stroke.alpha; p.strokeWidth=stroke.width; p.style=Paint.Style.STROKE; p.isAntiAlias=true; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
        val path=Path(); path.moveTo(stroke.points[0],stroke.points[1]); var i=2; while(i+1<stroke.points.size){ path.lineTo(stroke.points[i],stroke.points[i+1]); i+=2 }
        canvas.drawPath(path,p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.pointerCount>=2){ scaleDetector.onTouchEvent(event); return true }
        val wx=(event.x-translateX)/scaleFactor; val wy=(event.y-translateY)/scaleFactor
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN -> {
                if(currentTool==PdfTool.ERASER){ eraseAt(wx,wy); invalidate(); return true }
                val color=when(currentTool){ PdfTool.HIGHLIGHT->Color.YELLOW; else->Color.BLUE }
                val width=when(currentTool){ PdfTool.HIGHLIGHT->20f; else->4f }
                val alpha=if(currentTool==PdfTool.HIGHLIGHT) 100 else 255
                currentStroke=PdfStroke(mutableListOf(wx,wy),color,width,alpha)
            }
            MotionEvent.ACTION_MOVE -> { if(currentTool==PdfTool.ERASER){ eraseAt(wx,wy); invalidate(); return true }; currentStroke?.points?.addAll(listOf(wx,wy)); invalidate() }
            MotionEvent.ACTION_UP -> { currentStroke?.let{ strokes.add(it) }; currentStroke=null; invalidate() }
        }
        return true
    }

    private fun eraseAt(x:Float,y:Float) {
        val r=30f/scaleFactor
        strokes.removeAll{ s -> var i=0; while(i+1<s.points.size){ val dx=s.points[i]-x;val dy=s.points[i+1]-y; if(dx*dx+dy*dy<=r*r) return@removeAll true; i+=2 }; false }
    }
}
