package com.creativeclasses.report

import kotlin.test.Test
import kotlin.test.assertContains

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
}
