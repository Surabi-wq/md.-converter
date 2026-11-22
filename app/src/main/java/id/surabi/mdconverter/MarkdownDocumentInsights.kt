package id.surabi.mdconverter

internal data class MarkdownDocumentInsights(
    val wordCount: Int,
    val sectionCount: Int,
    val tableCount: Int,
    val codeBlockCount: Int,
    val readingMinutes: Int
)

internal object MarkdownDocumentInsightsCalculator {
    fun calculate(blocks: List<MarkdownBlock>): MarkdownDocumentInsights {
        val words = blocks.flatMap { block ->
            when (block) {
                is MarkdownBlock.Heading -> listOf(block.text)
                is MarkdownBlock.Paragraph -> listOf(block.text)
                is MarkdownBlock.BulletList -> block.items
                is MarkdownBlock.NumberedList -> block.items
                is MarkdownBlock.Code -> listOf(block.content)
                is MarkdownBlock.Quote -> listOf(block.text)
                is MarkdownBlock.Table -> block.rows.flatten()
                MarkdownBlock.Rule -> emptyList()
            }
        }.sumOf { value ->
            Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*").findAll(value).count()
        }
        val sections = blocks.count { it is MarkdownBlock.Heading }
        val tables = blocks.count { it is MarkdownBlock.Table }
        val code = blocks.count { it is MarkdownBlock.Code }
        return MarkdownDocumentInsights(
            wordCount = words,
            sectionCount = sections,
            tableCount = tables,
            codeBlockCount = code,
            readingMinutes = ((words + 199) / 200).coerceAtLeast(1)
        )
    }
}
