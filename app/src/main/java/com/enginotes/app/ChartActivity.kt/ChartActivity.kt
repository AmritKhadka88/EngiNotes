package com.enginotes.app

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream

class ChartActivity : AppCompatActivity() {

    private lateinit var chartView: ChartView
    private var chartData: List<Pair<String, Float>> = emptyList()
    private var chartType = ChartType.BAR

    private val pickExcelLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadExcel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL

        // Top toolbar
        val toolbar = LinearLayout(this); toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#FF1565C0"))
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6))
        toolbar.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this); title.text = "📊 Chart Builder"; title.textSize = 16f
        title.setTextColor(Color.WHITE)
        val tlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); title.layoutParams = tlp
        toolbar.addView(title)

        fun tbtn(label: String, action: () -> Unit) {
            val b = Button(this); b.text = label; b.textSize = 12f; b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(3), 0, dp(3), 0); b.layoutParams = p
            b.setPadding(dp(8), dp(4), dp(8), dp(4)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { action() }; toolbar.addView(b)
        }

        tbtn("📂 Excel") { pickExcelLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
        tbtn("✏ Manual") { showManualDataDialog() }
        tbtn("Save") { saveChartToCanvas() }
        tbtn("✕") { finish() }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Chart type selector
        val typeRow = LinearLayout(this); typeRow.orientation = LinearLayout.HORIZONTAL
        typeRow.setBackgroundColor(Color.parseColor("#EEF0F0F0"))
        typeRow.setPadding(dp(8), dp(4), dp(8), dp(4))

        val typeLabel = TextView(this); typeLabel.text = "Type: "; typeLabel.textSize = 14f
        typeLabel.gravity = Gravity.CENTER_VERTICAL
        typeRow.addView(typeLabel)

        for ((ct, label) in listOf(ChartType.BAR to "Bar", ChartType.LINE to "Line", ChartType.PIE to "Pie")) {
            val b = Button(this); b.text = label; b.textSize = 12f
            b.setBackgroundColor(if (chartType == ct) Color.parseColor("#2196F3") else Color.LTGRAY)
            b.setTextColor(if (chartType == ct) Color.WHITE else Color.BLACK)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); b.layoutParams = p
            b.setPadding(dp(4), dp(4), dp(4), dp(4)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener {
                chartType = ct; chartView.chartType = ct; chartView.invalidate()
                for (v in typeRow.touchables) if (v is Button) {
                    v.setBackgroundColor(if (v.text == label) Color.parseColor("#2196F3") else Color.LTGRAY)
                    v.setTextColor(if (v.text == label) Color.WHITE else Color.BLACK)
                }
            }
            typeRow.addView(b)
        }
        root.addView(typeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Chart view
        chartView = ChartView(this)
        root.addView(chartView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        // Load sample data
        chartData = listOf("Jan" to 120f, "Feb" to 85f, "Mar" to 200f, "Apr" to 150f, "May" to 95f, "Jun" to 180f)
        chartView.setData(chartData); chartView.chartType = chartType
    }

    private fun loadExcel(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return
            val workbook = WorkbookFactory.create(stream)
            stream.close()
            val sheet = workbook.getSheetAt(0)
            val data = mutableListOf<Pair<String, Float>>()
            for (row in sheet) {
                if (row.rowNum == 0) continue // skip header
                val labelCell = row.getCell(0); val valueCell = row.getCell(1)
                if (labelCell != null && valueCell != null) {
                    val label = labelCell.toString()
                    val value = try { valueCell.numericCellValue.toFloat() } catch (e: Exception) { continue }
                    data.add(label to value)
                }
            }
            workbook.close()
            if (data.isEmpty()) { Toast.makeText(this, "No data found. Expected: Label | Value columns", Toast.LENGTH_LONG).show(); return }
            chartData = data; chartView.setData(data); Toast.makeText(this, "Loaded ${data.size} rows", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Excel error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showManualDataDialog() {
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(16), dp(8), dp(16), dp(8))

        val hint = TextView(this); hint.text = "Enter data as: Label,Value (one per line)\nExample:\nJan,120\nFeb,85\nMar,200"
        hint.textSize = 13f; hint.setPadding(0, 0, 0, dp(8)); container.addView(hint)

        val input = EditText(this); input.minLines = 6
        input.setText(chartData.joinToString("\n") { "${it.first},${it.second.toInt()}" })
        container.addView(input)

        AlertDialog.Builder(this).setTitle("Manual Data Entry").setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val data = mutableListOf<Pair<String, Float>>()
                for (line in input.text.toString().lines()) {
                    val parts = line.trim().split(","); if (parts.size < 2) continue
                    val v = parts[1].trim().toFloatOrNull() ?: continue
                    data.add(parts[0].trim() to v)
                }
                if (data.isNotEmpty()) { chartData = data; chartView.setData(data) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun saveChartToCanvas() {
        val bmp = Bitmap.createBitmap(chartView.width, chartView.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); chartView.draw(c)
        val folder = File(filesDir, "images"); if (!folder.exists()) folder.mkdirs()
        val outFile = File(folder, "chart_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "Chart saved! Go back and use Insert → Image to add it.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

enum class ChartType { BAR, LINE, PIE }

class ChartView(context: Context) : View(context) {
    private var data: List<Pair<String, Float>> = emptyList()
    var chartType = ChartType.BAR
    private val colors = listOf(
        Color.parseColor("#2196F3"), Color.parseColor("#F44336"), Color.parseColor("#4CAF50"),
        Color.parseColor("#FF9800"), Color.parseColor("#9C27B0"), Color.parseColor("#00BCD4"),
        Color.parseColor("#FFEB3B"), Color.parseColor("#795548"), Color.parseColor("#607D8B"), Color.parseColor("#E91E63")
    )

    fun setData(d: List<Pair<String, Float>>) { data = d; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        if (data.isEmpty()) {
            val p = Paint(); p.textSize = 40f; p.color = Color.GRAY; p.textAlign = Paint.Align.CENTER
            canvas.drawText("No data", width / 2f, height / 2f, p); return
        }
        when (chartType) { ChartType.BAR -> drawBar(canvas); ChartType.LINE -> drawLine(canvas); ChartType.PIE -> drawPie(canvas) }
    }

    private fun drawBar(canvas: Canvas) {
        val pad = 80f; val chartW = width - pad * 2f; val chartH = height - pad * 2f
        val maxVal = data.maxOf { it.second }.coerceAtLeast(1f)
        val barW = chartW / data.size * 0.7f; val gap = chartW / data.size * 0.3f

        // Axes
        val axP = Paint(); axP.color = Color.DKGRAY; axP.strokeWidth = 2f; axP.style = Paint.Style.STROKE
        canvas.drawLine(pad, pad, pad, pad + chartH, axP)
        canvas.drawLine(pad, pad + chartH, pad + chartW, pad + chartH, axP)

        // Y grid lines and labels
        val lp = Paint(); lp.color = Color.GRAY; lp.textSize = 28f; lp.textAlign = Paint.Align.RIGHT
        for (i in 0..4) {
            val v = maxVal * i / 4f; val y = pad + chartH - (chartH * i / 4f)
            canvas.drawLine(pad, y, pad + chartW, y, Paint().also { it.color = Color.parseColor("#EEEEEE"); it.strokeWidth = 1f })
            canvas.drawText(v.toInt().toString(), pad - 8f, y + 10f, lp)
        }

        // Bars
        for ((idx, entry) in data.withIndex()) {
            val barH = chartH * entry.second / maxVal
            val left = pad + idx * (chartW / data.size) + gap / 2f
            val right = left + barW
            val top = pad + chartH - barH
            val bp = Paint(); bp.color = colors[idx % colors.size]; bp.style = Paint.Style.FILL
            canvas.drawRect(left, top, right, pad + chartH, bp)

            // Value label
            val vp = Paint(); vp.color = Color.DKGRAY; vp.textSize = 24f; vp.textAlign = Paint.Align.CENTER
            canvas.drawText(entry.second.toInt().toString(), (left + right) / 2f, top - 8f, vp)

            // X label
            val xl = Paint(); xl.color = Color.DKGRAY; xl.textSize = 24f; xl.textAlign = Paint.Align.CENTER
            canvas.drawText(entry.first, (left + right) / 2f, pad + chartH + 36f, xl)
        }

        // Title
        val tp = Paint(); tp.color = Color.BLACK; tp.textSize = 36f; tp.textAlign = Paint.Align.CENTER; tp.isFakeBoldText = true
        canvas.drawText("Bar Chart", width / 2f, 48f, tp)
    }

    private fun drawLine(canvas: Canvas) {
        val pad = 80f; val chartW = width - pad * 2f; val chartH = height - pad * 2f
        val maxVal = data.maxOf { it.second }.coerceAtLeast(1f)
        val stepX = chartW / (data.size - 1).coerceAtLeast(1)

        val axP = Paint(); axP.color = Color.DKGRAY; axP.strokeWidth = 2f
        canvas.drawLine(pad, pad, pad, pad + chartH, axP)
        canvas.drawLine(pad, pad + chartH, pad + chartW, pad + chartH, axP)

        val lp = Paint(); lp.color = Color.GRAY; lp.textSize = 28f; lp.textAlign = Paint.Align.RIGHT
        for (i in 0..4) {
            val v = maxVal * i / 4f; val y = pad + chartH - (chartH * i / 4f)
            canvas.drawLine(pad, y, pad + chartW, y, Paint().also { it.color = Color.parseColor("#EEEEEE"); it.strokeWidth = 1f })
            canvas.drawText(v.toInt().toString(), pad - 8f, y + 10f, lp)
        }

        val linePaint = Paint(); linePaint.color = Color.parseColor("#2196F3"); linePaint.strokeWidth = 4f; linePaint.style = Paint.Style.STROKE; linePaint.isAntiAlias = true
        val path = Path()
        val points = data.mapIndexed { idx, entry ->
            val x = pad + idx * stepX; val y = pad + chartH - chartH * entry.second / maxVal
            Pair(x, y)
        }
        path.moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) path.lineTo(points[i].first, points[i].second)
        canvas.drawPath(path, linePaint)

        for ((idx, pt) in points.withIndex()) {
            val cp = Paint(); cp.color = Color.parseColor("#F44336"); cp.style = Paint.Style.FILL
            canvas.drawCircle(pt.first, pt.second, 8f, cp)
            val xl = Paint(); xl.color = Color.DKGRAY; xl.textSize = 24f; xl.textAlign = Paint.Align.CENTER
            canvas.drawText(data[idx].first, pt.first, pad + chartH + 36f, xl)
            val vl = Paint(); vl.color = Color.DKGRAY; vl.textSize = 22f; vl.textAlign = Paint.Align.CENTER
            canvas.drawText(data[idx].second.toInt().toString(), pt.first, pt.second - 16f, vl)
        }

        val tp = Paint(); tp.color = Color.BLACK; tp.textSize = 36f; tp.textAlign = Paint.Align.CENTER; tp.isFakeBoldText = true
        canvas.drawText("Line Chart", width / 2f, 48f, tp)
    }

    private fun drawPie(canvas: Canvas) {
        val total = data.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
        val cx = width / 2f; val cy = height / 2f
        val radius = minOf(width, height) / 2.5f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        var startAngle = -90f

        for ((idx, entry) in data.withIndex()) {
            val sweep = 360f * entry.second / total
            val pp = Paint(); pp.color = colors[idx % colors.size]; pp.style = Paint.Style.FILL; pp.isAntiAlias = true
            canvas.drawArc(oval, startAngle, sweep, true, pp)

            // Label line + text
            val midAngle = Math.toRadians((startAngle + sweep / 2).toDouble())
            val lx = cx + (radius * 1.25f * kotlin.math.cos(midAngle)).toFloat()
            val ly = cy + (radius * 1.25f * kotlin.math.sin(midAngle)).toFloat()
            val lp = Paint(); lp.color = Color.DKGRAY; lp.textSize = 28f
            lp.textAlign = if (lx > cx) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText("${entry.first} (${(entry.second / total * 100).toInt()}%)", lx, ly, lp)

            startAngle += sweep
        }

        val tp = Paint(); tp.color = Color.BLACK; tp.textSize = 36f; tp.textAlign = Paint.Align.CENTER; tp.isFakeBoldText = true
        canvas.drawText("Pie Chart", width / 2f, 48f, tp)
    }
}
