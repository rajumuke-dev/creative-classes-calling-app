package com.creativeclasses.report

enum class MarkKind { NONE, SCORED, ABSENT }

data class Mark(val kind: MarkKind, val score: Double? = null)

/** Marks are kept as the text the teacher sees ("41", "AB", "-") and interpreted here. */
object Marks {
    private val NUMBER = Regex("""\d{1,3}(\.\d{1,2})?""")
    private val ABSENT = setOf("AB", "ABS", "ABSENT")

    fun parse(text: String?): Mark {
        val t = text?.trim().orEmpty()
        return when {
            t.uppercase() in ABSENT -> Mark(MarkKind.ABSENT)
            NUMBER.matches(t) -> Mark(MarkKind.SCORED, t.toDouble())
            else -> Mark(MarkKind.NONE)
        }
    }

    /** Absent and blank students have no score to rank, so they are never "low". */
    fun isLow(text: String?, maxMarks: Double?, cutoffPercent: Double): Boolean {
        val mark = parse(text)
        val score = mark.score ?: return false
        return maxMarks != null && maxMarks > 0 && score / maxMarks * 100 < cutoffPercent
    }

    fun isAbsent(text: String?) = parse(text).kind == MarkKind.ABSENT
}
