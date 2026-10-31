package com.creativeclasses.callingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reads a marklist image on-device and returns one text line per table row, top to bottom. */
object MarksOcr {
    private const val TARGET_WIDTH = 1400

    /** Small screenshots (e.g. from WhatsApp) are upscaled: digits are recognised far more reliably. */
    private fun loadScaled(context: Context, uri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("Could not open the image.")
        if (bitmap.width >= TARGET_WIDTH) return bitmap
        val scale = TARGET_WIDTH.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, TARGET_WIDTH, (bitmap.height * scale).toInt(), true)
    }

    suspend fun readRows(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromBitmap(loadScaled(context, uri), 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { l.text to it } }
            if (lines.isEmpty()) return emptyList()
            val tolerance = lines.map { it.second.height() }.sorted()[lines.size / 2] * 0.6f
            val rows = mutableListOf<MutableList<Pair<String, android.graphics.Rect>>>()
            var rowY = Float.NEGATIVE_INFINITY
            for (line in lines.sortedBy { it.second.exactCenterY() }) {
                if (rows.isEmpty() || line.second.exactCenterY() - rowY > tolerance) {
                    rows += mutableListOf(line)
                    rowY = line.second.exactCenterY()
                } else {
                    rows.last() += line
                }
            }
            return rows.map { row -> row.sortedBy { it.second.left }.joinToString(" ") { it.first } }
        } finally {
            recognizer.close()
        }
    }
}
