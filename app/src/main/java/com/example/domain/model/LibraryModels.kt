package com.example.domain.model

import com.example.data.local.entity.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light (Soft White)"),
    DARK("Dark (Deep Navy)")
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

enum class FileTypeFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    SCAN("Scans"),
    HAS_OCR("With OCR")
}

enum class DateRangeFilter(val label: String) {
    ALL("Any Date"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM_DATE("Pick Date")
}

object DocumentLibraryFilterEngine {

    fun filterAndSort(
        documents: List<DocumentEntity>,
        query: String,
        fileTypeFilter: FileTypeFilter,
        folderId: String?,
        dateRangeFilter: DateRangeFilter,
        customDateMillis: Long?,
        sortOption: SortOption,
        favoritesOnly: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DocumentEntity> {
        val trimmedQuery = query.trim()
        val displayDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val isoDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val slashDateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)

        val (rangeStart, rangeEnd) = resolveDateBounds(dateRangeFilter, customDateMillis, nowMillis)

        val filtered = documents.filter { doc ->
            val createdDate = Date(doc.createdAt)
            val matchesQuery = trimmedQuery.isBlank() ||
                doc.title.contains(trimmedQuery, ignoreCase = true) ||
                doc.ocrText.contains(trimmedQuery, ignoreCase = true) ||
                displayDateFmt.format(createdDate).contains(trimmedQuery, ignoreCase = true) ||
                isoDateFmt.format(createdDate).contains(trimmedQuery, ignoreCase = true) ||
                slashDateFmt.format(createdDate).contains(trimmedQuery, ignoreCase = true)

            val matchesType = when (fileTypeFilter) {
                FileTypeFilter.ALL -> true
                FileTypeFilter.PDF -> doc.fileType == "PDF"
                FileTypeFilter.SCAN -> doc.fileType == "SCAN"
                FileTypeFilter.HAS_OCR -> doc.ocrText.isNotBlank()
            }

            val matchesFolder = folderId == null || doc.folderId == folderId

            val matchesDate = (rangeStart == null || doc.createdAt >= rangeStart) &&
                (rangeEnd == null || doc.createdAt <= rangeEnd)

            val matchesFavorite = !favoritesOnly || doc.isFavorite

            matchesQuery && matchesType && matchesFolder && matchesDate && matchesFavorite
        }

        return when (sortOption) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            SortOption.DATE_ASC -> filtered.sortedBy { it.createdAt }
            SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase(Locale.getDefault()) }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.fileSizeBytes }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.fileSizeBytes }
        }
    }

    private fun resolveDateBounds(
        filter: DateRangeFilter,
        customDateMillis: Long?,
        nowMillis: Long
    ): Pair<Long?, Long?> {
        if (filter == DateRangeFilter.ALL) return null to null

        val cal = Calendar.getInstance()
        return when (filter) {
            DateRangeFilter.ALL -> null to null
            DateRangeFilter.TODAY -> {
                cal.timeInMillis = nowMillis
                setStartOfDay(cal)
                val start = cal.timeInMillis
                setEndOfDay(cal)
                start to cal.timeInMillis
            }
            DateRangeFilter.LAST_7_DAYS -> {
                cal.timeInMillis = nowMillis
                cal.add(Calendar.DAY_OF_YEAR, -6)
                setStartOfDay(cal)
                val start = cal.timeInMillis
                cal.timeInMillis = nowMillis
                setEndOfDay(cal)
                start to cal.timeInMillis
            }
            DateRangeFilter.LAST_30_DAYS -> {
                cal.timeInMillis = nowMillis
                cal.add(Calendar.DAY_OF_YEAR, -29)
                setStartOfDay(cal)
                val start = cal.timeInMillis
                cal.timeInMillis = nowMillis
                setEndOfDay(cal)
                start to cal.timeInMillis
            }
            DateRangeFilter.CUSTOM_DATE -> {
                val target = customDateMillis ?: return null to null
                // Material3 DatePicker returns UTC midnight; convert calendar fields cleanly
                val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = target
                }
                val localCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                    set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                }
                setStartOfDay(localCal)
                val start = localCal.timeInMillis
                setEndOfDay(localCal)
                start to localCal.timeInMillis
            }
        }
    }

    private fun setStartOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun setEndOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
    }
}

enum class RenameSeparator(val label: String, val token: String) {
    UNDERSCORE("_", "_"),
    HYPHEN("-", "-"),
    SPACE("Space", " "),
    NONE("None", "")
}

enum class SuffixPadding(val label: String, val digits: Int) {
    NONE("1, 2, 3", 1),
    TWO_DIGITS("01, 02, 03", 2),
    THREE_DIGITS("001, 002, 003", 3)
}

data class BulkRenameItemPreview(
    val documentId: Long,
    val currentTitle: String,
    val newTitle: String
)

object BulkRenameFormatter {

    fun formatName(
        prefix: String,
        indexOffset: Int,
        startNumber: Int = 1,
        separator: RenameSeparator = RenameSeparator.UNDERSCORE,
        padding: SuffixPadding = SuffixPadding.NONE
    ): String {
        val cleanPrefix = prefix.trim()
        if (cleanPrefix.isBlank()) return ""

        val safeStart = startNumber.coerceAtLeast(0)
        val number = safeStart + indexOffset.coerceAtLeast(0)
        val numericSuffix = number.toString().padStart(padding.digits, '0')
        val effectiveSeparator = if (
            separator != RenameSeparator.NONE &&
            cleanPrefix.endsWith(separator.token)
        ) {
            ""
        } else {
            separator.token
        }
        return "$cleanPrefix$effectiveSeparator$numericSuffix"
    }

    fun buildPreview(
        documents: List<DocumentEntity>,
        prefix: String,
        startNumber: Int = 1,
        separator: RenameSeparator = RenameSeparator.UNDERSCORE,
        padding: SuffixPadding = SuffixPadding.NONE
    ): List<BulkRenameItemPreview> {
        return documents.mapIndexed { index, doc ->
            BulkRenameItemPreview(
                documentId = doc.id,
                currentTitle = doc.title,
                newTitle = formatName(
                    prefix = prefix,
                    indexOffset = index,
                    startNumber = startNumber,
                    separator = separator,
                    padding = padding
                )
            )
        }
    }
}
