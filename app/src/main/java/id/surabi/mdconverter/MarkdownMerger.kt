package id.surabi.mdconverter

import java.io.StringWriter
import java.io.Writer

internal object MarkdownMerger {
    fun combine(items: List<ConvertedFile>): String =
        StringWriter().also { writeTo(items, it) }.toString()

    /** Writes cached source bodies incrementally instead of loading every result into one large string. */
    fun writeTo(items: List<ConvertedFile>, writer: Writer) {
        require(items.isNotEmpty()) { "Tidak ada hasil konversi yang dapat digabungkan." }

        writer.appendLine("# Dokumen Gabungan")
        writer.appendLine()
        writer.appendLine("> Dokumen ini dibuat dari " + items.size + " file sumber. Setiap file dipisahkan dalam bagian tersendiri.")
        writer.appendLine()
        writer.appendLine("## Daftar Isi")
        items.forEachIndexed { index, item ->
            val heading = (index + 1).toString() + ". " + item.name
            val anchor = heading.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), " ")
                .trim()
                .replace(Regex("[\\s-]+"), "-")
            writer.appendLine("- [" + heading.replace("[", "\\[").replace("]", "\\]") + "](#" + anchor + ")")
        }

        items.forEachIndexed { index, item ->
            writer.appendLine()
            writer.appendLine("---")
            writer.appendLine()
            writer.appendLine("## " + (index + 1) + ". " + item.name)
            writer.appendLine()
            writer.appendLine("- **Format sumber:** " + item.format.uppercase())
            writer.appendLine("- **Status:** Berhasil dikonversi")
            if (item.warnings.isNotEmpty()) {
                writer.appendLine()
                writer.appendLine("### Catatan konversi")
                item.warnings.forEach { writer.appendLine("- " + it) }
            }
            writer.appendLine()
            item.writeBodyTo(writer)
            writer.appendLine()
        }
    }

    fun estimatedSourceBytes(items: List<ConvertedFile>): Long =
        items.sumOf { item ->
            item.cacheFilePath?.let { java.io.File(it).length() }
                ?: item.markdown.toByteArray(Charsets.UTF_8).size.toLong()
        }
}
