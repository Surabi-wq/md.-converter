package id.surabi.mdconverter

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownDocumentInsightsTest {
    @Test
    fun countsWordsSectionsTablesAndReadingTime() {
        val source = listOf(
            "# Laporan Keuangan",
            "",
            "Ringkasan hasil bulan ini.",
            "",
            "## Rincian",
            "",
            "| Kategori | Nilai |",
            "| --- | --- |",
            "| Makan | 120 |"
        ).joinToString("\n")
        val result = MarkdownDocumentInsightsCalculator.calculate(MarkdownBlockParser.parse(source))
        assertEquals(10, result.wordCount)
        assertEquals(2, result.sectionCount)
        assertEquals(1, result.tableCount)
        assertEquals(0, result.codeBlockCount)
        assertEquals(1, result.readingMinutes)
    }

    @Test
    fun emptyDocumentStillHasMinimumReadingTimeOfOneMinute() {
        val result = MarkdownDocumentInsightsCalculator.calculate(emptyList())
        assertEquals(0, result.wordCount)
        assertEquals(1, result.readingMinutes)
    }
}
