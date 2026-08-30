package com.enginotes.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// ── Voice command registry ──────────────────────────────────────────────────────────────
// One flat list, not a strict tree — [category] exists purely for grouping in the settings
// picker below; [keywords] is what a recognized phrase actually gets matched against (fuzzily,
// once the recognition engine itself is wired up), and can hold more than one word/spelling per
// command so close variants ("hatch"/"fill", "eraser"/"erase") both work without the user having
// to know the "official" name. [id] is a stable key used ONLY for persistence — a user's custom
// word-to-command binding is stored as (word, id) rather than (word, closure), since a closure
// can't be written to SharedPreferences; the registry is rebuilt fresh each run and the id looks
// the matching VoiceCommand back up.
data class VoiceCommand(val id: String, val category: String, val label: String, val keywords: List<String>, val action: () -> Unit)

data class VoiceBinding(val word: String, val commandId: String)

private const val VOICE_BINDINGS_PREF_KEY = "voice_command_bindings"

internal fun MainActivity.loadVoiceBindings(): MutableList<VoiceBinding> {
    val raw = getPrefs().getString(VOICE_BINDINGS_PREF_KEY, null) ?: return mutableListOf()
    return try {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            VoiceBinding(o.getString("word"), o.getString("commandId"))
        }.toMutableList()
    } catch (e: Exception) { mutableListOf() }
}

internal fun MainActivity.saveVoiceBindings(bindings: List<VoiceBinding>) {
    val arr = org.json.JSONArray()
    for (b in bindings) {
        val o = org.json.JSONObject()
        o.put("word", b.word); o.put("commandId", b.commandId)
        arr.put(o)
    }
    getPrefs().edit().putString(VOICE_BINDINGS_PREF_KEY, arr.toString()).apply()
}

/** Builds the full, current set of available voice commands — every tool/shape/colour/hatch/
 * action the app can currently reach through this list, freshly bound to real closures each
 * call (so it always reflects the live app instance, not a stale cached one from a previous
 * note/session). Kept deliberately as ONE big function rather than split per-category, since the
 * whole point is that it's a single place a future addition (a new hatch pattern, a new action)
 * gets added to and every consumer (settings picker, recognition engine) picks it up for free. */
