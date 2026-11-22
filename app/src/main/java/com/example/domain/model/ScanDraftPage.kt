package com.example.domain.model

import java.util.UUID

data class ScanDraftPage(
    val id: String = UUID.randomUUID().toString(),
    val persistedPageId: Long? = null,
    val originalImagePath: String,
    val processedImagePath: String,
    val filter: DocumentFilter = DocumentFilter.AUTO_ENHANCE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val sharpness: Float = 0.25f,
    val rotationDegrees: Int = 0,
    val cropCorners: QuadPoints = QuadPoints.DEFAULT_INSET,
    val imageQuality: ImageQuality = ImageQuality.BALANCED,
    val ocrText: String = "",
    val edgeAutoDetected: Boolean = false
)
