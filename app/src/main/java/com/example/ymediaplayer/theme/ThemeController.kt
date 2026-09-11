package com.example.ymediaplayer.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode(val displayName: String) {
    DARK_GREY("Dark Grey"),
    SOLID_BLACK("Solid Black & Grey"),
    MILK_WHITE("Solid Milk White"),
    LESS_WHITE("Less White");

    companion object {
        fun fromString(name: String?): ThemeMode {
            return when (name?.uppercase()) {
                "DARK_GREY" -> DARK_GREY
                "SOLID_BLACK" -> SOLID_BLACK
                "MILK_WHITE" -> MILK_WHITE
                "LESS_WHITE" -> LESS_WHITE
                "DARK" -> DARK_GREY
                "LIGHT" -> LESS_WHITE
                "SYSTEM" -> DARK_GREY
                else -> DARK_GREY
            }
        }
    }
}

/**
 * Holds the current [ThemeMode] and persists it across launches.
 * Exposed to the composition through [LocalThemeController].
 */
class ThemeController(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)

    var mode by mutableStateOf(
        ThemeMode.fromString(prefs.getString(KEY, ThemeMode.DARK_GREY.name))
    )
        private set

    var colorTheme by mutableStateOf(
        com.example.ymediaplayer.ui.PlayerTheme.fromId(
            prefs.getString("player_theme", "CYBER") ?: "CYBER"
        )
    )
        private set

    fun updateMode(newMode: ThemeMode) {
        mode = newMode
        prefs.edit().putString(KEY, newMode.name).apply()
    }

    /** Cycle through the 4 theme modes one by one: Dark Grey -> Solid Black & Grey -> Solid Milk White -> Less White */
    fun cycleThemeMode(): ThemeMode {
        val allModes = ThemeMode.entries
        val currentIndex = allModes.indexOf(mode).takeIf { it >= 0 } ?: 0
        val nextMode = allModes[(currentIndex + 1) % allModes.size]
        updateMode(nextMode)
        return nextMode
    }

    fun updateColorTheme(newTheme: com.example.ymediaplayer.ui.PlayerTheme) {
        colorTheme = newTheme
        prefs.edit().putString("player_theme", newTheme.id).apply()
    }

    /** Cycle through all available color themes one by one. */
    fun cycleColorTheme(): com.example.ymediaplayer.ui.PlayerTheme {
        val allThemes = com.example.ymediaplayer.ui.PlayerTheme.entries
        val currentIndex = allThemes.indexOf(colorTheme).takeIf { it >= 0 } ?: 0
        val nextTheme = allThemes[(currentIndex + 1) % allThemes.size]
        updateColorTheme(nextTheme)
        return nextTheme
    }

    /** Convenience: cycles to next theme mode */
    fun toggle(systemDark: Boolean = true): ThemeMode {
        return cycleThemeMode()
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("ThemeController not provided")
}

@Composable
fun rememberThemeController(): ThemeController {
    val context = LocalContext.current
    return remember { ThemeController(context) }
}
