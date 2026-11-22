package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index("folderId"), Index("updatedAt")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val folderId: String,
    val fileType: String, // "PDF" or "SCAN"
    val pdfPath: String? = null,
    val thumbnailPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val ocrText: String = "",
    val isPasswordProtected: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
