package com.enginotes.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

enum class Tool { PEN, ERASER, RECTANGLE, CIRCLE, LINE, TEXT }
enum class PaperType { BLANK, LINED, GRID, DOTS, ENGINEERING }

class StrokeData(
    val type: Tool,
    val points: MutableList<Float>,
    var color: Int,
    var strokeWidth: Float,
    var fill: Boolean
) {
    fun buildPath(): Path {
        val path = Path()
        when (type) {
            Tool.PEN, Tool.ERASER -> {
                if (points.size >= 2) {
                    path.moveTo(points[0], points[1])
                    var i = 2
                    while (i + 1 < points.size) {
                        path.lineTo(points[i], points[i + 1])
                        i += 2
                    }
                }
            }
            Tool.LINE -> {
                if (points.size >= 4) {
                    path.moveTo(points[0], points[1])
                    path.lineTo(points[2], points[3])
                }
            }
            Tool.RECTANGLE -> {
                if (points.size >= 4) {
                    path.addRect(
                        RectF(
                            minOf(points[0], points[2]),
                            minOf(points[1], points[3]),
                            maxOf(points[0], points[2]),
                            maxOf(points[1], points[3])
                        ),
                        Path.Direction.CW
                    )
                }
            }
            Tool.CIRCLE -> {
                if (points.size >= 4) {
                    val radius = kotlin.math.hypot((points[2] - points[0]).toDouble(), (points[3] - points[1]).toDouble()).toFloat()
                    path.addCircle(points[0], points[1], radius, Path.Direction.CW)
                }
            }
            Tool.TEXT -> {}
        }
        return path
    }

    fun toPaint(): Paint {
        val p = Paint()
        p.color = color
        val isShape = type == Tool.RECTANGLE || type == Tool.CIRCLE
        p.style = if (fill && isShape) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        p.strokeWidth = strokeWidth
        p.isAntiAlias = true
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        return p
    }
}