internal fun MainActivity.buildVoiceCommandRegistry(): List<VoiceCommand> {
    val dv = drawingView
    val list = mutableListOf<VoiceCommand>()
    fun add(id: String, category: String, label: String, keywords: List<String>, action: () -> Unit) {
        list.add(VoiceCommand(id, category, label, keywords, action))
    }

    // Tools — the ones actually likely to be voice-triggered; niche modes (export window, OCR
    // snip, hatch snip, autoselect/multiselect) are left out of the SPOKEN set on purpose, same
    // reasoning as shapes below, though they still exist and remain reachable normally.
    add("tool.pen", "Tool", "Pen", listOf("pen", "pencil")) { setActiveTool(null, Tool.PEN) }
    add("tool.brush", "Tool", "Brush", listOf("brush")) { setActiveTool(null, Tool.BRUSH) }
    add("tool.eraser", "Tool", "Eraser", listOf("eraser", "erase")) { setActiveTool(null, Tool.ERASER) }
    add("tool.highlighter", "Tool", "Highlighter", listOf("highlighter", "highlight")) { setActiveTool(null, Tool.HIGHLIGHTER) }
    add("tool.text", "Tool", "Text", listOf("text", "type")) { setActiveTool(null, Tool.TEXT) }
    add("tool.select", "Tool", "Select", listOf("select", "selection")) { setActiveTool(null, Tool.SELECT) }
    add("tool.lasso", "Tool", "Lasso", listOf("lasso")) { setActiveTool(null, Tool.LASSO) }
    add("tool.hatch", "Tool", "Hatch / Fill", listOf("hatch", "fill")) { setActiveTool(null, Tool.FILL) }
    add("tool.dimension", "Tool", "Dimension / Ruler", listOf("ruler", "dimension")) { setActiveTool(null, Tool.DIMENSION) }
    add("tool.polyline", "Tool", "Polyline", listOf("polyline")) { setActiveTool(null, Tool.POLYLINE) }
    add("tool.curve", "Tool", "Curve", listOf("curve")) { setActiveTool(null, Tool.CURVE) }
    add("tool.autoselect", "Tool", "Auto Select", listOf("autoselect", "auto select")) { setActiveTool(null, Tool.AUTOSELECT) }
    add("tool.multiselect", "Tool", "Multi Select", listOf("multiselect", "multi select")) { setActiveTool(null, Tool.MULTISELECT) }
    add("tool.exportwindow", "Tool", "Export Window", listOf("export window", "export")) { setActiveTool(null, Tool.EXPORT_WINDOW) }
    add("tool.ocrsnip", "Tool", "OCR Snip", listOf("ocr snip", "text snip")) { setActiveTool(null, Tool.OCR_SNIP) }
    add("tool.hatchsnip", "Tool", "Hatch Snip", listOf("hatch snip")) { setActiveTool(null, Tool.HATCH_SNIP) }

    // Shapes — kept to a small, practical set on purpose (not all ~65 in the Tool enum, most of
    // which are niche polygons nobody's realistically going to say out loud). More can be added
    // to this same list later; nothing about the architecture limits it to six.
    add("shape.rectangle", "Shape", "Rectangle", listOf("rectangle", "square")) { setActiveTool(null, Tool.RECTANGLE) }
    add("shape.circle", "Shape", "Circle", listOf("circle")) { setActiveTool(null, Tool.CIRCLE) }
    add("shape.triangle", "Shape", "Triangle", listOf("triangle")) { setActiveTool(null, Tool.TRIANGLE) }
    add("shape.line", "Shape", "Line", listOf("line")) { setActiveTool(null, Tool.LINE) }
    add("shape.arrow", "Shape", "Arrow", listOf("arrow")) { setActiveTool(null, Tool.ARROW) }
    add("shape.star", "Shape", "Star", listOf("star")) { setActiveTool(null, Tool.STAR) }

    // Colours — reusing the app's OWN established quick-color palette (the same values used in
    // the Pen/Highlighter/Brush colour swatches elsewhere), not arbitrary new ones, so saying
    // "red" gives you the exact same red you'd get tapping the swatch.
    val paletteColors = listOf(
        "Black" to Color.BLACK, "Red" to Color.RED, "Blue" to Color.parseColor("#03A9F4"),
        "Green" to Color.parseColor("#4CAF50"), "Yellow" to Color.parseColor("#FFC107"),
        "Orange" to Color.parseColor("#FF9800"), "Purple" to Color.parseColor("#9C27B0"),
        "Navy" to Color.parseColor("#1A237E")
    )
    for ((name, value) in paletteColors) {
        add("color.${name.lowercase()}", "Colour", name, listOf(name.lowercase())) { dv.currentColor = value }
    }

    // Combos — a single spoken phrase doing "switch tool AND set its colour" in one go, e.g.
    // "red pen" or "blue line", rather than needing two separate commands strung together.
    // currentColor is shared across Pen/Brush/Highlighter/Text/shapes (confirmed by checking how
    // the existing colour swatches for each of those already set the SAME property elsewhere in
    // this codebase); Fill/Hatch is the one exception with its own separate fillColor, which is
    // why "red fill"/"red hatch" set a different field below — using currentColor there would
    // silently do nothing, since the Fill tool doesn't read from it at all.
    for ((name, value) in paletteColors) {
        val n = name.lowercase()
        add("combo.${n}.pen", "Combo", "$name Pen", listOf("$n pen")) { setActiveTool(null, Tool.PEN); dv.currentColor = value }
        add("combo.${n}.brush", "Combo", "$name Brush", listOf("$n brush")) { setActiveTool(null, Tool.BRUSH); dv.currentColor = value }
        add("combo.${n}.highlighter", "Combo", "$name Highlighter", listOf("$n highlighter")) { setActiveTool(null, Tool.HIGHLIGHTER); dv.currentColor = value }
        // Both phrasings map to the SAME action (set the Fill tool's own colour) — matches how
        // "hatch" and "fill" are already treated as synonyms for the plain tool.hatch entry above.
        add("combo.${n}.fill", "Combo", "$name Fill", listOf("$n fill", "$n hatch")) {
            setActiveTool(null, Tool.FILL); dv.fillColor = value
        }
        add("combo.${n}.text", "Combo", "Text Box — $name Font", listOf("text box with $n font", "$n text")) {
            setActiveTool(null, Tool.TEXT); dv.currentColor = value
        }
        add("combo.${n}.line", "Combo", "$name Line", listOf("$n line")) { setActiveTool(null, Tool.LINE); dv.currentColor = value }
        add("combo.${n}.rectangle", "Combo", "$name Rectangle", listOf("$n rectangle", "$n square")) { setActiveTool(null, Tool.RECTANGLE); dv.currentColor = value }
        add("combo.${n}.circle", "Combo", "$name Circle", listOf("$n circle")) { setActiveTool(null, Tool.CIRCLE); dv.currentColor = value }
        add("combo.${n}.triangle", "Combo", "$name Triangle", listOf("$n triangle")) { setActiveTool(null, Tool.TRIANGLE); dv.currentColor = value }
        add("combo.${n}.arrow", "Combo", "$name Arrow", listOf("$n arrow")) { setActiveTool(null, Tool.ARROW); dv.currentColor = value }
        add("combo.${n}.star", "Combo", "$name Star", listOf("$n star")) { setActiveTool(null, Tool.STAR); dv.currentColor = value }
    }

    // Hatch patterns — the full real list, not trimmed the way Shapes was. This is the ORIGINAL
    // motivating example for the whole feature ("say concrete, get concrete hatch"), and unlike
    // decagon/nonagon these are exactly the kind of precise engineering terms worth having all of.
    for (hp in HatchPattern.values()) {
        val spoken = hp.name.lowercase().replace('_', ' ')
        val label = spoken.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        add("hatch.${hp.name.lowercase()}", "Hatch", label, listOf(spoken)) {
            setActiveTool(null, Tool.FILL)
            // Was dv.hatchPattern — that's a per-item field on FillItem (the hatch object
            // already placed on canvas), not a "what pattern should the FILL tool use next"
            // setting. The actual setting is DrawingView.pendingHatchPattern, applied to whatever
            // FillItem gets created next — confirmed by checking how the existing hatch-picker
            // dialog (HatchExtensions.kt) itself sets it, rather than assuming from the name alone.
            // pendingCustomHatchPath is the mutually-exclusive alternative (a user-picked custom
            // image tiled as the hatch instead of a built-in pattern) — cleared here the same way
            // the existing colour-swatch handler clears the OPPOSITE pairing, so a voice command
            // can't leave a stale custom hatch path still set alongside a real pattern.
            dv.pendingCustomHatchPath = null
            dv.pendingHatchPattern = hp
        }
    }

    // Actions
    add("action.undo", "Action", "Undo", listOf("undo")) { dv.undo() }
    add("action.redo", "Action", "Redo", listOf("redo")) { dv.redo() }
    add("action.save", "Action", "Save", listOf("save")) { saveCurrent() }
    add("action.layers", "Action", "Layers", listOf("layers", "layer")) { showLayersPanel() }
    add("action.lock", "Action", "Lock", listOf("lock")) { dv.lockSelectedItems() }
    add("action.unlock", "Action", "Unlock", listOf("unlock")) { dv.unlockSelectedItems() }

    return list
}

