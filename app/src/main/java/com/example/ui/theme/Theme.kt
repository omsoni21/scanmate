package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val ScanMateDarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = Color(0xFF0A1033),
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = OnIndigoContainerDark,
    secondary = ElectricBlueDark,
    onSecondary = Color(0xFF061B47),
    secondaryContainer = ElectricBlueContainerDark,
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = TealAccentDark,
    onTertiary = Color(0xFF022C28),
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = DeepNavyBackground,
    onBackground = TextPrimaryDark,
    surface = DeepNavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DeepNavySurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = DeepNavySurface,
    surfaceContainer = DeepNavySurfaceElevated,
    surfaceContainerHigh = DeepNavySurfaceHighest,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorRedDark,
    errorContainer = ErrorContainerDark
)

private val ScanMateLightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = OnIndigoContainerLight,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = ElectricBlueContainerLight,
    onSecondaryContainer = OnElectricBlueContainerLight,
    tertiary = TealAccent,
    onTertiary = Color.White,
    tertiaryContainer = TealContainerLight,
    onTertiaryContainer = OnTealContainerLight,
    background = SoftWhiteBackground,
    onBackground = TextPrimaryLight,
    surface = CrispSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = CrispSurfaceLight,
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorRedLight,
    errorContainer = ErrorContainerLight
)

val ScanMateShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ScanMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ScanMateDarkColorScheme
        else -> ScanMateLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScanMateTypography,
        shapes = ScanMateShapes,
        content = content
    )
}
