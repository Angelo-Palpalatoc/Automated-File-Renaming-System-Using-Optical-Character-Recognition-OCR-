package com.local.secrename

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class CompanyListReaderTest {
    @Test fun importsFixtureWithTwentyCompaniesAndPreservesIntentionalTypo() {
        val relative = "demo-documents/company-list-fixtures-20/sample_company_list_20.xlsx"
        val fixture = listOf(File(relative), File("../$relative")).first { it.exists() }
        val sheet = fixture.inputStream().use(CompanyListReader::read).sheets.single()
        assertEquals(1, sheet.suggestedColumn)
        val selected = CompanyListReader.select(sheet, 1)
        assertEquals(20, selected.names.size)
        assertEquals("ABC SOLUTIONS INC.", selected.names.first())
        assertEquals("WESTBROOK GENERAL MERCHANDISE CORPRATION", selected.names.last())
    }

    @Test fun selectsTextColumnIgnoresNumbersAndDeduplicatesCaseAndSpacing() {
        val sheet = read("""
            <row r="1"><c r="A1" t="inlineStr"><is><t>No.</t></is></c><c r="B1" t="inlineStr"><is><t>Company Name</t></is></c></row>
            <row r="2"><c r="A2"><v>1</v></c><c r="B2" t="inlineStr"><is><t> ABC  Solutions Inc. </t></is></c></row>
            <row r="3"><c r="B3" t="inlineStr"><is><t>abc solutions inc.</t></is></c></row>
            <row r="4"><c r="A4"><v>3</v></c></row>
        """).sheets.single()
        val result = CompanyListReader.select(sheet, 1)
        assertEquals(listOf("ABC Solutions Inc."), result.names)
        assertEquals(1, result.duplicateCount)
        assertEquals(1, result.skippedBlankCount)
    }

    @Test fun sharedStringsAndRichInlineTextAreRead() {
        val strings = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><t>Company Name</t></si><si><r><t>ABC </t></r><r><t>SOLUTIONS INC.</t></r></si></sst>"
        val sheet = read("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row><row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c></row>", strings).sheets.single()
        assertEquals(listOf("ABC SOLUTIONS INC."), CompanyListReader.select(sheet, 0).names)
    }

    @Test fun noHeaderPreservesFirstCompanyAndRequiresColumnChoice() {
        val sheet = read("<row><c t=\"inlineStr\"><is><t>ABC SOLUTIONS INC.</t></is></c></row>").sheets.single()
        assertNull(sheet.headerRow)
        assertNull(sheet.suggestedColumn)
        assertEquals("ABC SOLUTIONS INC.", CompanyListReader.select(sheet, 0).names.single())
    }

    @Test fun ambiguousHeadersRequireColumnChoice() {
        val sheet = read("<row><c t=\"inlineStr\"><is><t>Company Name</t></is></c><c t=\"inlineStr\"><is><t>Company Name</t></is></c></row>").sheets.single()
        assertNull(sheet.suggestedColumn)
        assertEquals(0, sheet.headerRow)
    }

    @Test fun formulaCachedTextCannotBecomeACompany() {
        val sheet = read("<row><c t=\"str\"><f>CONCAT(A1,B1)</f><v>ABC SOLUTIONS INC.</v></c></row>").sheets.single()
        rejected { CompanyListReader.select(sheet, 0) }
    }

    @Test fun numericColumnIsRejected() {
        val sheet = read("<row><c><v>123</v></c></row>").sheets.single()
        rejected { CompanyListReader.select(sheet, 0) }
    }

    @Test fun emptyColumnIsRejected() {
        val sheet = read("<row><c t=\"inlineStr\"><is><t>Company Name</t></is></c></row>").sheets.single()
        rejected { CompanyListReader.select(sheet, 0) }
    }

    @Test fun invalidSharedStringIndexIsRejected() {
        rejected { read("<row><c t=\"s\"><v>99</v></c></row>") }
    }

    @Test fun externalEntityIsRejectedBeforeXmlParsing() {
        val xml = "<!DOCTYPE worksheet [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><worksheet><sheetData><row><c t=\"inlineStr\"><is><t>&secret;</t></is></c></row></sheetData></worksheet>"
        rejected { CompanyListReader.read(ByteArrayInputStream(zip(sheetXml = xml))) }
    }

    @Test fun internalEntityExpansionIsRejected() {
        val xml = "<!DOCTYPE worksheet [<!ENTITY x 'ABC SOLUTIONS INC.'>]><worksheet><sheetData><row><c t=\"inlineStr\"><is><t>&x;</t></is></c></row></sheetData></worksheet>"
        rejected { CompanyListReader.read(ByteArrayInputStream(zip(sheetXml = xml))) }
    }

    @Test fun externalWorksheetRelationshipIsRejected() {
        rejected { CompanyListReader.read(ByteArrayInputStream(zip(target = "https://example.com/sheet.xml", mode = "External"))) }
    }

    @Test fun parentTraversalWorksheetRelationshipIsRejected() {
        rejected { CompanyListReader.read(ByteArrayInputStream(zip(target = "../secret.xml"))) }
    }

    @Test fun unsupportedLegacyOrEncryptedDataIsRejected() {
        rejected { CompanyListReader.read(ByteArrayInputStream(byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte()))) }
    }

    @Test fun excessiveSparseRowIsRejectedWithoutAllocation() {
        rejected { read("<row r=\"1048576\"><c r=\"A1048576\" t=\"inlineStr\"><is><t>Company</t></is></c></row>") }
    }

    @Test fun maximumInputSizeIsEnforced() {
        rejected { CompanyListReader.read(ByteArrayInputStream(ByteArray(10 * 1024 * 1024 + 1))) }
    }

    private fun read(rows: String, strings: String? = null) = CompanyListReader.read(ByteArrayInputStream(zip(
        sheetXml = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>$rows</sheetData></worksheet>",
        sharedStrings = strings
    )))

    private fun zip(
        sheetXml: String = "<worksheet/>", sharedStrings: String? = null,
        target: String = "worksheets/sheet1.xml", mode: String = "Internal"
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun add(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            add("[Content_Types].xml", "<Types/>")
            add("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Companies\" r:id=\"rId1\"/></sheets></workbook>")
            add("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"$target\" TargetMode=\"$mode\"/></Relationships>")
            add("xl/worksheets/sheet1.xml", sheetXml)
            sharedStrings?.let { add("xl/sharedStrings.xml", it) }
        }
        return output.toByteArray()
    }

    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected invalid workbook/selection to be rejected") }
        catch (_: IllegalArgumentException) { }
        catch (_: java.io.IOException) { }
    }
}
