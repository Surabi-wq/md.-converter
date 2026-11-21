package id.surabi.mdconverter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSignaturesTest {
    @Test
    fun identifiesAdtsAacWithoutMisclassifyingItAsMpegAudio() {
        val adtsHeader = byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(), 0x50.toByte(), 0x80.toByte(),
            0x00, 0x1F, 0xFC.toByte()
        )

        assertTrue(MediaSignatures.isAdtsAac(adtsHeader))
        assertFalse(MediaSignatures.isMpegAudioFrame(adtsHeader))
    }

    @Test
    fun identifiesValidMpegAudioFrameHeader() {
        val mp3Header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64)

        assertTrue(MediaSignatures.isMpegAudioFrame(mp3Header))
        assertFalse(MediaSignatures.isAdtsAac(mp3Header))
    }

    @Test
    fun rejectsInvalidMpegLayerAndReservedSampleRate() {
        val invalidLayer = byteArrayOf(0xFF.toByte(), 0xF9.toByte(), 0x90.toByte())
        val reservedSampleRate = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x93.toByte())

        assertFalse(MediaSignatures.isMpegAudioFrame(invalidLayer))
        assertFalse(MediaSignatures.isMpegAudioFrame(reservedSampleRate))
    }

    @Test
    fun findsOpusIdentificationPacketInOggHeader() {
        val oggOpus = "OggS".toByteArray() + ByteArray(24) + "OpusHead".toByteArray()

        assertTrue(MediaSignatures.containsAscii(oggOpus, "OpusHead"))
        assertFalse(MediaSignatures.containsAscii("OggS".toByteArray(), "OpusHead"))
    }
}
