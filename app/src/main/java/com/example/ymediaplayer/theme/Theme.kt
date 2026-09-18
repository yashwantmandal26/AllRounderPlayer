package com.example.ymediaplayer.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

/**
 * Resolves [themeMode] into an effective dark/light state, exposes both a Material3
 * [MaterialTheme] and the app's semantic [LocalAppColors] palette so every screen
 * reacts to the theme switch without visual jitter or sudden color jumps.
 */
@Composable
fun YMediaPlayerTheme(
  themeMode: ThemeMode = ThemeMode.OLED_BLACK,
  colorTheme: com.example.ymediaplayer.ui.PlayerTheme = com.example.ymediaplayer.ui.PlayerTheme.CYBER,
  content: @Composable () -> Unit,
) {
  val baseAppColors = when (themeMode) {
    ThemeMode.OLED_BLACK -> OledBlackAppColors
    ThemeMode.SLATE_GREY -> SlateGreyAppColors
    ThemeMode.CLEAN_LIGHT -> CleanLightAppColors
  }
  val isDark = baseAppColors.isDark

  // Smoothly interpolate theme accents with hardware-accelerated easing to eliminate visual pop and jitter
  val animSpec = tween<androidx.compose.ui.graphics.Color>(240, easing = FastOutSlowInEasing)
  val animAccentBlue by animateColorAsState(targetValue = colorTheme.primaryAccent, animationSpec = animSpec, label = "accentBlue")
  val animAccentGreen by animateColorAsState(targetValue = colorTheme.secondaryAccent, animationSpec = animSpec, label = "accentGreen")

  val targetBlob1 = if (isDark) colorTheme.primaryAccent.copy(alpha = 0.22f) else colorTheme.primaryAccent.copy(alpha = 0.14f)
  val targetBlob2 = if (isDark) colorTheme.secondaryAccent.copy(alpha = 0.18f) else colorTheme.secondaryAccent.copy(alpha = 0.12f)
  val animBlob1 by animateColorAsState(targetValue = targetBlob1, animationSpec = animSpec, label = "blob1")
  val animBlob2 by animateColorAsState(targetValue = targetBlob2, animationSpec = animSpec, label = "blob2")

  // Apply selected color theme to app accents & ambient gradient blobs (subtle theme colours in bg)
  val appColors = baseAppColors.copy(
      accentBlue = animAccentBlue,
      accentGreen = animAccentGreen,
      onAccent = androidx.compose.ui.graphics.Color.White,
      gradientBlob1 = animBlob1,
      gradientBlob2 = animBlob2
  )

  val colorScheme = if (isDark) {
    DarkColorScheme.copy(primary = animAccentBlue, secondary = animAccentGreen)
  } else {
    LightColorScheme.copy(primary = animAccentBlue, secondary = animAccentGreen)
  }

  CompositionLocalProvider(LocalAppColors provides appColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
