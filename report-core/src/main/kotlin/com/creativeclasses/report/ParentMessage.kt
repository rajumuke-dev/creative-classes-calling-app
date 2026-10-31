package com.creativeclasses.report

/** Text pre-filled for the teacher when a parent did not pick up. The teacher reviews it and presses Send in WhatsApp. */
object ParentMessage {
    fun notReached(studentName: String, organisation: String = "Creative Classes"): String {
        val name = studentName.trim().replace(Regex("\\s+"), " ")
        val about = if (name.isEmpty()) "your child" else name
        return "Dear Parent,\n\nThis is $organisation. We tried to call you regarding $about but could not reach you. " +
            "Kindly call us back at your convenience.\n\nThank you,\n$organisation"
    }
}
