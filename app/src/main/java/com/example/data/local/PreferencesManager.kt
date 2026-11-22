package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DocumentFilter
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfQuality
import com.example.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scanMateDataStore: DataStore<Preferences> by preferencesDataStore(name = "scanmate_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_PAPER_SIZE = stringPreferencesKey("default_paper_size")
        val DEFAULT_PDF_QUALITY = stringPreferencesKey("default_pdf_quality")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val GRID_OVERLAY = booleanPreferencesKey("grid_overlay")
        val AUTO_BATTERY_SAVER = booleanPreferencesKey("auto_battery_saver")
        val FORCE_BATTERY_SAVER = booleanPreferencesKey("force_battery_saver")
    }

    val onboardingCompleted: Flow<Boolean> = context.scanMateDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val themeMode: Flow<ThemeMode> = context.scanMateDataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE]
        runCatching { ThemeMode.valueOf(raw ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }

    val defaultPaperSize: Flow<PaperSize> = context.scanMateDataStore.data.map { prefs ->
        val raw = prefs[Keys.DEFAULT_PAPER_SIZE]
        runCatching { PaperSize.valueOf(raw ?: "") }.getOrDefault(PaperSize.A4)
    }

    val defaultPdfQuality: Flow<PdfQuality> = context.scanMateDataStore.data.map { prefs ->
        val raw = prefs[Keys.DEFAULT_PDF_QUALITY]
        runCatching { PdfQuality.valueOf(raw ?: "") }.getOrDefault(PdfQuality.STANDARD)
    }

    val defaultFilter: Flow<DocumentFilter> = context.scanMateDataStore.data.map { prefs ->
        val raw = prefs[Keys.DEFAULT_FILTER]
        runCatching { DocumentFilter.valueOf(raw ?: "") }.getOrDefault(DocumentFilter.AUTO_ENHANCE)
    }

    val autoCaptureEnabled: Flow<Boolean> = context.scanMateDataStore.data.map { prefs ->
        prefs[Keys.AUTO_CAPTURE] ?: false
    }

    val gridOverlayEnabled: Flow<Boolean> = context.scanMateDataStore.data.map { prefs ->
        prefs[Keys.GRID_OVERLAY] ?: true
    }

    val batterySaverAutoEnabled: Flow<Boolean> = context.scanMateDataStore.data.map { prefs ->
        prefs[Keys.AUTO_BATTERY_SAVER] ?: true
    }

    val batterySaverAlwaysOn: Flow<Boolean> = context.scanMateDataStore.data.map { prefs ->
        prefs[Keys.FORCE_BATTERY_SAVER] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.scanMateDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.scanMateDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultPaperSize(paperSize: PaperSize) {
        context.scanMateDataStore.edit { it[Keys.DEFAULT_PAPER_SIZE] = paperSize.name }
    }

    suspend fun setDefaultPdfQuality(quality: PdfQuality) {
        context.scanMateDataStore.edit { it[Keys.DEFAULT_PDF_QUALITY] = quality.name }
    }

    suspend fun setDefaultFilter(filter: DocumentFilter) {
        context.scanMateDataStore.edit { it[Keys.DEFAULT_FILTER] = filter.name }
    }

    suspend fun setAutoCaptureEnabled(enabled: Boolean) {
        context.scanMateDataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    }

    suspend fun setGridOverlayEnabled(enabled: Boolean) {
        context.scanMateDataStore.edit { it[Keys.GRID_OVERLAY] = enabled }
    }

    suspend fun setBatterySaverAutoEnabled(enabled: Boolean) {
        context.scanMateDataStore.edit { it[Keys.AUTO_BATTERY_SAVER] = enabled }
    }

    suspend fun setBatterySaverAlwaysOn(enabled: Boolean) {
        context.scanMateDataStore.edit { it[Keys.FORCE_BATTERY_SAVER] = enabled }
    }
}
