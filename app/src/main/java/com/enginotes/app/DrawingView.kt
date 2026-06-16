package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

enum class Tool {
    SELECT, FILL, PEN, ERASER, LINE, RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE,
    TRIANGLE, DIAMOND, ARROW, STAR, PENTAGON, HEXAGON, CURVE, CROSS, ARC, TEXT, AUTOSELECT, EXPORT_WINDOW
}
enum class PaperType { BLANK, BLANK_COLORED, LINED, GRID, DOTS, ENGINEERING }
enum class EraserMode { OBJECT, AREA }
enum class AutoSelectShape { RECTANGLE, FREEFORM }
enum class AutoSelectDivide { WHOLE, DIVIDED }
enum class CanvasMode { INFINITE, FIXED, PAGINATED }
enum class Orientation { PORTRAIT, LANDSCAPE }

enum class PaperSizeOption(val widthMM: Float, val heightMM: Float) {
    A4(210f, 297f), LETTER(215.9f, 279.4f), A3(297f, 420f), A5(148f, 210f),
    LEGAL(215.9f, 355.6f), TABLOID(279.4f, 431.8f), A2(420f, 594f),
    A1(594f, 841f), A0(841f, 1189f), B4(250f, 353f), B5(176f, 250f), EXECUTIVE(184.1f, 266.7f)
}

val SHAPE_TOOLS = setOf(
    Tool.LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE,
    Tool.TRIANGLE, Tool.DIAMOND, Tool.ARROW, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CURVE, Tool.CROSS
)
val CLOSED_SHAPES = setOf(
    Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE,
    Tool.TRIANGLE, Tool.DIAMOND, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON
)
val BBOX_RESIZE_SHAPES = setOf(
    Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.ELLIPSE, Tool.TRIANGLE,
    Tool.DIAMOND, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CROSS
)
val ENDPOINT_RESIZE_SHAPES = setOf(Tool.LINE, Tool.CIRCLE, Tool.ARROW, Tool.CURVE)

data class TextSpanData(val start: Int, val end: Int, val type: Char, val value: Int)

// Thread pool for image loading — max 3 concurrent loads
private val imageLoadExecutor = ThreadPoolExecutor(
    2, 3, 60L, TimeUnit.SECONDS, LinkedBlockingQueue()
)

// Global bitmap cache — limited size LRU
private val bitmapCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    private val MAX_SIZE = 50 * 1024 * 1024 // 50MB
    private var currentSize = 0L
    override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>): Boolean {
        return currentSize > MAX_SIZE
    }
    fun putBitmap(key: String, bmp: Bitmap) {
        currentSize += bmp.byteCount
        put(key, bmp)
    }
    fun getBitmap(key: String): Bitmap? = get(key)
}

class StrokeData(
    val type: Tool, val points: MutableList<Float>,
    var color: Int, var strokeWidth: Float, var fill: Boolean, var rotation: Float = 0f
) {
    fun buildPath(): Path {
        val path = Path()
        if (type == Tool.PEN || type == Tool.ERASER) {
            if (points.size >= 2) {
                path.moveTo(points[0], points[1])
                var i = 2
                while (i + 1 < points.size) { path.lineTo(points[i], points[i + 1]); i += 2 }
            }
            return path
        }
        if (type == Tool.ARC) {
            if (points.size >= 2) {
                path.moveTo(points[0], points[1])
                if (points.size == 4) {
                    path.lineTo(points[2], points[3])
                } else if (points.size > 4) {
                    var i = 0
                    while (i + 3 < points.size) {
                        val x0 = if (i == 0) points[0] else points[i - 2]
                        val y0 = if (i == 0) points[1] else points[i - 1]
                        val x1 = points[i]; val y1 = points[i + 1]
                        val x2 = points[i + 2]; val y2 = points[i + 3]
                        val x3 = if (i + 4 < points.size) points[i + 4] else x2
                        val y3 = if (i + 5 < points.size) points[i + 5] else y2
                        path.cubicTo(
                            x1 + (x2 - x0) / 6f, y1 + (y2 - y0) / 6f,
                            x2 - (x3 - x1) / 6f, y2 - (y3 - y1) / 6f,
                            x2, y2
                        )
                        i += 2
                    }
                }
            }
            return path
        }
        if (points.size < 4) return path
        val x1 = points[0]; val y1 = points[1]; val x2 = points[2]; val y2 = points[3]
        val left = minOf(x1, x2); val right = maxOf(x1, x2)
        val top = minOf(y1, y2); val bottom = maxOf(y1, y2)
        val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
        when (type) {
            Tool.LINE -> { path.moveTo(x1, y1); path.lineTo(x2, y2) }
            Tool.RECTANGLE -> path.addRect(RectF(left, top, right, bottom), Path.Direction.CW)
            Tool.ROUNDED_RECT -> {
                val rx = ((right - left) * 0.15f).coerceAtMost(40f)
                val ry = ((bottom - top) * 0.15f).coerceAtMost(40f)
                path.addRoundRect(RectF(left, top, right, bottom), rx, ry, Path.Direction.CW)
            }
            Tool.CIRCLE -> {
                val r = kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                path.addCircle(x1, y1, r, Path.Direction.CW)
            }
            Tool.ELLIPSE -> path.addOval(RectF(left, top, right, bottom), Path.Direction.CW)
            Tool.TRIANGLE -> { path.moveTo(cx, top); path.lineTo(right, bottom); path.lineTo(left, bottom); path.close() }
            Tool.DIAMOND -> { path.moveTo(cx, top); path.lineTo(right, cy); path.lineTo(cx, bottom); path.lineTo(left, cy); path.close() }
            Tool.ARROW -> {
                path.moveTo(x1, y1); path.lineTo(x2, y2)
                val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                val al = 20f; val aa = Math.PI / 7
                path.moveTo(x2, y2); path.lineTo(x2 - (al * kotlin.math.cos(angle - aa)).toFloat(), y2 - (al * kotlin.math.sin(angle - aa)).toFloat())
                path.moveTo(x2, y2); path.lineTo(x2 - (al * kotlin.math.cos(angle + aa)).toFloat(), y2 - (al * kotlin.math.sin(angle + aa)).toFloat())
            }
            Tool.CURVE -> {
                val dx = x2 - x1; val dy = y2 - y1
                path.moveTo(x1, y1); path.quadTo(cx - dy * 0.25f, cy + dx * 0.25f, x2, y2)
            }
            Tool.CROSS -> { path.moveTo(left, cy); path.lineTo(right, cy); path.moveTo(cx, top); path.lineTo(cx, bottom) }
            Tool.STAR -> addPolygon(path, left, top, right, bottom, 5, true)
            Tool.PENTAGON -> addPolygon(path, left, top, right, bottom, 5, false)
            Tool.HEXAGON -> addPolygon(path, left, top, right, bottom, 6, false)
            else -> {}
        }
        return path
    }

    private fun addPolygon(path: Path, left: Float, top: Float, right: Float, bottom: Float, sides: Int, isStar: Boolean) {
        val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
        val rx = (right - left) / 2f; val ry = (bottom - top) / 2f
        val count = if (isStar) sides * 2 else sides
        val step = 2 * Math.PI / count; val start = -Math.PI / 2
        for (i in 0 until count) {
            val r = if (isStar && i % 2 == 1) 0.5f else 1f
            val a = start + i * step
            val px = cx + (kotlin.math.cos(a) * rx * r).toFloat()
            val py = cy + (kotlin.math.sin(a) * ry * r).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    fun toPaint(): Paint {
        val p = Paint()
        p.color = color
        p.style = if (fill && CLOSED_SHAPES.contains(type)) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        p.strokeWidth = strokeWidth; p.isAntiAlias = true
        p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        return p
    }
}

class StrokeItem(val data: StrokeData, var path: Path, var paint: Paint)

class TextItem(var text: String, var x: Float, var y: Float, var color: Int, var size: Float, var rotation: Float) {
    var spans: MutableList<TextSpanData> = mutableListOf()
    var isEditing: Boolean = false
}

class ImageItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float, var rotation: Float) {
    // bitmap loaded async — always use getBitmap()
    @Volatile var bitmap: Bitmap? = null
    @Volatile var loading: Boolean = false
}

class FillItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float) {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var loading: Boolean = false
}

class DrawingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val ctx = context
    private val actions = mutableListOf<Any>()
    private val redoStack = mutableListOf<Any>()
    private var currentItem: StrokeItem? = null

    var currentTool: Tool = Tool.PEN
        set(value) {
            if (field == Tool.SELECT && value != Tool.SELECT) selectedItem = null
            if (field == Tool.ARC && value != Tool.ARC) activeArcItem = null
            if (field == Tool.AUTOSELECT && value != Tool.AUTOSELECT) {
                selectedGroup = null; regionPath = null; regionStart = null
            }
            field = value; invalidate()
        }

    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var eraserSize: Float = 40f
    var eraserMode: EraserMode = EraserMode.OBJECT
    var fillShapes: Boolean = false
    var fillColor: Int = Color.RED
    var arcDivisions: Int = 3
    var paperType: PaperType = PaperType.GRID
    var paperColor: Int = Color.parseColor("#FFFDE7")
    var defaultTextSize: Float = 16f * 1.333f

    var autoSelectShape: AutoSelectShape = AutoSelectShape.RECTANGLE
    var autoSelectDivide: AutoSelectDivide = AutoSelectDivide.WHOLE
    var canvasMode: CanvasMode = CanvasMode.INFINITE
    var paperSize: PaperSizeOption = PaperSizeOption.A4
    var pageOrientation: Orientation = Orientation.PORTRAIT

    var selectedGroup: MutableList<Any>? = null
    private var regionPath: Path? = null
    private var regionStart: Pair<Float, Float>? = null
    private var groupMoveStartX = 0f; private var groupMoveStartY = 0f
    private var groupResizeHandle = -1
    private var groupResizeOrigBounds = FloatArray(4)
    private var groupResizeItemSnapshots: List<FloatArray?> = emptyList()

    var selectedItem: Any? = null

    private enum class HandleType { NONE, MOVE, ROTATE, TL, TM, TR, ML, MR, BL, BM, BR }

    private var activeHandle = HandleType.NONE
    private var dragStartWorldX = 0f; private var dragStartWorldY = 0f
    private var dragStartAngle = 0f; private var dragStartRotation = 0f
    private var dragStartPivotX = 0f; private var dragStartPivotY = 0f

    // FIX: Track previous world position during resize drag (not local coords)
    private var resizePrevWorldX = 0f; private var resizePrevWorldY = 0f

    private var activeArcItem: StrokeItem? = null
    private var arcDragPointIndex = -1

    private var activeTableItem: TableItem? = null
    private var tableDragRowBorder = -1
    private var tableDragColBorder = -1
    private var tableDragStartY = 0f
    private var tableDragStartX = 0f
    private var tableDragOrigSize = 0f
    private var tableSelStart: Pair<Int, Int>? = null
    private var tableSelEnd: Pair<Int, Int>? = null

    var onTextEditRequest: ((TextItem?, Float, Float, Float, Float) -> Unit)? = null
    var onTableCellEditRequest: ((TableItem, Int, Int, Float, Float) -> Unit)? = null
    var isTextEditorOpen: Boolean = false
    var onScaleChanged: ((Float) -> Unit)? = null
    var onCanvasTransformed: (() -> Unit)? = null

    private var exportWindowStart: Pair<Float, Float>? = null
    private var exportWindowEnd: Pair<Float, Float>? = null
    var onExportWindowSelected: ((Float, Float, Float, Float) -> Unit)? = null

    private var scaleFactor = 1f
    private var translateX = 0f; private var translateY = 0f
    private var prevFocusX = 0f; private var prevFocusY = 0f
    private var hoverX: Float? = null; private var hoverY: Float? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            prevFocusX = detector.focusX; prevFocusY = detector.focusY; return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val minScale = if (canvasMode != CanvasMode.INFINITE && width > 0 && height > 0) {
                minOf(width.toFloat() / (pageWidthPx() * 1.5f), height.toFloat() / (pageHeightPx() * 1.5f)).coerceAtLeast(0.05f)
            } else 0.2f
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, 6f)
            val factor = newScale / scaleFactor
            translateX = detector.focusX - (detector.focusX - translateX) * factor
            translateY = detector.focusY - (detector.focusY - translateY) * factor
            scaleFactor = newScale
            translateX += detector.focusX - prevFocusX
            translateY += detector.focusY - prevFocusY
            prevFocusX = detector.focusX; prevFocusY = detector.focusY
            clampTranslation()
            onScaleChanged?.invoke(scaleFactor)
            onCanvasTransformed?.invoke()
            invalidate(); return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            when (currentTool) {
                Tool.TEXT -> {
                    val hit = findTextItemAt(wx, wy)
                    if (hit != null) {
                        selectedItem = hit; currentTool = Tool.SELECT; invalidate()
                    } else {
                        selectedItem = null
                        onTextEditRequest?.invoke(null, e.x, e.y, wx, wy)
                    }
                }
                Tool.SELECT -> {
                    val hit = findTextItemAt(wx, wy)
                    if (hit != null && selectedItem === hit) {
                        hit.isEditing = true; invalidate()
                        onTextEditRequest?.invoke(hit, e.x, e.y, wx, wy)
                    }
                }
                Tool.FILL -> {
                    val item = findItemAt(wx, wy)
                    if (item is StrokeItem && CLOSED_SHAPES.contains(item.data.type)) {
                        item.data.fill = !item.data.fill; item.data.color = fillColor
                        item.paint = item.data.toPaint(); invalidate()
                    } else performFill(e.x, e.y)
                }
                else -> {}
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            val hit = findTextItemAt(wx, wy)
            if (hit != null) {
                hit.isEditing = true; invalidate()
                onTextEditRequest?.invoke(hit, e.x, e.y, wx, wy)
                return true
            }
            if (currentTool == Tool.TEXT) onTextEditRequest?.invoke(null, e.x, e.y, wx, wy)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (canvasMode != CanvasMode.INFINITE) {
                translateX -= distanceX; translateY -= distanceY
                clampTranslation()
                onScaleChanged?.invoke(scaleFactor)
                onCanvasTransformed?.invoke()
                invalidate(); return true
            }
            return false
        }
    })

    private fun clampTranslation() {
        if (canvasMode == CanvasMode.INFINITE) return
        val pw = pageWidthPx() * scaleFactor; val ph = pageHeightPx() * scaleFactor
        val margin = 20f
        val minTx = width - pw - margin; val maxTx = margin
        translateX = translateX.coerceIn(minTx.coerceAtMost(maxTx), maxTx)
        if (canvasMode == CanvasMode.FIXED) {
            val minTy = height - ph - margin; val maxTy = margin
            translateY = translateY.coerceIn(minTy.coerceAtMost(maxTy), maxTy)
        }
    }

    // FIX: Load bitmaps async to avoid ANR on image insert
    private fun loadBitmapAsync(path: String, onLoaded: (Bitmap?) -> Unit) {
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(path) }
        if (cached != null) { onLoaded(cached); return }
        imageLoadExecutor.execute {
            try {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeFile(path, this)
                    val maxDim = 2048
                    inSampleSize = 1
                    var w = outWidth; var h = outHeight
                    while (w > maxDim || h > maxDim) { inSampleSize *= 2; w /= 2; h /= 2 }
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.RGB_565 // Less memory than ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(path, opts)
                if (bmp != null) synchronized(bitmapCache) { bitmapCache.putBitmap(path, bmp) }
                post { onLoaded(bmp) }
            } catch (e: Exception) {
                post { onLoaded(null) }
            }
        }
    }

    private fun getOrLoadBitmap(item: ImageItem): Bitmap? {
        if (item.bitmap != null) return item.bitmap
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(item.path) }
        if (cached != null) { item.bitmap = cached; return cached }
        if (!item.loading) {
            item.loading = true
            loadBitmapAsync(item.path) { bmp ->
                item.bitmap = bmp; item.loading = false; invalidate()
            }
        }
        return null
    }

    private fun getOrLoadFillBitmap(item: FillItem): Bitmap? {
        if (item.bitmap != null) return item.bitmap
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(item.path) }
        if (cached != null) { item.bitmap = cached; return cached }
        if (!item.loading) {
            item.loading = true
            loadBitmapAsync(item.path) { bmp ->
                item.bitmap = bmp; item.loading = false; invalidate()
            }
        }
        return null
    }

    private fun drawActionItem(canvas: Canvas, action: Any, includeFills: Boolean) {
        when (action) {
            is TableItem -> action.draw(canvas, scaleFactor)
            is FillItem -> {
                if (!includeFills) return
                val bmp = getOrLoadFillBitmap(action) ?: return
                canvas.drawBitmap(bmp, null, RectF(action.x, action.y, action.x + action.w, action.y + action.h), null)
            }
            is StrokeItem -> {
                if (action.data.rotation != 0f) {
                    val b = getBounds(action)
                    if (b != null) {
                        val cx = (b[0] + b[2]) / 2f; val cy = (b[1] + b[3]) / 2f
                        canvas.save(); canvas.rotate(action.data.rotation, cx, cy)
                        canvas.drawPath(action.path, action.paint); canvas.restore()
                    } else canvas.drawPath(action.path, action.paint)
                } else canvas.drawPath(action.path, action.paint)
            }
            is TextItem -> { if (!action.isEditing) drawTextItem(canvas, action) }
            is ImageItem -> {
                val bmp = getOrLoadBitmap(action) ?: return
                canvas.save()
                canvas.translate(action.x + action.w / 2f, action.y + action.h / 2f)
                canvas.rotate(action.rotation)
                canvas.drawBitmap(bmp, null, RectF(-action.w / 2f, -action.h / 2f, action.w / 2f, action.h / 2f), null)
                canvas.restore()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        drawBackground(canvas)
        for (action in actions) drawActionItem(canvas, action, true)
        currentItem?.let { canvas.drawPath(it.path, it.paint) }
        drawSelection(canvas)
        drawArcHandles(canvas)
        drawAutoSelectOverlay(canvas)
        drawTableOverlay(canvas)
        drawExportWindowOverlay(canvas)
        canvas.restore()
        drawCursor(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (canvasMode != CanvasMode.INFINITE && width > 0 && height > 0 && changed) {
            val margin = 20f
            scaleFactor = (width.toFloat() - margin * 2f) / pageWidthPx()
            translateX = margin; translateY = margin
            clampTranslation(); invalidate()
        }
    }

    private fun drawArcHandles(canvas: Canvas) {
        if (currentTool != Tool.ARC) return
        val arc = activeArcItem ?: return
        val p = Paint(); p.color = Color.parseColor("#2196F3"); p.style = Paint.Style.FILL
        val r = 12f / scaleFactor; var i = 0
        while (i + 1 < arc.data.points.size) { canvas.drawCircle(arc.data.points[i], arc.data.points[i + 1], r, p); i += 2 }
    }

    private fun drawTextItem(canvas: Canvas, item: TextItem) {
        val tp = TextPaint(); tp.color = item.color; tp.textSize = item.size; tp.isAntiAlias = true
        val spannable = SpannableString(item.text)
        for (sp in item.spans) {
            val s = sp.start.coerceIn(0, item.text.length); val e = sp.end.coerceIn(s, item.text.length)
            if (s < e) when (sp.type) {
                'S' -> spannable.setSpan(StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'C' -> spannable.setSpan(ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'U' -> spannable.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'H' -> spannable.setSpan(BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val w = (tp.measureText(item.text).toInt() + item.size.toInt() + 10).coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(spannable, 0, spannable.length, tp, w).setIncludePad(true).build()
        canvas.save(); canvas.translate(item.x, item.y - layout.height)
        canvas.rotate(item.rotation, 0f, layout.height.toFloat()); layout.draw(canvas); canvas.restore()
    }

    private fun bboxHandlePositions(bounds: FloatArray): List<Pair<HandleType, Pair<Float, Float>>> {
        val cx = (bounds[0] + bounds[2]) / 2f; val cy = (bounds[1] + bounds[3]) / 2f
        return listOf(
            HandleType.TL to Pair(bounds[0], bounds[1]), HandleType.TM to Pair(cx, bounds[1]),
            HandleType.TR to Pair(bounds[2], bounds[1]), HandleType.ML to Pair(bounds[0], cy),
            HandleType.MR to Pair(bounds[2], cy), HandleType.BL to Pair(bounds[0], bounds[3]),
            HandleType.BM to Pair(cx, bounds[3]), HandleType.BR to Pair(bounds[2], bounds[3])
        )
    }

    private fun drawSelection(canvas: Canvas) {
        val item = selectedItem ?: return
        val bounds = getBounds(item) ?: return
        val rotation = getRotation(item); val (pivotX, pivotY) = getPivot(item, bounds)
        canvas.save(); canvas.rotate(rotation, pivotX, pivotY)
        val selP = Paint(); selP.color = Color.parseColor("#2196F3"); selP.style = Paint.Style.STROKE
        selP.strokeWidth = 2f / scaleFactor
        selP.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f/scaleFactor, 5f/scaleFactor), 0f)
        canvas.drawRect(bounds[0], bounds[1], bounds[2], bounds[3], selP)
        val hr = 18f / scaleFactor
        val hFill = Paint(); hFill.style = Paint.Style.FILL
        val hStroke = Paint(); hStroke.style = Paint.Style.STROKE
        hStroke.color = Color.parseColor("#2196F3"); hStroke.strokeWidth = 2f / scaleFactor
        val isBbox = item is ImageItem || item is TextItem || (item is StrokeItem && BBOX_RESIZE_SHAPES.contains(item.data.type))
        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)
        if (isBbox) {
            for ((_, pos) in bboxHandlePositions(bounds)) {
                hFill.color = Color.WHITE
                canvas.drawCircle(pos.first, pos.second, hr, hFill)
                canvas.drawCircle(pos.first, pos.second, hr, hStroke)
            }
        } else if (isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
            // FIX: Draw endpoint handles at actual point positions
            hFill.color = Color.WHITE
            val p0x = item.data.points[0]; val p0y = item.data.points[1]
            val p1x = item.data.points[2]; val p1y = item.data.points[3]
            canvas.drawCircle(p0x, p0y, hr, hFill); canvas.drawCircle(p0x, p0y, hr, hStroke)
            canvas.drawCircle(p1x, p1y, hr, hFill); canvas.drawCircle(p1x, p1y, hr, hStroke)
        }
        val canRotate = item is ImageItem || item is TextItem || (item is StrokeItem && item.data.type != Tool.PEN && item.data.type != Tool.ARC)
        if (canRotate) {
            val cx = (bounds[0] + bounds[2]) / 2f; val rotY = bounds[1] - 60f / scaleFactor
            val rotLinePaint = Paint(); rotLinePaint.color = Color.parseColor("#2196F3"); rotLinePaint.strokeWidth = 1.5f / scaleFactor
            canvas.drawLine(cx, bounds[1], cx, rotY, rotLinePaint)
            hFill.color = Color.parseColor("#4CAF50")
            canvas.drawCircle(cx, rotY, hr, hFill); canvas.drawCircle(cx, rotY, hr, hStroke)
            val rp = Paint(); rp.color = Color.WHITE; rp.textSize = hr * 1.2f; rp.textAlign = Paint.Align.CENTER; rp.isAntiAlias = true
            canvas.drawText("↻", cx, rotY + hr * 0.4f, rp)
        }
        hFill.color = Color.parseColor("#F44336")
        val delR = hr * 1.4f; val delX = bounds[2] + hr * 5f; val delY = bounds[1] - hr * 5f
        canvas.drawCircle(delX, delY, delR, hFill)
        val xp = Paint(); xp.color = Color.WHITE; xp.textSize = delR * 1.4f; xp.textAlign = Paint.Align.CENTER; xp.isAntiAlias = true
        canvas.drawText("✕", delX, delY + delR * 0.4f, xp)
        canvas.restore()
    }

    private fun getBounds(item: Any): FloatArray? {
        return when (item) {
            is TableItem -> floatArrayOf(item.x, item.y, item.x + item.totalWidth(), item.y + item.totalHeight())
            is ImageItem -> floatArrayOf(item.x, item.y, item.x + item.w, item.y + item.h)
            is FillItem -> floatArrayOf(item.x, item.y, item.x + item.w, item.y + item.h)
            is TextItem -> {
                val tp = TextPaint(); tp.textSize = item.size
                val lines = item.text.split("\n")
                val w = lines.maxOf { tp.measureText(it) }.coerceAtLeast(10f)
                val h = item.size * 1.4f * lines.size
                floatArrayOf(item.x, item.y - h, item.x + w, item.y)
            }
            is StrokeItem -> {
                val pts = item.data.points; if (pts.size < 2) return null
                if (item.data.type == Tool.CIRCLE && pts.size >= 4) {
                    val r = kotlin.math.hypot((pts[2] - pts[0]).toDouble(), (pts[3] - pts[1]).toDouble()).toFloat()
                    floatArrayOf(pts[0] - r, pts[1] - r, pts[0] + r, pts[1] + r)
                } else if (SHAPE_TOOLS.contains(item.data.type) && pts.size >= 4) {
                    floatArrayOf(minOf(pts[0], pts[2]), minOf(pts[1], pts[3]), maxOf(pts[0], pts[2]), maxOf(pts[1], pts[3]))
                } else {
                    var minX = pts[0]; var maxX = pts[0]; var minY = pts[1]; var maxY = pts[1]
                    var i = 0
                    while (i + 1 < pts.size) {
                        minX = minOf(minX, pts[i]); maxX = maxOf(maxX, pts[i])
                        minY = minOf(minY, pts[i + 1]); maxY = maxOf(maxY, pts[i + 1]); i += 2
                    }
                    floatArrayOf(minX, minY, maxX, maxY)
                }
            }
            else -> null
        }
    }

    private fun getRotation(item: Any): Float = when (item) {
        is ImageItem -> item.rotation; is TextItem -> item.rotation
        is StrokeItem -> item.data.rotation; is TableItem -> item.rotation; else -> 0f
    }

    private fun setRotation(item: Any, r: Float) {
        when (item) {
            is ImageItem -> item.rotation = r; is TextItem -> item.rotation = r
            is StrokeItem -> item.data.rotation = r; is TableItem -> item.rotation = r
        }
    }

    private fun getPivot(item: Any, b: FloatArray): Pair<Float, Float> =
        Pair((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f)

    private fun rotatePoint(x: Float, y: Float, px: Float, py: Float, deg: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble()); val dx = x - px; val dy = y - py
        val cos = kotlin.math.cos(a); val sin = kotlin.math.sin(a)
        return Pair((dx * cos - dy * sin).toFloat() + px, (dx * sin + dy * cos).toFloat() + py)
    }

    private fun computeAngle(item: Any, wx: Float, wy: Float): Float {
        val b = getBounds(item) ?: return 0f; val (px, py) = getPivot(item, b)
        return Math.toDegrees(kotlin.math.atan2((wy - py).toDouble(), (wx - px).toDouble())).toFloat()
    }

    private fun moveItem(item: Any, dx: Float, dy: Float) {
        when (item) {
            is TableItem -> { item.x += dx; item.y += dy }
            is ImageItem -> { item.x += dx; item.y += dy }
            is FillItem -> { item.x += dx; item.y += dy }
            is TextItem -> { item.x += dx; item.y += dy }
            is StrokeItem -> {
                var i = 0
                while (i + 1 < item.data.points.size) { item.data.points[i] += dx; item.data.points[i + 1] += dy; i += 2 }
                item.path = item.data.buildPath()
            }
        }
    }

    private fun resizeItem(item: Any, handle: HandleType, wx: Float, wy: Float) {
        val minSize = 15f

        when (item) {
            is ImageItem -> {
                val left = item.x; val top = item.y
                val right = item.x + item.w; val bottom = item.y + item.h

                var newLeft = left; var newTop = top
                var newRight = right; var newBottom = bottom

                when (handle) {
                    HandleType.TL -> { newLeft = wx; newTop = wy }
                    HandleType.TM -> { newTop = wy }
                    HandleType.TR -> { newRight = wx; newTop = wy }
                    HandleType.ML -> { newLeft = wx }
                    HandleType.MR -> { newRight = wx }
                    HandleType.BL -> { newLeft = wx; newBottom = wy }
                    HandleType.BM -> { newBottom = wy }
                    HandleType.BR -> { newRight = wx; newBottom = wy }
                    else -> return
                }

                // Enforce min size — anchor the opposite edge
                when (handle) {
                    HandleType.TL, HandleType.ML, HandleType.BL ->
                        if (right - newLeft < minSize) newLeft = right - minSize
                    HandleType.TR, HandleType.MR, HandleType.BR ->
                        if (newRight - left < minSize) newRight = left + minSize
                    else -> {}
                }
                when (handle) {
                    HandleType.TL, HandleType.TM, HandleType.TR ->
                        if (bottom - newTop < minSize) newTop = bottom - minSize
                    HandleType.BL, HandleType.BM, HandleType.BR ->
                        if (newBottom - top < minSize) newBottom = top + minSize
                    else -> {}
                }

                item.x = newLeft; item.y = newTop
                item.w = newRight - newLeft; item.h = newBottom - newTop
            }

            is StrokeItem -> {
                if (BBOX_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    val left = minOf(item.data.points[0], item.data.points[2])
                    val top = minOf(item.data.points[1], item.data.points[3])
                    val right = maxOf(item.data.points[0], item.data.points[2])
                    val bottom = maxOf(item.data.points[1], item.data.points[3])

                    var nl = left; var nt = top; var nr = right; var nb = bottom

                    when (handle) {
                        HandleType.TL -> { nl = wx; nt = wy }
                        HandleType.TM -> { nt = wy }
                        HandleType.TR -> { nr = wx; nt = wy }
                        HandleType.ML -> { nl = wx }
                        HandleType.MR -> { nr = wx }
                        HandleType.BL -> { nl = wx; nb = wy }
                        HandleType.BM -> { nb = wy }
                        HandleType.BR -> { nr = wx; nb = wy }
                        else -> return
                    }

                    when (handle) {
                        HandleType.TL, HandleType.ML, HandleType.BL ->
                            if (right - nl < minSize) nl = right - minSize
                        HandleType.TR, HandleType.MR, HandleType.BR ->
                            if (nr - left < minSize) nr = left + minSize
                        else -> {}
                    }
                    when (handle) {
                        HandleType.TL, HandleType.TM, HandleType.TR ->
                            if (bottom - nt < minSize) nt = bottom - minSize
                        HandleType.BL, HandleType.BM, HandleType.BR ->
                            if (nb - top < minSize) nb = top + minSize
                        else -> {}
                    }

                    item.data.points[0] = nl; item.data.points[1] = nt
                    item.data.points[2] = nr; item.data.points[3] = nb
                    item.path = item.data.buildPath()

                } else if (ENDPOINT_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    when (handle) {
                        HandleType.TL -> { item.data.points[0] = wx; item.data.points[1] = wy }
                        HandleType.BR -> { item.data.points[2] = wx; item.data.points[3] = wy }
                        else -> {}
                    }
                    item.path = item.data.buildPath()
                }
            }

            is TextItem -> {
                val dy = wy - resizePrevWorldY
                item.size = (item.size + dy * 0.5f).coerceIn(8f, 300f)
            }
        }

        resizePrevWorldX = wx
        resizePrevWorldY = wy
    }
            
    private fun findItemAt(x: Float, y: Float): Any? {
        val pad = 20f / scaleFactor
        for (a in actions.reversed()) {
            if (a is FillItem) continue
            val b = getBounds(a) ?: continue
            if (x in (b[0] - pad)..(b[2] + pad) && y in (b[1] - pad)..(b[3] + pad)) return a
        }
        return null
    }

    private fun findTextItemAt(x: Float, y: Float): TextItem? {
        for (a in actions.reversed()) {
            if (a is TextItem) {
                val tp = TextPaint(); tp.textSize = a.size
                val lines = a.text.split("\n")
                val w = lines.maxOf { tp.measureText(it) }.coerceAtLeast(10f)
                val h = a.size * 1.4f * lines.size
                val pad = 24f / scaleFactor
                if (x >= a.x - pad && x <= a.x + w + pad && y >= a.y - h - pad && y <= a.y + pad) return a
            }
        }
        return null
    }

    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private fun handleTable(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val tol = 18f / scaleFactor
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val table = activeTableItem
                if (table != null) {
                    val rb = table.hitTestRowBorder(wy, tol)
                    val cb = table.hitTestColBorder(wx, tol)
                    if (rb >= 0) { tableDragRowBorder = rb; tableDragStartY = wy; tableDragOrigSize = table.rowHeights[rb]; return }
                    if (cb >= 0) { tableDragColBorder = cb; tableDragStartX = wx; tableDragOrigSize = table.colWidths[cb]; return }
                    val cell = table.hitTestCell(wx, wy)
                    if (cell != null) {
                        if (tableSelStart == null) { tableSelStart = cell; tableSelEnd = null }
                        else if (tableSelEnd == null && cell != tableSelStart) tableSelEnd = cell
                        else { tableSelStart = cell; tableSelEnd = null }
                        invalidate(); return
                    }
                    activeTableItem = null; tableSelStart = null; tableSelEnd = null; invalidate()
                } else {
                    for (action in actions.reversed()) {
                        if (action is TableItem) {
                            val b = getBounds(action) ?: continue
                            if (wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3]) {
                                activeTableItem = action; tableSelStart = null; tableSelEnd = null; invalidate(); return
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val table = activeTableItem ?: return
                if (tableDragRowBorder >= 0) { table.rowHeights[tableDragRowBorder] = (tableDragOrigSize + (wy - tableDragStartY)).coerceAtLeast(20f); invalidate() }
                else if (tableDragColBorder >= 0) { table.colWidths[tableDragColBorder] = (tableDragOrigSize + (wx - tableDragStartX)).coerceAtLeast(30f); invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                if (tableDragRowBorder >= 0 || tableDragColBorder >= 0) { tableDragRowBorder = -1; tableDragColBorder = -1; return }
                val table = activeTableItem ?: return
                val cell = table.hitTestCell(wx, wy) ?: return
                val selStart = tableSelStart
                if (selStart != null && tableSelEnd == null && selStart == cell) {
                    val rect = table.cellRect(cell.first, cell.second)
                    val sx = worldToScreenX(rect.left); val sy = worldToScreenY(rect.top)
                    onTableCellEditRequest?.invoke(table, cell.first, cell.second, sx, sy)
                    tableSelStart = null
                }
            }
        }
    }

    fun addTableRow(afterRow: Int) {
        val table = activeTableItem ?: return
        val insertAt = (afterRow + 1).coerceIn(0, table.rowHeights.size)
        table.rowHeights.add(insertAt, 60f); table.rows = table.rowHeights.size
        table.insertRow(insertAt); invalidate()
    }

    fun addTableCol(afterCol: Int) {
        val table = activeTableItem ?: return
        val insertAt = (afterCol + 1).coerceIn(0, table.colWidths.size)
        table.colWidths.add(insertAt, 80f); table.cols = table.colWidths.size
        table.insertCol(insertAt); invalidate()
    }

    fun removeTableRow(row: Int) {
        val table = activeTableItem ?: return; if (table.rows <= 1) return
        val delAt = row.coerceIn(0, table.rowHeights.size - 1)
        table.rowHeights.removeAt(delAt); table.rows = table.rowHeights.size
        table.deleteRow(delAt); invalidate()
    }

    fun removeTableCol(col: Int) {
        val table = activeTableItem ?: return; if (table.cols <= 1) return
        val delAt = col.coerceIn(0, table.colWidths.size - 1)
        table.colWidths.removeAt(delAt); table.cols = table.colWidths.size
        table.deleteCol(delAt); invalidate()
    }

    fun mergeCellSelection() {
        val table = activeTableItem ?: return
        val s = tableSelStart ?: return; val e = tableSelEnd ?: return
        table.mergeCells(s.first, s.second, e.first, e.second)
        tableSelStart = null; tableSelEnd = null; invalidate()
    }

    fun unmergeCellSelection() {
        val table = activeTableItem ?: return
        val s = tableSelStart ?: return
        table.unmergeCells(s.first, s.second)
        tableSelStart = null; tableSelEnd = null; invalidate()
    }

    fun getActiveTable(): TableItem? = activeTableItem
    fun getTableSelection(): Pair<Pair<Int, Int>?, Pair<Int, Int>?> = Pair(tableSelStart, tableSelEnd)

    fun worldToScreenX(wx: Float): Float = wx * scaleFactor + translateX
    fun worldToScreenY(wy: Float): Float = wy * scaleFactor + translateY

    private fun drawTableOverlay(canvas: Canvas) {
        val table = activeTableItem ?: return
        val s = tableSelStart; val e = tableSelEnd
        if (s != null) {
            val minR = if (e != null) minOf(s.first, e.first) else s.first
            val maxR = if (e != null) maxOf(s.first, e.first) else s.first
            val minC = if (e != null) minOf(s.second, e.second) else s.second
            val maxC = if (e != null) maxOf(s.second, e.second) else s.second
            val hlP = Paint(); hlP.color = Color.parseColor("#442196F3"); hlP.style = Paint.Style.FILL
            for (r in minR..maxR) for (c in minC..maxC) {
                try { canvas.drawRect(table.cellRect(r, c), hlP) } catch (ex: Exception) {}
            }
        }
        val op = Paint(); op.color = Color.parseColor("#2196F3"); op.style = Paint.Style.STROKE; op.strokeWidth = 2f / scaleFactor
        canvas.drawRect(table.x, table.y, table.x + table.totalWidth(), table.y + table.totalHeight(), op)
    }

    private fun handleSelect(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val at = activeTableItem
        if (at != null) {
            val b = getBounds(at)
            if (b != null) {
                val pad = 20f / scaleFactor
                if (wx >= b[0] - pad && wx <= b[2] + pad && wy >= b[1] - pad && wy <= b[3] + pad) {
                    handleTable(event); return
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            for (action in actions.reversed()) {
                if (action is TableItem) {
                    val b = getBounds(action) ?: continue
                    if (wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3]) {
                        activeTableItem = action; tableSelStart = null; tableSelEnd = null; invalidate(); return
                    }
                }
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }; longPressRunnable = null
                val item = selectedItem; var handled = false
                if (item != null) {
                    val b = getBounds(item)
                    if (b != null) {
                        val rot = getRotation(item); val (px, py) = getPivot(item, b)
                        val (lx, ly) = rotatePoint(wx, wy, px, py, -rot)
                        val hr = 18f / scaleFactor; val hit = 50f / scaleFactor

                        val delX = b[2] + hr * 5f; val delY = b[1] - hr * 5f
                        if (distance(lx, ly, delX, delY) <= hit * 1.2f) {
                            actions.remove(item); selectedItem = null; handled = true; invalidate(); return
                        }

                        val canRot = item is ImageItem || item is TextItem || (item is StrokeItem && item.data.type != Tool.PEN && item.data.type != Tool.ARC)
                        if (!handled && canRot) {
                            val cx = (b[0] + b[2]) / 2f; val ry = b[1] - 60f / scaleFactor
                            if (distance(lx, ly, cx, ry) <= hit) {
                                activeHandle = HandleType.ROTATE
                                dragStartAngle = computeAngle(item, wx, wy)
                                dragStartRotation = rot; handled = true
                            }
                        }
                        val isBbox = item is ImageItem || item is TextItem || (item is StrokeItem && BBOX_RESIZE_SHAPES.contains(item.data.type))
                        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)
                        if (!handled && isBbox) {
                            for ((type, pos) in bboxHandlePositions(b)) {
                                if (distance(lx, ly, pos.first, pos.second) <= hit) {
                                    activeHandle = type
                                    dragStartPivotX = px; dragStartPivotY = py
                                    dragStartRotation = rot
                                    // FIX: initialize resize prev to current world position
                                    resizePrevWorldX = wx; resizePrevWorldY = wy
                                    handled = true; break
                                }
                            }
                        }
                        if (!handled && isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
                            // FIX: hit test against actual endpoint positions (not bbox corners)
                            val p0x = item.data.points[0]; val p0y = item.data.points[1]
                            val p1x = item.data.points[2]; val p1y = item.data.points[3]
                            if (distance(wx, wy, p0x, p0y) <= hit) {
                                activeHandle = HandleType.TL
                                resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true
                            } else if (distance(wx, wy, p1x, p1y) <= hit) {
                                activeHandle = HandleType.BR
                                resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true
                            }
                        }
                        if (!handled && lx >= b[0] - hit && lx <= b[2] + hit && ly >= b[1] - hit && ly <= b[3] + hit) {
                            activeHandle = HandleType.MOVE
                            dragStartWorldX = wx; dragStartWorldY = wy; handled = true
                        }
                    }
                }
                if (!handled) {
                    activeHandle = HandleType.NONE
                    selectedItem = findItemAt(wx, wy)
                }
                val sel = selectedItem
                if (sel is TextItem) {
                    val r = Runnable { sel.isEditing = true; invalidate(); onTextEditRequest?.invoke(sel, event.x, event.y, wx, wy) }
                    longPressRunnable = r; longPressHandler.postDelayed(r, 500)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }
                val item = selectedItem ?: return
                when (activeHandle) {
                    HandleType.MOVE -> {
                        moveItem(item, wx - dragStartWorldX, wy - dragStartWorldY)
                        dragStartWorldX = wx; dragStartWorldY = wy
                    }
                    HandleType.ROTATE -> {
                        val newAngle = computeAngle(item, wx, wy)
                        setRotation(item, dragStartRotation + (newAngle - dragStartAngle))
                    }
                    HandleType.NONE -> return
                    else -> {
                        resizeItem(item, activeHandle, wx, wy)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }
                activeHandle = HandleType.NONE
            }
        }
    }

    private fun handleArc(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val arc = activeArcItem
                if (arc != null) {
                    val r = 30f / scaleFactor; var found = -1; var i = 0
                    while (i + 1 < arc.data.points.size) {
                        if (distance(wx, wy, arc.data.points[i], arc.data.points[i + 1]) <= r) { found = i; break }; i += 2
                    }
                    if (found >= 0) arcDragPointIndex = found
                    else {
                        activeArcItem = null
                        val d = StrokeData(Tool.ARC, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, false)
                        currentItem = StrokeItem(d, d.buildPath(), d.toPaint())
                    }
                } else {
                    val d = StrokeData(Tool.ARC, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, false)
                    currentItem = StrokeItem(d, d.buildPath(), d.toPaint())
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (arcDragPointIndex >= 0) {
                    val arc = activeArcItem ?: return
                    arc.data.points[arcDragPointIndex] = wx; arc.data.points[arcDragPointIndex + 1] = wy
                    arc.path = arc.data.buildPath()
                } else {
                    val item = currentItem ?: return
                    item.data.points[2] = wx; item.data.points[3] = wy; item.path = item.data.buildPath()
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (arcDragPointIndex >= 0) { arcDragPointIndex = -1 }
                else {
                    val item = currentItem
                    if (item != null) {
                        val p0x = item.data.points[0]; val p0y = item.data.points[1]
                        val p1x = item.data.points[2]; val p1y = item.data.points[3]
                        val n = arcDivisions.coerceIn(1, 20); val pts = mutableListOf<Float>()
                        for (i in 0..n) { val t = i.toFloat() / n; pts.add(p0x + (p1x - p0x) * t); pts.add(p0y + (p1y - p0y) * t) }
                        val d = StrokeData(Tool.ARC, pts, item.data.color, item.data.strokeWidth, false)
                        val ni = StrokeItem(d, d.buildPath(), d.toPaint())
                        actions.add(ni); redoStack.clear(); activeArcItem = ni; currentItem = null
                    }
                }
                invalidate()
            }
        }
    }

    private fun scaleItemInGroup(item: Any, ox: Float, oy: Float, sx: Float, sy: Float) {
        when (item) {
            is StrokeItem -> {
                var i = 0
                while (i + 1 < item.data.points.size) {
                    item.data.points[i] = ox + (item.data.points[i] - ox) * sx
                    item.data.points[i + 1] = oy + (item.data.points[i + 1] - oy) * sy; i += 2
                }
                item.path = item.data.buildPath()
            }
            is TextItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.size = (item.size * ((sx + sy) / 2f)).coerceIn(6f, 500f) }
            is ImageItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.w *= sx; item.h *= sy }
            is FillItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.w *= sx; item.h *= sy }
        }
    }

    private fun groupBounds(group: List<Any>): FloatArray? {
        var res: FloatArray? = null
        for (item in group) {
            val b = getBounds(item) ?: continue
            res = if (res == null) b.copyOf() else floatArrayOf(minOf(res[0], b[0]), minOf(res[1], b[1]), maxOf(res[2], b[2]), maxOf(res[3], b[3]))
        }
        return res
    }

    private fun buildRegion(path: Path): Region {
        val rf = RectF(); path.computeBounds(rf, true)
        val clip = Rect(kotlin.math.floor(rf.left).toInt() - 1, kotlin.math.floor(rf.top).toInt() - 1, kotlin.math.ceil(rf.right).toInt() + 1, kotlin.math.ceil(rf.bottom).toInt() + 1)
        val region = Region(); region.setPath(path, Region(clip)); return region
    }

    private fun splitStrokeByRegion(data: StrokeData, region: Region): Pair<List<StrokeItem>, List<StrokeItem>> {
        val pts = data.points
        if (pts.size < 4) return Pair(emptyList(), listOf(StrokeItem(data, data.buildPath(), data.toPaint())))
        val inSegs = mutableListOf<MutableList<Float>>(); val outSegs = mutableListOf<MutableList<Float>>()
        var curIn = mutableListOf<Float>(); var curOut = mutableListOf<Float>(); var last: Boolean? = null; var i = 0
        while (i + 1 < pts.size) {
            val x = pts[i]; val y = pts[i + 1]; val isIn = region.contains(x.toInt(), y.toInt())
            if (last != null && last != isIn) {
                if (last) { if (curIn.size >= 4) inSegs.add(curIn); curIn = mutableListOf() }
                else { if (curOut.size >= 4) outSegs.add(curOut); curOut = mutableListOf() }
            }
            if (isIn) { curIn.add(x); curIn.add(y) } else { curOut.add(x); curOut.add(y) }
            last = isIn; i += 2
        }
        if (curIn.size >= 4) inSegs.add(curIn); if (curOut.size >= 4) outSegs.add(curOut)
        fun makeItems(segs: List<MutableList<Float>>): List<StrokeItem> = segs.map {
            val d = StrokeData(data.type, it, data.color, data.strokeWidth, data.fill); StrokeItem(d, d.buildPath(), d.toPaint())
        }
        return Pair(makeItems(inSegs), makeItems(outSegs))
    }

    private fun selectItemsInRegion(region: Region) {
        val group = mutableListOf<Any>(); val newActions = mutableListOf<Any>()
        for (action in actions) {
            if (action is FillItem) { newActions.add(action); continue }
            if (action is StrokeItem && (action.data.type == Tool.PEN || action.data.type == Tool.ERASER || action.data.type == Tool.ARC) && autoSelectDivide == AutoSelectDivide.DIVIDED) {
                val (inside, outside) = splitStrokeByRegion(action.data, region); newActions.addAll(outside); group.addAll(inside); continue
            }
            val b = getBounds(action); if (b == null) { newActions.add(action); continue }
            val cx = (b[0] + b[2]) / 2f; val cy = (b[1] + b[3]) / 2f
            if (region.contains(cx.toInt(), cy.toInt())) group.add(action) else newActions.add(action)
        }
        newActions.addAll(group); actions.clear(); actions.addAll(newActions); redoStack.clear()
        selectedGroup = if (group.isNotEmpty()) group else null
    }

    private fun handleAutoSelect(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val hr = 18f / scaleFactor; val hit = 50f / scaleFactor
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    val gb = groupBounds(group)
                    if (gb != null) {
                        val delX = gb[2] + hr * 5f; val delY = gb[1] - hr * 5f
                        if (distance(wx, wy, delX, delY) <= hit) {
                            for (it in group) actions.remove(it); selectedGroup = null; invalidate(); return
                        }
                        val gcx = (gb[0] + gb[2]) / 2f
                        val gHandles = listOf(gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1], gb[0] to (gb[1]+gb[3])/2f, gb[2] to (gb[1]+gb[3])/2f, gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3])
                        var found = -1
                        for ((hi, hpos) in gHandles.withIndex()) { if (distance(wx, wy, hpos.first, hpos.second) <= hit) { found = hi; break } }
                        if (found >= 0) { groupResizeHandle = found; groupResizeOrigBounds = gb.copyOf(); groupResizeItemSnapshots = group.map { getBounds(it)?.copyOf() }; invalidate(); return }
                        if (wx in gb[0]..gb[2] && wy in gb[1]..gb[3]) { groupMoveStartX = wx; groupMoveStartY = wy; groupResizeHandle = -1; invalidate(); return }
                    }
                    selectedGroup = null
                }
                regionStart = Pair(wx, wy); regionPath = Path().apply { moveTo(wx, wy) }; invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    if (groupResizeHandle >= 0) {
                        val origW = (groupResizeOrigBounds[2] - groupResizeOrigBounds[0]).coerceAtLeast(1f)
                        val origH = (groupResizeOrigBounds[3] - groupResizeOrigBounds[1]).coerceAtLeast(1f)
                        var nl = groupResizeOrigBounds[0]; var nt = groupResizeOrigBounds[1]; var nr = groupResizeOrigBounds[2]; var nb = groupResizeOrigBounds[3]
                        when (groupResizeHandle) { 0 -> { nl = wx; nt = wy }; 1 -> nt = wy; 2 -> { nr = wx; nt = wy }; 3 -> nl = wx; 4 -> nr = wx; 5 -> { nl = wx; nb = wy }; 6 -> nb = wy; 7 -> { nr = wx; nb = wy } }
                        val sx = (nr - nl).coerceAtLeast(10f) / origW; val sy = (nb - nt).coerceAtLeast(10f) / origH
                        for (it in group) scaleItemInGroup(it, groupResizeOrigBounds[0], groupResizeOrigBounds[1], sx, sy)
                    } else { val dx = wx - groupMoveStartX; val dy = wy - groupMoveStartY; for (it in group) moveItem(it, dx, dy); groupMoveStartX = wx; groupMoveStartY = wy }
                    invalidate(); return
                }
                if (autoSelectShape == AutoSelectShape.RECTANGLE) {
                    val s = regionStart ?: return
                    regionPath = Path().apply { addRect(minOf(s.first, wx), minOf(s.second, wy), maxOf(s.first, wx), maxOf(s.second, wy), Path.Direction.CW) }
                } else regionPath?.lineTo(wx, wy)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                groupResizeHandle = -1
                val group = selectedGroup; if (group != null && group.isNotEmpty()) return
                val rp = regionPath
                if (rp != null) { if (autoSelectShape == AutoSelectShape.FREEFORM) rp.close(); selectItemsInRegion(buildRegion(rp)) }
                regionPath = null; regionStart = null; invalidate()
            }
        }
    }

    private fun drawExportWindowOverlay(canvas: Canvas) {
        val s = exportWindowStart ?: return; val e = exportWindowEnd ?: return
        val left = minOf(s.first, e.first); val top = minOf(s.second, e.second)
        val right = maxOf(s.first, e.first); val bottom = maxOf(s.second, e.second)
        val dimP = Paint(); dimP.color = Color.parseColor("#88000000"); dimP.style = Paint.Style.FILL
        val vl = screenToWorldX(0f); val vt = screenToWorldY(0f)
        val vr = screenToWorldX(width.toFloat()); val vb = screenToWorldY(height.toFloat())
        canvas.drawRect(vl, vt, vr, top, dimP); canvas.drawRect(vl, bottom, vr, vb, dimP)
        canvas.drawRect(vl, top, left, bottom, dimP); canvas.drawRect(right, top, vr, bottom, dimP)
        val bp = Paint(); bp.color = Color.parseColor("#2196F3"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 3f / scaleFactor
        bp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f / scaleFactor, 6f / scaleFactor), 0f)
        canvas.drawRect(left, top, right, bottom, bp)
        val hr = 10f / scaleFactor
        val hf = Paint(); hf.color = Color.WHITE; hf.style = Paint.Style.FILL
        val hs = Paint(); hs.color = Color.parseColor("#2196F3"); hs.style = Paint.Style.STROKE; hs.strokeWidth = 2f / scaleFactor
        for ((cx, cy) in listOf(left to top, right to top, left to bottom, right to bottom)) { canvas.drawCircle(cx, cy, hr, hf); canvas.drawCircle(cx, cy, hr, hs) }
        val wp = ((right - left) / 3.7795f).toInt(); val hp = ((bottom - top) / 3.7795f).toInt()
        val lp = Paint(); lp.color = Color.WHITE; lp.textSize = 28f / scaleFactor; lp.isAntiAlias = true; lp.setShadowLayer(3f / scaleFactor, 0f, 0f, Color.BLACK)
        canvas.drawText("${wp}×${hp}mm", left + 8f / scaleFactor, top - 12f / scaleFactor, lp)
    }

    private fun drawAutoSelectOverlay(canvas: Canvas) {
        regionPath?.let { rp ->
            val fp = Paint(); fp.color = Color.parseColor("#332196F3"); fp.style = Paint.Style.FILL
            val sp = Paint(); sp.color = Color.parseColor("#2196F3"); sp.style = Paint.Style.STROKE; sp.strokeWidth = 2f / scaleFactor
            sp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f / scaleFactor, 6f / scaleFactor), 0f)
            canvas.drawPath(rp, fp); canvas.drawPath(rp, sp)
        }
        val group = selectedGroup
        if (group != null && group.isNotEmpty()) {
            val hp = Paint(); hp.color = Color.parseColor("#332196F3"); hp.style = Paint.Style.FILL
            for (item in group) { val b = getBounds(item) ?: continue; canvas.drawRect(b[0], b[1], b[2], b[3], hp) }
            val gb = groupBounds(group)
            if (gb != null) {
                val bp = Paint(); bp.color = Color.parseColor("#2196F3"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 2f / scaleFactor
                canvas.drawRect(gb[0], gb[1], gb[2], gb[3], bp)
                val r = 18f / scaleFactor; val cx = (gb[0] + gb[2]) / 2f
                val hf = Paint(); hf.style = Paint.Style.FILL; hf.color = Color.WHITE
                val hs = Paint(); hs.style = Paint.Style.STROKE; hs.color = Color.parseColor("#2196F3"); hs.strokeWidth = 2f / scaleFactor
                for ((hx, hy) in listOf(gb[0] to gb[1], cx to gb[1], gb[2] to gb[1], gb[0] to (gb[1]+gb[3])/2f, gb[2] to (gb[1]+gb[3])/2f, gb[0] to gb[3], cx to gb[3], gb[2] to gb[3])) {
                    canvas.drawCircle(hx, hy, r, hf); canvas.drawCircle(hx, hy, r, hs)
                }
                val dp = Paint(); dp.color = Color.parseColor("#F44336"); dp.style = Paint.Style.FILL
                canvas.drawCircle(gb[2] + r * 5f, gb[1] - r * 5f, r * 1.4f, dp)
            }
        }
    }

    private fun drawCursor(canvas: Canvas) {
        val hx = hoverX ?: return; val hy = hoverY ?: return
        val p = Paint(); p.isAntiAlias = true
        when (currentTool) {
            Tool.PEN -> { p.color = currentColor; p.style = Paint.Style.FILL; canvas.drawCircle(hx, hy, (currentStrokeWidth * scaleFactor / 2f).coerceAtLeast(2f), p) }
            Tool.ERASER -> {
                p.color = if (eraserMode == EraserMode.OBJECT) Color.DKGRAY else Color.RED; p.style = Paint.Style.STROKE; p.strokeWidth = 2f
                val half = eraserSize * scaleFactor / 2f
                if (eraserMode == EraserMode.OBJECT) canvas.drawRect(hx - half, hy - half, hx + half, hy + half, p)
                else canvas.drawCircle(hx, hy, half, p)
            }
            else -> { p.color = Color.DKGRAY; p.style = Paint.Style.FILL; canvas.drawCircle(hx, hy, 5f, p) }
        }
    }

    private fun pageWidthPx(): Float {
        val m = 3.7795f
        return if (pageOrientation == Orientation.PORTRAIT) paperSize.widthMM * m else paperSize.heightMM * m
    }

    private fun pageHeightPx(): Float {
        val m = 3.7795f
        return if (pageOrientation == Orientation.PORTRAIT) paperSize.heightMM * m else paperSize.widthMM * m
    }

    private fun drawBackground(canvas: Canvas) {
        val vl = -translateX / scaleFactor; val vt = -translateY / scaleFactor
        val vr = vl + width / scaleFactor; val vb = vt + height / scaleFactor
        when (canvasMode) {
            CanvasMode.INFINITE -> {
                if (paperType == PaperType.BLANK_COLORED) {
                    val p = Paint(); p.color = paperColor; canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, p)
                } else if (paperType != PaperType.BLANK) drawPaperPattern(canvas, vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f)
            }
            CanvasMode.FIXED -> {
                val pw = pageWidthPx(); val ph = pageHeightPx()
                val gp = Paint(); gp.color = Color.parseColor("#D5D5D5"); canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, gp)
                val wp = Paint(); wp.color = if (paperType == PaperType.BLANK_COLORED) paperColor else Color.WHITE
                canvas.drawRect(0f, 0f, pw, ph, wp)
                if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) {
                    canvas.save(); canvas.clipRect(0f, 0f, pw, ph); drawPaperPattern(canvas, 0f, 0f, pw, ph); canvas.restore()
                }
                val bp = Paint(); bp.color = Color.parseColor("#909090"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 2f / scaleFactor
                canvas.drawRect(0f, 0f, pw, ph, bp)
            }
            CanvasMode.PAGINATED -> {
                val pw = pageWidthPx(); val ph = pageHeightPx(); val gap = 40f
                val gp = Paint(); gp.color = Color.parseColor("#D5D5D5"); canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, gp)
                val wp = Paint(); wp.color = if (paperType == PaperType.BLANK_COLORED) paperColor else Color.WHITE
                val bp = Paint(); bp.color = Color.parseColor("#909090"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 2f / scaleFactor
                val period = ph + gap
                val si = (kotlin.math.floor(vt / period).toInt() - 1).coerceAtLeast(0)
                val ei = kotlin.math.ceil(vb / period).toInt() + 1
                for (i in si..ei) {
                    val top = i * period
                    canvas.drawRect(0f, top, pw, top + ph, wp)
                    if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) {
                        canvas.save(); canvas.clipRect(0f, top, pw, top + ph); drawPaperPattern(canvas, 0f, top, pw, top + ph); canvas.restore()
                    }
                    canvas.drawRect(0f, top, pw, top + ph, bp)
                }
            }
        }
    }

    private fun drawPaperPattern(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        when (paperType) {
            PaperType.LINED -> {
                val p = Paint(); p.color = Color.parseColor("#C8D6F0"); p.strokeWidth = 1f
                val s = 60f; var y = (top / s).toInt() * s
                while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += s }
            }
            PaperType.GRID -> {
                val p = Paint(); p.color = Color.parseColor("#D0D0D0"); p.strokeWidth = 1f
                val s = 50f
                var x = (left / s).toInt() * s; while (x < right) { canvas.drawLine(x, top, x, bottom, p); x += s }
                var y = (top / s).toInt() * s; while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += s }
            }
            PaperType.DOTS -> {
                val p = Paint(); p.color = Color.parseColor("#B0B0B0"); p.style = Paint.Style.FILL
                val s = 50f; var x = (left / s).toInt() * s
                while (x < right) { var y = (top / s).toInt() * s; while (y < bottom) { canvas.drawCircle(x, y, 2f, p); y += s }; x += s }
            }
            PaperType.ENGINEERING -> {
                val mp = Paint(); mp.color = Color.parseColor("#E0E8F5"); mp.strokeWidth = 1f
                val Mp = Paint(); Mp.color = Color.parseColor("#A8C0E8"); Mp.strokeWidth = 1.5f
                val ms = 20f; val me = 5
                var i = (left / ms).toInt(); var x = i * ms
                while (x < right) { canvas.drawLine(x, top, x, bottom, if (i % me == 0) Mp else mp); i++; x = i * ms }
                var j = (top / ms).toInt(); var y = j * ms
                while (y < bottom) { canvas.drawLine(left, y, right, y, if (j % me == 0) Mp else mp); j++; y = j * ms }
            }
            else -> {}
        }
    }

    fun screenToWorldX(x: Float): Float = (x - translateX) / scaleFactor
    fun screenToWorldY(y: Float): Float = (y - translateY) / scaleFactor
    fun screenCenterWorldX(): Float = screenToWorldX(width / 2f)
    fun screenCenterWorldY(): Float = screenToWorldY(height / 2f)
    fun getScaleFactor(): Float = scaleFactor

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hoverX = event.x; hoverY = event.y; invalidate() }
            MotionEvent.ACTION_HOVER_EXIT -> { hoverX = null; hoverY = null; invalidate() }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) { scaleDetector.onTouchEvent(event); return true }
        gestureDetector.onTouchEvent(event)
        if (currentTool == Tool.TEXT || currentTool == Tool.FILL) return true
        if (currentTool == Tool.SELECT) { handleSelect(event); return true }
        if (currentTool == Tool.ARC) { handleArc(event); return true }
        if (currentTool == Tool.AUTOSELECT) { handleAutoSelect(event); return true }
        if (currentTool == Tool.EXPORT_WINDOW) { handleExportWindow(event); return true }
        handleDrawing(event); return true
    }

    private fun handleExportWindow(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { exportWindowStart = Pair(wx, wy); exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_MOVE -> { exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_UP -> {
                val s = exportWindowStart ?: return; val e = exportWindowEnd ?: return
                val left = minOf(s.first, e.first); val top = minOf(s.second, e.second)
                val right = maxOf(s.first, e.first); val bottom = maxOf(s.second, e.second)
                if (right - left > 20f && bottom - top > 20f) onExportWindowSelected?.invoke(left, top, right, bottom)
                exportWindowStart = null; exportWindowEnd = null; currentTool = Tool.SELECT; invalidate()
            }
        }
    }

    private fun handleDrawing(event: MotionEvent) {
        hoverX = event.x; hoverY = event.y
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val pressure = event.pressure.coerceIn(0.3f, 1.5f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (currentTool == Tool.ERASER) { eraseAt(wx, wy); invalidate(); return }
                val data = when {
                    currentTool == Tool.PEN -> StrokeData(Tool.PEN, mutableListOf(wx, wy), currentColor, currentStrokeWidth * pressure, false)
                    SHAPE_TOOLS.contains(currentTool) -> StrokeData(currentTool, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, fillShapes)
                    else -> StrokeData(Tool.PEN, mutableListOf(wx, wy), currentColor, currentStrokeWidth * pressure, false)
                }
                currentItem = StrokeItem(data, data.buildPath(), data.toPaint()); invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == Tool.ERASER) { eraseAt(wx, wy); invalidate(); return }
                val item = currentItem ?: return
                if (currentTool == Tool.PEN) { item.data.points.add(wx); item.data.points.add(wy) }
                else if (SHAPE_TOOLS.contains(currentTool)) { item.data.points[2] = wx; item.data.points[3] = wy }
                item.path = item.data.buildPath(); invalidate()
            }
            MotionEvent.ACTION_UP -> { currentItem?.let { actions.add(it); redoStack.clear() }; currentItem = null; invalidate() }
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserSize / 2f
        if (eraserMode == EraserMode.OBJECT) {
            val it = actions.iterator()
            while (it.hasNext()) {
                val a = it.next()
                val hit = when (a) {
                    is StrokeItem -> strokeHitTest(a.data, x, y, r)
                    is TextItem -> distance(x, y, a.x, a.y) <= r + a.size
                    is ImageItem -> distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) <= r + maxOf(a.w, a.h) / 2f
                    is FillItem -> distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) <= r + maxOf(a.w, a.h) / 2f
                    else -> false
                }
                if (hit) it.remove()
            }
        } else {
            val newActions = mutableListOf<Any>()
            for (a in actions) {
                when (a) {
                    is StrokeItem -> {
                        if (a.data.type == Tool.PEN || a.data.type == Tool.ERASER || a.data.type == Tool.ARC)
                            newActions.addAll(splitStrokeAroundEraser(a.data, x, y, r))
                        else if (!strokeHitTest(a.data, x, y, r)) newActions.add(a)
                    }
                    is TextItem -> { if (distance(x, y, a.x, a.y) > r + a.size) newActions.add(a) }
                    is ImageItem -> { if (distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) > r + maxOf(a.w, a.h) / 2f) newActions.add(a) }
                    is FillItem -> { if (distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) > r + maxOf(a.w, a.h) / 2f) newActions.add(a) }
                    else -> newActions.add(a)
                }
            }
            actions.clear(); actions.addAll(newActions)
        }
    }

    private fun splitStrokeAroundEraser(data: StrokeData, ex: Float, ey: Float, r: Float): List<StrokeItem> {
        val pts = data.points
        if (pts.size < 4) { if (pts.size >= 2 && distance(ex, ey, pts[0], pts[1]) <= r) return emptyList(); return listOf(StrokeItem(data, data.buildPath(), data.toPaint())) }
        val segs = mutableListOf<MutableList<Float>>(); var cur = mutableListOf<Float>(); var i = 0
        while (i + 1 < pts.size) {
            if (distance(ex, ey, pts[i], pts[i + 1]) <= r) { if (cur.size >= 4) segs.add(cur); cur = mutableListOf() }
            else { cur.add(pts[i]); cur.add(pts[i + 1]) }; i += 2
        }
        if (cur.size >= 4) segs.add(cur)
        return segs.map { sp -> val d = StrokeData(data.type, sp, data.color, data.strokeWidth, data.fill); StrokeItem(d, d.buildPath(), d.toPaint()) }
    }

    private fun strokeHitTest(data: StrokeData, x: Float, y: Float, r: Float): Boolean {
        if (data.type == Tool.PEN || data.type == Tool.ERASER || data.type == Tool.ARC) {
            if (data.points.size == 2) return distance(x, y, data.points[0], data.points[1]) <= r
            var i = 0
            while (i + 3 < data.points.size) { if (distToSeg(x, y, data.points[i], data.points[i + 1], data.points[i + 2], data.points[i + 3]) <= r) return true; i += 2 }
            return false
        } else {
            if (data.points.size >= 4) {
                val l = minOf(data.points[0], data.points[2]) - r; val ri = maxOf(data.points[0], data.points[2]) + r
                val t = minOf(data.points[1], data.points[3]) - r; val b = maxOf(data.points[1], data.points[3]) + r
                return x in l..ri && y in t..b
            }
            return false
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    private fun distToSeg(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0f && dy == 0f) return distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return distance(px, py, x1 + t * dx, y1 + t * dy)
    }

    fun addText(text: String, x: Float, y: Float, size: Float, rotation: Float, color: Int, spans: MutableList<TextSpanData> = mutableListOf()) {
        if (text.isBlank()) return
        val item = TextItem(text, x, y, color, size, rotation); item.spans = spans
        actions.add(item); redoStack.clear(); invalidate()
    }

    fun removeTextItem(item: TextItem) { actions.remove(item); invalidate() }

    // FIX: addImage no longer loads bitmap synchronously — just adds item, async load happens in drawActionItem
    fun addImage(path: String, wx: Float, wy: Float, w: Float, h: Float) {
        val item = ImageItem(path, wx - w / 2f, wy - h / 2f, w, h, 0f)
        actions.add(item); redoStack.clear()
        // Kick off async load immediately so it's ready when drawn
        loadBitmapAsync(path) { bmp -> item.bitmap = bmp; item.loading = false; invalidate() }
        invalidate()
    }

    fun addTable(rows: Int, cols: Int, wx: Float, wy: Float, screenWidth: Float) {
        val table = TableItem(wx, wy)
        table.rows = rows; table.cols = cols
        val cellW = (screenWidth / scaleFactor / 2f) / cols; val cellH = 60f
        table.rowHeights.clear(); repeat(rows) { table.rowHeights.add(cellH) }
        table.colWidths.clear(); repeat(cols) { table.colWidths.add(cellW) }
        for (r in 0 until rows) for (c in 0 until cols) table.getCellPublic(r, c)
        actions.add(table); redoStack.clear()
        activeTableItem = table; invalidate()
    }

    fun migrateOldNotes(filesDir: File) {
        val oldFolder = File(filesDir, "drawings"); if (!oldFolder.exists()) return
        val newFolder = File(File(filesDir, "books"), "General"); if (!newFolder.exists()) newFolder.mkdirs()
        oldFolder.listFiles()?.filter { it.extension == "eng" }?.forEach { file ->
            val dest = File(newFolder, file.name); if (!dest.exists()) file.copyTo(dest)
        }
    }

    fun exportWindow(left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val tmpBmp = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        draw(Canvas(tmpBmp))
        val sx = worldToScreenX(left).toInt().coerceAtLeast(0)
        val sy = worldToScreenY(top).toInt().coerceAtLeast(0)
        val ex = worldToScreenX(right).toInt().coerceAtMost(this.width)
        val ey = worldToScreenY(bottom).toInt().coerceAtMost(this.height)
        val cw = (ex - sx).coerceAtLeast(1); val ch = (ey - sy).coerceAtLeast(1)
        return Bitmap.createBitmap(tmpBmp, sx, sy, cw, ch)
    }

    fun undo() { if (actions.isNotEmpty()) { redoStack.add(actions.removeAt(actions.size - 1)); invalidate() } }
    fun redo() { if (redoStack.isNotEmpty()) { actions.add(redoStack.removeAt(redoStack.size - 1)); invalidate() } }
    fun clearAll() { actions.clear(); redoStack.clear(); selectedItem = null; activeTableItem = null; invalidate() }
    fun hasContent(): Boolean = actions.isNotEmpty()
    fun exportBitmap(): Bitmap { val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); draw(Canvas(bmp)); return bmp }

    fun renderStrokesOnly(scale: Float): Bitmap {
        val w = (width * scale).toInt().coerceAtLeast(1); val h = (height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
        canvas.save(); canvas.scale(scale, scale); canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        for (a in actions) drawActionItem(canvas, a, false)
        canvas.restore(); return bmp
    }

    fun zoomTo(wx: Float, wy: Float, scale: Float) {
        scaleFactor = scale.coerceIn(0.2f, 6f)
        translateX = width / 2f - wx * scaleFactor; translateY = height / 2f - wy * scaleFactor
        clampTranslation(); invalidate()
    }

    private fun clusterEdgePoints(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val clusters = mutableListOf<MutableList<Pair<Int, Int>>>()
        for (p in points) {
            var added = false
            for (c in clusters) { val c0 = c[0]; if (kotlin.math.abs(c0.first - p.first) < 30 && kotlin.math.abs(c0.second - p.second) < 30) { c.add(p); added = true; break } }
            if (!added) clusters.add(mutableListOf(p))
        }
        return clusters.map { Pair(it.map { it.first }.average().toInt(), it.map { it.second }.average().toInt()) }
    }

    fun performFill(screenX: Float, screenY: Float) {
        val scale = 0.4f; val bmp = renderStrokesOnly(scale)
        val w = bmp.width; val h = bmp.height
        val px = (screenX * scale).toInt().coerceIn(0, w - 1); val py = (screenY * scale).toInt().coerceIn(0, h - 1)
        val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        fun isEmpty(x: Int, y: Int): Boolean = ((pixels[y * w + x] ushr 24) and 0xFF) < 10
        if (!isEmpty(px, py)) { invalidate(); return }
        val visited = BooleanArray(w * h); val queue = ArrayDeque<Int>()
        val start = py * w + px; queue.add(start); visited[start] = true
        var filled = 0; val edgeHits = mutableListOf<Pair<Int, Int>>()
        val maxFill = (w * h * 0.7f).toInt(); var leaked = false
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst(); val x = idx % w; val y = idx / w; filled++
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) edgeHits.add(Pair(x, y))
            if (filled > maxFill) { leaked = true; break }
            if (x > 0) { val n = idx - 1; if (!visited[n] && isEmpty(x - 1, y)) { visited[n] = true; queue.add(n) } }
            if (x < w - 1) { val n = idx + 1; if (!visited[n] && isEmpty(x + 1, y)) { visited[n] = true; queue.add(n) } }
            if (y > 0) { val n = idx - w; if (!visited[n] && isEmpty(x, y - 1)) { visited[n] = true; queue.add(n) } }
            if (y < h - 1) { val n = idx + w; if (!visited[n] && isEmpty(x, y + 1)) { visited[n] = true; queue.add(n) } }
        }
        if (leaked) {
            val clusters = clusterEdgePoints(edgeHits)
            if (clusters.isNotEmpty()) {
                val (cx, cy) = clusters[0]
                zoomTo(screenToWorldX(cx / scale), screenToWorldY(cy / scale), (scaleFactor * 2.5f).coerceAtMost(6f))
            }
            invalidate()
        } else {
            val fp = IntArray(w * h) { if (visited[it]) fillColor else Color.TRANSPARENT }
            val fb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); fb.setPixels(fp, 0, w, 0, 0, w, h)
            val folder = File(ctx.filesDir, "images"); if (!folder.exists()) folder.mkdirs()
            val outFile = File(folder, "fill_${System.currentTimeMillis()}.png")
            try { FileOutputStream(outFile).use { fb.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { invalidate(); return }
            val wx0 = screenToWorldX(0f); val wy0 = screenToWorldY(0f)
            val wx1 = screenToWorldX(width.toFloat()); val wy1 = screenToWorldY(height.toFloat())
            actions.add(0, FillItem(outFile.absolutePath, wx0, wy0, wx1 - wx0, wy1 - wy0)); redoStack.clear(); invalidate()
        }
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("META\u0001${paperType.name}\u0001${canvasMode.name}\u0001${paperSize.name}\u0001${pageOrientation.name}\u0001$paperColor\n")
        for (a in actions) when (a) {
            is TableItem -> sb.append(a.serialize())
            is StrokeItem -> sb.append("${a.data.type.name}|${a.data.color}|${a.data.strokeWidth}|${a.data.fill}|${a.data.rotation}|${a.data.points.joinToString(",")}\n")
            is TextItem -> sb.append("TEXT\u0001${a.x}\u0001${a.y}\u0001${a.color}\u0001${a.size}\u0001${a.rotation}\u0001${a.spans.joinToString(";") { "${it.start},${it.end},${it.type},${it.value}" }}\u0001${a.text.replace("\n", "\u0002")}\n")
            is ImageItem -> sb.append("IMAGE\u0001${a.path}\u0001${a.x}\u0001${a.y}\u0001${a.w}\u0001${a.h}\u0001${a.rotation}\n")
            is FillItem -> sb.append("FILL\u0001${a.path}\u0001${a.x}\u0001${a.y}\u0001${a.w}\u0001${a.h}\n")
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        actions.clear(); redoStack.clear(); selectedItem = null; activeTableItem = null
        val lines = content.lines(); var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            try {
                when {
                    line.startsWith("META\u0001") -> {
                        val p = line.split("\u0001")
                        try { if (p.size > 1) paperType = PaperType.valueOf(p[1]) } catch (e: Exception) {}
                        try { if (p.size > 2) canvasMode = CanvasMode.valueOf(p[2]) } catch (e: Exception) {}
                        try { if (p.size > 3) paperSize = PaperSizeOption.valueOf(p[3]) } catch (e: Exception) {}
                        try { if (p.size > 4) pageOrientation = Orientation.valueOf(p[4]) } catch (e: Exception) {}
                        try { if (p.size > 5) paperColor = p[5].toInt() } catch (e: Exception) {}
                        i++
                    }
                    line.startsWith("TABLE\u0001") -> {
                        val tableLines = mutableListOf<String>(); var j = i
                        while (j < lines.size && !lines[j].startsWith("TABLEEND")) { tableLines.add(lines[j]); j++ }
                        val (tableItem, _) = TableItem.deserialize(tableLines, 0)
                        if (tableItem != null) actions.add(tableItem); i = j + 1
                    }
                    line.startsWith("TEXT\u0001") -> {
                        val p = line.split("\u0001"); if (p.size >= 7) {
                            val item = TextItem("", p[1].toFloat(), p[2].toFloat(), p[3].toInt(), p[4].toFloat(), p[5].toFloat())
                            if (p.size >= 9) {
                                val bold = p[6].toBoolean(); val italic = p[7].toBoolean()
                                item.text = p[8].replace("\u0002", "\n")
                                val style = if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else -1
                                if (style >= 0) item.spans.add(TextSpanData(0, item.text.length, 'S', style))
                            } else {
                                if (p[6].isNotBlank()) for (t in p[6].split(";")) { val sp = t.split(","); if (sp.size == 4) item.spans.add(TextSpanData(sp[0].toInt(), sp[1].toInt(), sp[2][0], sp[3].toInt())) }
                                item.text = if (p.size > 7) p[7].replace("\u0002", "\n") else ""
                            }
                            actions.add(item)
                        }
                        i++
                    }
                    line.startsWith("IMAGE\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 7) {
                            val item = ImageItem(p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat())
                            actions.add(item)
                            // FIX: async load on restore too
                            loadBitmapAsync(p[1]) { bmp -> item.bitmap = bmp; item.loading = false; invalidate() }
                        }
                        i++
                    }
                    line.startsWith("FILL\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 6) actions.add(FillItem(p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()))
                        i++
                    }
                    else -> {
                        val p = line.split("|")
                        if (p.size >= 5) {
                            val type = Tool.valueOf(p[0]); val color = p[1].toInt(); val sw = p[2].toFloat(); val fill = p[3].toBoolean()
                            if (p.size >= 6) {
                                val rot = p[4].toFloat()
                                val pts = if (p[5].isBlank()) mutableListOf() else p[5].split(",").map { it.toFloat() }.toMutableList()
                                val d = StrokeData(type, pts, color, sw, fill, rot); actions.add(StrokeItem(d, d.buildPath(), d.toPaint()))
                            } else {
                                val pts = if (p[4].isBlank()) mutableListOf() else p[4].split(",").map { it.toFloat() }.toMutableList()
                                val d = StrokeData(type, pts, color, sw, fill); actions.add(StrokeItem(d, d.buildPath(), d.toPaint()))
                            }
                        }
                        i++
                    }
                }
            } catch (e: Exception) { i++ }
        }
        invalidate()
    }
}
