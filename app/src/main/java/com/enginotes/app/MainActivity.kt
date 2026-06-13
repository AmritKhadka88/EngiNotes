package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var canvasContainer: FrameLayout
    private lateinit var tvTitle: TextView

    private var currentFileName: String? = null

    private val shapeSymbols = listOf("╱ Line", "▭ Rectangle", "▢ Rounded Rect", "○ Circle", "⬭ Ellipse", "△ Triangle", "◇ Diamond", "➔ Arrow", "★ Star", "⬠ Pentagon", "⬡ Hexagon", "〜 Curve", "✛ Cross")
    private val shapeTools = listOf(Tool.LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE, Tool.TRIANGLE, Tool.DIAMOND, Tool.ARROW, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CURVE, Tool.CROSS)

    private var activeEditText: EditText? = null
    private var activeToolbar: LinearLayout? = null
    private var editingItem: TextItem? = null
    private var editWorldX = 0f
    private var editWorldY = 0f
    private var editRotation = 0f
    private var editColor = Color.BLACK
    private var editSize = 30f

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) insertImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        canvasContainer = findViewById(R.id.canvasContainer)
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

        drawingView.onTextEditRequest = { item, screenX, screenY, worldX, worldY ->
            showInlineTextEditor(item, screenX, screenY, worldX, worldY)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            closeInlineEditor(commit = true)
            finish()
        }

        findViewById<Button>(R.id.btnText).setOnClickListener {
            closeInlineEditor(commit = true)
            drawingView.currentTool = Tool.TEXT
        }

        findViewById<Button>(R.id.btnDraw).setOnClickListener {
            closeInlineEditor(commit = true)
            val options = listOf("👆 Select", "✏ Pen") + shapeSymbols
            AlertDialog.Builder(this)
                .setTitle("Draw")
                .setItems(options.toTypedArray()) { _, index ->
                    drawingView.currentTool = when (index) {
                        0 -> Tool.SELECT
                        1 -> Tool.PEN
                        else -> shapeTools[index - 2]
                    }
                }
                .show()
        }

        findViewById<Button>(R.id.btnTools).setOnClickListener {
            closeInlineEditor(commit = true)
            val options = arrayOf("🧹 Eraser", "🎨 Color", "🪣 Fill", "📏 Size", "📄 Paper Style")
            AlertDialog.Builder(this)
                .setTitle("Tools")
                .setItems(options) { _, index ->
                    when (index) {
                        0 -> {
                            if (drawingView.currentTool == Tool.ERASER) {
                                drawingView.eraserMode = if (drawingView.eraserMode == EraserMode.OBJECT) EraserMode.AREA else EraserMode.OBJECT
                                Toast.makeText(this, if (drawingView.eraserMode == EraserMode.OBJECT) "Object Eraser" else "Area Eraser", Toast.LENGTH_SHORT).show()
                            } else {
                                drawingView.currentTool = Tool.ERASER
                            }
                        }
                        1 -> showColorGridDialog { color ->
                            drawingView.currentColor = color
                            if (drawingView.currentTool == Tool.ERASER) drawingView.currentTool = Tool.PEN
                        }
                        2 -> {
                            drawingView.fillShapes = !drawingView.fillShapes
                            Toast.makeText(this, if (drawingView.fillShapes) "Fill: On" else "Fill: Off", Toast.LENGTH_SHORT).show()
                        }
                        3 -> showSizePicker()
                        else -> showPaperPicker()
                    }
                }
                .show()
        }

        findViewById<Button>(R.id.btnInsert).setOnClickListener {
            closeInlineEditor(commit = true)
            AlertDialog.Builder(this)
                .setTitle("Insert")
                .setItems(arrayOf("🖼 Image from Gallery")) { _, _ ->
                    pickImageLauncher.launch("image/*")
                }
                .show()
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            closeInlineEditor(commit = true)
            drawingView.undo()
        }
        findViewById<Button>(R.id.btnRedo).setOnClickListener {
            closeInlineEditor(commit = true)
            drawingView.redo()
        }

        findViewById<Button>(R.id.btnMenu).setOnClickListener { anchor ->
            closeInlineEditor(commit = true)
            val popup = PopupMenu(this, anchor)
            popup.menu.add("Save")
            popup.menu.add("Save As")
            popup.menu.add("Export as Image")
            popup.menu.add("Clear Canvas")
            if (currentFileName != null) popup.menu.add("Delete This Note")
            popup.menu.add("Exit")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Save" -> saveCurrent()
                    "Save As" -> saveAsNew()
                    "Export as Image" -> exportImage()
                    "Clear Canvas" -> drawingView.clearAll()
                    "Delete This Note" -> deleteCurrentNote()
                    "Exit" -> finish()
                }
                true
            }
            popup.show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- Color grid (MS Word style) ----------

    private fun showColorGridDialog(onPicked: (Int) -> Unit) {
        val grid = GridLayout(this)
        grid.columnCount = 10
        grid.setPadding(dp(10), dp(10), dp(10), dp(10))

        fun addSwatch(color: Int) {
            val swatch = View(this)
            val params = GridLayout.LayoutParams()
            params.width = dp(28)
            params.height = dp(28)
            params.setMargins(dp(2), dp(2), dp(2), dp(2))
            swatch.layoutParams = params
            swatch.setBackgroundColor(color)
            swatch.setOnClickListener { onPicked(color) }
            grid.addView(swatch)
        }

        for (i in 0..9) {
            val v = i / 9f
            addSwatch(Color.HSVToColor(floatArrayOf(0f, 0f, 1f - v)))
        }
        val hues = listOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330)
        val values = listOf(1.0f, 0.85f, 0.7f, 0.5f, 0.3f)
        for (value in values) {
            for (hue in hues) {
                addSwatch(Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.65f, value)))
            }
        }

        val scroll = ScrollView(this)
        scroll.addView(grid)

        AlertDialog.Builder(this)
            .setTitle("Color")
            .setView(scroll)
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

    // ---------- Inline text editing ----------

    private fun toggleStyleOnSelection(editText: EditText, styleFlag: Int) {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) {
            Toast.makeText(this, "Select text first to format", Toast.LENGTH_SHORT).show()
            return
        }
        val from = minOf(start, end); val to = maxOf(start, end)
        val editable = editText.text
        val existing = editable.getSpans(from, to, StyleSpan::class.java)
            .filter { it.style == styleFlag && editable.getSpanStart(it) <= from && editable.getSpanEnd(it) >= to }
        if (existing.isNotEmpty()) {
            for (sp in existing) editable.removeSpan(sp)
        } else {
            editable.setSpan(StyleSpan(styleFlag), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyColorToSelection(editText: EditText, color: Int) {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) {
            editColor = color
            editText.setTextColor(color)
            return
        }
        val from = minOf(start, end); val to = maxOf(start, end)
        editText.text.setSpan(ForegroundColorSpan(color), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        closeInlineEditor(commit = true)

        editingItem = item
        editWorldX = item?.x ?: worldX
        editWorldY = item?.y ?: worldY
        editRotation = item?.rotation ?: 0f
        editColor = item?.color ?: drawingView.currentColor
        editSize = item?.size ?: drawingView.defaultTextSize

        val density = resources.displayMetrics.density
        val sizePx = editSize * drawingView.getScaleFactor()

        val editText = EditText(this)
        val spannable = SpannableStringBuilder(item?.text ?: "")
        item?.spans?.forEach { sp ->
            val s = sp.start.coerceIn(0, spannable.length)
            val e = sp.end.coerceIn(s, spannable.length)
            if (s < e) {
                when (sp.type) {
                    'S' -> spannable.setSpan(StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    'C' -> spannable.setSpan(ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        editText.setText(spannable, TextView.BufferType.SPANNABLE)
        editText.setTextColor(editColor)
        editText.textSize = (sizePx / density).coerceAtLeast(8f)
        editText.setBackgroundColor(Color.TRANSPARENT)
        editText.setPadding(dp(4), dp(4), dp(4), dp(4))
        editText.minWidth = dp(120)
        editText.rotation = editRotation

        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin = screenX.toInt()
        params.topMargin = (screenY - sizePx).toInt().coerceAtLeast(0)
        canvasContainer.addView(editText, params)

        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(0xAA222222.toInt())
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4))

        fun toolBtn(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.textSize = 14f
            b.setTextColor(Color.WHITE)
            b.setBackgroundColor(0x55FFFFFF)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(2), 0, dp(2), 0)
            b.layoutParams = p
            b.setPadding(dp(10), dp(6), dp(10), dp(6))
            b.minWidth = 0
            b.minimumWidth = 0
            b.setOnClickListener { action() }
            toolbar.addView(b)
        }

        toolBtn("B") { toggleStyleOnSelection(editText, Typeface.BOLD) }
        toolBtn("I") { toggleStyleOnSelection(editText, Typeface.ITALIC) }
        toolBtn("🎨") { showColorGridDialog { color -> applyColorToSelection(editText, color) } }
        toolBtn("↺") { editRotation -= 15f; editText.rotation = editRotation }
        toolBtn("↻") { editRotation += 15f; editText.rotation = editRotation }
        toolBtn("✓") { closeInlineEditor(commit = true) }
        toolBtn("🗑") { closeInlineEditor(commit = false, delete = true) }

        val toolbarParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        canvasContainer.addView(toolbar, toolbarParams)

        activeEditText = editText
        activeToolbar = toolbar

        editText.requestFocus()
        editText.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeInlineEditor(commit: Boolean, delete: Boolean = false) {
        val editText = activeEditText ?: return
        val toolbar = activeToolbar

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)

        val text = editText.text.toString()
        val spans = mutableListOf<TextSpanData>()
        val editable = editText.text
        for (span in editable.getSpans(0, editable.length, Any::class.java)) {
            val s = editable.getSpanStart(span)
            val e = editable.getSpanEnd(span)
            if (s < 0 || e < 0 || s >= e) continue
            when (span) {
                is StyleSpan -> spans.add(TextSpanData(s, e, 'S', span.style))
                is ForegroundColorSpan -> spans.add(TextSpanData(s, e, 'C', span.foregroundColor))
            }
        }

        canvasContainer.removeView(editText)
        if (toolbar != null) canvasContainer.removeView(toolbar)

        val item = editingItem
        if (commit && !delete && text.isNotBlank()) {
            if (item != null) {
                item.text = text
                item.color = editColor
                item.size = editSize
                item.rotation = editRotation
                item.spans = spans
                item.isEditing = false
            } else {
                drawingView.addText(text, editWorldX, editWorldY, editSize, editRotation, editColor, spans)
            }
        } else {
            if (item != null) drawingView.removeTextItem(item)
        }
        drawingView.invalidate()

        activeEditText = null
        activeToolbar = null
        editingItem = null
    }

    // ---------- Image insert ----------

    private fun insertImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val imagesFolder = File(filesDir, "images")
            if (!imagesFolder.exists()) imagesFolder.mkdirs()
            val outFile = File(imagesFolder, "img_" + System.currentTimeMillis() + ".png")
            val out = FileOutputStream(outFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.close()

            val maxDim = 300f
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val w: Float; val h: Float
            if (ratio >= 1f) { w = maxDim; h = maxDim / ratio } else { h = maxDim; w = maxDim * ratio }

            drawingView.addImage(outFile.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
        } catch (e: Exception) {
            Toast.makeText(this, "Image insert failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- File ops ----------

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
