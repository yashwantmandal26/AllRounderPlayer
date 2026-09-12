package com.example.ymediaplayer.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.view.WindowManager
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.data.MusicRepository
import com.example.ymediaplayer.service.MusicService
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// ─── Equalizer Presets ────────────────────────────────────────────────────────
data class MusicEqPreset(val name: String, val bands: List<Float>) // 5 bands in dB (-10 to +10)

val defaultEqPresets = listOf(
    MusicEqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    MusicEqPreset("Bass Boost", listOf(7f, 5f, 1f, 0f, -1f)),
    MusicEqPreset("Rock", listOf(5f, 3f, -1f, 3f, 6f)),
    MusicEqPreset("Pop", listOf(-1f, 2f, 5f, 2f, -2f)),
    MusicEqPreset("Jazz", listOf(3f, 2f, -1f, 2f, 4f)),
    MusicEqPreset("Electronic", listOf(6f, 4f, 0f, 2f, 5f)),
    MusicEqPreset("Vocal", listOf(-2f, 1f, 6f, 3f, 0f)),
    MusicEqPreset("Acoustic", listOf(3f, 1f, 2f, 3f, 2f)),
    MusicEqPreset("Classical", listOf(4f, 3f, -1f, 2f, 3f)),
    MusicEqPreset("Hip Hop", listOf(6f, 5f, 0f, 2f, 3f))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    onFullScreenChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val repository = remember { MusicRepository(context) }
    val appPreferences = remember { AppPreferences(context) }

    var allSongs by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Player State
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var currentlyPlaying by remember { mutableStateOf<MusicItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showFullScreenPlayer by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Playback modes
    var isShuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) } // 0: OFF, 1: ONE, 2: ALL
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Customization State
    var artworkStyle by remember { mutableStateOf(appPreferences.getMusicArtworkStyle()) } // "VINYL", "CARD"
    var favorites by remember { mutableStateOf(appPreferences.getMusicFavorites()) }

    // Library Filtering & Search
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Explore") } // "Explore", "Tracks", "Artists", "Albums", "Favorites"
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<Long?>(null) }
    var sortOrder by remember { mutableStateOf("TITLE") } // "TITLE", "ARTIST", "DURATION"
    var showSortMenu by remember { mutableStateOf(false) }

    // Handle back button when viewing an artist or album drilldown
    BackHandler(enabled = selectedArtist != null || selectedAlbum != null) {
        selectedArtist = null
        selectedAlbum = null
    }

    // Sheets & Dialogs
    var showEqSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }

    // Sleep Timer
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerSecondsRemaining by remember { mutableIntStateOf(0) }

    // Audio Effects
    var eqEnabled by remember { mutableStateOf(true) }
    var selectedPresetName by remember { mutableStateOf("Flat") }
    var bandLevels by remember { mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f)) }
    var bassBoostStrength by remember { mutableFloatStateOf(0.4f) }
    var virtualizerStrength by remember { mutableFloatStateOf(0.25f) }
    var activeAudioSessionId by remember { mutableIntStateOf(MusicService.currentAudioSessionId) }

    // Hardware Audio Effects keyed to active session ID
    val audioEffects = remember(activeAudioSessionId) {
        var eq: Equalizer? = null
        var bass: BassBoost? = null
        var virt: Virtualizer? = null
        try {
            if (activeAudioSessionId > 0) {
                eq = Equalizer(0, activeAudioSessionId).apply { enabled = eqEnabled }
                bass = BassBoost(0, activeAudioSessionId).apply { enabled = eqEnabled }
                virt = Virtualizer(0, activeAudioSessionId).apply { enabled = eqEnabled }
            }
        } catch (_: Exception) {}
        Triple(eq, bass, virt)
    }

    DisposableEffect(audioEffects) {
        onDispose {
            val (eq, bass, virt) = audioEffects
            try { eq?.release() } catch (_: Exception) {}
            try { bass?.release() } catch (_: Exception) {}
            try { virt?.release() } catch (_: Exception) {}
        }
    }

    // Automatically apply EQ, Bass Boost, and Virtualizer whenever effects attach
    LaunchedEffect(audioEffects, eqEnabled, bandLevels, bassBoostStrength, virtualizerStrength) {
        val (eq, bass, virt) = audioEffects
        if (eq != null) {
            try {
                eq.enabled = eqEnabled
                val minRange = eq.bandLevelRange[0]
                val maxRange = eq.bandLevelRange[1]
                bandLevels.forEachIndexed { idx, db ->
                    val mB = (db * 100).toInt().coerceIn(minRange.toInt(), maxRange.toInt()).toShort()
                    if (idx < eq.numberOfBands.toInt()) {
                        eq.setBandLevel(idx.toShort(), mB)
                    }
                }
            } catch (_: Exception) {}
        }
        if (bass != null) {
            try {
                bass.enabled = eqEnabled
                bass.setStrength((bassBoostStrength * 1000).toInt().toShort())
            } catch (_: Exception) {}
        }
        if (virt != null) {
            try {
                virt.enabled = eqEnabled
                virt.setStrength((virtualizerStrength * 1000).toInt().toShort())
            } catch (_: Exception) {}
        }
    }

    // Apply EQ Band Changes
    fun applyBandLevel(bandIndex: Int, gainDb: Float) {
        val newBands = bandLevels.toMutableList()
        newBands[bandIndex] = gainDb
        bandLevels = newBands
        selectedPresetName = "Custom"
        val (eq, _, _) = audioEffects
        if (eq != null && eqEnabled) {
            try {
                val minRange = eq.bandLevelRange[0]
                val maxRange = eq.bandLevelRange[1]
                val mB = (gainDb * 100).toInt().coerceIn(minRange.toInt(), maxRange.toInt()).toShort()
                if (bandIndex < eq.numberOfBands.toInt()) {
                    eq.setBandLevel(bandIndex.toShort(), mB)
                }
            } catch (_: Exception) {}
        }
    }

    fun applyPreset(preset: MusicEqPreset) {
        selectedPresetName = preset.name
        bandLevels = preset.bands
        val (eq, _, _) = audioEffects
        if (eq != null && eqEnabled) {
            try {
                val minRange = eq.bandLevelRange[0]
                val maxRange = eq.bandLevelRange[1]
                preset.bands.forEachIndexed { idx, db ->
                    val mB = (db * 100).toInt().coerceIn(minRange.toInt(), maxRange.toInt()).toShort()
                    if (idx < eq.numberOfBands.toInt()) {
                        eq.setBandLevel(idx.toShort(), mB)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun applyBassBoost(value: Float) {
        bassBoostStrength = value
        val (_, bass, _) = audioEffects
        if (bass != null && eqEnabled) {
            try {
                bass.setStrength((value * 1000).toInt().toShort())
            } catch (_: Exception) {}
        }
    }

    fun applyVirtualizer(value: Float) {
        virtualizerStrength = value
        val (_, _, virt) = audioEffects
        if (virt != null && eqEnabled) {
            try {
                virt.setStrength((value * 1000).toInt().toShort())
            } catch (_: Exception) {}
        }
    }

    // Inform parent of full screen state so floating pill navigation hides
    LaunchedEffect(showFullScreenPlayer) {
        onFullScreenChanged(showFullScreenPlayer)
    }

    // Handle back button when full screen player is open
    BackHandler(enabled = showFullScreenPlayer) {
        showFullScreenPlayer = false
    }

    // Listen for notification deep link tap
    LaunchedEffect(MainActivity.openMusicTrigger.value) {
        if (MainActivity.openMusicTrigger.value) {
            showFullScreenPlayer = true
            MainActivity.openMusicTrigger.value = false
        }
    }

    // Periodic position update (throttled when paused)
    LaunchedEffect(mediaController) {
        while (true) {
            val playing = mediaController?.isPlaying == true
            isPlaying = playing
            currentPosition = mediaController?.currentPosition ?: 0L
            duration = (mediaController?.duration ?: 0L).coerceAtLeast(0L)
            delay(if (playing) 250L else 1000L)
        }
    }

    // Sleep Timer countdown
    LaunchedEffect(sleepTimerSecondsRemaining) {
        if (sleepTimerSecondsRemaining > 0) {
            delay(1000)
            sleepTimerSecondsRemaining -= 1
            if (sleepTimerSecondsRemaining == 0) {
                mediaController?.pause()
                sleepTimerMinutes = 0
            }
        }
    }

    val playSongForce = { song: MusicItem ->
        currentlyPlaying = song
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()
        val item = MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(metadata)
            .build()
        mediaController?.setMediaItem(item)
        mediaController?.prepare()
        mediaController?.play()
        isPlaying = true
    }

    val playNext = {
        if (allSongs.isNotEmpty()) {
            val nextSong = if (isShuffle) {
                val pool = allSongs.filter { it.id != currentlyPlaying?.id }
                if (pool.isNotEmpty()) pool.random() else allSongs.first()
            } else {
                val idx = allSongs.indexOfFirst { it.id == currentlyPlaying?.id }
                val nextIdx = if (idx == -1) 0 else (idx + 1) % allSongs.size
                allSongs[nextIdx]
            }
            playSongForce(nextSong)
        }
    }

    val playPrev = {
        if (allSongs.isNotEmpty()) {
            if (currentPosition > 3000L) {
                mediaController?.seekTo(0)
            } else {
                val idx = allSongs.indexOfFirst { it.id == currentlyPlaying?.id }
                val prevIdx = if (idx <= 0) allSongs.size - 1 else idx - 1
                playSongForce(allSongs[prevIdx])
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        allSongs = repository.getMusicFiles()
        isLoading = false
    }

    // Connect MediaController
    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        mediaController?.seekTo(0)
                        mediaController?.play()
                    } else if (repeatMode == Player.REPEAT_MODE_ALL || isShuffle) {
                        playNext()
                    }
                }
            }
        }

        controllerFuture.addListener({
            val controller = controllerFuture.get()
            mediaController = controller
            controller.addListener(listener)

            isPlaying = controller.isPlaying
            val currentMediaItem = controller.currentMediaItem
            if (currentMediaItem != null) {
                val uriStr = currentMediaItem.localConfiguration?.uri?.toString()
                currentlyPlaying = allSongs.find { it.uri.toString() == uriStr }
            }

            if (MusicService.currentAudioSessionId > 0) {
                activeAudioSessionId = MusicService.currentAudioSessionId
            }

            // If notification deep link was triggered before controller connected
            if (MainActivity.openMusicTrigger.value) {
                showFullScreenPlayer = true
                MainActivity.openMusicTrigger.value = false
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            mediaController?.removeListener(listener)
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // ─── Sync now-playing state to MusicService companion so Video tab can show indicator ───
    LaunchedEffect(currentlyPlaying, isPlaying) {
        MusicService.isMusicPlaying.value = isPlaying && currentlyPlaying != null
        MusicService.nowPlayingTitle.value = currentlyPlaying?.title ?: ""
        MusicService.nowPlayingArtist.value = currentlyPlaying?.artist ?: ""
        MusicService.nowPlayingArtUri.value = currentlyPlaying?.albumArtUri
    }

    // ─── Online Streaming Data Groupings ──────────────────────────────────────────
    val artistsMap = remember(allSongs) {

        allSongs.groupBy { it.artist.ifBlank { "Unknown Artist" } }
    }
    val albumsMap = remember(allSongs) {
        allSongs.groupBy { it.albumId }
    }
    val quickPicks = remember(allSongs) {
        if (allSongs.size > 9) allSongs.shuffled().take(9) else allSongs
    }
    val topArtists = remember(artistsMap) {
        artistsMap.toList().sortedByDescending { it.second.size }
    }
    val featuredAlbums = remember(albumsMap) {
        albumsMap.toList()
    }
    val recentlyAddedSongs = remember(allSongs) {
        allSongs.sortedByDescending { it.id }.take(10)
    }

    // Filter and sort songs for Tracks & Favorites views
    val filteredSongs = remember(allSongs, searchQuery, selectedCategory, sortOrder, favorites) {
        var list = allSongs
        if (selectedCategory == "Favorites") {
            list = list.filter { favorites.contains(it.uri.toString()) }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortOrder) {
            "TITLE" -> list.sortedBy { it.title.lowercase(Locale.getDefault()) }
            "TITLE_DESC" -> list.sortedByDescending { it.title.lowercase(Locale.getDefault()) }
            "ARTIST" -> list.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            "ARTIST_DESC" -> list.sortedByDescending { it.artist.lowercase(Locale.getDefault()) }
            "DATE" -> list.sortedByDescending { it.id }
            "DATE_ASC" -> list.sortedBy { it.id }
            "DURATION" -> list.sortedByDescending { it.duration }
            "DURATION_ASC" -> list.sortedBy { it.duration }
            "SIZE" -> list.sortedByDescending { it.size }
            "SIZE_ASC" -> list.sortedBy { it.size }
            else -> list
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Top Bar & Navigation ───────────────────────────────────────
            if (selectedArtist != null) {
                // Artist Drill-down Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedArtist = null },
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                            .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ARTIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accentBlue, letterSpacing = 1.sp)
                        Text(selectedArtist!!, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (selectedAlbum != null) {
                // Album Drill-down Top Bar
                val albumSongs = albumsMap[selectedAlbum] ?: emptyList()
                val albumTitle = albumSongs.firstOrNull()?.album ?: "Album"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedAlbum = null },
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                            .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ALBUM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accentBlue, letterSpacing = 1.sp)
                        Text(albumTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                // Standard Online Top Bar with Search & Quick Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search songs, artists, albums...", color = c.textHint) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = c.glassBg,
                                unfocusedContainerColor = c.glassBg,
                                focusedTextColor = c.textPrimary,
                                unfocusedTextColor = c.textPrimary,
                                cursorColor = c.accentBlue,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearching = false
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), RoundedCornerShape(14.dp))
                        )
                    } else {
                        Column {
                            Text(
                                text = "Music Streaming",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            Text(
                                text = "${allSongs.size} tracks · ${artistsMap.size} artists",
                                fontSize = 12.sp,
                                color = c.textSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { isSearching = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = c.textPrimary, modifier = Modifier.size(19.dp))
                            }
                            IconButton(
                                onClick = { showEqSheet = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = c.accentBlue, modifier = Modifier.size(19.dp))
                            }
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort", tint = c.textPrimary, modifier = Modifier.size(19.dp))
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    containerColor = c.dropdownBg,
                                    modifier = Modifier
                                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                                        .shadow(12.dp, RoundedCornerShape(14.dp))
                                ) {
                                    Text(
                                        "SORT TRACKS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textSecondary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    HorizontalDivider(color = c.glassBorder)
                                    listOf(
                                        "TITLE" to ("Title (A to Z)" to Icons.Rounded.SortByAlpha),
                                        "TITLE_DESC" to ("Title (Z to A)" to Icons.Rounded.SortByAlpha),
                                        "ARTIST" to ("Artist (A to Z)" to Icons.Rounded.Person),
                                        "DATE" to ("Date Added (Newest)" to Icons.Rounded.Schedule),
                                        "DATE_ASC" to ("Date Added (Oldest)" to Icons.Rounded.Schedule),
                                        "DURATION" to ("Duration (Longest)" to Icons.Rounded.Schedule),
                                        "DURATION_ASC" to ("Duration (Shortest)" to Icons.Rounded.Schedule),
                                        "SIZE" to ("Size (Largest)" to Icons.Rounded.Storage)
                                    ).forEach { (order, pair) ->
                                        val (label, icon) = pair
                                        val isSelected = sortOrder == order
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) c.accentBlue else c.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            text = {
                                                Text(
                                                    label,
                                                    color = if (isSelected) c.accentBlue else c.textPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 14.sp
                                                )
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(18.dp))
                                                }
                                            },
                                            onClick = {
                                                sortOrder = order
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 5 Modern Online Category Pills ───────────────────────────
                val categories = listOf(
                    "Explore" to Icons.Rounded.AutoAwesome,
                    "Tracks" to Icons.Rounded.MusicNote,
                    "Artists" to Icons.Rounded.Person,
                    "Albums" to Icons.Rounded.Album,
                    "Favorites" to Icons.Rounded.Favorite
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categories) { (cat, icon) ->
                        val selected = selectedCategory == cat
                        val chipShape = RoundedCornerShape(16.dp)
                        val countLabel = when (cat) {
                            "Tracks" -> " (${allSongs.size})"
                            "Artists" -> " (${artistsMap.size})"
                            "Albums" -> " (${albumsMap.size})"
                            "Favorites" -> " (${favorites.size})"
                            else -> ""
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .shadow(if (selected) 6.dp else 2.dp, chipShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                .clip(chipShape)
                                .background(
                                    if (selected) Brush.verticalGradient(listOf(c.accentBlue, c.accentBlue.copy(0.85f)))
                                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                                )
                                .border(
                                    1.3.dp,
                                    if (selected) Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.12f)))
                                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)),
                                    chipShape
                                )
                                .bounceClick { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selected) c.onAccent else if (cat == "Favorites") Color(0xFFFF4D6D) else c.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$cat$countLabel",
                                color = if (selected) c.onAccent else c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ─── Main Content Body ───────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accentBlue, strokeWidth = 3.dp)
                }
            } else if (allSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.MusicOff, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No Music Files Found", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Add audio files to your device storage to start streaming", color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            } else if (selectedArtist != null) {
                // ─── ARTIST DETAIL VIEW ──────────────────────────────────────
                val artistSongs = artistsMap[selectedArtist] ?: emptyList()
                val firstArt = artistSongs.firstOrNull()?.albumArtUri
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                ) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .shadow(12.dp, CircleShape, ambientColor = c.accentBlue.copy(0.4f), spotColor = c.accentBlue.copy(0.5f))
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                    .border(2.dp, Brush.verticalGradient(listOf(c.accentBlue, c.cardBorderHighlight)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(model = firstArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (firstArt == null) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(44.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(selectedArtist!!, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, textAlign = TextAlign.Center)
                            Text("${artistSongs.size} Songs · ${formatTotalDuration(artistSongs)}", fontSize = 13.sp, color = c.textSecondary)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { artistSongs.firstOrNull()?.let { playSongForce(it) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = c.accentBlue, spotColor = c.accentBlue)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play All", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val shuffled = artistSongs.shuffled()
                                        shuffled.firstOrNull()?.let { playSongForce(it) }
                                    },
                                    border = BorderStroke(1.2.dp, c.cardBorderHighlight),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shuffle")
                                }
                            }
                        }
                    }
                    items(artistSongs, key = { "art_${it.id}" }) { song ->
                        val isCurrent = currentlyPlaying?.id == song.id
                        val isFav = favorites.contains(song.uri.toString())
                        MusicListItem(
                            song = song,
                            isCurrentlyPlaying = isCurrent,
                            isPlaying = isPlaying && isCurrent,
                            isFavorite = isFav,
                            onFavoriteToggle = {
                                appPreferences.toggleMusicFavorite(song.uri.toString())
                                favorites = appPreferences.getMusicFavorites()
                            },
                            onClick = {
                                if (!isCurrent) playSongForce(song)
                                else if (isPlaying) mediaController?.pause() else mediaController?.play()
                            }
                        )
                    }
                }
            } else if (selectedAlbum != null) {
                // ─── ALBUM DETAIL VIEW ───────────────────────────────────────
                val albumSongs = albumsMap[selectedAlbum] ?: emptyList()
                val firstSong = albumSongs.firstOrNull()
                val albumTitle = firstSong?.album ?: "Album"
                val artistName = firstSong?.artist ?: "Artist"
                val albumArt = firstSong?.albumArtUri
                val albumShape = RoundedCornerShape(20.dp)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                ) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .shadow(16.dp, albumShape, ambientColor = Color.Black.copy(0.7f), spotColor = c.cardShadowColor)
                                    .clip(albumShape)
                                    .background(Color(0xFF141520))
                                    .border(1.4.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), albumShape)
                            ) {
                                MusicAlbumArtImage(artUri = albumArt, iconSize = 56.dp, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(albumTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, textAlign = TextAlign.Center)
                            Text("$artistName · ${albumSongs.size} Tracks", fontSize = 13.sp, color = c.textSecondary)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { albumSongs.firstOrNull()?.let { playSongForce(it) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = c.accentBlue, spotColor = c.accentBlue)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play All", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val shuffled = albumSongs.shuffled()
                                        shuffled.firstOrNull()?.let { playSongForce(it) }
                                    },
                                    border = BorderStroke(1.2.dp, c.cardBorderHighlight),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shuffle")
                                }
                            }
                        }
                    }
                    items(albumSongs, key = { "alb_${it.id}" }) { song ->
                        val isCurrent = currentlyPlaying?.id == song.id
                        val isFav = favorites.contains(song.uri.toString())
                        MusicListItem(
                            song = song,
                            isCurrentlyPlaying = isCurrent,
                            isPlaying = isPlaying && isCurrent,
                            isFavorite = isFav,
                            onFavoriteToggle = {
                                appPreferences.toggleMusicFavorite(song.uri.toString())
                                favorites = appPreferences.getMusicFavorites()
                            },
                            onClick = {
                                if (!isCurrent) playSongForce(song)
                                else if (isPlaying) mediaController?.pause() else mediaController?.play()
                            }
                        )
                    }
                }
            } else when (selectedCategory) {
                // ─── 1. EXPLORE (ONLINE STREAMING HOME FEED) ─────────────────
                "Explore" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                    ) {
                        // Hero "Jump Back In" / "Shuffle Mix" Card
                        item {
                            val heroShape = RoundedCornerShape(22.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .shadow(elevation = 8.dp, shape = heroShape, ambientColor = c.accentBlue.copy(0.3f), spotColor = c.accentBlue.copy(0.4f))
                                    .clip(heroShape)
                                    .background(Brush.linearGradient(listOf(c.accentBlue.copy(alpha = 0.35f), c.cardBgElevated, c.cardBg)))
                                    .border(1.4.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), heroShape)
                                    .padding(18.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(c.accentBlue.copy(0.25f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("ONLINE STREAMING FEED", color = c.accentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("Instant Library Mix", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("${allSongs.size} tracks across ${artistsMap.size} artists ready to stream", color = Color.White.copy(0.75f), fontSize = 12.sp)
                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                if (allSongs.isNotEmpty()) {
                                                    val randomSong = allSongs.random()
                                                    playSongForce(randomSong)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = c.accentBlue, spotColor = c.accentBlue)
                                        ) {
                                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Shuffle All", fontWeight = FontWeight.Bold)
                                        }
                                        // Overlapping mini album stack
                                        Row {
                                            allSongs.take(3).forEachIndexed { index, song ->
                                                MusicAlbumArtImage(
                                                    artUri = song.albumArtUri,
                                                    iconSize = 16.dp,
                                                    modifier = Modifier
                                                        .offset(x = (-index * 10).dp)
                                                        .size(34.dp)
                                                        .shadow(4.dp, CircleShape)
                                                        .clip(CircleShape)
                                                        .border(1.5.dp, Color.White, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Shelf: QUICK PICKS (3 songs stacked in horizontal pages)
                        if (quickPicks.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("QUICK PICKS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 1.sp)
                                    }
                                    Text(
                                        "Play mix",
                                        color = c.accentBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            quickPicks.firstOrNull()?.let { playSongForce(it) }
                                        }.padding(4.dp)
                                    )
                                }
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val columns = quickPicks.chunked(3)
                                    items(columns) { colSongs ->
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            colSongs.forEach { song ->
                                                val isCurrent = currentlyPlaying?.id == song.id
                                                QuickPickItem(
                                                    song = song,
                                                    isCurrent = isCurrent,
                                                    isPlaying = isPlaying && isCurrent,
                                                    onClick = {
                                                        if (!isCurrent) playSongForce(song)
                                                        else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // Shelf: TOP ARTISTS (Circular horizontal avatars)
                        if (topArtists.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("POPULAR ARTISTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 1.sp)
                                    }
                                    Text(
                                        "See all",
                                        color = c.accentBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable { selectedCategory = "Artists" }.padding(4.dp)
                                    )
                                }
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(topArtists.take(8)) { (name, songs) ->
                                        OnlineArtistCard(name = name, songs = songs, onClick = { selectedArtist = name })
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // Shelf: FEATURED ALBUMS (Square vinyl covers)
                        if (featuredAlbums.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Album, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("FEATURED ALBUMS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 1.sp)
                                    }
                                    Text(
                                        "See all",
                                        color = c.accentBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable { selectedCategory = "Albums" }.padding(4.dp)
                                    )
                                }
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(featuredAlbums.take(8)) { (albumId, songs) ->
                                        OnlineAlbumCard(songs = songs, onClick = { selectedAlbum = albumId })
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // Shelf: RECENTLY ADDED TRACKS
                        if (recentlyAddedSongs.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("RECENTLY ADDED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 1.sp)
                                }
                            }
                            items(recentlyAddedSongs, key = { "recent_${it.id}" }) { song ->
                                val isCurrent = currentlyPlaying?.id == song.id
                                val isFav = favorites.contains(song.uri.toString())
                                MusicListItem(
                                    song = song,
                                    isCurrentlyPlaying = isCurrent,
                                    isPlaying = isPlaying && isCurrent,
                                    isFavorite = isFav,
                                    onFavoriteToggle = {
                                        appPreferences.toggleMusicFavorite(song.uri.toString())
                                        favorites = appPreferences.getMusicFavorites()
                                    },
                                    onClick = {
                                        if (!isCurrent) playSongForce(song)
                                        else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                    }
                                )
                            }
                        }
                    }
                }

                // ─── 2. TRACKS (FULL LIBRARY VIEW) ───────────────────────────
                "Tracks" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.accentBlue.copy(0.18f))
                                        .border(1.dp, c.accentBlue.copy(0.35f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (filteredSongs.isNotEmpty()) playSongForce(filteredSongs.random())
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shuffle All", color = c.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("${filteredSongs.size} tracks", color = c.textSecondary, fontSize = 12.sp)
                            }
                        }
                        items(filteredSongs, key = { it.id }) { song ->
                            val isCurrent = currentlyPlaying?.id == song.id
                            val isFav = favorites.contains(song.uri.toString())
                            MusicListItem(
                                song = song,
                                isCurrentlyPlaying = isCurrent,
                                isPlaying = isPlaying && isCurrent,
                                isFavorite = isFav,
                                onFavoriteToggle = {
                                    appPreferences.toggleMusicFavorite(song.uri.toString())
                                    favorites = appPreferences.getMusicFavorites()
                                },
                                onClick = {
                                    if (!isCurrent) playSongForce(song)
                                    else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                }
                            )
                        }
                    }
                }

                // ─── 3. ARTISTS (CATALOG GALLERY) ────────────────────────────
                "Artists" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                "ALL ARTISTS (${topArtists.size})",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(topArtists, key = { "all_art_${it.first}" }) { (name, songs) ->
                            val firstArt = songs.firstOrNull()?.albumArtUri
                            val rowShape = RoundedCornerShape(16.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .shadow(elevation = 4.dp, shape = rowShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                    .clip(rowShape)
                                    .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), rowShape)
                                    .clickable { selectedArtist = name }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color(0xFF141520))
                                        .border(1.4.dp, c.cardBorderHighlight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(model = firstArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    if (firstArt == null) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(28.dp))
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${songs.size} songs", color = c.textSecondary, fontSize = 12.sp)
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                        item { Spacer(Modifier.height(if (currentlyPlaying != null) 160.dp else 100.dp)) }
                    }
                }

                // ─── 4. ALBUMS (2-COLUMN GRID GALLERY) ───────────────────────
                "Albums" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                "ALL ALBUMS (${featuredAlbums.size})",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                        val chunkedAlbums = featuredAlbums.chunked(2)
                        items(chunkedAlbums, key = { "chunk_alb_${it.first().first}" }) { rowAlbums ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowAlbums.forEach { (albumId, songs) ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        OnlineAlbumCard(songs = songs, onClick = { selectedAlbum = albumId })
                                    }
                                }
                                if (rowAlbums.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(if (currentlyPlaying != null) 160.dp else 100.dp)) }
                    }
                }

                // ─── 5. FAVORITES (LIKED SONGS HERO FEED) ────────────────────
                "Favorites" -> {
                    val favSongs = allSongs.filter { favorites.contains(it.uri.toString()) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                    ) {
                        item {
                            val heroShape = RoundedCornerShape(22.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .shadow(elevation = 8.dp, shape = heroShape, ambientColor = Color(0xFFFF4D6D).copy(0.3f), spotColor = Color(0xFFFF4D6D).copy(0.4f))
                                    .clip(heroShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFFFF4D6D).copy(0.45f), c.cardBgElevated, c.cardBg)))
                                    .border(1.4.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), heroShape)
                                    .padding(18.dp)
                            ) {
                                Column {
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Liked Songs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("${favSongs.size} favorites saved on device", color = Color.White.copy(0.8f), fontSize = 12.sp)
                                    Spacer(Modifier.height(14.dp))
                                    if (favSongs.isNotEmpty()) {
                                        Button(
                                            onClick = { playSongForce(favSongs.random()) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D6D), contentColor = Color.White),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Shuffle Favorites", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        if (favSongs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text("No favorite songs yet. Tap the heart icon on any song to add it here.", color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            items(favSongs, key = { "fav_${it.id}" }) { song ->
                                val isCurrent = currentlyPlaying?.id == song.id
                                MusicListItem(
                                    song = song,
                                    isCurrentlyPlaying = isCurrent,
                                    isPlaying = isPlaying && isCurrent,
                                    isFavorite = true,
                                    onFavoriteToggle = {
                                        appPreferences.toggleMusicFavorite(song.uri.toString())
                                        favorites = appPreferences.getMusicFavorites()
                                    },
                                    onClick = {
                                        if (!isCurrent) playSongForce(song)
                                        else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Floating Mini Player ───────────────────────────────────────────
        AnimatedVisibility(
            visible = currentlyPlaying != null && !showFullScreenPlayer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 76.dp, start = 12.dp, end = 12.dp)
        ) {
            currentlyPlaying?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = { if (isPlaying) mediaController?.pause() else mediaController?.play() },
                    onNext = playNext,
                    onPrev = playPrev,
                    onClick = { showFullScreenPlayer = true }
                )
            }
        }

        // ─── Full Screen Music Player ───────────────────────────────────────
        AnimatedVisibility(
            visible = showFullScreenPlayer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            currentlyPlaying?.let { song ->
                FullScreenMusicPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    playbackSpeed = playbackSpeed,
                    artworkStyle = artworkStyle,
                    sleepTimerSecondsRemaining = sleepTimerSecondsRemaining,
                    isFavorite = favorites.contains(song.uri.toString()),
                    onPlayPause = { if (isPlaying) mediaController?.pause() else mediaController?.play() },
                    onNext = playNext,
                    onPrev = playPrev,
                    onSeek = { mediaController?.seekTo(it) },
                    onToggleShuffle = { isShuffle = !isShuffle },
                    onToggleRepeat = {
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },
                    onToggleArtworkStyle = {
                        val styles = listOf("VINYL", "CARD", "CASSETTE", "CYBER_ORB", "WAVE")
                        val currentIdx = styles.indexOf(artworkStyle.uppercase())
                        val nextIdx = if (currentIdx == -1) 0 else (currentIdx + 1) % styles.size
                        val newStyle = styles[nextIdx]
                        artworkStyle = newStyle
                        appPreferences.setMusicArtworkStyle(newStyle)
                        val themeName = when (newStyle) {
                            "VINYL" -> "Vinyl Record"
                            "CARD" -> "Glass 3D Card"
                            "CASSETTE" -> "Retro Cassette"
                            "CYBER_ORB" -> "Cyber Neon Orb"
                            "WAVE" -> "Waveform Stage"
                            else -> newStyle
                        }
                        android.widget.Toast.makeText(context, "Visual Theme: $themeName", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onFavoriteToggle = {
                        appPreferences.toggleMusicFavorite(song.uri.toString())
                        favorites = appPreferences.getMusicFavorites()
                    },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenEqualizer = { showEqSheet = true },
                    onOpenSleepTimer = { showSleepDialog = true },
                    onOpenSpeed = { showSpeedDialog = true },
                    onOpenSongInfo = { showSongInfoDialog = true },
                    onClose = { showFullScreenPlayer = false }
                )
            }
        }

        // ─── Equalizer Modal / Bottom Sheet ─────────────────────────────────
        if (showEqSheet) {
            EqualizerDialog(
                enabled = eqEnabled,
                onToggleEnabled = { eqEnabled = it },
                selectedPreset = selectedPresetName,
                presets = defaultEqPresets,
                bandLevels = bandLevels,
                bassStrength = bassBoostStrength,
                virtualizerStrength = virtualizerStrength,
                onPresetSelected = { applyPreset(it) },
                onBandChange = { band, level -> applyBandLevel(band, level) },
                onBassChange = { applyBassBoost(it) },
                onVirtualizerChange = { applyVirtualizer(it) },
                onDismiss = { showEqSheet = false }
            )
        }

        // ─── Play Queue Bottom Sheet ────────────────────────────────────────
        if (showQueueSheet) {
            QueueDialog(
                queue = allSongs,
                currentSong = currentlyPlaying,
                isPlaying = isPlaying,
                onSelectSong = { song -> playSongForce(song); showQueueSheet = false },
                onDismiss = { showQueueSheet = false }
            )
        }

        // ─── Sleep Timer Dialog ─────────────────────────────────────────────
        if (showSleepDialog) {
            SleepTimerDialog(
                currentRemainingSeconds = sleepTimerSecondsRemaining,
                onSetTimer = { minutes ->
                    sleepTimerMinutes = minutes
                    sleepTimerSecondsRemaining = minutes * 60
                    showSleepDialog = false
                },
                onCancelTimer = {
                    sleepTimerMinutes = 0
                    sleepTimerSecondsRemaining = 0
                    showSleepDialog = false
                },
                onDismiss = { showSleepDialog = false }
            )
        }

        // ─── Speed Selector Dialog ──────────────────────────────────────────
        if (showSpeedDialog) {
            SpeedSelectorDialog(
                currentSpeed = playbackSpeed,
                onSelectSpeed = { speed ->
                    playbackSpeed = speed
                    mediaController?.playbackParameters = PlaybackParameters(speed)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        // ─── Song Info Dialog ───────────────────────────────────────────────
        if (showSongInfoDialog && currentlyPlaying != null) {
            SongInfoDialog(song = currentlyPlaying!!, onDismiss = { showSongInfoDialog = false })
        }
    }
}

private fun formatTotalDuration(songs: List<MusicItem>): String {
    val totalMs = songs.sumOf { it.duration }
    val minutes = totalMs / 1000 / 60
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

// ─────────────────────────────────────────────────────────────────
// ONLINE STREAMING QUICK PICK COMPONENT
// ─────────────────────────────────────────────────────────────────
@Composable
private fun QuickPickItem(
    song: MusicItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val itemShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .width(260.dp)
            .shadow(elevation = if (isCurrent) 6.dp else 3.dp, shape = itemShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
            .clip(itemShape)
            .background(
                if (isCurrent) Brush.verticalGradient(listOf(c.accentBlue.copy(0.22f), c.cardBgElevated))
                else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
            )
            .border(
                1.2.dp,
                if (isCurrent) Brush.verticalGradient(listOf(c.accentBlue, c.cardBorderHighlight, c.accentBlue.copy(0.5f)))
                else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)),
                itemShape
            )
            .bounceClick(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicAlbumArtImage(
            artUri = song.albumArtUri,
            iconSize = 20.dp,
            modifier = Modifier
                .size(44.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .border(0.8.dp, c.cardBorderHighlight, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) c.accentBlue else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = c.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent && isPlaying) {
            BeatVisualizer(isPlaying = true, barCount = 3, height = 16.dp, withReflection = false)
        } else {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// ONLINE STREAMING ARTIST CARD (CIRCULAR AVATAR)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun OnlineArtistCard(
    name: String,
    songs: List<MusicItem>,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val firstArt = songs.firstOrNull()?.albumArtUri
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .bounceClick(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                .border(1.4.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = firstArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (firstArt == null) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "${songs.size} tracks",
            color = c.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// ONLINE STREAMING ALBUM CARD (SQUARE COVER)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun OnlineAlbumCard(
    songs: List<MusicItem>,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val firstSong = songs.firstOrNull()
    val albumTitle = firstSong?.album ?: "Unknown Album"
    val artist = firstSong?.artist ?: "Unknown Artist"
    val artUri = firstSong?.albumArtUri
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .width(130.dp)
            .bounceClick(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .shadow(elevation = 8.dp, shape = cardShape, ambientColor = Color.Black.copy(0.6f), spotColor = c.cardShadowColor)
                .clip(cardShape)
                .background(Color(0xFF141520))
                .border(1.4.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), cardShape)
        ) {
            MusicAlbumArtImage(
                artUri = artUri,
                iconSize = 48.dp,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("${songs.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            albumTitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            artist,
            color = c.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// MUSIC LIST ITEM
// ─────────────────────────────────────────────────────────────────
@Composable
fun MusicListItem(
    song: MusicItem,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val itemShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .shadow(
                elevation = if (isCurrentlyPlaying) 8.dp else 4.dp,
                shape = itemShape,
                ambientColor = if (isCurrentlyPlaying) c.accentBlue.copy(0.4f) else c.cardShadowColor,
                spotColor = if (isCurrentlyPlaying) c.accentBlue.copy(0.5f) else c.cardShadowColor
            )
            .clip(itemShape)
            .background(
                if (isCurrentlyPlaying)
                    Brush.verticalGradient(listOf(c.accentBlue.copy(0.18f), c.cardBgElevated))
                else
                    Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
            )
            .border(
                1.3.dp,
                if (isCurrentlyPlaying)
                    Brush.verticalGradient(listOf(c.accentBlue, c.cardBorderHighlight, c.accentBlue.copy(0.5f)))
                else
                    Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)),
                itemShape
            )
            .bounceClick(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.cardBorderHighlight, RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        ) {
            MusicAlbumArtImage(artUri = song.albumArtUri, iconSize = 22.dp, modifier = Modifier.fillMaxSize())
            if (isCurrentlyPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    BeatVisualizer(isPlaying = isPlaying, barCount = 5, height = 22.dp, withReflection = false)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrentlyPlaying) c.accentBlue else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    " • ${formatTime(song.duration)}",
                    fontSize = 12.sp,
                    color = c.textSecondary
                )
            }
        }
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .size(38.dp)
                .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                .border(1.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
        ) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF4D6D) else c.textSecondary.copy(0.7f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MINI PLAYER
// ─────────────────────────────────────────────────────────────────
@Composable
fun MiniPlayer(
    song: MusicItem,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit = {},
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val progressRatio = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val miniShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 18.dp, shape = miniShape, ambientColor = Color.Black.copy(0.7f), spotColor = c.accentBlue.copy(0.4f))
            .clip(miniShape)
            .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
            .border(
                1.4.dp,
                Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)),
                miniShape
            )
            .bounceClick(onClick = onClick)
    ) {
        // Frosted background with heavy blur
        MusicAlbumArtImage(
            artUri = song.albumArtUri,
            iconSize = 40.dp,
            modifier = Modifier.matchParentSize().blur(30.dp)
        )
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(0.70f)))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MusicAlbumArtImage(
                    artUri = song.albumArtUri,
                    iconSize = 22.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, c.cardBorderHighlight, RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isPlaying) {
                    BeatVisualizer(isPlaying = true, barCount = 4, height = 18.dp, withReflection = false)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .size(34.dp)
                        .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                        .border(1.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = c.accentBlue, spotColor = c.accentBlue)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(c.accentBlue, c.accentBlue.copy(0.85f))))
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(34.dp)
                        .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                        .border(1.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.cardBorderShadow)), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Continuous progress bar along bottom of mini player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio)
                        .fillMaxHeight()
                        .background(c.accentBlue)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// FULL SCREEN PLAYER
// ─────────────────────────────────────────────────────────────────
@Composable
fun FullScreenMusicPlayer(
    song: MusicItem,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffle: Boolean,
    repeatMode: Int,
    playbackSpeed: Float,
    artworkStyle: String,
    sleepTimerSecondsRemaining: Int,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleArtworkStyle: () -> Unit,
    onSelectArtworkStyle: (String) -> Unit = {},
    onFavoriteToggle: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSongInfo: () -> Unit,
    onClose: () -> Unit
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var brightnessPct by remember {
        val lp = activity?.window?.attributes
        val currentVal = lp?.screenBrightness?.takeIf { it in 0.005f..1f }
        mutableFloatStateOf(if (currentVal != null) screenBrightnessToSlider(currentVal) else getSystemBrightness(context))
    }
    var showBrightnessBar by remember { mutableStateOf(false) }
    var brightnessTouchTrigger by remember { mutableLongStateOf(0L) }
    var hasUserAdjustedBrightness by remember { mutableStateOf(false) }

    LaunchedEffect(brightnessPct, hasUserAdjustedBrightness) {
        if (!hasUserAdjustedBrightness) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        val lp = act.window?.attributes ?: return@LaunchedEffect
        val targetHardware = sliderToScreenBrightness(brightnessPct)
        if (lp.screenBrightness != targetHardware) {
            lp.screenBrightness = targetHardware
            act.window?.attributes = lp
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val act = activity ?: return@onDispose
            val lp = act.window?.attributes ?: return@onDispose
            if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window?.attributes = lp
            }
        }
    }

    var volumePct by remember {
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(vol.toFloat() / maxVolume.toFloat())
    }
    var showVolumeBar by remember { mutableStateOf(false) }
    var volumeTouchTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(brightnessTouchTrigger) {
        if (brightnessTouchTrigger > 0L) {
            delay(1600.milliseconds)
            showBrightnessBar = false
        }
    }

    LaunchedEffect(volumeTouchTrigger) {
        if (volumeTouchTrigger > 0L) {
            delay(1600.milliseconds)
            showVolumeBar = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "spin_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B22))
            .pointerInput(Unit) {
                var isVertical = false
                var isHorizontal = false
                var isLeft = false

                detectDragGestures(
                    onDragStart = { offset ->
                        isVertical = false
                        isHorizontal = false
                        isLeft = offset.x < size.width * 0.5f
                    },
                    onDragEnd = {
                        if (isVertical) {
                            if (isLeft) brightnessTouchTrigger = System.currentTimeMillis()
                            else volumeTouchTrigger = System.currentTimeMillis()
                        }
                    },
                    onDragCancel = {
                        if (isVertical) {
                            if (isLeft) brightnessTouchTrigger = System.currentTimeMillis()
                            else volumeTouchTrigger = System.currentTimeMillis()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!isVertical && !isHorizontal) {
                            if (abs(dragAmount.y) > abs(dragAmount.x) && abs(dragAmount.y) > 4f) {
                                isVertical = true
                            } else if (abs(dragAmount.x) > abs(dragAmount.y) && abs(dragAmount.x) > 4f) {
                                isHorizontal = true
                            }
                        }
                        if (isVertical) {
                            change.consume()
                            val dragRatio = -dragAmount.y / size.height
                            if (isLeft) {
                                brightnessPct = (brightnessPct + dragRatio * 1.4f).coerceIn(0.01f, 1f)
                                hasUserAdjustedBrightness = true
                                showBrightnessBar = true
                                showVolumeBar = false
                                brightnessTouchTrigger = 0L
                            } else {
                                val newVol = (volumePct + dragRatio * 1.4f).coerceIn(0f, 1f)
                                volumePct = newVol
                                val streamVol = (newVol * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                if (streamVol != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, streamVol, 0)
                                }
                                showVolumeBar = true
                                showBrightnessBar = false
                                volumeTouchTrigger = 0L
                            }
                        }
                    }
                )
            }
    ) {
        // High-blur album art background mesh
        MusicAlbumArtImage(
            artUri = song.albumArtUri,
            iconSize = 64.dp,
            modifier = Modifier.fillMaxSize().blur(85.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1B22).copy(alpha = 0.72f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Header: Back, Sleep Timer Pill, Equalizer, Style, More ──────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp))
                }

                // Sleep timer badge if active
                if (sleepTimerSecondsRemaining > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.accentBlue.copy(0.85f))
                            .clickable { onOpenSleepTimer() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Timer: ${formatTime(sleepTimerSecondsRemaining * 1000L)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "NOW PLAYING",
                        color = Color.White.copy(0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tap to cycle visual styles one-by-one
                    IconButton(onClick = onToggleArtworkStyle) {
                        val styleIcon = when (artworkStyle.uppercase()) {
                            "VINYL" -> Icons.Rounded.Album
                            "CARD" -> Icons.Rounded.CropPortrait
                            "CASSETTE" -> Icons.Rounded.GraphicEq
                            "CYBER_ORB" -> Icons.Rounded.Radio
                            "WAVE" -> Icons.Rounded.Waves
                            else -> Icons.Rounded.Palette
                        }
                        Icon(
                            styleIcon,
                            contentDescription = "Visual Themes",
                            tint = c.accentBlue
                        )
                    }
                    // Equalizer
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = Color.White)
                    }
                    // Song Info / More
                    IconButton(onClick = onOpenSongInfo) {
                        Icon(Icons.Rounded.Info, contentDescription = "Info", tint = Color.White.copy(0.8f))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ─── Customizable Artwork: 5 Visual Themes ──────────────────────
            if (artworkStyle == "VINYL") {
                // 1. Spinning Vinyl Record
                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .shadow(32.dp, CircleShape, ambientColor = Color.Black, spotColor = c.accentBlue.copy(0.4f))
                        .clip(CircleShape)
                        .background(Color(0xFF101012))
                        .border(3.dp, Color.White.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(14.dp).border(1.dp, Color.White.copy(0.06f), CircleShape))
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp).border(1.dp, Color.White.copy(0.06f), CircleShape))
                    Box(modifier = Modifier.fillMaxSize().padding(50.dp).border(1.dp, Color.White.copy(0.06f), CircleShape))

                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .graphicsLayer { rotationZ = if (isPlaying) rotation else 0f }
                    ) {
                        MusicAlbumArtImage(
                            artUri = song.albumArtUri,
                            iconSize = 50.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(2.dp, Color.White.copy(0.35f), CircleShape)
                    )
                }
            } else if (artworkStyle == "CASSETTE") {
                // 2. Retro 80s/90s Cassette Tape
                val progressRatio = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0.05f, 0.95f) else 0.5f
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(190.dp)
                        .shadow(32.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color(0xFFFFB703).copy(0.3f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF2B2B30), Color(0xFF18181B))))
                        .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.2f), Color.White.copy(0.05f))), RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { align ->
                        Box(
                            modifier = Modifier
                                .align(align)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF55555C))
                                .border(0.8.dp, Color.White.copy(0.3f), CircleShape)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8ECEF))
                                .border(1.dp, Color(0xFFC5CBD2), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SIDE A • ${song.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2421),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "HQ-90",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD90429)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(78.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF101014))
                                .border(1.5.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(170.dp)
                                    .height(26.dp)
                                    .background(Color(0xFF2E1C14))
                                    .border(0.5.dp, Color(0xFF4A3528))
                            )

                            Row(
                                modifier = Modifier.width(180.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E140F))
                                        .border((12 * (1f - progressRatio * 0.7f)).coerceAtLeast(3f).dp, Color(0xFF3B2519), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .graphicsLayer { rotationZ = if (isPlaying) rotation * 2.5f else 0f }
                                    ) {
                                        for (deg in 0 until 3) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer { rotationZ = deg * 60f }
                                                    .padding(horizontal = 11.dp)
                                                    .background(Color(0xFF1A1A1E))
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.width(28.dp).height(2.dp).background(Color.White.copy(0.4f)))
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${(progressRatio * 100).toInt()}%",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(0.7f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(modifier = Modifier.width(28.dp).height(2.dp).background(Color.White.copy(0.4f)))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E140F))
                                        .border((3f + 12 * (progressRatio * 0.7f)).dp, Color(0xFF3B2519), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .graphicsLayer { rotationZ = if (isPlaying) rotation * 2.5f else 0f }
                                    ) {
                                        for (deg in 0 until 3) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer { rotationZ = deg * 60f }
                                                    .padding(horizontal = 11.dp)
                                                    .background(Color(0xFF1A1A1E))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .width(140.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(Color(0xFF222226))
                                .border(0.8.dp, Color.White.copy(0.1f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF888890)))
                            Box(modifier = Modifier.width(36.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3E3E46)))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF888890)))
                        }
                    }
                }
            } else if (artworkStyle == "CYBER_ORB") {
                // 3. Cyber Neon Orb
                val pulseGlow by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "cyberGlow"
                )
                Box(
                    modifier = Modifier.size(290.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(285.dp * (if (isPlaying) pulseGlow else 1f))
                            .graphicsLayer { rotationZ = if (isPlaying) rotation * 1.6f else 0f }
                            .clip(CircleShape)
                            .border(
                                2.5.dp,
                                Brush.sweepGradient(listOf(c.accentBlue, Color(0xFF00F5D4), Color(0xFF7B2CBF), c.accentBlue)),
                                CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(255.dp)
                            .graphicsLayer { rotationZ = if (isPlaying) -rotation * 1.2f else 0f }
                            .clip(CircleShape)
                            .border(
                                1.5.dp,
                                Brush.sweepGradient(listOf(Color(0xFFFF007F), Color.Transparent, Color(0xFF00F5D4), Color.Transparent)),
                                CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(28.dp, CircleShape, ambientColor = c.accentBlue, spotColor = Color(0xFF00F5D4))
                            .clip(CircleShape)
                            .border(3.dp, Color.White, CircleShape)
                    ) {
                        MusicAlbumArtImage(
                            artUri = song.albumArtUri,
                            iconSize = 64.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (artworkStyle == "WAVE") {
                // 4. Waveform Stage
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = c.accentBlue.copy(0.6f))
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.glassBg)
                            .border(1.5.dp, c.glassBorder, RoundedCornerShape(24.dp))
                    ) {
                        MusicAlbumArtImage(
                            artUri = song.albumArtUri,
                            iconSize = 72.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    val wavePhase by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = (2 * Math.PI).toFloat(),
                        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
                        label = "wavePhase"
                    )

                    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp)) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f
                        val amp = if (isPlaying) height * 0.42f else height * 0.08f

                        val path1 = androidx.compose.ui.graphics.Path()
                        path1.moveTo(0f, midY)
                        for (x in 0..width.toInt() step 4) {
                            val normX = x / width
                            val y = midY + kotlin.math.sin(normX * 3.5 * Math.PI + wavePhase).toFloat() * amp
                            path1.lineTo(x.toFloat(), y)
                        }
                        drawPath(
                            path = path1,
                            color = Color(0xFF00F5D4),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )

                        val path2 = androidx.compose.ui.graphics.Path()
                        path2.moveTo(0f, midY)
                        for (x in 0..width.toInt() step 4) {
                            val normX = x / width
                            val y = midY + kotlin.math.sin(normX * 4.0 * Math.PI - wavePhase * 1.3f).toFloat() * (amp * 0.8f)
                            path2.lineTo(x.toFloat(), y)
                        }
                        drawPath(
                            path = path2,
                            color = Color(0xFF007AFF),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )

                        val path3 = androidx.compose.ui.graphics.Path()
                        path3.moveTo(0f, midY)
                        for (x in 0..width.toInt() step 4) {
                            val normX = x / width
                            val y = midY + kotlin.math.sin(normX * 2.5 * Math.PI + wavePhase * 0.7f).toFloat() * (amp * 0.6f)
                            path3.lineTo(x.toFloat(), y)
                        }
                        drawPath(
                            path = path3,
                            color = Color(0xFFFF007F).copy(alpha = 0.85f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            } else {
                // 5. High-End 3D Frosted Glass Album Card ("CARD")
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .shadow(36.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = c.accentBlue.copy(0.5f))
                        .clip(RoundedCornerShape(28.dp))
                        .background(c.glassBg)
                        .border(1.5.dp, c.glassBorder, RoundedCornerShape(28.dp))
                ) {
                    MusicAlbumArtImage(
                        artUri = song.albumArtUri,
                        iconSize = 88.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (artworkStyle != "WAVE") {
                Spacer(Modifier.height(30.dp))
                BeatVisualizer(
                    isPlaying = isPlaying,
                    barCount = 28,
                    height = 54.dp,
                    withReflection = true
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Track Info & Heart Favorite ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        song.artist,
                        color = Color.White.copy(0.7f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White.copy(0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Seekbar ────────────────────────────────────────────────────
            MusicSeekBar(currentPosition = currentPosition, duration = duration, onSeek = onSeek)

            Spacer(Modifier.height(14.dp))

            // ─── Playback Controls: Shuffle, Prev, Play/Pause, Next, Repeat ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) c.accentBlue else Color.White.copy(0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Track
                IconButton(onClick = onPrev) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Main Radiant Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(20.dp, CircleShape, ambientColor = c.accentBlue, spotColor = c.accentBlue)
                        .clip(CircleShape)
                        .background(c.accentBlue)
                        .bounceClick(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = c.onAccent,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Next Track
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(onClick = onToggleRepeat) {
                    val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    val repeatTint = if (repeatMode != Player.REPEAT_MODE_OFF) c.accentBlue else Color.White.copy(0.5f)
                    Icon(
                        repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ─── Utility Bottom Controls: Speed, Sleep Timer, Queue ─────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Button
                TextButton(onClick = onOpenSpeed) {
                    Text(
                        "${playbackSpeed}x",
                        color = if (playbackSpeed != 1.0f) c.accentBlue else Color.White.copy(0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sleep Timer Button
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerSecondsRemaining > 0) c.accentBlue else Color.White.copy(0.7f)
                    )
                }

                // Up Next Queue Button
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White.copy(0.85f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // ─── Brightness Gesture Bar (Left) ──────────────────────────────────
        AnimatedVisibility(
            visible = showBrightnessBar,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
        ) {
            MusicVerticalGestureBar(
                icon = Icons.Rounded.LightMode,
                percentage = brightnessPct,
                label = "${(brightnessPct * 100).toInt()}%",
                gradient = Brush.verticalGradient(listOf(c.accentBlue, c.accentBlue.copy(0.7f))),
                glowColor = c.accentBlue,
                onValueChange = {
                    brightnessPct = it.coerceIn(0.01f, 1f)
                    showBrightnessBar = true
                    brightnessTouchTrigger = System.currentTimeMillis()
                }
            )
        }

        // ─── Volume Gesture Bar (Right) ─────────────────────────────────────
        AnimatedVisibility(
            visible = showVolumeBar,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
        ) {
            MusicVerticalGestureBar(
                icon = if (volumePct <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                percentage = volumePct,
                label = if (volumePct <= 0f) "Muted" else "${(volumePct * 100).toInt()}%",
                gradient = Brush.verticalGradient(listOf(c.accentBlue, c.accentBlue.copy(0.7f))),
                glowColor = c.accentBlue,
                onValueChange = {
                    val newRatio = it.coerceIn(0f, 1f)
                    volumePct = newRatio
                    val newVol = (newRatio * maxVolume).roundToInt().coerceIn(0, maxVolume)
                    if (newVol != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                    }
                    showVolumeBar = true
                    volumeTouchTrigger = System.currentTimeMillis()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MUSIC VERTICAL GESTURE BAR COMPONENT
// ─────────────────────────────────────────────────────────────────
@Composable
private fun MusicVerticalGestureBar(
    icon: ImageVector,
    percentage: Float,
    label: String,
    gradient: Brush,
    glowColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedPct = percentage.coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = clampedPct,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "smoothMusicGesturePct"
    )

    Box(
        modifier = modifier
            .width(48.dp)
            .height(200.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = glowColor, spotColor = glowColor)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.2.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dragRatio = -dragAmount.y / size.height
                    onValueChange(clampedPct + dragRatio)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Vertical Fill Capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(animatedPct)
                .clip(RoundedCornerShape(24.dp))
                .background(gradient)
        )

        // Inside Indicator: Top Icon & Bottom Percentage
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// VISUAL STYLE THEMES & PICKER DIALOG
// ─────────────────────────────────────────────────────────────────
data class VisualStyleOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

val visualStylesList = listOf(
    VisualStyleOption("VINYL", "Vinyl Record", "Classic spinning vinyl with realistic grooves", Icons.Rounded.Album),
    VisualStyleOption("CARD", "Glass 3D Card", "Elevated frosted glass card with ambient glow", Icons.Rounded.CropPortrait),
    VisualStyleOption("CASSETTE", "Retro Cassette", "Authentic 80s tape with spinning spools & ribbon", Icons.Rounded.GraphicEq),
    VisualStyleOption("CYBER_ORB", "Cyber Neon Orb", "Futuristic pulsing audio ring with orbital beams", Icons.Rounded.Radio),
    VisualStyleOption("WAVE", "Waveform Stage", "Dynamic flowing neon audio waveform visualizer", Icons.Rounded.Waves)
)

@Composable
private fun VisualStylePickerDialog(
    currentStyle: String,
    onSelectStyle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.dropdownBg)
                .border(1.2.dp, c.glassBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Player Visual Theme",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))

            visualStylesList.forEach { option ->
                val isSelected = option.id.equals(currentStyle, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) c.accentBlue.copy(alpha = 0.18f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) c.accentBlue.copy(alpha = 0.6f) else Color.White.copy(0.06f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onSelectStyle(option.id)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) c.accentBlue else Color.White.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            option.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) c.accentBlue else c.textPrimary
                        )
                        Text(
                            text = option.subtitle,
                            fontSize = 11.sp,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = c.accentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// EQUALIZER & AUDIO EFFECTS DIALOG
// ─────────────────────────────────────────────────────────────────
@Composable
private fun EqualizerDialog(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    selectedPreset: String,
    presets: List<MusicEqPreset>,
    bandLevels: List<Float>,
    bassStrength: Float,
    virtualizerStrength: Float,
    onPresetSelected: (MusicEqPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassChange: (Float) -> Unit,
    onVirtualizerChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val bandFreqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.dropdownBg)
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = c.accentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("5-Band Equalizer", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.accentBlue)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Presets Horizontal Row
            Text("PRESETS", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { preset ->
                    val isSelected = selectedPreset == preset.name
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) c.accentBlue else c.glassBg)
                            .border(0.5.dp, c.glassBorder, CircleShape)
                            .clickable(enabled = enabled) { onPresetSelected(preset) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            preset.name,
                            color = if (isSelected) c.onAccent else c.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 5 Band Sliders
            Text("FREQUENCY BANDS", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            bandLevels.forEachIndexed { index, gainDb ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        bandFreqLabels.getOrElse(index) { "Band $index" },
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.width(55.dp)
                    )
                    Slider(
                        value = gainDb,
                        onValueChange = { onBandChange(index, it) },
                        valueRange = -10f..10f,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(activeTrackColor = c.accentBlue, thumbColor = Color.White)
                    )
                    Text(
                        "${if (gainDb >= 0) "+" else ""}${gainDb.toInt()}dB",
                        color = c.textPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bass Boost & Virtualizer
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bass Boost", color = c.textSecondary, fontSize = 12.sp)
                    Slider(
                        value = bassStrength,
                        onValueChange = onBassChange,
                        enabled = enabled,
                        colors = SliderDefaults.colors(activeTrackColor = c.accentBlue, thumbColor = Color.White)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("3D Virtualizer", color = c.textSecondary, fontSize = 12.sp)
                    Slider(
                        value = virtualizerStrength,
                        onValueChange = onVirtualizerChange,
                        enabled = enabled,
                        colors = SliderDefaults.colors(activeTrackColor = c.accentBlue, thumbColor = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White)
            ) {
                Text("Done")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// QUEUE DIALOG
// ─────────────────────────────────────────────────────────────────
@Composable
private fun QueueDialog(
    queue: List<MusicItem>,
    currentSong: MusicItem?,
    isPlaying: Boolean,
    onSelectSong: (MusicItem) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(26.dp))
                .background(c.dropdownBg)
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = c.accentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Up Next (${queue.size})", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(queue, key = { "queue_${it.id}" }) { song ->
                    val isCurrent = song.id == currentSong?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) c.accentBlue.copy(0.18f) else Color.Transparent)
                            .border(0.5.dp, if (isCurrent) c.accentBlue.copy(0.4f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { onSelectSong(song) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicAlbumArtImage(
                            artUri = song.albumArtUri,
                            iconSize = 18.dp,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = if (isCurrent) c.accentBlue else c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            BeatVisualizer(isPlaying = isPlaying, barCount = 4, height = 18.dp, withReflection = false)
                        } else {
                            Text(formatTime(song.duration), color = c.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SLEEP TIMER DIALOG
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SleepTimerDialog(
    currentRemainingSeconds: Int,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val options = listOf(15, 30, 45, 60, 90)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.dropdownBg)
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = c.accentBlue)
                Spacer(Modifier.width(8.dp))
                Text("Sleep Timer", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text("Music will automatically pause when the timer expires.", color = c.textSecondary, fontSize = 12.sp)

            Spacer(Modifier.height(18.dp))

            options.forEach { mins ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.glassBg)
                        .clickable { onSetTimer(mins) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$mins minutes", color = c.textPrimary, fontSize = 14.sp)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
            }

            if (currentRemainingSeconds > 0) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onCancelTimer,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.8f), contentColor = Color.White)
                ) {
                    Text("Turn Off Timer")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SPEED SELECTOR DIALOG
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SpeedSelectorDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.dropdownBg)
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = c.accentBlue)
                Spacer(Modifier.width(8.dp))
                Text("Playback Speed", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))

            speeds.forEach { speed ->
                val isSelected = currentSpeed == speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) c.accentBlue.copy(0.2f) else c.glassBg)
                        .border(0.5.dp, if (isSelected) c.accentBlue else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onSelectSpeed(speed) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${speed}x ${if (speed == 1.0f) "(Normal)" else ""}",
                        color = if (isSelected) c.accentBlue else c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SONG INFO DIALOG
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SongInfoDialog(song: MusicItem, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.dropdownBg)
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = c.accentBlue)
                Spacer(Modifier.width(8.dp))
                Text("Track Details", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))

            InfoRow("Title", song.title)
            InfoRow("Artist", song.artist)
            InfoRow("Album", song.album)
            InfoRow("Duration", formatTime(song.duration))
            InfoRow("File Size", formatSize(song.size))
            InfoRow("URI", song.uri.toString())

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White)
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = c.textPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────
// BEAT VISUALIZER
// ─────────────────────────────────────────────────────────────────
private val spectrumColors = listOf(
    Color(0xFF7C3AED), Color(0xFF6D28D9), Color(0xFF2563EB), Color(0xFF0891B2),
    Color(0xFF059669), Color(0xFF65A30D), Color(0xFFF59E0B), Color(0xFFEA580C),
    Color(0xFFDC2626), Color(0xFFDB2777), Color(0xFF7C3AED)
)

private fun spectrumColor(fraction: Float): Color {
    val scaled = fraction * (spectrumColors.size - 1)
    val lo = scaled.toInt().coerceIn(0, spectrumColors.size - 2)
    val hi = lo + 1
    val t = scaled - lo
    val a = spectrumColors[lo]
    val b = spectrumColors[hi]
    return Color(
        red   = a.red   + (b.red   - a.red)   * t,
        green = a.green + (b.green - a.green) * t,
        blue  = a.blue  + (b.blue  - a.blue)  * t
    )
}

@Composable
fun BeatVisualizer(
    isPlaying: Boolean,
    barCount: Int = 24,
    height: Dp = 64.dp,
    withReflection: Boolean = true,
    barWidthDp: Dp = 5.dp,
    gapDp: Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beatVis")

    val durations = remember(barCount) {
        List(barCount) { i ->
            val posNorm = i.toFloat() / (barCount - 1)
            val shapeFactor = 1f - kotlin.math.abs(posNorm - 0.35f) * 1.4f
            val baseDuration = (180 + (shapeFactor * 220).toInt()).coerceIn(140, 500)
            (baseDuration + (-60..60).random()).coerceIn(120, 600)
        }
    }

    val initialValues = remember(barCount) {
        List(barCount) { 0.08f + (kotlin.math.sin(it * 1.3) * 0.25f).toFloat().coerceAtLeast(0f) }
    }
    val targetValues = remember(barCount) {
        List(barCount) { i ->
            val posNorm = i.toFloat() / (barCount - 1)
            val envPeak  = 0.5f + 0.5f * kotlin.math.sin(kotlin.math.PI * posNorm).toFloat()
            (0.55f + envPeak * 0.45f).coerceIn(0.55f, 1f)
        }
    }

    val scaleYValues = (0 until barCount).map { i ->
        infiniteTransition.animateFloat(
            initialValue = initialValues[i],
            targetValue  = targetValues[i],
            animationSpec = infiniteRepeatable(
                animation   = tween(durations[i], easing = FastOutSlowInEasing),
                repeatMode  = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    val totalHeight = if (withReflection) height * 1.55f else height

    Row(
        horizontalArrangement = Arrangement.spacedBy(gapDp),
        verticalAlignment     = Alignment.Bottom,
        modifier              = Modifier.height(totalHeight)
    ) {
        for (i in 0 until barCount) {
            val fraction   = i.toFloat() / (barCount - 1)
            val barColor   = spectrumColor(fraction)
            val rawScale   = scaleYValues[i].value
            val activeScale = if (isPlaying) rawScale else 0.06f

            val barGradient = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    barColor,
                    barColor.copy(alpha = 0.7f)
                )
            )
            val reflectionGradient = Brush.verticalGradient(
                colors = listOf(
                    barColor.copy(alpha = 0.30f),
                    Color.Transparent
                )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .width(barWidthDp)
                        .fillMaxHeight(activeScale)
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        barColor.copy(alpha = 0.55f * activeScale),
                                        Color.Transparent
                                    )
                                ),
                                size = this.size.copy(
                                    width  = this.size.width * 3.5f,
                                    height = this.size.height
                                ),
                                topLeft = Offset(x = -this.size.width * 1.25f, y = 0f)
                            )
                        }
                        .clip(RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp))
                        .background(barGradient)
                )

                if (withReflection) {
                    Box(
                        modifier = Modifier
                            .width(barWidthDp)
                            .fillMaxHeight(activeScale * 0.35f)
                            .graphicsLayer { scaleY = -1f }
                            .clip(RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp))
                            .background(reflectionGradient)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MUSIC SEEKBAR
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    Column(modifier = modifier) {
        Slider(
            value = if (isDragging) sliderPosition else currentPosition.toFloat(),
            onValueChange = { isDragging = true; sliderPosition = it },
            onValueChangeFinished = { isDragging = false; onSeek(sliderPosition.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = LocalAppColors.current.accentBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(sliderPosition.toLong()), color = Color.White.copy(0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(formatTime(duration), color = Color.White.copy(0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MUSIC ALBUM ART — with animated music-note fallback
// ─────────────────────────────────────────────────────────────────
@Composable
fun MusicAlbumArtImage(
    artUri: android.net.Uri?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: androidx.compose.ui.unit.Dp = 36.dp
) {
    val c = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "musicIconPulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconAlpha"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "iconScale"
    )

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(artUri)
            .build(),
        contentDescription = "Album Art",
        contentScale = contentScale,
        modifier = modifier,
        error = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF1E2136), Color(0xFF0E0F1A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = c.accentBlue.copy(alpha = iconAlpha),
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                )
            }
        },
        loading = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color(0xFF12131A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = c.accentBlue.copy(0.35f),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    )
}
