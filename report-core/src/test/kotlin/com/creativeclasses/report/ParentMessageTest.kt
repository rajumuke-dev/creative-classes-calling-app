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
}
