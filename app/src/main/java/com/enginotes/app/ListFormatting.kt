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
import android.text.Layout
import android.text.Spannable
import android.text.style.LeadingMarginSpan
import android.view.Gravity
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

sealed class ListMarginSpan(var styleIndex: Int, val textSizePx: Float) : LeadingMarginSpan2Compat {
    // Margin scales with the text's own size so a 12pt list and a 48pt heading-sized list both
    // get proportionally sensible indent/glyph-size instead of one fixed dp value looking right
    // at only one font size.
    val marginPx: Int = (textSizePx * 2.4f).toInt().coerceAtLeast(32)
    // Bounding box of the glyph as last drawn, in the same coordinate space as MotionEvent
    // coordinates within the EditText — used only for checklist tap-to-toggle hit-testing.
    // Deliberately not persisted; it's a pure runtime draw-time cache, rebuilt every layout pass.
    var lastDrawnBounds: RectF? = null

    abstract fun glyphFor(): String

    override fun getLeadingMargin(first: Boolean): Int = marginPx

    override fun drawLeadingMargin(
        canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?
    ) {
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

class BulletMarginSpan(styleIndex: Int, textSizePx: Float) : ListMarginSpan(styleIndex, textSizePx) {
    override fun glyphFor(): String = BulletStyle.safe(styleIndex).glyph
}

class NumberMarginSpan(styleIndex: Int, textSizePx: Float, var ordinal: Int = 1) : ListMarginSpan(styleIndex, textSizePx) {
    override fun glyphFor(): String = NumberStyle.safe(styleIndex).format(ordinal)
}

class ChecklistMarginSpan(styleIndex: Int, textSizePx: Float, var checked: Boolean = false) : ListMarginSpan(styleIndex, textSizePx) {
    override fun glyphFor(): String { val st = ChecklistStyle.safe(styleIndex); return if (checked) st.checked else st.unchecked }
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
                LIST_SPAN_TYPE_CHECK -> ChecklistMarginSpan(styleIndex, textSizePx, false)
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
    var runStyle = -1; var counter = 0; var lastLineEnd = -1
    for (sp in spans) {
        val start = editable.getSpanStart(sp)
        // A run breaks if the style changes OR there's a gap (a non-numbered line, or a blank
        // line) between this span's line and the previous numbered line.
        val contiguous = lastLineEnd >= 0 && start <= lastLineEnd + 1
        if (sp.styleIndex != runStyle || !contiguous) { runStyle = sp.styleIndex; counter = 1 } else counter++
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
 * Call when a single '\n' has just been inserted at [newlinePos] (i.e. editable[newlinePos] is
 * that '\n'). If the line just ended had a list span: an empty list line (just the glyph, no
 * text — the standard "press Enter on a blank bullet to exit the list" gesture) removes that
 * span instead of continuing it; otherwise the same list style continues onto the new line, the
 * way every list-editing UI behaves. Always renumbers afterward. No-op if the ended line wasn't
 * a list line at all.
 */
fun handleListContinuation(editable: Spannable, newlinePos: Int) {
    val prevStart = lineStart(editable, newlinePos)
    val prevText = editable.subSequence(prevStart, newlinePos).toString()
    val existing = editable.getSpans(prevStart, newlinePos, ListMarginSpan::class.java)
        .firstOrNull { editable.getSpanStart(it) <= prevStart && editable.getSpanEnd(it) >= newlinePos } ?: return
    if (prevText.isBlank()) {
        editable.removeSpan(existing)
    } else {
        val newPos = newlinePos + 1
        val newSpan: ListMarginSpan = when (existing) {
            is BulletMarginSpan -> BulletMarginSpan(existing.styleIndex, existing.textSizePx)
            is NumberMarginSpan -> NumberMarginSpan(existing.styleIndex, existing.textSizePx)
            is ChecklistMarginSpan -> ChecklistMarginSpan(existing.styleIndex, existing.textSizePx, false) // new item always starts unchecked
        }
        if (newPos <= editable.length) editable.setSpan(newSpan, newPos, newPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    renumberLists(editable)
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
        if (s < e) when (sp.type) {
            'S' -> sb.setSpan(android.text.style.StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            'C' -> sb.setSpan(android.text.style.ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            'U' -> sb.setSpan(android.text.style.UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            'H' -> sb.setSpan(android.text.style.BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            LIST_SPAN_TYPE_BULLET -> sb.setSpan(BulletMarginSpan(decodeListStyleIndex(sp.value), item.size), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            LIST_SPAN_TYPE_NUMBER -> sb.setSpan(NumberMarginSpan(decodeListStyleIndex(sp.value), item.size), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            LIST_SPAN_TYPE_CHECK -> sb.setSpan(ChecklistMarginSpan(decodeListStyleIndex(sp.value), item.size, decodeListChecked(sp.value)), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return sb
}

/** Reverse of the above: reads a SpannableStringBuilder's spans back into TextItem.spans form. */
private fun spansFromSpannable(sb: android.text.SpannableStringBuilder): MutableList<TextSpanData> {
    val out = mutableListOf<TextSpanData>()
    for (span in sb.getSpans(0, sb.length, Any::class.java)) {
        val s = sb.getSpanStart(span); val e = sb.getSpanEnd(span); if (s < 0 || e < 0 || s >= e) continue
        when (span) {
            is android.text.style.StyleSpan -> out.add(TextSpanData(s, e, 'S', span.style))
            is android.text.style.ForegroundColorSpan -> out.add(TextSpanData(s, e, 'C', span.foregroundColor))
            is android.text.style.UnderlineSpan -> out.add(TextSpanData(s, e, 'U', 0))
            is android.text.style.BackgroundColorSpan -> out.add(TextSpanData(s, e, 'H', span.backgroundColor))
            is BulletMarginSpan -> out.add(TextSpanData(s, e, LIST_SPAN_TYPE_BULLET, encodeListValue(span.styleIndex, false)))
            is NumberMarginSpan -> out.add(TextSpanData(s, e, LIST_SPAN_TYPE_NUMBER, encodeListValue(span.styleIndex, false)))
            is ChecklistMarginSpan -> out.add(TextSpanData(s, e, LIST_SPAN_TYPE_CHECK, encodeListValue(span.styleIndex, span.checked)))
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
        applyPickedListStyle(kind, i); dlg.dismiss()
    }
}

private fun MainActivity.applyPickedListStyle(kind: Char, styleIndex: Int) {
    val et = activeEditText
    if (et != null) {
        val s = et.selectionStart; val e = et.selectionEnd
        val from = if (s == e) s else minOf(s, e); val to = if (s == e) s else maxOf(s, e)
        applyListStyle(et.text, from, to, kind, styleIndex, et.textSize)
        renumberLists(et.text)
        et.invalidate()
    } else {
        textSelectionItem?.let { item ->
            val sb = rebuildSpannableForItem(item)
            applyListStyle(sb, 0, sb.length, kind, styleIndex, item.size)
            renumberLists(sb)
            item.spans = spansFromSpannable(sb)
            item.text = sb.toString()
            drawingView.invalidate()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Encoding list-span extra state (checked flag, style index) into TextSpanData.value, which is
// a single Int. Ordinal is NOT encoded — it's always recomputed by renumberLists() after spans
// are rebuilt, so edits that add/remove lines can never leave a stale/wrong number behind.
// ---------------------------------------------------------------------------------------------
fun encodeListValue(styleIndex: Int, checked: Boolean): Int = styleIndex * 2 + (if (checked) 1 else 0)
fun decodeListStyleIndex(value: Int): Int = value / 2
fun decodeListChecked(value: Int): Boolean = value % 2 == 1
