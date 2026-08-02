package com.enginotes.app

// Bullet / numbering / checklist support for TextItem, kept entirely separate from
// MainActivity.kt / DrawingView.kt / TextEditingExtensions.kt so this can be reviewed, tested,
// or reverted on its own. Integration into those files is limited to a handful of call sites
// (documented at each one) — the actual formatting logic all lives here.
//
// HOW THIS FITS THE EXISTING ARCHITECTURE:
// TextItem's rich formatting (bold/italic/underline/color/highlight) already works by storing a
// list of TextSpanData(start, end, type, value) records that get replayed onto a real Android
// Spannable — both the live editing EditText's Spannable and DrawingView's own StaticLayout for
// static (non-editing) rendering. Lists reuse that exact same mechanism instead of inventing a
// parallel one: each bullet/number/checklist line gets a span implementing LeadingMarginSpan2,
// which is Android's own built-in mechanism for drawing something in a line's left margin during
// normal text layout (it's what android.text.style.BulletSpan itself uses). Because both the
// EditText and the StaticLayout render through Android's ordinary Layout/StaticLayout drawing
// path, these spans render correctly in BOTH places with no changes needed to either's drawing
// code — this is precisely why LeadingMarginSpan2 was the right tool here rather than, say,
// inserting literal bullet characters into the text itself (which would get corrupted the moment
// the user edited that line, and couldn't auto-renumber).
//
// PERSISTENCE: TextSpanData already serializes generically as "start,end,type,value" with no
// per-type special-casing in DrawingView.serialize()/deserialize — so the three new type chars
// added here ('B' bullet, 'N' number, 'K' checklist) persist and load automatically with zero
// changes to the save format.

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.style.LeadingMarginSpan
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

// ---------------------------------------------------------------------------------------------
// Style catalogs — 10 bullets, 10 numbering styles, 5 checklist styles, each visually distinct
// within its own category (that's the constraint that actually matters for usability; a bullet
// glyph and a checklist glyph don't need to differ from each other, only from their own peers).
// ---------------------------------------------------------------------------------------------

enum class BulletStyle(val glyph: String, val label: String) {
    DISC("\u25CF", "Disc"),           // ●
    CIRCLE("\u25CB", "Circle"),       // ○
    SQUARE("\u25AA", "Square"),       // ▪
    DIAMOND("\u25C6", "Diamond"),     // ◆
    ARROW("\u27A4", "Arrow"),         // ➤
    DASH("\u2013", "Dash"),           // –
    STAR("\u2605", "Star"),           // ★
    TRIANGLE("\u25B8", "Triangle"),   // ▸
    PLUS("+", "Plus"),
    CHEVRON("\u203A", "Chevron");     // ›

    companion object { fun safe(i: Int) = values()[i.coerceIn(0, values().size - 1)] }
}

enum class NumberStyle(val label: String, val numeral: (Int) -> String, private val wrap: (String) -> String) {
    ARABIC_DOT("1.", { n -> "$n" }, { s -> "$s." }),
    ARABIC_PAREN("1)", { n -> "$n" }, { s -> "$s)" }),
    ARABIC_BRACKET("[1]", { n -> "$n" }, { s -> "[$s]" }),
    ARABIC_BOTH_PAREN("(1)", { n -> "$n" }, { s -> "($s)" }),
    LOWER_ALPHA("a.", { n -> numberStyleToAlpha(n, false) }, { s -> "$s." }),
    UPPER_ALPHA("A.", { n -> numberStyleToAlpha(n, true) }, { s -> "$s." }),
    LOWER_ROMAN("i.", { n -> numberStyleToRoman(n).lowercase() }, { s -> "$s." }),
    UPPER_ROMAN("I.", { n -> numberStyleToRoman(n) }, { s -> "$s." }),
    ZERO_PADDED("01.", { n -> n.toString().padStart(2, '0') }, { s -> "$s." }),
    COLON("1:", { n -> "$n" }, { s -> "$s:" });

    /** A single top-level number, styled — unchanged behavior from before this was split into
     * numeral+wrap (still what the style picker's own preview uses, and still what a
     * single-level, unindented list item renders with). */
    fun format(n: Int): String = wrap(numeral(n))

    /** A full outline path (e.g. [2, 1] for the 1st sub-item under the 2nd top-level item),
     * joined with dots and styled only once at the end — "2.1" for ARABIC_DOT, "(2.1)" for
     * ARABIC_BOTH_PAREN, etc. Each level's own number always uses this style's numeral form
     * (so UPPER_ROMAN nesting looks like "II.I", not "II.1"), which keeps a nested item visually
     * consistent with its parent even though there's no single natural convention here to match
     * against — most word processors don't support mixing numeral kinds by depth in one style.
     */
    fun formatPath(path: List<Int>): String = wrap(path.joinToString(".") { numeral(it) })

    companion object {
        fun safe(i: Int) = values()[i.coerceIn(0, values().size - 1)]
    }
}

// Kotlin forbids enum entries from referencing their own companion object during entry
// initialization (entries are constructed before the companion object exists) — that's what
// actually broke the build here, not the functions themselves. Top-level functions have no such
// restriction, so LOWER_ALPHA/UPPER_ALPHA/LOWER_ROMAN/UPPER_ROMAN above call these instead of a
// companion-object version of the same logic.
// a, b, c ... z, aa, ab ... — same scheme spreadsheets use for column letters, so it never "runs
// out" the way a fixed a-z table would past 26 items.
private fun numberStyleToAlpha(n: Int, upper: Boolean): String {
    var num = n; val sb = StringBuilder()
    while (num > 0) { val rem = (num - 1) % 26; sb.insert(0, ('a' + rem)); num = (num - 1) / 26 }
    val s = if (sb.isEmpty()) "a" else sb.toString()
    return if (upper) s.uppercase() else s
}
private fun numberStyleToRoman(n: Int): String {
    if (n <= 0) return "0"
    val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val syms = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var num = n; val sb = StringBuilder()
    for (i in vals.indices) { while (num >= vals[i]) { sb.append(syms[i]); num -= vals[i] } }
    return sb.toString()
}

