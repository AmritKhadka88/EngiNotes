package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var canvasContainer: FrameLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvActiveTool: TextView

    private var currentFileName: String? = null
    private var lastSavedContent: String = ""
    private val PT_TO_PX = 1.333f

    private val shapeSymbols = listOf("╱ Line", "▭ Rectangle", "▢ Rounded Rect", "○ Circle", "⬭ Ellipse", "△ Triangle", "◇ Diamond", "➔ Arrow", "★ Star", "⬠ Pentagon", "⬡ Hexagon", "〜 Curve", "✛ Cross")
    private val shapeTools = listOf(Tool.LINE, Tool.RECTANGLE, Tool.ROUNDED_RECT, Tool.CIRCLE, Tool.ELLIPSE, Tool.TRIANGLE, Tool.DIAMOND, Tool.ARROW, Tool.STAR, Tool.PENTAGON, Tool.HEXAGON, Tool.CURVE, Tool.CROSS)

    private var activeEditText: EditText? = null
    private var activeToolbar: LinearLayout? = null
    private var editingItem: TextItem? = null
    private var editWorldX = 0f
    private var editWorldY = 0f
    private var editRotation = 0f
    private var editColor = Color.BLACK
    private var editSize = 12f * 1.333f
    private var pendingBold = false
    private var pendingItalic = false
    private var pendingUnderline = false
    private var pendingHighlight: Int? = null
    private var cameraImageFile: File? = null
    private var activeToolbarButton: Button? = null

    private val ACTIVE_BTN_COLOR = 0x552196F3.toInt()
    private val PRESS_BTN_COLOR = 0x992196F3.toInt()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) insertImage(uri)
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraImageFile?.let { addImageFromFile(it) }
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
            val file = File(getDrawingsFolder(), "$fileName.eng")
            if (file.exists()) drawingView.loadFromString(file.readText())
        } else {
            tvTitle.text = "New Note"
        }
        lastSavedContent = drawingView.serialize()
        drawingView.arcDivisions = getPrefs().getInt("arc_divisions", 3)

        tvActiveTool = TextView(this)
        tvActiveTool.textSize = 9f
        tvActiveTool.setTextColor(Color.parseColor("#CCFFFFFF"))
        tvActiveTool.setBackgroundColor(Color.parseColor("#55000000"))
        tvActiveTool.setPadding(dp(3), 0, dp(3), dp(1))
        tvActiveTool.text = "Select"
        val ip = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        ip.gravity = Gravity.TOP or Gravity.END
        ip.topMargin = dp(28); ip.rightMargin = dp(4)
        canvasContainer.addView(tvActiveTool, ip)

        drawingView.onTextEditRequest = { item, screenX, screenY, worldX, worldY ->
            showInlineTextEditor(item, screenX, screenY, worldX, worldY)
        }

        for (id in listOf(R.id.btnBack, R.id.btnMenu, R.id.btnText, R.id.btnDraw, R.id.btnTools, R.id.btnInsert, R.id.btnUndo, R.id.btnRedo))
            addPressEffect(findViewById(id))

        findViewById<Button>(R.id.btnBack).setOnClickListener { confirmThenExit() }

        findViewById<Button>(R.id.btnText).setOnClickListener {
            // Don't close and reopen if already editing this same item
        if (editingItem != null && editingItem == item) return
        closeInlineEditor(commit = true)
            setActiveTool(it as Button, Tool.TEXT, "Text")
        }

        findViewById<Button>(R.id.btnDraw).setOnClickListener { btn ->
            closeInlineEditor(commit = true)
            val options = listOf("👆 Select", "🪣 Fill", "✏ Pen", "⌇ Arc", "🔲 AutoSelect") + shapeSymbols
            AlertDialog.Builder(this).setTitle("Draw")
                .setItems(options.toTypedArray()) { _, i ->
                    when (i) {
                        0 -> setActiveTool(btn as Button, Tool.SELECT, "Select")
                        1 -> { showColorGridDialog { c -> drawingView.fillColor = c }; setActiveTool(btn as Button, Tool.FILL, "Fill") }
                        2 -> { setActiveTool(btn as Button, Tool.PEN, "Pen"); collapseToolbar() }
                        3 -> setActiveTool(btn as Button, Tool.ARC, "Arc")
                        4 -> { showAutoSelectModeDialog(); setActiveToolbarBtn(btn as Button) }
                        else -> setActiveTool(btn as Button, shapeTools[i - 5], shapeSymbols[i - 5].take(2))
                    }
                }.show()
        }

        findViewById<Button>(R.id.btnTools).setOnClickListener { btn ->
            closeInlineEditor(commit = true)
            AlertDialog.Builder(this).setTitle("Tools")
                .setItems(arrayOf("🧹 Eraser", "🎨 Color", "🪣 Fill Shapes", "📏 Size")) { _, i ->
                    when (i) {
                        0 -> if (drawingView.currentTool == Tool.ERASER) {
                            drawingView.eraserMode = if (drawingView.eraserMode == EraserMode.OBJECT) EraserMode.AREA else EraserMode.OBJECT
                            Toast.makeText(this, if (drawingView.eraserMode == EraserMode.OBJECT) "Object Eraser" else "Area Eraser", Toast.LENGTH_SHORT).show()
                        } else setActiveTool(btn as Button, Tool.ERASER, "Eraser")
                        1 -> showColorGridDialog { c -> drawingView.currentColor = c; if (drawingView.currentTool == Tool.ERASER) drawingView.currentTool = Tool.PEN }
                        2 -> { drawingView.fillShapes = !drawingView.fillShapes; Toast.makeText(this, if (drawingView.fillShapes) "Fill: On" else "Fill: Off", Toast.LENGTH_SHORT).show() }
                        3 -> showSizePicker()
                    }
                }.show()
        }

        findViewById<Button>(R.id.btnInsert).setOnClickListener {
            closeInlineEditor(commit = true)
            AlertDialog.Builder(this).setTitle("Insert")
                .setItems(arrayOf("🖼 Image from Gallery", "📷 Take Photo")) { _, i ->
                    if (i == 0) pickImageLauncher.launch("image/*") else launchCamera()
                }.show()
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener { closeInlineEditor(commit = true); drawingView.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { closeInlineEditor(commit = true); drawingView.redo() }

        findViewById<Button>(R.id.btnExpand).setOnClickListener { expandToolbar() }
        findViewById<Button>(R.id.btnQuickColor).setOnClickListener { showColorGridDialog { c -> drawingView.currentColor = c } }
        findViewById<Button>(R.id.btnQuickSize).setOnClickListener { showSizePicker() }
        findViewById<Button>(R.id.btnQuickEraser).setOnClickListener { setActiveTool(null, Tool.ERASER, "Eraser") }
        findViewById<Button>(R.id.btnQuickFill)?.setOnClickListener {
            showColorGridDialog { c -> drawingView.fillColor = c }
            setActiveTool(null, Tool.FILL, "Fill")
        }

        findViewById<Button>(R.id.btnMenu).setOnClickListener { anchor ->
            closeInlineEditor(commit = true)
            val popup = PopupMenu(this, anchor)
            listOf("Save", "Save As", "Export as Image", "Clear Canvas").forEach { popup.menu.add(it) }
            if (currentFileName != null) popup.menu.add("Delete This Note")
            listOf("Settings", "Exit").forEach { popup.menu.add(it) }
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Save" -> saveCurrent()
                    "Save As" -> saveAsNew()
                    "Export as Image" -> exportImage()
                    "Clear Canvas" -> confirmThenClear()
                    "Delete This Note" -> deleteCurrentNote()
                    "Settings" -> showSettingsDialog()
                    "Exit" -> confirmThenExit()
                }
                true
            }
            popup.show()
        }

        setActiveTool(null, Tool.SELECT, "Select")

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmThenExit() }
        })
    }
    private fun setActiveTool(btn: Button?, tool: Tool, label: String) {
        drawingView.currentTool = tool
        tvActiveTool.text = label
        setActiveToolbarBtn(btn)
    }

    private fun setActiveToolbarBtn(btn: Button?) {
        activeToolbarButton?.setBackgroundColor(Color.TRANSPARENT)
        activeToolbarButton = btn
        btn?.setBackgroundColor(ACTIVE_BTN_COLOR)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun addPressEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.setBackgroundColor(PRESS_BTN_COLOR)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    v.setBackgroundColor(if (v == activeToolbarButton) ACTIVE_BTN_COLOR else Color.TRANSPARENT)
            }
            false
        }
    }

    private fun collapseToolbar() {
        findViewById<View>(R.id.toolbarScroll).visibility = View.GONE
        findViewById<View>(R.id.collapsedBar).visibility = View.VISIBLE
    }

    private fun expandToolbar() {
        findViewById<View>(R.id.toolbarScroll).visibility = View.VISIBLE
        findViewById<View>(R.id.collapsedBar).visibility = View.GONE
    }

    private fun getPrefs() = getSharedPreferences("enginotes_prefs", Context.MODE_PRIVATE)

    private fun showSettingsDialog() {
        val prefs = getPrefs()
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(8), dp(20), dp(8))

        fun divider() {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            lp.setMargins(0, dp(10), 0, dp(4)); v.layoutParams = lp
            v.setBackgroundColor(Color.LTGRAY); container.addView(v)
        }

        fun header(text: String) {
            val tv = TextView(this); tv.text = text; tv.textSize = 11f
            tv.setTextColor(Color.parseColor("#7B61FF"))
            tv.setPadding(0, dp(10), 0, dp(2))
            tv.typeface = Typeface.DEFAULT_BOLD; container.addView(tv)
        }

        header("GENERAL")
        val confirmPref = prefs.getBoolean("confirm_exit_clear", true)
        val checkbox = CheckBox(this)
        checkbox.text = "Confirm before exit or clear canvas"
        checkbox.isChecked = confirmPref
        container.addView(checkbox)

        divider(); header("DRAWING")

        val arcDivPref = prefs.getInt("arc_divisions", 3)
        val arcRow = LinearLayout(this); arcRow.orientation = LinearLayout.HORIZONTAL
        arcRow.gravity = Gravity.CENTER_VERTICAL; arcRow.setPadding(0, dp(8), 0, dp(8))
        val arcLbl = TextView(this); arcLbl.text = "Arc divisions"; arcLbl.textSize = 15f
        arcLbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val arcInput = EditText(this); arcInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        arcInput.setText(arcDivPref.toString())
        arcInput.layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
        arcInput.gravity = Gravity.CENTER
        arcRow.addView(arcLbl); arcRow.addView(arcInput); container.addView(arcRow)

        val eraserTypes = arrayOf("Object Eraser (whole strokes)", "Area Eraser (partial strokes)")
        var selectedEraserIdx = if (drawingView.eraserMode == EraserMode.OBJECT) 0 else 1
        val eraserLbl = TextView(this); eraserLbl.textSize = 15f
        eraserLbl.setTextColor(Color.parseColor("#1565C0"))
        eraserLbl.setPadding(0, dp(10), 0, dp(10))
        fun refreshEraserLbl() { eraserLbl.text = "Eraser: ${eraserTypes[selectedEraserIdx].substringBefore(" (")}  (tap)" }
        refreshEraserLbl()
        eraserLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Eraser Type").setItems(eraserTypes) { _, i ->
                selectedEraserIdx = i; refreshEraserLbl()
            }.show()
        }
        container.addView(eraserLbl)

        divider(); header("PAPER")

        val paperTypes = arrayOf("Blank White", "Blank Coloured", "Lined", "Graph Grid", "Dot Grid", "Engineering Grid")
        val paperValues = arrayOf(PaperType.BLANK, PaperType.BLANK_COLORED, PaperType.LINED, PaperType.GRID, PaperType.DOTS, PaperType.ENGINEERING)
        val paperLbl = TextView(this); paperLbl.textSize = 15f
        paperLbl.setTextColor(Color.parseColor("#1565C0"))
        paperLbl.setPadding(0, dp(10), 0, dp(10))
        fun refreshPaperLbl() { paperLbl.text = "Style: ${paperTypes[paperValues.indexOf(drawingView.paperType).coerceAtLeast(0)]}  (tap)" }
        refreshPaperLbl()
        paperLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Paper Style").setItems(paperTypes) { _, i ->
                if (paperValues[i] == PaperType.BLANK_COLORED) {
                    showColorGridDialog { c -> drawingView.paperColor = c; drawingView.paperType = PaperType.BLANK_COLORED; drawingView.invalidate(); refreshPaperLbl() }
                } else { drawingView.paperType = paperValues[i]; drawingView.invalidate(); refreshPaperLbl() }
            }.show()
        }
        container.addView(paperLbl)

        divider(); header("PAGE SETUP")

        val modeLbl = TextView(this); val sizeLbl = TextView(this); val orientLbl = TextView(this)
        fun refreshPageLbls() {
            modeLbl.text = "Canvas: ${when (drawingView.canvasMode) { CanvasMode.INFINITE -> "Infinite"; CanvasMode.FIXED -> "Fixed Page"; else -> "Paginated" }}  (tap)"
            sizeLbl.text = "Size: ${drawingView.paperSize.name}  (tap)"
            orientLbl.text = "Orientation: ${if (drawingView.pageOrientation == Orientation.PORTRAIT) "Portrait" else "Landscape"}  (tap)"
        }
        refreshPageLbls()
        for (lbl in listOf(modeLbl, sizeLbl, orientLbl)) {
            lbl.textSize = 15f; lbl.setTextColor(Color.parseColor("#1565C0"))
            lbl.setPadding(0, dp(8), 0, dp(8)); container.addView(lbl)
        }
        modeLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Canvas Mode")
                .setItems(arrayOf("Infinite Canvas", "Fixed Page", "Paginated")) { _, i ->
                    drawingView.canvasMode = when (i) { 0 -> CanvasMode.INFINITE; 1 -> CanvasMode.FIXED; else -> CanvasMode.PAGINATED }
                    drawingView.invalidate(); refreshPageLbls()
                }.show()
        }
        sizeLbl.setOnClickListener {
            val sizes = PaperSizeOption.values()
            AlertDialog.Builder(this).setTitle("Paper Size")
                .setItems(sizes.map { it.name }.toTypedArray()) { _, i ->
                    drawingView.paperSize = sizes[i]; drawingView.invalidate(); refreshPageLbls()
                }.show()
        }
        orientLbl.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Orientation")
                .setItems(arrayOf("Portrait", "Landscape")) { _, i ->
                    drawingView.pageOrientation = if (i == 0) Orientation.PORTRAIT else Orientation.LANDSCAPE
                    drawingView.invalidate(); refreshPageLbls()
                }.show()
        }

        val scroll = ScrollView(this); scroll.addView(container)
        AlertDialog.Builder(this).setTitle("⚙ Settings").setView(scroll)
            .setPositiveButton("Done") { _, _ ->
                prefs.edit().putBoolean("confirm_exit_clear", checkbox.isChecked).apply()
                val n = (arcInput.text.toString().toIntOrNull() ?: 3).coerceIn(2, 12)
                prefs.edit().putInt("arc_divisions", n).apply()
                drawingView.arcDivisions = n
                drawingView.eraserMode = if (selectedEraserIdx == 0) EraserMode.OBJECT else EraserMode.AREA
            }.show()
    }

    private fun confirmThenExit() {
        closeInlineEditor(commit = true)
        if (getPrefs().getBoolean("confirm_exit_clear", true) && drawingView.serialize() != lastSavedContent && drawingView.hasContent()) {
            AlertDialog.Builder(this).setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Save before leaving?")
                .setPositiveButton("Save") { _, _ -> saveCurrent(); finish() }
                .setNeutralButton("Don't Save") { _, _ -> finish() }
                .setNegativeButton("Cancel", null)
                .show()
        } else finish()
    }

    private fun confirmThenClear() {
        if (getPrefs().getBoolean("confirm_exit_clear", true) && drawingView.hasContent()) {
            AlertDialog.Builder(this).setTitle("Clear Canvas?").setMessage("This will remove everything.")
                .setPositiveButton("Clear") { _, _ -> drawingView.clearAll() }.setNegativeButton("Cancel", null).show()
        } else drawingView.clearAll()
    }

    private fun showColorGridDialog(onPicked: (Int) -> Unit) {
        val grid = GridLayout(this); grid.columnCount = 10
        grid.setPadding(dp(10), dp(10), dp(10), dp(10))
        lateinit var dialog: AlertDialog
        var previewPopup: android.widget.PopupWindow? = null

        fun addSwatch(color: Int) {
            val swatch = View(this)
            val p = GridLayout.LayoutParams()
            p.width = dp(28); p.height = dp(28); p.setMargins(dp(2), dp(2), dp(2), dp(2))
            swatch.layoutParams = p; swatch.setBackgroundColor(color)
            swatch.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        val prev = View(this); prev.setBackgroundColor(color)
                        val pw = android.widget.PopupWindow(prev, dp(40), dp(40))
                        pw.showAsDropDown(v, 0, -dp(70)); previewPopup = pw
                    }
                    android.view.MotionEvent.ACTION_UP -> { previewPopup?.dismiss(); previewPopup = null; onPicked(color); dialog.dismiss() }
                    android.view.MotionEvent.ACTION_CANCEL -> { previewPopup?.dismiss(); previewPopup = null }
                }
                true
            }
            grid.addView(swatch)
        }

        for (i in 0..9) addSwatch(Color.HSVToColor(floatArrayOf(0f, 0f, 1f - i / 9f)))
        for (value in listOf(1.0f, 0.85f, 0.7f, 0.5f, 0.3f))
            for (hue in listOf(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330))
                addSwatch(Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.65f, value)))

        val scroll = ScrollView(this); scroll.addView(grid)
        dialog = AlertDialog.Builder(this).setTitle("Color").setView(scroll).create()
        dialog.show()
    }

    private fun showSizePicker() {
        val tool = drawingView.currentTool
        val current: Float; val maxSize: Int; val label: String
        when (tool) {
            Tool.ERASER -> { current = drawingView.eraserSize; maxSize = 200; label = "Eraser Size" }
            Tool.TEXT -> { current = drawingView.defaultTextSize / PT_TO_PX; maxSize = 144; label = "Font Size (pt)" }
            else -> { current = drawingView.currentStrokeWidth; maxSize = 100; label = "Stroke Width" }
        }
        val container = LinearLayout(this); container.orientation = LinearLayout.VERTICAL
        container.setPadding(50, 30, 50, 10)
        val tv = TextView(this); tv.text = "$label: ${current.toInt()}"; tv.textSize = 16f
        container.addView(tv)
        val seekBar = SeekBar(this); seekBar.max = maxSize
        seekBar.progress = current.toInt().coerceIn(1, maxSize)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                val v = value.coerceAtLeast(1); tv.text = "$label: $v"
                when (tool) {
                    Tool.ERASER -> drawingView.eraserSize = v.toFloat()
                    Tool.TEXT -> drawingView.defaultTextSize = v * PT_TO_PX
                    else -> drawingView.currentStrokeWidth = v.toFloat()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(seekBar)
        AlertDialog.Builder(this).setTitle(label).setView(container).setPositiveButton("Done", null).show()
    }

    private fun showAutoSelectModeDialog() {
        val modes = arrayOf("▭ Rectangle – Whole", "▭ Rectangle – Divided", "✏ Freeform – Whole", "✏ Freeform – Divided")
        AlertDialog.Builder(this).setTitle("AutoSelect Mode").setItems(modes) { _, i ->
            when (i) {
                0 -> { drawingView.autoSelectShape = AutoSelectShape.RECTANGLE; drawingView.autoSelectDivide = AutoSelectDivide.WHOLE }
                1 -> { drawingView.autoSelectShape = AutoSelectShape.RECTANGLE; drawingView.autoSelectDivide = AutoSelectDivide.DIVIDED }
                2 -> { drawingView.autoSelectShape = AutoSelectShape.FREEFORM; drawingView.autoSelectDivide = AutoSelectDivide.WHOLE }
                else -> { drawingView.autoSelectShape = AutoSelectShape.FREEFORM; drawingView.autoSelectDivide = AutoSelectDivide.DIVIDED }
            }
            drawingView.currentTool = Tool.AUTOSELECT
            tvActiveTool.text = "AutoSel"
            Toast.makeText(this, "Draw a region to select", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun toggleStyleOnSelection(editText: EditText, styleFlag: Int) {
        val start = editText.selectionStart; val end = editText.selectionEnd
        if (start == end) { Toast.makeText(this, "Select text first", Toast.LENGTH_SHORT).show(); return }
        val from = minOf(start, end); val to = maxOf(start, end)
        val editable = editText.text
        val existing = editable.getSpans(from, to, StyleSpan::class.java)
            .filter { it.style == styleFlag && editable.getSpanStart(it) <= from && editable.getSpanEnd(it) >= to }
        if (existing.isNotEmpty()) for (sp in existing) editable.removeSpan(sp)
        else editable.setSpan(StyleSpan(styleFlag), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyColorToSelection(editText: EditText, color: Int) {
        val start = editText.selectionStart; val end = editText.selectionEnd
        if (start == end) { editColor = color; editText.setTextColor(color); return }
        editText.text.setSpan(ForegroundColorSpan(color), minOf(start, end), maxOf(start, end), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun toggleUnderlineOnSelection(editText: EditText) {
        val start = editText.selectionStart; val end = editText.selectionEnd
        val from = minOf(start, end); val to = maxOf(start, end)
        val editable = editText.text
        val existing = editable.getSpans(from, to, UnderlineSpan::class.java)
            .filter { editable.getSpanStart(it) <= from && editable.getSpanEnd(it) >= to }
        if (existing.isNotEmpty()) for (sp in existing) editable.removeSpan(sp)
        else editable.setSpan(UnderlineSpan(), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun applyHighlightToSelection(editText: EditText, color: Int) {
        editText.text.setSpan(BackgroundColorSpan(color),
            minOf(editText.selectionStart, editText.selectionEnd),
            maxOf(editText.selectionStart, editText.selectionEnd),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showInlineTextEditor(item: TextItem?, screenX: Float, screenY: Float, worldX: Float, worldY: Float) {
        closeInlineEditor(commit = true)
        pendingBold = false; pendingItalic = false; pendingUnderline = false; pendingHighlight = null
        editingItem = item
        editWorldX = item?.x ?: worldX; editWorldY = item?.y ?: worldY
        editRotation = item?.rotation ?: 0f
        editColor = item?.color ?: drawingView.currentColor
        editSize = item?.size ?: drawingView.defaultTextSize

        val density = resources.displayMetrics.density
        val screenSizePx = editSize * drawingView.getScaleFactor()

        val editText = EditText(this)
        val spannable = SpannableStringBuilder(item?.text ?: "")
        item?.spans?.forEach { sp ->
            val s = sp.start.coerceIn(0, spannable.length)
            val e = sp.end.coerceIn(s, spannable.length)
            if (s < e) when (sp.type) {
                'S' -> spannable.setSpan(StyleSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'C' -> spannable.setSpan(ForegroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'U' -> spannable.setSpan(UnderlineSpan(), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                'H' -> spannable.setSpan(BackgroundColorSpan(sp.value), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        editText.setText(spannable, TextView.BufferType.SPANNABLE)
        editText.setTextColor(editColor)
        editText.textSize = (screenSizePx / density).coerceAtLeast(8f)
        editText.setBackgroundColor(Color.TRANSPARENT)
        editText.setPadding(dp(4), dp(4), dp(4), dp(4))
        editText.minWidth = dp(120); editText.rotation = editRotation

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0) {
                    val e = editText.text; val end = start + count
                    if (pendingBold) e.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (pendingItalic) e.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (pendingUnderline) e.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    pendingHighlight?.let { e.setSpan(BackgroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin = screenX.toInt()
        params.topMargin = (screenY - screenSizePx).toInt().coerceAtLeast(0)
        canvasContainer.addView(editText, params)

        val toolbar = LinearLayout(this); toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#AA222222"))
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4))

        fun toolBtn(label: String, action: (Button) -> Unit): Button {
            val b = Button(this); b.text = label; b.textSize = 14f; b.setTextColor(Color.WHITE)
            b.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(dp(2), 0, dp(2), 0); b.layoutParams = p
            b.setPadding(dp(10), dp(6), dp(10), dp(6)); b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { action(b) }; toolbar.addView(b); return b
        }

        val activeTextBtnColor = Color.parseColor("#2196F3")
        val inactiveTextBtnColor = Color.parseColor("#55FFFFFF")

        toolBtn("B") { btn ->
            if (editText.selectionStart != editText.selectionEnd) toggleStyleOnSelection(editText, Typeface.BOLD)
            else { pendingBold = !pendingBold; btn.setBackgroundColor(if (pendingBold) activeTextBtnColor else inactiveTextBtnColor) }
        }
        toolBtn("I") { btn ->
            if (editText.selectionStart != editText.selectionEnd) toggleStyleOnSelection(editText, Typeface.ITALIC)
            else { pendingItalic = !pendingItalic; btn.setBackgroundColor(if (pendingItalic) activeTextBtnColor else inactiveTextBtnColor) }
        }
        toolBtn("U") { btn ->
            if (editText.selectionStart != editText.selectionEnd) toggleUnderlineOnSelection(editText)
            else { pendingUnderline = !pendingUnderline; btn.setBackgroundColor(if (pendingUnderline) activeTextBtnColor else inactiveTextBtnColor) }
        }
        toolBtn("🖍") { btn ->
            showColorGridDialog { color ->
                if (editText.selectionStart != editText.selectionEnd) applyHighlightToSelection(editText, color)
                else { pendingHighlight = color; btn.setBackgroundColor(color) }
            }
        }
        toolBtn("🎨") { showColorGridDialog { color -> applyColorToSelection(editText, color) } }

        val ptSize = (editSize / PT_TO_PX).toInt().coerceIn(1, 144)
        toolBtn(ptSize.toString()) { btn ->
            AlertDialog.Builder(this).setTitle("Font Size (pt)")
                .setItems((1..144).map { it.toString() }.toTypedArray()) { _, idx ->
                    val pt = idx + 1; editSize = pt * PT_TO_PX
                    editText.textSize = (editSize * drawingView.getScaleFactor() / density).coerceAtLeast(8f)
                    btn.text = pt.toString()
                }.show()
        }
        toolBtn("↺") { editRotation -= 15f; editText.rotation = editRotation }
        toolBtn("↻") { editRotation += 15f; editText.rotation = editRotation }
        toolBtn("✓") { closeInlineEditor(commit = true) }
        toolBtn("🗑") { closeInlineEditor(commit = false, delete = true) }

        val tp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        canvasContainer.addView(toolbar, tp)
        activeEditText = editText; activeToolbar = toolbar
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
            val s = editable.getSpanStart(span); val e = editable.getSpanEnd(span)
            if (s < 0 || e < 0 || s >= e) continue
            when (span) {
                is StyleSpan -> spans.add(TextSpanData(s, e, 'S', span.style))
                is ForegroundColorSpan -> spans.add(TextSpanData(s, e, 'C', span.foregroundColor))
                is UnderlineSpan -> spans.add(TextSpanData(s, e, 'U', 0))
                is BackgroundColorSpan -> spans.add(TextSpanData(s, e, 'H', span.backgroundColor))
            }
        }
        canvasContainer.removeView(editText)
        if (toolbar != null) canvasContainer.removeView(toolbar)

        val item = editingItem
        if (commit && !delete && text.isNotBlank()) {
            drawingView.defaultTextSize = editSize  // persist chosen size
            if (item != null) {
                item.text = text; item.color = editColor; item.size = editSize
                item.rotation = editRotation; item.spans = spans; item.isEditing = false
            } else {
                drawingView.addText(text, editWorldX, editWorldY, editSize, editRotation, editColor, spans)
            }
        } else { if (item != null) drawingView.removeTextItem(item) }
        drawingView.invalidate()
        activeEditText = null; activeToolbar = null; editingItem = null
    }

    private fun launchCamera() {
        val folder = File(filesDir, "images"); if (!folder.exists()) folder.mkdirs()
        val photoFile = File(folder, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageFile = photoFile
        takePictureLauncher.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile))
    }

    private fun addImageFromFile(file: File) {
        try {
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return
            val ratio = bmp.width.toFloat() / bmp.height
            val w = if (ratio >= 1f) 300f else 300f * ratio
            val h = if (ratio >= 1f) 300f / ratio else 300f
            drawingView.addImage(file.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
        } catch (e: Exception) { Toast.makeText(this, "Photo failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun insertImage(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return
            val bmp = android.graphics.BitmapFactory.decodeStream(stream); stream.close()
            val folder = File(filesDir, "images"); if (!folder.exists()) folder.mkdirs()
            val out = File(folder, "img_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            val ratio = bmp.width.toFloat() / bmp.height
            val w = if (ratio >= 1f) 300f else 300f * ratio
            val h = if (ratio >= 1f) 300f / ratio else 300f
            drawingView.addImage(out.absolutePath, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), w, h)
        } catch (e: Exception) { Toast.makeText(this, "Image failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun getDrawingsFolder(): File {
        val f = File(filesDir, "drawings"); if (!f.exists()) f.mkdirs(); return f
    }

    private fun writeCurrentFile() {
        val name = currentFileName ?: return
        File(getDrawingsFolder(), "$name.eng").writeText(drawingView.serialize())
        lastSavedContent = drawingView.serialize()
    }

    private fun saveCurrent() {
        if (currentFileName == null) {
            val input = EditText(this); input.hint = "Note name"
            AlertDialog.Builder(this).setTitle("Save Note").setView(input)
                .setPositiveButton("Save") { _, _ ->
                    var name = input.text.toString().trim().ifEmpty { "Note_${System.currentTimeMillis()}" }
                    currentFileName = name; tvTitle.text = name; writeCurrentFile()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null).show()
        } else { writeCurrentFile(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
    }

    private fun saveAsNew() {
        val input = EditText(this); input.hint = "New note name"
        AlertDialog.Builder(this).setTitle("Save as New").setView(input)
            .setPositiveButton("Save") { _, _ ->
                var name = input.text.toString().trim().ifEmpty { "Note_${System.currentTimeMillis()}" }
                currentFileName = name; tvTitle.text = name; writeCurrentFile()
                Toast.makeText(this, "Saved as $name", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteCurrentNote() {
        val name = currentFileName ?: return
        AlertDialog.Builder(this).setTitle("Delete Note").setMessage("Delete \"$name\"? Cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> File(getDrawingsFolder(), "$name.eng").delete(); finish() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun exportImage() {
        val bmp = drawingView.exportBitmap()
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "EngiNote_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "Exported: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
