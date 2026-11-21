package id.surabi.mdconverter

internal object MarkdownFileNames {
    fun fromSource(sourceName: String): String {
        val fileName = sourceName.substringAfterLast('/').substringAfterLast('\\').trim()
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        return sanitize(base).ifBlank { "hasil" } + ".md"
    }

    fun merged(sourceNames: List<String>): String {
        require(sourceNames.isNotEmpty()) { "Tidak ada file sumber untuk digabungkan." }
        if (sourceNames.size == 1) return fromSource(sourceNames.first())
        val firstBase = fromSource(sourceNames.first()).removeSuffix(".md")
        return "gabungan-" + firstBase + ".md"
    }

    private fun sanitize(value: String): String = value
        .map { ch ->
            if (ch.isISOControl() || ch in charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')) '_' else ch
        }
        .joinToString("")
        .trim()
        .trimEnd('.', ' ')
}
