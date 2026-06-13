package com.enginotes.app

import android.content.Context
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

enum class Tool {
    SELECT, FILL, PEN, ERASER, LINE, RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE,
    TRIANGLE, DIAMOND, ARROW, STAR, PENTAGON, HEXAGON, CURVE, CROSS, TEXT
}
enum class PaperType { BLANK, LINED, GRID, DOTS, ENGINEERING }
enum class EraserMode { OBJECT, AREA }

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
    var bitmap: android.graphics.Bitmap? = null
}

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val actions = mutableListOf<Any>()
    private val redoStack = mutableListOf<Any>()
    private var currentItem: StrokeItem? = null

    var currentTool: Tool = Tool.PEN
        set(value) {
            if (field == Tool.SELECT && value != Tool.SELECT) selectedItem = null
            field = value
            invalidate()
        }

    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var eraserSize: Float = 40f
    var eraserMode: EraserMode = EraserMode.OBJECT
    var fillShapes: Boolean = false
    var paperType: PaperType = PaperType.GRID
    var defaultTextSize: Float = 36f

    var selectedItem: Any? = null
    private enum class HandleType { NONE, MOVE, ROTATE, TL, TM, TR, ML, MR, BL, BM, BR }
    private var activeHandle = HandleType.NONE
    private var dragStartWorldX = 0f
    private var dragStartWorldY = 0f
    private var dragStartAngle = 0f
    private var dragStartRotation = 0f
    private var dragStartPivotX = 0f
    private var dragStartPivotY = 0f

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
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(0.2f, 6f)
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
                val item = findItemAt(worldX, worldY)
                if (item is StrokeItem && CLOSED_SHAPES.contains(item.data.type)) {
                    item.data.fill = !item.data.fill
                    item.paint = item.data.toPaint()
                    invalidate()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        drawBackground(canvas)

        for (action in actions) {
            when (action) {
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
                    val bmp = action.bitmap
                    if (bmp != null) {
                        canvas.save()
                        canvas.translate(action.x, action.y)
                        canvas.rotate(action.rotation)
                        canvas.drawBitmap(bmp, null, RectF(0f, 0f, action.w, action.h), null)
                        canvas.restore()
                    }
                }
            }
        }
        currentItem?.let { canvas.drawPath(it.path, it.paint) }

        drawSelection(canvas)

        canvas.restore()

        drawCursor(canvas)
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

        val canRotate = item is ImageItem || item is TextItem || (item is StrokeItem && item.data.type != Tool.PEN)
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
                item.x = left; item.y = top; item.w = right - left; item.h = bottom - top
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

                        val canRotate = item is ImageItem || item is TextItem || (item is StrokeItem && item.data.type != Tool.PEN)
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
                        resizeItem(item, activeHandle, lx, ly)
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

    private fun drawBackground(canvas: Canvas) {
        if (paperType == PaperType.BLANK) return

        val left = (-translateX / scaleFactor) - 2000f
        val top = (-translateY / scaleFactor) - 2000f
        val right = left + (width / scaleFactor) + 4000f
        val bottom = top + (height / scaleFactor) + 4000f

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
        if (data.type == Tool.PEN || data.type == Tool.ERASER) {
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
        invalidate()
    }

    fun exportBitmap(): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    fun serialize(): String {
        val sb = StringBuilder()
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
            }
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        actions.clear()
        redoStack.clear()
        selectedItem = null
        for (line in content.lines()) {
            if (line.isBlank()) continue
            try {
                if (line.startsWith("TEXT\u0001")) {
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
                    val path = parts[1]
                    val x = parts[2].toFloat()
                    val y = parts[3].toFloat()
                    val w = parts[4].toFloat()
                    val h = parts[5].toFloat()
                    val rotation = parts[6].toFloat()
                    actions.add(ImageItem(path, x, y, w, h, rotation))
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
