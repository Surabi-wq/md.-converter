package id.surabi.mdconverter

internal sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
    data class Code(val language: String, val content: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Table(val rows: List<List<String>>) : MarkdownBlock()
    data object Rule : MarkdownBlock()
}

/** Small, dependency-free block parser for the in-app reading preview. Exported Markdown stays unchanged. */
internal object MarkdownBlockParser {
    private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
    private val bulletPattern = Regex("^\\s*[-*+]\\s+(.+)$")
    private val numberedPattern = Regex("^\\s*\\d+[.)]\\s+(.+)$")
    private val tableDividerCell = Regex("^:?-{3,}:?$")
    private val backtickFence = 96.toChar().toString().repeat(3)

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }

            val trimmed = line.trim()
            val heading = headingPattern.matchEntire(trimmed)
            if (heading != null) {
                blocks.add(MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
                index++
                continue
            }

            if (isCodeFence(trimmed)) {
                val fenceChar = trimmed.first()
                val fence = trimmed.takeWhile { it == fenceChar }
                val language = trimmed.drop(fence.length).trim()
                index++
                val code = mutableListOf<String>()
                while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
                    code.add(lines[index])
                    index++
                }
                if (index < lines.size) index++
                blocks.add(MarkdownBlock.Code(language, code.joinToString("\n")))
                continue
            }

            if (isHorizontalRule(trimmed)) {
                blocks.add(MarkdownBlock.Rule)
                index++
                continue
            }

            if (trimmed.startsWith(">")) {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote.add(lines[index].trimStart().removePrefix(">").trimStart())
                    index++
                }
                blocks.add(MarkdownBlock.Quote(quote.joinToString(" ")))
                continue
            }

            if (index + 1 < lines.size && looksLikeTableDivider(lines[index + 1])) {
                val rows = mutableListOf<List<String>>()
                rows.add(parseTableRow(line))
                index += 2
                while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) {
                    rows.add(parseTableRow(lines[index]))
                    index++
                }
                blocks.add(MarkdownBlock.Table(rows))
                continue
            }

            if (bulletPattern.matches(line)) {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val match = bulletPattern.matchEntire(lines[index]) ?: break
                    items.add(match.groupValues[1].trim())
                    index++
                }
                blocks.add(MarkdownBlock.BulletList(items))
                continue
            }

            if (numberedPattern.matches(line)) {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val match = numberedPattern.matchEntire(lines[index]) ?: break
                    items.add(match.groupValues[1].trim())
                    index++
                }
                blocks.add(MarkdownBlock.NumberedList(items))
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) {
                if (paragraph.isNotEmpty() && startsBlock(lines, index)) break
                paragraph.add(lines[index].trim())
                index++
            }
            if (paragraph.isNotEmpty()) blocks.add(MarkdownBlock.Paragraph(paragraph.joinToString(" ")))
        }

        return blocks
    }

    private fun startsBlock(lines: List<String>, index: Int): Boolean {
        val trimmed = lines[index].trim()
        return headingPattern.matches(trimmed) ||
            isCodeFence(trimmed) ||
            trimmed.startsWith(">") ||
            isHorizontalRule(trimmed) ||
            bulletPattern.matches(lines[index]) ||
            numberedPattern.matches(lines[index]) ||
            (index + 1 < lines.size && looksLikeTableDivider(lines[index + 1]))
    }

    private fun isCodeFence(value: String): Boolean =
        value.startsWith(backtickFence) || value.startsWith("~~~")

    private fun looksLikeTableDivider(line: String): Boolean {
        if (!line.contains('|')) return false
        val cells = parseTableRow(line)
        return cells.isNotEmpty() && cells.all { tableDividerCell.matches(it.trim()) }
    }

    private fun parseTableRow(line: String): List<String> {
        val source = line.trim().removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var index = 0
        while (index < source.length) {
            val current = source[index]
            if (current == '\\' && index + 1 < source.length && source[index + 1] == '|') {
                cell.append('|')
                index += 2
            } else if (current == '|') {
                cells.add(cell.toString().trim())
                cell.setLength(0)
                index++
            } else {
                cell.append(current)
                index++
            }
        }
        cells.add(cell.toString().trim())
        return cells
    }

    private fun isHorizontalRule(value: String): Boolean =
        value.replace(" ", "").replace("\t", "").matches(Regex("(---+|___+|\\*\\*\\*+)"))
}
