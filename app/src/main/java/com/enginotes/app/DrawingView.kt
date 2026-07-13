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
enum class LineType(val label: String, val intervals: FloatArray?, val cap: android.graphics.Paint.Cap = android.graphics.Paint.Cap.BUTT) {
    CONTINUOUS      ("Continuous",          null),
    DASHED          ("Dashed",              floatArrayOf(12f, 6f)),
    DOTTED          ("Dotted",              floatArrayOf(1f, 4f),  android.graphics.Paint.Cap.ROUND),
    DASH_DOT        ("Dash Dot",            floatArrayOf(12f, 4f, 1f, 4f)),
    DASH_DOT_DOT    ("Dash Dot Dot",        floatArrayOf(12f, 4f, 1f, 4f, 1f, 4f)),
    LONG_DASH       ("Long Dash",           floatArrayOf(24f, 6f)),
    LONG_DASH_DOT   ("Long Dash Dot",       floatArrayOf(24f, 4f, 1f, 4f)),
    LONG_DASH_2DOT  ("Long Dash 2 Dot",     floatArrayOf(24f, 4f, 1f, 4f, 1f, 4f)),
    SHORT_DASH      ("Short Dash",          floatArrayOf(4f, 4f)),
    FINE_DASH_DOT   ("Fine Dash Dot",       floatArrayOf(8f, 3f, 1f, 3f)),
    STITCH          ("Stitch Line",         floatArrayOf(6f, 6f)),
    PHANTOM         ("Phantom",             floatArrayOf(20f, 4f, 4f, 4f, 4f, 4f)),
    SPARSE_DOT      ("Sparse Dot",          floatArrayOf(1f, 8f),  android.graphics.Paint.Cap.ROUND),
    DENSE_DOT       ("Dense Dot",           floatArrayOf(1f, 2f),  android.graphics.Paint.Cap.ROUND),
    DOUBLE_DASH     ("Double Dash",         floatArrayOf(8f, 4f, 8f, 10f)),
    VERY_LONG_DASH  ("Extra Long Dash",     floatArrayOf(36f, 6f)),
    DASH_3DOT       ("Dash 3 Dot",          floatArrayOf(12f, 3f, 1f, 3f, 1f, 3f, 1f, 3f)),
    FENCE           ("Fence Line",          floatArrayOf(2f, 6f, 2f, 6f)),
    TRACK           ("Track / Rail",        floatArrayOf(16f, 2f, 1f, 2f, 1f, 2f)),
    IRREGULAR_DASH  ("Irregular Dash",      floatArrayOf(10f, 3f, 6f, 3f, 14f, 3f)),
    ULTRA_FINE      ("Ultra Fine Dot",      floatArrayOf(1f, 12f), android.graphics.Paint.Cap.ROUND),
    SECTION_PLANE   ("Section Plane",       floatArrayOf(20f, 4f, 1f, 4f, 1f, 4f, 20f, 4f)),
}
enum class BrushStyle {
    ROUND, INK, WATERCOLOR, CRAYON, CHARCOAL, NEON, DRY_BRUSH, SPRAY, FIRE, GRASS
}
enum class EraserShape { ROUND, SQUARE }
enum class InputMode { AUTO, STYLUS_ONLY, FINGER_ONLY }

enum class Tool {
    SELECT, FILL, PEN, BRUSH, ERASER, HIGHLIGHTER, LINE, RECTANGLE, ROUNDED_RECT, CIRCLE, ELLIPSE,
    TRIANGLE, DIAMOND, ARROW, STAR, PENTAGON, HEXAGON, CURVE, CROSS, ARC, TEXT, AUTOSELECT, LASSO, MULTISELECT, POLYLINE, EXPORT_WINDOW, OCR_SNIP,
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
val EXPLICIT_VERTEX_SHAPES = setOf(
    Tool.LINE, Tool.ARROW, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE,
    Tool.TRIANGLE, Tool.ISOSCELES_TRIANGLE, Tool.TRIANGLE_DOWN, Tool.RIGHT_TRIANGLE, Tool.DIAMOND,
    Tool.TRAPEZOID, Tool.PARALLELOGRAM, Tool.PENTAGON, Tool.HEXAGON, Tool.HEPTAGON, Tool.OCTAGON,
    Tool.NONAGON, Tool.DECAGON, Tool.STAR, Tool.CROSS, Tool.PEN, Tool.CURVE, Tool.ARC
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
// Brush styles that are expensive to render and benefit from bitmap caching
val CACHED_BRUSH_STYLES = setOf(BrushStyle.SPRAY, BrushStyle.GRASS, BrushStyle.FIRE,
    BrushStyle.DRY_BRUSH, BrushStyle.CHARCOAL, BrushStyle.CRAYON,
    BrushStyle.WATERCOLOR, BrushStyle.NEON)

// Shapes resized by moving their two endpoints (fine reshaping of start/end point)
val ENDPOINT_RESIZE_SHAPES = setOf(Tool.LINE, Tool.CIRCLE, Tool.ARROW, Tool.CURVE, Tool.PEN)
// Shapes that also get bbox (8-handle) scaling — for uniform scale of all points
val STROKE_SCALE_SHAPES = setOf(Tool.LINE, Tool.ARROW, Tool.CURVE, Tool.PEN)

data class TextSpanData(val start: Int, val end: Int, val type: Char, val value: Int)

private val imageLoadExecutor = ThreadPoolExecutor(2, 3, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())

private val PAPER_BASE_COLOR = Color.parseColor("#FFFDF6")

private val bitmapCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    private val MAX_SIZE = 80 * 1024 * 1024L
    private var currentSize = 0L
    override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>): Boolean {
        val over = currentSize > MAX_SIZE
        if (over) currentSize -= eldest.value.byteCount  // was never decremented — cache became useless after ~80MB of cumulative historical inserts, regardless of current actual size
        return over
    }
    fun putBitmap(key: String, bmp: Bitmap) { currentSize += bmp.byteCount; put(key, bmp) }
    fun getBitmap(key: String): Bitmap? = get(key)
}