enum class ChecklistStyle(val unchecked: String, val checked: String, val label: String) {
    SQUARE("\u2610", "\u2611", "Square"),     // ☐ / ☑
    CIRCLE("\u26AA", "\u26AB", "Circle"),     // ⚪ / ⚫
    DIAMOND("\u25C7", "\u25C6", "Diamond"),   // ◇ / ◆
    ROUNDED("\u2B1C", "\u2705", "Rounded"),   // ⬜ / ✅
    STAR("\u2606", "\u2605", "Star");         // ☆ / ★

    companion object { fun safe(i: Int) = values()[i.coerceIn(0, values().size - 1)] }
}

// ---------------------------------------------------------------------------------------------
// Spans. One shared base handles the actual margin-drawing (identical for all three kinds —
// draw a glyph, indent the rest of the line past it); each subtype only supplies which glyph.
// ---------------------------------------------------------------------------------------------

const val LIST_SPAN_TYPE_BULLET = 'B'
const val LIST_SPAN_TYPE_NUMBER = 'N'
const val LIST_SPAN_TYPE_CHECK = 'K'

sealed class ListMarginSpan(var styleIndex: Int, val textSizePx: Float, var indentLevel: Int = 0) : LeadingMarginSpan2Compat {
    // Margin scales with the text's own size (a 12pt list and a 48pt heading-sized list both get
    // proportionally sensible indent/glyph-size) AND with indentLevel, so a double-Enter sub-item
    // sits visibly further right than its parent, like Word's Tab-to-demote behavior.
    val marginPx: Int get() = ((textSizePx * 2.4f).toInt().coerceAtLeast(32)) + indentLevel * (textSizePx * 1.9f).toInt().coerceAtLeast(24)
    // A number or checkbox glyph is legible at full text size, but a solid bullet dot/square at
    // full text height reads as oversized next to the text it's labeling — real bullet points sit
    // noticeably smaller than the cap-height of the text beside them. BulletMarginSpan overrides
    // this down to 0.7; number/checklist glyphs stay at their natural 1.0.
    open val glyphSizeScale: Float = 1.0f
    // Bounding box of the glyph as last drawn, in the same coordinate space as MotionEvent
    // coordinates within the EditText — used only for checklist tap-to-toggle hit-testing.
    // Deliberately not persisted; it's a pure runtime draw-time cache, rebuilt every layout pass.
    var lastDrawnBounds: RectF? = null
    // When true, drawLeadingMargin below does nothing — ListAwareEditText sets this once it
    // takes over drawing a span itself. getLeadingMargin() (the margin RESERVATION that pushes
    // paragraph text to the right) is a completely separate, much more fundamental layout-time
    // mechanism and is NOT affected by this flag; only the actual glyph DRAWING is skipped here.
    // See ListAwareEditText's own doc comment for why relying solely on drawLeadingMargin being
    // invoked reliably by a live, editable DynamicLayout — especially for a genuinely empty
    // paragraph, which is exactly the case that kept silently failing to show anything — wasn't
    // something this could keep being built around without a way to verify it.
    var suppressAutoDraw: Boolean = false

    abstract fun glyphFor(): String

    override fun getLeadingMargin(first: Boolean): Int = marginPx

    override fun drawLeadingMargin(
        canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?
    ) {
        if (suppressAutoDraw) return
        if (!first) { lastDrawnBounds = null; return }
        val glyph = glyphFor()
        val gp = Paint(paint)
        gp.textSize = paint.textSize * glyphSizeScale
        val glyphWidth = gp.measureText(glyph)
        // Right-align the glyph within its margin column (with a little breathing room before
        // the text starts) rather than left-jammed against the page edge — this is what makes a
        // multi-digit "10." line up with a single-digit "9." the way a real document does.
        val gx = if (dir >= 0) (x + marginPx - glyphWidth - marginPx * 0.18f) else (x - marginPx * 0.18f)
        canvas.drawText(glyph, gx, baseline.toFloat(), gp)
        lastDrawnBounds = RectF(gx, top.toFloat(), gx + glyphWidth, bottom.toFloat())
    }
}

// LeadingMarginSpan2 additionally requires getLeadingMarginLineCount(); split into its own tiny
// interface only so ListMarginSpan's constructor signature above stays readable — implemented
// identically (always 1: the glyph only ever draws on a line's first visual row, matching every
// bullet/number list convention) by all three concrete span types below via the default method.
interface LeadingMarginSpan2Compat : android.text.style.LeadingMarginSpan.LeadingMarginSpan2 {
    override fun getLeadingMarginLineCount(): Int = 1
}

class BulletMarginSpan(styleIndex: Int, textSizePx: Float, indentLevel: Int = 0) : ListMarginSpan(styleIndex, textSizePx, indentLevel) {
    override val glyphSizeScale: Float = 0.7f
    override fun glyphFor(): String = BulletStyle.safe(styleIndex).glyph
}

class NumberMarginSpan(styleIndex: Int, textSizePx: Float, indentLevel: Int = 0, var ordinal: Int = 1, var path: List<Int> = listOf(1)) : ListMarginSpan(styleIndex, textSizePx, indentLevel) {
    override fun glyphFor(): String = NumberStyle.safe(styleIndex).formatPath(path)
}

