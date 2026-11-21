package id.surabi.mdconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Html
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
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
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val imageTypes = setOf("jpg","jpeg","png","webp","bmp","gif","tif","tiff","heic","heif")
    private val audioTypes = setOf("mp3","m4a","aac","wav","ogg","oga","opus","flac","amr","wma")
    private val videoTypes = setOf("mp4","m4v","mkv","webm","mov","avi","3gp","ts","mpeg","mpg")
    private val codeTypes = setOf("py","js","ts","jsx","tsx","java","kt","kts","c","h","cpp","hpp","cs","php","go","rs","swift","css","scss","sass","sh","bash","bat","ps1","sql","rb","r","lua","dart","scala")

    fun convert(context: Context, uri: Uri, name: String): ConversionResult {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val ext = resolveExtension(name, mime, context, uri)
        val warnings = mutableListOf<String>()
        val title = name.substringBeforeLast('.', name).replace("\\", "\\\\").replace("#", "\\#")

        // Metadata extraction reads the SAF URI directly; large media is never copied into RAM.
        if (ext in audioTypes || ext in videoTypes || mime.startsWith("audio/") || mime.startsWith("video/")) {
            val body = mediaToMarkdown(context, uri, name, ext in videoTypes || mime.startsWith("video/"), warnings)
            return ConversionResult("# $title\n\n$body\n", ext, warnings.distinct())
        }

        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_BYTES) { "Ukuran file melebihi 64 MB." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("File tidak dapat dibaca. Periksa izin akses dokumen.")
        require(bytes.isNotEmpty()) { "File kosong." }
        val text = if (ext in codeTypes || ext in setOf("txt","md","markdown","log","csv","tsv","json","jsonl","ndjson","html","htm","xhtml","rtf","yaml","yml","toml","ini","xml","svg","eml","mhtml","ics","vcf")) decode(bytes) else ""
        val body = when {
            ext == "pdf" -> pdf(bytes, warnings)
            ext == "docx" -> docx(bytes, warnings)
            ext in imageTypes || mime.startsWith("image/") -> imageToMarkdown(bytes, ext.ifBlank { mime.substringAfter("/", "image") }, warnings)
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
        return ConversionResult("# $title\n\n$body\n", ext.ifBlank { "unknown" }, warnings.distinct())
    }

    private fun resolveExtension(name: String, mime: String, context: Context, uri: Uri): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank() && fromName !in setOf("bin", "dat", "tmp", "file")) return fromName
        val byMime = mapOf(
            "application/pdf" to "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "text/plain" to "txt", "text/markdown" to "md", "text/csv" to "csv",
            "application/json" to "json", "text/html" to "html", "application/rtf" to "rtf",
            "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp",
            "image/gif" to "gif", "image/heic" to "heic", "image/heif" to "heif",
            "image/tiff" to "tiff", "image/bmp" to "bmp",
            "audio/x-m4a" to "m4a", "audio/mpeg" to "mp3", "audio/mp4" to "m4a",
            "audio/aac" to "aac", "audio/wav" to "wav", "audio/x-wav" to "wav",
            "audio/x-aac" to "aac", "audio/ogg" to "ogg", "audio/flac" to "flac",
            "audio/x-flac" to "flac", "audio/amr" to "amr", "audio/3gpp" to "amr",
            "audio/x-ms-wma" to "wma", "video/mp4" to "mp4", "video/x-m4v" to "m4v",
            "video/webm" to "webm", "video/quicktime" to "mov", "video/x-matroska" to "mkv",
            "video/3gpp" to "3gp", "video/mp2t" to "ts", "video/mpeg" to "mpeg",
            "video/x-msvideo" to "avi"
        )
        byMime[mime]?.let { return it }
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(32)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read <= 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
        }.getOrNull() ?: return ""
        fun ascii(start: Int, length: Int) = if (head.size >= start + length) String(head, start, length, Charsets.US_ASCII) else ""
        return when {
            head.size >= 5 && ascii(0, 5) == "%PDF-" -> "pdf"
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "jpg"
            head.size >= 8 && head[0] == 0x89.toByte() && ascii(1, 3) == "PNG" -> "png"
            ascii(0, 6) in setOf("GIF87a", "GIF89a") -> "gif"
            ascii(0, 2) == "BM" -> "bmp"
            head.size >= 4 && ((head[0] == 0x49.toByte() && head[1] == 0x49.toByte() && head[2] == 0x2A.toByte() && head[3] == 0.toByte()) || (head[0] == 0x4D.toByte() && head[1] == 0x4D.toByte() && head[2] == 0.toByte() && head[3] == 0x2A.toByte())) -> "tiff"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "webp"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> "wav"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "AVI " -> "avi"
            head.size >= 12 && ascii(4, 4) == "ftyp" -> {
                val brand = ascii(8, 4)
                when {
                    mime.startsWith("video/") || brand == "qt  " || brand.startsWith("isom") || brand.startsWith("mp4") -> "mp4"
                    brand in setOf("M4A ", "M4B ", "F4A ", "F4B ") -> "m4a"
                    else -> "mp4"
                }
            }
            ascii(0, 4) == "OggS" -> "ogg"
            ascii(0, 3) == "ID3" || (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) -> "mp3"
            ascii(0, 4) == "fLaC" -> "flac"
            head.size >= 6 && ascii(0, 5) == "#!AMR" && head[5] == 0x0A.toByte() -> "amr"
            head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> if (mime == "video/webm") "webm" else "mkv"
            head.size >= 1 && head[0] == 0x47.toByte() -> "ts"
            else -> ""
        }
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
            .replace(Regex("[ \t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun rtf(value: String): String = value
        .replace(Regex("\\\\'([0-9a-fA-F]{2})")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("\\\\par[d]?\\b?"), "\n")
        .replace(Regex("\\\\(tab|line)\\b"), "\t")
        .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
        .replace(Regex("\\\\[^a-zA-Z]"), "")
        .replace(Regex("[{}]"), "").trim()

    private fun imageToMarkdown(bytes: ByteArray, ext: String, warnings: MutableList<String>): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "File gambar tidak valid." }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Gambar tidak dapat didekode.")
        try {
            val text = runCatching { recognizeText(bitmap) }.getOrElse { warnings.add("OCR gambar tidak dapat dijalankan: ${it.message ?: "model/perangkat tidak mendukung"}"); "" }
            val output = StringBuilder()
            output.appendLine("## Informasi gambar")
            output.appendLine("- Format: ${ext.uppercase()}")
            output.appendLine("- Dimensi: ${bounds.outWidth} × ${bounds.outHeight} piksel")
            output.appendLine("- Ukuran file: ${"%.2f".format(bytes.size / 1024.0)} KB")
            output.appendLine()
            output.appendLine("## Teks yang terdeteksi (OCR)")
            if (text.isBlank()) output.appendLine("_Tidak ditemukan teks yang dapat dibaca pada gambar._") else { output.appendLine(text); warnings.add("OCR dapat keliru pada gambar buram, miring, atau tulisan tangan.") }
            return output.toString().trim()
        } finally { bitmap.recycle() }
    }

    private fun recognizeText(bitmap: Bitmap): String {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxSide > 2048) {
            val ratio = 2048f / maxSide.toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else bitmap
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(scaled, 0))).text.trim()
        } finally {
            recognizer.close()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun mediaToMarkdown(context: Context, uri: Uri, name: String, isVideo: Boolean, warnings: MutableList<String>): String {
        val retriever = MediaMetadataRetriever()
        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            val configured = try {
                descriptor != null && descriptor.length >= 0 && runCatching { retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length) }.isSuccess
            } finally { descriptor?.close() }
            if (!configured) {
                val fallback = runCatching { retriever.setDataSource(context, uri) }
                if (fallback.isFailure) {
                    warnings.add("File media terdeteksi, tetapi decoder Android tidak dapat membaca metadata: ${fallback.exceptionOrNull()?.message ?: "format/codec tidak didukung"}.")
                    return buildString {
                        appendLine(if (isVideo) "## Informasi video" else "## Informasi audio")
                        appendLine("- Nama file: $name")
                        appendLine("- Format terdeteksi: ${name.substringAfterLast('.', "media").uppercase()}")
                        appendLine("- MIME: ${context.contentResolver.getType(uri) ?: "Tidak diketahui"}")
                        appendLine("_Metadata rinci tidak tersedia pada decoder perangkat ini._")
                    }.trim()
                }
            }
            fun meta(key: Int): String? = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val out = StringBuilder()
            out.appendLine(if (isVideo) "## Informasi video" else "## Informasi audio")
            out.appendLine("- Nama file: $name")
            out.appendLine("- Durasi: ${duration?.let { formatDuration(it) } ?: "Tidak diketahui"}")
            out.appendLine("- MIME/container: ${meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "Tidak diketahui"}")
            meta(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out.appendLine("- Judul: $it") }
            meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out.appendLine("- Artis: $it") }
            meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out.appendLine("- Album: $it") }
            meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { out.appendLine("- Bitrate: $it bit/detik") }
            if (isVideo) {
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { out.appendLine("- Lebar: $it piksel") }
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { out.appendLine("- Tinggi: $it piksel") }
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { out.appendLine("- Rotasi: $it derajat") }
                out.appendLine(); out.appendLine("## Teks pada cuplikan video (OCR)")
                val total = duration ?: 0L
                val points = if (total > 0) listOf(total / 3, total * 2 / 3) else listOf(-1L)
                var found = false
                points.distinct().forEachIndexed { index, ms ->
                    val frame = runCatching { if (ms < 0) retriever.getFrameAtTime() else retriever.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                    if (frame != null) try {
                        val text = runCatching { recognizeText(frame) }.getOrElse { warnings.add("OCR cuplikan video gagal: ${it.message ?: "tidak didukung"}."); "" }
                        if (text.isNotBlank()) { found = true; out.appendLine("### Cuplikan ${index + 1} — ${if (ms < 0) "waktu tidak diketahui" else formatDuration(ms)}"); out.appendLine(text); out.appendLine() }
                    } finally { frame.recycle() }
                }
                if (!found) out.appendLine("_Tidak ditemukan teks pada cuplikan yang diperiksa._")
                warnings.add("Video diperiksa pada maksimal dua cuplikan; hasil OCR bukan transkrip dialog penuh.")
            } else {
                out.appendLine(); out.appendLine("## Transkripsi"); out.appendLine("_Transkripsi ucapan/lirik belum tersedia; yang diekstrak hanya metadata audio._")
                warnings.add("Audio belum ditranskripsikan. Diperlukan mesin speech-to-text untuk mengubah ucapan menjadi teks.")
            }
            return out.toString().trim()
        } finally { retriever.release() }
    }

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000; val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h,m,s) else "%02d:%02d".format(m,s)
    }

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
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
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
                    "pStyle" -> style = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val") ?: parser.getAttributeValue(null, "val") ?: ""
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
