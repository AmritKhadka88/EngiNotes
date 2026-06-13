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

enum class Tool { PEN, ERASER, RECTANGLE, CIRCLE, LINE }
enum class PaperType { BLANK, LINED, GRID }

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

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokes = mutableListOf<StrokeItem>()
    private var currentItem: StrokeItem? = null

    var currentTool: Tool = Tool.PEN
    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var eraserSize: Float = 40f
    var fillShapes: Boolean = false
    var paperType: PaperType = PaperType.GRID

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private var shapeStartXWorld = 0f
    private var shapeStartYWorld = 0f

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        drawBackground(canvas)

        for (item in strokes) {
            canvas.drawPath(item.path, item.paint)
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
            Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE -> {
                cursorPaint.color = Color.DKGRAY
                cursorPaint.style = Paint.Style.FILL
                canvas.drawCircle(hx, hy, 5f, cursorPaint)
            }
        }
    }

    private fun drawBackground(canvas: Canvas) {
        if (paperType == PaperType.BLANK) return

        val linePaint = Paint()
        linePaint.color = Color.parseColor("#D0D0D0")
        linePaint.strokeWidth = 1f

        val left = (-translateX / scaleFactor) - 2000f
        val top = (-translateY / scaleFactor) - 2000f
        val right = left + (width / scaleFactor) + 4000f
        val bottom = top + (height / scaleFactor) + 4000f

        if (paperType == PaperType.GRID) {
            val spacing = 50f
            var x = (left / spacing).toInt() * spacing
            while (x < right) {
                canvas.drawLine(x, top, x, bottom, linePaint)
                x += spacing
            }
            var y = (top / spacing).toInt() * spacing
            while (y < bottom) {
                canvas.drawLine(left, y, right, y, linePaint)
                y += spacing
            }
        } else if (paperType == PaperType.LINED) {
            val spacing = 60f
            var y = (top / spacing).toInt() * spacing
            while (y < bottom) {
                canvas.drawLine(left, y, right, y, linePaint)
                y += spacing
            }
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
        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

        if (isStylus) {
            handleDrawing(event)
            return true
        }

        if (event.pointerCount >= 2) {
            scaleDetector.onTouchEvent(event)
        }

        return true
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

                val data: StrokeData = when (currentTool) {
                    Tool.PEN -> StrokeData(Tool.PEN, mutableListOf(worldX, worldY), currentColor, currentStrokeWidth * pressure, false)
                    Tool.ERASER -> StrokeData(Tool.ERASER, mutableListOf(worldX, worldY), Color.WHITE, eraserSize, false)
                    Tool.LINE -> StrokeData(Tool.LINE, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, false)
                    Tool.RECTANGLE -> StrokeData(Tool.RECTANGLE, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, fillShapes)
                    Tool.CIRCLE -> StrokeData(Tool.CIRCLE, mutableListOf(worldX, worldY, worldX, worldY), currentColor, currentStrokeWidth, fillShapes)
                }
                currentItem = StrokeItem(data, data.buildPath(), data.toPaint())
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val item = currentItem ?: return
                when (currentTool) {
                    Tool.PEN, Tool.ERASER -> {
                        item.data.points.add(worldX)
                        item.data.points.add(worldY)
                    }
                    Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE -> {
                        item.data.points[2] = worldX
                        item.data.points[3] = worldY
                    }
                }
                item.path = item.data.buildPath()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentItem?.let { strokes.add(it) }
                currentItem = null
                invalidate()
            }
        }
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clearAll() {
        strokes.clear()
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
        for (item in strokes) {
            val d = item.data
            sb.append(d.type.name).append("|")
            sb.append(d.color).append("|")
            sb.append(d.strokeWidth).append("|")
            sb.append(d.fill).append("|")
            sb.append(d.points.joinToString(","))
            sb.append("\n")
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        strokes.clear()
        for (line in content.lines()) {
            if (line.isBlank()) continue
            val parts = line.split("|")
            if (parts.size < 5) continue
            try {
                val type = Tool.valueOf(parts[0])
                val color = parts[1].toInt()
                val strokeWidth = parts[2].toFloat()
                val fill = parts[3].toBoolean()
                val pts = if (parts[4].isBlank()) mutableListOf() else parts[4].split(",").map { it.toFloat() }.toMutableList()
                val data = StrokeData(type, pts, color, strokeWidth, fill)
                strokes.add(StrokeItem(data, data.buildPath(), data.toPaint()))
            } catch (e: Exception) {
            }
        }
        invalidate()
    }
}