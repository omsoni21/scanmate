package com.example.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.ScanMateDatabase
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.PageDao
import com.example.data.ocr.OcrEngine
import com.example.data.ocr.OfflineMlKitOcrEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE documents ADD COLUMN isPasswordProtected INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE documents ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE folders ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanMateDatabase =
        Room.databaseBuilder(
            context,
            ScanMateDatabase::class.java,
            "scanmate_offline.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFolderDao(db: ScanMateDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideDocumentDao(db: ScanMateDatabase): DocumentDao = db.documentDao()

    @Provides
    fun providePageDao(db: ScanMateDatabase): PageDao = db.pageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: OfflineMlKitOcrEngine): OcrEngine
}
