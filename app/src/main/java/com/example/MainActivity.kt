package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.ThemeMode
import com.example.ui.MainShellScreen
import com.example.ui.MainViewModel
import com.example.ui.editor.PageCropScreen
import com.example.ui.editor.PageEditorScreen
import com.example.ui.editor.ScanSessionScreen
import com.example.ui.folders.FolderDetailScreen
import com.example.ui.navigation.CameraScannerRoute
import com.example.ui.navigation.FolderDetailRoute
import com.example.ui.navigation.MainTabsRoute
import com.example.ui.navigation.PageCropRoute
import com.example.ui.navigation.PageEditorRoute
import com.example.ui.navigation.PdfViewerRoute
import com.example.ui.navigation.ScanSessionRoute
import com.example.ui.scanner.CameraScannerScreen
import com.example.ui.theme.ScanMateTheme
import com.example.ui.viewer.PdfViewerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()

            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ScanMateTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = MainTabsRoute
                ) {

                    composable<MainTabsRoute> {
                        MainShellScreen(
                            onStartNewScan = {
                                navController.navigate(CameraScannerRoute(folderId = "personal", appendToCurrentSession = false))
                            },
                            onOpenScanSession = {
                                navController.navigate(ScanSessionRoute)
                            },
                            onOpenDocument = { docId ->
                                navController.navigate(PdfViewerRoute(documentId = docId))
                            },
                            onOpenFolder = { folderId ->
                                navController.navigate(FolderDetailRoute(folderId = folderId))
                            }
                        )
                    }

                    composable<CameraScannerRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<CameraScannerRoute>()
                        CameraScannerScreen(
                            folderId = args.folderId,
                            appendToCurrentSession = args.appendToCurrentSession,
                            onBack = { navController.popBackStack() },
                            onOpenCropPage = { pageId ->
                                navController.navigate(PageCropRoute(pageId = pageId))
                            },
                            onOpenSessionReview = {
                                navController.navigate(ScanSessionRoute) {
                                    popUpTo(CameraScannerRoute::class) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<ScanSessionRoute> {
                        ScanSessionScreen(
                            onBack = { navController.popBackStack() },
                            onAddCameraPage = {
                                navController.navigate(CameraScannerRoute(appendToCurrentSession = true))
                            },
                            onEditPage = { pageId ->
                                navController.navigate(PageEditorRoute(pageId = pageId))
                            },
                            onCropPage = { pageId ->
                                navController.navigate(PageCropRoute(pageId = pageId))
                            },
                            onDocumentSaved = { docId ->
                                navController.navigate(PdfViewerRoute(documentId = docId)) {
                                    popUpTo(MainTabsRoute) { inclusive = false }
                                }
                            }
                        )
                    }

                    composable<PageCropRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<PageCropRoute>()
                        PageCropScreen(
                            pageId = args.pageId,
                            fileStorage = mainViewModel.fileStorage,
                            onBack = { navController.popBackStack() },
                            onAppliedGoToEditor = { pageId ->
                                navController.navigate(PageEditorRoute(pageId = pageId)) {
                                    popUpTo(PageCropRoute(pageId)) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<PageEditorRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<PageEditorRoute>()
                        PageEditorScreen(
                            pageId = args.pageId,
                            fileStorage = mainViewModel.fileStorage,
                            onBack = { navController.popBackStack() },
                            onOpenCrop = { pageId ->
                                navController.navigate(PageCropRoute(pageId = pageId))
                            },
                            onDoneToSession = {
                                navController.navigate(ScanSessionRoute) {
                                    popUpTo(MainTabsRoute) { inclusive = false }
                                }
                            }
                        )
                    }

                    composable<PdfViewerRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<PdfViewerRoute>()
                        PdfViewerScreen(
                            documentId = args.documentId,
                            onBack = { navController.popBackStack() },
                            onEditPages = {
                                navController.navigate(ScanSessionRoute)
                            }
                        )
                    }

                    composable<FolderDetailRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<FolderDetailRoute>()
                        FolderDetailScreen(
                            folderId = args.folderId,
                            onBack = { navController.popBackStack() },
                            onScanIntoFolder = { fId ->
                                navController.navigate(CameraScannerRoute(folderId = fId, appendToCurrentSession = false))
                            },
                            onOpenDocument = { docId ->
                                navController.navigate(PdfViewerRoute(documentId = docId))
                            }
                        )
                    }
                }
            }
        }
    }
}
