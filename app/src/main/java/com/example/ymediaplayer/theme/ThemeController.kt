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
    OLED_BLACK("OLED Black"),
    SLATE_GREY("Slate Grey"),
    CLEAN_LIGHT("Clean Light");

    companion object {
        val SOLID_DARK get() = OLED_BLACK
        val SOLID_BLACK get() = OLED_BLACK
        val DARK_GREY get() = SLATE_GREY
        val SOLID_WHITE get() = CLEAN_LIGHT
        val MILK_WHITE get() = CLEAN_LIGHT
        val LESS_WHITE get() = CLEAN_LIGHT

        fun fromString(name: String?): ThemeMode {
            return when (name?.uppercase()) {
                "OLED_BLACK", "SOLID_BLACK", "SOLID_DARK" -> OLED_BLACK
                "SLATE_GREY", "DARK_GREY" -> SLATE_GREY
                "CLEAN_LIGHT", "SOLID_WHITE", "MILK_WHITE", "LESS_WHITE", "LIGHT" -> CLEAN_LIGHT
                "DARK" -> OLED_BLACK
                "SYSTEM" -> SLATE_GREY
                else -> OLED_BLACK
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
        ThemeMode.fromString(prefs.getString(KEY, ThemeMode.OLED_BLACK.name))
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

    /** Cycle through the 3 theme modes one by one: OLED Black -> Slate Grey -> Clean Light */
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
