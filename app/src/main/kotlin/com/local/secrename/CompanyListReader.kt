package com.local.secrename

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

data class CompanyWorkbook(val sheets: List<CompanySheet>)
data class CompanyColumn(val index: Int, val label: String)
data class CompanySheet(
    val name: String,
    val rows: List<List<String>>,
    val headerRow: Int?,
    val suggestedColumn: Int?,
    val unsupportedCells: Set<Pair<Int, Int>> = emptySet()
) {
    val columns: List<CompanyColumn> get() = (0 until (rows.maxOfOrNull { it.size } ?: 0)).map { index ->
        val title = headerRow?.let { rows[it].getOrNull(index) }.orEmpty()
        CompanyColumn(index, "${columnLabel(index)}${if (title.isBlank()) "" else " — $title"}")
    }
}
data class CompanySelection(val names: List<String>, val skippedBlankCount: Int, val duplicateCount: Int)

private fun columnLabel(index: Int): String {
    var value = index + 1
    var result = ""
    while (value > 0) { value--; result = ('A' + value % 26) + result; value /= 26 }
    return result
}

/** A bounded reader for text company columns in ordinary XLSX files. No formulas are evaluated. */
object CompanyListReader {
    const val MAX_COMPANIES = 5_000
    private const val MAX_INPUT = 10 * 1024 * 1024
    private const val MAX_UNPACKED = 30 * 1024 * 1024
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMNS = 64

