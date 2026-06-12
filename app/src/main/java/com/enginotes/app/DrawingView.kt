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

data class Stroke(val path: Path, val paint: Paint)

enum class Tool { PEN, ERASER, RECTANGLE, CIRCLE, LINE }

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint = createPaint(Color.BLACK, 6f, Paint.Style.STROKE)

    var currentTool: Tool = Tool.PEN
    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var fillShapes: Boolean = false

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private var shapeStartXWorld = 0f
    private var shapeStartYWorld = 0f

    private var prevFocusX = 0f
    private var prevFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(0.2f, 5f)
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

    private fun createPaint(color: Int, strokeWidth: Float, style: Paint.Style): Paint {
        val p = Paint()
        p.color = color
        p.style = style
        p.strokeWidth = strokeWidth
        p.isAntiAlias = true
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        return p
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        drawGrid(canvas)

        for (stroke in strokes) {
            canvas.drawPath(stroke.path, stroke.paint)
        }
        currentPath?.let { canvas.drawPath(it, currentPaint) }

        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas) {
        val gridPaint = Paint()
        gridPaint.color = Color.LTGRAY
        gridPaint.strokeWidth = 1f

        val spacing = 50f
        val left = (-translateX / scaleFactor) - 2000f
        val top = (-translateY / scaleFactor) - 2000f
        val right = left + (width / scaleFactor) + 4000f
        val bottom = top + (height / scaleFactor) + 4000f

        var x = (left / spacing).toInt() * spacing
        while (x < right) {
            canvas.drawLine(x, top, x, bottom, gridPaint)
            x += spacing
        }
        var y = (top / spacing).toInt() * spacing
        while (y < bottom) {
            canvas.drawLine(left, y, right, y, gridPaint)
            y += spacing
        }
    }

    private fun screenToWorldX(x: Float): Float {
        return (x - translateX) / scaleFactor
    }

    private fun screenToWorldY(y: Float): Float {
        return (y - translateY) / scaleFactor
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
        val worldX = screenToWorldX(event.x)
        val worldY = screenToWorldY(event.y)
        val pressure = event.pressure.coerceIn(0.2f, 1.5f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                shapeStartXWorld = worldX
                shapeStartYWorld = worldY

                when (currentTool) {
                    Tool.PEN, Tool.ERASER -> {
                        currentPath = Path()
                        currentPath?.moveTo(worldX, worldY)
                        val color = if (currentTool == Tool.ERASER) Color.WHITE else currentColor
                        val strokeW = if (currentTool == Tool.ERASER) currentStrokeWidth * 4 else currentStrokeWidth * pressure
                        currentPaint = createPaint(color, strokeW, Paint.Style.STROKE)
                    }
                    Tool.RECTANGLE, Tool.CIRCLE, Tool.LINE -> {
                        currentPath = Path()
                        val style = if (fillShapes && currentTool != Tool.LINE) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                        currentPaint = createPaint(currentColor, currentStrokeWidth, style)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (currentTool) {
                    Tool.PEN, Tool.ERASER -> {
                        currentPath?.lineTo(worldX, worldY)
                    }
                    Tool.RECTANGLE -> {
                        val newPath = Path()
                        newPath.addRect(
                            RectF(
                                minOf(shapeStartXWorld, worldX),
                                minOf(shapeStartYWorld, worldY),
                                maxOf(shapeStartXWorld, worldX),
                                maxOf(shapeStartYWorld, worldY)
                            ),
                            Path.Direction.CW
                        )
                        currentPath = newPath
                    }
                    Tool.CIRCLE -> {
                        val newPath = Path()
                        val radius = kotlin.math.hypot((worldX - shapeStartXWorld).toDouble(), (worldY - shapeStartYWorld).toDouble()).toFloat()
                        newPath.addCircle(shapeStartXWorld, shapeStartYWorld, radius, Path.Direction.CW)
                        currentPath = newPath
                    }
                    Tool.LINE -> {
                        val newPath = Path()
                        newPath.moveTo(shapeStartXWorld, shapeStartYWorld)
                        newPath.lineTo(worldX, worldY)
                        currentPath = newPath
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.let { strokes.add(Stroke(it, currentPaint)) }
                currentPath = null
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
}