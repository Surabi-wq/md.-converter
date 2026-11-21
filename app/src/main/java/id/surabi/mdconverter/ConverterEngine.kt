package id.surabi.mdconverter

import android.content.ContentResolver
import android.net.Uri
import android.text.Html
import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class ConversionResult(val markdown: String, val format: String, val warnings: List<String>)

object ConverterEngine {
    private const val MAX_BYTES = 100L * 1024 * 1024
    private val codeTypes = setOf("py","js","ts","jsx","tsx","java","kt","kts","c","h","cpp","hpp","cs","php","go","rs","swift","css","scss","sass","sh","bash","bat","ps1","sql","rb","r","lua","dart","scala")

    fun convert(resolver: ContentResolver, uri: Uri, name: String): ConversionResult {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_BYTES) { "Ukuran file melebihi 100 MB." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("File tidak dapat dibaca. Periksa izin akses dokumen.")
        require(bytes.isNotEmpty()) { "File kosong." }
        val ext = name.substringAfterLast('.', "").lowercase()
        val warnings = mutableListOf<String>()
        val text = decode(bytes)
        val body = when {
            ext == "pdf" -> pdf(bytes, warnings)
            ext == "docx" -> docx(bytes, warnings)
            ext in setOf("txt","md","markdown","log") -> text.trimEnd()
            ext in codeTypes -> fenced(ext, text)
            ext == "csv" || ext == "tsv" -> table(text, if (ext == "tsv") '\t' else ',')
            ext == "json" -> json(text)
            ext in setOf("jsonl","ndjson") -> jsonl(text)
            ext in setOf("html","htm","xhtml") -> html(text)
            ext == "rtf" -> rtf(text)
            ext in setOf("yaml","yml","toml","ini","xml","svg","eml","mhtml","ics","vcf") -> fenced(ext, text)
            else -> error("Format .$ext belum didukung. File asli tidak diubah.")
        }
        require(body.isNotBlank()) { "Tidak ditemukan teks yang dapat diekstrak. File mungkin berupa hasil scan/gambar atau kosong." }
        val title = name.substringBeforeLast('.', name).replace("\\", "\\\\").replace("#", "\\#")
        return ConversionResult("# $title\n\n$body\n", ext.ifBlank { "unknown" }, warnings.distinct())
    }

