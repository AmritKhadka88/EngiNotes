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

// ──────────────────────────────────────────────────────────────
//  Enums & Shared Domain Configuration Definitions
// ──────────────────────────────────────────────────────────────

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

// ──────────────────────────────────────────────────────────────
//  Background Threads & Memory Cache
// ──────────────────────────────────────────────────────────────

private val imageLoadExecutor = ThreadPoolExecutor(
    2, 3, 60L, TimeUnit.SECONDS, LinkedBlockingQueue()
)

private val bitmapCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    private val MAX_SIZE = 80 * 1024 * 1024L // 80 Megabytes Maximum bounds
    private var currentSize = 0L

    override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>): Boolean {
        val remove = currentSize > MAX_SIZE
        if (remove) {
            currentSize -= eldest.value.byteCount
        }
        return remove
    }

    fun putBitmap(key: String, bmp: Bitmap) {
        currentSize += bmp.byteCount
        put(key, bmp)
    }

    fun getBitmap(key: String): Bitmap? = get(key)
}

// ──────────────────────────────────────────────────────────────
//  Entity / Element Data Classes
// ──────────────────────────────────────────────────────────────

class StrokeData(
    val type: Tool, 
    val points: MutableList<Float>,
    var color: Int, 
    var strokeWidth: Float, 
    var fill: Boolean, 
    var rotation: Float = 0f
) {
    fun buildPath(): Path {
        val path = Path()
        if (type == Tool.PEN || type == Tool.ERASER) {
            if (points.size >= 2) {
                path.moveTo(points[0], points[1])
                var i = 2
                while (i + 1 < points.size) { 
                    path.lineTo(points[i], points[i + 1])
                    i += 2 
                }
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
                        val x1 = points[i]
                        val y1 = points[i + 1]
                        val x2 = points[i + 2]
                        val y2 = points[i + 3]
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
        val x1 = points[0]
        val y1 = points[1]
        val x2 = points[2]
        val y2 = points[3]
        val left = minOf(x1, x2)
        val right = maxOf(x1, x2)
        val top = minOf(y1, y2)
        val bottom = maxOf(y1, y2)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        when (type) {
            Tool.LINE -> { 
                path.moveTo(x1, y1)
                path.lineTo(x2, y2) 
            }
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
            Tool.TRIANGLE -> { 
                path.moveTo(cx, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close() 
            }
            Tool.DIAMOND -> { 
                path.moveTo(cx, top)
                path.lineTo(right, cy)
                path.lineTo(cx, bottom)
                path.lineTo(left, cy)
                path.close() 
            }
            Tool.ARROW -> {
                path.moveTo(x1, y1)
                path.lineTo(x2, y2)
                val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                val al = 20f
                val aa = Math.PI / 7
                path.moveTo(x2, y2)
                path.lineTo(x2 - (al * kotlin.math.cos(angle - aa)).toFloat(), y2 - (al * kotlin.math.sin(angle - aa)).toFloat())
                path.moveTo(x2, y2)
                path.lineTo(x2 - (al * kotlin.math.cos(angle + aa)).toFloat(), y2 - (al * kotlin.math.sin(angle + aa)).toFloat())
            }
            Tool.CURVE -> {
                val dx = x2 - x1
                val dy = y2 - y1
                path.moveTo(x1, y1)
                path.quadTo(cx - dy * 0.25f, cy + dx * 0.25f, x2, y2)
            }
            Tool.CROSS -> { 
                path.moveTo(left, cy)
                path.lineTo(right, cy)
                path.moveTo(cx, top)
                path.lineTo(cx, bottom) 
            }
            Tool.STAR -> addPolygon(path, left, top, right, bottom, 5, true)
            Tool.PENTAGON -> addPolygon(path, left, top, right, bottom, 5, false)
            Tool.HEXAGON -> addPolygon(path, left, top, right, bottom, 6, false)
            else -> {}
        }
        return path
    }

    private fun addPolygon(path: Path, left: Float, top: Float, right: Float, bottom: Float, sides: Int, isStar: Boolean) {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = (right - left) / 2f
        val ry = (bottom - top) / 2f
        val count = if (isStar) sides * 2 else sides
        val step = 2 * Math.PI / count
        val start = -Math.PI / 2
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
        p.strokeWidth = strokeWidth
        p.isAntiAlias = true
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        return p
    }
}

class StrokeItem(val data: StrokeData, var path: Path, var paint: Paint)

class TextItem(var text: String, var x: Float, var y: Float, var color: Int, var size: Float, var rotation: Float) {
    var spans: MutableList<TextSpanData> = mutableListOf()
    var isEditing: Boolean = false
}

class ImageItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float, var rotation: Float) {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var loading: Boolean = false
}

class FillItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float) {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var loading: Boolean = false
}

// ──────────────────────────────────────────────────────────────
//  Primary Interface Canvas View Class Architecture
// ──────────────────────────────────────────────────────────────

class DrawingView @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ctx = context
    private val actions = mutableListOf<Any>()
    private val redoStack = mutableListOf<Any>()
    private var currentItem: StrokeItem? = null

    var currentTool: Tool = Tool.PEN
        set(value) {
            if (field == Tool.SELECT && value != Tool.SELECT) selectedItem = null
            if (field == Tool.ARC && value != Tool.ARC) activeArcItem = null
            if (field == Tool.AUTOSELECT && value != Tool.AUTOSELECT) {
                selectedGroup = null
                regionPath = null
                regionStart = null
            }
            field = value
            invalidate()
        }

    // Settings Parameters
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

    // Selection Group Mechanics
    var selectedGroup: MutableList<Any>? = null
    private var regionPath: Path? = null
    private var regionStart: Pair<Float, Float>? = null
    private var groupMoveStartX = 0f
    private var groupMoveStartY = 0f
    private var groupResizeHandle = -1
    private var groupResizeOrigBounds = FloatArray(4)
    private var groupResizeItemSnapshots: List<FloatArray?> = emptyList()

    var selectedItem: Any? = null

    private enum class HandleType { NONE, MOVE, ROTATE, TL, TM, TR, ML, MR, BL, BM, BR }

    private var activeHandle = HandleType.NONE
    private var dragStartWorldX = 0f
    private var dragStartWorldY = 0f
    private var dragStartAngle = 0f
    private var dragStartRotation = 0f
    private var dragStartPivotX = 0f
    private var dragStartPivotY = 0f
    private var resizePrevWorldX = 0f
    private var resizePrevWorldY = 0f

    // Arc Mode State tracking
    private var activeArcItem: StrokeItem? = null
    private var arcDragPointIndex = -1

    // Table Tracking Architecture
    private var activeTableItem: TableItem? = null
    private var tableDragRowBorder = -1
    private var tableDragColBorder = -1
    private var tableDragStartY = 0f
    private var tableDragStartX = 0f
    private var tableDragOrigSize = 0f
    private var tableSelStart: Pair<Int, Int>? = null
    private var tableSelEnd: Pair<Int, Int>? = null
    private var tableIsActive: Boolean = false
    private var tableSingleTapCell: Pair<Int, Int>? = null
    private var tableSingleTapTime: Long = 0L
    private val TABLE_DOUBLE_TAP_MS = 300L

    // Public API Event Callbacks hooks
    var onTextEditRequest: ((TextItem?, Float, Float, Float, Float) -> Unit)? = null
    var onTableCellEditRequest: ((TableItem, Int, Int, Float, Float) -> Unit)? = null
    var isTextEditorOpen: Boolean = false
    var onScaleChanged: ((Float) -> Unit)? = null
    var onCanvasTransformed: (() -> Unit)? = null

    // Export Windows Framing Context
    private var exportWindowStart: Pair<Float, Float>? = null
    private var exportWindowEnd: Pair<Float, Float>? = null
    var onExportWindowSelected: ((Float, Float, Float, Float) -> Unit)? = null

    // Matrix View Tracking Dimensions
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var prevFocusX = 0f
    private var prevFocusY = 0f
    private var twoFingerLastX = 0f
    private var twoFingerLastY = 0f
    private var hoverX: Float? = null
    private var hoverY: Float? = null

    // Palm rejection and Tracking properties
    private var isStylusDown = false   
    private var drawingPointerId = -1  

    private fun isDrawingTool() = currentTool == Tool.PEN ||
            currentTool == Tool.ERASER ||
            currentTool in SHAPE_TOOLS ||
            currentTool == Tool.ARC ||
            currentTool == Tool.FILL

    // ──────────────────────────────────────────────────────────────
    //  Gestures and Input Listener Initialization
    // ──────────────────────────────────────────────────────────────

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            return true
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
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            clampTranslation()
            onScaleChanged?.invoke(scaleFactor)
            onCanvasTransformed?.invoke()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x)
            val wy = screenToWorldY(e.y)
            when (currentTool) {
                Tool.TEXT -> {
                    val hit = findTextItemAt(wx, wy)
                    if (hit != null) {
                        selectedItem = hit
                        currentTool = Tool.SELECT
                        invalidate()
                    } else {
                        selectedItem = null
                        onTextEditRequest?.invoke(null, e.x, e.y, wx, wy)
                    }
                }
                Tool.SELECT -> {
                    val hit = findTextItemAt(wx, wy)
                    if (hit != null && selectedItem === hit) {
                        hit.isEditing = true
                        invalidate()
                        onTextEditRequest?.invoke(hit, e.x, e.y, wx, wy)
                    }
                }
                Tool.FILL -> {
                    val item = findItemAt(wx, wy)
                    if (item is StrokeItem && CLOSED_SHAPES.contains(item.data.type)) {
                        item.data.fill = !item.data.fill
                        item.data.color = fillColor
                        item.paint = item.data.toPaint()
                        invalidate()
                    } else {
                        performFill(e.x, e.y)
                    }
                }
                else -> {}
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x)
            val wy = screenToWorldY(e.y)
            val hit = findTextItemAt(wx, wy)
            if (hit != null) {
                hit.isEditing = true
                invalidate()
                onTextEditRequest?.invoke(hit, e.x, e.y, wx, wy)
                return true
            }
            if (currentTool == Tool.TEXT) {
                onTextEditRequest?.invoke(null, e.x, e.y, wx, wy)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (canvasMode == CanvasMode.INFINITE && !isDrawingTool()) {
                translateX -= distanceX
                translateY -= distanceY
                onScaleChanged?.invoke(scaleFactor)
                onCanvasTransformed?.invoke()
                invalidate()
                return true
            }
            return false
        }
    })

    private fun clampTranslation() {
        if (canvasMode == CanvasMode.INFINITE) return
        val pw = pageWidthPx() * scaleFactor
        val ph = pageHeightPx() * scaleFactor
        val margin = 20f
        val minTx = width - pw - margin
        val maxTx = margin
        translateX = translateX.coerceIn(minTx.coerceAtMost(maxTx), maxTx)
        if (canvasMode == CanvasMode.FIXED) {
            val minTy = height - ph - margin
            val maxTy = margin
            translateY = translateY.coerceIn(minTy.coerceAtMost(maxTy), maxTy)
        }
    }

    private fun loadBitmapAsync(path: String, onLoaded: (Bitmap?) -> Unit) {
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(path) }
        if (cached != null) {
            onLoaded(cached)
            return
        }
        imageLoadExecutor.execute {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val srcW = bounds.outWidth
                val srcH = bounds.outHeight
                if (srcW <= 0 || srcH <= 0) {
                    post { onLoaded(null) }
                    return@execute
                }
                val maxDim = 1920
                var sample = 1
                var tw = srcW
                var th = srcH
                while (tw > maxDim || th > maxDim) {
                    sample *= 2
                    tw /= 2
                    th /= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = BitmapFactory.decodeFile(path, opts)
                if (decoded != null) {
                    synchronized(bitmapCache) { bitmapCache.putBitmap(path, decoded) }
                    post { onLoaded(decoded) }
                } else {
                    post { onLoaded(null) }
                }
            } catch (e: Exception) {
                post { onLoaded(null) }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Serialization Engine Framework
    // ──────────────────────────────────────────────────────────────

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("CANVAS\u0001${canvasMode.name}\u0001${paperSize.name}\u0001${pageOrientation.name}\n")
        for (act in actions) {
            when (act) {
                is StrokeItem -> {
                    val d = act.data
                    val pts = d.points.joinToString(",")
                    sb.append("${d.type.name}|${d.color}|${d.strokeWidth}|${d.fill}|${d.rotation}|$pts\n")
                }
                is TextItem -> {
                    val spanStr = act.spans.joinToString(",") { "${it.start}:${it.end}:${it.type}:${it.value}" }
                    val cleanText = act.text.replace("\n", "\u0002")
                    sb.append("TEXT\u0001$cleanText\u0001${act.x}\u0001${act.y}\u0001${act.color}\u0001${act.size}\u0001${act.rotation}\u0001$spanStr\n")
                }
                is ImageItem -> {
                    sb.append("IMAGE\u0001${act.path}\u0001${act.x}\u0001${act.y}\u0001${act.w}\u0001${act.h}\u0001${act.rotation}\n")
                }
                is FillItem -> {
                    sb.append("FILL\u0001${act.path}\u0001${act.x}\u0001${act.y}\u0001${act.w}\u0001${act.h}\n")
                }
                is TableItem -> {
                    sb.append("TABLE\u0001${act.x}\u0001${act.y}\u0001${act.rows}\u0001${act.cols}\u0001${act.rotation}\n")
                    sb.append("ROW_H\u0001${act.rowHeights.joinToString(",")}\n")
                    sb.append("COL_W\u0001${act.colWidths.joinToString(",")}\n")
                    for (r in 0 until act.rows) {
                        for (c in 0 until act.cols) {
                            val cell = act.cells[r][c]
                            if (cell.text.isNotEmpty() || cell.bgColor != Color.WHITE || cell.textColor != Color.BLACK) {
                                val ct = cell.text.replace("\n", "\u0002")
                                val mir = cell.mergedInto?.first ?: -1
                                val mic = cell.mergedInto?.second ?: -1
                                sb.append("CELL\u0001$r\u0001$c\u0001${cell.textColor}\u0001${cell.bgColor}\u0001${cell.textSize}\u0001${cell.borderColor}\u0001${cell.borderWidth}\u0001${cell.alignment}\u0001$mir\u0001$mic\u0001$ct\n")
                            }
                        }
                    }
                    for ((k, v) in act.mergeSpans) {
                        sb.append("MERGE\u0001${k.first}\u0001${k.second}\u0001${v.rowSpan}\u0001${v.colSpan}\n")
                    }
                    sb.append("END_TABLE\n")
                }
            }
        }
        return sb.toString()
    }

    fun deserialize(data: String) {
        actions.clear()
        redoStack.clear()
        selectedItem = null
        selectedGroup = null
        val lines = data.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) { i++; continue }
            try {
                when {
                    line.startsWith("CANVAS\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 4) {
                            canvasMode = CanvasMode.valueOf(p[1])
                            paperSize = PaperSizeOption.valueOf(p[2])
                            pageOrientation = Orientation.valueOf(p[3])
                        }
                        i++
                    }
                    line.startsWith("TEXT\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 7) {
                            val txt = p[1].replace("\u0002", "\n")
                            val item = TextItem(txt, p[2].toFloat(), p[3].toFloat(), p[4].toInt(), p[5].toFloat(), p[6].toFloat())
                            if (p.size >= 8 && p[7].isNotBlank()) {
                                p[7].split(",").forEach { s ->
                                    val sp = s.split(":")
                                    if (sp.size == 4) item.spans.add(TextSpanData(sp[0].toInt(), sp[1].toInt(), sp[2][0], sp[3].toInt()))
                                }
                            }
                            actions.add(item)
                        }
                        i++
                    }
                    line.startsWith("IMAGE\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 7) actions.add(ImageItem(p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat()))
                        i++
                    }
                    line.startsWith("FILL\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 6) actions.add(FillItem(p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()))
                        i++
                    }
                    line.startsWith("TABLE\u0001") -> {
                        val p = line.split("\u0001")
                        val item = TableItem(p[1].toFloat(), p[2].toFloat(), p[5].toFloat())
                        item.rows = p[3].toInt()
                        item.cols = p[4].toInt()
                        i++
                        while (i < lines.size && !lines[i].startsWith("END_TABLE")) {
                            val tl = lines[i].trim()
                            when {
                                tl.startsWith("ROW_H\u0001") -> {
                                    val rh = tl.split("\u0001")[1].split(",").map { it.toFloat() }
                                    item.rowHeights.clear(); item.rowHeights.addAll(rh)
                                }
                                tl.startsWith("COL_W\u0001") -> {
                                    val cw = tl.split("\u0001")[1].split(",").map { it.toFloat() }
                                    item.colWidths.clear(); item.colWidths.addAll(cw)
                                }
                                tl.startsWith("CELL\u0001") -> {
                                    val cp = tl.split("\u0001")
                                    val r = cp[1].toInt(); val c = cp[2].toInt()
                                    val cell = item.cells[r][c]
                                    cell.textColor = cp[3].toInt()
                                    cell.bgColor = cp[4].toInt()
                                    cell.textSize = cp[5].toFloat()
                                    cell.borderColor = cp[6].toInt()
                                    cell.borderWidth = cp[7].toFloat()
                                    cell.alignment = cp[8].toInt()
                                    val mir = cp[9].toInt(); val mic = cp[10].toInt()
                                    cell.mergedInto = if (mir >= 0) Pair(mir, mic) else null
                                    cell.text = cp[11].replace("\u0002", "\n")
                                }
                                tl.startsWith("MERGE\u0001") -> {
                                    val mp = tl.split("\u0001")
                                    val r = mp[1].toInt(); val c = mp[2].toInt()
                                    item.mergeSpans[Pair(r, c)] = CellSpan(r, c, mp[3].toInt(), mp[4].toInt())
                                }
                            }
                            i++
                        }
                        actions.add(item)
                        i++
                    }
                    else -> {
                        val p = line.split("|")
                        if (p.size >= 5) {
                            val type = Tool.valueOf(p[0])
                            val color = p[1].toInt()
                            val sw = p[2].toFloat()
                            val fill = p[3].toBoolean()
                            if (p.size >= 6) {
                                val rot = p[4].toFloat()
                                val pts = if (p[5].isBlank()) mutableListOf() else p[5].split(",").map { it.toFloat() }.toMutableList()
                                val d = StrokeData(type, pts, color, sw, fill, rot)
                                actions.add(StrokeItem(d, d.buildPath(), d.toPaint()))
                            } else {
                                val pts = if (p[4].isBlank()) mutableListOf() else p[4].split(",").map { it.toFloat() }.toMutableList()
                                val d = StrokeData(type, pts, color, sw, fill)
                                actions.add(StrokeItem(d, d.buildPath(), d.toPaint()))
                            }
                        }
                        i++
                    }
                }
            } catch (e: Exception) { 
                i++ 
            }
        }
        invalidate()
    }

    // ──────────────────────────────────────────────────────────────
    //  View Engine Core Calculations & Utility Coordinates Extensions
    // ──────────────────────────────────────────────────────────────

    fun screenToWorldX(screenX: Float): Float = (screenX - translateX) / scaleFactor
    fun screenToWorldY(screenY: Float): Float = (screenY - translateY) / scaleFactor
    fun worldToScreenX(worldX: Float): Float = worldX * scaleFactor + translateX
    fun worldToScreenY(worldY: Float): Float = worldY * scaleFactor + translateY

    fun pageWidthPx(): Int {
        val mm = if (pageOrientation == Orientation.PORTRAIT) paperSize.widthMM else paperSize.heightMM
        return (mm * 3.7795f).toInt()
    }

    fun pageHeightPx(): Int {
        val mm = if (pageOrientation == Orientation.PORTRAIT) paperSize.heightMM else paperSize.widthMM
        return (mm * 3.7795f).toInt()
    }

    fun hasContent(): Boolean = actions.isNotEmpty()

    fun clearAll() {
        actions.clear()
        redoStack.clear()
        selectedItem = null
        selectedGroup = null
        invalidate()
    }

    fun undo() {
        if (actions.isNotEmpty()) {
            val removed = actions.removeAt(actions.size - 1)
            redoStack.add(removed)
            if (selectedItem === removed) selectedItem = null
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val act = redoStack.removeAt(redoStack.size - 1)
            actions.add(act)
            invalidate()
        }
    }

    fun addImageItem(filePath: String) {
        val wx = screenToWorldX(width / 2f)
        val wy = screenToWorldY(height / 2f)
        val item = ImageItem(filePath, wx - 150f, wy - 150f, 300f, 300f, 0f)
        actions.add(item)
        selectedItem = item
        currentTool = Tool.SELECT
        invalidate()
    }

    fun addTableItem(rows: Int, cols: Int) {
        val wx = screenToWorldX(width / 2f)
        val wy = screenToWorldY(height / 2f)
        val table = TableItem(wx - (cols * 100f) / 2f, wy - (rows * 60f) / 2f, 0f)
        table.rows = rows
        table.cols = cols
        table.rowHeights.clear(); table.rowHeights.addAll(List(rows) { 60f })
        table.colWidths.clear(); table.colWidths.addAll(List(cols) { 100f })
        actions.add(table)
        selectedItem = table
        currentTool = Tool.SELECT
        invalidate()
    }

    // ──────────────────────────────────────────────────────────────
    //  Filling Algorithmic Core Mechanisms
    // ──────────────────────────────────────────────────────────────

    private fun performFill(screenX: Float, screenY: Float) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        
        val p = Paint().apply { isAntiAlias = true; strokeWidth = currentStrokeWidth; style = Paint.Style.STROKE }
        for (act in actions) {
            if (act is StrokeItem && act.data.type != Tool.ERASER) {
                p.color = act.data.color
                p.strokeWidth = act.data.strokeWidth
                p.style = if (act.data.fill && CLOSED_SHAPES.contains(act.data.type)) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                canvas.drawPath(act.path, p)
            }
        }
        canvas.restore()

        val targetX = screenX.toInt().coerceIn(0, width - 1)
        val targetY = screenY.toInt().coerceIn(0, height - 1)
        val targetColor = bmp.getPixel(targetX, targetY)
        if (targetColor == fillColor) return

        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        val queue = LinkedBlockingQueue<Pair<Int, Int>>()
        queue.add(Pair(targetX, targetY))

        val fillPath = Path()
        val visited = BooleanArray(width * height)

        while (queue.isNotEmpty()) {
            val pt = queue.poll() ?: break
            val x = pt.first
            val y = pt.second
            val idx = y * width + x
            if (visited[idx]) continue
            visited[idx] = true

            if (pixels[idx] == targetColor) {
                var left = x
                while (left > 0 && pixels[y * width + (left - 1)] == targetColor) {
                    left--
                    visited[y * width + left] = true
                }
                var right = x
                while (right < width - 1 && pixels[y * width + (right + 1)] == targetColor) {
                    right++
                    visited[y * width + right] = true
                }
                
                fillPath.moveTo(screenToWorldX(left.toFloat()), screenToWorldY(y.toFloat()))
                fillPath.lineTo(screenToWorldX(right.toFloat() + 1f), screenToWorldY(y.toFloat()))

                for (cx in left..right) {
                    if (y > 0 && pixels[(y - 1) * width + cx] == targetColor && !visited[(y - 1) * width + cx]) {
                        queue.add(Pair(cx, y - 1))
                    }
                    if (y < height - 1 && pixels[(y + 1) * width + cx] == targetColor && !visited[(y + 1) * width + cx]) {
                        queue.add(Pair(cx, y + 1))
                    }
                }
            }
        }

        val fFile = File(ctx.cacheDir, "fill_${System.currentTimeMillis()}.png")
        try {
            val out = FileOutputStream(fFile)
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.close()
            val bounds = RectF()
            fillPath.computeBounds(bounds, true)
            val fillItem = FillItem(fFile.absolutePath, bounds.left, bounds.top, bounds.width(), bounds.height())
            fillItem.bitmap = bmp
            actions.add(fillItem)
            invalidate()
        } catch (e: Exception) {}
    }

    // ──────────────────────────────────────────────────────────────
    //  Search Intersection Point Operations
    // ──────────────────────────────────────────────────────────────

    private fun findItemAt(wx: Float, wy: Float): Any? {
        for (i in actions.indices.reversed()) {
            val act = actions[i]
            if (act is StrokeItem) {
                val bounds = RectF()
                act.path.computeBounds(bounds, true)
                val pad = act.data.strokeWidth + 15f
                bounds.inset(-pad, -pad)
                if (bounds.contains(wx, wy)) return act
            } else if (act is TextItem) {
                if (getTextBoundsWorld(act).contains(wx, wy)) return act
            } else if (act is ImageItem) {
                if (RectF(act.x, act.y, act.x + act.w, act.y + act.h).contains(wx, wy)) return act
            } else if (act is TableItem) {
                val totalW = act.colWidths.sum()
                val totalH = act.rowHeights.sum()
                if (RectF(act.x, act.y, act.x + totalW, act.y + totalH).contains(wx, wy)) return act
            }
        }
        return null
    }

    private fun findTextItemAt(wx: Float, wy: Float): TextItem? {
        val item = findItemAt(wx, wy)
        return if (item is TextItem) item else null
    }

    private fun getTextBoundsWorld(item: TextItem): RectF {
        val tp = TextPaint().apply { textSize = item.size; typeface = Typeface.DEFAULT }
        val sl = StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, 10000).build()
        var maxW = 0f
        for (r in 0 until sl.lineCount) { maxW = maxOf(maxW, sl.getLineWidth(r)) }
        return RectF(item.x, item.y, item.x + maxW, item.y + sl.height)
    }

    // ──────────────────────────────────────────────────────────────
    //  Rendering Engine pipeline
    // ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackgroundCanvas(canvas)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        for (act in actions) {
            when (act) {
                is StrokeItem -> {
                    if (act.data.type == Tool.ERASER) {
                        // Background matches drawing mode masks
                        act.paint.color = if (paperType == PaperType.BLANK_COLORED || paperType == PaperType.ENGINEERING) paperColor else Color.WHITE
                    }
                    canvas.save()
                    if (act.data.rotation != 0f) {
                        val bounds = RectF()
                        act.path.computeBounds(bounds, true)
                        canvas.rotate(act.data.rotation, bounds.centerX(), bounds.centerY())
                    }
                    canvas.drawPath(act.path, act.paint)
                    canvas.restore()
                }
                is TextItem -> {
                    if (!act.isEditing) drawTextItem(canvas, act)
                }
                is ImageItem -> drawImageItem(canvas, act)
                is FillItem -> drawFillItem(canvas, act)
                is TableItem -> drawTableItem(canvas, act)
            }
        }

        // Active UI Transformed HUD elements overlay
        drawActiveOverlays(canvas)

        canvas.restore()
        drawScreenSpaceOverlays(canvas)
    }

    private fun drawBackgroundCanvas(canvas: Canvas) {
        if (canvasMode == CanvasMode.INFINITE) {
            canvas.drawColor(if (paperType == PaperType.BLANK_COLORED || paperType == PaperType.ENGINEERING) paperColor else Color.WHITE)
            drawGridLines(canvas, 0f, 0f, width.toFloat(), height.toFloat(), true)
        } else {
            canvas.drawColor(Color.parseColor("#CCCCCC")) // Outside bounds pasteboard
            canvas.save()
            canvas.translate(translateX, translateY)
            canvas.scale(scaleFactor, scaleFactor)

            val pw = pageWidthPx().toFloat()
            val ph = pageHeightPx().toFloat()
            val bgPaint = Paint().apply { color = if (paperType == PaperType.BLANK_COLORED || paperType == PaperType.ENGINEERING) paperColor else Color.WHITE; style = Paint.Style.FILL }
            val shPaint = Paint().apply { color = Color.parseColor("#44000000"); style = Paint.Style.FILL }
            
            // Drop Shadow Bounds
            canvas.drawRect(5f, 5f, pw + 5f, ph + 5f, shPaint)
            canvas.drawRect(0f, 0f, pw, ph, bgPaint)

            canvas.save()
            canvas.clipRect(0f, 0f, pw, ph)
            drawGridLines(canvas, 0f, 0f, pw, ph, false)
            canvas.restore()

            canvas.restore()
        }
    }

    private fun drawGridLines(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, screenSpace: Boolean) {
        val paint = Paint().apply { strokeWidth = 1f; style = Paint.Style.STROKE }
        
        val space = if (screenSpace) 40f * scaleFactor else 40f
        val startX = if (screenSpace) {
            val rem = translateX % space
            if (rem < 0) rem + space else rem
        } else 0f
        val startY = if (screenSpace) {
            val rem = translateY % space
            if (rem < 0) rem + space else rem
        } else 0f

        when (paperType) {
            PaperType.GRID -> {
                paint.color = Color.parseColor("#E0E0E0")
                var x = startX
                while (x <= r) { canvas.drawLine(x, t, x, b, paint); x += space }
                var y = startY
                while (y <= b) { canvas.drawLine(l, y, r, y, paint); y += space }
            }
            PaperType.LINED -> {
                paint.color = Color.parseColor("#E0E0E0")
                var y = startY
                while (y <= b) { canvas.drawLine(l, y, r, y, paint); y += space }
            }
            PaperType.DOTS -> {
                paint.color = Color.parseColor("#BDBDBD")
                paint.style = Paint.Style.FILL
                val radius = if (screenSpace) 2f * scaleFactor else 2f
                var x = startX
                while (x <= r) {
                    var y = startY
                    while (y <= b) {
                        canvas.drawCircle(x, y, radius, paint)
                        y += space
                    }
                    x += space
                }
            }
            PaperType.ENGINEERING -> {
                paint.color = Color.parseColor("#C8E6C9")
                var x = startX; var cnt = 0
                while (x <= r) {
                    paint.strokeWidth = if (cnt % 5 == 0) 2f else 1f
                    canvas.drawLine(x, t, x, b, paint)
                    x += space; cnt++
                }
                var y = startY; cnt = 0
                while (y <= b) {
                    paint.strokeWidth = if (cnt % 5 == 0) 2f else 1f
                    canvas.drawLine(l, y, r, y, paint)
                    y += space; cnt++
                }
            }
            else -> {}
        }
    }

    private fun drawTextItem(canvas: Canvas, item: TextItem) {
        canvas.save()
        canvas.rotate(item.rotation, item.x, item.y)
        val tp = TextPaint().apply { textSize = item.size; color = item.color; isAntiAlias = true }
        val ss = SpannableString(item.text)
        for (s in item.spans) {
            val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            when (s.type) {
                'C' -> ss.setSpan(ForegroundColorSpan(s.value), s.start, s.end, flag)
                'B' -> ss.setSpan(BackgroundColorSpan(s.value), s.start, s.end, flag)
                'S' -> ss.setSpan(StyleSpan(s.value), s.start, s.end, flag)
                'U' -> ss.setSpan(UnderlineSpan(), s.start, s.end, flag)
            }
        }
        val sl = StaticLayout.Builder.obtain(ss, 0, ss.length, tp, 10000).build()
        sl.draw(canvas)
        canvas.restore()
    }

    private fun drawImageItem(canvas: Canvas, item: ImageItem) {
        if (item.bitmap == null && !item.loading) {
            item.loading = true
            loadBitmapAsync(item.path) { bmp ->
                item.bitmap = bmp
                item.loading = false
                invalidate()
            }
        }
        canvas.save()
        canvas.rotate(item.rotation, item.x + item.w / 2f, item.y + item.h / 2f)
        val bmp = item.bitmap
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(item.x, item.y, item.x + item.w, item.y + item.h), Paint().apply { isAntiAlias = true })
        } else {
            val p = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
            canvas.drawRect(item.x, item.y, item.x + item.w, item.y + item.h, p)
        }
        canvas.restore()
    }

    private fun drawFillItem(canvas: Canvas, item: FillItem) {
        val bmp = item.bitmap
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(item.x, item.y, item.x + item.w, item.y + item.h), Paint().apply { isAntiAlias = true })
        }
    }

    private fun drawTableItem(canvas: Canvas, act: TableItem) {
        canvas.save()
        canvas.rotate(act.rotation, act.x, act.y)
        val p = Paint().apply { isAntiAlias = true }
        var currY = act.y
        for (r in 0 until act.rows) {
            var currX = act.x
            for (c in 0 until act.cols) {
                val cell = act.cells[r][c]
                val cw = act.colWidths[c]
                val rh = act.rowHeights[r]
                
                if (cell.mergedInto == null) {
                    var finalW = cw
                    var finalH = rh
                    val span = act.mergeSpans[Pair(r, c)]
                    if (span != null) {
                        finalW = (c until c + span.colSpan).sumOf { act.colWidths[it].toDouble() }.toFloat()
                        finalH = (r until r + span.rowSpan).sumOf { act.rowHeights[it].toDouble() }.toFloat()
                    }
                    val rect = RectF(currX, currY, currX + finalW, currY + finalH)
                    p.color = cell.bgColor
                    p.style = Paint.Style.FILL
                    canvas.drawRect(rect, p)

                    p.color = cell.borderColor
                    p.strokeWidth = cell.borderWidth
                    p.style = Paint.Style.STROKE
                    canvas.drawRect(rect, p)

                    if (cell.text.isNotEmpty()) {
                        canvas.save()
                        canvas.clipRect(rect)
                        val tp = TextPaint().apply { textSize = cell.textSize; color = cell.textColor; isAntiAlias = true }
                        val sl = StaticLayout.Builder.obtain(cell.text, 0, cell.text.length, tp, (finalW - 8f).toInt().coerceAtLeast(10)).build()
                        canvas.translate(currX + 4f, currY + (finalH - sl.height) / 2f)
                        sl.draw(canvas)
                        canvas.restore()
                    }
                }
                currX += cw
            }
            currY += act.rowHeights[r]
        }
        canvas.restore()
    }

    private fun drawActiveOverlays(canvas: Canvas) {
        val p = Paint().apply { isAntiAlias = true }
        
        // Arc Segment Modifiers Overlay HUD
        val arcItem = activeArcItem
        if (currentTool == Tool.ARC && arcItem != null) {
            p.style = Paint.Style.FILL
            for (i in 0 until arcItem.data.points.size step 2) {
                p.color = if (i / 2 == arcDragPointIndex) Color.RED else Color.BLUE
                canvas.drawCircle(arcItem.data.points[i], arcItem.data.points[i + 1], 10f, p)
            }
        }

        // Object Bounds Frame Selector HUD Transformation Handle Indicators Overlay
        val sel = selectedItem
        if (currentTool == Tool.SELECT && sel != null) {
            val bounds = RectF()
            var rot = 0f
            var px = 0f; var py = 0f
            when (sel) {
                is StrokeItem -> {
                    sel.path.computeBounds(bounds, true)
                    rot = sel.data.rotation
                    px = bounds.centerX(); py = bounds.centerY()
                }
                is TextItem -> {
                    bounds.set(getTextBoundsWorld(sel))
                    rot = sel.rotation
                    px = sel.x; py = sel.y
                }
                is ImageItem -> {
                    bounds.set(sel.x, sel.y, sel.x + sel.w, sel.y + sel.h)
                    rot = sel.rotation
                    px = bounds.centerX(); py = bounds.centerY()
                }
                is TableItem -> {
                    val tw = sel.colWidths.sum()
                    val th = sel.rowHeights.sum()
                    bounds.set(sel.x, sel.y, sel.x + tw, sel.y + th)
                    rot = sel.rotation
                    px = sel.x; py = sel.y
                }
            }
            canvas.save()
            canvas.rotate(rot, px, py)
            p.color = Color.parseColor("#442196F3")
            p.style = Paint.Style.FILL
            canvas.drawRect(bounds, p)
            p.color = Color.parseColor("#2196F3")
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            canvas.drawRect(bounds, p)

            // Outer Structural Handles
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            val hSize = 12f
            canvas.drawRect(bounds.left - hSize, bounds.top - hSize, bounds.left + hSize, bounds.top + hSize, p)
            canvas.drawRect(bounds.right - hSize, bounds.top - hSize, bounds.right + hSize, bounds.top + hSize, p)
            canvas.drawRect(bounds.left - hSize, bounds.bottom - hSize, bounds.left + hSize, bounds.bottom + hSize, p)
            canvas.drawRect(bounds.right - hSize, bounds.bottom - hSize, bounds.right + hSize, bounds.bottom + hSize, p)
            p.color = Color.parseColor("#2196F3")
            p.style = Paint.Style.STROKE
            canvas.drawRect(bounds.left - hSize, bounds.top - hSize, bounds.left + hSize, bounds.top + hSize, p)
            canvas.drawRect(bounds.right - hSize, bounds.top - hSize, bounds.right + hSize, bounds.top + hSize, p)
            canvas.drawRect(bounds.left - hSize, bounds.bottom - hSize, bounds.left + hSize, bounds.bottom + hSize, p)
            canvas.drawRect(bounds.right - hSize, bounds.bottom - hSize, bounds.right + hSize, bounds.bottom + hSize, p)

            // Rotation Pole
            val midX = bounds.centerX()
            canvas.drawLine(midX, bounds.top, midX, bounds.top - 40f, p)
            p.style = Paint.Style.FILL
            canvas.drawCircle(midX, bounds.top - 40f, hSize, p)
            p.style = Paint.Style.STROKE
            canvas.drawCircle(midX, bounds.top - 40f, hSize, p)

            canvas.restore()
        }

        // Multi Group Lasso Overlay Window Rendering Target
        if (currentTool == Tool.AUTOSELECT) {
            val rPath = regionPath
            if (rPath != null) {
                p.color = Color.parseColor("#3300E676")
                p.style = Paint.Style.FILL
                canvas.drawPath(rPath, p)
                p.color = Color.parseColor("#00E676")
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                canvas.drawPath(rPath, p)
            }
            val g = selectedGroup
            if (g != null) {
                val gb = getGroupBounds(g)
                p.color = Color.parseColor("#332196F3")
                p.style = Paint.Style.FILL
                canvas.drawRect(gb, p)
                p.color = Color.parseColor("#2196F3")
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                canvas.drawRect(gb, p)
            }
        }

        // Framing Window Layout Target Export Canvas Rendering Setup
        if (currentTool == Tool.EXPORT_WINDOW) {
            val s = exportWindowStart
            val e = exportWindowEnd
            if (s != null && e != null) {
                val r = RectF(s.first, s.second, e.first, e.second)
                p.color = Color.parseColor("#44FF9800")
                p.style = Paint.Style.FILL
                canvas.drawRect(r, p)
                p.color = Color.parseColor("#FF9800")
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                canvas.drawRect(r, p)
            }
        }
    }

    private fun drawScreenSpaceOverlays(canvas: Canvas) {
        val hx = hoverX
        val hy = hoverY
        if (currentTool == Tool.ERASER && hx != null && hy != null) {
            val p = Paint().apply { color = Color.parseColor("#44FF5252"); style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawCircle(hx, hy, eraserSize * scaleFactor / 2f, p)
            p.color = Color.RED
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            canvas.drawCircle(hx, hy, eraserSize * scaleFactor / 2f, p)
        }
    }

    private fun getGroupBounds(group: List<Any>): RectF {
        val out = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (act in group) {
            when (act) {
                is StrokeItem -> { val b = RectF(); act.path.computeBounds(b, true); out.union(b) }
                is TextItem -> out.union(getTextBoundsWorld(act))
                is ImageItem -> out.union(RectF(act.x, act.y, act.x + act.w, act.y + act.h))
                is TableItem -> out.union(RectF(act.x, act.y, act.x + act.colWidths.sum(), act.y + act.rowHeights.sum()))
            }
        }
        return out
    }

    // ──────────────────────────────────────────────────────────────
    //  MotionEvent Routing Dispatcher Mechanics
    // ──────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true
        if (gestureDetector.onTouchEvent(event)) {
            if (event.action == MotionEvent.ACTION_UP) {
                hoverX = null; hoverY = null; invalidate()
            }
            return true
        }

        val action = event.actionMasked
        
        // Stylus tracking overrides and palm rejection parameters
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            if (action == MotionEvent.ACTION_DOWN) isStylusDown = true
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) isStylusDown = false
        }

        // Two-finger viewport panning support
        if (event.pointerCount == 2) {
            val x0 = event.getX(0); val y0 = event.getY(0)
            val x1 = event.getX(1); val y1 = event.getY(1)
            val cx = (x0 + x1) / 2f
            val cy = (y0 + y1) / 2f
            if (action == MotionEvent.ACTION_MOVE) {
                if (twoFingerLastX != 0f || twoFingerLastY != 0f) {
                    translateX += cx - twoFingerLastX
                    translateY += cy - twoFingerLastY
                    clampTranslation()
                    onScaleChanged?.invoke(scaleFactor)
                    onCanvasTransformed?.invoke()
                    invalidate()
                }
                twoFingerLastX = cx
                twoFingerLastY = cy
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                twoFingerLastX = 0f; twoFingerLastY = 0f
            }
            return true
        }

        val ex = event.x
        val ey = event.y
        val wx = screenToWorldX(ex)
        val wy = screenToWorldY(ey)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                drawingPointerId = event.getPointerId(0)
                if (isDrawingTool()) {
                    redoStack.clear()
                    handleDrawingToolDown(wx, wy)
                } else {
                    handleTransformToolDown(ex, ey, wx, wy)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.findPointerIndex(drawingPointerId) == 0) {
                    if (isDrawingTool()) {
                        hoverX = ex; hoverY = ey
                        handleDrawingToolMove(wx, wy)
                    } else {
                        handleTransformToolMove(ex, ey, wx, wy)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.findPointerIndex(drawingPointerId) == 0) {
                    if (isDrawingTool()) {
                        handleDrawingToolUp()
                    } else {
                        handleTransformToolUp()
                    }
                }
                hoverX = null; hoverY = null
                drawingPointerId = -1
            }
        }
        invalidate()
        return true
    }

    // ──────────────────────────────────────────────────────────────
    //  Tool Action Phase Pipelines
    // ──────────────────────────────────────────────────────────────

    private fun handleDrawingToolDown(wx: Float, wy: Float) {
        when (currentTool) {
            Tool.PEN, Tool.ERASER -> {
                val pts = mutableListOf(wx, wy)
                val d = StrokeData(currentTool, pts, currentColor, currentStrokeWidth, false)
                val item = StrokeItem(d, d.buildPath(), d.toPaint())
                currentItem = item
                actions.add(item)
                if (currentTool == Tool.ERASER && eraserMode == EraserMode.AREA) {
                    performAreaEraser(wx, wy)
                }
            }
            Tool.ARC -> {
                val hitArc = findArcPointHit(wx, wy)
                if (hitArc != null) {
                    activeArcItem = hitArc.first
                    arcDragPointIndex = hitArc.second
                } else {
                    val pts = mutableListOf(wx, wy)
                    val d = StrokeData(Tool.ARC, pts, currentColor, currentStrokeWidth, false)
                    val item = StrokeItem(d, d.buildPath(), d.toPaint())
                    currentItem = item
                    actions.add(item)
                    activeArcItem = item
                    arcDragPointIndex = -1
                }
            }
            Tool.FILL -> {} 
            else -> {
                if (currentTool in SHAPE_TOOLS) {
                    val pts = mutableListOf(wx, wy, wx, wy)
                    val d = StrokeData(currentTool, pts, currentColor, currentStrokeWidth, fillShapes, 0f)
                    d.color = if (fillShapes) fillColor else currentColor
                    val item = StrokeItem(d, d.buildPath(), d.toPaint())
                    currentItem = item
                    actions.add(item)
                }
            }
        }
    }

    private fun handleDrawingToolMove(wx: Float, wy: Float) {
        when (currentTool) {
            Tool.PEN, Tool.ERASER -> {
                val item = currentItem
                if (item != null) {
                    item.data.points.add(wx)
                    item.data.points.add(wy)
                    item.path = item.data.buildPath()
                    if (currentTool == Tool.ERASER) {
                        if (eraserMode == EraserMode.OBJECT) performObjectEraser(wx, wy)
                        else performAreaEraser(wx, wy)
                    }
                }
            }
            Tool.ARC -> {
                val arcItem = activeArcItem
                if (arcItem != null) {
                    if (arcDragPointIndex >= 0) {
                        arcItem.data.points[arcDragPointIndex * 2] = wx
                        arcItem.data.points[arcDragPointIndex * 2 + 1] = wy
                        arcItem.path = arcItem.data.buildPath()
                    } else {
                        val item = currentItem
                        if (item != null) {
                            if (item.data.points.size == 2) {
                                item.data.points.add(wx)
                                item.data.points.add(wy)
                            } else {
                                item.data.points[2] = wx
                                item.data.points[3] = wy
                            }
                            item.path = item.data.buildPath()
                        }
                    }
                }
            }
            Tool.FILL -> {}
            else -> {
                if (currentTool in SHAPE_TOOLS) {
                    val item = currentItem
                    if (item != null && item.data.points.size >= 4) {
                        item.data.points[2] = wx
                        item.data.points[3] = wy
                        item.path = item.data.buildPath()
                    }
                }
            }
        }
    }

    private fun handleDrawingToolUp() {
        if (currentTool == Tool.ARC && arcDragPointIndex == -1) {
            val item = currentItem
            if (item != null && item.data.points.size >= 4) {
                val x1 = item.data.points[0]; val y1 = item.data.points[1]
                val x2 = item.data.points[2]; val y2 = item.data.points[3]
                val updated = mutableListOf(x1, y1)
                val div = arcDivisions.coerceIn(2, 12)
                for (k in 1 until div) {
                    val f = k.toFloat() / div
                    val cx = x1 + (x2 - x1) * f
                    val cy = y1 + (y2 - y1) * f
                    updated.add(cx)
                    updated.add(cy)
                }
                updated.add(x2)
                updated.add(y2)
                item.data.points.clear()
                item.data.points.addAll(updated)
                item.path = item.data.buildPath()
            }
        }
        currentItem = null
    }

    // ──────────────────────────────────────────────────────────────
    //  Transform and Selection Operations Logic
    // ──────────────────────────────────────────────────────────────

    private fun handleTransformToolDown(ex: Float, ey: Float, wx: Float, wy: Float) {
        if (currentTool == Tool.EXPORT_WINDOW) {
            exportWindowStart = Pair(wx, wy)
            exportWindowEnd = Pair(wx, wy)
            return
        }
        if (currentTool == Tool.AUTOSELECT) {
            if (selectedGroup != null && getGroupBounds(selectedGroup!!).contains(wx, wy)) {
                activeHandle = HandleType.MOVE
                groupMoveStartX = wx
                groupMoveStartY = wy
                val g = selectedGroup!!
                groupResizeItemSnapshots = g.map { act ->
                    when (act) {
                        is StrokeItem -> { val b = RectF(); act.path.computeBounds(b, true); floatArrayOf(b.left, b.top, b.width(), b.height(), act.data.rotation) }
                        is TextItem -> floatArrayOf(act.x, act.y, act.size, act.rotation)
                        is ImageItem -> floatArrayOf(act.x, act.y, act.w, act.h, act.rotation)
                        is TableItem -> floatArrayOf(act.x, act.y, act.rotation)
                        else -> null
                    }
                }
            } else {
                selectedGroup = null
                regionStart = Pair(wx, wy)
                regionPath = Path().apply { moveTo(wx, wy) }
            }
            return
        }

        val sel = selectedItem
        if (sel != null) {
            val h = evaluateHandleHit(sel, wx, wy)
            if (h != HandleType.NONE) {
                activeHandle = h
                dragStartWorldX = wx; dragStartWorldY = wy
                resizePrevWorldX = wx; resizePrevWorldY = wy
                val bounds = getElementBoundsWorld(sel)
                dragStartPivotX = bounds.centerX()
                dragStartPivotY = bounds.centerY()
                dragStartAngle = kotlin.math.atan2((wy - dragStartPivotY).toDouble(), (wx - dragStartPivotX).toDouble()).toFloat()
                dragStartRotation = getElementRotation(sel)
                return
            }
        }

        val hit = findItemAt(wx, wy)
        if (hit != null) {
            selectedItem = hit
            currentTool = Tool.SELECT
            activeHandle = HandleType.MOVE
            dragStartWorldX = wx; dragStartWorldY = wy
        } else {
            selectedItem = null
        }
    }

    private fun handleTransformToolMove(ex: Float, ey: Float, wx: Float, wy: Float) {
        if (currentTool == Tool.EXPORT_WINDOW) {
            exportWindowEnd = Pair(wx, wy)
            return
        }
        if (currentTool == Tool.AUTOSELECT) {
            val rPath = regionPath
            val sGroup = selectedGroup
            if (rPath != null && autoSelectShape == AutoSelectShape.FREEFORM) {
                rPath.lineTo(wx, wy)
            } else if (rPath != null && regionStart != null && autoSelectShape == AutoSelectShape.RECTANGLE) {
                rPath.reset()
                val sx = regionStart!!.first; val sy = regionStart!!.second
                rPath.addRect(minOf(sx, wx), minOf(sy, wy), maxOf(sx, wx), maxOf(sy, wy), Path.Direction.CW)
            } else if (activeHandle == HandleType.MOVE && sGroup != null) {
                val dx = wx - groupMoveStartX
                val dy = wy - groupMoveStartY
                for (act in sGroup) {
                    moveElement(act, dx, dy)
                }
                groupMoveStartX = wx
                groupMoveStartY = wy
            }
            return
        }

        val sel = selectedItem ?: return
        val dx = wx - dragStartWorldX
        val dy = wy - dragStartWorldY

        if (activeHandle == HandleType.MOVE) {
            moveElement(sel, dx, dy)
            dragStartWorldX = wx; dragStartWorldY = wy
        } else if (activeHandle == HandleType.ROTATE) {
            val currAngle = kotlin.math.atan2((wy - dragStartPivotY).toDouble(), (wx - dragStartPivotX).toDouble()).toFloat()
            val rotDeg = Math.toDegrees((currAngle - dragStartAngle).toDouble()).toFloat()
            setElementRotation(sel, dragStartRotation + rotDeg)
        } else if (activeHandle != HandleType.NONE) {
            resizeElement(sel, activeHandle, wx, wy)
        }
    }

    private fun handleTransformToolUp() {
        if (currentTool == Tool.EXPORT_WINDOW) {
            val s = exportWindowStart
            val e = exportWindowEnd
            if (s != null && e != null) {
                onExportWindowSelected?.invoke(minOf(s.first, e.first), minOf(s.second, e.second), maxOf(s.first, e.first), maxOf(s.second, e.second))
            }
            exportWindowStart = null; exportWindowEnd = null
            return
        }
        if (currentTool == Tool.AUTOSELECT && regionPath != null) {
            performAutoSelectGroup()
            regionPath = null
            regionStart = null
        }
        activeHandle = HandleType.NONE
    }

    private fun performAutoSelectGroup() {
        val rPath = regionPath ?: return
        val rect = RectF()
        rPath.computeBounds(rect, true)
        val region = Region().apply { setPath(rPath, Region(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())) }
        val found = mutableListOf<Any>()
        for (act in actions) {
            val b = getElementBoundsWorld(act)
            if (autoSelectDivide == AutoSelectDivide.WHOLE) {
                if (rect.contains(b)) found.add(act)
            } else {
                if (RectF.intersects(rect, b)) found.add(act)
            }
        }
        if (found.isNotEmpty()) {
            selectedGroup = found
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Geometric Core Matrix Mutation Modifiers
    // ──────────────────────────────────────────────────────────────

    private fun getElementBoundsWorld(sel: Any): RectF {
        val bounds = RectF()
        when (sel) {
            is StrokeItem -> sel.path.computeBounds(bounds, true)
            is TextItem -> bounds.set(getTextBoundsWorld(sel))
            is ImageItem -> bounds.set(sel.x, sel.y, sel.x + sel.w, sel.y + sel.h)
            is TableItem -> bounds.set(sel.x, sel.y, sel.x + sel.colWidths.sum(), sel.y + sel.rowHeights.sum())
        }
        return bounds
    }

    private fun getElementRotation(sel: Any): Float = when (sel) {
        is StrokeItem -> sel.data.rotation
        is TextItem -> sel.rotation
        is ImageItem -> sel.rotation
        is TableItem -> sel.rotation
        else -> 0f
    }

    private fun setElementRotation(sel: Any, r: Float) {
        when (sel) {
            is StrokeItem -> sel.data.rotation = r
            is TextItem -> sel.rotation = r
            is ImageItem -> sel.rotation = r
            is TableItem -> sel.rotation = r
        }
    }

    private fun moveElement(sel: Any, dx: Float, dy: Float) {
        when (sel) {
            is StrokeItem -> {
                for (j in 0 until sel.data.points.size step 2) {
                    sel.data.points[j] += dx
                    sel.data.points[j + 1] += dy
                }
                sel.path = sel.data.buildPath()
            }
            is TextItem -> { sel.x += dx; sel.y += dy }
            is ImageItem -> { sel.x += dx; sel.y += dy }
            is TableItem -> { sel.x += dx; sel.y += dy }
        }
    }

    private fun evaluateHandleHit(sel: Any, wx: Float, wy: Float): HandleType {
        val b = getElementBoundsWorld(sel)
        val r = getElementRotation(sel)
        val px = if (sel is TextItem || sel is TableItem) b.left else b.centerX()
        val py = if (sel is TextItem || sel is TableItem) b.top else b.centerY()

        // Un-rotate the point back into coordinate systems context space frame
        val rad = Math.toRadians((-r).toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val dx = wx - px; val dy = wy - py
        val rx = px + (dx * cos - dy * sin)
        val ry = py + (dx * sin + dy * cos)

        val threshold = 25f / scaleFactor
        
        val midX = b.centerX()
        if (kotlin.math.hypot((rx - midX).toDouble(), (ry - (b.top - 40f)).toDouble()) < threshold) return HandleType.ROTATE

        if (kotlin.math.hypot((rx - b.left).toDouble(), (ry - b.top).toDouble()) < threshold) return HandleType.TL
        if (kotlin.math.hypot((rx - b.right).toDouble(), (ry - b.top).toDouble()) < threshold) return HandleType.TR
        if (kotlin.math.hypot((rx - b.left).toDouble(), (ry - b.bottom).toDouble()) < threshold) return HandleType.BL
        if (kotlin.math.hypot((rx - b.right).toDouble(), (ry - b.bottom).toDouble()) < threshold) return HandleType.BR

        if (b.contains(rx, ry)) return HandleType.MOVE
        return HandleType.NONE
    }

    private fun resizeElement(sel: Any, hType: HandleType, wx: Float, wy: Float) {
        val b = getElementBoundsWorld(sel)
        when (sel) {
            is ImageItem -> {
                when (hType) {
                    HandleType.BR -> { sel.w = (wx - sel.x).coerceAtLeast(30f); sel.h = (wy - sel.y).coerceAtLeast(30f) }
                    HandleType.BL -> { val origR = sel.x + sel.w; sel.x = wx.coerceAtMost(origR - 30f); sel.w = origR - sel.x; sel.h = (wy - sel.y).coerceAtLeast(30f) }
                    HandleType.TR -> { val origB = sel.y + sel.h; sel.y = wy.coerceAtMost(origB - 30f); sel.h = origB - sel.y; sel.w = (wx - sel.x).coerceAtLeast(30f) }
                    HandleType.TL -> { 
                        val origR = sel.x + sel.w; val origB = sel.y + sel.h
                        sel.x = wx.coerceAtMost(origR - 30f); sel.w = origR - sel.x
                        sel.y = wy.coerceAtMost(origB - 30f); sel.h = origB - sel.y
                    }
                    else -> {}
                }
            }
            is TextItem -> {
                val factor = if (hType == HandleType.BR || hType == HandleType.TR) 1f else -1f
                sel.size = (sel.size + (wx - resizePrevWorldX) * 0.5f * factor).coerceIn(8f, 200f)
                resizePrevWorldX = wx
            }
            is StrokeItem -> {
                if (sel.data.points.size >= 4) {
                    if (BBOX_RESIZE_SHAPES.contains(sel.data.type)) {
                        when (hType) {
                            HandleType.BR -> { sel.data.points[2] = wx; sel.data.points[3] = wy }
                            HandleType.TL -> { sel.data.points[0] = wx; sel.data.points[1] = wy }
                            HandleType.TR -> { sel.data.points[2] = wx; sel.data.points[1] = wy }
                            HandleType.BL -> { sel.data.points[0] = wx; sel.data.points[3] = wy }
                            else -> {}
                        }
                    } else if (ENDPOINT_RESIZE_SHAPES.contains(sel.data.type)) {
                        if (hType == HandleType.BR || hType == HandleType.TR) {
                            sel.data.points[2] = wx; sel.data.points[3] = wy
                        } else {
                            sel.data.points[0] = wx; sel.data.points[1] = wy
                        }
                    }
                    sel.path = sel.data.buildPath()
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Eraser Intersect Filtering Framework
    // ──────────────────────────────────────────────────────────────

    private fun performObjectEraser(wx: Float, wy: Float) {
        val radius = eraserSize / 2f
        val hit = findItemAt(wx, wy)
        if (hit != null && hit !is StrokeItem) {
            actions.remove(hit)
            if (selectedItem === hit) selectedItem = null
            return
        }
        val targetRect = RectF(wx - radius, wy - radius, wx + radius, wy + radius)
        val iterator = actions.iterator()
        while (iterator.hasNext()) {
            val act = iterator.next()
            if (act is StrokeItem) {
                val bounds = RectF()
                act.path.computeBounds(bounds, true)
                if (RectF.intersects(bounds, targetRect)) {
                    iterator.remove()
                    if (selectedItem === act) selectedItem = null
                }
            }
        }
    }

    private fun performAreaEraser(wx: Float, wy: Float) {
        val radius = eraserSize / 2f
        val targetRect = RectF(wx - radius, wy - radius, wx + radius, wy + radius)
        val toAdd = mutableListOf<StrokeItem>()
        val iterator = actions.iterator()

        while (iterator.hasNext()) {
            val act = iterator.next()
            if (act is StrokeItem && act.data.type == Tool.PEN) {
                val bounds = RectF()
                act.path.computeBounds(bounds, true)
                if (RectF.intersects(bounds, targetRect)) {
                    iterator.remove()
                    if (selectedItem === act) selectedItem = null
                    val splitStrokes = splitStrokeData(act.data, wx, wy, radius)
                    for (sd in splitStrokes) {
                        toAdd.add(StrokeItem(sd, sd.buildPath(), sd.toPaint()))
                    }
                }
            }
        }
        actions.addAll(toAdd)
    }

    private fun splitStrokeData(d: StrokeData, cx: Float, cy: Float, r: Float): List<StrokeData> {
        val res = mutableListOf<StrokeData>()
        var currentPoints = mutableListOf<Float>()
        var j = 0
        while (j + 1 < d.points.size) {
            val px = d.points[j]
            val py = d.points[j + 1]
            val inside = kotlin.math.hypot((px - cx).toDouble(), (py - cy).toDouble()) <= r
            if (inside) {
                if (currentPoints.size >= 2) {
                    res.add(StrokeData(d.type, currentPoints, d.color, d.strokeWidth, d.fill, d.rotation))
                    currentPoints = mutableListOf()
                }
            } else {
                currentPoints.add(px)
                currentPoints.add(py)
            }
            j += 2
        }
        if (currentPoints.size >= 2) {
            res.add(StrokeData(d.type, currentPoints, d.color, d.strokeWidth, d.fill, d.rotation))
        }
        return res
    }

    private fun findArcPointHit(wx: Float, wy: Float): Pair<StrokeItem, Int>? {
        val threshold = 25f / scaleFactor
        for (i in actions.indices.reversed()) {
            val act = actions[i]
            if (act is StrokeItem && act.data.type == Tool.ARC) {
                for (k in 0 until act.data.points.size step 2) {
                    val px = act.data.points[k]
                    val py = act.data.points[k + 1]
                    if (kotlin.math.hypot((px - wx).toDouble(), (py - wy).toDouble()) < threshold) {
                        return Pair(act, k / 2)
                    }
                }
            }
        }
        return null
    }
}
