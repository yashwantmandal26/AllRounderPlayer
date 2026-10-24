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

/** 2. Dark Grey (Comfort): Eye-comfort soothing dark grey (#16171E) where OLED pixels stay gently lit to avoid glare and smearing */
val SlateGreyAppColors = AppColors(
    isDark = true,
    isMatte = false,
    baseBackground = Color(0xFF16171E),      // Eye-comfort soothing dark grey (#16171E)
    gradientBlob1 = Color(0xFF232738),       // Soothing ambient glow
    gradientBlob2 = Color(0xFF1C1F2D),       // Soothing ambient glow
    glassBg = Color(0xFF20222D),             // Comfortable dark grey card surface
    glassBgNested = Color(0xFF292C3A),       // Elevated comfortable surface
    glassBorder = Color(0xFF323648),         // Clean subtle slate border
    topBarScrim = Color(0xF216171E),
    navBarScrim = Color(0xF616171E),
    textPrimary = Color(0xFFE8EAF2),         // Softened off-white to prevent night eye-strain
    textSecondary = Color(0xFFA2A7BC),       // Gentle readable secondary text
    textHint = Color(0xFF6B7288),            // Muted hint text
    accentBlue = Color(0xFF3B82F6),
    accentGreen = Color(0xFF10B981),
    onAccent = Color.White,
    dropdownBg = Color(0xFF20222D),
    cardBg = Color(0xFF20222D),              // Dark comfort card
    cardBgElevated = Color(0xFF292C3A),      // Elevated dark comfort card
    cardBorderHighlight = Color(0xFF383C50),
    cardBorderShadow = Color(0xFF101117),
    cardShadowColor = Color(0x66000000),
)

/** 3. Clean Light: Multi-shade white theme with pure white cards, soft milk-white canvas, and rich ambient pastels */
val CleanLightAppColors = AppColors(
    isDark = false,
    isMatte = false,
    baseBackground = Color(0xFFF7F8FC),      // Shade 1: Soft milk/alabaster canvas
    gradientBlob1 = Color(0xFFE5ECF8),       // Ambient pastel tint
    gradientBlob2 = Color(0xFFDFE7F5),       // Ambient pastel tint
    glassBg = Color(0xFFFFFFFF),             // Shade 2: Pure brilliant white cards
    glassBgNested = Color(0xFFEDF1F7),       // Shade 3: Cool frost white elevated surfaces/pills
    glassBorder = Color(0xFFE2E7F0),         // Clean hairline light border
    topBarScrim = Color(0xF8F7F8FC),
    navBarScrim = Color(0xF8F7F8FC),
    textPrimary = Color(0xFF0F172A),         // High-contrast slate charcoal text
    textSecondary = Color(0xFF475569),       // Readable slate secondary text
    textHint = Color(0xFF94A3B8),            // Clear hint text
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF16A34A),
    onAccent = Color.White,
    dropdownBg = Color(0xFFFFFFFF),
    cardBg = Color(0xFFFFFFFF),              // Pure brilliant white card
    cardBgElevated = Color(0xFFEDF1F7),      // Elevated cool frost white card
    cardBorderHighlight = Color(0xFFEDF2F7),
    cardBorderShadow = Color(0xFFCBD5E1),
    cardShadowColor = Color(0x180F172A),     // Soft ambient slate drop shadow
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
