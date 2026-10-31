package com.creativeclasses.report

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudentImporterTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return bytes.toByteArray()
    }

    private fun s(col: String, row: Int, idx: Int) = "<c r=\"$col$row\" t=\"s\"><v>$idx</v></c>"

    @Test
    fun `workbook exposes sheet names and reads the chosen sheet`() {
        val workbook = "<workbook><sheets><sheet name=\"8th CBSE ALL\" sheetId=\"1\" r:id=\"rId1\"/>" +
            "<sheet name=\"10th SSC All &amp; Co\" sheetId=\"3\" r:id=\"rId2\"/></sheets></workbook>"
        val rels = "<Relationships><Relationship Id=\"rId2\" Type=\"x\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId1\" Type=\"x\" Target=\"/xl/worksheets/sheet2.xml\"/></Relationships>"
        val shared = "<sst><si><t>Name Of Student</t></si><si><t>Contact No 1</t></si><si><t>Asha &amp; Co</t></si></sst>"
        val sheetA = "<worksheet><sheetData><row r=\"1\">${s("A", 1, 0)}${s("B", 1, 1)}</row>" +
            "<row r=\"2\">${s("A", 2, 2)}<c r=\"B2\"><v>9.876543210E9</v></c></row></sheetData></worksheet>"
        val sheetB = "<worksheet><sheetData><row r=\"1\">${s("A", 1, 0)}</row></sheetData></worksheet>"
        val wb = XlsxReader.open(
            ByteArrayInputStream(
                zip(
                    "xl/workbook.xml" to workbook, "xl/_rels/workbook.xml.rels" to rels,
                    "xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheetA, "xl/worksheets/sheet2.xml" to sheetB,
                ),
            ),
        )
        assertEquals(listOf("8th CBSE ALL", "10th SSC All & Co"), wb.sheetNames)
        // rId1 (first sheet) maps to sheet2.xml and rId2 to sheet1.xml
        assertEquals(listOf(listOf("Name Of Student")), wb.rows(0))
        assertEquals(listOf(listOf("Name Of Student", "Contact No 1"), listOf("Asha & Co", "9876543210")), wb.rows(1))
    }

    @Test
    fun `handles title rows, repeated headers, two contacts, school and publication`() {
        val rows = listOf(
            listOf("Contact list std 8th cbse (NCERT)"),
            listOf("Sr No", "Name Of Student", "Publication", "Contact No 1", "Contact No 2", "School Name"),
            listOf("1", "Test Student One", "NCERT", "9000000005", "9000000002", "APS Dighi"),
            listOf("2", "No Second", "NCERT", "9000000007", "", "APS Dighi"),
            listOf("Contact list std 8th cbse (Lead)"),
            listOf("Sr No", "Name Of Student", "Publication", "Contact No 1 ", "Contact No 2", "School Name"),
            listOf("1", "Test Student Two ", "LEAD", "9000000006", "9000000004", "Master Mind"),
            listOf("", "", "", "", "", ""),
        )
        val students = StudentImporter.fromRows(rows)
        assertEquals(
            listOf(
                ImportedStudent("Test Student One", "9000000005", "9000000002", "NCERT", "APS Dighi"),
                ImportedStudent("No Second", "9000000007", null, "NCERT", "APS Dighi"),
                ImportedStudent("Test Student Two", "9000000006", "9000000004", "LEAD", "Master Mind"),
            ),
            students,
        )
    }

    @Test
    fun `different column layouts per sheet are supported`() {
        val rows = listOf(
            listOf("Contact list std 9th cbse"),
            listOf("Sr No", "Name Of Student", "Contact No 1 ", "Contact No 2", "School Name"),
            listOf("1", "Test Student Three", "9000000001", "9000000003", "APS Dighi"),
        )
        assertEquals(listOf(ImportedStudent("Test Student Three", "9000000001", "9000000003", null, "APS Dighi")), StudentImporter.fromRows(rows))
    }

    @Test
    fun `sheet name gives class and board`() {
        assertEquals(SheetInfo("8th", "CBSE"), StudentImporter.parseSheetName("8th CBSE ALL"))
        assertEquals(SheetInfo("10th", "CBSE"), StudentImporter.parseSheetName("10th CBSE All "))
        assertEquals(SheetInfo("9th", "SSC"), StudentImporter.parseSheetName("9th SSC ALL "))
        assertEquals(SheetInfo(null, "Sheet1"), StudentImporter.parseSheetName("Sheet1"))
    }

    @Test
    fun `phone validation flags 9 digit numbers`() {
        assertTrue(PhoneNumbers.isValid("9000000005"))
        assertTrue(PhoneNumbers.isValid("+91 91450-83645"))
        assertFalse(PhoneNumbers.isValid("842126447"))
        assertFalse(PhoneNumbers.isValid("98765abc43"))
        assertEquals("9000000005", PhoneNumbers.dialable("90000 00005"))
        assertNull(PhoneNumbers.dialable("842126447"))
    }

    @Test
    fun `sheet without name header or students is rejected`() {
        assertFailsWith<IllegalArgumentException> { StudentImporter.fromRows(listOf(listOf("Phone"), listOf("1"))) }
        assertFailsWith<IllegalArgumentException> { StudentImporter.fromRows(listOf(listOf("Name Of Student", "Contact No 1"))) }
    }

    @Test
    fun `non xlsx input is rejected`() {
        assertFailsWith<IllegalArgumentException> { XlsxReader.open(ByteArrayInputStream("hello".toByteArray())) }
    }
}
