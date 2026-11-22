package com.example.domain.model

enum class PaperSize(
    val label: String,
    val widthPt: Int,
    val heightPt: Int
) {
    A4("A4 (210 × 297 mm)", 595, 842),
    A3("A3 (297 × 420 mm)", 842, 1191),
    LETTER("Letter (8.5 × 11 in)", 612, 792),
    ORIGINAL_RATIO("Original Image Ratio", 0, 0)
}

enum class PdfOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PdfQuality(val label: String, val maxPageSidePx: Int, val jpegQuality: Int) {
    COMPACT("Compact (Small File)", 1240, 70),
    STANDARD("Standard (Balanced)", 1800, 85),
    HIGH("High (Print Crisp)", 2480, 94)
}

data class PdfGenerationConfig(
    val documentName: String,
    val folderId: String,
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val quality: PdfQuality = PdfQuality.STANDARD,
    val includeMargins: Boolean = true,
    val password: String? = null
)
