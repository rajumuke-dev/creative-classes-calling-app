package com.creativeclasses.report

/** Text pre-filled for the teacher when a parent did not pick up. The teacher reviews it and presses Send in the SMS app. */
object ParentMessage {
    /** Signs as [teacher] when set, otherwise as the institute; adds when the parent can call back if [availability] is set. */
    fun notReached(studentName: String, teacher: String = "", availability: String = "", organisation: String = "Creative Classes"): String {
        val about = studentName.tidy().ifEmpty { "your child" }
        val name = teacher.tidy()
        val hours = availability.tidy().trimEnd('.').replace(Regex("^available\\s*", RegexOption.IGNORE_CASE), "")
        val (intro, callBack, available, signature) = if (name.isEmpty()) {
            listOf("This is $organisation. We tried", "call us back", "We are available", organisation)
        } else {
            listOf("This is $name from $organisation. I tried", "call me back", "I am available", "$name\n$organisation")
        }
        return "Dear Parent,\n\n$intro to call you regarding $about but could not reach you. Kindly $callBack at your convenience." +
            (if (hours.isEmpty()) "" else " $available $hours.") + "\n\nThank you,\n$signature"
    }

    private fun String.tidy() = trim().replace(Regex("\\s+"), " ")
}
