package id.surabi.mdconverter

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFileNamesTest {
    @Test
    fun replacesOnlyTheSourceExtensionWithMarkdown() {
        assertEquals("laporan-akhir.md", MarkdownFileNames.fromSource("laporan-akhir.pdf"))
        assertEquals("foto liburan.md", MarkdownFileNames.fromSource("foto liburan.jpeg"))
    }

    @Test
    fun supportsNamesWithoutExtensionsAndHiddenFiles() {
        assertEquals("catatan.md", MarkdownFileNames.fromSource("catatan"))
        assertEquals(".env.md", MarkdownFileNames.fromSource(".env"))
    }

    @Test
    fun namesMergedOutputFromTheFirstSource() {
        assertEquals("laporan.md", MarkdownFileNames.merged(listOf("laporan.pdf")))
        assertEquals("gabungan-laporan.md", MarkdownFileNames.merged(listOf("laporan.pdf", "foto.png")))
    }
}
