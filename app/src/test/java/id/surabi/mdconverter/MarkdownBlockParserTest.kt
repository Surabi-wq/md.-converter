package id.surabi.mdconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockParserTest {
    @Test
    fun parsesHeadingsParagraphsListsTablesQuotesAndFencedCode() {
        val fence = 96.toChar().toString().repeat(3)
        val source = listOf(
            "# Judul",
            "",
            "Paragraf pertama.",
            "",
            "- Satu",
            "- Dua",
            "",
            "1. Pertama",
            "2. Kedua",
            "",
            "| Nama | Nilai |",
            "| --- | --- |",
            "| Ana | 90 |",
            "",
            "> Catatan penting",
            "",
            fence + "kotlin",
            "val answer = 42",
            fence
        ).joinToString("\n")

        val blocks = MarkdownBlockParser.parse(source)

        assertTrue(blocks.any { it is MarkdownBlock.Heading && it.level == 1 && it.text == "Judul" })
        assertTrue(blocks.any { it is MarkdownBlock.Paragraph && it.text == "Paragraf pertama." })
        assertTrue(blocks.any { it is MarkdownBlock.BulletList && it.items == listOf("Satu", "Dua") })
        assertTrue(blocks.any { it is MarkdownBlock.NumberedList && it.items == listOf("Pertama", "Kedua") })
        assertTrue(blocks.any { it is MarkdownBlock.Table && it.rows.size == 2 && it.rows[1] == listOf("Ana", "90") })
        assertTrue(blocks.any { it is MarkdownBlock.Quote && it.text == "Catatan penting" })
        assertTrue(blocks.any { it is MarkdownBlock.Code && it.language == "kotlin" && it.content == "val answer = 42" })
    }

    @Test
    fun preservesEscapedPipesInsideTableCells() {
        val blocks = MarkdownBlockParser.parse(
            "| Kolom | Catatan |\n| --- | --- |\n| A | Nilai A \\| B |"
        )
        val table = blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(listOf("A", "Nilai A | B"), table.rows[1])
    }
}
