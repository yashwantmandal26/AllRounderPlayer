@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ymediaplayer.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal class VideoSubtitleState(val videoUrl: String, val prefs: AppPreferences) {
    var uri by mutableStateOf(prefs.getExternalSubtitleUri(videoUrl))
    var lines by mutableStateOf<List<SubtitleLine>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var delayMs by mutableLongStateOf(prefs.getSubtitleDelayMs(videoUrl))
    var restoreEmbeddedSelection = true
    fun useEmbedded(preserveSelection: Boolean = false) {
        restoreEmbeddedSelection = !preserveSelection
        uri = null; lines = emptyList(); error = null
        prefs.setExternalSubtitleUri(videoUrl, null)
    }
    fun setDelay(value: Long) { delayMs = value.coerceIn(-30_000L, 30_000L); prefs.setSubtitleDelayMs(videoUrl, delayMs) }
}

@Composable
internal fun rememberVideoSubtitles(url: String, player: ExoPlayer, view: PlayerView,
    prefs: AppPreferences, inPip: Boolean): VideoSubtitleState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember(url) { VideoSubtitleState(url, prefs) }
    LaunchedEffect(state.uri) {
        val uri = state.uri ?: return@LaunchedEffect
        state.restoreEmbeddedSelection = true
        state.loading = true
        try {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri.toUri())?.bufferedReader()?.use { reader ->
                    val content = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        content.append(buffer, 0, count)
                        require(content.length <= 4_000_000) { "Subtitle file is too large" }
                    }
                    content.toString()
                } ?: error("Cannot read subtitle file")
            }
            val lines = withContext(Dispatchers.Default) { SubtitleTimeline.parse(text) }
            require(lines.isNotEmpty()) { "No readable SRT or WebVTT cues found" }
            state.lines = lines; state.error = null
            prefs.setExternalSubtitleUri(url, uri)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            state.error = e.message ?: "Could not read this subtitle file"
            state.lines = emptyList()
        } finally { state.loading = false }
    }
    val externalActive = state.lines.isNotEmpty() && state.uri != null
    DisposableEffect(player, state, externalActive) {
        val wasDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        if (externalActive) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        onDispose {
            if (externalActive) {
                if (state.restoreEmbeddedSelection) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, wasDisabled).build()
                }
                view.subtitleView?.setCues(emptyList())
            }
        }
    }
    val latestDelay = rememberUpdatedState(state.delayMs)
    LaunchedEffect(state.lines, externalActive, player, view, inPip) {
        if (!externalActive || inPip) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous: List<SubtitleLine>? = null
            while (isActive) {
                val lines = SubtitleTimeline.active(state.lines, player.currentPosition, latestDelay.value)
                if (lines != previous) {
                    view.subtitleView?.setCues(lines.map { Cue.Builder().setText(it.text).build() })
                    previous = lines
                }
                delay(50L)
            }
        }
    }
    return state
}

@Composable
internal fun VideoSubtitleTools(state: VideoSubtitleState, view: PlayerView, design: Int,
    onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = state.prefs
    var query by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(prefs.getSubtitleFontSize().toFloat()) }
    var position by remember { mutableFloatStateOf(prefs.getSubtitleBottomPadding()) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            state.uri = it.toString()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            LazyColumn(Modifier.widthIn(max = 460.dp).heightIn(max = 560.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Subtitle tools", style = MaterialTheme.typography.titleLarge)
                    Text("Import an SRT or WebVTT file to adjust timing and search dialogue.", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("Import subtitles") }
                        if (state.uri != null) TextButton(onClick = { state.useEmbedded() }) { Text("Use embedded") }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Text size · ${fontSize.toInt()} sp")
                    AppSlider(
                        value = fontSize,
                        onValueChange = {
                            fontSize = it; prefs.setSubtitleFontSize(it.toInt()); applySubtitleDesign(view, design, prefs)
                        },
                        valueRange = 12f..36f,
                        modifier = Modifier.semantics { contentDescription = "Subtitle text size" },
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Text("Position above bottom · ${(position * 100).toInt()}%")
                    AppSlider(
                        value = position,
                        onValueChange = {
                            position = it; prefs.setSubtitleBottomPadding(it); applySubtitleDesign(view, design, prefs)
                        },
                        valueRange = 0f..0.4f,
                        modifier = Modifier.semantics { contentDescription = "Subtitle vertical position" },
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    if (state.lines.isNotEmpty()) {
                        Text("Timing · ${state.delayMs} ms (positive = later)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { state.setDelay(state.delayMs - 100) }) { Text("−100 ms") }
                            TextButton(onClick = { state.setDelay(0) }) { Text("Reset") }
                            TextButton(onClick = { state.setDelay(state.delayMs + 100) }) { Text("+100 ms") }
                        }
                        OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Search dialogue") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
                val results = if (query.isBlank()) emptyList() else state.lines.filter { it.text.contains(query.trim(), true) }.take(50)
                items(results) { line ->
                    Column(Modifier.fillMaxWidth().clickable { onSeek((line.startMs + state.delayMs).coerceAtLeast(0L)); onDismiss() }
                        .padding(vertical = 8.dp)) {
                        Text(formatTime(line.startMs + state.delayMs), style = MaterialTheme.typography.labelMedium)
                        Text(line.text)
                    }
                }
                if (query.isNotBlank() && results.isEmpty()) item { Text("No matching dialogue") }
                item { TextButton(onClick = onDismiss) { Text("Done") } }
            }
        }
    }
}
