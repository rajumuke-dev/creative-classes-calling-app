package com.creativeclasses.report

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportGeneratorTest {
    private val generator = ReportGenerator()
    private val session = CallingSession(
        sessionName = "8th CBSE Results",
        className = "8th",
        board = "CBSE",
        purpose = CallingPurpose.EXAM_RESULT,
        date = LocalDate.of(2026, 10, 31),
    )

    private fun ok(n: Int) = (1..n).map { CallRecord("Student $it", CallOutcome.CONTACTED) }

    @Test
    fun `completed session with no remarks matches expected report`() {
        val report = generator.generate(session, ok(25))
        val expected = """
            Creative Classes
            Parent Calling Report

            Date: 31 October 2026
            Class: 8th CBSE
            Purpose: Exam Result Calling

            The parent calling activity regarding the 8th CBSE examination results has been completed successfully.

            Total Parents: 25
            Parents Contacted: 25
            Follow-ups Required: 0

            All parents were informed regarding the 8th CBSE examination results and no concerns were reported.

            Key Feedback & Follow-ups:
${(1..25).joinToString("") { "\n            $it. Student $it\n            Parent contacted and informed.\n" }}
            Overall Status:
            Completed Successfully

            Prepared by:
            Creative Classes
        """.trimIndent()
        assertEquals(expected, report.text)
        assertEquals(ReportStatus.COMPLETED_SUCCESSFULLY, report.status)
    }

    @Test
    fun `concern remark is rewritten without raw field labels`() {
        val records = ok(2) + CallRecord(
            "Rahul Patil", CallOutcome.CONCERN_RAISED, setOf(Concern.LOW_MARKS),
            "Parent is concerned about mathematics marks and requested a meeting with the teacher.",
        )
        val text = generator.generate(session, records).text
        assertContains(text, "3. Rahul Patil\nParent expressed concern regarding Mathematics marks and requested a meeting with the teacher.")
        assertContains(text, "Concerns Raised: 1")
        assertContains(text, "1. Student 1\nParent contacted and informed.\n\n2. Student 2\nParent contacted and informed.")
        assertFalse(text.contains("Low Marks") || text.contains("Remark:") || text.contains("CONCERN_RAISED"))
        assertEquals(ReportStatus.COMPLETED, generator.generate(session, records).status)
    }

    @Test
    fun `every student called is completed even when some parents were not reached`() {
        val records = ok(21) + List(3) { CallRecord("NA $it", CallOutcome.NO_ANSWER) } +
            CallRecord("Later", CallOutcome.CALL_LATER)
        val report = generator.generate(session, records)
        assertEquals(ReportStatus.COMPLETED, report.status)
        assertContains(report.text, "The parent calling activity regarding the 8th CBSE examination results has been completed.")
        assertContains(report.text, "Parents Contacted: 21")
        assertContains(report.text, "Call Not Received: 3")
        assertContains(report.text, "Call Later: 1")
        assertContains(report.text, "Follow-ups Required: 4")
        assertContains(report.text, "4 parents require follow-up.")
        assertFalse(report.text.contains("Partially"))
    }

    @Test
    fun `a remark without a call result counts as done`() {
        val records = ok(2) + CallRecord("Asha Rao", CallOutcome.NOT_CALLED, remark = "Met parent at the class")
        val report = generator.generate(session, records)
        assertEquals(ReportStatus.COMPLETED_SUCCESSFULLY, report.status)
        assertContains(report.text, "Asha Rao\nMet parent at the class.")
        assertFalse(report.text.contains("Not Yet Called") || report.text.contains("not been made"))
        assertEquals(ReportStatus.PARTIALLY_COMPLETED, generator.generate(session, ok(2) + CallRecord("Ravi", CallOutcome.NOT_CALLED)).status)
    }

    @Test
    fun `student with no contact number is reported and keeps session incomplete`() {
        val records = ok(2) + CallRecord("Asha Rao", CallOutcome.NOT_CALLED, contactMissing = true)
        val text = generator.generate(session, records).text
        assertContains(text, "Contact Not Found: 1")
        assertContains(text, "Asha Rao\nContact number not found, so the parent could not be called. Follow-up call required.")
        assertContains(text, "Partially Completed")
        assertFalse(text.contains("Not Yet Called"))
        assertEquals(1, Regex("could not be called as no valid contact number was found").findAll(text).count())
    }

    @Test
    fun `parent who will visit is contacted, listed and does not block completion`() {
        val text = generator.generate(session, ok(2) + CallRecord("Asha Rao", CallOutcome.WILL_VISIT)).text
        assertContains(text, "Parents Contacted: 3")
        assertContains(text, "Will Come to Visit: 1")
        assertContains(text, "Asha Rao\nParent will come to visit the class.")
        assertContains(text, "Completed Successfully")
    }

    @Test
    fun `no answer call lists follow-up call`() {
        val report = generator.generate(session, ok(1) + CallRecord("Amit Joshi", CallOutcome.NO_ANSWER))
        assertContains(report.text, "1. Student 1\nParent contacted and informed.\n\n2. Amit Joshi\nParent could not be reached. Follow-up call required.")
        assertContains(report.text, "1 parent requires follow-up.")
    }

    @Test
    fun `flagged follow-up on contacted parent`() {
        val records = ok(1) + CallRecord("Sneha Shah", CallOutcome.CONTACTED, remark = "requested clarification regarding the science examination result", followUpRequired = true)
        val report = generator.generate(session, records)
        assertContains(report.text, "Requested clarification regarding the Science examination result. Follow-up required.")
        assertEquals(ReportStatus.COMPLETED, report.status)
        assertContains(report.text, "No concerns were raised by the parents contacted.")
    }

    @Test
    fun `multiple concern types are joined naturally`() {
        val records = listOf(CallRecord("A", CallOutcome.CONCERN_RAISED, setOf(Concern.FEES, Concern.LOW_MARKS, Concern.ATTENDANCE)))
        assertContains(generator.generate(session, records).text, "Parent expressed concern regarding low marks, attendance and fees.")
    }

    @Test
    fun `non-standard remark is kept and prefixed with concern selection`() {
        val records = listOf(CallRecord("A", CallOutcome.CONCERN_RAISED, setOf(Concern.LOW_MARKS), "wants to meet teacher"))
        assertContains(generator.generate(session, records).text, "Parent expressed concern regarding low marks. Wants to meet teacher.")
    }

    @Test
    fun `custom purpose is used verbatim and nothing is invented`() {
        val custom = session.copy(purpose = CallingPurpose.OTHER, customPurpose = "Annual Day invitation")
        val text = generator.generate(custom, ok(3)).text
        assertContains(text, "Purpose: Annual Day invitation")
        assertContains(text, "regarding Annual Day invitation has been completed successfully.")
        assertFalse(text.contains("examination"))
    }

    @Test
    fun `every called student is named, including parents simply contacted`() {
        val records = List(50) { CallRecord("Follow $it", CallOutcome.NO_ANSWER) } + List(24) { CallRecord("Plain $it", CallOutcome.CONTACTED) }
        val text = generator.generate(session, records).text
        records.forEachIndexed { i, r -> assertContains(text, "${i + 1}. ${r.studentName}\n") }
        assertContains(text, "74. Plain 23\nParent contacted and informed.")
        assertFalse(text.contains("remaining"))
    }

    @Test
    fun `empty and placeholder remarks are ignored`() {
        val records = listOf(
            CallRecord("A", CallOutcome.CONTACTED, remark = "   "),
            CallRecord("B", CallOutcome.CONTACTED, remark = "N/A"),
            CallRecord("C", CallOutcome.CONTACTED, remark = "null"),
        )
        val report = generator.generate(session, records)
        assertEquals(ReportStatus.COMPLETED_SUCCESSFULLY, report.status)
        assertContains(report.text, "1. A\nParent contacted and informed.\n\n2. B\nParent contacted and informed.\n\n3. C\nParent contacted and informed.")
        assertFalse(report.text.contains("N/A") || report.text.contains("null", ignoreCase = true))
    }

    @Test
    fun `mixed outcomes with teacher and section`() {
        val full = session.copy(section = "A", teacher = "Mrs. Rao")
        val records = ok(2) + listOf(
            CallRecord("Rahul Patil", CallOutcome.CONCERN_RAISED, setOf(Concern.BEHAVIOUR)),
            CallRecord("Amit Joshi", CallOutcome.NO_ANSWER, remark = "number busy"),
            CallRecord("Pending Kid", CallOutcome.NOT_CALLED),
        )
        val report = generator.generate(full, records)
        assertEquals(ReportStatus.PARTIALLY_COMPLETED, report.status)
        assertContains(report.text, "Class: 8th CBSE, Section A")
        assertContains(report.text, "Parent expressed concern regarding behaviour.")
        assertContains(report.text, "Parent could not be reached. Number busy. Follow-up call required.")
        assertContains(report.text, "Call has not been made yet.")
        assertContains(report.text, "1 call is yet to be made.")
        assertContains(report.text, "Mrs. Rao, Creative Classes")
    }

    @Test
    fun `no calls recorded is not started`() {
        val report = generator.generate(session, emptyList())
        assertEquals(ReportStatus.NOT_STARTED, report.status)
        assertContains(report.text, "has not started yet.")
    }

    @Test
    fun `output is deterministic`() {
        val records = ok(3) + CallRecord("X", CallOutcome.NO_ANSWER)
        assertEquals(generator.generate(session, records), generator.generate(session, records))
    }

    @Test
    fun `marks lists are separate sections of one report`() {
        val low = listOf(CallRecord("Asha", CallOutcome.CONTACTED), CallRecord("Ravi", CallOutcome.NO_ANSWER))
        val high = listOf(CallRecord("Meera", CallOutcome.CONTACTED, remark = "Parent is happy"))
        val text = generator.generateSections(session, listOf(ReportSection("Lower than 20 marks", low), ReportSection("Higher than 80 marks", high))).text
        val lowAt = text.indexOf("Lower than 20 marks (2 students):")
        val highAt = text.indexOf("Higher than 80 marks (1 student):")
        assertTrue(lowAt in 0 until highAt)
        assertContains(text.substring(lowAt, highAt), "Call Not Received: 1")
        assertContains(text.substring(highAt), "1. Meera\nParent is happy.")
        assertEquals(1, Regex("Overall Status:").findAll(text).count())
        assertContains(text, "Overall Status:\nCompleted\n1 parent requires follow-up.")
        assertEquals(generator.generate(session, low).text, generator.generateSections(session, listOf(ReportSection(null, low))).text)
    }
}
