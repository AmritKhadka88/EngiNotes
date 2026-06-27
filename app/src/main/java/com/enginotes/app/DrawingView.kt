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

enum class PenStyle { FOUNTAIN, BALL, PENCIL, CALLIGRAPHY, MARKER }
enum class BrushStyle {
    ROUND, FLAT, TEXTURE, INK, WATERCOLOR, CRAYON, CHARCOAL, AIRBRUSH, SPRAY, STIPPLE, SPLATTER, NEON,
    MARKER, DRY_BRUSH, SCATTER, FUR, GRASS, SMOKE, PATTERN_BRUSH,
    FILL_SPRAY, GLITTER, CONFETTI, FIRE, LIGHTNING
}
enum class EraserShape { ROUND, SQUARE }

enum class Tool {
    SELECT, FILL, PEN, BRUSH, ERASER, HIGHLIGHTER, LINE, RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE,
    TRIANGLE, DIAMOND, ARROW, STAR, PENTAGON, HEXAGON, CURVE, CROSS, ARC, TEXT, AUTOSELECT, LASSO, EXPORT_WINDOW,
    HEPTAGON, OCTAGON, NONAGON, DECAGON, TRAPEZOID, PARALLELOGRAM, RIGHT_TRIANGLE, ISOSCELES_TRIANGLE,
    SEMICIRCLE, HALF_ELLIPSE, TEARDROP, HEART, PLUS_THICK, DOUBLE_ARROW, BRACKET_L, BRACKET_R,
    CLOUD, SPEECH_BUBBLE, LIGHTNING, MOON, CHEVRON_RIGHT, CHEVRON_LEFT, CHEVRON_UP, CHEVRON_DOWN,
    TRIANGLE_DOWN, RHOMBUS_TALL, OCTAGON_STOP, GEAR, SHIELD, RING, BLOCK_ARROW_RIGHT, BLOCK_ARROW_LEFT,
    BLOCK_ARROW_UP, BLOCK_ARROW_DOWN, SQUARE_ROUNDED_SMALL, BURST, FRAME, PLAQUE, FIVE_POINT_BURST,
    DIMENSION
}
enum class PaperType { BLANK, BLANK_COLORED, LINED, GRID, DOTS, ENGINEERING }
enum class EraserMode { OBJECT, AREA }
enum class CanvasMode { INFINITE, FIXED, PAGINATED, CONVENIENT }
enum class HatchPattern {
    // Lines
    HATCH_45, HATCH_135, HATCH_90, HATCH_0, HATCH_CROSS, HATCH_DIAGONAL_CROSS,
    // Engineering Standard
    CONCRETE, STEEL, EARTH, SAND, ROCK, GRAVEL, WOOD_GRAIN, WOOD_END, BRICK, BLOCK,
    GLASS, INSULATION, RUBBER, PLASTIC, CERAMIC, FIBERGLASS, FOAM, MEMBRANE,
    // Metal/Structure
    ALUMINUM, COPPER, IRON, BRONZE, TITANIUM, GOLD_HATCH,
    // Soil/Civil
    COMPACTED_FILL, LOOSE_FILL, CLAY, SILT, PEAT, CHALK,
    // Patterns
    DOTS_FINE, DOTS_COARSE, HONEYCOMB, BASKET_WEAVE, DIAMOND_GRID, ZIGZAG,
    WAVE, HERRINGBONE, SCALE, CHAIN_LINK, STIPPLE, CONTOUR,
    // Specialized
    WATER, CONCRETE_PRECAST, REBAR, ASPHALT, PLYWOOD, DRYWALL
}
enum class Orientation { PORTRAIT, LANDSCAPE }

enum class PaperSizeOption(val widthMM: Float, val heightMM: Float) {
    A4(210f, 297f), LETTER(215.9f, 279.4f), A3(297f, 420f), A5(148f, 210f),
    LEGAL(215.9f, 355.6f), TABLOID(279.4f, 431.8f), A2(420f, 594f),
    A1(594f, 841f), A0(841f, 1189f), B4(250f, 353f), B5(176f, 250f), EXECUTIVE(184.1f, 266.7f)
}

val SHAPE_TOOLS = setOf(
    Tool.LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE,
    Tool.TRIANGLE, Tool.DIAMOND, Tool.ARROW, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CURVE, Tool.CROSS,
    Tool.HEPTAGON, Tool.OCTAGON, Tool.NONAGON, Tool.DECAGON, Tool.TRAPEZOID, Tool.PARALLELOGRAM,
    Tool.RIGHT_TRIANGLE, Tool.ISOSCELES_TRIANGLE, Tool.SEMICIRCLE, Tool.HALF_ELLIPSE, Tool.TEARDROP,
    Tool.HEART, Tool.PLUS_THICK, Tool.DOUBLE_ARROW, Tool.BRACKET_L, Tool.BRACKET_R, Tool.CLOUD,
    Tool.SPEECH_BUBBLE, Tool.LIGHTNING, Tool.MOON, Tool.CHEVRON_RIGHT, Tool.CHEVRON_LEFT, Tool.CHEVRON_UP,
    Tool.CHEVRON_DOWN, Tool.TRIANGLE_DOWN, Tool.RHOMBUS_TALL, Tool.OCTAGON_STOP, Tool.GEAR, Tool.SHIELD,
    Tool.RING, Tool.BLOCK_ARROW_RIGHT, Tool.BLOCK_ARROW_LEFT, Tool.BLOCK_ARROW_UP, Tool.BLOCK_ARROW_DOWN,
    Tool.SQUARE_ROUNDED_SMALL, Tool.BURST, Tool.FRAME, Tool.PLAQUE, Tool.FIVE_POINT_BURST
)
val CLOSED_SHAPES = setOf(
    Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE,
    Tool.TRIANGLE, Tool.DIAMOND, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON,
    Tool.HEPTAGON, Tool.OCTAGON, Tool.NONAGON, Tool.DECAGON, Tool.TRAPEZOID, Tool.PARALLELOGRAM,
    Tool.RIGHT_TRIANGLE, Tool.ISOSCELES_TRIANGLE, Tool.SEMICIRCLE, Tool.HALF_ELLIPSE, Tool.TEARDROP,
    Tool.HEART, Tool.CLOUD, Tool.SPEECH_BUBBLE, Tool.LIGHTNING, Tool.MOON, Tool.TRIANGLE_DOWN,
    Tool.RHOMBUS_TALL, Tool.OCTAGON_STOP, Tool.GEAR, Tool.SHIELD, Tool.RING,
    Tool.SQUARE_ROUNDED_SMALL, Tool.BURST, Tool.FRAME, Tool.PLAQUE, Tool.FIVE_POINT_BURST
)
val BBOX_RESIZE_SHAPES = setOf(
    Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.ELLIPSE, Tool.TRIANGLE,
    Tool.DIAMOND, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CROSS,
    Tool.HEPTAGON, Tool.OCTAGON, Tool.NONAGON, Tool.DECAGON, Tool.TRAPEZOID, Tool.PARALLELOGRAM,
    Tool.RIGHT_TRIANGLE, Tool.ISOSCELES_TRIANGLE, Tool.SEMICIRCLE, Tool.HALF_ELLIPSE, Tool.TEARDROP,
    Tool.HEART, Tool.PLUS_THICK, Tool.DOUBLE_ARROW, Tool.BRACKET_L, Tool.BRACKET_R, Tool.CLOUD,
    Tool.SPEECH_BUBBLE, Tool.LIGHTNING, Tool.MOON, Tool.CHEVRON_RIGHT, Tool.CHEVRON_LEFT, Tool.CHEVRON_UP,
    Tool.CHEVRON_DOWN, Tool.TRIANGLE_DOWN, Tool.RHOMBUS_TALL, Tool.OCTAGON_STOP, Tool.GEAR, Tool.SHIELD,
    Tool.RING, Tool.BLOCK_ARROW_RIGHT, Tool.BLOCK_ARROW_LEFT, Tool.BLOCK_ARROW_UP, Tool.BLOCK_ARROW_DOWN,
    Tool.SQUARE_ROUNDED_SMALL, Tool.BURST, Tool.FRAME, Tool.PLAQUE, Tool.FIVE_POINT_BURST
)
val ENDPOINT_RESIZE_SHAPES = setOf(Tool.LINE, Tool.CIRCLE, Tool.ARROW, Tool.CURVE)

data class TextSpanData(val start: Int, val end: Int, val type: Char, val value: Int)

private val imageLoadExecutor = ThreadPoolExecutor(2, 3, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())

private val PAPER_BASE_COLOR = Color.parseColor("#FFFDF6")

private val bitmapCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    private val MAX_SIZE = 80 * 1024 * 1024L
    private var currentSize = 0L
    override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>): Boolean = currentSize > MAX_SIZE
    fun putBitmap(key: String, bmp: Bitmap) { currentSize += bmp.byteCount; put(key, bmp) }
    fun getBitmap(key: String): Bitmap? = get(key)
}

class StrokeData(
    val type: Tool, val points: MutableList<Float>,
    var color: Int, var strokeWidth: Float, var fill: Boolean, var rotation: Float = 0f,
    var fillColorVal: Int = color, var penStyle: PenStyle = PenStyle.FOUNTAIN, var opacity: Int = 255,
    var brushStyle: BrushStyle = BrushStyle.ROUND,
    // Per-point width samples for velocity-sensitive pens (Fountain). Parallel to points (one
    // width per x,y pair). Empty for pens that don't use variable width - falls back to strokeWidth.
    var widths: MutableList<Float> = mutableListOf()
) {
    fun buildPath(): Path {
        val path = Path()
        if (type == Tool.PEN || type == Tool.ERASER || type == Tool.HIGHLIGHTER || type == Tool.BRUSH) {
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
            Tool.HEPTAGON -> addPolygon(path, left, top, right, bottom, 7, false)
            Tool.OCTAGON -> addPolygon(path, left, top, right, bottom, 8, false)
            Tool.NONAGON -> addPolygon(path, left, top, right, bottom, 9, false)
            Tool.DECAGON -> addPolygon(path, left, top, right, bottom, 10, false)
            Tool.OCTAGON_STOP -> addPolygon(path, left, top, right, bottom, 8, false)
            Tool.TRAPEZOID -> {
                val inset = (right - left) * 0.2f
                path.moveTo(left + inset, top); path.lineTo(right - inset, top); path.lineTo(right, bottom); path.lineTo(left, bottom); path.close()
            }
            Tool.PARALLELOGRAM -> {
                val skew = (right - left) * 0.2f
                path.moveTo(left + skew, top); path.lineTo(right, top); path.lineTo(right - skew, bottom); path.lineTo(left, bottom); path.close()
            }
            Tool.RIGHT_TRIANGLE -> { path.moveTo(left, top); path.lineTo(left, bottom); path.lineTo(right, bottom); path.close() }
            Tool.ISOSCELES_TRIANGLE -> { path.moveTo(cx, top); path.lineTo(right, bottom); path.lineTo(left, bottom); path.close() }
            Tool.TRIANGLE_DOWN -> { path.moveTo(left, top); path.lineTo(right, top); path.lineTo(cx, bottom); path.close() }
            Tool.SEMICIRCLE -> { path.addArc(RectF(left, top, right, bottom + (bottom - top)), 180f, 180f); path.close() }
            Tool.HALF_ELLIPSE -> { path.moveTo(left, bottom); path.arcTo(RectF(left, top, right, bottom), 180f, 180f, false); path.close() }
            Tool.TEARDROP -> {
                val r = (right - left) / 2f
                path.moveTo(cx, top)
                path.cubicTo(right, top + r * 0.6f, right, bottom, cx, bottom)
                path.cubicTo(left, bottom, left, top + r * 0.6f, cx, top)
                path.close()
            }
            Tool.HEART -> {
                val w = right - left; val h = bottom - top
                path.moveTo(cx, top + h * 0.3f)
                path.cubicTo(cx - w * 0.5f, top - h * 0.1f, left - w * 0.1f, top + h * 0.5f, cx, bottom)
                path.cubicTo(right + w * 0.1f, top + h * 0.5f, cx + w * 0.5f, top - h * 0.1f, cx, top + h * 0.3f)
                path.close()
            }
            Tool.PLUS_THICK -> {
                val w = right - left; val h = bottom - top
                val tw = w * 0.32f; val th = h * 0.32f
                path.addRect(cx - tw / 2f, top, cx + tw / 2f, bottom, Path.Direction.CW)
                path.addRect(left, cy - th / 2f, right, cy + th / 2f, Path.Direction.CW)
            }
            Tool.DOUBLE_ARROW -> {
                path.moveTo(left, cy); path.lineTo(right, cy)
                val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                val al = 18f; val aa = Math.PI / 6
                for ((ex, ey, dir) in listOf(Triple(right, cy, 1.0), Triple(left, cy, -1.0))) {
                    path.moveTo(ex, ey); path.lineTo((ex - dir * al * kotlin.math.cos(aa)).toFloat(), (ey - al * kotlin.math.sin(aa)).toFloat())
                    path.moveTo(ex, ey); path.lineTo((ex - dir * al * kotlin.math.cos(aa)).toFloat(), (ey + al * kotlin.math.sin(aa)).toFloat())
                }
            }
            Tool.BRACKET_L -> {
                val r = (right - left) * 0.4f
                path.moveTo(right, top); path.quadTo(left, top, left, cy); path.quadTo(left, bottom, right, bottom)
            }
            Tool.BRACKET_R -> {
                path.moveTo(left, top); path.quadTo(right, top, right, cy); path.quadTo(right, bottom, left, bottom)
            }
            Tool.CLOUD -> {
                val w = right - left; val h = bottom - top
                path.addCircle(left + w * 0.25f, cy + h * 0.1f, h * 0.3f, Path.Direction.CW)
                path.addCircle(left + w * 0.5f, top + h * 0.3f, h * 0.35f, Path.Direction.CW)
                path.addCircle(left + w * 0.75f, cy + h * 0.1f, h * 0.3f, Path.Direction.CW)
                path.addRect(left + w * 0.2f, cy, right - w * 0.2f, bottom, Path.Direction.CW)
            }
            Tool.SPEECH_BUBBLE -> {
                val bh = (bottom - top) * 0.78f
                path.addRoundRect(RectF(left, top, right, top + bh), 16f, 16f, Path.Direction.CW)
                path.moveTo(left + (right - left) * 0.2f, top + bh)
                path.lineTo(left + (right - left) * 0.1f, bottom)
                path.lineTo(left + (right - left) * 0.4f, top + bh)
                path.close()
            }
            Tool.LIGHTNING -> {
                val w = right - left; val h = bottom - top
                path.moveTo(cx + w * 0.15f, top)
                path.lineTo(left + w * 0.2f, cy)
                path.lineTo(cx, cy)
                path.lineTo(cx - w * 0.15f, bottom)
                path.lineTo(right - w * 0.2f, top + h * 0.45f)
                path.lineTo(cx + w * 0.05f, top + h * 0.45f)
                path.close()
            }
            Tool.MOON -> {
                val r = minOf(right - left, bottom - top) / 2f
                path.addCircle(cx, cy, r, Path.Direction.CW)
                val innerPath = Path(); innerPath.addCircle(cx + r * 0.55f, cy, r * 0.85f, Path.Direction.CW)
                path.op(innerPath, Path.Op.DIFFERENCE)
            }
            Tool.CHEVRON_RIGHT -> { path.moveTo(left, top); path.lineTo(right, cy); path.lineTo(left, bottom); path.lineTo(left + (right - left) * 0.35f, cy); path.close() }
            Tool.CHEVRON_LEFT -> { path.moveTo(right, top); path.lineTo(left, cy); path.lineTo(right, bottom); path.lineTo(right - (right - left) * 0.35f, cy); path.close() }
            Tool.CHEVRON_UP -> { path.moveTo(left, bottom); path.lineTo(cx, top); path.lineTo(right, bottom); path.lineTo(cx, top + (bottom - top) * 0.35f); path.close() }
            Tool.CHEVRON_DOWN -> { path.moveTo(left, top); path.lineTo(cx, bottom); path.lineTo(right, top); path.lineTo(cx, bottom - (bottom - top) * 0.35f); path.close() }
            Tool.RHOMBUS_TALL -> { path.moveTo(cx, top); path.lineTo(right, top + (bottom - top) * 0.35f); path.lineTo(cx, bottom); path.lineTo(left, top + (bottom - top) * 0.35f); path.close() }
            Tool.GEAR -> addPolygon(path, left, top, right, bottom, 8, true)
            Tool.SHIELD -> {
                val w = right - left
                path.moveTo(left, top); path.lineTo(right, top); path.lineTo(right, top + (bottom - top) * 0.6f)
                path.quadTo(cx, bottom, left, top + (bottom - top) * 0.6f); path.close()
            }
            Tool.RING -> {
                val r = minOf(right - left, bottom - top) / 2f
                path.addCircle(cx, cy, r, Path.Direction.CW)
                val innerPath = Path(); innerPath.addCircle(cx, cy, r * 0.55f, Path.Direction.CW)
                path.op(innerPath, Path.Op.DIFFERENCE)
            }
            Tool.BLOCK_ARROW_RIGHT -> {
                val w = right - left; val h = bottom - top
                path.moveTo(left, cy - h * 0.2f); path.lineTo(right - w * 0.35f, cy - h * 0.2f); path.lineTo(right - w * 0.35f, top)
                path.lineTo(right, cy); path.lineTo(right - w * 0.35f, bottom)
                path.lineTo(right - w * 0.35f, cy + h * 0.2f); path.lineTo(left, cy + h * 0.2f); path.close()
            }
            Tool.BLOCK_ARROW_LEFT -> {
                val w = right - left; val h = bottom - top
                path.moveTo(right, cy - h * 0.2f); path.lineTo(left + w * 0.35f, cy - h * 0.2f); path.lineTo(left + w * 0.35f, top)
                path.lineTo(left, cy); path.lineTo(left + w * 0.35f, bottom)
                path.lineTo(left + w * 0.35f, cy + h * 0.2f); path.lineTo(right, cy + h * 0.2f); path.close()
            }
            Tool.BLOCK_ARROW_UP -> {
                val w = right - left; val h = bottom - top
                path.moveTo(cx - w * 0.2f, bottom); path.lineTo(cx - w * 0.2f, top + h * 0.35f); path.lineTo(left, top + h * 0.35f)
                path.lineTo(cx, top); path.lineTo(right, top + h * 0.35f)
                path.lineTo(cx + w * 0.2f, top + h * 0.35f); path.lineTo(cx + w * 0.2f, bottom); path.close()
            }
            Tool.BLOCK_ARROW_DOWN -> {
                val w = right - left; val h = bottom - top
                path.moveTo(cx - w * 0.2f, top); path.lineTo(cx - w * 0.2f, bottom - h * 0.35f); path.lineTo(left, bottom - h * 0.35f)
                path.lineTo(cx, bottom); path.lineTo(right, bottom - h * 0.35f)
                path.lineTo(cx + w * 0.2f, bottom - h * 0.35f); path.lineTo(cx + w * 0.2f, top); path.close()
            }
            Tool.SQUARE_ROUNDED_SMALL -> path.addRoundRect(RectF(left, top, right, bottom), 8f, 8f, Path.Direction.CW)
            Tool.BURST -> addPolygon(path, left, top, right, bottom, 12, true)
            Tool.FIVE_POINT_BURST -> addPolygon(path, left, top, right, bottom, 10, true)
            Tool.FRAME -> {
                path.addRect(left, top, right, bottom, Path.Direction.CW)
                val inset = minOf(right - left, bottom - top) * 0.15f
                val innerPath = Path(); innerPath.addRect(left + inset, top + inset, right - inset, bottom - inset, Path.Direction.CCW)
                path.addPath(innerPath)
            }
            Tool.PLAQUE -> {
                val cut = minOf(right - left, bottom - top) * 0.2f
                path.moveTo(left + cut, top); path.lineTo(right - cut, top); path.lineTo(right, top + cut)
                path.lineTo(right, bottom - cut); path.lineTo(right - cut, bottom); path.lineTo(left + cut, bottom)
                path.lineTo(left, bottom - cut); path.lineTo(left, top + cut); path.close()
            }
            else -> {}
        }
        return path
    }

    // Builds the calligraphy nib as a LIST of small per-segment quads instead of one giant
    // polygon for the whole stroke. This is the robust fix for the self-erasure bug: a single
    // closed polygon traced as "all left edge points then all right edge points reversed" will
    // self-intersect at any sharp reversal (loops in cursive letters, backstrokes, crossing
    // strokes), and Android's winding-rule fill turns those self-intersections into holes -
    // which is exactly the black-blob/erasure artifact seen when writing cursive signatures.
    // Drawing each segment as its own small filled quad sidesteps the problem entirely: each
    // quad is independently solid, overlapping quads just alpha-composite normally (like real
    // ink laid down stroke by stroke), and there is no shared winding state across the stroke
    // for a reversal to corrupt.
    fun buildCalligraphySegmentQuads(): List<Path> {
        val quads = mutableListOf<Path>()
        if (points.size < 4) return quads
        val nibAngle = Math.toRadians(45.0) // nib held at 45 degrees, like a real chisel-tip pen
        val nibDirX = kotlin.math.cos(nibAngle).toFloat(); val nibDirY = kotlin.math.sin(nibAngle).toFloat()
        val halfNib = strokeWidth * 0.65f
        var i = 0
        while (i + 3 < points.size) {
            val x1 = points[i]; val y1 = points[i + 1]; val x2 = points[i + 2]; val y2 = points[i + 3]
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val ndx = dx / len; val ndy = dy / len
            // Width at this segment: how perpendicular the stroke direction is to the nib's fixed
            // axis. Horizontal motion (perpendicular to a 45-degree nib) reads thick; motion along
            // the nib axis reads thin - the classic chisel-tip behavior.
            val widthFactor = kotlin.math.abs(ndx * nibDirY - ndy * nibDirX).coerceIn(0.18f, 1f)
            val nx = nibDirX * halfNib * widthFactor; val ny = nibDirY * halfNib * widthFactor
            val quad = Path()
            quad.moveTo(x1 - nx, y1 - ny)
            quad.lineTo(x2 - nx, y2 - ny)
            quad.lineTo(x2 + nx, y2 + ny)
            quad.lineTo(x1 + nx, y1 + ny)
            quad.close()
            quads.add(quad)
            i += 2
        }
        return quads
    }

    // Legacy single-polygon path - kept only as a fallback for very short strokes (under 2
    // segments) where quad-splitting has nothing to split; never used for real handwriting.
    fun buildCalligraphyRibbonPath(): Path {
        val ribbon = Path()
        if (points.size < 4) return buildPath()
        val nibAngle = Math.toRadians(45.0)
        val nibDirX = kotlin.math.cos(nibAngle).toFloat(); val nibDirY = kotlin.math.sin(nibAngle).toFloat()
        val halfNib = strokeWidth * 0.65f
        val px = points[0]; val py = points[1]
        val nx = nibDirX * halfNib; val ny = nibDirY * halfNib
        ribbon.moveTo(px - nx, py - ny); ribbon.lineTo(points[2] - nx, points[3] - ny)
        ribbon.lineTo(points[2] + nx, points[3] + ny); ribbon.lineTo(px + nx, py + ny)
        ribbon.close()
        return ribbon
    }

    // Velocity-sensitive Fountain pen ribbon: width is inversely proportional to drawing speed
    // (fast = thin, slow = pools thicker, like real ink flow), built from the per-point `widths`
    // samples recorded while drawing. Falls back to a uniform-width ribbon if no samples exist
    // (e.g. for strokes loaded from older saved files).
    fun buildFountainRibbonPath(): Path {
        val ribbon = Path()
        if (points.size < 4) return buildPath()
        val left = mutableListOf<Pair<Float, Float>>(); val right = mutableListOf<Pair<Float, Float>>()
        var i = 0; var wi = 0
        while (i + 3 < points.size) {
            val x1 = points[i]; val y1 = points[i + 1]; val x2 = points[i + 2]; val y2 = points[i + 3]
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val nx = -dy / len; val ny = dx / len
            val w = (if (wi < widths.size) widths[wi] else strokeWidth) / 2f
            left.add(Pair(x1 + nx * w, y1 + ny * w)); right.add(Pair(x1 - nx * w, y1 - ny * w))
            i += 2; wi++
        }
        // Cap the final point with the last known width
        val lastIdx = points.size - 2
        val lastW = (if (widths.isNotEmpty()) widths.last() else strokeWidth) / 2f
        if (i >= 2) {
            val px = points[lastIdx]; val py = points[lastIdx + 1]
            val pdx = px - points[i - 2]; val pdy = py - points[i - 1]
            val plen = kotlin.math.hypot(pdx.toDouble(), pdy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val nx = -pdy / plen; val ny = pdx / plen
            left.add(Pair(px + nx * lastW, py + ny * lastW)); right.add(Pair(px - nx * lastW, py - ny * lastW))
        }
        if (left.isEmpty() || right.isEmpty()) return buildPath()
        ribbon.moveTo(left[0].first, left[0].second)
        for (p in left.drop(1)) ribbon.lineTo(p.first, p.second)
        for (p in right.reversed()) ribbon.lineTo(p.first, p.second)
        ribbon.close()
        return ribbon
    }

    // Draws a Pencil stroke segment-by-segment with per-segment opacity derived from drawing
    // speed (stored in `widths` as a 0..1 intensity factor) - faster strokes fade lighter, slower
    // strokes stay darker, mimicking how graphite deposits less material when moved quickly.
    fun drawPencilStroke(canvas: Canvas, basePaint: Paint) {
        if (points.size < 4 || widths.size < 2) { canvas.drawPath(buildPath(), basePaint); return }
        var i = 0; var wi = 0
        val segPaint = Paint(basePaint)
        while (i + 3 < points.size) {
            val intensity = (if (wi < widths.size) widths[wi] else 1f).coerceIn(0.25f, 1f)
            segPaint.alpha = (basePaint.alpha * intensity).toInt()
            canvas.drawLine(points[i], points[i + 1], points[i + 2], points[i + 3], segPaint)
            i += 2; wi++
        }
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
        p.alpha = opacity
        p.style = Paint.Style.STROKE
        p.isAntiAlias = true
        if (type == Tool.HIGHLIGHTER) {
            p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.SQUARE
            return p
        }
        if (type == Tool.BRUSH) {
            when (brushStyle) {
                BrushStyle.ROUND -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND }
                BrushStyle.FLAT -> { p.strokeWidth = strokeWidth * 1.4f; p.strokeJoin = Paint.Join.MITER; p.strokeCap = Paint.Cap.SQUARE }
                BrushStyle.TEXTURE -> { p.strokeWidth = strokeWidth * 1.2f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.75f).toInt(); p.maskFilter = android.graphics.BlurMaskFilter(strokeWidth * 0.08f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                BrushStyle.INK -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = opacity }
                BrushStyle.WATERCOLOR -> { p.strokeWidth = strokeWidth * 2.2f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.22f).toInt(); p.maskFilter = android.graphics.BlurMaskFilter(strokeWidth * 0.6f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                BrushStyle.CRAYON -> { p.strokeWidth = strokeWidth * 1.1f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.80f).toInt(); p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 0.6f, strokeWidth * 0.15f), 0f) }
                BrushStyle.CHARCOAL -> { p.strokeWidth = strokeWidth * 1.4f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.55f).toInt(); p.maskFilter = android.graphics.BlurMaskFilter(strokeWidth * 0.3f, android.graphics.BlurMaskFilter.Blur.NORMAL); p.pathEffect = android.graphics.ComposePathEffect(android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 0.4f, strokeWidth * 0.2f), 0f), android.graphics.DiscretePathEffect(strokeWidth * 0.15f, strokeWidth * 0.08f)) }
                BrushStyle.AIRBRUSH -> { p.strokeWidth = strokeWidth * 2.5f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.18f).toInt(); p.maskFilter = android.graphics.BlurMaskFilter(strokeWidth * 1.2f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                BrushStyle.MARKER -> { p.strokeWidth = strokeWidth * 1.8f; p.strokeJoin = Paint.Join.MITER; p.strokeCap = Paint.Cap.SQUARE; p.alpha = (opacity * 0.95f).toInt() }
                BrushStyle.NEON -> { p.strokeWidth = strokeWidth * 1.5f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = opacity; p.maskFilter = android.graphics.BlurMaskFilter(strokeWidth * 1.2f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                BrushStyle.DRY_BRUSH -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.7f).toInt(); p.pathEffect = android.graphics.ComposePathEffect(android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 0.3f, strokeWidth * 0.25f), 0f), android.graphics.DiscretePathEffect(strokeWidth * 0.4f, strokeWidth * 0.2f)) }
                BrushStyle.LIGHTNING -> { p.strokeWidth = strokeWidth * 0.5f; p.strokeJoin = Paint.Join.MITER; p.strokeCap = Paint.Cap.BUTT; p.alpha = (opacity * 0.9f).toInt(); p.pathEffect = android.graphics.DiscretePathEffect(strokeWidth * 2f, strokeWidth * 1.5f) }
                // Particle brushes handled separately in drawActionItem
                else -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND }
            }
            return p
        }
        when (penStyle) {
            // Fountain: smooth ink flow, rounded joins/caps, true to nib width
            PenStyle.FOUNTAIN -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND }
            // Ballpoint: thinner and crisper than fountain, uniform line - no flow variation
            PenStyle.BALL -> { p.strokeWidth = (strokeWidth * 0.65f).coerceAtLeast(1.5f); p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND }
            // Pencil: thin, slightly grainy via a fine dash pattern that breaks up the line like graphite texture
            PenStyle.PENCIL -> {
                p.strokeWidth = (strokeWidth * 0.55f).coerceAtLeast(1f); p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
                p.alpha = (opacity * 0.8f).toInt()
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2.2f, 0.6f), 0f)
            }
            // Calligraphy: handled separately via the ribbon path (see buildCalligraphyRibbonPath);
            // this stroke paint is only a fallback for hit-test rendering contexts.
            PenStyle.CALLIGRAPHY -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND }
            // Marker: wide, flat-tipped, translucent felt tip - BUTT cap so the tip doesn't overshoot the stroke ends
            // Marker: wide, flat-tipped, translucent felt tip. BUTT cap so the tip doesn't
            // overshoot stroke ends; opacity fixed at 57% per spec regardless of speed/pressure.
            // A single continuous Path drawn once never self-darkens on loops since Android
            // rasterizes the whole stroke as one paint operation - darkening only appears when
            // SEPARATE strokes overlap, which is the correct/expected behavior.
            PenStyle.MARKER -> { p.strokeWidth = strokeWidth * 2.4f; p.strokeJoin = Paint.Join.MITER; p.strokeCap = Paint.Cap.BUTT; p.alpha = (255 * 0.57f).toInt() }
        }
        return p
    }

    // Separate fill paint so the outline color is never overwritten by the fill color
    fun toFillPaint(): Paint? {
        if (!fill || !CLOSED_SHAPES.contains(type)) return null
        val p = Paint()
        p.color = fillColorVal
        p.style = Paint.Style.FILL
        p.isAntiAlias = true
        return p
    }
}

