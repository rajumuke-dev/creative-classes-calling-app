package com.creativeclasses.report

data class MarkRow(val name: String, val marks: String)

data class MarkMatchResult(
    /** Student index in the calling list -> marks text. */
    val matched: Map<Int, String>,
    val unmatched: List<MarkRow>,
)

/** Turns text lines read from a marklist (one string per table row) into name/marks pairs. */
object MarkRowParser {
    private val ROW = Regex("""^\s*\d{0,3}[.)]?\s*(\p{L}[\p{L} .'’-]*?)\s+(\d{1,3}(?:\.\d{1,2})?|AB|ABS|ABSENT|AR|A|-|–)\s*$""", RegexOption.IGNORE_CASE)
    /** Side-by-side tables read as one line: a new entry starts at a serial number that follows a mark. */
    private val NEXT_ENTRY = Regex("""(?<=\b(?:AB|ABS|ABSENT|AR|A|\d{1,3}))\s+(?=\d{1,3}\s+\p{L})""", RegexOption.IGNORE_CASE)
    private val BORDER = Regex("[|\\[\\]_:]")
    private val HEADER_WORDS = setOf("marks", "subject", "exam", "student", "students", "date")

    fun parse(lines: List<String>): List<MarkRow> = lines.flatMap { line ->
        line.replace(BORDER, " ").trim().split(NEXT_ENTRY).mapNotNull { entry ->
            val m = ROW.matchEntire(entry.trim()) ?: return@mapNotNull null
            val name = m.groupValues[1].trim()
            if (NameMatcher.words(name).any { it in HEADER_WORDS }) return@mapNotNull null
            // OCR sometimes clips a trailing "AB" to "A"/"AR".
            val marks = m.groupValues[2].replace('–', '-').let { if (it.uppercase() in setOf("A", "AR")) "AB" else it }
            MarkRow(name, marks)
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
