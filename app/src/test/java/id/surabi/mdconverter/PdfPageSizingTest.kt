package id.surabi.mdconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageSizingTest {
    @Test
    fun keepsNormalPageAtConfiguredMaximumScale() {
        assertEquals(1.35f, PdfPageSizing.renderScale(595f, 842f), 0.0001f)
    }

    @Test
    fun reducesScaleForOversizedPage() {
        val scale = PdfPageSizing.renderScale(20_000f, 10_000f)
        assertTrue(20_000f * scale <= 2048f)
        assertTrue(10_000f * scale <= 2048f)
    }

    @Test
    fun rejectsInvalidPageDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageSizing.renderScale(0f, 842f)
        }
    }
}