// ── Settings UI ──────────────────────────────────────────────────────────────────────────
/** Main voice-command management screen — lists the user's current word→command bindings (each
 * removable), with an "Add" entry point into the category/search picker below. Deliberately
 * simple LinearLayout-in-a-ScrollView rows rather than a RecyclerView, matching how the rest of
 * this app's settings/list UIs are already built (no new UI pattern introduced just for this). */
internal fun MainActivity.showVoiceCommandSettings() {
    val registry = buildVoiceCommandRegistry()
    val byId = registry.associateBy { it.id }
    val bindings = loadVoiceBindings()
    val accent = currentThemeButtonColor()

    val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4)) }
    val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    container.addView(listHolder)

    fun refreshList() {
        listHolder.removeAllViews()
        if (bindings.isEmpty()) {
            listHolder.addView(TextView(this).apply {
                text = "No voice commands yet — tap \"+ Add Command\" below to create one."
                textSize = 13f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(0, dp(8), 0, dp(12))
            })
        }
        for (b in bindings) {
            val cmd = byId[b.commandId]
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            row.addView(TextView(this).apply {
                text = if (cmd != null) "\"${b.word}\"  →  ${cmd.category}: ${cmd.label}" else "\"${b.word}\"  →  (unknown command)"
                textSize = 14f; setTextColor(Color.parseColor("#2A2A2A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = "✕"; textSize = 16f; setTextColor(Color.parseColor("#C0392B")); setPadding(dp(12), 0, dp(4), 0)
                setOnClickListener { bindings.remove(b); saveVoiceBindings(bindings); refreshList() }
            })
            listHolder.addView(row)
        }
    }
    refreshList()

    val addBtn = TextView(this).apply {
        text = "+ Add Command"; textSize = 15f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(14), 0, dp(6))
    }
    container.addView(addBtn)

    val dialog = AlertDialog.Builder(this)
        .setTitle("Voice Commands")
        .setView(ScrollView(this).apply { addView(container) })
        .setPositiveButton("Done", null)
        .create()

    addBtn.setOnClickListener {
        showVoiceCommandPicker(registry) { chosen ->
            showVoiceWordEntry(chosen) { word ->
                bindings.removeAll { it.word.equals(word, ignoreCase = true) } // one command per word — a re-added word replaces its old binding rather than stacking silently
                bindings.add(VoiceBinding(word, chosen.id))
                saveVoiceBindings(bindings)
                refreshList()
            }
        }
    }
    dialog.show()
}

