package com.example.ymediaplayer.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
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
    onFullScreenChanged: (Boolean) -> Unit = {},
    hasMediaPermission: Boolean = true,
    onRequestPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
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
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            kotlinx.coroutines.delay(150)
            try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    var selectedCategory by remember { mutableStateOf("Tracks") } // "Explore", "Tracks", "Artists", "Albums", "Favorites"
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<Long?>(null) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf(appPreferences.getPlaylists()) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForContextMenu by remember { mutableStateOf<MusicItem?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<MusicItem?>(null) }
    var priorityNextSong by remember { mutableStateOf<MusicItem?>(null) }
    var mostPlayedSongs by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var sortOrder by remember { mutableStateOf("TITLE") } // "TITLE", "ARTIST", "DURATION"
    var showSortMenu by remember { mutableStateOf(false) }

    // Handle back button when searching or viewing an artist, album, or playlist drilldown
    BackHandler(enabled = isSearching || searchQuery.isNotEmpty() || selectedArtist != null || selectedAlbum != null || selectedPlaylist != null) {
        if (selectedArtist != null) {
            selectedArtist = null
        } else if (selectedAlbum != null) {
            selectedAlbum = null
        } else if (selectedPlaylist != null) {
            selectedPlaylist = null
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (isSearching) {
            isSearching = false
        }
    }

    // Sheets & Dialogs
    var showVisualStyleDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }

    // Sleep Timer
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerSecondsRemaining by remember { mutableIntStateOf(0) }

    // Audio Effects (loaded from persistent AppPreferences)
    var eqEnabled by remember { mutableStateOf(appPreferences.isEqEnabled()) }
    var selectedPresetName by remember { mutableStateOf(appPreferences.getEqPreset()) }
    var bandLevels by remember { mutableStateOf(appPreferences.getEqBands()) }
    var bassBoostStrength by remember { mutableFloatStateOf(appPreferences.getEqBassBoost()) }
    var virtualizerStrength by remember { mutableFloatStateOf(appPreferences.getEqVirtualizer()) }
    var activeAudioSessionId by remember { mutableIntStateOf(MusicService.currentAudioSessionId) }

    fun persistEq() {
        appPreferences.saveEqSettings(
            preset = selectedPresetName,
            bands = bandLevels,
            bassBoost = bassBoostStrength,
            virt = virtualizerStrength,
            enabled = eqEnabled
        )
    }

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
        persistEq()
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
        persistEq()
    }

    fun applyBassBoost(value: Float) {
        bassBoostStrength = value
        val (_, bass, _) = audioEffects
        if (bass != null && eqEnabled) {
            try {
                bass.setStrength((value * 1000).toInt().toShort())
            } catch (_: Exception) {}
        }
        persistEq()
    }

    fun applyVirtualizer(value: Float) {
        virtualizerStrength = value
        val (_, _, virt) = audioEffects
        if (virt != null && eqEnabled) {
            try {
                virt.setStrength((value * 1000).toInt().toShort())
            } catch (_: Exception) {}
        }
        persistEq()
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
        com.example.ymediaplayer.player.VideoPlaybackManager.player?.let {
            if (it.isPlaying) it.pause()
        }
        currentlyPlaying = song
        MusicService.currentMusicItem = song
        appPreferences.incrementPlayCount(song.uri.toString())
        val artBytes = MusicService.resolveArtworkBytes(context, song.albumArtUri)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .apply {
                if (artBytes != null) {
                    setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
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
        MusicService.serviceInstance?.updateSessionCustomLayout()
    }

    // Keep most played songs shelf updated
    LaunchedEffect(allSongs, currentlyPlaying) {
        val topUris = appPreferences.getTopPlayedUris(10)
        mostPlayedSongs = topUris.mapNotNull { uriStr -> allSongs.find { it.uri.toString() == uriStr } }
    }

    val playNext: () -> Unit = {
        if (priorityNextSong != null) {
            val nextSong = priorityNextSong!!
            priorityNextSong = null
            playSongForce(nextSong)
        } else if (allSongs.isNotEmpty()) {
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

    val playPrev: () -> Unit = {
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
            MusicService.onPlayNextAction = null
            MusicService.onPlayPrevAction = null
            MusicService.onPauseAction = null
            MusicService.onStopAction = null
            MusicService.onTogglePlayPauseAction = null
        }
    }

    // ─── Sync now-playing state to MusicService companion so Video tab can show indicator & controls ───
    LaunchedEffect(currentlyPlaying, isPlaying, allSongs) {
        MusicService.isMusicPlaying.value = isPlaying && currentlyPlaying != null
        MusicService.nowPlayingTitle.value = currentlyPlaying?.title ?: ""
        MusicService.nowPlayingArtist.value = currentlyPlaying?.artist ?: ""
        MusicService.nowPlayingArtUri.value = currentlyPlaying?.albumArtUri
        if (allSongs.isNotEmpty()) {
            MusicService.currentPlaylist = allSongs
            MusicService.currentSongIndex = allSongs.indexOfFirst { it.id == currentlyPlaying?.id }
        }
        MusicService.onPlayNextAction = playNext
        MusicService.onPlayPrevAction = playPrev
        MusicService.onPauseAction = {
            mediaController?.pause()
        }
        MusicService.onStopAction = {
            mediaController?.stop()
        }
        MusicService.onTogglePlayPauseAction = {
            if (mediaController?.isPlaying == true) {
                mediaController?.pause()
            } else {
                com.example.ymediaplayer.player.VideoPlaybackManager.player?.let {
                    if (it.isPlaying) it.pause()
                }
                mediaController?.play()
            }
        }
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

    val searchMatchingSongs = remember(allSongs, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }
    val searchMatchingArtists = remember(topArtists, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else topArtists.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }
    val searchMatchingAlbums = remember(featuredAlbums, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else featuredAlbums.filter { (_, songs) ->
            val albumName = songs.firstOrNull()?.album ?: ""
            val artistName = songs.firstOrNull()?.artist ?: ""
            albumName.contains(searchQuery, ignoreCase = true) || artistName.contains(searchQuery, ignoreCase = true)
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
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = c.accentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearching = false
                                    }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Close", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), RoundedCornerShape(14.dp))
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Your Music",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "· ${allSongs.size} tracks",
                                fontSize = 13.sp,
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

                // ─── 6 Modern Online Category Pills ───────────────────────────
                if (!isSearching && searchQuery.isEmpty()) {
                    val categories = listOf(
                        "Explore" to Icons.Rounded.AutoAwesome,
                        "Tracks" to Icons.Rounded.MusicNote,
                        "Artists" to Icons.Rounded.Person,
                        "Albums" to Icons.Rounded.Album,
                        "Playlists" to Icons.AutoMirrored.Rounded.PlaylistPlay,
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
                                "Playlists" -> " (${playlists.size})"
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
                        Text(if (hasMediaPermission) "No Music Files Found" else "Audio Access Needed", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (hasMediaPermission) "Add audio files to your device storage to start listening" else "Allow audio access to browse and play music on this device",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (!hasMediaPermission) {
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White)
                            ) {
                                Text("Allow Access")
                            }
                        }
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
                                    .background(c.cardBg)
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
                            onLongClick = { songForContextMenu = song },
                            onMoreClick = { songForContextMenu = song },
                            onClick = {
                                if (!isCurrent) playSongForce(song)
                                else if (isPlaying) mediaController?.pause() else mediaController?.play()
                            }
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                // ─── INSTANT SEARCH RESULTS VIEW ─────────────────────────────
                val totalResults = searchMatchingSongs.size + searchMatchingArtists.size + searchMatchingAlbums.size
                if (totalResults == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (currentlyPlaying != null) 175.dp else 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(c.cardBgElevated)
                                    .border(1.2.dp, c.glassBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(36.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No results for \"$searchQuery\"",
                                color = c.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Check your spelling or try searching for another track, artist, or album",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    isSearching = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Clear Search", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                    ) {
                        // Header summary + Play / Shuffle actions
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Search Results",
                                            color = c.textPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${searchMatchingSongs.size} tracks · ${searchMatchingArtists.size} artists · ${searchMatchingAlbums.size} albums",
                                            color = c.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (searchMatchingSongs.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    searchMatchingSongs.firstOrNull()?.let { playSongForce(it) }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    searchMatchingSongs.shuffled().firstOrNull()?.let { playSongForce(it) }
                                                },
                                                border = BorderStroke(1.dp, c.accentBlue.copy(0.4f)),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accentBlue),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Shuffle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Matching Artists Section (Horizontal Shelf)
                        if (searchMatchingArtists.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "ARTISTS (${searchMatchingArtists.size})",
                                    color = c.accentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(searchMatchingArtists, key = { "srch_art_${it.first}" }) { (artistName, songs) ->
                                        val firstArt = songs.firstOrNull()?.albumArtUri
                                        val cardShape = RoundedCornerShape(16.dp)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(100.dp)
                                                .shadow(4.dp, cardShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                                .clip(cardShape)
                                                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), cardShape)
                                                .clickable { selectedArtist = artistName }
                                                .padding(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .shadow(4.dp, CircleShape)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF141520))
                                                    .border(1.2.dp, c.cardBorderHighlight, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                MusicAlbumArtImage(artUri = firstArt, iconSize = 28.dp, modifier = Modifier.fillMaxSize())
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                artistName,
                                                color = c.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                "${songs.size} songs",
                                                color = c.textSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Matching Albums Section (Horizontal Shelf)
                        if (searchMatchingAlbums.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "ALBUMS (${searchMatchingAlbums.size})",
                                    color = c.accentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(searchMatchingAlbums, key = { "srch_alb_${it.first}" }) { (albumId, songs) ->
                                        val firstSong = songs.firstOrNull()
                                        val albumTitle = firstSong?.album?.ifBlank { "Unknown Album" } ?: "Unknown Album"
                                        val artistName = firstSong?.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist"
                                        val cardShape = RoundedCornerShape(16.dp)
                                        Column(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .shadow(4.dp, cardShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                                .clip(cardShape)
                                                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), cardShape)
                                                .clickable { selectedAlbum = albumId }
                                                .padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF141520)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                MusicAlbumArtImage(artUri = firstSong?.albumArtUri, iconSize = 36.dp, modifier = Modifier.fillMaxSize())
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                albumTitle,
                                                color = c.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                artistName,
                                                color = c.textSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${songs.size} tracks",
                                                color = c.accentBlue,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Matching Songs Section (Vertical List)
                        if (searchMatchingSongs.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "SONGS (${searchMatchingSongs.size})",
                                    color = c.accentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(searchMatchingSongs, key = { "srch_song_${it.id}" }) { song ->
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
                                    onLongClick = { songForContextMenu = song },
                                    onMoreClick = { songForContextMenu = song },
                                    onClick = {
                                        if (!isCurrent) playSongForce(song)
                                        else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                    }
                                )
                            }
                        }
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

                        // Shelf: MOST PLAYED TRACKS (Play count analytics shelf)
                        if (mostPlayedSongs.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("MOST PLAYED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 1.sp)
                                }
                            }
                            items(mostPlayedSongs.take(6), key = { "top_played_${it.id}" }) { song ->
                                val isCurrent = currentlyPlaying?.id == song.id
                                val isFav = favorites.contains(song.uri.toString())
                                val count = appPreferences.getPlayCount(song.uri.toString())
                                MusicListItem(
                                    song = song,
                                    isCurrentlyPlaying = isCurrent,
                                    isPlaying = isPlaying && isCurrent,
                                    isFavorite = isFav,
                                    playCountBadge = if (count > 0) "$count plays" else null,
                                    onFavoriteToggle = {
                                        appPreferences.toggleMusicFavorite(song.uri.toString())
                                        favorites = appPreferences.getMusicFavorites()
                                    },
                                    onLongClick = { songForContextMenu = song },
                                    onMoreClick = { songForContextMenu = song },
                                    onClick = {
                                        if (!isCurrent) playSongForce(song)
                                        else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                    }
                                )
                            }
                            item { Spacer(Modifier.height(14.dp)) }
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
                                    onLongClick = { songForContextMenu = song },
                                    onMoreClick = { songForContextMenu = song },
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
                                onLongClick = { songForContextMenu = song },
                                onMoreClick = { songForContextMenu = song },
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
                                    onLongClick = { songForContextMenu = song },
                                    onMoreClick = { songForContextMenu = song },
                                    onClick = {
                                        if (!isCurrent) playSongForce(song)
                                        else if (isPlaying) mediaController?.pause() else mediaController?.play()
                                    }
                                )
                            }
                        }
                    }
                }

                // ─── 6. PLAYLISTS (CUSTOM USER PLAYLISTS) ────────────────────
                "Playlists" -> {
                    if (selectedPlaylist == null) {
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
                                        .shadow(elevation = 8.dp, shape = heroShape, ambientColor = c.accentBlue.copy(0.3f), spotColor = c.accentBlue.copy(0.4f))
                                        .clip(heroShape)
                                        .background(Brush.linearGradient(listOf(c.accentBlue.copy(0.45f), c.cardBgElevated, c.cardBg)))
                                        .border(1.4.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), heroShape)
                                        .padding(18.dp)
                                ) {
                                    Column {
                                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("Playlists", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        Text("${playlists.size} custom playlists created", color = Color.White.copy(0.8f), fontSize = 12.sp)
                                        Spacer(Modifier.height(14.dp))
                                        Button(
                                            onClick = { showCreatePlaylistDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("New Playlist", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            if (playlists.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        Text("No playlists created yet. Tap 'New Playlist' or long-press any track to add to a playlist.", color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                items(playlists.keys.toList(), key = { "pl_$it" }) { plName ->
                                    val uris = playlists[plName] ?: emptyList()
                                    val plSongs = uris.mapNotNull { uriStr -> allSongs.find { it.uri.toString() == uriStr } }
                                    val itemShape = RoundedCornerShape(18.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 5.dp)
                                            .shadow(4.dp, itemShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                            .clip(itemShape)
                                            .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                            .border(1.3.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), itemShape)
                                            .clickable { selectedPlaylist = plName }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(c.accentBlue.copy(0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (plSongs.isNotEmpty()) {
                                                MusicAlbumArtImage(artUri = plSongs.first().albumArtUri, iconSize = 22.dp, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(plName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${plSongs.size} tracks", fontSize = 12.sp, color = c.textSecondary)
                                        }
                                        if (plSongs.isNotEmpty()) {
                                            IconButton(onClick = { playSongForce(plSongs.first()) }) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = c.accentBlue)
                                            }
                                        }
                                        IconButton(onClick = {
                                            appPreferences.deletePlaylist(plName)
                                            playlists = appPreferences.getPlaylists()
                                        }) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = c.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Sub-screen for active playlist drilldown
                        val currentPlName = selectedPlaylist!!
                        val uris = playlists[currentPlName] ?: emptyList()
                        val plSongs = uris.mapNotNull { uriStr -> allSongs.find { it.uri.toString() == uriStr } }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = if (currentlyPlaying != null) 175.dp else 120.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { selectedPlaylist = null }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(currentPlName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                        Text("${plSongs.size} tracks", fontSize = 12.sp, color = c.textSecondary)
                                    }
                                    if (plSongs.isNotEmpty()) {
                                        IconButton(onClick = { playSongForce(plSongs.random()) }) {
                                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = c.accentBlue)
                                        }
                                    }
                                }
                            }
                            if (plSongs.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        Text("This playlist is empty. Use the more options (⋮) on any song to add tracks.", color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                items(plSongs, key = { "pl_song_${it.id}" }) { song ->
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
                                        onLongClick = { songForContextMenu = song },
                                        onMoreClick = { songForContextMenu = song },
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
        }

        // ─── Floating Mini Player ───────────────────────────────────────────
        AnimatedVisibility(
            visible = currentlyPlaying != null && !showFullScreenPlayer,
            enter = fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180)),
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
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200))
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
                    onToggleShuffle = { 
                        view.performHaptic(HapticType.LIGHT)
                        isShuffle = !isShuffle 
                    },
                    onToggleRepeat = {
                        view.performHaptic(HapticType.LIGHT)
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },
                    onToggleArtworkStyle = {
                        val styles = listOf("VINYL", "CARD", "SONIC_REACTOR", "CASSETTE", "CYBER_ORB", "WAVE")
                        val currentIdx = styles.indexOf(artworkStyle.uppercase())
                        val nextIdx = if (currentIdx == -1) 0 else (currentIdx + 1) % styles.size
                        val newStyle = styles[nextIdx]
                        artworkStyle = newStyle
                        appPreferences.setMusicArtworkStyle(newStyle)
                        val themeName = when (newStyle) {
                            "VINYL" -> "Vinyl Record"
                            "CARD" -> "Glass 3D Card"
                            "SONIC_REACTOR" -> "Sonic Reactor"
                            "CASSETTE" -> "Retro Cassette"
                            "CYBER_ORB" -> "Cyber Neon Orb"
                            "WAVE" -> "Waveform Stage"
                            else -> newStyle
                        }
                        android.widget.Toast.makeText(context, "Visual Theme: $themeName", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onFavoriteToggle = {
                        view.performHaptic(HapticType.MEDIUM)
                        appPreferences.toggleMusicFavorite(song.uri.toString())
                        favorites = appPreferences.getMusicFavorites()
                    },
                    onOpenQueue = { view.performHaptic(HapticType.LIGHT); showQueueSheet = true },
                    onOpenVisualStyleDialog = { view.performHaptic(HapticType.LIGHT); showVisualStyleDialog = true },
                    onOpenSleepTimer = { view.performHaptic(HapticType.LIGHT); showSleepDialog = true },
                    onOpenSpeed = { view.performHaptic(HapticType.LIGHT); showSpeedDialog = true },
                    onOpenSongInfo = { view.performHaptic(HapticType.LIGHT); showSongInfoDialog = true },
                    onClose = { showFullScreenPlayer = false }
                )
            }
        }

        // ─── Visual Theme Picker Dialog ──────────────────────────────────
        if (showVisualStyleDialog) {
            VisualStylePickerDialog(
                currentStyle = artworkStyle,
                onSelectStyle = { newStyle ->
                    artworkStyle = newStyle
                    appPreferences.setMusicArtworkStyle(newStyle)
                    showVisualStyleDialog = false
                    val themeName = when (newStyle) {
                        "VINYL" -> "Vinyl Record"
                        "CARD" -> "Glass 3D Card"
                        "CASSETTE" -> "Retro Cassette"
                        "CYBER_ORB" -> "Cyber Neon Orb"
                        "WAVE" -> "Waveform Stage"
                        else -> newStyle
                    }
                    android.widget.Toast.makeText(context, "Theme: $themeName", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showVisualStyleDialog = false }
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

        // ─── Song Context Menu Sheet ─────────────────────────────────────────
        if (songForContextMenu != null) {
            val s = songForContextMenu!!
            SongActionMenuSheet(
                song = s,
                isFavorite = favorites.contains(s.uri.toString()),
                onPlayNext = {
                    priorityNextSong = s
                    songForContextMenu = null
                    android.widget.Toast.makeText(context, "Will play next: ${s.title}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onAddToPlaylist = {
                    songForAddToPlaylist = s
                    songForContextMenu = null
                },
                onToggleFavorite = {
                    appPreferences.toggleMusicFavorite(s.uri.toString())
                    favorites = appPreferences.getMusicFavorites()
                    songForContextMenu = null
                },
                onShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Check out \"${s.title}\" by ${s.artist}! 🎵")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                    songForContextMenu = null
                },
                onViewDetails = {
                    currentlyPlaying = s
                    showSongInfoDialog = true
                    songForContextMenu = null
                },
                onDismiss = { songForContextMenu = null }
            )
        }

        // ─── Create Playlist Dialog ──────────────────────────────────────────
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onCreate = { name ->
                    if (appPreferences.createPlaylist(name)) {
                        playlists = appPreferences.getPlaylists()
                        android.widget.Toast.makeText(context, "Playlist created: $name", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Playlist already exists or invalid", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    showCreatePlaylistDialog = false
                },
                onDismiss = { showCreatePlaylistDialog = false }
            )
        }

        // ─── Add To Playlist Dialog ──────────────────────────────────────────
        if (songForAddToPlaylist != null) {
            val s = songForAddToPlaylist!!
            AddToPlaylistDialog(
                song = s,
                playlists = playlists,
                onSelectPlaylist = { plName ->
                    appPreferences.addSongToPlaylist(plName, s.uri.toString())
                    playlists = appPreferences.getPlaylists()
                    android.widget.Toast.makeText(context, "Added to $plName", android.widget.Toast.LENGTH_SHORT).show()
                    songForAddToPlaylist = null
                },
                onCreateNew = {
                    showCreatePlaylistDialog = true
                },
                onDismiss = { songForAddToPlaylist = null }
            )
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
    playCountBadge: String? = null,
    onFavoriteToggle: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
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
            if (playCountBadge != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.accentBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        playCountBadge,
                        color = c.accentBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
        if (onMoreClick != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
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
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (totalDragY < -35f && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX)) {
                            onClick()
                        } else if (totalDragX < -50f) {
                            onNext()
                        } else if (totalDragX > 50f) {
                            onPrev()
                        }
                    }
                )
            }
            .clickable(onClick = onClick)
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
    onOpenVisualStyleDialog: () -> Unit = {},
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

    val infiniteTransition = rememberInfiniteTransition(label = "musicVisuals")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "spin_angle"
    )

    // Real-time Song-Reactive Audio Dynamics (zero mechanical jumping/bobbing)
    val liveEnergy by com.example.ymediaplayer.service.AudioReactor.energy
    val liveBass by com.example.ymediaplayer.service.AudioReactor.bassPulse
    val liveBands by com.example.ymediaplayer.service.AudioReactor.bands
    val liveWaveform by com.example.ymediaplayer.service.AudioReactor.waveformData

    val reactiveBass by animateFloatAsState(
        targetValue = if (isPlaying) liveBass else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "reactiveBass"
    )
    val reactiveEnergy by animateFloatAsState(
        targetValue = if (isPlaying) liveEnergy else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "reactiveEnergy"
    )

    val ambientGlowAlpha = if (isPlaying) (0.35f + reactiveEnergy * 0.45f).coerceIn(0.2f, 0.85f) else 0.12f

    // Turntable Tonearm Angle: 0f (at rest off-platter) -> 26f (sits on record grooves when playing)
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 26f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "tonearmAngle"
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
        // ─── Immersive Ambient Blurred Album Art Canvas ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090A10))
        ) {
            // Layer 1: Overscaled Album Art with Ultra-Smooth Gaussian Blur
            MusicAlbumArtImage(
                artUri = song.albumArtUri,
                iconSize = 72.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .blur(56.dp)
            )

            // Layer 2: Radial vignette focusing light toward center-bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x6608090F),
                                Color(0xDD08090F)
                            )
                        )
                    )
            )

            // Layer 3: Vertical gradient for crystal-clear readability of top/bottom controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xB308090F),
                                Color(0x3308090F),
                                Color(0xCC08090F)
                            )
                        )
                    )
            )
        }

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
                        text = "Now playing",
                        color = Color.White.copy(0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Visual Style Theme Picker Dialog
                    IconButton(onClick = onOpenVisualStyleDialog) {
                        val styleIcon = when (artworkStyle.uppercase()) {
                            "VINYL" -> Icons.Rounded.Album
                            "CARD" -> Icons.Rounded.CropPortrait
                            "SONIC_REACTOR" -> Icons.Rounded.GraphicEq
                            "CASSETTE" -> Icons.Rounded.Audiotrack
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
                    // Song Info / More
                    IconButton(onClick = onOpenSongInfo) {
                        Icon(Icons.Rounded.Info, contentDescription = "Info", tint = Color.White.copy(0.8f))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ─── Customizable Artwork: 6 Visual Themes with Double-Tap Like ───
            var showHeartBurst by remember { mutableStateOf(false) }
            val heartScale by animateFloatAsState(
                targetValue = if (showHeartBurst) 1.35f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "heartScale",
                finishedListener = { if (it > 0f) showHeartBurst = false }
            )
            val heartAlpha by animateFloatAsState(
                targetValue = if (showHeartBurst) 0.95f else 0f,
                animationSpec = tween(if (showHeartBurst) 100 else 400),
                label = "heartAlpha"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isFavorite) {
                                onFavoriteToggle()
                            }
                            showHeartBurst = true
                        }
                    )
                }
            ) {
                // ─── Real-Time Song-Reactive Ambient Glow & Resonance Rings ───
                // 1. Reactive Bass Glow Halo (no mechanical jumping timer)
                Box(
                    modifier = Modifier
                        .size(310.dp * (1f + reactiveBass * 0.14f))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    c.accentBlue.copy(alpha = ambientGlowAlpha),
                                    Color(0xFF00F5D4).copy(alpha = (ambientGlowAlpha * 0.45f * reactiveBass).coerceIn(0f, 0.45f)),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 2. Dynamic Audio Sonic Resonance Ring (ripples with beat/bass drop)
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(320.dp + (30.dp * reactiveBass))
                            .clip(CircleShape)
                            .border(
                                width = (1.5.dp + (1.5.dp * reactiveBass)),
                                brush = Brush.radialGradient(
                                    listOf(
                                        Color.Transparent,
                                        c.accentBlue.copy(alpha = (reactiveBass * 0.6f).coerceIn(0f, 0.7f)),
                                        Color(0xFF00F5D4).copy(alpha = (reactiveBass * 0.35f).coerceIn(0f, 0.5f)),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                if (artworkStyle == "VINYL") {
                    // 1. Spinning Vinyl Record with Realistic Turntable Tonearm
                    Box(
                        modifier = Modifier.size(320.dp, 290.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Vinyl Disc Platter
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .shadow(32.dp, CircleShape, ambientColor = Color.Black, spotColor = c.accentBlue.copy(0.45f))
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF141416),
                                            Color(0xFF0D0D0F),
                                            Color(0xFF060607)
                                        )
                                    )
                                )
                                .border(2.5.dp, Color.White.copy(0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Authentic Microgroove Rings
                            listOf(14.dp, 24.dp, 34.dp, 44.dp, 54.dp, 64.dp).forEach { pad ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(pad)
                                        .border(0.8.dp, Color.White.copy(0.045f), CircleShape)
                                )
                            }

                            // Dynamic anisotropic sweep sheen reflection
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = if (isPlaying) rotation * 0.4f else 45f }
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color.White.copy(0.06f),
                                                Color.Transparent,
                                                Color.White.copy(0.06f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Center Spinning Album Art Label
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .shadow(10.dp, CircleShape)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White.copy(0.25f), CircleShape)
                                    .graphicsLayer { rotationZ = if (isPlaying) rotation else 0f }
                            ) {
                                MusicAlbumArtImage(
                                    artUri = song.albumArtUri,
                                    iconSize = 48.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.dp, Color.Black.copy(0.3f), CircleShape)
                                )
                            }

                            // Spindle hole with metallic bushing
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFF4A4A52),
                                                Color(0xFF1E1E22),
                                                Color.Black
                                            )
                                        )
                                    )
                                    .border(1.8.dp, Color(0xFFC0C0C8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black)
                                )
                            }
                        }

                        // Turntable Tonearm Assembly (Pivots smoothly from rest onto vinyl grooves)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .graphicsLayer {
                                    // Pivot at the center of the round base (top of the arm column)
                                    transformOrigin = TransformOrigin(0.5f, 0.08f)
                                    rotationZ = tonearmAngle
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(42.dp)
                            ) {
                                // Pivot Base
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .shadow(6.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    Color(0xFF707078),
                                                    Color(0xFF35353C),
                                                    Color(0xFF18181C)
                                                )
                                            )
                                        )
                                        .border(1.5.dp, Color(0xFFA0A0A8), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF18181C))
                                            .border(1.dp, Color(0xFF888892), CircleShape)
                                    )
                                }

                                // Metallic Tonearm Tube
                                Box(
                                    modifier = Modifier
                                        .width(4.5.dp)
                                        .height(115.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFFB0B0B8),
                                                    Color(0xFFECECF0),
                                                    Color(0xFF909098)
                                                )
                                            )
                                        )
                                        .shadow(4.dp, RoundedCornerShape(2.dp))
                                )

                                // Cartridge & Stylus Headshell
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFF2B2B30),
                                                    c.accentBlue,
                                                    Color(0xFF101014)
                                                )
                                            )
                                        )
                                        .border(0.8.dp, Color.White.copy(0.4f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 6.dp, bottomEnd = 6.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    // Stylus needle tip
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlaying) Color(0xFF00F5D4) else Color(0xFFFFB703))
                                    )
                                }
                            }
                        }
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
                // 3. Cyber Neon Orb with Live Audio Dynamics (no jumping timer)
                val orbPulse = 1f + (reactiveBass * 0.07f)
                Box(
                    modifier = Modifier.size(290.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(285.dp * (if (isPlaying) orbPulse else 1f))
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
                            .graphicsLayer {
                                scaleX = if (isPlaying) orbPulse else 1f
                                scaleY = if (isPlaying) orbPulse else 1f
                            }
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
            } else if (artworkStyle == "SONIC_REACTOR") {
                // 4. New Visual Theme: Sonic Reactor (Circular Spectrum with Live Acoustic Rays)
                val reactorScale = 1f + (reactiveBass * 0.04f)
                Box(
                    modifier = Modifier.size(310.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Concentric Audio-Reactive Resonance Rings
                    Box(
                        modifier = Modifier
                            .size(300.dp + (18.dp * reactiveBass))
                            .graphicsLayer {
                                rotationZ = if (isPlaying) rotation * 0.6f else 0f
                            }
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF00F5D4),
                                        c.accentBlue,
                                        Color(0xFF7B2CBF),
                                        Color(0xFFFF007F),
                                        Color(0xFF00F5D4)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Real-Time Radial Spectrum Rays around Album Artwork
                    Canvas(modifier = Modifier.size(290.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val innerRadius = 96.dp.toPx()
                        val maxRayLength = 40.dp.toPx()
                        val bands = liveBands
                        val totalRays = 32

                        for (rayIdx in 0 until totalRays) {
                            val angleRad = (rayIdx.toFloat() / totalRays) * (2 * Math.PI)
                            val bandIdx = (rayIdx % bands.size)
                            val rawAmp = if (isPlaying) bands[bandIdx] else 0.08f
                            val rayLen = maxRayLength * rawAmp.coerceIn(0.08f, 1f)

                            val cosA = kotlin.math.cos(angleRad).toFloat()
                            val sinA = kotlin.math.sin(angleRad).toFloat()

                            val startX = center.x + innerRadius * cosA
                            val startY = center.y + innerRadius * sinA
                            val endX = center.x + (innerRadius + rayLen) * cosA
                            val endY = center.y + (innerRadius + rayLen) * sinA

                            val rayFraction = rayIdx.toFloat() / totalRays
                            val rayColor = spectrumColor(rayFraction)

                            drawLine(
                                color = rayColor.copy(alpha = if (isPlaying) 0.85f else 0.25f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }

                    // Center Floating Circular Album Artwork
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .graphicsLayer {
                                scaleX = if (isPlaying) reactorScale else 1f
                                scaleY = if (isPlaying) reactorScale else 1f
                            }
                            .shadow(28.dp, CircleShape, ambientColor = c.accentBlue, spotColor = Color(0xFF00F5D4))
                            .clip(CircleShape)
                            .border(2.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    ) {
                        MusicAlbumArtImage(
                            artUri = song.albumArtUri,
                            iconSize = 64.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (artworkStyle == "WAVE") {
                // 5. Waveform Stage with Real-Time Audio Soundwave Analyzer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val waveCardScale = 1f + (reactiveBass * 0.03f)
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .graphicsLayer {
                                scaleX = if (isPlaying) waveCardScale else 1f
                                scaleY = if (isPlaying) waveCardScale else 1f
                            }
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

                    Spacer(Modifier.height(18.dp))

                    // Real-Time Acoustic Soundwave Canvas
                    Canvas(modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 20.dp)) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f
                        val wavePoints = liveWaveform

                        // Base centerline glow
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(0f, midY),
                            end = Offset(width, midY),
                            strokeWidth = 1.dp.toPx()
                        )

                        if (isPlaying && wavePoints.isNotEmpty()) {
                            val ptsCount = wavePoints.size
                            val stepX = width / (ptsCount - 1).coerceAtLeast(1)

                            // Primary acoustic wave path (Neon Cyan)
                            val wavePath = androidx.compose.ui.graphics.Path()
                            wavePath.moveTo(0f, midY)
                            for (i in 0 until ptsCount) {
                                val sample = wavePoints[i]
                                val y = midY + (sample * height * 0.45f)
                                wavePath.lineTo(i * stepX, y)
                            }
                            drawPath(
                                path = wavePath,
                                color = Color(0xFF00F5D4),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )

                            // Secondary harmonic wave path (Accent Blue)
                            val harmonicPath = androidx.compose.ui.graphics.Path()
                            harmonicPath.moveTo(0f, midY)
                            for (i in 0 until ptsCount) {
                                val sample = wavePoints[i]
                                val y = midY - (sample * height * 0.32f * (1f + reactiveBass * 0.4f))
                                harmonicPath.lineTo(i * stepX, y)
                            }
                            drawPath(
                                path = harmonicPath,
                                color = c.accentBlue.copy(alpha = 0.85f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )

                            // Tertiary ambient envelope path (Magenta Glow)
                            val envelopePath = androidx.compose.ui.graphics.Path()
                            envelopePath.moveTo(0f, midY)
                            for (i in 0 until ptsCount) {
                                val sample = kotlin.math.abs(wavePoints[i])
                                val y = midY - (sample * height * 0.48f)
                                envelopePath.lineTo(i * stepX, y)
                            }
                            drawPath(
                                path = envelopePath,
                                color = Color(0xFFFF007F).copy(alpha = 0.5f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        } else {
                            // Tranquil resting waveform
                            drawLine(
                                color = c.accentBlue.copy(alpha = 0.4f),
                                start = Offset(width * 0.1f, midY),
                                end = Offset(width * 0.9f, midY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            } else {
                // 6. High-End 3D Frosted Glass Album Card with Audio-Reactive Bass Response (No bobbing/jumping)
                val cardPunchScale = 1f + (reactiveBass * 0.032f)
                Box(
                    modifier = Modifier
                        .size(285.dp)
                        .graphicsLayer {
                            scaleX = if (isPlaying) cardPunchScale else 1f
                            scaleY = if (isPlaying) cardPunchScale else 1f
                            // No artificial Y translation (jumping effect removed)
                        }
                        .shadow(
                            elevation = if (isPlaying) (26.dp + (14.dp * reactiveBass)) else 20.dp,
                            shape = RoundedCornerShape(32.dp),
                            ambientColor = Color.Black,
                            spotColor = c.accentBlue.copy(alpha = if (isPlaying) (0.35f + reactiveBass * 0.35f).coerceIn(0.2f, 0.8f) else 0.2f)
                        )
                        .clip(RoundedCornerShape(32.dp))
                        .background(c.glassBg)
                        .border(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(32.dp)
                        )
                ) {
                    MusicAlbumArtImage(
                        artUri = song.albumArtUri,
                        iconSize = 88.dp,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Frosted Specular Sheen across the top edge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.16f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // Animated Heart Burst Overlay
            if (heartAlpha > 0.01f) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF3366),
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                            alpha = heartAlpha
                        }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

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
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(84.dp + (8.dp * reactiveBass))
                                .clip(CircleShape)
                                .background(c.accentBlue.copy(alpha = 0.18f + reactiveBass * 0.14f))
                        )
                    }
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
                // Speed Capsule Pill
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onOpenSpeed,
                        shape = RoundedCornerShape(12.dp),
                        color = if (playbackSpeed != 1.0f) c.accentBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (playbackSpeed != 1.0f) c.accentBlue.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = if (playbackSpeed != 1.0f) c.accentBlue else Color.White.copy(0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${playbackSpeed}x",
                                color = if (playbackSpeed != 1.0f) c.accentBlue else Color.White.copy(0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Speed", color = Color.White.copy(0.5f), fontSize = 10.sp)
                }

                // Sleep Timer Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onOpenSleepTimer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerSecondsRemaining > 0) c.accentBlue else Color.White.copy(0.7f)
                            )
                            if (sleepTimerSecondsRemaining > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(c.accentBlue)
                                )
                            }
                        }
                    }
                    Text("Sleep", color = Color.White.copy(0.5f), fontSize = 10.sp)
                }

                // Up Next Queue Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White.copy(0.85f)
                        )
                    }
                    Text("Queue", color = Color.White.copy(0.5f), fontSize = 10.sp)
                }

                // Share Track Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Listening to \"${song.title}\" by ${song.artist} on YMedia Player! 🎵")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                    }) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = Color.White.copy(0.75f)
                        )
                    }
                    Text("Share", color = Color.White.copy(0.5f), fontSize = 10.sp)
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
    val view = androidx.compose.ui.platform.LocalView.current
    val clampedPct = percentage.coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = clampedPct,
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "smoothMusicGesturePct"
    )

    var lastHapticMilestone by remember { mutableIntStateOf(((clampedPct * 100f) / 5f).roundToInt().coerceIn(0, 20)) }
    val updateValueWithHaptic: (Float) -> Unit = { newVal ->
        val cl = newVal.coerceIn(0f, 1f)
        val milestone = ((cl * 100f) / 5f).roundToInt().coerceIn(0, 20)
        if (milestone != lastHapticMilestone) {
            lastHapticMilestone = milestone
            if (milestone == 0 || milestone == 20) {
                view.performHaptic(HapticType.MEDIUM)
            } else {
                view.performLevelHaptic(milestone / 20f)
            }
        }
        onValueChange(cl)
    }

    // Outer touch area (70.dp wide for effortless finger touch target)
    Box(
        modifier = modifier
            .width(70.dp)
            .height(210.dp)
            .pointerInput(clampedPct) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downY = down.position.y
                    val barHeight = size.height.toFloat().coerceAtLeast(1f)
                    var hasMoved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break

                        val currentY = change.position.y
                        if (abs(currentY - downY) > 6f) {
                            hasMoved = true
                            // Direct 1:1 scrub tracking with high responsiveness
                            val targetPct = (1f - (currentY / barHeight)).coerceIn(0f, 1f)
                            updateValueWithHaptic(targetPct)
                            change.consume()
                        }
                    }

                    if (!hasMoved) {
                        // Quick stationary tap on the bar
                        view.performHaptic(HapticType.TICK)
                        if (downY < barHeight * 0.5f) {
                            updateValueWithHaptic((clampedPct + 0.05f).coerceAtMost(1f))
                        } else {
                            updateValueWithHaptic((clampedPct - 0.05f).coerceAtLeast(0f))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Main Frosted Glass Capsule (44.dp wide, 192.dp tall) with native rounded shadow
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(192.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black.copy(alpha = 0.65f),
                    spotColor = glowColor.copy(alpha = 0.50f)
                )
                .clip(RoundedCornerShape(22.dp))
                // Dark tinted acrylic base
                .background(Color(0xDD0B0C13))
                // Frosted glass micro-gradient
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.35f)
                        )
                    )
                )
                // Specular Glass Rim Border
                .border(
                    width = 1.3.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.45f), // Top glass specular rim
                            glowColor.copy(alpha = 0.65f),   // Mid accent rim
                            Color.White.copy(alpha = 0.12f)  // Bottom subtle rim
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            // Fluid Track Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedPct)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = if (animatedPct > 0.95f) 22.dp else 6.dp, topEnd = if (animatedPct > 0.95f) 22.dp else 6.dp))
                    .background(gradient)
            )

            // 5. Glowing Fluid Meniscus (Top surface line of liquid)
            if (animatedPct in 0.02f..0.98f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .offset { androidx.compose.ui.unit.IntOffset(0, ((-192.dp.toPx() * animatedPct) + 2.dp.toPx()).roundToInt()) }
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.95f),
                                    Color.White.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }

            // 6. Top Specular Glass Reflection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 7. High-Contrast Inner Icons & Typography
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Icon with subtle frosted circular backing for contrast
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Bottom Percentage Label with frosted badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
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
    VisualStyleOption("VINYL", "Vinyl Record", "Classic turntable vinyl with realistic tonearm & grooves", Icons.Rounded.Album),
    VisualStyleOption("CARD", "Glass 3D Card", "Refined 3D frosted glass card with acoustic bass response", Icons.Rounded.CropPortrait),
    VisualStyleOption("SONIC_REACTOR", "Sonic Reactor", "Audiophile circular spectrum with real-time reactive sound rings", Icons.Rounded.GraphicEq),
    VisualStyleOption("CASSETTE", "Retro Cassette", "Authentic 80s tape with spinning spools & ribbon", Icons.Rounded.Audiotrack),
    VisualStyleOption("CYBER_ORB", "Cyber Neon Orb", "Futuristic reactive neon ring pulsing with audio dynamics", Icons.Rounded.Radio),
    VisualStyleOption("WAVE", "Waveform Stage", "Real-time acoustic audio soundwave analyzer", Icons.Rounded.Waves)
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

            Spacer(Modifier.height(14.dp))

            // Audiophile Bezier Frequency Curve Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.glassBg)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2f

                    // Draw 0 dB baseline
                    drawLine(
                        color = Color.White.copy(alpha = 0.15f),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 5 band points
                    val points = mutableListOf<Offset>()
                    points.add(Offset(0f, midY))
                    val stepX = w / 4f
                    bandLevels.forEachIndexed { i, db ->
                        val norm = ((db.coerceIn(-10f, 10f) + 10f) / 20f).coerceIn(0f, 1f)
                        val y = h - (norm * (h - 16.dp.toPx()) + 8.dp.toPx())
                        val x = i * stepX
                        points.add(Offset(x, y))
                    }
                    points.add(Offset(w, midY))

                    // Build smooth cubic Bezier path
                    val curvePath = androidx.compose.ui.graphics.Path()
                    val fillPath = androidx.compose.ui.graphics.Path()

                    curvePath.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(points[0].x, h)
                    fillPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx1 = p0.x + (p1.x - p0.x) / 2f
                        val cy1 = p0.y
                        val cx2 = p0.x + (p1.x - p0.x) / 2f
                        val cy2 = p1.y
                        curvePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Draw area fill under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(c.accentBlue.copy(alpha = if (enabled) 0.38f else 0.1f), Color.Transparent)
                        )
                    )

                    // Draw smooth frequency curve stroke
                    drawPath(
                        path = curvePath,
                        color = if (enabled) c.accentBlue else c.textSecondary.copy(alpha = 0.4f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )

                    // Draw dots on band levels
                    for (i in 1..5) {
                        val pt = points[i]
                        drawCircle(
                            color = if (enabled) c.accentBlue else c.textSecondary,
                            radius = 4.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val currentIndex = remember(queue, currentSong) {
        queue.indexOfFirst { it.id == currentSong?.id }.coerceAtLeast(0)
    }

    LaunchedEffect(Unit) {
        if (currentIndex > 0) {
            listState.scrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentSong != null) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Current", color = c.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
    val view = androidx.compose.ui.platform.LocalView.current
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
                        .clickable { 
                            view.performHaptic(HapticType.MEDIUM)
                            onSetTimer(mins) 
                        }
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
    val context = LocalContext.current
    val playCount = remember { AppPreferences(context).getPlayCount(song.uri.toString()) }
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
            InfoRow("Total Plays", "$playCount plays")
            InfoRow("File Size", formatSize(song.size))
            InfoRow("URI", song.uri.toString())

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out \"${song.title}\" by ${song.artist}! 🎵")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionMenuSheet(
    song: MusicItem,
    isFavorite: Boolean,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.dropdownBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MusicAlbumArtImage(
                    artUri = song.albumArtUri,
                    iconSize = 24.dp,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = c.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${song.artist} • ${formatTime(song.duration)}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = c.glassBorder, thickness = 0.8.dp)
            Spacer(Modifier.height(8.dp))

            ActionMenuItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Play Next",
                subtitle = "Queue this song after the current track",
                onClick = onPlayNext
            )

            ActionMenuItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                title = "Add to Playlist",
                subtitle = "Save to custom playlists",
                onClick = onAddToPlaylist
            )

            ActionMenuItem(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                tint = if (isFavorite) Color(0xFFFF4D6D) else null,
                title = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                subtitle = if (isFavorite) "Liked track" else "Save to your Favorites list",
                onClick = onToggleFavorite
            )

            ActionMenuItem(
                icon = Icons.Rounded.Share,
                title = "Share Track",
                subtitle = "Send track info to friends",
                onClick = onShare
            )

            ActionMenuItem(
                icon = Icons.Rounded.Info,
                title = "Track Details",
                subtitle = "View file specs and metadata",
                onClick = onViewDetails
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.glassBg)
                .border(0.8.dp, c.glassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint ?: c.accentBlue,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    var name by remember { mutableStateOf("") }
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
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = c.accentBlue)
                Spacer(Modifier.width(8.dp))
                Text("New Playlist", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name...", color = c.textSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accentBlue,
                    unfocusedBorderColor = c.glassBorder,
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary
                )
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = c.textSecondary)
                }
                Button(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create")
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: MusicItem,
    playlists: Map<String, List<String>>,
    onSelectPlaylist: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
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
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = c.accentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Playlist", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onCreateNew()
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Create New Playlist", color = c.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(color = c.glassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No playlists yet", color = c.textSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(playlists.keys.toList()) { plName ->
                        val count = playlists[plName]?.size ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPlaylist(plName) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plName, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("$count tracks", color = c.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
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
    val rawBands by com.example.ymediaplayer.service.AudioReactor.bands
    val liveEnergy by com.example.ymediaplayer.service.AudioReactor.energy

    val totalHeight = if (withReflection) height * 1.55f else height

    Row(
        horizontalArrangement = Arrangement.spacedBy(gapDp),
        verticalAlignment     = Alignment.Bottom,
        modifier              = Modifier.height(totalHeight)
    ) {
        val bandCount = rawBands.size
        for (i in 0 until barCount) {
            val fraction = if (barCount > 1) i.toFloat() / (barCount - 1) else 0.5f
            val barColor = spectrumColor(fraction)

            val mappedIdx = (fraction * (bandCount - 1)).toInt().coerceIn(0, bandCount - 1)
            val bandValue = if (isPlaying) rawBands[mappedIdx] else 0.06f

            val animatedScale by animateFloatAsState(
                targetValue = if (isPlaying) (bandValue * (1f + liveEnergy * 0.3f)).coerceIn(0.14f, 1f) else 0.06f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "barScale_$i"
            )

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
                        .fillMaxHeight(animatedScale)
                        .clip(CircleShape)
                        .background(barGradient)
                )

                if (withReflection) {
                    Box(
                        modifier = Modifier
                            .width(barWidthDp)
                            .fillMaxHeight((animatedScale * 0.35f).coerceAtLeast(0.04f))
                            .graphicsLayer { scaleY = -1f }
                            .clip(CircleShape)
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
