package com.creativeclasses.report

/** Text pre-filled for the admin when a parent did not pick up. The admin reviews it and presses Send in the SMS app. */
object ParentMessage {
    /** Sent on behalf of the institute; when [availability] is set, tells the parent when the [teacher] can be reached. */
    fun notReached(studentName: String, teacher: String = "", availability: String = "", organisation: String = "Creative Classes"): String {
        val about = studentName.tidy().ifEmpty { "your child" }
        val name = teacher.tidy()
        val hours = availability.tidy().trimEnd('.').replace(Regex("^available\\s*", RegexOption.IGNORE_CASE), "")
        val teacherLine = when {
            hours.isEmpty() -> ""
            name.isEmpty() -> "\n\nThe teacher is available $hours."
            else -> "\n\nTeacher $name is available $hours."
        }
        return "Dear Parent,\n\nThis is $organisation. We tried to call you regarding $about but could not reach you. " +
            "Kindly call us back at your convenience.$teacherLine\n\nThank you,\n$organisation"
    }

    private fun String.tidy() = trim().replace(Regex("\\s+"), " ")
}
