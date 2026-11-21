package id.surabi.mdconverter

/** Calculates a PDF render scale that prevents unusually large page dimensions from creating huge bitmaps. */
internal object PdfPageSizing {
    fun renderScale(
        pageWidthPoints: Float,
        pageHeightPoints: Float,
        maximumSidePixels: Int = 2048,
        maximumScale: Float = 1.35f
    ): Float {
        require(pageWidthPoints.isFinite() && pageHeightPoints.isFinite()) { "Ukuran halaman PDF tidak valid." }
        require(pageWidthPoints > 0f && pageHeightPoints > 0f) { "Ukuran halaman PDF harus lebih besar dari nol." }
        require(maximumSidePixels > 0 && maximumScale > 0f && maximumScale.isFinite()) {
            "Batas render PDF tidak valid."
        }
        val largestSide = maxOf(pageWidthPoints, pageHeightPoints)
        return minOf(maximumScale, maximumSidePixels / largestSide)
    }
}
