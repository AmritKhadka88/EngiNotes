package com.enginotes.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.UnderlineSpan

data class CellSpan(val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)

class TableCell(
    var text: String = "",
    var textColor: Int = Color.BLACK,
    var bgColor: Int = Color.WHITE,
    var textSize: Float = 14f,
    var borderColor: Int = Color.BLACK,
    var borderWidth: Float = 2f,
    var alignment: Int = 0,
    var mergedInto: Pair<Int, Int>? = null,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var fontFamily: String? = null
) {
    // Render cache — rebuilding a StaticLayout (and especially calling Typeface.create()) on
    // every single draw() frame, for every cell, was expensive enough to freeze the UI thread on
    // any table with a handful of cells. Only rebuilds when something that actually affects the
    // rendered layout has changed.
    @Volatile var cachedLayout: StaticLayout? = null
    var cachedLayoutKey: String = ""
}

class TableItem(var x: Float, var y: Float, var rotation: Float = 0f) {
    var rows: Int = 3
    var cols: Int = 3
    // Toggled from Table Properties. Draws A/B/C... above each column and 1/2/3... to the left
    // of each row, aligned to this table's own colWidths/rowHeights (not the whole canvas) —
    // matches what a formula/cell-reference system will need later (e.g. "B3").
    var showHeaders: Boolean = false
    // Screen-space pt size for the A/B/C + 1/2/3 header labels. Adjustable via +/- in Table
    // Properties. Was hardcoded at 12f before, which read as basically invisible on a phone.
    var headerTextSize: Float = 22f

    // Column auto-grows up to this width as the user types; beyond it, text wraps to new lines
    // instead of pushing the column wider. Merged cells are exempt (they can grow freely).
    val MAX_AUTO_COL_WIDTH = 260f
    val MIN_COL_WIDTH = 60f
    val MIN_ROW_HEIGHT = 44f

    private val _cells: MutableList<MutableList<TableCell>> = MutableList(rows) { MutableList(cols) { TableCell() } }

    val rowHeights: MutableList<Float> = MutableList(rows) { 60f }
    val colWidths: MutableList<Float> = MutableList(cols) { 100f }
    val mergeSpans: MutableMap<Pair<Int, Int>, CellSpan> = mutableMapOf()

    // Tracks whether a column's width has been manually adjusted by the user (drag-resize).
    // Once true, auto-grow-on-type no longer changes that column - the user is in full control.
    val colManuallyResized: MutableList<Boolean> = MutableList(cols) { false }

    val cells: Array<Array<TableCell>>
        get() = Array(rows) { r -> Array(cols) { c -> getCellSafe(r, c) } }

    private fun getCellSafe(r: Int, c: Int): TableCell {
        while (_cells.size <= r) _cells.add(MutableList(cols) { TableCell() })
        while (_cells[r].size <= c) _cells[r].add(TableCell())
        return _cells[r][c]
    }

    fun getCellPublic(r: Int, c: Int): TableCell = getCellSafe(r, c)

    private fun ensureColFlagSize() { while (colManuallyResized.size < cols) colManuallyResized.add(false) }

    fun insertRow(at: Int) {
        val newRow = MutableList(cols) { TableCell() }
        if (at >= _cells.size) _cells.add(newRow) else _cells.add(at, newRow)
    }

    fun insertCol(at: Int) {
        for (row in _cells) {
            if (at >= row.size) row.add(TableCell()) else row.add(at, TableCell())
        }
        if (at >= colManuallyResized.size) colManuallyResized.add(false) else colManuallyResized.add(at, false)
    }

    fun deleteRow(at: Int) {
        if (at < _cells.size) _cells.removeAt(at)
    }

    fun deleteCol(at: Int) {
        for (row in _cells) { if (at < row.size) row.removeAt(at) }
        if (at < colManuallyResized.size) colManuallyResized.removeAt(at)
    }

    fun totalWidth(): Float = colWidths.sum()
    fun totalHeight(): Float = rowHeights.sum()

    fun cellX(col: Int): Float = x + colWidths.take(col).sum()
    fun cellY(row: Int): Float = y + rowHeights.take(row).sum()

