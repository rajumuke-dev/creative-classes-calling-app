package com.creativeclasses.report

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StudentImporterTest {
    private fun xlsx(sheet: String, shared: String? = null): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet.toByteArray()); zip.closeEntry()
            if (shared != null) {
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml")); zip.write(shared.toByteArray()); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `reads shared strings, numeric phones and empty cells`() {
        val shared = "<sst><si><t>Student Name</t></si><si><t>Mobile</t></si><si><t>Class</t></si>" +
            "<si><t>Rahul &amp; Co</t></si><si><t>8th</t></si><si><t>Sneha</t></si></sst>"
        val sheet = "<worksheet><sheetData>" +
            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c></row>" +
            "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>3</v></c><c r=\"B2\"><v>9.876543210E9</v></c><c r=\"C2\" t=\"s\"><v>4</v></c></row>" +
            "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>5</v></c><c r=\"B3\" s=\"1\"/><c r=\"C3\" t=\"s\"><v>4</v></c></row>" +
            "</sheetData></worksheet>"
        val rows = XlsxReader.readRows(ByteArrayInputStream(xlsx(sheet, shared)))
        val result = StudentImporter.fromRows(rows)
        assertEquals(listOf(ImportedStudent("Rahul & Co", "9876543210"), ImportedStudent("Sneha", null)), result.students)
        assertEquals("8th", result.className)
        assertNull(result.exam)
    }

    @Test
    fun `class is not prefilled when values differ`() {
        val rows = listOf(listOf("Name", "Class"), listOf("A", "8th"), listOf("B", "9th"))
        assertNull(StudentImporter.fromRows(rows).className)
    }

    @Test
    fun `missing name column is rejected`() {
        assertFailsWith<IllegalArgumentException> { StudentImporter.fromRows(listOf(listOf("Phone"), listOf("1"))) }
    }

    @Test
    fun `non xlsx input is rejected`() {
        assertFailsWith<IllegalArgumentException> { XlsxReader.readRows(ByteArrayInputStream("hello".toByteArray())) }
    }
}
