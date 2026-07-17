package com.enginotes.app

// Everything related to custom hatches (Add-from-image and Snip-from-canvas) plus the hatch
// picker dialog itself — split out of MainActivity.kt as part of breaking that file into
// smaller, topic-focused pieces. These are extension functions on MainActivity rather than
// members of a separate class, so they're called exactly the same way as before from anywhere
// else in MainActivity (showHatchPicker(), applyCustomHatch(path), etc.) — Kotlin resolves an
// extension function through the same implicit receiver it uses for a member function, so no
// call site anywhere else needed to change.
//
// A few things this file depends on (drawingView, setActiveTool, pickImageForCustomHatchLauncher)
// had to move from `private` to `internal` in MainActivity.kt to make that possible — `internal`
// means "accessible from any file in this app module," not "accessible to the outside world," so
// this doesn't change what the app exposes, just how its own files can talk to each other.

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream

internal fun MainActivity.customHatchDir(): File = File(filesDir, "custom_hatches").also { it.mkdirs() }
internal fun MainActivity.listCustomHatches(): List<File> = customHatchDir().listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

internal fun MainActivity.addCustomHatchFromUri(uri: Uri) {
    try {
        val outFile = File(customHatchDir(), "hatch_${System.currentTimeMillis()}.png")
        contentResolver.openInputStream(uri)?.use { input ->
            val bmp = android.graphics.BitmapFactory.decodeStream(input)
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        applyCustomHatch(outFile.absolutePath)
    } catch (e: Exception) {
        Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.applyCustomHatch(path: String) {
    drawingView.pendingHatchPattern = null
    drawingView.pendingCustomHatchPath = path
    drawingView.pendingHatchColor = drawingView.currentColor
    setActiveTool(null, Tool.FILL)
    Toast.makeText(this, "Tap area to fill with this hatch", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.startHatchSnip() {
    drawingView.onHatchSnipSelected = { bmp, _, _, _, _ ->
        try {
            val outFile = File(customHatchDir(), "hatch_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            applyCustomHatch(outFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save that snip", Toast.LENGTH_SHORT).show()
        }
    }
    setActiveTool(null, Tool.HATCH_SNIP)
    Toast.makeText(this, "Drag a box around the strokes you want as a hatch", Toast.LENGTH_LONG).show()
}

internal fun MainActivity.showHatchPicker() {
    val categories = linkedMapOf(
        "Lines" to listOf("45° Lines" to HatchPattern.HATCH_45, "135° Lines" to HatchPattern.HATCH_135, "Vertical" to HatchPattern.HATCH_90, "Horizontal" to HatchPattern.HATCH_0, "Cross" to HatchPattern.HATCH_CROSS, "Diagonal Cross" to HatchPattern.HATCH_DIAGONAL_CROSS),
        "Civil/Structural" to listOf("Concrete" to HatchPattern.CONCRETE, "Steel" to HatchPattern.STEEL, "Earth" to HatchPattern.EARTH, "Sand" to HatchPattern.SAND, "Rock" to HatchPattern.ROCK, "Gravel" to HatchPattern.GRAVEL, "Compacted Fill" to HatchPattern.COMPACTED_FILL, "Loose Fill" to HatchPattern.LOOSE_FILL, "Clay" to HatchPattern.CLAY, "Silt" to HatchPattern.SILT, "Peat" to HatchPattern.PEAT, "Chalk" to HatchPattern.CHALK, "Asphalt" to HatchPattern.ASPHALT, "Rebar" to HatchPattern.REBAR, "Concrete Precast" to HatchPattern.CONCRETE_PRECAST),
        "Wood/Building" to listOf("Wood Grain" to HatchPattern.WOOD_GRAIN, "Wood End" to HatchPattern.WOOD_END, "Brick" to HatchPattern.BRICK, "Block" to HatchPattern.BLOCK, "Plywood" to HatchPattern.PLYWOOD, "Drywall" to HatchPattern.DRYWALL, "Insulation" to HatchPattern.INSULATION),
        "Metal" to listOf("Aluminum" to HatchPattern.ALUMINUM, "Copper" to HatchPattern.COPPER, "Iron" to HatchPattern.IRON, "Bronze" to HatchPattern.BRONZE, "Titanium" to HatchPattern.TITANIUM, "Gold" to HatchPattern.GOLD_HATCH),
        "Material" to listOf("Glass" to HatchPattern.GLASS, "Rubber" to HatchPattern.RUBBER, "Plastic" to HatchPattern.PLASTIC, "Ceramic" to HatchPattern.CERAMIC, "Fiberglass" to HatchPattern.FIBERGLASS, "Foam" to HatchPattern.FOAM, "Membrane" to HatchPattern.MEMBRANE),
        "Patterns" to listOf("Dots Fine" to HatchPattern.DOTS_FINE, "Dots Coarse" to HatchPattern.DOTS_COARSE, "Stipple" to HatchPattern.STIPPLE, "Honeycomb" to HatchPattern.HONEYCOMB, "Basket Weave" to HatchPattern.BASKET_WEAVE, "Diamond Grid" to HatchPattern.DIAMOND_GRID, "Zigzag" to HatchPattern.ZIGZAG, "Wave" to HatchPattern.WAVE, "Herringbone" to HatchPattern.HERRINGBONE, "Scale" to HatchPattern.SCALE, "Chain Link" to HatchPattern.CHAIN_LINK, "Contour" to HatchPattern.CONTOUR, "Water" to HatchPattern.WATER)
    )
    val allItems = mutableListOf<String>(); val allPatterns = mutableListOf<HatchPattern?>()
    val allCustomPaths = mutableListOf<String?>(); val allActions = mutableListOf<(() -> Unit)?>()
    fun addRow(label: String, pattern: HatchPattern? = null, customPath: String? = null, action: (() -> Unit)? = null) {
        allItems.add(label); allPatterns.add(pattern); allCustomPaths.add(customPath); allActions.add(action)
    }
    categories.forEach { (cat, items) ->
        addRow("── $cat ──")
        items.forEach { (name, pat) -> addRow("  $name", pattern = pat) }
    }
    // Custom section — existing procedural patterns above are completely untouched. "Add"
    // brings in any image (PNG etc.) from the device; "Snip" crops a region of the current
    // canvas with the page background/lines/fills excluded, keeping only the ink itself.
    // Both end up as tileable custom hatches, listed below so they can be reused later.
    addRow("── Custom ──")
    addRow("  ➕ Add image (PNG, etc.)", action = { pickImageForCustomHatchLauncher.launch("image/*") })
    addRow("  ✂ Snip from canvas", action = { startHatchSnip() })
    listCustomHatches().forEach { f -> addRow("  🖼 ${f.name}", customPath = f.absolutePath) }

    AlertDialog.Builder(this).setTitle("Hatch Pattern (long-press area to fill)")
        .setItems(allItems.toTypedArray()) { _, i ->
            val label = allItems[i]
            if (label.startsWith("──")) return@setItems
            allActions[i]?.let { it(); return@setItems }
            allCustomPaths[i]?.let { applyCustomHatch(it); return@setItems }
            val selected = allPatterns[i] ?: return@setItems
            // Set fill tool with hatch — next tap fills the tapped area with this hatch
            drawingView.pendingHatchPattern = selected
            drawingView.pendingCustomHatchPath = null
            drawingView.pendingHatchColor = drawingView.currentColor
            setActiveTool(null, Tool.FILL)
            Toast.makeText(this, "Tap area to fill with hatch", Toast.LENGTH_SHORT).show()
        }.show()
}
