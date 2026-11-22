package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.folders.FoldersScreen
import com.example.ui.home.HomeScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.settings.SettingsScreen

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    LIBRARY("Library", Icons.AutoMirrored.Rounded.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    FOLDERS("Folders", Icons.Rounded.Folder, Icons.Outlined.Folder),
    SETTINGS("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    onStartNewScan: () -> Unit,
    onOpenScanSession: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenFolder: (String) -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                MainTab.HOME -> "ScanMate"
                                MainTab.LIBRARY -> "Document Library"
                                MainTab.FOLDERS -> "Folders"
                                MainTab.SETTINGS -> "Settings"
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentTab == MainTab.HOME || currentTab == MainTab.LIBRARY) {
                    ExtendedFloatingActionButton(
                        onClick = onStartNewScan,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Rounded.DocumentScanner, contentDescription = null) },
                        text = { Text("New Scan") },
                        modifier = Modifier.testTag("fab_new_scan")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        header = {
                            FloatingActionButton(
                                onClick = onStartNewScan,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.DocumentScanner, contentDescription = "New Scan")
                            }
                        }
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        MainTab.HOME -> HomeScreen(
                            onScanDocument = onStartNewScan,
                            onOpenScanSession = onOpenScanSession,
                            onBrowseDocuments = { currentTab = MainTab.LIBRARY },
                            onBrowseFolders = { currentTab = MainTab.FOLDERS },
                            onOpenDocument = onOpenDocument,
                            onOpenFolder = onOpenFolder,
                            onOpenSettings = { currentTab = MainTab.SETTINGS }
                        )
                        MainTab.LIBRARY -> LibraryScreen(
                            onOpenDocument = onOpenDocument,
                            onScanDocument = onStartNewScan,
                            onOpenScanSession = onOpenScanSession
                        )
                        MainTab.FOLDERS -> FoldersScreen(
                            onOpenFolder = onOpenFolder
                        )
                        MainTab.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
    }
}
