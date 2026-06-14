package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import android.graphics.Rect
import android.graphics.Region
import java.io.File
import java.io.FileOutputStream

enum class Tool {
    SELECT, FILL, PEN, ERASER, LINE, RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE,
    TRIANGLE, DIAMOND, ARROW, STAR, PENTAGON, HEXAGON, CURVE, CROSS, ARC, TEXT, AUTOSELECT
}
enum class PaperType { BLANK, BLANK_COLORED, LINED, GRID, DOTS, ENGINEERING }
enum class EraserMode { OBJECT, AREA }
enum class AutoSelectShape { RECTANGLE, FREEFORM }
enum class AutoSelectDivide { WHOLE, DIVIDED }
enum class CanvasMode { INFINITE, FIXED, PAGINATED }
enum class Orientation { PORTRAIT, LANDSCAPE }

enum class PaperSizeOption(val widthMM: Float, val heightMM: Float) {
    A4(210f, 297f),
    LETTER(215.9f, 279.4f),
    A3(297f, 420f),
    A5(148f, 210f),
    LEGAL(215.9f, 355.6f),
    TABLOID(279.4f, 431.8f),
    A2(420f, 594f),
    A1(594f, 841f),
    A0(841f, 1189f),
    B4(250f, 353f),
    B5(176f, 250f),
    EXECUTIVE(184.1f, 266.7f)
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

class StrokeData(
    val type: Tool,
    val points: MutableList<Float>,
    var color: Int,
    var strokeWidth: Float,
    var fill: Boolean,
    var rotation: Float = 0f
) {
    fun buildPath(): Path {
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
            // Catmull-Rom spline through all arc points for smooth curves
            if (points.size >= 2) {
                path.moveTo(points[0], points[1])
                if (points.size == 2) {
                    // single point, nothing to draw
                } else if (points.size == 4) {
                    path.lineTo(points[2], points[3])
                } else {
                    var i = 0
                    while (i + 3 < points.size) {
                        val x0 = if (i == 0) points[0] else points[i - 2]
                        val y0 = if (i == 0) points[1] else points[i - 1]
                        val x1 = points[i]; val y1 = points[i + 1]
                        val x2 = points[i + 2]; val y2 = points[i + 3]
                        val x3 = if (i + 4 < points.size) points[i + 4] else x2
                        val y3 = if (i + 5 < points.size) points[i + 5] else y2
                        val cp1x = x1 + (x2 - x0) / 6f
                        val cp1y = y1 + (y2 - y0) / 6f
                        val cp2x = x2 - (x3 - x1) / 6f
                        val cp2y = y2 - (y3 - y1) / 6f
                        path.cubicTo(cp1x, cp1y, cp2x, cp2y, x2, y2)
                        i += 2
                    }
                }
            }
            return path
        }
        if (points.size < 4) return path

        val x1 = points[0]; val y1 = points[1]
        val x2 = points[2]; val y2 = points[3]
        val left = minOf(x1, x2); val right = maxOf(x1, x2)
        val top = minOf(y1, y2); val bottom = maxOf(y1, y2)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        when (type) {
            Tool.LINE -> { path.moveTo(x1, y1); path.lineTo(x2, y2) }
            Tool.RECTANGLE -> path.addRect(RectF(left, top, right, bottom), Path.Direction.CW)
            Tool.ROUNDED_RECT -> {
                val rx = ((right - left) * 0.15f).coerceAtMost(40f)
                val ry = ((bottom - top) * 0.15f).coerceAtMost(40f)
                path.addRoundRect(RectF(left, top, right, bottom), rx, ry, Path.Direction.CW)
            }
            Tool.CIRCLE -> {
                val radius = kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                path.addCircle(x1, y1, radius, Path.Direction.CW)
            }
            Tool.ELLIPSE -> path.addOval(RectF(left, top, right, bottom), Path.Direction.CW)
            Tool.TRIANGLE -> {
                path.moveTo(cx, top); path.lineTo(right, bottom); path.lineTo(left, bottom); path.close()
            }
            Tool.DIAMOND -> {
                path.moveTo(cx, top); path.lineTo(right, cy); path.lineTo(cx, bottom); path.lineTo(left, cy); path.close()
            }
            Tool.ARROW -> {
                path.moveTo(x1, y1); path.lineTo(x2, y2)
                val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                val arrowLen = 20f
                val arrowAngle = Math.PI / 7
                val ax1 = x2 - (arrowLen * kotlin.math.cos(angle - arrowAngle)).toFloat()
                val ay1 = y2 - (arrowLen * kotlin.math.sin(angle - arrowAngle)).toFloat()
                val ax2 = x2 - (arrowLen * kotlin.math.cos(angle + arrowAngle)).toFloat()
                val ay2 = y2 - (arrowLen * kotlin.math.sin(angle + arrowAngle)).toFloat()
                path.moveTo(x2, y2); path.lineTo(ax1, ay1)
                path.moveTo(x2, y2); path.lineTo(ax2, ay2)
            }
            Tool.CURVE -> {
                val dx = x2 - x1; val dy = y2 - y1
                val ctrlX = cx - dy * 0.25f
                val ctrlY = cy + dx * 0.25f
                path.moveTo(x1, y1); path.quadTo(ctrlX, ctrlY, x2, y2)
            }
            Tool.CROSS -> {
                path.moveTo(left, cy); path.lineTo(right, cy)
                path.moveTo(cx, top); path.lineTo(cx, bottom)
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
        val radiusX = (right - left) / 2f
        val radiusY = (bottom - top) / 2f
        val pointCount = if (isStar) sides * 2 else sides
        val angleStep = 2 * Math.PI / pointCount
        val startAngle = -Math.PI / 2
        for (i in 0 until pointCount) {
            val r = if (isStar && i % 2 == 1) 0.5f else 1f
            val angle = startAngle + i * angleStep
            val px = cx + (kotlin.math.cos(angle) * radiusX * r).toFloat()
            val py = cy + (kotlin.math.sin(angle) * radiusY * r).toFloat()
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
    var bitmap: Bitmap? = null
}

class FillItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float) {
    var bitmap: Bitmap? = null
}

class LeakMarker(var x: Float, var y: Float)

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ctx = context
    private val actions = mutableListOf<Any>()
    private val redoStack = mutableListOf<Any>()
    private var currentItem: StrokeItem? = null
    val leakMarkers = mutableListOf<LeakMarker>()

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

    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var eraserSize: Float = 40f
    var eraserMode: EraserMode = EraserMode.OBJECT
    var fillShapes: Boolean = false
    var fillColor: Int = Color.RED
    var arcDivisions: Int = 3
    var paperType: PaperType = PaperType.GRID
    var paperColor: Int = Color.parseColor("#FFFDE7")
    var defaultTextSize: Float = 36f

    var autoSelectShape: AutoSelectShape = AutoSelectShape.RECTANGLE
    var autoSelectDivide: AutoSelectDivide = AutoSelectDivide.WHOLE
    var canvasMode: CanvasMode = CanvasMode.INFINITE
    var paperSize: PaperSizeOption = PaperSizeOption.A4
    var pageOrientation: Orientation = Orientation.PORTRAIT

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

    private var activeArcItem: StrokeItem? = null
    private var arcDragPointIndex = -1

    var onTextEditRequest: ((TextItem?, Float, Float, Float, Float) -> Unit)? = null

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private var prevFocusX = 0f
    private var prevFocusY = 0f

    private var hoverX: Float? = null
    private var hoverY: Float? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val minScale = if (canvasMode != CanvasMode.INFINITE) {
                val pageW = pageWidthPx(); val pageH = pageHeightPx()
                val marginFactor = 1.3f  // allow 30% margin beyond page
                val minByW = width.toFloat() / (pageW * marginFactor)
                val minByH = height.toFloat() / (pageH * marginFactor)
                minOf(minByW, minByH).coerceAtLeast(0.05f)
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

            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (currentTool == Tool.TEXT) {
                onTextEditRequest?.invoke(null, e.x, e.y, screenToWorldX(e.x), screenToWorldY(e.y))
            } else if (currentTool == Tool.FILL) {
                val worldX = screenToWorldX(e.x)
                val worldY = screenToWorldY(e.y)
                if (removeLeakMarkerAt(worldX, worldY)) {
                    // dismissed leak marker
                } else {
                    val item = findItemAt(worldX, worldY)
                    if (item is StrokeItem && CLOSED_SHAPES.contains(item.data.type)) {
                        item.data.fill = !item.data.fill
                        item.data.color = fillColor
                        item.paint = item.data.toPaint()
                        invalidate()
                    } else {
                        performFill(e.x, e.y)
                    }
                }
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentTool == Tool.TEXT) {
                val worldX = screenToWorldX(e.x)
                val worldY = screenToWorldY(e.y)
                val hit = findTextItemAt(worldX, worldY)
                if (hit != null) {
                    hit.isEditing = true
                    invalidate()
                    onTextEditRequest?.invoke(hit, e.x, e.y, worldX, worldY)
                } else {
                    onTextEditRequest?.invoke(null, e.x, e.y, worldX, worldY)
                }
            }
            return true
        }
    })

