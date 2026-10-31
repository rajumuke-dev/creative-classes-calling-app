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

/** Opens a WhatsApp chat with the message pre-filled; nothing is sent until the teacher presses Send. */
fun messageParent(context: Context, phone: String, text: String) {
    val digits = PhoneNumbers.dialable(phone) ?: return
    val number = if (digits.length == 10) "91$digits" else digits
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}"))
    try {
        context.startActivity(Intent(view).setPackage(WHATSAPP_PACKAGE))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            // No WhatsApp or browser available on this device.
        }
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
