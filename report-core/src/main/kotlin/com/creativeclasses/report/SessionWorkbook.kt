package com.creativeclasses.report

import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One student line of the exported sheet; [mark] is the imported marks text ("41", "AB" or blank). */
data class SheetRow(val student: ImportedStudent, val record: CallRecord, val mark: String?)

/**
 * Minimal, dependency-free .xlsx writer for a calling session (the counterpart of [XlsxReader]).
 * Sheet "Calls": branded title, session details and colour-coded counts, then a frozen, filterable, striped table with
 * a colour-coded call status. Sheet "Report": the report text with headings, labels and students styled.
 */
object SessionWorkbook {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val ORGANISATION = "CREATIVE CLASSES"
    private const val HEADER_ROW = 5
    private const val FONT = "Arial"
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val UNSAFE_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val INVALID_XML = Regex("[^\\t\\n\\r\\x{20}-\\x{D7FF}\\x{E000}-\\x{FFFD}\\x{10000}-\\x{10FFFF}]")
    private val LABEL_LINE = Regex("^([A-Za-z][A-Za-z &/()-]{1,30}): (.+)$")
    private val NUMBERED_LINE = Regex("^\\d+\\.\\s.*")

    private val STATUS = mapOf(
        CallOutcome.CONTACTED to "Contacted",
        CallOutcome.CONCERN_RAISED to "Concern Raised",
        CallOutcome.WILL_VISIT to "Will Come to Visit",
        CallOutcome.NO_ANSWER to "Call Not Received",
        CallOutcome.CALL_LATER to "Call Later",
        CallOutcome.NOT_CALLED to "Not Called",
    )

    /** Status colours follow the app's outcome colours: a soft tint with dark text of the same hue (ordinal order). */
    private val STATUS_COLOURS = listOf(
        "DCFCE7" to "166534", // Contacted
        "FEE2E2" to "991B1B", // Concern raised
        "E0F2FE" to "075985", // Will visit
        "FFEDD5" to "9A3412", // Not received
        "FEF9C3" to "854D0E", // Call later
        "EEF2FF" to "3730A3", // Not called
    )
    private const val INDIGO = "3730A3"
    private const val INK = "1F2937"
    private const val MUTED = "6B7280"
    private const val ORANGE = "C2410C"

    // Cell style indexes into cellXfs of styles.xml (below). Table styles have a striped twin at index + 1.
    private const val TITLE = 1
    private const val DETAILS = 2
    private const val SUMMARY = 3
    private const val HEADER = 4
    private const val TEXT = 5
    private const val CENTER = 7
    private const val WRAP = 9
    private const val STUDENT = 11
    private const val FOLLOW_UP = 13
    private const val STATUS_BASE = 15
    private const val REPORT_HEADING = 21
    private const val REPORT_TEXT = 22
    private const val REPORT_ITEM = 23

    /** The session name as a safe file name, e.g. "10th SSC Unit Test.xlsx". */
    fun fileName(sessionName: String): String =
        sessionName.replace(UNSAFE_FILE_CHARS, " ").replace(Regex("\\s+"), " ").trim().trim('.').take(80)
            .ifEmpty { "Calling Session" } + ".xlsx"

