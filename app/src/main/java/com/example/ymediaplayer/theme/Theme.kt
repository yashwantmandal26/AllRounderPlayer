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

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

/**
 * Resolves [themeMode] into an effective dark/light state, exposes both a Material3
 * [MaterialTheme] and the app's semantic [LocalAppColors] palette so every screen
 * reacts to the theme switch.
 */
@Composable
fun YMediaPlayerTheme(
  themeMode: ThemeMode = ThemeMode.DARK_GREY,
  colorTheme: com.example.ymediaplayer.ui.PlayerTheme = com.example.ymediaplayer.ui.PlayerTheme.CYBER,
  content: @Composable () -> Unit,
) {
  val baseAppColors = when (themeMode) {
    ThemeMode.DARK_GREY -> DarkGreyAppColors
    ThemeMode.SOLID_BLACK -> SolidBlackAppColors
    ThemeMode.MILK_WHITE -> MilkWhiteAppColors
    ThemeMode.LESS_WHITE -> LessWhiteAppColors
  }
  val isDark = baseAppColors.isDark
  
  // Apply selected color theme to app accents & ambient gradient blobs
  val appColors = baseAppColors.copy(
      accentBlue = colorTheme.primaryAccent,
      onAccent = androidx.compose.ui.graphics.Color.White,
      gradientBlob1 = if (isDark) colorTheme.secondaryAccent.copy(alpha = 0.12f) else colorTheme.secondaryAccent.copy(alpha = 0.18f),
      gradientBlob2 = if (isDark) colorTheme.primaryAccent.copy(alpha = 0.10f) else colorTheme.primaryAccent.copy(alpha = 0.14f)
  )

  val colorScheme = if (isDark) {
    DarkColorScheme.copy(primary = colorTheme.primaryAccent, secondary = colorTheme.secondaryAccent)
  } else {
    LightColorScheme.copy(primary = colorTheme.primaryAccent, secondary = colorTheme.secondaryAccent)
  }

  CompositionLocalProvider(LocalAppColors provides appColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