    fun read(input: InputStream): CompanyWorkbook {
        try {
            val bytes = input.readBounded(MAX_INPUT)
            val entries = linkedMapOf<String, ByteArray>()
            var total = 0
            var entryCount = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entryCount <= 200) { "The workbook has too many ZIP entries." }
                    require(!entry.name.startsWith('/') && !entry.name.contains('\\') && entry.name.split('/').none { it == ".." }) { "Invalid workbook path." }
                    if (!entry.isDirectory) {
                        require(!entries.containsKey(entry.name)) { "Duplicate workbook entry." }
                        val data = zip.readBounded(MAX_UNPACKED - total)
                        total += data.size
                        entries[entry.name] = data
                    }
                    zip.closeEntry()
                }
            }
            require(entries.containsKey("[Content_Types].xml")) { "Choose an .xlsx workbook, not .xls or a password-protected file." }
            fun entry(path: String) = entries[path] ?: throw IllegalArgumentException("The workbook is missing $path.")
            val relationships = mutableMapOf<String, String>()
            parse(entry("xl/_rels/workbook.xml.rels"), object : DefaultHandler() {
                override fun startElement(uri: String?, local: String?, q: String?, a: Attributes) {
                    if (local == "Relationship" && a.getValue("Type").orEmpty().endsWith("/worksheet")) {
                        require(a.getValue("TargetMode") != "External") { "External worksheets are not supported." }
                        val target = a.getValue("Target").orEmpty()
                        require(!target.contains('\\') && !target.contains(':') && target.split('/').none { it == ".." }) { "Invalid worksheet path." }
                        val path = if (target.startsWith('/')) target.removePrefix("/") else "xl/$target"
                        relationships[a.getValue("Id")] = path
                    }
                }
            })
            val sheets = mutableListOf<Pair<String, String>>()
            parse(entry("xl/workbook.xml"), object : DefaultHandler() {
                override fun startElement(uri: String?, local: String?, q: String?, a: Attributes) {
                    if (local == "sheet") {
                        require(sheets.size < 20) { "The workbook contains more than 20 sheets." }
                        val id = (0 until a.length).firstOrNull { a.getLocalName(it) == "id" }?.let { a.getValue(it) }
                        val path = relationships[id] ?: throw IllegalArgumentException("The workbook contains an unsupported worksheet.")
                        sheets += a.getValue("name").orEmpty().take(100) to path
                    }
                }
            })
            val strings = mutableListOf<String>()
            entries["xl/sharedStrings.xml"]?.let { bytesShared ->
                parse(bytesShared, object : DefaultHandler() {
                    var inText = false
                    var phonetic = false
                    var current = StringBuilder()
                    override fun startElement(uri: String?, local: String?, q: String?, a: Attributes) {
                        if (local == "si") current = StringBuilder()
                        if (local == "rPh") phonetic = true
                        if (local == "t" && !phonetic) inText = true
                    }
                    override fun characters(ch: CharArray, start: Int, length: Int) {
                        if (inText) { current.append(ch, start, length); require(current.length <= 2_000) { "A workbook cell is too long." } }
                    }
                    override fun endElement(uri: String?, local: String?, q: String?) {
                        if (local == "t") inText = false
                        if (local == "rPh") phonetic = false
                        if (local == "si") { require(strings.size < 100_000) { "The workbook has too many text cells." }; strings += current.toString() }
                    }
                })
            }
            require(sheets.isNotEmpty()) { "The workbook contains no worksheets." }
            return CompanyWorkbook(sheets.map { (name, path) -> readSheet(name, entry(path), strings) })
        } catch (e: IllegalArgumentException) { throw e
        } catch (e: Exception) { throw IOException("Could not read this Excel file. Choose a valid, unencrypted .xlsx workbook.", e) }
    }

    fun select(sheet: CompanySheet, columnIndex: Int): CompanySelection {
        require(sheet.columns.any { it.index == columnIndex }) { "Choose a company-name column." }
        val start = sheet.headerRow?.plus(1) ?: 0
        val names = linkedMapOf<String, String>()
        var blank = 0
        var duplicates = 0
        for (row in start until sheet.rows.size) {
            require((row to columnIndex) !in sheet.unsupportedCells) { "Row ${row + 1} is not plain text. Replace formulas, numbers, or errors with company names." }
            val value = sheet.rows[row].getOrNull(columnIndex).orEmpty().trim().replace(Regex("\\s+"), " ")
            if (value.isBlank()) { blank++; continue }
            require(value.length <= 180 && value.none { it.isISOControl() } && value.any { it.isLetter() }) { "Check the company name in row ${row + 1}." }
            if (names.putIfAbsent(value.uppercase(Locale.ROOT), value) != null) duplicates++
            require(names.size <= MAX_COMPANIES) { "Import at most $MAX_COMPANIES companies." }
        }
        require(names.isNotEmpty()) { "No company names found in this column." }
        return CompanySelection(names.values.toList(), blank, duplicates)
    }

    private fun readSheet(name: String, bytes: ByteArray, strings: List<String>): CompanySheet {
        val rows = mutableListOf<MutableList<String>>()
        val unsupported = mutableSetOf<Pair<Int, Int>>()
        parse(bytes, object : DefaultHandler() {
            var row = -1
            var column = -1
            var type = ""
            var formula = false
            var capture = false
            var phonetic = false
            var value = StringBuilder()
            var cells = 0
            override fun startElement(uri: String?, local: String?, q: String?, a: Attributes) {
                when (local) {
                    "row" -> {
                        row = a.getValue("r")?.toIntOrNull()?.minus(1) ?: (row + 1)
                        require(row in 0 until MAX_ROWS) { "The worksheet exceeds $MAX_ROWS rows." }
                        while (rows.size <= row) rows.add(mutableListOf())
                        column = -1
                    }
                    "c" -> {
                        require(row >= 0 && ++cells <= 100_000) { "The worksheet contains too many cells." }
                        val reference = a.getValue("r")
                        column = if (reference == null) column + 1 else reference.takeWhile { it in 'A'..'Z' }.fold(0) { n, c -> n * 26 + c.code - 64 } - 1
                        require(column in 0 until MAX_COLUMNS) { "Use a workbook with at most $MAX_COLUMNS columns." }
                        type = a.getValue("t").orEmpty(); formula = false; value = StringBuilder()
                    }
                    "f" -> formula = true
                    "rPh" -> phonetic = true
                    "v", "t" -> if (!phonetic) capture = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capture) { value.append(ch, start, length); require(value.length <= 2_000) { "A workbook cell is too long." } }
            }
            override fun endElement(uri: String?, local: String?, q: String?) {
                if (local == "v" || local == "t") capture = false
                if (local == "rPh") phonetic = false
                if (local == "c") {
                    val raw = value.toString()
                    val text = if (type == "s") strings.getOrNull(raw.toIntOrNull() ?: -1)
                        ?: throw IllegalArgumentException("Invalid shared string reference.") else raw
                    if (formula || (text.isNotBlank() && type !in listOf("s", "inlineStr", "str"))) unsupported += row to column
                    while (rows[row].size <= column) rows[row].add("")
                    rows[row][column] = if (formula) "[Formula]" else text
                }
            }
        })
        val aliases = setOf("company name", "company names", "corporate name", "registered company name")
        val header = rows.take(20).indexOfFirst { cells -> cells.any { it.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") in aliases } }.takeIf { it >= 0 }
        val suggested = header?.let { h -> rows[h].indices.filter { rows[h][it].trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") in aliases }.singleOrNull() }
        return CompanySheet(name, rows.map { it.toList() }, header, suggested, unsupported)
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size().toLong() + count <= limit) { "The Excel file is too large (10 MB compressed / 30 MB unpacked maximum)." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val xml = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(!xml.contains('\u0000') && !xml.contains(Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE))) { "External entities and document type declarations are not supported." }
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
        // Android SAX implementations vary in supported feature flags. Reject declarations above,
        // and install a resolver that fails closed even when an optional feature is unavailable.
        for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd")) {
            try { factory.setFeature(feature, false) } catch (_: Exception) { }
        }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External XML resources are not allowed.") }
        reader.parse(InputSource(StringReader(xml)))
    }
}