    private fun drawActionItem(canvas: Canvas, action: Any, includeFills: Boolean) {
        when (action) {
            is FillItem -> {
                if (!includeFills) return
                if (action.bitmap == null) {
                    try { action.bitmap = android.graphics.BitmapFactory.decodeFile(action.path) } catch (e: Exception) {}
                }
                action.bitmap?.let { bmp ->
                    canvas.drawBitmap(bmp, null, RectF(action.x, action.y, action.x + action.w, action.y + action.h), null)
                }
            }
            is StrokeItem -> {
                if (action.data.rotation != 0f) {
                    val b = getBounds(action)
                    if (b != null) {
                        val cx = (b[0] + b[2]) / 2f
                        val cy = (b[1] + b[3]) / 2f
                        canvas.save()
                        canvas.rotate(action.data.rotation, cx, cy)
                        canvas.drawPath(action.path, action.paint)
                        canvas.restore()
                    } else {
                        canvas.drawPath(action.path, action.paint)
                    }
                } else {
                    canvas.drawPath(action.path, action.paint)
                }
            }
            is TextItem -> {
                if (!action.isEditing) drawTextItem(canvas, action)
            }
            is ImageItem -> {
                if (action.bitmap == null) {
                    try { action.bitmap = android.graphics.BitmapFactory.decodeFile(action.path) } catch (e: Exception) {}
                }
                action.bitmap?.let { bmp ->
                    canvas.save()
                    canvas.translate(action.x, action.y)
                    canvas.rotate(action.rotation)
                    canvas.drawBitmap(bmp, null, RectF(0f, 0f, action.w, action.h), null)
                    canvas.restore()
                }
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
        drawLeakMarkers(canvas)
        drawAutoSelectOverlay(canvas)

        canvas.restore()

        drawCursor(canvas)
    }

    private fun drawArcHandles(canvas: Canvas) {
        if (currentTool != Tool.ARC) return
        val arc = activeArcItem ?: return
        val p = Paint(); p.color = Color.parseColor("#2196F3"); p.style = Paint.Style.FILL
        val r = 12f / scaleFactor
        var i = 0
        while (i + 1 < arc.data.points.size) {
            canvas.drawCircle(arc.data.points[i], arc.data.points[i + 1], r, p)
            i += 2
        }
    }

    private fun drawLeakMarkers(canvas: Canvas) {
        if (leakMarkers.isEmpty()) return
        val p = Paint(); p.color = Color.RED; p.style = Paint.Style.STROKE; p.strokeWidth = 4f / scaleFactor
        val r = 25f / scaleFactor
        for (m in leakMarkers) canvas.drawCircle(m.x, m.y, r, p)
    }

    private fun drawTextItem(canvas: Canvas, item: TextItem) {
        val tp = TextPaint()
        tp.color = item.color
        tp.textSize = item.size
        tp.isAntiAlias = true

        val spannable = SpannableString(item.text)
        for (sp in item.spans) {
            val s = sp.start.coerceIn(0, item.text.length)
            val e = sp.end.coerceIn(s, item.text.length)
            if (s < e) {
                when (sp.type) {
                    'S' -> spannable.setSpan(StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'C' -> spannable.setSpan(ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'U' -> spannable.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'H' -> spannable.setSpan(BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        val width = (tp.measureText(item.text).toInt() + item.size.toInt() + 10).coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(spannable, 0, spannable.length, tp, width)
            .setIncludePad(true)
            .build()

        canvas.save()
        canvas.translate(item.x, item.y - layout.height)
        canvas.rotate(item.rotation, 0f, layout.height.toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    private fun bboxHandlePositions(bounds: FloatArray): List<Pair<HandleType, Pair<Float, Float>>> {
        val cx = (bounds[0] + bounds[2]) / 2f
        val cy = (bounds[1] + bounds[3]) / 2f
        return listOf(
            HandleType.TL to Pair(bounds[0], bounds[1]),
            HandleType.TM to Pair(cx, bounds[1]),
            HandleType.TR to Pair(bounds[2], bounds[1]),
            HandleType.ML to Pair(bounds[0], cy),
            HandleType.MR to Pair(bounds[2], cy),
            HandleType.BL to Pair(bounds[0], bounds[3]),
            HandleType.BM to Pair(cx, bounds[3]),
            HandleType.BR to Pair(bounds[2], bounds[3])
        )
    }

    private fun drawSelection(canvas: Canvas) {
        val item = selectedItem ?: return
        val bounds = getBounds(item) ?: return
        val rotation = getRotation(item)
        val (pivotX, pivotY) = getPivot(item, bounds)

        canvas.save()
        canvas.rotate(rotation, pivotX, pivotY)

        val selPaint = Paint()
        selPaint.color = Color.parseColor("#2196F3")
        selPaint.style = Paint.Style.STROKE
        selPaint.strokeWidth = 2f / scaleFactor
        canvas.drawRect(bounds[0], bounds[1], bounds[2], bounds[3], selPaint)

        val handleRadius = 14f / scaleFactor
        val handleFill = Paint(); handleFill.style = Paint.Style.FILL
        val handleStroke = Paint(); handleStroke.style = Paint.Style.STROKE
        handleStroke.color = Color.parseColor("#2196F3")
        handleStroke.strokeWidth = 2f / scaleFactor

        val isPen = item is StrokeItem && item.data.type == Tool.PEN
        val isBbox = item is ImageItem || item is TextItem || (item is StrokeItem && BBOX_RESIZE_SHAPES.contains(item.data.type))
        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)

        if (isBbox) {
            for ((_, pos) in bboxHandlePositions(bounds)) {
                handleFill.color = Color.WHITE
                canvas.drawCircle(pos.first, pos.second, handleRadius, handleFill)
                canvas.drawCircle(pos.first, pos.second, handleRadius, handleStroke)
            }
        } else if (isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
            handleFill.color = Color.WHITE
            canvas.drawCircle(item.data.points[0], item.data.points[1], handleRadius, handleFill)
            canvas.drawCircle(item.data.points[0], item.data.points[1], handleRadius, handleStroke)
            canvas.drawCircle(item.data.points[2], item.data.points[3], handleRadius, handleFill)
            canvas.drawCircle(item.data.points[2], item.data.points[3], handleRadius, handleStroke)
        }

        val canRotate = item is ImageItem || item is TextItem || (item is StrokeItem && item.data.type != Tool.PEN && item.data.type != Tool.ARC)
        if (canRotate) {
            val cx = (bounds[0] + bounds[2]) / 2f
            val rotY = bounds[1] - 50f / scaleFactor
            canvas.drawLine(cx, bounds[1], cx, rotY, handleStroke)
            handleFill.color = Color.WHITE
            canvas.drawCircle(cx, rotY, handleRadius, handleFill)
            canvas.drawCircle(cx, rotY, handleRadius, handleStroke)
        }

        handleFill.color = Color.parseColor("#F44336")
        canvas.drawCircle(bounds[2] + handleRadius * 2.5f, bounds[1] - handleRadius * 2.5f, handleRadius, handleFill)

        canvas.restore()
    }

    private fun getBounds(item: Any): FloatArray? {
        return when (item) {
            is ImageItem -> floatArrayOf(item.x, item.y, item.x + item.w, item.y + item.h)
            is FillItem -> floatArrayOf(item.x, item.y, item.x + item.w, item.y + item.h)
            is TextItem -> {
                val tp = TextPaint(); tp.textSize = item.size
                val w = tp.measureText(item.text).coerceAtLeast(10f)
                val h = item.size * 1.2f
                floatArrayOf(item.x, item.y - h, item.x + w, item.y)
            }
            is StrokeItem -> {
                val pts = item.data.points
                if (pts.size < 2) return null
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
                        minY = minOf(minY, pts[i + 1]); maxY = maxOf(maxY, pts[i + 1])
                        i += 2
                    }
                    floatArrayOf(minX, minY, maxX, maxY)
                }
            }
            else -> null
        }
    }

    private fun getRotation(item: Any): Float = when (item) {
        is ImageItem -> item.rotation
        is TextItem -> item.rotation
        is StrokeItem -> item.data.rotation
        else -> 0f
    }

    private fun setRotation(item: Any, rotation: Float) {
        when (item) {
            is ImageItem -> item.rotation = rotation
            is TextItem -> item.rotation = rotation
            is StrokeItem -> item.data.rotation = rotation
            else -> {}
        }
    }

    private fun getPivot(item: Any, bounds: FloatArray): Pair<Float, Float> {
        return when (item) {
            is ImageItem -> Pair(item.x, item.y)
            is TextItem -> Pair(item.x, item.y)
            else -> Pair((bounds[0] + bounds[2]) / 2f, (bounds[1] + bounds[3]) / 2f)
        }
    }

    private fun rotatePoint(x: Float, y: Float, pivotX: Float, pivotY: Float, angleDeg: Float): Pair<Float, Float> {
        val angle = Math.toRadians(angleDeg.toDouble())
        val dx = x - pivotX
        val dy = y - pivotY
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        val rx = (dx * cos - dy * sin).toFloat() + pivotX
        val ry = (dx * sin + dy * cos).toFloat() + pivotY
        return Pair(rx, ry)
    }

    private fun computeAngle(item: Any, worldX: Float, worldY: Float): Float {
        val bounds = getBounds(item) ?: return 0f
        val (pivotX, pivotY) = getPivot(item, bounds)
        return Math.toDegrees(kotlin.math.atan2((worldY - pivotY).toDouble(), (worldX - pivotX).toDouble())).toFloat()
    }

    private fun moveItem(item: Any, dx: Float, dy: Float) {
        when (item) {
            is ImageItem -> { item.x += dx; item.y += dy }
            is FillItem -> { item.x += dx; item.y += dy }
            is TextItem -> { item.x += dx; item.y += dy }
            is StrokeItem -> {
                val pts = item.data.points
                var i = 0
                while (i + 1 < pts.size) { pts[i] += dx; pts[i + 1] += dy; i += 2 }
                item.path = item.data.buildPath()
            }
        }
    }

    private fun resizeItem(item: Any, handle: HandleType, lx: Float, ly: Float) {
        val minSize = 10f
        when (item) {
            is ImageItem -> {
            
                // lx/ly are already in the item's unrotated local frame (rotated back by handleSelect)
                // Opposite corners stay fixed — only the dragged edge(s) move
                var left = item.x; var top = item.y; var right = item.x + item.w; var bottom = item.y + item.h
                when (handle) {
                    HandleType.TL -> { left = lx; top = ly }
                    HandleType.TM -> top = ly
                    HandleType.TR -> { right = lx; top = ly }
                    HandleType.ML -> left = lx
                    HandleType.MR -> right = lx
                    HandleType.BL -> { left = lx; bottom = ly }
                    HandleType.BM -> bottom = ly
                    HandleType.BR -> { right = lx; bottom = ly }
                    else -> {}
                }
                if (right - left < minSize) {
                    if (handle == HandleType.TL || handle == HandleType.ML || handle == HandleType.BL) left = right - minSize else right = left + minSize
                }
                if (bottom - top < minSize) {
                    if (handle == HandleType.TL || handle == HandleType.TM || handle == HandleType.TR) top = bottom - minSize else bottom = top + minSize
                }
                // Re-anchor: pivot for ImageItem rotation is top-left (item.x, item.y).
                // When we move the top-left corner (TL/TM/ML), the pivot itself moves,
                // so we must offset the rotation anchor to keep opposite corner fixed.
                val oldPivotX = dragStartPivotX; val oldPivotY = dragStartPivotY
                val newPivotX = left; val newPivotY = top
                if ((handle == HandleType.TL || handle == HandleType.TM || handle == HandleType.ML ||
                     handle == HandleType.TR || handle == HandleType.BL) && item.rotation != 0f) {
                    // The visual position of the old pivot in world space must stay fixed.
                    // Rotate old pivot into world, then adjust translate so new pivot lands there.
                    val rot = Math.toRadians(item.rotation.toDouble())
                    val cos = kotlin.math.cos(rot).toFloat(); val sin = kotlin.math.sin(rot).toFloat()
                    // Old top-left in world = it was the pivot, so it was at dragStartPivotX/Y in world.
                    // New top-left is (left, top) in local. We want the OPPOSITE corner to stay fixed.
                    // Opposite corner in local:
                    val oppLocalX = if (handle == HandleType.TL || handle == HandleType.TM || handle == HandleType.TR) right else left
                    val oppLocalY = if (handle == HandleType.TL || handle == HandleType.ML || handle == HandleType.BL) bottom else top
                    // Opposite corner in world (using old pivot + old rotation):
                    val dox = oppLocalX - oldPivotX; val doy = oppLocalY - oldPivotY
                    val oppWorldX = oldPivotX + dox * cos - doy * sin
                    val oppWorldY = oldPivotY + dox * sin + doy * cos
                    // New pivot in world should place opposite corner at oppWorld:
                    val dnx = oppLocalX - newPivotX; val dny = oppLocalY - newPivotY
                    val oppFromNewX = newPivotX + dnx * cos - dny * sin
                    val oppFromNewY = newPivotY + dnx * sin + dny * cos
                    item.x = left + (oppWorldX - oppFromNewX)
                    item.y = top + (oppWorldY - oppFromNewY)
                } else {
                    item.x = left; item.y = top
                }
                item.w = right - left; item.h = bottom - top
            }
            is StrokeItem -> {
                if (BBOX_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    var left = minOf(item.data.points[0], item.data.points[2])
                    var top = minOf(item.data.points[1], item.data.points[3])
                    var right = maxOf(item.data.points[0], item.data.points[2])
                    var bottom = maxOf(item.data.points[1], item.data.points[3])
                    when (handle) {
                        HandleType.TL -> { left = lx; top = ly }
                        HandleType.TM -> top = ly
                        HandleType.TR -> { right = lx; top = ly }
                        HandleType.ML -> left = lx
                        HandleType.MR -> right = lx
                        HandleType.BL -> { left = lx; bottom = ly }
                        HandleType.BM -> bottom = ly
                        HandleType.BR -> { right = lx; bottom = ly }
                        else -> {}
                    }
                    if (right - left < minSize) {
                        if (handle == HandleType.TL || handle == HandleType.ML || handle == HandleType.BL) left = right - minSize else right = left + minSize
                    }
                    if (bottom - top < minSize) {
                        if (handle == HandleType.TL || handle == HandleType.TM || handle == HandleType.TR) top = bottom - minSize else bottom = top + minSize
                    }
                    item.data.points[0] = left; item.data.points[1] = top
                    item.data.points[2] = right; item.data.points[3] = bottom
                    item.path = item.data.buildPath()
                } else if (ENDPOINT_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    when (handle) {
                        HandleType.TL -> { item.data.points[0] = lx; item.data.points[1] = ly }
                        HandleType.BR -> { item.data.points[2] = lx; item.data.points[3] = ly }
                        else -> {}
                    }
                    item.path = item.data.buildPath()
                }
            }
            is TextItem -> {
                val d = distance(item.x, item.y, lx, ly)
                item.size = (d / (item.text.length.coerceAtLeast(1) * 0.4f)).coerceIn(10f, 300f)
            }
        }
    }

    private fun findItemAt(x: Float, y: Float): Any? {
        val pad = 15f / scaleFactor
        for (action in actions.reversed()) {
            if (action is FillItem) continue
            val bounds = getBounds(action) ?: continue
            if (x in (bounds[0] - pad)..(bounds[2] + pad) && y in (bounds[1] - pad)..(bounds[3] + pad)) return action
        }
        return null
    }

    private fun findTextItemAt(x: Float, y: Float): TextItem? {
        for (action in actions.reversed()) {
            if (action is TextItem) {
                val bounds = getBounds(action) ?: continue
                if (x in (bounds[0] - 10f)..(bounds[2] + 10f) && y in (bounds[1] - 10f)..(bounds[3] + 10f)) return action
            }
        }
        return null
    }

    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private fun handleSelect(event: MotionEvent) {
        val worldX = screenToWorldX(event.x)
        val worldY = screenToWorldY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                longPressRunnable = null
                val item = selectedItem
                var handled = false
                if (item != null) {
                    val bounds = getBounds(item)
                    if (bounds != null) {
                        val rotation = getRotation(item)
                        val (pivotX, pivotY) = getPivot(item, bounds)
                        val (lx, ly) = rotatePoint(worldX, worldY, pivotX, pivotY, -rotation)
                        val handleRadius = 14f / scaleFactor
                        val hitRadius = 30f / scaleFactor
                        val isPen = item is StrokeItem && item.data.type == Tool.PEN
                        val isBbox = item is ImageItem || item is TextItem || (item is StrokeItem && BBOX_RESIZE_SHAPES.contains(item.data.type))
                        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)

                        val delX = bounds[2] + handleRadius * 2.5f
                        val delY = bounds[1] - handleRadius * 2.5f
                        if (distance(lx, ly, delX, delY) <= hitRadius) {
                            actions.remove(item)
                            selectedItem = null
                            handled = true
                        }

                        val canRotate = item is ImageItem || item is TextItem || (item is StrokeItem && item.data.type != Tool.PEN && item.data.type != Tool.ARC)
                        if (!handled && canRotate) {
                            val cx = (bounds[0] + bounds[2]) / 2f
                            val rotY = bounds[1] - 50f / scaleFactor
                            if (distance(lx, ly, cx, rotY) <= hitRadius) {
                                activeHandle = HandleType.ROTATE
                                dragStartAngle = computeAngle(item, worldX, worldY)
                                dragStartRotation = rotation
                                handled = true
                            }
                        }

                        if (!handled && isBbox) {
                            for ((type, pos) in bboxHandlePositions(bounds)) {
                                if (distance(lx, ly, pos.first, pos.second) <= hitRadius) {
                                    activeHandle = type
                                    dragStartPivotX = pivotX
                                    dragStartPivotY = pivotY
                                    dragStartRotation = rotation
                                    handled = true
                                    break
                                }
                            }
                        }

                        if (!handled && isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
                            if (distance(lx, ly, item.data.points[0], item.data.points[1]) <= hitRadius) {
                                activeHandle = HandleType.TL
                                dragStartPivotX = pivotX
                                dragStartPivotY = pivotY
                                dragStartRotation = rotation
                                handled = true
                            } else if (distance(lx, ly, item.data.points[2], item.data.points[3]) <= hitRadius) {
                                activeHandle = HandleType.BR
                                dragStartPivotX = pivotX
                                dragStartPivotY = pivotY
                                dragStartRotation = rotation
                                handled = true
                            }
                        }

                        if (!handled) {
                            val pad = hitRadius
                            if (lx >= bounds[0] - pad && lx <= bounds[2] + pad && ly >= bounds[1] - pad && ly <= bounds[3] + pad) {
                                activeHandle = HandleType.MOVE
                                dragStartWorldX = worldX
                                dragStartWorldY = worldY
                                handled = true
                            }
                        }
                    }
                }
                if (!handled) {
                    activeHandle = HandleType.NONE
                    selectedItem = findItemAt(worldX, worldY)
                }

                val sel = selectedItem
                if (sel is TextItem) {
                    val runnable = Runnable {
                        sel.isEditing = true
                        invalidate()
                        onTextEditRequest?.invoke(sel, event.x, event.y, worldX, worldY)
                    }
                    longPressRunnable = runnable
                    longPressHandler.postDelayed(runnable, 450)
                }

                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }
                val item = selectedItem ?: return
                when (activeHandle) {
                    HandleType.MOVE -> {
                        moveItem(item, worldX - dragStartWorldX, worldY - dragStartWorldY)
                        dragStartWorldX = worldX; dragStartWorldY = worldY
                    }
                    HandleType.ROTATE -> {
                        val currentAngle = computeAngle(item, worldX, worldY)
                        setRotation(item, dragStartRotation + (currentAngle - dragStartAngle))
                    }
                    HandleType.NONE -> return
                    else -> {
                        val (lx, ly) = rotatePoint(worldX, worldY, dragStartPivotX, dragStartPivotY, -dragStartRotation)
                        // For single-axis handles, clamp the unused axis to its drag-start value
                        // so rotating an object and dragging TM only changes height, not width.
                        val b = getBounds(item)
                        val constrainedLx: Float
                        val constrainedLy: Float
                        if (b != null) {
                            val cx = (b[0] + b[2]) / 2f; val cy = (b[1] + b[3]) / 2f
                            constrainedLx = when (activeHandle) {
                                HandleType.TM, HandleType.BM -> cx  // lock x to centre
                                else -> lx
                            }
                            constrainedLy = when (activeHandle) {
                                HandleType.ML, HandleType.MR -> cy  // lock y to centre
                                else -> ly
                            }
                        } else {
                            constrainedLx = lx; constrainedLy = ly
                        }
                        resizeItem(item, activeHandle, constrainedLx, constrainedLy)
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
        val worldX = screenToWorldX(event.x)
        val worldY = screenToWorldY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val arc = activeArcItem
                if (arc != null) {
                    val radius = 30f / scaleFactor
                    var found = -1
                    var i = 0
                    while (i + 1 < arc.data.points.size) {
                        if (distance(worldX, worldY, arc.data.points[i], arc.data.points[i + 1]) <= radius) { found = i; break }
                        i += 2
                    }
                    if (found >= 0) {
                        arcDragPointIndex = found
                    } else {
                        activeArcItem = null
                        val data = StrokeData(Tool.ARC, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, false)
                        currentItem = StrokeItem(data, data.buildPath(), data.toPaint())
                    }
                } else {
                    val data = StrokeData(Tool.ARC, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, false)
                    currentItem = StrokeItem(data, data.buildPath(), data.toPaint())
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (arcDragPointIndex >= 0) {
                    val arc = activeArcItem ?: return
                    arc.data.points[arcDragPointIndex] = worldX
                    arc.data.points[arcDragPointIndex + 1] = worldY
                    arc.path = arc.data.buildPath()
                } else {
                    val item = currentItem ?: return
                    item.data.points[2] = worldX
                    item.data.points[3] = worldY
                    item.path = item.data.buildPath()
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (arcDragPointIndex >= 0) {
                    arcDragPointIndex = -1
                } else {
                    val item = currentItem
                    if (item != null) {
                        val p0x = item.data.points[0]; val p0y = item.data.points[1]
                        val p1x = item.data.points[2]; val p1y = item.data.points[3]
                        val n = arcDivisions.coerceIn(1, 20)
                        val newPoints = mutableListOf<Float>()
                        for (i in 0..n) {
                            val t = i.toFloat() / n
                            newPoints.add(p0x + (p1x - p0x) * t)
                            newPoints.add(p0y + (p1y - p0y) * t)
                        }
                        val data = StrokeData(Tool.ARC, newPoints, item.data.color, item.data.strokeWidth, false)
                        val newItem = StrokeItem(data, data.buildPath(), data.toPaint())
                        actions.add(newItem)
                        redoStack.clear()
                        activeArcItem = newItem
                        currentItem = null
                    }
                }
                invalidate()
            }
        }
    }
    private fun scaleItemInGroup(item: Any, originX: Float, originY: Float, scaleX: Float, scaleY: Float) {
        when (item) {
            is StrokeItem -> {
                val pts = item.data.points
                var i = 0
                while (i + 1 < pts.size) {
                    pts[i] = originX + (pts[i] - originX) * scaleX
                    pts[i + 1] = originY + (pts[i + 1] - originY) * scaleY
                    i += 2
                }
                item.path = item.data.buildPath()
            }
            is TextItem -> {
                item.x = originX + (item.x - originX) * scaleX
                item.y = originY + (item.y - originY) * scaleY
                item.size = (item.size * ((scaleX + scaleY) / 2f)).coerceIn(6f, 500f)
            }
            is ImageItem -> {
                item.x = originX + (item.x - originX) * scaleX
                item.y = originY + (item.y - originY) * scaleY
                item.w *= scaleX; item.h *= scaleY
            }
            is FillItem -> {
                item.x = originX + (item.x - originX) * scaleX
                item.y = originY + (item.y - originY) * scaleY
                item.w *= scaleX; item.h *= scaleY
            }
        }
    }

    // ---------- AutoSelect ----------

    
    private fun groupBounds(group: List<Any>): FloatArray? {
        var result: FloatArray? = null
        for (item in group) {
            val b = getBounds(item) ?: continue
            result = if (result == null) b.copyOf() else floatArrayOf(
                minOf(result[0], b[0]), minOf(result[1], b[1]),
                maxOf(result[2], b[2]), maxOf(result[3], b[3])
            )
        }
        return result
    }

    private fun buildRegion(path: Path): Region {
        val rectF = RectF()
        path.computeBounds(rectF, true)
        val clip = Rect(
            kotlin.math.floor(rectF.left).toInt() - 1,
            kotlin.math.floor(rectF.top).toInt() - 1,
            kotlin.math.ceil(rectF.right).toInt() + 1,
            kotlin.math.ceil(rectF.bottom).toInt() + 1
        )
        val region = Region()
        region.setPath(path, Region(clip))
        return region
    }

    private fun splitStrokeByRegion(data: StrokeData, region: Region): Pair<List<StrokeItem>, List<StrokeItem>> {
        val pts = data.points
        if (pts.size < 4) return Pair(emptyList(), listOf(StrokeItem(data, data.buildPath(), data.toPaint())))

        val insideSegs = mutableListOf<MutableList<Float>>()
        val outsideSegs = mutableListOf<MutableList<Float>>()
        var curInside = mutableListOf<Float>()
        var curOutside = mutableListOf<Float>()
        var lastState: Boolean? = null
        var i = 0
        while (i + 1 < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            val isIn = region.contains(x.toInt(), y.toInt())
            if (lastState != null && lastState != isIn) {
                if (lastState) { if (curInside.size >= 4) insideSegs.add(curInside); curInside = mutableListOf() }
                else { if (curOutside.size >= 4) outsideSegs.add(curOutside); curOutside = mutableListOf() }
            }
            if (isIn) { curInside.add(x); curInside.add(y) } else { curOutside.add(x); curOutside.add(y) }
            lastState = isIn
            i += 2
        }
        if (curInside.size >= 4) insideSegs.add(curInside)
        if (curOutside.size >= 4) outsideSegs.add(curOutside)

        val inside = insideSegs.map {
            val d = StrokeData(data.type, it, data.color, data.strokeWidth, data.fill)
            StrokeItem(d, d.buildPath(), d.toPaint())
        }
        val outside = outsideSegs.map {
            val d = StrokeData(data.type, it, data.color, data.strokeWidth, data.fill)
            StrokeItem(d, d.buildPath(), d.toPaint())
        }
        return Pair(inside, outside)
    }

    private fun selectItemsInRegion(region: Region) {
        val group = mutableListOf<Any>()
        val newActions = mutableListOf<Any>()

        for (action in actions) {
            if (action is FillItem) { newActions.add(action); continue }

            if (action is StrokeItem && (action.data.type == Tool.PEN || action.data.type == Tool.ERASER || action.data.type == Tool.ARC) && autoSelectDivide == AutoSelectDivide.DIVIDED) {
                val (inside, outside) = splitStrokeByRegion(action.data, region)
                newActions.addAll(outside)
                group.addAll(inside)
                continue
            }

            val b = getBounds(action)
            if (b == null) { newActions.add(action); continue }
            val cx = (b[0] + b[2]) / 2f
            val cy = (b[1] + b[3]) / 2f
            if (region.contains(cx.toInt(), cy.toInt())) group.add(action) else newActions.add(action)
        }

        newActions.addAll(group)
        actions.clear()
        actions.addAll(newActions)
        redoStack.clear()
        selectedGroup = if (group.isNotEmpty()) group else null
    }

    private fun handleAutoSelect(event: MotionEvent) {
        val worldX = screenToWorldX(event.x)
        val worldY = screenToWorldY(event.y)
        val handleRadius = 14f / scaleFactor
        val hitRadius = 30f / scaleFactor

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    val gb = groupBounds(group)
                    if (gb != null) {
                        val delX = gb[2] + handleRadius * 2.5f
                        val delY = gb[1] - handleRadius * 2.5f
                        if (distance(worldX, worldY, delX, delY) <= hitRadius) {
                            for (it in group) actions.remove(it)
                            selectedGroup = null
                            invalidate()
                            return
                        }
                        val gcx = (gb[0] + gb[2]) / 2f; val gcy = (gb[1] + gb[3]) / 2f
                        val groupHandlePositions = listOf(
                            gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1],
                            gb[0] to gcy,                 gb[2] to gcy,
                            gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3]
                        )
                        var foundHandle = -1
                        for ((hi, hpos) in groupHandlePositions.withIndex()) {
                            if (distance(worldX, worldY, hpos.first, hpos.second) <= hitRadius) {
                                foundHandle = hi; break
                            }
                        }
                        if (foundHandle >= 0) {
                            groupResizeHandle = foundHandle
                            groupResizeOrigBounds = gb.copyOf()
                            // snapshot each item's bounds for proportional scaling
                            groupResizeItemSnapshots = group.map { getBounds(it)?.copyOf() }
                            invalidate()
                            return
                        }
                        if (worldX in gb[0]..gb[2] && worldY in gb[1]..gb[3]) {
                            groupMoveStartX = worldX
                            groupMoveStartY = worldY
                            groupResizeHandle = -1
                            invalidate()
                            return
                        }
                    }
                    selectedGroup = null
                }
                regionStart = Pair(worldX, worldY)
                regionPath = Path().apply { moveTo(worldX, worldY) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    if (groupResizeHandle >= 0) {
                        val gb = groupBounds(group)
                        if (gb != null) {
                            val origW = (groupResizeOrigBounds[2] - groupResizeOrigBounds[0]).coerceAtLeast(1f)
                            val origH = (groupResizeOrigBounds[3] - groupResizeOrigBounds[1]).coerceAtLeast(1f)
                            var newL = groupResizeOrigBounds[0]; var newT = groupResizeOrigBounds[1]
                            var newR = groupResizeOrigBounds[2]; var newB = groupResizeOrigBounds[3]
                            when (groupResizeHandle) {
                                0 -> { newL = worldX; newT = worldY }
                                1 -> newT = worldY
                                2 -> { newR = worldX; newT = worldY }
                                3 -> newL = worldX
                                4 -> newR = worldX
                                5 -> { newL = worldX; newB = worldY }
                                6 -> newB = worldY
                                7 -> { newR = worldX; newB = worldY }
                            }
                            val newW = (newR - newL).coerceAtLeast(10f)
                            val newH = (newB - newT).coerceAtLeast(10f)
                            val scaleX = newW / origW; val scaleY = newH / origH
                            for (it in group) scaleItemInGroup(it, groupResizeOrigBounds[0], groupResizeOrigBounds[1], scaleX, scaleY)
                        }
                    } else {
                        val dx = worldX - groupMoveStartX
                        val dy = worldY - groupMoveStartY
                        for (it in group) moveItem(it, dx, dy)
                        groupMoveStartX = worldX
                        groupMoveStartY = worldY
                    }
                    invalidate()
                    return
                }
                if (autoSelectShape == AutoSelectShape.RECTANGLE) {
                    val start = regionStart ?: return
                    regionPath = Path().apply {
                        addRect(minOf(start.first, worldX), minOf(start.second, worldY), maxOf(start.first, worldX), maxOf(start.second, worldY), Path.Direction.CW)
                    }
                } else {
                    regionPath?.lineTo(worldX, worldY)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                groupResizeHandle = -1
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) return

                val rp = regionPath
                if (rp != null) {
                    if (autoSelectShape == AutoSelectShape.FREEFORM) rp.close()
                    selectItemsInRegion(buildRegion(rp))
                }
                regionPath = null
                regionStart = null
                invalidate()
            }
        }
    }

    private fun drawAutoSelectOverlay(canvas: Canvas) {
        regionPath?.let { rp ->
            val fillP = Paint(); fillP.color = Color.parseColor("#332196F3"); fillP.style = Paint.Style.FILL
            val strokeP = Paint(); strokeP.color = Color.parseColor("#2196F3"); strokeP.style = Paint.Style.STROKE
            strokeP.strokeWidth = 2f / scaleFactor
            strokeP.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f / scaleFactor, 6f / scaleFactor), 0f)
            canvas.drawPath(rp, fillP)
            canvas.drawPath(rp, strokeP)
        }

        val group = selectedGroup
        if (group != null && group.isNotEmpty()) {
            val highlightP = Paint(); highlightP.color = Color.parseColor("#332196F3"); highlightP.style = Paint.Style.FILL
            for (item in group) {
                val b = getBounds(item) ?: continue
                canvas.drawRect(b[0], b[1], b[2], b[3], highlightP)
            }
            val gb = groupBounds(group)
            if (gb != null) {
                val borderP = Paint(); borderP.color = Color.parseColor("#2196F3"); borderP.style = Paint.Style.STROKE
                borderP.strokeWidth = 2f / scaleFactor
                canvas.drawRect(gb[0], gb[1], gb[2], gb[3], borderP)

                val r = 14f / scaleFactor
                val cx = (gb[0] + gb[2]) / 2f; val cy = (gb[1] + gb[3]) / 2f
                val handleFill = Paint(); handleFill.style = Paint.Style.FILL; handleFill.color = Color.WHITE
                val handleStroke = Paint(); handleStroke.style = Paint.Style.STROKE
                handleStroke.color = Color.parseColor("#2196F3"); handleStroke.strokeWidth = 2f / scaleFactor

                val handles = listOf(
                    gb[0] to gb[1], cx to gb[1], gb[2] to gb[1],
                    gb[0] to cy,                  gb[2] to cy,
                    gb[0] to gb[3], cx to gb[3], gb[2] to gb[3]
                )
                for ((hx, hy) in handles) {
                    canvas.drawCircle(hx, hy, r, handleFill)
                    canvas.drawCircle(hx, hy, r, handleStroke)
                }

                val delP = Paint(); delP.color = Color.parseColor("#F44336"); delP.style = Paint.Style.FILL
                canvas.drawCircle(gb[2] + r * 2.5f, gb[1] - r * 2.5f, r, delP)
            }
        }
    }

