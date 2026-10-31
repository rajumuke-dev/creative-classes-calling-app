package com.creativeclasses.report

data class ImportedStudent(val name: String, val phone: String?)

/** [className], [board] and [exam] are pre-filled only when the sheet has a column with exactly one distinct value. */
data class ImportResult(
    val students: List<ImportedStudent>,
    val className: String? = null,
    val board: String? = null,
    val exam: String? = null,
)

object StudentImporter {
    private val NAME = Regex("(?i)(student(\\s*name)?|name)")
    private val PHONE = Regex("(?i).*(phone|mobile|contact|whatsapp).*")
    private val CLASS = Regex("(?i)(class|standard|std)")
    private val BOARD = Regex("(?i)board")
    private val EXAM = Regex("(?i)exam.*")

    fun fromRows(rows: List<List<String>>): ImportResult {
        val headerIdx = rows.indexOfFirst { r -> r.any { NAME.matches(it.trim()) } }
        require(headerIdx >= 0) { "Could not find a 'Student Name' column in the first sheet." }
        val header = rows[headerIdx]
        fun col(rx: Regex) = header.indexOfFirst { rx.matches(it.trim()) }.takeIf { it >= 0 }
        val nameCol = col(NAME)!!
        val phoneCol = col(PHONE)
        val dataRows = rows.drop(headerIdx + 1)

        fun cell(row: List<String>, c: Int?) = c?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
        fun single(c: Int?) = c?.let { dataRows.mapNotNull { r -> cell(r, it) }.distinctBy { v -> v.lowercase() } }
            ?.singleOrNull()

        val students = dataRows.mapNotNull { r ->
            cell(r, nameCol)?.let { ImportedStudent(it, cell(r, phoneCol)) }
        }
        require(students.isNotEmpty()) { "No students found below the header row." }
        return ImportResult(students, single(col(CLASS)), single(col(BOARD)), single(col(EXAM)))
    }
}
