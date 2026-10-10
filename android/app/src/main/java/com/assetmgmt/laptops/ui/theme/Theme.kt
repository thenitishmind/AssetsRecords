package com.assetmgmt.laptops.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ---- Brand ----
val Navy900 = Color(0xFF0F1F4D)
val Indigo700 = Color(0xFF1E3A8A)
val Indigo500 = Color(0xFF3B5BDB)
val Sky400 = Color(0xFF38BDF8)
val Surface = Color(0xFFF6F8FC)

// ---- Status colours (consistent everywhere) ----
val StatusAvailable = Color(0xFF16A34A) // green
val StatusAssigned = Color(0xFF2563EB)  // blue
val StatusRepair = Color(0xFFEA580C)    // orange
val StatusDead = Color(0xFFDC2626)      // red
val StatusRetired = Color(0xFF6B7280)   // grey

private val LightColors = lightColorScheme(
    primary = Indigo700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Navy900,
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF4FC),
    tertiary = StatusRepair,
    background = Surface,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEAEFF8),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusDead,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Navy900,
    primaryContainer = Color(0xFF26407F),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFF67D4EE),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF111A2E),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1B2640),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun AssetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

fun statusColor(status: String?): Color = when (status?.uppercase()) {
    "AVAILABLE", "ACTIVE", "REPAIRED", "RETURNED" -> StatusAvailable
    "ASSIGNED" -> StatusAssigned
    "REPAIR", "SENT_FOR_REPAIR", "UNDER_REPAIR" -> StatusRepair
    "DEAD", "NOT_REPAIRABLE" -> StatusDead
    "SOLD", "RETIRED", "INACTIVE", "RELEASED", "NO_LAPTOP_ASSIGNED" -> StatusRetired
    else -> StatusRetired
}
