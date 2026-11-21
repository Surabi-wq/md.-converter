package id.surabi.mdconverter

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.io.File

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
    fun streamingWriterMatchesCombinedStringOutput() {
        val first = ConvertedFile(1L, "catatan.txt", "txt", "# Catatan\n\nIsi pertama", emptyList(), null)
        val second = ConvertedFile(2L, "data.csv", "csv", "# Data\n\n| A | B |\n| --- | --- |\n| 1 | 2 |", emptyList(), null)
        val writer = StringWriter()

        MarkdownMerger.writeTo(listOf(first, second), writer)

        assertEquals(MarkdownMerger.combine(listOf(first, second)), writer.toString())
    }

    @Test
    fun readsCachedBodyWithoutKeepingTheFullDocumentInMemory() {
        val source = File.createTempFile("marka-test-", ".md")
        try {
            source.writeText("# Judul sumber\n\nParagraf pertama\n\nParagraf kedua\n", Charsets.UTF_8)
            val file = ConvertedFile(3L, "sumber.md", "md", "", emptyList(), null, source.absolutePath)
            val writer = StringWriter()

            file.writeBodyTo(writer)

            assertEquals("Paragraf pertama\n\nParagraf kedua\n", writer.toString())
        } finally {
            source.delete()
        }
    }

    @Test
    fun rejectsAnEmptySourceList() {
        assertThrows(IllegalArgumentException::class.java) {
            MarkdownMerger.combine(emptyList())
        }
    }
}
