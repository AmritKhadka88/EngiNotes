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
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var btnFill: Button
    private lateinit var btnShapes: Button
    private lateinit var tvSize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        btnFill = findViewById(R.id.btnFill)
        btnShapes = findViewById(R.id.btnShapes)
        tvSize = findViewById(R.id.tvSize)

        drawingView.onTextTapListener = { x, y -> showTextDialog(x, y) }

        findViewById<Button>(R.id.btnPen).setOnClickListener {
            drawingView.currentTool = Tool.PEN
            updateSizeLabel()
        }
        findViewById<Button>(R.id.btnEraser).setOnClickListener {
            drawingView.currentTool = Tool.ERASER
            updateSizeLabel()
        }
        btnShapes.setOnClickListener {
            val shapes = arrayOf("Line", "Rectangle", "Circle")
            AlertDialog.Builder(this)
                .setTitle("Select Shape")
                .setItems(shapes) { _, index ->
                    drawingView.currentTool = when (index) {
                        0 -> Tool.LINE
                        1 -> Tool.RECTANGLE
                        else -> Tool.CIRCLE
                    }
                    btnShapes.text = "Shapes: " + shapes[index]
                    updateSizeLabel()
                }
                .show()
        }
        findViewById<Button>(R.id.btnText).setOnClickListener {
            drawingView.currentTool = Tool.TEXT
            updateSizeLabel()
        }
        findViewById<Button>(R.id.btnColor).setOnClickListener {
            showColorPicker()
        }
        findViewById<Button>(R.id.btnFill).setOnClickListener {
            drawingView.fillShapes = !drawingView.fillShapes
            btnFill.text = if (drawingView.fillShapes) "Fill: On" else "Fill: Off"
        }
        findViewById<Button>(R.id.btnSize).setOnClickListener {
            showSizePicker()
        }
        findViewById<Button>(R.id.btnPaper).setOnClickListener {
            showPaperPicker()
        }
        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            drawingView.undo()
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            drawingView.clearAll()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveProject()
        }
        findViewById<Button>(R.id.btnLoad).setOnClickListener {
            loadProject()
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportImage()
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
                if (drawingView.currentTool == Tool.ERASER || drawingView.currentTool == Tool.TEXT) {
                    drawingView.currentTool = Tool.PEN
                    btnShapes.text = "Shapes: Line"
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

        val title = when (tool) {
            Tool.ERASER -> "Eraser Size (1 - $maxSize px)"
            Tool.TEXT -> "Default Text Size (1 - $maxSize px)"
            else -> "Pen Thickness (1 - $maxSize px)"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
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

    private fun saveProject() {
        val input = EditText(this)
        input.hint = "File name"

        AlertDialog.Builder(this)
            .setTitle("Save Drawing")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty()) name = "drawing_" + System.currentTimeMillis()
                val file = File(getDrawingsFolder(), name + ".eng")
                file.writeText(drawingView.serialize())
                Toast.makeText(this, "Saved: " + name, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProject() {
        val folder = getDrawingsFolder()
        val files = folder.listFiles()?.filter { it.name.endsWith(".eng") } ?: emptyList()

        if (files.isEmpty()) {
            Toast.makeText(this, "No saved drawings found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = files.map { it.nameWithoutExtension }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Load Drawing")
            .setItems(names) { _, index ->
                val content = files[index].readText()
                drawingView.loadFromString(content)
                Toast.makeText(this, "Loaded: " + names[index], Toast.LENGTH_SHORT).show()
            }
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