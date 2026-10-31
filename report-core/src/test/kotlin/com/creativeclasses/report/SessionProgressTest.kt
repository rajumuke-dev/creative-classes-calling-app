package com.creativeclasses.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionProgressTest {
    private fun rec(outcome: CallOutcome, followUp: Boolean = false) = CallRecord("S", outcome, followUpRequired = followUp)

    @Test
    fun `counts attempted outcomes and resumes at first uncalled student`() {
        val p = SessionProgress.of(
            listOf(
                rec(CallOutcome.CONTACTED),
                rec(CallOutcome.NO_ANSWER),
                rec(CallOutcome.NOT_CALLED),
                rec(CallOutcome.CONCERN_RAISED, followUp = true),
                rec(CallOutcome.NOT_CALLED),
            ),
        )
        assertEquals(5, p.total)
        assertEquals(3, p.attempted)
        assertEquals(1, p.contacted)
        assertEquals(1, p.concerns)
        assertEquals(1, p.noAnswer)
        assertEquals(2, p.notCalled)
        assertEquals(2, p.followUps)
        assertEquals(2, p.resumeIndex)
        assertFalse(p.isFinished)
    }

    @Test
    fun `no resume point once every student has an outcome`() {
        val p = SessionProgress.of(listOf(rec(CallOutcome.CONTACTED), rec(CallOutcome.CALL_LATER)))
        assertNull(p.resumeIndex)
        assertEquals(1, p.callLater)
        assertTrue(p.isFinished)
    }

    @Test
    fun `empty session is not finished`() {
        val p = SessionProgress.of(emptyList())
        assertNull(p.resumeIndex)
        assertFalse(p.isFinished)
    }
}