/** Second step of "Add": ask for the trigger word itself, once a target command has already been
 * picked — kept as its own small dialog rather than folding a text field into the picker screen,
 * so the picker (which the user may reopen/search several times while deciding) doesn't have to
 * carry an EditText's state around. */
private fun MainActivity.showVoiceWordEntry(command: VoiceCommand, onConfirm: (String) -> Unit) {
    val input = EditText(this).apply {
        hint = "e.g. ${command.keywords.firstOrNull() ?: command.label.lowercase()}"
        setPadding(dp(20), dp(16), dp(20), dp(4))
    }
    AlertDialog.Builder(this)
        .setTitle("Word for \"${command.category}: ${command.label}\"")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            val word = input.text.toString().trim().lowercase()
            if (word.isNotEmpty()) onConfirm(word)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/** The category-browse + live-search picker itself. Search filters across ALL categories at
 * once by label/keyword match (not just within whichever category is currently expanded) — typing
 * "concrete" finds it immediately without needing to know it lives under "Hatch" first, which
 * matters once this list is 100+ entries long. Categories are shown as collapsible headers purely
 * as a browsing aid for when you don't already know the exact word you want. */
private fun MainActivity.showVoiceCommandPicker(registry: List<VoiceCommand>, onPick: (VoiceCommand) -> Unit) {
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
    val search = EditText(this).apply { hint = "Search all commands…" }
    root.addView(search)

    val resultsHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
    root.addView(resultsHolder)

    val dialog = AlertDialog.Builder(this)
        .setTitle("Choose a Function")
        .setView(ScrollView(this).apply { addView(root) })
        .setNegativeButton("Cancel", null)
        .create()

    fun rowFor(cmd: VoiceCommand): View = TextView(this).apply {
        text = cmd.label; textSize = 15f; setTextColor(Color.parseColor("#2A2A2A"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { dialog.dismiss(); onPick(cmd) }
    }

    fun renderGrouped() {
        resultsHolder.removeAllViews()
        for ((category, items) in registry.groupBy { it.category }) {
            resultsHolder.addView(TextView(this).apply {
                text = category.uppercase(); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(currentThemeButtonColor()); setPadding(dp(4), dp(12), 0, dp(4))
            })
            for (cmd in items) resultsHolder.addView(rowFor(cmd))
        }
    }
    fun renderFiltered(query: String) {
        resultsHolder.removeAllViews()
        val q = query.trim().lowercase()
        val matches = registry.filter { cmd -> cmd.label.lowercase().contains(q) || cmd.keywords.any { it.contains(q) } || cmd.category.lowercase().contains(q) }
        if (matches.isEmpty()) {
            resultsHolder.addView(TextView(this).apply { text = "No matches"; textSize = 13f; setTextColor(Color.parseColor("#9A9A9A")); setPadding(dp(4), dp(12), 0, 0) })
        } else {
            for (cmd in matches) resultsHolder.addView(rowFor(cmd))
        }
    }
    renderGrouped()
    search.addTextChangedListener(object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) {
            val q = s?.toString().orEmpty()
            if (q.isBlank()) renderGrouped() else renderFiltered(q)
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })

    dialog.show()
}

// ── Recognition engine ──────────────────────────────────────────────────────────────────
// Deliberately press-to-listen-once rather than continuous open-mic: in a classroom or site
// environment with background chatter, an always-listening mic trying to match speech against
// commands is a real false-trigger risk (accidentally switching tools mid-conversation). One
// press, one utterance, auto-stop — same toggle-button gesture the person asked for, just not
// open-mic the whole time it's "on".

/** Plain Levenshtein edit distance — no library needed for this, and pulling one in for a single
 * well-known algorithm would be a strange trade for what's ~15 lines of code. */
private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
        else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
    }
    return dp[a.length][b.length]
}

