package id.surabi.mdconverter

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMergerTest {
    @Test
    fun combinesFilesInSelectionOrderAndBuildsTableOfContents() {
        val first = ConvertedFile(1L, "catatan.txt", "txt", "# Catatan\n\nIsi pertama", emptyList(), null)
        val second = ConvertedFile(2L, "diagram.png", "png", "# Diagram\n\nTeks hasil OCR", listOf("OCR parsial"), null)

        val result = MarkdownMerger.combine(listOf(first, second))

        assertTrue(result.contains("## Daftar Isi"))
        assertTrue(result.contains("[1. catatan.txt](#1-catatan-txt)"))
        assertTrue(result.contains("[2. diagram.png](#2-diagram-png)"))
        assertTrue(result.indexOf("## 1. catatan.txt") < result.indexOf("## 2. diagram.png"))
        assertTrue(result.contains("Isi pertama"))
        assertTrue(result.contains("- OCR parsial"))
    }

    @Test
    fun rejectsAnEmptySourceList() {
        assertThrows(IllegalArgumentException::class.java) {
            MarkdownMerger.combine(emptyList())
        }
    }
}
