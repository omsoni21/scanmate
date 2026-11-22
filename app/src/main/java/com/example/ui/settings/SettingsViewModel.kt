package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.power.BatteryStatusMonitor
import com.example.data.repository.ScanMateRepository
import com.example.domain.model.BatterySaverProfile
import com.example.domain.model.DocumentFilter
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfQuality
import com.example.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultPaperSize: PaperSize = PaperSize.A4,
    val defaultPdfQuality: PdfQuality = PdfQuality.STANDARD,
    val defaultFilter: DocumentFilter = DocumentFilter.AUTO_ENHANCE,
    val autoCaptureEnabled: Boolean = false,
    val gridOverlayEnabled: Boolean = true,
    val batterySaver: BatterySaverProfile = BatterySaverProfile(),
    val totalStorageBytes: Long = 0L
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val repository: ScanMateRepository,
    batteryStatusMonitor: BatteryStatusMonitor
) : ViewModel() {

    private val storageBytes = MutableStateFlow(0L)

    private val appDefaultsFlow = combine(
        preferencesManager.themeMode,
        preferencesManager.defaultPaperSize,
        preferencesManager.defaultPdfQuality,
        preferencesManager.defaultFilter
    ) { theme, paper, quality, filter ->
        SettingsUiState(
            themeMode = theme,
            defaultPaperSize = paper,
            defaultPdfQuality = quality,
            defaultFilter = filter
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        appDefaultsFlow,
        preferencesManager.autoCaptureEnabled,
        preferencesManager.gridOverlayEnabled,
        batteryStatusMonitor.batterySaverProfile,
        storageBytes
    ) { base, autoCap, grid, powerProfile, bytes ->
        base.copy(
            autoCaptureEnabled = autoCap,
            gridOverlayEnabled = grid,
            batterySaver = powerProfile,
            totalStorageBytes = bytes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            storageBytes.value = repository.getTotalStorageBytes()
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { preferencesManager.setThemeMode(mode) } }
    fun setDefaultPaperSize(size: PaperSize) { viewModelScope.launch { preferencesManager.setDefaultPaperSize(size) } }
    fun setDefaultPdfQuality(quality: PdfQuality) { viewModelScope.launch { preferencesManager.setDefaultPdfQuality(quality) } }
    fun setDefaultFilter(filter: DocumentFilter) { viewModelScope.launch { preferencesManager.setDefaultFilter(filter) } }
    fun setAutoCapture(enabled: Boolean) { viewModelScope.launch { preferencesManager.setAutoCaptureEnabled(enabled) } }
    fun setGridOverlay(enabled: Boolean) { viewModelScope.launch { preferencesManager.setGridOverlayEnabled(enabled) } }
    fun setBatterySaverAutoEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBatterySaverAutoEnabled(enabled) }
    }
    fun setBatterySaverAlwaysOn(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBatterySaverAlwaysOn(enabled) }
    }
}
