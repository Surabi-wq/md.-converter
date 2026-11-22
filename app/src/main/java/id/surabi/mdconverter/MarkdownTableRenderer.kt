package id.surabi.mdconverter

/**
 * Produces consistent Markdown tables for CSV, HTML, DOCX, XLSX and ODS conversions.
 * Cell text is escaped without silently changing the source values.
 */
internal object MarkdownTableRenderer {
    fun render(source: List<List<String>>): String {
        require(source.isNotEmpty()) { "Tabel kosong." }
        val width = source.maxOfOrNull { it.size } ?: 0
        require(width > 0) { "Tabel tidak memiliki kolom." }
        val rows = source.map { row ->
            row.take(width) + List((width - row.size).coerceAtLeast(0)) { "" }
        }

        fun renderRow(row: List<String>): String = "| " + row.joinToString(" | ") {
            it.trim()
                .replace("|", "\\|")
                .replace("\r", "")
                .replace("\n", "<br>")
        } + " |"

        return buildString {
            appendLine(renderRow(rows.first()))
            appendLine("| " + List(width) { "---" }.joinToString(" | ") + " |")
            rows.drop(1).forEach { appendLine(renderRow(it)) }
        }.trimEnd()
    }
}
