package com.example.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

@Serializable
data class QuadPoints(
    val topLeft: NormalizedPoint = NormalizedPoint(0.06f, 0.06f),
    val topRight: NormalizedPoint = NormalizedPoint(0.94f, 0.06f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.94f, 0.94f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.06f, 0.94f)
) {
    fun toList(): List<NormalizedPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun toJson(): String = Json.encodeToString(this)

    companion object {
        val FULL_FRAME = QuadPoints(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(1f, 1f),
            bottomLeft = NormalizedPoint(0f, 1f)
        )

        val DEFAULT_INSET = QuadPoints()

        fun fromJson(raw: String?): QuadPoints {
            if (raw.isNullOrBlank()) return DEFAULT_INSET
            return runCatching { Json.decodeFromString<QuadPoints>(raw) }.getOrDefault(DEFAULT_INSET)
        }
    }
}