class ChecklistMarginSpan(styleIndex: Int, textSizePx: Float, indentLevel: Int = 0, var checked: Boolean = false) : ListMarginSpan(styleIndex, textSizePx, indentLevel) {
    override fun glyphFor(): String { val st = ChecklistStyle.safe(styleIndex); return if (checked) st.checked else st.unchecked }
}

/**
 * A plain EditText that draws its own list glyphs after the normal text draw pass, instead of
 * relying solely on LeadingMarginSpan2's drawLeadingMargin callback being invoked reliably by a
 * live, editable DynamicLayout for every paragraph — in particular for a genuinely empty
 * paragraph (no characters at all), which is exactly the case that kept silently rendering
 * nothing: applying a bullet/number/checklist to an empty line showed no visible glyph until
 * some other edit forced a layout rebuild for an unrelated reason.
 *
 * getLeadingMargin() (the RESERVATION that pushes paragraph text right, making room for the
 * glyph) is untouched — that's a layout-time concern Layout.getDesiredWidth()/line-breaking
 * always consults regardless of drawing, and isn't what was unreliable here. Only the actual
 * glyph PAINTING is taken over, driven directly off this EditText's own Layout geometry
 * (getLineTop/getLineBaseline/getLineLeft), which is guaranteed populated for every line —
 * including an empty one — the moment layout has run, well before onDraw is ever called.
 */
