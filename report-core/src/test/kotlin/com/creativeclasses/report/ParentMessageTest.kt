package com.creativeclasses.report

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ParentMessageTest {
    @Test
    fun `message names the student and asks for a call back`() {
        val text = ParentMessage.notReached("  Asha   Rao ")
        assertContains(text, "regarding Asha Rao but could not reach you")
        assertContains(text, "call us back")
    }

    @Test
    fun `blank name falls back to your child`() {
        assertContains(ParentMessage.notReached(" "), "regarding your child")
    }

    @Test
    fun `admin message tells when the teacher is available`() {
        assertEquals(
            "Dear Parent,\n\nThis is Creative Classes. We tried to call you regarding Asha Rao but could not reach you. " +
                "Kindly call us back at your convenience.\n\nPriya Sharma is available on Monday from 4 PM to 7 PM. Please do visit the class." +
                "\n\nThank you,\nCreative Classes",
            ParentMessage.notReached("Asha Rao", teacher = " Priya  Sharma ", availability = " on Monday from 4 PM to 7 PM. "),
        )
    }

    @Test
    fun `availability without teacher name`() {
        val text = ParentMessage.notReached("Asha Rao", availability = "Available 10 AM to 1 PM")
        assertContains(text, "convenience.\n\nThe teacher is available 10 AM to 1 PM. Please do visit the class.\n\nThank you,\nCreative Classes")
    }

    @Test
    fun `no availability leaves out the teacher line`() {
        val text = ParentMessage.notReached("Asha Rao", teacher = "Priya Sharma", availability = "  ")
        assertContains(text, "Kindly call us back at your convenience.\n\nThank you,\nCreative Classes")
        assertFalse(text.contains("Priya"))
    }

    @Test
    fun `message tells the mark with subject and exam`() {
        val text = ParentMessage.notReached("Asha Rao", subject = " History ", exam = "Unit Test 1", mark = " 19 ")
        assertContains(text, "could not reach you. Asha Rao scored 19 marks in History (Unit Test 1). Kindly call us back")
    }

    @Test
    fun `absent student is reported as absent`() {
        assertContains(ParentMessage.notReached("Asha Rao", subject = "History", mark = "AB"), "Asha Rao was absent for History.")
        assertContains(ParentMessage.notReached(" ", mark = "ab"), "Your child was absent for the exam.")
    }

    @Test
    fun `mark without subject or exam`() {
        assertContains(ParentMessage.notReached("Asha Rao", exam = "Unit Test 1", mark = "29"), "Asha Rao scored 29 marks in Unit Test 1.")
        assertContains(ParentMessage.notReached("Asha Rao", mark = "9"), "Asha Rao scored 9 marks. Kindly")
    }

    @Test
    fun `no mark leaves out the marks line`() {
        val expected = ParentMessage.notReached("Asha Rao")
        assertEquals(expected, ParentMessage.notReached("Asha Rao", subject = "History", exam = "Unit Test 1", mark = " "))
        assertEquals(expected, ParentMessage.notReached("Asha Rao", subject = "History", mark = null))
        assertContains(expected, "could not reach you. Kindly call us back")
    }
}
