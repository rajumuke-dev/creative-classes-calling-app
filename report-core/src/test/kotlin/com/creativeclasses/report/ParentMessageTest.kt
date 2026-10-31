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
    fun `teacher and availability are used when set`() {
        assertEquals(
            "Dear Parent,\n\nThis is Priya Sharma from Creative Classes. I tried to call you regarding Asha Rao but could not reach you. " +
                "Kindly call me back at your convenience. I am available Mon to Sat, 4 PM to 7 PM.\n\nThank you,\nPriya Sharma\nCreative Classes",
            ParentMessage.notReached("Asha Rao", teacher = " Priya  Sharma ", availability = " Mon to Sat, 4 PM to 7 PM. "),
        )
    }

    @Test
    fun `availability without teacher speaks for the institute`() {
        val text = ParentMessage.notReached("Asha Rao", availability = "Available 10 AM to 1 PM")
        assertContains(text, "Kindly call us back at your convenience. We are available 10 AM to 1 PM.\n\nThank you,\nCreative Classes")
    }

    @Test
    fun `blank availability adds no availability sentence`() {
        val text = ParentMessage.notReached("Asha Rao", teacher = "Priya Sharma", availability = "  ")
        assertContains(text, "Kindly call me back at your convenience.\n\nThank you,\nPriya Sharma\nCreative Classes")
        assertFalse(text.contains("available"))
    }
}
