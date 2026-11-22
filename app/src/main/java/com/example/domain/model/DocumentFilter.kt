package com.example.domain.model

enum class DocumentFilter(val label: String, val shortLabel: String) {
    ORIGINAL("Original", "Original"),
    AUTO_ENHANCE("Auto Enhance", "Auto"),
    DOCUMENT("Document", "Doc"),
    BLACK_AND_WHITE("Black & White", "B&W"),
    GRAYSCALE("Grayscale", "Gray"),
    COLOR("Vibrant Color", "Color"),
    HIGH_CONTRAST("High Contrast", "Contrast")
}

enum class ImageQuality(val label: String, val maxDimensionPx: Int, val jpegQuality: Int) {
    COMPACT("Compact", 1400, 72),
    BALANCED("Balanced", 2000, 86),
    CRISP("High Quality", 2800, 95)
}
