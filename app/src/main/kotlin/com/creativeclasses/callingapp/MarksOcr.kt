package com.creativeclasses.callingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reads a marklist image on-device and returns one text line per table row, top to bottom. */
object MarksOcr {
    private const val TARGET_WIDTH = 1400
    private const val LABEL = "Marks"
    private val MARK_CELL = Regex("^(\\d{1,3}|AB)$", RegexOption.IGNORE_CASE)

    /** Small screenshots (e.g. from WhatsApp) are upscaled: digits are recognised far more reliably. */
    private fun loadScaled(context: Context, uri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("Could not open the image.")
        if (bitmap.width >= TARGET_WIDTH) return bitmap
        val scale = TARGET_WIDTH.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, TARGET_WIDTH, (bitmap.height * scale).toInt(), true)
    }

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun readRows(context: Context, uri: Uri): List<String> {
        val page = loadScaled(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val lines = recognize(recognizer, page).textBlocks.flatMap { it.lines }
                .mapNotNull { l -> l.boundingBox?.let { l.text to it } }
            if (lines.isEmpty()) return emptyList()
            val tolerance = lines.map { it.second.height() }.sorted()[lines.size / 2] * 0.6f
            val rows = mutableListOf<MutableList<Pair<String, Rect>>>()
            var rowY = Float.NEGATIVE_INFINITY
            for (line in lines.sortedBy { it.second.exactCenterY() }) {
                if (rows.isEmpty() || line.second.exactCenterY() - rowY > tolerance) {
                    rows += mutableListOf(line)
                    rowY = line.second.exactCenterY()
                } else {
                    rows.last() += line
                }
            }
            val columns = marksColumns(rows)
            return rows.map { row ->
                val cells = row.sortedBy { it.second.left }.toMutableList()
                for ((i, column) in columns.withIndex()) {
                    // Only this table's cells: right of the previous table's marks column.
                    val from = columns.getOrNull(i - 1)?.let { it.centre + it.halfWidth } ?: Float.NEGATIVE_INFINITY
                    val table = cells.filter { it.second.exactCenterX() > from && it.second.left < column.centre + column.halfWidth }
                    val inColumn = table.filter { column.overlaps(it.second) }
                    if (table.none { isName(it.first) }) continue
                    // Lone digits are sometimes dropped or misread as a letter (e.g. "9" as "o"); read those cells again.
                    if (inColumn.any { MARK_CELL.matches(it.first.trim().substringAfterLast(' ')) }) continue
                    val recovered = rereadCell(recognizer, page, column, row) ?: continue
                    val last = inColumn.lastOrNull()
                    if (last == null) {
                        cells += recovered to column.box(row.first().second)
                    } else {
                        cells[cells.indexOf(last)] = "${last.first.trim().substringBeforeLast(' ', "")} $recovered".trim() to last.second
                    }
                }
                cells.sortedBy { it.second.left }.joinToString(" ") { it.first }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun isName(text: String) = text.count { it.isLetter() } >= 2 && !MARK_CELL.matches(text.trim())

    /**
     * One marks column per table, found from the cells read just after a name in each row (serials come before names).
     * Positions are grouped by gaps wider than a few cell heights; groups seen in too few rows are noise. Empty if unsure.
     */
    private fun marksColumns(rows: List<List<Pair<String, Rect>>>): List<Column> {
        val marks = rows.flatMap { row ->
            row.sortedBy { it.second.left }.zipWithNext()
                .filter { (a, b) -> isName(a.first) && MARK_CELL.matches(b.first.trim()) }
                .map { it.second.second }
        }
        if (marks.size < 3) return emptyList()
        val height = marks.map { it.height() }.sorted()[marks.size / 2]
        val groups = mutableListOf<MutableList<Rect>>()
        for (r in marks.sortedBy { it.exactCenterX() }) {
            if (groups.isEmpty() || r.exactCenterX() - groups.last().last().exactCenterX() > height * 3) groups += mutableListOf(r) else groups.last() += r
        }
        val minimum = maxOf(3, groups.maxOf { it.size } / 4)
        return groups.filter { it.size >= minimum }.map { g -> Column(g.map { it.exactCenterX() }.sorted()[g.size / 2], height) }
    }

    private class Column(val centre: Float, val height: Int) {
        val halfWidth get() = height * 1.8f
        fun overlaps(r: Rect) = r.right > centre - halfWidth && r.left < centre + halfWidth
        fun box(row: Rect) = Rect((centre - height).toInt(), row.top, (centre + height).toInt(), row.bottom)
    }

    /**
     * The full-page pass drops some lone single digits (e.g. "9"). Reading the cell again beside a printed label
     * recovers them; two renderings must agree, otherwise the cell is left blank for the teacher to type.
     */
    private suspend fun rereadCell(recognizer: TextRecognizer, page: Bitmap, column: Column, row: List<Pair<String, Rect>>): String? {
        val centreY = row.map { it.second.exactCenterY() }.average().toFloat()
        val half = column.height * 1.1f
        val box = Rect(
            (column.centre - column.halfWidth).toInt().coerceAtLeast(0),
            (centreY - half).toInt().coerceAtLeast(0),
            (column.centre + column.halfWidth).toInt().coerceAtMost(page.width),
            (centreY + half).toInt().coerceAtMost(page.height),
        )
        if (box.width() < 10 || box.height() < 10) return null
        val cell = Bitmap.createBitmap(page, box.left, box.top, box.width(), box.height())
        val reads = listOf(false, true).map { binarise ->
            val text = recognize(recognizer, labelled(cell, binarise)).text
            text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.equals(LABEL, ignoreCase = true) }
                .singleOrNull()?.takeIf { MARK_CELL.matches(it) }
        }
        return reads.first()?.takeIf { it == reads.last() }
    }

    private fun labelled(cell: Bitmap, binarise: Boolean): Bitmap {
        val pad = cell.height
        val labelWidth = cell.height * 4
        val out = Bitmap.createBitmap(cell.width + 2 * pad + labelWidth, cell.height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            val paint = Paint().apply { textSize = cell.height * 0.7f; color = Color.BLACK; isAntiAlias = true }
            drawText(LABEL, pad.toFloat(), pad + cell.height * 0.75f, paint)
            drawBitmap(cell, (pad + labelWidth).toFloat(), pad.toFloat(), null)
        }
        if (binarise) {
            val px = IntArray(out.width * out.height)
            out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
            for (i in px.indices) {
                val p = px[i]
                val luminance = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                px[i] = if (luminance < 140) Color.BLACK else Color.WHITE
            }
            out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        }
        return Bitmap.createScaledBitmap(out, out.width * 2, out.height * 2, true)
    }
}
