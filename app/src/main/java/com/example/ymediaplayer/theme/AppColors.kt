package com.example.ymediaplayer.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-wide semantic color palette. Every screen reads colors from
 * [LocalAppColors] so the whole UI reacts to the light/dark switch.
 */
data class AppColors(
    val isDark: Boolean,
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

/** 1. Dark Grey: Muted dark grey background, elegant graphite cards */
val DarkGreyAppColors = AppColors(
    isDark = true,
    baseBackground = Color(0xFF1A1B22),      // Elegant dark grey background (not black)
    gradientBlob1 = Color(0xFF2C2D3A),       // Muted slate-grey ambient blob
    gradientBlob2 = Color(0xFF22232E),       // Deep grey-slate ambient blob
    glassBg = Color(0xFA252733),             // Sleek dark grey card surface
    glassBgNested = Color(0xFF303242),       // Slightly elevated dark grey
    glassBorder = Color.White.copy(alpha = 0.16f),
    topBarScrim = Color(0xEE1A1B22),
    navBarScrim = Color(0xF421222C),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.72f),
    textHint = Color.White.copy(alpha = 0.45f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF32D74B),
    onAccent = Color.White,
    dropdownBg = Color(0xFF252733),
    cardBg = Color(0xFF232531),              // Dark grey card background
    cardBgElevated = Color(0xFF2E303F),      // Elevated dark grey
    cardBorderHighlight = Color.White.copy(alpha = 0.28f),
    cardBorderShadow = Color.Black.copy(alpha = 0.35f),
    cardShadowColor = Color.Black.copy(alpha = 0.45f),
)

/** 2. Solid Black & Grey: AMOLED true pitch black with solid dark grey cards */
val SolidBlackAppColors = AppColors(
    isDark = true,
    baseBackground = Color(0xFF000000),      // Pitch AMOLED black
    gradientBlob1 = Color(0xFF141416),       // Deep charcoal ambient blob
    gradientBlob2 = Color(0xFF0D0D0F),       // Deep dark ambient blob
    glassBg = Color(0xFF141416),             // Solid dark grey card surface
    glassBgNested = Color(0xFF1E1E22),       // Elevated dark grey
    glassBorder = Color.White.copy(alpha = 0.12f),
    topBarScrim = Color(0xF2000000),
    navBarScrim = Color(0xF8080808),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.65f),
    textHint = Color.White.copy(alpha = 0.40f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF32D74B),
    onAccent = Color.White,
    dropdownBg = Color(0xFF161618),
    cardBg = Color(0xFF161618),              // Solid dark grey card
    cardBgElevated = Color(0xFF202024),      // Elevated dark grey card
    cardBorderHighlight = Color.White.copy(alpha = 0.20f),
    cardBorderShadow = Color.Black.copy(alpha = 0.65f),
    cardShadowColor = Color.Black.copy(alpha = 0.65f),
)

/** 3. Solid Milk White: Crisp pure white background and clean white surfaces */
val MilkWhiteAppColors = AppColors(
    isDark = false,
    baseBackground = Color(0xFFFFFFFF),      // Pure solid milk white
    gradientBlob1 = Color(0xFFF6F7FA),       // Subtle clean ambient tint
    gradientBlob2 = Color(0xFFEFF1F7),       // Subtle clean ambient tint
    glassBg = Color(0xFFFFFFFF),             // Solid milk white cards
    glassBgNested = Color(0xFFF7F8FA),
    glassBorder = Color.Black.copy(alpha = 0.08f),
    topBarScrim = Color.White.copy(alpha = 0.95f),
    navBarScrim = Color.White.copy(alpha = 0.96f),
    textPrimary = Color(0xFF0D0D11),
    textSecondary = Color.Black.copy(alpha = 0.65f),
    textHint = Color.Black.copy(alpha = 0.40f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF28A745),
    onAccent = Color.White,
    dropdownBg = Color(0xFFFFFFFF),
    cardBg = Color(0xFFFFFFFF),              // Pure milk white card
    cardBgElevated = Color(0xFFF8F9FD),      // Elevated milk white card
    cardBorderHighlight = Color.White,
    cardBorderShadow = Color.Black.copy(alpha = 0.08f),
    cardShadowColor = Color.Black.copy(alpha = 0.10f),
)

/** 4. Less White: Muted soft light grey / off-white, easier on eyes */
val LessWhiteAppColors = AppColors(
    isDark = false,
    baseBackground = Color(0xFFE8E9EE),      // Soft muted light grey / off-white
    gradientBlob1 = Color(0xFFDFE1E8),       // Soft muted blob
    gradientBlob2 = Color(0xFFD6D9E2),       // Soft muted blob
    glassBg = Color(0xFFF1F2F6),             // Soft less-white card surface
    glassBgNested = Color(0xFFE2E4EB),
    glassBorder = Color.Black.copy(alpha = 0.14f),
    topBarScrim = Color(0xEEE8E9EE),
    navBarScrim = Color(0xF4E0E2E9),
    textPrimary = Color(0xFF1A1C22),
    textSecondary = Color.Black.copy(alpha = 0.65f),
    textHint = Color.Black.copy(alpha = 0.45f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF28A745),
    onAccent = Color.White,
    dropdownBg = Color(0xFFEFF0F5),
    cardBg = Color(0xFFF1F2F6),              // Less white card
    cardBgElevated = Color(0xFFE5E7EE),
    cardBorderHighlight = Color.White.copy(alpha = 0.85f),
    cardBorderShadow = Color.Black.copy(alpha = 0.12f),
    cardShadowColor = Color.Black.copy(alpha = 0.14f),
)

// Backward compatible aliases
val DarkAppColors = DarkGreyAppColors
val LightAppColors = LessWhiteAppColors

/** Defaults to dark grey. */
val LocalAppColors = compositionLocalOf { DarkGreyAppColors }
