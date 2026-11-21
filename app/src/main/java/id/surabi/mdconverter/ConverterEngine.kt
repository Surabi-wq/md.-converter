package id.surabi.mdconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.Html
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.rendering.PDFRenderer
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class ConversionResult(val markdown: String, val format: String, val warnings: List<String>)

object ConverterEngine {
    private const val MAX_BYTES = 64L * 1024 * 1024
    private const val MAX_PDF_OCR_PAGES = 50
    private val imageTypes = SupportedFormats.imageTypes
    private val audioTypes = SupportedFormats.audioTypes
    private val videoTypes = SupportedFormats.videoTypes
    private val codeTypes = SupportedFormats.codeTypes
    private val plainTextTypes = SupportedFormats.plainTextTypes
    private val archiveDocumentTypes = SupportedFormats.archiveDocumentTypes

    fun convert(context: Context, uri: Uri, name: String): ConversionResult {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val ext = resolveExtension(name, mime, context, uri)
        val warnings = mutableListOf<String>()
        val title = name.substringBeforeLast('.', name).replace("\\", "\\\\").replace("#", "\\#")

        // Audio/video is read from the provider URI; do not copy the media payload into the heap.
        if (ext in audioTypes || ext in videoTypes || mime.startsWith("audio/") || mime.startsWith("video/")) {
            val body = mediaToMarkdown(context, uri, name, ext in videoTypes || mime.startsWith("video/"), warnings)
            return ConversionResult("# " + title + "\n\n" + body + "\n", ext, warnings.distinct())
        }

        // Images are bounds-checked and decoded directly from a fresh stream at a lower sample size.
        if (ext in imageTypes || mime.startsWith("image/")) {
            val body = imageToMarkdown(context, uri, name, ext.ifBlank { mime.substringAfter("/", "image") }, queryFileSize(context, uri), warnings)
            return ConversionResult("# " + title + "\n\n" + body + "\n", ext, warnings.distinct())
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
                require(total <= MAX_BYTES) { "Ukuran file dokumen melebihi 64 MB." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("File tidak dapat dibaca. Periksa izin akses dokumen.")
        require(bytes.isNotEmpty()) { "File kosong." }
        val text = if (ext in codeTypes || ext in plainTextTypes) decode(bytes) else ""
        val body = when {
            ext in archiveDocumentTypes -> ArchiveDocumentReader.convert(ext, bytes, warnings)
            ext == "pdf" -> pdf(bytes, warnings)
            ext == "docx" -> docx(bytes, warnings)
            ext in setOf("txt", "md", "markdown", "mdx", "log", "rst", "adoc", "tex") -> text.trimEnd()
            ext in codeTypes -> fenced(ext, text)
            ext == "csv" -> table(text, detectCsvDelimiter(text))
            ext == "tsv" -> table(text, '\t')
            ext == "json" -> json(text)
            ext == "jsonl" || ext == "ndjson" -> jsonl(text)
            ext in setOf("html", "htm", "xhtml") -> html(text)
            ext == "rtf" -> rtf(text)
            ext in plainTextTypes -> fenced(ext, text)
            else -> error("Format ." + ext + " belum didukung. File asli tidak diubah.")
        }
        require(body.isNotBlank()) { "Tidak ditemukan teks yang dapat diekstrak. File mungkin berupa hasil scan/gambar atau kosong." }
        return ConversionResult("# " + title + "\n\n" + body + "\n", ext.ifBlank { "unknown" }, warnings.distinct())
    }

    private fun resolveExtension(name: String, mime: String, context: Context, uri: Uri): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        val byMime = mapOf(
            "application/pdf" to "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
            "application/vnd.oasis.opendocument.text" to "odt",
            "application/vnd.oasis.opendocument.spreadsheet" to "ods",
            "application/vnd.oasis.opendocument.presentation" to "odp",
            "application/epub+zip" to "epub",
            "application/x-ipynb+json" to "ipynb",
            "text/plain" to "txt", "text/markdown" to "md", "text/csv" to "csv",
            "application/json" to "json", "text/html" to "html", "application/rtf" to "rtf",
            "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp",
            "image/gif" to "gif", "image/heic" to "heic", "image/heif" to "heif",
            "image/avif" to "avif", "image/tiff" to "tiff", "image/bmp" to "bmp",
            "audio/x-m4a" to "m4a", "audio/mpeg" to "mp3", "audio/mp4" to "m4a",
            "audio/aac" to "aac", "audio/wav" to "wav", "audio/x-wav" to "wav",
            "audio/x-aac" to "aac", "audio/ogg" to "ogg", "audio/flac" to "flac",
            "audio/x-flac" to "flac", "audio/amr" to "amr", "audio/3gpp" to "amr",
            "audio/x-ms-wma" to "wma", "video/mp4" to "mp4", "video/x-m4v" to "m4v",
            "video/webm" to "webm", "video/quicktime" to "mov", "video/x-matroska" to "mkv",
            "video/3gpp" to "3gp", "video/3gpp2" to "3g2", "video/mp2t" to "ts",
            "video/mpeg" to "mpeg", "video/x-msvideo" to "avi", "video/x-ms-wmv" to "wmv",
            "video/x-flv" to "flv", "video/x-ms-asf" to "asf"
        )
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(512)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read <= 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
        }.getOrNull() ?: ByteArray(0)
        fun ascii(start: Int, length: Int): String =
            if (head.size >= start + length) String(head, start, length, Charsets.US_ASCII) else ""
        val signature = when {
            head.size >= 5 && ascii(0, 5) == "%PDF-" -> "pdf"
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "jpg"
            head.size >= 8 && head[0] == 0x89.toByte() && ascii(1, 3) == "PNG" -> "png"
            ascii(0, 6) in setOf("GIF87a", "GIF89a") -> "gif"
            ascii(0, 2) == "BM" -> "bmp"
            head.size >= 4 && ((head[0] == 0x49.toByte() && head[1] == 0x49.toByte() && head[2] == 0x2A.toByte() && head[3] == 0.toByte()) ||
                (head[0] == 0x4D.toByte() && head[1] == 0x4D.toByte() && head[2] == 0.toByte() && head[3] == 0x2A.toByte())) -> "tiff"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "webp"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> "wav"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "AVI " -> "avi"
            head.size >= 12 && ascii(4, 4) == "ftyp" -> {
                val brand = ascii(8, 4)
                when {
                    brand == "qt  " -> "mov"
                    brand.startsWith("M4V") -> "m4v"
                    brand in setOf("M4A ", "M4B ", "F4A ", "F4B ") -> if (brand == "M4B ") "m4b" else "m4a"
                    brand in setOf("avif", "avis") -> "avif"
                    brand in setOf("heic", "heix", "hevc", "hevx") -> "heic"
                    brand in setOf("mif1", "msf1") -> "heif"
                    brand.startsWith("3g2") -> "3g2"
                    brand.startsWith("3g") -> "3gp"
                    mime.startsWith("audio/") -> "m4a"
                    else -> "mp4"
                }
            }
            ascii(0, 4) == "OggS" -> if (MediaSignatures.containsAscii(head, "OpusHead")) "opus" else "ogg"
            MediaSignatures.isAdtsAac(head) -> "aac"
            ascii(0, 3) == "ID3" || MediaSignatures.isMpegAudioFrame(head) -> "mp3"
            ascii(0, 4) == "fLaC" -> "flac"
            head.size >= 6 && ascii(0, 5) == "#!AMR" && head[5] == 0x0A.toByte() -> "amr"
            head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() ->
                if (mime == "video/webm" || fromName == "webm") "webm" else "mkv"
            head.size >= 1 && head[0] == 0x47.toByte() -> "ts"
            head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() ->
                detectZipExtension(context, uri).orEmpty()
            else -> ""
        }
        if (signature.isNotBlank()) return signature
        if (fromName in SupportedFormats.supportedTypes) return fromName
        byMime[mime]?.let { return it }
        return fromName.takeIf { it.isNotBlank() && it !in setOf("bin", "dat", "tmp", "file") }.orEmpty()
    }

