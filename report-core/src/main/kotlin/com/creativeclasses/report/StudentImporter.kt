package com.creativeclasses.report

/** [group] is the sheet's Publication / Medium column and [school] its School Name column; neither goes into reports. */
data class ImportedStudent(
    val name: String,
    val contact1: String?,
    val contact2: String?,
    val group: String? = null,
    val school: String? = null,
)

data class SheetInfo(val className: String?, val board: String?)

object PhoneNumbers {
    private fun digits(raw: String) = raw.filter { it.isDigit() }

    /** 10 digits, optionally prefixed with 91; only digits, spaces, +, - and brackets are allowed. */
    fun isValid(raw: String): Boolean {
        if (!raw.all { it.isDigit() || it in " +-()" }) return false
        val d = digits(raw)
        return d.length == 10 || (d.length == 12 && d.startsWith("91"))
    }

    fun dialable(raw: String): String? = if (isValid(raw)) digits(raw) else null
}

object StudentImporter {
    private val NAME = Regex("(?i)(student\\s*name|name\\s*of\\s*(the\\s*)?student|student|name)")
    private val PHONE = Regex("(?i)(contact|phone|mobile|whatsapp).*")
    private val GROUP = Regex("(?i)(publication|medium|med)")
    private val SCHOOL = Regex("(?i)school.*")
    private val CLASS_TOKEN = Regex("(?i)\\b(\\d{1,2})\\s*(st|nd|rd|th)\\b")
    private val SPACES = Regex("\\s+")

    private class Columns(val name: Int, val phones: List<Int>, val group: Int?, val school: Int?)

    /**
     * Sheets may hold several groups, each with its own title and header row, so every header row
     * (re)defines the columns and title rows (no name cell) are skipped.
     */
    fun fromRows(rows: List<List<String>>): List<ImportedStudent> {
        var cols: Columns? = null
        var sawHeader = false
        val students = mutableListOf<ImportedStudent>()
        for (row in rows) {
            val header = columnsOf(row)
            if (header != null) {
                cols = header
                sawHeader = true
                continue
            }
            val c = cols ?: continue
            val name = cell(row, c.name) ?: continue
            students += ImportedStudent(
                name = name,
                contact1 = cell(row, c.phones.getOrNull(0)),
                contact2 = cell(row, c.phones.getOrNull(1)),
                group = cell(row, c.group),
                school = cell(row, c.school),
            )
        }
        require(sawHeader) { "Could not find a 'Name Of Student' column in this sheet." }
        require(students.isNotEmpty()) { "No students found in this sheet." }
        return students
    }

    /** "8th CBSE ALL" -> class "8th", board "CBSE". A part that is not found is left null. */
    fun parseSheetName(sheetName: String): SheetInfo {
        val m = CLASS_TOKEN.find(sheetName)
        val className = m?.let { it.groupValues[1] + it.groupValues[2].lowercase() }
        val rest = if (m != null) sheetName.removeRange(m.range) else sheetName
        val board = rest.split(SPACES).filter { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
            .joinToString(" ").ifBlank { null }
        return SheetInfo(className, board)
    }

    private fun columnsOf(row: List<String>): Columns? {
        val name = row.indexOfFirst { NAME.matches(it.trim()) }.takeIf { it >= 0 } ?: return null
        fun all(rx: Regex) = row.indices.filter { rx.matches(row[it].trim()) }
        return Columns(name, all(PHONE), all(GROUP).firstOrNull(), all(SCHOOL).firstOrNull())
    }

    private fun cell(row: List<String>, c: Int?) = c?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
}
