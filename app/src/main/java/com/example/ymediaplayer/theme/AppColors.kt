package com.example.ymediaplayer.theme

import androidx.compose.runtime.staticCompositionLocalOf
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
)

val DarkAppColors = AppColors(
    isDark = true,
    baseBackground = Color(0xFF070709),
    gradientBlob1 = Color(0xFF2A0845),
    gradientBlob2 = Color(0xFF0D1B2A),
    glassBg = Color.White.copy(alpha = 0.06f),
    glassBgNested = Color.White.copy(alpha = 0.03f),
    glassBorder = Color.White.copy(alpha = 0.12f),
    topBarScrim = Color.Black.copy(alpha = 0.4f),
    navBarScrim = Color.Black.copy(alpha = 0.5f),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color.White.copy(alpha = 0.6f),
    textHint = Color.White.copy(alpha = 0.4f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF32D74B),
    onAccent = Color.White,
    dropdownBg = Color(0xFF2C2C2E),
)

val LightAppColors = AppColors(
    isDark = false,
    baseBackground = Color(0xFFF2F3F7),
    gradientBlob1 = Color(0xFFD9C7FF),
    gradientBlob2 = Color(0xFFC3D9F5),
    glassBg = Color.Black.copy(alpha = 0.04f),
    glassBgNested = Color.Black.copy(alpha = 0.02f),
    glassBorder = Color.Black.copy(alpha = 0.10f),
    topBarScrim = Color.White.copy(alpha = 0.55f),
    navBarScrim = Color.White.copy(alpha = 0.7f),
    textPrimary = Color(0xFF0B0B0F),
    textSecondary = Color.Black.copy(alpha = 0.55f),
    textHint = Color.Black.copy(alpha = 0.4f),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF28A745),
    onAccent = Color.White,
    dropdownBg = Color(0xFFFFFFFF),
)

/** Defaults to dark to match the app's original look before a theme is provided. */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
