package com.example.ymediaplayer.ui

import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.player.*
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun HapticsSettingsPanel(
    videoEnabled: Boolean, onVideoEnabled: (Boolean) -> Unit,
    touchEnabled: Boolean, onTouchEnabled: (Boolean) -> Unit,
    resetVersion: Int
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val colors = LocalAppColors.current
    val manager = remember { requireNotNull(context.getSystemService(AudioManager::class.java)) }
    val activeRoute = rememberHapticRoute(manager)
    var route by rememberSaveable { mutableStateOf(activeRoute) }
    val editing = remember(route, resetVersion) { mutableStateOf(prefs.getVideoHapticTuning(route)) }
    val config = editing.value
    val latestRevision by rememberUpdatedState(resetVersion)
    var touchStrength by remember(resetVersion) { mutableFloatStateOf(prefs.getTouchHapticStrength()) }
    var live by remember { mutableStateOf(false) }
    var cue by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(HapticPreviewType.MIX) }
    var previewState by remember { mutableStateOf(HapticPreviewState()) }
    val preview = remember { HapticPreviewController(context, { previewState = it }) }
    val owner = remember { Any() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resetVersion) { if (resetVersion > 0) { live = false; preview.stop() } }

    DisposableEffect(editing) {
        val revision = resetVersion
        val profileRoute = route
        onDispose { if (revision == latestRevision) prefs.setVideoHapticTuning(profileRoute, editing.value) }
    }
    LaunchedEffect(config, route) {
        VideoPlaybackManager.applyHapticTuning(route, config)
        delay(180)
        prefs.setVideoHapticTuning(route, config)
    }
    DisposableEffect(live, previewState.busy) {
        VideoPlaybackManager.setHapticPreviewActive(owner, live || previewState.busy)
        onDispose { VideoPlaybackManager.setHapticPreviewActive(owner, false) }
    }
    DisposableEffect(preview, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                live = false; preview.stop(); VideoPlaybackManager.setHapticPreviewActive(owner, false)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer); preview.close()
            VideoPlaybackManager.setHapticPreviewActive(owner, false)
        }
    }
    LaunchedEffect(config, touchStrength, type, live) {
        if (live) {
            VideoPlaybackManager.setHapticPreviewActive(owner, true)
            preview.preview(HapticPreviewSamples.create(type, config, touchStrength, compact = true),
                live = true, touch = type == HapticPreviewType.TOUCH)
        }
    }
    LaunchedEffect(previewState) {
        if (previewState.startedAtMs > 0 && previewState.busy) {
            while (previewState.busy) {
                withFrameNanos { }
                progress = ((SystemClock.elapsedRealtime() - previewState.startedAtMs).toFloat() /
                    (previewState.signal?.durationMs ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        } else progress = if (previewState.signal != null) 1f else 0f
    }
    fun change(next: HapticTuning, sample: HapticPreviewType? = null) {
        editing.value = next.bounded()
        if (sample != null) type = sample
        VideoPlaybackManager.applyHapticTuning(route, editing.value)
    }
    val sample = remember(type, config, touchStrength, live) {
        HapticPreviewSamples.create(type, config, touchStrength, compact = live)
    }
    val displayed = if (previewState.busy) previewState.signal ?: sample else sample

    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HapticSettingSwitch("Movie haptics", "Automatic audio-reactive effects", videoEnabled, onVideoEnabled)
        HapticSettingSwitch("Touch feedback", "Buttons, gestures and sliders", touchEnabled, onTouchEnabled)
        Text("Profile", color = colors.textPrimary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("speakers" to "Speaker", "headphones" to "Wired", "bluetooth" to "Bluetooth").forEach { (key, label) ->
                FilterChip(selected = route == key, onClick = { preview.stop(); route = key }, label = { Text(label) })
            }
        }
        Text("Saved separately for each output. Preview sound uses the current audio output.", color = colors.textSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Subtle", "Balanced", "Cinematic").forEach { name ->
                val preset = HapticTuning.preset(name).copy(syncMs = config.syncMs)
                FilterChip(selected = config == preset, onClick = { change(preset) }, label = { Text(name) })
            }
        }
        HapticSettingSlider("Overall intensity", config.strength, 0.25f..1.5f) { change(config.copy(strength = it)) }
        HapticSettingSwitch("Impact effects", "Clicks and bass hits", config.impacts, { change(config.copy(impacts = it)) })
        HapticSettingSlider("Impact intensity", config.impactStrength, 0f..1.5f) {
            change(config.copy(impactStrength = it), HapticPreviewType.IMPACT)
        }
        HapticSettingSwitch("Rumble and textures", "Sustained rumble, swells and grain", config.textures, { change(config.copy(textures = it)) })
        HapticSettingSlider("Rumble intensity", config.textureStrength, 0f..1.5f) {
            change(config.copy(textureStrength = it), HapticPreviewType.RUMBLE)
        }
        HapticSettingSlider("Texture detail", config.detail, 0f..1f, "Smooth → Detailed") {
            change(config.copy(detail = it), HapticPreviewType.GRAIN)
        }
        HapticSettingSlider("Dynamic range", config.dynamics, 0f..1f, "Gentle → Punchy") { change(config.copy(dynamics = it)) }
        HapticSettingSlider("Sensitivity", config.sensitivity, 0.5f..1.5f, "Higher reacts to quieter sounds") {
            change(config.copy(sensitivity = it), HapticPreviewType.IMPACT)
        }
        Text("Sync: ${if (config.syncMs > 0) "+" else ""}${config.syncMs} ms", color = colors.textPrimary)
        Slider(value = config.syncMs.toFloat(), valueRange = -250f..250f,
            modifier = Modifier.semantics { contentDescription = "Haptic synchronization" },
            onValueChange = { change(config.copy(syncMs = ((it / 5).roundToInt() * 5).toLong())) })
        Text("Negative: earlier. Positive: later. Enable the sound cue below to compare timing.", color = colors.textSecondary)
        HapticSettingSlider("Touch intensity", touchStrength, 0.25f..1.5f) {
            touchStrength = it; prefs.setTouchHapticStrength(it); type = HapticPreviewType.TOUCH
        }
        HorizontalDivider()
        Text("Live preview", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
        HapticSettingSwitch("Preview while adjusting", "Temporarily replaces movie haptics; works even with movie haptics off", live, {
            live = it; if (!it) preview.stop()
        })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HapticPreviewType.entries.forEach { selected ->
                FilterChip(selected = type == selected, onClick = { type = selected },
                    enabled = preview.supportsTextures || selected == HapticPreviewType.IMPACT || selected == HapticPreviewType.TOUCH,
                    label = { Text(selected.label) })
            }
        }
        Canvas(Modifier.fillMaxWidth().height(82.dp).background(colors.cardBgElevated)
            .semantics { contentDescription = "Haptic waveform preview" }) {
            val values = displayed.amplitudes
            values.indices.forEach { index ->
                val x = size.width * (index + 0.5f) / values.size
                val height = size.height * values[index] / 255f
                drawLine(colors.accentBlue, Offset(x, size.height), Offset(x, size.height - height),
                    strokeWidth = (size.width / values.size).coerceAtLeast(1f))
            }
            drawLine(colors.textPrimary, Offset(size.width * progress, 0f), Offset(size.width * progress, size.height), 2f)
        }
        HapticSettingSwitch("Sound cue", "Short audio cue at sample start for sync comparison", cue, { cue = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = preview.available, onClick = {
                VideoPlaybackManager.setHapticPreviewActive(owner, true)
                preview.preview(HapticPreviewSamples.create(type, config, touchStrength), cue, config.syncMs,
                    touch = type == HapticPreviewType.TOUCH)
            }) { Text("Play sample") }
            OutlinedButton(onClick = {
                live = false; preview.stop(); VideoPlaybackManager.setHapticPreviewActive(owner, false)
            }) { Text("Stop") }
        }
        previewState.message?.let { Text(it, color = colors.textSecondary) }
        if (!preview.supportsTextures) Text("This phone supports basic effects only; dynamic amplitude textures are unavailable.", color = colors.textSecondary)
        TextButton(onClick = { preview.stop(); change(HapticTuning()) }) { Text("Reset this profile") }
    }
}

@Composable
private fun HapticSettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary)
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun HapticSettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, hint: String? = null, onChange: (Float) -> Unit) {
    val colors = LocalAppColors.current
    Column {
        Text("$title: ${(value * 100).roundToInt()}%", color = colors.textPrimary)
        hint?.let { Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = title })
    }
}
