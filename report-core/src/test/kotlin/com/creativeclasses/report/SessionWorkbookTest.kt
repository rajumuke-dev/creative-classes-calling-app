package com.creativeclasses.report

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionWorkbookTest {
    private val session = CallingSession(
        sessionName = "10th SSC Unit Test",
        className = "10th",
        board = "SSC",
        exam = "Unit Test 1",
        purpose = CallingPurpose.EXAM_RESULT,
        date = LocalDate.of(2026, 11, 7),
        teacher = "Priya",
    )

    private val rows = listOf(
        SheetRow(ImportedStudent("Asha Rao", "9876543210", null), CallRecord("Asha Rao", CallOutcome.CONTACTED), "19"),
        SheetRow(
            ImportedStudent("Ravi <Kumar> & Sons", "9876543211", null),
            CallRecord("Ravi <Kumar> & Sons", CallOutcome.CONCERN_RAISED, setOf(Concern.LOW_MARKS), remark = "Needs extra \"maths\" class"),
            "AB",
        ),
        SheetRow(ImportedStudent("Meera", "9876543212", null), CallRecord("Meera", CallOutcome.NO_ANSWER), ""),
    )

    private fun bytes(rows: List<SheetRow>, report: String? = "Line one.\n\nLine two.") =
        ByteArrayOutputStream().also { SessionWorkbook.write(it, session, "History", rows, report) }.toByteArray()

    @Test
    fun `calls sheet holds every student with status, marks and contact`() {
        val book = XlsxReader.open(ByteArrayInputStream(bytes(rows)))
        assertEquals(listOf("Calls", "Report"), book.sheetNames)
        val table = book.rows(0)
        assertEquals("CREATIVE CLASSES     10th SSC Unit Test", table[0][0])
        assertEquals("10th SSC  \u2022  Unit Test 1  \u2022  History  \u2022  Teacher: Priya  \u2022  7 November 2026", table[1][0])
        assertEquals(
            "Total 3     Called 3     Contacted 1     Concern 1     Will visit 0     Not received 1     Call later 0     Not called 0     Follow-ups 1",
            table[2][0],
        )
        assertEquals(listOf("No.", "Student", "Marks", "Call Status", "Concerns", "Remarks", "Follow-up", "Parent Contact"), table[4])
        assertEquals(listOf("1", "Asha Rao", "19", "Contacted", "", "", "", "9876543210"), table[5])
        assertEquals(
            listOf("2", "Ravi <Kumar> & Sons", "AB", "Concern Raised", "Low marks", "Needs extra \"maths\" class", "", "9876543211"),
            table[6],
        )
        assertEquals(listOf("3", "Meera", "", "Call Not Received", "", "", "Yes", "9876543212"), table[7])
        assertEquals(listOf("CREATIVE CLASSES     10th SSC Unit Test \u2013 Report", "Line one.", "Line two."), book.rows(1).map { it.joinToString("") }.filter { it.isNotEmpty() })
    }

    @Test
    fun `optional columns appear only when some student has them`() {
        val withExtras = rows.take(1).map { it.copy(student = it.student.copy(contact2 = "9123456789", group = "A", school = "City School")) }
        val header = XlsxReader.open(ByteArrayInputStream(bytes(withExtras))).rows(0)[4]
        assertEquals(listOf("Alternate Contact", "Batch", "School"), header.takeLast(3))
        val noMarks = rows.map { it.copy(mark = null) }
        assertFalse("Marks" in XlsxReader.open(ByteArrayInputStream(bytes(noMarks))).rows(0)[4])
    }

    @Test
    fun `report sheet is left out without report text and table is frozen and filterable`() {
        val data = bytes(rows, report = "  ")
        assertEquals(listOf("Calls"), XlsxReader.open(ByteArrayInputStream(data)).sheetNames)
        val sheet = entry(data, "xl/worksheets/sheet1.xml")
        assertTrue("state=\"frozen\"" in sheet && "topLeftCell=\"C6\"" in sheet)
        assertTrue("<autoFilter ref=\"A5:H8\"/>" in sheet)
        assertTrue("&lt;Kumar&gt; &amp; Sons" in sheet)
    }

    @Test
    fun `report sheet styles headings, labels and numbered students`() {
        val data = bytes(rows, report = "Total Parents: 3\n\nKey Feedback & Follow-ups:\n1. Asha Rao\nParent will visit.")
        val lines = XlsxReader.open(ByteArrayInputStream(data)).rows(1).map { it.joinToString("") }.filter { it.isNotEmpty() }
        assertEquals(listOf("Total Parents:  3", "Key Feedback & Follow-ups", "1. Asha Rao", "Parent will visit."), lines.drop(1))
        val sheet = entry(data, "xl/worksheets/sheet2.xml")
        assertTrue("s=\"21\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Key Feedback &amp; Follow-ups</t>" in sheet)
        assertTrue("s=\"23\" t=\"inlineStr\"><is><t xml:space=\"preserve\">1. Asha Rao</t>" in sheet)
        assertTrue("<b/><color rgb=\"FF1F2937\"/><sz val=\"10\"/></rPr><t xml:space=\"preserve\">3</t>" in sheet)
    }

    @Test
    fun `blank section leaves no double space in the details line`() {
        val data = ByteArrayOutputStream().also { SessionWorkbook.write(it, session.copy(section = " "), null, rows, null) }.toByteArray()
        assertEquals("10th SSC  \u2022  Unit Test 1  \u2022  Teacher: Priya  \u2022  7 November 2026", XlsxReader.open(ByteArrayInputStream(data)).rows(0)[1][0])
    }

    @Test
    fun `file name is the session name made safe`() {
        assertEquals("10th SSC Unit Test.xlsx", SessionWorkbook.fileName(" 10th  SSC Unit Test "))
        assertEquals("Class 8 A B Marks.xlsx", SessionWorkbook.fileName("Class 8 A/B: Marks?"))
        assertEquals("Calling Session.xlsx", SessionWorkbook.fileName("  /..  "))
    }

    private fun entry(data: ByteArray, name: String): String = ZipInputStream(ByteArrayInputStream(data)).use { zip ->
        generateSequence { zip.nextEntry }.first { it.name == name }
        zip.readBytes().toString(Charsets.UTF_8)
    }
}
