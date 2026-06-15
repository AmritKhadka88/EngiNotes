package com.enginotes.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

data class DataSeries(
    var name: String,
    var data: MutableList<Pair<String, Float>>,
    var color: Int = Color.parseColor("#2196F3"),
    var lineWidth: Float = 3f,
    var fontSize: Float = 28f
)

data class PinnedLabel(val label: String, val value: Float, val x: Float, val y: Float)

class ChartActivity : AppCompatActivity() {

    private lateinit var chartView: ChartView
    private val seriesList = mutableListOf<DataSeries>()
    private var chartType = ChartType.BAR
    private var chartTitle = "My Chart"
    private var titleFontSize = 40f
    private var titleColor = Color.BLACK
    private var bgColor = Color.WHITE
    private var gridColor = Color.parseColor("#EEEEEE")
    private var labelFontSize = 26f
    private var labelColor = Color.DKGRAY
    private var showGrid = true
    private var show3D = false

    // Whether opened from note (for send-to-note)
    private val openedFromNote: Boolean get() = callingActivity != null

    private val pickExcelLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) loadExcel(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL

        // Top toolbar
        val toolbar = LinearLayout(this); toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#FF1565C0"))
        toolbar.setPadding(dp(6), dp(4), dp(6), dp(4))
        toolbar.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this); title.text = "📊 Chart Builder"; title.textSize = 14f
        title.setTextColor(Color.WHITE)
        title.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        toolbar.addView(title)

        fun tbtn(label: String, action: () -> Unit) {
            val b = Button(this); b.text = label; b.textSize = 11f; b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
            b.setPadding(dp(6), dp(3), dp(6), dp(3)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { action() }; toolbar.addView(b)
        }

        tbtn("📂 Excel") { pickExcelLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
        tbtn("✏ Data") { showDataDialog() }
        tbtn("🎨 Style") { showStyleDialog() }
        tbtn("📌 Labels") { chartView.togglePinnedMode(); Toast.makeText(this, if (chartView.pinnedMode) "Tap points to pin labels" else "Pin mode off", Toast.LENGTH_SHORT).show() }
        tbtn("📤 Send") { showSendOptions() }
        tbtn("✕") { finish() }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Excel format hint bar
        val hintBar = TextView(this)
        hintBar.text = "  💡 Excel format: Row 1 = headers (Label, Series1, Series2...), Row 2+ = data"
        hintBar.textSize = 11f
        hintBar.setTextColor(Color.WHITE)
        hintBar.setBackgroundColor(Color.parseColor("#CC1565C0"))
        hintBar.setPadding(dp(8), dp(4), dp(8), dp(4))
        root.addView(hintBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Chart type scroll
        val typeScroll = HorizontalScrollView(this)
        typeScroll.setBackgroundColor(Color.parseColor("#F5F5F5"))
        val typeRow = LinearLayout(this); typeRow.orientation = LinearLayout.HORIZONTAL
        typeRow.setPadding(dp(4), dp(4), dp(4), dp(4))

        val chartTypes = listOf(
            ChartType.BAR to "Bar", ChartType.BAR_3D to "Bar 3D",
            ChartType.HORIZONTAL_BAR to "H.Bar", ChartType.STACKED_BAR to "Stacked",
            ChartType.STACKED_BAR_100 to "100% Stack", ChartType.LINE to "Line",
            ChartType.LINE_SMOOTH to "Smooth", ChartType.AREA to "Area",
            ChartType.STACKED_AREA to "Stk.Area", ChartType.PIE to "Pie",
            ChartType.DONUT to "Donut", ChartType.PIE_3D to "Pie 3D",
            ChartType.SCATTER to "Scatter", ChartType.BUBBLE to "Bubble",
            ChartType.RADAR to "Radar", ChartType.HISTOGRAM to "Histogram",
            ChartType.WATERFALL to "Waterfall", ChartType.FUNNEL to "Funnel",
            ChartType.HEATMAP to "Heatmap", ChartType.GAUGE to "Gauge",
            ChartType.CANDLESTICK to "Candle", ChartType.TREEMAP to "Treemap"
        )

        val typeBtns = mutableListOf<Button>()
        for ((ct, label) in chartTypes) {
            val b = Button(this); b.text = label; b.textSize = 11f
            b.setBackgroundColor(if (chartType == ct) Color.parseColor("#2196F3") else Color.LTGRAY)
            b.setTextColor(if (chartType == ct) Color.WHITE else Color.BLACK)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
            b.setPadding(dp(8), dp(3), dp(8), dp(3)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener {
                chartType = ct; chartView.chartType = ct; chartView.invalidate()
                typeBtns.forEach { it.setBackgroundColor(Color.LTGRAY); it.setTextColor(Color.BLACK) }
                b.setBackgroundColor(Color.parseColor("#2196F3")); b.setTextColor(Color.WHITE)
            }
            typeBtns.add(b); typeRow.addView(b)
        }
        typeScroll.addView(typeRow)
        root.addView(typeScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Chart view
        chartView = ChartView(this)
        root.addView(chartView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        seriesList.add(DataSeries("Series A", mutableListOf(
            "Jan" to 120f, "Feb" to 85f, "Mar" to 200f, "Apr" to 150f, "May" to 95f, "Jun" to 180f
        ), Color.parseColor("#2196F3")))
        seriesList.add(DataSeries("Series B", mutableListOf(
            "Jan" to 80f, "Feb" to 140f, "Mar" to 110f, "Apr" to 190f, "May" to 60f, "Jun" to 220f
        ), Color.parseColor("#F44336")))

        chartView.bind(seriesList, chartType)
        chartView.applyStyle(bgColor, gridColor, titleColor, titleFontSize, labelColor, labelFontSize, showGrid, chartTitle)
    }

    private fun showSendOptions() {
        val options = mutableListOf("📄 Save as PDF", "🖼 Save as Image (PNG)", "🖼 Save as JPG")
        if (openedFromNote) options.add("📝 Send to Note")
        AlertDialog.Builder(this).setTitle("Export Chart")
            .setItems(options.toTypedArray()) { _, i ->
                when (i) {
                    0 -> exportChartAsPdf()
                    1 -> exportChartAsImage(Bitmap.CompressFormat.PNG, "png")
                    2 -> exportChartAsImage(Bitmap.CompressFormat.JPEG, "jpg")
                    3 -> sendToNote()
                }
            }.show()
    }

    private fun getChartBitmap(): Bitmap {
        val w = chartView.width.coerceAtLeast(800)
        val h = chartView.height.coerceAtLeast(600)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        chartView.draw(canvas)
        return bmp
    }

    private fun exportChartAsPdf() {
        try {
            val bmp = getChartBitmap()
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create()
            val page = doc.startPage(pageInfo)
            page.canvas.drawBitmap(bmp, 0f, 0f, Paint())
            doc.finishPage(page)
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "chart_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            shareFile(file, "application/pdf")
            Toast.makeText(this, "PDF saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportChartAsImage(format: Bitmap.CompressFormat, ext: String) {
        try {
            val bmp = getChartBitmap()
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "chart_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { bmp.compress(format, 95, it) }
            shareFile(file, "image/$ext")
            Toast.makeText(this, "Image saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendToNote() {
        try {
            val bmp = getChartBitmap()
            val folder = File(filesDir, "images"); if (!folder.exists()) folder.mkdirs()
            val outFile = File(folder, "chart_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = Intent()
            result.putExtra("chart_image_path", outFile.absolutePath)
            setResult(RESULT_OK, result)
            Toast.makeText(this, "Chart sent to note!", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = mimeType
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDataDialog() {
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(12), dp(8), dp(12), dp(8))

        // Excel format guide
        val guide = TextView(this)
        guide.text = "📋 Excel format:\nRow 1: Label | Series1 | Series2 | ...\nRow 2+: Jan | 120 | 80 | ..."
        guide.textSize = 12f
        guide.setBackgroundColor(Color.parseColor("#E3F2FD"))
        guide.setPadding(dp(8), dp(8), dp(8), dp(8))
        guide.setTextColor(Color.parseColor("#1565C0"))
        container.addView(guide)

        val seriesContainer = LinearLayout(this); seriesContainer.orientation = LinearLayout.VERTICAL
        container.addView(seriesContainer)

        fun refreshSeries() {
            seriesContainer.removeAllViews()
            for ((idx, series) in seriesList.withIndex()) {
                val row = LinearLayout(this); row.orientation = LinearLayout.VERTICAL
                row.setBackgroundColor(Color.parseColor("#F5F5F5"))
                row.setPadding(dp(8), dp(6), dp(8), dp(6))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, dp(4), 0, dp(4)); row.layoutParams = lp

                val headerRow = LinearLayout(this); headerRow.orientation = LinearLayout.HORIZONTAL; headerRow.gravity = Gravity.CENTER_VERTICAL
                val nameInput = EditText(this); nameInput.setText(series.name); nameInput.textSize = 13f
                nameInput.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                nameInput.setOnFocusChangeListener { _, _ -> series.name = nameInput.text.toString() }

                val colorBtn = Button(this); colorBtn.text = "🎨"; colorBtn.textSize = 13f
                colorBtn.setBackgroundColor(series.color); colorBtn.setTextColor(Color.WHITE)
                colorBtn.setPadding(dp(8), dp(2), dp(8), dp(2)); colorBtn.minWidth = 0; colorBtn.minimumWidth = 0
                colorBtn.setOnClickListener { showColorGridDialog { c -> series.color = c; colorBtn.setBackgroundColor(c); chartView.invalidate() } }

                val delBtn = Button(this); delBtn.text = "✕"; delBtn.textSize = 13f; delBtn.setTextColor(Color.WHITE)
                delBtn.setBackgroundColor(Color.parseColor("#F44336"))
                delBtn.setPadding(dp(8), dp(2), dp(8), dp(2)); delBtn.minWidth = 0; delBtn.minimumWidth = 0
                delBtn.setOnClickListener { if (seriesList.size > 1) { seriesList.removeAt(idx); refreshSeries(); chartView.invalidate() } }

                headerRow.addView(nameInput); headerRow.addView(colorBtn); headerRow.addView(delBtn)
                row.addView(headerRow)

                val hint = TextView(this); hint.text = "Data (Label,Value per line):"; hint.textSize = 11f; row.addView(hint)
                val dataInput = EditText(this); dataInput.minLines = 4
                dataInput.setText(series.data.joinToString("\n") { "${it.first},${it.second.toInt()}" })
                dataInput.textSize = 12f
                dataInput.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val newData = mutableListOf<Pair<String, Float>>()
                        for (line in dataInput.text.toString().lines()) {
                            val parts = line.trim().split(","); if (parts.size < 2) continue
                            val v = parts[1].trim().toFloatOrNull() ?: continue
                            newData.add(parts[0].trim() to v)
                        }
                        if (newData.isNotEmpty()) { series.data = newData; chartView.invalidate() }
                    }
                }
                row.addView(dataInput)

                val widthRow = LinearLayout(this); widthRow.orientation = LinearLayout.HORIZONTAL; widthRow.gravity = Gravity.CENTER_VERTICAL
                val widthLbl = TextView(this); widthLbl.text = "Line width: "; widthLbl.textSize = 12f
                val widthSeek = SeekBar(this); widthSeek.max = 20; widthSeek.progress = series.lineWidth.toInt()
                widthSeek.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                widthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { series.lineWidth = v.toFloat().coerceAtLeast(1f); chartView.invalidate() }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
                widthRow.addView(widthLbl); widthRow.addView(widthSeek); row.addView(widthRow)
                seriesContainer.addView(row)
            }
        }
        refreshSeries()

        val addBtn = Button(this); addBtn.text = "+ Add Series"; addBtn.textSize = 13f
        addBtn.setBackgroundColor(Color.parseColor("#4CAF50")); addBtn.setTextColor(Color.WHITE)
        addBtn.setOnClickListener {
            val colors = listOf("#2196F3","#F44336","#4CAF50","#FF9800","#9C27B0","#00BCD4","#FFEB3B","#795548")
            seriesList.add(DataSeries("Series ${seriesList.size + 1}",
                mutableListOf("A" to 100f, "B" to 150f, "C" to 80f),
                Color.parseColor(colors[seriesList.size % colors.size])))
            refreshSeries(); chartView.invalidate()
        }
        container.addView(addBtn)

        val scroll = ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("Edit Data & Series").setView(scroll)
            .setPositiveButton("Apply") { _, _ -> chartView.bind(seriesList, chartType); chartView.invalidate() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showStyleDialog() {
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(16), dp(8), dp(16), dp(8))

        fun lbl(text: String) { val tv = TextView(this); tv.text = text; tv.textSize = 13f; tv.setPadding(0, dp(8), 0, dp(2)); container.addView(tv) }

        lbl("Chart Title")
        val titleInput = EditText(this); titleInput.setText(chartTitle); titleInput.textSize = 14f; container.addView(titleInput)

        lbl("Title Color")
        val titleColorBtn = Button(this); titleColorBtn.text = "Pick Title Color"; titleColorBtn.textSize = 13f
        titleColorBtn.setBackgroundColor(titleColor); titleColorBtn.setTextColor(Color.WHITE)
        titleColorBtn.setOnClickListener { showColorGridDialog { c -> titleColor = c; titleColorBtn.setBackgroundColor(c) } }
        container.addView(titleColorBtn)

        lbl("Title Font Size")
        val titleSizeSeek = SeekBar(this); titleSizeSeek.max = 80; titleSizeSeek.progress = titleFontSize.toInt()
        titleSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { titleFontSize = v.toFloat().coerceAtLeast(20f) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(titleSizeSeek)

        lbl("Label Color")
        val labelColorBtn = Button(this); labelColorBtn.text = "Pick Label Color"; labelColorBtn.textSize = 13f
        labelColorBtn.setBackgroundColor(labelColor); labelColorBtn.setTextColor(Color.WHITE)
        labelColorBtn.setOnClickListener { showColorGridDialog { c -> labelColor = c; labelColorBtn.setBackgroundColor(c) } }
        container.addView(labelColorBtn)

        lbl("Label Font Size")
        val labelSizeSeek = SeekBar(this); labelSizeSeek.max = 60; labelSizeSeek.progress = labelFontSize.toInt()
        labelSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) { labelFontSize = v.toFloat().coerceAtLeast(14f) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(labelSizeSeek)

        lbl("Background Color")
        val bgBtn = Button(this); bgBtn.text = "Pick Background"; bgBtn.textSize = 13f
        bgBtn.setBackgroundColor(bgColor)
        bgBtn.setOnClickListener { showColorGridDialog { c -> bgColor = c; bgBtn.setBackgroundColor(c) } }
        container.addView(bgBtn)

        lbl("Grid Color")
        val gridBtn = Button(this); gridBtn.text = "Pick Grid Color"; gridBtn.textSize = 13f
        gridBtn.setBackgroundColor(gridColor)
        gridBtn.setOnClickListener { showColorGridDialog { c -> gridColor = c; gridBtn.setBackgroundColor(c) } }
        container.addView(gridBtn)

        val gridCheck = CheckBox(this); gridCheck.text = "Show Grid"; gridCheck.isChecked = showGrid; container.addView(gridCheck)
        val d3Check = CheckBox(this); d3Check.text = "3D Effect (Bar/Pie)"; d3Check.isChecked = show3D; container.addView(d3Check)

        val scroll = ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("Chart Style").setView(scroll)
            .setPositiveButton("Apply") { _, _ ->
                chartTitle = titleInput.text.toString()
                showGrid = gridCheck.isChecked; show3D = d3Check.isChecked
                chartView.applyStyle(bgColor, gridColor, titleColor, titleFontSize, labelColor, labelFontSize, showGrid, chartTitle)
                chartView.invalidate()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun loadExcel(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return
            val workbook = WorkbookFactory.create(stream); stream.close()
            val sheet = workbook.getSheetAt(0)
            seriesList.clear()
            val headerRow = sheet.getRow(0) ?: return
            val numSeries = headerRow.lastCellNum - 1
            val colors = listOf("#2196F3","#F44336","#4CAF50","#FF9800","#9C27B0","#00BCD4","#FFEB3B","#795548","#607D8B","#E91E63")
            for (s in 0 until numSeries) {
                val name = headerRow.getCell(s + 1)?.toString() ?: "Series ${s+1}"
                seriesList.add(DataSeries(name, mutableListOf(), Color.parseColor(colors[s % colors.size])))
            }
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val label = row.getCell(0)?.toString() ?: continue
                for (s in 0 until numSeries) {
                    val v = row.getCell(s + 1)?.numericCellValue?.toFloat() ?: continue
                    if (s < seriesList.size) seriesList[s].data.add(label to v)
                }
            }
            workbook.close()
            chartView.bind(seriesList, chartType); chartView.invalidate()
            Toast.makeText(this, "Loaded ${seriesList.size} series", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Excel error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showColorGridDialog(onPicked: (Int) -> Unit) {
        val grid = GridLayout(this); grid.columnCount = 10; grid.setPadding(dp(10), dp(10), dp(10), dp(10))
        lateinit var dialog: AlertDialog
        fun addSwatch(color: Int) {
            val s = View(this); val p = GridLayout.LayoutParams()
            p.width = dp(28); p.height = dp(28); p.setMargins(dp(2), dp(2), dp(2), dp(2))
            s.layoutParams = p; s.setBackgroundColor(color)
            s.setOnClickListener { onPicked(color); dialog.dismiss() }
            grid.addView(s)
        }
        for (i in 0..9) addSwatch(Color.HSVToColor(floatArrayOf(0f, 0f, 1f - i / 9f)))
        for (value in listOf(1.0f, 0.85f, 0.7f, 0.5f, 0.3f))
            for (hue in listOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330))
                addSwatch(Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.65f, value)))
        val scroll = ScrollView(this); scroll.addView(grid)
        dialog = AlertDialog.Builder(this).setTitle("Color").setView(scroll).create(); dialog.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

enum class ChartType {
    BAR, BAR_3D, HORIZONTAL_BAR, STACKED_BAR, STACKED_BAR_100,
    LINE, LINE_SMOOTH, AREA, STACKED_AREA,
    PIE, DONUT, PIE_3D,
    SCATTER, BUBBLE, RADAR, HISTOGRAM,
    WATERFALL, FUNNEL, HEATMAP, GAUGE,
    CANDLESTICK, TREEMAP
}

class ChartView(context: Context) : View(context) {
    private var series = listOf<DataSeries>()
    var chartType = ChartType.BAR
    var pinnedMode = false

    private var bgColor = Color.WHITE
    private var gridColor = Color.parseColor("#EEEEEE")
    private var titleColor = Color.BLACK
    private var titleFontSize = 40f
    private var labelColor = Color.DKGRAY
    private var labelFontSize = 26f
    private var showGrid = true
    private var chartTitle = "Chart"

    // Pinned labels that always show on chart
    private val pinnedLabels = mutableListOf<PinnedLabel>()
    // Temporary hover label (tap to show, tap again to pin)
    private var hoverLabel: PinnedLabel? = null

    // All interactive data points for tap detection
    private val dataPoints = mutableListOf<PinnedLabel>()

    private val defaultColors = listOf(
        Color.parseColor("#2196F3"), Color.parseColor("#F44336"),
        Color.parseColor("#4CAF50"), Color.parseColor("#FF9800"),
        Color.parseColor("#9C27B0"), Color.parseColor("#00BCD4"),
        Color.parseColor("#FFEB3B"), Color.parseColor("#795548"),
        Color.parseColor("#607D8B"), Color.parseColor("#E91E63")
    )

    fun bind(s: List<DataSeries>, type: ChartType) { series = s; chartType = type; dataPoints.clear(); hoverLabel = null; invalidate() }

    fun applyStyle(bg: Int, grid: Int, tc: Int, tfs: Float, lc: Int, lfs: Float, sg: Boolean, title: String) {
        bgColor = bg; gridColor = grid; titleColor = tc; titleFontSize = tfs
        labelColor = lc; labelFontSize = lfs; showGrid = sg; chartTitle = title; invalidate()
    }

    fun togglePinnedMode() { pinnedMode = !pinnedMode; hoverLabel = null; invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val tx = event.x; val ty = event.y
            val hitRadius = 40f

            // Check if tapping an existing pinned label to remove it
            val toRemove = pinnedLabels.find { abs(it.x - tx) < hitRadius && abs(it.y - ty) < hitRadius }
            if (toRemove != null) { pinnedLabels.remove(toRemove); hoverLabel = null; invalidate(); return true }

            // Find nearest data point
            val nearest = dataPoints.minByOrNull { sqrt((it.x - tx).pow(2) + (it.y - ty).pow(2)) }
            if (nearest != null && sqrt((nearest.x - tx).pow(2) + (nearest.y - ty).pow(2)) < hitRadius * 2) {
                if (pinnedMode) {
                    // Pin it permanently
                    if (pinnedLabels.none { abs(it.x - nearest.x) < 5f && abs(it.y - nearest.y) < 5f }) {
                        pinnedLabels.add(nearest)
                    }
                    hoverLabel = null
                } else {
                    // Show hover tooltip
                    hoverLabel = if (hoverLabel?.label == nearest.label && hoverLabel?.value == nearest.value) null else nearest
                }
                invalidate(); return true
            }
            // Tap empty area clears hover
            hoverLabel = null; invalidate()
        }
        return true
    }

    private fun color(idx: Int): Int = if (idx < series.size) series[idx].color else defaultColors[idx % defaultColors.size]
    private fun allLabels(): List<String> = series.firstOrNull()?.data?.map { it.first } ?: emptyList()
    private fun allValues(): List<Float> = series.flatMap { s -> s.data.map { it.second } }
    private fun maxVal(): Float = allValues().maxOrNull()?.coerceAtLeast(1f) ?: 1f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgColor)
        drawTitle(canvas)
        dataPoints.clear()
        if (series.isEmpty() || series.all { it.data.isEmpty() }) {
            val p = Paint(); p.textSize = 40f; p.color = Color.GRAY; p.textAlign = Paint.Align.CENTER
            canvas.drawText("No data", width / 2f, height / 2f, p); return
        }
        when (chartType) {
            ChartType.BAR -> drawBar(canvas, false)
            ChartType.BAR_3D -> drawBar(canvas, true)
            ChartType.HORIZONTAL_BAR -> drawHorizontalBar(canvas)
            ChartType.STACKED_BAR -> drawStackedBar(canvas, false)
            ChartType.STACKED_BAR_100 -> drawStackedBar(canvas, true)
            ChartType.LINE -> drawLine(canvas, false, false)
            ChartType.LINE_SMOOTH -> drawLine(canvas, true, false)
            ChartType.AREA -> drawLine(canvas, false, true)
            ChartType.STACKED_AREA -> drawStackedArea(canvas)
            ChartType.PIE -> drawPie(canvas, false, false)
            ChartType.DONUT -> drawPie(canvas, true, false)
            ChartType.PIE_3D -> drawPie(canvas, false, true)
            ChartType.SCATTER -> drawScatter(canvas)
            ChartType.BUBBLE -> drawBubble(canvas)
            ChartType.RADAR -> drawRadar(canvas)
            ChartType.HISTOGRAM -> drawHistogram(canvas)
            ChartType.WATERFALL -> drawWaterfall(canvas)
            ChartType.FUNNEL -> drawFunnel(canvas)
            ChartType.HEATMAP -> drawHeatmap(canvas)
            ChartType.GAUGE -> drawGauge(canvas)
            ChartType.CANDLESTICK -> drawCandlestick(canvas)
            ChartType.TREEMAP -> drawTreemap(canvas)
        }
        drawLegend(canvas)
        drawPinnedLabels(canvas)
        drawHoverLabel(canvas)
        if (pinnedMode) drawPinnedModeIndicator(canvas)
    }

    private fun drawPinnedModeIndicator(canvas: Canvas) {
        val p = Paint(); p.color = Color.parseColor("#CC2196F3"); p.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(10f, 10f, 260f, 55f), 12f, 12f, p)
        val tp = Paint(); tp.color = Color.WHITE; tp.textSize = 28f; tp.isAntiAlias = true
        canvas.drawText("📌 Tap points to pin", 20f, 42f, tp)
    }

    private fun drawPinnedLabels(canvas: Canvas) {
        for (pl in pinnedLabels) drawDataLabel(canvas, pl, Color.parseColor("#CC1565C0"), true)
    }

    private fun drawHoverLabel(canvas: Canvas) {
        val hl = hoverLabel ?: return
        drawDataLabel(canvas, hl, Color.parseColor("#CC333333"), false)
    }

    private fun drawDataLabel(canvas: Canvas, pl: PinnedLabel, bgC: Int, isPinned: Boolean) {
        val text = "${pl.label}: ${pl.value.toInt()}"
        val tp = Paint(); tp.textSize = labelFontSize; tp.isAntiAlias = true; tp.color = Color.WHITE
        val tw = tp.measureText(text) + 20f; val th = labelFontSize + 16f
        var lx = pl.x - tw / 2f; val ly = pl.y - th - 12f
        lx = lx.coerceIn(0f, width - tw)
        val bp = Paint(); bp.color = bgC; bp.style = Paint.Style.FILL; bp.isAntiAlias = true
        canvas.drawRoundRect(RectF(lx, ly, lx + tw, ly + th), 8f, 8f, bp)
        // Arrow
        val ap = Paint(); ap.color = bgC; ap.style = Paint.Style.FILL
        val path = Path()
        path.moveTo(pl.x - 8f, ly + th); path.lineTo(pl.x + 8f, ly + th); path.lineTo(pl.x, pl.y - 4f); path.close()
        canvas.drawPath(path, ap)
        canvas.drawText(text, lx + 10f, ly + labelFontSize + 6f, tp)
        if (isPinned) {
            val pp = Paint(); pp.color = Color.parseColor("#FFEB3B"); pp.textSize = labelFontSize * 0.8f; pp.isAntiAlias = true
            canvas.drawText("📌", lx + tw - labelFontSize, ly + labelFontSize + 4f, pp)
        }
        // Dot at data point
        val dp2 = Paint(); dp2.color = bgC; dp2.style = Paint.Style.FILL; dp2.isAntiAlias = true
        canvas.drawCircle(pl.x, pl.y, 8f, dp2)
    }

    private fun drawTitle(canvas: Canvas) {
        val p = Paint(); p.color = titleColor; p.textSize = titleFontSize
        p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true; p.isAntiAlias = true
        canvas.drawText(chartTitle, width / 2f, titleFontSize + 10f, p)
    }

    private fun chartArea(): RectF {
        val legendH = if (series.size > 1) series.size * (labelFontSize + 8f) + 20f else 0f
        return RectF(80f, titleFontSize + 30f, width - 20f, height - legendH - 50f)
    }

    private fun drawAxes(canvas: Canvas, area: RectF, maxV: Float, labels: List<String>, horizontal: Boolean = false) {
        val ap = Paint(); ap.color = Color.DKGRAY; ap.strokeWidth = 2f
        canvas.drawLine(area.left, area.top, area.left, area.bottom, ap)
        canvas.drawLine(area.left, area.bottom, area.right, area.bottom, ap)
        val lp = Paint(); lp.color = labelColor; lp.textSize = labelFontSize; lp.isAntiAlias = true
        val gp = Paint(); gp.color = gridColor; gp.strokeWidth = 1f
        for (i in 0..4) {
            val v = maxV * i / 4f
            val y = area.bottom - (area.height() * i / 4f)
            if (showGrid) canvas.drawLine(area.left, y, area.right, y, gp)
            lp.textAlign = Paint.Align.RIGHT
            canvas.drawText(v.toInt().toString(), area.left - 8f, y + lp.textSize / 3f, lp)
        }
        lp.textAlign = Paint.Align.CENTER
        for ((idx, label) in labels.withIndex()) {
            val x = area.left + (idx + 0.5f) * area.width() / labels.size
            canvas.drawText(label, x, area.bottom + labelFontSize + 8f, lp)
        }
    }

    private fun drawBar(canvas: Canvas, is3D: Boolean) {
        val area = chartArea(); val labels = allLabels(); val maxV = maxVal()
        drawAxes(canvas, area, maxV, labels)
        val groupW = area.width() / labels.size.coerceAtLeast(1)
        val barW = groupW * 0.8f / series.size.coerceAtLeast(1)
        val d3offset = if (is3D) 12f else 0f
        for ((si, s) in series.withIndex()) {
            val p = Paint(); p.color = color(si); p.style = Paint.Style.FILL; p.isAntiAlias = true
            for ((di, entry) in s.data.withIndex()) {
                val barH = area.height() * entry.second / maxV
                val left = area.left + di * groupW + si * barW + groupW * 0.1f
                val right = left + barW; val top = area.bottom - barH
                if (is3D) {
                    val sp = Paint(); sp.color = Color.parseColor("#80000000"); sp.style = Paint.Style.FILL
                    val path = Path()
                    path.moveTo(right, top); path.lineTo(right + d3offset, top - d3offset)
                    path.lineTo(right + d3offset, area.bottom - d3offset); path.lineTo(right, area.bottom); path.close()
                    canvas.drawPath(path, sp)
                    val tp = Paint(); tp.color = Color.parseColor("#40FFFFFF"); tp.style = Paint.Style.FILL
                    val tpath = Path()
                    tpath.moveTo(left, top); tpath.lineTo(left + d3offset, top - d3offset)
                    tpath.lineTo(right + d3offset, top - d3offset); tpath.lineTo(right, top); tpath.close()
                    canvas.drawPath(tpath, tp)
                }
                canvas.drawRect(left, top, right, area.bottom, p)
                val cx = (left + right) / 2f
                dataPoints.add(PinnedLabel("${s.name} ${entry.first}", entry.second, cx, top))
                val vp = Paint(); vp.color = labelColor; vp.textSize = labelFontSize * 0.8f; vp.textAlign = Paint.Align.CENTER; vp.isAntiAlias = true
                canvas.drawText(entry.second.toInt().toString(), cx, top - 4f, vp)
            }
        }
    }

    private fun drawHorizontalBar(canvas: Canvas) {
        val area = chartArea(); val labels = allLabels(); val maxV = maxVal()
        val ap = Paint(); ap.color = Color.DKGRAY; ap.strokeWidth = 2f
        canvas.drawLine(area.left, area.top, area.left, area.bottom, ap)
        canvas.drawLine(area.left, area.bottom, area.right, area.bottom, ap)
        val groupH = area.height() / labels.size.coerceAtLeast(1)
        val barH = groupH * 0.8f / series.size.coerceAtLeast(1)
        val lp = Paint(); lp.color = labelColor; lp.textSize = labelFontSize; lp.textAlign = Paint.Align.RIGHT; lp.isAntiAlias = true
        for ((di, label) in labels.withIndex()) {
            val cy = area.top + (di + 0.5f) * groupH
            canvas.drawText(label, area.left - 8f, cy + labelFontSize / 3f, lp)
        }
        for ((si, s) in series.withIndex()) {
            val p = Paint(); p.color = color(si); p.style = Paint.Style.FILL; p.isAntiAlias = true
            for ((di, entry) in s.data.withIndex()) {
                val barW = area.width() * entry.second / maxV
                val top2 = area.top + di * groupH + si * barH + groupH * 0.1f
                val bottom2 = top2 + barH
                canvas.drawRect(area.left, top2, area.left + barW, bottom2, p)
                dataPoints.add(PinnedLabel("${s.name} ${entry.first}", entry.second, area.left + barW, (top2 + bottom2) / 2f))
            }
        }
    }

    private fun drawStackedBar(canvas: Canvas, pct: Boolean) {
        val area = chartArea(); val labels = allLabels()
        val totals = labels.indices.map { idx -> series.sumOf { s -> s.data.getOrNull(idx)?.second?.toDouble() ?: 0.0 }.toFloat() }
        val maxV = if (pct) 100f else totals.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        drawAxes(canvas, area, maxV, labels)
        val barW = area.width() / labels.size.coerceAtLeast(1) * 0.7f
        for ((di, label) in labels.withIndex()) {
            var yOff = 0f
            val total = totals.getOrElse(di) { 1f }.coerceAtLeast(1f)
            for ((si, s) in series.withIndex()) {
                val rawV = s.data.getOrNull(di)?.second ?: 0f
                val v = if (pct) rawV / total * 100f else rawV
                val barH = area.height() * v / maxV
                val left = area.left + di * area.width() / labels.size + area.width() / labels.size * 0.15f
                val top = area.bottom - yOff - barH
                val p = Paint(); p.color = color(si); p.style = Paint.Style.FILL; p.isAntiAlias = true
                canvas.drawRect(left, top, left + barW, area.bottom - yOff, p)
                dataPoints.add(PinnedLabel("${s.name} $label", rawV, left + barW / 2f, top))
                yOff += barH
            }
        }
    }

    private fun drawLine(canvas: Canvas, smooth: Boolean, fillArea: Boolean) {
        val area = chartArea(); val labels = allLabels(); val maxV = maxVal()
        drawAxes(canvas, area, maxV, labels)
        for ((si, s) in series.withIndex()) {
            if (s.data.isEmpty()) continue
            val pts = s.data.mapIndexed { di, entry ->
                val x = area.left + (di + 0.5f) * area.width() / s.data.size
                val y = area.bottom - area.height() * entry.second / maxV
                Pair(x, y)
            }
            val p = Paint(); p.color = color(si); p.strokeWidth = s.lineWidth; p.style = Paint.Style.STROKE; p.isAntiAlias = true; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            val path = Path()
            if (smooth && pts.size >= 2) {
                path.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) {
                    val cx = (pts[i-1].first + pts[i].first) / 2f
                    path.cubicTo(cx, pts[i-1].second, cx, pts[i].second, pts[i].first, pts[i].second)
                }
            } else {
                path.moveTo(pts[0].first, pts[0].second)
                for (pt in pts.drop(1)) path.lineTo(pt.first, pt.second)
            }
            if (fillArea) {
                val fillPath = Path(path)
                fillPath.lineTo(pts.last().first, area.bottom); fillPath.lineTo(pts.first().first, area.bottom); fillPath.close()
                val fp = Paint(); fp.color = color(si); fp.alpha = 60; fp.style = Paint.Style.FILL; fp.isAntiAlias = true
                canvas.drawPath(fillPath, fp)
            }
            canvas.drawPath(path, p)
            val dp2 = Paint(); dp2.color = color(si); dp2.style = Paint.Style.FILL; dp2.isAntiAlias = true
            for ((di, pt) in pts.withIndex()) {
                canvas.drawCircle(pt.first, pt.second, 9f, dp2)
                dataPoints.add(PinnedLabel("${s.name}: ${s.data[di].first}", s.data[di].second, pt.first, pt.second))
            }
        }
    }

    private fun drawStackedArea(canvas: Canvas) {
        val area = chartArea(); val labels = allLabels()
        val totals = labels.indices.map { idx -> series.sumOf { s -> s.data.getOrNull(idx)?.second?.toDouble() ?: 0.0 }.toFloat() }
        val maxV = totals.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        drawAxes(canvas, area, maxV, labels)
        val cumulative = MutableList(labels.size) { 0f }
        for ((si, s) in series.withIndex()) {
            val pts = s.data.indices.map { di ->
                val x = area.left + (di + 0.5f) * area.width() / labels.size
                val y = area.bottom - area.height() * (cumulative[di] + (s.data.getOrNull(di)?.second ?: 0f)) / maxV
                Pair(x, y)
            }
            val prevPts = s.data.indices.map { di ->
                val x = area.left + (di + 0.5f) * area.width() / labels.size
                val y = area.bottom - area.height() * cumulative[di] / maxV
                Pair(x, y)
            }
            val path = Path(); path.moveTo(prevPts.first().first, prevPts.first().second)
            for (pt in prevPts.drop(1)) path.lineTo(pt.first, pt.second)
            for (pt in pts.reversed()) path.lineTo(pt.first, pt.second)
            path.close()
            val p = Paint(); p.color = color(si); p.alpha = 180; p.style = Paint.Style.FILL; p.isAntiAlias = true
            canvas.drawPath(path, p)
            for ((di, pt) in pts.withIndex()) dataPoints.add(PinnedLabel("${s.name}: ${labels[di]}", s.data.getOrNull(di)?.second ?: 0f, pt.first, pt.second))
            for (di in s.data.indices) cumulative[di] += s.data.getOrNull(di)?.second ?: 0f
        }
    }

    private fun drawPie(canvas: Canvas, donut: Boolean, is3D: Boolean) {
        val area = chartArea()
        val cx = area.centerX(); val cy = area.centerY()
        val radius = minOf(area.width(), area.height()) / 2.5f
        val s = series.firstOrNull() ?: return
        val total = s.data.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
        var startAngle = -90f
        val d3depth = if (is3D) 20f else 0f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        if (is3D) {
            val oval3d = RectF(cx - radius, cy - radius + d3depth, cx + radius, cy + radius + d3depth)
            for ((idx, entry) in s.data.withIndex()) {
                val sweep = 360f * entry.second / total
                if (startAngle + sweep > 0) {
                    val p = Paint(); p.color = color(idx); p.alpha = 180; p.style = Paint.Style.FILL; p.isAntiAlias = true
                    canvas.drawArc(oval3d, startAngle, sweep, true, p)
                }
                startAngle += sweep
            }
            startAngle = -90f
        }
        for ((idx, entry) in s.data.withIndex()) {
            val sweep = 360f * entry.second / total
            val p = Paint(); p.color = color(idx); p.style = Paint.Style.FILL; p.isAntiAlias = true
            canvas.drawArc(oval, startAngle, sweep, true, p)
            if (donut) {
                val dp2 = Paint(); dp2.color = bgColor; dp2.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, radius * 0.5f, dp2)
            }
            val mid = Math.toRadians((startAngle + sweep / 2).toDouble())
            val lx = cx + (radius * 1.2f * cos(mid)).toFloat()
            val ly = cy + (radius * 1.2f * sin(mid)).toFloat()
            val lp = Paint(); lp.color = labelColor; lp.textSize = labelFontSize * 0.9f; lp.isAntiAlias = true
            lp.textAlign = if (lx > cx) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText("${entry.first} ${(entry.second/total*100).toInt()}%", lx, ly, lp)
            // Data point at midpoint of arc
            val dpx = cx + (radius * 0.7f * cos(mid)).toFloat()
            val dpy = cy + (radius * 0.7f * sin(mid)).toFloat()
            dataPoints.add(PinnedLabel(entry.first, entry.second, dpx, dpy))
            startAngle += sweep
        }
    }

    private fun drawScatter(canvas: Canvas) {
        val area = chartArea(); val maxV = maxVal()
        drawAxes(canvas, area, maxV, allLabels())
        for ((si, s) in series.withIndex()) {
            val p = Paint(); p.color = color(si); p.style = Paint.Style.FILL; p.isAntiAlias = true; p.alpha = 180
            for ((di, entry) in s.data.withIndex()) {
                val x = area.left + (di + 0.5f) * area.width() / s.data.size
                val y = area.bottom - area.height() * entry.second / maxV
                canvas.drawCircle(x, y, 12f, p)
                dataPoints.add(PinnedLabel("${s.name}: ${entry.first}", entry.second, x, y))
            }
        }
    }

    private fun drawBubble(canvas: Canvas) {
        val area = chartArea(); val maxV = maxVal()
        drawAxes(canvas, area, maxV, allLabels())
        for ((si, s) in series.withIndex()) {
            val p = Paint(); p.color = color(si); p.style = Paint.Style.FILL; p.isAntiAlias = true; p.alpha = 120
            for ((di, entry) in s.data.withIndex()) {
                val x = area.left + (di + 0.5f) * area.width() / s.data.size
                val y = area.bottom - area.height() * entry.second / maxV
                val r = (entry.second / maxV * 40f).coerceIn(8f, 50f)
                canvas.drawCircle(x, y, r, p)
                dataPoints.add(PinnedLabel("${s.name}: ${entry.first}", entry.second, x, y))
            }
        }
    }

    private fun drawRadar(canvas: Canvas) {
        val labels = allLabels(); val n = labels.size.coerceAtLeast(3); val maxV = maxVal()
        val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) / 3f
        val gp = Paint(); gp.color = gridColor; gp.style = Paint.Style.STROKE; gp.strokeWidth = 1f
        for (ring in 1..4) {
            val rr = r * ring / 4f; val path = Path()
            for (i in 0 until n) {
                val a = Math.toRadians(-90.0 + 360.0 * i / n)
                val x = cx + (rr * cos(a)).toFloat(); val y = cy + (rr * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); canvas.drawPath(path, gp)
        }
        val lp = Paint(); lp.color = labelColor; lp.textSize = labelFontSize; lp.textAlign = Paint.Align.CENTER; lp.isAntiAlias = true
        for (i in 0 until n) {
            val a = Math.toRadians(-90.0 + 360.0 * i / n)
            canvas.drawLine(cx, cy, cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), gp)
            canvas.drawText(labels[i], cx + ((r + 24f) * cos(a)).toFloat(), cy + ((r + 24f) * sin(a)).toFloat(), lp)
        }
        for ((si, s) in series.withIndex()) {
            val p = Paint(); p.color = color(si); p.strokeWidth = s.lineWidth; p.style = Paint.Style.STROKE; p.isAntiAlias = true
            val fp = Paint(); fp.color = color(si); fp.alpha = 60; fp.style = Paint.Style.FILL; fp.isAntiAlias = true
            val path = Path()
            for (i in 0 until n) {
                val v = s.data.getOrNull(i)?.second ?: 0f
                val a = Math.toRadians(-90.0 + 360.0 * i / n)
                val rr = r * v / maxV
                val x = cx + (rr * cos(a)).toFloat(); val y = cy + (rr * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                dataPoints.add(PinnedLabel("${s.name}: ${labels.getOrElse(i) { "" }}", v, x, y))
            }
            path.close(); canvas.drawPath(path, fp); canvas.drawPath(path, p)
        }
    }

    private fun drawHistogram(canvas: Canvas) {
        val area = chartArea(); val s = series.firstOrNull() ?: return
        val values = s.data.map { it.second }
        val bins = 8; val minV = values.minOrNull() ?: 0f; val maxV2 = values.maxOrNull() ?: 1f
        val binSize = (maxV2 - minV) / bins
        val counts = IntArray(bins)
        for (v in values) { val bin = ((v - minV) / binSize).toInt().coerceIn(0, bins - 1); counts[bin]++ }
        val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val ap = Paint(); ap.color = Color.DKGRAY; ap.strokeWidth = 2f
        canvas.drawLine(area.left, area.top, area.left, area.bottom, ap)
        canvas.drawLine(area.left, area.bottom, area.right, area.bottom, ap)
        val bw = area.width() / bins
        for (i in 0 until bins) {
            val bh = area.height() * counts[i] / maxCount
            val p = Paint(); p.color = color(0); p.style = Paint.Style.FILL; p.isAntiAlias = true
            canvas.drawRect(area.left + i * bw + 2f, area.bottom - bh, area.left + (i+1) * bw - 2f, area.bottom, p)
            val lp = Paint(); lp.color = labelColor; lp.textSize = labelFontSize * 0.8f; lp.textAlign = Paint.Align.CENTER; lp.isAntiAlias = true
            canvas.drawText("${(minV + i * binSize).toInt()}", area.left + (i + 0.5f) * bw, area.bottom + labelFontSize + 4f, lp)
            dataPoints.add(PinnedLabel("${(minV + i * binSize).toInt()}-${(minV + (i+1) * binSize).toInt()}", counts[i].toFloat(), area.left + (i + 0.5f) * bw, area.bottom - bh))
        }
    }

    private fun drawWaterfall(canvas: Canvas) {
        val area = chartArea(); val s = series.firstOrNull() ?: return
        val maxV = s.data.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
        val labels = s.data.map { it.first }
        drawAxes(canvas, area, maxV, labels)
        var cumulative = 0f
        val bw = area.width() / s.data.size * 0.7f
        for ((di, entry) in s.data.withIndex()) {
            val x = area.left + di * area.width() / s.data.size + area.width() / s.data.size * 0.15f
            val bottom = area.bottom - area.height() * cumulative / maxV
            val top = area.bottom - area.height() * (cumulative + entry.second) / maxV
            val p = Paint(); p.color = if (entry.second >= 0) color(0) else color(1); p.style = Paint.Style.FILL; p.isAntiAlias = true
            canvas.drawRect(x, top, x + bw, bottom, p)
            dataPoints.add(PinnedLabel(entry.first, entry.second, x + bw / 2f, top))
            cumulative += entry.second
        }
    }

    private fun drawFunnel(canvas: Canvas) {
        val area = chartArea(); val s = series.firstOrNull() ?: return
        val total = s.data.firstOrNull()?.second?.coerceAtLeast(1f) ?: 1f
        val sliceH = area.height() / s.data.size.coerceAtLeast(1)
        for ((idx, entry) in s.data.withIndex()) {
            val ratio = entry.second / total
            val thisW = area.width() * ratio
            val nextRatio = s.data.getOrNull(idx + 1)?.second?.div(total) ?: (ratio * 0.6f)
            val nextW = area.width() * nextRatio
            val top = area.top + idx * sliceH; val bottom = top + sliceH
            val path = Path()
            path.moveTo(area.centerX() - thisW / 2f, top)
            path.lineTo(area.centerX() + thisW / 2f, top)
            path.lineTo(area.centerX() + nextW / 2f, bottom)
            path.lineTo(area.centerX() - nextW / 2f, bottom)
            path.close()
            val p = Paint(); p.color = color(idx); p.style = Paint.Style.FILL; p.isAntiAlias = true
            canvas.drawPath(path, p)
            val lp = Paint(); lp.color = Color.WHITE; lp.textSize = labelFontSize; lp.textAlign = Paint.Align.CENTER; lp.isAntiAlias = true
            canvas.drawText("${entry.first}: ${entry.second.toInt()}", area.centerX(), top + sliceH / 2f + labelFontSize / 3f, lp)
            dataPoints.add(PinnedLabel(entry.first, entry.second, area.centerX(), top + sliceH / 2f))
        }
    }

    private fun drawHeatmap(canvas: Canvas) {
        val area = chartArea()
        val rows = series.size.coerceAtLeast(1)
        val cols = series.firstOrNull()?.data?.size?.coerceAtLeast(1) ?: 1
        val cellW = area.width() / cols; val cellH = area.height() / rows
        val allV = allValues(); val minV = allV.minOrNull() ?: 0f; val maxV2 = allV.maxOrNull()?.coerceAtLeast(minV + 1f) ?: 1f
        for ((si, s) in series.withIndex()) {
            for ((di, entry) in s.data.withIndex()) {
                val t = (entry.second - minV) / (maxV2 - minV)
                val c = Color.HSVToColor(floatArrayOf(240f * (1f - t), 0.8f, 0.9f))
                val p = Paint(); p.color = c; p.style = Paint.Style.FILL
                canvas.drawRect(area.left + di * cellW, area.top + si * cellH, area.left + (di+1) * cellW - 2f, area.top + (si+1) * cellH - 2f, p)
                val lp = Paint(); lp.color = Color.WHITE; lp.textSize = labelFontSize * 0.8f; lp.textAlign = Paint.Align.CENTER; lp.isAntiAlias = true
                canvas.drawText(entry.second.toInt().toString(), area.left + (di + 0.5f) * cellW, area.top + (si + 0.5f) * cellH + labelFontSize * 0.3f, lp)
                dataPoints.add(PinnedLabel("${s.name}: ${entry.first}", entry.second, area.left + (di + 0.5f) * cellW, area.top + (si + 0.5f) * cellH))
            }
        }
    }

    private fun drawGauge(canvas: Canvas) {
        val s = series.firstOrNull() ?: return
        val v = s.data.firstOrNull()?.second ?: 0f
        val maxV = 100f; val cx = width / 2f; val cy = height * 0.65f; val r = minOf(width, height) * 0.38f
        val sweepAngle = 180f * v / maxV
        val bgP = Paint(); bgP.color = Color.LTGRAY; bgP.style = Paint.Style.STROKE; bgP.strokeWidth = 30f; bgP.isAntiAlias = true
        canvas.drawArc(RectF(cx-r, cy-r, cx+r, cy+r), 180f, 180f, false, bgP)
        val fgP = Paint(); fgP.color = color(0); fgP.style = Paint.Style.STROKE; fgP.strokeWidth = 30f; fgP.isAntiAlias = true
        canvas.drawArc(RectF(cx-r, cy-r, cx+r, cy+r), 180f, sweepAngle, false, fgP)
        val tp = Paint(); tp.color = titleColor; tp.textSize = titleFontSize * 1.2f; tp.textAlign = Paint.Align.CENTER; tp.isFakeBoldText = true; tp.isAntiAlias = true
        canvas.drawText("${v.toInt()}%", cx, cy + 20f, tp)
        val lp = Paint(); lp.color = labelColor; lp.textSize = labelFontSize; lp.textAlign = Paint.Align.CENTER; lp.isAntiAlias = true
        canvas.drawText(s.data.firstOrNull()?.first ?: "", cx, cy + labelFontSize + 30f, lp)
        dataPoints.add(PinnedLabel(s.data.firstOrNull()?.first ?: "Value", v, cx, cy))
    }

    private fun drawCandlestick(canvas: Canvas) {
        val area = chartArea(); val s = series.firstOrNull() ?: return
        val maxV = maxVal(); val labels = allLabels()
        drawAxes(canvas, area, maxV, labels)
        val cw = area.width() / s.data.size * 0.6f
        for ((di, entry) in s.data.withIndex()) {
            val x = area.left + (di + 0.5f) * area.width() / s.data.size
            val prev = s.data.getOrNull(di - 1)?.second ?: entry.second
            val open = prev; val close = entry.second
            val high = maxOf(open, close) * 1.05f; val low = minOf(open, close) * 0.95f
            val openY = area.bottom - area.height() * open / maxV
            val closeY = area.bottom - area.height() * close / maxV
            val highY = area.bottom - area.height() * high / maxV
            val lowY = area.bottom - area.height() * low / maxV
            val isUp = close >= open
            val p = Paint(); p.color = if (isUp) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"); p.strokeWidth = 2f; p.isAntiAlias = true
            canvas.drawLine(x, highY, x, lowY, p)
            p.style = Paint.Style.FILL
            canvas.drawRect(x - cw/2f, minOf(openY, closeY), x + cw/2f, maxOf(openY, closeY), p)
            dataPoints.add(PinnedLabel(entry.first, entry.second, x, closeY))
        }
    }

    private fun drawTreemap(canvas: Canvas) {
        val area = chartArea(); val s = series.firstOrNull() ?: return
        val total = s.data.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
        var x = area.left; var y = area.top; var rowH = 0f
        for ((idx, entry) in s.data.withIndex()) {
            val w = area.width() * entry.second / total
            val h = area.height() * entry.second / total * 2f
            if (x + w > area.right) { x = area.left; y += rowH; rowH = 0f }
            rowH = maxOf(rowH, h)
            val p = Paint(); p.color = color(idx); p.style = Paint.Style.FILL; p.isAntiAlias = true
            canvas.drawRect(x + 2f, y + 2f, x + w - 2f, y + h - 2f, p)
            val lp = Paint(); lp.color = Color.WHITE; lp.textSize = labelFontSize * 0.8f; lp.textAlign = Paint.Align.CENTER; lp.isAntiAlias = true
            if (w > 60f && h > 30f) canvas.drawText(entry.first, x + w/2f, y + h/2f, lp)
            dataPoints.add(PinnedLabel(entry.first, entry.second, x + w/2f, y + h/2f))
            x += w
        }
    }

    private fun drawLegend(canvas: Canvas) {
        if (series.size <= 1) return
        val lp = Paint(); lp.textSize = labelFontSize; lp.isAntiAlias = true
        var ly = height - (series.size * (labelFontSize + 8f)) - 10f
        for ((si, s) in series.withIndex()) {
            val p = Paint(); p.color = color(si); p.style = Paint.Style.FILL
            canvas.drawRect(20f, ly, 20f + labelFontSize, ly + labelFontSize, p)
            lp.color = labelColor; lp.textAlign = Paint.Align.LEFT
            canvas.drawText(s.name, 20f + labelFontSize + 8f, ly + labelFontSize, lp)
            ly += labelFontSize + 8f
        }
    }
}