class StrokeData(
    val type: Tool, val points: MutableList<Float>,
    var color: Int, var strokeWidth: Float, var fill: Boolean, var rotation: Float = 0f,
    var fillColorVal: Int = color, var penStyle: PenStyle = PenStyle.FOUNTAIN, var opacity: Int = 255,
    var brushStyle: BrushStyle = BrushStyle.ROUND,
    var widths: MutableList<Float> = mutableListOf(),
    var lineType: LineType = LineType.CONTINUOUS,
    var isLocked: Boolean = false,
    // Multiplier controlling how thick the calligraphy nib's heavy (perpendicular) axis gets,
    // relative to strokeWidth. 1.0 = same as strokeWidth at its thickest point; higher values
    // exaggerate the calligraphic contrast. Stored per-stroke like strokeWidth itself, so
    // existing strokes keep whatever slant thickness they were drawn with.
    var calligraphySlantThickness: Float = 0.65f,
    // Pixel-erase holes: each entry is [cx, cy, radius] in world units.
    // Rendered by punching transparent circles out of the shape via PorterDuff.CLEAR.
    val clipHoles: MutableList<FloatArray> = mutableListOf(),
    // True for a stroke committed by the AutoCAD-style Polyline tool. Polyline vertices are
    // deliberately placed exact points, not freehand samples — smoothing/curve-fitting them
    // (which every Tool.PEN render path below does, to remove hand tremor from natural
    // handwriting) would round off intentional sharp corners into a curve, which is wrong for
    // a tool whose entire purpose is precise straight segments.
    var isPolyline: Boolean = false
) {
    fun buildPath(): Path {
        val path = Path()
        if (type == Tool.PEN || type == Tool.ERASER || type == Tool.HIGHLIGHTER || type == Tool.BRUSH) {
            if (points.size >= 2) {
                path.moveTo(points[0], points[1])
                if (type == Tool.PEN && !isPolyline) {
                    // Smooth the line by drawing a quadratic Bezier curve through the midpoint
                    // of each consecutive pair of points, using the point itself as the curve's
                    // control point — on top of smoothedPoints()'s own two-pass averaging and
                    // straight-line snap, so Ball/Pencil/Marker get the same wobble-removal as
                    // Fountain/Calligraphy instead of only smoothing the raw jittery samples.
                    // Skipped for polylines: quadTo would round off intentional sharp vertices
                    // into a curve even with unsmoothed points, since it's the curve-fitting
                    // itself (not just point smoothing) that removes corners.
                    val pts = smoothedPoints()
                    var i = 2
                    while (i + 3 < pts.size) {
                        val midX = (pts[i] + pts[i + 2]) / 2f
                        val midY = (pts[i + 1] + pts[i + 3]) / 2f
                        path.quadTo(pts[i], pts[i + 1], midX, midY)
                        i += 2
                    }
                    if (i + 1 < pts.size) path.lineTo(pts[i], pts[i + 1])
                } else {
                    var i = 2
                    while (i + 1 < points.size) { path.lineTo(points[i], points[i + 1]); i += 2 }
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

    // Smooths the raw touch-point list with a weighted moving average before it's used to
    // build a nib/ribbon outline. drawCalligraphyStroke/buildCalligraphyRibbonPath/
    // buildFountainRibbonPath below turn every pair of consecutive points into its own little
    // segment — so without this, the exact same per-sample jitter that used to show up as
    // faceted lineTo segments in a plain path instead shows up as a rough, hairy-looking edge
    // where each tiny segment's angle jumps around. Endpoints are left untouched so the stroke
    // still starts/ends exactly where drawn, and the point count/order is unchanged so it stays
    // in lockstep with the `widths` samples used for fountain-pen thickness.
    private fun smoothedPoints(): List<Float> {
        if (isPolyline) return points
        if (points.size < 6) return points
        fun onePass(src: List<Float>): List<Float> {
            val out = src.toMutableList()
            var i = 2
            while (i + 3 < src.size) {
                out[i] = (src[i - 2] + src[i] * 2f + src[i + 2]) / 4f
                out[i + 1] = (src[i - 1] + src[i + 1] * 2f + src[i + 3]) / 4f
                i += 2
            }
            return out
        }
        // Five passes now, not two — the circled bumps in testing showed two passes still
        // wasn't enough to fully absorb real-world hand tremor / touch-sensor noise. Each pass
        // only pulls a point toward its neighbors' midpoint, so it can only ever erode small
        // back-and-forth jitter, not a genuine corner or the overall shape of a letterform —
        // repeating it more times just keeps eroding whatever jitter is left, favoring a
        // smooth result over microscopically tracking every sampled point, which is exactly
        // what's wanted here even if the shakiness is coming from the finger/pen/digitizer
        // rather than the smoothing being too weak.
        var smoothed: List<Float> = points
        repeat(5) { smoothed = onePass(smoothed) }

        // Straight-line snap: averaging can only ever soften wobble, never fully remove it —
        // but a stroke the person clearly INTENDED as straight (an underline, a ruled line)
        // has no reason to keep any deviation at all. If the whole stroke barely strays from
        // the direct line between its first and last point, snap every point exactly onto
        // that line instead of just smoothing around it.
        val x0 = smoothed[0]; val y0 = smoothed[1]
        val xn = smoothed[smoothed.size - 2]; val yn = smoothed[smoothed.size - 1]
        val dx = xn - x0; val dy = yn - y0
        val lineLen = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (lineLen > 8f) {  // too short to judge straightness (a dot, a tiny flick) — skip
            val ux = dx / lineLen; val uy = dy / lineLen
            var maxDeviation = 0f
            var i = 0
            while (i + 1 < smoothed.size) {
                val px = smoothed[i] - x0; val py = smoothed[i + 1] - y0
                val proj = px * ux + py * uy
                val perpX = px - proj * ux; val perpY = py - proj * uy
                val dev = kotlin.math.hypot(perpX.toDouble(), perpY.toDouble()).toFloat()
                if (dev > maxDeviation) maxDeviation = dev
                i += 2
            }
            // Tolerance scales with stroke length so a long confident line is allowed a touch
            // more absolute wobble than a short one, but stays capped so this never triggers
            // on anything actually curved (letterforms deviate far more than this).
            val tolerance = (lineLen * 0.035f).coerceIn(1.5f, 6f)
            if (maxDeviation <= tolerance) {
                val snapped = smoothed.toMutableList()
                var j = 0
                while (j + 1 < snapped.size) {
                    val px = smoothed[j] - x0; val py = smoothed[j + 1] - y0
                    val proj = (px * ux + py * uy).coerceIn(0f, lineLen)
                    snapped[j] = x0 + ux * proj
                    snapped[j + 1] = y0 + uy * proj
                    j += 2
                }
                return snapped
            }
        }
        return smoothed
    }

    // Legacy single-polygon path - kept only as a fallback for very short strokes (under 2
    // segments) where quad-splitting has nothing to split; never used for real handwriting.
    fun buildCalligraphyRibbonPath(): Path {
        val ribbon = Path()
        if (points.size < 4) return buildPath()
        val pts = smoothedPoints()
        val nibAngle = Math.toRadians(-45.0) // corrected for Android's Y-down canvas coordinate flip
        val nibDirX = kotlin.math.cos(nibAngle).toFloat(); val nibDirY = kotlin.math.sin(nibAngle).toFloat()
        val halfNib = strokeWidth * calligraphySlantThickness
        val px = pts[0]; val py = pts[1]
        val nx = nibDirX * halfNib; val ny = nibDirY * halfNib
        ribbon.moveTo(px - nx, py - ny); ribbon.lineTo(pts[2] - nx, pts[3] - ny)
        ribbon.lineTo(pts[2] + nx, pts[3] + ny); ribbon.lineTo(px + nx, py + ny)
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
        val pts = smoothedPoints()
        val left = mutableListOf<Pair<Float, Float>>(); val right = mutableListOf<Pair<Float, Float>>()
        // Segment count, used below to taper width at both ends of the stroke — without this,
        // the very first/last sample already carries near-full width, so the ribbon starts and
        // ends as an abrupt blunt blob instead of tapering to a point like a real nib lifting
        // on/off the page.
        val totalSegments = ((pts.size - 2) / 2).coerceAtLeast(1)
        val taperCount = 4
        fun endTaper(segIndex: Int): Float {
            val fadeIn = ((segIndex + 1).toFloat()).coerceAtMost(taperCount.toFloat()) / taperCount
            val fadeOut = ((totalSegments - segIndex).toFloat()).coerceAtMost(taperCount.toFloat()) / taperCount
            return minOf(fadeIn, fadeOut).coerceIn(0.12f, 1f)
        }
        var i = 0; var wi = 0
        while (i + 3 < pts.size) {
            val x1 = pts[i]; val y1 = pts[i + 1]; val x2 = pts[i + 2]; val y2 = pts[i + 3]
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val nx = -dy / len; val ny = dx / len
            val w = (if (wi < widths.size) widths[wi] else strokeWidth) * endTaper(wi) / 2f
            left.add(Pair(x1 + nx * w, y1 + ny * w)); right.add(Pair(x1 - nx * w, y1 - ny * w))
            i += 2; wi++
        }
        // Cap the final point with the last known width, also tapered
        val lastIdx = pts.size - 2
        val lastW = (if (widths.isNotEmpty()) widths.last() else strokeWidth) * endTaper(totalSegments) / 2f
        if (i >= 2) {
            val px = pts[lastIdx]; val py = pts[lastIdx + 1]
            val pdx = px - pts[i - 2]; val pdy = py - pts[i - 1]
            val plen = kotlin.math.hypot(pdx.toDouble(), pdy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val nx = -pdy / plen; val ny = pdx / plen
            left.add(Pair(px + nx * lastW, py + ny * lastW)); right.add(Pair(px - nx * lastW, py - ny * lastW))
        }
        if (left.isEmpty() || right.isEmpty()) return buildPath()
        // Curve through the offset points instead of connecting them with straight lineTo
        // segments (the polygon-edge look was part of why the ribbon read as faceted rather
        // than a fluently tapering nib stroke) — same midpoint-quadTo technique already used
        // to smooth plain pen strokes in buildPath(). Skipped for polylines, same reason as
        // there: quadTo would round off intentional sharp vertices into a curve.
        fun addSmoothed(path: Path, pts: List<Pair<Float, Float>>, moveToFirst: Boolean) {
            if (pts.isEmpty()) return
            if (moveToFirst) path.moveTo(pts[0].first, pts[0].second)
            if (isPolyline || pts.size < 3) { for (p in pts.drop(1)) path.lineTo(p.first, p.second); return }
            var j = 1
            while (j + 1 < pts.size) {
                val midX = (pts[j].first + pts[j + 1].first) / 2f
                val midY = (pts[j].second + pts[j + 1].second) / 2f
                path.quadTo(pts[j].first, pts[j].second, midX, midY)
                j++
            }
            path.lineTo(pts.last().first, pts.last().second)
        }
        addSmoothed(ribbon, left, true)
        addSmoothed(ribbon, right.reversed(), false)
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

    // Draws a Calligraphy stroke segment-by-segment as native stroked lines with a per-segment
    // chisel-nib width, using ROUND caps/joins — the same proven technique already used above
    // for Pencil. This replaces an earlier per-segment-quad + joint-circle approach that turned
    // out fragile through several iterations (visible gaps, then a slow path-union fix, then a
    // dotted/fragmented-fill rendering bug caused by an inherited LineType dash/discrete
    // pathEffect corrupting the filled quads). Native Paint.Style.STROKE with ROUND caps is
    // what Android itself uses to guarantee gap-free joins between segments — there's no custom
    // polygon geometry left to get subtly wrong, and pathEffect is explicitly stripped so no
    // inherited dash/dotted line-type setting can ever corrupt the stroke body again.
    fun drawCalligraphyStroke(canvas: Canvas, basePaint: Paint) {
        if (points.size < 4) { canvas.drawPath(buildPath(), basePaint); return }
        val pts = smoothedPoints()
        val nibAngle = Math.toRadians(-45.0) // corrected for Android's Y-down canvas coordinate flip
        val nibDirX = kotlin.math.cos(nibAngle).toFloat(); val nibDirY = kotlin.math.sin(nibAngle).toFloat()
        val segPaint = Paint(basePaint).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; pathEffect = null }
        // Segment count, used to taper the nib width at both ends — otherwise the very first
        // segment already carries near-full width, and with a round cap that's an abrupt round
        // blob right at the stroke's start (same for the end) instead of a nib tapering to a
        // point the way it lifts on/off the page in real handwriting.
        val totalSegments = ((pts.size - 2) / 2).coerceAtLeast(1)
        val taperCount = 4
        fun endTaper(segIndex: Int): Float {
            val fadeIn = ((segIndex + 1).toFloat()).coerceAtMost(taperCount.toFloat()) / taperCount
            val fadeOut = ((totalSegments - segIndex).toFloat()).coerceAtMost(taperCount.toFloat()) / taperCount
            return minOf(fadeIn, fadeOut).coerceIn(0.12f, 1f)
        }
        var i = 0
        var segIndex = 0
        var smoothedWidth = -1f  // -1 sentinel: first segment sets it directly, no smoothing to blend from yet
        while (i + 3 < pts.size) {
            val x1 = pts[i]; val y1 = pts[i + 1]; val x2 = pts[i + 2]; val y2 = pts[i + 3]
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val ndx = dx / len; val ndy = dy / len
            // Same width-factor formula as before: how perpendicular the stroke direction is
            // to the nib's fixed 45-degree axis. Floor brought back down to 0.15 (from 0.42) —
            // raising it earlier had killed the dramatic bold/thin chisel contrast that a real
            // calligraphy nib (and Notewise's rendering) shows; 0.15 restores that contrast.
            val widthFactor = kotlin.math.abs(ndx * nibDirY - ndy * nibDirX).coerceIn(0.15f, 1f)
            val targetWidth = strokeWidth * calligraphySlantThickness * 2f * widthFactor * endTaper(segIndex)
            // Blend toward the target rather than snapping straight to it each segment — this
            // is what actually reads as "smooth": the nib width still swings from thin to bold
            // across the stroke, just as a gradual taper instead of a visible step between two
            // adjacent line segments.
            smoothedWidth = if (smoothedWidth < 0f) targetWidth else smoothedWidth * 0.55f + targetWidth * 0.45f
            segPaint.strokeWidth = smoothedWidth
            canvas.drawLine(x1, y1, x2, y2, segPaint)
            i += 2; segIndex++
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
                BrushStyle.INK -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = opacity }
                BrushStyle.WATERCOLOR -> { p.strokeWidth = strokeWidth * 2.8f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.15f).toInt() }
                BrushStyle.CRAYON -> { p.strokeWidth = strokeWidth * 1.1f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.80f).toInt(); p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 0.6f, strokeWidth * 0.15f), 0f) }
                BrushStyle.CHARCOAL -> { p.strokeWidth = strokeWidth * 1.4f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.55f).toInt(); p.pathEffect = android.graphics.ComposePathEffect(android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 0.4f, strokeWidth * 0.2f), 0f), android.graphics.DiscretePathEffect(strokeWidth * 0.15f, strokeWidth * 0.08f)) }
                BrushStyle.NEON -> { p.strokeWidth = strokeWidth * 1.5f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = opacity }
                BrushStyle.DRY_BRUSH -> { p.strokeWidth = strokeWidth; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND; p.alpha = (opacity * 0.7f).toInt(); p.pathEffect = android.graphics.ComposePathEffect(android.graphics.DashPathEffect(floatArrayOf(strokeWidth * 0.3f, strokeWidth * 0.25f), 0f), android.graphics.DiscretePathEffect(strokeWidth * 0.4f, strokeWidth * 0.2f)) }
                // Particle brushes (SPRAY, FIRE, GRASS) handled separately in drawActionItem
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
        val intervals = lineType.intervals
        if (lineType != LineType.CONTINUOUS && penStyle != PenStyle.PENCIL && intervals != null) {
            val sw = p.strokeWidth.coerceAtLeast(1f)
            val scaled = intervals.map { it * sw / 3f }.toFloatArray()
            p.pathEffect = android.graphics.DashPathEffect(scaled, 0f)
            if (lineType.cap != android.graphics.Paint.Cap.BUTT) p.strokeCap = lineType.cap
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

class StrokeItem(val data: StrokeData, var path: Path, var paint: Paint) {
    var cachedBitmap: android.graphics.Bitmap? = null
    var cacheValid: Boolean = false
    var cacheLeft = 0f; var cacheTop = 0f; var cacheRight = 0f; var cacheBottom = 0f
    // Sampled path points for fast hit testing — built once from PathMeasure, reused on every tap
    var sampledPathPts: FloatArray? = null

    fun getOrBuildSampledPath(hitRadius: Float): FloatArray {
        return sampledPathPts ?: buildSampledPath(hitRadius)
    }
    private fun buildSampledPath(hitRadius: Float): FloatArray {
        val pm = android.graphics.PathMeasure(path, false)
        val result = mutableListOf<Float>()
        val pos = FloatArray(2)
        do {
            val len = pm.length; if (len <= 0f) continue
            val step = (hitRadius * 0.4f).coerceIn(1f, 15f)
            var d = 0f
            while (d <= len) { pm.getPosTan(d, pos, null); result.add(pos[0]); result.add(pos[1]); d += step }
        } while (pm.nextContour())
        val arr = result.toFloatArray()
        sampledPathPts = arr; return arr
    }
    // Pre-built PointF list for brush rendering
    var cachedPoints: List<android.graphics.PointF>? = null
    fun getPoints(): List<android.graphics.PointF> {
        return cachedPoints ?: run {
            val pts = data.points
            val list = ArrayList<android.graphics.PointF>(pts.size / 2)
            var i = 0; while (i < pts.size - 1) { list.add(android.graphics.PointF(pts[i], pts[i+1])); i += 2 }
            cachedPoints = list; list
        }
    }
    fun invalidateCache() {
        cachedBitmap?.recycle(); cachedBitmap = null; cacheValid = false
        sampledPathPts = null; cachedPoints = null  // path changed — resample next hit test
    }
}

class TextItem(var text: String, var x: Float, var y: Float, var color: Int, var size: Float, var rotation: Float) {
    var spans: MutableList<TextSpanData> = mutableListOf()
    var isEditing: Boolean = false
    var maxWidth: Float = 0f  // 0 = unbounded (legacy); >0 = wrap to this width
    var fontFamily: String = "sans-serif"  // system family name OR absolute path to .ttf/.otf file
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
    // Separate from `bitmap` above (which holds the flood-fill SHAPE mask) — this holds the
    // fully-rendered, already-masked hatch pattern itself, computed once and reused every
    // frame after that. See drawHatchPattern().
    @Volatile var hatchRenderCache: Bitmap? = null
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
    fun displayLabel(refPixelLen: Float, mmPerUnit: Float = 0f): String {
        return when {
            label.isNotEmpty() -> label
            isAngular -> "%.1f°".format(angle)
            mmPerUnit > 0f -> { val mm = len * mmPerUnit; if (mm >= 1000f) "${"%.3f".format(mm/1000f)}m" else "${"%.1f".format(mm)}mm" }
            mode == DimMode.AUTO && refPixelLen > 0f -> "%.2f %s".format(len * refLength / refPixelLen, unit)
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
    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    // Resolves a font family string to a Typeface.
    // Supports both system family names ("sans-serif") and absolute file paths ("/data/.../font.ttf").
    private val typefaceCache = HashMap<String, android.graphics.Typeface>()
    fun typefaceFromFamily(family: String): android.graphics.Typeface {
        return typefaceCache.getOrPut(family) {
            try {
                if (family.startsWith("/") && java.io.File(family).exists()) {
                    android.graphics.Typeface.createFromFile(family)
                } else {
                    android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
                }
            } catch (e: Exception) { android.graphics.Typeface.DEFAULT }
        }
    }
    private val actions = mutableListOf<Any>()

    // ── Spatial index for fast viewport culling and eraser hit-testing ────────
    private val GRID_CELL = 400f
    private val spatialGrid = HashMap<Long, MutableList<Any>>(256)
    private var spatialDirty = true

    private fun cellKey(cx: Int, cy: Int): Long = cx.toLong().shl(32) or cy.toLong().and(0xFFFFFFFFL)

    private fun boundsToGridCells(minX: Float, minY: Float, maxX: Float, maxY: Float): List<Long> {
        val x0 = kotlin.math.floor(minX / GRID_CELL).toInt()
        val y0 = kotlin.math.floor(minY / GRID_CELL).toInt()
        val x1 = kotlin.math.floor(maxX / GRID_CELL).toInt()
        val y1 = kotlin.math.floor(maxY / GRID_CELL).toInt()
        val cells = mutableListOf<Long>()
        for (cx in x0..x1) for (cy in y0..y1) cells.add(cellKey(cx, cy))
        return cells
    }

    private fun rebuildSpatialIndex() {
        spatialGrid.clear()
        for (a in actions) {
            val b = getBounds(a)
            // Use a generous padding (half a cell) so items near cell edges aren't missed
            val pad = GRID_CELL * 0.6f
            val bl = (b?.get(0) ?: 0f) - pad; val bt = (b?.get(1) ?: 0f) - pad
            val br = (b?.get(2) ?: 0f) + pad; val bb = (b?.get(3) ?: 0f) + pad
            for (key in boundsToGridCells(bl, bt, br, bb)) {
                spatialGrid.getOrPut(key) { mutableListOf() }.add(a)
            }
        }
        spatialDirty = false
    }

    private fun markSpatialDirty() { spatialDirty = true; snapMarkersActionCount = -1 }
    fun markSpatialDirtyAndInvalidate() { spatialDirty = true; snapMarkersDirty = true; snapMarkersActionCount = -1; invalidate() }
    private var snapMarkersDirty = true
    private var snapMarkersScale = -1f
    private val cachedSnapMarkerPaint = Paint()
    // Cached paints for frequently-called drawing functions — avoids Paint() allocation each frame
    private val _selBoxPaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val _handleFill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val _handleStroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val _holePaint = Paint().apply { style = Paint.Style.FILL; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR); isAntiAlias = true }
    private val _fillErasePaint = Paint().apply { color = Color.TRANSPARENT; style = Paint.Style.FILL; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
    private val _cursorPaint = Paint().apply { isAntiAlias = true }

    // Lock/unlock — called from MainActivity lock button
    fun lockSelectedItems() {
        for (item in selectedItems) if (item is StrokeItem) item.data.isLocked = true
        if (selectedItem is StrokeItem) (selectedItem as StrokeItem).data.isLocked = true
        invalidate()
    }
    fun unlockSelectedItems() {
        for (item in selectedItems) if (item is StrokeItem) item.data.isLocked = false
        if (selectedItem is StrokeItem) (selectedItem as StrokeItem).data.isLocked = false
        invalidate()
    }
    fun isSelectionLocked(): Boolean {
        val items = (selectedItems + setOfNotNull(selectedItem)).filterIsInstance<StrokeItem>()
        return items.isNotEmpty() && items.all { it.data.isLocked }
    }

    private fun itemsNear(x: Float, y: Float, r: Float): List<Any> {
        if (spatialDirty) rebuildSpatialIndex()
        val wx = x - r; val wy = y - r; val wx2 = x + r; val wy2 = y + r
        val seen = HashSet<Any>(); val result = mutableListOf<Any>()
        for (key in boundsToGridCells(wx, wy, wx2, wy2)) {
            spatialGrid[key]?.forEach { a -> if (seen.add(a)) result.add(a) }
        }
        return result
    }

    private fun itemsInViewport(): List<Any> {
        if (spatialDirty) rebuildSpatialIndex()
        val vl = -translateX / scaleFactor; val vt = -translateY / scaleFactor
        val vr = vl + width / scaleFactor; val vb = vt + height / scaleFactor
        // Add padding equal to one grid cell so items near the edge are never missed
        val pad = GRID_CELL
        val seen = HashSet<Any>(); val result = mutableListOf<Any>()
        for (key in boundsToGridCells(vl - pad, vt - pad, vr + pad, vb + pad)) {
            spatialGrid[key]?.forEach { a -> if (seen.add(a)) result.add(a) }
        }
        // Previously also unconditionally scanned every TextItem/TableItem/AudioItem/
        // DimensionItem in the WHOLE document here, regardless of whether they were anywhere
        // near the viewport — meant a document with thousands of text boxes or tables spent
        // real work "including" (and then drawing) all of them, every single frame, no matter
        // how far away you'd scrolled. That existed because DimensionItem had no real bounds
        // (see getBounds() above) so it couldn't be trusted to show up in the spatial query on
        // its own. Now that every type has real bounds and is correctly indexed by
        // rebuildSpatialIndex(), the grid query above already finds all of them — this is now
        // genuinely just "items on screen" regardless of how large the document is.
        return result
    }
    fun removeDimensionItem(d: DimensionItem) { actions.remove(d); selectedItem = null; redoStack.clear(); markSpatialDirty(); invalidate() }
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
                groupCurrentRotation = 0f
            }
            field = value; invalidate()
        }
    // Fired only when DrawingView switches tools ON ITS OWN (link-tap auto-selecting an item,
    // exiting table-cell editing, finishing an export-window drag) — NOT when MainActivity sets
    // currentTool directly via its own tool buttons. Without this, the toolbar highlight could
    // show one tool active while the canvas was actually behaving as a different one, since
    // these internal switches previously had no way to notify MainActivity at all.
    var onInternalToolChange: ((Tool) -> Unit)? = null

    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 6f
    var currentPenStyle: PenStyle = PenStyle.FOUNTAIN
    var currentCalligraphySlant: Float = 0.65f  // applied to new fountain-pen strokes; adjustable separately from base thickness
    var currentLineType: LineType = LineType.CONTINUOUS
    // Snap-to-endpoint: snaps stroke start/end to nearby existing endpoints within snapRadius world units.
    // snapRadius is in screen pixels and converted to world space on use, so it stays consistent at any zoom.
    var snapToEndpoints: Boolean
        get() = snapEnabled
        set(v) { snapEnabled = v }
    // ── Polyline state ────────────────────────────────────────────────────────
    // Points accumulate with each tap; double-tap or Close button finishes.
    val polylinePoints = mutableListOf<Float>()  // world coords [x0,y0,x1,y1,...]
    var polylineCursorX = 0f; var polylineCursorY = 0f  // live preview end point
    var onPolylineUpdated: (() -> Unit)? = null  // notify MainActivity to offer Close/Undo

    fun finalizePolyline(close: Boolean = false) {
        if (polylinePoints.size < 4) { polylinePoints.clear(); invalidate(); return }
        if (close && polylinePoints.size >= 4) {
            polylinePoints.add(polylinePoints[0]); polylinePoints.add(polylinePoints[1])
        }
        // Commit as ONE unified multi-segment stroke (true AutoCAD-style polyline) — moves,
        // rotates, selects, and deletes as a single object, and renders in a single draw call
        // instead of fragmenting into N independent LINE items. Uses the user's currently
        // selected pen style so drawn thickness matches the thickness slider (PenStyle.BALL
        // renders at 0.65x strokeWidth, which was silently shrinking every polyline).
        val d = StrokeData(Tool.PEN, polylinePoints.toMutableList(),
            currentColor, currentStrokeWidth, false, lineType = currentLineType, penStyle = currentPenStyle, isPolyline = true)
        val si = StrokeItem(d, d.buildPath(), d.toPaint())
        actions.add(si)
        redoStack.clear(); markSpatialDirty()
        polylinePoints.clear(); onPolylineUpdated?.invoke(); invalidate()
    }

    fun undoLastPolylineVertex() {
        if (polylinePoints.size >= 2) { polylinePoints.removeAt(polylinePoints.size-1); polylinePoints.removeAt(polylinePoints.size-1) }
        invalidate()
    }
    var snapEnabled: Boolean = false
    var snapEndpoint: Boolean = true
    var snapMidpoint: Boolean = true
    var snapIntersection: Boolean = true
    var snapCenter: Boolean = true
    var snapNearest: Boolean = true
    var snapPerpendicular: Boolean = true
    var snapTangent: Boolean = true
    var snapParallel: Boolean = true
    var snapGrid: Boolean = true
    var snapAutoConnect: Boolean = false
    private val snapScreenRadius = 48f        // actual snap in screen pixels — larger for finger
    private val snapAwarenessRadius = 130f    // show ghost markers on objects within this range
    private var snapResult: SnapResult? = null
    private var snapAwarenessResults: List<SnapResult> = emptyList()  // all nearby snap points for preview

    enum class SnapType(val priority: Int) {
        ENDPOINT(1), INTERSECTION(2), MIDPOINT(3), CENTER(4),
        PERPENDICULAR(5), TANGENT(6), PARALLEL(7), NEAREST(8), GRID(9)
    }
    data class SnapResult(val wx: Float, val wy: Float, val type: SnapType)

    // Kept for backward compat — callers that used Pair<Float,Float> snapTarget
    private var snapTarget: Pair<Float, Float>?
        get() = snapResult?.let { Pair(it.wx, it.wy) }
        set(v) { snapResult = v?.let { SnapResult(it.first, it.second, SnapType.ENDPOINT) } }
    var currentOpacity: Int = 255
    var highlighterThickness: Float = 24f
    var highlighterOpacity: Int = 20
    var currentBrushStyle: BrushStyle = BrushStyle.ROUND
    var brushThickness: Float = 30f
    var brushOpacity: Int = 255
    var eraserSize: Float = 40f
    var eraserMode: EraserMode = EraserMode.OBJECT
    var eraserAffectsFill: Boolean = true  // when false, eraser skips FillItems entirely (leaves colour fills untouched)
    // Fill items currently mutated in-memory during an active erase drag, not yet written to
    // disk. Flushed once on ACTION_UP instead of on every touch tick — see eraseFillItemRegion.
    private val dirtyFillItems = mutableSetOf<FillItem>()
    private var eraserLastX = Float.NaN; private var eraserLastY = Float.NaN  // persists ACROSS ACTION_MOVE calls for gap-free interpolation
    var eraserShape: EraserShape = EraserShape.ROUND
    var inputMode: InputMode = InputMode.AUTO  // AUTO = existing palm-rejection-while-stylus-down behavior
    var fillShapes: Boolean = false
    var fillColor: Int = Color.RED
    var arcDivisions: Int = 3
    var paperType: PaperType = PaperType.GRID
    var paperColor: Int = Color.parseColor("#FFFDE7")
    var defaultTextSize: Float = 50f * 1.333f

    var canvasMode: CanvasMode = CanvasMode.CONVENIENT
    var paperSize: PaperSizeOption = PaperSizeOption.A4

    // ── Real-world scale system ───────────────────────────────────────────────
    var paperScale: Float = 1f        // denominator: 100 = 1:100
    var gridRealSizeMm: Float = 10f   // for INFINITE canvas: mm per grid square

    fun mmPerWorldUnit(): Float = if (canvasMode == CanvasMode.INFINITE)
        gridRealSizeMm / gridSpacingPx()
    else
        paperScale * paperSize.widthMM / pageWidthPx()

    fun worldToRealMm(worldUnits: Float): Float = worldUnits * mmPerWorldUnit()
    fun realMmToWorld(mm: Float): Float { val m = mmPerWorldUnit(); return if (m < 1e-9f) mm else mm / m }
    fun formatRealWorld(worldUnits: Float): String { val mm = worldToRealMm(worldUnits); return if (mm >= 1000f) "${"%.3f".format(mm/1000f)}m" else "${"%.1f".format(mm)}mm" }
    fun parseRealWorldMm(input: String): Float? {
        val s = input.trim().lowercase()
        return try { when { s.endsWith("mm") -> s.dropLast(2).trim().toFloat(); s.endsWith("cm") -> s.dropLast(2).trim().toFloat()*10f; s.endsWith("m") -> s.dropLast(1).trim().toFloat()*1000f; else -> s.toFloat() } } catch (e: Exception) { null }
    }
    // ─────────────────────────────────────────────────────────────────────────
    var pageOrientation: Orientation = Orientation.PORTRAIT

    var selectedGroup: MutableList<Any>? = null
    var onGroupDelete: (() -> Unit)? = null  // called when group delete handle tapped
    private var groupRotateStartAngle = 0f
    private var groupRotateCenterX = 0f; private var groupRotateCenterY = 0f
    private var groupRotateSnapshots: List<FloatArray?>? = null
    private var groupCurrentRotation = 0f  // tracked rotation of the group box (for drawing)
    private var pinkGroupRotation = 0f  // rigid-body display rotation for the MULTISELECT pink box during GROUP-mode drag
    private var groupRotating = false
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
    // Multi-select: set of all selected items (includes selectedItem when non-null)
    val selectedItems: MutableSet<Any> = mutableSetOf()
    var onMultiSelectionChanged: ((Set<Any>) -> Unit)? = null
    var multiSelectMode: Boolean = false  // when true, taps add/remove from selectedItems
    var multiSelectIndividual: Boolean = true  // true=each item transforms independently, false=rigid group
    var onItemSelected: ((Any?) -> Unit)? = null

    private enum class HandleType { NONE, MOVE, ROTATE, TL, TM, TR, ML, MR, BL, BM, BR }

    private var activeHandle = HandleType.NONE
    private var dragStartWorldX = 0f; private var dragStartWorldY = 0f
    // Group (pink) bounding box state for multi-select
    private var groupActiveHandle = HandleType.NONE
    private var groupDragStartX = 0f; private var groupDragStartY = 0f
    private var groupDragStartAngle = 0f  // atan2 angle at rotation drag start
    private var groupOrigGcx = 0f; private var groupOrigGcy = 0f  // group center at drag start
    private var groupOrigBounds: FloatArray? = null  // [minX,minY,maxX,maxY] at drag start
    private var msTapDownWx = Float.NaN; private var msTapDownWy = Float.NaN
    private var msDragging = false
    // Snapshots of each item's defining points at group drag start, for proportional resize
    private val groupSnapshots = mutableListOf<Pair<Any, FloatArray>>()  // item → pts copy
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
    var onTextSelectRequest: ((TextItem, Float, Float, Float, Float) -> Unit)? = null  // item, screenX, screenY, initialRawX, initialRawY
    var onTextEditOptions: ((TextItem) -> Unit)? = null  // show text options in context bar
    var onTextHoldDragMove: ((TextItem, Float, Float) -> Unit)? = null  // called when hold+drag moves — rawX, rawY of current finger
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
    // Invalidate all stroke caches when zoom changes (pixel dimensions change)
    fun shiftCanvasVertically(deltaY: Float) {
        translateY += deltaY
        // Do NOT clamp here — keyboard-avoidance scroll needs to move beyond normal page
        // boundaries so the text editor stays visible above the keyboard. Clamping eats
        // the shift when the page already fills the screen, which is the common case.
        invalidate()
        onCanvasTransformed?.invoke()
    }

    fun getTranslateY(): Float = translateY

    private fun invalidateAllStrokeCaches() {
        actions.filterIsInstance<StrokeItem>().forEach { it.invalidateCache() }
    }
    var onCanvasTransformed: (() -> Unit)? = null
    var onPageSwipe: ((Int) -> Unit)? = null
    var onScrollPercentChanged: ((Float) -> Unit)? = null
    var onDrawingStarted: (() -> Unit)? = null
    var onDrawingEnded: (() -> Unit)? = null
    var onShapeCompleted: ((StrokeItem) -> Unit)? = null  // fired after each shape is drawn
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
    var onOcrSnipSelected: ((Bitmap, Float, Float, Float, Float) -> Unit)? = null  // cropped bitmap + world bounds (left, top, right, bottom), ready for OCR or any other per-region use

    private var scaleFactor = 1f
    private var translateX = 0f; private var translateY = 0f
    private var twoFingerLastX = 0f; private var twoFingerLastY = 0f
    private var twoFingerInitialDist = 0f  // finger distance when second finger touched down
    private var twoFingerActive = false       // true while 2+ fingers are on screen
    private var twoFingerEverActive = false   // true from first 2-finger contact until next fresh ACTION_DOWN
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
            // Larger deadzone (8%) so slightly angled pan gestures don't trigger scale.
            // Panning with two non-parallel fingers naturally creates a tiny pinch signal —
            // this threshold absorbs that noise without blocking intentional pinch-zoom.
            if (kotlin.math.abs(accumulatedScaleFactor - 1f) < 0.08f) return true
            accumulatedScaleFactor = 1f  // reset after applying so next frame starts fresh

            val minScale = if (canvasMode != CanvasMode.INFINITE && width > 0 && height > 0) {
                minOf(width.toFloat() / (pageWidthPx() * 1.5f), height.toFloat() / (pageHeightPx() * 1.5f)).coerceAtLeast(0.05f)
            } else 0.2f
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, 6f)
            val factor = newScale / scaleFactor
            // Zoom to focus point horizontally only — no vertical shift (avoids scroll-up on zoom)
            translateX = detector.focusX - (detector.focusX - translateX) * factor
            translateY = translateY * factor
            scaleFactor = newScale
            clampTranslation()
            onScaleChanged?.invoke(scaleFactor)
            onCanvasTransformed?.invoke()
            invalidate(); return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        // onSingleTapUp: links navigate on single tap (no hold).
        // Non-link text: selection box shown on finger-up (single tap confirmed via onSingleTapConfirmed).
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val wx = screenToWorldX(e.x); val wy = screenToWorldY(e.y)
            val hit = findTextItemAt(wx, wy)
            // Links: single tap navigates
            if (hit != null && hit.linkTarget != null) { onLinkTap?.invoke(hit.linkTarget!!); return true }
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
                    if (hit != null) {
                        // Was missing the onTextSelectRequest call that the identical case in
                        // the Tool.SELECT branch below has — so this correctly switched to
                        // Select (so the text becomes draggable/resizable) but never told
                        // MainActivity to actually show the font/size/color options panel,
                        // leaving the person looking at a plain Select toolbar with no way to
                        // change what they just tapped.
                        selectedItem = hit; currentTool = Tool.SELECT; onInternalToolChange?.invoke(Tool.SELECT)
                        onTextSelectRequest?.invoke(hit, e.x, e.y, e.rawX, e.rawY)
                        invalidate()
                    }
                    else if (isTextEditorOpen) {
                        // An editor is already open and the user tapped empty space - this means
                        // "I'm done typing," not "start a new text box here." Close/commit the
                        // current one instead of opening another.
                        onEmptyAreaTap?.invoke()
                    } else {
                        selectedItem = null; if (!isTextSelected) onTextEditRequest?.invoke(null, e.x, e.y, wx, wy)
                    }
                }
                Tool.MULTISELECT -> {
                    // Handled in ACTION_DOWN directly for responsiveness
                }
                Tool.SELECT -> {
                    val hit = findTextItemAt(wx, wy)
                    if (hit != null && hit.linkTarget == null) {
                        // Single tap on normal text: show selection box
                        selectedItem = hit
                        onTextSelectRequest?.invoke(hit, e.x, e.y, e.rawX, e.rawY)
                        invalidate()
                        return true
                    }
                    val tableHit = actions.reversed().filterIsInstance<TableItem>().firstOrNull { t -> val b = getBounds(t); b != null && wx >= b[0] && wx <= b[2] && wy >= b[1] && wy <= b[3] }
                    when {
                        tableHit != null -> { /* let handleTable manage this */ }
                        else -> {
                            if (selectedItem == null || selectedItem is TextItem) {
                                if (selectedItem is TextItem) { selectedItem = null; onTextDeselectRequest?.invoke() }
                                onEmptyAreaTap?.invoke()
                            }
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
            // Double-tap on empty space in MULTISELECT mode: clear all selections
            if (currentTool == Tool.MULTISELECT) {
                val hit = findItemAt(wx, wy)
                if (hit == null) {
                    selectedItems.clear(); selectedItem = null
                    onMultiSelectionChanged?.invoke(emptySet()); invalidate(); return true
                }
            }
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
                if (hit.linkTarget != null) {
                    // Double-tap on link after holding to select: show text options (size/font)
                    selectedItem = hit; invalidate()
                    onTextEditOptions?.invoke(hit)
                    return true
                }
                // Normal text: open inline editor
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
                selectedItem = hit; invalidate()
                onTextSelectRequest?.invoke(hit, e.x, e.y, e.rawX, e.rawY)
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
        // ── Fast path: simple unrotated LINE stroke ────────────────────────────
        // Avoids Path overhead entirely — canvas.drawLine() is much cheaper.
        if (action is StrokeItem && action.data.type == Tool.LINE &&
            action.data.points.size == 4 && action.data.rotation == 0f &&
            action.data.clipHoles.isEmpty() && action.data.lineType == LineType.CONTINUOUS) {
            val pts = action.data.points
            canvas.drawLine(pts[0], pts[1], pts[2], pts[3], action.paint)
            // Cyan multiselect overlay
            if (currentTool == Tool.MULTISELECT) {
                val allSel2 = (selectedItems + setOfNotNull(selectedItem)).toSet()
                if (action in allSel2) {
                    val cp = _selBoxPaint.apply {
                        color = android.graphics.Color.parseColor("#CC00BCD4"); style = Paint.Style.STROKE
                        strokeWidth = (action.paint.strokeWidth + 6f/scaleFactor).coerceAtLeast(6f/scaleFactor)
                        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
                    }
                    canvas.drawLine(pts[0], pts[1], pts[2], pts[3], cp)
                }
            }
            return
        }
        // ── End fast path ──────────────────────────────────────────────────────
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
                // All brush strokes go through the dedicated brush renderer (with caching for expensive styles)
                if (action.data.type == Tool.BRUSH) {
                    drawBrushStrokeWithCache(canvas, action); return
                }
                val isCalligraphyPen = action.data.type == Tool.PEN && action.data.penStyle == PenStyle.CALLIGRAPHY
                val isFountainPen = action.data.type == Tool.PEN && action.data.penStyle == PenStyle.FOUNTAIN && action.data.widths.size >= 2
                val isPencilPen = action.data.type == Tool.PEN && action.data.penStyle == PenStyle.PENCIL && action.data.widths.size >= 2
                if (isPencilPen && action.data.rotation == 0f) { action.data.drawPencilStroke(canvas, action.paint); return }
                if (isCalligraphyPen && action.data.rotation == 0f) {
                    drawWithBitmapCache(canvas, action, action.data.strokeWidth * 2f) { c, item -> item.data.drawCalligraphyStroke(c, item.paint) }
                    return
                }
                // clipHoles (pixel-eraser holes) and rotation both need the shared handling
                // further below, so only the common case — no rotation, nothing erased out of
                // it — takes the fast cached path here.
                if (isFountainPen && action.data.rotation == 0f && action.data.clipHoles.isEmpty()) {
                    drawWithBitmapCache(canvas, action, action.data.strokeWidth * 2f) { c, item ->
                        c.drawPath(item.data.buildFountainRibbonPath(), Paint(item.paint).apply { style = Paint.Style.FILL; pathEffect = null })
                    }
                    return
                }
                val renderPath = when { isCalligraphyPen -> action.data.buildCalligraphyRibbonPath(); isFountainPen -> action.data.buildFountainRibbonPath(); else -> action.path }
                val renderPaint = if (isCalligraphyPen || isFountainPen) Paint(action.paint).apply { style = Paint.Style.FILL; pathEffect = null } else action.paint
                if (action.data.rotation != 0f) {
                    val b = getBounds(action)
                    if (b != null) {
                        val cx = (b[0] + b[2]) / 2f; val cy = (b[1] + b[3]) / 2f
                        canvas.save(); canvas.rotate(action.data.rotation, cx, cy)
                        if (action.data.clipHoles.isEmpty()) action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }
                        if (action.data.clipHoles.isEmpty()) canvas.drawPath(renderPath, renderPaint); canvas.restore()
                    } else {
                        if (action.data.clipHoles.isEmpty()) action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }
                        if (action.data.clipHoles.isEmpty()) canvas.drawPath(renderPath, renderPaint)
                    }
                } else {
                    if (action.data.clipHoles.isEmpty()) action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }
                    if (action.data.clipHoles.isEmpty()) canvas.drawPath(renderPath, renderPaint)
                }
                // Punch out pixel-erase holes using saveLayer + PorterDuff.CLEAR
                if (action.data.clipHoles.isNotEmpty()) {
                    val b = getBounds(action)
                    val rect = if (b != null) android.graphics.RectF(b[0]-1f, b[1]-1f, b[2]+1f, b[3]+1f)
                               else android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
                    val sc = canvas.saveLayer(rect, null)
                    // Redraw shape into isolated layer (with rotation if needed)
                    val rot2 = action.data.rotation
                    if (rot2 != 0f && b != null) {
                        val cx2=(b[0]+b[2])/2f; val cy2=(b[1]+b[3])/2f
                        canvas.save(); canvas.rotate(rot2, cx2, cy2)
                        action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }
                        canvas.drawPath(renderPath, renderPaint); canvas.restore()
                        // Punch holes also rotated with item
                        val holePaint = _holePaint
                        canvas.save(); canvas.rotate(rot2, cx2, cy2)
                        for (h in action.data.clipHoles) canvas.drawCircle(h[0], h[1], h[2], holePaint)
                        canvas.restore()
                    } else {
                        action.data.toFillPaint()?.let { canvas.drawPath(action.path, it) }
                        canvas.drawPath(renderPath, renderPaint)
                        val holePaint = _holePaint
                        for (h in action.data.clipHoles) canvas.drawCircle(h[0], h[1], h[2], holePaint)
                    }
                    canvas.restoreToCount(sc)
                }
                // Cyan selection overlay for MULTISELECT — rotated same as the item
                if (currentTool == Tool.MULTISELECT) {
                    val allSel = (selectedItems + setOfNotNull(selectedItem)).toSet()
                    if (action in allSel) {
                        val cyanP = Paint().apply {
                            color = android.graphics.Color.parseColor("#CC00BCD4")
                            style = Paint.Style.STROKE
                            strokeWidth = (action.paint.strokeWidth + 6f / scaleFactor).coerceAtLeast(6f / scaleFactor)
                            isAntiAlias = true; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
                        }
                        val rot = action.data.rotation
                        if (rot != 0f) {
                            val b = getBounds(action)
                            if (b != null) { val cx=(b[0]+b[2])/2f; val cy=(b[1]+b[3])/2f; canvas.save(); canvas.rotate(rot,cx,cy); canvas.drawPath(action.path,cyanP); canvas.restore() }
                            else canvas.drawPath(action.path, cyanP)
                        } else canvas.drawPath(action.path, cyanP)
                    }
                }
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
        val labelStr = d.displayLabel(autoRefPixelLen, mmPerWorldUnit())
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
        val supplementary = parts.getOrNull(2) == "true"

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
        // Was rebuilding the entire hatch (potentially thousands of drawLine/drawCircle calls
        // for dense/small hatch spacing, PLUS a fresh Bitmap.createBitmap allocation) on every
        // single frame this fill was on screen — exactly the same "recompute forever" mistake
        // fixed earlier for Calligraphy/Fountain strokes. hatchPattern/hatchColor/hatchScale
        // are only ever set once at creation (no live hatch-editing exists), so caching the
        // final composited result is safe indefinitely.
        item.hatchRenderCache?.let { canvas.drawBitmap(it, null, RectF(l, t, r, b), null); return }
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
            item.hatchRenderCache = hatchBmp
            canvas.drawBitmap(hatchBmp, null, RectF(l, t, r, b), null)
        } else {
            // Shape mask still loading asynchronously — draw directly this one time WITHOUT
            // caching (caching now would lock in a render made before the mask was ready).
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

    // Cache brush strokes as world-space bitmaps at a fixed resolution (CACHE_SCALE px per world unit).
    // The bitmap is drawn via drawBitmap(bmp, srcRect, dstRect) where dstRect is the world bbox —
    // so the already-transformed canvas scales it correctly at any zoom level WITHOUT re-rendering.
    // Cache is NEVER invalidated by zoom/pan — only when stroke data actually changes.
    private val CACHE_SCALE = 2f
    private val MAX_CACHE_BYTES = 64L * 1024 * 1024  // 64 MB max total brush cache
    private fun pruneBrushCache() {
        var totalBytes = 0L
        val cached = actions.filterIsInstance<StrokeItem>().filter { it.cacheValid && it.cachedBitmap != null }
        for (s in cached) totalBytes += (s.cachedBitmap!!.byteCount).toLong()
        if (totalBytes > MAX_CACHE_BYTES) {
            // Free oldest caches (those furthest from viewport)
            val vl = -translateX / scaleFactor; val vt = -translateY / scaleFactor
            val vr = vl + width / scaleFactor; val vb = vt + height / scaleFactor
            val sorted = cached.sortedByDescending { s ->
                val b = getBounds(s); if (b == null) Float.MAX_VALUE
                else maxOf(b[0] - vr, vl - b[2], 0f) + maxOf(b[1] - vb, vt - b[3], 0f)
            }
            var freed = 0L
            for (s in sorted) {
                if (totalBytes - freed <= MAX_CACHE_BYTES * 3 / 4) break
                freed += s.cachedBitmap!!.byteCount.toLong()
                s.invalidateCache()
            }
        }
        pruneFillBitmaps()
    }

    // Releases in-memory bitmaps for FillItems well outside the current viewport, so a document
    // with thousands of fills/hatches doesn't grow unbounded in memory as the user scrolls
    // around during a session. The disk-backed bitmapCache still holds recently-used bitmaps
    // and reloads them quickly if scrolled back into view — this only clears each FillItem's
    // OWN direct reference, which otherwise bypasses that cache entirely once set.
    private fun pruneFillBitmaps() {
        val fills = actions.filterIsInstance<FillItem>().filter { it.bitmap != null || it.hatchRenderCache != null }
        if (fills.size <= 40) return  // small documents: no need to churn memory at all
        val vl = -translateX / scaleFactor; val vt = -translateY / scaleFactor
        val vr = vl + width / scaleFactor; val vb = vt + height / scaleFactor
        val marginX = (vr - vl) * 1.5f; val marginY = (vb - vt) * 1.5f
        for (f in fills) {
            val outside = f.x + f.w < vl - marginX || f.x > vr + marginX || f.y + f.h < vt - marginY || f.y > vb + marginY
            if (outside && !dirtyFillItems.contains(f)) { f.bitmap = null; f.hatchRenderCache = null }  // never release a fill mid-erase-gesture
        }
    }

    // Generic version of the brush-cache pattern above, for any stroke type whose render is
    // expensive to recompute every frame. Without this, a finalized Calligraphy/Fountain
    // stroke was rebuilding its entire smoothed ribbon geometry from scratch on EVERY single
    // onDraw call — which fires constantly (panning, zooming, or just drawing anything else
    // anywhere on the page) — so cost grew with (stroke count × frame rate), which is exactly
    // what "gets extremely slow after a few strokes" means. Render happens once into an
    // off-screen bitmap; every frame after that is a single drawBitmap call regardless of how
    // complex the underlying geometry was.
    private fun drawWithBitmapCache(canvas: Canvas, item: StrokeItem, pad: Float, render: (Canvas, StrokeItem) -> Unit) {
        if (!item.cacheValid || item.cachedBitmap == null) {
            val bounds = getBounds(item) ?: run { render(canvas, item); return }
            val wl = bounds[0] - pad; val wt = bounds[1] - pad
            val wr = bounds[2] + pad; val wb = bounds[3] + pad
            val bmpW = ((wr - wl) * CACHE_SCALE).toInt().coerceIn(1, 4096)
            val bmpH = ((wb - wt) * CACHE_SCALE).toInt().coerceIn(1, 4096)
            if (bmpW.toLong() * bmpH * 4 > 16L * 1024 * 1024) { render(canvas, item); return }
            item.cachedBitmap?.recycle()
            try {
                val bmp = android.graphics.Bitmap.createBitmap(bmpW, bmpH, android.graphics.Bitmap.Config.ARGB_8888)
                val bc = Canvas(bmp)
                bc.translate(-wl * CACHE_SCALE, -wt * CACHE_SCALE)
                bc.scale(CACHE_SCALE, CACHE_SCALE)
                render(bc, item)
                item.cachedBitmap = bmp
                item.cacheLeft = wl; item.cacheTop = wt
                item.cacheRight = wr; item.cacheBottom = wb
                item.cacheValid = true
            } catch (e: OutOfMemoryError) { render(canvas, item); return }
        }
        val bmp = item.cachedBitmap ?: return
        val dst = android.graphics.RectF(item.cacheLeft, item.cacheTop, item.cacheRight, item.cacheBottom)
        canvas.drawBitmap(bmp, null, dst, null)
    }

    private fun drawBrushStrokeWithCache(canvas: Canvas, item: StrokeItem) {
        if (!CACHED_BRUSH_STYLES.contains(item.data.brushStyle)) {
            drawBrushStroke(canvas, item); return
        }
        if (!item.cacheValid || item.cachedBitmap == null) {
            val bounds = getBounds(item) ?: run { drawBrushStroke(canvas, item); return }
            val pad = item.data.strokeWidth * 2f
            val wl = bounds[0] - pad; val wt = bounds[1] - pad
            val wr = bounds[2] + pad; val wb = bounds[3] + pad
            val bmpW = ((wr - wl) * CACHE_SCALE).toInt().coerceIn(1, 4096)
            val bmpH = ((wb - wt) * CACHE_SCALE).toInt().coerceIn(1, 4096)
            // Skip caching if bitmap would exceed 16 MB — fall back to direct render
            if (bmpW.toLong() * bmpH * 4 > 16L * 1024 * 1024) { drawBrushStroke(canvas, item); return }
            item.cachedBitmap?.recycle()
            try {
                val bmp = android.graphics.Bitmap.createBitmap(bmpW, bmpH, android.graphics.Bitmap.Config.ARGB_8888)
                val bc = Canvas(bmp)
                bc.translate(-wl * CACHE_SCALE, -wt * CACHE_SCALE)
                bc.scale(CACHE_SCALE, CACHE_SCALE)  // render at fixed world scale, zoom-independent
                drawBrushStroke(bc, item)
                item.cachedBitmap = bmp
                item.cacheLeft = wl; item.cacheTop = wt
                item.cacheRight = wr; item.cacheBottom = wb
                item.cacheValid = true
            } catch (e: OutOfMemoryError) { drawBrushStroke(canvas, item); return }
        }
        val bmp = item.cachedBitmap ?: return
        // dstRect is in world coords — the canvas transform (translate+scale) maps to screen correctly
        val dst = android.graphics.RectF(item.cacheLeft, item.cacheTop, item.cacheRight, item.cacheBottom)
        canvas.drawBitmap(bmp, null, dst, null)
    }

    private fun drawBrushStroke(canvas: Canvas, item: StrokeItem) {
        val pts = item.data.points; if (pts.size < 2) return
        val sw = item.data.strokeWidth; val col = item.data.color
        // opF: user opacity as 0.0-1.0 scale. Brushes use fixed internal base alphas
        // so the character is preserved, and opF just scales overall density/visibility.
        val opF = item.data.opacity / 255f
        val op = item.data.opacity  // kept for legacy uses
        val bStyle = item.data.brushStyle
        val rand = java.util.Random((pts[0] * 1000 + pts[1]).toLong())
        val pointsRaw = item.getPoints()
        val points = pointsRaw
        // bp: base alpha is fixed per brush, scaled by user opacity
        fun bp(baseAlpha: Int = 220, w: Float = sw) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=col; this.style=Paint.Style.STROKE; strokeWidth=w
            strokeJoin=Paint.Join.ROUND; strokeCap=Paint.Cap.ROUND
            alpha=(baseAlpha * opF).toInt().coerceIn(1, 255)
        }
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
                canvas.drawPath(item.path, bp(32, sw*2.2f))
                canvas.drawPath(item.path, bp(24, sw*1.4f))
                canvas.drawPath(item.path, bp(18, sw*0.7f))
                canvas.drawPath(item.path, bp(40, sw*0.07f))
            }
            BrushStyle.CHARCOAL -> {
                val r2=java.util.Random(pts[0].toLong())
                val cp=bp(55, sw*0.6f)
                repeat(6) { canvas.save(); canvas.translate((r2.nextFloat()-0.5f)*sw*0.5f,(r2.nextFloat()-0.5f)*sw*0.3f); canvas.drawPath(item.path, cp); canvas.restore() }
                val sp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(50*opF).toInt().coerceAtLeast(1)}
                val r3=java.util.Random(pts[1].toLong())
                points.forEach { pt -> if(r3.nextFloat()<0.25f) canvas.drawCircle(pt.x+(r3.nextFloat()-0.5f)*sw*0.7f, pt.y+(r3.nextFloat()-0.5f)*sw*0.7f, sw*0.04f+r3.nextFloat()*sw*0.04f, sp) }
            }
            BrushStyle.CRAYON -> {
                canvas.drawPath(item.path, bp(200).apply{pathEffect=android.graphics.DashPathEffect(floatArrayOf(sw*0.8f,sw*0.1f),0f)})
                val gp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=col;this.style=Paint.Style.FILL;alpha=(35*opF).toInt().coerceAtLeast(1)}
                val r2=java.util.Random(pts[0].toLong())
                points.forEach { pt -> repeat(2){canvas.drawCircle(pt.x+(r2.nextFloat()-0.5f)*sw,pt.y+(r2.nextFloat()-0.5f)*sw*0.8f,sw*0.05f+r2.nextFloat()*sw*0.06f,gp)} }
            }
            BrushStyle.NEON -> {
                canvas.drawPath(item.path, bp(60, sw*3f).apply{maskFilter=android.graphics.BlurMaskFilter(sw*2f, android.graphics.BlurMaskFilter.Blur.NORMAL)})
                canvas.drawPath(item.path, Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;this.style=Paint.Style.STROKE;strokeWidth=sw*0.35f;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND;alpha=(200*opF).toInt().coerceAtLeast(1)})
            }
            BrushStyle.DRY_BRUSH -> {
                val r2=java.util.Random(pts[0].toLong())
                repeat(7) { i -> val off=(i-3f)*sw*0.12f; canvas.save(); canvas.translate(off,0f); canvas.drawPath(item.path, bp((80+r2.nextInt(80)), sw*0.07f+r2.nextFloat()*sw*0.05f).apply{pathEffect=android.graphics.DashPathEffect(floatArrayOf(sw*(0.3f+r2.nextFloat()*0.5f),sw*(0.1f+r2.nextFloat()*0.25f)),r2.nextFloat()*sw)}); canvas.restore() }
            }
            BrushStyle.SPRAY -> drawParticleBrush(canvas, item)
            BrushStyle.GRASS -> {
                val p=bp(200, sw*0.07f).apply{strokeCap=Paint.Cap.ROUND}
                val r2=java.util.Random(pts[0].toLong())
                points.forEachIndexed { i,pt -> if(i%3==0){val h=sw*(1.2f+r2.nextFloat()*0.8f);val lean=(r2.nextFloat()-0.5f)*sw*0.5f;val p2=android.graphics.Path();p2.moveTo(pt.x,pt.y);p2.quadTo(pt.x+lean*0.5f,pt.y-h*0.6f,pt.x+lean,pt.y-h);canvas.drawPath(p2,p)} }
            }
            BrushStyle.FIRE -> {
                val r2=java.util.Random(pts[0].toLong())
                points.forEachIndexed { i,pt -> if(i%3==0){val fc=if(r2.nextFloat()>0.5f) android.graphics.Color.argb((200*opF).toInt().coerceAtLeast(1),255,80+r2.nextInt(120),0) else android.graphics.Color.argb((200*opF).toInt().coerceAtLeast(1),255,180+r2.nextInt(75),0);val fp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=fc;this.style=Paint.Style.FILL;maskFilter=android.graphics.BlurMaskFilter(sw*0.3f, android.graphics.BlurMaskFilter.Blur.NORMAL)};val h=sw*(0.8f+r2.nextFloat()*1.0f);val lean=(r2.nextFloat()-0.5f)*sw*0.5f;val p2=android.graphics.Path();p2.moveTo(pt.x-sw*0.25f,pt.y);p2.quadTo(pt.x+lean,pt.y-h*0.5f,pt.x,pt.y-h);p2.quadTo(pt.x-lean*0.4f,pt.y-h*0.5f,pt.x+sw*0.25f,pt.y);p2.close();canvas.drawPath(p2,fp)} }
            }
            else -> canvas.drawPath(item.path, item.paint)
        }
    }
    private val rng = java.util.Random(42L)
    private fun drawParticleBrush(canvas: Canvas, item: StrokeItem) {
        val pts = item.data.points; if (pts.size < 2) return
        val opF2 = item.data.opacity / 255f
        val p = Paint(item.paint); p.style = Paint.Style.FILL; p.pathEffect = null; p.maskFilter = null
        // Spray base alpha: fixed so it looks good at 100% opacity, scaled by user opacity
        p.alpha = (180 * opF2).toInt().coerceAtLeast(1)
        val sw = item.data.strokeWidth; val style = item.data.brushStyle
        // Density per sampled point — kept low because we batch all dots into one Path
        val density = 22
        val spread = sw * 1.2f
        // dotR sized so particles are clearly visible — sw*0.3 means at sw=30 each dot is ~9 world units
        val dotR = sw * 0.3f
        // Minimum spacing between sampled points — avoids redundant dots on slow/stationary strokes
        val minStep = sw * 0.3f; val minStep2 = minStep * minStep
        val seed = (pts[0] + pts[1]).toLong()
        val rand = java.util.Random(seed)
        // Batch all circles into a single Path so the GPU issues one draw call instead of N
        val batchPath = android.graphics.Path()
        var lastSx = Float.MAX_VALUE; var lastSy = Float.MAX_VALUE
        var i = 0; while (i < pts.size - 2) {
            val cx = pts[i]; val cy = pts[i+1]
            val dx2 = cx - lastSx; val dy2 = cy - lastSy
            // Skip points too close to the last sampled one
            if (dx2*dx2 + dy2*dy2 >= minStep2 || lastSx == Float.MAX_VALUE) {
                lastSx = cx; lastSy = cy
                repeat(density) {
                    val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
                    val dist  = rand.nextGaussian().toFloat().coerceIn(-2f, 2f) * spread * 0.5f
                    val ox = kotlin.math.cos(angle) * dist; val oy = kotlin.math.sin(angle) * dist
                    val r = dotR * (0.3f + rand.nextFloat() * 0.7f)
                    batchPath.addCircle(cx + ox, cy + oy, r, android.graphics.Path.Direction.CW)
                }
            }
            i += 2
        }
        canvas.drawPath(batchPath, p)
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

    private var drawCount = 0
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (++drawCount % 60 == 0) pruneBrushCache()
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        drawBackground(canvas)
        // Only draw items visible in current viewport — massive speedup for large notes
        val visibleItems = itemsInViewport()

        // ── Batched LINE rendering ──────────────────────────────────────────────
        // Group simple unrotated LINE strokes by paint key and draw as a single
        // canvas.drawLines() call. This is 10-20x faster than one drawPath() each.
        // Only applies to: plain continuous lines, no rotation, no clip holes.
        val lineBuckets = LinkedHashMap<Long, Pair<Paint, ArrayList<Float>>>(16)
        val remainingItems = ArrayList<Any>(visibleItems.size)
        val inMultiSel = currentTool == Tool.MULTISELECT
        val allSel = if (inMultiSel) (selectedItems + setOfNotNull(selectedItem)).toSet() else emptySet<Any>()

        for (item in visibleItems) {
            if (item is StrokeItem && item.data.type == Tool.LINE &&
                item.data.points.size == 4 && item.data.rotation == 0f &&
                item.data.clipHoles.isEmpty() && item.data.lineType == LineType.CONTINUOUS &&
                !inMultiSel) {
                val paint = item.paint
                val key = (paint.color.toLong() and 0xFFFFFFFFL) xor
                          (java.lang.Float.floatToIntBits(paint.strokeWidth).toLong() shl 32) xor
                          (paint.alpha.toLong() shl 48)
                val (_, pts) = lineBuckets.getOrPut(key) { Pair(paint, ArrayList(64)) }
                val p = item.data.points
                pts.add(p[0]); pts.add(p[1]); pts.add(p[2]); pts.add(p[3])
            } else {
                remainingItems.add(item)
            }
        }
        for ((_, pair) in lineBuckets) {
            val arr = pair.second
            val fa = FloatArray(arr.size) { arr[it] }
            canvas.drawLines(fa, pair.first)
        }
        // Draw all non-batched items (shapes, pen strokes, text, images, everything else)
        for (action in remainingItems) drawActionItem(canvas, action, true)
        currentItem?.let {
            val isCalligraphyPen = it.data.type == Tool.PEN && it.data.penStyle == PenStyle.CALLIGRAPHY
            val isFountainPen = it.data.type == Tool.PEN && it.data.penStyle == PenStyle.FOUNTAIN && it.data.widths.size >= 2
            val isPencilPen = it.data.type == Tool.PEN && it.data.penStyle == PenStyle.PENCIL && it.data.widths.size >= 2
            when {
                // Same native-stroke renderer used for the finalized stroke, so live preview
                // and final look match exactly with no separate code path to drift out of sync.
                isCalligraphyPen -> it.data.drawCalligraphyStroke(canvas, it.paint)
                isFountainPen -> canvas.drawPath(it.data.buildFountainRibbonPath(), Paint(it.paint).apply { style = Paint.Style.FILL; pathEffect = null })
                isPencilPen -> it.data.drawPencilStroke(canvas, it.paint)
                // Was falling through to the flat-width `else` below, so Brush strokes only
                // showed their real per-point width variation once finalized (via
                // drawBrushStrokeWithCache) — the live stroke looked uniform-width the whole
                // time you were actually drawing it, then visibly "changed" the instant you
                // lifted the pen. Calling the same drawBrushStroke used for the finalized
                // render (not the cached version — caching a stroke that's still changing every
                // frame would be pointless) makes the live preview match from the first pixel.
                // invalidateCache() first: drawBrushStroke reads item.getPoints(), which caches
                // its result — without invalidating every frame here, it'd keep returning
                // whatever the point list looked like on the very first frame of the stroke.
                it.data.type == Tool.BRUSH -> { it.invalidateCache(); drawBrushStroke(canvas, it) }
                else -> canvas.drawPath(it.path, it.paint)
            }
        }
        drawSelection(canvas)
        drawArcHandles(canvas)
        drawAutoSelectOverlay(canvas)
        drawTableOverlay(canvas)
        drawExportWindowOverlay(canvas)
        // Draw persistent snap markers on all committed strokes — always visible when snap is
        // enabled, so user knows exactly where snap points are before touching the screen.
        if (snapEnabled) {
            if (snapMarkersActionCount != actions.size || snapMarkersDirty) rebuildSnapMarkerCache()
        }
        if (snapEnabled) drawSnapMarkers(canvas)
        if (currentTool == Tool.POLYLINE) drawPolylinePreview(canvas)
        canvas.restore()
        drawCursor(canvas)
    }

    private var hasInitialLayout = false
    private var lastLayoutWidth = 0
    private var stableLayoutHeight = 0  // frozen height used for page-size math, ignores keyboard resize
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (width > 0 && height > 0) {
            val isFirstLayout = !hasInitialLayout
            val widthChanged = width != lastLayoutWidth
            lastLayoutWidth = width
            // Only update the height used for page-size calculations on first layout or real
            // width changes (rotation) — NOT on keyboard-triggered height-only resize. This
            // keeps pages a stable, consistent size regardless of keyboard state.
            if (isFirstLayout || widthChanged || stableLayoutHeight == 0) {
                stableLayoutHeight = height
            }
            convenientPageW = width.toFloat() * 0.82f
            convenientPageH = stableLayoutHeight.toFloat() * 1.1f
            if (isFirstLayout) {
                hasInitialLayout = true
                when (canvasMode) {
                    CanvasMode.CONVENIENT -> {
                        val margin = 16f
                        scaleFactor = ((width.toFloat() - margin * 2f) / pageWidthPx()).coerceAtMost(1f)
                        translateX = (width - pageWidthPx() * scaleFactor) / 2f
                        translateY = margin
                        clampTranslation(); invalidate()
                    }
                    CanvasMode.INFINITE -> {}
                    else -> {
                        val margin = 20f
                        scaleFactor = (width.toFloat() - margin * 2f) / pageWidthPx()
                        translateX = margin; translateY = margin
                        clampTranslation(); invalidate()
                    }
                }
            } else if (widthChanged) {
                // Only re-clamp on real layout changes (rotation), not keyboard open/close
                clampTranslation(); invalidate()
            }
        }
    }
    // Called when canvasMode changes — forces position reset on next layout
    fun resetLayoutPosition() { hasInitialLayout = false }

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

    // TextItem.y means the BOTTOM of the rendered text everywhere in this file (see
    // drawTextItem/getBounds/findTextItemAt, which all compute top = item.y - height) — never
    // the top. Any caller that wants to keep a text item's TOP fixed while its content (and
    // therefore height) keeps changing — a streaming Gemini answer, a large paste — needs to
    // recompute item.y = desiredTopY + currentHeight every time the content changes, rather
    // than setting item.y once and leaving it. Otherwise the rendered top silently climbs
    // upward as the content grows, since the same fixed "bottom" now has more height above it.
    fun textItemHeight(item: TextItem): Float {
        val tp = TextPaint(); tp.textSize = item.size
        try { tp.typeface = typefaceFromFamily(item.fontFamily) } catch (e: Exception) {}
        val ww = textWrapWidth(item)
        return try {
            StaticLayout.Builder.obtain(item.text, 0, item.text.length, tp, ww).setIncludePad(true).build().height.toFloat().coerceAtLeast(item.size * 1.2f)
        } catch (e: Exception) { item.size * 1.2f }
    }
    fun repositionTextItemTop(item: TextItem, desiredTopY: Float) {
        item.y = desiredTopY + textItemHeight(item)
        markSpatialDirty()
    }

    private fun drawTextItem(canvas: Canvas, item: TextItem) {
        val isLink = item.linkTarget != null
        val tp = TextPaint(); tp.color = if (isLink) Color.parseColor("#1565C0") else item.color; tp.alpha = item.opacity; tp.textSize = item.size; tp.isAntiAlias = true
        try { tp.typeface = typefaceFromFamily(item.fontFamily) } catch (e: Exception) {}
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
        // ── MULTISELECT drawing (only pink box — items show cyan overlay via drawActionItem) ──
        if (currentTool == Tool.MULTISELECT) {
            val allItems = (selectedItems + setOfNotNull(selectedItem)).toSet()
            if (allItems.isEmpty()) return

            // During an active GROUP-mode rotation drag, draw the box RIGIDLY: use the bounds
            // captured at drag-start and apply a canvas rotation, rather than recomputing a
            // fresh axis-aligned union every frame (which grows/shrinks as the now-rotated
            // items' AABBs expand, making the box look like it's resizing instead of rotating).
            val isGroupRotating = groupActiveHandle == HandleType.ROTATE && !multiSelectIndividual
            val gb = (if (isGroupRotating) groupOrigBounds else null) ?: computeGroupBounds() ?: return
            val rotForDraw = if (isGroupRotating) pinkGroupRotation else 0f
            val gcxForRotate = (gb[0]+gb[2])/2f; val gcyForRotate = (gb[1]+gb[3])/2f
            if (rotForDraw != 0f) canvas.save().also { canvas.rotate(rotForDraw, gcxForRotate, gcyForRotate) }
            val hr = 28f / scaleFactor  // larger handles
            val pinkP = Paint().apply { color = android.graphics.Color.parseColor("#E91E8C"); style = Paint.Style.STROKE; strokeWidth = 2.5f/scaleFactor; isAntiAlias = true }
            canvas.drawRect(gb[0], gb[1], gb[2], gb[3], pinkP)
            val hF = Paint().apply { style = Paint.Style.FILL; color = android.graphics.Color.parseColor("#E91E8C"); isAntiAlias = true }
            val hS = Paint().apply { style = Paint.Style.STROKE; color = android.graphics.Color.WHITE; strokeWidth = 2.5f/scaleFactor; isAntiAlias = true }
            val gcx = (gb[0]+gb[2])/2f; val gcy = (gb[1]+gb[3])/2f
            // 8 resize handles
            for ((hx,hy) in listOf(gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1], gb[0] to gcy, gb[2] to gcy, gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3])) {
                canvas.drawCircle(hx, hy, hr, hF); canvas.drawCircle(hx, hy, hr, hS)
            }
            // Rotation handle (green circle with arc symbol — matches single-select design)
            val rotY = gb[1] - 90f/scaleFactor
            val rotF = Paint().apply { style = Paint.Style.FILL; color = android.graphics.Color.parseColor("#34C759"); isAntiAlias = true }
            canvas.drawLine(gcx, gb[1], gcx, rotY+hr, pinkP)
            canvas.drawCircle(gcx, rotY, hr*1.2f, rotF); canvas.drawCircle(gcx, rotY, hr*1.2f, hS)
            // Arc symbol inside green circle
            val arcSP = Paint().apply { style = Paint.Style.STROKE; color = android.graphics.Color.WHITE; strokeWidth = 2.5f/scaleFactor; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
            val arcR = hr*0.65f
            canvas.drawArc(android.graphics.RectF(gcx-arcR, rotY-arcR, gcx+arcR, rotY+arcR), -30f, 240f, false, arcSP)
            canvas.drawLine(gcx+arcR*0.5f, rotY-arcR*0.85f, gcx+arcR, rotY-arcR*0.3f, arcSP)
            // Delete handle (red, top-right)
            val delX = gb[2]+hr*4f; val delY = gb[1]-hr*4f
            val delF = Paint().apply { style = Paint.Style.FILL; color = android.graphics.Color.parseColor("#FF3B30"); isAntiAlias = true }
            val delS = Paint().apply { style = Paint.Style.STROKE; color = android.graphics.Color.WHITE; strokeWidth = 3f/scaleFactor; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
            canvas.drawCircle(delX, delY, hr*1.5f, delF); canvas.drawCircle(delX, delY, hr*1.5f, hS)
            val d2 = hr*0.9f; canvas.drawLine(delX-d2,delY-d2,delX+d2,delY+d2,delS); canvas.drawLine(delX+d2,delY-d2,delX-d2,delY+d2,delS)
            // Move handle (center arrow icon)
            val moveF = Paint().apply { style = Paint.Style.FILL; color = android.graphics.Color.parseColor("#2196F3"); isAntiAlias = true }
            canvas.drawCircle(gcx, gcy, hr*1.2f, moveF); canvas.drawCircle(gcx, gcy, hr*1.2f, hS)
            val ap = Paint().apply { style = Paint.Style.STROKE; color = android.graphics.Color.WHITE; strokeWidth = 2.5f/scaleFactor; isAntiAlias = true; strokeCap = Paint.Cap.ROUND }
            val a = hr*0.6f
            canvas.drawLine(gcx-a, gcy, gcx+a, gcy, ap); canvas.drawLine(gcx, gcy-a, gcx, gcy+a, ap)
            if (rotForDraw != 0f) canvas.restore()
            // NOTE: Indiv/Group toggle is shown as a screen-space button via MainActivity
            return
        }
        // ── END MULTISELECT drawing ─────────────────────────────────────────────

        val item = selectedItem ?: return
        if (item is TextItem) {
            val tp = android.text.TextPaint(); tp.textSize = item.size
            try { tp.typeface = typefaceFromFamily(item.fontFamily) } catch (e: Exception) {}
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
        val bounds = getBoundsRaw(item) ?: return  // unrotated bbox — canvas.rotate() below handles orientation
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
            (item is StrokeItem && (BBOX_RESIZE_SHAPES.contains(item.data.type) || STROKE_SCALE_SHAPES.contains(item.data.type)))
        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)
        if (isBbox) {
            for ((_, pos) in bboxHandlePositions(bounds)) {
                hFill.color = Color.WHITE
                canvas.drawCircle(pos.first, pos.second, hr, hFill)
                canvas.drawCircle(pos.first, pos.second, hr, hStroke)
            }
        }
        // Endpoint handles drawn ON TOP of bbox handles for endpoint shapes so both work
        if (isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
            hFill.color = Color.WHITE
            val p0x = item.data.points[0]; val p0y = item.data.points[1]
            // For PEN (multi-point), use last point; for 2-point shapes use points[2,3]
            val lastIdx = item.data.points.size - 2
            val p1x = item.data.points[lastIdx]; val p1y = item.data.points[lastIdx + 1]
            canvas.drawCircle(p0x, p0y, hr, hFill); canvas.drawCircle(p0x, p0y, hr, hStroke)
            canvas.drawCircle(p1x, p1y, hr, hFill); canvas.drawCircle(p1x, p1y, hr, hStroke)
        }
        val canRotate = item is ImageItem || item is TextItem || item is AudioItem || item is StrokeItem
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
                try { tp.typeface = typefaceFromFamily(item.fontFamily) } catch (e: Exception) {}
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
                } else if (SHAPE_TOOLS.contains(item.data.type) && pts.size == 4) {
                    // 2-point shapes (LINE, RECT, CIRCLE, TRIANGLE etc.) — fast path
                    floatArrayOf(minOf(pts[0], pts[2]), minOf(pts[1], pts[3]), maxOf(pts[0], pts[2]), maxOf(pts[1], pts[3]))
                } else {
                    var minX = pts[0]; var maxX = pts[0]; var minY = pts[1]; var maxY = pts[1]; var i = 0
                    while (i + 1 < pts.size) {
                        minX = minOf(minX, pts[i]); maxX = maxOf(maxX, pts[i])
                        minY = minOf(minY, pts[i + 1]); maxY = maxOf(maxY, pts[i + 1]); i += 2
                    }
                    if (item.data.type == Tool.BRUSH && item.data.brushStyle in setOf(BrushStyle.SPRAY, BrushStyle.FIRE, BrushStyle.GRASS)) {
                        val pad = item.data.strokeWidth * 2.5f
                        minX -= pad; maxX += pad; minY -= pad; maxY += pad
                    }
                    floatArrayOf(minX, minY, maxX, maxY)
                }.let { b ->
                    // If item has a rotation angle, expand to axis-aligned bbox of the ROTATED shape
                    val rot = item.data.rotation
                    if (rot == 0f || b == null) b
                    else {
                        val cx = (b[0]+b[2])/2f; val cy = (b[1]+b[3])/2f
                        val rad = Math.toRadians(rot.toDouble())
                        val cos = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
                        val sin = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
                        val hw = (b[2]-b[0])/2f; val hh = (b[3]-b[1])/2f
                        // Rotated AABB half-extents
                        val newHw = hw*cos + hh*sin; val newHh = hw*sin + hh*cos
                        floatArrayOf(cx-newHw, cy-newHh, cx+newHw, cy+newHh)
                    }
                }
            }
            is DimensionItem -> {
                // Was missing entirely — fell through to `else -> null`, which
                // rebuildSpatialIndex() below silently treated as bounds (0,0,0,0). Every
                // dimension line in the document, no matter where it actually was, got filed
                // into the spatial grid cell at the world origin — meaning the normal viewport
                // query could never find them there, which is exactly why itemsInViewport()
                // below had a separate unconditional "just include every DimensionItem"
                // fallback. With real bounds here, that workaround is no longer needed.
                val minX = minOf(item.x1, item.x2); val maxX = maxOf(item.x1, item.x2)
                val minY = minOf(item.y1, item.y2); val maxY = maxOf(item.y1, item.y2)
                val pad = kotlin.math.abs(item.offset) + item.fontSize * 2f + item.arrowSize * 2f + 20f
                floatArrayOf(minX - pad, minY - pad, maxX + pad, maxY + pad)
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
                item.invalidateCache()
                markSpatialDirty()  // spatial grid must update or hit testing fails at new position
                // Also translate clip holes so they move with the shape
                for (h in item.data.clipHoles) { h[0] += dx; h[1] += dy }
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
                if (STROKE_SCALE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
                    // Uniform scale from centroid using delta-drag (not absolute position).
                    // We compare finger distance from centroid NOW vs at the point the handle was grabbed.
                    // The ratio of those distances = the scale factor. This makes small drags = small changes.
                    val pts = item.data.points
                    var minX = pts[0]; var minY = pts[1]; var maxX = pts[0]; var maxY = pts[1]
                    var i2 = 0; while (i2 + 1 < pts.size) { minX = minOf(minX, pts[i2]); minY = minOf(minY, pts[i2+1]); maxX = maxOf(maxX, pts[i2]); maxY = maxOf(maxY, pts[i2+1]); i2 += 2 }
                    val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
                    val oldW = (maxX - minX).coerceAtLeast(1f); val oldH = (maxY - minY).coerceAtLeast(1f)
                    val halfW = oldW / 2f; val halfH = oldH / 2f
                    // Finger offset from centroid
                    val fx = wx - cx; val fy = wy - cy
                    // For each handle, the "fixed" opposite corner stays, and the dragged edge follows finger.
                    // Scale = finger_dist / original_half_size. Use only the relevant axis per handle.
                    val scaleX = when (handle) {
                        HandleType.ML -> if (halfW > 1f) (-fx / halfW).coerceIn(0.05f, 20f) else 1f
                        HandleType.MR -> if (halfW > 1f) (fx / halfW).coerceIn(0.05f, 20f) else 1f
                        HandleType.TL, HandleType.BL -> if (halfW > 1f) (-fx / halfW).coerceIn(0.05f, 20f) else 1f
                        HandleType.TR, HandleType.BR -> if (halfW > 1f) (fx / halfW).coerceIn(0.05f, 20f) else 1f
                        else -> 1f
                    }
                    val scaleY = when (handle) {
                        HandleType.TM -> if (halfH > 1f) (-fy / halfH).coerceIn(0.05f, 20f) else 1f
                        HandleType.BM -> if (halfH > 1f) (fy / halfH).coerceIn(0.05f, 20f) else 1f
                        HandleType.TL, HandleType.TR -> if (halfH > 1f) (-fy / halfH).coerceIn(0.05f, 20f) else 1f
                        HandleType.BL, HandleType.BR -> if (halfH > 1f) (fy / halfH).coerceIn(0.05f, 20f) else 1f
                        else -> 1f
                    }
                    val newHalfW = halfW * scaleX; val newHalfH = halfH * scaleY
                    // Shift centroid: the opposite (fixed) side stays put
                    val newCx = when (handle) {
                        HandleType.TL, HandleType.ML, HandleType.BL -> maxX - newHalfW  // right edge fixed
                        HandleType.TR, HandleType.MR, HandleType.BR -> minX + newHalfW  // left edge fixed
                        else -> cx
                    }
                    val newCy = when (handle) {
                        HandleType.TL, HandleType.TM, HandleType.TR -> maxY - newHalfH  // bottom edge fixed
                        HandleType.BL, HandleType.BM, HandleType.BR -> minY + newHalfH  // top edge fixed
                        else -> cy
                    }
                    if (newHalfW > 0.5f && newHalfH > 0.5f) {
                        var j = 0; while (j + 1 < pts.size) { pts[j] = newCx + (pts[j] - cx) * scaleX; pts[j+1] = newCy + (pts[j+1] - cy) * scaleY; j += 2 }
                        item.path = item.data.buildPath()
                    }
                } else if (BBOX_RESIZE_SHAPES.contains(item.data.type) && item.data.points.size >= 4) {
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
                    when (handle) {
                        HandleType.TL -> { item.data.points[0] = ux; item.data.points[1] = uy }
                        HandleType.BR -> {
                            // For PEN (multi-point), move the last point; for 2-point shapes move points[2,3]
                            val lastIdx3 = item.data.points.size - 2
                            item.data.points[lastIdx3] = ux; item.data.points[lastIdx3 + 1] = uy
                        }
                        else -> {}
                    }
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
                        try { tp.typeface = typefaceFromFamily(item.fontFamily) } catch (e: Exception) {}
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

    // Hit test using pre-sampled path points cached on StrokeItem.
    // First call per stroke builds the sample array via PathMeasure; subsequent calls are O(n) float scan.
    private fun pathHitTest(item: StrokeItem, x: Float, y: Float, r: Float): Boolean {
        // Inverse-rotate test point if item has rotation
        val testX: Float; val testY: Float
        val rot = item.data.rotation
        if (rot != 0f) {
            val b = getBounds(item)
            val cx = if (b != null) (b[0]+b[2])/2f else x; val cy = if (b != null) (b[1]+b[3])/2f else y
            val rad = Math.toRadians(-rot.toDouble())
            val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
            val dx = x-cx; val dy = y-cy
            testX = cx + dx*cos - dy*sin; testY = cy + dx*sin + dy*cos
        } else { testX = x; testY = y }
        val r2 = r * r
        val pts = item.getOrBuildSampledPath(r)
        var i = 0; while (i + 1 < pts.size) {
            val dx = pts[i] - testX; val dy = pts[i+1] - testY
            if (dx * dx + dy * dy <= r2) return true
            i += 2
        }
        return false
    }

    private fun findItemAt(x: Float, y: Float): Any? {
        val pad = (28f / scaleFactor).coerceAtLeast(8f)
        // Spatial index gives only nearby candidates — O(1) lookup instead of O(n) scan
        val candidates = itemsNear(x, y, pad * 6f)
        if (candidates.isEmpty()) return null
        // Build order map once for efficient sorting (avoid O(n) indexOf per candidate)
        val orderMap = HashMap<Any, Int>(candidates.size * 2)
        for (i in actions.indices) orderMap[actions[i]] = i
        // Single top-to-bottom pass (highest index = drawn last = on top) so whichever item is
        // ACTUALLY on top visually wins the tap. Previously strokes were tested in their own
        // pass before fills were even considered, so an outline stroke could "win" a tap just by
        // being nearby — even when a fill/hatch was drawn on top of it and should have won.
        for (a in candidates.sortedByDescending { orderMap[it] ?: -1 }) {
            when (a) {
                is StrokeItem -> {
                    val hitPad = if (a.data.type == Tool.BRUSH && a.data.brushStyle in setOf(BrushStyle.SPRAY, BrushStyle.FIRE, BrushStyle.GRASS))
                        pad + a.data.strokeWidth * 2.5f  // scattered dots/flames/blades land well off the raw anchor path
                    else pad + a.data.strokeWidth * 0.5f
                    if (pathHitTest(a, x, y, hitPad)) return a
                }
                is FillItem -> {
                    // Bbox check first (cheap), then actual pixel alpha at the tap point so
                    // tapping empty space just outside an irregular fill shape's bounding box
                    // doesn't accidentally select it.
                    if (x < a.x - pad || x > a.x + a.w + pad || y < a.y - pad || y > a.y + a.h + pad) continue
                    val bmp = a.bitmap ?: getOrLoadFillBitmap(a) ?: continue
                    val bx = ((x - a.x) / a.w * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                    val by = ((y - a.y) / a.h * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                    if ((bmp.getPixel(bx, by) ushr 24) != 0) return a
                }
                is AudioItem -> { if (distance(x, y, a.x, a.y) <= (a.radius + 12f) / scaleFactor) return a }
                is TableItem, is TextItem, is ImageItem, is DimensionItem -> {
                    val b = getBounds(a) ?: continue
                    if (x in (b[0] - pad)..(b[2] + pad) && y in (b[1] - pad)..(b[3] + pad)) return a
                }
            }
        }
        return null
    }

    fun findTextItemAtPublic(x: Float, y: Float): TextItem? = findTextItemAt(x, y)
    private fun findTextItemAt(x: Float, y: Float): TextItem? {
        for (a in actions.reversed()) {
            if (a is TextItem) {
                val tp = TextPaint(); tp.textSize = a.size
                try { tp.typeface = typefaceFromFamily(a.fontFamily) } catch (e: Exception) {}
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
    // Manual hold+drag for SELECT tool text items (including links)
    private var selectHoldDownX = 0f; private var selectHoldDownY = 0f
    private var selectHoldRawX = 0f; private var selectHoldRawY = 0f
    private var selectHoldDownTime = 0L; private var selectHoldItem: TextItem? = null
    private var selectHoldTriggered = false  // true once hold threshold passed and selection shown
    private var selectHoldMoved = false      // true if finger moved significantly before hold threshold
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
    // Default density for newly-created hatches, set from the Settings > Hatch Density
    // picker in MainActivity. Smaller = tighter spacing = more lines drawn per area (see the
    // `s = 8f * hatchScale` spacing calc in drawHatchPattern), which is what makes a "Fine"
    // hatch genuinely more expensive to first-render than a "Coarse" one, though after the
    // caching fix above that cost is only ever paid once per fill, not every frame.
    var pendingHatchScale: Float = 1f
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
                    if (cell == null) { tableIsActive = false; tableSelStart = null; tableSelEnd = null; activeTableItem = null; currentTool = Tool.SELECT; onInternalToolChange?.invoke(Tool.SELECT); invalidate(); return }
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
            // Pink group box handles — active for ANY tool when 2+ items selected (lasso, rect, multi)
            val allSel = (selectedItems + setOfNotNull(selectedItem)).toSet()
            val minForGroup = if (currentTool == Tool.MULTISELECT) 1 else 2
            if (allSel.size >= minForGroup) {
                val gb = computeGroupBounds()
                if (gb != null) {
                    val hr = 28f / scaleFactor
                    val gcx = (gb[0]+gb[2])/2f; val gcy = (gb[1]+gb[3])/2f
                    val rotY = gb[1] - 90f/scaleFactor
                    val delX = gb[2]+hr*4f; val delY = gb[1]-hr*4f
                    if (distance(wx,wy,delX,delY) <= hr*2.5f) {
                        for (it in allSel) { if (!(it is StrokeItem && it.data.isLocked)) actions.remove(it) }
                        selectedItems.clear(); selectedItem=null; markSpatialDirty(); onMultiSelectionChanged?.invoke(emptySet()); invalidate(); return
                    }
                    if (distance(wx,wy,gcx,rotY) <= 28f/scaleFactor) {
                        groupActiveHandle=HandleType.ROTATE; groupOrigGcx=gcx; groupOrigGcy=gcy
                        groupDragStartAngle=kotlin.math.atan2((wy-gcy).toDouble(),(wx-gcx).toDouble()).toFloat()
                        groupOrigBounds=gb.copyOf(); groupSnapshots.clear()
                        for (it in allSel) { val b2=getBounds(it)?:continue; groupSnapshots.add(Pair(it,floatArrayOf((b2[0]+b2[2])/2f,(b2[1]+b2[3])/2f,getRotation(it)))) }
                        return
                    }
                    val corners=listOf(HandleType.TL to (gb[0] to gb[1]),HandleType.TM to (gcx to gb[1]),HandleType.TR to (gb[2] to gb[1]),HandleType.ML to (gb[0] to gcy),HandleType.MR to (gb[2] to gcy),HandleType.BL to (gb[0] to gb[3]),HandleType.BM to (gcx to gb[3]),HandleType.BR to (gb[2] to gb[3]))
                    for ((hType,pos) in corners) {
                        if (distance(wx,wy,pos.first,pos.second) <= hr*1.5f) {
                            groupActiveHandle=hType; groupDragStartX=wx; groupDragStartY=wy; groupOrigBounds=gb.copyOf(); groupSnapshots.clear()
                            for (it in allSel) { val b2=getBounds(it); if (b2!=null) groupSnapshots.add(Pair(it,b2.copyOf())) }
                            return
                        }
                    }
                    // MOVE: tapping anywhere inside the box (not just a tiny center dot) drags
                    // the whole group. A precise center-only hit-zone meant most taps inside the
                    // box fell through to item-toggle logic instead of moving — very unforgiving.
                    val movePad = hr * 0.5f
                    if (wx >= gb[0]-movePad && wx <= gb[2]+movePad && wy >= gb[1]-movePad && wy <= gb[3]+movePad) {
                        groupActiveHandle=HandleType.MOVE; groupDragStartX=wx; groupDragStartY=wy; return
                    }
                }
            }
            // MULTISELECT: toggle pill + record tap for ACTION_UP toggle
            if (currentTool == Tool.MULTISELECT) {
                val gb2 = computeGroupBounds()
                if (gb2 != null) {
                    val pillW = 80f/scaleFactor; val pillH = 20f/scaleFactor
                    val gcx2 = (gb2[0]+gb2[2])/2f; val pillX = gcx2-pillW/2f; val pillY = gb2[3]+14f/scaleFactor
                    if (wx>=pillX && wx<=pillX+pillW && wy>=pillY && wy<=pillY+pillH) { multiSelectIndividual=!multiSelectIndividual; invalidate(); return }
                }
                msTapDownWx = wx; msTapDownWy = wy; msDragging = false
                return
            }

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
                        val hr = 18f / scaleFactor; val hit = (hr * 4f).coerceAtLeast(50f / scaleFactor)
                        val delX = b[2] + hr * 5f; val delY = b[1] - hr * 5f
                        if (distance(lx, ly, delX, delY) <= hit) {
                            // Don't delete locked items
                            if (item is StrokeItem && item.data.isLocked) { handled = true; return }
                            actions.remove(item); if (item === activeTableItem) { activeTableItem = null; tableIsActive = false }; selectedItem = null; markSpatialDirty(); handled = true; invalidate(); return
                        }
                        val canRot = item is ImageItem || item is TextItem || item is AudioItem || item is StrokeItem
                        if (!handled && canRot) {
                            val cx = (b[0] + b[2]) / 2f; val ry = b[1] - 60f / scaleFactor
                            if (distance(lx, ly, cx, ry) <= hit) {
                                activeHandle = HandleType.ROTATE
                                dragStartAngle = computeAngle(item, wx, wy); dragStartRotation = rot; handled = true
                                // In multiselect: snapshot all items for absolute rotation from group center
                                val gb2 = computeGroupBounds()
                                if (gb2 != null) {
                                    groupOrigGcx = (gb2[0]+gb2[2])/2f; groupOrigGcy = (gb2[1]+gb2[3])/2f
                                    groupDragStartAngle = kotlin.math.atan2((wy-groupOrigGcy).toDouble(), (wx-groupOrigGcx).toDouble()).toFloat()
                                    groupSnapshots.clear()
                                    val all = (selectedItems + setOfNotNull(selectedItem)).toSet()
                                    for (itSnap in all) {
                                        val bSnap = getBounds(itSnap) ?: continue
                                        groupSnapshots.add(Pair(itSnap, floatArrayOf((bSnap[0]+bSnap[2])/2f, (bSnap[1]+bSnap[3])/2f, getRotation(itSnap))))
                                    }
                                }
                            }
                        }
                        val isBbox = item is ImageItem || item is TextItem || item is AudioItem || (item is StrokeItem && (BBOX_RESIZE_SHAPES.contains(item.data.type) || STROKE_SCALE_SHAPES.contains(item.data.type)))
                        val isEndpoint = item is StrokeItem && ENDPOINT_RESIZE_SHAPES.contains(item.data.type)
                        if (!handled && isBbox) {
                            for ((type, pos) in bboxHandlePositions(b)) {
                                if (distance(lx, ly, pos.first, pos.second) <= hit) { activeHandle = type; dragStartPivotX = px; dragStartPivotY = py; dragStartRotation = rot; resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true; break }
                            }
                        }
                        if (!handled && isEndpoint && item is StrokeItem && item.data.points.size >= 4) {
                            val p0x = item.data.points[0]; val p0y = item.data.points[1]
                            val lastIdx2 = item.data.points.size - 2
                            val p1x = item.data.points[lastIdx2]; val p1y = item.data.points[lastIdx2 + 1]
                            if (distance(lx, ly, p0x, p0y) <= hit) { activeHandle = HandleType.TL; resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true }
                            else if (distance(lx, ly, p1x, p1y) <= hit) { activeHandle = HandleType.BR; resizePrevWorldX = wx; resizePrevWorldY = wy; handled = true }
                        }
                        if (!handled && lx >= b[0] - hit && lx <= b[2] + hit && ly >= b[1] - hit && ly <= b[3] + hit) { activeHandle = HandleType.MOVE; dragStartWorldX = wx; dragStartWorldY = wy; handled = true }
                    }
                }
                if (!handled) {
                    activeHandle = HandleType.NONE
                    if (currentTool != Tool.MULTISELECT) {
                        selectedItems.clear()
                        selectedItem = findItemAt(wx, wy)
                    }
                    // MULTISELECT item toggling is handled in onSingleTapConfirmed
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }

                // Track drag distance for multiselect tap-vs-drag detection
                if (currentTool == Tool.MULTISELECT && !msTapDownWx.isNaN()) {
                    val dragThreshold = 8f / scaleFactor
                    if (distance(wx, wy, msTapDownWx, msTapDownWy) > dragThreshold) msDragging = true
                }

                // Handle pink group bounding box operations
                if (groupActiveHandle != HandleType.NONE) {
                    val ogb = groupOrigBounds ?: return
                    val gcx = (ogb[0]+ogb[2])/2f; val gcy = (ogb[1]+ogb[3])/2f
                    val gW = ogb[2]-ogb[0]; val gH = ogb[3]-ogb[1]
                    when (groupActiveHandle) {
                        HandleType.MOVE -> {
                            val dx = wx - groupDragStartX; val dy = wy - groupDragStartY
                            val allSel2 = (selectedItems + setOfNotNull(selectedItem)).toSet()
                            for (it in allSel2) { if (!(it is StrokeItem && it.data.isLocked)) moveItem(it, dx, dy) }
                            groupDragStartX = wx; groupDragStartY = wy
                        }
                        HandleType.ROTATE -> {
                            val currentAngle = kotlin.math.atan2((wy-groupOrigGcy).toDouble(), (wx-groupOrigGcx).toDouble()).toFloat()
                            val totalDeltaRad = (currentAngle - groupDragStartAngle).toDouble()
                            val totalDeltaDeg = Math.toDegrees(totalDeltaRad).toFloat()
                            if (multiSelectIndividual) {
                                // Individual mode: each item rotates about its OWN center, stays in place
                                for ((it, snap) in groupSnapshots) {
                                    if (it is StrokeItem && it.data.isLocked) continue
                                    setRotation(it, snap[2] + totalDeltaDeg)
                                    // Pink box will auto-adjust to enclose rotated items
                                }
                            } else {
                                // Group mode: rigid body — all items orbit group center together
                                val cosD = kotlin.math.cos(totalDeltaRad).toFloat()
                                val sinD = kotlin.math.sin(totalDeltaRad).toFloat()
                                for ((it, snap) in groupSnapshots) {
                                    if (it is StrokeItem && it.data.isLocked) continue
                                    val relX = snap[0]-groupOrigGcx; val relY = snap[1]-groupOrigGcy
                                    val targetCx = groupOrigGcx + relX*cosD - relY*sinD
                                    val targetCy = groupOrigGcy + relX*sinD + relY*cosD
                                    val curB = getBounds(it) ?: continue
                                    moveItem(it, targetCx-(curB[0]+curB[2])/2f, targetCy-(curB[1]+curB[3])/2f)
                                    setRotation(it, snap[2]+totalDeltaDeg)
                                }
                                // Track angle so the pink box draws as a RIGID rotated rectangle
                                // (using the original captured bounds) instead of a fresh
                                // axis-aligned union of the now-rotated items' expanded AABBs,
                                // which visually looked like the box was resizing mid-rotation.
                                pinkGroupRotation = totalDeltaDeg
                            }
                        }
                        else -> {
                            // Proportional resize of all items based on how the group bounds changed
                            val newGB = computeGroupBounds() ?: ogb
                            var newMinX = ogb[0]; var newMinY = ogb[1]; var newMaxX = ogb[2]; var newMaxY = ogb[3]
                            when (groupActiveHandle) {
                                HandleType.TL -> { newMinX = wx; newMinY = wy }
                                HandleType.TM -> { newMinY = wy }
                                HandleType.TR -> { newMaxX = wx; newMinY = wy }
                                HandleType.ML -> { newMinX = wx }
                                HandleType.MR -> { newMaxX = wx }
                                HandleType.BL -> { newMinX = wx; newMaxY = wy }
                                HandleType.BM -> { newMaxY = wy }
                                HandleType.BR -> { newMaxX = wx; newMaxY = wy }
                                else -> {}
                            }
                            val newW = (newMaxX-newMinX).coerceAtLeast(20f)
                            val newH = (newMaxY-newMinY).coerceAtLeast(20f)
                            val scaleX = (if (gW > 0f) newW/gW else 1f).coerceIn(0.01f, 50f)
                            val scaleY = (if (gH > 0f) newH/gH else 1f).coerceIn(0.01f, 50f)
                            for ((it, snap) in groupSnapshots) {
                                if (it !is StrokeItem || !it.data.isLocked) {
                                    val relL = (snap[0]-ogb[0])*scaleX + newMinX
                                    val relT = (snap[1]-ogb[1])*scaleY + newMinY
                                    val relR = (snap[2]-ogb[0])*scaleX + newMinX
                                    val relB = (snap[3]-ogb[1])*scaleY + newMinY
                                    if (it is StrokeItem) {
                                        val pts = it.data.points
                                        if (pts.size >= 4) {
                                            pts[0] = relL; pts[1] = relT
                                            pts[pts.size-2] = relR; pts[pts.size-1] = relB
                                            it.path = it.data.buildPath(); it.invalidateCache()
                                        }
                                    } else if (it is ImageItem) { it.x=relL; it.y=relT; it.w=relR-relL; it.h=relB-relT }
                                }
                            }
                        }
                    }
                    markSpatialDirty(); invalidate(); return
                }

                val item = selectedItem ?: return
                // Skip all manipulation if item is locked
                if (item is StrokeItem && item.data.isLocked) {
                    // Still allow ACTION_DOWN to detect the delete handle attempt
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) return
                    return
                }
                when (activeHandle) {
                    HandleType.MOVE -> {
                        var finalWx = wx; var finalWy = wy
                        if (snapEnabled && item is StrokeItem) {
                            val pts = item.data.points
                            if (pts.size >= 2) {
                                val dx0 = wx - dragStartWorldX; val dy0 = wy - dragStartWorldY
                                // Build a shifted copy of pts at the new drag position
                                val shiftedPts = pts.toMutableList().also { p ->
                                    for (i in p.indices) { if (i % 2 == 0) p[i] += dx0 else p[i] += dy0 }
                                }
                                // Use the same helpers as findSnapTarget for consistent snap sources:
                                // endpoints (corners), edge midpoints, AND centroid/center
                                val snapSources = mutableListOf<Pair<Float,Float>>()
                                snapSources.addAll(shapeEndpoints(shiftedPts, item.data.type))
                                snapSources.addAll(shapeEdgeMidpoints(shiftedPts, item.data.type))
                                shapeCenter(shiftedPts, item.data.type)?.let { snapSources.add(it) }

                                actions.remove(item)
                                var bestSnap: SnapResult? = null
                                var bestPriority = Int.MAX_VALUE
                                var bestDist = Float.MAX_VALUE
                                var bestVx = 0f; var bestVy = 0f
                                for ((vx2, vy2) in snapSources) {
                                    val snap = findSnapTarget(vx2, vy2)
                                    if (snap != null) {
                                        val d = distance(vx2, vy2, snap.wx, snap.wy)
                                        if (snap.type.priority < bestPriority ||
                                            (snap.type.priority == bestPriority && d < bestDist)) {
                                            bestSnap = snap; bestPriority = snap.type.priority
                                            bestDist = d; bestVx = vx2; bestVy = vy2
                                        }
                                    }
                                }
                                actions.add(item)
                                if (bestSnap != null) {
                                    finalWx = wx + (bestSnap.wx - bestVx)
                                    finalWy = wy + (bestSnap.wy - bestVy)
                                }
                            }
                        }
                        val dx = finalWx - dragStartWorldX; val dy = finalWy - dragStartWorldY
                        moveItem(item, dx, dy)
                        // Move all other selected items by the same delta (group move)
                        for (other in selectedItems) {
                            if (other !== item && !(other is StrokeItem && other.data.isLocked)) moveItem(other, dx, dy)
                        }
                        dragStartWorldX = finalWx; dragStartWorldY = finalWy
                    }
                    HandleType.ROTATE -> {
                        val newAngle = computeAngle(item, wx, wy)
                        // newAngle and dragStartAngle are already in degrees (computeAngle uses Math.toDegrees)
                        setRotation(item, dragStartRotation + (newAngle - dragStartAngle))
                    }
                    HandleType.NONE -> return
                    else -> resizeItem(item, activeHandle, wx, wy)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it); longPressRunnable = null }
                activeHandle = HandleType.NONE; groupActiveHandle = HandleType.NONE; groupSnapshots.clear(); pinkGroupRotation = 0f
                // MULTISELECT: toggle item only if this was a tap (not a drag)
                if (currentTool == Tool.MULTISELECT && !msTapDownWx.isNaN() && !msDragging) {
                    val hit = findItemAtPreferSelected(msTapDownWx, msTapDownWy)
                    if (hit != null) {
                        if (selectedItems.contains(hit) || hit === selectedItem) {
                            selectedItems.remove(hit); selectedItem = if (selectedItems.isEmpty()) null else selectedItems.last()
                        } else {
                            if (selectedItem != null && !selectedItems.contains(selectedItem!!)) selectedItems.add(selectedItem!!)
                            selectedItems.add(hit); selectedItem = hit
                        }
                        onMultiSelectionChanged?.invoke(selectedItems.toSet()); invalidate()
                    }
                }
                msTapDownWx = Float.NaN; msTapDownWy = Float.NaN; msDragging = false
            }
        }
    }

    // ── Polyline tool ─────────────────────────────────────────────────────────
    // Tap to add vertices. Double-tap on last vertex or empty space to finish.
    // Each segment commits as an independent LINE stroke on completion.
    private fun handlePolyline(event: MotionEvent) {
        var wx = screenToWorldX(event.x); var wy = screenToWorldY(event.y)
        // Snap if enabled
        if (snapEnabled && polylinePoints.size >= 2) {
            val sx = polylinePoints[polylinePoints.size-2]; val sy = polylinePoints[polylinePoints.size-1]
            val snap = findSnapTarget(wx, wy, sx, sy)
            if (snap != null) { wx = snap.wx; wy = snap.wy; snapResult = snap } else snapResult = null
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                polylineCursorX = wx; polylineCursorY = wy; invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                polylineCursorX = wx; polylineCursorY = wy; invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // Double-tap: finish polyline
                if (gestureDetectorPolyline.onTouchEvent(event)) return
                if (polylinePoints.isEmpty()) { polylinePoints.add(wx); polylinePoints.add(wy) }
                else { polylinePoints.add(wx); polylinePoints.add(wy) }
                onPolylineUpdated?.invoke(); invalidate()
            }
        }
        gestureDetectorPolyline.onTouchEvent(event)
    }

    private val gestureDetectorPolyline = android.view.GestureDetector(context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                finalizePolyline(false); return true
            }
        })

    private fun drawPolylinePreview(canvas: Canvas) {
        if (currentTool != Tool.POLYLINE || polylinePoints.size < 2) return
        val p = Paint().apply {
            color = currentColor; style = Paint.Style.STROKE
            strokeWidth = currentStrokeWidth; isAntiAlias = true
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        // Draw committed segments
        var i = 0
        while (i + 3 < polylinePoints.size) {
            canvas.drawLine(polylinePoints[i], polylinePoints[i+1], polylinePoints[i+2], polylinePoints[i+3], p)
            i += 2
        }
        // Draw live preview segment (last committed point → cursor)
        if (polylinePoints.size >= 2) {
            p.alpha = 150
            canvas.drawLine(polylinePoints[polylinePoints.size-2], polylinePoints[polylinePoints.size-1],
                polylineCursorX, polylineCursorY, p)
        }
        // Draw vertex dots
        val dotP = Paint().apply { color = Color.parseColor("#2196F3"); style = Paint.Style.FILL; isAntiAlias = true }
        i = 0
        while (i + 1 < polylinePoints.size) {
            canvas.drawCircle(polylinePoints[i], polylinePoints[i+1], 8f/scaleFactor, dotP)
            i += 2
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
                if (arcDragPointIndex >= 0) { val arc = activeArcItem ?: return; arc.data.points[arcDragPointIndex] = wx; arc.data.points[arcDragPointIndex + 1] = wy; arc.path = arc.data.buildPath(); arc.invalidateCache(); markSpatialDirty() }
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
                        actions.add(ni); redoStack.clear(); markSpatialDirty(); activeArcItem = ni; currentItem = null
                    }
                }
                invalidate()
            }
        }
    }

    private fun rotateItemAroundPoint(item: Any, cx: Float, cy: Float, angleDeg: Float) {
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val cos = kotlin.math.cos(rad); val sin = kotlin.math.sin(rad)
        fun rotPt(x: Float, y: Float): Pair<Float, Float> {
            val dx = x - cx; val dy = y - cy
            return Pair(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
        }
        when (item) {
            is StrokeItem -> {
                val pts = item.data.points; var i = 0
                while (i + 1 < pts.size) { val (nx, ny) = rotPt(pts[i], pts[i+1]); pts[i] = nx; pts[i+1] = ny; i += 2 }
                item.path = item.data.buildPath(); item.invalidateCache()
            }
            is TextItem -> { val (nx, ny) = rotPt(item.x, item.y); item.x = nx; item.y = ny; item.rotation += angleDeg }
            is ImageItem -> { val (nx, ny) = rotPt(item.x + item.w/2f, item.y + item.h/2f); item.x = nx - item.w/2f; item.y = ny - item.h/2f; item.rotation += angleDeg }
            is DimensionItem -> {
                val (nx1, ny1) = rotPt(item.x1, item.y1); val (nx2, ny2) = rotPt(item.x2, item.y2)
                item.x1 = nx1; item.y1 = ny1; item.x2 = nx2; item.y2 = ny2
            }
        }
    }

    private fun scaleItemInGroup(item: Any, ox: Float, oy: Float, sx: Float, sy: Float) {
        when (item) {
            is StrokeItem -> { var i = 0; while (i + 1 < item.data.points.size) { item.data.points[i] = ox + (item.data.points[i] - ox) * sx; item.data.points[i + 1] = oy + (item.data.points[i + 1] - oy) * sy; i += 2 }; item.path = item.data.buildPath(); item.invalidateCache(); markSpatialDirty() }
            is TextItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.size = (item.size * ((sx + sy) / 2f)).coerceIn(6f, 500f) }
            is ImageItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.w *= sx; item.h *= sy }
            is FillItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.w *= sx; item.h *= sy }
            is AudioItem -> { item.x = ox + (item.x - ox) * sx; item.y = oy + (item.y - oy) * sy; item.radius = (item.radius * ((sx + sy) / 2f)).coerceIn(24f, 220f) }
        }
    }

    // Snapshot-based version: applies scale from original captured state to avoid incremental compounding
    private fun scaleItemInGroupFromSnapshot(item: Any, snap: FloatArray?, ox: Float, oy: Float, sx: Float, sy: Float, newOx: Float, newOy: Float) {
        if (snap == null) return
        when (item) {
            is StrokeItem -> {
                // snap is FloatArray of original points (captured at drag start)
                if (snap.size >= 4 && item.data.points.size >= 4) {
                    var i = 0
                    val pts = item.data.points
                    while (i + 1 < minOf(snap.size, pts.size)) {
                        pts[i] = newOx + (snap[i] - ox) * sx
                        pts[i+1] = newOy + (snap[i+1] - oy) * sy
                        i += 2
                    }
                    item.path = item.data.buildPath(); item.invalidateCache(); markSpatialDirty()
                }
            }
            else -> scaleItemInGroup(item, ox, oy, sx, sy)  // fallback for non-stroke items
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
        val group = mutableListOf<Any>()
        val rl = regionBounds[0]; val rt = regionBounds[1]; val rr = regionBounds[2]; val rb = regionBounds[3]
        for (action in actions) {
            val b = getBounds(action) ?: continue
            val matches = if (windowMode) {
                // Window select (L→R): item's full bbox must be completely inside the rectangle
                b[0] >= rl && b[1] >= rt && b[2] <= rr && b[3] <= rb
            } else {
                // Crossing select (R→L): the item's OUTLINE must touch/intersect the selection rect.
                // Pure bbox overlap is NOT enough — a large outer shape whose outline is outside
                // the rect but whose interior contains the rect would incorrectly be selected.
                // Test: any of the item's actual line segments cross any edge of the selection rect.
                // Exception: particle-style brushes (SPRAY/FIRE/GRASS) render scattered dots well
                // off their raw anchor path, so path-crossing doesn't represent what's actually
                // visible — bbox overlap (already padded for scatter in getBounds) is used instead.
                val isParticleBrush = action is StrokeItem && action.data.type == Tool.BRUSH &&
                    action.data.brushStyle in setOf(BrushStyle.SPRAY, BrushStyle.FIRE, BrushStyle.GRASS)
                if (action is StrokeItem && !isParticleBrush) {
                    pathIntersectsRect(action, rl, rt, rr, rb)
                } else {
                    // Non-stroke items and particle brushes: use bbox intersection
                    b[0] <= rr && b[2] >= rl && b[1] <= rb && b[3] >= rt
                }
            }
            if (matches) group.add(action)
        }
        // Preserve z-order — do not reorder actions list
        selectedGroup = if (group.isNotEmpty()) group.toMutableList() else null
    }

    // Check if stroke intersects rect using pre-sampled path points (no PathMeasure per call)
    private fun pathIntersectsRect(item: StrokeItem, rl: Float, rt: Float, rr: Float, rb: Float): Boolean {
        val step = ((rr - rl).coerceAtLeast(rb - rt) * 0.05f).coerceIn(1f, 20f)
        val rawPts = item.getOrBuildSampledPath(step)
        val rot = item.data.rotation
        // If item has rotation, rotate each sampled point before testing against the rect
        if (rot != 0f) {
            val b = getBoundsRaw(item) ?: return false
            val cx = (b[0]+b[2])/2f; val cy = (b[1]+b[3])/2f
            val rad = Math.toRadians(rot.toDouble())
            val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
            var i = 0
            while (i + 1 < rawPts.size) {
                val dx = rawPts[i]-cx; val dy = rawPts[i+1]-cy
                val rx = cx + dx*cos - dy*sin; val ry = cy + dx*sin + dy*cos
                if (rx in rl..rr && ry in rt..rb) return true
                i += 2
            }
            return false
        }
        var i = 0; while (i + 1 < rawPts.size) {
            if (rawPts[i] in rl..rr && rawPts[i+1] in rt..rb) return true
            i += 2
        }
        return false
    }

    // Raw (unrotated) bbox — used for rendering selection handles and computing pivot points.
    // data.rotation is NOT factored in; the caller applies canvas.rotate() for visual orientation.
    private fun getBoundsRaw(item: Any): FloatArray? {
        return when (item) {
            is StrokeItem -> {
                val pts = item.data.points; if (pts.size < 2) return null
                if (item.data.type == Tool.CIRCLE && pts.size >= 4) {
                    val r = kotlin.math.hypot((pts[2]-pts[0]).toDouble(),(pts[3]-pts[1]).toDouble()).toFloat()
                    floatArrayOf(pts[0]-r, pts[1]-r, pts[0]+r, pts[1]+r)
                } else if (SHAPE_TOOLS.contains(item.data.type) && pts.size == 4) {
                    floatArrayOf(minOf(pts[0],pts[2]),minOf(pts[1],pts[3]),maxOf(pts[0],pts[2]),maxOf(pts[1],pts[3]))
                } else {
                    var mnX=pts[0]; var mxX=pts[0]; var mnY=pts[1]; var mxY=pts[1]; var i=0
                    while (i+1<pts.size) { mnX=minOf(mnX,pts[i]); mxX=maxOf(mxX,pts[i]); mnY=minOf(mnY,pts[i+1]); mxY=maxOf(mxY,pts[i+1]); i+=2 }
                    floatArrayOf(mnX, mnY, mxX, mxY)
                }
            }
            else -> getBounds(item)  // non-stroke items don't have separate raw bounds
        }
    }

    // Segment AB intersects segment CD — kept for potential future use
    private fun segmentsIntersect(ax: Float, ay: Float, bx: Float, by: Float,
                                   cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        val d1x = bx - ax; val d1y = by - ay; val d2x = dx - cx; val d2y = dy - cy
        val denom = d1x * d2y - d1y * d2x
        if (kotlin.math.abs(denom) < 0.0001f) return false
        val t = ((cx - ax) * d2y - (cy - ay) * d2x) / denom
        val u = ((cx - ax) * d1y - (cy - ay) * d1x) / denom
        return t in 0f..1f && u in 0f..1f
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
                        val gcx = (gb[0] + gb[2]) / 2f
                        val delX = gb[2] + hr * 5f; val delY = gb[1] - hr * 5f
                        val rotX = gcx; val rotY = gb[1] - hr * 5f
                        // Delete handle
                        if (distance(wx, wy, delX, delY) <= hit) { for (it in group) actions.remove(it); selectedGroup = null; markSpatialDirty(); invalidate(); return }
                        // Rotate handle
                        if (distance(wx, wy, rotX, rotY) <= hit) {
                            groupRotating = true; groupRotateCenterX = gcx; groupRotateCenterY = (gb[1] + gb[3]) / 2f
                            groupRotateStartAngle = kotlin.math.atan2((wy - groupRotateCenterY).toDouble(), (wx - groupRotateCenterX).toDouble()).toFloat()
                            // Snapshot center + rotation for absolute (non-incremental) rotation
                            groupRotateSnapshots = group.map { it2 ->
                                val b2 = getBounds(it2) ?: return@map null
                                floatArrayOf((b2[0]+b2[2])/2f, (b2[1]+b2[3])/2f, getRotation(it2))
                            }
                            invalidate(); return
                        }
                        val gHandles = listOf(gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1], gb[0] to (gb[1]+gb[3])/2f, gb[2] to (gb[1]+gb[3])/2f, gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3])
                        var found = -1
                        for ((hi, hpos) in gHandles.withIndex()) { if (distance(wx, wy, hpos.first, hpos.second) <= hit) { found = hi; break } }
                        if (found >= 0) { groupResizeHandle = found; groupResizeOrigBounds = gb.copyOf(); groupResizeItemSnapshots = group.map { if (it is StrokeItem) it.data.points.toFloatArray() else getBounds(it)?.copyOf() }; invalidate(); return }
                        if (wx in gb[0]..gb[2] && wy in gb[1]..gb[3]) { groupMoveStartX = wx; groupMoveStartY = wy; groupResizeHandle = -1; invalidate(); return }
                    }
                    selectedGroup = null
                }
                regionStart = Pair(wx, wy); regionPath = Path().apply { moveTo(wx, wy) }; invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val group = selectedGroup
                if (group != null && group.isNotEmpty()) {
                    if (groupRotating) {
                        val currentAngle = kotlin.math.atan2((wy - groupRotateCenterY).toDouble(), (wx - groupRotateCenterX).toDouble()).toFloat()
                        val totalDelta = Math.toDegrees((currentAngle - groupRotateStartAngle).toDouble()).toFloat()
                        val snaps = groupRotateSnapshots
                        if (snaps != null) {
                            for ((idx, it) in group.withIndex()) {
                                val snap = snaps.getOrNull(idx) ?: continue
                                // Restore to snapshot position first, then rotate
                                val origCx = snap[0]; val origCy = snap[1]
                                val rad = Math.toRadians(totalDelta.toDouble())
                                val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
                                val dx = origCx - groupRotateCenterX; val dy = origCy - groupRotateCenterY
                                val newCx = groupRotateCenterX + dx*cos - dy*sin
                                val newCy = groupRotateCenterY + dx*sin + dy*cos
                                // Move item to new center from CURRENT position
                                val curB = getBounds(it) ?: continue
                                val curCx = (curB[0]+curB[2])/2f; val curCy = (curB[1]+curB[3])/2f
                                moveItem(it, newCx - curCx, newCy - curCy)
                                setRotation(it, snap[2] + totalDelta)
                            }
                        } else {
                            // Fallback: incremental (for items without snapshots)
                            val incDelta = Math.toDegrees((currentAngle - groupRotateStartAngle).toDouble()).toFloat()
                            for (it in group) rotateItemAroundPoint(it, groupRotateCenterX, groupRotateCenterY, incDelta)
                            groupRotateStartAngle = currentAngle
                        }
                        groupCurrentRotation = totalDelta
                        markSpatialDirty(); invalidate(); return
                    }
                    if (groupResizeHandle >= 0) {
                        val origW = (groupResizeOrigBounds[2] - groupResizeOrigBounds[0]).coerceAtLeast(1f); val origH = (groupResizeOrigBounds[3] - groupResizeOrigBounds[1]).coerceAtLeast(1f)
                        var nl = groupResizeOrigBounds[0]; var nt = groupResizeOrigBounds[1]; var nr = groupResizeOrigBounds[2]; var nb = groupResizeOrigBounds[3]
                        when (groupResizeHandle) { 0 -> { nl = wx; nt = wy }; 1 -> nt = wy; 2 -> { nr = wx; nt = wy }; 3 -> nl = wx; 4 -> nr = wx; 5 -> { nl = wx; nb = wy }; 6 -> nb = wy; 7 -> { nr = wx; nb = wy } }
                        val sx = ((nr - nl).coerceAtLeast(10f) / origW).coerceIn(0.01f, 100f)
                        val sy = ((nb - nt).coerceAtLeast(10f) / origH).coerceIn(0.01f, 100f)
                        val snaps = groupResizeItemSnapshots
                        for ((idx, it) in group.withIndex()) {
                            val snap = snaps.getOrNull(idx)
                            scaleItemInGroupFromSnapshot(it, snap, groupResizeOrigBounds[0], groupResizeOrigBounds[1], sx, sy, nl, nt)
                        }
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
                groupResizeHandle = -1; groupRotating = false; groupRotateSnapshots = null; groupCurrentRotation = 0f; val group = selectedGroup; if (group != null && group.isNotEmpty()) return
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
                        val gcx = (gb[0] + gb[2]) / 2f
                        val rotY = gb[1] - hr * 5f
                        if (distance(wx, wy, delX, delY) <= hit) { for (it in group) actions.remove(it); selectedGroup = null; markSpatialDirty(); invalidate(); return }
                        // Rotation handle
                        if (distance(wx, wy, gcx, rotY) <= hit) {
                            groupRotating = true; groupRotateCenterX = gcx; groupRotateCenterY = (gb[1] + gb[3]) / 2f
                            groupRotateStartAngle = kotlin.math.atan2((wy - groupRotateCenterY).toDouble(), (wx - groupRotateCenterX).toDouble()).toFloat()
                            groupRotateSnapshots = group.map { it2 ->
                                val b2 = getBounds(it2) ?: return@map null
                                floatArrayOf((b2[0]+b2[2])/2f, (b2[1]+b2[3])/2f, getRotation(it2))
                            }
                            invalidate(); return
                        }
                        val gHandles = listOf(gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1], gb[0] to (gb[1]+gb[3])/2f, gb[2] to (gb[1]+gb[3])/2f, gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3])
                        var found = -1
                        for ((hi, hpos) in gHandles.withIndex()) { if (distance(wx, wy, hpos.first, hpos.second) <= hit) { found = hi; break } }
                        if (found >= 0) { groupResizeHandle = found; groupResizeOrigBounds = gb.copyOf(); groupResizeItemSnapshots = group.map { if (it is StrokeItem) it.data.points.toFloatArray() else getBounds(it)?.copyOf() }; invalidate(); return }
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
                    if (groupRotating) {
                        val currentAngle = kotlin.math.atan2((wy - groupRotateCenterY).toDouble(), (wx - groupRotateCenterX).toDouble()).toFloat()
                        val totalDelta = Math.toDegrees((currentAngle - groupRotateStartAngle).toDouble()).toFloat()
                        val snaps = groupRotateSnapshots
                        if (snaps != null) {
                            for ((idx, it) in group.withIndex()) {
                                val snap = snaps.getOrNull(idx) ?: continue
                                val rad = Math.toRadians(totalDelta.toDouble())
                                val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
                                val dx = snap[0] - groupRotateCenterX; val dy = snap[1] - groupRotateCenterY
                                val newCx = groupRotateCenterX + dx*cos - dy*sin
                                val newCy = groupRotateCenterY + dx*sin + dy*cos
                                val curB = getBounds(it) ?: continue
                                moveItem(it, newCx-(curB[0]+curB[2])/2f, newCy-(curB[1]+curB[3])/2f)
                                setRotation(it, snap[2] + totalDelta)
                            }
                        }
                        markSpatialDirty(); invalidate(); return
                    }
                    if (groupResizeHandle >= 0) {
                        val origW = (groupResizeOrigBounds[2] - groupResizeOrigBounds[0]).coerceAtLeast(1f); val origH = (groupResizeOrigBounds[3] - groupResizeOrigBounds[1]).coerceAtLeast(1f)
                        var nl = groupResizeOrigBounds[0]; var nt = groupResizeOrigBounds[1]; var nr = groupResizeOrigBounds[2]; var nb = groupResizeOrigBounds[3]
                        when (groupResizeHandle) { 0 -> { nl = wx; nt = wy }; 1 -> nt = wy; 2 -> { nr = wx; nt = wy }; 3 -> nl = wx; 4 -> nr = wx; 5 -> { nl = wx; nb = wy }; 6 -> nb = wy; 7 -> { nr = wx; nb = wy } }
                        val sx = ((nr - nl).coerceAtLeast(10f) / origW).coerceIn(0.01f, 100f)
                        val sy = ((nb - nt).coerceAtLeast(10f) / origH).coerceIn(0.01f, 100f)
                        val snaps = groupResizeItemSnapshots
                        for ((idx, it) in group.withIndex()) { scaleItemInGroupFromSnapshot(it, snaps.getOrNull(idx), groupResizeOrigBounds[0], groupResizeOrigBounds[1], sx, sy, nl, nt) }
                    } else { val dx = wx - groupMoveStartX; val dy = wy - groupMoveStartY; for (it in group) moveItem(it, dx, dy); groupMoveStartX = wx; groupMoveStartY = wy }
                    invalidate(); return
                }
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
            // Highlight each item
            val hp = Paint(); hp.color = Color.parseColor("#332196F3"); hp.style = Paint.Style.FILL
            for (item in group) { val b = getBounds(item) ?: continue; canvas.drawRect(b[0], b[1], b[2], b[3], hp) }
            val gb = groupBounds(group)
            if (gb != null) {
                val r = 18f / scaleFactor; val gcx = (gb[0]+gb[2])/2f; val gcy = (gb[1]+gb[3])/2f
                // Rotate the entire group box around its center by current rotation
                canvas.save(); canvas.rotate(groupCurrentRotation, gcx, gcy)
                val bp = Paint(); bp.color = Color.parseColor("#2196F3"); bp.style = Paint.Style.STROKE; bp.strokeWidth = 2f/scaleFactor
                bp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f/scaleFactor, 5f/scaleFactor), 0f)
                canvas.drawRect(gb[0], gb[1], gb[2], gb[3], bp)
                // 8 resize handles (white circle with blue stroke — matches single-select style)
                val hf = Paint(); hf.style = Paint.Style.FILL; hf.color = Color.WHITE; hf.isAntiAlias = true
                val hs = Paint(); hs.style = Paint.Style.STROKE; hs.color = Color.parseColor("#2196F3"); hs.strokeWidth = 2f/scaleFactor; hs.isAntiAlias = true
                for ((hx,hy) in listOf(gb[0] to gb[1], gcx to gb[1], gb[2] to gb[1], gb[0] to gcy, gb[2] to gcy, gb[0] to gb[3], gcx to gb[3], gb[2] to gb[3])) {
                    canvas.drawCircle(hx, hy, r, hf); canvas.drawCircle(hx, hy, r, hs)
                }
                // Rotation handle (green circle with rotation arc — same as single-select)
                val rotY = gb[1] - r * 5f
                val rotLinePaint = Paint(); rotLinePaint.color = Color.parseColor("#34C759"); rotLinePaint.strokeWidth = 1.5f/scaleFactor; rotLinePaint.isAntiAlias = true
                canvas.drawLine(gcx, gb[1], gcx, rotY + r, rotLinePaint)
                val rotF = Paint(); rotF.style = Paint.Style.FILL; rotF.color = Color.parseColor("#34C759"); rotF.isAntiAlias = true
                val rotS = Paint(); rotS.style = Paint.Style.STROKE; rotS.color = Color.WHITE; rotS.strokeWidth = 2f/scaleFactor; rotS.isAntiAlias = true
                canvas.drawCircle(gcx, rotY, r * 1.4f, rotF); canvas.drawCircle(gcx, rotY, r * 1.4f, rotS)
                // Small rotation arc symbol inside green circle
                val arcP = Paint(); arcP.style = Paint.Style.STROKE; arcP.color = Color.WHITE; arcP.strokeWidth = 2f/scaleFactor; arcP.isAntiAlias = true; arcP.strokeCap = Paint.Cap.ROUND
                val arcR = r * 0.65f
                canvas.drawArc(android.graphics.RectF(gcx-arcR, rotY-arcR, gcx+arcR, rotY+arcR), -30f, 240f, false, arcP)
                canvas.drawLine(gcx+arcR*0.5f, rotY-arcR*0.85f, gcx+arcR, rotY-arcR*0.3f, arcP)
                // Delete handle (red circle with X — same as single-select)
                val delX = gb[2] + r * 5f; val delY = gb[1] - r * 5f
                val delF = Paint(); delF.style = Paint.Style.FILL; delF.color = Color.parseColor("#FF3B30"); delF.isAntiAlias = true
                val delS = Paint(); delS.style = Paint.Style.STROKE; delS.color = Color.WHITE; delS.strokeWidth = 2.5f/scaleFactor; delS.isAntiAlias = true; delS.strokeCap = Paint.Cap.ROUND
                canvas.drawCircle(delX, delY, r * 1.4f, delF); canvas.drawCircle(delX, delY, r * 1.4f, rotS)
                val d2 = r * 0.8f
                canvas.drawLine(delX-d2, delY-d2, delX+d2, delY+d2, delS)
                canvas.drawLine(delX+d2, delY-d2, delX-d2, delY+d2, delS)
                canvas.restore()
            }
        }
    }

    // Draws persistent snap point markers on all committed strokes so users can see exactly
    // where snap targets are at all times when snap is enabled — no need to hover first.
    // Drawn in canvas (world) coordinate space (inside the canvas.save/restore block).
    // Cached snap marker positions: [x, y, type] where type 0=endpoint, 1=midpoint, 2=center
    private var cachedSnapMarkers: List<FloatArray> = emptyList()
    private var snapMarkersActionCount = -1

    private fun rebuildSnapMarkerCache() {
        val markers = mutableListOf<FloatArray>()
        for (action in actions) {
            if (action !is StrokeItem) continue
            val pts = action.data.points; val t = action.data.type
            if (pts.size < 2) continue
            if (snapEndpoint) shapeEndpoints(pts, t).forEach { (ex,ey) -> markers.add(floatArrayOf(ex,ey,0f)) }
            if (snapMidpoint) when (t) { Tool.CIRCLE, Tool.ELLIPSE -> {} else -> shapeEdgeMidpoints(pts, t).forEach { (mx,my) -> markers.add(floatArrayOf(mx,my,1f)) } }
            if (snapCenter) shapeCenter(pts, t)?.let { (cx,cy) -> markers.add(floatArrayOf(cx,cy,2f)) }
        }
        cachedSnapMarkers = markers
        snapMarkersActionCount = actions.size
    }

    private fun drawSnapMarkers(canvas: Canvas) {
        // Rebuild cache only when actions list changes
        if (snapMarkersActionCount != actions.size || snapMarkersDirty) rebuildSnapMarkerCache()

        val r = 7f / scaleFactor
        val p = cachedSnapMarkerPaint.apply { isAntiAlias = true; strokeWidth = 1.5f/scaleFactor }

        for (m in cachedSnapMarkers) {
            val x = m[0]; val y = m[1]
            when (m[2].toInt()) {
                0 -> { // Endpoint - blue square
                    p.color = android.graphics.Color.parseColor("#2196F3"); p.style = Paint.Style.STROKE
                    canvas.drawRect(x-r, y-r, x+r, y+r, p)
                }
                1 -> { // Midpoint - green triangle
                    p.color = android.graphics.Color.parseColor("#4CAF50"); p.style = Paint.Style.STROKE
                    val tri = android.graphics.Path().apply { moveTo(x,y-r); lineTo(x+r,y+r); lineTo(x-r,y+r); close() }
                    canvas.drawPath(tri, p)
                }
                2 -> { // Center - purple crosshair
                    p.color = android.graphics.Color.parseColor("#9C27B0"); p.style = Paint.Style.STROKE
                    canvas.drawCircle(x, y, r, p)
                    canvas.drawLine(x-r*1.4f, y, x+r*1.4f, y, p)
                    canvas.drawLine(x, y-r*1.4f, x, y+r*1.4f, p)
                }
            }
        }
    }

    private fun drawCursor(canvas: Canvas) {
        val hx = hoverX ?: return; val hy = hoverY ?: return
        val p = Paint(); p.isAntiAlias = true
        when (currentTool) {
            Tool.PEN -> { p.color = currentColor; p.style = Paint.Style.FILL; canvas.drawCircle(hx, hy, (currentStrokeWidth * scaleFactor / 2f).coerceAtLeast(2f), p) }
            Tool.ERASER -> {
                if (eraserMode == EraserMode.OBJECT) { p.color = Color.DKGRAY; p.style = Paint.Style.STROKE; p.strokeWidth = 2f; val half = eraserSize * scaleFactor / 2f; canvas.drawRect(hx - half, hy - half, hx + half, hy + half, p) }
                else { p.color = Color.RED; p.style = Paint.Style.STROKE; p.strokeWidth = 2f; val half = eraserSize * scaleFactor / 2f; canvas.drawCircle(hx, hy, half, p) }
            }
            else -> { p.color = Color.DKGRAY; p.style = Paint.Style.FILL; canvas.drawCircle(hx, hy, 5f, p) }
        }
        // Ghost markers: show snap candidates on nearby objects within awareness radius
        // so user can see where they can snap to before entering the actual snap radius.
        if (snapEnabled && snapAwarenessResults.isNotEmpty()) {
            val ghostP = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 1.5f; alpha = 100 }
            for (ghost in snapAwarenessResults) {
                val gx = worldToScreenX(ghost.wx); val gy = worldToScreenY(ghost.wy)
                ghostP.color = when (ghost.type) {
                    SnapType.ENDPOINT -> Color.parseColor("#2196F3")
                    SnapType.MIDPOINT -> Color.parseColor("#4CAF50")
                    SnapType.INTERSECTION -> Color.parseColor("#FF5722")
                    SnapType.CENTER -> Color.parseColor("#9C27B0")
                    SnapType.PERPENDICULAR -> Color.parseColor("#00BCD4")
                    SnapType.TANGENT -> Color.parseColor("#FF9800")
                    SnapType.PARALLEL -> Color.parseColor("#2196F3")
                    SnapType.NEAREST -> Color.parseColor("#607D8B")
                    SnapType.GRID -> Color.parseColor("#9E9E9E")
                }
                canvas.drawCircle(gx, gy, 6f, ghostP)
            }
        }

        // Snap indicator — type-specific icon drawn in screen space at the snap target
        val snap = snapResult
        if (snapEnabled && snap != null && currentItem == null) {
            val sx = worldToScreenX(snap.wx); val sy = worldToScreenY(snap.wy)
            val sp = Paint().apply { isAntiAlias = true; strokeWidth = 2.5f }
            val r = 10f
            when (snap.type) {
                SnapType.ENDPOINT -> {
                    // Filled square
                    sp.color = Color.parseColor("#2196F3"); sp.style = Paint.Style.FILL
                    canvas.drawRect(sx-r*0.7f, sy-r*0.7f, sx+r*0.7f, sy+r*0.7f, sp)
                    sp.color = Color.WHITE; sp.style = Paint.Style.STROKE
                    canvas.drawRect(sx-r*0.7f, sy-r*0.7f, sx+r*0.7f, sy+r*0.7f, sp)
                }
                SnapType.MIDPOINT -> {
                    // Triangle
                    sp.color = Color.parseColor("#4CAF50"); sp.style = Paint.Style.FILL
                    val tri = android.graphics.Path().apply { moveTo(sx, sy-r); lineTo(sx+r, sy+r); lineTo(sx-r, sy+r); close() }
                    canvas.drawPath(tri, sp)
                    sp.color = Color.WHITE; sp.style = Paint.Style.STROKE; canvas.drawPath(tri, sp)
                }
                SnapType.INTERSECTION -> {
                    // X cross
                    sp.color = Color.parseColor("#FF5722"); sp.style = Paint.Style.STROKE
                    canvas.drawLine(sx-r, sy-r, sx+r, sy+r, sp); canvas.drawLine(sx+r, sy-r, sx-r, sy+r, sp)
                    sp.color = Color.parseColor("#FF5722"); sp.alpha = 60; sp.style = Paint.Style.FILL
                    canvas.drawCircle(sx, sy, r*0.4f, sp)
                }
                SnapType.CENTER -> {
                    // Circle with crosshair
                    sp.color = Color.parseColor("#9C27B0"); sp.style = Paint.Style.STROKE
                    canvas.drawCircle(sx, sy, r, sp)
                    canvas.drawLine(sx-r*1.4f, sy, sx+r*1.4f, sy, sp)
                    canvas.drawLine(sx, sy-r*1.4f, sx, sy+r*1.4f, sp)
                }
                SnapType.PERPENDICULAR -> {
                    // Right-angle corner
                    sp.color = Color.parseColor("#00BCD4"); sp.style = Paint.Style.STROKE
                    canvas.drawLine(sx, sy, sx, sy-r*1.2f, sp); canvas.drawLine(sx, sy, sx+r*1.2f, sy, sp)
                    canvas.drawLine(sx, sy-r*0.5f, sx+r*0.5f, sy-r*0.5f, sp); canvas.drawLine(sx+r*0.5f, sy-r*0.5f, sx+r*0.5f, sy, sp)
                }
                SnapType.TANGENT -> {
                    // Circle with horizontal tangent line — orange
                    sp.color = Color.parseColor("#FF9800"); sp.style = Paint.Style.STROKE
                    canvas.drawCircle(sx, sy, r, sp)
                    canvas.drawLine(sx-r*1.4f, sy, sx+r*1.4f, sy, sp)
                }
                SnapType.PARALLEL -> {
                    // Two parallel lines
                    sp.color = Color.parseColor("#2196F3"); sp.style = Paint.Style.STROKE
                    canvas.drawLine(sx-r, sy-r*0.4f, sx+r, sy-r*0.4f, sp)
                    canvas.drawLine(sx-r, sy+r*0.4f, sx+r, sy+r*0.4f, sp)
                }
                SnapType.NEAREST -> {
                    // Small X mark
                    sp.color = Color.parseColor("#607D8B"); sp.style = Paint.Style.STROKE
                    canvas.drawLine(sx-r*0.7f, sy-r*0.7f, sx+r*0.7f, sy+r*0.7f, sp)
                    canvas.drawLine(sx+r*0.7f, sy-r*0.7f, sx-r*0.7f, sy+r*0.7f, sp)
                    canvas.drawCircle(sx, sy, r, sp)
                }
                SnapType.GRID -> {
                    // Plus mark
                    sp.color = Color.parseColor("#9E9E9E"); sp.style = Paint.Style.STROKE
                    canvas.drawLine(sx-r, sy, sx+r, sy, sp); canvas.drawLine(sx, sy-r, sx, sy+r, sp)
                    canvas.drawCircle(sx, sy, r*0.25f, sp)
                }
            }
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
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverX = event.x; hoverY = event.y
                if (snapEnabled && (currentTool == Tool.PEN || SHAPE_TOOLS.contains(currentTool))) {
                    snapResult = findSnapTarget(screenToWorldX(event.x), screenToWorldY(event.y))
                }
                postInvalidateOnAnimation()  // syncs to display vsync — smoother than invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> { hoverX = null; hoverY = null; snapResult = null; snapAwarenessResults = emptyList(); postInvalidateOnAnimation() }
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
                MotionEvent.ACTION_DOWN -> {
                    // Cancel any in-progress stroke so pan→draw switch doesn't produce a straight line
                    if (currentItem != null) { currentItem = null; invalidate() }
                    twoFingerLastX = event.x; twoFingerLastY = event.y
                }
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
            twoFingerActive = true; twoFingerEverActive = true
            if (currentItem != null) { currentItem = null; invalidate() }
            isStylusDown = false; drawingPointerId = -1
            activeHandle = HandleType.NONE
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }; longPressRunnable = null
            selectHoldItem = null; selectHoldTriggered = false
            val cancel = MotionEvent.obtain(event); cancel.action = MotionEvent.ACTION_CANCEL
            gestureDetector.onTouchEvent(cancel); cancel.recycle()
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Record initial finger distance so we can tell pan from pinch
                    val dx0 = event.getX(0) - event.getX(1); val dy0 = event.getY(0) - event.getY(1)
                    twoFingerInitialDist = kotlin.math.sqrt((dx0*dx0 + dy0*dy0).toDouble()).toFloat()
                    twoFingerLastX = 0f; twoFingerLastY = 0f  // reset midpoint
                    scaleDetector.onTouchEvent(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val fx = (event.getX(0) + event.getX(1)) / 2f
                    val fy = (event.getY(0) + event.getY(1)) / 2f
                    // Midpoint pan — always applied regardless of angle
                    if (twoFingerLastX != 0f || twoFingerLastY != 0f) {
                        translateX += fx - twoFingerLastX; translateY += fy - twoFingerLastY
                        clampTranslation(); onCanvasTransformed?.invoke(); invalidate()
                        val maxScroll = (pageHeightPx() * estimatePageCount().coerceAtLeast(2) * scaleFactor - height).coerceAtLeast(1f)
                        onScrollPercentChanged?.invoke((-translateY / maxScroll).coerceIn(0f, 1f))
                    }
                    twoFingerLastX = fx; twoFingerLastY = fy
                    // Only pass to scaleDetector if fingers have meaningfully changed distance (pinch intent)
                    val dxC = event.getX(0) - event.getX(1); val dyC = event.getY(0) - event.getY(1)
                    val currentDist = kotlin.math.sqrt((dxC*dxC + dyC*dyC).toDouble()).toFloat()
                    val distRatio = if (twoFingerInitialDist > 1f) currentDist / twoFingerInitialDist else 1f
                    // Only engage scale if distance changed by more than 12% — suppresses angled-pan noise
                    if (kotlin.math.abs(distRatio - 1f) > 0.12f) scaleDetector.onTouchEvent(event)
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // If exactly one finger will remain after this lifts and finger-pan mode is
                    // on, that remaining finger's continued movement is handled by the
                    // single-finger-pan branch at the top of this function, which computes its
                    // delta against twoFingerLastX/Y — zeroing them here (the old behavior)
                    // meant the very next move computed a delta against (0,0) instead of the
                    // finger's actual position, producing one huge jump that slammed the view
                    // against its scroll clamp (reported as "resets to the top"). Seeding them
                    // with the remaining finger's current position instead makes that delta
                    // start at ~0, so panning continues smoothly through the transition.
                    if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.pointerCount - 1 == 1 && fingerPanMode) {
                        var seeded = false
                        for (i in 0 until event.pointerCount) {
                            if (i != event.actionIndex) { twoFingerLastX = event.getX(i); twoFingerLastY = event.getY(i); seeded = true; break }
                        }
                        if (!seeded) { twoFingerLastX = 0f; twoFingerLastY = 0f }
                    } else {
                        twoFingerLastX = 0f; twoFingerLastY = 0f
                    }
                    twoFingerInitialDist = 0f
                    scaleDetector.onTouchEvent(event)
                }
                else -> scaleDetector.onTouchEvent(event)
            }
            return true
        }
        // Reset twoFingerActive only when ALL fingers are off the screen
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            twoFingerLastX = 0f; twoFingerLastY = 0f; twoFingerActive = false
        }
        // Block all drawing/gestures while two fingers are active OR until the next fresh touch
        if (twoFingerActive) return true
        // Clear twoFingerEverActive only on a brand-new ACTION_DOWN (new gesture starting)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) twoFingerEverActive = false
        // Still block if this touch sequence ever had two fingers (GestureDetector cancel may not
        // have fully suppressed all callbacks — this is the final safety gate)
        if (twoFingerEverActive) return true
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_UNKNOWN
        // User-selected input restriction (Settings). AUTO keeps the existing behavior below
        // (only reject finger touches while the stylus is actively down). STYLUS_ONLY rejects
        // ALL finger touches at all times (full palm rejection); FINGER_ONLY rejects the stylus.
        if (inputMode == InputMode.STYLUS_ONLY && isFinger) return true
        if (inputMode == InputMode.FINGER_ONLY && isStylus) return true
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
                        dimDraggingItem = null; redoStack.clear(); markSpatialDirty()
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
                                actions.add(newDim); redoStack.clear(); markSpatialDirty(); invalidate()
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
                            actions.add(newDim); redoStack.clear(); markSpatialDirty()
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
                                    onTextSelectRequest?.invoke(capturedHit, lx, ly, lx, ly)
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
        if (currentTool == Tool.SELECT || currentTool == Tool.MULTISELECT) {
            val wx2 = screenToWorldX(event.x); val wy2 = screenToWorldY(event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val textHit = findTextItemAt(wx2, wy2)
                    if (textHit != null) {
                        // Track the down event for hold detection
                        selectHoldItem = textHit
                        selectHoldDownX = event.x; selectHoldDownY = event.y
                        selectHoldRawX = event.rawX; selectHoldRawY = event.rawY
                        selectHoldDownTime = System.currentTimeMillis()
                        selectHoldTriggered = false; selectHoldMoved = false
                        // Still feed gestureDetector for double-tap and single-tap-confirmed
                        gestureDetector.onTouchEvent(event)
                        return true
                    } else {
                        selectHoldItem = null; selectHoldTriggered = false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val holdItem = selectHoldItem
                    if (holdItem != null) {
                        val dx = event.x - selectHoldDownX; val dy = event.y - selectHoldDownY
                        val moved = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (moved > dp(8)) selectHoldMoved = true
                        val heldMs = System.currentTimeMillis() - selectHoldDownTime
                        if (!selectHoldTriggered && heldMs >= 400L) {
                            // Hold threshold reached — select and immediately start drag
                            selectHoldTriggered = true
                            selectedItem = holdItem; invalidate()
                            onTextSelectRequest?.invoke(holdItem, selectHoldDownX, selectHoldDownY, selectHoldRawX, selectHoldRawY)
                        }
                        if (selectHoldTriggered) {
                            // Forward current finger position so moveSurface can update position
                            onTextHoldDragMove?.invoke(holdItem, event.rawX, event.rawY)
                            return true
                        }
                        gestureDetector.onTouchEvent(event)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val holdItem = selectHoldItem
                    if (holdItem != null) {
                        val heldMs = System.currentTimeMillis() - selectHoldDownTime
                        if (!selectHoldTriggered && !selectHoldMoved) {
                            if (holdItem.linkTarget != null && heldMs < 300L) {
                                // Short tap on link → navigate
                                onLinkTap?.invoke(holdItem.linkTarget!!)
                                selectHoldItem = null
                                gestureDetector.onTouchEvent(event)
                                return true
                            }
                            if (holdItem.linkTarget == null) {
                                // Short tap on normal text → select (single tap confirmed)
                                // gestureDetector will fire onSingleTapConfirmed which handles this
                            }
                        }
                        selectHoldItem = null; selectHoldTriggered = false
                        gestureDetector.onTouchEvent(event); handleSelect(event); return true
                    }
                }
            }
            gestureDetector.onTouchEvent(event); handleSelect(event); return true
        }
        if (currentTool == Tool.ARC) { handleArc(event); return true }
        if (currentTool == Tool.POLYLINE) { handlePolyline(event); return true }
        if (currentTool == Tool.AUTOSELECT) { gestureDetector.onTouchEvent(event); handleAutoSelect(event); return true }
        if (currentTool == Tool.LASSO) { handleLasso(event); return true }
        if (currentTool == Tool.EXPORT_WINDOW) { handleExportWindow(event); return true }
        if (currentTool == Tool.OCR_SNIP) { handleOcrSnip(event); return true }
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
                exportWindowStart = null; exportWindowEnd = null; currentTool = Tool.SELECT; onInternalToolChange?.invoke(Tool.SELECT); invalidate()
            }
        }
    }

    private fun handleOcrSnip(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { exportWindowStart = Pair(wx, wy); exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_MOVE -> { exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_UP -> {
                val s = exportWindowStart ?: return; val e = exportWindowEnd ?: return
                val left = minOf(s.first, e.first); val top = minOf(s.second, e.second); val right = maxOf(s.first, e.first); val bottom = maxOf(s.second, e.second)
                exportWindowStart = null; exportWindowEnd = null; currentTool = Tool.SELECT; onInternalToolChange?.invoke(Tool.SELECT); invalidate()
                if (right - left > 20f && bottom - top > 20f) {
                    val bmp = exportWindow(left, top, right, bottom)
                    onOcrSnipSelected?.invoke(bmp, left, top, right, bottom)
                }
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

    // Same idea as clampToPage, but for text items specifically — those can legitimately be
    // taller than a single page (a long pasted block), unlike a stroke or shape. The plain
    // clampToPage always squeezed the Y anchor into exactly one page's height (ph) no matter
    // how tall the actual content was, which forced a tall text box's position back into a
    // band it structurally couldn't fit inside — every move or paste-triggered reposition kept
    // getting snapped back into that too-narrow range, which is what "stuck"/"jumps up" was.
    private fun clampToPageForText(wx: Float, wy: Float, text: String, size: Float, fontFamily: String): Pair<Float, Float> {
        if (canvasMode == CanvasMode.INFINITE) return Pair(wx, wy)
        val pw = pageWidthPx(); val ph = pageHeightPx()
        val tp = TextPaint().apply { textSize = size; try { typeface = typefaceFromFamily(fontFamily) } catch (e: Exception) {} }
        val wrapW = (pw - 40f).coerceAtLeast(50f).toInt()
        val estHeight = try {
            android.text.StaticLayout.Builder.obtain(text, 0, text.length, tp, wrapW).build().height.toFloat()
        } catch (e: Exception) { size }
        val pageTop = if (canvasMode == CanvasMode.CONVENIENT || canvasMode == CanvasMode.PAGINATED) {
            val gap = if (canvasMode == CanvasMode.CONVENIENT) 24f else 40f
            val period = ph + gap
            kotlin.math.floor(wy / period) * period
        } else 0f
        val cx = wx.coerceIn(0f, pw)
        // Taller than one page: only clamp the top bound, let it extend downward across
        // page/section boundaries as far as it actually needs instead of being squeezed.
        val cy = if (estHeight > ph) wy.coerceAtLeast(pageTop) else wy.coerceIn(pageTop, pageTop + ph)
        return Pair(cx, cy)
    }

    private fun handleDrawing(event: MotionEvent) {
        // Narrow safety net: if the eraser gesture gets interrupted (e.g. a parent view steals
        // the touch), flush any in-memory-only fill edits rather than leaving them stranded
        // until some later gesture happens to trigger a flush. Scoped ONLY to this case so it
        // never affects any other tool's cancel/up behavior.
        if (event.actionMasked == MotionEvent.ACTION_CANCEL && currentTool == Tool.ERASER) { flushDirtyFillItems(); return }
        hoverX = event.x; hoverY = event.y
        var wx = screenToWorldX(event.x); var wy = screenToWorldY(event.y)
        if (currentTool == Tool.PEN || currentTool == Tool.HIGHLIGHTER || currentTool == Tool.BRUSH || SHAPE_TOOLS.contains(currentTool)) {
            val (cx, cy) = clampToPage(wx, wy); wx = cx; wy = cy
        }
        val pressure = event.pressure.coerceIn(0.3f, 1.5f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (currentTool == Tool.ERASER) { eraserLastX = wx; eraserLastY = wy; eraseAt(wx, wy); invalidate(); return }
                // Snap start point to nearest existing endpoint if snap is enabled
                if (snapEnabled && (currentTool == Tool.PEN || SHAPE_TOOLS.contains(currentTool))) {
                    val snap = findSnapTarget(wx, wy)
                    if (snap != null) { wx = snap.wx; wy = snap.wy }
                    snapResult = null
                }
                val data = when {
                    currentTool == Tool.PEN -> {
                        // Ball pen is strictly uniform - no pressure or speed sensitivity, per spec.
                        val baseW = if (currentPenStyle == PenStyle.BALL) currentStrokeWidth else currentStrokeWidth * pressure
                        StrokeData(Tool.PEN, mutableListOf(wx, wy), currentColor, baseW, false, rotation = 0f, penStyle = currentPenStyle, opacity = if (currentPenStyle == PenStyle.BALL) 255 else brushOpacity, lineType = currentLineType, calligraphySlantThickness = currentCalligraphySlant)
                    }
                    currentTool == Tool.HIGHLIGHTER -> StrokeData(Tool.HIGHLIGHTER, mutableListOf(wx, wy), currentColor, highlighterThickness, false, rotation = 0f, penStyle = PenStyle.MARKER, opacity = (highlighterOpacity * 255 / 100))
                    currentTool == Tool.BRUSH -> StrokeData(Tool.BRUSH, mutableListOf(wx, wy), currentColor, brushThickness * pressure, false, rotation = 0f, brushStyle = currentBrushStyle, opacity = brushOpacity)
                    SHAPE_TOOLS.contains(currentTool) -> StrokeData(currentTool, mutableListOf(wx, wy, wx, wy), currentColor, currentStrokeWidth, fillShapes, lineType = currentLineType)
                    else -> StrokeData(Tool.PEN, mutableListOf(wx, wy), currentColor, currentStrokeWidth * pressure, false, rotation = 0f, penStyle = currentPenStyle, opacity = brushOpacity, lineType = currentLineType, calligraphySlantThickness = currentCalligraphySlant)
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
                    val spacing = (eraserSize / 3f / scaleFactor).coerceAtLeast(1f)
                    fun eraseTo(nx: Float, ny: Float) {
                        if (eraserLastX.isNaN()) { eraseAt(nx, ny); eraserLastX = nx; eraserLastY = ny; return }
                        val d = distance(nx, ny, eraserLastX, eraserLastY)
                        if (d < spacing) return  // too close — skip to avoid redundant work on slow movement
                        val steps = (d / spacing).toInt().coerceAtLeast(1)
                        for (s in 1..steps) {
                            val frac = s.toFloat() / steps
                            eraseAt(eraserLastX + (nx - eraserLastX) * frac, eraserLastY + (ny - eraserLastY) * frac)
                        }
                        eraserLastX = nx; eraserLastY = ny
                    }
                    for (h in 0 until event.historySize) {
                        eraseTo(screenToWorldX(event.getHistoricalX(h)), screenToWorldY(event.getHistoricalY(h)))
                    }
                    eraseTo(wx, wy)
                    invalidate(); return
                }
                // Update snap indicator and apply magnetic pull during live drawing.
                // For shapes with computed vertices (TRIANGLE etc.), check ALL vertices
                // of the current shape — not just the cursor — against snap targets.
                // Offset wx,wy so the closest vertex lands exactly on the snap target.
                if (snapEnabled && (currentTool == Tool.PEN || SHAPE_TOOLS.contains(currentTool))) {
                    val pts0 = currentItem?.data?.points
                    val x1s = if (pts0 != null && pts0.size >= 2) pts0[0] else Float.NaN
                    val y1s = if (pts0 != null && pts0.size >= 2) pts0[1] else Float.NaN

                    // Compute candidate "check points" for this shape: all its visible vertices
                    val checkPoints = mutableListOf(Pair(wx, wy))  // cursor = drag end
                    if (!x1s.isNaN() && pts0 != null) {
                        val l = minOf(x1s, wx); val r = maxOf(x1s, wx)
                        val t = minOf(y1s, wy); val b = maxOf(y1s, wy)
                        val cxs = (l + r) / 2f; val cys = (t + b) / 2f
                        when (currentTool) {
                            Tool.TRIANGLE, Tool.ISOSCELES_TRIANGLE -> {
                                checkPoints.add(Pair(cxs, t))   // apex
                                checkPoints.add(Pair(l, b))      // bottom-left
                                checkPoints.add(Pair(r, b))      // bottom-right
                            }
                            Tool.TRIANGLE_DOWN -> {
                                checkPoints.add(Pair(l, t))
                                checkPoints.add(Pair(r, t))
                                checkPoints.add(Pair(cxs, b))
                            }
                            Tool.RIGHT_TRIANGLE -> {
                                checkPoints.add(Pair(l, t))
                                checkPoints.add(Pair(l, b))
                                checkPoints.add(Pair(r, b))
                            }
                            Tool.DIAMOND -> {
                                checkPoints.add(Pair(cxs, t))
                                checkPoints.add(Pair(r, cys))
                                checkPoints.add(Pair(cxs, b))
                                checkPoints.add(Pair(l, cys))
                            }
                            Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.ELLIPSE -> {
                                checkPoints.add(Pair(l, t)); checkPoints.add(Pair(r, t))
                                checkPoints.add(Pair(l, b)); checkPoints.add(Pair(r, b))
                            }
                            else -> {}  // LINE, ARROW, PEN: cursor already in list
                        }
                    }

                    // Find the best snap target for any check point
                    var bestSnap: SnapResult? = null
                    var bestDist = Float.MAX_VALUE
                    var bestVertexX = wx; var bestVertexY = wy

                    for ((cpx, cpy) in checkPoints) {
                        val snap = findSnapTarget(cpx, cpy, x1s, y1s)
                        if (snap != null) {
                            val d = distance(cpx, cpy, snap.wx, snap.wy)
                            if (bestSnap == null || snap.type.priority < bestSnap.type.priority ||
                                (snap.type.priority == bestSnap.type.priority && d < bestDist)) {
                                bestSnap = snap; bestDist = d
                                bestVertexX = cpx; bestVertexY = cpy
                            }
                        }
                    }

                    snapResult = bestSnap
                    if (bestSnap != null) {
                        // Offset wx,wy so the winning vertex lands exactly on the snap target
                        val dx = bestSnap.wx - bestVertexX
                        val dy = bestSnap.wy - bestVertexY
                        wx += dx; wy += dy
                    }
                } else {
                    snapResult = null; snapAwarenessResults = emptyList()
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
                                // Was 0.55 + (1-speedNorm)*0.9 with speedNorm capped at 0.7 — that
                                // formula only ever produced widths between ~0.82x-1.45x, well
                                // short of the 0.4x-1.8x range it was supposedly clamped to. This
                                // spans that full range directly, so slow strokes actually pool
                                // thick and fast strokes actually go thin, not just mildly vary.
                                val speedNorm = (speed / 2200f).coerceIn(0f, 1f)
                                val rawTarget = (currentStrokeWidth * (0.4f + (1f - speedNorm) * 1.4f)).coerceIn(currentStrokeWidth * 0.4f, currentStrokeWidth * 1.8f)
                                val prevWidth = item.data.widths.lastOrNull() ?: currentStrokeWidth
                                // Rate-limit: cap how much the target can differ from the last
                                // width in a single sample. A sharp turn/cusp forces the pen to
                                // momentarily decelerate to change direction — without this cap,
                                // that single-sample deceleration blip reads as "slowed down to
                                // pool more ink" and balloons into a visible blob right at the
                                // turn, even though the hand never actually paused there.
                                val maxDelta = currentStrokeWidth * 0.10f
                                val targetWidth = rawTarget.coerceIn(prevWidth - maxDelta, prevWidth + maxDelta)
                                // Was 0.8/0.2 — so heavily damped that a normal-speed signature
                                // finished before the width ever caught up to how fast the pen
                                // was actually moving, reading as flat/unfluid instead of a
                                // nib that responds to your hand. 0.45/0.55 still smooths raw
                                // per-sample jitter but lets the ink weight actually track speed.
                                item.data.widths.add(prevWidth * 0.45f + targetWidth * 0.55f)
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
                            val speedNorm = (speed / 2200f).coerceIn(0f, 1f)
                            val rawTarget = (currentStrokeWidth * (0.4f + (1f - speedNorm) * 1.4f)).coerceIn(currentStrokeWidth * 0.4f, currentStrokeWidth * 1.8f)
                            val prevWidth = item.data.widths.lastOrNull() ?: currentStrokeWidth
                            val maxDelta = currentStrokeWidth * 0.10f
                            val targetWidth = rawTarget.coerceIn(prevWidth - maxDelta, prevWidth + maxDelta)
                            // 0.45/0.55: still smooths raw jitter but tracks actual pen speed
                            // closely enough to read as fluent rather than lagging behind it.
                            item.data.widths.add(prevWidth * 0.45f + targetWidth * 0.55f)
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
                if (currentTool == Tool.ERASER) { flushDirtyFillItems(); eraserLastX = Float.NaN; eraserLastY = Float.NaN }
                // Snap end point to nearest existing endpoint if snap is enabled
                if (snapEnabled && (currentTool == Tool.PEN || SHAPE_TOOLS.contains(currentTool))) {
                    val pts0 = currentItem?.data?.points
                    val sx0 = if (pts0 != null && pts0.size >= 2) pts0[0] else Float.NaN
                    val sy0 = if (pts0 != null && pts0.size >= 2) pts0[1] else Float.NaN
                    val snap = findSnapTarget(wx, wy, sx0, sy0)
                    if (snap != null) {
                        wx = snap.wx; wy = snap.wy
                        val item = currentItem
                        if (item != null) {
                            if (SHAPE_TOOLS.contains(currentTool) && item.data.points.size >= 4) {
                                item.data.points[2] = wx; item.data.points[3] = wy
                            } else if (item.data.points.size >= 2) {
                                item.data.points[item.data.points.size - 2] = wx
                                item.data.points[item.data.points.size - 1] = wy
                            }
                            item.path = item.data.buildPath()
                        }
                    }
                    snapResult = null; snapAwarenessResults = emptyList()
                }
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
                    if (!tooShort) {
                    // Downsample overly dense PEN strokes (>6000 pts) to keep serialization and
                    // PathMeasure sampling fast. Visual difference is imperceptible.
                    if (item.data.type == Tool.PEN && item.data.points.size > 6000) {
                        val src = item.data.points; val keep = mutableListOf<Float>()
                        val skip = (src.size / 2) / 3000  // keep every Nth point pair
                        var ii = 0; while (ii < src.size - 1) { if ((ii/2) % skip == 0 || ii >= src.size - 2) { keep.add(src[ii]); keep.add(src[ii+1]) }; ii += 2 }
                        src.clear(); src.addAll(keep); item.path = item.data.buildPath(); item.invalidateCache()
                    }
                    actions.add(item); redoStack.clear(); markSpatialDirty()
                    // For shape tools: notify MainActivity to show select handles temporarily
                    if (isShapeTool) {
                        selectedItem = item
                        onShapeCompleted?.invoke(item)
                    }
                    }
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
            // Adaptive density: at least 150 samples regardless of length, or denser still for
            // long curves (1 sample per 2 world units). At the old fixed 48 segments, a smooth
            // curve visibly turned into a faceted polyline the instant any part was erased.
            val segmentCount = maxOf(150, (len / 2f).toInt())
            val step = (len / segmentCount).coerceAtLeast(0.25f)
            var dist = 0f
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
            val d = StrokeData(Tool.PEN, sp, data.color, data.strokeWidth, false, penStyle = PenStyle.FOUNTAIN, opacity = data.opacity, lineType = data.lineType)
            StrokeItem(d, d.buildPath(), d.toPaint())
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserSize / 2f
        if (eraserMode == EraserMode.OBJECT) {
            // Only test items whose bounding box overlaps eraser circle — skips distant strokes
            val candidates = itemsNear(x, y, r * 3f)
            val toRemove = HashSet<Any>()
            for (a in candidates) {
                val hit = when (a) {
                    is StrokeItem -> strokeHitTest(a.data, x, y, r)
                    is TextItem -> distance(x, y, a.x, a.y) <= r + a.size
                    is ImageItem -> distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) <= r + maxOf(a.w, a.h) / 2f
                    is FillItem -> eraserAffectsFill && distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) <= r + maxOf(a.w, a.h) / 2f
                    is AudioItem -> distance(x, y, a.x, a.y) <= r + a.radius; else -> false
                }
                if (hit) {
                    // Don't remove locked items
                    if (a is StrokeItem && a.data.isLocked) continue
                    toRemove.add(a)
                }
            }
            if (toRemove.isNotEmpty()) { actions.removeAll(toRemove); markSpatialDirty() }
        } else {
            val candidates = itemsNear(x, y, r * 3f).toHashSet()
            val newActions = mutableListOf<Any>()
            for (a in actions) {
                // Items far from eraser pass through unchanged — no processing needed
                if (a !in candidates) { newActions.add(a); continue }
                when (a) {
                    is StrokeItem -> {
                        if (a.data.isLocked) {
                            newActions.add(a)
                        } else if (a.data.type == Tool.PEN || a.data.type == Tool.ERASER || a.data.type == Tool.ARC || a.data.type == Tool.HIGHLIGHTER || a.data.type == Tool.BRUSH) {
                            newActions.addAll(splitStrokeAroundEraser(a.data, x, y, r))
                        } else if (CLOSED_SHAPES.contains(a.data.type)) {
                            // Convert to component lines at erase time.
                            // Each edge becomes independent — user gets remaining parts as separate strokes.
                            // Use strokeHitTestRotated: the plain strokeHitTest tests against the
                            // UNROTATED outline, which caused wrong-edge erasure on rotated shapes.
                            if (strokeHitTestRotated(a.data, x, y, r)) {
                                val components = convertShapeToComponents(a.data)
                                for (comp in components) {
                                    if (strokeHitTest(comp.data, x, y, r)) {
                                        newActions.addAll(splitStrokeAroundEraser(comp.data, x, y, r))
                                    } else {
                                        newActions.add(comp)
                                    }
                                }
                            } else {
                                newActions.add(a)
                            }
                        } else {
                            // Open shapes (LINE, ARROW): split into fragments
                            if (strokeHitTest(a.data, x, y, r)) newActions.addAll(splitShapeAroundEraser(a.data, x, y, r))
                            else newActions.add(a)
                        }
                    }
                    is TextItem -> { if (distance(x, y, a.x, a.y) > r + a.size) newActions.add(a) }
                    is ImageItem -> { if (distance(x, y, a.x + a.w / 2f, a.y + a.h / 2f) > r + maxOf(a.w, a.h) / 2f) newActions.add(a) }
                    is FillItem -> {
                        if (eraserMode == EraserMode.AREA && eraserAffectsFill) {
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
            actions.clear(); actions.addAll(newActions); markSpatialDirty()
        }
    }

    private fun eraseFillItemRegion(item: FillItem, ex: Float, ey: Float, r: Float): FillItem? {
        val cached = getOrLoadFillBitmap(item) ?: return item
        val bw = cached.width; val bh = cached.height
        val scaleX = bw / item.w; val scaleY = bh / item.h
        val bex = ((ex - item.x) * scaleX).toInt(); val bey = ((ey - item.y) * scaleY).toInt()
        val brx = (r * scaleX).toInt().coerceAtLeast(1); val bry = (r * scaleY).toInt().coerceAtLeast(1)

        // Cheap early-out: ONE batched getPixels() read over the erase circle's bounding box,
        // then scan the returned array in pure Kotlin. Previously this looped up to ~169
        // individual getPixel() calls per tick — each one a separate JNI round-trip into native
        // Skia code — which was still adding measurable overhead during a drag even after the
        // disk-write fix. A single batched read is dramatically cheaper than many small ones.
        val rx0 = (bex - brx).coerceAtLeast(0); val ry0 = (bey - bry).coerceAtLeast(0)
        val rx1 = (bex + brx).coerceAtMost(bw - 1); val ry1 = (bey + bry).coerceAtMost(bh - 1)
        if (rx1 < rx0 || ry1 < ry0) return item
        val rw = rx1 - rx0 + 1; val rh = ry1 - ry0 + 1
        val region = IntArray(rw * rh)
        cached.getPixels(region, 0, rw, rx0, ry0, rw, rh)
        var anyOpaqueHit = false
        outer@ for (yy in 0 until rh) {
            val py = ry0 + yy
            val ny = (py - bey).toFloat() / bry.coerceAtLeast(1)
            for (xx in 0 until rw) {
                val px = rx0 + xx
                val nx = (px - bex).toFloat() / brx.coerceAtLeast(1)
                if (nx*nx + ny*ny > 1f) continue
                if ((region[yy * rw + xx] ushr 24) != 0) { anyOpaqueHit = true; break@outer }
            }
        }
        if (!anyOpaqueHit) return item

        // Make ONE mutable copy per gesture (tracked via dirtyFillItems), then draw directly
        // onto that SAME bitmap for every subsequent tick in this gesture — no repeated
        // allocation, no repeated full-bitmap pixel scan, and critically NO disk write here at
        // all. The expensive PNG encode + FileOutputStream write (and the full-transparency
        // check that decides whether to remove the item) are deferred to flushDirtyFillItems(),
        // called once on ACTION_UP. This was previously the main cause of erasing becoming
        // slow/jammed near any filled shape — a full copy+scan+disk-write on every touch tick.
        val bmp: Bitmap
        if (dirtyFillItems.contains(item)) {
            bmp = item.bitmap ?: cached.copy(Bitmap.Config.ARGB_8888, true).also { item.bitmap = it }
        } else {
            bmp = cached.copy(Bitmap.Config.ARGB_8888, true)
            item.bitmap = bmp
            dirtyFillItems.add(item)
        }
        val cv = Canvas(bmp)
        val p = _fillErasePaint
        cv.drawOval(RectF((bex - brx).toFloat(), (bey - bry).toFloat(), (bex + brx).toFloat(), (bey + bry).toFloat()), p)
        return item
    }

    // Flushes all in-memory-only fill edits from the current erase gesture: checks each dirty
    // FillItem for full transparency (removing it if so) and writes the rest to disk exactly
    // once. Called on ACTION_UP for the eraser, never during ACTION_MOVE.
    private fun flushDirtyFillItems() {
        if (dirtyFillItems.isEmpty()) return
        var removedAny = false
        for (item in dirtyFillItems) {
            val bmp = item.bitmap ?: continue
            val bw = bmp.width; val bh = bmp.height
            val pixels = IntArray(bw * bh); bmp.getPixels(pixels, 0, bw, 0, 0, bw, bh)
            if (pixels.all { (it ushr 24) == 0 }) {
                actions.remove(item); removedAny = true; continue
            }
            try { FileOutputStream(item.path).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { }
            synchronized(bitmapCache) { bitmapCache.remove(item.path) }
        }
        dirtyFillItems.clear()
        if (removedAny) markSpatialDirty()
        invalidate()
    }
    // sample-point distance), so erasing is accurate to what's visually under the eraser circle
    // regardless of how sparse the stroke's recorded points are.
    private fun splitStrokeAroundEraser(data: StrokeData, ex: Float, ey: Float, r: Float): List<StrokeItem> {
        val pts = data.points
        if (pts.size < 4) { if (pts.size >= 2 && distance(ex, ey, pts[0], pts[1]) <= r) return emptyList(); return listOf(StrokeItem(data, data.buildPath(), data.toPaint())) }

        // Walk each segment and find EXACT circle-crossing parameters along it (0, 1, or 2
        // crossings), rather than classifying the whole segment as one in/out unit based on its
        // nearest point. The old whole-segment approach broke badly for long straight segments
        // (a rectangle edge, or a long span in a polyline) — erasing near just one endpoint made
        // the nearest-point distance small for the ENTIRE segment, deleting all of it instead of
        // only the portion actually under the eraser.
        val segs = mutableListOf<MutableList<Float>>(); var cur = mutableListOf<Float>()
        fun flush() { if (cur.size >= 4) segs.add(cur); cur = mutableListOf() }
        fun ptIn(x: Float, y: Float) = distance(ex, ey, x, y) <= r

        var prevIn = ptIn(pts[0], pts[1])
        if (!prevIn) { cur.add(pts[0]); cur.add(pts[1]) }

        var i = 0
        while (i + 3 < pts.size) {
            val x1 = pts[i]; val y1 = pts[i + 1]; val x2 = pts[i + 2]; val y2 = pts[i + 3]
            val endIn = ptIn(x2, y2)
            val crossings = findAllCircleSegIntersections(ex, ey, r, x1, y1, x2, y2)

            if (crossings.isEmpty()) {
                // No boundary crossing on this segment — it shares prevIn's state throughout.
                if (!prevIn) { cur.add(x2); cur.add(y2) }
            } else {
                var state = prevIn
                for (t in crossings) {
                    val cx2 = x1 + t * (x2 - x1); val cy2 = y1 + t * (y2 - y1)
                    if (!state) {
                        // Was outside, entering the erased zone: this crossing ends the surviving fragment.
                        if (cur.isEmpty()) { cur.add(x1); cur.add(y1) }
                        cur.add(cx2); cur.add(cy2); flush()
                    } else {
                        // Was inside the erased zone, exiting: this crossing starts a new fragment.
                        cur.add(cx2); cur.add(cy2)
                    }
                    state = !state
                }
                if (!endIn) {
                    if (cur.isEmpty()) { val lastT = crossings.last(); cur.add(x1 + lastT * (x2 - x1)); cur.add(y1 + lastT * (y2 - y1)) }
                    cur.add(x2); cur.add(y2)
                }
            }
            prevIn = endIn
            i += 2
        }
        flush()

        // If the original stroke was a CLOSED loop (first point == last point, as produced by
        // convertShapeToComponents for closed shapes), the "first" and "last" surviving
        // fragments are really two ends of the SAME continuous arc — the loop only artificially
        // starts/ends at an arbitrary seam point. Without this merge, erasing anywhere except
        // exactly at that seam always splits the shape into two separate objects instead of one.
        if (segs.size >= 2) {
            val n = pts.size
            val isClosedLoop = n >= 6 && distance(pts[0], pts[1], pts[n - 2], pts[n - 1]) < 0.01f
            if (isClosedLoop) {
                val firstFrag = segs.first(); val lastFrag = segs.last()
                val firstStartsAtSeam = distance(firstFrag[0], firstFrag[1], pts[0], pts[1]) < 0.01f
                val lastEndsAtSeam = distance(lastFrag[lastFrag.size - 2], lastFrag[lastFrag.size - 1], pts[n - 2], pts[n - 1]) < 0.01f
                if (firstStartsAtSeam && lastEndsAtSeam && firstFrag !== lastFrag) {
                    val merged = mutableListOf<Float>()
                    merged.addAll(lastFrag)
                    merged.addAll(firstFrag.subList(2, firstFrag.size)) // skip duplicate seam point
                    val newSegs = mutableListOf<MutableList<Float>>()
                    newSegs.addAll(segs.subList(1, segs.size - 1))
                    newSegs.add(merged)
                    return newSegs.map { sp -> val d = StrokeData(data.type, sp, data.color, data.strokeWidth, data.fill, penStyle = data.penStyle, opacity = data.opacity, brushStyle = data.brushStyle, lineType = data.lineType); StrokeItem(d, d.buildPath(), d.toPaint()) }
                }
            }
        }
        return segs.map { sp -> val d = StrokeData(data.type, sp, data.color, data.strokeWidth, data.fill, penStyle = data.penStyle, opacity = data.opacity, brushStyle = data.brushStyle, lineType = data.lineType); StrokeItem(d, d.buildPath(), d.toPaint()) }
    }

    // Finds ALL points (as sorted t-values in [0,1]) where a segment crosses the eraser circle
    // boundary — up to 2 crossings, needed to correctly clip a single long segment that may pass
    // through the erase circle at any point along its length (not just near an endpoint).
    private fun findAllCircleSegIntersections(cx: Float, cy: Float, r: Float, x1: Float, y1: Float, x2: Float, y2: Float): List<Float> {
        val dx = x2 - x1; val dy = y2 - y1
        val fx = x1 - cx; val fy = y1 - cy
        val a = dx * dx + dy * dy
        if (a < 0.0001f) return emptyList()
        val b = 2f * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - r * r
        val disc = b * b - 4f * a * c
        if (disc < 0f) return emptyList()
        val sq = kotlin.math.sqrt(disc)
        val t1 = (-b - sq) / (2f * a); val t2 = (-b + sq) / (2f * a)
        val result = mutableListOf<Float>()
        if (t1 in 0f..1f) result.add(t1)
        if (t2 in 0f..1f && kotlin.math.abs(t2 - t1) > 1e-5f) result.add(t2)
        result.sort()
        return result
    }

    // Backward-compat wrapper for the legacy splitShapeAroundEraser path (which pre-samples
    // shapes into many short chunks, so the coarse single-crossing approximation is fine there).
    private fun findCircleSegIntersection(cx: Float, cy: Float, r: Float, x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float>? {
        val t = findAllCircleSegIntersections(cx, cy, r, x1, y1, x2, y2).firstOrNull() ?: return null
        return Pair(x1 + t * (x2 - x1), y1 + t * (y2 - y1))
    }

    // True distance from a point to a closed/open shape's outline (not its inflated bounding box).
    // Used by area-mode erasing so touching empty space near a shape's bbox corner doesn't delete
    // the whole shape - only actually touching its drawn line does.
    // ── Offset & Explode ──────────────────────────────────────────────────────
    // Infinite-line intersection (NOT bounded to the segment range) — needed for offset corner
    // miter joins, since the new corner point usually lies outside either original segment.
    private fun lineLineIntersection(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float): Pair<Float, Float>? {
        val d1x = x2 - x1; val d1y = y2 - y1
        val d2x = x4 - x3; val d2y = y4 - y3
        val denom = d1x * d2y - d1y * d2x
        if (kotlin.math.abs(denom) < 1e-6f) return null
        val t = ((x3 - x1) * d2y - (y3 - y1) * d2x) / denom
        return Pair(x1 + t * d1x, y1 + t * d1y)
    }

    // Bounded segment intersection (0..1 on segment A) — used to detect self-intersection after
    // offsetting, which happens when the offset distance exceeds the shape's narrowest width.
    private fun offsetSegSegT(ax1: Float, ay1: Float, ax2: Float, ay2: Float, bx1: Float, by1: Float, bx2: Float, by2: Float): Float? {
        val d1x = ax2 - ax1; val d1y = ay2 - ay1
        val d2x = bx2 - bx1; val d2y = by2 - by1
        val denom = d1x * d2y - d1y * d2x
        if (kotlin.math.abs(denom) < 1e-6f) return null
        val dx = bx1 - ax1; val dy = by1 - ay1
        val t = (dx * d2y - dy * d2x) / denom
        val u = (dx * d1y - dy * d1x) / denom
        return if (t in 0.02f..0.98f && u in 0.02f..0.98f) t else null  // small margin excludes shared-endpoint false positives
    }

    // Extracts a straight-edged vertex list + closed/open flag from an item, for offset/explode.
    // Returns null for anything curve-like (dense point count, or a genuine curve/circle type).
    private fun extractStraightVertices(data: StrokeData): Pair<List<Pair<Float, Float>>, Boolean>? {
        if (data.type == Tool.CURVE || data.type == Tool.ARC || data.type == Tool.CIRCLE || data.type == Tool.ELLIPSE || data.type == Tool.ROUNDED_RECT) return null

        // Rotates a vertex list around the shape's own bounding-box center by data.rotation.
        // Tool.PEN items don't need this (already rotation-baked at conversion time), but
        // LINE/ARROW and typed shapes store rotation SEPARATELY from their raw points — without
        // baking it in here, offset/explode would silently operate on the unrotated geometry.
        fun bakeRotation(verts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            val rot = data.rotation
            if (rot == 0f || verts.isEmpty()) return verts
            var minX = verts[0].first; var maxX = verts[0].first; var minY = verts[0].second; var maxY = verts[0].second
            for ((vx, vy) in verts) { if (vx < minX) minX = vx; if (vx > maxX) maxX = vx; if (vy < minY) minY = vy; if (vy > maxY) maxY = vy }
            val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
            val rad = Math.toRadians(rot.toDouble())
            val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
            return verts.map { (vx, vy) -> val dx = vx - cx; val dy = vy - cy; Pair(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos) }
        }

        if (data.type == Tool.PEN) {
            val pts = data.points
            if (pts.size < 4 || pts.size > 42) return null  // too many points = curve/freehand, unsupported
            val vlist = mutableListOf<Pair<Float, Float>>()
            var i = 0; while (i + 1 < pts.size) { vlist.add(Pair(pts[i], pts[i + 1])); i += 2 }
            val isClosed = vlist.size >= 3 && distance(vlist.first().first, vlist.first().second, vlist.last().first, vlist.last().second) < 0.5f
            return Pair(if (isClosed) vlist.dropLast(1) else vlist, isClosed)
        }
        if (data.type == Tool.LINE || data.type == Tool.ARROW) {
            val pts = data.points; if (pts.size < 4) return null
            return Pair(bakeRotation(listOf(Pair(pts[0], pts[1]), Pair(pts[2], pts[3]))), false)
        }
        if (EXPLICIT_VERTEX_SHAPES.contains(data.type) || CLOSED_SHAPES.contains(data.type)) {
            val verts = shapeEndpoints(data.points, data.type)
            if (verts.size < 2) return null
            return Pair(bakeRotation(verts), CLOSED_SHAPES.contains(data.type))
        }
        return null
    }

    // Creates a new offset copy of the item at the given signed distance (positive = outward for
    // a closed shape, direction is otherwise just perpendicular to travel direction). Returns
    // null if the item type isn't supported (curves) or if the offset would self-intersect
    // (distance too large relative to the shape's narrowest width) — never silently produces
    // broken geometry.
    fun offsetItem(item: StrokeItem, dist: Float): StrokeItem? {
        val data = item.data
        if (kotlin.math.abs(dist) < 0.01f) return null

        when (data.type) {
            Tool.CIRCLE -> {
                val pts = data.points; if (pts.size < 4) return null
                val cx = pts[0]; val cy = pts[1]
                val rad = kotlin.math.hypot((pts[2] - cx).toDouble(), (pts[3] - cy).toDouble()).toFloat()
                val newRad = rad + dist
                if (newRad <= 2f) return null
                val ang = kotlin.math.atan2((pts[3] - cy).toDouble(), (pts[2] - cx).toDouble())
                val d = StrokeData(Tool.CIRCLE, mutableListOf(cx, cy, cx + (newRad * kotlin.math.cos(ang)).toFloat(), cy + (newRad * kotlin.math.sin(ang)).toFloat()),
                    data.color, data.strokeWidth, false, lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
                return StrokeItem(d, d.buildPath(), d.toPaint())
            }
            Tool.ELLIPSE -> {
                val pts = data.points; if (pts.size < 4) return null
                val l = minOf(pts[0], pts[2]) - dist; val r = maxOf(pts[0], pts[2]) + dist
                val t = minOf(pts[1], pts[3]) - dist; val b = maxOf(pts[1], pts[3]) + dist
                if (r - l <= 2f || b - t <= 2f) return null
                val d = StrokeData(Tool.ELLIPSE, mutableListOf(l, t, r, b), data.color, data.strokeWidth, false, lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
                return StrokeItem(d, d.buildPath(), d.toPaint())
            }
            else -> {}
        }

        val (verts, closed) = extractStraightVertices(data) ?: return null
        val n = verts.size
        val edgeCount = if (closed) n else n - 1
        if (edgeCount < 1) return null

        data class OffLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
        val offLines = mutableListOf<OffLine>()
        for (i in 0 until edgeCount) {
            val a = verts[i]; val b = verts[(i + 1) % n]
            val dx = b.first - a.first; val dy = b.second - a.second
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len < 0.01f) { offLines.add(OffLine(a.first, a.second, b.first, b.second)); continue }
            val nx = -dy / len; val ny = dx / len
            offLines.add(OffLine(a.first + nx * dist, a.second + ny * dist, b.first + nx * dist, b.second + ny * dist))
        }
        if (offLines.isEmpty()) return null

        val newVerts = mutableListOf<Pair<Float, Float>>()
        val lc = offLines.size
        if (closed) {
            for (i in 0 until lc) {
                val prev = offLines[(i - 1 + lc) % lc]; val cur = offLines[i]
                val ip = lineLineIntersection(prev.x1, prev.y1, prev.x2, prev.y2, cur.x1, cur.y1, cur.x2, cur.y2)
                newVerts.add(ip ?: Pair(cur.x1, cur.y1))
            }
        } else {
            newVerts.add(Pair(offLines[0].x1, offLines[0].y1))
            for (i in 0 until lc - 1) {
                val cur = offLines[i]; val next = offLines[i + 1]
                val ip = lineLineIntersection(cur.x1, cur.y1, cur.x2, cur.y2, next.x1, next.y1, next.x2, next.y2)
                newVerts.add(ip ?: Pair(cur.x2, cur.y2))
            }
            newVerts.add(Pair(offLines.last().x2, offLines.last().y2))
        }

        // Safety check: reject if any two non-adjacent new edges cross — this is exactly what
        // happens when the offset distance exceeds the shape's narrowest local width (e.g.
        // offsetting an L-shape inward by more than its narrower arm's width).
        val vc = newVerts.size
        val checkEdges = if (closed) vc else vc - 1
        for (i in 0 until checkEdges) {
            val a1 = newVerts[i]; val a2 = newVerts[(i + 1) % vc]
            for (j in i + 2 until checkEdges) {
                if (closed && i == 0 && j == checkEdges - 1) continue  // adjacent via wraparound
                val b1 = newVerts[j]; val b2 = newVerts[(j + 1) % vc]
                if (offsetSegSegT(a1.first, a1.second, a2.first, a2.second, b1.first, b1.second, b2.first, b2.second) != null) return null
            }
        }

        val finalPts = mutableListOf<Float>()
        for ((vx, vy) in newVerts) { finalPts.add(vx); finalPts.add(vy) }
        if (closed) { finalPts.add(newVerts[0].first); finalPts.add(newVerts[0].second) }
        val d = StrokeData(Tool.PEN, finalPts, data.color, data.strokeWidth, false, lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
        return StrokeItem(d, d.buildPath(), d.toPaint())
    }

    // Breaks a compound object (polyline, or a still-typed shape like rectangle/hexagon) into
    // its individual straight-line primitive edges — AutoCAD's EXPLODE. Returns null for
    // anything curve-like (never explodes curves) or already a single primitive (a plain LINE
    // has nothing to explode into).
    fun explodeItem(item: StrokeItem): List<StrokeItem>? {
        val data = item.data
        if (data.type == Tool.LINE || data.type == Tool.ARROW) return null  // already a single primitive
        val (verts, closed) = extractStraightVertices(data) ?: return null
        val n = verts.size
        val edgeCount = if (closed) n else n - 1
        if (edgeCount < 1) return null
        val result = mutableListOf<StrokeItem>()
        for (i in 0 until edgeCount) {
            val a = verts[i]; val b = verts[(i + 1) % n]
            val d = StrokeData(Tool.LINE, mutableListOf(a.first, a.second, b.first, b.second),
                data.color, data.strokeWidth, false, lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
            result.add(StrokeItem(d, d.buildPath(), d.toPaint()))
        }
        return result
    }

    // Public wrappers for MainActivity — actions is private, so offset/explode results need
    // a safe entry point to commit their changes.
    fun applyOffsetResult(original: StrokeItem, offset: StrokeItem) {
        actions.add(offset); redoStack.clear(); markSpatialDirty(); invalidate()
        selectedItem = offset
    }
    fun applyExplodeResult(original: StrokeItem, pieces: List<StrokeItem>) {
        actions.remove(original); actions.addAll(pieces)
        redoStack.clear(); markSpatialDirty(); invalidate()
        selectedItem = null
    }

    // Creates a copy of the item, shifted to sit beside the original (not overlapping it) —
    // offset by the item's own width plus a small gap, so the copy is always fully clear of
    // the original regardless of the shape's size.
    fun duplicateStrokeItem(item: StrokeItem): StrokeItem {
        val b = getBoundsRaw(item) ?: getBounds(item)
        val w = if (b != null) (b[2] - b[0]) else item.data.strokeWidth * 4f
        val gap = (w * 0.15f).coerceAtLeast(20f)
        val dx = w + gap
        val newPoints = mutableListOf<Float>()
        var i = 0
        while (i + 1 < item.data.points.size) { newPoints.add(item.data.points[i] + dx); newPoints.add(item.data.points[i + 1]); i += 2 }
        val newClipHoles = item.data.clipHoles.map { floatArrayOf(it[0] + dx, it[1], it[2]) }.toMutableList()
        val d = StrokeData(item.data.type, newPoints, item.data.color, item.data.strokeWidth, item.data.fill,
            rotation = item.data.rotation, fillColorVal = item.data.fillColorVal, penStyle = item.data.penStyle,
            opacity = item.data.opacity, brushStyle = item.data.brushStyle, lineType = item.data.lineType,
            widths = item.data.widths.toMutableList())
        d.clipHoles.addAll(newClipHoles)
        d.isLocked = false
        return StrokeItem(d, d.buildPath(), d.toPaint())
    }
    fun applyDuplicateResult(copy: StrokeItem) {
        actions.add(copy); redoStack.clear(); markSpatialDirty(); invalidate()
        selectedItem = copy
    }

    private fun distanceToShapeOutline(data: StrokeData, x: Float, y: Float): Float {
        var tx = x; var ty = y
        val rot = data.rotation
        if (rot != 0f) {
            val pts = data.points
            if (pts.size >= 2) {
                var minX=pts[0]; var maxX=pts[0]; var minY=pts[1]; var maxY=pts[1]; var i=0
                while (i+1<pts.size) { if(pts[i]<minX)minX=pts[i]; if(pts[i]>maxX)maxX=pts[i]; if(pts[i+1]<minY)minY=pts[i+1]; if(pts[i+1]>maxY)maxY=pts[i+1]; i+=2 }
                val cx=(minX+maxX)/2f; val cy=(minY+maxY)/2f
                val rad = Math.toRadians(-rot.toDouble())
                val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
                val dx=x-cx; val dy=y-cy
                tx = cx + dx*cos - dy*sin; ty = cy + dx*sin + dy*cos
            }
        }
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
                val d = distance(tx, ty, pos[0], pos[1])
                if (d < minDist) minDist = d
                dist += step
            }
        } while (measure.nextContour())
        return minDist
    }

    // Rotation-aware hit test: inverse-rotate the test point into the item's local space
    private fun strokeHitTestRotated(data: StrokeData, x: Float, y: Float, r: Float): Boolean {
        val rot = data.rotation
        if (rot == 0f) return strokeHitTest(data, x, y, r)
        // Compute bounding box center (pivot for rotation)
        val pts = data.points; if (pts.size < 2) return false
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0; while (i < pts.size-1) { if (pts[i]<minX) minX=pts[i]; if (pts[i]>maxX) maxX=pts[i]; if (pts[i+1]<minY) minY=pts[i+1]; if (pts[i+1]>maxY) maxY=pts[i+1]; i+=2 }
        val cx = (minX+maxX)/2f; val cy = (minY+maxY)/2f
        // Inverse-rotate tap point around pivot
        val rad = Math.toRadians(-rot.toDouble())
        val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
        val dx = x-cx; val dy = y-cy
        val lx = cx + dx*cos - dy*sin; val ly = cy + dx*sin + dy*cos
        return strokeHitTest(data, lx, ly, r)
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

    // ── Snap geometry helpers (AutoCAD-accurate, matching buildPath exactly) ──
    private fun cosf(a: Float) = kotlin.math.cos(a.toDouble()).toFloat()
    private fun sinf(a: Float) = kotlin.math.sin(a.toDouble()).toFloat()
    private val PIf = Math.PI.toFloat()

    // Polygon vertices matching addPolygon(sides, isStar) — start = -PI/2, step = 2PI/count
    private fun polygonVerts(cx: Float, cy: Float, rx: Float, ry: Float, sides: Int, isStar: Boolean): List<Pair<Float,Float>> {
        val count = if (isStar) sides * 2 else sides
        val step = 2f * PIf / count; val start = -PIf / 2f
        return (0 until count).map { i ->
            val r2 = if (isStar && i % 2 == 1) 0.5f else 1f
            val a = start + i * step
            Pair(cx + cosf(a) * rx * r2, cy + sinf(a) * ry * r2)
        }
    }

    // Actual drawn vertices for each shape — exactly matching buildPath
    private fun shapeEndpoints(pts: MutableList<Float>, type: Tool): List<Pair<Float,Float>> {
        if (pts.size < 2) return emptyList()
        val x1=pts[0]; val y1=pts[1]
        val x2=if(pts.size>=4) pts[pts.size-2] else x1; val y2=if(pts.size>=4) pts[pts.size-1] else y1
        val l=minOf(x1,x2); val r=maxOf(x1,x2); val t=minOf(y1,y2); val b=maxOf(y1,y2)
        val cx=(l+r)/2f; val cy=(t+b)/2f; val rx=(r-l)/2f; val ry=(b-t)/2f
        return when(type) {
            Tool.LINE, Tool.ARROW -> listOf(Pair(x1,y1), Pair(x2,y2))
            Tool.RECTANGLE, Tool.ROUNDED_RECT ->
                listOf(Pair(l,t), Pair(r,t), Pair(r,b), Pair(l,b))
            Tool.CIRCLE -> {
                val rad = kotlin.math.hypot((x2-x1).toDouble(),(y2-y1).toDouble()).toFloat()
                listOf(Pair(x1,y1-rad), Pair(x1+rad,y1), Pair(x1,y1+rad), Pair(x1-rad,y1)) // quadrants
            }
            Tool.ELLIPSE ->
                listOf(Pair(cx,t), Pair(r,cy), Pair(cx,b), Pair(l,cy)) // cardinal points
            Tool.TRIANGLE -> listOf(Pair(cx,t), Pair(r,b), Pair(l,b))
            Tool.ISOSCELES_TRIANGLE -> listOf(Pair(cx,t), Pair(r,b), Pair(l,b))
            Tool.TRIANGLE_DOWN -> listOf(Pair(l,t), Pair(r,t), Pair(cx,b))
            Tool.RIGHT_TRIANGLE -> listOf(Pair(l,t), Pair(l,b), Pair(r,b))
            Tool.DIAMOND -> listOf(Pair(cx,t), Pair(r,cy), Pair(cx,b), Pair(l,cy))
            Tool.TRAPEZOID -> { val ins=(r-l)*0.2f; listOf(Pair(l+ins,t),Pair(r-ins,t),Pair(r,b),Pair(l,b)) }
            Tool.PARALLELOGRAM -> { val sk=(r-l)*0.2f; listOf(Pair(l+sk,t),Pair(r,t),Pair(r-sk,b),Pair(l,b)) }
            Tool.PENTAGON -> polygonVerts(cx, cy, rx, ry, 5, false)
            Tool.HEXAGON -> polygonVerts(cx, cy, rx, ry, 6, false)
            Tool.HEPTAGON -> polygonVerts(cx, cy, rx, ry, 7, false)
            Tool.OCTAGON -> polygonVerts(cx, cy, rx, ry, 8, false)
            Tool.NONAGON -> polygonVerts(cx, cy, rx, ry, 9, false)
            Tool.DECAGON -> polygonVerts(cx, cy, rx, ry, 10, false)
            Tool.STAR -> polygonVerts(cx, cy, rx, ry, 5, true) // outer (even) + inner (odd) vertices
            Tool.CROSS -> // two crossing lines: 4 endpoints
                listOf(Pair(l,cy), Pair(r,cy), Pair(cx,t), Pair(cx,b))
            Tool.PEN, Tool.CURVE, Tool.ARC -> {
                val res = mutableListOf(Pair(x1,y1))
                if (pts.size >= 4) res.add(Pair(x2,y2))
                res
            }
            else -> {
                // Unknown shape: try polygon approximation from bbox corners
                if (pts.size >= 4) listOf(Pair(l,t),Pair(r,t),Pair(r,b),Pair(l,b)) else listOf(Pair(x1,y1))
            }
        }
    }

    // Edge midpoints for each shape
    private fun shapeEdgeMidpoints(pts: MutableList<Float>, type: Tool): List<Pair<Float,Float>> {
        val verts = shapeEndpoints(pts, type)
        if (verts.size < 2) {
            // For lines: just the single midpoint
            if (pts.size >= 4 && (type == Tool.LINE || type == Tool.ARROW)) {
                return listOf(Pair((pts[0]+pts[pts.size-2])/2f, (pts[1]+pts[pts.size-1])/2f))
            }
            return emptyList()
        }
        return verts.indices.map { i ->
            val (ax,ay) = verts[i]; val (bx,by) = verts[(i+1) % verts.size]
            Pair((ax+bx)/2f, (ay+by)/2f)
        }
    }

    // Centroid / center for each shape
    private fun shapeCenter(pts: MutableList<Float>, type: Tool): Pair<Float,Float>? {
        if (pts.size < 2) return null
        val x1=pts[0]; val y1=pts[1]
        val x2=if(pts.size>=4) pts[pts.size-2] else x1; val y2=if(pts.size>=4) pts[pts.size-1] else y1
        val l=minOf(x1,x2); val r=maxOf(x1,x2); val t=minOf(y1,y2); val b=maxOf(y1,y2)
        val cx=(l+r)/2f; val cy=(t+b)/2f
        return when(type) {
            Tool.CIRCLE -> Pair(x1, y1)  // center is pts[0,1]
            Tool.LINE, Tool.ARROW, Tool.PEN, Tool.CURVE, Tool.ARC -> null  // no center concept
            // True centroid = average of vertices (not bounding-box center)
            Tool.TRIANGLE, Tool.ISOSCELES_TRIANGLE -> Pair(cx, (t + b + b) / 3f)  // apex=(cx,t), bl/br=(l/r,b)
            Tool.TRIANGLE_DOWN -> Pair(cx, (t + t + b) / 3f)
            Tool.RIGHT_TRIANGLE -> Pair((l + l + r) / 3f, (t + b + b) / 3f)
            Tool.TRAPEZOID -> { val ins=(r-l)*0.2f; Pair((l+ins+r-ins+r+l)/4f, cy) } // avg of 4 vertices x
            else -> Pair(cx, cy)
        }
    }

    // Nearest point on the shape's boundary from (wx, wy)
    private fun nearestOnBoundary(pts: MutableList<Float>, type: Tool, wx: Float, wy: Float): Pair<Float,Float>? {
        if (pts.size < 2) return null
        val x1=pts[0]; val y1=pts[1]
        val x2=if(pts.size>=4) pts[pts.size-2] else x1; val y2=if(pts.size>=4) pts[pts.size-1] else y1

        // Circle: project onto circumference
        if (type == Tool.CIRCLE) {
            val rad = kotlin.math.hypot((x2-x1).toDouble(),(y2-y1).toDouble()).toFloat()
            if (rad < 1e-4f) return null
            val dx=wx-x1; val dy=wy-y1
            val len=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(1e-6f)
            return Pair(x1+dx/len*rad, y1+dy/len*rad)
        }
        // Ellipse: project onto ellipse boundary using angle from center
        if (type == Tool.ELLIPSE || type == Tool.ROUNDED_RECT) {
            val l=minOf(x1,x2); val r=maxOf(x1,x2); val t=minOf(y1,y2); val b=maxOf(y1,y2)
            val cx=(l+r)/2f; val cy=(t+b)/2f; val rx=(r-l)/2f; val ry=(b-t)/2f
            if (rx < 1e-4f || ry < 1e-4f) return null
            val angle = kotlin.math.atan2((wy-cy).toDouble()/ry, (wx-cx).toDouble()/rx).toFloat()
            return Pair(cx+rx*cosf(angle), cy+ry*sinf(angle))
        }
        // Lines: nearest point on segment
        if (type == Tool.LINE || type == Tool.ARROW) {
            val dx=x2-x1; val dy=y2-y1; val lenSq=(dx*dx+dy*dy).coerceAtLeast(1e-6f)
            val t2=(((wx-x1)*dx+(wy-y1)*dy)/lenSq).coerceIn(0f,1f)
            return Pair(x1+t2*dx, y1+t2*dy)
        }
        // PEN/ARC/CURVE: nearest on sampled path
        if (type == Tool.PEN || type == Tool.ARC || type == Tool.CURVE) {
            if (pts.size < 4) return Pair(x1,y1)
            var bDist=Float.MAX_VALUE; var bx=x1; var by=y1
            var i=0
            while (i<pts.size-3) {
                val ax=pts[i]; val ay=pts[i+1]; val bx2=pts[i+2]; val by2=pts[i+3]
                val dx=bx2-ax; val dy=by2-ay
                val t2=(((wx-ax)*dx+(wy-ay)*dy)/(dx*dx+dy*dy).coerceAtLeast(1e-6f)).coerceIn(0f,1f)
                val nx=ax+t2*dx; val ny=ay+t2*dy
                val d=distance(wx,wy,nx,ny); if(d<bDist){bDist=d;bx=nx;by=ny}
                i+=2
            }
            return Pair(bx,by)
        }
        // All other polygon shapes: nearest on any edge
        val verts = shapeEndpoints(pts, type)
        if (verts.isEmpty()) return null
        var bDist=Float.MAX_VALUE; var bx=verts[0].first; var by=verts[0].second
        for (i in verts.indices) {
            val (ax,ay)=verts[i]; val (ex,ey)=verts[(i+1)%verts.size]
            val dx=ex-ax; val dy=ey-ay
            val t2=(((wx-ax)*dx+(wy-ay)*dy)/(dx*dx+dy*dy).coerceAtLeast(1e-6f)).coerceIn(0f,1f)
            val nx=ax+t2*dx; val ny=ay+t2*dy
            val d=distance(wx,wy,nx,ny); if(d<bDist){bDist=d;bx=nx;by=ny}
        }
        return Pair(bx,by)
    }

    // ── Snap target finder ────────────────────────────────────────────────────
    // Evaluates all enabled snap types within snapScreenRadius, returning the highest-priority
    // result. Within same type, returns the closest. Priority: Endpoint>Intersection>Midpoint>
    // Center>Perpendicular>Parallel>Nearest>Grid.
    private fun findSnapTarget(wx: Float, wy: Float, strokeStartWx: Float = Float.NaN, strokeStartWy: Float = Float.NaN): SnapResult? {
        val worldRadius = snapScreenRadius / scaleFactor
        val worldAwareness = snapAwarenessRadius / scaleFactor
        val candidates = mutableListOf<Pair<SnapResult, Float>>()
        val awarenessCandidates = mutableListOf<SnapResult>()

        fun add(rx: Float, ry: Float, type: SnapType) {
            if (!rx.isFinite() || !ry.isFinite()) return
            val d = distance(wx, wy, rx, ry)
            if (d <= worldRadius) candidates.add(Pair(SnapResult(rx, ry, type), d))
            else if (d <= worldAwareness) awarenessCandidates.add(SnapResult(rx, ry, type))
        }

        val strokes = actions.filterIsInstance<StrokeItem>()

        for (item in strokes) {
            val pts = item.data.points
            if (pts.size < 2) continue
            val t = item.data.type

            // Endpoint — actual shape vertices via helper
            if (snapEndpoint) shapeEndpoints(pts, t).forEach { (ex,ey) -> add(ex, ey, SnapType.ENDPOINT) }

            // Midpoint — actual edge midpoints via helper
            if (snapMidpoint) shapeEdgeMidpoints(pts, t).forEach { (mx,my) -> add(mx, my, SnapType.MIDPOINT) }

            // Center — centroid/geometric center
            if (snapCenter) shapeCenter(pts, t)?.let { (cx,cy) -> add(cx, cy, SnapType.CENTER) }

            // Nearest — closest point on the actual boundary (circle circumference, polygon edge, etc.)
            if (snapNearest) nearestOnBoundary(pts, t, wx, wy)?.let { (nx,ny) -> add(nx, ny, SnapType.NEAREST) }

            // Perpendicular — foot of perpendicular from stroke start to this shape's edges
            if (snapPerpendicular && !strokeStartWx.isNaN()) {
                val verts = shapeEndpoints(pts, t)
                for (i in verts.indices) {
                    val (ax,ay) = verts[i]; val (bx,by) = verts[(i+1) % verts.size]
                    val dx=bx-ax; val dy=by-ay; val lenSq=(dx*dx+dy*dy).coerceAtLeast(1e-6f)
                    val tf=(((strokeStartWx-ax)*dx+(strokeStartWy-ay)*dy)/lenSq).coerceIn(0f,1f)
                    add(ax+tf*dx, ay+tf*dy, SnapType.PERPENDICULAR)
                }
            }

            // Tangent — point(s) on circle/ellipse where a line from strokeStart is tangent
            if (snapTangent && !strokeStartWx.isNaN() && pts.size >= 4) {
                val px1=pts[0]; val py1=pts[1]; val px2=pts[pts.size-2]; val py2=pts[pts.size-1]
                val pl=minOf(px1,px2); val pr=maxOf(px1,px2); val pt2=minOf(py1,py2); val pb=maxOf(py1,py2)
                when (t) {
                    Tool.CIRCLE -> {
                        val ox=px1; val oy=py1
                        val rad = kotlin.math.hypot((px2-ox).toDouble(),(py2-oy).toDouble()).toFloat()
                        val dx2=strokeStartWx-ox; val dy2=strokeStartWy-oy
                        val d_sq=dx2*dx2+dy2*dy2
                        if (d_sq > rad*rad + 1e-4f) {
                            val A=(rad*rad/d_sq)
                            val B=(rad* kotlin.math.sqrt((d_sq-rad*rad).toDouble())/d_sq).toFloat()
                            add(ox+A*dx2-B*(-dy2), oy+A*dy2-B*dx2, SnapType.TANGENT)
                            add(ox+A*dx2+B*(-dy2), oy+A*dy2+B*dx2, SnapType.TANGENT)
                        }
                    }
                    Tool.ELLIPSE -> {
                        val cx2=(pl+pr)/2f; val cy2=(pt2+pb)/2f
                        val rx2=(pr-pl)/2f; val ry2=(pb-pt2)/2f; val rad2=(rx2+ry2)/2f
                        val dx2=strokeStartWx-cx2; val dy2=strokeStartWy-cy2
                        val d_sq=dx2*dx2+dy2*dy2
                        if (d_sq > rad2*rad2 + 1e-4f) {
                            val A=(rad2*rad2/d_sq)
                            val B=(rad2* kotlin.math.sqrt((d_sq-rad2*rad2).toDouble())/d_sq).toFloat()
                            val t1x=cx2+A*dx2-B*(-dy2); val t1y=cy2+A*dy2-B*dx2
                            val t2x=cx2+A*dx2+B*(-dy2); val t2y=cy2+A*dy2+B*dx2
                            val a1= kotlin.math.atan2((t1y-cy2).toDouble()/ry2,(t1x-cx2).toDouble()/rx2).toFloat()
                            val a2= kotlin.math.atan2((t2y-cy2).toDouble()/ry2,(t2x-cx2).toDouble()/rx2).toFloat()
                            add(cx2+rx2*cosf(a1), cy2+ry2*sinf(a1), SnapType.TANGENT)
                            add(cx2+rx2*cosf(a2), cy2+ry2*sinf(a2), SnapType.TANGENT)
                        }
                    }
                    else -> {}
                }
            }

            // Parallel — constrain to be parallel to any edge of any shape
            if (snapParallel && !strokeStartWx.isNaN()) {
                val verts = shapeEndpoints(pts, t)
                for (i in verts.indices) {
                    val (ax,ay) = verts[i]; val (bx,by) = verts[(i+1) % verts.size]
                    val dx=bx-ax; val dy=by-ay
                    val len=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat().coerceAtLeast(1e-6f)
                    val ux=dx/len; val uy=dy/len
                    val tf=(wx-strokeStartWx)*ux+(wy-strokeStartWy)*uy
                    add(strokeStartWx+tf*ux, strokeStartWy+tf*uy, SnapType.PARALLEL)
                }
            }
        }

        // Intersection — all pairs of shapes, checking edge-edge crossings
        if (snapIntersection && strokes.size >= 2) {
            for (i in strokes.indices) for (j in i+1 until strokes.size) {
                val va = shapeEndpoints(strokes[i].data.points, strokes[i].data.type)
                val vb = shapeEndpoints(strokes[j].data.points, strokes[j].data.type)
                for (ia in va.indices) for (ib in vb.indices) {
                    val (x1a,y1a)=va[ia]; val (x2a,y2a)=va[(ia+1)%va.size]
                    val (x1b,y1b)=vb[ib]; val (x2b,y2b)=vb[(ib+1)%vb.size]
                    val denom=(x1a-x2a)*(y1b-y2b)-(y1a-y2a)*(x1b-x2b)
                    if (kotlin.math.abs(denom) < 1e-6f) continue
                    val tf=((x1a-x1b)*(y1b-y2b)-(y1a-y1b)*(x1b-x2b))/denom
                    val uf=-((x1a-x2a)*(y1a-y1b)-(y1a-y2a)*(x1a-x1b))/denom
                    if (tf in 0f..1f && uf in 0f..1f)
                        add(x1a+tf*(x2a-x1a), y1a+tf*(y2a-y1a), SnapType.INTERSECTION)
                }
            }
        }

        // Grid
        if (snapGrid && (paperType == PaperType.GRID || paperType == PaperType.ENGINEERING)) {
            val gs = gridSpacingPx()
            add(kotlin.math.round(wx/gs)*gs, kotlin.math.round(wy/gs)*gs, SnapType.GRID)
        }

        snapAwarenessResults = awarenessCandidates
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(compareBy({ it.first.type.priority }, { it.second })).first().first
    }

    // Returns union bounding box of all selectedItems (including selectedItem). Works for 1+ items.
    private fun computeGroupBounds(): FloatArray? {
        val allItems = (selectedItems + setOfNotNull(selectedItem)).toSet()
        if (allItems.isEmpty()) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (item in allItems) {
            val b = getBounds(item) ?: continue
            if (b[0] < minX) minX = b[0]; if (b[1] < minY) minY = b[1]
            if (b[2] > maxX) maxX = b[2]; if (b[3] > maxY) maxY = b[3]
        }
        return if (minX == Float.MAX_VALUE) null else floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun findItemAtPreferSelected(wx: Float, wy: Float): Any? {
        // Use tight hit test — only match if tap is on the actual shape, not just nearby
        val tightR = (8f / scaleFactor).coerceAtLeast(2f)
        // Prefer already-selected items (so re-tapping deselects the right one)
        for (item in selectedItems) {
            if (item is StrokeItem && strokeHitTestRotated(item.data, wx, wy, tightR + item.data.strokeWidth * 0.5f)) return item
        }
        if (selectedItem is StrokeItem) {
            val si = selectedItem as StrokeItem
            if (strokeHitTestRotated(si.data, wx, wy, tightR + si.data.strokeWidth * 0.5f)) return si
        }
        // Fallback: any item at position using normal hit testing
        return findItemAt(wx, wy)
    }

    // Converts a typed shape (RECTANGLE, TRIANGLE, etc.) into component LINE or PEN StrokeItems.
    // This makes every edge independently erasable, selectable, and splittable — like pen strokes.
    // CRITICAL: shapes store their rotation separately (applied via canvas.rotate at render time),
    // but component lines have no such rotation field applied elsewhere — so we must BAKE the
    // current rotation into the actual point coordinates here, or the converted lines land at the
    // shape's original unrotated position (causing wrong-edge erasure and apparent "rotation reset").
    private fun convertShapeToComponents(data: StrokeData): List<StrokeItem> {
        val pts = data.points; if (pts.size < 2) return emptyList()
        var verts = shapeEndpoints(pts, data.type)
        if (verts.isEmpty()) return emptyList()

        // Bake rotation: rotate every vertex around the shape's bounding-box center
        val rot = data.rotation
        if (rot != 0f) {
            var minX=verts[0].first; var maxX=verts[0].first; var minY=verts[0].second; var maxY=verts[0].second
            for ((vx,vy) in verts) { if(vx<minX)minX=vx; if(vx>maxX)maxX=vx; if(vy<minY)minY=vy; if(vy>maxY)maxY=vy }
            val cx=(minX+maxX)/2f; val cy=(minY+maxY)/2f
            val rad = Math.toRadians(rot.toDouble())
            val cos = kotlin.math.cos(rad).toFloat(); val sin = kotlin.math.sin(rad).toFloat()
            verts = verts.map { (vx,vy) ->
                val dx=vx-cx; val dy=vy-cy
                Pair(cx+dx*cos-dy*sin, cy+dx*sin+dy*cos)
            }
        }

        return when (data.type) {
            Tool.CIRCLE, Tool.ELLIPSE, Tool.ROUNDED_RECT -> {
                // Sample the ROTATED path directly (build path, then rotate via matrix) so the
                // sampled points already reflect the shape's current on-screen orientation —
                // and use a generous fixed segment count so a circle never looks like a polygon
                // ("connection of multiple lines") regardless of zoom level at conversion time.
                val path = data.buildPath()
                if (rot != 0f) {
                    val b = getBoundsRaw(StrokeItem(data, path, data.toPaint()))
                    if (b != null) {
                        val m = android.graphics.Matrix()
                        m.setRotate(rot, (b[0]+b[2])/2f, (b[1]+b[3])/2f)
                        path.transform(m)
                    }
                }
                val measure = android.graphics.PathMeasure(path, true)
                val segPts = mutableListOf<Float>()
                // Fixed high segment count at conversion time (erase-triggered, not per-frame) —
                // always smooth regardless of current zoom, never faceted.
                val segments = 96
                val len = measure.length
                val pos = FloatArray(2)
                // Index-based sampling (dist = k * step) avoids the floating-point drift that
                // cumulative "dist += step" produces over 96 additions — that drift was causing
                // the sampled loop to fall slightly short of full closure, leaving a real
                // (if tiny) gap in the geometry at the seam. That gap only became visible once
                // the shape was split by the eraser, showing up as an unexplained SECOND gap
                // (e.g. on the right side) in addition to the one the user actually erased.
                for (k in 0..segments) {
                    val dist = (len * k / segments).coerceAtMost(len)
                    measure.getPosTan(dist, pos, null)
                    segPts.add(pos[0]); segPts.add(pos[1])
                }
                // Force exact closure regardless of any residual PathMeasure rounding.
                if (segPts.size >= 4) { segPts[segPts.size-2] = segPts[0]; segPts[segPts.size-1] = segPts[1] }
                // Preserve the shape's ORIGINAL pen style so erasing/converting a shape never
                // changes its rendered thickness. PenStyle.BALL renders at 0.65x strokeWidth —
                // hardcoding it here silently shrunk every shape's line weight the instant it
                // was first touched by the eraser.
                val d = StrokeData(Tool.PEN, segPts, data.color, data.strokeWidth, false,
                    lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
                listOf(StrokeItem(d, d.buildPath(), d.toPaint()))
            }
            else -> {
                if (!EXPLICIT_VERTEX_SHAPES.contains(data.type)) {
                    // Complex/unhandled shape (RING, GEAR, CLOUD, SPEECH_BUBBLE, HEART, BURST,
                    // etc.) — shapeEndpoints() has no correct vertex list for these, so using it
                    // silently produced a "4 bounding-box corners" rectangle approximation that
                    // looked nothing like the original shape. Sample the ACTUAL rendered path
                    // directly instead — the same reliable technique already used for circles —
                    // emitting ONE fragment PER CONTOUR so genuinely multi-part shapes (anything
                    // built from multiple moveTo() calls) don't get their disconnected pieces
                    // incorrectly merged into a single connected pline.
                    val path = data.buildPath()
                    if (rot != 0f) {
                        val b = getBoundsRaw(StrokeItem(data, path, data.toPaint()))
                        if (b != null) {
                            val m = android.graphics.Matrix()
                            m.setRotate(rot, (b[0]+b[2])/2f, (b[1]+b[3])/2f)
                            path.transform(m)
                        }
                    }
                    val result = mutableListOf<StrokeItem>()
                    val measure = android.graphics.PathMeasure(path, false)
                    do {
                        val len = measure.length
                        if (len > 0f) {
                            val segPts = mutableListOf<Float>()
                            val segments = maxOf(24, (len / 4f).toInt())
                            val pos = FloatArray(2)
                            for (k in 0..segments) {
                                val dist = (len * k / segments).coerceAtMost(len)
                                measure.getPosTan(dist, pos, null)
                                segPts.add(pos[0]); segPts.add(pos[1])
                            }
                            if (segPts.size >= 4) {
                                val d = StrokeData(Tool.PEN, segPts, data.color, data.strokeWidth, false,
                                    lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
                                result.add(StrokeItem(d, d.buildPath(), d.toPaint()))
                            }
                        }
                    } while (measure.nextContour())
                    return result
                }
                // All simple straight-edged polygon shapes (rectangle, triangle, star, etc.):
                // build ONE unified multi-vertex pline (Tool.PEN) walking through all vertices in
                // order, closing the loop back to vertex 0 for closed shapes. This matches the
                // same object model as the dedicated Polyline tool — a single connected stroke,
                // not N separate LINE items — so unrelated edges stay joined as one object and
                // the eraser (via splitStrokeAroundEraser) naturally produces correctly-connected
                // pline fragments around whatever was actually erased.
                val closed = CLOSED_SHAPES.contains(data.type)
                val plinePts = mutableListOf<Float>()
                for ((vx, vy) in verts) { plinePts.add(vx); plinePts.add(vy) }
                if (closed && verts.isNotEmpty()) { plinePts.add(verts[0].first); plinePts.add(verts[0].second) }
                val d = StrokeData(Tool.PEN, plinePts, data.color, data.strokeWidth, false,
                    lineType = data.lineType, penStyle = data.penStyle, opacity = data.opacity)
                listOf(StrokeItem(d, d.buildPath(), d.toPaint()))
            }
        }
    }

    // Backward-compat wrapper
    private fun findNearestEndpoint(wx: Float, wy: Float): Pair<Float, Float>? =
        findSnapTarget(wx, wy)?.let { Pair(it.wx, it.wy) }

    private fun distToSeg(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0f && dy == 0f) return distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return distance(px, py, x1 + t * dx, y1 + t * dy)
    }

    fun addText(text: String, x: Float, y: Float, size: Float, rotation: Float, color: Int, spans: MutableList<TextSpanData> = mutableListOf(), fontFamily: String = "sans-serif", opacity: Int = 255): TextItem? {
        if (text.isBlank()) return null
        val (cx, cy) = if (canvasMode != CanvasMode.INFINITE) clampToPageForText(x, y, text, size, fontFamily) else Pair(x, y)
        val item = TextItem(text, cx, cy, color, size, rotation); item.spans = spans; item.fontFamily = fontFamily; item.opacity = opacity
        actions.add(item); redoStack.clear(); markSpatialDirty(); invalidate()
        return item
    }

    // Inserts a fully pre-built TextItem (used for link creation, where linkTarget is already
    // set on the item before insertion) - clamps position to the page like addText does.
    fun addLinkText(item: TextItem) {
        if (item.text.isBlank()) return
        val (cx, cy) = if (canvasMode != CanvasMode.INFINITE) clampToPageForText(item.x, item.y, item.text, item.size, item.fontFamily) else Pair(item.x, item.y)
        item.x = cx; item.y = cy
        actions.add(item); redoStack.clear(); markSpatialDirty(); invalidate()
    }

    fun removeTextItem(item: TextItem) { actions.remove(item); markSpatialDirty(); invalidate() }

    fun bringToFront(item: Any) {
        if (actions.remove(item)) { actions.add(item); redoStack.clear(); markSpatialDirty(); invalidate() }
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
        actions.add(item); redoStack.clear(); markSpatialDirty()
        loadBitmapAsync(path) { bmp -> item.bitmap = bmp; item.loading = false; markSpatialDirty(); invalidate() }
        invalidate()
    }

    fun addAudioItem(filePath: String, title: String, durationMs: Long) {
        val item = AudioItem(filePath, title, screenCenterWorldX(), screenCenterWorldY(), durationMs)
        actions.add(item); redoStack.clear(); markSpatialDirty(); invalidate()
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
        // If dimension tool is mid-placement (point 1 placed, waiting for point 2),
        // undo cancels the in-progress dimension and resets to IDLE — no action removed.
        if (dimPhase != DimPhase.IDLE) { dimPhase = DimPhase.IDLE; dimAngPhase = 0; invalidate(); return }
        if (actions.isEmpty()) return
        val last = actions.removeAt(actions.size - 1)
        if (last is FillToggleAction) {
            last.item.data.fill = last.wasFilled; last.item.data.fillColorVal = last.wasColor; last.item.paint = last.item.data.toPaint()
        }
        redoStack.add(last); if (redoStack.size > 50) redoStack.removeAt(0); markSpatialDirty(); invalidate()
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.size - 1)
        if (next is FillToggleAction) {
            next.item.data.fill = !next.wasFilled; next.item.data.fillColorVal = next.newColor; next.item.paint = next.item.data.toPaint()
        }
        actions.add(next); markSpatialDirty(); invalidate()
    }
    fun clearAll() { actions.clear(); redoStack.clear(); selectedItem = null; activeTableItem = null; markSpatialDirty(); invalidate() }
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
        // Capture the z-order insertion point NOW (fill was initiated at this moment), not when
        // the async flood-fill computation finishes. Without this, a stroke drawn while the fill
        // is still computing would commit first, then the fill's completion callback would
        // append itself AFTER that stroke — landing on top of it, even though the user drew the
        // stroke after tapping fill and expects it to stay on top.
        val fillInsertionIndex = actions.size
        // Expand the fill-detection area beyond the exact visible viewport (a fixed margin on
        // each side, ~1.5x viewport width/height total) so a boundary just off-screen still
        // gets detected — without ballooning to full-page size, which for a large document could
        // mean a bitmap tens of MB in size and a proportionally slower flood-fill on every tap.
        // This is a deliberate middle ground: better reach than viewport-only, but bounded cost.
        val marginX = (width * 0.3f); val marginY = (height * 0.3f)
        val viewW = width + marginX * 2f; val viewH = height + marginY * 2f
        // Hard safety cap: never allocate more than ~16M pixels (~64MB ARGB_8888) regardless of
        // screen size or margin — a previous version of this margin+scale combination produced
        // a ~247MB single bitmap allocation on a typical phone screen, which reliably crashes
        // with OutOfMemoryError, especially on lower-RAM devices. If the requested area would
        // exceed the budget, the SUPERSAMPLING scale shrinks (never the margin reach) to fit.
        val maxPixels = 16_000_000L
        var scale = 2.0f
        var w = (viewW * scale).toInt().coerceAtLeast(1); var h = (viewH * scale).toInt().coerceAtLeast(1)
        if (w.toLong() * h.toLong() > maxPixels) {
            val shrink = kotlin.math.sqrt(maxPixels.toDouble() / (w.toLong() * h.toLong())).toFloat()
            scale = (scale * shrink).coerceAtLeast(0.5f)
            w = (viewW * scale).toInt().coerceAtLeast(1); h = (viewH * scale).toInt().coerceAtLeast(1)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
        cv.save(); cv.scale(scale, scale); cv.translate(translateX + marginX, translateY + marginY); cv.scale(scaleFactor, scaleFactor)
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

        val px = ((screenX + marginX) * scale).toInt().coerceIn(0, w - 1); val py = ((screenY + marginY) * scale).toInt().coerceIn(0, h - 1)
        val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        fun isWall(x: Int, y: Int): Boolean = ((pixels[y * w + x] ushr 24) and 0xFF) > 25
        if (isWall(px, py)) { invalidate(); return }
        // Capture world coords now (before any canvas scroll/zoom changes)
        val tapWorldX = screenToWorldX(screenX); val tapWorldY = screenToWorldY(screenY)

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
                post { if (clusters.isNotEmpty()) { val (cx, cy) = clusters[0]; zoomTo(screenToWorldX(cx/scale - marginX), screenToWorldY(cy/scale - marginY), (scaleFactor*2.5f).coerceAtMost(6f)) }; invalidate() }
            } else {
                val cw = maxX - minX + 1; val ch = maxY - minY + 1
                val fp = IntArray(cw * ch) { i -> val gx=minX+i%cw; val gy=minY+i/cw; if(visited[gy*w+gx]) fillColor else Color.TRANSPARENT }
                val fb = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888); fb.setPixels(fp, 0, cw, 0, 0, cw, ch)
                val folder = File(ctx.filesDir, "images"); if (!folder.exists()) folder.mkdirs()
                val outFile = File(folder, "fill_${System.currentTimeMillis()}.png")
                try { FileOutputStream(outFile).use { fb.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { post { invalidate() }; return@execute }
                val wx0 = screenToWorldX(minX / scale - marginX); val wy0 = screenToWorldY(minY / scale - marginY)
                val wx1 = screenToWorldX((minX + cw) / scale - marginX); val wy1 = screenToWorldY((minY + ch) / scale - marginY)
                val newArea = (wx1 - wx0) * (wy1 - wy0)
                post {
                    val fi = FillItem(outFile.absolutePath, wx0, wy0, wx1 - wx0, wy1 - wy0)
                    if (pendingHatchPattern != null) { fi.hatchPattern = pendingHatchPattern; fi.hatchColor = pendingHatchColor; fi.hatchScale = pendingHatchScale }
                    fi.bitmap = fb
                    // Only remove an existing FillItem if the tap pixel lands inside it AND the
                    // new fill is roughly the same size as the existing one — meaning the user
                    // re-tapped the same closed region to replace its color. If the new fill is
                    // MUCH SMALLER (e.g. the intersection between this shape and an overlapping
                    // one), it's a separate, more localized fill that should layer on top instead
                    // of destroying the larger existing fill underneath it.
                    val tapWx = tapWorldX; val tapWy = tapWorldY
                    actions.removeAll { existing ->
                        if (existing !is FillItem) return@removeAll false
                        // Quick bbox check first
                        if (tapWx < existing.x || tapWx > existing.x + existing.w ||
                            tapWy < existing.y || tapWy > existing.y + existing.h) return@removeAll false
                        // Pixel check: is the tap point non-transparent in the existing fill?
                        val existBmp = existing.bitmap ?: getOrLoadFillBitmap(existing) ?: return@removeAll false
                        val bx = ((tapWx - existing.x) / existing.w * existBmp.width).toInt().coerceIn(0, existBmp.width - 1)
                        val by = ((tapWy - existing.y) / existing.h * existBmp.height).toInt().coerceIn(0, existBmp.height - 1)
                        val alpha = (existBmp.getPixel(bx, by) ushr 24) and 0xFF
                        if (alpha <= 25) return@removeAll false  // tap wasn't actually inside this fill
                        val existingArea = existing.w * existing.h
                        newArea >= existingArea * 0.7f  // only replace if roughly the same region, not a smaller sub-region
                    }
                    // Insert at the position captured when the fill was initiated, not the end —
                    // so strokes drawn while this fill was still computing correctly stay on top.
                    val safeIndex = fillInsertionIndex.coerceAtMost(actions.size)
                    actions.add(safeIndex, fi); redoStack.clear(); markSpatialDirty(); invalidate()
                }
            }
        }
    }

    // ── Serialize / Deserialize ─────────────────────────────────────

    fun serialize(): String {
        flushDirtyFillItems()  // safety net: never save with fill edits still only in memory
        val sb = StringBuilder()
        sb.append("META\u0001${paperType.name}\u0001${canvasMode.name}\u0001${paperSize.name}\u0001${pageOrientation.name}\u0001$paperColor\n")
        for (a in actions) when (a) {
            is TableItem -> sb.append(a.serialize())
            is StrokeItem -> sb.append("${a.data.type.name}|${a.data.color}|${a.data.strokeWidth}|${a.data.fill}|${a.data.rotation}|${a.data.points.joinToString(",")}|${a.data.fillColorVal}|${a.data.penStyle.name}|${a.data.opacity}|${a.data.brushStyle.name}|${a.data.widths.joinToString(",")}|${a.data.lineType.name}|${a.data.isLocked}|${a.data.clipHoles.joinToString(";") { h -> "${h[0]},${h[1]},${h[2]}" }}|${a.data.calligraphySlantThickness}|${a.data.isPolyline}\n")
            is TextItem -> sb.append("TEXT\u0001${a.x}\u0001${a.y}\u0001${a.color}\u0001${a.size}\u0001${a.rotation}\u0001${a.spans.joinToString(";") { "${it.start},${it.end},${it.type},${it.value}" }}\u0001${a.text.replace("\n", "\u0002")}\u0001${a.maxWidth}\u0001${a.fontFamily}\u0001${a.opacity}\u0001${a.linkTarget ?: ""}\n")
            is ImageItem -> sb.append("IMAGE\u0001${a.path}\u0001${a.x}\u0001${a.y}\u0001${a.w}\u0001${a.h}\u0001${a.rotation}\n")
            is FillItem -> sb.append("FILL\u0001${a.path}\u0001${a.x}\u0001${a.y}\u0001${a.w}\u0001${a.h}\n")
            is AudioItem -> sb.append("AUDIO\u0001${a.filePath}\u0001${a.title.replace("\u0001","_")}\u0001${a.x}\u0001${a.y}\u0001${a.durationMs}\u0001${a.radius}\n")
        }
        return sb.toString()
    }

    fun loadFromString(content: String) {
        actions.clear(); redoStack.clear(); selectedItem = null; activeTableItem = null; markSpatialDirty()
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
                            if (p.size >= 9)  item.maxWidth  = p[8].toFloatOrNull() ?: 0f
                            if (p.size >= 10) item.fontFamily = p[9]
                            if (p.size >= 11) item.opacity   = p[10].toIntOrNull() ?: 255
                            if (p.size >= 12 && p[11].isNotBlank()) item.linkTarget = p[11]
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
                                val lType = if (p.size >= 12 && p[11].isNotBlank()) try { LineType.valueOf(p[11]) } catch (e: Exception) { LineType.CONTINUOUS } else LineType.CONTINUOUS
                                val locked = if (p.size >= 13) p[12] == "true" else false
                                val holes = mutableListOf<FloatArray>()
                                if (p.size >= 14 && p[13].isNotBlank()) for (h in p[13].split(";")) { val hv = h.split(","); if (hv.size == 3) holes.add(floatArrayOf(hv[0].toFloat(), hv[1].toFloat(), hv[2].toFloat())) }
                                val slant = if (p.size >= 15) p[14].toFloatOrNull() ?: 0.65f else 0.65f
                                val isPoly = if (p.size >= 16) p[15] == "true" else false
                                val d = StrokeData(type, pts, color, sw, fill, rot, fcv, pStyle, opac, bStyle, wArr, lType, locked, slant, holes, isPoly); actions.add(StrokeItem(d, d.buildPath(), d.toPaint()))
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
