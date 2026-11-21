package id.surabi.mdconverter

import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFormatsTest {
    @Test
    fun includesTheNewArchiveDocumentFormats() {
        assertTrue(SupportedFormats.archiveDocumentTypes.containsAll(setOf("xlsx", "pptx", "odt", "ods", "odp", "epub", "ipynb")))
    }

    @Test
    fun mediaRegistriesIncludeAdditionalContainers() {
        assertTrue(SupportedFormats.imageTypes.contains("avif"))
        assertTrue(SupportedFormats.audioTypes.containsAll(setOf("aiff", "caf", "3ga", "aac", "opus")))
        assertTrue(SupportedFormats.videoTypes.containsAll(setOf("3g2", "m2ts", "flv", "wmv")))
    }
}