    private fun detectZipExtension(context: Context, uri: Uri): String? {
        val names = mutableSetOf<String>()
        var openDocumentMime: String? = null
        return runCatching {
            var totalUncompressed = 0L
            var entryCount = 0
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount++
                        require(entryCount <= 10_000) { "Arsip memiliki terlalu banyak entri." }
                        val captureMime = entry.name == "mimetype"
                        var entryBytes = 0L
                        val output = if (captureMime) ByteArrayOutputStream(128) else null
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalUncompressed += count
                            require(entryBytes <= 32L * 1024 * 1024) {
                                "Satu entri arsip terlalu besar saat mendeteksi format."
                            }
                            require(totalUncompressed <= 64L * 1024 * 1024) {
                                "Data hasil dekompresi arsip terlalu besar saat mendeteksi format."
                            }
                            if (captureMime && output!!.size() < 128) {
                                output.write(buffer, 0, minOf(count, 128 - output.size()))
                            }
                        }
                        if (captureMime) openDocumentMime = output!!.toString("UTF-8").trim()
                        if (entry.name in setOf(
                                "word/document.xml",
                                "xl/workbook.xml",
                                "ppt/presentation.xml",
                                "META-INF/container.xml"
                            )
                        ) {
                            names.add(entry.name)
                            break
                        }
                    }
                }
            }
            when {
                names.contains("word/document.xml") -> "docx"
                names.contains("xl/workbook.xml") -> "xlsx"
                names.contains("ppt/presentation.xml") -> "pptx"
                names.contains("META-INF/container.xml") -> "epub"
                openDocumentMime == "application/vnd.oasis.opendocument.text" -> "odt"
                openDocumentMime == "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
                openDocumentMime == "application/vnd.oasis.opendocument.presentation" -> "odp"
                else -> null
            }
        }.getOrNull()
    }

    private fun queryFileSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

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
        return fenced("json", prettyJsonValue(source))
    }

    private fun jsonl(value: String): String {
        val items = value.lineSequence().filter { it.isNotBlank() }.mapIndexed { i, line ->
            runCatching { prettyJsonValue(line.trim()) }
                .getOrElse { error("JSONL tidak valid pada baris ${i + 1}.") }
        }.toList()
        require(items.isNotEmpty()) { "File JSONL kosong." }
        return items.mapIndexed { i, item ->
            "### Record ${i + 1}\n\n${fenced("json", item)}"
        }.joinToString("\n\n")
    }

    private fun prettyJsonValue(source: String): String {
        val parser = JSONTokener(source)
        val parsed = parser.nextValue()
        require(parser.nextClean() == '\u0000') { "Konten JSON memiliki karakter tambahan setelah nilai utama." }
        return when (parsed) {
            is JSONObject -> parsed.toString(2)
            is JSONArray -> parsed.toString(2)
            is String -> JSONObject.quote(parsed)
            JSONObject.NULL -> "null"
            is Number, is Boolean -> parsed.toString()
            else -> error("Tipe nilai JSON tidak didukung.")
        }
    }

    private fun detectCsvDelimiter(value: String): Char {
        val sample = value.lineSequence().take(8).filter { it.isNotBlank() }.toList()
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { delimiter ->
            sample.sumOf { line -> countUnquotedDelimiter(line, delimiter) }
        }?.takeIf { delimiter ->
            sample.any { countUnquotedDelimiter(it, delimiter) > 0 }
        } ?: ','
    }

    private fun countUnquotedDelimiter(line: String, delimiter: Char): Int {
        var quoted = false
        var count = 0
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> index++
                line[index] == '"' -> quoted = !quoted
                line[index] == delimiter && !quoted -> count++
            }
            index++
        }
        return count
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

    private fun imageToMarkdown(
        context: Context,
        uri: Uri,
        name: String,
        ext: String,
        fileSize: Long?,
        warnings: MutableList<String>
    ): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("File gambar tidak dapat dibuka.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "File gambar tidak valid atau decoder perangkat tidak mendukung format ini." }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (ext in setOf("jpg", "jpeg")) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            inDither = true
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Gambar tidak dapat didekode. Format mungkin belum didukung perangkat.")
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try {
                recognizeText(bitmap, recognizer)
            } catch (error: Exception) {
                warnings.add("OCR gambar tidak dapat dijalankan: " + (error.message ?: "model/perangkat tidak mendukung"))
                ""
            } finally {
                recognizer.close()
            }
            val output = StringBuilder()
            output.appendLine("## Informasi gambar")
            output.appendLine("- Nama file: " + name)
            output.appendLine("- Format: " + ext.uppercase())
            output.appendLine("- Dimensi: " + bounds.outWidth + " × " + bounds.outHeight + " piksel")
            fileSize?.let { output.appendLine("- Ukuran file: " + String.format(java.util.Locale.ROOT, "%.2f", it / 1024.0) + " KB") }
            output.appendLine("- Resolusi untuk OCR: " + bitmap.width + " × " + bitmap.height + " piksel")
            output.appendLine()
            output.appendLine("## Teks yang terdeteksi (OCR)")
            if (text.isBlank()) output.appendLine("_Tidak ditemukan teks yang dapat dibaca pada gambar._")
            else {
                output.appendLine(text)
                warnings.add("OCR dapat keliru pada gambar buram, miring, tulisan tangan, atau aksara yang tidak didukung.")
            }
            return output.toString().trim()
        } finally {
            bitmap.recycle()
        }
    }

    private fun recognizeText(bitmap: Bitmap, sharedRecognizer: TextRecognizer? = null): String {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxSide > 2048) {
            val ratio = 2048f / maxSide.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val recognizer = sharedRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(scaled, 0))).text.trim()
        } finally {
            if (sharedRecognizer == null) recognizer.close()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun mediaToMarkdown(context: Context, uri: Uri, name: String, isVideo: Boolean, warnings: MutableList<String>): String {
        val retriever = MediaMetadataRetriever()
        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            val configured = try {
                descriptor != null && descriptor.length > 0 &&
                    runCatching { retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length) }.isSuccess
            } finally {
                descriptor?.close()
            }
            if (!configured) {
                val fallback = runCatching { retriever.setDataSource(context, uri) }
                if (fallback.isFailure) {
                    warnings.add("File media terdeteksi, tetapi decoder Android tidak dapat membaca metadata: " +
                        (fallback.exceptionOrNull()?.message ?: "format/codec tidak didukung") + ".")
                    return buildString {
                        appendLine(if (isVideo) "## Informasi video" else "## Informasi audio")
                        appendLine("- Nama file: " + name)
                        appendLine("- Format terdeteksi: " + name.substringAfterLast('.', "media").uppercase())
                        appendLine("- MIME: " + (context.contentResolver.getType(uri) ?: "Tidak diketahui"))
                        appendLine("_Metadata rinci tidak tersedia pada decoder perangkat ini._")
                    }.trim()
                }
            }
            fun meta(key: Int): String? = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val out = StringBuilder()
            out.appendLine(if (isVideo) "## Informasi video" else "## Informasi audio")
            out.appendLine("- Nama file: " + name)
            out.appendLine("- Durasi: " + (duration?.let { formatDuration(it) } ?: "Tidak diketahui"))
            out.appendLine("- MIME/container: " + (meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "Tidak diketahui"))
            meta(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out.appendLine("- Judul: " + it) }
            meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out.appendLine("- Artis: " + it) }
            meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out.appendLine("- Album: " + it) }
            meta(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { out.appendLine("- Genre: " + it) }
            meta(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { out.appendLine("- Tanggal: " + it) }
            meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { out.appendLine("- Bitrate: " + it + " bit/detik") }
            meta(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.let { out.appendLine("- Jumlah track: " + it) }
            if (isVideo) {
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { out.appendLine("- Lebar: " + it + " piksel") }
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { out.appendLine("- Tinggi: " + it + " piksel") }
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { out.appendLine("- Rotasi: " + it + " derajat") }
                meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { out.appendLine("- Frame rate: " + it + " fps") }
                out.appendLine()
                out.appendLine("## Teks pada cuplikan video (OCR)")
                var found = false
                if (Build.VERSION.SDK_INT < 27) {
                    warnings.add("OCR cuplikan video memerlukan Android 8.1 (API 27) atau lebih baru; pengambilan frame resolusi penuh dilewati untuk menghindari lonjakan RAM.")
                    out.appendLine("_OCR cuplikan video tidak dijalankan pada versi Android ini._")
                } else {
                    val total = duration ?: 0L
                    val points = if (total > 0) listOf(total / 4, total / 2, total * 3 / 4) else listOf(-1L)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        points.distinct().forEachIndexed { index, ms ->
                            val frame = runCatching {
                                if (ms < 0) retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1280, 1280)
                                else retriever.getScaledFrameAtTime(
                                    ms * 1000,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    1280,
                                    1280
                                )
                            }.getOrNull()
                            if (frame != null) try {
                                val text = runCatching { recognizeText(frame, recognizer) }
                                    .getOrElse { warnings.add("OCR cuplikan video gagal: " + (it.message ?: "tidak didukung") + "."); "" }
                                if (text.isNotBlank()) {
                                    found = true
                                    out.appendLine("### Cuplikan " + (index + 1) + " — " +
                                        if (ms < 0) "waktu tidak diketahui" else formatDuration(ms))
                                    out.appendLine(text)
                                    out.appendLine()
                                }
                            } finally {
                                frame.recycle()
                            }
                        }
                    } finally {
                        recognizer.close()
                    }
                }
                if (!found) out.appendLine("_Tidak ditemukan teks pada cuplikan yang diperiksa._")
                warnings.add("Video diperiksa pada maksimal tiga cuplikan; hasil OCR bukan transkrip dialog penuh atau subtitle.")
            } else {
                out.appendLine()
                out.appendLine("## Transkripsi")
                out.appendLine("_Transkripsi ucapan/lirik belum tersedia; yang diekstrak hanya metadata audio._")
                warnings.add("Audio belum ditranskripsikan. Konversi saat ini mengambil metadata; pengenalan ucapan memerlukan mesin speech-to-text.")
            }
            return out.toString().trim()
        } finally {
            retriever.release()
        }
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
            val textLayer = stripper.getText(document).trim()
            if (textLayer.isNotBlank()) {
                if (document.numberOfPages > 300) warnings.add("PDF memiliki lebih dari 300 halaman; periksa kelengkapan hasil.")
                return textLayer
            }

            val pageCount = document.numberOfPages
            require(pageCount > 0) { "PDF tidak memiliki halaman." }
            val pageLimit = minOf(pageCount, MAX_PDF_OCR_PAGES)
            val renderer = PDFRenderer(document)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val extracted = StringBuilder()
            try {
                for (pageIndex in 0 until pageLimit) {
                    val bitmap = try {
                        renderer.renderImage(pageIndex, 1.35f)
                    } catch (error: Exception) {
                        warnings.add("Halaman PDF ${pageIndex + 1} tidak dapat dirender untuk OCR: ${error.message ?: "kesalahan render"}.")
                        null
                    }
                    if (bitmap != null) {
                        try {
                            val pageText = runCatching { recognizeText(bitmap, recognizer) }
                                .getOrElse { error ->
                                    warnings.add("OCR halaman PDF ${pageIndex + 1} gagal: ${error.message ?: "tidak didukung"}.")
                                    ""
                                }
                            if (pageText.isNotBlank()) {
                                extracted.append("## Halaman ").append(pageIndex + 1).append("\n\n")
                                    .append(pageText).append("\n\n")
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            } finally {
                recognizer.close()
            }

            if (pageCount > pageLimit) {
                warnings.add("OCR PDF dibatasi hingga $pageLimit dari $pageCount halaman untuk menjaga memori dan waktu proses.")
            }
            require(extracted.isNotBlank()) {
                "PDF tidak memiliki lapisan teks dan OCR tidak menemukan teks pada halaman yang diperiksa."
            }
            warnings.add("PDF hasil scan diproses dengan OCR lokal. Tata letak, tabel kompleks, dan urutan kolom dapat berbeda dari dokumen asli.")
            return extracted.toString().trim()
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
