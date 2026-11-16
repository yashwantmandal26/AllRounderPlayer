package com.example.ymediaplayer.ui

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ymediaplayer.player.currentHapticRoute
import kotlin.math.roundToInt

@Composable
internal fun rememberHapticRoute(manager: AudioManager): String {
    var route by remember(manager) { mutableStateOf(currentHapticRoute(manager)) }
    DisposableEffect(manager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { route = currentHapticRoute(manager) }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { route = currentHapticRoute(manager) }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAudioDeviceCallback(callback) }
    }
    return route
}

@Composable
internal fun HapticTuningDialog(
    route: String,
    initialStrength: Float,
    initialOffsetMs: Long,
    onApply: (Float, Long) -> Unit,
    onDismiss: () -> Unit,
    mediaLabel: String = "video",
    defaultOffsetMs: Long = 0L
) {
    var strength by remember(route) { mutableFloatStateOf(initialStrength) }
    var offset by remember(route) { mutableLongStateOf(initialOffsetMs) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Haptic tuning") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (route) { "bluetooth" -> "Bluetooth earphones"; "headphones" -> "Wired headphones"; else -> "Phone speaker" })
                Text("Intensity follows the audio automatically. Adjust its overall strength here.")
                Text("Strength: ${(strength * 100).roundToInt()}%")
                Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.25f..1.5f,
                    onValueChangeFinished = { onApply(strength, offset) })
                Text("Sync: ${if (offset > 0) "+" else ""}$offset ms")
                Text("Negative: vibration earlier. Positive: vibration later. Tune while your $mediaLabel plays.")
                Slider(value = offset.toFloat(), onValueChange = { offset = ((it / 5).roundToInt() * 5).toLong() },
                    valueRange = -250f..250f, steps = 99,
                    onValueChangeFinished = { onApply(strength, offset) })
                Text("Saved separately for Bluetooth, wired headphones and speakers.")
            }
        },
        confirmButton = { TextButton(onClick = { onApply(strength, offset); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { strength = 1f; offset = defaultOffsetMs; onApply(strength, offset) }) { Text("Reset") } }
    )
}