class StrokeItem(val data: StrokeData, var path: Path, var paint: Paint)
class TextItem(var text: String, var x: Float, var y: Float, var color: Int, var size: Float, var rotation: Float)

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val actions = mutableListOf<Any>()
    private var currentItem: StrokeItem? = null

    var currentTool: Tool = Tool.PEN
    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var eraserSize: Float = 40f
    var fillShapes: Boolean = false
    var paperType: PaperType = PaperType.GRID
    var defaultTextSize: Float = 30f

    var onTextTapListener: ((Float, Float) -> Unit)? = null

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private var shapeStartXWorld = 0f
    private var shapeStartYWorld = 0f

    private var prevFocusX = 0f
    private var prevFocusY = 0f

    private var hoverX: Float? = null
    private var hoverY: Float? = null

    private var textDownX = 0f
    private var textDownY = 0f
    private var textDownTime = 0L

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        drawBackground(canvas)

        for (action in actions) {
            when (action) {
                is StrokeItem -> canvas.drawPath(action.path, action.paint)
                is TextItem -> {
                    val paint = Paint()
                    paint.color = action.color
                    paint.textSize = action.size
                    paint.isAntiAlias = true
                    canvas.save()
                    canvas.translate(action.x, action.y)
                    canvas.rotate(action.rotation)
                    canvas.drawText(action.text, 0f, 0f, paint)
                    canvas.restore()
                }
            }
        }
        currentItem?.let { canvas.drawPath(it.path, it.paint) }

        canvas.restore()

        drawCursor(canvas)
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
                cursorPaint.color = Color.DKGRAY
                cursorPaint.style = Paint.Style.STROKE
                cursorPaint.strokeWidth = 2f
                val half = (eraserSize * scaleFactor) / 2f
                canvas.drawRect(hx - half, hy - half, hx + half, hy + half, cursorPaint)
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
                val p = Paint()
                p.color = Color.parseColor("#C8D6F0")
                p.strokeWidth = 1f
                val spacing = 60f
                var y = (top / spacing).toInt() * spacing
                while (y < bottom) {
                    canvas.drawLine(left, y, right, y, p)
                    y += spacing
                }
            }
            PaperType.GRID -> {
                val p = Paint()
                p.color = Color.parseColor("#D0D0D0")
                p.strokeWidth = 1f
                val spacing = 50f
                var x = (left / spacing).toInt() * spacing
                while (x < right) { canvas.drawLine(x, top, x, bottom, p); x += spacing }
                var y = (top / spacing).toInt() * spacing
                while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += spacing }
            }
            PaperType.DOTS -> {
                val p = Paint()
                p.color = Color.parseColor("#B0B0B0")
                p.style = Paint.Style.FILL
                val spacing = 50f
                var x = (left / spacing).toInt() * spacing
                while (x < right) {
                    var y = (top / spacing).toInt() * spacing
                    while (y < bottom) {
                        canvas.drawCircle(x, y, 2f, p)
                        y += spacing
                    }
                    x += spacing
                }
            }
            PaperType.ENGINEERING -> {
                val minorPaint = Paint()
                minorPaint.color = Color.parseColor("#E0E8F5")
                minorPaint.strokeWidth = 1f
                val majorPaint = Paint()
                majorPaint.color = Color.parseColor("#A8C0E8")
                majorPaint.strokeWidth = 1.5f

                val minorSpacing = 20f
                val majorEvery = 5

                var i = (left / minorSpacing).toInt()
                var x = i * minorSpacing
                while (x < right) {
                    val paint = if (i % majorEvery == 0) majorPaint else minorPaint
                    canvas.drawLine(x, top, x, bottom, paint)
                    i++
                    x = i * minorSpacing
                }
                var j = (top / minorSpacing).toInt()
                var y = j * minorSpacing
                while (y < bottom) {
                    val paint = if (j % majorEvery == 0) majorPaint else minorPaint
                    canvas.drawLine(left, y, right, y, paint)
                    j++
                    y = j * minorSpacing
                }
            }
            else -> {}
        }
    }

    private fun screenToWorldX(x: Float): Float {
        return (x - translateX) / scaleFactor
    }

    private fun screenToWorldY(y: Float): Float {
        return (y - translateY) / scaleFactor
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverX = event.x
                hoverY = event.y
                invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverX = null
                hoverY = null
                invalidate()
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            scaleDetector.onTouchEvent(event)
            return true
        }

        if (currentTool == Tool.TEXT) {
            handleTextTap(event)
            return true
        }

        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        if (isStylus) {
            handleDrawing(event)
        }

        return true
    }

    private fun handleTextTap(event: MotionEvent) {
        hoverX = event.x
        hoverY = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                textDownX = event.x
                textDownY = event.y
                textDownTime = System.currentTimeMillis()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - textDownX
                val dy = event.y - textDownY
                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                val dt = System.currentTimeMillis() - textDownTime
                if (dist < 25 && dt < 600) {
                    val worldX = screenToWorldX(event.x)
                    val worldY = screenToWorldY(event.y)
                    onTextTapListener?.invoke(worldX, worldY)
                }
            }
        }
    }

    private fun handleDrawing(event: MotionEvent) {
        hoverX = event.x
        hoverY = event.y

        val worldX = screenToWorldX(event.x)
        val worldY = screenToWorldY(event.y)
        val pressure = event.pressure.coerceIn(0.3f, 1.5f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                shapeStartXWorld = worldX
                shapeStartYWorld = worldY

                if (currentTool == Tool.ERASER) {
                    eraseAt(worldX, worldY)
                    invalidate()
                    return
                }

                val data: StrokeData = when (currentTool) {
                    Tool.PEN -> StrokeData(Tool.PEN, mutableListOf(worldX, worldY), currentColor, currentStrokeWidth * pressure, false)
                    Tool.LINE -> StrokeData(Tool.LINE, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, false)
                    Tool.RECTANGLE -> StrokeData(Tool.RECTANGLE, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, fillShapes)
                    Tool.CIRCLE -> StrokeData(Tool.CIRCLE, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, fillShapes)
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
                when (currentTool) {
                    Tool.PEN -> {
                        item.data.points.add(worldX)
                        item.data.points.add(worldY)
                    }
                    Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE -> {
                        item.data.points[2] = worldX
                        item.data.points[3] = worldY
                    }
                    else -> {}
                }
                item.path = item.data.buildPath()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentItem?.let { actions.add(it) }
                currentItem = null
                invalidate()
            }
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val radius = eraserSize / 2f
        val iterator = actions.iterator()
        while (iterator.hasNext()) {
            val action = iterator.next()
            val hit = when (action) {
                is StrokeItem -> strokeHitTest(action.data, x, y, radius)
                is TextItem -> distance(x, y, action.x, action.y) <= radius + action.size
                else -> false
            }
            if (hit) iterator.remove()
        }
    }

    private fun strokeHitTest(data: StrokeData, x: Float, y: Float, radius: Float): Boolean {
        if (data.type == Tool.PEN || data.type == Tool.ERASER) {
            if (data.points.size == 2) {
                return distance(x, y, data.points[0], data.points[1]) <= radius
            }
            var i = 0
            while (i + 3 < data.points.size) {
                val x1 = data.points[i]
                val y1 = data.points[i + 1]
                val x2 = data.points[i + 2]
                val y2 = data.points[i + 3]
                if (distanceToSegment(x, y, x1, y1, x2, y2) <= radius) return true
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
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val closestX = x1 + t * dx
        val closestY = y1 + t * dy
        return distance(px, py, closestX, closestY)
    }

    fun addText(text: String, x: Float, y: Float, size: Float, rotation: Float, color: Int) {
        if (text.isBlank()) return
        actions.add(TextItem(text, x, y, color, size, rotation))
        invalidate()
    }

    fun undo() {
        if (actions.isNotEmpty()) {
            actions.removeAt(actions.size - 1)
            invalidate()
        }
    }

    fun clearAll() {
        actions.clear()
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
                    sb.append(d.points.joinToString(","))
                    sb.append("\n")
                }
                is TextItem -> {
                    sb.append("TEXT\u0001")
                    sb.append(action.x).append("\u0001")
                    sb.append(action.y).append("\u0001")
                    sb.append(action.color).append("\u0001")
                    sb.append(action.size).append("\u0001")
                    sb.append(action.rotation).append("\u0001")
                    sb.append(action.text.replace("\n", "\u0002"))
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        actions.clear()
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
                    val text = parts[6].replace("\u0002", "\n")
                    actions.add(TextItem(text, x, y, color, size, rotation))
                } else {
                    val parts = line.split("|")
                    if (parts.size < 5) continue
                    val type = Tool.valueOf(parts[0])
                    val color = parts[1].toInt()
                    val strokeWidth = parts[2].toFloat()
                    val fill = parts[3].toBoolean()
                    val pts = if (parts[4].isBlank()) mutableListOf() else parts[4].split(",").map { it.toFloat() }.toMutableList()
                    val data = StrokeData(type, pts, color, strokeWidth, fill)
                    actions.add(StrokeItem(data, data.buildPath(), data.toPaint()))
                }
            } catch (e: Exception) {
            }
        }
        invalidate()
    }
}
