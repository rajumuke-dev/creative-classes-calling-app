package com.creativeclasses.callingapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.creativeclasses.report.PhoneNumbers

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

fun dialPhone(context: Context, phone: String) {
    val number = PhoneNumbers.dialable(phone) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (e: ActivityNotFoundException) {
        // No dialer available on this device.
    }
}
