package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.FolderEntity
import com.example.ui.components.ReorderableDragHandle
import com.example.ui.components.ReorderablePageItem
import com.example.ui.components.rememberReorderableLazyListState
import com.example.ui.components.reorderableContainer
import com.example.ui.home.HomeFavoritesSection
import com.example.data.local.ScanMateDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.ocr.OfflineMlKitOcrEngine
import com.example.data.pdf.PdfGenerator
import com.example.data.pdf.PdfInspectOutcome
import com.example.data.pdf.PdfRendererHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.example.data.processing.DocumentEdgeDetector
import com.example.data.processing.ImageProcessor
import com.example.data.repository.ScanMateRepository
import com.example.data.storage.LocalFileStorage
import com.example.domain.model.BulkRenameFormatter
import com.example.domain.model.DateRangeFilter
import com.example.domain.model.DocumentFilter
import com.example.domain.model.DocumentLibraryFilterEngine
import com.example.domain.model.FileTypeFilter
import com.example.domain.model.ImageQuality
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrResult
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfOrientation
import com.example.domain.model.QuadPoints
import com.example.domain.model.RenameSeparator
import com.example.domain.model.SortOption
import com.example.domain.model.SuffixPadding
import android.content.Intent
import android.os.BatteryManager
import com.example.data.local.PreferencesManager
import com.example.data.power.BatteryStatusMonitor
import com.example.domain.model.BatterySaverResolver
import com.example.domain.model.BatteryStatus
import com.example.domain.model.PdfGenerationConfig
import com.example.ui.components.PdfExportSheet
import com.example.ui.components.SetPdfPasswordDialog
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.ui.home.HomeBatterySaverBanner
import com.example.ui.settings.BatterySaverSettingsCard
import com.example.ui.session.ActiveScanSessionHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches ScanMate`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ScanMate", appName)
    }

    @Test
    fun `quad points json roundtrip preserves coordinates`() {
        val original = QuadPoints.DEFAULT_INSET
        val json = original.toJson()
        val restored = QuadPoints.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun `edge detector detects bright document rectangle on dark backdrop`() {
        val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(25f, 35f, 175f, 245f, paint)

        val result = DocumentEdgeDetector.detectFromBitmap(bmp)
        assertTrue(result.quad.topLeft.x < 0.35f)
        assertTrue(result.quad.bottomRight.x > 0.65f)
        bmp.recycle()
    }

    @Test
    fun `image processor runs all 7 filters and perspective warp without error`() = runTest {
        val bmp = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.LTGRAY)
        DocumentFilter.entries.forEach { filter ->
            val out = ImageProcessor.processFullPipeline(
                originalBitmap = bmp,
                quad = QuadPoints.DEFAULT_INSET,
                rotationDegrees = 90,
                filter = filter,
                brightness = 0.1f,
                contrast = 1.1f,
                sharpness = 0.3f,
                quality = ImageQuality.COMPACT
            )
            assertTrue(out.width > 0 && out.height > 0)
            out.recycle()
        }
        bmp.recycle()
    }

    @Test
    fun `hindi ocr cleanly indicates language pack unavailable offline`() = runTest {
        val engine = OfflineMlKitOcrEngine()
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val result = engine.recognizeText(bmp, OcrLanguage.HINDI)
        assertTrue(result is OcrResult.LanguageUnavailable)
        bmp.recycle()
    }

    @Test
    fun `room database seeds all 7 predefined folders`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScanMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.folderDao().insertFoldersIgnore(ScanMateRepository.PREDEFINED_FOLDERS)
            val folders = db.folderDao().observeAllFolders().first()
            assertEquals(7, folders.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `pdf page dimensions and multi page session reorder edit delete work correctly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalFileStorage(context)
        val generator = PdfGenerator(storage)

        assertEquals(595 to 842, generator.resolvePageDimensions(1000, 1400, PaperSize.A4, PdfOrientation.PORTRAIT))
        assertEquals(842 to 595, generator.resolvePageDimensions(1000, 1400, PaperSize.A4, PdfOrientation.LANDSCAPE))
        assertEquals(612 to 792, generator.resolvePageDimensions(1000, 1400, PaperSize.LETTER, PdfOrientation.PORTRAIT))
        assertEquals(792 to 612, generator.resolvePageDimensions(1000, 1400, PaperSize.LETTER, PdfOrientation.LANDSCAPE))
        assertEquals(842 to 1191, generator.resolvePageDimensions(1000, 1400, PaperSize.A3, PdfOrientation.PORTRAIT))
        assertEquals(1191 to 842, generator.resolvePageDimensions(1000, 1400, PaperSize.A3, PdfOrientation.LANDSCAPE))
        assertEquals(500 to 1000, generator.resolvePageDimensions(1000, 2000, PaperSize.ORIGINAL_RATIO, PdfOrientation.PORTRAIT))
        assertEquals(1000 to 500, generator.resolvePageDimensions(1000, 2000, PaperSize.ORIGINAL_RATIO, PdfOrientation.LANDSCAPE))

        val sessionHolder = ActiveScanSessionHolder(
            storage,
            BatteryStatusMonitor(context, PreferencesManager(context))
        )
        sessionHolder.startFreshSession(folderId = "office", defaultTitle = "Combined_Packet")

        val p1 = storage.createOriginalImageFile()
        val p2 = storage.createOriginalImageFile()
        val p3 = storage.createOriginalImageFile()
        val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        storage.saveBitmapJpeg(bmp, p1, 85)
        storage.saveBitmapJpeg(bmp, p2, 85)
        storage.saveBitmapJpeg(bmp, p3, 85)
        bmp.recycle()

        val page1 = sessionHolder.addCapturedPage(p1, DocumentFilter.ORIGINAL)
        val page2 = sessionHolder.addCapturedPage(p2, DocumentFilter.DOCUMENT)
        val page3 = sessionHolder.addCapturedPage(p3, DocumentFilter.GRAYSCALE)
        assertEquals(3, sessionHolder.state.value.pages.size)

        // Reorder page 3 to the front
        sessionHolder.movePage(fromIndex = 2, toIndex = 0)
        assertEquals(listOf(page3.id, page1.id, page2.id), sessionHolder.state.value.pages.map { it.id })

        // Edit page 1 filter + rotation
        val edited = sessionHolder.updatePageAndReprocess(
            pageId = page1.id,
            quad = QuadPoints.DEFAULT_INSET,
            rotationDegrees = 90,
            filter = DocumentFilter.HIGH_CONTRAST,
            brightness = 0.05f,
            contrast = 1.15f,
            sharpness = 0.3f,
            quality = ImageQuality.COMPACT
        )
        assertEquals(DocumentFilter.HIGH_CONTRAST, edited?.filter)
        assertEquals(90, edited?.rotationDegrees)

        // Delete middle page
        sessionHolder.deletePage(page1.id)
        assertEquals(listOf(page3.id, page2.id), sessionHolder.state.value.pages.map { it.id })
    }

    @Test
    fun `library filter engine filters by name creation date folder and sorts accurately`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nov18 = cal.timeInMillis
        val nov10 = nov18 - 8L * 24 * 60 * 60 * 1000
        val oct01 = nov18 - 48L * 24 * 60 * 60 * 1000

        val docs = listOf(
            DocumentEntity(id = 1, title = "Electric Bill", folderId = "bills", fileType = "PDF", fileSizeBytes = 5000, pageCount = 2, createdAt = nov18, updatedAt = nov18),
            DocumentEntity(id = 2, title = "Alpha Contract", folderId = "office", fileType = "PDF", fileSizeBytes = 25000, pageCount = 5, createdAt = nov10, updatedAt = nov10),
            DocumentEntity(id = 3, title = "Zeta Receipt", folderId = "bills", fileType = "SCAN", fileSizeBytes = 1200, pageCount = 1, createdAt = oct01, updatedAt = oct01)
        )

        // Search by formatted creation date
        val byDateSearch = DocumentLibraryFilterEngine.filterAndSort(
            documents = docs,
            query = "2026-11-18",
            fileTypeFilter = FileTypeFilter.ALL,
            folderId = null,
            dateRangeFilter = DateRangeFilter.ALL,
            customDateMillis = null,
            sortOption = SortOption.DATE_DESC,
            nowMillis = nov18
        )
        assertEquals(listOf(1L), byDateSearch.map { it.id })

        // Filter by folder + sort by size descending
        val billsBySize = DocumentLibraryFilterEngine.filterAndSort(
            documents = docs,
            query = "",
            fileTypeFilter = FileTypeFilter.ALL,
            folderId = "bills",
            dateRangeFilter = DateRangeFilter.ALL,
            customDateMillis = null,
            sortOption = SortOption.SIZE_DESC,
            nowMillis = nov18
        )
        assertEquals(listOf(1L, 3L), billsBySize.map { it.id })

        // Custom date picker UTC midnight match for Nov 10
        val utcNov10 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val customDateFiltered = DocumentLibraryFilterEngine.filterAndSort(
            documents = docs,
            query = "",
            fileTypeFilter = FileTypeFilter.ALL,
            folderId = null,
            dateRangeFilter = DateRangeFilter.CUSTOM_DATE,
            customDateMillis = utcNov10,
            sortOption = SortOption.NAME_ASC,
            nowMillis = nov18
        )
        assertEquals(listOf(2L), customDateFiltered.map { it.id })

        // Sort alphabetically A-Z across all
        val alphaSorted = DocumentLibraryFilterEngine.filterAndSort(
            documents = docs,
            query = "",
            fileTypeFilter = FileTypeFilter.ALL,
            folderId = null,
            dateRangeFilter = DateRangeFilter.ALL,
            customDateMillis = null,
            sortOption = SortOption.NAME_ASC,
            nowMillis = nov18
        )
        assertEquals(listOf("Alpha Contract", "Electric Bill", "Zeta Receipt"), alphaSorted.map { it.title })
    }

    @Test
    fun `bulk rename formatter and repository persist prefix and incremental numerical suffixes`() = runTest {
        val sampleDocs = listOf(
            DocumentEntity(id = 10, title = "Scan A", folderId = "bills", fileType = "PDF", fileSizeBytes = 1000, pageCount = 1),
            DocumentEntity(id = 20, title = "Scan B", folderId = "bills", fileType = "PDF", fileSizeBytes = 1000, pageCount = 1),
            DocumentEntity(id = 30, title = "Scan C", folderId = "bills", fileType = "PDF", fileSizeBytes = 1000, pageCount = 1)
        )

        val paddedUnderscore = BulkRenameFormatter.buildPreview(
            documents = sampleDocs,
            prefix = "Invoice",
            startNumber = 5,
            separator = RenameSeparator.UNDERSCORE,
            padding = SuffixPadding.TWO_DIGITS
        )
        assertEquals(listOf("Invoice_05", "Invoice_06", "Invoice_07"), paddedUnderscore.map { it.newTitle })

        val noDoubleSeparator = BulkRenameFormatter.formatName(
            prefix = "Tax_",
            indexOffset = 0,
            startNumber = 1,
            separator = RenameSeparator.UNDERSCORE,
            padding = SuffixPadding.THREE_DIGITS
        )
        assertEquals("Tax_001", noDoubleSeparator)

        val spaceSeparated = BulkRenameFormatter.formatName(
            prefix = "Receipt",
            indexOffset = 2,
            startNumber = 10,
            separator = RenameSeparator.SPACE,
            padding = SuffixPadding.NONE
        )
        assertEquals("Receipt 12", spaceSeparated)

        // Verify transactional Room bulk rename persistence
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScanMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.folderDao().insertFoldersIgnore(ScanMateRepository.PREDEFINED_FOLDERS)
            val id1 = db.documentDao().insertDocument(sampleDocs[0].copy(id = 0))
            val id2 = db.documentDao().insertDocument(sampleDocs[1].copy(id = 0))
            val id3 = db.documentDao().insertDocument(sampleDocs[2].copy(id = 0))

            val storage = LocalFileStorage(context)
            val repo = ScanMateRepository(
                database = db,
                folderDao = db.folderDao(),
                documentDao = db.documentDao(),
                pageDao = db.pageDao(),
                fileStorage = storage,
                pdfGenerator = PdfGenerator(storage),
                pdfRendererHelper = PdfRendererHelper(),
                ocrEngine = OfflineMlKitOcrEngine()
            )

            repo.bulkRenameDocuments(
                listOf(
                    id1 to "Contract_01",
                    id2 to "Contract_02",
                    id3 to "Contract_03"
                )
            )

            assertEquals("Contract_01", db.documentDao().getDocumentById(id1)?.title)
            assertEquals("Contract_02", db.documentDao().getDocumentById(id2)?.title)
            assertEquals("Contract_03", db.documentDao().getDocumentById(id3)?.title)
        } finally {
            db.close()
        }
    }

    @Test
    fun `pdf encryption protects file with password and verifies unlock outcomes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalFileStorage(context)
        val generator = PdfGenerator(storage)
        val renderer = PdfRendererHelper()

        val rawPdf = storage.createTempPdfFile("test_raw")
        val protectedPdf = storage.createPdfFile("Protected_Test")
        try {
            PDDocument().use { doc ->
                doc.addPage(PDPage())
                doc.addPage(PDPage())
                doc.save(rawPdf)
            }

            val encrypted = generator.encryptPdfFile(
                sourceFile = rawPdf,
                destinationFile = protectedPdf,
                userPassword = "secret123"
            )
            assertTrue(encrypted)
            assertTrue(protectedPdf.exists() && protectedPdf.length() > 0L)

            val noPassOutcome = renderer.inspectPdfOutcome(protectedPdf.absolutePath, password = null)
            assertTrue(noPassOutcome is PdfInspectOutcome.PasswordRequired)

            val wrongPassOutcome = renderer.inspectPdfOutcome(protectedPdf.absolutePath, password = "wrong")
            assertTrue(wrongPassOutcome is PdfInspectOutcome.WrongPassword)

            val rightPassOutcome = renderer.inspectPdfOutcome(protectedPdf.absolutePath, password = "secret123")
            assertTrue(rightPassOutcome is PdfInspectOutcome.Ready)
            val info = (rightPassOutcome as PdfInspectOutcome.Ready).info
            assertEquals(2, info.pageCount)
            assertTrue(info.isEncrypted)
        } finally {
            rawPdf.delete()
            protectedPdf.delete()
        }
    }

    @Test
    fun `drag and drop handle reorders multi page list items accurately`() {
        lateinit var currentOrder: List<String>

        composeTestRule.setContent {
            val pages = remember { mutableStateListOf("Page-A", "Page-B", "Page-C") }
            currentOrder = pages
            val listState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(
                lazyListState = listState,
                enabled = pages.size > 1,
                onMove = { from, to ->
                    val moved = pages.removeAt(from)
                    pages.add(to, moved)
                }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .size(width = 360.dp, height = 520.dp)
                    .reorderableContainer(reorderState),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(pages, key = { _, item -> item }) { index, item ->
                    ReorderablePageItem(
                        state = reorderState,
                        itemKey = item
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("test_card_$item")
                        ) {
                            ReorderableDragHandle(
                                state = reorderState,
                                itemKey = item,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("test_handle_$item")
                            ) {
                                Box(modifier = Modifier.size(24.dp))
                            }
                            Text(text = "$item#${index + 1}")
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(listOf("Page-A", "Page-B", "Page-C"), currentOrder.toList())

        // Drag Page-A down past Page-B's midpoint and release
        composeTestRule.onNodeWithTag("test_handle_Page-A").performTouchInput {
            down(center)
            moveBy(Offset(0f, 140f), delayMillis = 16L)
            moveBy(Offset(0f, 140f), delayMillis = 16L)
            moveBy(Offset(0f, 140f), delayMillis = 16L)
            up()
        }

        composeTestRule.waitForIdle()
        assertEquals("Page-B", currentOrder[0])
        assertEquals("Page-A", currentOrder[1])
        assertEquals("Page-C", currentOrder[2])
    }

    @Test
    fun `favorites repository persists document and folder stars and filter engine filters favorites`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScanMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.folderDao().insertFoldersIgnore(ScanMateRepository.PREDEFINED_FOLDERS)
            val id1 = db.documentDao().insertDocument(
                DocumentEntity(title = "Passport Scan", folderId = "identity", fileType = "PDF", fileSizeBytes = 1200, pageCount = 2)
            )
            val id2 = db.documentDao().insertDocument(
                DocumentEntity(title = "Grocery Receipt", folderId = "receipts", fileType = "SCAN", fileSizeBytes = 600, pageCount = 1)
            )

            val storage = LocalFileStorage(context)
            val repo = ScanMateRepository(
                database = db,
                folderDao = db.folderDao(),
                documentDao = db.documentDao(),
                pageDao = db.pageDao(),
                fileStorage = storage,
                pdfGenerator = PdfGenerator(storage),
                pdfRendererHelper = PdfRendererHelper(),
                ocrEngine = OfflineMlKitOcrEngine()
            )

            repo.setDocumentFavorite(id1, true)
            repo.setFolderFavorite("identity", true)

            val allDocs = repo.documents.first()
            val allFolders = repo.folders.first()
            assertEquals(true, allDocs.first { it.id == id1 }.isFavorite)
            assertEquals(false, allDocs.first { it.id == id2 }.isFavorite)
            assertEquals(true, allFolders.first { it.id == "identity" }.isFavorite)

            val starredOnly = DocumentLibraryFilterEngine.filterAndSort(
                documents = allDocs,
                query = "",
                fileTypeFilter = FileTypeFilter.ALL,
                folderId = null,
                dateRangeFilter = DateRangeFilter.ALL,
                customDateMillis = null,
                sortOption = SortOption.DATE_DESC,
                favoritesOnly = true
            )
            assertEquals(listOf(id1), starredOnly.map { it.id })

            repo.setDocumentsFavorite(listOf(id1, id2), true)
            assertEquals(2, repo.documents.first().count { it.isFavorite })

            repo.setFolderFavorite("identity", false)
            assertEquals(false, db.folderDao().getFolderById("identity")?.isFavorite)
        } finally {
            db.close()
        }
    }

    @Test
    fun `home favorites section renders empty state and starred documents and folders`() {
        var openedFolderId: String? = null
        var openedDocId: Long? = null
        var unstarredFolderId: String? = null
        var unstarredDocId: Long? = null

        val favFolder = FolderEntity(
            id = "office",
            name = "Office",
            colorHex = 0xFF0D9488L,
            iconKey = "work",
            isPredefined = true,
            isFavorite = true
        )
        val favDoc = DocumentEntity(
            id = 42L,
            title = "Tax_Summary_2026",
            folderId = "office",
            fileType = "PDF",
            fileSizeBytes = 4096,
            pageCount = 4,
            isFavorite = true
        )

        val showPopulated = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            val populated = showPopulated.value
            HomeFavoritesSection(
                favoriteFolders = if (populated) listOf(favFolder) else emptyList(),
                favoriteDocuments = if (populated) listOf(favDoc) else emptyList(),
                folderDocumentCounts = mapOf("office" to 3),
                folderMap = mapOf("office" to favFolder),
                hasAnyDocuments = true,
                onOpenFolder = { openedFolderId = it },
                onOpenDocument = { openedDocId = it },
                onToggleFolderFavorite = { unstarredFolderId = it.id },
                onToggleDocumentFavorite = { unstarredDocId = it.id },
                onBrowseDocuments = {},
                onBrowseFolders = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_favorites_empty").assertIsDisplayed()

        showPopulated.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_favorite_folders_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorite_folder_card_office").assertIsDisplayed()
        composeTestRule.onNodeWithText("Office").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_favorite_docs_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorite_doc_card_42").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tax_Summary_2026").assertIsDisplayed()

        composeTestRule.onNodeWithTag("favorite_folder_card_office").performClick()
        composeTestRule.onNodeWithTag("favorite_doc_card_42").performClick()
        composeTestRule.onNodeWithTag("home_favorite_folder_star_office").performClick()
        composeTestRule.onNodeWithTag("home_favorite_doc_star_42").performClick()

        assertEquals("office", openedFolderId)
        assertEquals(42L, openedDocId)
        assertEquals("office", unstarredFolderId)
        assertEquals(42L, unstarredDocId)
    }

    @Test
    fun `battery saver policy activates below 20 percent and parses battery intent`() {
        val lowIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 19)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        }
        val lowStatus = BatteryStatusMonitor.parseBatteryIntent(lowIntent)
        assertEquals(BatteryStatus(levelPercent = 19, isCharging = false), lowStatus)
        assertTrue(lowStatus!!.isLowBattery)

        val profileAt19 = BatterySaverResolver.resolve(
            batteryStatus = lowStatus,
            autoEnabled = true,
            alwaysOnEnabled = false
        )
        assertTrue(profileAt19.isActive)
        assertEquals(BatterySaverResolver.BATTERY_SAVER_MAX_SCAN_DIM_PX, profileAt19.maxScanDimensionPx)
        assertEquals(BatterySaverResolver.BATTERY_SAVER_ENHANCEMENT_INTENSITY, profileAt19.enhancementIntensity, 0.001f)
        assertEquals(ImageQuality.COMPACT, profileAt19.scanQuality)

        val statusAt20 = BatteryStatus(levelPercent = 20, isCharging = false)
        assertFalse(statusAt20.isLowBattery)
        val profileAt20 = BatterySaverResolver.resolve(
            batteryStatus = statusAt20,
            autoEnabled = true,
            alwaysOnEnabled = false
        )
        assertFalse(profileAt20.isActive)
        assertEquals(BatterySaverResolver.NORMAL_MAX_SCAN_DIM_PX, profileAt20.maxScanDimensionPx)
        assertEquals(BatterySaverResolver.NORMAL_ENHANCEMENT_INTENSITY, profileAt20.enhancementIntensity, 0.001f)

        val forcedProfileAt80 = BatterySaverResolver.resolve(
            batteryStatus = BatteryStatus(levelPercent = 80, isCharging = true),
            autoEnabled = false,
            alwaysOnEnabled = true
        )
        assertTrue(forcedProfileAt80.isActive)

        val disabledAutoAt15 = BatterySaverResolver.resolve(
            batteryStatus = BatteryStatus(levelPercent = 15, isCharging = false),
            autoEnabled = false,
            alwaysOnEnabled = false
        )
        assertFalse(disabledAutoAt15.isActive)
    }

    @Test
    fun `image processor reduces scan resolution and enhancement intensity in battery saver mode`() = runTest {
        val source = Bitmap.createBitmap(1800, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        canvas.drawColor(Color.rgb(210, 200, 185))
        val darkTextPaint = Paint().apply { color = Color.rgb(65, 70, 80) }
        canvas.drawRect(250f, 350f, 1550f, 2050f, darkTextPaint)

        val normalOut = ImageProcessor.processFullPipeline(
            originalBitmap = source,
            quad = QuadPoints.FULL_FRAME,
            rotationDegrees = 0,
            filter = DocumentFilter.HIGH_CONTRAST,
            brightness = 0.2f,
            contrast = 1.4f,
            sharpness = 0.3f,
            quality = ImageQuality.BALANCED,
            maxDimensionOverridePx = BatterySaverResolver.NORMAL_MAX_SCAN_DIM_PX,
            enhancementIntensity = BatterySaverResolver.NORMAL_ENHANCEMENT_INTENSITY
        )
        val saverOut = ImageProcessor.processFullPipeline(
            originalBitmap = source,
            quad = QuadPoints.FULL_FRAME,
            rotationDegrees = 0,
            filter = DocumentFilter.HIGH_CONTRAST,
            brightness = 0.2f,
            contrast = 1.4f,
            sharpness = 0.3f,
            quality = ImageQuality.COMPACT,
            maxDimensionOverridePx = BatterySaverResolver.BATTERY_SAVER_MAX_SCAN_DIM_PX,
            enhancementIntensity = BatterySaverResolver.BATTERY_SAVER_ENHANCEMENT_INTENSITY
        )

        // Battery Saver caps max dimension to 1080px vs 2000px normal
        assertEquals(2000, normalOut.height)
        assertEquals(1080, saverOut.height)
        assertTrue(saverOut.width < normalOut.width)

        // Compare color/contrast delta vs original mid-tone background pixel (100, 100)
        val origPixel = source.getPixel(100, 100)
        val normalPixel = normalOut.getPixel(40, 40)
        val saverPixel = saverOut.getPixel(40, 40)
        val origRed = Color.red(origPixel)
        val normalShift = abs(Color.red(normalPixel) - origRed)
        val saverShift = abs(Color.red(saverPixel) - origRed)
        assertTrue("Expected Battery Saver enhancement shift ($saverShift) <= normal ($normalShift)", saverShift < normalShift)

        normalOut.recycle()
        saverOut.recycle()
        source.recycle()
    }

    @Test
    fun `battery saver settings card and home banner display status and handle toggles`() {
        var autoToggledValue: Boolean? = null
        var forceToggledValue: Boolean? = null
        var openedSettings = false

        val activeProfile = BatterySaverResolver.resolve(
            batteryStatus = BatteryStatus(levelPercent = 16, isCharging = false),
            autoEnabled = true,
            alwaysOnEnabled = false
        )

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                HomeBatterySaverBanner(
                    profile = activeProfile,
                    onOpenSettings = { openedSettings = true }
                )
                BatterySaverSettingsCard(
                    profile = activeProfile,
                    onToggleAuto = { autoToggledValue = it },
                    onToggleAlwaysOn = { forceToggledValue = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_battery_saver_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Battery Saver Active • 16%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_saver_status_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_battery_saver_settings_button").performClick()
        composeTestRule.onNodeWithTag("battery_saver_auto_switch").performClick()
        composeTestRule.onNodeWithTag("battery_saver_force_switch").performClick()

        assertTrue(openedSettings)
        assertEquals(false, autoToggledValue)
        assertEquals(true, forceToggledValue)
    }

    @Test
    fun `pdf generator creates encrypted password protected pdf from images using PdfDocument`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalFileStorage(context)
        val generator = PdfGenerator(storage)
        val renderer = PdfRendererHelper()

        val img1 = storage.createProcessedImageFile()
        val img2 = storage.createProcessedImageFile()
        val bmp = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        storage.saveBitmapJpeg(bmp, img1, 85)
        storage.saveBitmapJpeg(bmp, img2, 85)
        bmp.recycle()

        val result = generator.generatePdf(
            imagePaths = listOf(img1.absolutePath, img2.absolutePath),
            config = PdfGenerationConfig(
                documentName = "Confidential_Contract",
                folderId = "office",
                paperSize = PaperSize.A4,
                orientation = PdfOrientation.PORTRAIT,
                password = "VaultPassword#99"
            ),
            onProgress = { _, _ -> }
        )

        try {
            assertTrue(result.isPasswordProtected)
            assertEquals(2, result.pageCount)
            assertTrue(result.pdfFile.exists() && result.fileSizeBytes > 0L)

            val lockedOutcome = renderer.inspectPdfOutcome(result.pdfFile.absolutePath, password = null)
            assertTrue(lockedOutcome is PdfInspectOutcome.PasswordRequired)

            val wrongOutcome = renderer.inspectPdfOutcome(result.pdfFile.absolutePath, password = "wrong-password")
            assertTrue(wrongOutcome is PdfInspectOutcome.WrongPassword)

            val readyOutcome = renderer.inspectPdfOutcome(result.pdfFile.absolutePath, password = "VaultPassword#99")
            assertTrue(readyOutcome is PdfInspectOutcome.Ready)
            assertEquals(2, (readyOutcome as PdfInspectOutcome.Ready).info.pageCount)
            assertTrue(readyOutcome.info.isEncrypted)

            val rendered = renderer.renderPage(
                pdfPath = result.pdfFile.absolutePath,
                pageIndex = 0,
                targetWidthPx = 400,
                password = "VaultPassword#99"
            )
            assertNotNull(rendered)
            rendered?.recycle()
        } finally {
            result.pdfFile.delete()
            img1.delete()
            img2.delete()
        }
    }

    @Test
    fun `set pdf password dialog and export sheet validate and submit password`() {
        var submittedDialogPassword: String? = null
        var exportedConfig: PdfGenerationConfig? = null

        val showSheet = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            if (!showSheet.value) {
                SetPdfPasswordDialog(
                    onDismiss = {},
                    onConfirm = { submittedDialogPassword = it }
                )
            } else {
                PdfExportSheet(
                    initialTitle = "Tax_2026",
                    initialFolderId = "bills",
                    initialPaperSize = PaperSize.A4,
                    initialQuality = com.example.domain.model.PdfQuality.STANDARD,
                    folders = listOf(
                        FolderEntity("bills", "Bills", 0xFFD97706L, "receipt_long", isPredefined = true)
                    ),
                    pageCount = 2,
                    onDismiss = {},
                    onExportPdf = { exportedConfig = it },
                    onSaveScanOnly = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        // Initially disabled when empty
        composeTestRule.onNodeWithTag("confirm_set_pdf_password_button").assertIsNotEnabled()

        // Short password stays disabled
        composeTestRule.onNodeWithTag("set_pdf_password_input").performTextInput("12")
        composeTestRule.onNodeWithTag("set_pdf_confirm_password_input").performTextInput("12")
        composeTestRule.onNodeWithTag("confirm_set_pdf_password_button").assertIsNotEnabled()

        // Valid matching password enables & submits
        composeTestRule.onNodeWithTag("set_pdf_password_input").performTextInput("3456")
        composeTestRule.onNodeWithTag("set_pdf_confirm_password_input").performTextInput("3456")
        composeTestRule.onNodeWithTag("confirm_set_pdf_password_button").assertIsEnabled().performClick()
        assertEquals("123456", submittedDialogPassword)

        // Switch to PdfExportSheet and toggle password protection
        showSheet.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pdf_password_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("generate_pdf_confirm_button").performScrollTo().assertIsNotEnabled()

        composeTestRule.onNodeWithTag("pdf_password_input").performScrollTo().performTextInput("ScanPass!1")
        composeTestRule.onNodeWithTag("pdf_confirm_password_input").performScrollTo().performTextInput("ScanPass!1")
        composeTestRule.onNodeWithTag("generate_pdf_confirm_button").performScrollTo().assertIsEnabled().performClick()

        assertEquals("ScanPass!1", exportedConfig?.password)
        assertEquals("Tax_2026", exportedConfig?.documentName)
    }
}