    private fun drawCursor(canvas: Canvas) {
        val hx = hoverX ?: return
        val hy = hoverY ?: return

        val cursorPaint = Paint()
        cursorPaint.isAntiAlias = true

        when (currentTool) {
            Tool.PEN -> {
                cursorPaint.color = currentColor
                cursorPaint.style = Paint.Style.FILL
                val radius = (currentStrokeWidth * scaleFactor / 2f).coerceAtLeast(2f)
                canvas.drawCircle(hx, hy, radius, cursorPaint)
            }
            Tool.ERASER -> {
                cursorPaint.color = if (eraserMode == EraserMode.OBJECT) Color.DKGRAY else Color.RED
                cursorPaint.style = Paint.Style.STROKE
                cursorPaint.strokeWidth = 2f
                val half = (eraserSize * scaleFactor) / 2f
                if (eraserMode == EraserMode.OBJECT) {
                    canvas.drawRect(hx - half, hy - half, hx + half, hy + half, cursorPaint)
                } else {
                    canvas.drawCircle(hx, hy, half, cursorPaint)
                }
            }
            else -> {
                cursorPaint.color = Color.DKGRAY
                cursorPaint.style = Paint.Style.FILL
                canvas.drawCircle(hx, hy, 5f, cursorPaint)
            }
        }
    }