/** How close [heard] is to [target], as a 0..1 similarity (1 = identical) — normalized by the
 * longer string's length so short and long words are judged on a comparable scale, since a raw
 * edit-distance count of "2" means something very different for a 3-letter word vs. a
 * 12-letter one. */
private fun similarity(heard: String, target: String): Float {
    val maxLen = maxOf(heard.length, target.length)
    if (maxLen == 0) return 1f
    return 1f - levenshtein(heard, target).toFloat() / maxLen
}

// Threshold chosen to comfortably tolerate one or two misheard letters on a short word (which is
// most of what these trigger words are) without accepting a genuinely different word. Worth
// revisiting after real use if it's letting through wrong matches or rejecting good ones.
private const val VOICE_MATCH_THRESHOLD = 0.6f

/** Finds the best-matching saved binding for whatever the recognizer heard, or null if nothing
 * clears [VOICE_MATCH_THRESHOLD] — checked against the FULL heard phrase first (in case someone
 * says a multi-word trigger exactly), then against each individual word in it (recognizers often
 * pick up a stray "the"/"a" or mis-split a short word), keeping whichever single comparison
 * scored highest across all of that. */
private fun bestVoiceMatch(heard: String, bindings: List<VoiceBinding>): VoiceBinding? {
    val cleaned = heard.trim().lowercase()
    if (cleaned.isEmpty() || bindings.isEmpty()) return null
    var best: VoiceBinding? = null
    var bestScore = 0f
    val candidates = (listOf(cleaned) + cleaned.split(" ")).distinct()
    for (b in bindings) {
        for (c in candidates) {
            val score = similarity(c, b.word)
            if (score > bestScore) { bestScore = score; best = b }
        }
    }
    return if (bestScore >= VOICE_MATCH_THRESHOLD) best else null
}

private val voiceMicPermission = "android.permission.RECORD_AUDIO"

/** Call once from onCreate — embedded directly into the SAME top toolbar row as btnMenu/
 * btnReadMode/etc. (found at runtime via btnMenu's parent, not by editing activity_main.xml
 * directly), sized and styled to match its siblings, rather than floating over the canvas as a
 * separate overlay — floating there read as visual clutter sitting on top of the page itself.
 * Inserted right before btnMenu specifically so it lands among the other utility icons rather
 * than off at either end of the row. */
internal fun MainActivity.setupVoiceCommandButton() {
    val menuBtn = findViewById<ImageButton>(R.id.btnMenu)
    val topBar = menuBtn.parent as? android.view.ViewGroup ?: return
    // Guards against adding a second mic button if this ever gets called more than once (e.g. a
    // config change recreating the Activity) — without this, each extra call appended another
    // icon into the same row rather than replacing the first.
    if (topBar.findViewWithTag<View>("voice_mic_btn") != null) return
    val insertIndex = topBar.indexOfChild(menuBtn)
    val btn = TextView(this).apply {
        text = "🎤"; textSize = 18f; gravity = Gravity.CENTER
        tag = "voice_mic_btn"
        setTextColor(Color.parseColor("#333333"))
        // Resolves to the exact same ripple background every other icon button in this row uses
        // in XML (?attr/selectableItemBackgroundBorderless) — there's no direct "?attr/" syntax
        // available from code, so this is the equivalent lookup through the current theme.
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        setBackgroundResource(tv.resourceId)
    }
    val lp = android.view.ViewGroup.LayoutParams(dp(40), dp(40))
    topBar.addView(btn, insertIndex.coerceAtLeast(0), lp)
    voiceMicButtonRef = btn
    btn.setOnClickListener { onVoiceMicTapped() }
}

