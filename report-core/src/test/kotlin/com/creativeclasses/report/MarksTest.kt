package com.creativeclasses.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarksTest {
    @Test
    fun `parses scores absent and blank`() {
        assertEquals(Mark(MarkKind.SCORED, 41.0), Marks.parse(" 41 "))
        assertEquals(Mark(MarkKind.SCORED, 12.5), Marks.parse("12.5"))
        assertEquals(MarkKind.ABSENT, Marks.parse("ab").kind)
        assertEquals(MarkKind.NONE, Marks.parse("-").kind)
        assertEquals(MarkKind.NONE, Marks.parse("").kind)
        assertEquals(MarkKind.NONE, Marks.parse("NaN").kind)
        assertEquals(MarkKind.NONE, Marks.parse("-5").kind)
    }

    @Test
    fun `lower than and higher than are strict and ignore absent and blank`() {
        assertTrue(Marks.matches("19", MarksRule.BELOW, 20.0))
        assertFalse(Marks.matches("20", MarksRule.BELOW, 20.0))
        assertTrue(Marks.matches("41", MarksRule.ABOVE, 40.0))
        assertFalse(Marks.matches("40", MarksRule.ABOVE, 40.0))
        for (rule in MarksRule.values()) {
            assertFalse(Marks.matches("AB", rule, 20.0))
            assertFalse(Marks.matches("-", rule, 20.0))
            assertFalse(Marks.matches("5", rule, null))
        }
    }

    @Test
    fun `parses marklist rows and skips headers`() {
        val rows = MarkRowParser.parse(
            listOf("SUBJECT - HINDI", "Students name. Marks 50", "1 Asha Verma 41", "2 Ravi Kumar AB", "3 Meena Rao -", "4 Omar Shah 9.5", "garbage"),
        )
        assertEquals(
            listOf(MarkRow("Asha Verma", "41"), MarkRow("Ravi Kumar", "AB"), MarkRow("Meena Rao", "-"), MarkRow("Omar Shah", "9.5")),
            rows,
        )
    }

    @Test
    fun `matches exact reordered and closely spelled names`() {
        val students = listOf("Asha Verma", "Kumar Ravi", "Meenakshi Rao", "Omar Shah")
        val result = NameMatcher.match(
            listOf(MarkRow("Asha  Verma", "41"), MarkRow("Ravi Kumar", "AB"), MarkRow("Meenaksh Rao", "20"), MarkRow("Unknown Person", "30")),
            students,
        )
        assertEquals(mapOf(0 to "41", 1 to "AB", 2 to "20"), result.matched)
        assertEquals(listOf(MarkRow("Unknown Person", "30")), result.unmatched)
    }

    @Test
    fun `ambiguous close names are not guessed`() {
        val result = NameMatcher.match(listOf(MarkRow("Sneha Patel", "10")), listOf("Sneha Patil", "Sneha Patal"))
        assertTrue(result.matched.isEmpty())
        assertEquals(1, result.unmatched.size)
    }

    @Test
    fun `duplicate names each take one student`() {
        val result = NameMatcher.match(listOf(MarkRow("Amit Joshi", "10"), MarkRow("Amit Joshi", "20")), listOf("Amit Joshi", "Amit Joshi"))
        assertEquals(mapOf(0 to "10", 1 to "20"), result.matched)
    }

    @Test
    fun `table border characters from OCR are ignored`() {
        val rows = MarkRowParser.parse(listOf("| 2 | Aaradhya Shinde | 29 |"))
        assertEquals(listOf("Aaradhya Shinde" to "29"), rows.map { it.name to it.marks })
    }
}
