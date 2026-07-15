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
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
  }

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val baseAppColors = if (darkTheme) DarkAppColors else LightAppColors
  
  // Inject dynamic accent color if enabled
  val appColors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    baseAppColors.copy(
        accentBlue = colorScheme.primary,
        onAccent = colorScheme.onPrimary
    )
  } else {
    baseAppColors
  }

  CompositionLocalProvider(LocalAppColors provides appColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
