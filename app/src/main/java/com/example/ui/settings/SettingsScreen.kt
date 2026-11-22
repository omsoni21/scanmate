package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.BatterySaverProfile
import com.example.domain.model.BatteryStatus
import com.example.domain.model.DocumentFilter
import com.example.domain.model.OcrLanguage
import com.example.domain.model.PaperSize
import com.example.domain.model.PdfQuality
import com.example.domain.model.ThemeMode
import com.example.ui.components.formatFileSize

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshStorage() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsSectionCard(
                    title = "Appearance",
                    icon = Icons.Rounded.DarkMode
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size)
                            ) {
                                Text(mode.label.substringBefore(" ("))
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Scanner & Camera",
                    icon = Icons.Rounded.CameraAlt
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Capture Stable Pages", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Automatically captures when detected edges hold steady",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.autoCaptureEnabled,
                            onCheckedChange = viewModel::setAutoCapture
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rule-of-Thirds Grid Overlay", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Helps align documents cleanly in viewfinder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.gridOverlayEnabled,
                            onCheckedChange = viewModel::setGridOverlay
                        )
                    }
                }
            }

            item {
                BatterySaverSettingsCard(
                    profile = state.batterySaver,
                    onToggleAuto = viewModel::setBatterySaverAutoEnabled,
                    onToggleAlwaysOn = viewModel::setBatterySaverAlwaysOn
                )
            }

            item {
                SettingsSectionCard(
                    title = "Default Enhancement Filter",
                    icon = Icons.Rounded.AutoFixHigh
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.defaultFilter == filter,
                                onClick = { viewModel.setDefaultFilter(filter) },
                                label = { Text(filter.label) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "PDF Export Defaults",
                    icon = Icons.Rounded.PictureAsPdf
                ) {
                    Text("Paper Size", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaperSize.entries.forEach { size ->
                            FilterChip(
                                selected = state.defaultPaperSize == size,
                                onClick = { viewModel.setDefaultPaperSize(size) },
                                label = { Text(size.label.substringBefore(" (")) }
                            )
                        }
                    }

                    Text("PDF Quality", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = state.defaultPdfQuality == quality,
                                onClick = { viewModel.setDefaultPdfQuality(quality) },
                                label = { Text(quality.label.substringBefore(" (")) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Offline OCR Language Packs",
                    icon = Icons.Rounded.TextFields
                ) {
                    OcrLanguage.entries.forEach { lang ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lang.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        lang.statusNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (lang.isBundled) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = if (lang.isBundled) "Bundled" else "Unavailable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lang.isBundled) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Local Vault & Privacy",
                    icon = Icons.Rounded.Storage
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Used Local Storage", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = formatFileSize(state.totalStorageBytes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "ScanMate operates 100% offline. No accounts, telemetry, or cloud uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatterySaverSettingsCard(
    profile: BatterySaverProfile,
    onToggleAuto: (Boolean) -> Unit,
    onToggleAlwaysOn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        title = "Battery Saver",
        icon = Icons.Rounded.BatterySaver,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (profile.isActive) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battery_saver_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (profile.isActive) "Power-Saving Active" else "Standard Performance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (profile.isActive) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = "Device battery: ${profile.batteryPercent}% • Max scan ${profile.maxScanDimensionPx}px • ${(profile.enhancementIntensity * 100).toInt()}% enhance intensity",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.isActive) {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (profile.isActive) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier.testTag("battery_saver_status_badge")
                ) {
                    Text(
                        text = if (profile.isActive) "Active" else "Standby",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (profile.isActive) {
                            MaterialTheme.colorScheme.onTertiary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "Auto Battery Saver (< ${BatteryStatus.LOW_BATTERY_THRESHOLD_PERCENT}%)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Automatically lowers scan resolution and filter processing intensity when battery drops below 20%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = profile.autoEnabled,
                onCheckedChange = onToggleAuto,
                modifier = Modifier.testTag("battery_saver_auto_switch")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "Always Use Battery Saver",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Keep reduced scan resolution (1080px) and gentle filter intensity on regardless of battery level",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = profile.alwaysOnEnabled,
                onCheckedChange = onToggleAlwaysOn,
                modifier = Modifier.testTag("battery_saver_force_switch")
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
