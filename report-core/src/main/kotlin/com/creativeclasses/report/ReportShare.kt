package com.creativeclasses.report

/**
 * WhatsApp keeps only the first 4,096 characters of shared text, so a longer report is shared as one PDF document
 * with its summary as the caption, the way school and banking apps send long reports.
 */
object ReportShare {
    const val MAX_MESSAGE_CHARS = 4000
    private const val MAX_CAPTION_CHARS = 1000
    private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9 ._-]")

    fun fitsOneMessage(text: String) = text.length <= MAX_MESSAGE_CHARS

    /** The summary above the student list plus a pointer to the PDF; null when there is no short summary. */
    fun caption(text: String): String? = text.substringBefore("\n\nKey Feedback", "").trim()
        .takeIf { it.isNotEmpty() && it.length <= MAX_CAPTION_CHARS }
        ?.let { "$it\n\nFull report with every student is in the attached PDF." }

    /** "Parent Calling Report - <class> - <date>.pdf" from the report's Class and Date lines. */
    fun pdfName(text: String): String {
        val lines = text.lines()
        val parts = listOf("Class: ", "Date: ").mapNotNull { key -> lines.firstOrNull { it.startsWith(key) }?.removePrefix(key)?.trim() }
        return (listOf("Parent Calling Report") + parts).joinToString(" - ").replace(UNSAFE_FILE_CHARS, " ")
            .replace(Regex("\\s+"), " ").trim().take(100) + ".pdf"
    }
}
