package com.example.ymediaplayer.ui

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.service.MusicService
import com.example.ymediaplayer.theme.LocalAppColors

/** Small beat toggle beside Favorite; long press opens route-specific calibration. */
@Composable
internal fun MusicHapticsControl() {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val preferences = remember(context) { AppPreferences(context) }
    val enabled by MusicService.isMusicHapticsEnabled
    val fallback by MusicService.isUsingFallbackEngine
    var showTuning by remember { mutableStateOf(false) }
    val manager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val route = rememberHapticRoute(requireNotNull(manager))
    val available = remember(context) {
        try { context.getSystemService(Vibrator::class.java)?.hasVibrator() == true }
        catch (_: RuntimeException) { false }
    }
    // Also refresh when settings are reset while the music service is not running.
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)
        fun refresh() { MusicService.isMusicHapticsEnabled.value = AppPreferences(context).isMusicHapticsEnabled() }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "music_haptics_enabled") refresh()
        }
        refresh()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape)
            .background(if (enabled) c.accentBlue.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (enabled) c.accentBlue.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
            .combinedClickable(enabled = available, role = Role.Switch,
                onClick = {
                    MusicService.setMusicHapticsEnabled(context, !enabled)
                    if (fallback && !enabled) Toast.makeText(context, "Haptics resume with standard playback", Toast.LENGTH_SHORT).show()
                },
                onLongClickLabel = "Tune music haptics",
                onLongClick = { showTuning = true })
            .testTag("music_beat_haptics").semantics {
                contentDescription = "Beat haptics"
                stateDescription = if (!available) "Unavailable" else if (enabled) "On" else "Off"
                toggleableState = ToggleableState(enabled)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Vibration, contentDescription = null,
            tint = if (enabled) c.accentBlue else c.textSecondary.copy(alpha = if (available) 0.8f else 0.35f),
            modifier = Modifier.size(22.dp))
    }
    if (showTuning) {
        val tuning = preferences.getMusicHapticTuning(route)
        HapticTuningDialog(route, tuning.strength, tuning.syncMs,
            onApply = { strength, offset -> preferences.setMusicHapticTuning(route, tuning.copy(strength = strength, syncMs = offset)) },
            onDismiss = { showTuning = false }, mediaLabel = "song", defaultOffsetMs = -20L)
    }
}