    fun cellRect(row: Int, col: Int): RectF {
        val span = mergeSpans[Pair(row, col)]
        val w = if (span != null) colWidths.subList(col, (col + span.colSpan).coerceAtMost(colWidths.size)).sum() else colWidths.getOrElse(col) { 100f }
        val h = if (span != null) rowHeights.subList(row, (row + span.rowSpan).coerceAtMost(rowHeights.size)).sum() else rowHeights.getOrElse(row) { 60f }
        return RectF(cellX(col), cellY(row), cellX(col) + w, cellY(row) + h)
    }

    // World-space touch point -> the table's own unrotated local space, using the same center
    // pivot as draw(). Hit-testing below operates entirely in local space; without this, tapping
    // a rotated table only ever hit-tested against where it WOULD be if unrotated — the exact
    // "have to tap its previous position" bug.
    private fun toLocal(wx: Float, wy: Float): FloatArray {
        if (rotation == 0f) return floatArrayOf(wx, wy)
        val cx = x + totalWidth() / 2f; val cy = y + totalHeight() / 2f
        val rad = Math.toRadians(-rotation.toDouble())
        val cosT = Math.cos(rad).toFloat(); val sinT = Math.sin(rad).toFloat()
        val dx = wx - cx; val dy = wy - cy
        return floatArrayOf(cx + dx * cosT - dy * sinT, cy + dx * sinT + dy * cosT)
    }

    fun hitTestCell(wxIn: Float, wyIn: Float): Pair<Int, Int>? {
        val local = toLocal(wxIn, wyIn); val wx = local[0]; val wy = local[1]
        if (wx < x || wx > x + totalWidth() || wy < y || wy > y + totalHeight()) return null
        var row = rows - 1; var cy = y
        for (r in 0 until rows) { if (wy < cy + rowHeights.getOrElse(r) { 60f }) { row = r; break }; cy += rowHeights.getOrElse(r) { 60f } }
        var col = cols - 1; var cx = x
        for (c in 0 until cols) { if (wx < cx + colWidths.getOrElse(c) { 100f }) { col = c; break }; cx += colWidths.getOrElse(c) { 100f } }
        if (row < 0 || col < 0) return null
        val cell = getCellSafe(row, col)
        return cell.mergedInto ?: Pair(row, col)
    }

    fun hitTestRowBorder(wxIn: Float, wyIn: Float, tolerance: Float): Int {
        val wy = toLocal(wxIn, wyIn)[1]
        var cy = y
        for (r in 0 until rows - 1) { cy += rowHeights.getOrElse(r) { 60f }; if (kotlin.math.abs(wy - cy) <= tolerance) return r }
        return -1
    }

    fun hitTestColBorder(wxIn: Float, wyIn: Float, tolerance: Float): Int {
        val wx = toLocal(wxIn, wyIn)[0]
        var cx = x
        for (c in 0 until cols - 1) { cx += colWidths.getOrElse(c) { 100f }; if (kotlin.math.abs(wx - cx) <= tolerance) return c }
        return -1
    }

    // Marks a column as manually resized (called when the user drags a column border directly)
    fun markColManuallyResized(col: Int) { ensureColFlagSize(); if (col in colManuallyResized.indices) colManuallyResized[col] = true }

