package com.enginotes.app

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.digitalink.*

class HandwritingActivity : AppCompatActivity() {

    private lateinit var inkView: InkView
    private lateinit var tvResult: TextView
    private var recognizer: DigitalInkRecognizer? = null
    private var modelIdentifier: DigitalInkRecognitionModelIdentifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL

        // Toolbar
        val toolbar = LinearLayout(this); toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(Color.parseColor("#FF1565C0"))
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6)); toolbar.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this); title.text = "✍ Handwriting to Text"; title.textSize = 15f
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

        tbtn("Recognise") { recognise() }
        tbtn("Clear") { inkView.clear(); tvResult.text = "Write above, tap Recognise" }
        tbtn("✕") { finish() }
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Language info
        val langInfo = TextView(this); langInfo.text = "Language: English (en-US)  |  Write naturally on the canvas below"
        langInfo.textSize = 12f; langInfo.setPadding(dp(12), dp(6), dp(12), dp(4))
        langInfo.setBackgroundColor(Color.parseColor("#EEF0F0F0"))
        root.addView(langInfo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Ink canvas
        inkView = InkView(this)
        inkView.setBackgroundColor(Color.WHITE)
        root.addView(inkView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))

        // Divider
        val div = View(this); div.setBackgroundColor(Color.LTGRAY)
        root.addView(div, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        // Result area
        tvResult = TextView(this)
        tvResult.text = "Write above, tap Recognise"
        tvResult.textSize = 18f; tvResult.setPadding(dp(16), dp(12), dp(16), dp(12))
        tvResult.setTextColor(Color.DKGRAY)
        val scroll = ScrollView(this)
        scroll.addView(tvResult)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        setupRecognizer()
    }

    private fun setupRecognizer() {
        try {
            modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            val model = DigitalInkRecognitionModel.builder(modelIdentifier!!).build()
            val remoteModelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            remoteModelManager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (!downloaded) {
                    tvResult.text = "Downloading handwriting model... please wait"
                    remoteModelManager.download(model, com.google.mlkit.common.model.DownloadConditions.Builder().build())
                        .addOnSuccessListener { buildRecognizer(model) }
                        .addOnFailureListener { tvResult.text = "Model download failed. Check internet connection." }
                } else buildRecognizer(model)
            }
        } catch (e: Exception) {
            tvResult.text = "ML Kit setup error: ${e.message}"
        }
    }

    private fun buildRecognizer(model: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        tvResult.text = "Ready! Write on the canvas above."
    }

    private fun recognise() {
        val rec = recognizer
        if (rec == null) { tvResult.text = "Recognizer not ready yet. Please wait."; return }
        val inkData = inkView.getInk()
        if (inkData.strokes.isEmpty()) { tvResult.text = "Nothing to recognise — draw something first!"; return }
        tvResult.text = "Recognising..."
        rec.recognize(inkData)
            .addOnSuccessListener { result ->
                val candidates = result.candidates
                if (candidates.isEmpty()) tvResult.text = "Could not recognise. Try writing more clearly."
                else {
                    val sb = StringBuilder()
                    sb.append("Best match: ${candidates[0].text}\n\n")
                    if (candidates.size > 1) {
                        sb.append("Other suggestions:\n")
                        for (i in 1 until minOf(candidates.size, 5)) sb.append("• ${candidates[i].text}\n")
                    }
                    tvResult.text = sb.toString()
                }
            }
            .addOnFailureListener { tvResult.text = "Recognition failed: ${it.message}" }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { super.onDestroy(); recognizer?.close() }
}

class InkView(context: Context) : View(context) {
    private val strokes = mutableListOf<Ink.Stroke>()
    private var currentStrokeBuilder: Ink.Stroke.Builder? = null
    private val displayStrokes = mutableListOf<List<Pair<Float, Float>>>()
    private var currentDisplayStroke = mutableListOf<Pair<Float, Float>>()
    private val paint = Paint().apply {
        color = Color.BLACK; strokeWidth = 6f; style = Paint.Style.STROKE
        isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    fun clear() { strokes.clear(); displayStrokes.clear(); currentDisplayStroke.clear(); currentStrokeBuilder = null; invalidate() }

    fun getInk(): Ink {
        val builder = Ink.builder()
        for (s in strokes) builder.addStroke(s)
        return builder.build()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in displayStrokes) {
            if (stroke.size < 2) continue
            val path = Path(); path.moveTo(stroke[0].first, stroke[0].second)
            for (i in 1 until stroke.size) path.lineTo(stroke[i].first, stroke[i].second)
            canvas.drawPath(path, paint)
        }
        if (currentDisplayStroke.size >= 2) {
            val path = Path(); path.moveTo(currentDisplayStroke[0].first, currentDisplayStroke[0].second)
            for (i in 1 until currentDisplayStroke.size) path.lineTo(currentDisplayStroke[i].first, currentDisplayStroke[i].second)
            canvas.drawPath(path, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val t = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentStrokeBuilder = Ink.Stroke.builder()
                currentStrokeBuilder?.addPoint(Ink.Point.create(event.x, event.y, t))
                currentDisplayStroke = mutableListOf(Pair(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> {
                currentStrokeBuilder?.addPoint(Ink.Point.create(event.x, event.y, t))
                currentDisplayStroke.add(Pair(event.x, event.y)); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                currentStrokeBuilder?.addPoint(Ink.Point.create(event.x, event.y, t))
                currentStrokeBuilder?.build()?.let { strokes.add(it) }
                displayStrokes.add(currentDisplayStroke.toList())
                currentDisplayStroke.clear(); currentStrokeBuilder = null; invalidate()
            }
        }
        return true
    }
}
