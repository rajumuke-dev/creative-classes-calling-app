package com.creativeclasses.report

data class MarkRow(val name: String, val marks: String)

data class MarkMatchResult(
    /** Student index in the calling list -> marks text. */
    val matched: Map<Int, String>,
    val unmatched: List<MarkRow>,
)

/**
 * Turns text lines read from a marklist (one string per table row) into name/marks pairs.
 * Works token by token, so any number of side-by-side tables is handled even when OCR misses a serial number:
 * a name is a run of words, its mark is the token right after it; serials and noise end the run.
 */
object MarkRowParser {
    private val NAME_WORD = Regex("""\p{L}[\p{L}.'’-]*""")
    private val SCORE = Regex("""\d{1,3}(?:\.\d{1,2})?""")
    private val ABSENT = setOf("AB", "ABS", "ABSENT")
    /** OCR misreads of "AB" (clipped or a wrong last letter); only trusted at the end of an entry. */
    private val ABSENT_MISREAD = setOf("A", "AR", "AE", "A8")
    private val NO_MARK = setOf("-", "–")
    private val BORDER = Regex("[|\\[\\]_:]")
    private val HEADER_WORDS = setOf("marks", "subject", "exam", "student", "students", "date", "marklist", "batch")

    fun parse(lines: List<String>): List<MarkRow> = lines.flatMap { line ->
        val tokens = line.replace(BORDER, " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (NameMatcher.words(line).any { it in HEADER_WORDS }) emptyList() else entries(tokens)
    }

    private fun entries(tokens: List<String>): List<MarkRow> {
        val rows = mutableListOf<MarkRow>()
        val name = mutableListOf<String>()
        val serial = tokens.firstOrNull()?.takeIf { SCORE.matches(it) }
        tokens.forEachIndexed { i, token ->
            val next = tokens.getOrNull(i + 1)
            val mark = if (name.isEmpty()) null else markOf(token, next, serial)
            when {
                mark != null -> { rows += MarkRow(name.joinToString(" "), mark); name.clear() }
                NAME_WORD.matches(token) -> name += token
                else -> name.clear()
            }
        }
        return rows
    }

    private fun markOf(token: String, next: String?, serial: String?): String? {
        val upper = token.uppercase()
        val nextIsName = next != null && NAME_WORD.matches(next)
        return when {
            // A blank mark cell: the number after the name is the next table's serial (side-by-side rows share it).
            SCORE.matches(token) -> token.takeUnless { it == serial && nextIsName }
            upper in ABSENT -> token
            token in NO_MARK -> "-"
            upper in ABSENT_MISREAD && (next == null || SCORE.matches(next)) -> "AB"
            else -> null
        }
    }
}

/** Matches marklist names to the contact list: exact, then same words in any order, then a unique close spelling. */
object NameMatcher {
    fun words(name: String): List<String> =
        name.lowercase().replace(Regex("[^\\p{L} ]"), " ").split(' ').filter { it.isNotEmpty() }

    private fun key(name: String) = words(name).joinToString(" ")

    fun match(rows: List<MarkRow>, studentNames: List<String>): MarkMatchResult {
        val matched = mutableMapOf<Int, String>()
        val unmatched = mutableListOf<MarkRow>()
        val keys = studentNames.map { key(it) }
        val sortedKeys = studentNames.map { words(it).sorted() }
        for (row in rows) {
            val free = studentNames.indices.filter { it !in matched }
            val k = key(row.name)
            val idx = free.firstOrNull { keys[it] == k }
                ?: free.firstOrNull { sortedKeys[it] == words(row.name).sorted() }
                ?: closest(k, free, keys)
                ?: missingWords(k, words(row.name), free, sortedKeys)
            if (idx == null) unmatched += row else matched[idx] = row.marks
        }
        return MarkMatchResult(matched, unmatched)
    }

    /** A marklist name that omits part of the contact name (e.g. only the surname) matches if exactly one student contains all its words. */
    private fun missingWords(k: String, rowWords: List<String>, free: List<Int>, studentWords: List<List<String>>): Int? {
        if (k.length < 5) return null
        return free.filter { i -> rowWords.all { it in studentWords[i] } }.singleOrNull()
    }

    private fun closest(k: String, free: List<Int>, keys: List<String>): Int? {
        if (k.length < 5) return null
        val scored = free.map { it to distance(k, keys[it]) }.sortedBy { it.second }
        val best = scored.firstOrNull() ?: return null
        val unique = scored.size < 2 || scored[1].second > best.second
        return best.first.takeIf { unique && best.second * 5 <= k.length }
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1).also { it[0] = i }
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
