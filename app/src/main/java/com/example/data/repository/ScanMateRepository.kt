package com.example.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.ScanMateDatabase
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.PageDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.PageEntity
import com.example.data.ocr.OcrEngine
import com.example.data.pdf.PdfGenerator
import com.example.data.pdf.PdfInspectOutcome
import com.example.data.pdf.PdfRendererHelper
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.DocumentFilter
import com.example.domain.model.ImageQuality
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.model.QuadPoints
import com.example.domain.model.ScanDraftPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanMateRepository @Inject constructor(
    private val database: ScanMateDatabase,
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val fileStorage: LocalFileStorage,
    private val pdfGenerator: PdfGenerator,
    private val pdfRendererHelper: PdfRendererHelper,
    private val ocrEngine: OcrEngine
) {
    val folders: Flow<List<FolderEntity>> = folderDao.observeAllFolders()
    val documents: Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()
    val recentPages: Flow<List<PageEntity>> = pageDao.observeRecentPages(12)

    fun observeFolder(folderId: String): Flow<FolderEntity?> = folderDao.observeFolder(folderId)
    fun observeDocumentsInFolder(folderId: String): Flow<List<DocumentEntity>> =
        documentDao.observeDocumentsInFolder(folderId)
    fun observeDocument(documentId: Long): Flow<DocumentEntity?> = documentDao.observeDocument(documentId)
    fun observePages(documentId: Long): Flow<List<PageEntity>> = pageDao.observePagesForDocument(documentId)

    suspend fun ensureDefaultFoldersSeeded() {
        if (folderDao.getFolderCount() == 0) {
            folderDao.insertFoldersIgnore(PREDEFINED_FOLDERS)
        }
    }

    suspend fun createFolder(name: String, colorHex: Long, iconKey: String): FolderEntity {
        val folder = FolderEntity(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            colorHex = colorHex,
            iconKey = iconKey,
            isPredefined = false,
            sortOrder = 200
        )
        folderDao.insertFolder(folder)
        return folder
    }

    suspend fun renameFolder(folder: FolderEntity, newName: String) {
        folderDao.updateFolder(folder.copy(name = newName.trim()))
    }

    suspend fun updateFolderAppearance(folder: FolderEntity, colorHex: Long, iconKey: String) {
        folderDao.updateFolder(folder.copy(colorHex = colorHex, iconKey = iconKey))
    }

    suspend fun deleteCustomFolder(folderId: String) {
        database.withTransaction {
            documentDao.reassignFolderDocuments(deletedFolderId = folderId, fallbackFolderId = "other")
            folderDao.deleteCustomFolder(folderId)
        }
    }

    suspend fun saveDocumentFromDrafts(
        existingDocumentId: Long?,
        drafts: List<ScanDraftPage>,
        config: PdfGenerationConfig,
        exportAsPdf: Boolean,
        onProgress: (Int, Int) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        ensureDefaultFoldersSeeded()
        val now = System.currentTimeMillis()
        val oldDoc = existingDocumentId?.let { documentDao.getDocumentById(it) }

        val firstPageBitmap = drafts.firstOrNull()?.let { fileStorage.loadBitmap(it.processedImagePath, 720) }
        val thumbFile = firstPageBitmap?.let { bmp ->
            val f = fileStorage.saveThumbnail(bmp)
            bmp.recycle()
            f
        }

        var pdfPath: String? = null
        var totalSizeBytes = 0L
        var isPasswordProtected = false

        if (exportAsPdf && drafts.isNotEmpty()) {
            val result = pdfGenerator.generatePdf(
                imagePaths = drafts.map { it.processedImagePath },
                config = config,
                onProgress = onProgress
            )
            pdfPath = result.pdfFile.absolutePath
            totalSizeBytes = result.fileSizeBytes
            isPasswordProtected = result.isPasswordProtected
            if (oldDoc?.pdfPath != null && oldDoc.pdfPath != pdfPath) {
                fileStorage.deleteFileQuietly(oldDoc.pdfPath)
            }
        } else {
            totalSizeBytes = drafts.sumOf { fileStorage.getFileSize(it.processedImagePath) }
        }

        val combinedOcr = drafts
            .mapNotNull { it.ocrText.trim().takeIf { t -> t.isNotEmpty() } }
            .joinToString("\n\n")

        val docId = database.withTransaction {
            val entity = DocumentEntity(
                id = existingDocumentId ?: 0L,
                title = config.documentName.trim().ifBlank { "Scan ${now}" },
                folderId = config.folderId,
                fileType = if (exportAsPdf) "PDF" else "SCAN",
                pdfPath = pdfPath,
                thumbnailPath = thumbFile?.absolutePath ?: oldDoc?.thumbnailPath,
                fileSizeBytes = totalSizeBytes,
                pageCount = drafts.size,
                ocrText = combinedOcr,
                isPasswordProtected = isPasswordProtected,
                isFavorite = oldDoc?.isFavorite ?: false,
                createdAt = oldDoc?.createdAt ?: now,
                updatedAt = now
            )
            val savedId = if (existingDocumentId != null && existingDocumentId > 0L) {
                documentDao.updateDocument(entity)
                pageDao.deletePagesForDocument(existingDocumentId)
                existingDocumentId
            } else {
                documentDao.insertDocument(entity)
            }

            val pageEntities = drafts.mapIndexed { idx, draft ->
                PageEntity(
                    documentId = savedId,
                    pageIndex = idx,
                    originalImagePath = draft.originalImagePath,
                    processedImagePath = draft.processedImagePath,
                    filterType = draft.filter.name,
                    brightness = draft.brightness,
                    contrast = draft.contrast,
                    sharpness = draft.sharpness,
                    rotationDegrees = draft.rotationDegrees,
                    cropCornersJson = draft.cropCorners.toJson(),
                    imageQuality = draft.imageQuality.name,
                    ocrText = draft.ocrText
                )
            }
            pageDao.insertPages(pageEntities)
            savedId
        }
        docId
    }

    suspend fun importExternalPdf(uri: Uri, title: String, folderId: String): Long? = withContext(Dispatchers.IO) {
        ensureDefaultFoldersSeeded()
        val copied = fileStorage.importPdfFromUri(uri, title) ?: return@withContext null
        val outcome = pdfRendererHelper.inspectPdfOutcome(copied.absolutePath)
        val isEncrypted = when (outcome) {
            is PdfInspectOutcome.Ready -> outcome.info.isEncrypted
            PdfInspectOutcome.PasswordRequired, PdfInspectOutcome.WrongPassword -> true
            PdfInspectOutcome.Error -> false
        }
        val pageCount = when (outcome) {
            is PdfInspectOutcome.Ready -> outcome.info.pageCount.coerceAtLeast(1)
            else -> 1
        }
        val coverBitmap = if (!isEncrypted) {
            pdfRendererHelper.renderPage(copied.absolutePath, 0, 600)
        } else null
        val thumbFile = coverBitmap?.let { bmp ->
            val f = fileStorage.saveThumbnail(bmp)
            bmp.recycle()
            f
        }
        val now = System.currentTimeMillis()
        documentDao.insertDocument(
            DocumentEntity(
                title = title.removeSuffix(".pdf").ifBlank { "Imported PDF" },
                folderId = folderId,
                fileType = "PDF",
                pdfPath = copied.absolutePath,
                thumbnailPath = thumbFile?.absolutePath,
                fileSizeBytes = copied.length(),
                pageCount = pageCount,
                ocrText = "",
                isPasswordProtected = isEncrypted,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun protectPdfDocument(documentId: Long, password: String): Boolean = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId) ?: return@withContext false
        val pdfPath = doc.pdfPath ?: return@withContext false
        val updatedSize = pdfGenerator.encryptExistingPdfInPlace(pdfPath, password) ?: return@withContext false
        documentDao.updatePasswordProtection(
            documentId = documentId,
            isProtected = true,
            fileSizeBytes = updatedSize
        )
        true
    }

    suspend fun loadDraftsForDocument(documentId: Long): List<ScanDraftPage> {
        val pages = pageDao.getPagesForDocument(documentId)
        return pages.map { p ->
            ScanDraftPage(
                persistedPageId = p.id,
                originalImagePath = p.originalImagePath,
                processedImagePath = p.processedImagePath,
                filter = runCatching { DocumentFilter.valueOf(p.filterType) }.getOrDefault(DocumentFilter.AUTO_ENHANCE),
                brightness = p.brightness,
                contrast = p.contrast,
                sharpness = p.sharpness,
                rotationDegrees = p.rotationDegrees,
                cropCorners = QuadPoints.fromJson(p.cropCornersJson),
                imageQuality = runCatching { ImageQuality.valueOf(p.imageQuality) }.getOrDefault(ImageQuality.BALANCED),
                ocrText = p.ocrText
            )
        }
    }

    suspend fun loadCombinedDraftsForDocuments(documentIds: List<Long>): List<ScanDraftPage> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ScanDraftPage>()
        for (docId in documentIds) {
            val pages = pageDao.getPagesForDocument(docId)
            if (pages.isNotEmpty()) {
                for (p in pages) {
                    val copiedOrig = fileStorage.copyFile(p.originalImagePath, fileStorage.createOriginalImageFile())
                    val copiedProc = fileStorage.copyFile(p.processedImagePath, fileStorage.createProcessedImageFile())
                    if (copiedOrig != null && copiedProc != null) {
                        result.add(
                            ScanDraftPage(
                                originalImagePath = copiedOrig.absolutePath,
                                processedImagePath = copiedProc.absolutePath,
                                filter = runCatching { DocumentFilter.valueOf(p.filterType) }.getOrDefault(DocumentFilter.AUTO_ENHANCE),
                                brightness = p.brightness,
                                contrast = p.contrast,
                                sharpness = p.sharpness,
                                rotationDegrees = p.rotationDegrees,
                                cropCorners = QuadPoints.fromJson(p.cropCornersJson),
                                imageQuality = runCatching { ImageQuality.valueOf(p.imageQuality) }.getOrDefault(ImageQuality.BALANCED),
                                ocrText = p.ocrText
                            )
                        )
                    }
                }
            } else {
                val doc = documentDao.getDocumentById(docId)
                val pdfPath = doc?.pdfPath
                if (!pdfPath.isNullOrBlank()) {
                    val info = pdfRendererHelper.inspectPdf(pdfPath)
                    val count = info?.pageCount ?: 0
                    for (i in 0 until count) {
                        val bmp = pdfRendererHelper.renderPage(pdfPath, i, 1800) ?: continue
                        val origFile = fileStorage.createOriginalImageFile()
                        val procFile = fileStorage.createProcessedImageFile()
                        fileStorage.saveBitmapJpeg(bmp, origFile, 92)
                        fileStorage.saveBitmapJpeg(bmp, procFile, 92)
                        bmp.recycle()
                        result.add(
                            ScanDraftPage(
                                originalImagePath = origFile.absolutePath,
                                processedImagePath = procFile.absolutePath,
                                filter = DocumentFilter.ORIGINAL,
                                cropCorners = QuadPoints.FULL_FRAME
                            )
                        )
                    }
                }
            }
        }
        result
    }

    suspend fun renameDocument(documentId: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) {
            documentDao.renameDocument(documentId, clean)
        }
    }

    suspend fun bulkRenameDocuments(renames: List<Pair<Long, String>>) {
        if (renames.isEmpty()) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            renames.forEach { (id, rawTitle) ->
                val clean = rawTitle.trim()
                if (clean.isNotEmpty()) {
                    documentDao.renameDocument(id, clean, now)
                }
            }
        }
    }

    suspend fun moveDocumentsToFolder(documentIds: List<Long>, targetFolderId: String) {
        documentDao.moveDocumentsToFolder(documentIds, targetFolderId)
    }

    suspend fun setDocumentFavorite(documentId: Long, isFavorite: Boolean) {
        documentDao.setDocumentFavorite(documentId, isFavorite)
    }

    suspend fun setDocumentsFavorite(documentIds: List<Long>, isFavorite: Boolean) {
        if (documentIds.isNotEmpty()) {
            documentDao.setDocumentsFavorite(documentIds, isFavorite)
        }
    }

    suspend fun setFolderFavorite(folderId: String, isFavorite: Boolean) {
        folderDao.setFolderFavorite(folderId, isFavorite)
    }

    suspend fun deleteDocuments(documentIds: List<Long>) = withContext(Dispatchers.IO) {
        val docs = documentIds.mapNotNull { documentDao.getDocumentById(it) }
        val pages = documentIds.flatMap { pageDao.getPagesForDocument(it) }
        documentDao.deleteDocumentsByIds(documentIds)
        docs.forEach { doc ->
            fileStorage.deleteFileQuietly(doc.pdfPath)
            fileStorage.deleteFileQuietly(doc.thumbnailPath)
        }
        pages.forEach { page ->
            fileStorage.deleteFileQuietly(page.originalImagePath)
            fileStorage.deleteFileQuietly(page.processedImagePath)
        }
    }

    suspend fun runOcrOnImagePath(imagePath: String, language: OcrLanguage): OcrResult = withContext(Dispatchers.IO) {
        if (!language.isBundled) {
            return@withContext OcrResult.LanguageUnavailable(language, language.statusNote)
        }
        val bitmap = fileStorage.loadBitmap(imagePath, 2000)
            ?: return@withContext OcrResult.Error("Unable to load page image.")
        try {
            ocrEngine.recognizeText(bitmap, language)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun runOcrOnPdfDocument(
        documentId: Long,
        language: OcrLanguage,
        password: String? = null
    ): OcrResult = withContext(Dispatchers.IO) {
        if (!language.isBundled) {
            return@withContext OcrResult.LanguageUnavailable(language, language.statusNote)
        }
        val doc = documentDao.getDocumentById(documentId)
            ?: return@withContext OcrResult.Error("Document not found.")

        val pages = pageDao.getPagesForDocument(documentId)
        val extractedPages = mutableListOf<String>()
        var totalBlocks = 0

        if (pages.isNotEmpty()) {
            for (page in pages) {
                val bmp = fileStorage.loadBitmap(page.processedImagePath, 1800) ?: continue
                val res = try {
                    ocrEngine.recognizeText(bmp, language)
                } finally {
                    bmp.recycle()
                }
                if (res is OcrResult.Success) {
                    extractedPages.add("--- Page ${page.pageIndex + 1} ---\n${res.text}")
                    totalBlocks += res.blockCount
                    pageDao.updatePage(page.copy(ocrText = res.text))
                }
            }
        } else if (!doc.pdfPath.isNullOrBlank()) {
            val info = pdfRendererHelper.inspectPdf(doc.pdfPath, password)
            val count = info?.pageCount ?: 0
            for (i in 0 until count) {
                val bmp = pdfRendererHelper.renderPage(doc.pdfPath, i, 1600, password) ?: continue
                val res = try {
                    ocrEngine.recognizeText(bmp, language)
                } finally {
                    bmp.recycle()
                }
                if (res is OcrResult.Success) {
                    extractedPages.add("--- Page ${i + 1} ---\n${res.text}")
                    totalBlocks += res.blockCount
                }
            }
        }

        val combined = extractedPages.joinToString("\n\n").trim()
        if (combined.isEmpty()) {
            OcrResult.NoTextFound(language)
        } else {
            documentDao.updateDocumentOcrText(documentId, combined)
            OcrResult.Success(text = combined, language = language, blockCount = totalBlocks)
        }
    }

    suspend fun saveDocumentOcrText(documentId: Long, text: String) {
        documentDao.updateDocumentOcrText(documentId, text)
    }

    suspend fun getTotalStorageBytes(): Long = fileStorage.computeTotalStorageBytes()

    companion object {
        val PREDEFINED_FOLDERS = listOf(
            FolderEntity("personal", "Personal Documents", 0xFF1E2A78L, "person", isPredefined = true, sortOrder = 1),
            FolderEntity("education", "Education", 0xFF2563EBL, "school", isPredefined = true, sortOrder = 2),
            FolderEntity("office", "Office", 0xFF0D9488L, "work", isPredefined = true, sortOrder = 3),
            FolderEntity("bills", "Bills", 0xFFD97706L, "receipt_long", isPredefined = true, sortOrder = 4),
            FolderEntity("receipts", "Receipts", 0xFF16A34AL, "receipt", isPredefined = true, sortOrder = 5),
            FolderEntity("identity", "Identity Documents", 0xFF7C3AEDL, "badge", isPredefined = true, sortOrder = 6),
            FolderEntity("other", "Other", 0xFF475569L, "folder", isPredefined = true, sortOrder = 7)
        )
    }
}