class StrokeItem(val data: StrokeData, var path: Path, var paint: Paint)

class TextItem(var text: String, var x: Float, var y: Float, var color: Int, var size: Float, var rotation: Float) {
    var spans: MutableList<TextSpanData> = mutableListOf()
    var isEditing: Boolean = false
    var maxWidth: Float = 0f  // 0 = unbounded (legacy); >0 = wrap to this width
    var fontFamily: String = "sans-serif"  // Typeface family name
    var opacity: Int = 255
    // Link target: when non-null, this text renders in blue and is tappable to navigate instead
    // of being editable like normal text. Format: "bookName/noteFileName" - empty bookName means
    // "General" book. Null = not a link (normal text behavior).
    var linkTarget: String? = null
}

class ImageItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float, var rotation: Float) {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var loading: Boolean = false
}

class FillItem(var path: String, var x: Float, var y: Float, var w: Float, var h: Float) {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var loading: Boolean = false
    var hatchPattern: HatchPattern? = null
    var hatchColor: Int = android.graphics.Color.BLACK
    var hatchScale: Float = 1f
}

enum class DimMode { AUTO, MANUAL }
enum class DimPhase { IDLE, FIRST_POINT, SECOND_POINT, DRAGGING_OFFSET }

class DimensionItem(
    var x1: Float, var y1: Float,
    var x2: Float, var y2: Float,
    var offset: Float = 0f,
    var color: Int = android.graphics.Color.parseColor("#1565C0"),
    var strokeW: Float = 2f,
    var label: String = "",
    var mode: DimMode = DimMode.AUTO,
    var unit: String = "m",
    var refLength: Float = 0f,
    var isAngular: Boolean = false,
    var angle: Float = 0f,
    var fontSize: Float = 11f,      // sp
    var arrowSize: Float = 9f,      // world-unit base
    var textColor: Int = android.graphics.Color.parseColor("#1565C0")
) {
    val len: Float get() { val dx=x2-x1; val dy=y2-y1; return kotlin.math.sqrt((dx*dx+dy*dy).toDouble()).toFloat() }
    fun displayLabel(refPixelLen: Float): String {
        return when {
            label.isNotEmpty() -> label
            isAngular -> "%.1f°".format(angle)
            mode == DimMode.AUTO && refPixelLen > 0f -> {
                val scale = refLength / refPixelLen
                "%.2f %s".format(len * scale, unit)
            }
            else -> "%.0fpx".format(len)
        }
    }
    var handleP1sx = 0f; var handleP1sy = 0f
    var handleP2sx = 0f; var handleP2sy = 0f
    var handleMidsx = 0f; var handleMidsy = 0f
}

// Represents an undoable fill-toggle on a shape. Undo/redo flip fill back and rebuild the paint.
class FillToggleAction(val item: StrokeItem, val wasFilled: Boolean, val wasColor: Int, val newColor: Int)

class DrawingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    init {
        // Hardware acceleration dramatically improves smoothness for canvas drawing.
        // LAYER_TYPE_HARDWARE lets the GPU cache the view's rendering output.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private val ctx = context
    private val actions = mutableListOf<Any>()
    fun removeDimensionItem(d: DimensionItem) { actions.remove(d); selectedItem = null; redoStack.clear(); invalidate() }
    private val redoStack = mutableListOf<Any>()
    private var currentItem: StrokeItem? = null
    // Velocity tracking for the Fountain pen's speed-sensitive width (fast = thin, slow = thick)
    private var lastMoveX = 0f; private var lastMoveY = 0f; private var lastMoveTime = 0L

    var currentTool: Tool = Tool.PEN
        set(value) {
            if (field == Tool.SELECT && value != Tool.SELECT) selectedItem = null
            if (field == Tool.ARC && value != Tool.ARC) activeArcItem = null
            if ((field == Tool.AUTOSELECT || field == Tool.LASSO) && value != Tool.AUTOSELECT && value != Tool.LASSO) {
                selectedGroup = null; regionPath = null; regionStart = null
            }
            field = value; invalidate()
        }

    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var currentPenStyle: PenStyle = PenStyle.FOUNTAIN
    var currentOpacity: Int = 255
    var highlighterThickness: Float = 24f
    var highlighterOpacity: Int = 20
    var currentBrushStyle: BrushStyle = BrushStyle.ROUND
    var brushThickness: Float = 10f
    var brushOpacity: Int = 255
    var eraserOpacity: Int = 255 // 255 = full erase, lower = partial erase
    var eraserSize: Float = 40f
    var eraserMode: EraserMode = EraserMode.OBJECT
    var eraserShape: EraserShape = EraserShape.ROUND
    var fillShapes: Boolean = false
    var fillColor: Int = Color.RED
    var arcDivisions: Int = 3
    var paperType: PaperType = PaperType.GRID
    var paperColor: Int = Color.parseColor("#FFFDE7")
    var defaultTextSize: Float = 50f * 1.333f

    var canvasMode: CanvasMode = CanvasMode.CONVENIENT
    var paperSize: PaperSizeOption = PaperSizeOption.A4
    var pageOrientation: Orientation = Orientation.PORTRAIT

    var selectedGroup: MutableList<Any>? = null
    private var regionPath: Path? = null
    private var regionStart: Pair<Float, Float>? = null
    private var isWindowSelect: Boolean = true
    private val lassoPoints = mutableListOf<Pair<Float, Float>>()
    private var groupMoveStartX = 0f; private var groupMoveStartY = 0f
    private var groupResizeHandle = -1
    private var groupResizeOrigBounds = FloatArray(4)
    private var groupResizeItemSnapshots: List<FloatArray?> = emptyList()

    var selectedItem: Any? = null
        set(value) { field = value; onItemSelected?.invoke(value) }
    var onItemSelected: ((Any?) -> Unit)? = null

    private enum class HandleType { NONE, MOVE, ROTATE, TL, TM, TR, ML, MR, BL, BM, BR }

    private var activeHandle = HandleType.NONE
    private var dragStartWorldX = 0f; private var dragStartWorldY = 0f
    private var dragStartAngle = 0f; private var dragStartRotation = 0f
    private var dragStartPivotX = 0f; private var dragStartPivotY = 0f
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
    private var tableIsActive: Boolean = false
    private var tableSingleTapCell: Pair<Int, Int>? = null
    private var tableSingleTapTime: Long = 0L
    private val TABLE_DOUBLE_TAP_MS = 300L

    var onTextEditRequest: ((TextItem?, Float, Float, Float, Float) -> Unit)? = null
    var onTextSelectRequest: ((TextItem, Float, Float) -> Unit)? = null
    var onTextDeselectRequest: (() -> Unit)? = null
    var onTableCellEditRequest: ((TableItem, Int, Int, Float, Float) -> Unit)? = null
    var onAudioItemTap: ((AudioItem) -> Unit)? = null
    // Fired when a tap lands on empty canvas (no item, no handle) while using SELECT or TEXT
    // tool - the host activity uses this to commit-and-close any open text/table editor. This is
    // deliberately NOT fired for drawing/shape/line tools, where a stray tap should just be
    // ignored rather than treated as "finish editing" (and definitely shouldn't place an unwanted
    // dot - see the SHAPE_TOOLS tap-ignore handling in handleDrawing).
    var onEmptyAreaTap: (() -> Unit)? = null
    // Fired when a tap lands on a text item that has a linkTarget set - the host activity uses
    // this to navigate to the linked note/page instead of selecting or editing the text.
    var onLinkTap: ((String) -> Unit)? = null
    var isTextEditorOpen: Boolean = false
    var isTextSelected: Boolean = false  // true when text selection box is showing — blocks new editor
    var onScaleChanged: ((Float) -> Unit)? = null
    var onCanvasTransformed: (() -> Unit)? = null
    var onPageSwipe: ((Int) -> Unit)? = null
    var onScrollPercentChanged: ((Float) -> Unit)? = null
    var onDrawingStarted: (() -> Unit)? = null
    var onDrawingEnded: (() -> Unit)? = null
    var fingerPanMode: Boolean = false

    fun scrollToPercent(pct: Float) {
        // Total scrollable height = number of pages × page height, minimum 2 pages
        val pageH = pageHeightPx()
        val totalPages = estimatePageCount().coerceAtLeast(2)
        val totalH = pageH * totalPages * scaleFactor
        val maxScroll = (totalH - height).coerceAtLeast(0f)
        translateY = -(pct * maxScroll)
        clampTranslation(); onCanvasTransformed?.invoke(); invalidate()
        // Report back current position
        if (maxScroll > 0f) onScrollPercentChanged?.invoke((-translateY / maxScroll).coerceIn(0f, 1f))
    }

    private fun estimatePageCount(): Int {
        // Count pages based on the lowest content on canvas
        if (canvasMode == CanvasMode.INFINITE) return 1
        val pageH = pageHeightPx()
        var maxY = pageH // at least 1 page
        for (a in actions) {
            val b = getBounds(a)
            if (b != null && b[3] > maxY) maxY = b[3]
        }
        return kotlin.math.ceil(maxY / pageH).toInt().coerceAtLeast(1)
    }

    private var exportWindowStart: Pair<Float, Float>? = null
    private var exportWindowEnd: Pair<Float, Float>? = null
    var onExportWindowSelected: ((Float, Float, Float, Float) -> Unit)? = null

    private var scaleFactor = 1f
    private var translateX = 0f; private var translateY = 0f
    private var twoFingerLastX = 0f; private var twoFingerLastY = 0f
    private var twoFingerActive = false // true from first 2-finger contact until all fingers lift
    private var hoverX: Float? = null; private var hoverY: Float? = null

    private var isStylusDown = false
    private var drawingPointerId = -1

    // Convenient layout page dimensions (screen-sized)
    private var convenientPageW = 0f
    private var convenientPageH = 0f

    fun isDrawingTool() = currentTool == Tool.PEN || currentTool == Tool.ERASER || currentTool == Tool.HIGHLIGHTER || currentTool == Tool.BRUSH ||
        currentTool in SHAPE_TOOLS || currentTool == Tool.ARC || currentTool == Tool.FILL

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var accumulatedScaleFactor = 1f
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            accumulatedScaleFactor = 1f
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Deadzone: real two-finger panning naturally has small, noisy variance in the
            // distance between fingers even when the user's intent is purely "move the page,"
            // and ScaleGestureDetector reports every tiny variance as a scale change. Accumulate
            // the raw factor and only apply it once it's moved meaningfully away from 1.0 (a
            // deliberate pinch), otherwise this frame's "scale" is just pan noise and gets
            // ignored - the actual panning is handled separately by the two-finger midpoint
            // tracking in onTouchEvent, so this listener no longer needs to (and shouldn't) also
            // apply focus-point translation, which was duplicating that pan and compounding the
            // jitter.
            accumulatedScaleFactor *= detector.scaleFactor
            if (kotlin.math.abs(accumulatedScaleFactor - 1f) < 0.02f) return true

