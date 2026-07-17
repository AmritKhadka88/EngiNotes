package com.enginotes.app

import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

// On-device OCR (photo/gallery/PDF-snip → editable text → inserted as a text item). Split out of
// MainActivity.kt the same way as HatchExtensions.kt and TextEditingExtensions.kt — extension
// functions on MainActivity, same behavior, just organized into its own file. Needed
// ocrCameraFile, requestCameraPermission, takePictureForOcrLauncher, pickImageForOcrLauncher,
// and pickPdfForOcrLauncher widened from private to internal in MainActivity.kt to make this
// possible — same "usable anywhere in this app module" reasoning as the other extractions.

internal fun MainActivity.launchCameraForOcr() {
    if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        try {
            val folder = File(filesDir, "images").also { it.mkdirs() }
            val pf = File(folder, "ocr_camera_${System.currentTimeMillis()}.jpg"); ocrCameraFile = pf
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", pf)
            takePictureForOcrLauncher.launch(uri)
        } catch (e: Exception) { Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show() }
    } else {
        requestCameraPermission.launch(android.Manifest.permission.CAMERA)
    }
}

// Runs ML Kit's on-device Text Recognition (free, fully offline once the model is bundled
// with the app - no network call, no per-use cost) on the given image and shows the result
// in an editable dialog before inserting it as a text item on the canvas.
//
// IMPORTANT LIMITATION: this recognizes general printed/handwritten TEXT character-by-
// character. It is NOT a math-aware OCR engine - it has no understanding of equation
// structure (fractions, exponents, square roots, summations, matrices, etc.) and will read
// math notation as a flat left-to-right sequence of characters/symbols. True structured math
// recognition (the kind that outputs real LaTeX for fractions, exponents, etc.) is a
// significantly harder problem that, as far as free/offline-capable options go, doesn't have
// a good solution at the moment - the well-known accurate engines (e.g. Mathpix) are
// cloud-based paid services. Simple expressions with no special structure (e.g. "x + 2 = 5")
// will often come through fine.
internal fun MainActivity.runOcrOnUri(uri: Uri) {
    val progress = android.app.ProgressDialog(this).apply { setMessage("Reading text..."); setCancelable(false); show() }
    try {
        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                progress.dismiss()
                showOcrResultDialog(result.text)
            }
            .addOnFailureListener { e ->
                progress.dismiss()
                Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    } catch (e: Exception) {
        progress.dismiss()
        Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.showOcrResultDialog(text: String) {
    if (text.isBlank()) { Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show(); return }
    val et = EditText(this).apply {
        setText(text); minLines = 4; maxLines = 14; gravity = Gravity.TOP or Gravity.START
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    val scroll = ScrollView(this).apply { addView(et) }
    AlertDialog.Builder(this).setTitle("Extracted Text (edit if needed)").setView(scroll)
        .setPositiveButton("Insert") { _, _ ->
            val finalText = et.text.toString()
            if (finalText.isNotBlank()) {
                drawingView.addText(finalText, drawingView.screenCenterWorldX(), drawingView.screenCenterWorldY(), drawingView.defaultTextSize, 0f, drawingView.currentColor)
                Toast.makeText(this, "Text inserted", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("Cancel", null).show()
}

internal fun MainActivity.showOcrSourceDialog() {
    AlertDialog.Builder(this).setTitle("Extract Text From")
        .setItems(arrayOf("Photo (camera)", "Image from Gallery", "Snip from PDF")) { _, i ->
            when (i) {
                0 -> launchCameraForOcr()
                1 -> pickImageForOcrLauncher.launch("image/*")
                2 -> pickPdfForOcrLauncher.launch("application/pdf")
            }
        }.show()
}
