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

    // Hatch patterns — the full real list, not trimmed the way Shapes was. This is the ORIGINAL
    // motivating example for the whole feature ("say concrete, get concrete hatch"), and unlike
    // decagon/nonagon these are exactly the kind of precise engineering terms worth having all of.
    for (hp in HatchPattern.values()) {
        val spoken = hp.name.lowercase().replace('_', ' ')
        val label = spoken.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        add("hatch.${hp.name.lowercase()}", "Hatch", label, listOf(spoken)) {
            setActiveTool(null, Tool.FILL)
            dv.hatchPattern = hp
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
