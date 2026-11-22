package com.example.data.ocr

import android.graphics.Bitmap
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface OcrEngine {
    fun getSupportedLanguages(): List<OcrLanguage>
    suspend fun recognizeText(bitmap: Bitmap, language: OcrLanguage = OcrLanguage.ENGLISH): OcrResult
}

/**
 * 100% offline on-device OCR engine backed by bundled ML Kit Latin text recognition.
 * Designed around OcrLanguage so additional offline language models (e.g., Hindi/Devanagari)
 * can be plugged in cleanly while clearly indicating unavailable language packs.
 */
@Singleton
class OfflineMlKitOcrEngine @Inject constructor() : OcrEngine {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun getSupportedLanguages(): List<OcrLanguage> = OcrLanguage.entries

    override suspend fun recognizeText(bitmap: Bitmap, language: OcrLanguage): OcrResult {
        if (!language.isBundled) {
            return OcrResult.LanguageUnavailable(
                language = language,
                message = "${language.displayName} OCR data is not bundled in this offline package yet. Switch to English or add the ${language.code} language pack."
            )
        }

        return withContext(Dispatchers.Default) {
            runCatching {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                suspendCancellableCoroutine { continuation ->
                    latinRecognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val cleanText = visionText.text.trim()
                            if (cleanText.isEmpty()) {
                                continuation.resume(OcrResult.NoTextFound(language))
                            } else {
                                continuation.resume(
                                    OcrResult.Success(
                                        text = cleanText,
                                        language = language,
                                        blockCount = visionText.textBlocks.size
                                    )
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            continuation.resume(
                                OcrResult.Error(e.localizedMessage ?: "Failed to extract text offline.")
                            )
                        }
                }
            }.getOrElse { e ->
                OcrResult.Error(e.localizedMessage ?: "Unexpected offline OCR error.")
            }
        }
    }
}
