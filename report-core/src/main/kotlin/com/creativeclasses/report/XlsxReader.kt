package com.creativeclasses.report

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream

/** Minimal, dependency-free reader for the first sheet of an .xlsx file. Returns rows of cell text. */
object XlsxReader {
    private const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    private val SHARED_ITEM = Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val TEXT = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val ROW = Regex("<row\\b[^>]*?(?:/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("<c\\s([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val VALUE = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

    fun readRows(input: InputStream): List<List<String>> {
        var shared: List<String> = emptyList()
        var sheetName: String? = null
        var sheetXml: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    shared = SHARED_ITEM.findAll(readLimited(zip)).map { item ->
                        TEXT.findAll(item.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
                    }.toList()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") &&
                    (sheetName == null || name < sheetName!!)
                ) {
                    sheetName = name
                    sheetXml = readLimited(zip)
                }
            }
        }
        val xml = sheetXml ?: throw IllegalArgumentException("This is not a valid .xlsx Excel file.")
        return ROW.findAll(xml).map { parseRow(it.groupValues[1], shared) }.toList()
    }

    private fun parseRow(rowXml: String, shared: List<String>): List<String> {
        val cells = sortedMapOf<Int, String>()
        CELL.findAll(rowXml).forEach { c ->
            val attrs = c.groupValues[1]
            val body = c.groupValues[2]
            val col = attr(attrs, "r")?.let(::columnIndex) ?: ((cells.keys.maxOrNull() ?: -1) + 1)
            val raw = VALUE.find(body)?.groupValues?.get(1)?.trim()
            cells[col] = when (attr(attrs, "t")) {
                "s" -> raw?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                "inlineStr" -> TEXT.findAll(body).joinToString("") { unescape(it.groupValues[1]) }
                "str", "b", "e" -> unescape(raw ?: "")
                else -> raw?.let(::plainNumber) ?: ""
            }
        }
        val width = (cells.keys.maxOrNull() ?: -1) + 1
        return List(width) { cells[it] ?: "" }
    }

    private fun attr(attrs: String, name: String) =
        Regex("(?:^|\\s)$name=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)

    private fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    // Numeric cells such as 9.8765432E9 must read back as plain digits (phone numbers).
    private fun plainNumber(v: String) =
        runCatching { BigDecimal(v).stripTrailingZeros().toPlainString() }.getOrDefault(v)

    private fun unescape(s: String) = ENTITY.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val cp = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
            }
        }
    }

    private fun readLimited(zip: ZipInputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            require(total <= MAX_ENTRY_BYTES) { "The Excel file is too large." }
            out.write(buf, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