// Held so recognition callbacks (which fire well after the tap that started them) can update the
// button's own appearance (listening/idle) without needing to re-find it by id each time.
private var voiceMicButtonRef: TextView? = null
private var voiceRecognizer: android.speech.SpeechRecognizer? = null
private var voiceListening = false

internal fun MainActivity.destroyVoiceRecognizer() {
    voiceRecognizer?.destroy()
    voiceRecognizer = null
}

private fun MainActivity.onVoiceMicTapped() {
    if (voiceListening) { stopVoiceListening(); return }
    if (checkSelfPermission(voiceMicPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestVoiceMicPermission.launch(voiceMicPermission)
        return
    }
    startVoiceListening()
}

private fun MainActivity.stopVoiceListening() {
    voiceRecognizer?.cancel()
    voiceListening = false
    voiceMicButtonRef?.background = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.parseColor("#CC2A2A2A")); cornerRadius = dp(28).toFloat()
    }
}

internal fun MainActivity.startVoiceListening() {
    if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
        Toast.makeText(this, "Speech recognition isn't available on this device", Toast.LENGTH_LONG).show()
        return
    }
    val bindings = loadVoiceBindings()
    if (bindings.isEmpty()) {
        Toast.makeText(this, "No voice commands set up yet — add some in Settings first", Toast.LENGTH_LONG).show()
        return
    }
    val registry = buildVoiceCommandRegistry()
    val byId = registry.associateBy { it.id }
    // Captured explicitly — inside the anonymous RecognitionListener below, a bare `this` refers
    // to the listener object itself, not this extension function's MainActivity receiver.
    val activity = this
    // Some devices' SpeechRecognizer implementations call onError (or, less commonly, onResults)
    // more than once for the SAME listening session — a known cross-OEM speech-engine quirk, not
    // something this app's flow triggers. Without a guard, each stray extra callback queued its
    // own Toast, and several Toasts firing back-to-back is exactly what "the error displays
    // continuously" looks like from the outside. This makes each session produce at most one
    // outcome, however many times the system actually calls back.
    var sessionHandled = false

    voiceRecognizer?.destroy()
    val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
    voiceRecognizer = recognizer
    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            voiceListening = true
            voiceMicButtonRef?.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#CCE53935")); cornerRadius = dp(28).toFloat()
            }
        }
        override fun onResults(results: android.os.Bundle?) {
            if (sessionHandled) return
            sessionHandled = true
            val heardList = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            // Every candidate transcription gets a shot at matching, not just the recognizer's
            // top pick — its first guess is sometimes a homophone or a mis-split of the actual
            // word, and a lower-ranked alternative can still be an exact match.
            var matched: VoiceBinding? = null
            for (heard in heardList) { val m = bestVoiceMatch(heard, bindings); if (m != null) { matched = m; break } }
            if (matched != null) {
                val cmd = byId[matched.commandId]
                if (cmd != null) {
                    cmd.action()
                    Toast.makeText(activity, "✓ ${cmd.label}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "That command no longer exists", Toast.LENGTH_SHORT).show()
                }
            } else {
                val heardText = heardList.firstOrNull()
                Toast.makeText(activity, if (heardText != null) "Didn't recognize \"$heardText\" as a command" else "Didn't catch that", Toast.LENGTH_SHORT).show()
            }
            activity.stopVoiceListening()
        }
        override fun onError(error: Int) {
            if (sessionHandled) return
            sessionHandled = true
            val msg = when (error) {
                android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error — speech recognition needs a connection on this device"
                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
                else -> "Voice command not recognized"
            }
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            activity.stopVoiceListening()
        }
        override fun onEndOfSpeech() {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
    })
    recognizer.startListening(intent)
}

