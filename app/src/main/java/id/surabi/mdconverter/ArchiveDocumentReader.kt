package id.surabi.mdconverter

import android.text.Html
import android.net.Uri
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Extracts common ZIP-based document formats without a large office-suite dependency. */
internal object ArchiveDocumentReader {
    private const val MAX_ENTRY_BYTES = 12L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
    private const val MAX_UNSELECTED_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_ARCHIVE_UNCOMPRESSED_BYTES = 80L * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 10_000
    private const val MAX_ROWS_PER_SHEET = 5000
    private const val MAX_COLUMNS_PER_SHEET = 256

    fun convert(extension: String, bytes: ByteArray, warnings: MutableList<String>): String =
        when (extension) {
            "xlsx" -> xlsx(bytes, warnings)
            "ods" -> ods(bytes, warnings)
            "pptx" -> pptx(bytes, warnings)
            "odt" -> odfText(bytes, warnings, "ODT")
            "odp" -> odfText(bytes, warnings, "ODP")
            "epub" -> epub(bytes, warnings)
            "ipynb" -> notebook(bytes)
            else -> error("Arsip .$extension belum didukung.")
        }

    private fun readEntries(bytes: ByteArray, keep: (String) -> Boolean): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        var archiveTotal = 0L
        var selectedTotal = 0L
        var entryCount = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ARCHIVE_ENTRIES) { "Arsip memiliki terlalu banyak entri." }

                if (!entry.isDirectory) {
                    val selected = keep(entry.name)
                    val output = if (selected) ByteArrayOutputStream() else null
                    var entrySize = 0L
                    val buffer = ByteArray(8192)

                    // Read and account for skipped entries too. ZipInputStream.closeEntry()
                    // would otherwise drain them without our decompression limits being checked.
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entrySize += count
                        archiveTotal += count
                        require(entrySize <= if (selected) MAX_ENTRY_BYTES else MAX_UNSELECTED_ENTRY_BYTES) {
                            "Konten arsip terlalu besar pada ${entry.name}."
                        }
                        require(archiveTotal <= MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                            "Total data hasil dekompresi arsip melebihi batas aman 80 MB."
                        }

                        if (selected) {
                            selectedTotal += count
                            require(selectedTotal <= MAX_TOTAL_BYTES) {
                                "Total konten teks arsip melebihi batas aman 40 MB."
                            }
                            output!!.write(buffer, 0, count)
                        }
                    }
                    if (selected) result[entry.name] = output!!.toString("UTF-8")
                }
                zip.closeEntry()
            }
        }
        require(result.isNotEmpty()) { "Struktur arsip tidak dikenali atau konten teks tidak ditemukan." }
        return result
    }

    private fun xlsx(bytes: ByteArray, warnings: MutableList<String>): String {
        val entries = readEntries(bytes) {
            it == "xl/sharedStrings.xml" ||
                (it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") && !it.contains("/_rels/"))
        }
        val strings = entries["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val sheets = entries.filterKeys {
            it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml")
        }.toSortedMap(compareBy { sheetNumber(it) })
        require(sheets.isNotEmpty()) { "XLSX tidak memiliki lembar kerja yang dapat dibaca." }
        warnings.add("XLSX: nilai sel yang tersimpan diekstrak. Rumus tidak dihitung ulang dan format visual tidak dipertahankan.")
        return sheets.entries.mapIndexed { index, entry ->
            val label = "Lembar " + (index + 1)
            "## " + label + "\n\n" + MarkdownTableRenderer.render(xlsxRows(entry.value, strings, warnings, label))
        }.joinToString("\n\n")
    }

    private fun sheetNumber(path: String): Int =
        Regex("sheet(\\d+)\\.xml$").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun sharedStrings(xml: String): List<String> {
        val parser = parser(xml)
        val result = mutableListOf<String>()
        var insideString = false
        var insideText = false
        var value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { insideString = true; value = StringBuilder() }
                    "t" -> if (insideString) insideText = true
                }
                XmlPullParser.TEXT -> if (insideString && insideText) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> insideText = false
                    "si" -> { result.add(value.toString()); insideString = false }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun xlsxRows(xml: String, shared: List<String>, warnings: MutableList<String>, sheetLabel: String): List<List<String>> {
        val parser = parser(xml)
        val rows = mutableListOf<List<String>>()
        var truncatedRows = false
        var truncatedColumns = false
        var row = sortedMapOf<Int, String>()
        var inCell = false
        var cellType = ""
        var cellRef = ""
        var cellValue = StringBuilder()
        var capture = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = sortedMapOf()
                    "c" -> {
                        inCell = true
                        cellRef = attr(parser, "r").orEmpty()
                        cellType = attr(parser, "t").orEmpty()
                        cellValue = StringBuilder()
                    }
                    "v", "t" -> if (inCell) capture = true
                }
                XmlPullParser.TEXT -> if (inCell && capture) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val index = columnIndex(cellRef)
                        if (index >= MAX_COLUMNS_PER_SHEET) truncatedColumns = true
                        if (index >= 0 && index < MAX_COLUMNS_PER_SHEET) {
                            val raw = cellValue.toString()
                            val value = when (cellType) {
                                "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            row[index] = value
                        }
                        inCell = false
                    }
                    "row" -> if (row.isNotEmpty()) {
                        if (rows.size < MAX_ROWS_PER_SHEET) {
                            val width = minOf(row.lastKey() + 1, MAX_COLUMNS_PER_SHEET)
                            rows.add((0 until width).map { row[it].orEmpty() })
                        } else truncatedRows = true
                    }
                }
            }
            event = parser.next()
        }
        if (truncatedRows) warnings.add("XLSX: sebagian baris pada " + sheetLabel + " tidak ditampilkan karena batas 5.000 baris.")
        if (truncatedColumns) warnings.add("XLSX: kolom setelah kolom ke-256 pada " + sheetLabel + " tidak ditampilkan demi membatasi memori.")
        return rows
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var result = 0
        letters.forEach {
            result = result * 26 + (it - 'A' + 1)
            if (result > MAX_COLUMNS_PER_SHEET) return MAX_COLUMNS_PER_SHEET
        }
        return result - 1
    }

    private fun ods(bytes: ByteArray, warnings: MutableList<String>): String {
        val xml = readEntries(bytes) { it == "content.xml" }["content.xml"]
            ?: error("ODS tidak memiliki content.xml.")
        warnings.add("ODS: isi sel diekstrak menjadi tabel Markdown; format, grafik, dan rumus tidak dipertahankan.")
        val parser = parser(xml)
        val sheets = mutableListOf<Pair<String, List<List<String>>>>()
        var sheetName = "Lembar"
        var rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var truncatedRows = false
        var truncatedColumns = false
        var cell = StringBuilder()
        var inCell = false
        var inParagraph = false
        var repeatColumns = 1
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "table" -> {
                        sheetName = attr(parser, "name", "urn:oasis:names:tc:opendocument:xmlns:table:1.0")
                            ?: "Lembar " + (sheets.size + 1)
                        rows = mutableListOf()
                        truncatedRows = false
                        truncatedColumns = false
                    }
                    "table-row" -> row = mutableListOf()
                    "table-cell", "covered-table-cell" -> {
                        inCell = true
                        cell = StringBuilder()
                        repeatColumns = (attr(parser, "number-columns-repeated", "urn:oasis:names:tc:opendocument:xmlns:table:1.0")?.toIntOrNull() ?: 1).coerceIn(1, 100)
                    }
                    "p" -> if (inCell) inParagraph = true
                }
                XmlPullParser.TEXT -> if (inCell && inParagraph) cell.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "p" -> if (inCell) { cell.append(' '); inParagraph = false }
                    "table-cell", "covered-table-cell" -> {
                        repeat(repeatColumns) {
                            if (row.size < MAX_COLUMNS_PER_SHEET) row.add(cell.toString().trim())
                            else truncatedColumns = true
                        }
                        inCell = false
                    }
                    "table-row" -> if (row.isNotEmpty()) {
                        if (rows.size < MAX_ROWS_PER_SHEET) rows.add(row.toList())
                        else truncatedRows = true
                    }
                    "table" -> if (rows.isNotEmpty()) {
                        sheets.add(sheetName to rows)
                        if (truncatedRows) warnings.add("ODS: sebagian baris pada '" + sheetName + "' tidak ditampilkan karena batas 5.000 baris.")
                        if (truncatedColumns) warnings.add("ODS: kolom setelah kolom ke-256 pada '" + sheetName + "' tidak ditampilkan demi membatasi memori.")
                    }
                }
            }
            event = parser.next()
        }
        require(sheets.isNotEmpty()) { "ODS tidak memiliki tabel yang dapat dibaca." }
        return sheets.map { (name, data) -> "## " + name + "\n\n" + MarkdownTableRenderer.render(data) }.joinToString("\n\n")
    }

    private fun pptx(bytes: ByteArray, warnings: MutableList<String>): String {
        val slides = readEntries(bytes) {
            it.startsWith("ppt/slides/slide") && it.endsWith(".xml") && !it.contains("/_rels/")
        }.toSortedMap(compareBy { sheetNumber(it.replace("slide", "sheet")) })
        require(slides.isNotEmpty()) { "PPTX tidak memiliki slide yang dapat dibaca." }
        warnings.add("PPTX: teks tiap slide diekstrak. Gambar, animasi, catatan pembicara, dan tata letak tidak dipertahankan.")
        return slides.entries.mapIndexed { index, entry ->
            val text = xmlText(entry.value, setOf("t"))
            "## Slide " + (index + 1) + "\n\n" + text.ifBlank { "_Tidak ada teks pada slide ini._" }
        }.joinToString("\n\n")
    }

    private fun odfText(bytes: ByteArray, warnings: MutableList<String>, label: String): String {
        val xml = readEntries(bytes) { it == "content.xml" }["content.xml"]
            ?: error(label + " tidak memiliki content.xml.")
        warnings.add(label + ": paragraf dan heading diekstrak; format halaman, gambar, tabel kompleks, dan tata letak belum dipertahankan.")
        val parser = parser(xml)
        val output = StringBuilder()
        var element = ""
        var level = 1
        var paragraph = StringBuilder()
        var capture = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "h", "p" -> {
                        element = parser.name
                        level = (attr(parser, "outline-level", "urn:oasis:names:tc:opendocument:xmlns:text:1.0")?.toIntOrNull() ?: 1).coerceIn(1, 6)
                        paragraph = StringBuilder()
                        capture = true
                    }
                }
                XmlPullParser.TEXT -> if (capture) paragraph.append(parser.text)
                XmlPullParser.END_TAG -> if ((parser.name == "h" || parser.name == "p") && capture) {
                    val value = paragraph.toString().replace(Regex("\\s+"), " ").trim()
                    if (value.isNotBlank()) {
                        if (element == "h") output.append("#".repeat(level)).append(' ').append(value).append("\n\n")
                        else output.append(value).append("\n\n")
                    }
                    capture = false
                }
            }
            event = parser.next()
        }
        return output.toString().trim().ifBlank { error(label + " tidak memiliki teks yang dapat diekstrak.") }
    }

    private fun epub(bytes: ByteArray, warnings: MutableList<String>): String {
        val entries = readEntries(bytes) {
            val lower = it.lowercase()
            lower == "meta-inf/container.xml" ||
                lower.endsWith(".opf") ||
                ((lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                    !lower.startsWith("meta-inf/") && !lower.contains("nav.xhtml") && !lower.contains("toc.xhtml"))
        }
        val chapterEntries = entries.filterKeys(::isEpubChapter)
        require(chapterEntries.isNotEmpty()) { "EPUB tidak memiliki bab HTML/XHTML yang dapat dibaca." }

        val spineOrder = epubSpineOrder(entries)
        val orderedPaths = spineOrder
            ?.filter { chapterEntries.containsKey(it) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                warnings.add("EPUB: manifest atau spine tidak dapat dibaca; bab diurutkan menurut urutan entri ZIP. Urutan ini dapat berbeda dari urutan bacaan buku.")
                chapterEntries.keys.toList()
            }

        if (orderedPaths.size > 300) warnings.add("EPUB: hanya 300 bab pertama menurut urutan bacaan yang diekstrak.")
        val output = orderedPaths.take(300).mapIndexedNotNull { index, path ->
            val source = chapterEntries[path] ?: return@mapIndexedNotNull null
            val title = path.substringAfterLast('/').substringBeforeLast('.').replace('-', ' ').replace('_', ' ')
            val text = htmlText(source)
            if (text.isBlank()) null
            else "## " + title.ifBlank { "Bab " + (index + 1) } + "\n\n" + text
        }.joinToString("\n\n")

        if (spineOrder != null) {
            warnings.add("EPUB: urutan bab mengikuti spine pada paket EPUB; tata letak, gambar tertanam, dan catatan kaki mungkin tidak dipertahankan.")
        }
        return output.ifBlank { error("Tidak ditemukan teks bab pada EPUB.") }
    }

    private fun isEpubChapter(path: String): Boolean {
        val lower = path.lowercase()
        return (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
            !lower.startsWith("meta-inf/") &&
            !lower.substringAfterLast('/').startsWith("nav.") &&
            !lower.substringAfterLast('/').startsWith("toc.")
    }

    private fun epubSpineOrder(entries: Map<String, String>): List<String>? {
        val containerPath = entries.keys.firstOrNull { it.equals("META-INF/container.xml", ignoreCase = true) }
            ?: return null
        val containerParser = parser(entries[containerPath] ?: return null)
        var packagePath: String? = null
        var event = containerParser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && containerParser.name == "rootfile") {
                packagePath = attr(containerParser, "full-path")
                if (!packagePath.isNullOrBlank()) break
            }
            event = containerParser.next()
        }
        val resolvedPackagePath = packagePath?.let { resolveEpubPath("", it) } ?: return null
        val opf = entries[resolvedPackagePath] ?: return null
        val basePath = resolvedPackagePath.substringBeforeLast('/', "")
        val manifest = linkedMapOf<String, Pair<String, String>>()
        val spineRefs = mutableListOf<String>()
        val opfParser = parser(opf)
        event = opfParser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (opfParser.name) {
                    "item" -> {
                        val id = attr(opfParser, "id")
                        val href = attr(opfParser, "href")
                        val mediaType = attr(opfParser, "media-type").orEmpty()
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            manifest[id] = href to mediaType
                        }
                    }
                    "itemref" -> {
                        val idRef = attr(opfParser, "idref")
                        if (!idRef.isNullOrBlank()) spineRefs.add(idRef)
                    }
                }
            }
            event = opfParser.next()
        }

        val ordered = spineRefs.mapNotNull { idRef ->
            val (href, mediaType) = manifest[idRef] ?: return@mapNotNull null
            if (mediaType.isNotBlank() && mediaType !in setOf("application/xhtml+xml", "text/html")) {
                return@mapNotNull null
            }
            resolveEpubPath(basePath, href)?.takeIf(::isEpubChapter)
        }
        return ordered.takeIf { it.isNotEmpty() }
    }

    private fun resolveEpubPath(basePath: String, href: String): String? {
        val cleanHref = Uri.decode(href.substringBefore('#').substringBefore('?'))
            .replace('\\', '/')
        if (cleanHref.isBlank()) return null
        val combined = if (cleanHref.startsWith('/')) cleanHref.drop(1)
            else listOf(basePath, cleanHref).filter { it.isNotBlank() }.joinToString("/")
        val segments = mutableListOf<String>()
        combined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex) else return null
                else -> segments.add(segment)
            }
        }
        return segments.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun notebook(bytes: ByteArray): String {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val cells = root.optJSONArray("cells") ?: error("Notebook tidak memiliki daftar cells.")
        val language = root.optJSONObject("metadata")
            ?.optJSONObject("kernelspec")
            ?.optString("language")
            ?.takeIf { it.isNotBlank() && it != "null" } ?: "text"
        val output = StringBuilder()
        for (index in 0 until cells.length()) {
            val cell = cells.optJSONObject(index) ?: continue
            val type = cell.optString("cell_type")
            val source = jsonSource(cell.opt("source")).trimEnd()
            if (source.isBlank()) continue
            when (type) {
                "markdown" -> output.append(source).append("\n\n")
                "code" -> output.append("### Kode ").append(index + 1).append("\n\n")
                    .append(fenced(language, source)).append("\n\n")
                else -> output.append(source).append("\n\n")
            }
        }
        return output.toString().trim().ifBlank { error("Notebook tidak memiliki sel teks atau kode.") }
    }

    private fun jsonSource(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) append(value.optString(index))
        }
        else -> ""
    }

    private fun htmlText(source: String): String {
        val clean = source.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1\\s*>"), "")
        return Html.fromHtml(clean, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun xmlText(xml: String, tags: Set<String>): String {
        val parser = parser(xml)
        val output = StringBuilder()
        var capture = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name in tags) capture = true
                XmlPullParser.TEXT -> if (capture) output.append(parser.text).append(' ')
                XmlPullParser.END_TAG -> if (parser.name in tags) capture = false
            }
            event = parser.next()
        }
        return output.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun fenced(language: String, value: String): String {
        val longest = Regex("\u0060{3,}").findAll(value).maxOfOrNull { it.value.length } ?: 0
        val fence = "\u0060".repeat(maxOf(3, longest + 1))
        return fence + language + "\n" + value.trimEnd() + "\n" + fence
    }

    private fun parser(xml: String): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        parser.setInput(xml.reader())
        return parser
    }

    private fun attr(parser: XmlPullParser, name: String, namespace: String? = null): String? =
        parser.getAttributeValue(namespace, name) ?: parser.getAttributeValue(null, name)
}
