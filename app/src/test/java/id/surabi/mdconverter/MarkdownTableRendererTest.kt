package id.surabi.mdconverter

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableRendererTest {
    @Test
    fun rendersHeaderRowsAndPadsShortRows() {
        val result = MarkdownTableRenderer.render(
            listOf(
                listOf("Nama", "Nilai", "Catatan"),
                listOf("Ayu", "95"),
                listOf("Bima", "88", "Lulus")
            )
        )

        assertEquals(
            "| Nama | Nilai | Catatan |\n" +
                "| --- | --- | --- |\n" +
                "| Ayu | 95 |  |\n" +
                "| Bima | 88 | Lulus |",
            result
        )
    }

    @Test
    fun escapesPipesAndConvertsCellLineBreaks() {
        val result = MarkdownTableRenderer.render(
            listOf(listOf("Kolom", "Keterangan"), listOf("A", "Satu | Dua\nTiga"))
        )

        assertEquals(
            "| Kolom | Keterangan |\n" +
                "| --- | --- |\n" +
                "| A | Satu \\| Dua<br>Tiga |",
            result
        )
    }
}
