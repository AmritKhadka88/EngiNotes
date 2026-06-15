package com.enginotes.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

data class CellSpan(val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)

class TableCell(
    var text: String = "",
    var textColor: Int = Color.BLACK,
    var bgColor: Int = Color.WHITE,
    var textSize: Float = 14f,
    var borderColor: Int = Color.BLACK,
    var borderWidth: Float = 2f,
    var alignment: Int = 0, // 0=left, 1=center, 2=right
    var mergedInto: Pair<Int, Int>? = null // (row, col) of the master cell if this is merged
)

class TableItem(
    var x: Float,
    var y: Float,
    var rotation: Float = 0f
) {
    var rows: Int = 3
    var cols: Int = 3

    // Cell data [row][col]
    val cells: Array<Array<TableCell>> = Array(rows) { Array(cols) { TableCell() } }

    // Row heights and col widths in world coords
    val rowHeights: MutableList<Float> = MutableList(rows) { 60f }
    val colWidths: MutableList<Float> = MutableList(cols) { 100f }

    // Merge spans: master cell -> CellSpan
    val mergeSpans: MutableMap<Pair<Int, Int>, CellSpan> = mutableMapOf()

    fun totalWidth(): Float = colWidths.sum()
    fun totalHeight(): Float = rowHeights.sum()

    fun cellX(col: Int): Float = x + colWidths.take(col).sum()
    fun cellY(row: Int): Float = y + rowHeights.take(row).sum()

    fun cellRect(row: Int, col: Int): RectF {
        val span = mergeSpans[Pair(row, col)]
        val w = if (span != null) colWidths.subList(col, col + span.colSpan).sum() else colWidths[col]
        val h = if (span != null) rowHeights.subList(row, row + span.rowSpan).sum() else rowHeights[row]
        return RectF(cellX(col), cellY(row), cellX(col) + w, cellY(row) + h)
    }

    fun hitTestCell(wx: Float, wy: Float): Pair<Int, Int>? {
        if (wx < x || wx > x + totalWidth() || wy < y || wy > y + totalHeight()) return null
        var row = -1; var col = -1
        var cy = y
        for (r in 0 until rows) {
            if (wy < cy + rowHeights[r]) { row = r; break }
            cy += rowHeights[r]
        }
        var cx = x
        for (c in 0 until cols) {
            if (wx < cx + colWidths[c]) { col = c; break }
            cx += colWidths[c]
        }
        if (row < 0 || col < 0) return null
        // If this cell is merged into another, return the master
        val cell = cells[row][col]
        return cell.mergedInto ?: Pair(row, col)
    }

    // Returns (row border index, is row border) for resize hit testing
    // Row border: between row i and i+1 → index i
    // Col border: between col j and j+1 → index j
    fun hitTestRowBorder(wy: Float, tolerance: Float): Int {
        var cy = y
        for (r in 0 until rows - 1) {
            cy += rowHeights[r]
            if (kotlin.math.abs(wy - cy) <= tolerance) return r
        }
        return -1
    }

    fun hitTestColBorder(wx: Float, tolerance: Float): Int {
        var cx = x
        for (c in 0 until cols - 1) {
            cx += colWidths[c]
            if (kotlin.math.abs(wx - cx) <= tolerance) return c
        }
        return -1
    }

    fun draw(canvas: Canvas, scaleFactor: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.translate(-x, -y)

        // Draw cell backgrounds
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = cells[r][c]
                if (cell.mergedInto != null) continue
                val rect = cellRect(r, c)
                val bgPaint = Paint(); bgPaint.color = cell.bgColor; bgPaint.style = Paint.Style.FILL
                canvas.drawRect(rect, bgPaint)
            }
        }

        // Draw cell text
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = cells[r][c]
                if (cell.mergedInto != null || cell.text.isBlank()) continue
                val rect = cellRect(r, c)
                val tp = Paint()
                tp.color = cell.textColor
                tp.textSize = cell.textSize
                tp.isAntiAlias = true
                val textW = tp.measureText(cell.text)
                val tx = when (cell.alignment) {
                    1 -> rect.centerX() - textW / 2f
                    2 -> rect.right - textW - 4f
                    else -> rect.left + 4f
                }
                val ty = rect.centerY() - (tp.descent() + tp.ascent()) / 2f
                canvas.drawText(cell.text, tx, ty, tp)
            }
        }

        // Draw borders
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = cells[r][c]
                if (cell.mergedInto != null) continue
                val rect = cellRect(r, c)
                val bp = Paint(); bp.color = cell.borderColor; bp.strokeWidth = cell.borderWidth / scaleFactor; bp.style = Paint.Style.STROKE
                canvas.drawRect(rect, bp)
            }
        }

        canvas.restore()
    }

    fun addRow(after: Int = rows - 1) {
        rows++
        rowHeights.add(after + 1, 60f)
        val newRow = Array(cols) { TableCell() }
        val newCells = Array(rows) { r -> if (r <= after) cells[r] else if (r == after + 1) newRow else cells[r - 1] }
        for (r in newCells.indices) for (c in newCells[r].indices) cells[r][c] = newCells[r][c]
    }

    fun addCol(after: Int = cols - 1) {
        cols++
        colWidths.add(after + 1, 100f)
    }

    fun mergeCells(r1: Int, c1: Int, r2: Int, c2: Int) {
        val minR = minOf(r1, r2); val maxR = maxOf(r1, r2)
        val minC = minOf(c1, c2); val maxC = maxOf(c1, c2)
        val master = Pair(minR, minC)
        mergeSpans[master] = CellSpan(minR, minC, maxR - minR + 1, maxC - minC + 1)
        for (r in minR..maxR) for (c in minC..maxC) {
            if (r == minR && c == minC) continue
            cells[r][c].mergedInto = master
        }
    }

    fun unmergeCells(r: Int, c: Int) {
        val key = Pair(r, c)
        val span = mergeSpans[key] ?: return
        mergeSpans.remove(key)
        for (dr in 0 until span.rowSpan) for (dc in 0 until span.colSpan) {
            if (dr == 0 && dc == 0) continue
            cells[r + dr][c + dc].mergedInto = null
        }
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("TABLE\u0001$x\u0001$y\u0001$rotation\u0001$rows\u0001$cols\n")
        sb.append("ROWHEIGHTS\u0001${rowHeights.joinToString(",")}\n")
        sb.append("COLWIDTHS\u0001${colWidths.joinToString(",")}\n")
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = cells[r][c]
            val mi = cell.mergedInto
            sb.append("CELL\u0001$r\u0001$c\u0001${cell.textColor}\u0001${cell.bgColor}\u0001${cell.textSize}\u0001${cell.borderColor}\u0001${cell.borderWidth}\u0001${cell.alignment}\u0001${mi?.first ?: -1}\u0001${mi?.second ?: -1}\u0001${cell.text.replace("\n", "\u0002")}\n")
        }
        for ((key, span) in mergeSpans) {
            sb.append("MERGE\u0001${key.first}\u0001${key.second}\u0001${span.rowSpan}\u0001${span.colSpan}\n")
        }
        sb.append("TABLEEND\n")
        return sb.toString()
    }

    fun getCellPublic(r: Int, c: Int): TableCell = getCellSafe(r, c)

    fun insertRow(at: Int) {
        val newRow = MutableList(cols) { TableCell() }
        if (at >= _cells.size) _cells.add(newRow)
        else _cells.add(at, newRow)
    }

    fun insertCol(at: Int) {
        for (row in _cells) {
            if (at >= row.size) row.add(TableCell())
            else row.add(at, TableCell())
        }
    }

    fun deleteRow(at: Int) {
        if (at < _cells.size) _cells.removeAt(at)
    }

    fun deleteCol(at: Int) {
        for (row in _cells) {
            if (at < row.size) row.removeAt(at)
        }
    }

    companion object {
        fun deserialize(lines: List<String>, startIdx: Int): Pair<TableItem?, Int> {
            if (startIdx >= lines.size) return Pair(null, startIdx)
            val header = lines[startIdx].split("\u0001")
            if (header.size < 6) return Pair(null, startIdx)
            val item = TableItem(header[1].toFloat(), header[2].toFloat(), header[3].toFloat())
            // rows/cols will be set from cells
            var idx = startIdx + 1
            while (idx < lines.size && !lines[idx].startsWith("TABLEEND")) {
                val line = lines[idx]
                when {
                    line.startsWith("ROWHEIGHTS\u0001") -> {
                        val parts = line.substringAfter("\u0001").split(",")
                        item.rowHeights.clear()
                        item.rowHeights.addAll(parts.map { it.toFloat() })
                    }
                    line.startsWith("COLWIDTHS\u0001") -> {
                        val parts = line.substringAfter("\u0001").split(",")
                        item.colWidths.clear()
                        item.colWidths.addAll(parts.map { it.toFloat() })
                    }
                    line.startsWith("CELL\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 12) {
                            val r = p[1].toInt(); val c = p[2].toInt()
                            if (r < item.rows && c < item.cols) {
                                val cell = item.cells[r][c]
                                cell.textColor = p[3].toInt(); cell.bgColor = p[4].toInt()
                                cell.textSize = p[5].toFloat(); cell.borderColor = p[6].toInt()
                                cell.borderWidth = p[7].toFloat(); cell.alignment = p[8].toInt()
                                val mir = p[9].toInt(); val mic = p[10].toInt()
                                cell.mergedInto = if (mir >= 0) Pair(mir, mic) else null
                                cell.text = p[11].replace("\u0002", "\n")
                            }
                        }
                    }
                    line.startsWith("MERGE\u0001") -> {
                        val p = line.split("\u0001")
                        if (p.size >= 5) {
                            val r = p[1].toInt(); val c = p[2].toInt()
                            item.mergeSpans[Pair(r, c)] = CellSpan(r, c, p[3].toInt(), p[4].toInt())
                        }
                    }
                }
                idx++
            }
            return Pair(item, idx + 1)
        }
    }
}
