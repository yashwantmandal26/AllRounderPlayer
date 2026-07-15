package com.example.ymediaplayer.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Holds the current [ThemeMode] and persists it across launches.
 * Exposed to the composition through [LocalThemeController].
 */
class ThemeController(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)

    var mode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    )
        private set

    fun updateMode(newMode: ThemeMode) {
        mode = newMode
        prefs.edit().putString(KEY, newMode.name).apply()
    }

    /** Convenience: flip between light and dark (resolving SYSTEM against [systemDark]). */
    fun toggle(systemDark: Boolean) {
        val currentlyDark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        updateMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
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
