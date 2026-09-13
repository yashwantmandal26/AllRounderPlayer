package com.example.ymediaplayer.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-wide semantic color palette. Every screen reads colors from
 * [LocalAppColors] so the whole UI reacts to the light/dark switch.
 */
data class AppColors(
    val isDark: Boolean,
    val isMatte: Boolean = false,
    val baseBackground: Color,
    val gradientBlob1: Color,
    val gradientBlob2: Color,
    val glassBg: Color,
    val glassBgNested: Color,
    val glassBorder: Color,
    val topBarScrim: Color,
    val navBarScrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val onAccent: Color,
    val dropdownBg: Color,
    val cardBg: Color = if (isDark) Color(0xFF141520) else Color(0xFFFFFFFF),
    val cardBgElevated: Color = if (isDark) Color(0xFF1E2030) else Color(0xFFF7F8FC),
    val cardBorderHighlight: Color = if (isDark) Color(0x66FFFFFF) else Color(0x99FFFFFF),
    val cardBorderShadow: Color = if (isDark) Color(0x99000000) else Color(0x25000000),
    val cardShadowColor: Color = if (isDark) Color(0xD9000000) else Color(0x30000000),
)

/** 1. OLED Black: Pure deep pitch black background, sleek dark cards, maximum AMOLED battery efficiency */
val OledBlackAppColors = AppColors(
    isDark = true,
    isMatte = false,
    baseBackground = Color(0xFF000000),      // True pitch black (#000000)
    gradientBlob1 = Color(0xFF181B26),       // Ambient glow
    gradientBlob2 = Color(0xFF12141D),       // Ambient glow
    glassBg = Color(0xFF0D0E14),             // Deep sleek card surface
    glassBgNested = Color(0xFF171922),       // Elevated card surface
    glassBorder = Color(0xFF222530),         // Subtle border
    topBarScrim = Color(0xF2000000),         // Pure black scrim
    navBarScrim = Color(0xF6000000),         // Pure black scrim
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.72f),
    textHint = Color.White.copy(alpha = 0.45f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF30D158),
    onAccent = Color.White,
    dropdownBg = Color(0xFF0E0F16),
    cardBg = Color(0xFF0D0E14),              // Dark card
    cardBgElevated = Color(0xFF171922),      // Elevated dark card
    cardBorderHighlight = Color(0xFF2A2E3D),
    cardBorderShadow = Color(0xFF000000),
    cardShadowColor = Color.Black.copy(alpha = 0.5f),
)

/** 2. Slate Grey: Soft, low-contrast dark slate grey background with rich elevated cards */
val SlateGreyAppColors = AppColors(
    isDark = true,
    isMatte = false,
    baseBackground = Color(0xFF13141B),      // Soft dark slate grey (#13141B)
    gradientBlob1 = Color(0xFF252838),       // Ambient glow
    gradientBlob2 = Color(0xFF1E212E),       // Ambient glow
    glassBg = Color(0xFF1F222E),             // Elevated slate card surface
    glassBgNested = Color(0xFF2B2E3E),       // Elevated surface
    glassBorder = Color(0xFF333748),         // Clean subtle slate border
    topBarScrim = Color(0xF213141B),
    navBarScrim = Color(0xF613141B),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.72f),
    textHint = Color.White.copy(alpha = 0.45f),
    accentBlue = Color(0xFF2F80ED),
    accentGreen = Color(0xFF27AE60),
    onAccent = Color.White,
    dropdownBg = Color(0xFF1F222E),
    cardBg = Color(0xFF1F222E),              // Slate grey card
    cardBgElevated = Color(0xFF2B2E3E),      // Elevated slate card
    cardBorderHighlight = Color(0xFF3D4256),
    cardBorderShadow = Color(0xFF0C0D12),
    cardShadowColor = Color.Black.copy(alpha = 0.40f),
)

/** 3. Clean Light: Crisp modern light theme with pure white cards and high-contrast typography */
val CleanLightAppColors = AppColors(
    isDark = false,
    isMatte = true,
    baseBackground = Color(0xFFF6F8FC),      // Crisp clean light background
    gradientBlob1 = Color(0xFFE5ECF8),       // Subtle clean ambient tint
    gradientBlob2 = Color(0xFFDFE7F5),       // Subtle clean ambient tint
    glassBg = Color(0xFFFFFFFF),             // Pure white card surface
    glassBgNested = Color(0xFFF0F3F9),       // Elevated crisp card surface
    glassBorder = Color(0xFFE2E6EE),         // Crisp light border
    topBarScrim = Color(0xF8F6F8FC),
    navBarScrim = Color(0xF8F6F8FC),
    textPrimary = Color(0xFF0F172A),         // High-contrast slate text
    textSecondary = Color(0xFF475569),       // Readable secondary text
    textHint = Color(0xFF94A3B8),            // Clear hint text
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF16A34A),
    onAccent = Color.White,
    dropdownBg = Color(0xFFFFFFFF),
    cardBg = Color(0xFFFFFFFF),              // Pure white card
    cardBgElevated = Color(0xFFF0F3F9),      // Elevated clean light card
    cardBorderHighlight = Color(0xFFE2E6EE),
    cardBorderShadow = Color(0xFFCBD5E1),
    cardShadowColor = Color(0x14000000),     // Subtle soft drop shadow
)

/** Backward compatible aliases */
val SolidDarkAppColors = OledBlackAppColors
val SolidBlackAppColors = OledBlackAppColors
val DarkGreyAppColors = SlateGreyAppColors
val SolidWhiteAppColors = CleanLightAppColors
val MilkWhiteAppColors = CleanLightAppColors
val LessWhiteAppColors = CleanLightAppColors
val DarkAppColors = SlateGreyAppColors
val LightAppColors = CleanLightAppColors

/** Defaults to OLED Black. */
val LocalAppColors = compositionLocalOf { OledBlackAppColors }
