package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object OnboardingRoute

@Serializable
data object MainTabsRoute

@Serializable
data class CameraScannerRoute(
    val folderId: String = "personal",
    val appendToCurrentSession: Boolean = false
)

@Serializable
data object ScanSessionRoute

@Serializable
data class PageCropRoute(val pageId: String)

@Serializable
data class PageEditorRoute(val pageId: String)

@Serializable
data class PdfViewerRoute(val documentId: Long)

@Serializable
data class FolderDetailRoute(val folderId: String)