    private fun pageWidthPx(): Float {
        val mmToPx = 3.7795f
        return if (pageOrientation == Orientation.PORTRAIT) paperSize.widthMM * mmToPx else paperSize.heightMM * mmToPx
    }

    private fun pageHeightPx(): Float {
        val mmToPx = 3.7795f
        return if (pageOrientation == Orientation.PORTRAIT) paperSize.heightMM * mmToPx else paperSize.widthMM * mmToPx
    }

    private fun drawBackground(canvas: Canvas) {
        val visLeft = -translateX / scaleFactor
        val visTop = -translateY / scaleFactor
        val visRight = visLeft + width / scaleFactor
        val visBottom = visTop + height / scaleFactor

        when (canvasMode) {
            CanvasMode.INFINITE -> {
                if (paperType == PaperType.BLANK_COLORED) {
                    val p = Paint(); p.color = paperColor
                    canvas.drawRect(visLeft - 2000f, visTop - 2000f, visRight + 2000f, visBottom + 2000f, p)
                } else if (paperType != PaperType.BLANK) {
                    drawPaperPattern(canvas, visLeft - 2000f, visTop - 2000f, visRight + 2000f, visBottom + 2000f)
                }
            }
            CanvasMode.FIXED -> {
                
                val pageW = pageWidthPx(); val pageH = pageHeightPx()
                val grayP = Paint(); grayP.color = Color.parseColor("#D5D5D5")
                canvas.drawRect(visLeft - 2000f, visTop - 2000f, visRight + 2000f, visBottom + 2000f, grayP)
                val pageColor = if (paperType == PaperType.BLANK_COLORED) paperColor else Color.WHITE
                val whiteP = Paint(); whiteP.color = pageColor
                canvas.drawRect(0f, 0f, pageW, pageH, whiteP)
                if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) {
                    canvas.save(); canvas.clipRect(0f, 0f, pageW, pageH)
                    drawPaperPattern(canvas, 0f, 0f, pageW, pageH)
                    canvas.restore()
                }
                val borderP = Paint(); borderP.color = Color.parseColor("#909090"); borderP.style = Paint.Style.STROKE
                borderP.strokeWidth = 2f / scaleFactor
                canvas.drawRect(0f, 0f, pageW, pageH, borderP)
            }
            CanvasMode.PAGINATED -> {
                val pageW = pageWidthPx(); val pageH = pageHeightPx()
                val gap = 40f
                val grayP = Paint(); grayP.color = Color.parseColor("#D5D5D5")
                canvas.drawRect(visLeft - 2000f, visTop - 2000f, visRight + 2000f, visBottom + 2000f, grayP)
                val pageColor = if (paperType == PaperType.BLANK_COLORED) paperColor else Color.WHITE
                val whiteP = Paint(); whiteP.color = pageColor
                val borderP = Paint(); borderP.color = Color.parseColor("#909090"); borderP.style = Paint.Style.STROKE
                borderP.strokeWidth = 2f / scaleFactor
                val period = pageH + gap
                val startIdx = (kotlin.math.floor(visTop / period).toInt() - 1).coerceAtLeast(0)
                val endIdx = kotlin.math.ceil(visBottom / period).toInt() + 1
                for (i in startIdx..endIdx) {
                    val top = i * period
                    canvas.drawRect(0f, top, pageW, top + pageH, whiteP)
                    if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) {
                        canvas.save(); canvas.clipRect(0f, top, pageW, top + pageH)
                        drawPaperPattern(canvas, 0f, top, pageW, top + pageH)
                        canvas.restore()
                    }
                    canvas.drawRect(0f, top, pageW, top + pageH, borderP)
                }
            }
        }
    }

    private fun drawPaperPattern(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        when (paperType) {
            PaperType.LINED -> {
                val p = Paint(); p.color = Color.parseColor("#C8D6F0"); p.strokeWidth = 1f
                val spacing = 60f
                var y = (top / spacing).toInt() * spacing
                while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += spacing }
            }
            PaperType.GRID -> {
                val p = Paint(); p.color = Color.parseColor("#D0D0D0"); p.strokeWidth = 1f
                val spacing = 50f
                var x = (left / spacing).toInt() * spacing
                while (x < right) { canvas.drawLine(x, top, x, bottom, p); x += spacing }
                var y = (top / spacing).toInt() * spacing
                while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += spacing }
            }
            PaperType.DOTS -> {
                val p = Paint(); p.color = Color.parseColor("#B0B0B0"); p.style = Paint.Style.FILL
                val spacing = 50f
                var x = (left / spacing).toInt() * spacing
                while (x < right) {
                    var y = (top / spacing).toInt() * spacing
                    while (y < bottom) { canvas.drawCircle(x, y, 2f, p); y += spacing }
                    x += spacing
                }
            }
            PaperType.ENGINEERING -> {
                val minorPaint = Paint(); minorPaint.color = Color.parseColor("#E0E8F5"); minorPaint.strokeWidth = 1f
                val majorPaint = Paint(); majorPaint.color = Color.parseColor("#A8C0E8"); majorPaint.strokeWidth = 1.5f
                val minorSpacing = 20f
                val majorEvery = 5

                var i = (left / minorSpacing).toInt()
                var x = i * minorSpacing
                while (x < right) {
                    val paint = if (i % majorEvery == 0) majorPaint else minorPaint
                    canvas.drawLine(x, top, x, bottom, paint)
                    i++; x = i * minorSpacing
                }
                var j = (top / minorSpacing).toInt()
                var y = j * minorSpacing
                while (y < bottom) {
                    val paint = if (j % majorEvery == 0) majorPaint else minorPaint
                    canvas.drawLine(left, y, right, y, paint)
                    j++; y = j * minorSpacing
                }
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
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverX = event.x; hoverY = event.y; invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverX = null; hoverY = null; invalidate()
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            scaleDetector.onTouchEvent(event)
            return true
        }

        if (currentTool == Tool.TEXT || currentTool == Tool.FILL) {
            gestureDetector.onTouchEvent(event)
            return true
        }

        if (currentTool == Tool.SELECT) {
            handleSelect(event)
            return true
        }

        if (currentTool == Tool.ARC) {
            handleArc(event)
            return true
        }

        if (currentTool == Tool.AUTOSELECT) {
            handleAutoSelect(event)
            return true
        }

        handleDrawing(event)

        return true
    }

    private fun handleDrawing(event: MotionEvent) {
        hoverX = event.x; hoverY = event.y

        val worldX = screenToWorldX(event.x)
        val worldY = screenToWorldY(event.y)
        val pressure = event.pressure.coerceIn(0.3f, 1.5f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (currentTool == Tool.ERASER) {
                    eraseAt(worldX, worldY)
                    invalidate()
                    return
                }
                val data: StrokeData = when {
                    currentTool == Tool.PEN -> StrokeData(Tool.PEN, mutableListOf(worldX, worldY), currentColor, currentStrokeWidth * pressure, false)
                    SHAPE_TOOLS.contains(currentTool) -> StrokeData(currentTool, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, fillShapes)
                    else -> StrokeData(Tool.PEN, mutableListOf(worldX, worldY), currentColor, currentStrokeWidth * pressure, false)
                }
                currentItem = StrokeItem(data, data.buildPath(), data.toPaint())
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == Tool.ERASER) {
                    eraseAt(worldX, worldY)
                    invalidate()
                    return
                }
                val item = currentItem ?: return
                if (currentTool == Tool.PEN) {
                    item.data.points.add(worldX)
                    item.data.points.add(worldY)
                } else if (SHAPE_TOOLS.contains(currentTool)) {
                    item.data.points[2] = worldX
                    item.data.points[3] = worldY
                }
                item.path = item.data.buildPath()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentItem?.let {
                    actions.add(it)
                    redoStack.clear()
                }
                currentItem = null
                invalidate()
            }
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val radius = eraserSize / 2f

        if (eraserMode == EraserMode.OBJECT) {
            val iterator = actions.iterator()
            while (iterator.hasNext()) {
                val action = iterator.next()
                val hit = when (action) {
                    is StrokeItem -> strokeHitTest(action.data, x, y, radius)
                    is TextItem -> distance(x, y, action.x, action.y) <= radius + action.size
                    is ImageItem -> distance(x, y, action.x + action.w / 2f, action.y + action.h / 2f) <= radius + maxOf(action.w, action.h) / 2f
                    is FillItem -> distance(x, y, action.x + action.w / 2f, action.y + action.h / 2f) <= radius + maxOf(action.w, action.h) / 2f
                    else -> false
                }
                if (hit) iterator.remove()
            }
        } else {
            val newActions = mutableListOf<Any>()
            for (action in actions) {
                when (action) {
                    is StrokeItem -> {
                        if (action.data.type == Tool.PEN || action.data.type == Tool.ERASER) {
                            newActions.addAll(splitStrokeAroundEraser(action.data, x, y, radius))
                        } else {
                            if (!strokeHitTest(action.data, x, y, radius)) newActions.add(action)
                        }
                    }
                    is TextItem -> {
                        if (distance(x, y, action.x, action.y) > radius + action.size) newActions.add(action)
                    }
                    is ImageItem -> {
                        if (distance(x, y, action.x + action.w / 2f, action.y + action.h / 2f) > radius + maxOf(action.w, action.h) / 2f) newActions.add(action)
                    }
                    is FillItem -> {
                        if (distance(x, y, action.x + action.w / 2f, action.y + action.h / 2f) > radius + maxOf(action.w, action.h) / 2f) newActions.add(action)
                    }
                    else -> newActions.add(action)
                }
            }
            actions.clear()
            actions.addAll(newActions)
        }
    }

    private fun splitStrokeAroundEraser(data: StrokeData, ex: Float, ey: Float, radius: Float): List<StrokeItem> {
        val pts = data.points
        if (pts.size < 4) {
            if (pts.size >= 2 && distance(ex, ey, pts[0], pts[1]) <= radius) return emptyList()
            return listOf(StrokeItem(data, data.buildPath(), data.toPaint()))
        }

        val segments = mutableListOf<MutableList<Float>>()
        var current = mutableListOf<Float>()
        var i = 0
        while (i + 1 < pts.size) {
            val px = pts[i]; val py = pts[i + 1]
            val erased = distance(ex, ey, px, py) <= radius
            if (erased) {
                if (current.size >= 4) segments.add(current)
                current = mutableListOf()
            } else {
                current.add(px); current.add(py)
            }
            i += 2
        }
        if (current.size >= 4) segments.add(current)

        return segments.map { segPts ->
            val newData = StrokeData(data.type, segPts, data.color, data.strokeWidth, data.fill)
            StrokeItem(newData, newData.buildPath(), newData.toPaint())
        }
    }

    private fun strokeHitTest(data: StrokeData, x: Float, y: Float, radius: Float): Boolean {
        if (data.type == Tool.PEN || data.type == Tool.ERASER || data.type == Tool.ARC) {
            if (data.points.size == 2) return distance(x, y, data.points[0], data.points[1]) <= radius
            var i = 0
            while (i + 3 < data.points.size) {
                if (distanceToSegment(x, y, data.points[i], data.points[i + 1], data.points[i + 2], data.points[i + 3]) <= radius) return true
                i += 2
            }
            return false
        } else {
            if (data.points.size >= 4) {
                val left = minOf(data.points[0], data.points[2]) - radius
                val right = maxOf(data.points[0], data.points[2]) + radius
                val top = minOf(data.points[1], data.points[3]) - radius
                val bottom = maxOf(data.points[1], data.points[3]) + radius
                return x in left..right && y in top..bottom
            }
            return false
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
    }

    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0f && dy == 0f) return distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return distance(px, py, x1 + t * dx, y1 + t * dy)
    }

    fun addText(text: String, x: Float, y: Float, size: Float, rotation: Float, color: Int, spans: MutableList<TextSpanData> = mutableListOf()) {
        if (text.isBlank()) return
        val item = TextItem(text, x, y, color, size, rotation)
        item.spans = spans
        actions.add(item)
        redoStack.clear()
        invalidate()
    }

    fun removeTextItem(item: TextItem) {
        actions.remove(item)
        invalidate()
    }

    fun addImage(path: String, worldX: Float, worldY: Float, w: Float, h: Float) {
        actions.add(ImageItem(path, worldX - w / 2f, worldY - h / 2f, w, h, 0f))
        redoStack.clear()
        invalidate()
    }

    fun undo() {
        if (actions.isNotEmpty()) {
            redoStack.add(actions.removeAt(actions.size - 1))
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            actions.add(redoStack.removeAt(redoStack.size - 1))
            invalidate()
        }
    }

    fun clearAll() {
        actions.clear()
        redoStack.clear()
        selectedItem = null
        leakMarkers.clear()
        invalidate()
    }

    fun hasContent(): Boolean = actions.isNotEmpty()

    fun exportBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    // ---------- Fill (MS Paint style) ----------

    fun renderStrokesOnly(scale: Float): Bitmap {
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        for (action in actions) drawActionItem(canvas, action, false)
        canvas.restore()
        return bitmap
    }

    fun removeLeakMarkerAt(worldX: Float, worldY: Float): Boolean {
        val r = 30f / scaleFactor
        val it = leakMarkers.iterator()
        while (it.hasNext()) {
            val m = it.next()
            if (distance(worldX, worldY, m.x, m.y) <= r) {
                it.remove()
                invalidate()
                return true
            }
        }
        return false
    }

    fun zoomTo(worldX: Float, worldY: Float, scale: Float) {
        scaleFactor = scale.coerceIn(0.2f, 6f)
        translateX = width / 2f - worldX * scaleFactor
        translateY = height / 2f - worldY * scaleFactor
        invalidate()
    }

    private fun clusterEdgePoints(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val clusters = mutableListOf<MutableList<Pair<Int, Int>>>()
        for (p in points) {
            var added = false
            for (cluster in clusters) {
                val c0 = cluster[0]
                if (kotlin.math.abs(c0.first - p.first) < 30 && kotlin.math.abs(c0.second - p.second) < 30) {
                    cluster.add(p); added = true; break
                }
            }
            if (!added) clusters.add(mutableListOf(p))
        }
        return clusters.map { cl ->
            Pair(cl.map { it.first }.average().toInt(), cl.map { it.second }.average().toInt())
        }
    }

    fun performFill(screenX: Float, screenY: Float) {
        leakMarkers.clear()
        val scale = 0.4f
        val bitmap = renderStrokesOnly(scale)
        val w = bitmap.width; val h = bitmap.height
        val px = (screenX * scale).toInt().coerceIn(0, w - 1)
        val py = (screenY * scale).toInt().coerceIn(0, h - 1)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        fun isEmpty(x: Int, y: Int): Boolean = ((pixels[y * w + x] ushr 24) and 0xFF) < 10

        if (!isEmpty(px, py)) {
            invalidate()
            return
        }

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        val start = py * w + px
        queue.add(start)
        visited[start] = true
        var filledCount = 0
        val edgeHits = mutableListOf<Pair<Int, Int>>()
        val maxFill = (w * h * 0.7f).toInt()
        var leaked = false

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val x = idx % w; val y = idx / w
            filledCount++
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) edgeHits.add(Pair(x, y))
            if (filledCount > maxFill) { leaked = true; break }
            if (x > 0) { val n = idx - 1; if (!visited[n] && isEmpty(x - 1, y)) { visited[n] = true; queue.add(n) } }
            if (x < w - 1) { val n = idx + 1; if (!visited[n] && isEmpty(x + 1, y)) { visited[n] = true; queue.add(n) } }
            if (y > 0) { val n = idx - w; if (!visited[n] && isEmpty(x, y - 1)) { visited[n] = true; queue.add(n) } }
            if (y < h - 1) { val n = idx + w; if (!visited[n] && isEmpty(x, y + 1)) { visited[n] = true; queue.add(n) } }
        }

        if (leaked) {
            val clusters = clusterEdgePoints(edgeHits)
            for ((cx, cy) in clusters) {
                leakMarkers.add(LeakMarker(screenToWorldX(cx / scale), screenToWorldY(cy / scale)))
            }
            if (clusters.isNotEmpty()) {
                val (cx, cy) = clusters[0]
                zoomTo(screenToWorldX(cx / scale), screenToWorldY(cy / scale), (scaleFactor * 2.5f).coerceAtMost(6f))
            }
            invalidate()
        } else {
            val fillPixels = IntArray(w * h)
            for (i in 0 until w * h) fillPixels[i] = if (visited[i]) fillColor else Color.TRANSPARENT
            val fillBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            fillBitmap.setPixels(fillPixels, 0, w, 0, 0, w, h)

            val imagesFolder = File(ctx.filesDir, "images")
            if (!imagesFolder.exists()) imagesFolder.mkdirs()
            val outFile = File(imagesFolder, "fill_" + System.currentTimeMillis() + ".png")
            try {
                val out = FileOutputStream(outFile)
                fillBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.close()
            } catch (e: Exception) {
                invalidate()
                return
            }

            val wx0 = screenToWorldX(0f); val wy0 = screenToWorldY(0f)
            val wx1 = screenToWorldX(width.toFloat()); val wy1 = screenToWorldY(height.toFloat())
            actions.add(0, FillItem(outFile.absolutePath, wx0, wy0, wx1 - wx0, wy1 - wy0))
            redoStack.clear()
            invalidate()
        }
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("META\u0001").append(paperType.name).append("\u0001").append(canvasMode.name).append("\u0001").append(paperSize.name).append("\u0001").append(pageOrientation.name).append("\u0001").append(paperColor).append("\n")
        for (action in actions) {
            when (action) {
                is StrokeItem -> {
                    val d = action.data
                    sb.append(d.type.name).append("|")
                    sb.append(d.color).append("|")
                    sb.append(d.strokeWidth).append("|")
                    sb.append(d.fill).append("|")
                    sb.append(d.rotation).append("|")
                    sb.append(d.points.joinToString(","))
                    sb.append("\n")
                }
                is TextItem -> {
                    val spansEncoded = action.spans.joinToString(";") { "${it.start},${it.end},${it.type},${it.value}" }
                    sb.append("TEXT\u0001")
                    sb.append(action.x).append("\u0001")
                    sb.append(action.y).append("\u0001")
                    sb.append(action.color).append("\u0001")
                    sb.append(action.size).append("\u0001")
                    sb.append(action.rotation).append("\u0001")
                    sb.append(spansEncoded).append("\u0001")
                    sb.append(action.text.replace("\n", "\u0002"))
                    sb.append("\n")
                }
                is ImageItem -> {
                    sb.append("IMAGE\u0001")
                    sb.append(action.path).append("\u0001")
                    sb.append(action.x).append("\u0001")
                    sb.append(action.y).append("\u0001")
                    sb.append(action.w).append("\u0001")
                    sb.append(action.h).append("\u0001")
                    sb.append(action.rotation)
                    sb.append("\n")
                }
                is FillItem -> {
                    sb.append("FILL\u0001")
                    sb.append(action.path).append("\u0001")
                    sb.append(action.x).append("\u0001")
                    sb.append(action.y).append("\u0001")
                    sb.append(action.w).append("\u0001")
                    sb.append(action.h)
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        actions.clear()
        redoStack.clear()
        selectedItem = null
        leakMarkers.clear()
        for (line in content.lines()) {
            if (line.isBlank()) continue
            try {
                if (line.startsWith("META\u0001")) {
                    val parts = line.split("\u0001")
                    try { if (parts.size > 1 && parts[1].isNotBlank()) paperType = PaperType.valueOf(parts[1]) } catch (e: Exception) {}
                    try { if (parts.size > 2 && parts[2].isNotBlank()) canvasMode = CanvasMode.valueOf(parts[2]) } catch (e: Exception) {}
                    try { if (parts.size > 3 && parts[3].isNotBlank()) paperSize = PaperSizeOption.valueOf(parts[3]) } catch (e: Exception) {}
                    try { if (parts.size > 4 && parts[4].isNotBlank()) pageOrientation = Orientation.valueOf(parts[4]) } catch (e: Exception) {}
                    try { if (parts.size > 5 && parts[5].isNotBlank()) paperColor = parts[5].toInt() } catch (e: Exception) {}
                } else if (line.startsWith("TEXT\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size < 7) continue
                    val x = parts[1].toFloat()
                    val y = parts[2].toFloat()
                    val color = parts[3].toInt()
                    val size = parts[4].toFloat()
                    val rotation = parts[5].toFloat()
                    val item = TextItem("", x, y, color, size, rotation)
                    if (parts.size >= 9) {
                        val bold = parts[6].toBoolean()
                        val italic = parts[7].toBoolean()
                        item.text = parts[8].replace("\u0002", "\n")
                        var style = -1
                        if (bold && italic) style = Typeface.BOLD_ITALIC
                        else if (bold) style = Typeface.BOLD
                        else if (italic) style = Typeface.ITALIC
                        if (style >= 0) item.spans.add(TextSpanData(0, item.text.length, 'S', style))
                    } else {
                        val spansStr = parts[6]
                        if (spansStr.isNotBlank()) {
                            for (token in spansStr.split(";")) {
                                val sp = token.split(",")
                                if (sp.size == 4) {
                                    item.spans.add(TextSpanData(sp[0].toInt(), sp[1].toInt(), sp[2][0], sp[3].toInt()))
                                }
                            }
                        }
                        item.text = parts[7].replace("\u0002", "\n")
                    }
                    actions.add(item)
                } else if (line.startsWith("IMAGE\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size < 7) continue
                    actions.add(ImageItem(parts[1], parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat(), parts[5].toFloat(), parts[6].toFloat()))
                } else if (line.startsWith("FILL\u0001")) {
                    val parts = line.split("\u0001")
                    if (parts.size < 6) continue
                    actions.add(FillItem(parts[1], parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat(), parts[5].toFloat()))
                } else {
                    val parts = line.split("|")
                    if (parts.size < 5) continue
                    val type = Tool.valueOf(parts[0])
                    val color = parts[1].toInt()
                    val strokeWidth = parts[2].toFloat()
                    val fill = parts[3].toBoolean()
                    if (parts.size >= 6) {
                        val rotation = parts[4].toFloat()
                        val pts = if (parts[5].isBlank()) mutableListOf() else parts[5].split(",").map { it.toFloat() }.toMutableList()
                        val data = StrokeData(type, pts, color, strokeWidth, fill, rotation)
                        actions.add(StrokeItem(data, data.buildPath(), data.toPaint()))
                    } else {
                        val pts = if (parts[4].isBlank()) mutableListOf() else parts[4].split(",").map { it.toFloat() }.toMutableList()
                        val data = StrokeData(type, pts, color, strokeWidth, fill)
                        actions.add(StrokeItem(data, data.buildPath(), data.toPaint()))
                    }
                }
            } catch (e: Exception) {
            }
        }
        invalidate()
    }
}
