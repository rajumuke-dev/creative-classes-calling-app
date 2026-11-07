package com.creativeclasses.report

/** Text pre-filled for the admin when a parent did not pick up. The admin reviews it and presses Send in the SMS app. */
object ParentMessage {
    /** Sent on behalf of the institute; when [availability] is set, tells the parent when the [teacher] can be reached. */
    fun notReached(
        studentName: String,
        teacher: String = "",
        availability: String = "",
        organisation: String = "Creative Classes",
        subject: String = "",
        exam: String = "",
        mark: String? = null,
    ): String {
        val about = studentName.tidy().ifEmpty { "your child" }
        val markLine = markLine(about, subject.tidy().trimEnd('.'), exam.tidy().trimEnd('.'), mark)
        val name = teacher.tidy()
        val hours = availability.tidy().trimEnd('.').replace(Regex("^available\\s*", RegexOption.IGNORE_CASE), "")
        val teacherLine = when {
            hours.isEmpty() -> ""
            name.isEmpty() -> "\n\nThe teacher is available $hours. Please do visit the class."
            else -> "\n\n$name is available $hours. Please do visit the class."
        }
        return "Dear Parent,\n\nThis is $organisation. We tried to call you regarding $about but could not reach you.$markLine " +
            "Kindly call us back at your convenience.$teacherLine\n\nThank you,\n$organisation"
    }

    /** E.g. " Asha Rao scored 18 marks in History (Unit Test 1)."; empty when no mark was imported for the student. */
    private fun markLine(about: String, subject: String, exam: String, mark: String?): String {
        val test = when {
            subject.isNotEmpty() && exam.isNotEmpty() -> "$subject ($exam)"
            else -> subject.ifEmpty { exam }
        }
        val who = about.replaceFirstChar { it.uppercase() }
        val parsed = Marks.parse(mark)
        return when (parsed.kind) {
            MarkKind.SCORED -> " $who scored ${mark.orEmpty().trim()} marks" + (if (test.isEmpty()) "." else " in $test.")
            MarkKind.ABSENT -> " $who was absent for " + test.ifEmpty { "the exam" } + "."
            MarkKind.NONE -> ""
        }
    }

    private fun String.tidy() = trim().replace(Regex("\\s+"), " ")
}