class ListAwareEditText(context: android.content.Context) : android.widget.EditText(context) {
    override fun onDraw(canvas: Canvas) {
        // suppressAutoDraw MUST be set before super.onDraw() runs, not after — super.onDraw()
        // is what internally invokes drawLeadingMargin (the old mechanism), so setting the flag
        // afterward meant drawLeadingMargin still drew the glyph once via the old path on every
        // single call, and then the code below drew it again — two glyphs, every frame, not just
        // a one-time flicker on the first frame like the original comment here assumed.
        val edForSuppress = text as? Spannable
        edForSuppress?.getSpans(0, edForSuppress.length, ListMarginSpan::class.java)?.forEach { it.suppressAutoDraw = true }
        super.onDraw(canvas)
        val layout = layout ?: return
        val ed = edForSuppress ?: return
        val spans = ed.getSpans(0, ed.length, ListMarginSpan::class.java)
        if (spans.isEmpty()) return
        // Defensive: at most one glyph per visual line, full stop. Spans are supposed to map
        // 1:1 with paragraph starts, but with markers now confirmed to persist correctly across
        // Enter presses (unlike before), a second, previously-masked issue showed up: later list
        // lines were each drawing one EXTRA glyph than the line before it (line 3 showing 3
        // glyphs, not 1). Tracking which line numbers have already been drawn this pass makes
        // that structurally impossible rather than depending on getting every span's line-match
        // check exactly right.
        val drawnLines = HashSet<Int>()
        for (sp in spans) {
            val pos = ed.getSpanStart(sp).coerceIn(0, ed.length)
            val line = layout.getLineForOffset(pos)
            // Only the paragraph's own start line gets a glyph — matches the
            // getLeadingMarginLineCount()=1 contract the LeadingMarginSpan2 path uses elsewhere,
            // so wrapped continuation lines of a long list item don't each get their own glyph.
            var trueLineStart = pos
            while (trueLineStart > 0 && ed[trueLineStart - 1] != '\n') trueLineStart--
            if (layout.getLineStart(line) != trueLineStart) continue
            if (!drawnLines.add(line)) continue

            val glyph = sp.glyphFor()
            val gp = Paint(paint)
            gp.color = currentTextColor
            gp.textSize = paint.textSize * sp.glyphSizeScale
            val glyphWidth = gp.measureText(glyph)
            // Was: layout.getLineLeft(line) + totalPaddingLeft. getLineLeft() is supposed to
            // equal exactly this span's own marginPx (that's what getLeadingMargin() reserved
            // for this paragraph) — but for a genuinely empty (zero-character) line specifically,
            // it was coming back as if no margin had been reserved at all, pushing gx (and the
            // glyph) off to the far left, effectively invisible. marginPx is a deterministic
            // property of the span itself (not something that needs to be re-derived from the
            // layout), so computing directly from it avoids depending on that query being correct
            // for a paragraph with no characters in it.
            val lineLeft = sp.marginPx.toFloat() + totalPaddingLeft
            val top = layout.getLineTop(line) + totalPaddingTop
            val bottom = layout.getLineBottom(line) + totalPaddingTop
            val baseline = layout.getLineBaseline(line) + totalPaddingTop
            // Same "right-aligned in the margin column, with a little breathing room before the
            // text starts" positioning as the drawLeadingMargin path this replaces — kept
            // identical so switching between live-editing and static rendering doesn't visibly
            // shift anything.
            val gx = lineLeft - glyphWidth - sp.marginPx * 0.18f
            canvas.drawText(glyph, gx, baseline.toFloat(), gp)
            sp.lastDrawnBounds = RectF(gx, top.toFloat(), gx + glyphWidth, bottom.toFloat())
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Core operations: find line bounds, apply/toggle a list style on a line range, renumber.
// All operate on a plain Spannable so they work identically whether that Spannable belongs to the
// live editing EditText or a throwaway SpannableStringBuilder used for static rendering.
// ---------------------------------------------------------------------------------------------

private fun lineStart(text: CharSequence, pos: Int): Int {
    var i = pos.coerceIn(0, text.length)
    while (i > 0 && text[i - 1] != '\n') i--
    return i
}
private fun lineEnd(text: CharSequence, pos: Int): Int {
    var i = pos.coerceIn(0, text.length)
    while (i < text.length && text[i] != '\n') i++
    return i
}

// All line-start offsets touched by [from, to] (inclusive of the line the caret/selection ends
// on, matching how every list-editing UI treats a selection spanning multiple lines).
private fun touchedLineStarts(text: CharSequence, from: Int, to: Int): List<Int> {
    val starts = mutableListOf<Int>()
    var pos = lineStart(text, from)
    val hardEnd = to.coerceIn(0, text.length)
    while (true) {
        starts.add(pos)
        val end = lineEnd(text, pos)
        if (end >= hardEnd) break
        pos = (end + 1).coerceAtMost(text.length)
        if (pos > text.length) break
    }
    return starts
}

private fun removeListSpansOnLine(editable: Spannable, ls: Int, le: Int) {
    for (sp in editable.getSpans(ls, le, ListMarginSpan::class.java)) editable.removeSpan(sp)
}

/**
 * Applies (or, if the exact same style is already on every touched line, removes — a toggle)
 * a bullet/number/checklist style across every line touched by [from, to]. [kind] is one of
 * LIST_SPAN_TYPE_BULLET / _NUMBER / _CHECK. Always call [renumberLists] afterward if any number
 * spans could have been added, removed, or reordered by this call.
 */
fun applyListStyle(editable: Spannable, from: Int, to: Int, kind: Char, styleIndex: Int, textSizePx: Float) {
    val starts = touchedLineStarts(editable, from, to)
    // Toggle off: every touched line already has this exact kind+style applied, so the intent
    // is "remove it" rather than "reapply it" — matches how the same toolbar button toggling a
    // BIU style off when already active behaves elsewhere in this app.
    val alreadyAllSame = starts.isNotEmpty() && starts.all { ls ->
        val le = lineEnd(editable, ls)
        editable.getSpans(ls, le, ListMarginSpan::class.java).any {
            (it.javaClass == spanClassFor(kind)) && it.styleIndex == styleIndex
        }
    }
    for (ls in starts) {
        val le = lineEnd(editable, ls)
        removeListSpansOnLine(editable, ls, le)
        if (!alreadyAllSame) {
            val span: ListMarginSpan = when (kind) {
                LIST_SPAN_TYPE_BULLET -> BulletMarginSpan(styleIndex, textSizePx)
                LIST_SPAN_TYPE_NUMBER -> NumberMarginSpan(styleIndex, textSizePx)
                LIST_SPAN_TYPE_CHECK -> ChecklistMarginSpan(styleIndex, textSizePx, checked = false)
                else -> return
            }
            val end = if (le < editable.length) le + 1 else le  // swallow trailing \n so span doesn't bleed onto next line if text shifts
            // SPAN_INCLUSIVE_INCLUSIVE, not SPAN_EXCLUSIVE_EXCLUSIVE — confirmed via device
            // diagnostics that a zero-length SPAN_EXCLUSIVE_EXCLUSIVE span (ls == end, i.e. an
            // empty line) was being silently dropped by setSpan() and never actually added, while
            // a non-zero-length one (a line with text) worked fine. INCLUSIVE_INCLUSIVE is the
            // same flag type Android's own text cursor uses for its own zero-length position
            // markers, and doesn't have this problem.
            editable.setSpan(span, ls, end.coerceAtMost(editable.length).coerceAtLeast(ls), Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }
}

private fun spanClassFor(kind: Char): Class<out ListMarginSpan> = when (kind) {
    LIST_SPAN_TYPE_BULLET -> BulletMarginSpan::class.java
    LIST_SPAN_TYPE_NUMBER -> NumberMarginSpan::class.java
    else -> ChecklistMarginSpan::class.java
}

/**
 * Recomputes ordinals for every NumberMarginSpan in [editable] so consecutive lines using the
 * SAME numbering style count 1, 2, 3... and a new style (or a non-numbered line) breaking the
 * run starts a fresh count — the same auto-renumbering behavior as Word/Docs numbered lists.
 * Call this after any edit that could touch a numbered line: text insertion/deletion, or a list
 * style being applied/removed/changed.
 */
fun renumberLists(editable: Spannable) {
    val spans = editable.getSpans(0, editable.length, NumberMarginSpan::class.java)
        .sortedBy { editable.getSpanStart(it) }
    // path[i] is the current counter at outline depth i (0 = top level). Going back to a
    // shallower depth (or the same depth again) drops anything deeper and bumps that depth's own
    // counter; going deeper pads up with fresh counters starting at 1 — the same way a
    // multi-level list restarts its sub-numbering under each new parent item in Word/Docs. A
    // style change or a gap (non-numbered/blank line breaking the run) clears the whole path and
    // starts over, same as the old flat version did per indent level.
    val path = mutableListOf<Int>()
    var runStyle = -1; var lastLineEnd = -1
    for (sp in spans) {
        val start = editable.getSpanStart(sp)
        val contiguous = lastLineEnd >= 0 && start <= lastLineEnd + 1
        if (!contiguous || sp.styleIndex != runStyle) { path.clear(); runStyle = sp.styleIndex }
        val indent = sp.indentLevel
        if (indent < path.size) {
            while (path.size > indent + 1) path.removeAt(path.size - 1)
            path[indent] = path[indent] + 1
        } else {
            while (path.size < indent) path.add(1)
            path.add(1)
        }
        sp.path = path.toList()
        sp.ordinal = path.last()
        lastLineEnd = editable.getSpanEnd(sp)
    }
}

/**
 * Toggles a ChecklistMarginSpan's checked state if [x],[y] (in the same coordinate space as a
 * MotionEvent delivered to the view owning [editable]/[layout]) falls within that line's glyph
 * bounds. Returns true if a checkbox was toggled (caller should re-render/invalidate).
 */
fun toggleChecklistAt(editable: Spannable, x: Float, y: Float): Boolean {
    for (sp in editable.getSpans(0, editable.length, ChecklistMarginSpan::class.java)) {
        val b = sp.lastDrawnBounds ?: continue
        // Generous vertical/horizontal padding around the drawn glyph — the actual glyph is
        // small, and a tap needs a real touch target, not just the visible ink.
        val padded = RectF(b.left - 12f, b.top - 6f, b.right + 12f, b.bottom + 6f)
        if (padded.contains(x, y)) { sp.checked = !sp.checked; return true }
    }
    return false
}

/**
 * Word-style autoformat: typing "- ", "> ", or "5. " (any number) at the very start of an
 * otherwise-empty line converts it into a bullet/numbered list line, consuming the trigger text
 * and the trailing space. Call when a single space has just been inserted at [spaceInsertPos].
 * Returns the exact trigger text that was consumed (e.g. "-", ">", "12.") so the caller can
 * remember it for a possible backspace-undo, or null if nothing matched.
 */
fun applyAutoformatTrigger(editable: Editable, spaceInsertPos: Int, textSizePx: Float): String? {
    val ls = lineStart(editable, spaceInsertPos)
    if (spaceInsertPos <= ls) return null
    val beforeSpace = editable.subSequence(ls, spaceInsertPos).toString()
    val kind: Char; val styleIndex: Int
    when {
        beforeSpace == "-" -> { kind = LIST_SPAN_TYPE_BULLET; styleIndex = BulletStyle.DASH.ordinal }
        beforeSpace == ">" -> { kind = LIST_SPAN_TYPE_BULLET; styleIndex = BulletStyle.ARROW.ordinal }
        beforeSpace.matches(Regex("\\d+\\.")) -> { kind = LIST_SPAN_TYPE_NUMBER; styleIndex = NumberStyle.ARABIC_DOT.ordinal }
        else -> return null
    }
    // Existing content on this line already, past where the trigger match starts? Can't happen —
    // beforeSpace IS the entire line content up to the cursor, and lineStart is exactly the
    // start of that line, so this only ever fires when the trigger text is the ONLY thing on
    // the line so far, matching Word's own "must be at the very start of a blank line" rule.
    editable.delete(ls, spaceInsertPos + 1) // consumes the trigger text AND the triggering space
    editable.setSpan(
        when (kind) { LIST_SPAN_TYPE_BULLET -> BulletMarginSpan(styleIndex, textSizePx); else -> NumberMarginSpan(styleIndex, textSizePx) },
        ls, ls, Spannable.SPAN_INCLUSIVE_INCLUSIVE
    )
    renumberLists(editable)
    return beforeSpace
}

/**
 * Enter-key handling for list lines, called when a single '\n' has just been inserted at
 * [newlinePos]. Two cases:
 *  - The line that just ended has TEXT on it → normal continuation: the new line gets the same
 *    style/indent (the '\n' insertion is left as-is).
 *  - The line that just ended is EMPTY (just the glyph, nothing typed) → rather than exiting the
 *    list (which is what a plain "Enter" would conventionally do), this promotes that SAME line
 *    one indent level deeper in place — the just-inserted '\n' is removed again (no new line
 *    actually appears) since the intent here is "indent", not "new paragraph". A second real
 *    Enter once there's content on that deeper line behaves as normal continuation again.
 * Returns: -2 if the line that just ended wasn't a list line at all (nothing done). -1 if it WAS
 * handled but the caller doesn't need to touch the cursor — this is the normal continuation
 * case, where Android's own post-'\n' cursor position is already correct. >=0 is the cursor
 * position to restore, for the promote-in-place case, which removes the '\n' Android just placed
 * the cursor after.
 */
/**
 * Set by handleListEnterKey for the "continuation" case instead of calling setSpan() directly.
 * Confirmed by diagnostic: setSpan() called synchronously from inside afterTextChanged — i.e.
 * reentrantly, while the SpannableStringBuilder is still unwinding the very text-change
 * notification that triggered this callback — was silently NOT taking effect (re-querying for
 * the exact span object immediately afterward, in the same function call, came back empty). The
 * caller must apply this via a posted Runnable, once that notification has fully finished, then
 * clear it back to null.
 */
data class PendingListSpan(val span: ListMarginSpan, val pos: Int)
var pendingListSpanApply: PendingListSpan? = null

fun handleListEnterKey(editable: Editable, newlinePos: Int): Int {
    val prevStart = lineStart(editable, newlinePos)
    val prevText = editable.subSequence(prevStart, newlinePos).toString()
    // List spans are always created zero-length, pinned to their line's start position, and
    // (being SPAN_EXCLUSIVE_EXCLUSIVE) never grow to cover text typed after them — insertion
    // exactly at an exclusive-exclusive span's own boundary is, by definition, excluded from it.
    // A point-query at prevStart is what reliably finds it regardless of how much text is now on
    // the line; requiring the span to span all the way to newlinePos (the earlier version of
    // this check) fails the moment there's any typed content, which is why Enter stopped
    // continuing the list as soon as a line actually had text on it.
    val allAtPrevStart = editable.getSpans(prevStart, prevStart, ListMarginSpan::class.java)
    val existing = allAtPrevStart.firstOrNull() ?: return -2
    if (prevText.isNotBlank()) {
        // Lock the just-finished line's own span down to its exact, final range now that we know
        // it (this line is done — Enter was just pressed to leave it). It started out
        // zero-length with SPAN_INCLUSIVE_INCLUSIVE (needed so it doesn't get silently dropped —
        // see the flag comments in applyListStyle/applyAutoformatTrigger), but INCLUSIVE means it
        // keeps absorbing every character typed at its own end — including, if never pinned down,
        // the '\n' that just ended it and then everything typed on the lines AFTER that. That's
        // exactly why later list lines were gaining extra indent and extra glyphs: earlier
        // siblings' spans had silently grown to cover them too, so multiple spans' margins/glyphs
        // were being applied to the same later paragraph. A non-zero-length, SPAN_EXCLUSIVE_
        // EXCLUSIVE span is confirmed safe (that's exactly what the toolbar-button-on-existing-
        // text case already relies on — only zero-length exclusive-exclusive spans were ever
        // dropped), so re-pin to that now that this line's content is finalized.
        editable.setSpan(existing, prevStart, newlinePos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        // Normal continuation onto the new line — same style/indent, fresh (unchecked, for
        // checklists) instance.
        val newPos = newlinePos + 1
        val newSpan: ListMarginSpan = when (existing) {
            is BulletMarginSpan -> BulletMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel)
            is NumberMarginSpan -> NumberMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel)
            is ChecklistMarginSpan -> ChecklistMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel, false)
        }
        val lenBefore = editable.length
        val willSet = newPos <= lenBefore
        // NOT calling editable.setSpan() here anymore — see PendingListSpan's doc comment above.
        // The caller applies it in a posted Runnable instead.
        if (willSet) pendingListSpanApply = PendingListSpan(newSpan, newPos)
        return -1
    }
    // Empty line: promote in place instead of creating a new paragraph. Remove the '\n' this
    // same keystroke just inserted, bump this line's own indent, and leave the cursor exactly
    // where it was (still on this one line, now one level deeper).
    editable.removeSpan(existing)
    editable.delete(newlinePos, newlinePos + 1)
    val promoted: ListMarginSpan = when (existing) {
        is BulletMarginSpan -> BulletMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel + 1)
        is NumberMarginSpan -> NumberMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel + 1)
        is ChecklistMarginSpan -> ChecklistMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel + 1, false)
    }
    // Deferred the same way as the continuation case above and for the same confirmed reason:
    // setSpan() called synchronously from inside afterTextChanged was being silently dropped.
    // This branch used to call it directly, which is very likely why promoting a line in place
    // (double-Enter on an empty continuation line, to start a "2.1" sub-item) wasn't reliably
    // sticking either — the caller applies this and calls renumberLists() together, in its
    // posted Runnable, whenever pendingListSpanApply is non-null.
    pendingListSpanApply = PendingListSpan(promoted, prevStart)
    return prevStart
}

