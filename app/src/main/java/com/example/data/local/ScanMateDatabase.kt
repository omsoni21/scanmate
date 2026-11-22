package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.PageDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.PageEntity

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, PageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ScanMateDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
}
