package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
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

    // Inline text editor state
    private var activeEditText: EditText? = null
    private var activeToolbar: LinearLayout? = null
    private var editingItem: TextItem? = null
    private var editWorldX = 0f
    private var editWorldY = 0f
    private var editBold = false
    private var editItalic = false
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
            val options = listOf("✏ Pen") + shapeSymbols
            AlertDialog.Builder(this)
                .setTitle("Draw")
                .setItems(options.toTypedArray()) { _, index ->
                    drawingView.currentTool = if (index == 0) Tool.PEN else shapeTools[index - 1]
                }
                .show()
        }

        findViewById<Button>(R.id.btnTools).setOnClickListener {
            closeInlineEditor(commit = true)
            showToolsDialog()
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

    private fun showToolsDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.setPadding(dp(20), dp(20), dp(20), dp(20))

        lateinit var dialog: AlertDialog

        val btnEraserDlg = Button(this)
        btnEraserDlg.text = if (drawingView.eraserMode == EraserMode.OBJECT) "🧹" else "🧼"
        btnEraserDlg.setOnClickListener {
            if (drawingView.currentTool == Tool.ERASER) {
                drawingView.eraserMode = if (drawingView.eraserMode == EraserMode.OBJECT) EraserMode.AREA else EraserMode.OBJECT
                Toast.makeText(this, if (drawingView.eraserMode == EraserMode.OBJECT) "Object Eraser" else "Area Eraser", Toast.LENGTH_SHORT).show()
            } else {
                drawingView.currentTool = Tool.ERASER
            }
            dialog.dismiss()
        }
        container.addView(btnEraserDlg)

        val colorSwatch = Button(this)
        colorSwatch.text = ""
        colorSwatch.setBackgroundColor(drawingView.currentColor)
        val swatchParams = LinearLayout.LayoutParams(dp(40), dp(40))
        swatchParams.setMargins(dp(8), 0, dp(8), 0)
        colorSwatch.layoutParams = swatchParams
        colorSwatch.setOnClickListener {
            dialog.dismiss()
            showColorPicker()
        }
        container.addView(colorSwatch)

        val btnFillDlg = Button(this)
        btnFillDlg.text = "🪣"
        btnFillDlg.setOnClickListener {
            drawingView.fillShapes = !drawingView.fillShapes
            Toast.makeText(this, if (drawingView.fillShapes) "Fill: On" else "Fill: Off", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        container.addView(btnFillDlg)

        val btnSizeDlg = Button(this)
        btnSizeDlg.text = "📏"
        btnSizeDlg.setOnClickListener { dialog.dismiss(); showSizePicker() }
        container.addView(btnSizeDlg)

        val btnPaperDlg = Button(this)
        btnPaperDlg.text = "📄"
        btnPaperDlg.setOnClickListener { dialog.dismiss(); showPaperPicker() }
        container.addView(btnPaperDlg)

        dialog = AlertDialog.Builder(this)
            .setTitle("Tools")
            .setView(container)
            .create()
        dialog.show()
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
                if (drawingView.currentTool == Tool.ERASER) drawingView.currentTool = Tool.PEN
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

    private fun applyStyle(editText: EditText) {
        var style = Typeface.NORMAL
        if (editBold && editItalic) style = Typeface.BOLD_ITALIC
        else if (editBold) style = Typeface.BOLD
        else if (editItalic) style = Typeface.ITALIC
        editText.setTypeface(Typeface.DEFAULT, style)
        editText.rotation = editRotation
    }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        closeInlineEditor(commit = true)

        editingItem = item
        editWorldX = item?.x ?: worldX
        editWorldY = item?.y ?: worldY
        editBold = item?.isBold ?: false
        editItalic = item?.isItalic ?: false
        editRotation = item?.rotation ?: 0f
        editColor = item?.color ?: drawingView.currentColor
        editSize = item?.size ?: drawingView.defaultTextSize

        val density = resources.displayMetrics.density
        val sizePx = editSize * drawingView.getScaleFactor()

        val editText = EditText(this)
        editText.setText(item?.text ?: "")
        editText.setTextColor(editColor)
        editText.textSize = (sizePx / density).coerceAtLeast(8f)
        editText.setBackgroundColor(Color.TRANSPARENT)
        editText.setPadding(dp(4), dp(4), dp(4), dp(4))
        editText.minWidth = dp(120)
        applyStyle(editText)

        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin = screenX.toInt()
        params.topMargin = (screenY - sizePx).toInt().coerceAtLeast(0)
        canvasContainer.addView(editText, params)

        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(0xFF333333.toInt())

        fun toolBtn(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.textSize = 14f
            b.setTextColor(Color.WHITE)
            b.setBackgroundColor(0xFF333333.toInt())
            b.setPadding(dp(10), dp(2), dp(10), dp(2))
            b.setOnClickListener { action() }
            toolbar.addView(b)
        }

        toolBtn("B") { editBold = !editBold; applyStyle(editText) }
        toolBtn("I") { editItalic = !editItalic; applyStyle(editText) }
        toolBtn("🎨") { showColorPickerForText(editText) }
        toolBtn("↺") { editRotation -= 15f; applyStyle(editText) }
        toolBtn("↻") { editRotation += 15f; applyStyle(editText) }
        toolBtn("✓") { closeInlineEditor(commit = true) }
        toolBtn("🗑") { closeInlineEditor(commit = false, delete = true) }

        val toolbarParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        toolbarParams.leftMargin = screenX.toInt().coerceAtLeast(0)
        toolbarParams.topMargin = (screenY - sizePx - dp(48)).toInt().coerceAtLeast(0)
        canvasContainer.addView(toolbar, toolbarParams)

        activeEditText = editText
        activeToolbar = toolbar

        editText.requestFocus()
        editText.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showColorPickerForText(editText: EditText) {
        val colors = listOf(Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.parseColor("#FFA500"), Color.parseColor("#800080"))
        val names = listOf("Black", "Red", "Blue", "Green", "Magenta", "Cyan", "Orange", "Purple")
        AlertDialog.Builder(this)
            .setTitle("Text Color")
            .setItems(names.toTypedArray()) { _, index ->
                editColor = colors[index]
                editText.setTextColor(editColor)
            }
            .show()
    }

    private fun closeInlineEditor(commit: Boolean, delete: Boolean = false) {
        val editText = activeEditText ?: return
        val toolbar = activeToolbar

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)

        val text = editText.text.toString()
        canvasContainer.removeView(editText)
        if (toolbar != null) canvasContainer.removeView(toolbar)

        val item = editingItem
        if (commit && !delete && text.isNotBlank()) {
            if (item != null) {
                item.text = text
                item.color = editColor
                item.size = editSize
                item.rotation = editRotation
                item.isBold = editBold
                item.isItalic = editItalic
                item.isEditing = false
            } else {
                drawingView.addText(text, editWorldX, editWorldY, editSize, editRotation, editColor, editBold, editItalic)
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
