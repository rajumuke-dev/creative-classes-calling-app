package com.creativeclasses.callingapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.creativeclasses.report.PhoneNumbers
import com.creativeclasses.report.SessionWorkbook
import java.io.File
import java.io.IOException

private const val WHATSAPP_PACKAGE = "com.whatsapp"

/** Opens the share flow only; the teacher picks the chat and presses Send manually. */
fun shareReport(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent(send).setPackage(WHATSAPP_PACKAGE))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(send, "Share report"))
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
