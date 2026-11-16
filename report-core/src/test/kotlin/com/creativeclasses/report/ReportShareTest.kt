package com.creativeclasses.report

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportShareTest {
    private val session = CallingSession("10th SSC", "10th", board = "SSC", purpose = CallingPurpose.EXAM_RESULT, date = LocalDate.of(2026, 11, 8))
    private val longReport = ReportGenerator().generate(
        session,
        (1..86).map { CallRecord("Student Name $it", CallOutcome.NO_ANSWER, remark = "Call not received and switched off") },
    ).text

    @Test
    fun `report over the WhatsApp limit does not fit one message`() {
        assertTrue(longReport.length > 4096)
        assertFalse(ReportShare.fitsOneMessage(longReport))
        assertTrue(ReportShare.fitsOneMessage("Short report"))
    }

    @Test
    fun `caption is the summary and points to the PDF`() {
        val caption = ReportShare.caption(longReport)!!
        assertTrue(caption.startsWith("Creative Classes\nParent Calling Report"))
        assertTrue("Total Parents: 86" in caption)
        assertFalse("Student Name 1" in caption)
        assertTrue(caption.endsWith("attached PDF."))
        assertNull(ReportShare.caption("No summary heading"))
    }

    @Test
    fun `pdf is named from class and date with safe characters`() {
        assertEquals("Parent Calling Report - 10th SSC - 8 November 2026.pdf", ReportShare.pdfName(longReport))
        assertEquals("Parent Calling Report - 10th A B.pdf", ReportShare.pdfName("Class: 10th A/B"))
        assertEquals("Parent Calling Report.pdf", ReportShare.pdfName("x"))
    }
}
