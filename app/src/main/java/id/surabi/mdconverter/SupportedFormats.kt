package id.surabi.mdconverter

internal data class FormatGroup(
    val title: String,
    val extensions: List<String>,
    val note: String
)

internal object SupportedFormats {
    val imageTypes = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "tif", "tiff", "heic", "heif", "avif")
    val audioTypes = setOf("mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "wma", "aif", "aiff", "caf", "3ga")
    val videoTypes = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "ts", "m2ts", "mpeg", "mpg", "mpe", "m2v", "vob", "flv", "f4v", "wmv", "asf")
    val codeTypes = setOf(
        "py", "js", "mjs", "cjs", "ts", "jsx", "tsx", "java", "kt", "kts", "c", "h", "cpp", "hpp",
        "cs", "php", "go", "rs", "swift", "css", "scss", "sass", "less", "sh", "bash", "bat", "ps1",
        "sql", "rb", "r", "lua", "dart", "scala", "groovy", "gradle", "vue", "svelte", "xml", "svg"
    )
    val plainTextTypes = setOf(
        "txt", "md", "markdown", "mdx", "log", "csv", "tsv", "json", "jsonc", "jsonl", "ndjson",
        "html", "htm", "xhtml", "rtf", "yaml", "yml", "toml", "ini", "properties", "conf", "cfg",
        "env", "eml", "mhtml", "ics", "vcf", "srt", "vtt", "ass", "ssa", "lrc", "m3u", "pls",
        "rst", "adoc", "tex"
    )
    val archiveDocumentTypes = setOf("xlsx", "pptx", "odt", "ods", "odp", "epub", "ipynb")
    val supportedTypes = imageTypes + audioTypes + videoTypes + codeTypes + plainTextTypes +
        archiveDocumentTypes + setOf("pdf", "docx")

    val groups = listOf(
        FormatGroup(
            "Dokumen dan e-book",
            listOf("pdf", "docx", "odt", "xlsx", "pptx", "ods", "odp", "epub", "ipynb"),
            "DOCX/ODT, spreadsheet, presentasi, EPUB, dan notebook diekstrak sebagai teks. Tata letak kompleks, rumus yang belum dihitung, dan media tertanam tidak selalu dipertahankan."
        ),
        FormatGroup(
            "Teks, data, dan subtitle",
            listOf("txt", "md", "markdown", "mdx", "log", "csv", "tsv", "json", "jsonc", "jsonl", "ndjson", "html", "htm", "xhtml", "yaml", "yml", "toml", "ini", "properties", "conf", "cfg", "env", "xml", "svg", "rtf", "eml", "mhtml", "ics", "vcf", "srt", "vtt", "ass", "ssa", "lrc", "m3u", "pls", "rst", "adoc", "tex"),
            "CSV/TSV diubah menjadi tabel Markdown. Subtitle dan daftar putar dipertahankan sebagai teks terstruktur."
        ),
        FormatGroup(
            "Kode dan konfigurasi",
            codeTypes.sorted(),
            "Source code disimpan sebagai code block agar indentasi dan sintaks tetap terbaca."
        ),
        FormatGroup(
            "Gambar dan foto",
            imageTypes.sorted(),
            "Metadata dimensi dan OCR teks diproses di perangkat. Hasil OCR bergantung pada kualitas gambar dan dukungan decoder Android."
        ),
        FormatGroup(
            "Audio",
            audioTypes.sorted(),
            "Metadata audio seperti durasi dan tag dibaca bila decoder perangkat mendukungnya. Transkripsi ucapan/lirik belum tersedia."
        ),
        FormatGroup(
            "Video",
            videoTypes.sorted(),
            "Metadata video dan OCR dari maksimal tiga cuplikan yang dipilih dari durasi video. Ini bukan transkrip dialog."
        )
    )
}
