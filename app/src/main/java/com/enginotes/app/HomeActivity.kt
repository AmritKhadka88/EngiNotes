package com.enginotes.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        container = findViewById(R.id.notesContainer)

        findViewById<Button>(R.id.btnNewNote).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadFileList()
    }

    private fun getDrawingsFolder(): File {
        val folder = File(filesDir, "drawings")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun loadFileList() {
        container.removeAllViews()
        val files = getDrawingsFolder().listFiles()?.filter { it.name.endsWith(".eng") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No notes yet. Tap + New to create one."
            tv.setPadding(20, 40, 20, 20)
            tv.gravity = Gravity.CENTER
            container.addView(tv)
            return
        }

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        for (file in files) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(24, 24, 24, 24)
            row.setBackgroundColor(0xFFF0F0F0.toInt())

            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 16
            row.layoutParams = params

            val title = TextView(this)
            title.text = file.nameWithoutExtension
            title.textSize = 18f
            title.setTextColor(0xFF000000.toInt())
            row.addView(title)

            val subtitle = TextView(this)
            subtitle.text = "Edited " + dateFormat.format(Date(file.lastModified()))
            subtitle.textSize = 12f
            subtitle.setTextColor(0xFF888888.toInt())
            row.addView(subtitle)

            row.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("filename", file.nameWithoutExtension)
                startActivity(intent)
            }

            container.addView(row)
        }
    }
}
