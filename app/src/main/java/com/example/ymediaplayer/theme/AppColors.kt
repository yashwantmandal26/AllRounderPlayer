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

/** 1. Solid Dark: Dark grey background with grey cards & buttons */
val SolidDarkAppColors = AppColors(
    isDark = true,
    isMatte = false,
    baseBackground = Color(0xFF16171E),      // Dark grey background
    gradientBlob1 = Color(0xFF262935),       // Subtle ambient blob
    gradientBlob2 = Color(0xFF1E202A),       // Subtle ambient blob
    glassBg = Color(0xFF232530),             // Grey card surface
    glassBgNested = Color(0xFF2D303D),       // Elevated grey
    glassBorder = Color(0xFF383B4B),         // Subtle grey border
    topBarScrim = Color(0xF216171E),         // Dark grey scrim
    navBarScrim = Color(0xF616171E),         // Dark grey scrim
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.72f),
    textHint = Color.White.copy(alpha = 0.45f),
    accentBlue = Color(0xFF1565C0),
    accentGreen = Color(0xFF1E824C),
    onAccent = Color.White,
    dropdownBg = Color(0xFF232530),
    cardBg = Color(0xFF232530),              // Grey card
    cardBgElevated = Color(0xFF2D303D),      // Elevated grey for buttons/cards
    cardBorderHighlight = Color(0xFF424658), // Grey border highlight
    cardBorderShadow = Color(0xFF101116),
    cardShadowColor = Color.Black.copy(alpha = 0.35f),
)

/** 2. Solid White: Pure solid flat white background, solid matte cards, ZERO gradients, ZERO glass */
val SolidWhiteAppColors = AppColors(
    isDark = false,
    isMatte = true,
    baseBackground = Color(0xFFFFFFFF),      // Pure solid matte white (no gradient)
    gradientBlob1 = Color.Transparent,       // Zero gradient blob
    gradientBlob2 = Color.Transparent,       // Zero gradient blob
    glassBg = Color(0xFFF6F7F9),             // Solid matte card surface (no glass/blur)
    glassBgNested = Color(0xFFECEEF2),       // Solid elevated matte surface
    glassBorder = Color(0xFFE2E4E9),         // Clean flat matte border
    topBarScrim = Color(0xFFFFFFFF),         // 100% opaque solid white
    navBarScrim = Color(0xFFFFFFFF),         // 100% opaque solid white
    textPrimary = Color(0xFF11141D),
    textSecondary = Color(0xFF4B5565),
    textHint = Color(0xFF9AA4B2),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF16A34A),
    onAccent = Color.White,
    dropdownBg = Color(0xFFFFFFFF),
    cardBg = Color(0xFFF6F7F9),              // Pure flat solid matte card
    cardBgElevated = Color(0xFFECEEF2),      // Solid elevated matte card
    cardBorderHighlight = Color(0xFFE2E4E9), // Clean flat border
    cardBorderShadow = Color(0xFFE2E4E9),
    cardShadowColor = Color(0x08000000),     // Minimal subtle elevation
)

/** Backward compatible aliases */
val SolidBlackAppColors = SolidDarkAppColors
val MilkWhiteAppColors = SolidWhiteAppColors

/** 3. Dark Grey: Dark grey background with grey cards/buttons & subtle theme ambient colors */
val DarkGreyAppColors = AppColors(
    isDark = true,
    isMatte = false,
    baseBackground = Color(0xFF16171E),      // Dark grey background
    gradientBlob1 = Color(0xFF262935),       // Ambient blob
    gradientBlob2 = Color(0xFF1E202A),       // Ambient blob
    glassBg = Color(0xFF232530),             // Grey card surface
    glassBgNested = Color(0xFF2D303D),       // Elevated grey
    glassBorder = Color(0xFF383B4B),         // Subtle grey border
    topBarScrim = Color(0xF216171E),
    navBarScrim = Color(0xF616171E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.72f),
    textHint = Color.White.copy(alpha = 0.45f),
    accentBlue = Color(0xFF1565C0),
    accentGreen = Color(0xFF1E824C),
    onAccent = Color.White,
    dropdownBg = Color(0xFF232530),
    cardBg = Color(0xFF232530),              // Grey card
    cardBgElevated = Color(0xFF2D303D),      // Elevated grey for buttons/cards
    cardBorderHighlight = Color(0xFF424658),
    cardBorderShadow = Color(0xFF101116),
    cardShadowColor = Color.Black.copy(alpha = 0.35f),
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
