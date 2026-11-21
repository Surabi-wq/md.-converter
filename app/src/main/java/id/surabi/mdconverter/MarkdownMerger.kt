package id.surabi.mdconverter

internal object MarkdownMerger {
    fun combine(items: List<ConvertedFile>): String {
        require(items.isNotEmpty()) { "Tidak ada hasil konversi yang dapat digabungkan." }
        return buildString {
            appendLine("# Dokumen Gabungan")
            appendLine()
            appendLine("> Dokumen ini dibuat dari ${items.size} file sumber. Setiap file dipisahkan dalam bagian tersendiri.")
            appendLine()
            appendLine("## Daftar Isi")
            items.forEachIndexed { index, item ->
                val heading = "${index + 1}. ${item.name}"
                val anchor = heading.lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), " ")
                    .trim()
                    .replace(Regex("[\\s-]+"), "-")
                appendLine("- [${heading.replace("[", "\\[").replace("]", "\\]")}](#${anchor})")
            }
            items.forEachIndexed { index, item ->
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ${index + 1}. ${item.name}")
                appendLine()
                appendLine("- **Format sumber:** ${item.format.uppercase()}")
                appendLine("- **Status:** Berhasil dikonversi")
                if (item.warnings.isNotEmpty()) {
                    appendLine()
                    appendLine("### Catatan konversi")
                    item.warnings.forEach { appendLine("- $it") }
                }
                val markdown = item.readMarkdown()
                val body = markdown.substringAfter("\n\n", markdown).trim()
                appendLine()
                appendLine(body.ifBlank { "_Tidak ada konten teks yang dapat ditampilkan._" })
                appendLine()
            }
        }.trimEnd() + "\n"
    }
}