    // Builds a StaticLayout for a cell's text wrapped to the given width - used both for drawing
    // and for measuring how tall the cell needs to be once text wraps.
    private fun buildCellLayout(cell: TableCell, wrapWidth: Int): StaticLayout {
        val w = wrapWidth.coerceAtLeast(20)
        val key = "${cell.text}|${cell.textColor}|${cell.textSize}|${cell.bold}|${cell.italic}|${cell.underline}|${cell.fontFamily}|$w"
        cell.cachedLayout?.let { if (cell.cachedLayoutKey == key) return it }
        val tp = TextPaint(); tp.color = cell.textColor; tp.textSize = cell.textSize; tp.isAntiAlias = true
        val style = when { cell.bold && cell.italic -> Typeface.BOLD_ITALIC; cell.bold -> Typeface.BOLD; cell.italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
        tp.typeface = resolveTypeface(cell.fontFamily, style)
        val text = if (cell.text.isEmpty()) " " else cell.text
        val cs: CharSequence = if (cell.underline) SpannableString(text).apply { setSpan(UnderlineSpan(), 0, text.length, 0) } else text
        val layout = StaticLayout.Builder.obtain(cs, 0, cs.length, tp, w).setIncludePad(false).build()
        cell.cachedLayout = layout; cell.cachedLayoutKey = key
        return layout
    }

    // Recomputes column width (auto-grow up to MAX_AUTO_COL_WIDTH, unless manually resized or
    // the cell belongs to a merged span) and row height (always grows to fit wrapped text) for
    // a single cell after its text changes. Call this from the editor's text-change listener.
    fun recalcCellSize(row: Int, col: Int) {
        ensureColFlagSize()
        val cell = getCellSafe(row, col)
        if (cell.mergedInto != null) return
        val span = mergeSpans[Pair(row, col)]
        val isMergedMaster = span != null && (span.rowSpan > 1 || span.colSpan > 1)

        val tp = TextPaint(); tp.textSize = cell.textSize; tp.isAntiAlias = true
        val style = when { cell.bold && cell.italic -> Typeface.BOLD_ITALIC; cell.bold -> Typeface.BOLD; cell.italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
        tp.typeface = resolveTypeface(cell.fontFamily, style)
        val longestLineWidth = (cell.text.split("\n").maxOfOrNull { tp.measureText(it) } ?: 0f) + 16f

        val curColWidth = colWidths.getOrElse(col) { 100f }
        val manuallyResized = col < colManuallyResized.size && colManuallyResized[col]

        val effectiveMax = if (isMergedMaster) Float.MAX_VALUE else MAX_AUTO_COL_WIDTH
        if (!manuallyResized && !isMergedMaster) {
            val newWidth = longestLineWidth.coerceIn(MIN_COL_WIDTH, effectiveMax)
            if (newWidth > curColWidth) colWidths[col] = newWidth
        }

        // Row height grows to fit wrapped content at the (possibly just-updated) column width
        val wrapWidth = (colWidths.getOrElse(col) { 100f } - 16f).toInt().coerceAtLeast(20)
        val layout = buildCellLayout(cell, wrapWidth)
        val neededHeight = layout.height + 16f
        val curRowHeight = rowHeights.getOrElse(row) { 60f }
        if (neededHeight > curRowHeight) rowHeights[row] = neededHeight.coerceAtLeast(MIN_ROW_HEIGHT)
    }

    fun draw(canvas: Canvas, scaleFactor: Float) {
        canvas.save()
        val pivotX = x + totalWidth() / 2f; val pivotY = y + totalHeight() / 2f
        canvas.rotate(rotation, pivotX, pivotY)
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = getCellSafe(r, c); if (cell.mergedInto != null) continue
            val rect = cellRect(r, c)
            val bgP = Paint(); bgP.color = cell.bgColor; bgP.style = Paint.Style.FILL
            canvas.drawRect(rect, bgP)
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = getCellSafe(r, c); if (cell.mergedInto != null || cell.text.isBlank()) continue
            val rect = cellRect(r, c)
            val wrapWidth = (rect.width() - 8f).toInt().coerceAtLeast(20)
            val layout = buildCellLayout(cell, wrapWidth)
            val ty = rect.top + 4f + (rect.height() - 8f - layout.height).coerceAtLeast(0f) / 2f
            val tx = when (cell.alignment) { 1 -> rect.left + (rect.width() - layout.width) / 2f; 2 -> rect.right - layout.width - 4f; else -> rect.left + 4f }
            canvas.save(); canvas.translate(tx, ty); layout.draw(canvas); canvas.restore()
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = getCellSafe(r, c); if (cell.mergedInto != null) continue
            val rect = cellRect(r, c)
            val bp = Paint(); bp.color = cell.borderColor; bp.strokeWidth = cell.borderWidth / scaleFactor; bp.style = Paint.Style.STROKE
            canvas.drawRect(rect, bp)
        }
        canvas.restore()
    }

    fun drawHeaders(canvas: Canvas, scaleFactor: Float) {
        canvas.save()
        val pivotX = x + totalWidth() / 2f; val pivotY = y + totalHeight() / 2f
        canvas.rotate(rotation, pivotX, pivotY)

        // Constant SCREEN size regardless of zoom, matching how selection handles do it elsewhere.
        // Was a fixed 20f/12f before — genuinely too small to read on a phone screen next to normal
        // cell text. barSize now derives from headerTextSize so the strip always comfortably fits
        // the label instead of clipping it as the text size is turned up.
        val textSize = headerTextSize / scaleFactor
        val barSize = textSize * 1.8f
        val strokeW = 1f / scaleFactor

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F0"); style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B0B0A8"); strokeWidth = strokeW; style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3A36"); this.textSize = textSize; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        // Column letters above the table, one cell per column, matching that column's width
        canvas.drawRect(x, y - barSize, x + totalWidth(), y, bg)
        var cx = x
        for (c in 0 until cols) {
            val w = colWidths.getOrElse(c) { 100f }
            canvas.drawText(columnLabel(c), cx + w / 2f, y - barSize * 0.28f, text)
            cx += w
            canvas.drawLine(cx, y - barSize, cx, y, line)
        }
        canvas.drawRect(x, y - barSize, x + totalWidth(), y, line)

        // Row numbers to the left of the table, one cell per row, matching that row's height
        canvas.drawRect(x - barSize, y, x, y + totalHeight(), bg)
        var cy = y
        for (r in 0 until rows) {
            val h = rowHeights.getOrElse(r) { 60f }
            canvas.drawText((r + 1).toString(), x - barSize / 2f, cy + h / 2f + textSize * 0.35f, text)
            cy += h
            canvas.drawLine(x - barSize, cy, x, cy, line)
        }
        canvas.drawRect(x - barSize, y, x, y + totalHeight(), line)

        // Corner square where the two header strips meet
        canvas.drawRect(x - barSize, y - barSize, x, y, bg)
        canvas.drawRect(x - barSize, y - barSize, x, y, line)

        canvas.restore()
    }

