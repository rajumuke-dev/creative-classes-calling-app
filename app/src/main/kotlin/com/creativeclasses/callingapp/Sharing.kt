package com.creativeclasses.callingapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.creativeclasses.report.PhoneNumbers
import com.creativeclasses.report.ReportShare
import com.creativeclasses.report.SessionWorkbook
import java.io.File
import java.io.IOException

private const val WHATSAPP_PACKAGE = "com.whatsapp"

private const val PAGE_WIDTH = 595 // A4 in points
private const val PAGE_HEIGHT = 842
private const val PAGE_MARGIN = 40

/**
 * Opens the share flow only; the teacher picks the chat and presses Send manually. WhatsApp cuts shared text at
 * 4,096 characters, so a longer report goes as one PDF with the full text and the summary as caption.
 */
fun shareReport(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
    if (ReportShare.fitsOneMessage(text)) {
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, text)
    } else {
        val file = try {
            writeReportPdf(context, text)
        } catch (e: IOException) {
            Toast.makeText(context, "Could not create the report PDF. Please try again.", Toast.LENGTH_LONG).show()
            return
        }
        send.type = "application/pdf"
        send.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        ReportShare.caption(text)?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent(send).setPackage(WHATSAPP_PACKAGE))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(send, "Share report"))
    }
}

/** Lays the report text out on A4 pages, unchanged, and writes it to the shareable exports folder. */
private fun writeReportPdf(context: Context, text: String): File {
    val width = PAGE_WIDTH - 2 * PAGE_MARGIN
    val pageText = PAGE_HEIGHT - 2 * PAGE_MARGIN
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
    val pdf = PdfDocument()
    try {
        var line = 0
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line + 1
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= pageText) end++
            // Break at the last blank line so a student's name and result stay on the same page.
            if (end < layout.lineCount) {
                (end - 1 downTo line + 1).firstOrNull { text.substring(layout.getLineStart(it), layout.getLineEnd(it)).isBlank() }
                    ?.let { end = it + 1 }
            }
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdf.pages.size + 1).create())
            page.canvas.translate(PAGE_MARGIN.toFloat(), (PAGE_MARGIN - top).toFloat())
            page.canvas.clipRect(0, top, width, layout.getLineBottom(end - 1))
            layout.draw(page.canvas)
            pdf.finishPage(page)
            line = end
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles { f -> f.extension == "pdf" }?.forEach { it.delete() }
        return File(dir, ReportShare.pdfName(text)).also { file -> file.outputStream().use { pdf.writeTo(it) } }
    } finally {
        pdf.close()
    }
}

/** Builds the Excel file with [export] and opens the share sheet (WhatsApp, Drive, Gmail, ...); the teacher picks where it goes. */
fun shareExcel(context: Context, export: () -> File) {
    val file = try {
        export()
    } catch (e: IOException) {
        Toast.makeText(context, "Could not create the Excel file. Please try again.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = SessionWorkbook.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share Excel"))
}

/** Opens the SMS app with the message pre-filled; nothing is sent until the teacher presses Send. */
fun textParent(context: Context, phone: String, text: String) {
    val number = PhoneNumbers.dialable(phone) ?: return
    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", text)
    try {
        context.startActivity(sms)
    } catch (e: ActivityNotFoundException) {
        // No SMS app available on this device.
    }
}

fun dialPhone(context: Context, phone: String) {
    val number = PhoneNumbers.dialable(phone) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (e: ActivityNotFoundException) {
        // No dialer available on this device.
    }
}
