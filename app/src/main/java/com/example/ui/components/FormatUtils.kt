package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatShortDate(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return fmt.format(Date(millis))
}

data class FolderIconOption(val key: String, val label: String, val icon: ImageVector)

val AvailableFolderIcons = listOf(
    FolderIconOption("folder", "Folder", Icons.Rounded.Folder),
    FolderIconOption("person", "Personal", Icons.Rounded.Person),
    FolderIconOption("school", "Education", Icons.Rounded.School),
    FolderIconOption("work", "Office", Icons.Rounded.Work),
    FolderIconOption("receipt_long", "Bills", Icons.AutoMirrored.Rounded.ReceiptLong),
    FolderIconOption("receipt", "Receipts", Icons.Rounded.Receipt),
    FolderIconOption("badge", "Identity", Icons.Rounded.Badge),
    FolderIconOption("finance", "Finance", Icons.Rounded.AccountBalance),
    FolderIconOption("home", "Home", Icons.Rounded.Home),
    FolderIconOption("medical", "Health", Icons.Rounded.MedicalServices),
    FolderIconOption("star", "Important", Icons.Rounded.Star),
    FolderIconOption("docs", "Docs", Icons.Rounded.Description)
)

fun folderIconForKey(key: String): ImageVector =
    AvailableFolderIcons.find { it.key == key }?.icon ?: Icons.Rounded.Folder
