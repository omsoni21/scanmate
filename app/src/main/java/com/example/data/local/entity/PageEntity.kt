package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index("pageIndex")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val filterType: String,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val sharpness: Float = 0.25f,
    val rotationDegrees: Int = 0,
    val cropCornersJson: String = "",
    val imageQuality: String = "BALANCED",
    val ocrText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
