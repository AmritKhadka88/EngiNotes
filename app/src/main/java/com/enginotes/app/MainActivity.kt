package com.enginotes.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var btnFill: Button
    private lateinit var btnShapes: Button
    private lateinit var tvSize: TextView
    private lateinit var tvTitle: TextView

    private var currentFileName: String? = null

    private val shapeSymbols = listOf("╱ Line", "▭ Rectangle", "▢ Rounded Rect", "○ Circle", "⬭ Ellipse", "△ Triangle", "◇ Diamond", "➔ Arrow", "★ Star", "⬠ Pentagon", "⬡ Hexagon", "〜 Curve", "✛ Cross")
    private val shapeTools = listOf(Tool.LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE, Tool.TRIANGLE, Tool.DIAMOND, Tool.ARROW, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CURVE, Tool.CROSS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        btnFill = findViewById(R.id.btnFill)
        btnShapes = findViewById(R.id.btnShapes)
        tvSize = findViewById(R.id.tvSize)
        tvTitle = findViewById(R.id.tvTitle)

        val fileName = intent.getStringExtra("filename")
        if (fileName != null) {
            currentFileName = fileName
            tvTitle.text = fileName
            val file = File(getDrawingsFolder(), fileName + ".eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else {
            tvTitle.text = "New Note"
        }

        drawingView.onTextTapListener = { x, y -> showTextDialog(x, y) }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnPen).setOnClickListener {
            drawingView.currentTool = Tool.PEN
            updateSizeLabel()
        }
        findViewById<Button>(R.id.btnEraser).setOnClickListener {
            drawingView.currentTool = Tool.ERASER
            updateSizeLabel()
        }
        btnShapes.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Shape")
                .setItems(shapeSymbols.toTypedArray()) { _, index ->
                    drawingView.currentTool = shapeTools[index]
                    btnShapes.text = shapeSymbols[index].split(" ")[0]
                    updateSizeLabel()
                }
                .show()
        }
        findViewById<Button>(R.id.btnText).setOnClickListener {
            drawingView.currentTool = Tool.TEXT
            updateSizeLabel()
        }
        findViewById<Button>(R.id.btnColor).setOnClickListener { showColorPicker() }
        findViewById<Button>(R.id.btnFill).setOnClickListener {
            drawingView.fillShapes = !drawingView.fillShapes
            btnFill.setBackgroundColor(if (drawingView.fillShapes) 0xFFB39DDB.toInt() else 0x00000000)
        }
        findViewById<Button>(R.id.btnSize).setOnClickListener { showSizePicker() }
        findViewById<Button>(R.id.btnUndo).setOnClickListener { drawingView.undo() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveCurrent() }

        findViewById<Button>(R.id.btnMenu).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add("Paper Type")
            popup.menu.add("Export as Image")
            popup.menu.add("Save as New")
            popup.menu.add("Clear Canvas")
            if (currentFileName != null) popup.menu.add("Delete This Note")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Paper Type" -> showPaperPicker()
                    "Export as Image" -> exportImage()
                    "Save as New" -> saveAsNew()
                    "Clear Canvas" -> drawingView.clearAll()
                    "Delete This Note" -> deleteCurrentNote()
                }
                true
            }
            popup.show()
        }

        updateSizeLabel()
    }

    private fun updateSizeLabel() {
        val size = when (drawingView.currentTool) {
            Tool.ERASER -> drawingView.eraserSize
            Tool.TEXT -> drawingView.defaultTextSize
            else -> drawingView.currentStrokeWidth
        }
        tvSize.text = size.toInt().toString() + "px"
    }

    private fun showColorPicker() {
        val colors = listOf(
            Color.BLACK, Color.RED, Color.BLUE, Color.GREEN,
            Color.MAGENTA, Color.CYAN,
            Color.parseColor("#FFA500"),
            Color.parseColor("#800080")
        )
        val names = listOf("Black", "Red", "Blue", "Green", "Magenta", "Cyan", "Orange", "Purple")

        AlertDialog.Builder(this)
            .setTitle("Choose Color")
            .setItems(names.toTypedArray()) { _, index ->
                drawingView.currentColor = colors[index]
                if (drawingView.currentTool == Tool.ERASER) {
                    drawingView.currentTool = Tool.PEN
                }
                updateSizeLabel()
            }
            .show()
    }

    private fun showSizePicker() {
        val tool = drawingView.currentTool
        val current: Float
        val maxSize: Int
        when (tool) {
            Tool.ERASER -> { current = drawingView.eraserSize; maxSize = 200 }
            Tool.TEXT -> { current = drawingView.defaultTextSize; maxSize = 150 }
            else -> { current = drawingView.currentStrokeWidth; maxSize = 100 }
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(50, 30, 50, 10)

        val label = TextView(this)
        label.text = "Size: " + current.toInt() + "px"
        label.textSize = 16f
        container.addView(label)

        val seekBar = SeekBar(this)
        seekBar.max = maxSize
        seekBar.progress = current.toInt().coerceIn(1, maxSize)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                val v = if (value < 1) 1 else value
                label.text = "Size: " + v + "px"
                when (tool) {
                    Tool.ERASER -> drawingView.eraserSize = v.toFloat()
                    Tool.TEXT -> drawingView.defaultTextSize = v.toFloat()
                    else -> drawingView.currentStrokeWidth = v.toFloat()
                }
                updateSizeLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(seekBar)

        AlertDialog.Builder(this)
            .setTitle("Size (1 - $maxSize px)")
            .setView(container)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showPaperPicker() {
        val types = arrayOf("Blank White", "Lined Paper", "Graph Paper", "Dot Grid", "Engineering Grid")
        AlertDialog.Builder(this)
            .setTitle("Paper Type")
            .setItems(types) { _, index ->
                drawingView.paperType = when (index) {
                    0 -> PaperType.BLANK
                    1 -> PaperType.LINED
                    2 -> PaperType.GRID
                    3 -> PaperType.DOTS
                    else -> PaperType.ENGINEERING
                }
                drawingView.invalidate()
            }
            .show()
    }

    private fun showTextDialog(worldX: Float, worldY: Float) {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(50, 20, 50, 10)

        val input = EditText(this)
        input.hint = "Enter text"
        container.addView(input)

        val sizeLabel = TextView(this)
        sizeLabel.text = "Font size: " + drawingView.defaultTextSize.toInt() + "px"
        container.addView(sizeLabel)

        var sizeValue = drawingView.defaultTextSize
        val sizeSeek = SeekBar(this)
        sizeSeek.max = 150
        sizeSeek.progress = drawingView.defaultTextSize.toInt().coerceIn(10, 150)
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                val v = if (value < 10) 10 else value
                sizeValue = v.toFloat()
                sizeLabel.text = "Font size: " + v + "px"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(sizeSeek)

        val rotLabel = TextView(this)
        rotLabel.text = "Rotation: 0°"
        container.addView(rotLabel)

        var rotValue = 0f
        val rotSeek = SeekBar(this)
        rotSeek.max = 360
        rotSeek.progress = 0
        rotSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                rotValue = value.toFloat()
                rotLabel.text = "Rotation: " + value + "°"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(rotSeek)

        AlertDialog.Builder(this)
            .setTitle("Add Text")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    drawingView.defaultTextSize = sizeValue
                    drawingView.addText(text, worldX, worldY, sizeValue, rotValue, drawingView.currentColor)
                    updateSizeLabel()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getDrawingsFolder(): File {
        val folder = File(filesDir, "drawings")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun writeCurrentFile() {
        val name = currentFileName ?: return
        File(getDrawingsFolder(), name + ".eng").writeText(drawingView.serialize())
    }

    private fun saveCurrent() {
        if (currentFileName == null) {
            val input = EditText(this)
            input.hint = "Note name"
            AlertDialog.Builder(this)
                .setTitle("Save Note")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    var name = input.text.toString().trim()
                    if (name.isEmpty()) name = "Note_" + System.currentTimeMillis()
                    currentFileName = name
                    tvTitle.text = name
                    writeCurrentFile()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            writeCurrentFile()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsNew() {
        val input = EditText(this)
        input.hint = "New note name"
        AlertDialog.Builder(this)
            .setTitle("Save as New")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty()) name = "Note_" + System.currentTimeMillis()
                currentFileName = name
                tvTitle.text = name
                writeCurrentFile()
                Toast.makeText(this, "Saved as " + name, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrentNote() {
        val name = currentFileName ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Delete \"" + name + "\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                File(getDrawingsFolder(), name + ".eng").delete()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportImage() {
        val bitmap = drawingView.exportBitmap()
        val folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val fileName = "EngiNote_" + System.currentTimeMillis() + ".png"
        val file = File(folder, fileName)
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Toast.makeText(this, "Exported: " + file.absolutePath, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
}