/**
 * Backspace handling for list lines — meant to be called from an EditText's key listener
 * (KEYCODE_DEL on ACTION_DOWN) BEFORE the default deletion happens, so it can fully replace
 * Android's default "merge into previous line" behavior rather than trying to undo it
 * afterward. Only acts when [cursorPos] sits exactly at the start of an EMPTY list line with no
 * selection — every other backspace (mid-text, non-list-line, has a selection) is left alone by
 * returning -1, letting the EditText's normal handling proceed untouched.
 *
 * [rememberedTrigger], if non-null, is (position, originalTriggerText) from the most recent
 * [applyAutoformatTrigger] call still considered "undoable" — if it matches this exact line and
 * the line is still empty, the original typed text ("-", ">", "12.") is restored instead of just
 * clearing the bullet, mirroring Word's "Backspace right after autoformat brings your text back"
 * behavior. Any other empty-list-line backspace just removes the list formatting.
 *
 * Returns the cursor position to restore if handled, or -1 if this wasn't a case this function
 * handles (caller should let the normal backspace happen).
 */
fun handleListBackspace(editable: Editable, cursorPos: Int, rememberedTrigger: Pair<Int, String>?): Int {
    val ls = lineStart(editable, cursorPos)
    // Used to also bail out (return -1, i.e. "let normal backspace handle it") whenever
    // ls == 0 — the very first line of the document. But normal backspace at position 0 is
    // itself always a no-op (there's nothing before the start of the document to merge with),
    // so that combination meant backspacing an empty FIRST list line did nothing whatsoever:
    // no marker removed, no character deleted, completely inert. The ls == 0 exclusion was
    // never actually needed for correctness here — removeSpan() below doesn't touch any text
    // and doesn't care whether there's a previous line to merge into, so the very first line
    // works the exact same way as any other empty list line.
    if (cursorPos != ls) return -1  // not "cursor at start of a line"
    val le = lineEnd(editable, ls)
    if (le != ls) return -1  // line isn't empty — normal backspace should just delete a character
    val existing = editable.getSpans(ls, ls, ListMarginSpan::class.java).firstOrNull() ?: return -1
    editable.removeSpan(existing)
    return if (rememberedTrigger != null && rememberedTrigger.first == ls) {
        val text = rememberedTrigger.second
        editable.insert(ls, text)
        ls + text.length
    } else {
        ls
    }
}

