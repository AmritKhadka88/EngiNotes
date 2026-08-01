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

enum class NumberStyle(val label: String, val format: (Int) -> String) {
    ARABIC_DOT("1.", { n -> "$n." }),
    ARABIC_PAREN("1)", { n -> "$n)" }),
    ARABIC_BRACKET("[1]", { n -> "[$n]" }),
    ARABIC_BOTH_PAREN("(1)", { n -> "($n)" }),
    LOWER_ALPHA("a.", { n -> "${numberStyleToAlpha(n, false)}." }),
    UPPER_ALPHA("A.", { n -> "${numberStyleToAlpha(n, true)}." }),
    LOWER_ROMAN("i.", { n -> "${numberStyleToRoman(n).lowercase()}." }),
    UPPER_ROMAN("I.", { n -> "${numberStyleToRoman(n)}." }),
    ZERO_PADDED("01.", { n -> "${n.toString().padStart(2, '0')}." }),
    COLON("1:", { n -> "$n:" });

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
    override fun glyphFor(): String = BulletStyle.safe(styleIndex).glyph
}

class NumberMarginSpan(styleIndex: Int, textSizePx: Float, indentLevel: Int = 0, var ordinal: Int = 1) : ListMarginSpan(styleIndex, textSizePx, indentLevel) {
    override fun glyphFor(): String = NumberStyle.safe(styleIndex).format(ordinal)
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
        for (sp in spans) {
            val pos = ed.getSpanStart(sp).coerceIn(0, ed.length)
            val line = layout.getLineForOffset(pos)
            // Only the paragraph's own start line gets a glyph — matches the
            // getLeadingMarginLineCount()=1 contract the LeadingMarginSpan2 path uses elsewhere,
            // so wrapped continuation lines of a long list item don't each get their own glyph.
            var trueLineStart = pos
            while (trueLineStart > 0 && ed[trueLineStart - 1] != '\n') trueLineStart--
            if (layout.getLineStart(line) != trueLineStart) continue

            val glyph = sp.glyphFor()
            val gp = Paint(paint)
            gp.color = currentTextColor
            val glyphWidth = gp.measureText(glyph)
            val lineLeft = layout.getLineLeft(line) + totalPaddingLeft
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
            editable.setSpan(span, ls, end.coerceAtMost(editable.length).coerceAtLeast(ls), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
    var runStyle = -1; var runIndent = -1; var counter = 0; var lastLineEnd = -1
    for (sp in spans) {
        val start = editable.getSpanStart(sp)
        // A run breaks if the style OR indent level changes, or there's a gap (a non-numbered
        // line, or a blank line) between this span's line and the previous numbered line — each
        // indent level counts independently, the same way a sub-list restarts at 1 in Word.
        val contiguous = lastLineEnd >= 0 && start <= lastLineEnd + 1
        if (sp.styleIndex != runStyle || sp.indentLevel != runIndent || !contiguous) {
            runStyle = sp.styleIndex; runIndent = sp.indentLevel; counter = 1
        } else counter++
        sp.ordinal = counter
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
        ls, ls, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
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
    val existing = editable.getSpans(prevStart, prevStart, ListMarginSpan::class.java).firstOrNull() ?: return -2
    if (prevText.isNotBlank()) {
        // Normal continuation onto the new line — same style/indent, fresh (unchecked, for
        // checklists) instance.
        val newPos = newlinePos + 1
        val newSpan: ListMarginSpan = when (existing) {
            is BulletMarginSpan -> BulletMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel)
            is NumberMarginSpan -> NumberMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel)
            is ChecklistMarginSpan -> ChecklistMarginSpan(existing.styleIndex, existing.textSizePx, existing.indentLevel, false)
        }
        if (newPos <= editable.length) editable.setSpan(newSpan, newPos, newPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        renumberLists(editable)
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
    editable.setSpan(promoted, prevStart, prevStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    renumberLists(editable)
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
    if (cursorPos != ls || ls == 0) return -1  // not "cursor at start of a non-first line"
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
            LIST_SPAN_TYPE_BULLET -> sb.setSpan(BulletMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListIndent(sp.value)), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            LIST_SPAN_TYPE_NUMBER -> sb.setSpan(NumberMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListIndent(sp.value)), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            LIST_SPAN_TYPE_CHECK -> sb.setSpan(ChecklistMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListIndent(sp.value), decodeListChecked(sp.value)), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        val s = sb.getSpanStart(span); val e = sb.getSpanEnd(span)
        // See the matching comment in TextEditingExtensions.kt's closeInlineEditor — list spans
        // are deliberately zero-length and must not be dropped here.
        if (s < 0 || e < 0 || s > e) continue
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
        applyPickedListStyle(kind, i, targetEt, targetFrom, targetTo, targetItem); dlg.dismiss()
    }
}

private fun MainActivity.applyPickedListStyle(kind: Char, styleIndex: Int, et: EditText?, from: Int, to: Int, item: TextItem?) {
    if (et != null) {
        applyListStyle(et.text, from, to, kind, styleIndex, et.textSize)
        renumberLists(et.text)
        // getLeadingMargin() affects each line's available width, which is a MEASURE concern —
        // invalidate() alone only requests a redraw using whatever layout is already cached, so
        // a brand new bullet/number/checkbox could silently not appear until some LATER edit
        // forced a real relayout for an unrelated reason. requestLayout() is what actually gets
        // EditText to rebuild its DynamicLayout and pick up the new margin immediately.
        et.requestLayout()
        et.invalidate()
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