            val minScale = if (canvasMode != CanvasMode.INFINITE && width > 0 && height > 0) {
                minOf(width.toFloat() / (pageWidthPx() * 1.5f), height.toFloat() / (pageHeightPx() * 1.5f)).coerceAtLeast(0.05f)
            } else 0.2f
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, 6f)
            val factor = newScale / scaleFactor
            translateX = detector.focusX - (detector.focusX - translateX) * factor
            translateY = detector.focusY - (detector.focusY - translateY) * factor
            scaleFactor = newScale
            clampTranslation()
            onScaleChanged?.invoke(scaleFactor)
            onCanvasTransformed?.invoke()
            invalidate(); return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        // onSingleTapUp fires immediately on finger-up with no 300ms delay.
        // For links: single tap always navigates immediately regardless of current tool.
        // For normal text in SELECT tool: select it instantly.
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            val hit = findTextItemAt(wx, wy)
            if (hit != null) {
                // Links always navigate on single tap — long press is how you select them
                if (hit.linkTarget != null) { onLinkTap?.invoke(hit.linkTarget!!); return true }
                // Normal text in SELECT tool: select immediately
                if (currentTool == Tool.SELECT) {
                    selectedItem = hit
                    onTextSelectRequest?.invoke(hit, e.x, e.y)
                    invalidate()
                    return true
                }
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            // Audio items: tapping an already-selected audio item toggles play.
            // Tapping an unselected one (in SELECT tool) selects it first so it can be moved/resized.
            for (a in actions.reversed()) {
                if (a is AudioItem) {
                    val r = (a.radius + 12f) / scaleFactor
                    if (distance(wx, wy, a.x, a.y) <= r) {
                        if (currentTool == Tool.SELECT && selectedItem !== a) {
                            selectedItem = a; invalidate(); return true
                        }
                        onAudioItemTap?.invoke(a); return true
                    }
                }
            }
            when (currentTool) {
                Tool.TEXT -> {
                    val hit = findTextItemAt(wx, wy)
                    if (hit != null) { selectedItem = hit; currentTool = Tool.SELECT; invalidate() }
                    else if (isTextEditorOpen) {
                        // An editor is already open and the user tapped empty space - this means
                        // "I'm done typing," not "start a new text box here." Close/commit the
                        // current one instead of opening another.
                        onEmptyAreaTap?.invoke()
                    } else {
                        selectedItem = null; if (!isTextSelected) onTextEditRequest?.invoke(null, e.x, e.y, wx, wy)
                    }
                }
                Tool.SELECT -> {
                    val hit = findTextItemAt(wx, wy)
                    // Text hits are handled immediately in onSingleTapUp - skip here to avoid double-fire
                    if (hit != null) return true
                    val tableHit = actions.reversed().filterIsInstance<TableItem>().firstOrNull { t -> val b = getBounds(t); b != null && wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3] }
                    when {
                        tableHit != null -> { /* let handleTable manage this */ }
                        else -> {
                            if (selectedItem is TextItem) { selectedItem = null; onTextDeselectRequest?.invoke() }
                            onEmptyAreaTap?.invoke()
                            invalidate()
                        }
                    }
                }
                Tool.FILL -> {
                    if (!fillScrollGuard) performFill(e.x, e.y)
                    fillScrollGuard = false
                }
                else -> {}
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            // Double-tap on a DimensionItem → edit it
            if (currentTool == Tool.DIMENSION || currentTool == Tool.SELECT) {
                val hitDim = actions.filterIsInstance<DimensionItem>().firstOrNull { d ->
                    val hr = 80f
                    kotlin.math.hypot((e.x - d.handleMidsx), (e.y - d.handleMidsy)) < hr ||
                    kotlin.math.hypot((e.x - d.handleP1sx), (e.y - d.handleP1sy)) < hr ||
                    kotlin.math.hypot((e.x - d.handleP2sx), (e.y - d.handleP2sy)) < hr
                }
                if (hitDim != null) { selectedItem = hitDim; onDimensionEdit?.invoke(hitDim); invalidate(); return true }
            }
            val hit = findTextItemAt(wx, wy)
            if (hit != null) {
                selectedItem = null
                hit.isEditing = true; invalidate()
                onTextEditRequest?.invoke(hit, e.x, e.y, wx, wy)
                return true
            }
            if (currentTool == Tool.TEXT) { if (!isTextSelected) onTextEditRequest?.invoke(null, e.x, e.y, wx, wy); return true }
            // Double-tap a table in SELECT tool: enter cell-editing mode and open the tapped cell
            if (currentTool == Tool.SELECT) {
                for (action in actions.reversed()) {
                    if (action is TableItem) {
                        val b = getBounds(action) ?: continue
                        if (wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3]) {
                            activeTableItem = action; tableIsActive = true; selectedItem = null
                            val cell = action.hitTestCell(wx, wy)
                            if (cell != null) {
                                tableSelStart = cell; tableSelEnd = null
                                val rect = action.cellRect(cell.first, cell.second)
                                onTableCellEditRequest?.invoke(action, cell.first, cell.second, worldToScreenX(rect.left), worldToScreenY(rect.top))
                            }
                            invalidate(); return true
                        }
                    }
                }
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (canvasMode == CanvasMode.INFINITE && !isDrawingTool()) {
                translateX -= distanceX; translateY -= distanceY
                onScaleChanged?.invoke(scaleFactor); onCanvasTransformed?.invoke()
                invalidate(); return true
            }
            // Mark that a scroll happened so onSingleTapConfirmed doesn't fire fill
            if (kotlin.math.abs(distanceX) > 2f || kotlin.math.abs(distanceY) > 2f) fillScrollGuard = true
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (canvasMode != CanvasMode.PAGINATED && canvasMode != CanvasMode.CONVENIENT) return false
            if (e1 == null) return false
            // Only trigger swipe if the touch started OUTSIDE the page bounds (in the margin area)
            val pageLeft = translateX; val pageRight = translateX + pageWidthPx() * scaleFactor
            val touchStartX = e1.x
            val outsidePage = touchStartX < pageLeft || touchStartX > pageRight
            if (!outsidePage) return false
            val dx = e2.x - e1.x
            if (kotlin.math.abs(dx) > kotlin.math.abs(e2.y - e1.y) && kotlin.math.abs(dx) > 80f) {
                onPageSwipe?.invoke(if (dx < 0) 1 else -1)
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            val hit = findTextItemAt(wx, wy)
            if (hit != null) {
                // Long press selects any text item — including links (so you can move/edit them)
                selectedItem = hit; invalidate()
                onTextSelectRequest?.invoke(hit, e.x, e.y)
                return
            }
        }
    })

    private fun clampTranslation() {
        if (canvasMode == CanvasMode.INFINITE) return
        val pw = pageWidthPx() * scaleFactor; val ph = pageHeightPx() * scaleFactor
        val margin = 16f
        // Enforce minimum scale: page must always fill at least the screen width
        val minScaleW = width.toFloat() / pageWidthPx()
        val minScaleH = height.toFloat() / pageHeightPx()
        val minScale = when (canvasMode) {
            CanvasMode.FIXED -> minScaleW.coerceAtLeast(minScaleH).coerceAtLeast(0.3f)
            CanvasMode.CONVENIENT, CanvasMode.PAGINATED -> minScaleW.coerceAtLeast(0.3f)
            else -> 0.3f
        }
        if (scaleFactor < minScale) {
            scaleFactor = minScale
            translateX = (width - pageWidthPx() * scaleFactor) / 2f
            if (canvasMode == CanvasMode.FIXED) translateY = (height - pageHeightPx() * scaleFactor) / 2f
        }
        val pw2 = pageWidthPx() * scaleFactor; val ph2 = pageHeightPx() * scaleFactor
        val minTx = width - pw2 - margin; val maxTx = margin
        translateX = translateX.coerceIn(minTx.coerceAtMost(maxTx), maxTx)
        if (canvasMode == CanvasMode.FIXED) {
            val minTy = height - ph2 - margin; val maxTy = margin
            translateY = translateY.coerceIn(minTy.coerceAtMost(maxTy), maxTy)
        } else if (canvasMode == CanvasMode.CONVENIENT || canvasMode == CanvasMode.PAGINATED) {
            val topBarH = 64f * resources.displayMetrics.density
            translateY = translateY.coerceAtMost(topBarH)
        }
    }

    private fun loadBitmapAsync(path: String, onLoaded: (Bitmap?) -> Unit) {
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(path) }
        if (cached != null) { onLoaded(cached); return }
        imageLoadExecutor.execute {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val srcW = bounds.outWidth; val srcH = bounds.outHeight
                if (srcW <= 0 || srcH <= 0) { post { onLoaded(null) }; return@execute }
                val maxDim = 1920; var sample = 1; var tw = srcW; var th = srcH
                while (tw > maxDim || th > maxDim) { sample *= 2; tw /= 2; th /= 2 }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample; inJustDecodeBounds = false; inPreferredConfig = Bitmap.Config.RGB_565 }
                val decoded = BitmapFactory.decodeFile(path, opts)
                val finalBmp: Bitmap? = if (decoded != null && (decoded.width > maxDim || decoded.height > maxDim)) {
                    val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
                    val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                    if (scaled !== decoded) decoded.recycle(); scaled
                } else decoded
                if (finalBmp != null) synchronized(bitmapCache) { bitmapCache.putBitmap(path, finalBmp) }
                post { onLoaded(finalBmp) }
            } catch (oom: OutOfMemoryError) {
                try {
                    val opts2 = BitmapFactory.Options().apply { inSampleSize = 16; inPreferredConfig = Bitmap.Config.RGB_565 }
                    val fallback = BitmapFactory.decodeFile(path, opts2)
                    if (fallback != null) synchronized(bitmapCache) { bitmapCache.putBitmap(path, fallback) }
                    post { onLoaded(fallback) }
                } catch (e: Exception) { post { onLoaded(null) } }
            } catch (e: Exception) { post { onLoaded(null) } }
        }
    }

    private fun getOrLoadBitmap(item: ImageItem): Bitmap? {
        if (item.bitmap != null) return item.bitmap
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(item.path) }
        if (cached != null) { item.bitmap = cached; return cached }
        if (!item.loading) {
            item.loading = true
            loadBitmapAsync(item.path) { bmp -> item.bitmap = bmp; item.loading = false; invalidate() }
        }
        return null
    }

    private fun getOrLoadFillBitmap(item: FillItem): Bitmap? {
        if (item.bitmap != null) return item.bitmap
        val cached = synchronized(bitmapCache) { bitmapCache.getBitmap(item.path) }
        if (cached != null) { item.bitmap = cached; return cached }
        if (!item.loading) {
            item.loading = true
            loadBitmapAsync(item.path) { bmp -> item.bitmap = bmp; item.loading = false; invalidate() }
        }
        return null
    }

    private fun drawActionItem(canvas: Canvas, action: Any, includeFills: Boolean) {
        when (action) {
            is FillToggleAction -> return // no visual - just an undo record
            is TableItem -> action.draw(canvas, scaleFactor)
            is AudioItem -> drawAudioItem(canvas, action)
            is FillItem -> {
                if (!includeFills) return
                if (action.hatchPattern != null) {
                    drawHatchPattern(canvas, action); return
                }
                val bmp = getOrLoadFillBitmap(action) ?: return
                canvas.drawBitmap(bmp, null, RectF(action.x, action.y, action.x + action.w, action.y + action.h), null)
            }
            is StrokeItem -> {
                // All brush strokes go through the dedicated brush renderer
                if (action.data.type == Tool.BRUSH) {
                    drawBrushStroke(canvas, action); return
                }
                val isCalligraphyPen = action.data.type == Tool.PEN && action.data.penStyle == PenStyle.CALLIGRAPHY
                val isFountainPen = action.data.type == Tool.PEN && action.data.penStyle == PenStyle.FOUNTAIN && action.data.widths.size >= 2
                val isPencilPen = action.data.type == Tool.PEN && action.data.penStyle == PenStyle.PENCIL && action.data.widths.size >= 2
                if (isPencilPen && action.data.rotation == 0f) { action.data.drawPencilStroke(canvas, action.paint); return }
                if (isCalligraphyPen && action.data.rotation == 0f) {
                    val quadPaint = Paint(action.paint).apply { style = Paint.Style.FILL }
                    for (quad in action.data.buildCalligraphySegmentQuads()) canvas.drawPath(quad, quadPaint)
                    return
                }
                val renderPath = when { isCalligraphyPen -> action.data.buildCalligraphyRibbonPath(); isFountainPen -> action.data.buildFountainRibbonPath(); else -> action.path }
                val renderPaint = if (isCalligraphyPen || isFountainPen) Paint(action.paint).apply { style = Paint.Style.FILL } else action.paint
                if (action.data.rotation != 0f) {
                    val b = getBounds(action)
                    if (b != null) {
                        val cx = (b[0] + b[2]) / 2f; val cy = (b[1] + b[3]) / 2f
                        canvas.save(); canvas.rotate(action.data.rotation, cx, cy)
                        action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }
                        canvas.drawPath(renderPath, renderPaint); canvas.restore()
                    } else { action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }; canvas.drawPath(renderPath, renderPaint) }
                } else { action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }; canvas.drawPath(renderPath, renderPaint) }
            }
            is DimensionItem -> drawDimensionItem(canvas, action)
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

    private fun drawDimensionItem(canvas: Canvas, d: DimensionItem, preview: Boolean = false) {
        if (d.isAngular) { drawAngularDimensionItem(canvas, d, preview); return }
        val dx = d.x2 - d.x1; val dy = d.y2 - d.y1
        val len = kotlin.math.sqrt((dx*dx+dy*dy).toDouble()).toFloat()
        if (len < 1f) return
        val ux = dx/len; val uy = dy/len
        val nx = -uy; val ny = ux

        val sw = d.strokeW / scaleFactor
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = d.color; strokeWidth = sw; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = d.color; style = Paint.Style.FILL }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = d.textColor
            // Absolute text size: fontSize is in sp, convert to screen pixels, then to world coords
            // This makes text appear the same physical size regardless of zoom
            textSize = d.fontSize * resources.displayMetrics.scaledDensity / scaleFactor
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }

        val off = d.offset
        val dl1x = d.x1 + nx*off; val dl1y = d.y1 + ny*off
        val dl2x = d.x2 + nx*off; val dl2y = d.y2 + ny*off

        // Extension lines: gap at source, overshoot past dim line
        val extGap = 3f/scaleFactor; val extOver = 4f/scaleFactor
        canvas.drawLine(d.x1 + nx*extGap, d.y1 + ny*extGap, dl1x + nx*extOver, dl1y + ny*extOver, p)
        canvas.drawLine(d.x2 + nx*extGap, d.y2 + ny*extGap, dl2x + nx*extOver, dl2y + ny*extOver, p)

        // Main dimension line
        canvas.drawLine(dl1x, dl1y, dl2x, dl2y, p)

        // Filled arrowheads — absolute size in screen pixels
        val arL = d.arrowSize * resources.displayMetrics.density / scaleFactor
        val arW = (d.arrowSize * 0.38f) * resources.displayMetrics.density / scaleFactor
        fun arrowHead(ex: Float, ey: Float, dirX: Float, dirY: Float) {
            val path2 = android.graphics.Path()
            path2.moveTo(ex, ey)
            path2.lineTo(ex + dirX*arL + ny*arW, ey + dirY*arL - nx*arW)
            path2.lineTo(ex + dirX*arL - ny*arW, ey + dirY*arL + nx*arW)
            path2.close(); canvas.drawPath(path2, fp)
        }
        arrowHead(dl1x, dl1y, ux, uy)
        arrowHead(dl2x, dl2y, -ux, -uy)

        // Label
        val midX = (dl1x+dl2x)/2f; val midY = (dl1y+dl2y)/2f
        val labelStr = d.displayLabel(autoRefPixelLen)
        canvas.save()
        canvas.translate(midX, midY)
        val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat() * 180f / Math.PI.toFloat()
        val normAngle = if (angle > 90f || angle < -90f) angle + 180f else angle
        canvas.rotate(normAngle)
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val tw = tp.measureText(labelStr); val th = tp.textSize
        val pad = 2f/scaleFactor
        canvas.drawRoundRect(android.graphics.RectF(-tw/2f-pad, -th-pad, tw/2f+pad, pad), pad, pad, bgP)
        canvas.drawText(labelStr, 0f, -th*0.15f, tp)
        canvas.restore()

        // Store screen handles
        if (!preview) {
            d.handleP1sx = worldToScreenX(d.x1); d.handleP1sy = worldToScreenY(d.y1)
            d.handleP2sx = worldToScreenX(d.x2); d.handleP2sy = worldToScreenY(d.y2)
            d.handleMidsx = worldToScreenX(midX); d.handleMidsy = worldToScreenY(midY)
        }

        // Handles — larger for easier touch (always shown for dim tool, only selected otherwise)
        val showHandles = !preview && (selectedItem === d || currentTool == Tool.DIMENSION)
        if (showHandles) {
            val hr = 10f/scaleFactor  // larger visual handle
            val hFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
            val hStr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = d.color; style = Paint.Style.STROKE; strokeWidth = sw*1.5f }
            for ((hx,hy) in listOf(d.x1 to d.y1, d.x2 to d.y2, midX to midY)) {
                canvas.drawCircle(hx, hy, hr, hFill); canvas.drawCircle(hx, hy, hr, hStr)
            }
        }
    }

    private fun drawAngularDimensionItem(canvas: Canvas, d: DimensionItem, preview: Boolean = false) {
        val parts = d.unit.split(",")
        val p3x = parts.getOrNull(0)?.toFloatOrNull() ?: d.x2
        val p3y = parts.getOrNull(1)?.toFloatOrNull() ?: d.y2
        val supplementary = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false

        val vx = d.x1; val vy = d.y1
        val sw = d.strokeW / scaleFactor
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=d.color; strokeWidth=sw; style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=d.color; style=Paint.Style.FILL }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=d.textColor; textSize=d.fontSize*resources.displayMetrics.scaledDensity/scaleFactor; textAlign=Paint.Align.CENTER; typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD) }

        val a1 = kotlin.math.atan2((d.y2-vy).toDouble(),(d.x2-vx).toDouble()).toFloat()
        val a2 = kotlin.math.atan2((p3y-vy).toDouble(),(p3x-vx).toDouble()).toFloat()
        val arm1Len = kotlin.math.hypot((d.x2-vx),(d.y2-vy))
        val arm2Len = kotlin.math.hypot((p3x-vx),(p3y-vy))
        val arcR = (d.offset.takeIf { it > 0f } ?: minOf(arm1Len, arm2Len) * 0.4f)

        val a1Deg = Math.toDegrees(a1.toDouble()).toFloat()
        // innerSweep: the SHORT angle between the two arms (-180 to +180)
        var innerSweep = (Math.toDegrees(a2.toDouble()) - Math.toDegrees(a1.toDouble())).toFloat()
        if (innerSweep > 180f) innerSweep -= 360f; if (innerSweep < -180f) innerSweep += 360f
        val absSweep = kotlin.math.abs(innerSweep)
        if (absSweep < 1f) return  // nearly parallel

        // drawSweep: inner = short arc between arms (absSweep degrees)
        //            exterior = long arc the other way (360 - absSweep degrees)
        val exteriorSweep = if (innerSweep >= 0f) -(360f - absSweep) else (360f - absSweep)
        val drawSweep = if (supplementary) exteriorSweep else innerSweep
        val drawStartDeg = a1Deg

        // Extension lines from vertex to beyond arc radius
        val extR = arcR * 1.15f
        canvas.drawLine(vx, vy + 0f, vx + (d.x2-vx)/arm1Len*extR, vy + (d.y2-vy)/arm1Len*extR, p)
        canvas.drawLine(vx, vy + 0f, vx + (p3x-vx)/arm2Len*extR, vy + (p3y-vy)/arm2Len*extR, p)

        // Arc
        val oval = android.graphics.RectF(vx-arcR, vy-arcR, vx+arcR, vy+arcR)
        canvas.drawArc(oval, drawStartDeg, drawSweep, false, p)

        val arL = d.arrowSize * resources.displayMetrics.density / scaleFactor
        val arW = arL * 0.4f
        fun arcArrow(atAngleRad: Float, sweepDir: Float) {
            val ax = vx + arcR * kotlin.math.cos(atAngleRad)
            val ay = vy + arcR * kotlin.math.sin(atAngleRad)
            val tx = -kotlin.math.sin(atAngleRad) * sweepDir
            val ty =  kotlin.math.cos(atAngleRad) * sweepDir
            val nx2 = -ty; val ny2 = tx
            val path2 = android.graphics.Path()
            path2.moveTo(ax, ay)
            path2.lineTo(ax - tx*arL + nx2*arW, ay - ty*arL + ny2*arW)
            path2.lineTo(ax - tx*arL - nx2*arW, ay - ty*arL - ny2*arW)
            path2.close(); canvas.drawPath(path2, fp)
        }
        val drawStartRad = Math.toRadians(drawStartDeg.toDouble()).toFloat()
        val drawSweepRad = Math.toRadians(drawSweep.toDouble()).toFloat()
        val drawSweepSign = if (drawSweep >= 0f) 1f else -1f
        arcArrow(drawStartRad, -drawSweepSign)                    // start: tip points INTO arc
        arcArrow(drawStartRad + drawSweepRad, drawSweepSign)     // end: tip points INTO arc

        val midAngle = drawStartRad + drawSweepRad / 2f
        val displayAngle = if (supplementary) 360f - absSweep else absSweep
        val labelStr = if (d.label.isNotEmpty()) d.label else "%.1f°".format(displayAngle)
        val angleIsSmall = displayAngle < 15f
        val labelR = if (angleIsSmall) arcR * 2.2f else arcR * 1.25f
        val lx = vx + labelR * kotlin.math.cos(midAngle)
        val ly = vy + labelR * kotlin.math.sin(midAngle)
        if (angleIsSmall) {
            val arcMidX = vx + arcR * 1.05f * kotlin.math.cos(midAngle)
            val arcMidY = vy + arcR * 1.05f * kotlin.math.sin(midAngle)
            canvas.drawLine(arcMidX, arcMidY, lx, ly, p)
        }

        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; style=Paint.Style.FILL }
        val tw = tp.measureText(labelStr); val th = tp.textSize; val pad = 2f/scaleFactor
        canvas.drawRoundRect(android.graphics.RectF(lx-tw/2f-pad, ly-th-pad, lx+tw/2f+pad, ly+pad), pad, pad, bgP)
        canvas.drawText(labelStr, lx, ly, tp)

        // Store handles
        if (!preview) {
            d.handleP1sx = worldToScreenX(vx); d.handleP1sy = worldToScreenY(vy)
            d.handleP2sx = worldToScreenX(d.x2); d.handleP2sy = worldToScreenY(d.y2)
            d.handleMidsx = worldToScreenX(lx); d.handleMidsy = worldToScreenY(ly)
        }
        if (!preview && (selectedItem === d || currentTool == Tool.DIMENSION)) {
            val hr = 10f/scaleFactor
            val hFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; style=Paint.Style.FILL }
            val hStr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=d.color; style=Paint.Style.STROKE; strokeWidth=sw*1.5f }
            for ((hx,hy) in listOf(vx to vy, d.x2 to d.y2, p3x to p3y)) {
                canvas.drawCircle(hx, hy, hr, hFill); canvas.drawCircle(hx, hy, hr, hStr)
            }
            // Orange supplementary handle — sits on the drawn arc midpoint
            val drawSweepRad2 = Math.toRadians(drawSweep.toDouble()).toFloat()
            val drawStartRad2 = Math.toRadians(drawStartDeg.toDouble()).toFloat()
            val midArcAngle = drawStartRad2 + drawSweepRad2 / 2f
            val midArcX = vx + arcR * kotlin.math.cos(midArcAngle)
            val midArcY = vy + arcR * kotlin.math.sin(midArcAngle)
            val hSupFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.parseColor("#FF9500"); style=Paint.Style.FILL }
            canvas.drawCircle(midArcX, midArcY, hr*1.4f, hSupFill)
            canvas.drawCircle(midArcX, midArcY, hr*1.4f, hStr)
            // Update handleMid for drag detection
            d.handleMidsx = worldToScreenX(midArcX); d.handleMidsy = worldToScreenY(midArcY)
        }
    }

    private fun drawMagnifierLens(canvas: Canvas, worldX: Float, worldY: Float) {
        val sx = worldToScreenX(worldX); val sy = worldToScreenY(worldY)
        val lensRadius = 70f
        val oneCm = resources.displayMetrics.xdpi / 2.54f  // 1cm in pixels
        val lensOffsetY = -(lensRadius + oneCm)  // 1cm above finger
        val cx = sx; val cy = sy + lensOffsetY
        val zoomFactor = 3f

        // Save world transform and switch to screen space for the lens overlay
        canvas.save()
        canvas.setMatrix(null)  // reset to screen coordinates

        // Clip to lens circle
        val lenPath = android.graphics.Path(); lenPath.addCircle(cx, cy, lensRadius, android.graphics.Path.Direction.CW)
        canvas.clipPath(lenPath)

        // White fill
        canvas.drawCircle(cx, cy, lensRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; style=Paint.Style.FILL })

        // Zoom: translate so worldX,worldY maps to cx,cy, then scale by zoomFactor
        val zTx = cx - sx * zoomFactor * scaleFactor + translateX * zoomFactor - translateX
        val zTy = cy - sy * zoomFactor * scaleFactor + translateY * zoomFactor - translateY
        // Simpler: apply the world transform but scaled up
        canvas.translate(cx - sx * zoomFactor, cy - sy * zoomFactor)
        canvas.scale(zoomFactor, zoomFactor)
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        drawBackground(canvas)
        for (action in actions) drawActionItem(canvas, action, includeFills = true)

        canvas.restore()

        // Border, crosshair, stem — all in screen space
        canvas.save(); canvas.setMatrix(null)
        // Crosshair
        val chP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; strokeWidth = 1.5f; style = Paint.Style.STROKE }
        canvas.drawLine(cx-12f, cy, cx+12f, cy, chP); canvas.drawLine(cx, cy-12f, cx, cy+12f, chP)
        // Lens border
        canvas.drawCircle(cx, cy, lensRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.parseColor("#1C1C1E"); strokeWidth=2f; style=Paint.Style.STROKE })
        // Shadow
        canvas.drawCircle(cx, cy, lensRadius+2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x22000000; strokeWidth=4f; style=Paint.Style.STROKE })
        // Stem line from lens bottom to finger
        canvas.drawLine(cx, cy+lensRadius, sx, sy-8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.parseColor("#1C1C1E"); strokeWidth=1.5f; style=Paint.Style.STROKE })
        canvas.restore()
    }

    private fun drawHatchPattern(canvas: Canvas, item: FillItem) {
        val hp = item.hatchPattern ?: return
        val l = item.x; val t = item.y; val r = item.x + item.w; val b = item.y + item.h
        // s is in world coordinates — fixed size regardless of zoom
        val s = 8f * item.hatchScale
        val sw = 1.5f  // stroke width in world coords
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.hatchColor; style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.hatchColor; style = Paint.Style.FILL }

        // Draw hatch into offscreen bitmap clipped to the flood-fill shape mask
        val bmp = getOrLoadFillBitmap(item)
        if (bmp != null) {
            // Render hatch to a temp bitmap at the same world size, then composite using flood-fill alpha as mask
            val bw = item.w.toInt().coerceAtLeast(1); val bh = item.h.toInt().coerceAtLeast(1)
            val hatchBmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            val hc = Canvas(hatchBmp)
            // Offset into local coords (0,0 = top-left of bounding box)
            val lp = Paint(p).apply { strokeWidth = sw }
            val lsp = Paint(sp)
            drawHatchLocal(hc, hp, 0f, 0f, bw.toFloat(), bh.toFloat(), s, lp, lsp)
            // Use flood-fill bitmap as alpha mask: multiply alpha channels
            val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
            hc.drawBitmap(bmp, null, android.graphics.Rect(0, 0, bw, bh), maskPaint)
            canvas.drawBitmap(hatchBmp, null, RectF(l, t, r, b), null)
        } else {
            // Fallback: clip to rect
            canvas.save(); canvas.clipRect(l, t, r, b)
            drawHatchLocal(canvas, hp, l, t, r, b, s, p, sp)
            canvas.restore()
        }
    }

    private fun drawHatchLocal(canvas: Canvas, hp: HatchPattern, l: Float, t: Float, r: Float, b: Float, s: Float, p: Paint, sp: Paint) {
        val w = r - l; val h = b - t
        when (hp) {
            HatchPattern.HATCH_45 -> { var x = l - h; while (x < r + h) { canvas.drawLine(x, t, x+h, b, p); x += s } }
            HatchPattern.HATCH_135 -> { var x = l - h; while (x < r + h) { canvas.drawLine(x+h, t, x, b, p); x += s } }
            HatchPattern.HATCH_90 -> { var x = l; while (x < r) { canvas.drawLine(x, t, x, b, p); x += s } }
            HatchPattern.HATCH_0 -> { var y = t; while (y < b) { canvas.drawLine(l, y, r, y, p); y += s } }
            HatchPattern.HATCH_CROSS -> { var x = l; while (x < r) { canvas.drawLine(x, t, x, b, p); x += s }; var y = t; while (y < b) { canvas.drawLine(l, y, r, y, p); y += s } }
            HatchPattern.HATCH_DIAGONAL_CROSS -> {
                var x = l - h; while (x < r + h) { canvas.drawLine(x, t, x+h, b, p); x += s }
                x = l - h; while (x < r + h) { canvas.drawLine(x+h, t, x, b, p); x += s }
            }
            HatchPattern.CONCRETE -> {
                val rand = java.util.Random(42); var y = t
                while (y < b) { canvas.drawLine(l, y, r, y, p); y += s * 1.5f }
                for (i in 0..((w*h/s/s*3).toInt())) { canvas.drawCircle(l + rand.nextFloat()*w, t + rand.nextFloat()*h, s*0.15f, sp) }
            }
            HatchPattern.STEEL -> { var x = l - h; while (x < r + h) { canvas.drawLine(x, t, x+h, b, p); canvas.drawLine(x+s*0.3f, t, x+h+s*0.3f, b, p); x += s * 2f } }
            HatchPattern.EARTH -> {
                var y = t; while (y < b) { canvas.drawLine(l, y, r, y, p); y += s }
                val rand = java.util.Random(42); for (i in 0..(w*h/s/s).toInt()) canvas.drawCircle(l+rand.nextFloat()*w, t+rand.nextFloat()*h, s*0.12f, sp)
            }
            HatchPattern.SAND -> { val rand = java.util.Random(42); for (i in 0..(w*h/s/s*5).toInt()) canvas.drawCircle(l+rand.nextFloat()*w, t+rand.nextFloat()*h, s*0.08f, sp) }
            HatchPattern.ROCK -> {
                val rand = java.util.Random(42); var y = t
                while (y < b) { var x = l; while (x < r) { val path2 = android.graphics.Path()
                    path2.moveTo(x+rand.nextFloat()*s, y+rand.nextFloat()*s*0.5f); path2.lineTo(x+s+rand.nextFloat()*s*0.3f, y+rand.nextFloat()*s*0.5f)
                    path2.lineTo(x+s*1.2f, y+s+rand.nextFloat()*s*0.3f); path2.lineTo(x+rand.nextFloat()*s*0.5f, y+s+rand.nextFloat()*s*0.3f); path2.close()
                    canvas.drawPath(path2, p); x += s*1.5f }; y += s*1.5f }
            }
            HatchPattern.GRAVEL -> { val rand = java.util.Random(42); for (i in 0..(w*h/s/s*3).toInt()) { val cx=l+rand.nextFloat()*w; val cy=t+rand.nextFloat()*h; val rs=s*0.2f+rand.nextFloat()*s*0.3f; canvas.drawOval(android.graphics.RectF(cx-rs,cy-rs*0.6f,cx+rs,cy+rs*0.6f),p) } }
            HatchPattern.WOOD_GRAIN -> {
                var y = t; while (y < b) { val path2 = android.graphics.Path(); path2.moveTo(l, y); var x = l
                    while (x < r) { path2.quadTo(x+s*1.5f, y+s*0.3f*kotlin.math.sin((x-l)/w.toFloat()*Math.PI.toFloat()*4), x+s*3f, y); x+=s*3f }
                    canvas.drawPath(path2, p); y += s*0.8f }
            }
            HatchPattern.WOOD_END -> { val cx=(l+r)/2f; val cy=(t+b)/2f; val maxR=minOf(w,h)/2f; var rad=s; while(rad<maxR){canvas.drawOval(android.graphics.RectF(cx-rad,cy-rad*0.6f,cx+rad,cy+rad*0.6f),p);rad+=s*0.8f} }
            HatchPattern.BRICK -> {
                var y = t; var row = 0; while (y < b) { val offset=if(row%2==0)0f else s*1.5f; canvas.drawLine(l,y,r,y,p); var x=l-offset; while(x<r){canvas.drawLine(x,y,x,y+s,p);x+=s*3f}; y+=s; row++ }
            }
            HatchPattern.BLOCK -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s}; var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s} }
            HatchPattern.GLASS -> { val ap=p.alpha; p.alpha=(ap*0.6f).toInt(); var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*0.8f}; p.alpha=(ap*0.3f).toInt(); x=l-h; while(x<r+h){canvas.drawLine(x+h,t,x,b,p);x+=s*1.6f}; p.alpha=ap }
            HatchPattern.INSULATION -> {
                var y=t; while(y<b){ val path2=android.graphics.Path(); path2.moveTo(l,y); var x=l; while(x<r){path2.quadTo(x+s*0.5f,y-s*0.4f,x+s,y);path2.quadTo(x+s*1.5f,y+s*0.4f,x+s*2f,y);x+=s*2f}; canvas.drawPath(path2,p); y+=s*1.2f }
            }
            HatchPattern.RUBBER -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.5f}; var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*3f} }
            HatchPattern.PLASTIC -> { var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*0.6f} }
            HatchPattern.CERAMIC -> { var y=t; var row=0; while(y<b){var x=l+if(row%2==0)0f else s; while(x<r){canvas.drawRect(x,y,x+s*1.8f,y+s*1.8f,p);x+=s*2f};y+=s*2f;row++} }
            HatchPattern.FIBERGLASS -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawLine(x,y,x+s*0.5f,y+s,p);x+=s*0.4f};y+=s*1.5f} }
            HatchPattern.FOAM -> { val rand=java.util.Random(42); var y=t; while(y<b){var x=l; while(x<r){val rs=s*(0.3f+rand.nextFloat()*0.4f);canvas.drawCircle(x+rand.nextFloat()*s*0.5f,y+rand.nextFloat()*s*0.5f,rs,p);x+=s*1.2f};y+=s*1.2f} }
            HatchPattern.MEMBRANE -> { var y=t; val origSW=p.strokeWidth; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.4f}; p.strokeWidth=origSW*2f; var y2=t+s; while(y2<b){canvas.drawLine(l,y2,r,y2,p);p.strokeWidth=origSW;y2+=s*3f} }
            HatchPattern.ALUMINUM -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*1.5f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*3f} }
            HatchPattern.COPPER -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);canvas.drawLine(x+s*0.5f,t,x+h+s*0.5f,b,p);x+=s*2f} }
            HatchPattern.IRON -> { val origSW=p.strokeWidth; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.6f}; p.strokeWidth=origSW*2f; var y2=t; while(y2<b){canvas.drawLine(l,y2,r,y2,p);y2+=s*3f}; p.strokeWidth=origSW }
            HatchPattern.BRONZE -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f} }
            HatchPattern.TITANIUM -> { var x=l-h; while(x<r+h){canvas.drawLine(x+h,t,x,b,p);x+=s*1.2f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2.4f} }
            HatchPattern.GOLD_HATCH -> { var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*0.8f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.8f} }
            HatchPattern.COMPACTED_FILL -> {
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s}
                val rand=java.util.Random(42); for(i in 0..(w*h/s/s*2).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;canvas.drawLine(cx-s*0.3f,cy,cx+s*0.3f,cy+s*0.3f,p)}
            }
            HatchPattern.LOOSE_FILL -> { val rand=java.util.Random(42); for(i in 0..(w*h/s/s*4).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;val a=rand.nextFloat()*Math.PI.toFloat()*2f;canvas.drawLine(cx,cy,cx+kotlin.math.cos(a)*s*0.5f,cy+kotlin.math.sin(a)*s*0.5f,p)} }
            HatchPattern.CLAY -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.4f} }
            HatchPattern.SILT -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.3f}; val rand=java.util.Random(42); for(i in 0..(w*h/s/s*2).toInt())canvas.drawCircle(l+rand.nextFloat()*w,t+rand.nextFloat()*h,s*0.06f,sp) }
            HatchPattern.PEAT -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s}; val rand=java.util.Random(42); for(i in 0..(w*h/s/s*3).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;canvas.drawOval(android.graphics.RectF(cx-s*0.2f,cy-s*0.1f,cx+s*0.2f,cy+s*0.1f),sp)} }
            HatchPattern.CHALK -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*0.7f} }
            HatchPattern.DOTS_FINE -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawCircle(x,y,s*0.08f,sp);x+=s*0.6f};y+=s*0.6f} }
            HatchPattern.DOTS_COARSE -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawCircle(x,y,s*0.2f,sp);x+=s};y+=s} }
            HatchPattern.HONEYCOMB -> {
                val hw=s*1.2f; val hh=s*1.4f; var row=0; var y=t
                while(y<b){var x=l+if(row%2==0)0f else hw*0.75f; while(x<r){val path2=android.graphics.Path()
                    path2.moveTo(x+hw*0.25f,y);path2.lineTo(x+hw*0.75f,y);path2.lineTo(x+hw,y+hh*0.3f);path2.lineTo(x+hw*0.75f,y+hh*0.6f);path2.lineTo(x+hw*0.25f,y+hh*0.6f);path2.lineTo(x,y+hh*0.3f);path2.close()
                    canvas.drawPath(path2,p);x+=hw*1.5f};y+=hh*0.6f;row++}
            }
            HatchPattern.BASKET_WEAVE -> {
                var y=t; var row=0; while(y<b){var x=l+if(row%2==0)0f else s; while(x<r){if(row%2==0){canvas.drawLine(x,y,x+s,y,p);canvas.drawLine(x,y,x,y+s,p)}else{canvas.drawLine(x,y+s,x+s,y+s,p);canvas.drawLine(x+s,y,x+s,y+s,p)};x+=s*2f};y+=s;row++}
            }
            HatchPattern.DIAMOND_GRID -> {
                var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s}
                x=l-h; while(x<r+h){canvas.drawLine(x+h,t,x,b,p);x+=s}
            }
            HatchPattern.ZIGZAG -> {
                var y=t; while(y<b){val path2=android.graphics.Path();path2.moveTo(l,y);var x=l;var up=true; while(x<r){path2.lineTo(x+s,if(up)y-s*0.5f else y+s*0.5f);x+=s;up=!up};canvas.drawPath(path2,p);y+=s*1.2f}
            }
            HatchPattern.WAVE -> {
                var y=t; while(y<b){val path2=android.graphics.Path();path2.moveTo(l,y);var x=l; while(x<r){path2.quadTo(x+s*0.5f,y-s*0.5f,x+s,y);path2.quadTo(x+s*1.5f,y+s*0.5f,x+s*2f,y);x+=s*2f};canvas.drawPath(path2,p);y+=s*1.2f}
            }
            HatchPattern.HERRINGBONE -> {
                var y=t;var row=0; while(y<b){var x=l; while(x<r){if(row%2==0){canvas.drawLine(x,y,x+s,y+s,p);canvas.drawLine(x+s,y+s,x+s*2f,y,p)}else{canvas.drawLine(x,y+s,x+s,y,p);canvas.drawLine(x+s,y,x+s*2f,y+s,p)};x+=s*2f};y+=s;row++}
            }
            HatchPattern.SCALE -> {
                var y=t;var row=0; while(y<b){var x=l+if(row%2==0)0f else s; while(x<r){canvas.drawArc(android.graphics.RectF(x-s,y,x+s,y+s*2f),0f,-180f,false,p);x+=s*2f};y+=s;row++}
            }
            HatchPattern.CHAIN_LINK -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawOval(android.graphics.RectF(x-s*0.3f,y-s*0.5f,x+s*0.3f,y+s*0.5f),p);x+=s};y+=s} }
            HatchPattern.STIPPLE -> { val rand=java.util.Random(42); for(i in 0..(w*h/s/s*8).toInt())canvas.drawCircle(l+rand.nextFloat()*w,t+rand.nextFloat()*h,s*0.06f,sp) }
            HatchPattern.CONTOUR -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f} }
            HatchPattern.WATER -> {
                var y=t; while(y<b){val path2=android.graphics.Path();path2.moveTo(l,y);var x=l; while(x<r){path2.quadTo(x+s*0.5f,y-s*0.3f,x+s,y);x+=s};canvas.drawPath(path2,p);y+=s*0.8f}
            }
            HatchPattern.CONCRETE_PRECAST -> {
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f}; var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*3f}
                val rand=java.util.Random(42); for(i in 0..(w*h/s/s).toInt())canvas.drawCircle(l+rand.nextFloat()*w,t+rand.nextFloat()*h,s*0.08f,sp)
            }
            HatchPattern.REBAR -> { var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*2f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f} }
            HatchPattern.ASPHALT -> {
                val rand=java.util.Random(42); for(i in 0..(w*h/s/s*6).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;canvas.drawCircle(cx,cy,s*0.05f+rand.nextFloat()*s*0.1f,sp)}
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f}
            }
            HatchPattern.PLYWOOD -> {
                var y=t;var layer=0; while(y<b){if(layer%2==0){var x=l; while(x<r){canvas.drawLine(x,y,x,y+s,p);x+=s*0.3f}}else{canvas.drawLine(l,y+s*0.5f,r,y+s*0.5f,p)};y+=s;layer++}
            }
            HatchPattern.DRYWALL -> {
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*3f}; var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*4f}
            }
        }
    }

    private fun drawBrushStroke(canvas: Canvas, item: StrokeItem) {
        val pts = item.data.points; if (pts.size < 2) return
        val sw = item.data.strokeWidth; val col = item.data.color; val op = item.data.opacity
        val bStyle = item.data.brushStyle
        val rand = java.util.Random((pts[0] * 1000 + pts[1]).toLong())
        data class Pt(val x: Float, val y: Float)
        val points = (0 until pts.size / 2).map { Pt(pts[it*2], pts[it*2+1]) }
        fun bp(a: Int = op, w: Float = sw) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=col; this.style=Paint.Style.STROKE; strokeWidth=w; strokeJoin=Paint.Join.ROUND; strokeCap=Paint.Cap.ROUND; alpha=a }
        when (bStyle) {
            BrushStyle.ROUND -> {
                // Method 1: Circle stamping for perfectly smooth round strokes
                if (points.size >= 2 && item.data.widths.size >= 2) {
                    val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=col; style=Paint.Style.FILL; alpha=op }
                    for (i in 1 until points.size) {
                        val p1=points[i-1]; val p2=points[i]
                        val w1=(if(i-1 < item.data.widths.size) item.data.widths[i-1] else sw)/2f
                        val w2=(if(i < item.data.widths.size) item.data.widths[i] else sw)/2f
                        val dx=p2.x-p1.x; val dy=p2.y-p1.y
                        val dist=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(0.01f)
                        var t=0f; while(t<=dist) {
                            val r=t/dist
                            canvas.drawCircle(p1.x+dx*r, p1.y+dy*r, (w1+(w2-w1)*r).coerceAtLeast(0.5f), fillP)
                            t+=1f
                        }
                    }
                } else canvas.drawPath(item.path, bp())
            }
            BrushStyle.INK -> {
                if (points.size < 2) return
                val leftPts = ArrayList<android.graphics.PointF>()
                val rightPts = ArrayList<android.graphics.PointF>()
                val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=col; this.style=Paint.Style.FILL; alpha=op }

                for (i in 1 until points.size) {
                    val p1=points[i-1]; val p2=points[i]
                    val w1=if(i-1<item.data.widths.size) item.data.widths[i-1] else sw
                    val w2=if(i<item.data.widths.size) item.data.widths[i] else sw
                    val dx=p2.x-p1.x; val dy=p2.y-p1.y
                    val dist=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat()
                    if (dist==0f) continue
                    val nx=-dy/dist; val ny=dx/dist
                    val h1=w1/2f
                    leftPts.add(android.graphics.PointF(p1.x+nx*h1, p1.y+ny*h1))
                    rightPts.add(android.graphics.PointF(p1.x-nx*h1, p1.y-ny*h1))
                    if (i==points.size-1) {
                        val h2=w2/2f
                        leftPts.add(android.graphics.PointF(p2.x+nx*h2, p2.y+ny*h2))
                        rightPts.add(android.graphics.PointF(p2.x-nx*h2, p2.y-ny*h2))
                    }
                }

                if (leftPts.isNotEmpty() && rightPts.isNotEmpty()) {
                    val meshPath = android.graphics.Path()
                    meshPath.moveTo(leftPts[0].x, leftPts[0].y)
                    // Left side — smooth quad bezier
                    for (i in 1 until leftPts.size) {
                        val mx=(leftPts[i-1].x+leftPts[i].x)/2f; val my=(leftPts[i-1].y+leftPts[i].y)/2f
                        meshPath.quadTo(leftPts[i-1].x, leftPts[i-1].y, mx, my)
                    }
                    meshPath.lineTo(leftPts.last().x, leftPts.last().y)
                    // Connect to right side
                    meshPath.lineTo(rightPts.last().x, rightPts.last().y)
                    // Right side — smooth quad bezier back
                    for (i in rightPts.size-2 downTo 0) {
                        val mx=(rightPts[i+1].x+rightPts[i].x)/2f; val my=(rightPts[i+1].y+rightPts[i].y)/2f
                        meshPath.quadTo(rightPts[i+1].x, rightPts[i+1].y, mx, my)
                    }
                    meshPath.lineTo(rightPts[0].x, rightPts[0].y)
                    meshPath.close()
                    canvas.drawPath(meshPath, fillP)
                }
            }
            BrushStyle.WATERCOLOR -> {
                listOf(sw*1.8f to (op*0.06f).toInt().coerceAtLeast(2), sw*1.2f to (op*0.05f).toInt().coerceAtLeast(2), sw*0.5f to (op*0.03f).toInt().coerceAtLeast(1)).forEach { (w,a) ->
                    canvas.drawPath(item.path, bp(a,w).apply { maskFilter=android.graphics.BlurMaskFilter(w*0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
                }
                canvas.drawPath(item.path, bp((op*0.07f).toInt(), sw*0.07f))
            }
            BrushStyle.CHARCOAL -> {
                val r2=java.util.Random(pts[0].toLong())
                repeat(6) { canvas.save(); canvas.translate((r2.nextFloat()-0.5f)*sw*0.5f,(r2.nextFloat()-0.5f)*sw*0.3f); canvas.drawPath(item.path, bp((op*0.2f).toInt(), sw*(0.5f+r2.nextFloat()*0.4f)).apply{maskFilter=android.graphics.BlurMaskFilter(sw*0.12f, android.graphics.BlurMaskFilter.Blur.NORMAL)}); canvas.restore() }
                val sp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(op*0.18f).toInt()}
                val r3=java.util.Random(pts[1].toLong())
                points.forEach { pt -> if(r3.nextFloat()<0.25f) canvas.drawCircle(pt.x+(r3.nextFloat()-0.5f)*sw*0.7f, pt.y+(r3.nextFloat()-0.5f)*sw*0.7f, sw*0.04f+r3.nextFloat()*sw*0.04f, sp) }
            }
            BrushStyle.CRAYON -> {
                canvas.drawPath(item.path, bp((op*0.82f).toInt()).apply{pathEffect=android.graphics.DashPathEffect(floatArrayOf(sw*0.8f,sw*0.1f),0f)})
                val gp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(op*0.12f).toInt()}
                val r2=java.util.Random(pts[0].toLong())
                points.forEach { pt -> repeat(2){canvas.drawCircle(pt.x+(r2.nextFloat()-0.5f)*sw,pt.y+(r2.nextFloat()-0.5f)*sw*0.8f,sw*0.05f+r2.nextFloat()*sw*0.06f,gp)} }
            }
            BrushStyle.AIRBRUSH -> {
                canvas.drawPath(item.path, bp((op*0.04f).toInt().coerceAtLeast(2), sw*2.5f).apply{maskFilter=android.graphics.BlurMaskFilter(sw*1.8f, android.graphics.BlurMaskFilter.Blur.NORMAL)})
                canvas.drawPath(item.path, bp((op*0.05f).toInt().coerceAtLeast(2), sw*1.2f).apply{maskFilter=android.graphics.BlurMaskFilter(sw*0.7f, android.graphics.BlurMaskFilter.Blur.NORMAL)})
            }
            BrushStyle.TEXTURE -> {
                canvas.drawPath(item.path, bp((op*0.88f).toInt()))
                val gp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(op*0.1f).toInt()}
                val r2=java.util.Random(pts[0].toLong())
                points.forEach { pt -> repeat(3){val rx=pt.x+(r2.nextFloat()-0.5f)*sw;val ry=pt.y+(r2.nextFloat()-0.5f)*sw*0.8f;canvas.drawRect(rx,ry,rx+sw*0.08f,ry+sw*0.08f,gp)} }
            }
            BrushStyle.MARKER -> canvas.drawPath(item.path, bp((op*0.88f).toInt(), sw*1.6f).apply{strokeCap=Paint.Cap.SQUARE;strokeJoin=Paint.Join.MITER})
            BrushStyle.NEON -> {
                canvas.drawPath(item.path, bp((op*0.22f).toInt(), sw*3f).apply{maskFilter=android.graphics.BlurMaskFilter(sw*2f, android.graphics.BlurMaskFilter.Blur.NORMAL)})
                canvas.drawPath(item.path, Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;this.style=Paint.Style.STROKE;strokeWidth=sw*0.35f;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND;alpha=(op*0.85f).toInt()})
            }
            BrushStyle.DRY_BRUSH -> {
                val r2=java.util.Random(pts[0].toLong())
                repeat(7) { i -> val off=(i-3f)*sw*0.12f; canvas.save(); canvas.translate(off,0f); canvas.drawPath(item.path, bp((op*(0.3f+r2.nextFloat()*0.35f)).toInt(), sw*0.07f+r2.nextFloat()*sw*0.05f).apply{pathEffect=android.graphics.DashPathEffect(floatArrayOf(sw*(0.3f+r2.nextFloat()*0.5f),sw*(0.1f+r2.nextFloat()*0.25f)),r2.nextFloat()*sw)}); canvas.restore() }
            }
            BrushStyle.SPRAY, BrushStyle.STIPPLE, BrushStyle.SPLATTER -> drawParticleBrush(canvas, item)
            BrushStyle.SCATTER -> {
                val fp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(op*0.75f).toInt()}
                val r2=java.util.Random(pts[0].toLong())
                points.forEachIndexed { i,pt -> if(i%2==0){canvas.save();canvas.translate(pt.x+(r2.nextFloat()-0.5f)*sw*0.8f,pt.y+(r2.nextFloat()-0.5f)*sw*0.8f);canvas.rotate(r2.nextFloat()*360f);val rx=sw*(0.2f+r2.nextFloat()*0.3f);val ry=rx*(0.4f+r2.nextFloat()*0.5f);canvas.drawOval(android.graphics.RectF(-rx,-ry,rx,ry),fp);canvas.restore()} }
            }
            BrushStyle.FUR -> {
                val p=bp((op*0.65f).toInt(), sw*0.06f).apply{strokeCap=Paint.Cap.ROUND}
                val r2=java.util.Random(pts[0].toLong())
                points.forEachIndexed { i,pt -> if(i%2==0){val len=sw*(0.8f+r2.nextFloat()*0.6f);val ang=(r2.nextFloat()-0.5f)*0.7f;canvas.drawLine(pt.x,pt.y,pt.x+kotlin.math.cos(ang)*len,pt.y+kotlin.math.sin(ang)*len,p)} }
            }
            BrushStyle.GRASS -> {
                val p=bp((op*0.85f).toInt(), sw*0.07f).apply{strokeCap=Paint.Cap.ROUND}
                val r2=java.util.Random(pts[0].toLong())
                points.forEachIndexed { i,pt -> if(i%3==0){val h=sw*(1.2f+r2.nextFloat()*0.8f);val lean=(r2.nextFloat()-0.5f)*sw*0.5f;val p2=android.graphics.Path();p2.moveTo(pt.x,pt.y);p2.quadTo(pt.x+lean*0.5f,pt.y-h*0.6f,pt.x+lean,pt.y-h);canvas.drawPath(p2,p)} }
            }
            BrushStyle.SMOKE -> {
                val r2=java.util.Random(pts[0].toLong())
                val sp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(op*0.04f).toInt().coerceAtLeast(2);maskFilter=android.graphics.BlurMaskFilter(sw*1.2f, android.graphics.BlurMaskFilter.Blur.NORMAL)}
                points.forEachIndexed { i,pt -> if(i%4==0) canvas.drawCircle(pt.x+(r2.nextFloat()-0.5f)*sw*0.4f,pt.y+(r2.nextFloat()-0.5f)*sw*0.4f,sw*(0.7f+r2.nextFloat()*0.5f),sp) }
            }
            BrushStyle.FILL_SPRAY -> {
                val fp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(op*0.55f).toInt()}
                val r2=java.util.Random(pts[0].toLong()); val spread=sw*1.2f; val dotR=sw*0.04f
                points.forEach { pt -> repeat(50){val a=r2.nextFloat()*Math.PI.toFloat()*2f;val d=r2.nextGaussian().toFloat().coerceIn(-2f,2f)*spread*0.35f;canvas.drawCircle(pt.x+kotlin.math.cos(a)*d,pt.y+kotlin.math.sin(a)*d,dotR+r2.nextFloat()*dotR,fp)} }
            }
            BrushStyle.GLITTER -> {
                val gp=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.style=Paint.Style.FILL}
                val r2=java.util.Random(pts[0].toLong()); val gc=intArrayOf(col,android.graphics.Color.WHITE,android.graphics.Color.YELLOW,android.graphics.Color.CYAN)
                points.forEachIndexed { i,pt -> if(i%2==0) repeat(6){gp.color=gc[r2.nextInt(gc.size)];gp.alpha=(op*(0.4f+r2.nextFloat()*0.6f)).toInt();canvas.drawCircle(pt.x+(r2.nextFloat()-0.5f)*sw*1.1f,pt.y+(r2.nextFloat()-0.5f)*sw*1.1f,sw*0.03f+r2.nextFloat()*sw*0.07f,gp)} }
            }
            BrushStyle.CONFETTI -> {
                val cp=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.style=Paint.Style.FILL}
                val r2=java.util.Random(pts[0].toLong()); val cc=intArrayOf(android.graphics.Color.RED,android.graphics.Color.BLUE,android.graphics.Color.GREEN,android.graphics.Color.YELLOW,android.graphics.Color.MAGENTA,col)
                points.forEachIndexed { i,pt -> if(i%2==0){cp.color=cc[r2.nextInt(cc.size)];cp.alpha=(op*0.85f).toInt();canvas.save();canvas.translate(pt.x+(r2.nextFloat()-0.5f)*sw,pt.y+(r2.nextFloat()-0.5f)*sw);canvas.rotate(r2.nextFloat()*360f);val w=sw*0.18f+r2.nextFloat()*sw*0.18f;val h=sw*0.07f+r2.nextFloat()*sw*0.09f;canvas.drawRect(-w,-h,w,h,cp);canvas.restore()} }
            }
            BrushStyle.FIRE -> {
                val r2=java.util.Random(pts[0].toLong())
                points.forEachIndexed { i,pt -> if(i%3==0){val fc=if(r2.nextFloat()>0.5f) android.graphics.Color.argb(op,255,80+r2.nextInt(120),0) else android.graphics.Color.argb(op,255,180+r2.nextInt(75),0);val fp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=fc;this.style=Paint.Style.FILL;maskFilter=android.graphics.BlurMaskFilter(sw*0.3f, android.graphics.BlurMaskFilter.Blur.NORMAL)};val h=sw*(0.8f+r2.nextFloat()*1.0f);val lean=(r2.nextFloat()-0.5f)*sw*0.5f;val p2=android.graphics.Path();p2.moveTo(pt.x-sw*0.25f,pt.y);p2.quadTo(pt.x+lean,pt.y-h*0.5f,pt.x,pt.y-h);p2.quadTo(pt.x-lean*0.4f,pt.y-h*0.5f,pt.x+sw*0.25f,pt.y);p2.close();canvas.drawPath(p2,fp)} }
            }
            BrushStyle.LIGHTNING -> {
                canvas.drawPath(item.path, bp((op*0.22f).toInt(), sw*1.8f).apply{maskFilter=android.graphics.BlurMaskFilter(sw, android.graphics.BlurMaskFilter.Blur.NORMAL);pathEffect=android.graphics.DiscretePathEffect(sw*1.5f,sw*1.2f)})
                canvas.drawPath(item.path, bp((op*0.9f).toInt(), sw*0.4f).apply{pathEffect=android.graphics.DiscretePathEffect(sw*1.5f,sw*1.2f)})
            }
            else -> canvas.drawPath(item.path, item.paint)
        }
    }
    private val rng = java.util.Random(42L)
    private fun drawParticleBrush(canvas: Canvas, item: StrokeItem) {
        val pts = item.data.points; if (pts.size < 2) return
        val p = Paint(item.paint); p.style = Paint.Style.FILL; p.pathEffect = null; p.maskFilter = null
        val sw = item.data.strokeWidth; val style = item.data.brushStyle
        val density = when(style) { BrushStyle.SPRAY -> 80; BrushStyle.SPLATTER -> 20; else -> 50 }
        val spread = when(style) { BrushStyle.SPRAY -> sw * 1.8f; BrushStyle.SPLATTER -> sw * 2.5f; else -> sw * 0.6f }
        val dotR = when(style) { BrushStyle.SPRAY -> sw * 0.08f; BrushStyle.SPLATTER -> sw * 0.25f; else -> sw * 0.12f }
        val seed = (pts[0] + pts[1]).toLong()
        val rand = java.util.Random(seed)
        var i = 0; while (i < pts.size - 2) {
            val cx = pts[i]; val cy = pts[i+1]
            repeat(density) {
                // Gaussian spread for natural spray falloff
                val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
                val dist = rand.nextGaussian().toFloat().coerceIn(-2f,2f) * spread * 0.5f
                val dx = kotlin.math.cos(angle) * dist; val dy = kotlin.math.sin(angle) * dist
                val r = dotR * (0.3f + rand.nextFloat() * 0.7f)
                if (style == BrushStyle.SPLATTER) {
                    // Splatter: some elongated drops
                    if (rand.nextFloat() < 0.3f) { val path2 = android.graphics.Path(); path2.addOval(android.graphics.RectF(cx+dx-r*2, cy+dy-r*0.5f, cx+dx+r*2, cy+dy+r*0.5f), android.graphics.Path.Direction.CW); canvas.drawPath(path2, p) }
                    else canvas.drawCircle(cx + dx, cy + dy, r, p)
                } else canvas.drawCircle(cx + dx, cy + dy, r, p)
            }
            i += 2
        }
    }

    private fun drawAudioItem(canvas: Canvas, item: AudioItem) {
        val r = item.radius
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG)
        bgP.color = if (item.isPlaying) Color.parseColor("#4CAF50") else Color.parseColor("#5C6BC0")
        bgP.style = Paint.Style.FILL
        canvas.drawCircle(item.x, item.y, r, bgP)
        val iconP = Paint(Paint.ANTI_ALIAS_FLAG)
        iconP.color = Color.WHITE; iconP.textSize = r * 1.0f; iconP.textAlign = Paint.Align.CENTER
        canvas.drawText(if (item.isPlaying) "\u25A0" else "\u25B6", item.x, item.y + r * 0.35f, iconP)
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG)
        titleP.color = Color.parseColor("#212121"); titleP.textSize = (r * 0.58f).coerceIn(14f, 40f); titleP.textAlign = Paint.Align.CENTER
        canvas.drawText(item.title.take(20), item.x, item.y + r + 32f, titleP)
        if (item.isPlaying) {
            val ringP = Paint(Paint.ANTI_ALIAS_FLAG); ringP.color = Color.parseColor("#4CAF50")
            ringP.style = Paint.Style.STROKE; ringP.strokeWidth = 4f
            canvas.drawCircle(item.x, item.y, r + 10f, ringP)
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
        currentItem?.let {
            val isCalligraphyPen = it.data.type == Tool.PEN && it.data.penStyle == PenStyle.CALLIGRAPHY
            val isFountainPen = it.data.type == Tool.PEN && it.data.penStyle == PenStyle.FOUNTAIN && it.data.widths.size >= 2
            val isPencilPen = it.data.type == Tool.PEN && it.data.penStyle == PenStyle.PENCIL && it.data.widths.size >= 2
            when {
                isCalligraphyPen -> { val qp = Paint(it.paint).apply { style = Paint.Style.FILL }; for (quad in it.data.buildCalligraphySegmentQuads()) canvas.drawPath(quad, qp) }
                isFountainPen -> canvas.drawPath(it.data.buildFountainRibbonPath(), Paint(it.paint).apply { style = Paint.Style.FILL })
                isPencilPen -> it.data.drawPencilStroke(canvas, it.paint)
                else -> canvas.drawPath(it.path, it.paint)
            }
        }
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
        if (width > 0 && height > 0 && changed) {
            // Convenient page is a comfortable writing column, smaller than screen width (like a notebook page)
            convenientPageW = width.toFloat() * 0.82f
            convenientPageH = height.toFloat() * 1.1f
            when (canvasMode) {
                CanvasMode.CONVENIENT -> {
                    val margin = 16f
                    scaleFactor = ((width.toFloat() - margin * 2f) / pageWidthPx()).coerceAtMost(1f)
                    translateX = (width - pageWidthPx() * scaleFactor) / 2f
                    translateY = margin // start near top, let clampTranslation enforce the top bar limit
                    clampTranslation(); invalidate()
                }
                CanvasMode.INFINITE -> {}
                else -> {
                    val margin = 20f
                    // Print = full real A4 size filling screen width
                    scaleFactor = (width.toFloat() - margin * 2f) / pageWidthPx()
                    translateX = margin
                    translateY = margin
                    clampTranslation(); invalidate()
                }
            }
        }
    }

    // Rearranges text items to wrap and fit within the current page width (used when switching to print)
    fun rearrangeTextForPrint() {
        val pw = pageWidthPx()
        for (a in actions) {
            if (a is TextItem) {
                a.x = a.x.coerceIn(16f, pw - 60f)
                a.maxWidth = (pw - a.x - 16f).coerceAtLeast(80f)
            }
        }
        invalidate()
    }

    // Keeps text exactly as typed (no rewrapping) - single logical line per paragraph, may extend past visual edge
    fun keepTextAsIs() {
        for (a in actions) { if (a is TextItem) a.maxWidth = 4000f }
        invalidate()
    }

    private fun drawArcHandles(canvas: Canvas) {
        if (currentTool != Tool.ARC) return
        val arc = activeArcItem ?: return
        val p = Paint(); p.color = Color.parseColor("#2196F3"); p.style = Paint.Style.FILL
        val r = 12f / scaleFactor; var i = 0
        while (i + 1 < arc.data.points.size) { canvas.drawCircle(arc.data.points[i], arc.data.points[i + 1], r, p); i += 2 }
    }

    private fun textWrapWidth(item: TextItem): Int {
        if (item.maxWidth > 0f) return item.maxWidth.toInt().coerceAtLeast(40)
        if (canvasMode == CanvasMode.INFINITE) return 4000
        // Default: wrap to remaining page width from item.x to page edge
        val pw = pageWidthPx()
        return (pw - item.x - 16f).toInt().coerceAtLeast(80)
    }

    private fun drawTextItem(canvas: Canvas, item: TextItem) {
        val isLink = item.linkTarget != null
        val tp = TextPaint(); tp.color = if (isLink) Color.parseColor("#1565C0") else item.color; tp.alpha = item.opacity; tp.textSize = item.size; tp.isAntiAlias = true
        try { tp.typeface = Typeface.create(item.fontFamily, Typeface.NORMAL) } catch (e: Exception) {}
        val spannable = SpannableString(item.text)
        if (isLink) {
            // Links always render blue + underlined, like a hyperlink, regardless of any manual
            // formatting spans the user may have applied before turning the text into a link.
            spannable.setSpan(UnderlineSpan(), 0, item.text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            for (sp in item.spans) {
                val s = sp.start.coerceIn(0, item.text.length); val e = sp.end.coerceIn(s, item.text.length)
                if (s < e) when (sp.type) {
                    'S' -> spannable.setSpan(StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'C' -> spannable.setSpan(ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'U' -> spannable.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'H' -> spannable.setSpan(BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        val w = textWrapWidth(item)
        val layout = StaticLayout.Builder.obtain(spannable, 0, spannable.length, tp, w).setIncludePad(true).build()
        // Actual content width = widest line, not the wrap width (which can be 4000 for infinite canvas)
        val contentW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) }?.coerceAtLeast(1f) ?: 1f
        val contentH = layout.height.toFloat()
        canvas.save(); canvas.translate(item.x, item.y - contentH)
        canvas.rotate(item.rotation, contentW / 2f, contentH / 2f); layout.draw(canvas); canvas.restore()
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
        // Preview dimension line while drawing
        if (currentTool == Tool.DIMENSION) {
            if (dimAngular) {
                val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=currentColor; style=Paint.Style.STROKE; strokeWidth=2f/scaleFactor; strokeCap=Paint.Cap.ROUND }
                val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=currentColor; style=Paint.Style.FILL }
                val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x3300AAFF; style=Paint.Style.FILL }
                when (dimAngPhase) {
                    0 -> {
                        // Searching p1: lens only while finger is touching
                        if (dimFingerDown) drawMagnifierLens(canvas, dimCurWx, dimCurWy)
                    }
                    2 -> {
                        // p1 fixed, searching vertex: show p1 dot + line to cursor while touching
                        canvas.drawCircle(dimP1wx, dimP1wy, 4f/scaleFactor, dotP)
                        if (dimFingerDown) {
                            canvas.drawLine(dimP1wx, dimP1wy, dimCurWx, dimCurWy, lineP)
                            drawMagnifierLens(canvas, dimCurWx, dimCurWy)
                        }
                    }
                    4 -> {
                        // Vertex fixed, searching p3: show both dots + lines + live arc + angle while touching
                        canvas.drawCircle(dimP1wx, dimP1wy, 4f/scaleFactor, dotP)
                        canvas.drawCircle(dimP2wx, dimP2wy, 4f/scaleFactor, dotP)
                        canvas.drawLine(dimP2wx, dimP2wy, dimP1wx, dimP1wy, lineP)
                        if (dimFingerDown) {
                            canvas.drawLine(dimP2wx, dimP2wy, dimCurWx, dimCurWy, lineP)
                            val a1r = kotlin.math.atan2((dimP1wy-dimP2wy).toDouble(),(dimP1wx-dimP2wx).toDouble()).toFloat()
                            val a2r = kotlin.math.atan2((dimCurWy-dimP2wy).toDouble(),(dimCurWx-dimP2wx).toDouble()).toFloat()
                            val r1 = kotlin.math.hypot(dimP1wx-dimP2wx, dimP1wy-dimP2wy)
                            val r2 = kotlin.math.hypot(dimCurWx-dimP2wx, dimCurWy-dimP2wy)
                            val arcR = minOf(r1,r2)*0.4f
                            if (arcR > 2f/scaleFactor) {
                                var sweep = (Math.toDegrees(a2r.toDouble()) - Math.toDegrees(a1r.toDouble())).toFloat()
                                if (sweep > 180f) sweep -= 360f; if (sweep < -180f) sweep += 360f
                                canvas.drawArc(android.graphics.RectF(dimP2wx-arcR,dimP2wy-arcR,dimP2wx+arcR,dimP2wy+arcR), Math.toDegrees(a1r.toDouble()).toFloat(), sweep, false, lineP)
                                val midA = a1r + Math.toRadians(sweep.toDouble()).toFloat()/2f
                                val tp2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=currentColor; textSize=defaultDimFontSize*resources.displayMetrics.scaledDensity/scaleFactor; textAlign=Paint.Align.CENTER; typeface=android.graphics.Typeface.DEFAULT_BOLD }
                                canvas.drawText("%.1f°".format(kotlin.math.abs(sweep)), dimP2wx+arcR*1.4f*kotlin.math.cos(midA), dimP2wy+arcR*1.4f*kotlin.math.sin(midA), tp2)
                            }
                            drawMagnifierLens(canvas, dimCurWx, dimCurWy)
                        }
                    }
                }
            } else {
                // Linear dimension preview
                val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=currentColor; style=Paint.Style.FILL }
                val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x3300AAFF; style=Paint.Style.FILL }
                when (dimPhase) {
                    DimPhase.FIRST_POINT -> {
                        // Searching for point1 — lens only while finger down
                        if (dimFingerDown) drawMagnifierLens(canvas, dimCurWx, dimCurWy)
                    }
                    DimPhase.SECOND_POINT -> {
                        // Point1 fixed — show dot at p1
                        canvas.drawCircle(dimP1wx, dimP1wy, 4f/scaleFactor, dotP)
                        canvas.drawCircle(dimP1wx, dimP1wy, 14f/scaleFactor, glowP)
                        if (dimFingerDown) {
                            // Live preview line while searching for p2
                            val preview = DimensionItem(dimP1wx, dimP1wy, dimCurWx, dimCurWy, 0f, currentColor, currentStrokeWidth)
                            drawDimensionItem(canvas, preview, preview = true)
                            drawMagnifierLens(canvas, dimCurWx, dimCurWy)
                        }
                    }
                    else -> {}
                }
            }
        }
        val item = selectedItem ?: return
        if (item is TextItem) {
            val tp = android.text.TextPaint(); tp.textSize = item.size
            try { tp.typeface = android.graphics.Typeface.create(item.fontFamily, android.graphics.Typeface.NORMAL) } catch (e: Exception) {}
            val wrapW = textWrapWidth(item)
            val layout = android.text.StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, wrapW).setIncludePad(true).build()
            val contentW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) }?.coerceAtLeast(1f) ?: 1f
            val contentH = layout.height.toFloat()
            canvas.save()
            canvas.translate(item.x, item.y - contentH)
            canvas.rotate(item.rotation, contentW / 2f, contentH / 2f)
            // Selection border
            val selP = Paint(); selP.color = Color.parseColor("#2196F3"); selP.style = Paint.Style.STROKE
            selP.strokeWidth = 2f / scaleFactor; selP.isAntiAlias = true
            canvas.drawRect(0f, 0f, contentW, contentH, selP)
            // Rotate handle — large green circle, 32px screen size, easy to tap
            val hr = 32f / scaleFactor
            val hx = contentW / 2f; val hy = -56f / scaleFactor
            canvas.drawLine(contentW / 2f, 0f, hx, hy + hr, selP)
            val hFill = Paint(); hFill.color = Color.parseColor("#34C759"); hFill.style = Paint.Style.FILL; hFill.isAntiAlias = true
            val hStroke = Paint(); hStroke.color = Color.WHITE; hStroke.style = Paint.Style.STROKE; hStroke.strokeWidth = 4f / scaleFactor; hStroke.isAntiAlias = true
            // Draw rotation symbol inside the handle
            canvas.drawCircle(hx, hy, hr, hFill)
            canvas.drawCircle(hx, hy, hr, hStroke)
            // Draw ↻ arrow inside
            val ap = Paint(); ap.color = Color.WHITE; ap.style = Paint.Style.STROKE; ap.strokeWidth = 3f / scaleFactor; ap.isAntiAlias = true; ap.strokeCap = Paint.Cap.ROUND
            val ar = hr * 0.5f
            canvas.drawArc(android.graphics.RectF(hx - ar, hy - ar, hx + ar, hy + ar), -150f, 270f, false, ap)
            val arrowPath = android.graphics.Path(); arrowPath.moveTo(hx + ar * 0.3f, hy - ar * 0.95f); arrowPath.lineTo(hx + ar, hy - ar * 0.3f); arrowPath.lineTo(hx + ar * 0.3f, hy + ar * 0.3f)
            canvas.drawPath(arrowPath, ap)
            canvas.restore()
            return
        }
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
        val isBbox = item is ImageItem || item is TextItem || item is AudioItem ||
            (item is StrokeItem && BBOX_RESIZE_SHAPES.contains(item.data.type))
        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)
        if (isBbox) {
            for ((_, pos) in bboxHandlePositions(bounds)) {
                hFill.color = Color.WHITE
                canvas.drawCircle(pos.first, pos.second, hr, hFill)
                canvas.drawCircle(pos.first, pos.second, hr, hStroke)
            }
        } else if (isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
            hFill.color = Color.WHITE
            val p0x = item.data.points[0]; val p0y = item.data.points[1]
            val p1x = item.data.points[2]; val p1y = item.data.points[3]
            canvas.drawCircle(p0x, p0y, hr, hFill); canvas.drawCircle(p0x, p0y, hr, hStroke)
            canvas.drawCircle(p1x, p1y, hr, hFill); canvas.drawCircle(p1x, p1y, hr, hStroke)
        }
        val canRotate = item is ImageItem || item is TextItem || item is AudioItem ||
            (item is StrokeItem && item.data.type != Tool.PEN && item.data.type != Tool.ARC)
        if (canRotate) {
            val cx = (bounds[0] + bounds[2]) / 2f; val rotY = bounds[1] - 60f / scaleFactor
            val rotLinePaint = Paint(); rotLinePaint.color = Color.parseColor("#2196F3"); rotLinePaint.strokeWidth = 1.5f / scaleFactor
            canvas.drawLine(cx, bounds[1], cx, rotY, rotLinePaint)
            hFill.color = Color.parseColor("#4CAF50")
            canvas.drawCircle(cx, rotY, hr, hFill); canvas.drawCircle(cx, rotY, hr, hStroke)
            val rp = Paint(); rp.color = Color.WHITE; rp.textSize = hr * 1.2f; rp.textAlign = Paint.Align.CENTER; rp.isAntiAlias = true
            canvas.drawText("\u21bb", cx, rotY + hr * 0.4f, rp)
        }
        hFill.color = Color.parseColor("#F44336")
        val delR = hr * 1.4f; val delX = bounds[2] + hr * 5f; val delY = bounds[1] - hr * 5f
        canvas.drawCircle(delX, delY, delR, hFill)
        val xp = Paint(); xp.color = Color.WHITE; xp.textSize = delR * 1.4f; xp.textAlign = Paint.Align.CENTER; xp.isAntiAlias = true
        canvas.drawText("\u2715", delX, delY + delR * 0.4f, xp)
        canvas.restore()
    }

    private fun getBounds(item: Any): FloatArray? {
        return when (item) {
            is TableItem -> floatArrayOf(item.x, item.y, item.x + item.totalWidth(), item.y + item.totalHeight())
            is ImageItem -> floatArrayOf(item.x, item.y, item.x + item.w, item.y + item.h)
            is FillItem -> floatArrayOf(item.x, item.y, item.x + item.w, item.y + item.h)
            is AudioItem -> { val r = item.radius; floatArrayOf(item.x - r, item.y - r, item.x + r, item.y + r + 40f) }
            is TextItem -> {
                val tp = TextPaint(); tp.textSize = item.size; tp.isAntiAlias = true
                try { tp.typeface = Typeface.create(item.fontFamily, Typeface.NORMAL) } catch (e: Exception) {}
                val ww = textWrapWidth(item)
                val layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, ww).setIncludePad(true).build()
                val w = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) }?.coerceAtLeast(10f) ?: 10f
                val h = layout.height.toFloat().coerceAtLeast(item.size * 1.2f)
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
                    var minX = pts[0]; var maxX = pts[0]; var minY = pts[1]; var maxY = pts[1]; var i = 0
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
            is AudioItem -> { item.x += dx; item.y += dy }
            is StrokeItem -> {
                var i = 0
                while (i + 1 < item.data.points.size) { item.data.points[i] += dx; item.data.points[i + 1] += dy; i += 2 }
                item.path = item.data.buildPath()
            }
        }
    }

    private fun resizeItem(item: Any, handle: HandleType, wx: Float, wy: Float) {
        val min = 15f
        when (item) {
            is ImageItem -> {
                val rot = Math.toRadians(item.rotation.toDouble())
                val cos = kotlin.math.cos(rot).toFloat(); val sin = kotlin.math.sin(rot).toFloat()
                val oldCx = item.x + item.w / 2f; val oldCy = item.y + item.h / 2f
                val dx = wx - oldCx; val dy = wy - oldCy
                val lx = dx * cos + dy * sin; val ly = -dx * sin + dy * cos
                val oldW = item.w; val oldH = item.h
                val fixedLocalX = when (handle) { HandleType.TL, HandleType.ML, HandleType.BL -> oldW / 2f; HandleType.TR, HandleType.MR, HandleType.BR -> -oldW / 2f; else -> 0f }
                val fixedLocalY = when (handle) { HandleType.TL, HandleType.TM, HandleType.TR -> oldH / 2f; HandleType.BL, HandleType.BM, HandleType.BR -> -oldH / 2f; else -> 0f }
                val fixedWorldX = oldCx + fixedLocalX * cos - fixedLocalY * sin
                val fixedWorldY = oldCy + fixedLocalX * sin + fixedLocalY * cos
                var nl = -oldW / 2f; var nt = -oldH / 2f; var nr = oldW / 2f; var nb = oldH / 2f
                when (handle) { HandleType.TL -> { nl = lx; nt = ly }; HandleType.TM -> { nt = ly }; HandleType.TR -> { nr = lx; nt = ly }; HandleType.ML -> { nl = lx }; HandleType.MR -> { nr = lx }; HandleType.BL -> { nl = lx; nb = ly }; HandleType.BM -> { nb = ly }; HandleType.BR -> { nr = lx; nb = ly }; else -> return }
                if (nr - nl < min) { if (handle == HandleType.TL || handle == HandleType.ML || handle == HandleType.BL) nl = nr - min else nr = nl + min }
                if (nb - nt < min) { if (handle == HandleType.TL || handle == HandleType.TM || handle == HandleType.TR) nt = nb - min else nb = nt + min }
                val newW = nr - nl; val newH = nb - nt
                val newFixedLocalX = when (handle) { HandleType.TL, HandleType.ML, HandleType.BL -> newW / 2f; HandleType.TR, HandleType.MR, HandleType.BR -> -newW / 2f; else -> 0f }
                val newFixedLocalY = when (handle) { HandleType.TL, HandleType.TM, HandleType.TR -> newH / 2f; HandleType.BL, HandleType.BM, HandleType.BR -> -newH / 2f; else -> 0f }
                val newCx = fixedWorldX - (newFixedLocalX * cos - newFixedLocalY * sin)
                val newCy = fixedWorldY - (newFixedLocalX * sin + newFixedLocalY * cos)
                item.x = newCx - newW / 2f; item.y = newCy - newH / 2f; item.w = newW; item.h = newH
            }
            is StrokeItem -> {
                if (BBOX_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    val rot = Math.toRadians(item.data.rotation.toDouble())
                    val cos = kotlin.math.cos(rot).toFloat(); val sin = kotlin.math.sin(rot).toFloat()
                    val l = minOf(item.data.points[0], item.data.points[2]); val t = minOf(item.data.points[1], item.data.points[3])
                    val r = maxOf(item.data.points[0], item.data.points[2]); val b = maxOf(item.data.points[1], item.data.points[3])
                    val oldW = r - l; val oldH = b - t; val oldCx = (l + r) / 2f; val oldCy = (t + b) / 2f
                    val dx = wx - oldCx; val dy = wy - oldCy
                    val lx = dx * cos + dy * sin; val ly = -dx * sin + dy * cos
                    val fixedLocalX = when (handle) { HandleType.TL, HandleType.ML, HandleType.BL -> oldW / 2f; HandleType.TR, HandleType.MR, HandleType.BR -> -oldW / 2f; else -> 0f }
                    val fixedLocalY = when (handle) { HandleType.TL, HandleType.TM, HandleType.TR -> oldH / 2f; HandleType.BL, HandleType.BM, HandleType.BR -> -oldH / 2f; else -> 0f }
                    val fixedWorldX = oldCx + fixedLocalX * cos - fixedLocalY * sin
                    val fixedWorldY = oldCy + fixedLocalX * sin + fixedLocalY * cos
                    var nl = -oldW / 2f; var nt = -oldH / 2f; var nr = oldW / 2f; var nb = oldH / 2f
                    when (handle) { HandleType.TL -> { nl = lx; nt = ly }; HandleType.TM -> { nt = ly }; HandleType.TR -> { nr = lx; nt = ly }; HandleType.ML -> { nl = lx }; HandleType.MR -> { nr = lx }; HandleType.BL -> { nl = lx; nb = ly }; HandleType.BM -> { nb = ly }; HandleType.BR -> { nr = lx; nb = ly }; else -> return }
                    if (nr - nl < min) { if (handle == HandleType.TL || handle == HandleType.ML || handle == HandleType.BL) nl = nr - min else nr = nl + min }
                    if (nb - nt < min) { if (handle == HandleType.TL || handle == HandleType.TM || handle == HandleType.TR) nt = nb - min else nb = nt + min }
                    val newW = nr - nl; val newH = nb - nt
                    val newFixedLocalX = when (handle) { HandleType.TL, HandleType.ML, HandleType.BL -> newW / 2f; HandleType.TR, HandleType.MR, HandleType.BR -> -newW / 2f; else -> 0f }
                    val newFixedLocalY = when (handle) { HandleType.TL, HandleType.TM, HandleType.TR -> newH / 2f; HandleType.BL, HandleType.BM, HandleType.BR -> -newH / 2f; else -> 0f }
                    val newCx = fixedWorldX - (newFixedLocalX * cos - newFixedLocalY * sin)
                    val newCy = fixedWorldY - (newFixedLocalX * sin + newFixedLocalY * cos)
                    item.data.points[0] = newCx - newW / 2f; item.data.points[1] = newCy - newH / 2f
                    item.data.points[2] = newCx + newW / 2f; item.data.points[3] = newCy + newH / 2f
                    item.path = item.data.buildPath()
                } else if (ENDPOINT_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    val rot = item.data.rotation
                    val (ux, uy) = if (rot != 0f) {
                        val b = getBounds(item) ?: floatArrayOf(0f, 0f, 0f, 0f)
                        val cx = (b[0] + b[2]) / 2f; val cy = (b[1] + b[3]) / 2f
                        rotatePoint(wx, wy, cx, cy, -rot)
                    } else Pair(wx, wy)
                    when (handle) { HandleType.TL -> { item.data.points[0] = ux; item.data.points[1] = uy }; HandleType.BR -> { item.data.points[2] = ux; item.data.points[3] = uy }; else -> {} }
                    item.path = item.data.buildPath()
                }
            }
            is TextItem -> {
                val isCorner = handle == HandleType.TL || handle == HandleType.TR || handle == HandleType.BL || handle == HandleType.BR
                val isHorizontalMiddle = handle == HandleType.ML || handle == HandleType.MR
                when {
                    isCorner -> {
                        // Corner handle: scale font size, same as resizing a shape by its corner.
                        // Uses diagonal drag distance from the opposite corner so it feels like a
                        // proportional scale rather than a raw vertical-drag size change.
                        val dy = wy - resizePrevWorldY
                        item.size = (item.size + dy * 0.6f).coerceIn(8f, 400f)
                    }
                    isHorizontalMiddle -> {
                        // Middle handle: reflow the wrap width, font size stays fixed. Cannot
                        // shrink narrower than the longest word's rendered width (the "compact"
                        // floor) - past that point there's nothing left to rearrange, just like
                        // a shape can't be resized smaller than its minimum content.
                        val tp = TextPaint(); tp.textSize = item.size
                        try { tp.typeface = Typeface.create(item.fontFamily, Typeface.NORMAL) } catch (e: Exception) {}
                        val longestWord = item.text.split(Regex("\\s+")).maxOfOrNull { tp.measureText(it) } ?: 40f
                        val compactFloor = (longestWord + 24f).coerceAtLeast(60f)
                        val dx = if (handle == HandleType.MR) (wx - resizePrevWorldX) else (resizePrevWorldX - wx)
                        val current = if (item.maxWidth > 0f) item.maxWidth else textWrapWidth(item).toFloat()
                        item.maxWidth = (current + dx * 2f).coerceIn(compactFloor, pageWidthPx())
                    }
                    else -> {
                        // TM/BM (vertical-only handles) don't apply to text - there's nothing
                        // meaningful to resize purely vertically for a text block.
                    }
                }
            }
            is AudioItem -> {
                // Audio items are a circle, not a rectangle, so resizing just scales the radius
                // based on how far the drag point is from the item's center - works the same
                // regardless of which corner handle was grabbed.
                val d = distance(wx, wy, item.x, item.y)
                item.radius = d.coerceIn(24f, 220f)
            }
        }
        resizePrevWorldX = wx; resizePrevWorldY = wy
    }

    private fun findItemAt(x: Float, y: Float): Any? {
        val pad = 20f / scaleFactor
        for (a in actions.reversed()) {
            if (a is FillItem) continue
            if (a is AudioItem) { if (distance(x, y, a.x, a.y) <= (a.radius + 12f) / scaleFactor) return a; continue }
            if (a is TableItem) {
                val b = getBounds(a) ?: continue
                if (x in (b[0] - pad)..(b[2] + pad) && y in (b[1] - pad)..(b[3] + pad)) return a; continue
            }
            val b = getBounds(a) ?: continue
            if (x in (b[0] - pad)..(b[2] + pad) && y in (b[1] - pad)..(b[3] + pad)) return a
        }
        return null
    }

    fun findTextItemAtPublic(x: Float, y: Float): TextItem? = findTextItemAt(x, y)
    private fun findTextItemAt(x: Float, y: Float): TextItem? {
        for (a in actions.reversed()) {
            if (a is TextItem) {
                val tp = TextPaint(); tp.textSize = a.size
                try { tp.typeface = android.graphics.Typeface.create(a.fontFamily, android.graphics.Typeface.NORMAL) } catch (e: Exception) {}
                val wrapW = textWrapWidth(a)
                val layout = android.text.StaticLayout.Builder.obtain(a.text, 0, a.text.length, tp, wrapW).setIncludePad(true).build()
                val cw = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) }?.coerceAtLeast(1f) ?: 1f
                val ch = layout.height.toFloat()
                val pad = 24f / scaleFactor
                val pivX = a.x + cw / 2f; val pivY = a.y - ch / 2f
                val lx: Float; val ly: Float
                if (a.rotation != 0f) {
                    val rad = Math.toRadians(-a.rotation.toDouble())
                    val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
                    val dx = x - pivX; val dy = y - pivY
                    lx = pivX + dx * cos - dy * sin; ly = pivY + dx * sin + dy * cos
                } else { lx = x; ly = y }
                if (lx >= a.x - pad && lx <= a.x + cw + pad && ly >= a.y - ch - pad && ly <= a.y + pad) return a
            }
        }
        return null
    }

    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var textLongPressStartX = 0f; private var textLongPressStartY = 0f
    private var dimPhase = DimPhase.IDLE
    private var dimP1wx = 0f; private var dimP1wy = 0f
    private var dimP2wx = 0f; private var dimP2wy = 0f
    private var dimCurWx = 0f; private var dimCurWy = 0f   // live finger position
    private var dimDraggingItem: DimensionItem? = null
    private var dimDragHandle = 0  // 1=p1, 2=p2, 3=mid
    var dimMode: DimMode = DimMode.AUTO
    var dimAngular: Boolean = false   // true = angular mode, tap 3 points
    // Angular: p1=vertex, p2=arm1 end, p3=arm2 end
    private var dimAngP3wx = 0f; private var dimAngP3wy = 0f
    private var dimAngPhase = 0  // 0=idle,1=arm1 confirmed,2=vertex confirmed,3=arm2 dragging
    private var dimFingerDown = false  // true only while finger is actively touching
    var autoRefPixelLen: Float = 0f
    var autoRefRealLen: Float = 1f
    var autoRefUnit: String = "m"
    var defaultDimFontSize: Float = 11f
    var defaultDimArrowSize: Float = 9f
    // Legacy compat
    private var dimStartWx = 0f; private var dimStartWy = 0f
    private var dimEndWx = 0f; private var dimEndWy = 0f
    private var isDimDrawing = false
    var pendingHatchPattern: HatchPattern? = null
    var pendingHatchColor: Int = android.graphics.Color.BLACK
    private var fillScrollGuard = false
    var onDimensionCreated: ((DimensionItem) -> Unit)? = null
    var onDimensionEdit: ((DimensionItem) -> Unit)? = null

    private fun handleTable(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val tol = 22f / scaleFactor
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val table = activeTableItem
                if (table != null && tableIsActive) {
                    val rb = table.hitTestRowBorder(wy, tol); val cb = table.hitTestColBorder(wx, tol)
                    if (rb >= 0) { tableDragRowBorder = rb; tableDragStartY = wy; tableDragOrigSize = table.rowHeights[rb]; return }
                    if (cb >= 0) { tableDragColBorder = cb; tableDragStartX = wx; tableDragOrigSize = table.colWidths[cb]; return }
                    val cell = table.hitTestCell(wx, wy)
                    if (cell == null) { tableIsActive = false; tableSelStart = null; tableSelEnd = null; activeTableItem = null; currentTool = Tool.SELECT; invalidate(); return }
                    // Excel-like: a single tap on a cell immediately opens it for editing
                    tableSelStart = cell; tableSelEnd = null
                    val rect = table.cellRect(cell.first, cell.second)
                    val sx = worldToScreenX(rect.left); val sy = worldToScreenY(rect.top)
                    onTableCellEditRequest?.invoke(table, cell.first, cell.second, sx, sy)
                    invalidate()
                } else if (table != null && !tableIsActive) {
                    val cell = table.hitTestCell(wx, wy)
                    if (cell != null) {
                        tableIsActive = true; tableSelStart = cell; tableSelEnd = null; tableSingleTapCell = null
                        val rect = table.cellRect(cell.first, cell.second)
                        val sx = worldToScreenX(rect.left); val sy = worldToScreenY(rect.top)
                        onTableCellEditRequest?.invoke(table, cell.first, cell.second, sx, sy)
                        invalidate()
                    } else { activeTableItem = null; invalidate() }
                } else {
                    for (action in actions.reversed()) {
                        if (action is TableItem) {
                            val b = getBounds(action) ?: continue
                            if (wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3]) {
                                activeTableItem = action; tableIsActive = false; tableSelStart = null; tableSelEnd = null; tableSingleTapCell = null; invalidate(); return
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val table = activeTableItem ?: return; if (!tableIsActive) return
                if (tableDragRowBorder >= 0) { table.rowHeights[tableDragRowBorder] = (tableDragOrigSize + (wy - tableDragStartY)).coerceAtLeast(20f); invalidate() }
                else if (tableDragColBorder >= 0) { table.colWidths[tableDragColBorder] = (tableDragOrigSize + (wx - tableDragStartX)).coerceAtLeast(30f); invalidate() }
            }
            MotionEvent.ACTION_UP -> { if (tableDragRowBorder >= 0 || tableDragColBorder >= 0) { tableDragRowBorder = -1; tableDragColBorder = -1 } }
        }
    }

    // Long-press extends selection to a range (for merge) without opening the editor
    fun extendTableSelection(wx: Float, wy: Float) {
        val table = activeTableItem ?: return; if (!tableIsActive) return
        val cell = table.hitTestCell(wx, wy) ?: return
        if (tableSelStart == null) tableSelStart = cell else tableSelEnd = cell
        invalidate()
    }

    fun addTableRow(afterRow: Int) { val table = activeTableItem ?: return; val insertAt = (afterRow + 1).coerceIn(0, table.rowHeights.size); table.rowHeights.add(insertAt, 60f); table.rows = table.rowHeights.size; table.insertRow(insertAt); invalidate() }
    fun addTableCol(afterCol: Int) { val table = activeTableItem ?: return; val insertAt = (afterCol + 1).coerceIn(0, table.colWidths.size); table.colWidths.add(insertAt, 80f); table.cols = table.colWidths.size; table.insertCol(insertAt); invalidate() }
    fun removeTableRow(row: Int) { val table = activeTableItem ?: return; if (table.rows <= 1) return; val delAt = row.coerceIn(0, table.rowHeights.size - 1); table.rowHeights.removeAt(delAt); table.rows = table.rowHeights.size; table.deleteRow(delAt); invalidate() }
    fun removeTableCol(col: Int) { val table = activeTableItem ?: return; if (table.cols <= 1) return; val delAt = col.coerceIn(0, table.colWidths.size - 1); table.colWidths.removeAt(delAt); table.cols = table.colWidths.size; table.deleteCol(delAt); invalidate() }
    fun mergeCellSelection() { val table = activeTableItem ?: return; val s = tableSelStart ?: return; val e = tableSelEnd ?: return; table.mergeCells(s.first, s.second, e.first, e.second); tableSelStart = null; tableSelEnd = null; invalidate() }
    fun unmergeCellSelection() { val table = activeTableItem ?: return; val s = tableSelStart ?: return; table.unmergeCells(s.first, s.second); tableSelStart = null; tableSelEnd = null; invalidate() }
    fun getActiveTable(): TableItem? = activeTableItem
    fun getTableSelection(): Pair<Pair<Int, Int>?, Pair<Int, Int>?> = Pair(tableSelStart, tableSelEnd)
    fun exitTableEditMode() { tableIsActive = false; invalidate() }
    fun deselectTable() { activeTableItem = null; tableIsActive = false; tableSelStart = null; tableSelEnd = null; invalidate() }

    fun worldToScreenX(wx: Float): Float = wx * scaleFactor + translateX
    fun worldToScreenY(wy: Float): Float = wy * scaleFactor + translateY

    private fun drawTableOverlay(canvas: Canvas) {
        val table = activeTableItem ?: return
        // Defensive check: if the table was deleted from `actions` through any path (eraser,
        // undo, select-and-delete) but `activeTableItem` still points to it, this overlay would
        // otherwise keep drawing a rectangle for an object that no longer exists - an
        // unselectable, uneraseable "ghost" rectangle. Clear the stale reference instead.
        if (!actions.contains(table)) { activeTableItem = null; tableIsActive = false; return }
        val selColor = if (tableIsActive) "#2196F3" else "#9E9E9E"
        val op = Paint(); op.color = Color.parseColor(selColor); op.style = Paint.Style.STROKE
        op.strokeWidth = (if (tableIsActive) 2f else 1.5f) / scaleFactor
        canvas.drawRect(table.x, table.y, table.x + table.totalWidth(), table.y + table.totalHeight(), op)
        if (!tableIsActive) return
        val s = tableSelStart; val e = tableSelEnd
        if (s != null) {
            val minR = if (e != null) minOf(s.first, e.first) else s.first; val maxR = if (e != null) maxOf(s.first, e.first) else s.first
            val minC = if (e != null) minOf(s.second, e.second) else s.second; val maxC = if (e != null) maxOf(s.second, e.second) else s.second
            val hlP = Paint(); hlP.color = Color.parseColor("#442196F3"); hlP.style = Paint.Style.FILL
            for (r in minR..maxR) for (c in minC..maxC) { try { canvas.drawRect(table.cellRect(r, c), hlP) } catch (ex: Exception) {} }
        }
    }

    private fun handleSelect(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        // In SELECT tool, tables behave like any other selectable/movable/resizable item.
        // Cell editing only happens when the table was entered via direct tap while no other tool drag is active
        // and the table is the currently selectedItem (so user explicitly chose to edit, not move).
        if (event.actionMasked == MotionEvent.ACTION_DOWN && activeTableItem != null && selectedItem !== activeTableItem) {
            // Clicking elsewhere while a table was mid-edit: deactivate cell editing, fall through to normal select
            tableIsActive = false; tableSelStart = null; tableSelEnd = null
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            for (action in actions.reversed()) {
                if (action is TableItem) {
                    val b = getBounds(action) ?: continue
                    if (wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3]) {
                        activeTableItem = action; tableIsActive = false; tableSelStart = null; tableSelEnd = null; tableSingleTapCell = null
                        // fall through to normal item selection below so the table can be moved/resized
                        break
                    }
                }
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }; longPressRunnable = null
                val item = selectedItem; var handled = false
                // Text items are fully owned by MainActivity's overlay box (its own handles
                // intercept touches directly before they'd reach here) - running this generic
                // drag/resize/rotate hit-test for text too was redundant and could fight with the
                // overlay box's own touch handling.
                if (item is TextItem) { if (!handled) { activeHandle = HandleType.NONE; selectedItem = findItemAt(wx, wy) }; invalidate(); return }
                if (item != null) {
                    val b = getBounds(item)
                    if (b != null) {
                        val rot = getRotation(item); val (px, py) = getPivot(item, b)
                        val (lx, ly) = rotatePoint(wx, wy, px, py, -rot)
                        val hr = 16f / scaleFactor; val hit = 70f / scaleFactor
                        val delX = b[2] + hr * 5f; val delY = b[1] - hr * 5f
                        if (distance(lx, ly, delX, delY) <= hit * 1.2f) { actions.remove(item); if (item === activeTableItem) { activeTableItem = null; tableIsActive = false }; selectedItem = null; handled = true; invalidate(); return }
                        val canRot = item is ImageItem || item is TextItem || item is AudioItem || (item is StrokeItem && item.data.type != Tool.PEN && item.data.type != Tool.ARC)
                        if (!handled && canRot) {
                            val cx = (b[0] + b[2]) / 2f; val ry = b[1] - 60f / scaleFactor
                            if (distance(lx, ly, cx, ry) <= hit) { activeHandle = HandleType.ROTATE; dragStartAngle = computeAngle(item, wx, wy); dragStartRotation = rot; handled = true }
                        }
                        val isBbox = item is ImageItem || item is TextItem || item is AudioItem || (item is StrokeItem && BBOX_RESIZE_SHAPES.contains(item.data.type))
                        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)
                        if (!handled && isBbox) {
                            for ((type, pos) in bboxHandlePositions(b)) {
                                if (distance(lx, ly, pos.first, pos.second) <= hit) { activeHandle = type; dragStartPivotX = px; dragStartPivotY = py; dragStartRotation = rot; resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true; break }
                            }
                        }
                        if (!handled && isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
                            val p0x = item.data.points[0]; val p0y = item.data.points[1]; val p1x = item.data.points[2]; val p1y = item.data.points[3]
                            if (distance(lx, ly, p0x, p0y) <= hit) { activeHandle = HandleType.TL; resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true }
                            else if (distance(lx, ly, p1x, p1y) <= hit) { activeHandle = HandleType.BR; resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true }
                        }
                        if (!handled && lx >= b[0] - hit && lx <= b[2] + hit && ly >= b[1] - hit && ly <= b[3] + hit) { activeHandle = HandleType.MOVE; dragStartWorldX = wx; dragStartWorldY = wy; handled = true }
                    }
                }
                if (!handled) { activeHandle = HandleType.NONE; selectedItem = findItemAt(wx, wy) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }
                val item = selectedItem ?: return
                when (activeHandle) {
                    HandleType.MOVE -> { moveItem(item, wx - dragStartWorldX, wy - dragStartWorldY); dragStartWorldX = wx; dragStartWorldY = wy }
                    HandleType.ROTATE -> { val newAngle = computeAngle(item, wx, wy); setRotation(item, dragStartRotation + (newAngle - dragStartAngle)) }
                    HandleType.NONE -> return
                    else -> resizeItem(item, activeHandle, wx, wy)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> { longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }; activeHandle = HandleType.NONE }
        }
    }

    private fun handleArc(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val arc = activeArcItem
                if (arc != null) {
                    val r = 50f / scaleFactor; var found = -1; var i = 0
                    while (i + 1 < arc.data.points.size) { if (distance(wx, wy, arc.data.points[i], arc.data.points[i + 1]) <= r) { found = i; break }; i += 2 }
                    if (found >= 0) arcDragPointIndex = found
                    else { activeArcItem = null; val d = StrokeData(Tool.ARC, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, false); currentItem = StrokeItem(d, d.buildPath(), d.toPaint()) }
                } else { val d = StrokeData(Tool.ARC, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, false); currentItem = StrokeItem(d, d.buildPath(), d.toPaint()) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (arcDragPointIndex >= 0) { val arc = activeArcItem ?: return; arc.data.points[arcDragPointIndex] = wx; arc.data.points[arcDragPointIndex + 1] = wy; arc.path = arc.data.buildPath() }
                else { val item = currentItem ?: return; item.data.points[2] = wx; item.data.points[3] = wy; item.path = item.data.buildPath() }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (arcDragPointIndex >= 0) { arcDragPointIndex = -1 }
                else {
                    val item = currentItem
                    if (item != null) {
                        val p0x = item.data.points[0]; val p0y = item.data.points[1]; val p1x = item.data.points[2]; val p1y = item.data.points[3]
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
            is StrokeItem -> { var i = 0; while (i + 1 < item.data.points.size) { item.data.points[i] = ox + (item.data.points[i] - ox) * sx; item.data.points[i + 1] = oy + (item.data.points[i + 1] - oy) * sy; i += 2 }; item.path = item.data.buildPath() }
            is TextItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.size = (item.size * ((sx + sy) / 2f)).coerceIn(6f, 500f) }
            is ImageItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.w *= sx; item.h *= sy }
            is FillItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.w *= sx; item.h *= sy }
            is AudioItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.radius = (item.radius * ((sx + sy) / 2f)).coerceIn(24f, 220f) }
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

    // AutoCAD-style box select: dragging LEFT-TO-RIGHT is a "window" select (only items fully
    // enclosed by the box are selected); dragging RIGHT-TO-LEFT is a "crossing" select (any item
    // the box even touches is selected). This replaces the old 4-mode picker (rectangle/freeform
    // x whole/divided) with a single intuitive gesture, matching how AutoCAD and most CAD/drawing
    // tools already work.
    private fun selectItemsInRegion(region: Region, regionBounds: FloatArray, windowMode: Boolean) {
        val group = mutableListOf<Any>(); val newActions = mutableListOf<Any>()
        for (action in actions) {
            if (action is FillItem) { newActions.add(action); continue }
            val b = getBounds(action); if (b == null) { newActions.add(action); continue }
            val matches = if (windowMode) {
                // Window select: item's full bounding box must be inside the drag rectangle
                b[0] >= regionBounds[0] && b[1] >= regionBounds[1] && b[2] <= regionBounds[2] && b[3] <= regionBounds[3]
            } else {
                // Crossing select: any overlap between item bounds and the drag rectangle counts
                b[0] <= regionBounds[2] && b[2] >= regionBounds[0] && b[1] <= regionBounds[3] && b[3] >= regionBounds[1]
            }
            if (matches) group.add(action) else newActions.add(action)
        }
        newActions.addAll(group); actions.clear(); actions.addAll(newActions); redoStack.clear()
        selectedGroup = if (group.isNotEmpty()) group else null
    }

    private fun handleAutoSelect(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val hr = 16f / scaleFactor; val hit = 70f / scaleFactor
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    val gb = groupBounds(group)
                    if (gb != null) {
                        val delX = gb[2] + hr * 5f; val delY = gb[1] - hr * 5f
                        if (distance(wx, wy, delX, delY) <= hit) { for (it in group) actions.remove(it); selectedGroup = null; invalidate(); return }
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
                        val origW = (groupResizeOrigBounds[2] - groupResizeOrigBounds[0]).coerceAtLeast(1f); val origH = (groupResizeOrigBounds[3] - groupResizeOrigBounds[1]).coerceAtLeast(1f)
                        var nl = groupResizeOrigBounds[0]; var nt = groupResizeOrigBounds[1]; var nr = groupResizeOrigBounds[2]; var nb = groupResizeOrigBounds[3]
                        when (groupResizeHandle) { 0 -> { nl = wx; nt = wy }; 1 -> nt = wy; 2 -> { nr = wx; nt = wy }; 3 -> nl = wx; 4 -> nr = wx; 5 -> { nl = wx; nb = wy }; 6 -> nb = wy; 7 -> { nr = wx; nb = wy } }
                        val sx = (nr - nl).coerceAtLeast(10f) / origW; val sy = (nb - nt).coerceAtLeast(10f) / origH
                        for (it in group) scaleItemInGroup(it, groupResizeOrigBounds[0], groupResizeOrigBounds[1], sx, sy)
                    } else { val dx = wx - groupMoveStartX; val dy = wy - groupMoveStartY; for (it in group) moveItem(it, dx, dy); groupMoveStartX = wx; groupMoveStartY = wy }
                    invalidate(); return
                }
                val s = regionStart ?: return
                // Window select (left-to-right drag) vs crossing select (right-to-left drag),
                // same convention as AutoCAD. Track this live so the box outline can hint which
                // mode is active while dragging.
                isWindowSelect = wx >= s.first
                regionPath = Path().apply { addRect(minOf(s.first, wx), minOf(s.second, wy), maxOf(s.first, wx), maxOf(s.second, wy), Path.Direction.CW) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                groupResizeHandle = -1; val group = selectedGroup; if (group != null && group.isNotEmpty()) return
                val rp = regionPath; val s = regionStart
                if (rp != null && s != null) {
                    val bounds = floatArrayOf(minOf(s.first, wx), minOf(s.second, wy), maxOf(s.first, wx), maxOf(s.second, wy))
                    selectItemsInRegion(buildRegion(rp), bounds, isWindowSelect)
                }
                regionPath = null; regionStart = null; invalidate()
            }
        }
    }

    // Lasso: freeform selection by tracing an irregular loop with your finger, instead of a
    // straight-edged rectangle. Selects any item whose bounds overlap the traced region - same
    // "touch it, select it" feel as a crossing-select, just with an arbitrary shape rather than a
    // box. This is the third selection mode (Select / Box Select / Lasso).
    private fun handleLasso(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        val hr = 16f / scaleFactor; val hit = 70f / scaleFactor
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    val gb = groupBounds(group)
                    if (gb != null) {
                        val delX = gb[2] + hr * 5f; val delY = gb[1] - hr * 5f
                        if (distance(wx, wy, delX, delY) <= hit) { for (it in group) actions.remove(it); selectedGroup = null; invalidate(); return }
                        val gcx = (gb[0] + gb[2]) / 2f
                        val gHandles = listOf(gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1], gb[0] to (gb[1]+gb[3])/2f, gb[2] to (gb[1]+gb[3])/2f, gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3])
                        var found = -1
                        for ((hi, hpos) in gHandles.withIndex()) { if (distance(wx, wy, hpos.first, hpos.second) <= hit) { found = hi; break } }
                        if (found >= 0) { groupResizeHandle = found; groupResizeOrigBounds = gb.copyOf(); groupResizeItemSnapshots = group.map { getBounds(it)?.copyOf() }; invalidate(); return }
                        if (wx in gb[0]..gb[2] && wy in gb[1]..gb[3]) { groupMoveStartX = wx; groupMoveStartY = wy; groupResizeHandle = -1; invalidate(); return }
                    }
                    selectedGroup = null
                }
                lassoPoints.clear(); lassoPoints.add(Pair(wx, wy))
                regionStart = Pair(wx, wy); regionPath = Path().apply { moveTo(wx, wy) }; invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    if (groupResizeHandle >= 0) {
                        val origW = (groupResizeOrigBounds[2] - groupResizeOrigBounds[0]).coerceAtLeast(1f); val origH = (groupResizeOrigBounds[3] - groupResizeOrigBounds[1]).coerceAtLeast(1f)
                        var nl = groupResizeOrigBounds[0]; var nt = groupResizeOrigBounds[1]; var nr = groupResizeOrigBounds[2]; var nb = groupResizeOrigBounds[3]
                        when (groupResizeHandle) { 0 -> { nl = wx; nt = wy }; 1 -> nt = wy; 2 -> { nr = wx; nt = wy }; 3 -> nl = wx; 4 -> nr = wx; 5 -> { nl = wx; nb = wy }; 6 -> nb = wy; 7 -> { nr = wx; nb = wy } }
                        val sx = (nr - nl).coerceAtLeast(10f) / origW; val sy = (nb - nt).coerceAtLeast(10f) / origH
                        for (it in group) scaleItemInGroup(it, groupResizeOrigBounds[0], groupResizeOrigBounds[1], sx, sy)
                    } else { val dx = wx - groupMoveStartX; val dy = wy - groupMoveStartY; for (it in group) moveItem(it, dx, dy); groupMoveStartX = wx; groupMoveStartY = wy }
                    invalidate(); return
                }
                // Freeform: just keep adding points to trace whatever shape the finger draws,
                // unlike box-select's rectangle reconstruction.
                lassoPoints.add(Pair(wx, wy))
                regionPath?.lineTo(wx, wy)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                groupResizeHandle = -1; val group = selectedGroup; if (group != null && group.isNotEmpty()) return
                val rp = regionPath
                if (rp != null) {
                    rp.close()
                    val rf = RectF(); rp.computeBounds(rf, true)
                    // Direction-sensitive, like the box-select tool: tracing CLOCKWISE acts like
                    // a "window" select (only items fully enclosed by the loop); tracing
                    // COUNTERCLOCKWISE acts like a "crossing" select (anything the loop touches).
                    // Determined via the shoelace formula (signed polygon area) on the traced
                    // points - positive signed area = clockwise in standard screen coordinates
                    // (Y increases downward), negative = counterclockwise.
                    val isClockwise = signedPolygonArea(lassoPoints) > 0f
                    selectItemsInRegion(buildRegion(rp), floatArrayOf(rf.left, rf.top, rf.right, rf.bottom), isClockwise)
                }
                regionPath = null; regionStart = null; lassoPoints.clear(); invalidate()
            }
        }
    }

    // Shoelace formula: sum of (x_i * y_{i+1} - x_{i+1} * y_i) over all edges, halved. Sign
    // indicates winding direction. Works on the raw traced points without needing them to form a
    // perfectly closed/simple polygon - good enough for direction detection on a hand-drawn loop.
    private fun signedPolygonArea(points: List<Pair<Float, Float>>): Float {
        if (points.size < 3) return 0f
        var sum = 0f
        for (i in points.indices) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[(i + 1) % points.size]
            sum += (x1 * y2 - x2 * y1)
        }
        return sum / 2f
    }

    private fun drawExportWindowOverlay(canvas: Canvas) {
        val s = exportWindowStart ?: return; val e = exportWindowEnd ?: return
        val left = minOf(s.first, e.first); val top = minOf(s.second, e.second)
        val right = maxOf(s.first, e.first); val bottom = maxOf(s.second, e.second)
        val dimP = Paint(); dimP.color = Color.parseColor("#88000000"); dimP.style = Paint.Style.FILL
        val vl = screenToWorldX(0f); val vt = screenToWorldY(0f); val vr = screenToWorldX(width.toFloat()); val vb = screenToWorldY(height.toFloat())
        canvas.drawRect(vl, vt, vr, top, dimP); canvas.drawRect(vl, bottom, vr, vb, dimP)
        canvas.drawRect(vl, top, left, bottom, dimP); canvas.drawRect(right, top, vr, bottom, dimP)
        val bp = Paint(); bp.color = Color.parseColor("#2196F3"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 3f / scaleFactor
        bp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f / scaleFactor, 6f / scaleFactor), 0f)
        canvas.drawRect(left, top, right, bottom, bp)
        val hr = 10f / scaleFactor; val hf = Paint(); hf.color = Color.WHITE; hf.style = Paint.Style.FILL
        val hs = Paint(); hs.color = Color.parseColor("#2196F3"); hs.style = Paint.Style.STROKE; hs.strokeWidth = 2f / scaleFactor
        for ((cx, cy) in listOf(left to top, right to top, left to bottom, right to bottom)) { canvas.drawCircle(cx, cy, hr, hf); canvas.drawCircle(cx, cy, hr, hs) }
        val wp = ((right - left) / 3.7795f).toInt(); val hp = ((bottom - top) / 3.7795f).toInt()
        val lp = Paint(); lp.color = Color.WHITE; lp.textSize = 28f / scaleFactor; lp.isAntiAlias = true; lp.setShadowLayer(3f / scaleFactor, 0f, 0f, Color.BLACK)
        canvas.drawText("${wp}\u00d7${hp}mm", left + 8f / scaleFactor, top - 12f / scaleFactor, lp)
    }

    private fun drawAutoSelectOverlay(canvas: Canvas) {
        regionPath?.let { rp ->
            // AutoCAD convention: window select (left-to-right, or clockwise for lasso) = solid;
            // crossing select (right-to-left, or counterclockwise for lasso) = dashed. Lasso gets
            // its own purple family so it reads as a clearly different, third selection mode.
            val lassoClockwise = currentTool == Tool.LASSO && lassoPoints.size >= 3 && signedPolygonArea(lassoPoints) > 0f
            val solid = if (currentTool == Tool.LASSO) lassoClockwise else isWindowSelect
            val color = if (currentTool == Tool.LASSO) "#9C27B0" else if (isWindowSelect) "#2196F3" else "#4CAF50"
            val fillColorHex = if (currentTool == Tool.LASSO) "#339C27B0" else if (isWindowSelect) "#332196F3" else "#334CAF50"
            val fp = Paint(); fp.color = Color.parseColor(fillColorHex); fp.style = Paint.Style.FILL
            val sp = Paint(); sp.color = Color.parseColor(color); sp.style = Paint.Style.STROKE; sp.strokeWidth = 2f / scaleFactor
            if (!solid) sp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f / scaleFactor, 6f / scaleFactor), 0f)
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
            Tool.ERASER -> { p.color = if (eraserMode == EraserMode.OBJECT) Color.DKGRAY else Color.RED; p.style = Paint.Style.STROKE; p.strokeWidth = 2f; val half = eraserSize * scaleFactor / 2f; if (eraserMode == EraserMode.OBJECT) canvas.drawRect(hx - half, hy - half, hx + half, hy + half, p) else canvas.drawCircle(hx, hy, half, p) }
            else -> { p.color = Color.DKGRAY; p.style = Paint.Style.FILL; canvas.drawCircle(hx, hy, 5f, p) }
        }
    }

    fun pageWidthPx(): Float {
        if (canvasMode == CanvasMode.CONVENIENT) return if (convenientPageW > 0) convenientPageW else width.toFloat()
        val m = 3.7795f
        return if (pageOrientation == Orientation.PORTRAIT) paperSize.widthMM * m else paperSize.heightMM * m
    }

    fun pageHeightPx(): Float {
        // Convenient = one screen-height page (tall, comfortable reading/writing)
        if (canvasMode == CanvasMode.CONVENIENT) return if (convenientPageH > 0) convenientPageH else height.toFloat()
        val m = 3.7795f
        return if (pageOrientation == Orientation.PORTRAIT) paperSize.heightMM * m else paperSize.widthMM * m
    }

    // Convenient line spacing = 80px (large, comfortable)
    // Print line spacing = 40px (A4 realistic)
    private fun lineSpacingPx(): Float = if (canvasMode == CanvasMode.CONVENIENT) 80f else 40f
    private fun gridSpacingPx(): Float = if (canvasMode == CanvasMode.CONVENIENT) 80f else 40f
    private fun dotSpacingPx(): Float = if (canvasMode == CanvasMode.CONVENIENT) 80f else 40f

    private fun drawBackground(canvas: Canvas) {
        val vl = -translateX / scaleFactor; val vt = -translateY / scaleFactor
        val vr = vl + width / scaleFactor; val vb = vt + height / scaleFactor
        when (canvasMode) {
            CanvasMode.INFINITE -> {
                if (paperType == PaperType.BLANK_COLORED) { val p = Paint(); p.color = paperColor; canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, p) }
                else if (paperType != PaperType.BLANK) drawPaperPattern(canvas, vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f)
            }
            CanvasMode.FIXED -> {
                val pw = pageWidthPx(); val ph = pageHeightPx()
                val gp = Paint(); gp.color = Color.parseColor("#EDEAE3"); canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, gp)
                val wp = Paint(); wp.color = if (paperType == PaperType.BLANK_COLORED) paperColor else PAPER_BASE_COLOR; canvas.drawRect(0f, 0f, pw, ph, wp)
                if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) { canvas.save(); canvas.clipRect(0f, 0f, pw, ph); drawPaperPattern(canvas, 0f, 0f, pw, ph); canvas.restore() }
                val bp = Paint(); bp.color = Color.parseColor("#C8C0B0"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 1.5f / scaleFactor; canvas.drawRect(0f, 0f, pw, ph, bp)
            }
            CanvasMode.CONVENIENT -> {
                val pw = pageWidthPx(); val ph = pageHeightPx(); val gap = 24f
                val gp = Paint(); gp.color = Color.parseColor("#EDEAE3"); canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, gp)
                val wp = Paint(); wp.color = if (paperType == PaperType.BLANK_COLORED) paperColor else PAPER_BASE_COLOR
                val sp = Paint(); sp.color = Color.parseColor("#00000022"); sp.style = Paint.Style.FILL
                val period = ph + gap
                val si = (kotlin.math.floor(vt / period).toInt() - 1).coerceAtLeast(0)
                val ei = kotlin.math.ceil(vb / period).toInt() + 1
                for (i in si..ei) {
                    val top = i * period
                    canvas.drawRect(2f / scaleFactor, top + 2f / scaleFactor, pw + 2f / scaleFactor, top + ph + 2f / scaleFactor, sp)
                    canvas.drawRect(0f, top, pw, top + ph, wp)
                    if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) { canvas.save(); canvas.clipRect(0f, top, pw, top + ph); drawPaperPattern(canvas, 0f, top, pw, top + ph); canvas.restore() }
                }
            }
            CanvasMode.PAGINATED -> {
                val pw = pageWidthPx(); val ph = pageHeightPx(); val gap = 40f
                val gp = Paint(); gp.color = Color.parseColor("#EDEAE3"); canvas.drawRect(vl - 2000f, vt - 2000f, vr + 2000f, vb + 2000f, gp)
                val wp = Paint(); wp.color = if (paperType == PaperType.BLANK_COLORED) paperColor else PAPER_BASE_COLOR
                val bp = Paint(); bp.color = Color.parseColor("#C8C0B0"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 1.5f / scaleFactor
                val period = ph + gap
                val si = (kotlin.math.floor(vt / period).toInt() - 1).coerceAtLeast(0)
                val ei = kotlin.math.ceil(vb / period).toInt() + 1
                for (i in si..ei) {
                    val top = i * period; canvas.drawRect(0f, top, pw, top + ph, wp)
                    if (paperType != PaperType.BLANK && paperType != PaperType.BLANK_COLORED) { canvas.save(); canvas.clipRect(0f, top, pw, top + ph); drawPaperPattern(canvas, 0f, top, pw, top + ph); canvas.restore() }
                    canvas.drawRect(0f, top, pw, top + ph, bp)
                }
            }
        }
    }

    private fun drawPaperPattern(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val ls = lineSpacingPx(); val gs = gridSpacingPx(); val ds = dotSpacingPx()
        when (paperType) {
            PaperType.LINED -> {
                val p = Paint(); p.color = Color.parseColor("#C8D6F0"); p.strokeWidth = 1f
                var y = (top / ls).toInt() * ls; while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += ls }
            }
            PaperType.GRID -> {
                val p = Paint(); p.color = Color.parseColor("#D0D0D0"); p.strokeWidth = 1f
                var x = (left / gs).toInt() * gs; while (x < right) { canvas.drawLine(x, top, x, bottom, p); x += gs }
                var y = (top / gs).toInt() * gs; while (y < bottom) { canvas.drawLine(left, y, right, y, p); y += gs }
            }
            PaperType.DOTS -> {
                val p = Paint(); p.color = Color.parseColor("#B0B0B0"); p.style = Paint.Style.FILL
                var x = (left / ds).toInt() * ds; while (x < right) { var y = (top / ds).toInt() * ds; while (y < bottom) { canvas.drawCircle(x, y, 2.5f, p); y += ds }; x += ds }
            }
            PaperType.ENGINEERING -> {
                val ms = if (canvasMode == CanvasMode.CONVENIENT) 40f else 20f; val me = 5
                val mp = Paint(); mp.color = Color.parseColor("#E0E8F5"); mp.strokeWidth = 1f
                val Mp = Paint(); Mp.color = Color.parseColor("#A8C0E8"); Mp.strokeWidth = 1.5f
                var i = (left / ms).toInt(); var x = i * ms; while (x < right) { canvas.drawLine(x, top, x, bottom, if (i % me == 0) Mp else mp); i++; x = i * ms }
                var j = (top / ms).toInt(); var y = j * ms; while (y < bottom) { canvas.drawLine(left, y, right, y, if (j % me == 0) Mp else mp); j++; y = j * ms }
            }
            else -> {}
        }
    }

    fun screenToWorldX(x: Float): Float = (x - translateX) / scaleFactor
    fun screenToWorldY(y: Float): Float = (y - translateY) / scaleFactor
    fun screenCenterWorldX(): Float = screenToWorldX(width / 2f)
    fun screenCenterWorldY(): Float = screenToWorldY(height / 2f)
    fun getScaleFactor(): Float = scaleFactor
    fun scrollPage(direction: Int) {
        translateY -= direction * pageHeightPx() * scaleFactor
        clampTranslation(); onScaleChanged?.invoke(scaleFactor); onCanvasTransformed?.invoke(); invalidate()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hoverX = event.x; hoverY = event.y; invalidate() }
            MotionEvent.ACTION_HOVER_EXIT -> { hoverX = null; hoverY = null; invalidate() }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            requestUnbufferedDispatch(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) fillScrollGuard = false

        // fingerPanMode: single finger ALWAYS pans — block ALL other tools immediately
        if (fingerPanMode && event.pointerCount == 1) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { twoFingerLastX = event.x; twoFingerLastY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    translateX += event.x - twoFingerLastX; translateY += event.y - twoFingerLastY
                    twoFingerLastX = event.x; twoFingerLastY = event.y
                    clampTranslation()
                    val totalH = pageHeightPx() * scaleFactor
                    val maxScroll = totalH - height
                    if (maxScroll > 0f) onScrollPercentChanged?.invoke((-translateY / maxScroll).coerceIn(0f, 1f))
                    onCanvasTransformed?.invoke(); invalidate()
                }
                MotionEvent.ACTION_UP -> { twoFingerLastX = 0f; twoFingerLastY = 0f }
            }
            return true
        }
        if (event.pointerCount >= 2) {
            twoFingerActive = true
            // Cancel any in-progress stroke immediately when second finger touches
            if (currentItem != null) { currentItem = null; invalidate() }
            isStylusDown = false; drawingPointerId = -1
            activeHandle = HandleType.NONE
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }; longPressRunnable = null
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val fx = (event.getX(0) + event.getX(1)) / 2f; val fy = (event.getY(0) + event.getY(1)) / 2f
                    if (twoFingerLastX != 0f || twoFingerLastY != 0f) {
                        translateX += fx - twoFingerLastX; translateY += fy - twoFingerLastY
                        clampTranslation(); onScaleChanged?.invoke(scaleFactor); onCanvasTransformed?.invoke(); invalidate()
                        val maxScroll = (pageHeightPx() * estimatePageCount().coerceAtLeast(2) * scaleFactor - height).coerceAtLeast(1f)
                        onScrollPercentChanged?.invoke((-translateY / maxScroll).coerceIn(0f, 1f))
                    }
                    twoFingerLastX = fx; twoFingerLastY = fy
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    twoFingerLastX = 0f; twoFingerLastY = 0f
                }
            }
            return true
        }
        // Reset twoFingerActive only when ALL fingers are off the screen
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            twoFingerLastX = 0f; twoFingerLastY = 0f; twoFingerActive = false
        }
        // Block all drawing/gestures until fingers fully lifted after a two-finger gesture
        if (twoFingerActive) return true
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_UNKNOWN
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (isStylus) { isStylusDown = true; drawingPointerId = event.getPointerId(0) } else { if (isStylusDown) return true; drawingPointerId = event.getPointerId(0) } }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (isStylus) isStylusDown = false; drawingPointerId = -1 }
            MotionEvent.ACTION_MOVE -> { if (isStylusDown && isFinger) return true }
        }
        if (currentTool == Tool.DIMENSION) {
            // Block if finger-pan mode is on
            if (fingerPanMode) return true
            // If ANY second finger appears, cancel the current dimension-in-progress
            if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                dimPhase = DimPhase.IDLE; dimDraggingItem = null; invalidate(); return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dimFingerDown = true
                    val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
                    val HR = 80f
                    val hitDim = actions.filterIsInstance<DimensionItem>().firstOrNull { d ->
                        kotlin.math.hypot((event.x - d.handleP1sx), (event.y - d.handleP1sy)) < HR ||
                        kotlin.math.hypot((event.x - d.handleP2sx), (event.y - d.handleP2sy)) < HR ||
                        kotlin.math.hypot((event.x - d.handleMidsx), (event.y - d.handleMidsy)) < HR
                    }
                    if (hitDim != null) {
                        selectedItem = hitDim; dimDraggingItem = hitDim
                        dimDragHandle = when {
                            kotlin.math.hypot((event.x - hitDim.handleP1sx), (event.y - hitDim.handleP1sy)) < HR -> 1
                            kotlin.math.hypot((event.x - hitDim.handleP2sx), (event.y - hitDim.handleP2sy)) < HR -> 2
                            else -> 3
                        }
                        invalidate()
                    } else if (dimAngular) {
                        // Angular: slide-to-place. Points are confirmed on ACTION_UP, not DOWN.
                        // Order: tap1=arm1 point, tap2=VERTEX (intersection), tap3=arm2 point
                        // We don't commit on DOWN — we let user slide finger to exact position, commit on UP
                        when (dimAngPhase) {
                            // Phase 0: searching for point 1. DOWN = start dragging to find it
                            0 -> { dimCurWx=wx; dimCurWy=wy }
                            // Phase 1: point1 fixed. DOWN = start dragging to find point2 (vertex)
                            2 -> { dimCurWx=wx; dimCurWy=wy }
                            // Phase 2: vertex fixed. DOWN = start dragging to find point3
                            4 -> { dimCurWx=wx; dimCurWy=wy }
                        }
                        invalidate()
                    } else {
                        dimDraggingItem = null; selectedItem = null
                        when (dimPhase) {
                            DimPhase.IDLE -> {
                                // DOWN = start searching for point1, don't commit yet
                                dimCurWx = wx; dimCurWy = wy
                                dimPhase = DimPhase.FIRST_POINT
                            }
                            DimPhase.SECOND_POINT -> {
                                // DOWN = start searching for point2
                                dimCurWx = wx; dimCurWy = wy
                            }
                            else -> {}
                        }
                        invalidate()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
                    dimCurWx = wx; dimCurWy = wy  // always track for preview
                    if (dimDraggingItem != null) {
                        val d = dimDraggingItem!!
                        when (dimDragHandle) {
                            1 -> { d.x1 = wx; d.y1 = wy }
                            2 -> { d.x2 = wx; d.y2 = wy }
                            3 -> {
                                if (d.isAngular) {
                                    val parts2 = d.unit.split(",").toMutableList()
                                    while (parts2.size < 3) parts2.add("false")
                                    val p3x2 = parts2[0].toFloatOrNull() ?: d.x2
                                    val p3y2 = parts2[1].toFloatOrNull() ?: d.y2
                                    // Compute the bisector direction of the inner angle
                                    val a1r2 = kotlin.math.atan2((d.y2-d.y1).toDouble(),(d.x2-d.x1).toDouble()).toFloat()
                                    val a2r2 = kotlin.math.atan2((p3y2-d.y1).toDouble(),(p3x2-d.x1).toDouble()).toFloat()
                                    var sweep2 = (Math.toDegrees(a2r2.toDouble()) - Math.toDegrees(a1r2.toDouble())).toFloat()
                                    if (sweep2 > 180f) sweep2 -= 360f; if (sweep2 < -180f) sweep2 += 360f
                                    val midA2 = a1r2 + Math.toRadians(sweep2.toDouble()).toFloat()/2f
                                    // Inner bisector direction from vertex
                                    val innerBx = kotlin.math.cos(midA2); val innerBy = kotlin.math.sin(midA2)
                                    // Dot product of finger-from-vertex with inner bisector
                                    val dot = (wx - d.x1) * innerBx + (wy - d.y1) * innerBy
                                    // Negative dot = finger is on the OUTER side → supplementary
                                    parts2[2] = (dot < 0f).toString()
                                    d.unit = parts2.joinToString(",")
                                    // Arc radius = distance from vertex to finger
                                    d.offset = kotlin.math.hypot(wx - d.x1, wy - d.y1).coerceAtLeast(8f)
                                } else {
                                    val ddx = d.x2-d.x1; val ddy = d.y2-d.y1
                                    val dlen = kotlin.math.sqrt((ddx*ddx+ddy*ddy).toDouble()).toFloat()
                                    if (dlen > 0f) { val nx2 = -ddy/dlen; val ny2 = ddx/dlen; d.offset = (wx-d.x1)*nx2 + (wy-d.y1)*ny2 }
                                }
                            }
                        }
                        invalidate()
                    } else if (dimAngular && dimAngPhase >= 0) {
                        invalidate()  // always redraw for preview during any angular phase
                    } else if (dimPhase == DimPhase.SECOND_POINT) {
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    dimFingerDown = false
                    val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
                    if (dimDraggingItem != null) {
                        dimDraggingItem = null; redoStack.clear()
                    } else if (dimAngular) {
                        // Each UP commits the current sliding position as the confirmed point
                        when (dimAngPhase) {
                            0 -> { // UP after searching for point1 — commit arm1
                                dimP1wx=wx; dimP1wy=wy; dimAngPhase=2; invalidate()
                            }
                            2 -> { // UP after searching for vertex — commit vertex
                                dimP2wx=wx; dimP2wy=wy; dimAngPhase=4; invalidate()
                            }
                            4 -> { // UP after searching for point3 — finalize dimension
                                dimAngP3wx=wx; dimAngP3wy=wy; dimAngPhase=0
                                val vx2=dimP2wx; val vy2=dimP2wy
                                val a1r = kotlin.math.atan2((dimP1wy-vy2).toDouble(),(dimP1wx-vx2).toDouble())
                                val a2r = kotlin.math.atan2((dimAngP3wy-vy2).toDouble(),(dimAngP3wx-vx2).toDouble())
                                var sweepDeg = Math.toDegrees(a2r - a1r).toFloat()
                                if (sweepDeg > 180f) sweepDeg -= 360f; if (sweepDeg < -180f) sweepDeg += 360f
                                val deg = kotlin.math.abs(sweepDeg)
                                val newDim = DimensionItem(
                                    vx2, vy2, dimP1wx, dimP1wy, 0f,
                                    currentColor, currentStrokeWidth,
                                    mode = DimMode.AUTO, isAngular = true, angle = deg,
                                    fontSize = defaultDimFontSize, arrowSize = defaultDimArrowSize, textColor = currentColor
                                )
                                newDim.unit = "${dimAngP3wx},${dimAngP3wy},false"
                                actions.add(newDim); redoStack.clear(); invalidate()
                            }
                        }
                        invalidate()
                    } else if (dimPhase == DimPhase.FIRST_POINT) {
                        // First UP — commit point1, start searching for point2
                        dimP1wx = wx; dimP1wy = wy; dimCurWx = wx; dimCurWy = wy
                        dimPhase = DimPhase.SECOND_POINT; invalidate()
                    } else if (dimPhase == DimPhase.SECOND_POINT) {
                        // Linear: slide to final position, UP commits
                        dimP2wx = wx; dimP2wy = wy
                        val dist = kotlin.math.hypot((dimP2wx-dimP1wx), (dimP2wy-dimP1wy))
                        if (dist > 30f) {
                            val newDim = DimensionItem(
                                dimP1wx, dimP1wy, dimP2wx, dimP2wy, 0f,
                                currentColor, currentStrokeWidth, mode = dimMode,
                                fontSize = defaultDimFontSize, arrowSize = defaultDimArrowSize,
                                textColor = currentColor
                            )
                            if (dimMode == DimMode.AUTO) {
                                newDim.refLength = autoRefRealLen; newDim.unit = autoRefUnit
                            }
                            actions.add(newDim); redoStack.clear()
                            onDimensionCreated?.invoke(newDim)
                        }
                        dimPhase = DimPhase.IDLE; invalidate()
                    }
                }
            }
            return true
        }
        if (currentTool == Tool.TEXT || currentTool == Tool.FILL) {
            if (currentTool == Tool.TEXT) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val wx2 = screenToWorldX(event.x); val wy2 = screenToWorldY(event.y)
                        val hit = findTextItemAt(wx2, wy2)
                        textLongPressStartX = event.x; textLongPressStartY = event.y
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }; longPressRunnable = null
                        if (hit != null) {
                            val capturedHit = hit; val lx = event.x; val ly = event.y
                            longPressRunnable = Runnable {
                                longPressRunnable = null
                                selectedItem = capturedHit; invalidate()
                                // Post to run AFTER current touch sequence ends
                                // so the touchSurface isn't added mid-sequence
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    onTextSelectRequest?.invoke(capturedHit, lx, ly)
                                }, 50L)
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, 450L)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (longPressRunnable != null && (kotlin.math.abs(event.x - textLongPressStartX) > 10f || kotlin.math.abs(event.y - textLongPressStartY) > 10f)) {
                            longPressHandler.removeCallbacks(longPressRunnable!!); longPressRunnable = null
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }
                    }
                }
            }
            gestureDetector.onTouchEvent(event); return true
        }
        if (currentTool == Tool.SELECT) { gestureDetector.onTouchEvent(event); handleSelect(event); return true }
        if (currentTool == Tool.ARC) { handleArc(event); return true }
        if (currentTool == Tool.AUTOSELECT) { gestureDetector.onTouchEvent(event); handleAutoSelect(event); return true }
        if (currentTool == Tool.LASSO) { handleLasso(event); return true }
        if (currentTool == Tool.EXPORT_WINDOW) { handleExportWindow(event); return true }
        if (canvasMode == CanvasMode.INFINITE && currentItem == null && event.actionMasked == MotionEvent.ACTION_MOVE && isFinger) gestureDetector.onTouchEvent(event)
        handleDrawing(event); return true
    }

    private fun handleExportWindow(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { exportWindowStart = Pair(wx, wy); exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_MOVE -> { exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_UP -> {
                val s = exportWindowStart ?: return; val e = exportWindowEnd ?: return
                val left = minOf(s.first, e.first); val top = minOf(s.second, e.second); val right = maxOf(s.first, e.first); val bottom = maxOf(s.second, e.second)
                if (right - left > 20f && bottom - top > 20f) onExportWindowSelected?.invoke(left, top, right, bottom)
                exportWindowStart = null; exportWindowEnd = null; currentTool = Tool.SELECT; invalidate()
            }
        }
    }

    private fun clampToPage(wx: Float, wy: Float): Pair<Float, Float> {
        if (canvasMode == CanvasMode.INFINITE) return Pair(wx, wy)
        val pw = pageWidthPx(); val ph = pageHeightPx()
        val pageTop = if (canvasMode == CanvasMode.CONVENIENT || canvasMode == CanvasMode.PAGINATED) {
            val gap = if (canvasMode == CanvasMode.CONVENIENT) 24f else 40f
            val period = ph + gap
            kotlin.math.floor(wy / period) * period
        } else 0f
        val cx = wx.coerceIn(0f, pw)
        val cy = wy.coerceIn(pageTop, pageTop + ph)
        return Pair(cx, cy)
    }

    private fun handleDrawing(event: MotionEvent) {
        hoverX = event.x; hoverY = event.y
        var wx = screenToWorldX(event.x); var wy = screenToWorldY(event.y)
        if (currentTool == Tool.PEN || currentTool == Tool.HIGHLIGHTER || currentTool == Tool.BRUSH || SHAPE_TOOLS.contains(currentTool)) {
            val (cx, cy) = clampToPage(wx, wy); wx = cx; wy = cy
        }
        val pressure = event.pressure.coerceIn(0.3f, 1.5f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (currentTool == Tool.ERASER) { eraseAt(wx, wy); invalidate(); return }
                val data = when {
                    currentTool == Tool.PEN -> {
                        // Ball pen is strictly uniform - no pressure or speed sensitivity, per spec.
                        val baseW = if (currentPenStyle == PenStyle.BALL) currentStrokeWidth else currentStrokeWidth * pressure
                        StrokeData(Tool.PEN, mutableListOf(wx, wy), currentColor, baseW, false, rotation = 0f, penStyle = currentPenStyle, opacity = if (currentPenStyle == PenStyle.BALL) 255 else brushOpacity)
                    }
                    currentTool == Tool.HIGHLIGHTER -> StrokeData(Tool.HIGHLIGHTER, mutableListOf(wx, wy), currentColor, highlighterThickness, false, rotation = 0f, penStyle = PenStyle.MARKER, opacity = (highlighterOpacity * 255 / 100))
                    currentTool == Tool.BRUSH -> StrokeData(Tool.BRUSH, mutableListOf(wx, wy), currentColor, brushThickness * pressure, false, rotation = 0f, brushStyle = currentBrushStyle, opacity = brushOpacity)
                    SHAPE_TOOLS.contains(currentTool) -> StrokeData(currentTool, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, fillShapes)
                    else -> StrokeData(Tool.PEN, mutableListOf(wx, wy), currentColor, currentStrokeWidth * pressure, false, rotation = 0f, penStyle = currentPenStyle, opacity = brushOpacity)
                }
                if (currentTool == Tool.PEN && currentPenStyle == PenStyle.FOUNTAIN) data.widths.add(currentStrokeWidth)
                if (currentTool == Tool.PEN && currentPenStyle == PenStyle.PENCIL) data.widths.add(1f)
                if (currentTool == Tool.BRUSH && (currentBrushStyle == BrushStyle.INK || currentBrushStyle == BrushStyle.ROUND)) data.widths.add(brushThickness * pressure)
                lastMoveX = wx; lastMoveY = wy; lastMoveTime = event.eventTime
                currentItem = StrokeItem(data, data.buildPath(), data.toPaint()); invalidate()
                onDrawingStarted?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == Tool.ERASER) {
                    // Process historical eraser positions too for smooth erasing
                    for (h in 0 until event.historySize) { eraseAt(screenToWorldX(event.getHistoricalX(h)), screenToWorldY(event.getHistoricalY(h))) }
                    eraseAt(wx, wy); invalidate(); return
                }
                val item = currentItem ?: return
                if (currentTool == Tool.PEN || currentTool == Tool.HIGHLIGHTER || currentTool == Tool.BRUSH) {
                    // Process all historically batched points first for smooth, unbroken strokes
                    for (h in 0 until event.historySize) {
                        val hx = screenToWorldX(event.getHistoricalX(h)); val hy = screenToWorldY(event.getHistoricalY(h))
                        item.data.points.add(hx); item.data.points.add(hy)
                        if (currentTool == Tool.PEN && (currentPenStyle == PenStyle.FOUNTAIN || currentPenStyle == PenStyle.PENCIL)) {
                            val dt = (event.getHistoricalEventTime(h) - lastMoveTime).coerceAtLeast(1L)
                            val dist = distance(hx, hy, lastMoveX, lastMoveY)
                            val speed = dist / dt * 1000f
                            if (currentPenStyle == PenStyle.FOUNTAIN) {
                                val targetWidth = (currentStrokeWidth * (0.55f + (1f - (speed / 2200f).coerceIn(0f, 0.7f)) * 0.9f)).coerceIn(currentStrokeWidth * 0.4f, currentStrokeWidth * 1.8f)
                                item.data.widths.add((item.data.widths.lastOrNull() ?: currentStrokeWidth) * 0.8f + targetWidth * 0.2f)
                            } else {
                                val targetIntensity = (1f - (speed / 1800f).coerceIn(0f, 0.75f)).coerceIn(0.25f, 1f)
                                item.data.widths.add((item.data.widths.lastOrNull() ?: 1f) * 0.7f + targetIntensity * 0.3f)
                            }
                            lastMoveTime = event.getHistoricalEventTime(h); lastMoveX = hx; lastMoveY = hy
                        }
                        if (currentTool == Tool.BRUSH && (currentBrushStyle == BrushStyle.INK || currentBrushStyle == BrushStyle.ROUND)) {
                            val dt = (event.getHistoricalEventTime(h) - lastMoveTime).coerceAtLeast(1L)
                            val dist = distance(hx, hy, lastMoveX, lastMoveY)
                            val speed = dist / dt * 1000f
                            val targetWidth = (brushThickness * (0.5f + (1f - (speed / 2000f).coerceIn(0f, 0.65f)) * 1.0f)).coerceIn(brushThickness * 0.3f, brushThickness * 1.8f)
                            item.data.widths.add((item.data.widths.lastOrNull() ?: brushThickness) * 0.8f + targetWidth * 0.2f)
                            lastMoveTime = event.getHistoricalEventTime(h); lastMoveX = hx; lastMoveY = hy
                        }
                    }
                    item.data.points.add(wx); item.data.points.add(wy)
                    if (currentTool == Tool.PEN && (currentPenStyle == PenStyle.FOUNTAIN || currentPenStyle == PenStyle.PENCIL)) {
                        val dt = (event.eventTime - lastMoveTime).coerceAtLeast(1L)
                        val dist = distance(wx, wy, lastMoveX, lastMoveY)
                        val speed = dist / dt * 1000f
                        if (currentPenStyle == PenStyle.FOUNTAIN) {
                            val targetWidth = (currentStrokeWidth * (0.55f + (1f - (speed / 2200f).coerceIn(0f, 0.7f)) * 0.9f)).coerceIn(currentStrokeWidth * 0.4f, currentStrokeWidth * 1.8f)
                            // 0.8/0.2 heavy smoothing to eliminate digitizer jitter
                            item.data.widths.add((item.data.widths.lastOrNull() ?: currentStrokeWidth) * 0.8f + targetWidth * 0.2f)
                        } else {
                            val targetIntensity = (1f - (speed / 1800f).coerceIn(0f, 0.75f)).coerceIn(0.25f, 1f)
                            item.data.widths.add((item.data.widths.lastOrNull() ?: 1f) * 0.7f + targetIntensity * 0.3f)
                        }
                        lastMoveX = wx; lastMoveY = wy; lastMoveTime = event.eventTime
                    }
                    if (currentTool == Tool.BRUSH && currentBrushStyle == BrushStyle.INK) {
                        val dt = (event.eventTime - lastMoveTime).coerceAtLeast(1L)
                        val dist = distance(wx, wy, lastMoveX, lastMoveY)
                        val speed = dist / dt * 1000f
                        val targetWidth = (brushThickness * (0.5f + (1f - (speed / 2000f).coerceIn(0f, 0.65f)) * 1.0f)).coerceIn(brushThickness * 0.3f, brushThickness * 1.8f)
                        item.data.widths.add((item.data.widths.lastOrNull() ?: brushThickness) * 0.8f + targetWidth * 0.2f)
                        lastMoveX = wx; lastMoveY = wy; lastMoveTime = event.eventTime
                    }
                    if (currentTool == Tool.BRUSH && currentBrushStyle == BrushStyle.ROUND) {
                        val dt = (event.eventTime - lastMoveTime).coerceAtLeast(1L)
                        val dist = distance(wx, wy, lastMoveX, lastMoveY)
                        val speed = dist / dt * 1000f
                        val targetWidth = (brushThickness * (0.7f + (1f - (speed / 2000f).coerceIn(0f, 0.5f)) * 0.6f)).coerceIn(brushThickness * 0.5f, brushThickness * 1.4f)
                        item.data.widths.add((item.data.widths.lastOrNull() ?: brushThickness) * 0.8f + targetWidth * 0.2f)
                        lastMoveX = wx; lastMoveY = wy; lastMoveTime = event.eventTime
                    }
                }
                else if (SHAPE_TOOLS.contains(currentTool)) { item.data.points[2] = wx; item.data.points[3] = wy }
                item.path = item.data.buildPath(); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentItem?.let { item ->
                    // For shape/line tools, a tap with no meaningful drag is almost certainly an
                    // accidental touch (e.g. brushing the screen while reaching for a toolbar
                    // button) - committing it would silently leave an invisible degenerate
                    // zero-size shape on the canvas that's confusing to find and remove later.
                    // Pen/highlighter/brush strokes are NOT affected by this check (a single dot
                    // tap with the pen is a deliberate, visible mark the user can see and want).
                    val isShapeTool = SHAPE_TOOLS.contains(currentTool)
                    val tooShort = isShapeTool && item.data.points.size >= 4 &&
                        distance(item.data.points[0], item.data.points[1], item.data.points[2], item.data.points[3]) < (8f / scaleFactor)
                    if (!tooShort) { actions.add(item); redoStack.clear() }
                }
                currentItem = null; invalidate()
                onDrawingEnded?.invoke()
            }
        }
    }

    // Area-erases part of a closed/open SHAPE (triangle, rectangle, star, etc.) instead of
    // deleting the whole thing. Shapes are defined parametrically (just a couple of corner
    // points), not as a dense point list like a pen stroke, so this first samples the shape's
    // actual rendered outline via PathMeasure into a dense point list, then reuses the same
    // segment-splitting logic as splitStrokeAroundEraser. Each surviving fragment becomes a new
    // open freeform (PEN-type) stroke with the shape's original color/width/opacity, since a
    // "triangle with a bite taken out" isn't a triangle anymore - it's an open polyline.
    private fun splitShapeAroundEraser(data: StrokeData, ex: Float, ey: Float, r: Float): List<StrokeItem> {
        val path = data.buildPath()
        val measure = android.graphics.PathMeasure(path, false)
        val allPts = mutableListOf<Float>()
        val pos = FloatArray(2)
        do {
            val len = measure.length
            if (len <= 0f) continue
            var dist = 0f
            val step = (len / 48f).coerceAtLeast(1.5f)
            while (dist <= len) {
                measure.getPosTan(dist, pos, null)
                allPts.add(pos[0]); allPts.add(pos[1])
                dist += step
            }
        } while (measure.nextContour())
        if (allPts.size < 4) return emptyList()

        val segs = mutableListOf<MutableList<Float>>(); var cur = mutableListOf<Float>()
        fun flush() { if (cur.size >= 4) segs.add(cur); cur = mutableListOf() }
        var i = 0
        var prevIn = distance(ex, ey, allPts[0], allPts[1]) <= r
        if (!prevIn) { cur.add(allPts[0]); cur.add(allPts[1]) }
        while (i + 3 < allPts.size) {
            val x1 = allPts[i]; val y1 = allPts[i + 1]; val x2 = allPts[i + 2]; val y2 = allPts[i + 3]
            val segDist = distToSeg(ex, ey, x1, y1, x2, y2)
            val curIn = segDist <= r
            if (curIn != prevIn) {
                val cut = findCircleSegIntersection(ex, ey, r, x1, y1, x2, y2)
                if (cut != null) {
                    if (!prevIn) { cur.add(cut.first); cur.add(cut.second); flush() }
                    else { cur.add(cut.first); cur.add(cut.second) }
                } else flush()
            }
            if (!curIn) { cur.add(x2); cur.add(y2) }
            prevIn = curIn
            i += 2
        }
        flush()
        return segs.map { sp ->
            // Fragments render as plain freeform pen strokes (no fill, original stroke color/width)
            val d = StrokeData(Tool.PEN, sp, data.color, data.strokeWidth, false, penStyle = PenStyle.FOUNTAIN, opacity = data.opacity)
            StrokeItem(d, d.buildPath(), d.toPaint())
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserSize / 2f
        if (eraserMode == EraserMode.OBJECT) {
            val it = actions.iterator()
            while (it.hasNext()) {
                val a = it.next()
                val hit = when (a) {
                    is StrokeItem -> strokeHitTest(a.data, x, y, r); is TextItem -> distance(x, y, a.x, a.y) <= r + a.size
                    is ImageItem -> distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) <= r + maxOf(a.w, a.h) / 2f
                    is FillItem -> distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) <= r + maxOf(a.w, a.h) / 2f
                    is AudioItem -> distance(x, y, a.x, a.y) <= r + a.radius; else -> false
                }
                if (hit) it.remove()
            }
        } else {
            val newActions = mutableListOf<Any>()
            for (a in actions) {
                when (a) {
                    is StrokeItem -> {
                        if (a.data.type == Tool.PEN || a.data.type == Tool.ERASER || a.data.type == Tool.ARC || a.data.type == Tool.HIGHLIGHTER || a.data.type == Tool.BRUSH) {
                            newActions.addAll(splitStrokeAroundEraser(a.data, x, y, r))
                        } else {
                            // Shapes: only touch them if the eraser circle actually overlaps the
                            // outline (checked first as a cheap early-out), then partially erase
                            // just the touched portion instead of deleting the whole shape.
                            if (strokeHitTest(a.data, x, y, r)) newActions.addAll(splitShapeAroundEraser(a.data, x, y, r))
                            else newActions.add(a)
                        }
                    }
                    is TextItem -> { if (distance(x, y, a.x, a.y) > r + a.size) newActions.add(a) }
                    is ImageItem -> { if (distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) > r + maxOf(a.w, a.h) / 2f) newActions.add(a) }
                    is FillItem -> {
                        if (eraserMode == EraserMode.AREA) {
                            // Erase only the pixels the eraser circle touches in the fill bitmap
                            val erased = eraseFillItemRegion(a, x, y, r)
                            if (erased != null) newActions.add(erased) // null = fully erased
                        } else {
                            newActions.add(a)
                        }
                    }
                    else -> newActions.add(a)
                }
            }
            actions.clear(); actions.addAll(newActions)
        }
    }

    private fun eraseFillItemRegion(item: FillItem, ex: Float, ey: Float, r: Float): FillItem? {
        val bmp = getOrLoadFillBitmap(item)?.copy(Bitmap.Config.ARGB_8888, true) ?: return item
        val bw = bmp.width; val bh = bmp.height
        // Convert world eraser circle to bitmap pixel coords
        val scaleX = bw / item.w; val scaleY = bh / item.h
        val bex = ((ex - item.x) * scaleX).toInt(); val bey = ((ey - item.y) * scaleY).toInt()
        val brx = (r * scaleX).toInt().coerceAtLeast(1); val bry = (r * scaleY).toInt().coerceAtLeast(1)
        val cv = Canvas(bmp)
        val p = Paint(); p.color = Color.TRANSPARENT; p.style = Paint.Style.FILL
        p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        cv.drawOval(RectF((bex - brx).toFloat(), (bey - bry).toFloat(), (bex + brx).toFloat(), (bey + bry).toFloat()), p)
        // Check if anything remains
        val pixels = IntArray(bw * bh); bmp.getPixels(pixels, 0, bw, 0, 0, bw, bh)
        if (pixels.all { (it ushr 24) == 0 }) return null // fully transparent - remove item
        // Save updated bitmap back to file
        try { FileOutputStream(item.path).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { return item }
        synchronized(bitmapCache) { bitmapCache.remove(item.path) } // invalidate so it reloads
        return item
    }
    // sample-point distance), so erasing is accurate to what's visually under the eraser circle
    // regardless of how sparse the stroke's recorded points are.
    private fun splitStrokeAroundEraser(data: StrokeData, ex: Float, ey: Float, r: Float): List<StrokeItem> {
        val pts = data.points
        if (pts.size < 4) { if (pts.size >= 2 && distance(ex, ey, pts[0], pts[1]) <= r) return emptyList(); return listOf(StrokeItem(data, data.buildPath(), data.toPaint())) }

        // Walk each segment; if the eraser circle intersects it, cut precisely at the circle
        // boundary (inserting the intersection point) instead of dropping the whole segment.
        val segs = mutableListOf<MutableList<Float>>(); var cur = mutableListOf<Float>()
        fun flush() { if (cur.size >= 4) segs.add(cur); cur = mutableListOf() }

        var i = 0
        var prevIn = distance(ex, ey, pts[0], pts[1]) <= r
        if (!prevIn) { cur.add(pts[0]); cur.add(pts[1]) }
        while (i + 3 < pts.size) {
            val x1 = pts[i]; val y1 = pts[i + 1]; val x2 = pts[i + 2]; val y2 = pts[i + 3]
            val segDist = distToSeg(ex, ey, x1, y1, x2, y2)
            val curIn = segDist <= r
            if (curIn != prevIn) {
                // Find the point along this segment where it crosses the eraser circle boundary
                // and use that as the cut point so erasing follows the actual visual edge.
                val cut = findCircleSegIntersection(ex, ey, r, x1, y1, x2, y2)
                if (cut != null) {
                    if (!prevIn) { cur.add(cut.first); cur.add(cut.second); flush() }
                    else { cur.add(cut.first); cur.add(cut.second) }
                } else flush()
            }
            if (!curIn) { cur.add(x2); cur.add(y2) }
            prevIn = curIn
            i += 2
        }
        flush()
        return segs.map { sp -> val d = StrokeData(data.type, sp, data.color, data.strokeWidth, data.fill, penStyle = data.penStyle, opacity = data.opacity, brushStyle = data.brushStyle); StrokeItem(d, d.buildPath(), d.toPaint()) }
    }

    // Finds where a line segment crosses a circle boundary (used to cut strokes precisely at the
    // eraser's visual edge rather than at the nearest sample point).
    private fun findCircleSegIntersection(cx: Float, cy: Float, r: Float, x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float>? {
        val dx = x2 - x1; val dy = y2 - y1
        val fx = x1 - cx; val fy = y1 - cy
        val a = dx * dx + dy * dy
        if (a < 0.0001f) return null
        val b = 2f * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - r * r
        val disc = b * b - 4f * a * c
        if (disc < 0f) return null
        val sq = kotlin.math.sqrt(disc)
        val t1 = (-b - sq) / (2f * a); val t2 = (-b + sq) / (2f * a)
        val t = if (t1 in 0f..1f) t1 else if (t2 in 0f..1f) t2 else return null
        return Pair(x1 + t * dx, y1 + t * dy)
    }

    // True distance from a point to a closed/open shape's outline (not its inflated bounding box).
    // Used by area-mode erasing so touching empty space near a shape's bbox corner doesn't delete
    // the whole shape - only actually touching its drawn line does.
    private fun distanceToShapeOutline(data: StrokeData, x: Float, y: Float): Float {
        val path = data.buildPath()
        val measure = android.graphics.PathMeasure(path, false)
        var minDist = Float.MAX_VALUE
        val pos = FloatArray(2)
        do {
            val len = measure.length
            if (len <= 0f) continue
            var dist = 0f
            val step = (len / 24f).coerceAtLeast(2f)
            while (dist <= len) {
                measure.getPosTan(dist, pos, null)
                val d = distance(x, y, pos[0], pos[1])
                if (d < minDist) minDist = d
                dist += step
            }
        } while (measure.nextContour())
        return minDist
    }

    private fun strokeHitTest(data: StrokeData, x: Float, y: Float, r: Float): Boolean {
        if (data.type == Tool.PEN || data.type == Tool.ERASER || data.type == Tool.ARC || data.type == Tool.HIGHLIGHTER || data.type == Tool.BRUSH) {
            // Account for the stroke's own width so the eraser circle has to actually overlap the
            // VISIBLE ink (not just the invisible centerline) - matters for thick marker/brush/
            // highlighter strokes where the painted area extends well past the recorded points.
            val effectiveR = r + (data.strokeWidth / 2f).coerceAtMost(r * 2f)
            if (data.points.size == 2) return distance(x, y, data.points[0], data.points[1]) <= effectiveR
            var i = 0; while (i + 3 < data.points.size) { if (distToSeg(x, y, data.points[i], data.points[i + 1], data.points[i + 2], data.points[i + 3]) <= effectiveR) return true; i += 2 }; return false
        } else {
            // Shapes: test against the actual outline, not an inflated bounding box, so the
            // eraser only triggers when it genuinely touches the drawn line - this is what makes
            // area-mode erasing on shapes behave like area erasing instead of object erasing.
            if (data.points.size < 4) return false
            val effectiveR = r + (data.strokeWidth / 2f)
            return distanceToShapeOutline(data, x, y) <= effectiveR
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    private fun distToSeg(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0f && dy == 0f) return distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return distance(px, py, x1 + t * dx, y1 + t * dy)
    }

    fun addText(text: String, x: Float, y: Float, size: Float, rotation: Float, color: Int, spans: MutableList<TextSpanData> = mutableListOf(), fontFamily: String = "sans-serif", opacity: Int = 255) {
        if (text.isBlank()) return
        val (cx, cy) = if (canvasMode != CanvasMode.INFINITE) clampToPage(x, y) else Pair(x, y)
        val item = TextItem(text, cx, cy, color, size, rotation); item.spans = spans; item.fontFamily = fontFamily; item.opacity = opacity
        actions.add(item); redoStack.clear(); invalidate()
    }

    // Inserts a fully pre-built TextItem (used for link creation, where linkTarget is already
    // set on the item before insertion) - clamps position to the page like addText does.
    fun addLinkText(item: TextItem) {
        if (item.text.isBlank()) return
        val (cx, cy) = if (canvasMode != CanvasMode.INFINITE) clampToPage(item.x, item.y) else Pair(item.x, item.y)
        item.x = cx; item.y = cy
        actions.add(item); redoStack.clear(); invalidate()
    }

    fun removeTextItem(item: TextItem) { actions.remove(item); invalidate() }

    fun bringToFront(item: Any) {
        if (actions.remove(item)) { actions.add(item); redoStack.clear(); invalidate() }
    }

    /** Renders only pen strokes currently visible on screen — used for inline handwriting OCR */
    fun renderVisibleStrokesOnly(): Bitmap? {
        if (width == 0 || height == 0) return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.drawColor(Color.WHITE)
        cv.save(); cv.translate(translateX, translateY); cv.scale(scaleFactor, scaleFactor)
        for (a in actions) {
            if (a is StrokeItem && a.data.type == Tool.PEN) cv.drawPath(a.path, a.paint)
        }
        cv.restore()
        return bmp
    }

    /** Removes all pen strokes from actions — called after handwriting OCR converts them to text */
    fun clearRecentPenStrokes() {
        actions.removeAll { it is StrokeItem && it.data.type == Tool.PEN }
        redoStack.clear(); invalidate()
    }    fun sendToBack(item: Any) {
        if (actions.remove(item)) { actions.add(0, item); redoStack.clear(); invalidate() }
    }
    fun bringForward(item: Any) {
        val i = actions.indexOf(item); if (i >= 0 && i < actions.size - 1) { actions.removeAt(i); actions.add(i + 1, item); redoStack.clear(); invalidate() }
    }
    fun sendBackward(item: Any) {
        val i = actions.indexOf(item); if (i > 0) { actions.removeAt(i); actions.add(i - 1, item); redoStack.clear(); invalidate() }
    }

    // Clamps an existing text item's position to the page boundary (used when committing edits)
    fun clampTextItemToPage(item: TextItem) {
        if (canvasMode == CanvasMode.INFINITE) return
        val pw = pageWidthPx()
        item.x = item.x.coerceIn(8f, pw - 40f)
        if (item.maxWidth <= 0f || item.maxWidth > pw) item.maxWidth = (pw - item.x - 16f).coerceAtLeast(80f)
    }

    fun addImage(path: String, wx: Float, wy: Float, w: Float, h: Float) {
        val item = ImageItem(path, wx - w / 2f, wy - h / 2f, w, h, 0f)
        actions.add(item); redoStack.clear()
        loadBitmapAsync(path) { bmp -> item.bitmap = bmp; item.loading = false; invalidate() }
        invalidate()
    }

    fun addAudioItem(filePath: String, title: String, durationMs: Long) {
        val item = AudioItem(filePath, title, screenCenterWorldX(), screenCenterWorldY(), durationMs)
        actions.add(item); redoStack.clear(); invalidate()
    }

    fun addTable(rows: Int, cols: Int, wx: Float, wy: Float, screenWidth: Float) {
        val table = TableItem(wx, wy); table.rows = rows; table.cols = cols
        val cellW = (screenWidth / scaleFactor / 2f) / cols; val cellH = 60f
        table.rowHeights.clear(); repeat(rows) { table.rowHeights.add(cellH) }
        table.colWidths.clear(); repeat(cols) { table.colWidths.add(cellW) }
        for (r in 0 until rows) for (c in 0 until cols) table.getCellPublic(r, c)
        actions.add(table); redoStack.clear(); activeTableItem = table; invalidate()
    }

    fun migrateOldNotes(filesDir: File) {
        val oldFolder = File(filesDir, "drawings"); if (!oldFolder.exists()) return
        val newFolder = File(File(filesDir, "books"), "General"); if (!newFolder.exists()) newFolder.mkdirs()
        oldFolder.listFiles()?.filter { it.extension == "eng" }?.forEach { file -> val dest = File(newFolder, file.name); if (!dest.exists()) file.copyTo(dest) }
    }

    fun exportWindow(left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val tmpBmp = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888); draw(Canvas(tmpBmp))
        val sx = worldToScreenX(left).toInt().coerceAtLeast(0); val sy = worldToScreenY(top).toInt().coerceAtLeast(0)
        val ex = worldToScreenX(right).toInt().coerceAtMost(this.width); val ey = worldToScreenY(bottom).toInt().coerceAtMost(this.height)
        val cw = (ex - sx).coerceAtLeast(1); val ch = (ey - sy).coerceAtLeast(1)
        return Bitmap.createBitmap(tmpBmp, sx, sy, cw, ch)
    }

    fun undo() {
        if (actions.isEmpty()) return
        val last = actions.removeAt(actions.size - 1)
        if (last is FillToggleAction) {
            last.item.data.fill = last.wasFilled; last.item.data.fillColorVal = last.wasColor; last.item.paint = last.item.data.toPaint()
        }
        redoStack.add(last); invalidate()
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.size - 1)
        if (next is FillToggleAction) {
            next.item.data.fill = !next.wasFilled; next.item.data.fillColorVal = next.newColor; next.item.paint = next.item.data.toPaint()
        }
        actions.add(next); invalidate()
    }
    fun clearAll() { actions.clear(); redoStack.clear(); selectedItem = null; activeTableItem = null; invalidate() }
    fun hasContent(): Boolean = actions.isNotEmpty()
    fun exportBitmap(): Bitmap { val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); draw(Canvas(bmp)); return bmp }

    fun renderStrokesOnly(scale: Float): Bitmap {
        val w = (width * scale).toInt().coerceAtLeast(1); val h = (height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val canvas = Canvas(bmp)
        canvas.save(); canvas.scale(scale, scale); canvas.translate(translateX, translateY); canvas.scale(scaleFactor, scaleFactor)
        for (a in actions) drawActionItem(canvas, a, false); canvas.restore(); return bmp
    }

    fun zoomTo(wx: Float, wy: Float, scale: Float) {
        scaleFactor = scale.coerceIn(0.2f, 6f); translateX = width / 2f - wx * scaleFactor; translateY = height / 2f - wy * scaleFactor; clampTranslation(); invalidate()
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
        val scale = 2.0f
        val w = (width * scale).toInt().coerceAtLeast(1); val h = (height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
        cv.save(); cv.scale(scale, scale); cv.translate(translateX, translateY); cv.scale(scaleFactor, scaleFactor)
        for (a in actions) {
            if (a is StrokeItem) {
                val thickPaint = Paint(a.paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeWidth.coerceAtLeast(4f / scaleFactor)
                    alpha = 255; isAntiAlias = false; shader = null; pathEffect = null
                }
                if (a.data.rotation != 0f) {
                    val b = getBounds(a)
                    if (b != null) { val cx=(b[0]+b[2])/2f; val cy=(b[1]+b[3])/2f; cv.save(); cv.rotate(a.data.rotation,cx,cy); cv.drawPath(a.path,thickPaint); cv.restore() }
                    else cv.drawPath(a.path, thickPaint)
                } else cv.drawPath(a.path, thickPaint)
            }
        }
        cv.restore()

        val px = (screenX * scale).toInt().coerceIn(0, w - 1); val py = (screenY * scale).toInt().coerceIn(0, h - 1)
        val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        fun isWall(x: Int, y: Int): Boolean = ((pixels[y * w + x] ushr 24) and 0xFF) > 25
        if (isWall(px, py)) { invalidate(); return }

        imageLoadExecutor.execute {
            val visited = BooleanArray(w * h); val queue = ArrayDeque<Int>()
            val start = py * w + px; queue.add(start); visited[start] = true
            var minX = px; var maxX = px; var minY = py; var maxY = py
            var filled = 0; val edgeHits = mutableListOf<Pair<Int, Int>>()
            val maxFill = (w * h * 0.85f).toInt(); var leaked = false
            while (queue.isNotEmpty()) {
                val idx = queue.removeFirst(); val x = idx % w; val y = idx / w; filled++
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) edgeHits.add(Pair(x, y))
                if (filled > maxFill) { leaked = true; break }
                if (x > 0)     { val n = idx - 1; if (!visited[n] && !isWall(x-1,y)) { visited[n]=true; queue.add(n) } }
                if (x < w - 1) { val n = idx + 1; if (!visited[n] && !isWall(x+1,y)) { visited[n]=true; queue.add(n) } }
                if (y > 0)     { val n = idx - w; if (!visited[n] && !isWall(x,y-1)) { visited[n]=true; queue.add(n) } }
                if (y < h - 1) { val n = idx + w; if (!visited[n] && !isWall(x,y+1)) { visited[n]=true; queue.add(n) } }
            }
            if (leaked) {
                val clusters = clusterEdgePoints(edgeHits)
                post { if (clusters.isNotEmpty()) { val (cx, cy) = clusters[0]; zoomTo(screenToWorldX(cx/scale), screenToWorldY(cy/scale), (scaleFactor*2.5f).coerceAtMost(6f)) }; invalidate() }
            } else {
                val cw = maxX - minX + 1; val ch = maxY - minY + 1
                val fp = IntArray(cw * ch) { i -> val gx=minX+i%cw; val gy=minY+i/cw; if(visited[gy*w+gx]) fillColor else Color.TRANSPARENT }
                val fb = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888); fb.setPixels(fp, 0, cw, 0, 0, cw, ch)
                val folder = File(ctx.filesDir, "images"); if (!folder.exists()) folder.mkdirs()
                val outFile = File(folder, "fill_${System.currentTimeMillis()}.png")
                try { FileOutputStream(outFile).use { fb.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { post { invalidate() }; return@execute }
                val wx0 = screenToWorldX(minX / scale); val wy0 = screenToWorldY(minY / scale)
                val wx1 = screenToWorldX((minX + cw) / scale); val wy1 = screenToWorldY((minY + ch) / scale)
                post {
                    val fi = FillItem(outFile.absolutePath, wx0, wy0, wx1 - wx0, wy1 - wy0)
                    if (pendingHatchPattern != null) { fi.hatchPattern = pendingHatchPattern; fi.hatchColor = pendingHatchColor }
                    // pendingHatchPattern stays set so repeated taps keep using the same hatch
                    // Pre-attach the bitmap so it draws immediately without async blink
                    fi.bitmap = fb
                    // Remove any existing FillItem that covers the same tap point (same area)
                    actions.removeAll { existing ->
                        existing is FillItem &&
                        existing.x <= wx0 + fi.w * 0.5f && existing.x + existing.w >= wx0 + fi.w * 0.5f &&
                        existing.y <= wy0 + fi.h * 0.5f && existing.y + existing.h >= wy0 + fi.h * 0.5f
                    }
                    actions.add(fi); redoStack.clear(); invalidate()
                }
            }
        }
    }

    // ── Serialize / Deserialize ─────────────────────────────────────

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("META\u0001${paperType.name}\u0001${canvasMode.name}\u0001${paperSize.name}\u0001${pageOrientation.name}\u0001$paperColor\n")
        for (a in actions) when (a) {
            is TableItem -> sb.append(a.serialize())
            is StrokeItem -> sb.append("${a.data.type.name}|${a.data.color}|${a.data.strokeWidth}|${a.data.fill}|${a.data.rotation}|${a.data.points.joinToString(",")}|${a.data.fillColorVal}|${a.data.penStyle.name}|${a.data.opacity}|${a.data.brushStyle.name}|${a.data.widths.joinToString(",")}\n")
            is TextItem -> sb.append("TEXT\u0001${a.x}\u0001${a.y}\u0001${a.color}\u0001${a.size}\u0001${a.rotation}\u0001${a.spans.joinToString(";") { "${it.start},${it.end},${it.type},${it.value}" }}\u0001${a.text.replace("\n", "\u0002")}\u0001${a.maxWidth}\u0001${a.fontFamily}\u0001${a.opacity}\u0001${a.linkTarget ?: ""}\n")
            is ImageItem -> sb.append("IMAGE\u0001${a.path}\u0001${a.x}\u0001${a.y}\u0001${a.w}\u0001${a.h}\u0001${a.rotation}\n")
            is FillItem -> sb.append("FILL\u0001${a.path}\u0001${a.x}\u0001${a.y}\u0001${a.w}\u0001${a.h}\n")
            is AudioItem -> sb.append("AUDIO\u0001${a.filePath}\u0001${a.title.replace("\u0001","_")}\u0001${a.x}\u0001${a.y}\u0001${a.durationMs}\u0001${a.radius}\n")
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        actions.clear(); redoStack.clear(); selectedItem = null; activeTableItem = null
        val lines = content.lines(); var i = 0
        while (i < lines.size) {
            val line = lines[i]; if (line.isBlank()) { i++; continue }
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
                    line.startsWith("AUDIO\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 6) {
                            val rad = if (p.size >= 7) p[6].toFloatOrNull() ?: 48f else 48f
                            actions.add(AudioItem(p[1], p[2], p[3].toFloat(), p[4].toFloat(), p[5].toLongOrNull() ?: 0L, rad))
                        }
                        i++
                    }
                    line.startsWith("TEXT\u0001") -> {
                        val p = line.split("\u0001"); if (p.size >= 7) {
                            val item = TextItem("", p[1].toFloat(), p[2].toFloat(), p[3].toInt(), p[4].toFloat(), p[5].toFloat())
                            // Detect old format: p[6] is "true"/"false" (bold flag), p[7] is "true"/"false" (italic flag)
                            // New format: p[6] is spans string (e.g. "0,5,S,1" or blank), p[7] is text
                            val isOldFormat = p.size >= 9 && (p[6] == "true" || p[6] == "false") && (p[7] == "true" || p[7] == "false")
                            if (isOldFormat) {
                                val bold = p[6].toBoolean(); val italic = p[7].toBoolean()
                                item.text = p[8].replace("\u0002", "\n")
                                val style = if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else -1
                                if (style >= 0) item.spans.add(TextSpanData(0, item.text.length, 'S', style))
                            } else {
                                if (p[6].isNotBlank()) for (t in p[6].split(";")) { val sp = t.split(","); if (sp.size == 4) item.spans.add(TextSpanData(sp[0].toInt(), sp[1].toInt(), sp[2][0], sp[3].toInt())) }
                                item.text = if (p.size > 7) p[7].replace("\u0002", "\n") else ""
                            }
                            if (p.size >= 10) item.maxWidth = p[9].toFloatOrNull() ?: 0f
                            if (p.size >= 11) item.fontFamily = p[10]
                            if (p.size >= 12) item.opacity = p[11].toIntOrNull() ?: 255
                            if (p.size >= 13 && p[12].isNotBlank()) item.linkTarget = p[12]
                            actions.add(item)
                        }
                        i++
                    }
                    line.startsWith("IMAGE\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 7) {
                            val item = ImageItem(p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat())
                            actions.add(item); loadBitmapAsync(p[1]) { bmp -> item.bitmap = bmp; item.loading = false; invalidate() }
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
                                val fcv = if (p.size >= 7) p[6].toIntOrNull() ?: color else color
                                val pStyle = if (p.size >= 8) try { PenStyle.valueOf(p[7]) } catch (e: Exception) { PenStyle.FOUNTAIN } else PenStyle.FOUNTAIN
                                val opac = if (p.size >= 9) p[8].toIntOrNull() ?: 255 else 255
                                val bStyle = if (p.size >= 10) try { BrushStyle.valueOf(p[9]) } catch (e: Exception) { BrushStyle.ROUND } else BrushStyle.ROUND
                                val wArr = if (p.size >= 11 && p[10].isNotBlank()) p[10].split(",").mapNotNull { it.toFloatOrNull() }.toMutableList() else mutableListOf()
                                val d = StrokeData(type, pts, color, sw, fill, rot, fcv, pStyle, opac, bStyle, wArr); actions.add(StrokeItem(d, d.buildPath(), d.toPaint()))
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
