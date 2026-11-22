package com.example.domain.model

enum class OcrLanguage(
    val code: String,
    val displayName: String,
    val isBundled: Boolean,
    val statusNote: String
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        isBundled = true,
        statusNote = "Bundled offline Latin OCR engine ready"
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi (Devanagari)",
        isBundled = false,
        statusNote = "Hindi language data is not bundled in this offline build"
    )
}

sealed class OcrResult {
    data class Success(
        val text: String,
        val language: OcrLanguage,
        val blockCount: Int
    ) : OcrResult()

    data class LanguageUnavailable(
        val language: OcrLanguage,
        val message: String
    ) : OcrResult()

    data class NoTextFound(val language: OcrLanguage) : OcrResult()

    data class Error(val message: String) : OcrResult()
}