    fun mergeCells(r1: Int, c1: Int, r2: Int, c2: Int) {
        val minR = minOf(r1, r2); val maxR = maxOf(r1, r2)
        val minC = minOf(c1, c2); val maxC = maxOf(c1, c2)
        val master = Pair(minR, minC)
        mergeSpans[master] = CellSpan(minR, minC, maxR - minR + 1, maxC - minC + 1)
        for (r in minR..maxR) for (c in minC..maxC) {
            if (r == minR && c == minC) continue
            getCellSafe(r, c).mergedInto = master
        }
    }

    fun unmergeCells(r: Int, c: Int) {
        val key = Pair(r, c); val span = mergeSpans[key] ?: return
        mergeSpans.remove(key)
        for (dr in 0 until span.rowSpan) for (dc in 0 until span.colSpan) {
            if (dr == 0 && dc == 0) continue
            getCellSafe(r + dr, c + dc).mergedInto = null
        }
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("TABLE\u0001$x\u0001$y\u0001$rotation\u0001$rows\u0001$cols\u0001$showHeaders\u0001$headerTextSize\n")
        sb.append("ROWHEIGHTS\u0001${rowHeights.joinToString(",")}\n")
        sb.append("COLWIDTHS\u0001${colWidths.joinToString(",")}\n")
        sb.append("COLRESIZED\u0001${colManuallyResized.joinToString(",")}\n")
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = getCellSafe(r, c); val mi = cell.mergedInto
            sb.append("CELL\u0001$r\u0001$c\u0001${cell.textColor}\u0001${cell.bgColor}\u0001${cell.textSize}\u0001${cell.borderColor}\u0001${cell.borderWidth}\u0001${cell.alignment}\u0001${mi?.first ?: -1}\u0001${mi?.second ?: -1}\u0001${cell.bold}\u0001${cell.italic}\u0001${cell.underline}\u0001${cell.fontFamily ?: ""}\u0001${cell.text.replace("\n", "\u0002")}\n")
        }
        for ((key, span) in mergeSpans) sb.append("MERGE\u0001${key.first}\u0001${key.second}\u0001${span.rowSpan}\u0001${span.colSpan}\n")
        sb.append("TABLEEND\n")
        return sb.toString()
    }

    companion object {
        // Converts a 0-based column index into Excel-style letters: 0->A, 25->Z, 26->AA, 27->AB...
        // Kept here (not private) so a future formula parser can reuse it for cell references.
        fun columnLabel(index: Int): String {
            var n = index
            val sb = StringBuilder()
            while (true) {
                sb.insert(0, ('A' + (n % 26)))
                n = n / 26 - 1
                if (n < 0) break
            }
            return sb.toString()
        }

        // Shared across every cell/table — Typeface.create() is a genuinely expensive call
        // (native font lookup); a table with many cells in the same font/style shouldn't pay
        // that cost once per cell.
        private val typefaceCache = HashMap<String, Typeface>()
        private fun resolveTypeface(fontFamily: String?, style: Int): Typeface {
            val key = "${fontFamily ?: "default"}:$style"
            return typefaceCache.getOrPut(key) {
                if (fontFamily != null) (try { Typeface.create(fontFamily, style) } catch (e: Exception) { Typeface.create(Typeface.DEFAULT, style) })
                else Typeface.create(Typeface.DEFAULT, style)
            }
        }

        fun deserialize(lines: List<String>, startIdx: Int): Pair<TableItem?, Int> {
            if (startIdx >= lines.size) return Pair(null, startIdx)
            val header = lines[startIdx].split("\u0001")
            if (header.size < 6) return Pair(null, startIdx)
            val item = try { TableItem(header[1].toFloat(), header[2].toFloat(), header[3].toFloat()) } catch (e: Exception) { return Pair(null, startIdx) }
            item.rows = header[4].toIntOrNull() ?: 3; item.cols = header[5].toIntOrNull() ?: 3
            if (header.size >= 7) item.showHeaders = header[6].toBoolean()
            if (header.size >= 8) item.headerTextSize = header[7].toFloatOrNull() ?: 22f
            var idx = startIdx + 1
            while (idx < lines.size && !lines[idx].startsWith("TABLEEND")) {
                val line = lines[idx]
                when {
                    line.startsWith("ROWHEIGHTS\u0001") -> { item.rowHeights.clear(); item.rowHeights.addAll(line.substringAfter("\u0001").split(",").mapNotNull { it.toFloatOrNull() }) }
                    line.startsWith("COLWIDTHS\u0001") -> { item.colWidths.clear(); item.colWidths.addAll(line.substringAfter("\u0001").split(",").mapNotNull { it.toFloatOrNull() }) }
                    line.startsWith("COLRESIZED\u0001") -> { item.colManuallyResized.clear(); item.colManuallyResized.addAll(line.substringAfter("\u0001").split(",").map { it.trim() == "true" }) }
                    line.startsWith("CELL\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 12) {
                            val r = p[1].toIntOrNull() ?: 0; val c = p[2].toIntOrNull() ?: 0
                            val cell = item.getCellSafe(r, c)
                            cell.textColor = p[3].toIntOrNull() ?: Color.BLACK
                            cell.bgColor = p[4].toIntOrNull() ?: Color.WHITE
                            cell.textSize = p[5].toFloatOrNull() ?: 14f
                            cell.borderColor = p[6].toIntOrNull() ?: Color.BLACK
                            cell.borderWidth = p[7].toFloatOrNull() ?: 2f
                            cell.alignment = p[8].toIntOrNull() ?: 0
                            val mir = p[9].toIntOrNull() ?: -1; val mic = p[10].toIntOrNull() ?: -1
                            cell.mergedInto = if (mir >= 0) Pair(mir, mic) else null
                            if (p.size >= 16) {
                                cell.bold = p[11].toBoolean(); cell.italic = p[12].toBoolean(); cell.underline = p[13].toBoolean()
                                cell.fontFamily = p[14].ifEmpty { null }
                                cell.text = p[15].replace("\u0002", "\n")
                            } else {
                                cell.text = p[11].replace("\u0002", "\n")
                            }
                        }
                    }
                    line.startsWith("MERGE\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 5) { val r = p[1].toIntOrNull() ?: 0; val c = p[2].toIntOrNull() ?: 0; item.mergeSpans[Pair(r, c)] = CellSpan(r, c, p[3].toIntOrNull() ?: 1, p[4].toIntOrNull() ?: 1) }
                    }
                }
                idx++
            }
            return Pair(item, idx + 1)
        }
    }
}
