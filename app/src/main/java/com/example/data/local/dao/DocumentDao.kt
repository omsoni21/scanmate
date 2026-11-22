package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun observeDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocument(documentId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET title = :newTitle, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun renameDocument(documentId: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET folderId = :targetFolderId, updatedAt = :updatedAt WHERE id IN (:documentIds)")
    suspend fun moveDocumentsToFolder(
        documentIds: List<Long>,
        targetFolderId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE documents SET folderId = :fallbackFolderId WHERE folderId = :deletedFolderId")
    suspend fun reassignFolderDocuments(deletedFolderId: String, fallbackFolderId: String = "other")

    @Query("UPDATE documents SET ocrText = :ocrText, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateDocumentOcrText(
        documentId: Long,
        ocrText: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE documents SET isPasswordProtected = :isProtected, fileSizeBytes = :fileSizeBytes, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updatePasswordProtection(
        documentId: Long,
        isProtected: Boolean,
        fileSizeBytes: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :documentId")
    suspend fun setDocumentFavorite(documentId: Long, isFavorite: Boolean)

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id IN (:documentIds)")
    suspend fun setDocumentsFavorite(documentIds: List<Long>, isFavorite: Boolean)

    @Query("DELETE FROM documents WHERE id IN (:documentIds)")
    suspend fun deleteDocumentsByIds(documentIds: List<Long>)
}
