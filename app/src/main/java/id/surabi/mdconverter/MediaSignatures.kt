package id.surabi.mdconverter

/** Conservative media-header checks used before trusting an extension or MIME hint. */
internal object MediaSignatures {
    fun isAdtsAac(bytes: ByteArray): Boolean {
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != 0xFF) return false
        val second = bytes[1].toInt() and 0xFF
        if ((second and 0xF0) != 0xF0 || (second and 0x06) != 0) return false
        val third = bytes[2].toInt() and 0xFF
        val sampleRateIndex = (third shr 2) and 0x0F
        return sampleRateIndex in 0..12
    }

    fun isMpegAudioFrame(bytes: ByteArray): Boolean {
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != 0xFF) return false
        val second = bytes[1].toInt() and 0xFF
        val third = bytes[2].toInt() and 0xFF
        if ((second and 0xE0) != 0xE0) return false
        val version = (second shr 3) and 0x03
        val layer = (second shr 1) and 0x03
        val bitrateIndex = (third shr 4) and 0x0F
        val sampleRateIndex = (third shr 2) and 0x03
        return version != 1 && layer != 0 && bitrateIndex in 1..14 && sampleRateIndex != 3
    }

    fun containsAscii(bytes: ByteArray, value: String): Boolean {
        if (value.isEmpty() || bytes.size < value.length) return false
        val needle = value.toByteArray(Charsets.US_ASCII)
        return (0..bytes.size - needle.size).any { offset ->
            needle.indices.all { index -> bytes[offset + index] == needle[index] }
        }
    }
}