    private fun decode(bytes: ByteArray): String {
        val decoded = runCatching { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
        return (decoded ?: runCatching { Charset.forName("windows-1252").newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }.getOrElse { bytes.toString(Charsets.UTF_8) })
            .removePrefix("\uFEFF").replace("\u0000", "")
    }

    private fun fenced(ext: String, value: String): String {
        val longest = Regex("`{3,}").findAll(value).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longest + 1))
        return "$fence$ext\n${value.trimEnd()}\n$fence"
    }

    private fun json(value: String): String {
        val source = value.trim()
        require(source.isNotEmpty()) { "JSON kosong." }
        val result = when (source.first()) {
            '{' -> JSONObject(source).toString(2)
            '[' -> JSONArray(source).toString(2)
            else -> error("JSON harus berupa objek atau array.")
        }
        return fenced("json", result)
    }

    private fun jsonl(value: String): String {
        val items = value.lineSequence().filter { it.isNotBlank() }.mapIndexed { i, line ->
            runCatching {
                val s = line.trim()
                if (s.startsWith("{")) JSONObject(s).toString(2) else JSONArray(s).toString(2)
            }.getOrElse { error("JSONL tidak valid pada baris ${i + 1}.") }
        }.toList()
        require(items.isNotEmpty()) { "File JSONL kosong." }
        return items.mapIndexed { i, item -> "### Record ${i + 1}\n\n${fenced("json", item)}" }.joinToString("\n\n")
    }

    private fun table(value: String, delimiter: Char): String {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '"' && quoted && i + 1 < value.length && value[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { row.add(cell.toString()); cell.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < value.length && value[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.setLength(0); rows.add(row); row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "CSV tidak valid: tanda kutip tidak berpasangan." }
        row.add(cell.toString())
        if (row.any { it.isNotEmpty() } || rows.isEmpty()) rows.add(row)
        val clean = rows.filter { it.any(String::isNotBlank) }
        require(clean.isNotEmpty()) { "Tabel kosong." }
        val width = clean.maxOf { it.size }
        val normalized = clean.map { it + List(width - it.size) { "" } }
        fun render(r: List<String>) = "| " + r.joinToString(" | ") { it.trim().replace("|", "\\|").replace("\n", "<br>") } + " |"
        return buildString {
            appendLine(render(normalized.first()))
            appendLine("| " + List(width) { "---" }.joinToString(" | ") + " |")
            normalized.drop(1).forEach { appendLine(render(it)) }
        }.trimEnd()
    }

    private fun html(value: String): String {
        val clean = value.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1\\s*>"), "")
            .replace(Regex("(?i)<(h[1-6])\\b[^>]*>"), "\n\n### ")
            .replace(Regex("(?i)<li\\b[^>]*>"), "\n- ")
            .replace(Regex("(?i)</(p|div|section|article|li|tr|h[1-6]|br|hr)\\s*>"), "\n")
        return Html.fromHtml(clean, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun rtf(value: String): String = value
        .replace(Regex("\\\\'([0-9a-fA-F]{2})")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("\\\\par[d]?\\b?"), "\n")
        .replace(Regex("\\\\(tab|line)\\b"), "\t")
        .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
        .replace(Regex("\\\\[^a-zA-Z]"), "")
        .replace(Regex("[{}]"), "").trim()

    private fun pdf(bytes: ByteArray, warnings: MutableList<String>): String {
        PDDocument.load(bytes).use { document ->
            require(!document.isEncrypted) { "PDF terenkripsi atau dilindungi kata sandi." }
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                addMoreFormatting = true
                lineSeparator = "\n"
                paragraphStart = "\n\n"
                paragraphEnd = "\n"
            }
            val result = stripper.getText(document).trim()
            if (result.isBlank()) error("PDF tidak memiliki lapisan teks. PDF hasil pemindaian memerlukan OCR yang belum tersedia.")
            if (document.numberOfPages > 300) warnings.add("PDF memiliki lebih dari 300 halaman; periksa kelengkapan hasil.")
            return result
        }
    }

    private fun docx(bytes: ByteArray, warnings: MutableList<String>): String {
        require(bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) { "DOCX tidak valid: arsip ZIP tidak ditemukan." }
        var xml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var size = 0
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        size += n
                        require(size <= 20 * 1024 * 1024) { "Konten XML DOCX terlalu besar." }
                        out.write(buffer, 0, n)
                    }
                    xml = out.toString("UTF-8")
                    break
                }
            }
        }
        val source = xml ?: error("DOCX tidak valid: document.xml tidak ditemukan.")
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(source.reader())
        val result = StringBuilder()
        var paragraph = StringBuilder()
        var inParagraph = false
        var inText = false
        var style = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> { inParagraph = true; paragraph = StringBuilder(); style = "" }
                    "pStyle" -> style = parser.getAttributeValue(null, "val") ?: ""
                    "t" -> inText = true
                    "tab" -> if (inParagraph) paragraph.append("    ")
                    "br", "cr" -> if (inParagraph) paragraph.append('\n')
                }
                XmlPullParser.TEXT -> if (inParagraph && inText) paragraph.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "p" -> {
                        val value = paragraph.toString().trim()
                        if (value.isNotEmpty()) {
                            val heading = Regex("(?i)^heading([1-6])$").matchEntire(style)
                            if (heading != null) result.append("#".repeat(heading.groupValues[1].toInt())).append(' ').append(value).append("\n\n")
                            else result.append(value).append("\n\n")
                        }
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
        warnings.add("DOCX: teks paragraf dan heading dasar diekstrak; tabel, gambar, catatan kaki, header/footer, dan tata letak belum dipertahankan.")
        return result.toString().trim()
    }
}