/**
 * Encodes a TextItem into a fresh SpannableStringBuilder — the exact same rebuild the live
 * editor and DrawingView's static renderer each already do independently, factored out here so
 * the picker below (which needs to edit a committed item's spans when there's no active
 * EditText — item tapped-to-select but not actively being typed into) doesn't need a 4th copy.
 */
private fun rebuildSpannableForItem(item: TextItem): android.text.SpannableStringBuilder {
    val sb = android.text.SpannableStringBuilder(item.text)
    for (sp in item.spans) {
        val s = sp.start.coerceIn(0, sb.length); val e = sp.end.coerceIn(s, sb.length)
        if (s <= e) when (sp.type) {
            'S' -> sb.setSpan(android.text.style.StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            'C' -> sb.setSpan(android.text.style.ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            'U' -> sb.setSpan(android.text.style.UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            'H' -> sb.setSpan(android.text.style.BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            LIST_SPAN_TYPE_BULLET -> sb.setSpan(BulletMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListIndent(sp.value)), s, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            LIST_SPAN_TYPE_NUMBER -> sb.setSpan(NumberMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListIndent(sp.value)), s, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            LIST_SPAN_TYPE_CHECK -> sb.setSpan(ChecklistMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListIndent(sp.value), decodeListChecked(sp.value)), s, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }
    return sb
}

/**
 * Re-derives item.spans from [spanned] and writes them back onto [item] — used after mutating a
 * span's own property in place (e.g. toggling a ChecklistMarginSpan's checked state) rather than
 * through the normal editor-close save path, so that mutation actually persists instead of only
 * existing in the cached StaticLayout's in-memory copy until the next cache invalidation quietly
 * reverts it back to whatever item.spans still says.
 */
internal fun syncTextItemSpansFromSpanned(item: TextItem, spanned: android.text.Spanned) {
    item.spans = spansFromSpannable(spanned)
}

/** Reads any Spanned's spans back into TextItem.spans form (read-only — works for a live
 *  SpannableStringBuilder being edited, or a SpannableString used for static rendering alike). */
private fun spansFromSpannable(sb: android.text.Spanned): MutableList<TextSpanData> {
    val out = mutableListOf<TextSpanData>()
    for (span in sb.getSpans(0, sb.length, Any::class.java)) {
        val s = sb.getSpanStart(span); var e = sb.getSpanEnd(span)
        // See the matching comment in TextEditingExtensions.kt's closeInlineEditor — list spans
        // are deliberately zero-length and must not be dropped here.
        if (s < 0 || e < 0 || s > e) continue
        // Safety net: a list span is only ever supposed to cover its own single line, but being
        // SPAN_INCLUSIVE_INCLUSIVE (needed so a freshly-created zero-length one doesn't get
        // silently dropped) means it keeps absorbing text typed at its end, including through
        // Enter presses, unless something explicitly re-pins it once its line is finished (which
        // handleListEnterKey now does — but this catches anything that slipped through, e.g. the
        // very last line of the document, which never gets an Enter press to trigger that
        // pinning). Clamping here, once, right before this gets saved, keeps a stray over-grown
        // span from ever reaching the saved item — which is what the static (non-editing) render
        // path reconstructs from, so this is also what was causing markers to keep stacking up
        // after closing the text box even once live-editing itself looked fixed.
        if (span is ListMarginSpan) {
            var ownLineEnd = s
            while (ownLineEnd < sb.length && sb[ownLineEnd] != '\n') ownLineEnd++
            e = e.coerceAtMost(ownLineEnd)
        }
        when (span) {
            is android.text.style.StyleSpan -> out.add(TextSpanData(s, e, 'S', span.style))
            is android.text.style.ForegroundColorSpan -> out.add(TextSpanData(s, e, 'C', span.foregroundColor))
            is android.text.style.UnderlineSpan -> out.add(TextSpanData(s, e, 'U', 0))
            is android.text.style.BackgroundColorSpan -> out.add(TextSpanData(s, e, 'H', span.backgroundColor))
            is BulletMarginSpan -> out.add(TextSpanData(s, e, LIST_SPAN_TYPE_BULLET, encodeListValue(span.styleIndex, false, span.indentLevel)))
            is NumberMarginSpan -> out.add(TextSpanData(s, e, LIST_SPAN_TYPE_NUMBER, encodeListValue(span.styleIndex, false, span.indentLevel)))
            is ChecklistMarginSpan -> out.add(TextSpanData(s, e, LIST_SPAN_TYPE_CHECK, encodeListValue(span.styleIndex, span.checked, span.indentLevel)))
        }
    }
    return out
}

/**
 * The bottom-bar list-style picker: shows every style in [kind]'s category (10 bullets / 10
 * numbering / 5 checklist) with a live glyph preview, applies the tapped one to whatever text is
 * currently addressable — the active selection if there is one, otherwise just the current
 * line, in the live editor; or the whole item's text if a committed item is selected but not
 * actively being typed into (mirrors exactly how the existing B/I/U buttons already split that
 * same two-state handling a few lines below where these buttons are wired in).
 */
internal fun MainActivity.showListStylePicker(kind: Char) {
    // Captured NOW, before the dialog opens — a dialog taking window focus can shift things
    // (soft keyboard visibility, etc.), so re-reading activeEditText/selection at the moment a
    // style is actually tapped risked acting on stale/cleared state. Whatever was focused when
    // the toolbar button itself was pressed is what the picked style should apply to.
    val targetEt = activeEditText
    val targetFrom: Int; val targetTo: Int
    if (targetEt != null) {
        val s = targetEt.selectionStart; val e = targetEt.selectionEnd
        targetFrom = if (s == e) s else minOf(s, e); targetTo = if (s == e) s else maxOf(s, e)
    } else { targetFrom = 0; targetTo = 0 }
    val targetItem = textSelectionItem

    val bg = currentThemeBackgroundColor(); val accent = currentThemeToolbarColor()
    val isDark = Color.red(bg) * 0.299 + Color.green(bg) * 0.587 + Color.blue(bg) * 0.114 < 140
    val textColor = if (isDark) Color.parseColor("#E8E8E8") else Color.parseColor("#2A2A2A")
    val title = when (kind) { LIST_SPAN_TYPE_BULLET -> "Bullet Style"; LIST_SPAN_TYPE_NUMBER -> "Numbering Style"; else -> "Checklist Style" }
    val count = when (kind) { LIST_SPAN_TYPE_BULLET -> BulletStyle.values().size; LIST_SPAN_TYPE_NUMBER -> NumberStyle.values().size; else -> ChecklistStyle.values().size }

    val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
    for (i in 0 until count) {
        val preview = when (kind) {
            LIST_SPAN_TYPE_BULLET -> "${BulletStyle.safe(i).glyph}  ${BulletStyle.safe(i).glyph}  ${BulletStyle.safe(i).glyph}"
            LIST_SPAN_TYPE_NUMBER -> "${NumberStyle.safe(i).format(1)}  ${NumberStyle.safe(i).format(2)}  ${NumberStyle.safe(i).format(3)}"
            else -> "${ChecklistStyle.safe(i).unchecked}  ${ChecklistStyle.safe(i).checked}"
        }
        val label = when (kind) { LIST_SPAN_TYPE_BULLET -> BulletStyle.safe(i).label; LIST_SPAN_TYPE_NUMBER -> NumberStyle.safe(i).label; else -> ChecklistStyle.safe(i).label }
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(accent), null, null)
            addView(TextView(this@showListStylePicker).apply {
                text = preview; textSize = 18f; setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@showListStylePicker).apply {
                text = label; textSize = 14f
                setTextColor(Color.argb(180, Color.red(textColor), Color.green(textColor), Color.blue(textColor)))
            })
        })
    }
    val scroll = ScrollView(this).apply { addView(container) }
    val dlg = AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel", null).create()
    dlg.show()
    dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
    for (i in 0 until container.childCount) (container.getChildAt(i) as LinearLayout).setOnClickListener {
        rememberListStyleIndex(kind, i)
        applyPickedListStyle(kind, i, targetEt, targetFrom, targetTo, targetItem); dlg.dismiss()
    }
}

/** Persists which style index was picked last for each kind (bullet/number/checklist), so a
 * plain tap on the toolbar button (as opposed to opening the picker) can apply "whatever you
 * used last" instead of always defaulting back to style 0. */
private const val LIST_STYLE_PREFS = "enginotes_list_prefs"
internal fun MainActivity.lastListStyleIndex(kind: Char): Int =
    getSharedPreferences(LIST_STYLE_PREFS, android.content.Context.MODE_PRIVATE).getInt("last_style_$kind", 0)
internal fun MainActivity.rememberListStyleIndex(kind: Char, index: Int) {
    getSharedPreferences(LIST_STYLE_PREFS, android.content.Context.MODE_PRIVATE).edit().putInt("last_style_$kind", index).apply()
}

/** Whether the line/selection a toolbar tap would target is already marked as [kind] — used to
 * decide whether a tap should apply the remembered style directly or open the picker (a second
 * tap on an already-active kind is what opens it, matching how B/I/U-style toggle buttons work
 * elsewhere in this toolbar). */
internal fun MainActivity.currentLineIsKind(et: EditText?, item: TextItem?, from: Int, kind: Char): Boolean {
    val cls = spanClassFor(kind)
    if (et != null) {
        val ls = lineStart(et.text, from)
        return et.text.getSpans(ls, ls, ListMarginSpan::class.java).any { it.javaClass == cls }
    }
    if (item != null) return item.spans.any { it.type == kind }
    return false
}

internal fun MainActivity.applyPickedListStyle(kind: Char, styleIndex: Int, et: EditText?, from: Int, to: Int, item: TextItem?) {
    if (et != null) {
        applyListStyle(et.text, from, to, kind, styleIndex, et.textSize)
        renumberLists(et.text)
        // Posted rather than called synchronously — getLeadingMargin() affects line width, a
        // MEASURE concern, and a synchronous requestLayout() here was not reliably sticking
        // (empty lines specifically kept failing to show their glyph even with this call in
        // place). Deferring until after the current call stack fully unwinds avoids whatever in
        // Android's own internal processing was overriding it.
        et.post { et.requestLayout(); et.invalidate() }
    } else if (item != null) {
        val sb = rebuildSpannableForItem(item)
        applyListStyle(sb, 0, sb.length, kind, styleIndex, item.size)
        renumberLists(sb)
        item.spans = spansFromSpannable(sb)
        item.text = sb.toString()
        item.cachedLayout = null  // same reasoning as et.requestLayout() above — force a rebuild rather than reusing a layout computed before this item had a margin span
        drawingView.invalidate()
    }
}

// ---------------------------------------------------------------------------------------------
// Encoding list-span extra state (style index, checked flag, indent level) into
// TextSpanData.value, which is a single Int — bit-packed since there are now three independent
// pieces of state to carry: bits 0-3 = styleIndex (0-15, covers all 10 styles per category with
// room to grow), bit 4 = checked, bits 5-8 = indentLevel (0-15, far more than this UI exposes).
// Ordinal (the actual "1, 2, 3..." for numbered lists) is deliberately NOT encoded here — it's
// always recomputed by renumberLists() after spans are rebuilt, so edits that add/remove lines
// can never leave a stale/wrong number behind.
// ---------------------------------------------------------------------------------------------
fun encodeListValue(styleIndex: Int, checked: Boolean, indentLevel: Int = 0): Int =
    (styleIndex and 0xF) or ((if (checked) 1 else 0) shl 4) or ((indentLevel and 0xF) shl 5)
fun decodeListStyleIndex(value: Int): Int = value and 0xF
fun decodeListChecked(value: Int): Boolean = (value shr 4) and 1 == 1
fun decodeListIndent(value: Int): Int = (value shr 5) and 0xF
