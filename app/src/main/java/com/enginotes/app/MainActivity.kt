package com.enginotes.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)

        findViewById<Button>(R.id.btnPen).setOnClickListener {
            drawingView.currentTool = Tool.PEN
        }
        findViewById<Button>(R.id.btnEraser).setOnClickListener {
            drawingView.currentTool = Tool.ERASER
        }
        findViewById<Button>(R.id.btnLine).setOnClickListener {
            drawingView.currentTool = Tool.LINE
        }
        findViewById<Button>(R.id.btnRectangle).setOnClickListener {
            drawingView.currentTool = Tool.RECTANGLE
        }
        findViewById<Button>(R.id.btnCircle).setOnClickListener {
            drawingView.currentTool = Tool.CIRCLE
        }
        findViewById<Button>(R.id.btnColor).setOnClickListener {
            showColorPicker()
        }
        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            drawingView.undo()
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            drawingView.clearAll()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveDrawing()
        }
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
                drawingView.currentTool = Tool.PEN
            }
            .show()
    }

    private fun saveDrawing() {
        val bitmap = drawingView.exportBitmap()
        val folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val fileName = "EngiNote_" + System.currentTimeMillis() + ".png"
        val file = File(folder, fileName)
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Toast.makeText(this, "Saved: " + file.absolutePath, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
}