    fun write(out: OutputStream, session: CallingSession, subject: String?, rows: List<SheetRow>, reportText: String?) {
        val report = reportText?.lines()?.map { it.trim() }?.takeIf { lines -> lines.any { it.isNotEmpty() } }.orEmpty()
        val sheets = listOfNotNull("Calls", "Report".takeIf { report.isNotEmpty() })
        ZipOutputStream(out).apply {
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets, callsColumns(rows).size, rows.size))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            entry("xl/worksheets/sheet1.xml", callsSheet(session, subject, rows))
            if (report.isNotEmpty()) entry("xl/worksheets/sheet2.xml", reportSheet(session, report))
            finish()
        }
    }

    private class Column(val title: String, val width: Double, val cell: (SheetRow) -> Cell)

    /** A text run with its own font, for cells mixing sizes or colours. */
    private class Run(val text: String, val size: Int, val colour: String, val bold: Boolean = false)

    private class Cell(val text: String, val style: Int, val numeric: Boolean = false, val runs: List<Run>? = null)

    /** Columns compared most often sit next to the student name; empty optional columns are left out. */
    private fun callsColumns(rows: List<SheetRow>): List<Column> {
        fun present(value: (SheetRow) -> String?) = rows.any { !value(it).isNullOrBlank() }
        return listOfNotNull(
            Column("No.", 6.0) { Cell("", CENTER) },
            Column("Student", 28.0) { Cell(it.record.studentName.ifBlank { it.student.name }, STUDENT) },
            if (present { it.mark }) Column("Marks", 10.0) { r ->
                val mark = r.mark?.trim().orEmpty()
                Cell(mark, CENTER, numeric = Marks.parse(mark).kind == MarkKind.SCORED)
            } else null,
            Column("Call Status", 21.0) { Cell(STATUS.getValue(it.record.outcome), STATUS_BASE + it.record.outcome.ordinal) },
            Column("Concerns", 22.0) { r -> Cell(r.record.concerns.joinToString(", ") { c -> c.phrase.replaceFirstChar(Char::uppercase) }, WRAP) },
            Column("Remarks", 42.0) { Cell(it.record.remark.orEmpty(), WRAP) },
            Column("Follow-up", 11.0) { Cell(if (it.record.needsFollowUp()) "Yes" else "", FOLLOW_UP) },
            Column("Parent Contact", 17.0) { Cell(it.student.contact1.orEmpty(), TEXT) },
            if (present { it.student.contact2 }) Column("Alternate Contact", 18.0) { Cell(it.student.contact2.orEmpty(), TEXT) } else null,
            if (present { it.student.group }) Column("Batch", 14.0) { Cell(it.student.group.orEmpty(), TEXT) } else null,
            if (present { it.student.school }) Column("School", 24.0) { Cell(it.student.school.orEmpty(), TEXT) } else null,
        )
    }

    private fun title(text: String) = Cell(
        "",
        TITLE,
        runs = listOf(Run("$ORGANISATION     ", 10, "C7D2FE", bold = true), Run(text, 18, "FFFFFF", bold = true)),
    )

    private fun callsSheet(session: CallingSession, subject: String?, rows: List<SheetRow>): String {
        val columns = callsColumns(rows)
        val last = colName(columns.size - 1)
        val lastRow = HEADER_ROW + rows.size
        val name = session.sessionName.trim().ifEmpty { "Calling Session" }
        val p = SessionProgress.of(rows.map { it.record })
        val details = listOf(
            listOfNotNull(session.className, session.section, session.board).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "),
            session.exam.orEmpty().trim(),
            subject.orEmpty().trim(),
            session.teacher?.trim()?.takeIf { it.isNotEmpty() }?.let { "Teacher: $it" }.orEmpty(),
            session.date.format(DATE_FORMAT),
        ).filter { it.isNotEmpty() }.joinToString("  \u2022  ")
        val counts = listOf(
            Triple("Total", p.total, INK),
            Triple("Called", p.attempted, INDIGO),
            Triple("Contacted", p.contacted, STATUS_COLOURS[0].second),
            Triple("Concern", p.concerns, STATUS_COLOURS[1].second),
            Triple("Will visit", p.willVisit, STATUS_COLOURS[2].second),
            Triple("Not received", p.noAnswer, STATUS_COLOURS[3].second),
            Triple("Call later", p.callLater, STATUS_COLOURS[4].second),
            Triple("Not called", p.notCalled, MUTED),
            Triple("Follow-ups", p.followUps, ORANGE),
        )
        val summary = counts.flatMapIndexed { i, (label, value, colour) ->
            listOfNotNull(
                Run("     ", 10, MUTED).takeIf { i > 0 },
                Run("$label ", 10, MUTED),
                Run("$value", 12, colour, bold = true),
            )
        }

        val sb = StringBuilder(XML_HEAD)
        sb.append("<worksheet xmlns=\"$NS\" xmlns:r=\"$NS_R\"><sheetPr><tabColor rgb=\"FF$INDIGO\"/><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
        sb.append("<dimension ref=\"A1:$last$lastRow\"/>")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\" zoomScale=\"110\">")
        sb.append("<pane xSplit=\"2\" ySplit=\"$HEADER_ROW\" topLeftCell=\"C${HEADER_ROW + 1}\" activePane=\"bottomRight\" state=\"frozen\"/>")
        sb.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"21\"/><cols>")
        columns.forEachIndexed { i, c -> sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${c.width}\" customWidth=\"1\"/>") }
        sb.append("</cols><sheetData>")
        sb.append(row(1, 40.0, listOf(title(name)), columns.size, TITLE))
        sb.append(row(2, 22.0, listOf(Cell(details, DETAILS)), columns.size, DETAILS))
        sb.append(row(3, 26.0, listOf(Cell("", SUMMARY, runs = summary)), columns.size, SUMMARY))
        sb.append("<row r=\"4\" ht=\"8\" customHeight=\"1\"/>")
        sb.append(row(HEADER_ROW, 28.0, columns.map { Cell(it.title, HEADER) }, columns.size, HEADER))
        rows.forEachIndexed { i, r ->
            val stripe = if (i % 2 == 1) 1 else 0
            val cells = columns.mapIndexed { c, col ->
                val cell = if (c == 0) Cell("${i + 1}", CENTER, numeric = true) else col.cell(r)
                // Status cells keep their own colour; the other styles have a striped twin at index + 1.
                if (cell.style >= STATUS_BASE) cell else Cell(cell.text, cell.style + stripe, cell.numeric)
            }
            sb.append(row(HEADER_ROW + 1 + i, null, cells, columns.size, TEXT + stripe))
        }
        sb.append("</sheetData>")
        if (rows.isNotEmpty()) sb.append("<autoFilter ref=\"A$HEADER_ROW:$last$lastRow\"/>")
        sb.append("<mergeCells count=\"3\"><mergeCell ref=\"A1:${last}1\"/><mergeCell ref=\"A2:${last}2\"/><mergeCell ref=\"A3:${last}3\"/></mergeCells>")
        sb.append(PAGE_SETUP)
        sb.append("<headerFooter><oddFooter>&amp;L${esc(name)}&amp;RPage &amp;P of &amp;N</oddFooter></headerFooter></worksheet>")
        return sb.toString()
    }

    /** Headings ("Overall Status:"), "Label: value" lines and numbered students are styled; other lines wrap as text. */
    private fun reportSheet(session: CallingSession, lines: List<String>): String {
        val sb = StringBuilder(XML_HEAD)
        sb.append("<worksheet xmlns=\"$NS\" xmlns:r=\"$NS_R\"><sheetPr><tabColor rgb=\"FF0E7490\"/></sheetPr>")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"/></sheetViews>")
        sb.append("<cols><col min=\"1\" max=\"1\" width=\"100\" customWidth=\"1\"/></cols><sheetData>")
        sb.append(row(1, 40.0, listOf(title("${session.sessionName.trim().ifEmpty { "Calling Session" }} \u2013 Report")), 1, TITLE))
        var r = 3
        var blank = false
        for (line in lines) {
            if (line.isEmpty()) {
                if (!blank) sb.append("<row r=\"${r++}\" ht=\"8\" customHeight=\"1\"/>")
                blank = true
                continue
            }
            blank = false
            val label = LABEL_LINE.matchEntire(line)
            val cell = when {
                line.endsWith(":") -> Cell(line.dropLast(1), REPORT_HEADING)
                label != null -> Cell(
                    "",
                    REPORT_TEXT,
                    runs = listOf(Run("${label.groupValues[1]}:  ", 10, MUTED), Run(label.groupValues[2], 10, INK, bold = true)),
                )
                NUMBERED_LINE.matches(line) -> Cell(line, REPORT_ITEM)
                else -> Cell(line, REPORT_TEXT)
            }
            val height = if (cell.style == REPORT_HEADING) 24.0 else null
            sb.append(row(r++, height, listOf(cell), 1, REPORT_TEXT))
        }
        sb.append("</sheetData>").append(PAGE_SETUP.replace("landscape", "portrait")).append("</worksheet>")
        return sb.toString()
    }

    /** Writes [cells] from column A; the rest of the row up to [width] is padded with [fill] so merged/striped rows look whole. */
    private fun row(r: Int, height: Double?, cells: List<Cell>, width: Int, fill: Int): String {
        val sb = StringBuilder("<row r=\"$r\"")
        if (height != null) sb.append(" ht=\"$height\" customHeight=\"1\"")
        sb.append(">")
        for (c in 0 until width) {
            val cell = cells.getOrNull(c)
            val ref = "${colName(c)}$r"
            when {
                cell == null -> sb.append("<c r=\"$ref\" s=\"$fill\"/>")
                cell.runs != null -> sb.append("<c r=\"$ref\" s=\"${cell.style}\" t=\"inlineStr\"><is>")
                    .append(cell.runs.joinToString("") { run ->
                        "<r><rPr><rFont val=\"$FONT\"/>${if (run.bold) "<b/>" else ""}<color rgb=\"FF${run.colour}\"/><sz val=\"${run.size}\"/></rPr>" +
                            "<t xml:space=\"preserve\">${esc(run.text)}</t></r>"
                    })
                    .append("</is></c>")
                cell.numeric -> sb.append("<c r=\"$ref\" s=\"${cell.style}\"><v>${cell.text}</v></c>")
                cell.text.isEmpty() -> sb.append("<c r=\"$ref\" s=\"${cell.style}\"/>")
                else -> sb.append("<c r=\"$ref\" s=\"${cell.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(cell.text)}</t></is></c>")
            }
        }
        return sb.append("</row>").toString()
    }

    private fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun esc(text: String) = text.replace(INVALID_XML, "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun ZipOutputStream.entry(name: String, xml: String) {
        putNextEntry(ZipEntry(name))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(sheets: Int) = XML_HEAD +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        (1..sheets).joinToString("") {
            "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        } + "</Types>"

    private fun workbook(sheets: List<String>, columns: Int, rows: Int): String {
        val last = colName(columns - 1)
        val names = buildString {
            append("<definedNames>")
            if (rows > 0) append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">Calls!\$A\$$HEADER_ROW:\$$last\$${HEADER_ROW + rows}</definedName>")
            append("<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"0\">Calls!\$$HEADER_ROW:\$$HEADER_ROW</definedName>")
            append("</definedNames>")
        }
        return XML_HEAD + "<workbook xmlns=\"$NS\" xmlns:r=\"$NS_R\"><sheets>" +
            sheets.mapIndexed { i, s -> "<sheet name=\"$s\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") +
            "</sheets>$names</workbook>"
    }

    private fun workbookRels(sheets: Int) = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        (1..sheets).joinToString("") {
            "<Relationship Id=\"rId$it\" Type=\"$NS_R/worksheet\" Target=\"worksheets/sheet$it.xml\"/>"
        } + "<Relationship Id=\"rId${sheets + 1}\" Type=\"$NS_R/styles\" Target=\"styles.xml\"/></Relationships>"

    private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PAGE_SETUP = "<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>" +
        "<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>"

    private const val ROOT_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_R/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

    private fun font(size: Int, colour: String, bold: Boolean = false, italic: Boolean = false) =
        "<font>${if (bold) "<b/>" else ""}${if (italic) "<i/>" else ""}<sz val=\"$size\"/><color rgb=\"FF$colour\"/><name val=\"$FONT\"/></font>"

    private fun fill(colour: String) = "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$colour\"/><bgColor indexed=\"64\"/></patternFill></fill>"

    private fun xf(font: Int, fill: Int, border: Int, h: String, wrap: Boolean = false, v: String = "center", indent: Int = 0) =
        "<xf numFmtId=\"0\" fontId=\"$font\" fillId=\"$fill\" borderId=\"$border\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
            "<alignment horizontal=\"$h\" vertical=\"$v\"${if (wrap) " wrapText=\"1\"" else ""}${if (indent > 0) " indent=\"$indent\"" else ""}/></xf>"

    private val STYLES: String = run {
        val fonts = listOf(
            font(10, INK), // 0 body
            font(18, "FFFFFF", bold = true), // 1 title
            font(10, "4B5563", italic = true), // 2 details
            font(10, MUTED), // 3 summary
            font(10, "FFFFFF", bold = true), // 4 header
        ) + STATUS_COLOURS.map { font(10, it.second, bold = true) } + // 5..10 status
            listOf(
                font(10, "111827", bold = true), // 11 student / numbered report line
                font(10, ORANGE, bold = true), // 12 follow-up
                font(12, INDIGO, bold = true), // 13 report heading
            )
        val fills = listOf(
            "<fill><patternFill patternType=\"none\"/></fill>",
            "<fill><patternFill patternType=\"gray125\"/></fill>",
            fill(INDIGO), // 2 banner/header
            fill("F6F7FB"), // 3 stripe
            fill("EEF2FF"), // 4 details/summary band
        ) + STATUS_COLOURS.map { fill(it.first) } // 5..10 status
        val borders = listOf(
            "<border><left/><right/><top/><bottom/><diagonal/></border>",
            "<border><left style=\"thin\"><color rgb=\"FFE5E7EB\"/></left><right style=\"thin\"><color rgb=\"FFE5E7EB\"/></right>" +
                "<top style=\"thin\"><color rgb=\"FFE5E7EB\"/></top><bottom style=\"thin\"><color rgb=\"FFE5E7EB\"/></bottom><diagonal/></border>",
            "<border><left/><right/><top/><bottom style=\"medium\"><color rgb=\"FFC7D2FE\"/></bottom><diagonal/></border>",
        )
        val xfs = listOf(
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>", // 0 default
            xf(1, 2, 0, "left", indent = 1), // 1 title
            xf(2, 4, 0, "left", indent = 1), // 2 details
            xf(3, 4, 0, "left", indent = 1), // 3 summary
            xf(4, 2, 1, "center", wrap = true), // 4 header
            xf(0, 0, 1, "left", indent = 1), xf(0, 3, 1, "left", indent = 1), // 5, 6 text
            xf(0, 0, 1, "center"), xf(0, 3, 1, "center"), // 7, 8 centred
            xf(0, 0, 1, "left", wrap = true, indent = 1), xf(0, 3, 1, "left", wrap = true, indent = 1), // 9, 10 wrapped
            xf(11, 0, 1, "left", indent = 1), xf(11, 3, 1, "left", indent = 1), // 11, 12 student
            xf(12, 0, 1, "center"), xf(12, 3, 1, "center"), // 13, 14 follow-up
        ) + STATUS_COLOURS.indices.map { xf(5 + it, 5 + it, 1, "center") } + // 15..20 status
            listOf(
                xf(13, 0, 2, "left", v = "bottom"), // 21 report heading
                xf(0, 0, 0, "left", wrap = true, v = "top"), // 22 report text
                xf(11, 0, 0, "left", wrap = true, v = "top"), // 23 report numbered line
            )
        XML_HEAD + "<styleSheet xmlns=\"$NS\">" +
            "<fonts count=\"${fonts.size}\">${fonts.joinToString("")}</fonts>" +
            "<fills count=\"${fills.size}\">${fills.joinToString("")}</fills>" +
            "<borders count=\"${borders.size}\">${borders.joinToString("")}</borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"${xfs.size}\">${xfs.joinToString("")}</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"
    }
}
