package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val iconKey: String,
    val isPredefined: Boolean = false,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 100,
    val createdAt: Long = System.currentTimeMillis()
)
