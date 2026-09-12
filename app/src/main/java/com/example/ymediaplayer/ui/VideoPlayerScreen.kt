package com.example.ymediaplayer.ui

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.example.ymediaplayer.MainActivity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import android.graphics.Typeface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import androidx.compose.foundation.lazy.items
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.SortOrder
import com.example.ymediaplayer.data.VideoFolder
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.data.sortVideosWithOrder
import com.example.ymediaplayer.data.sortFoldersWithOrder
import com.example.ymediaplayer.data.VideoRepository
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.LocalThemeController
import com.example.ymediaplayer.service.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentSkipListMap
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ─── Default Constants ────────────────────────────────────────────────────────
private const val VIDEO_NOTIFICATION_CHANNEL_ID = "video_playback_channel"
private const val VIDEO_NOTIFICATION_ID = 2001
private const val ACTION_VIDEO_PLAY_PAUSE = "com.example.ymediaplayer.ACTION_VIDEO_PLAY_PAUSE"
private const val ACTION_VIDEO_REWIND = "com.example.ymediaplayer.ACTION_VIDEO_REWIND"
private const val ACTION_VIDEO_FORWARD = "com.example.ymediaplayer.ACTION_VIDEO_FORWARD"
private const val ACTION_PIP_PLAY_PAUSE = "com.example.ymediaplayer.ACTION_PIP_PLAY_PAUSE"
private const val ACTION_PIP_PREV = "com.example.ymediaplayer.ACTION_PIP_PREV"
private const val ACTION_PIP_NEXT = "com.example.ymediaplayer.ACTION_PIP_NEXT"
private const val ACTION_PIP_BG_PLAY = "com.example.ymediaplayer.ACTION_PIP_BG_PLAY"
private const val ACTION_PIP_REWIND = "com.example.ymediaplayer.ACTION_PIP_REWIND"
private const val ACTION_PIP_FORWARD = "com.example.ymediaplayer.ACTION_PIP_FORWARD"

private val QuickActionBg = Color(0x33FFFFFF)       // Circular quick action button background
private val QuickActionBorder = Color(0x44FFFFFF)   // Circular quick action border

private enum class PlayerGestureMode {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
    SPEED_HOLD,
    ORIENTATION_SWIPE,
    VIDEO_SWITCH_SWIPE,
    PINCH_ZOOM,
    IGNORED_DRAG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val activity = context as? Activity
    val componentActivity = context as? androidx.activity.ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val appPreferences = remember { AppPreferences(context) }
    val repository = remember { VideoRepository(context) }
    val themeController = LocalThemeController.current
    val haptic = LocalHapticFeedback.current

    // ─── Dynamic Player Theme ─────────────────────────────────────────────────
    val activeTheme = themeController.colorTheme
    val primaryAccent = activeTheme.primaryAccent
    val secondaryAccent = activeTheme.secondaryAccent
    val gestureBrush = activeTheme.gestureBrush
    val glowColor = activeTheme.glowColor

    // ─── Playlist & Current Video State ───────────────────────────────────────
    var currentUrl by remember { mutableStateOf(videoUrl) }
    var allFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var playlistVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var currentVideoIndex by remember { mutableIntStateOf(-1) }
    var videoTitle by remember { mutableStateOf(videoUrl.substringAfterLast("/").substringBeforeLast(".")) }

    // Load folder playlist
    LaunchedEffect(videoUrl) {
        currentUrl = videoUrl
        withContext(Dispatchers.IO) {
            val folders = repository.getFoldersWithVideos()
            allFolders = folders
            val parentFolder = folders.find { folder -> folder.videos.any { it.uri.toString() == videoUrl } }
                ?: folders.firstOrNull()
            if (parentFolder != null) {
                playlistVideos = parentFolder.videos
                val index = playlistVideos.indexOfFirst { it.uri.toString() == videoUrl }
                currentVideoIndex = if (index >= 0) index else 0
                if (index >= 0) {
                    videoTitle = playlistVideos[index].title.substringBeforeLast(".")
                }
            }
        }
    }

    // ─── ExoPlayer Setup ──────────────────────────────────────────────────────
    val initialPlaybackSpeed = remember {
        if (appPreferences.isRememberPlaybackSpeed()) appPreferences.getLastPlaybackSpeed() else 1.0f
    }
    val initialRepeatMode = remember { appPreferences.getPlayerRepeatMode() }
    val initialMuted = remember { appPreferences.isPlayerMuted() }
    val preferredAudioLang = remember { appPreferences.getPreferredAudioLanguage() }
    val preferredSubLang = remember { appPreferences.getPreferredSubtitleLanguage() }
    val subtitlesEnabled = remember { appPreferences.isSubtitlesEnabled() }

    val exoPlayer = remember {
        val resumeMode = appPreferences.getResumeMode()
        val initialProgress = if (resumeMode != "START") appPreferences.getVideoProgress(videoUrl) else 0L
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(appPreferences.isPauseOnHeadsetDisconnect())
            .build().apply {
                var tspBuilder = trackSelectionParameters.buildUpon()
                if (preferredAudioLang != "default" && preferredAudioLang.isNotEmpty()) {
                    tspBuilder = tspBuilder.setPreferredAudioLanguage(preferredAudioLang)
                }
                if (!subtitlesEnabled) {
                    tspBuilder = tspBuilder.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                } else if (preferredSubLang.isNotEmpty()) {
                    tspBuilder = tspBuilder.setPreferredTextLanguage(preferredSubLang)
                }
                trackSelectionParameters = tspBuilder.build()

                if (initialPlaybackSpeed != 1.0f) {
                    playbackParameters = PlaybackParameters(initialPlaybackSpeed)
                }
                if (initialRepeatMode != Player.REPEAT_MODE_OFF) {
                    repeatMode = initialRepeatMode
                }
                if (initialMuted) {
                    volume = 0f
                }
                if (initialProgress > 3000L) {
                    setMediaItem(MediaItem.fromUri(videoUrl), initialProgress)
                } else {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                }
                prepare()
                playWhenReady = true
            }
    }

    val allVideos = remember(allFolders) { allFolders.flatMap { it.videos } }

    val playVideoItem: (VideoItem) -> Unit = { item ->
        val curPos = exoPlayer.currentPosition
        if (curPos > 1000L) {
            appPreferences.saveVideoProgress(currentUrl, curPos, exoPlayer.duration.coerceAtLeast(0L))
        }
        currentUrl = item.uri.toString()
        videoTitle = item.title.substringBeforeLast(".")
        val itemProgress = appPreferences.getVideoProgress(item.uri.toString())
        if (itemProgress > 3000L) {
            exoPlayer.setMediaItem(MediaItem.fromUri(item.uri), itemProgress)
        } else {
            exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
        }
        exoPlayer.prepare()
        exoPlayer.play()
        val idx = playlistVideos.indexOfFirst { it.uri == item.uri }
        if (idx >= 0) {
            currentVideoIndex = idx
        } else {
            val parent = allFolders.find { f -> f.videos.any { it.uri == item.uri } }
            if (parent != null) {
                playlistVideos = parent.videos
                currentVideoIndex = playlistVideos.indexOfFirst { it.uri == item.uri }
            }
        }
    }

    // Resolve title from ContentResolver if needed
    LaunchedEffect(currentUrl) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    currentUrl.toUri(),
                    arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let { videoTitle = it.substringBeforeLast(".") }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Player State ─────────────────────────────────────────────────────────
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isTouchingControls by remember { mutableStateOf(false) }
    var controlsInteractionTimestamp by remember { mutableLongStateOf(0L) }
    val optionsScrollState = rememberScrollState()
    var isMuted by remember { mutableStateOf(initialMuted) }
    var playbackSpeed by remember { mutableFloatStateOf(initialPlaybackSpeed) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val c = LocalAppColors.current
    var isLocked by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(appPreferences.isNightMode()) }
    var isBackgroundAudio by remember { mutableStateOf(appPreferences.isBackgroundPlayEnabled()) }
    var resizeMode by remember { mutableIntStateOf(appPreferences.getDefaultResizeMode()) }
    var isAmbientMode by remember { mutableStateOf(appPreferences.isAmbientModeEnabled()) }

    val toggleAmbientMode: () -> Unit = {
        view.performHaptic(HapticType.LIGHT)
        val newState = !isAmbientMode
        isAmbientMode = newState
        appPreferences.setAmbientModeEnabled(newState)
        Toast.makeText(
            context,
            if (newState) "Ambient Mode: On" else "Ambient Mode: Off",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ─── Music Now Playing Overlay State in Video Player ─────────────────────
    val isMusicPlaying by remember { MusicService.isMusicPlaying }
    val nowPlayingTitle by remember { MusicService.nowPlayingTitle }
    val nowPlayingArtist by remember { MusicService.nowPlayingArtist }
    val nowPlayingArtUri by remember { MusicService.nowPlayingArtUri }
    var isMusicDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(nowPlayingTitle) {
        isMusicDismissed = false
    }

    // Ensure screen capture and flags are always cleared (Privacy Shield removed)
    DisposableEffect(Unit) {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (appPreferences.isKeepScreenAwake()) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ─── Battery & Current Time HUD State ─────────────────────────────────────
    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var currentTimeStr by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date()))
    }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeStr = sdf.format(java.util.Date())
            try {
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                batteryIntent?.let { intent ->
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPct = (level * 100) / scale
                    }
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            } catch (_: Exception) {}
            delay(10000L)
        }
    }

    // ─── Press-Hold Speed Boost State ─────────────────────────────────────────
    var isSpeedHolding by remember { mutableStateOf(false) }
    var speedHoldDisplaySpeed by remember { mutableFloatStateOf(appPreferences.getPressHoldSpeed()) }
    var preHoldSpeed by remember { mutableFloatStateOf(1.0f) }

    // ─── Resume Playback Prompt State (Disabled - no prompt asking for Start Over) ───
    var resumePromptPosition by remember(currentUrl) { mutableLongStateOf(0L) }

    // ─── A-B Looping State ───────────────────────────────────────────────────
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }

    // ─── Bookmarks State ─────────────────────────────────────────────────────
    var bookmarksList by remember(currentUrl) { mutableStateOf(appPreferences.getBookmarks(currentUrl)) }

    // ─── Resolution Badge Intro Overlay (Disabled per user request) ──────────
    var showResolutionBadgeIntro by remember(currentUrl) { mutableStateOf(false) }
    var isQuickActionsExpanded by rememberSaveable { mutableStateOf(false) }

    var lastSeekMilestone by remember { mutableIntStateOf(-1) }
    var showRewindJumpMenu by remember { mutableStateOf(false) }
    var showForwardJumpMenu by remember { mutableStateOf(false) }

    // ─── Seek Thumbnail Preview State (High-Performance Instant Peek) ─────────
    var seekThumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var seekThumbnailPositionMs by remember { mutableLongStateOf(0L) }
    val frameCache = remember(currentUrl) { ConcurrentSkipListMap<Long, android.graphics.Bitmap>() }
    var thumbnailRetriever by remember { mutableStateOf<android.media.MediaMetadataRetriever?>(null) }
    LaunchedEffect(currentUrl) {
        withContext(Dispatchers.IO) {
            val r = android.media.MediaMetadataRetriever()
            try {
                if (currentUrl.startsWith("content://")) {
                    r.setDataSource(context, android.net.Uri.parse(currentUrl))
                } else {
                    r.setDataSource(currentUrl)
                }
                thumbnailRetriever = r
            } catch (_: Exception) {
                try { r.release() } catch (_: Exception) {}
            }
        }
    }
    DisposableEffect(currentUrl) {
        onDispose {
            try { thumbnailRetriever?.release() } catch (_: Exception) {}
            thumbnailRetriever = null
            frameCache.clear()
        }
    }

    // Background keyframe pre-caching across video timeline for instant peek previews
    LaunchedEffect(thumbnailRetriever, duration) {
        val retriever = thumbnailRetriever ?: return@LaunchedEffect
        if (duration <= 0L) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val intervalMs = when {
                duration > 3600_000L -> 15_000L
                duration > 600_000L -> 8_000L
                duration > 120_000L -> 4_000L
                else -> 2_000L
            }
            var pos = 0L
            while (pos <= duration && isActive) {
                if (!frameCache.containsKey(pos)) {
                    try {
                        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(
                                pos * 1000L,
                                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                160, 90
                            ) ?: retriever.getFrameAtTime(pos * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } else {
                            retriever.getFrameAtTime(pos * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                        if (bmp != null) {
                            frameCache[pos] = bmp
                        }
                    } catch (_: Exception) {}
                }
                pos += intervalMs
                delay(12L) // Gentle yield to prevent CPU contention
            }
        }
    }

    // ─── Fat Vertical Gesture Bars State ──────────────────────────────────────
    // Brightness on Left (0.01f..1f)
    val savedBrightness = remember { appPreferences.getLastPlayerBrightness() }
    var brightnessPct by remember {
        val lp = activity?.window?.attributes
        val currentVal = lp?.screenBrightness?.takeIf { it in 0.005f..1f }
        mutableFloatStateOf(
            if (savedBrightness in 0.01f..1.0f) savedBrightness
            else if (currentVal != null) screenBrightnessToSlider(currentVal)
            else getSystemBrightness(context)
        )
    }
    var showBrightnessBar by remember { mutableStateOf(false) }
    var brightnessTouchTrigger by remember { mutableLongStateOf(0L) }
    var hasUserAdjustedBrightness by remember { mutableStateOf(savedBrightness in 0.01f..1.0f) }

    // Smooth screen brightness batch updater using perceptual gamma curve (alters brightness when user changes it)
    LaunchedEffect(brightnessPct, hasUserAdjustedBrightness) {
        if (!hasUserAdjustedBrightness) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        val lp = act.window?.attributes ?: return@LaunchedEffect
        val targetHardware = sliderToScreenBrightness(brightnessPct)
        if (lp.screenBrightness != targetHardware) {
            lp.screenBrightness = targetHardware
            act.window?.attributes = lp
        }
        appPreferences.setLastPlayerBrightness(brightnessPct)
    }

    // Volume on Right (0f..1f)
    var volumePct by remember {
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(vol.toFloat() / maxVolume.toFloat())
    }
    var showVolumeBar by remember { mutableStateOf(false) }
    var volumeTouchTrigger by remember { mutableLongStateOf(0L) }

    // Seek Gestures
    var isDraggingSeek by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var currentDragSeekTarget by remember { mutableLongStateOf(0L) }
    var activeGestureMode by remember { mutableStateOf(PlayerGestureMode.NONE) }
    var doubleTapSeekSeconds by remember { mutableIntStateOf(0) }
    var doubleTapIsForward by remember { mutableStateOf(true) }
    var centerDoubleTapPlayPause by remember { mutableStateOf<Boolean?>(null) }
    var centerDoubleTapKey by remember { mutableLongStateOf(0L) }
    var verticalSwipeSwitchHUD by remember { mutableStateOf<String?>(null) }
    var verticalSwipeSwitchIsNext by remember { mutableStateOf(true) }
    var verticalSwipeSwitchKey by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }

    // Instant Peek Preview updater (Runs for both Portrait and Fullscreen Landscape)
    LaunchedEffect(scrubPosition, isDraggingSeek) {
        if (!isDraggingSeek) {
            seekThumbnail = null
            return@LaunchedEffect
        }
        val targetMs = scrubPosition.toLong()

        // 1. Immediately show nearest cached keyframe (INSTANT 0ms response!)
        val nearest = frameCache.floorEntry(targetMs)?.value
            ?: frameCache.ceilingEntry(targetMs)?.value
        if (nearest != null) {
            seekThumbnail = nearest
        }

        // 2. Refine exact frame if not in cache
        val bucket = (targetMs / 2000L) * 2000L
        if (!frameCache.containsKey(bucket)) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = thumbnailRetriever ?: return@withContext
                    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            targetMs * 1000L,
                            android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            160, 90
                        ) ?: retriever.getFrameAtTime(targetMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } else {
                        retriever.getFrameAtTime(targetMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                    if (bmp != null) {
                        frameCache[bucket] = bmp
                        seekThumbnail = bmp
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Center Seek HUD
    var centerSeekText by remember { mutableStateOf<String?>(null) }
    var centerSeekIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Sheets & Dialogs
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSettingsOverlay by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showThemePickerSheet by remember { mutableStateOf(false) }
    var showVideoInfoSheet by remember { mutableStateOf(false) }
    var showQuickControlsBar by remember { mutableStateOf(false) }
    var subtitleDesign by remember { mutableIntStateOf(appPreferences.getSubtitleDesign()) }

    val playerView = remember(context) {
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            applySubtitleDesign(this, subtitleDesign, appPreferences)
        }
    }

    LaunchedEffect(subtitleDesign, playerView) {
        applySubtitleDesign(playerView, subtitleDesign, appPreferences)
        appPreferences.saveSubtitleDesign(subtitleDesign)
    }

    // Sleep Timer
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }

    // Picture in Picture
    var isInPiP by remember { mutableStateOf(false) }
    var prePipSubtitleFlags by remember { mutableIntStateOf(-1) } // stores subtitle flags before PiP entry

    // Pinch-to-Zoom & Pan
    var videoZoomScale by remember { mutableFloatStateOf(1f) }
    var videoPanX by remember { mutableFloatStateOf(0f) }
    var videoPanY by remember { mutableFloatStateOf(0f) }
    var showZoomHUD by remember { mutableStateOf(false) }
    var zoomHUDText by remember { mutableStateOf("") }

    val resetSpeedToOne: () -> Unit = {
        view.performHaptic(HapticType.MEDIUM)
        playbackSpeed = 1.0f
        exoPlayer.playbackParameters = PlaybackParameters(1.0f)
        if (appPreferences.isRememberPlaybackSpeed()) {
            appPreferences.setLastPlaybackSpeed(1.0f)
        }
        Toast.makeText(context, "Speed reset to 1.0x", Toast.LENGTH_SHORT).show()
    }

    // Repeat Mode (0: Off, 1: One, 2: All)
    var repeatMode by remember { mutableIntStateOf(initialRepeatMode) }

    // ─── Back Press Handling ──────────────────────────────────────────────────
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showQuickControlsBar -> showQuickControlsBar = false
            showSettingsOverlay -> showSettingsOverlay = false
            showPlaylistSheet -> showPlaylistSheet = false
            showMoreMenuSheet -> showMoreMenuSheet = false
            showSpeedSheet -> showSpeedSheet = false
            showSleepTimerSheet -> showSleepTimerSheet = false
            showAudioTrackSheet -> showAudioTrackSheet = false
            showSubtitleSheet -> showSubtitleSheet = false
            showThemePickerSheet -> showThemePickerSheet = false
            showVideoInfoSheet -> showVideoInfoSheet = false
            isLocked -> isLocked = false
            else -> onBack()
        }
    }

    val configuredSeekStepSec = remember { appPreferences.getDoubleTapSeekSeconds().coerceAtLeast(1) }
    val configuredSeekStepMs = remember(configuredSeekStepSec) { configuredSeekStepSec * 1000L }

    // ─── Helper Functions ─────────────────────────────────────────────────────
    fun safeSeek(targetMs: Long) {
        val target = if (duration > 0L) targetMs.coerceIn(0L, duration) else targetMs.coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        currentPosition = target
    }

    fun seekBy(deltaMs: Long) {
        safeSeek(exoPlayer.currentPosition + deltaMs)
    }

    val playNextVideo: () -> Unit = {
        if (playlistVideos.isNotEmpty() && currentVideoIndex < playlistVideos.size - 1) {
            val curPos = exoPlayer.currentPosition
            if (curPos > 1000L) {
                appPreferences.saveVideoProgress(currentUrl, curPos, exoPlayer.duration.coerceAtLeast(0L))
            }
            val nextIndex = currentVideoIndex + 1
            val nextVideo = playlistVideos[nextIndex]
            currentVideoIndex = nextIndex
            currentUrl = nextVideo.uri.toString()
            videoTitle = nextVideo.title.substringBeforeLast(".")
            val nextProgress = appPreferences.getVideoProgress(nextVideo.uri.toString())
            if (nextProgress > 3000L) {
                exoPlayer.setMediaItem(MediaItem.fromUri(nextVideo.uri), nextProgress)
            } else {
                exoPlayer.setMediaItem(MediaItem.fromUri(nextVideo.uri))
            }
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    val playPreviousVideo: () -> Unit = {
        if (playlistVideos.isNotEmpty() && currentVideoIndex > 0) {
            val curPos = exoPlayer.currentPosition
            if (curPos > 1000L) {
                appPreferences.saveVideoProgress(currentUrl, curPos, exoPlayer.duration.coerceAtLeast(0L))
            }
            val prevIndex = currentVideoIndex - 1
            val prevVideo = playlistVideos[prevIndex]
            currentVideoIndex = prevIndex
            currentUrl = prevVideo.uri.toString()
            videoTitle = prevVideo.title.substringBeforeLast(".")
            val prevProgress = appPreferences.getVideoProgress(prevVideo.uri.toString())
            if (prevProgress > 3000L) {
                exoPlayer.setMediaItem(MediaItem.fromUri(prevVideo.uri), prevProgress)
            } else {
                exoPlayer.setMediaItem(MediaItem.fromUri(prevVideo.uri))
            }
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    val toggleMute: () -> Unit = {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0f else 1f
        appPreferences.setPlayerMuted(isMuted)
        if (!isMuted) {
            volumePct = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()).coerceIn(0.05f, 1f)
        }
        showVolumeBar = true
    }

    val cycleResizeMode: () -> Unit = {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        appPreferences.setDefaultResizeMode(resizeMode)
        val label = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom / Crop"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
            else -> "Original Aspect"
        }
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    // ─── Video Ratio & Orientation Detection ──────────────────────────────────
    var videoWidth by remember(currentUrl) { mutableIntStateOf(0) }
    var videoHeight by remember(currentUrl) { mutableIntStateOf(0) }
    val isVerticalVideo = remember(videoWidth, videoHeight) {
        videoHeight > 0 && videoWidth > 0 && videoHeight > videoWidth
    }
    val resolutionBadge = remember(videoWidth, videoHeight) {
        val maxDim = maxOf(videoWidth, videoHeight)
        val minDim = minOf(videoWidth, videoHeight)
        when {
            maxDim >= 3800 || minDim >= 2100 -> "4K UHD"
            maxDim >= 2500 || minDim >= 1400 -> "2K QHD"
            maxDim >= 1900 || minDim >= 1050 -> "1080p FHD"
            maxDim >= 1200 || minDim >= 700 -> "720p HD"
            maxDim >= 800 || minDim >= 450 -> "480p"
            minDim > 0 -> "${minDim}p"
            else -> null
        }
    }

    // Extract video dimensions asynchronously via retriever off main thread
    LaunchedEffect(currentUrl) {
        withContext(Dispatchers.IO) {
            try {
                val r = android.media.MediaMetadataRetriever()
                if (currentUrl.startsWith("content://")) {
                    r.setDataSource(context, android.net.Uri.parse(currentUrl))
                } else {
                    r.setDataSource(currentUrl)
                }
                val wStr = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val hStr = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotStr = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rawW = wStr?.toIntOrNull() ?: 0
                val rawH = hStr?.toIntOrNull() ?: 0
                val rot = rotStr?.toIntOrNull() ?: 0
                r.release()
                val effectiveW = if (rot == 90 || rot == 270) rawH else rawW
                val effectiveH = if (rot == 90 || rot == 270) rawW else rawH
                if (effectiveW > 0 && effectiveH > 0) {
                    videoWidth = effectiveW
                    videoHeight = effectiveH
                }
            } catch (_: Exception) {}
        }
    }

    // Also update from ExoPlayer decoded video size
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val rot = videoSize.unappliedRotationDegrees
                val rawW = videoSize.width
                val rawH = videoSize.height
                val effectiveW = if (rot == 90 || rot == 270) rawH else rawW
                val effectiveH = if (rot == 90 || rot == 270) rawW else rawH
                if (effectiveW > 0 && effectiveH > 0) {
                    videoWidth = effectiveW
                    videoHeight = effectiveH
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    appPreferences.markCompleted(currentUrl, true)
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (repeatMode == Player.REPEAT_MODE_ALL || (appPreferences.isAutoPlayNextEnabled() && playlistVideos.isNotEmpty() && currentVideoIndex < playlistVideos.size - 1)) {
                        playNextVideo()
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // ─── Auto-Orientation Sensor (Follows Phone Orientation When Enabled) ─────
    var manualOrientationOverrideTime by remember { mutableLongStateOf(0L) }
    var currentOrientationSetting by remember { mutableIntStateOf(-1) } // 0: portrait, 1: landscape
    var hasSetInitialOrientation by remember(currentUrl) { mutableStateOf(false) }

    val orientationEventListener = remember(context, activity) {
        object : android.view.OrientationEventListener(context, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            private var lastAutoRotateCheckTime = 0L
            private var cachedAutoRotateEnabled = true

            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                if (System.currentTimeMillis() < manualOrientationOverrideTime) return

                // Check device auto-rotate setting throttled to once every 2 seconds instead of every sensor tick
                val now = System.currentTimeMillis()
                if (now - lastAutoRotateCheckTime > 2000L) {
                    cachedAutoRotateEnabled = try {
                        android.provider.Settings.System.getInt(
                            context.contentResolver,
                            android.provider.Settings.System.ACCELEROMETER_ROTATION,
                            0
                        ) == 1
                    } catch (_: Exception) { false }
                    lastAutoRotateCheckTime = now
                }

                if (!cachedAutoRotateEnabled) return

                val isLandscapeSensor = (orientation in 65..115) || (orientation in 245..295)
                val isPortraitSensor = (orientation in 335..360) || (orientation in 0..25) || (orientation in 155..205)

                if (isLandscapeSensor && currentOrientationSetting != 1) {
                    currentOrientationSetting = 1
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (isPortraitSensor && currentOrientationSetting != 0) {
                    currentOrientationSetting = 0
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
    }

    // Adjust initial orientation according to video ratio (9:16 vertical vs 16:9 landscape)
    LaunchedEffect(videoWidth, videoHeight, currentUrl) {
        if (!hasSetInitialOrientation && videoWidth > 0 && videoHeight > 0) {
            hasSetInitialOrientation = true
            if (videoHeight > videoWidth) {
                // Vertical video (e.g. 9:16) -> play in vertical!
                currentOrientationSetting = 0
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                // Horizontal video (e.g. 16:9)
                // When phone is in vertical (portrait) orientation, open in vertical mode only!
                val isPhonePortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                if (isPhonePortrait) {
                    currentOrientationSetting = 0
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    currentOrientationSetting = 1
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    val insetsController = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(Unit) {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        onDispose {
            orientationEventListener.disable()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            val lp = activity?.window?.attributes
            if (lp != null && lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity?.window?.attributes = lp
            }
        }
    }

    LaunchedEffect(isLandscape, isVerticalVideo) {
        if (isLandscape || isVerticalVideo) {
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ─── PiP Subtitle Hiding: Completely suppress subtitles in PiP mode ────────
    LaunchedEffect(isInPiP) {
        if (isInPiP) {
            playerView.subtitleView?.visibility = android.view.View.GONE
            playerView.subtitleView?.setCues(emptyList())
            playerView.subtitleView?.alpha = 0f
        } else {
            playerView.subtitleView?.visibility = android.view.View.VISIBLE
            playerView.subtitleView?.alpha = 1f
        }
    }

    // ─── PiP Callback (Subtitle disable/restore + remote actions) ──────────────
    DisposableEffect(componentActivity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPiP = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                // Entering PiP: physically hide subtitle view, clear cues, and disable text tracks
                playerView.subtitleView?.visibility = android.view.View.GONE
                playerView.subtitleView?.setCues(emptyList())
                playerView.subtitleView?.alpha = 0f
                prePipSubtitleFlags = exoPlayer.trackSelectionParameters.ignoredTextSelectionFlags
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            } else {
                // Exiting PiP: restore subtitle view and state
                playerView.subtitleView?.visibility = android.view.View.VISIBLE
                playerView.subtitleView?.alpha = 1f
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                if (prePipSubtitleFlags >= 0) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setIgnoredTextSelectionFlags(prePipSubtitleFlags)
                        .build()
                    prePipSubtitleFlags = -1
                }
            }
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)

        // PiP remote action receiver
        val pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PIP_PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                    ACTION_PIP_PREV -> {
                        playPreviousVideo()
                    }
                    ACTION_PIP_NEXT -> {
                        playNextVideo()
                    }
                    ACTION_PIP_BG_PLAY -> {
                        isBackgroundAudio = true
                        appPreferences.setBackgroundPlayEnabled(true)
                        Toast.makeText(context, "Background Audio enabled", Toast.LENGTH_SHORT).show()
                        try {
                            activity?.moveTaskToBack(true)
                        } catch (_: Exception) {}
                    }
                    ACTION_PIP_REWIND -> {
                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                    }
                    ACTION_PIP_FORWARD -> {
                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
                        exoPlayer.seekTo(target)
                    }
                }
            }
        }
        val pipFilter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_PREV)
            addAction(ACTION_PIP_NEXT)
            addAction(ACTION_PIP_BG_PLAY)
            addAction(ACTION_PIP_REWIND)
            addAction(ACTION_PIP_FORWARD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(pipReceiver, pipFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(pipReceiver, pipFilter)
        }

        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
            try { context.unregisterReceiver(pipReceiver) } catch (_: Exception) {}
        }
    }

    // ─── PiP Params Builder (clamped aspect ratio, remote actions, auto-enter) ─
    val buildPipParams = remember(videoWidth, videoHeight, isPlaying, isVerticalVideo, currentVideoIndex, playlistVideos, isBackgroundAudio) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val aspectW = if (videoWidth > 0) videoWidth else (if (isVerticalVideo) 9 else 16)
                    val aspectH = if (videoHeight > 0) videoHeight else (if (isVerticalVideo) 16 else 9)
                    val rawRatio = aspectW.toFloat() / aspectH.toFloat()
                    val clampedRatio = rawRatio.coerceIn(0.41841f, 2.39f)
                    val rational = if (clampedRatio < 1f) {
                        android.util.Rational((clampedRatio * 1000).toInt(), 1000)
                    } else {
                        android.util.Rational(1000, (1000 / clampedRatio).toInt())
                    }

                    val prevPendingIntent = PendingIntent.getBroadcast(
                        context, 201,
                        Intent(ACTION_PIP_PREV).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val playPausePendingIntent = PendingIntent.getBroadcast(
                        context, 202,
                        Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val nextPendingIntent = PendingIntent.getBroadcast(
                        context, 203,
                        Intent(ACTION_PIP_NEXT).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val bgPlayPendingIntent = PendingIntent.getBroadcast(
                        context, 204,
                        Intent(ACTION_PIP_BG_PLAY).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val canPrev = playlistVideos.isNotEmpty() && currentVideoIndex > 0
                    val canNext = playlistVideos.isNotEmpty() && currentVideoIndex < playlistVideos.size - 1

                    val prevAction = android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_previous),
                        "Previous",
                        "Play previous video",
                        prevPendingIntent
                    ).apply { isEnabled = canPrev }

                    val playPauseAction = android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(
                            context,
                            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        ),
                        if (isPlaying) "Pause" else "Play",
                        if (isPlaying) "Pause playback" else "Resume playback",
                        playPausePendingIntent
                    )

                    val nextAction = android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_next),
                        "Next",
                        "Play next video",
                        nextPendingIntent
                    ).apply { isEnabled = canNext }

                    val bgPlayAction = android.app.RemoteAction(
                        android.graphics.drawable.Icon.createWithResource(context, com.example.ymediaplayer.R.drawable.ic_pip_bg_play),
                        "BG Play",
                        "Play in background",
                        bgPlayPendingIntent
                    )

                    val maxAllowed = try {
                        activity?.maxNumPictureInPictureActions ?: 3
                    } catch (_: Exception) { 3 }

                    val actionsList = mutableListOf<android.app.RemoteAction>()
                    if (maxAllowed >= 4) {
                        actionsList.add(prevAction)
                        actionsList.add(playPauseAction)
                        actionsList.add(nextAction)
                        actionsList.add(bgPlayAction)
                    } else if (maxAllowed == 3) {
                        // Exactly 3 action slots available on standard Android:
                        // Slot 1: BG Play (Always visible as requested)
                        // Slot 2: Play / Pause
                        // Slot 3: Next (or Previous if at the end of playlist)
                        if (canPrev && !canNext) {
                            actionsList.add(prevAction)
                            actionsList.add(playPauseAction)
                            actionsList.add(bgPlayAction)
                        } else {
                            actionsList.add(bgPlayAction)
                            actionsList.add(playPauseAction)
                            actionsList.add(nextAction)
                        }
                    } else {
                        actionsList.add(playPauseAction)
                    }

                    val paramsBuilder = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .setActions(actionsList.take(maxAllowed))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        paramsBuilder.setAutoEnterEnabled(!isBackgroundAudio && !isMusicPlaying)
                        paramsBuilder.setSeamlessResizeEnabled(true)
                    }

                    val rect = android.graphics.Rect()
                    playerView.getGlobalVisibleRect(rect)
                    if (!rect.isEmpty) {
                        paramsBuilder.setSourceRectHint(rect)
                    }

                    paramsBuilder.build()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    // Proactively set PiP params so auto-enter & mini controls are pre-registered
    LaunchedEffect(videoWidth, videoHeight, isPlaying, isVerticalVideo, currentVideoIndex, isBackgroundAudio, isMusicPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val act = activity ?: return@LaunchedEffect
            if (isBackgroundAudio || isMusicPlaying) {
                // For music / background audio, PiP should NOT be shown!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val noPip = android.app.PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(false)
                            .build()
                        act.setPictureInPictureParams(noPip)
                    } catch (_: Exception) {}
                }
            } else {
                val params = buildPipParams()
                if (params != null) {
                    try {
                        act.setPictureInPictureParams(params)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Connect to MainActivity.onUserLeaveHintListener so pressing Home button enters PiP on all Android versions
    DisposableEffect(exoPlayer, isPlaying, buildPipParams, isBackgroundAudio, isMusicPlaying) {
        MainActivity.onUserLeaveHintListener = {
            if (!isBackgroundAudio && !isMusicPlaying && appPreferences.isAutoPipEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    playerView.subtitleView?.visibility = android.view.View.GONE
                    playerView.subtitleView?.setCues(emptyList())
                    playerView.subtitleView?.alpha = 0f
                    val params = buildPipParams()
                    if (params != null) {
                        activity?.enterPictureInPictureMode(params)
                    } else {
                        @Suppress("DEPRECATION")
                        activity?.enterPictureInPictureMode()
                    }
                } catch (_: Exception) {}
            }
        }
        onDispose {
            MainActivity.onUserLeaveHintListener = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    activity?.setPictureInPictureParams(
                        android.app.PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(false)
                            .build()
                    )
                } catch (_: Exception) {}
            }
        }
    }

    val enterPiP: () -> Unit = {
        view.performHaptic(HapticType.MEDIUM)
        playerView.subtitleView?.visibility = android.view.View.GONE
        playerView.subtitleView?.setCues(emptyList())
        playerView.subtitleView?.alpha = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showControls = false
            try {
                val params = buildPipParams()
                val entered: Boolean = if (params != null) {
                    activity?.enterPictureInPictureMode(params) ?: false
                } else {
                    @Suppress("DEPRECATION")
                    activity?.enterPictureInPictureMode()
                    true
                }
                if (!entered) {
                    @Suppress("DEPRECATION")
                    activity?.enterPictureInPictureMode()
                }
            } catch (_: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    activity?.enterPictureInPictureMode()
                } catch (_: Exception) {
                    Toast.makeText(context, "Picture-in-Picture not supported on this device", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Progress Loop & A-B Looping Check ─────────────────────────────────────
    LaunchedEffect(exoPlayer, loopStartMs, loopEndMs) {
        while (true) {
            if (!isDraggingSeek) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            val lStart = loopStartMs
            val lEnd = loopEndMs
            if (lStart != null && lEnd != null && lEnd > lStart && currentPosition >= lEnd) {
                safeSeek(lStart)
            }
            delay(200.milliseconds)
        }
    }

    // ─── Periodic Progress Auto-Save (Every 4s while playing) ─────────────────
    LaunchedEffect(currentUrl) {
        while (true) {
            delay(4000.milliseconds)
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                if (pos > 1000L) {
                    appPreferences.saveVideoProgress(currentUrl, pos, dur)
                }
            }
        }
    }

    // ─── Auto-hide Controls (Stay visible while touching, dragging, dialogs open, or paused) ──
    val isAnySheetOrMenuOpen = showPlaylistSheet ||
            showSettingsOverlay ||
            showMoreMenuSheet ||
            showSpeedSheet ||
            showSleepTimerSheet ||
            showAudioTrackSheet ||
            showSubtitleSheet ||
            showThemePickerSheet ||
            showVideoInfoSheet ||
            showRewindJumpMenu ||
            showForwardJumpMenu

    val isControlsBusy = isDraggingSeek ||
            isTouchingControls ||
            optionsScrollState.isScrollInProgress ||
            isQuickActionsExpanded ||
            isAnySheetOrMenuOpen

    val autoHideTimeoutMs = remember { appPreferences.getControlsAutoHideTimeoutMs() }

    LaunchedEffect(
        showControls,
        isPlaying,
        isLocked,
        isControlsBusy,
        controlsInteractionTimestamp
    ) {
        if (!isPlaying && !isLocked) {
            showControls = true
        } else if (showControls && isPlaying && !isLocked && !isControlsBusy) {
            if (autoHideTimeoutMs > 0L) {
                delay(autoHideTimeoutMs.milliseconds)
                showControls = false
            }
        }
    }

    // ─── Auto-hide Vertical Gesture Bars (1.6s auto-dismiss after touch ends) ──
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

    // ─── Reset Double Tap Feedback ────────────────────────────────────────────
    LaunchedEffect(doubleTapSeekSeconds) {
        if (doubleTapSeekSeconds > 0) {
            delay(800.milliseconds)
            doubleTapSeekSeconds = 0
        }
    }

    LaunchedEffect(centerDoubleTapKey) {
        if (centerDoubleTapKey > 0L) {
            delay(750.milliseconds)
            centerDoubleTapPlayPause = null
        }
    }

    LaunchedEffect(verticalSwipeSwitchKey) {
        if (verticalSwipeSwitchKey > 0L) {
            delay(1300.milliseconds)
            verticalSwipeSwitchHUD = null
        }
    }

    // ─── Sleep Timer Countdown ────────────────────────────────────────────────
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            delay((sleepTimerMinutes * 60).seconds)
            exoPlayer.pause()
            sleepTimerMinutes = 0
            Toast.makeText(context, "Sleep timer finished. Playback stopped.", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Video Playback Notification with Live Progress & Tap-to-Open ────────
    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VIDEO_NOTIFICATION_CHANNEL_ID,
                "Video Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live playback progress and controls for the playing video"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_VIDEO_PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                    ACTION_VIDEO_REWIND -> {
                        safeSeek((exoPlayer.currentPosition - configuredSeekStepMs).coerceAtLeast(0L))
                    }
                    ACTION_VIDEO_FORWARD -> {
                        safeSeek((exoPlayer.currentPosition + configuredSeekStepMs).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L)))
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_VIDEO_PLAY_PAUSE)
            addAction(ACTION_VIDEO_REWIND)
            addAction(ACTION_VIDEO_FORWARD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            try {
                notificationManager.cancel(VIDEO_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }

    // Pre-create and cache notification PendingIntents
    val contentIntent = remember(context, currentUrl) {
        PendingIntent.getActivity(
            context,
            2001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_VIDEO_URL, currentUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    val rewindIntent = remember(context) {
        PendingIntent.getBroadcast(
            context, 101,
            Intent(ACTION_VIDEO_REWIND).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    val playPauseIntent = remember(context) {
        PendingIntent.getBroadcast(
            context, 102,
            Intent(ACTION_VIDEO_PLAY_PAUSE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    val forwardIntent = remember(context) {
        PendingIntent.getBroadcast(
            context, 103,
            Intent(ACTION_VIDEO_FORWARD).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Live update notification progress (Throttled to 1s intervals instead of 200ms)
    LaunchedEffect(isPlaying, duration, videoTitle, currentUrl) {
        while (true) {
            val curPos = exoPlayer.currentPosition
            val curDur = duration.takeIf { it > 0L } ?: exoPlayer.duration.coerceAtLeast(0L)
            if (curDur > 0L || curPos > 0L) {
                val progressPercent = if (curDur > 0L) {
                    ((curPos.toFloat() / curDur.toFloat()) * 100).toInt().coerceIn(0, 100)
                } else 0

                val title = videoTitle.ifEmpty { "Video Playing" }
                val timeText = "${formatTime(curPos)} / ${formatTime(curDur)}"

                val notification = NotificationCompat.Builder(context, VIDEO_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(title)
                    .setContentText("$timeText  ($progressPercent%)")
                    .setSubText(if (isPlaying) "Playing" else "Paused")
                    .setContentIntent(contentIntent)
                    .setOngoing(isPlaying)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, progressPercent, false)
                    .addAction(android.R.drawable.ic_media_rew, "-10s", rewindIntent)
                    .addAction(
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (isPlaying) "Pause" else "Play",
                        playPauseIntent
                    )
                    .addAction(android.R.drawable.ic_media_ff, "+10s", forwardIntent)
                    .build()

                try {
                    notificationManager.notify(VIDEO_NOTIFICATION_ID, notification)
                } catch (_: Exception) {}
            }

            if (!isPlaying) break // update once when paused, don't loop
            delay(1000L) // 1 second update interval
        }
    }

    // ─── Lifecycle Handling ───────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val pos = exoPlayer.currentPosition
                    if (pos > 1000L) {
                        appPreferences.saveVideoProgress(currentUrl, pos, exoPlayer.duration.coerceAtLeast(0L))
                    }
                    val allowBg = isBackgroundAudio || appPreferences.isBackgroundPlayEnabled()
                    if (!allowBg && !isInPiP) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh current brightness & volume
                    val lp = activity?.window?.attributes
                    val currentB = lp?.screenBrightness?.takeIf { it in 0.005f..1f }
                    brightnessPct = if (currentB != null) screenBrightnessToSlider(currentB) else getSystemBrightness(context)
                    val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    volumePct = vol.toFloat() / maxVolume.toFloat()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Save progress
            val pos = exoPlayer.currentPosition
            if (pos > 1000L) {
                appPreferences.saveVideoProgress(currentUrl, pos, exoPlayer.duration.coerceAtLeast(0L))
            }
            exoPlayer.release()
            val lp = activity?.window?.attributes
            if (lp != null && lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity?.window?.attributes = lp
            }
        }
    }

    // ─── Back Press Handling ──────────────────────────────────────────────────
    BackHandler {
        if (isLocked) {
            isLocked = false
        } else if (isLandscape && !isVerticalVideo) {
            manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
            currentOrientationSetting = 0
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else if (showControls) {
            onBack()
        } else {
            showControls = true
        }
    }

    val controlsTouchModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val anyPressed = event.changes.any { it.pressed }
                    isTouchingControls = anyPressed
                    controlsInteractionTimestamp = System.currentTimeMillis()
                }
            } finally {
                isTouchingControls = false
            }
        }
    }

    val playerGestureModifier = Modifier.pointerInput(isLocked, playbackSpeed, duration, isLandscape, isVerticalVideo) {
        if (isLocked) {
            detectTapGestures(
                onTap = { showControls = !showControls }
            )
            return@pointerInput
        }

        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var wasConsumed = down.isConsumed
            val downTime = System.currentTimeMillis()
            val startX = down.position.x
            val startY = down.position.y
            val screenWidth = size.width.toFloat()
            val screenHeight = size.height.toFloat()

            val isDoubleTap = (downTime - lastTapTime < 320L) &&
                    (abs(startX - lastTapX) < 90f && abs(startY - lastTapY) < 90f)

            var gestureMode = PlayerGestureMode.NONE
            var hasMoved = false
            val startBrightness = brightnessPct
            val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val startPosition = exoPlayer.currentPosition
            currentDragSeekTarget = startPosition
            var lastHoldX = startX
            var didActivateSpeedHold = false
            var speedHoldAccumulatorX = 0f
            var finalDiffX = 0f
            var finalDiffY = 0f
            var currentX = startX
            var currentY = startY
            var lastPinchDist = 0f
            var lastPinchCentroid = Offset.Zero
            var lastPanX = startX
            var lastPanY = startY

            while (true) {
                val elapsed = System.currentTimeMillis() - downTime

                // ─── 1-Second Speed Hold Activation (Tapping & holding anywhere on screen) ───
                if ((gestureMode == PlayerGestureMode.NONE || gestureMode == PlayerGestureMode.IGNORED_DRAG) && elapsed >= 1000L && !isDoubleTap) {
                    gestureMode = PlayerGestureMode.SPEED_HOLD
                    activeGestureMode = gestureMode
                    didActivateSpeedHold = true
                    preHoldSpeed = playbackSpeed
                    val targetHoldSpeed = appPreferences.getPressHoldSpeed()
                    speedHoldDisplaySpeed = targetHoldSpeed
                    speedHoldAccumulatorX = 0f
                    isSpeedHolding = true
                    showControls = false
                    exoPlayer.setPlaybackSpeed(targetHoldSpeed)
                    lastHoldX = currentX
                    view.performHaptic(HapticType.HEAVY)
                }

                val remainingMs = if ((gestureMode == PlayerGestureMode.NONE || gestureMode == PlayerGestureMode.IGNORED_DRAG) && !isDoubleTap && !didActivateSpeedHold) {
                    (1000L - elapsed).coerceAtLeast(1L)
                } else {
                    Long.MAX_VALUE
                }

                val event = if (remainingMs < 5000L) {
                    withTimeoutOrNull(remainingMs) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }

                if (event != null && event.changes.any { it.isConsumed }) {
                    wasConsumed = true
                }

                if (event == null) {
                    // Exactly 1 second reached while finger is held stationary
                    continue
                }

                // ─── Multi-touch Pinch-to-Zoom & Pan Detection ───
                val pressedPointers = event.changes.filter { it.pressed }
                if (pressedPointers.size >= 2) {
                    if (gestureMode == PlayerGestureMode.SPEED_HOLD && didActivateSpeedHold) {
                        isSpeedHolding = false
                        exoPlayer.setPlaybackSpeed(preHoldSpeed)
                        didActivateSpeedHold = false
                    }
                    gestureMode = PlayerGestureMode.PINCH_ZOOM
                    activeGestureMode = gestureMode
                    hasMoved = true

                    val p1 = pressedPointers[0].position
                    val p2 = pressedPointers[1].position
                    val currentDist = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)
                    val currentCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                    if (lastPinchDist > 0f) {
                        val zoomFactor = currentDist / lastPinchDist
                        val newScale = (videoZoomScale * zoomFactor).coerceIn(0.75f, 5.0f)
                        videoZoomScale = newScale

                        if (newScale > 1.05f) {
                            videoPanX += (currentCentroid.x - lastPinchCentroid.x)
                            videoPanY += (currentCentroid.y - lastPinchCentroid.y)
                        } else {
                            videoPanX = 0f
                            videoPanY = 0f
                        }

                        val pct = (newScale * 100).roundToInt()
                        zoomHUDText = if (abs(newScale - 1f) < 0.05f) "Fit (100%)" else "$pct%"
                        showZoomHUD = true
                    }

                    lastPinchDist = currentDist
                    lastPinchCentroid = currentCentroid
                    pressedPointers.forEach { it.consume() }
                    continue
                }

                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (change?.isConsumed == true) {
                        wasConsumed = true
                    }
                    break
                }

                currentX = change.position.x
                currentY = change.position.y
                val diffX = currentX - startX
                val diffY = currentY - startY
                finalDiffX = diffX
                finalDiffY = diffY

                // Step 1: Detect mode if still NONE
                if (gestureMode == PlayerGestureMode.NONE) {
                    val moveDistX = abs(diffX)
                    val moveDistY = abs(diffY)

                    if (videoZoomScale > 1.1f && (moveDistX > 15f || moveDistY > 15f)) {
                        hasMoved = true
                        gestureMode = PlayerGestureMode.PINCH_ZOOM
                        activeGestureMode = gestureMode
                        lastPanX = currentX
                        lastPanY = currentY
                    } else if (moveDistX > 45f || moveDistY > 45f) {
                        hasMoved = true
                        if (!isLandscape && !isVerticalVideo) {
                            // ─── PORTRAIT / SMALL WINDOWED MODE ───
                            // No brightness or volume controls in small windowed mode
                            if (diffY < -45f && moveDistY > moveDistX * 1.3f) {
                                // Gesturing swipe up on the screen makes it full screen
                                manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
                                currentOrientationSetting = 1
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                                activeGestureMode = gestureMode
                                change.consume()
                                break
                            } else if (moveDistX > 50f && moveDistX > moveDistY * 1.3f) {
                                if (appPreferences.isVideoSwitchGestureEnabled()) {
                                    gestureMode = PlayerGestureMode.VIDEO_SWITCH_SWIPE
                                    activeGestureMode = gestureMode
                                    change.consume()
                                }
                            } else if (diffY > 50f && moveDistY > moveDistX * 1.3f) {
                                // Downward swipe in portrait = enter PiP!
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val params = buildPipParams()
                                        if (params != null) {
                                            activity?.enterPictureInPictureMode(params)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            activity?.enterPictureInPictureMode()
                                        }
                                    } catch (_: Exception) {}
                                }
                                gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                                activeGestureMode = gestureMode
                                change.consume()
                                break
                            }
                        } else {
                            // ─── FULL-SCREEN VIDEO PLAYER MODE ───
                            // Extreme side areas have brightness (left) and volume (right).
                            // Screen center area has swipe down to return to windowed mode.
                            val isExtremeLeft = startX <= screenWidth * 0.35f
                            val isExtremeRight = startX >= screenWidth * 0.65f
                            val isCenterArea = startX in (screenWidth * 0.35f)..(screenWidth * 0.65f)

                            if (isExtremeLeft && moveDistY > moveDistX * 1.3f) {
                                if (appPreferences.isBrightnessGestureEnabled()) {
                                    gestureMode = PlayerGestureMode.BRIGHTNESS
                                    activeGestureMode = gestureMode
                                    change.consume()
                                }
                            } else if (isExtremeRight && moveDistY > moveDistX * 1.3f) {
                                if (appPreferences.isVolumeGestureEnabled()) {
                                    gestureMode = PlayerGestureMode.VOLUME
                                    activeGestureMode = gestureMode
                                    change.consume()
                                }
                            } else if (isVerticalVideo && moveDistX > 50f && moveDistX > moveDistY * 1.3f) {
                                if (appPreferences.isVideoSwitchGestureEnabled()) {
                                    gestureMode = PlayerGestureMode.VIDEO_SWITCH_SWIPE
                                    activeGestureMode = gestureMode
                                    change.consume()
                                }
                            } else if (isCenterArea) {
                                if (diffY > 50f && moveDistY > moveDistX * 1.3f) {
                                    if (isVerticalVideo) {
                                        // Swipe down on vertical video = enter PiP!
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            try {
                                                val params = buildPipParams()
                                                if (params != null) {
                                                    activity?.enterPictureInPictureMode(params)
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    activity?.enterPictureInPictureMode()
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                                        activeGestureMode = gestureMode
                                        change.consume()
                                        break
                                    } else {
                                        // Gesturing down at screen center area returns to windowed mode
                                        manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
                                        currentOrientationSetting = 0
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                                        activeGestureMode = gestureMode
                                        change.consume()
                                        break
                                    }
                                } else if (moveDistX > 45f && moveDistX > moveDistY) {
                                    // Horizontal swipe on screen disabled for seek; progress bar is used
                                    gestureMode = PlayerGestureMode.IGNORED_DRAG
                                    change.consume()
                                }
                            }
                        }
                    }
                }

                // Step 2: Handle ongoing active gesture mode
                when (gestureMode) {
                    PlayerGestureMode.BRIGHTNESS -> {
                        val dragRatio = -diffY / screenHeight
                        val newB = (startBrightness + dragRatio * 2.2f).coerceIn(0.01f, 1f)
                        brightnessPct = newB
                        hasUserAdjustedBrightness = true
                        showBrightnessBar = true
                        showVolumeBar = false
                        brightnessTouchTrigger = System.currentTimeMillis()
                        change.consume()
                    }
                    PlayerGestureMode.VOLUME -> {
                        val dragRatio = -diffY / screenHeight
                        val newVolRatio = ((startVolume.toFloat() / maxVolume.toFloat()) + dragRatio * 2.2f).coerceIn(0f, 1f)
                        volumePct = newVolRatio
                        val newVol = (newVolRatio * maxVolume).roundToInt().coerceIn(0, maxVolume)
                        if (newVol != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        }
                        showVolumeBar = true
                        showBrightnessBar = false
                        volumeTouchTrigger = System.currentTimeMillis()
                        change.consume()
                    }
                    PlayerGestureMode.SEEK -> {
                        val dragRatio = diffX / screenWidth
                        val delta = (dragRatio * 120000L).toLong()
                        currentDragSeekTarget = (startPosition + delta).coerceIn(0L, duration.coerceAtLeast(0L))
                        centerSeekIcon = if (delta >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind
                        centerSeekText = formatTime(currentDragSeekTarget)
                        change.consume()
                    }
                    PlayerGestureMode.SPEED_HOLD -> {
                        val deltaX = currentX - lastHoldX
                        lastHoldX = currentX
                        speedHoldAccumulatorX += deltaX

                        // Each ~48px of horizontal slide adjusts speed by 0.1x
                        val stepPx = 48f
                        if (kotlin.math.abs(speedHoldAccumulatorX) >= stepPx) {
                            val steps = (speedHoldAccumulatorX / stepPx).toInt()
                            speedHoldAccumulatorX -= steps * stepPx

                            val currentSpeed10 = kotlin.math.round(speedHoldDisplaySpeed * 10f).toInt()
                            val newSpeed10 = (currentSpeed10 + steps).coerceIn(2, 40) // 0.2x to 4.0x
                            val newSpeed = newSpeed10 / 10f

                            if (kotlin.math.abs(newSpeed - speedHoldDisplaySpeed) >= 0.05f) {
                                speedHoldDisplaySpeed = newSpeed
                                exoPlayer.setPlaybackSpeed(newSpeed)
                                view.performHaptic(HapticType.LIGHT)
                            }
                        }
                        change.consume()
                    }
                    PlayerGestureMode.PINCH_ZOOM -> {
                        val deltaX = currentX - lastPanX
                        val deltaY = currentY - lastPanY
                        lastPanX = currentX
                        lastPanY = currentY
                        videoPanX += deltaX
                        videoPanY += deltaY
                        change.consume()
                    }
                    PlayerGestureMode.VIDEO_SWITCH_SWIPE -> {
                        change.consume()
                    }
                    PlayerGestureMode.NONE, PlayerGestureMode.ORIENTATION_SWIPE, PlayerGestureMode.IGNORED_DRAG -> {
                        // Awaiting move or hold timeout or orientation change
                    }
                }
            }

            // Pointer lifted / up: Finalize gesture
            activeGestureMode = PlayerGestureMode.NONE
            when (gestureMode) {
                PlayerGestureMode.PINCH_ZOOM -> {
                    if (abs(videoZoomScale - 1f) < 0.08f) {
                        videoZoomScale = 1f
                        videoPanX = 0f
                        videoPanY = 0f
                    }
                }
                PlayerGestureMode.BRIGHTNESS -> {
                    brightnessTouchTrigger = System.currentTimeMillis()
                }
                PlayerGestureMode.VOLUME -> {
                    volumeTouchTrigger = System.currentTimeMillis()
                }
                PlayerGestureMode.SPEED_HOLD -> {
                    if (didActivateSpeedHold) {
                        isSpeedHolding = false
                        exoPlayer.setPlaybackSpeed(preHoldSpeed)
                    }
                }
                PlayerGestureMode.SEEK -> {
                    safeSeek(currentDragSeekTarget)
                    centerSeekText = null
                    centerSeekIcon = null
                }
                PlayerGestureMode.VIDEO_SWITCH_SWIPE -> {
                    if (finalDiffX < -50f) {
                        // Swiped Left -> Next Video
                        if (playlistVideos.isNotEmpty() && currentVideoIndex < playlistVideos.size - 1) {
                            val nextIndex = currentVideoIndex + 1
                            val nextItem = playlistVideos[nextIndex]
                            playNextVideo()
                            verticalSwipeSwitchHUD = "Next: ${nextItem.title.substringBeforeLast(".")}"
                            verticalSwipeSwitchIsNext = true
                            verticalSwipeSwitchKey = System.currentTimeMillis()
                            view.performHaptic(HapticType.MEDIUM)
                        } else {
                            Toast.makeText(context, "End of playlist", Toast.LENGTH_SHORT).show()
                        }
                    } else if (finalDiffX > 50f) {
                        // Swiped Right -> Previous Video
                        if (playlistVideos.isNotEmpty() && currentVideoIndex > 0) {
                            val prevIndex = currentVideoIndex - 1
                            val prevItem = playlistVideos[prevIndex]
                            playPreviousVideo()
                            verticalSwipeSwitchHUD = "Previous: ${prevItem.title.substringBeforeLast(".")}"
                            verticalSwipeSwitchIsNext = false
                            verticalSwipeSwitchKey = System.currentTimeMillis()
                            view.performHaptic(HapticType.MEDIUM)
                        } else {
                            Toast.makeText(context, "Beginning of playlist", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                PlayerGestureMode.ORIENTATION_SWIPE, PlayerGestureMode.IGNORED_DRAG -> {
                    // Swiped orientation or ignored drag - do not trigger tap
                }
                PlayerGestureMode.NONE -> {
                    if (!hasMoved && !didActivateSpeedHold && !wasConsumed) {
                        // Quick stationary tap!
                        if (isDoubleTap) {
                            lastTapTime = 0L // consume double tap
                            if (videoZoomScale != 1.0f) {
                                videoZoomScale = 1.0f
                                videoPanX = 0f
                                videoPanY = 0f
                                zoomHUDText = "Fit (100%)"
                                showZoomHUD = true
                                view.performHaptic(HapticType.MEDIUM)
                            } else {
                                val isCenter = startX in (screenWidth * 0.35f)..(screenWidth * 0.65f)
                                if (isCenter) {
                                    if (appPreferences.isDoubleTapCenterPlayPauseEnabled()) {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        centerDoubleTapPlayPause = !isPlaying
                                        centerDoubleTapKey = System.currentTimeMillis()
                                        if (appPreferences.isHapticsEnabled()) {
                                            view.performHaptic(HapticType.LIGHT)
                                        }
                                    }
                                } else {
                                    val isRightSide = startX > screenWidth * 0.65f
                                    val seekStepSec = appPreferences.getDoubleTapSeekSeconds()
                                    val stepMs = seekStepSec * 1000L
                                    if (isRightSide) {
                                        seekBy(stepMs)
                                        doubleTapIsForward = true
                                    } else {
                                        seekBy(-stepMs)
                                        doubleTapIsForward = false
                                    }
                                    doubleTapSeekSeconds += seekStepSec
                                    if (appPreferences.isHapticsEnabled()) {
                                        view.performHaptic(HapticType.LIGHT)
                                    }
                                }
                            }
                        } else {
                            lastTapTime = downTime
                            lastTapX = startX
                            lastTapY = startY
                            showControls = !showControls
                        }
                    }
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(showZoomHUD, zoomHUDText) {
        if (showZoomHUD) {
            delay(1500L)
            showZoomHUD = false
        }
    }

    // ─── PiP PURE VIDEO MODE: SHOW ONLY VIDEO SURFACE (NO SUBTITLES) ──────────
    if (isInPiP) {
        AndroidView(
            factory = { _ ->
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                playerView.subtitleView?.visibility = android.view.View.GONE
                playerView.subtitleView?.setCues(emptyList())
                playerView.subtitleView?.alpha = 0f
                playerView
            },
            update = { pv ->
                pv.resizeMode = resizeMode
                pv.subtitleView?.visibility = android.view.View.GONE
                pv.subtitleView?.setCues(emptyList())
                pv.subtitleView?.alpha = 0f
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // ─── ADAPTIVE ORIENTATION LAYOUT (Landscape vs Portrait YouTube Style) ───
    if (isLandscape || isVerticalVideo) {
        // ─── FULLSCREEN PLAYER MODE (Landscape or Fullscreen Vertical 9:16) ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(playerGestureModifier)
        ) {
            // ─── 0. Ambient Blurred Backdrop (Atmospheric Halo Reflection) ────────
            if (isVerticalVideo || isAmbientMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val bgBmp = seekThumbnail
                    if (bgBmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bgBmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(48.dp)
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentUrl)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(48.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isAmbientMode && !isVerticalVideo) 0.55f else 0.45f))
                    )
                }
            }

            // ─── 1. Video Surface with Pinch-to-Zoom & Pan ────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = videoZoomScale
                        scaleY = videoZoomScale
                        translationX = videoPanX
                        translationY = videoPanY
                    }
            ) {
                AndroidView(
                    factory = { _ ->
                        (playerView.parent as? ViewGroup)?.removeView(playerView)
                        playerView
                    },
                    update = { pv ->
                        pv.resizeMode = resizeMode
                        if (!isInPiP) {
                            pv.subtitleView?.visibility = android.view.View.VISIBLE
                            pv.subtitleView?.alpha = 1f
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

        // ─── 2. Night Mode Tint Overlay ───────────────────────────────────────
        if (isNightMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            )
        }

        // ─── 3. FAT VERTICAL GESTURE BAR: BRIGHTNESS (LEFT SIDE) ──────────────
        AnimatedVisibility(
            visible = showBrightnessBar && !isLocked && !isInPiP,
            enter = fadeIn(tween(140)) + slideInHorizontally(initialOffsetX = { -it / 2 }),
            exit = fadeOut(tween(250)) + slideOutHorizontally(targetOffsetX = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        ) {
            VerticalGestureBar(
                icon = Icons.Rounded.LightMode,
                percentage = brightnessPct,
                label = "${(brightnessPct * 100).roundToInt()}%",
                gradient = gestureBrush,
                glowColor = glowColor,
                onValueChange = {
                    brightnessPct = it.coerceIn(0.01f, 1f)
                    hasUserAdjustedBrightness = true
                    showBrightnessBar = true
                    brightnessTouchTrigger = System.currentTimeMillis()
                }
            )
        }

        // ─── 4. FAT VERTICAL GESTURE BAR: VOLUME (RIGHT SIDE) ─────────────────
        AnimatedVisibility(
            visible = showVolumeBar && !isLocked && !isInPiP,
            enter = fadeIn(tween(140)) + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut(tween(250)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        ) {
            VerticalGestureBar(
                icon = if (isMuted || volumePct <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else if (volumePct < 0.5f) Icons.AutoMirrored.Rounded.VolumeDown else Icons.AutoMirrored.Rounded.VolumeUp,
                percentage = if (isMuted) 0f else volumePct,
                label = if (isMuted) "Muted" else "${(volumePct * 100).roundToInt()}%",
                gradient = gestureBrush,
                glowColor = glowColor,
                onValueChange = {
                    if (isMuted) isMuted = false
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

        // ─── 5. Horizontal Seek Center HUD ────────────────────────────────────
        AnimatedVisibility(
            visible = centerSeekText != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp)
            ) {
                centerSeekIcon?.let {
                    Icon(it, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = centerSeekText ?: "",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ─── 6. Double Tap Ripple Indicator ───────────────────────────────────
        AnimatedVisibility(
            visible = doubleTapSeekSeconds > 0 && !isInPiP,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                val align = if (doubleTapIsForward) Alignment.CenterEnd else Alignment.CenterStart
                val padding = if (doubleTapIsForward) PaddingValues(end = 64.dp) else PaddingValues(start = 64.dp)
                Box(
                    modifier = Modifier
                        .align(align)
                        .padding(padding)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, primaryAccent.copy(alpha = 0.40f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!doubleTapIsForward) {
                            Icon(Icons.Rounded.FastRewind, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("-$doubleTapSeekSeconds s", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("+$doubleTapSeekSeconds s", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.FastForward, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }

        // ─── 6a. Center Double-Tap Play/Pause Ripple Indicator (Disabled per user request) ───

        // ─── 6c. Pinch-to-Zoom HUD Indicator ──────────────────────────────────
        AnimatedVisibility(
            visible = showZoomHUD && !isInPiP,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.80f))
                    .border(1.2.dp, primaryAccent, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(22.dp))
                    Text(text = zoomHUDText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // ─── 6b. Vertical Video Swipe-to-Switch HUD ───────────────────────────
        AnimatedVisibility(
            visible = verticalSwipeSwitchHUD != null && !isInPiP,
            enter = fadeIn(tween(120)) + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(1.2.dp, primaryAccent, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (verticalSwipeSwitchIsNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = verticalSwipeSwitchHUD ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ─── 6b. Press-Hold Speed HUD (Small Pill with Animated ">>" Icon) ───
        AnimatedVisibility(
            visible = isSpeedHolding && !isInPiP,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.90f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp)
        ) {
            TemporarySpeedHoldBadge(
                speed = speedHoldDisplaySpeed,
                accentColor = primaryAccent
            )
        }

        // ─── 6c. Floating Music Mini Bar in Fullscreen (Opaque Background) ───
        AnimatedVisibility(
            visible = (isMusicPlaying || nowPlayingTitle.isNotBlank()) && !isMusicDismissed && !isInPiP,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls) 120.dp else 24.dp, start = 16.dp, end = 16.dp)
        ) {
            VideoMusicMiniBar(
                title = nowPlayingTitle,
                artist = nowPlayingArtist,
                artUri = nowPlayingArtUri,
                isPlaying = isMusicPlaying,
                onTogglePlay = { MusicService.togglePlayPause() },
                onPrevious = { MusicService.playPrevious() },
                onNext = { MusicService.playNext() },
                onClose = { isMusicDismissed = true }
            )
        }



        // ─── 7. Main Controls Overlay ─────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls && !isInPiP,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().then(controlsTouchModifier)) {
                // Top Clean Black Gradient Scrim (No colored washes)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.78f),
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom Clean Black Gradient Scrim (No colored washes)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                if (!isLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ─── TOP SECTION: Compact Top Bar + Close Quick Action Pills ───
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top Bar (Back Button + Title + Live Time & Battery + Actions)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    IconButton(
                                        onClick = onBack,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = videoTitle,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = currentTimeStr,
                                                color = Color.White.copy(alpha = 0.75f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "•",
                                                color = Color.White.copy(alpha = 0.40f),
                                                fontSize = 11.sp
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = when {
                                                        isCharging -> Icons.Rounded.BatteryChargingFull
                                                        batteryPct >= 85 -> Icons.Rounded.BatteryFull
                                                        batteryPct >= 50 -> Icons.Rounded.Battery5Bar
                                                        batteryPct >= 20 -> Icons.Rounded.Battery3Bar
                                                        else -> Icons.Rounded.BatteryAlert
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isCharging) Color(0xFF4CAF50) else if (batteryPct <= 15) Color(0xFFFF5252) else primaryAccent,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    text = "$batteryPct%",
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            if (resolutionBadge != null) {
                                                Text(
                                                    text = "•",
                                                    color = Color.White.copy(alpha = 0.40f),
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = resolutionBadge,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Picture-in-Picture Button (Direct 1-Tap Entry)
                                    IconButton(
                                        onClick = enterPiP,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PictureInPicture,
                                            contentDescription = "PiP",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Color Theme Palette Button (Instant 1-tap cycle)
                                    // Theme button removed to declutter header

                                    // Playlist Button
                                    IconButton(
                                        onClick = { showPlaylistSheet = true },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = "Playlist",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // ─── Floating Player Controls Bar on Main Screen ───
                            FloatingPlayerControlsBar(
                                exoPlayer = exoPlayer,
                                scrollState = optionsScrollState,
                                onOpenAudioDialog = {
                                    showAudioTrackSheet = true
                                },
                                onOpenSubtitleDialog = {
                                    showSubtitleSheet = true
                                },
                                playbackSpeed = playbackSpeed,
                                onOpenSpeedDialog = { showSpeedSheet = true },
                                onResetSpeedToOne = resetSpeedToOne,
                                onEnterPiP = enterPiP,
                                repeatMode = repeatMode,
                                onCycleRepeatMode = {
                                    repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    exoPlayer.repeatMode = repeatMode
                                    appPreferences.setPlayerRepeatMode(repeatMode)
                                    Toast.makeText(context, when(repeatMode) {
                                        Player.REPEAT_MODE_ONE -> "Repeat: Loop 1"
                                        Player.REPEAT_MODE_ALL -> "Repeat: Loop All"
                                        else -> "Repeat: Off"
                                    }, Toast.LENGTH_SHORT).show()
                                },
                                isNightMode = isNightMode,
                                onToggleNightMode = {
                                    isNightMode = !isNightMode
                                    appPreferences.setNightMode(isNightMode)
                                    Toast.makeText(context, if (isNightMode) "Night mode ON" else "Night mode OFF", Toast.LENGTH_SHORT).show()
                                },
                                sleepTimerMinutes = sleepTimerMinutes,
                                onOpenSleepTimerDialog = { showSleepTimerSheet = true },
                                isBackgroundAudio = isBackgroundAudio,
                                onToggleBackgroundAudio = {
                                    isBackgroundAudio = !isBackgroundAudio
                                    appPreferences.setBackgroundPlayEnabled(isBackgroundAudio)
                                    Toast.makeText(context, if (isBackgroundAudio) "Background audio ON" else "Background audio OFF", Toast.LENGTH_SHORT).show()
                                },
                                onOpenVideoInfo = { showVideoInfoSheet = true },
                                loopStartMs = loopStartMs,
                                loopEndMs = loopEndMs,
                                onToggleLoop = {
                                    if (loopStartMs == null) {
                                        loopStartMs = currentPosition
                                        Toast.makeText(context, "Loop Point A set at ${formatTime(currentPosition)}", Toast.LENGTH_SHORT).show()
                                    } else if (loopEndMs == null) {
                                        if (currentPosition > loopStartMs!!) {
                                            loopEndMs = currentPosition
                                            Toast.makeText(context, "Loop Point B set. Looping A-B", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        loopStartMs = null
                                        loopEndMs = null
                                        Toast.makeText(context, "A-B Loop cleared", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onAddBookmark = {
                                    appPreferences.saveBookmark(currentUrl, currentPosition, "")
                                    bookmarksList = appPreferences.getBookmarks(currentUrl)
                                    Toast.makeText(context, "Bookmark saved at ${formatTime(currentPosition)}", Toast.LENGTH_SHORT).show()
                                },
                                primaryAccent = primaryAccent,
                                onOpenSettings = {
                                    showMoreMenuSheet = false
                                    showSettingsOverlay = true
                                },
                                isExpanded = isQuickActionsExpanded,
                                onToggleExpanded = {
                                    isQuickActionsExpanded = !isQuickActionsExpanded
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                },
                                isAmbientMode = isAmbientMode,
                                onToggleAmbientMode = toggleAmbientMode,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }

                        // ─── BOTTOM SECTION: Transparent Progress Bar + Media Controls ───
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isSeekingNow = isDraggingSeek
                            val previewTimeMs = scrubPosition.toLong()
                            AnimatedVisibility(
                                visible = isSeekingNow,
                                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
                                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(86.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                            .border(1.5.dp, primaryAccent, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val bmp = seekThumbnail
                                        if (bmp != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Seek preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = primaryAccent,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.85f))
                                            .border(0.8.dp, primaryAccent, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatTime(previewTimeMs),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            // ─── Transparent Progress Bar Slider Row (Thin Track & Pure Circle Thumb) ───
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(if (isDraggingSeek) scrubPosition.toLong() else currentPosition),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )

                                Slider(
                                    value = if (isDraggingSeek) scrubPosition else currentPosition.toFloat(),
                                    onValueChange = {
                                        isDraggingSeek = true
                                        scrubPosition = it
                                        val dur = duration.toFloat().coerceAtLeast(1f)
                                        val milestone = ((it / dur) * 4).toInt()
                                        if (milestone != lastSeekMilestone) {
                                            lastSeekMilestone = milestone
                                            view.performHaptic(HapticType.LIGHT)
                                        }
                                    },
                                    onValueChangeFinished = {
                                        isDraggingSeek = false
                                        safeSeek(scrubPosition.toLong())
                                    },
                                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                    track = { sliderState ->
                                        val dur = duration.toFloat().coerceAtLeast(1f)
                                        val curVal = if (isDraggingSeek) scrubPosition else currentPosition.toFloat()
                                        val playFraction = (curVal / dur).coerceIn(0f, 1f)
                                        val buffFraction = (bufferedPosition.toFloat() / dur).coerceIn(0f, 1f)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = 0.22f))
                                        ) {
                                            // Buffered progress layer with theme accent
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(buffFraction)
                                                    .fillMaxHeight()
                                                    .background(primaryAccent.copy(alpha = 0.38f))
                                            )
                                            // Active played layer
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(playFraction)
                                                    .fillMaxHeight()
                                                    .background(primaryAccent)
                                            )
                                        }
                                    },
                                    thumb = {
                                        Box(
                                            modifier = Modifier
                                                .size(if (isDraggingSeek) 18.dp else 14.dp)
                                                .clip(CircleShape)
                                                .background(primaryAccent)
                                                .border(2.dp, Color.White, CircleShape)
                                        )
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = primaryAccent,
                                        activeTrackColor = primaryAccent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                )

                                Text(
                                    text = if (showRemainingTime) "-${formatTime(maxOf(0L, duration - currentPosition))}" else formatTime(duration),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // ─── Bottom Media Controls Bar (Adaptive Layout) ───
                            if (isLandscape) {
                                // Landscape: Wide single-row Box layout (100% Symmetrical)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    // Left Group: Lock & Mute
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterStart),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        IconButton(
                                            onClick = { 
                                                view.performHaptic(HapticType.MEDIUM)
                                                isLocked = true 
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.LockOpen,
                                                contentDescription = "Lock controls",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = toggleMute,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                if (isMuted || volumePct == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                                contentDescription = "Mute toggle",
                                                tint = if (isMuted) Color(0xFFFF5252) else primaryAccent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    // Center Media Group: [10s Back] [Prev] [Play/Pause (64dp)] [Next] [10s Forward]
                                    // Strictly pinned to exact screen center
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        IconButton(
                                            onClick = { seekBy(-configuredSeekStepMs) },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Replay10,
                                                contentDescription = "-${configuredSeekStepSec}s",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = playPreviousVideo,
                                            enabled = currentVideoIndex > 0,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.SkipPrevious,
                                                contentDescription = "Previous video",
                                                tint = if (currentVideoIndex > 0) primaryAccent else primaryAccent.copy(alpha = 0.35f),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        // Center Play/Pause button with theme-colored radiant gradient & white icon
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Brush.radialGradient(listOf(primaryAccent, primaryAccent.copy(alpha = 0.85f))))
                                                .border(2.dp, Color.White, CircleShape)
                                                .clickable {
                                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = playNextVideo,
                                            enabled = currentVideoIndex >= 0 && currentVideoIndex < playlistVideos.size - 1,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.SkipNext,
                                                contentDescription = "Next video",
                                                tint = if (currentVideoIndex >= 0 && currentVideoIndex < playlistVideos.size - 1)
                                                    primaryAccent else primaryAccent.copy(alpha = 0.35f),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { seekBy(configuredSeekStepMs) },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Forward10,
                                                contentDescription = "+${configuredSeekStepSec}s",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // Right Group: Aspect Ratio & Fullscreen Exit
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        IconButton(
                                            onClick = cycleResizeMode,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.FitScreen,
                                                contentDescription = "Aspect ratio",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
                                                currentOrientationSetting = if (isLandscape) 0 else 1
                                                activity?.requestedOrientation = if (isLandscape) {
                                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                                } else {
                                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                }
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Rounded.ScreenRotation,
                                                contentDescription = "Rotate Screen",
                                                tint = primaryAccent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Portrait / Vertical Video: Clean 1-row layout (Lock, Prev, Play/Pause, Next, Aspect Ratio)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // 1. Lock Controls
                                    IconButton(
                                        onClick = { 
                                            view.performHaptic(HapticType.MEDIUM)
                                            isLocked = true 
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.LockOpen,
                                            contentDescription = "Lock controls",
                                            tint = primaryAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // 2. Previous Video
                                    IconButton(
                                        onClick = playPreviousVideo,
                                        enabled = currentVideoIndex > 0,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.SkipPrevious,
                                            contentDescription = "Previous video",
                                            tint = if (currentVideoIndex > 0) primaryAccent else primaryAccent.copy(alpha = 0.35f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    // 3. Center Play/Pause Button
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(Brush.radialGradient(listOf(primaryAccent, primaryAccent.copy(alpha = 0.85f))))
                                            .border(2.dp, Color.White, CircleShape)
                                            .clickable {
                                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    // 4. Next Video
                                    IconButton(
                                        onClick = playNextVideo,
                                        enabled = currentVideoIndex >= 0 && currentVideoIndex < playlistVideos.size - 1,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.SkipNext,
                                            contentDescription = "Next video",
                                            tint = if (currentVideoIndex >= 0 && currentVideoIndex < playlistVideos.size - 1)
                                                primaryAccent else primaryAccent.copy(alpha = 0.35f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    // 5. Aspect Ratio / Resize Mode
                                    IconButton(
                                        onClick = cycleResizeMode,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.FitScreen,
                                            contentDescription = "Aspect ratio",
                                            tint = primaryAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Screen Locked Floating Unlock Button ─────────────────────
                if (isLocked) {
                    IconButton(
                        onClick = { 
                            view.performHaptic(HapticType.MEDIUM)
                            isLocked = false 
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .systemBarsPadding()
                            .padding(start = 24.dp, bottom = 24.dp)
                            .size(54.dp)
                            .shadow(14.dp, CircleShape, ambientColor = primaryAccent, spotColor = primaryAccent)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF28293B), Color(0xFF101118))))
                            .border(2.dp, primaryAccent, CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
    } else {
        // ─── PORTRAIT MODE (YouTube Style: Top 16:9 Video + Bottom App Explorer) ───
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1F26))
        ) {
            // ─── Top Adaptive Aspect Ratio Video Box ───
            val videoBoxRatio = if (videoWidth > 0 && videoHeight > 0) {
                (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(1.0f, 2.4f)
            } else {
                16f / 9f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(videoBoxRatio)
                    .background(Color.Black)
                    .then(playerGestureModifier)
            ) {
                // 0. Ambient Blurred Backdrop for Portrait Video Box
                if (isAmbientMode) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val bgBmp = seekThumbnail
                        if (bgBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bgBmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(36.dp)
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentUrl)
                                    .decoderFactory(VideoFrameDecoder.Factory())
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(36.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.50f))
                        )
                    }
                }

                // 1. Video Surface with Pinch-to-Zoom & Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer {
                            scaleX = videoZoomScale
                            scaleY = videoZoomScale
                            translationX = videoPanX
                            translationY = videoPanY
                        }
                ) {
                    AndroidView(
                        factory = { _ ->
                            (playerView.parent as? ViewGroup)?.removeView(playerView)
                            playerView
                        },
                        update = { pv ->
                            pv.resizeMode = resizeMode
                            if (!isInPiP) {
                                pv.subtitleView?.visibility = android.view.View.VISIBLE
                                pv.subtitleView?.alpha = 1f
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. Night mode
                if (isNightMode) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
                }



                // 4. Center Seek HUD
                androidx.compose.animation.AnimatedVisibility(
                    visible = centerSeekText != null,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        centerSeekIcon?.let {
                            Icon(it, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = centerSeekText ?: "",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 5. Speed Hold HUD (Small Pill with Animated ">>" Icon)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSpeedHolding && !isInPiP,
                    enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.90f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    TemporarySpeedHoldBadge(
                        speed = speedHoldDisplaySpeed,
                        accentColor = primaryAccent
                    )
                }

                // 5b. Pinch-to-Zoom HUD Indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = showZoomHUD && !isInPiP,
                    enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.80f))
                            .border(1.2.dp, primaryAccent, RoundedCornerShape(24.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ZoomIn,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = zoomHUDText,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 6. Seek Thumbnail Preview
                val isSeekingNow = isDraggingSeek
                val previewTimeMs = scrubPosition.toLong()
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSeekingNow,
                    enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 44.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .border(1.2.dp, primaryAccent, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = seekThumbnail
                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Seek preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primaryAccent, strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .border(0.8.dp, primaryAccent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatTime(previewTimeMs),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 7. Portrait Controls Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !isInPiP,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize().then(controlsTouchModifier)) {
                        // Top Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .align(Alignment.TopCenter)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.70f), Color.Transparent)))
                        )
                        // Bottom Scrim
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                        )

                        // Top Section (Top Bar with Title, Details & Palette)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = videoTitle,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = currentTimeStr, color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Text(text = "•", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                                        Text(text = "$batteryPct%", color = Color.White.copy(alpha = 0.70f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = enterPiP,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PictureInPicture,
                                        contentDescription = "PiP",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showPlaylistSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = "Playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                QuickActionButton(
                                    icon = Icons.Rounded.Info,
                                    label = "Details",
                                    isActive = false,
                                    activeColor = primaryAccent,
                                    onClick = { showVideoInfoSheet = true }
                                )

                                // Theme button removed to declutter header
                            }
                        }

                        // Center Controls (10s Back, Play/Pause, 10s Forward)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            IconButton(
                                onClick = { seekBy(-configuredSeekStepMs) },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                            ) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "-${configuredSeekStepSec}s", tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(primaryAccent, primaryAccent.copy(alpha = 0.85f))))
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { seekBy(configuredSeekStepMs) },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                            ) {
                                Icon(Icons.Rounded.Forward10, contentDescription = "+${configuredSeekStepSec}s", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }

                        // Bottom Slider Bar
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(if (isDraggingSeek) scrubPosition.toLong() else currentPosition),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )

                            Slider(
                                value = if (isDraggingSeek) scrubPosition else currentPosition.toFloat(),
                                onValueChange = {
                                    isDraggingSeek = true
                                    scrubPosition = it
                                    val dur = duration.toFloat().coerceAtLeast(1f)
                                    val milestone = ((it / dur) * 4).toInt()
                                    if (milestone != lastSeekMilestone) {
                                        lastSeekMilestone = milestone
                                        view.performHaptic(HapticType.LIGHT)
                                    }
                                },
                                onValueChangeFinished = {
                                    isDraggingSeek = false
                                    safeSeek(scrubPosition.toLong())
                                },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                track = { sliderState ->
                                    val dur = duration.toFloat().coerceAtLeast(1f)
                                    val curVal = if (isDraggingSeek) scrubPosition else currentPosition.toFloat()
                                    val playFraction = (curVal / dur).coerceIn(0f, 1f)
                                    val buffFraction = (bufferedPosition.toFloat() / dur).coerceIn(0f, 1f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.5.dp)
                                            .clip(RoundedCornerShape(1.75.dp))
                                            .background(Color.White.copy(alpha = 0.22f))
                                    ) {
                                        // Buffered progress layer with theme accent
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(buffFraction)
                                                .fillMaxHeight()
                                                .background(primaryAccent.copy(alpha = 0.38f))
                                        )
                                        // Active played layer
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(playFraction)
                                                .fillMaxHeight()
                                                .background(primaryAccent)
                                        )
                                    }
                                },
                                thumb = {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isDraggingSeek) 15.dp else 12.dp)
                                            .clip(CircleShape)
                                            .background(primaryAccent)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = primaryAccent,
                                    activeTrackColor = primaryAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            )

                            Text(
                                text = if (showRemainingTime) "-${formatTime(maxOf(0L, duration - currentPosition))}" else formatTime(duration),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                            )

                            Spacer(Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
                                    currentOrientationSetting = 1
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Floating Music Mini Bar in Portrait Video Section ───
            AnimatedVisibility(
                visible = (isMusicPlaying || nowPlayingTitle.isNotBlank()) && !isMusicDismissed && !isInPiP,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                VideoMusicMiniBar(
                    title = nowPlayingTitle,
                    artist = nowPlayingArtist,
                    artUri = nowPlayingArtUri,
                    isPlaying = isMusicPlaying,
                    onTogglePlay = { MusicService.togglePlayPause() },
                    onPrevious = { MusicService.playPrevious() },
                    onNext = { MusicService.playNext() },
                    onClose = { isMusicDismissed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }



            // Bottom Section: Portrait App Explorer (Explore while video plays!)
            PortraitAppExplorer(
                currentUrl = currentUrl,
                videoTitle = videoTitle,
                duration = duration,
                playlistVideos = playlistVideos,
                allFolders = allFolders,
                allVideos = allVideos,
                repeatMode = repeatMode,
                onRepeatToggle = {
                    repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    exoPlayer.repeatMode = repeatMode
                    appPreferences.setPlayerRepeatMode(repeatMode)
                },
                playbackSpeed = playbackSpeed,
                onSpeedClick = { showSpeedSheet = true },
                onResetSpeedToOne = resetSpeedToOne,
                isNightMode = isNightMode,
                onNightModeToggle = {
                    isNightMode = !isNightMode
                    appPreferences.setNightMode(isNightMode)
                },
                resizeMode = resizeMode,
                onAspectToggle = cycleResizeMode,
                isMuted = isMuted,
                onMuteToggle = toggleMute,
                onPiPClick = enterPiP,
                onDetailsClick = {
                    val vf = exoPlayer.videoFormat
                    val res = vf?.let { "${it.width}x${it.height}" } ?: "Adapted"
                    val fps = vf?.frameRate?.takeIf { it > 0 }?.let { "${it.toInt()}fps" } ?: ""
                    val infoText = "$res ${if (fps.isNotEmpty()) "• $fps " else ""}• ${formatTime(duration)}"
                    Toast.makeText(context, infoText, Toast.LENGTH_LONG).show()
                },
                onSelectVideo = playVideoItem,
                isAmbientMode = isAmbientMode,
                onAmbientModeToggle = toggleAmbientMode,
                primaryAccent = primaryAccent
            )
        }
    }

    // ─── Settings In-Player Overlay (Preserves video playback & supports PiP anywhere) ───
    if (showSettingsOverlay && !isInPiP) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSettingsOverlay = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = c.baseBackground
            ) {
                SettingsScreen(
                    onBack = { showSettingsOverlay = false }
                )
            }
        }
    }

        // ─── Right-Side Playlist Drawer ─────────────────────────────────────────
        AnimatedVisibility(
            visible = showPlaylistSheet,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectTapGestures { showPlaylistSheet = false }
                    }
            ) {
                var playlistSheetTab by remember { mutableIntStateOf(0) }

                AnimatedVisibility(
                    visible = showPlaylistSheet,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(220)
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .background(c.dropdownBg)
                            .border(
                                width = 1.dp,
                                color = c.glassBorder,
                                shape = RectangleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures { /* consume taps inside drawer */ }
                            }
                            .systemBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (playlistSheetTab == 0) "Playlist" else "Bookmarks",
                                    color = c.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (playlistSheetTab == 0) "${playlistVideos.size} videos" else "${bookmarksList.size} bookmarks",
                                    color = c.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            IconButton(
                                onClick = { showPlaylistSheet = false },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(c.glassBgNested)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        var playlistSortOrder by remember { mutableStateOf(SortOrder.DATE) }
                        var showPlaylistSortMenu by remember { mutableStateOf(false) }
                        val sortedPlaylistVideos = remember(playlistVideos, playlistSortOrder) {
                            playlistVideos.sortVideosWithOrder(playlistSortOrder)
                        }

                        // Tab selector & Sort button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (playlistSheetTab == 0) primaryAccent else c.glassBgNested)
                                        .clickable { playlistSheetTab = 0 }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Videos (${playlistVideos.size})",
                                        color = if (playlistSheetTab == 0) Color.White else c.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (playlistSheetTab == 1) primaryAccent else c.glassBgNested)
                                        .clickable { playlistSheetTab = 1 }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Bookmarks (${bookmarksList.size})",
                                        color = if (playlistSheetTab == 1) Color.White else c.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (playlistSheetTab == 0) {
                                Box {
                                    IconButton(
                                        onClick = { showPlaylistSortMenu = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c.glassBgNested)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.Sort,
                                            contentDescription = "Sort",
                                            tint = primaryAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showPlaylistSortMenu,
                                        onDismissRequest = { showPlaylistSortMenu = false },
                                        containerColor = c.dropdownBg,
                                        modifier = Modifier.border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                                    ) {
                                        Text(
                                            "SORT PLAYLIST",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = c.textHint,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                        HorizontalDivider(color = c.glassBorder.copy(alpha = 0.5f))
                                        listOf(
                                            SortOrder.DATE to ("Date Added (Newest)" to Icons.Rounded.Schedule),
                                            SortOrder.DATE_ASC to ("Date Added (Oldest)" to Icons.Rounded.Schedule),
                                            SortOrder.NAME to ("Name (A to Z)" to Icons.Rounded.SortByAlpha),
                                            SortOrder.NAME_DESC to ("Name (Z to A)" to Icons.Rounded.SortByAlpha),
                                            SortOrder.SIZE to ("Size (Largest)" to Icons.Rounded.Storage),
                                            SortOrder.SIZE_ASC to ("Size (Smallest)" to Icons.Rounded.Storage),
                                            SortOrder.DURATION to ("Duration (Longest)" to Icons.Rounded.Schedule),
                                            SortOrder.DURATION_ASC to ("Duration (Shortest)" to Icons.Rounded.Schedule)
                                        ).forEach { (order, pair) ->
                                            val (label, icon) = pair
                                            val isSelected = playlistSortOrder == order
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(icon, contentDescription = null, tint = if (isSelected) primaryAccent else c.textSecondary, modifier = Modifier.size(16.dp))
                                                },
                                                text = {
                                                    Text(label, color = if (isSelected) primaryAccent else c.textPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                                },
                                                trailingIcon = {
                                                    if (isSelected) {
                                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(16.dp))
                                                    }
                                                },
                                                onClick = {
                                                    playlistSortOrder = order
                                                    showPlaylistSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (playlistSheetTab == 0) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(sortedPlaylistVideos, key = { _, item -> item.uri.toString() }) { index, item ->
                                    val isCurrent = item.uri.toString() == currentUrl
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isCurrent) primaryAccent.copy(alpha = 0.22f)
                                                else c.cardBgElevated
                                            )
                                            .border(
                                                width = if (isCurrent) 1.5.dp else 1.dp,
                                                color = if (isCurrent) primaryAccent
                                                else c.glassBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                playVideoItem(item)
                                                showPlaylistSheet = false
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 0. Track Number
                                        Box(
                                            modifier = Modifier.width(26.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = if (isCurrent) primaryAccent else c.textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Spacer(Modifier.width(4.dp))

                                        // 1. Video Thumbnail
                                        Box(
                                            modifier = Modifier
                                                .size(width = 76.dp, height = 48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(item.uri)
                                                    .decoderFactory(VideoFrameDecoder.Factory())
                                                    .build(),
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.45f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.PlayArrow,
                                                        contentDescription = "Playing",
                                                        tint = primaryAccent,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        // 2. Metadata: Name, Size, and Duration
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                color = if (isCurrent) primaryAccent else c.textPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = formatSize(item.size),
                                                    color = c.textSecondary,
                                                    fontSize = 11.5.sp
                                                )
                                                Text(
                                                    text = "•",
                                                    color = c.textHint,
                                                    fontSize = 11.5.sp
                                                )
                                                Text(
                                                    text = formatTime(item.duration),
                                                    color = c.textSecondary,
                                                    fontSize = 11.5.sp
                                                )
                                            }

                                            if (isCurrent && duration > 0) {
                                                Spacer(Modifier.height(4.dp))
                                                val prog = (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.5.dp)
                                                        .clip(RoundedCornerShape(1.2.dp))
                                                        .background(c.glassBorder)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(prog)
                                                            .fillMaxHeight()
                                                            .background(primaryAccent)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Bookmarks list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Button(
                                        onClick = {
                                            appPreferences.saveBookmark(currentUrl, currentPosition, "")
                                            bookmarksList = appPreferences.getBookmarks(currentUrl)
                                            Toast.makeText(context, "Bookmark added at ${formatTime(currentPosition)}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("+ Add Bookmark at ${formatTime(currentPosition)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (bookmarksList.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No bookmarks saved yet", color = c.textHint, fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    items(bookmarksList) { bookmark ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(c.cardBgElevated)
                                                .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    safeSeek(bookmark.first)
                                                    showPlaylistSheet = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = bookmark.second.ifBlank { "Bookmark" },
                                                    color = c.textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = formatTime(bookmark.first),
                                                    color = primaryAccent,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    appPreferences.deleteBookmark(currentUrl, bookmark.first)
                                                    bookmarksList = appPreferences.getBookmarks(currentUrl)
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        // ─── Mini Playback Speed Dialog ──────────────────────────────────────
        if (showSpeedSheet) {
            MiniSpeedDialog(
                currentSpeed = playbackSpeed,
                onSpeedChange = { newSpeed ->
                    view.performHaptic(HapticType.LIGHT)
                    playbackSpeed = newSpeed
                    exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
                    if (appPreferences.isRememberPlaybackSpeed()) {
                        appPreferences.setLastPlaybackSpeed(newSpeed)
                    }
                },
                accentColor = primaryAccent,
                onDismiss = { showSpeedSheet = false }
            )
        }

        // ─── Mini Sleep Timer Dialog ──────────────────────────────────────────
        if (showSleepTimerSheet) {
            MiniSleepTimerDialog(
                currentMinutes = sleepTimerMinutes,
                onSelectTimer = { mins ->
                    sleepTimerMinutes = mins
                    showSleepTimerSheet = false
                    Toast.makeText(context, if (mins > 0) "Sleep timer set for ${mins}m" else "Sleep timer disabled", Toast.LENGTH_SHORT).show()
                },
                accentColor = primaryAccent,
                onDismiss = { showSleepTimerSheet = false }
            )
        }

        // ─── Theme Picker Bottom Sheet ────────────────────────────────────────
        if (showThemePickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showThemePickerSheet = false },
                containerColor = c.dropdownBg,
                scrimColor = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Choose Player Dynamic Theme", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerTheme.entries.forEach { theme ->
                            val isSelected = activeTheme == theme
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) theme.primaryAccent.copy(alpha = 0.18f) else c.glassBgNested)
                                    .border(1.5.dp, if (isSelected) theme.primaryAccent else c.glassBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        themeController.updateColorTheme(theme)
                                        showThemePickerSheet = false
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(theme.primaryAccent, theme.secondaryAccent)))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(theme.displayName, color = if (isSelected) theme.primaryAccent else c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(theme.description, color = c.textSecondary, fontSize = 12.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // ─── Audio Tracks & Language Dialog ───
        if (showAudioTrackSheet) {
            AudioTracksDialog(
                exoPlayer = exoPlayer,
                accentColor = primaryAccent,
                onDismiss = { showAudioTrackSheet = false }
            )
        }

        // ─── Subtitles & Captions Dialog (Tracks + 4 Cinema Styles) ───
        if (showSubtitleSheet) {
            SubtitlesDialog(
                exoPlayer = exoPlayer,
                subtitleDesign = subtitleDesign,
                onSubtitleDesignChange = { newDesign ->
                    subtitleDesign = newDesign
                    applySubtitleDesign(playerView, newDesign, appPreferences)
                    appPreferences.saveSubtitleDesign(newDesign)
                },
                accentColor = primaryAccent,
                onDismiss = { showSubtitleSheet = false }
            )
        }

        // ─── Mini Video Details Dialog ────────────────────────────────────────
        if (showVideoInfoSheet) {
            MiniVideoDetailsDialog(
                exoPlayer = exoPlayer,
                title = videoTitle,
                currentUrl = currentUrl,
                durationMs = duration,
                accentColor = primaryAccent,
                onDismiss = { showVideoInfoSheet = false }
            )
        }
    }

// ─── FAT VERTICAL GESTURE BAR COMPONENT ───────────────────────────────────────
@Composable
private fun VerticalGestureBar(
    icon: ImageVector,
    percentage: Float,
    label: String,
    gradient: Brush,
    glowColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val haptic = LocalHapticFeedback.current
    val clampedPct = percentage.coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = clampedPct,
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "smoothGesturePct"
    )

    var lastHapticMilestone by remember { mutableIntStateOf((clampedPct * 10).toInt()) }
    val updateValueWithHaptic: (Float) -> Unit = { newVal ->
        val cl = newVal.coerceIn(0f, 1f)
        val milestone = (cl * 10).toInt()
        if (milestone != lastHapticMilestone) {
            lastHapticMilestone = milestone
            view.performHaptic(HapticType.LIGHT)
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
                            // Direct 1:1 scrub tracking with instant responsiveness
                            val targetPct = (1f - (currentY / barHeight)).coerceIn(0f, 1f)
                            updateValueWithHaptic(targetPct)
                            change.consume()
                        }
                    }

                    if (!hasMoved) {
                        // Quick stationary tap on the bar
                        view.performHaptic(HapticType.LIGHT)
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
        // Visual Glassmorphic Bar (42.dp wide, 190.dp tall - sleek capsule)
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(190.dp)
                .shadow(16.dp, RoundedCornerShape(21.dp), ambientColor = glowColor.copy(alpha = 0.4f), spotColor = glowColor.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(21.dp))
                .background(Color(0xD90E0F17))
                .border(1.2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(21.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Vertical Fill Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedPct)
                    .clip(RoundedCornerShape(21.dp))
                    .background(gradient)
            )

            // Inside Indicator: Top Icon and Bottom Percentage Label (No +/-)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Icon
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )

                // Bottom Percentage Label
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Temporary Press-Hold Speed Badge (Small Capsule with Animated ">>" Icon) ───
@Composable
private fun TemporarySpeedHoldBadge(
    speed: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speedChevron")
    val chevronAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronBlink"
    )

    val speedText = if (abs(speed - speed.toInt().toFloat()) < 0.05f) {
        "${speed.toInt()}X"
    } else {
        String.format(Locale.getDefault(), "%.1fX", speed)
    }

    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xD90E0F17))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = speedText,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp
            )

            // Animated ">>" on / off chevrons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = chevronAlpha }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(9.5.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(9.5.dp)
                        .offset(x = (-3.5).dp)
                )
            }
        }
    }
}

// ─── Compact Quick Action Pill (Close together & space efficient) ────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF22C55E),
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val contentColor = if (isActive) Color.White else Color.White.copy(alpha = 0.92f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(
                elevation = if (isActive) 5.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isActive) activeColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
                spotColor = if (isActive) activeColor.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) Brush.horizontalGradient(
                    listOf(activeColor, activeColor.copy(alpha = 0.82f))
                )
                else Brush.verticalGradient(
                    listOf(Color(0xFF292C3A), Color(0xFF1E202B))
                )
            )
            .border(
                width = if (isActive) 1.dp else 0.8.dp,
                color = if (isActive) activeColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}

// ─── Menu Item Helper ─────────────────────────────────────────────────────────
@Composable
private fun PlayerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.40f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Subtitle Style Applicator (4 Distinct Designs) ──────────────────────────
fun applySubtitleDesign(playerView: PlayerView?, design: Int, appPreferences: AppPreferences? = null) {
    val subtitleView = playerView?.subtitleView ?: return
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    val fontSize = appPreferences?.getSubtitleFontSize()?.toFloat() ?: 18f
    subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)

    val captionStyle = if (appPreferences != null && design == 0) {
        val textColor = appPreferences.getSubtitleColor().toInt()
        val bgStyle = appPreferences.getSubtitleBackgroundStyle()
        val bgColor = when (bgStyle) {
            1 -> android.graphics.Color.argb(180, 0, 0, 0)
            2 -> android.graphics.Color.argb(255, 0, 0, 0)
            else -> android.graphics.Color.TRANSPARENT
        }
        val outlineStyle = appPreferences.getSubtitleOutlineStyle()
        val edgeType = when (outlineStyle) {
            1 -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            0 -> CaptionStyleCompat.EDGE_TYPE_NONE
            else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        }
        val edgeColor = if (outlineStyle == 0) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
        CaptionStyleCompat(
            textColor,
            bgColor,
            android.graphics.Color.TRANSPARENT,
            edgeType,
            edgeColor,
            Typeface.DEFAULT_BOLD
        )
    } else {
        when (design) {
            // Design 0: Classic Cinema - Crisp white text, transparent bg, bold black outline
            0 -> CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                Typeface.DEFAULT_BOLD
            )
            // Design 1: Dark Box - Clean white text on translucent dark-grey box backing
            1 -> CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.argb(215, 26, 27, 34),
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT,
                Typeface.SANS_SERIF
            )
            // Design 2: Golden Film - Warm golden-yellow text with drop shadow
            2 -> CaptionStyleCompat(
                android.graphics.Color.rgb(255, 215, 0),
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                android.graphics.Color.argb(220, 0, 0, 0),
                Typeface.DEFAULT_BOLD
            )
            // Design 3: Cyber Neon - High-contrast cyan text with deep backing and cyan neon outline
            3 -> CaptionStyleCompat(
                android.graphics.Color.rgb(0, 240, 255),
                android.graphics.Color.argb(220, 16, 18, 25),
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.rgb(0, 130, 160),
                Typeface.MONOSPACE
            )
            else -> CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                Typeface.DEFAULT_BOLD
            )
        }
    }
    subtitleView.setStyle(captionStyle)
}

// ─── Floating Player Controls Bar (Direct On-Screen Actions) ─────────────────
@Composable
private fun FloatingPlayerControlsBar(
    exoPlayer: ExoPlayer,
    scrollState: ScrollState = rememberScrollState(),
    isExpanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    onOpenAudioDialog: () -> Unit,
    onOpenSubtitleDialog: () -> Unit,
    playbackSpeed: Float,
    onOpenSpeedDialog: () -> Unit,
    onResetSpeedToOne: () -> Unit = {},
    onEnterPiP: () -> Unit = {},
    repeatMode: Int,
    onCycleRepeatMode: () -> Unit,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    sleepTimerMinutes: Int,
    onOpenSleepTimerDialog: () -> Unit,
    isBackgroundAudio: Boolean,
    onToggleBackgroundAudio: () -> Unit,
    onOpenVideoInfo: () -> Unit,
    loopStartMs: Long? = null,
    loopEndMs: Long? = null,
    onToggleLoop: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    primaryAccent: Color,
    onOpenSettings: (() -> Unit)? = null,
    isAmbientMode: Boolean = true,
    onToggleAmbientMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasSubtitles = remember(exoPlayer.currentTracks) {
        exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            scrollState.animateScrollTo(0)
        } else {
            delay(50)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Playback Speed (Press & hold resets to 1.0x)
        QuickActionButton(
            icon = Icons.Rounded.Speed,
            label = if (playbackSpeed != 1f) String.format(Locale.getDefault(), "%.2fx", playbackSpeed) else "Speed",
            isActive = playbackSpeed != 1f,
            activeColor = primaryAccent,
            onLongClick = onResetSpeedToOne,
            onClick = onOpenSpeedDialog
        )

        // 2. Audio Track / Language (Opens Mini Popup Dialog on Audio Tab)
        QuickActionButton(
            icon = Icons.Rounded.Audiotrack,
            label = "Audio",
            isActive = false,
            activeColor = primaryAccent,
            onClick = onOpenAudioDialog
        )

        // 3. Subtitles (Opens Mini Popup Dialog with 4 Styles Preview + Audio/Sub tracks)
        QuickActionButton(
            icon = Icons.Rounded.Subtitles,
            label = "Subtitles",
            isActive = hasSubtitles,
            activeColor = primaryAccent,
            onClick = onOpenSubtitleDialog
        )

        // 4. Video Details
        QuickActionButton(
            icon = Icons.Rounded.Info,
            label = "Details",
            isActive = false,
            activeColor = primaryAccent,
            onClick = onOpenVideoInfo
        )

        // 5. Expand / Collapse '>' Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(
                    elevation = if (isExpanded) 5.dp else 2.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = if (isExpanded) primaryAccent.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
                    spotColor = if (isExpanded) primaryAccent.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isExpanded) Brush.horizontalGradient(
                        listOf(primaryAccent, primaryAccent.copy(alpha = 0.82f))
                    )
                    else Brush.verticalGradient(
                        listOf(Color(0xFF292C3A), Color(0xFF1E202B))
                    )
                )
                .border(
                    width = if (isExpanded) 1.dp else 0.8.dp,
                    color = if (isExpanded) primaryAccent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onToggleExpanded() }
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // Rest of the buttons (shown when isExpanded == true)
        if (isExpanded) {
            // A-B Loop Button
            val isLoopActive = loopStartMs != null
            val loopLabel = when {
                loopStartMs != null && loopEndMs != null -> "A-B [${formatTime(loopStartMs)}-${formatTime(loopEndMs)}]"
                loopStartMs != null -> "Set B [${formatTime(loopStartMs)}]"
                else -> "A-B Loop"
            }
            QuickActionButton(
                icon = Icons.Rounded.AllInclusive,
                label = loopLabel,
                isActive = isLoopActive,
                activeColor = primaryAccent,
                onClick = onToggleLoop
            )

            // Sleep Timer (Opens Mini Popup Dialog)
            QuickActionButton(
                icon = Icons.Rounded.Timer,
                label = if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Timer",
                isActive = sleepTimerMinutes > 0,
                activeColor = primaryAccent,
                onClick = onOpenSleepTimerDialog
            )

            // Repeat / Loop Mode
            QuickActionButton(
                icon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                    Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                    else -> Icons.Rounded.Repeat
                },
                label = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Loop 1"
                    Player.REPEAT_MODE_ALL -> "Loop All"
                    else -> "Loop"
                },
                isActive = repeatMode != Player.REPEAT_MODE_OFF,
                activeColor = primaryAccent,
                onClick = onCycleRepeatMode
            )

            // Night Cinema Tint
            QuickActionButton(
                icon = Icons.Rounded.Bedtime,
                label = if (isNightMode) "Night ON" else "Night",
                isActive = isNightMode,
                activeColor = primaryAccent,
                onClick = onToggleNightMode
            )

            // Background Audio Playback
            QuickActionButton(
                icon = Icons.Rounded.Headphones,
                label = if (isBackgroundAudio) "BG ON" else "BG Play",
                isActive = isBackgroundAudio,
                activeColor = primaryAccent,
                onClick = onToggleBackgroundAudio
            )

            // Dynamic Video Ambient Mode
            QuickActionButton(
                icon = if (isAmbientMode) Icons.Rounded.BlurOn else Icons.Rounded.BlurOff,
                label = if (isAmbientMode) "Ambient ON" else "Ambient",
                isActive = isAmbientMode,
                activeColor = primaryAccent,
                onClick = onToggleAmbientMode
            )

            // Settings
            if (onOpenSettings != null) {
                QuickActionButton(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    isActive = false,
                    activeColor = primaryAccent,
                    onClick = onOpenSettings
                )
            }

            // Trailing collapse button (quick collapse after scrolling to end)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF292C3A), Color(0xFF1E202B))
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Collapse",
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Comprehensive ISO-639-1 & ISO-639-2 Language Dictionary ────────────────
private val ISO_639_LANG_MAP = mapOf(
    // Indian Languages
    "hin" to "Hindi", "hi" to "Hindi",
    "tam" to "Tamil", "ta" to "Tamil",
    "tel" to "Telugu", "te" to "Telugu",
    "kan" to "Kannada", "kn" to "Kannada",
    "mal" to "Malayalam", "ml" to "Malayalam",
    "ben" to "Bengali", "bn" to "Bengali",
    "guj" to "Gujarati", "gu" to "Gujarati",
    "mar" to "Marathi", "mr" to "Marathi",
    "pan" to "Punjabi", "pa" to "Punjabi",
    "urd" to "Urdu", "ur" to "Urdu",
    "ori" to "Odia", "or" to "Odia",
    "asm" to "Assamese", "as" to "Assamese",
    "nep" to "Nepali", "ne" to "Nepali",
    "san" to "Sanskrit", "sa" to "Sanskrit",
    "sin" to "Sinhala", "si" to "Sinhala",
    "bho" to "Bhojpuri", "mai" to "Maithili",

    // Major Global Languages
    "eng" to "English", "en" to "English",
    "spa" to "Spanish", "es" to "Spanish",
    "fre" to "French", "fra" to "French", "fr" to "French",
    "ger" to "German", "deu" to "German", "de" to "German",
    "ita" to "Italian", "it" to "Italian",
    "por" to "Portuguese", "pt" to "Portuguese",
    "rus" to "Russian", "ru" to "Russian",
    "jpn" to "Japanese", "ja" to "Japanese",
    "chi" to "Chinese", "zho" to "Chinese", "zh" to "Chinese",
    "kor" to "Korean", "ko" to "Korean",
    "ara" to "Arabic", "ar" to "Arabic",
    "tur" to "Turkish", "tr" to "Turkish",
    "vie" to "Vietnamese", "vi" to "Vietnamese",
    "tha" to "Thai", "th" to "Thai",
    "ind" to "Indonesian", "id" to "Indonesian",
    "may" to "Malay", "msa" to "Malay", "ms" to "Malay",
    "per" to "Persian", "fas" to "Persian", "fa" to "Persian",
    "nld" to "Dutch", "dut" to "Dutch", "nl" to "Dutch",
    "pol" to "Polish", "pl" to "Polish",
    "ukr" to "Ukrainian", "uk" to "Ukrainian",
    "ces" to "Czech", "cze" to "Czech", "cs" to "Czech",
    "slk" to "Slovak", "slo" to "Slovak", "sk" to "Slovak",
    "hun" to "Hungarian", "hu" to "Hungarian",
    "ron" to "Romanian", "rum" to "Romanian", "ro" to "Romanian",
    "bul" to "Bulgarian", "bg" to "Bulgarian",
    "ell" to "Greek", "gre" to "Greek", "el" to "Greek",
    "heb" to "Hebrew", "he" to "Hebrew",
    "swe" to "Swedish", "sv" to "Swedish",
    "nor" to "Norwegian", "no" to "Norwegian", "nob" to "Norwegian Bokmål", "nno" to "Norwegian Nynorsk",
    "dan" to "Danish", "da" to "Danish",
    "fin" to "Finnish", "fi" to "Finnish",
    "fil" to "Filipino", "tgl" to "Tagalog", "tl" to "Tagalog",
    "kat" to "Georgian", "geo" to "Georgian", "ka" to "Georgian",
    "hye" to "Armenian", "arm" to "Armenian", "hy" to "Armenian",
    "aze" to "Azerbaijani", "az" to "Azerbaijani",
    "kaz" to "Kazakh", "kk" to "Kazakh",
    "uzb" to "Uzbek", "uz" to "Uzbek",
    "mon" to "Mongolian", "mn" to "Mongolian",
    "mya" to "Burmese", "bur" to "Burmese", "my" to "Burmese",
    "khm" to "Khmer", "km" to "Khmer",
    "swa" to "Swahili", "sw" to "Swahili",
    "hau" to "Hausa", "ha" to "Hausa",
    "yor" to "Yoruba", "yo" to "Yoruba",
    "ibo" to "Igbo", "ig" to "Igbo",
    "zul" to "Zulu", "zu" to "Zulu",
    "afr" to "Afrikaans", "af" to "Afrikaans",
    "lat" to "Latin", "la" to "Latin",
    "cat" to "Catalan", "ca" to "Catalan",
    "glg" to "Galician", "gl" to "Galician",
    "eus" to "Basque", "baq" to "Basque", "eu" to "Basque",
    "cym" to "Welsh", "wel" to "Welsh", "cy" to "Welsh",
    "gle" to "Irish", "ga" to "Irish",
    "gla" to "Scottish Gaelic", "gd" to "Scottish Gaelic",
    "hrv" to "Croatian", "hr" to "Croatian",
    "srp" to "Serbian", "sr" to "Serbian",
    "bos" to "Bosnian", "bs" to "Bosnian",
    "slv" to "Slovenian", "sl" to "Slovenian",
    "mkd" to "Macedonian", "mac" to "Macedonian", "mk" to "Macedonian",
    "sqi" to "Albanian", "alb" to "Albanian", "sq" to "Albanian",
    "est" to "Estonian", "et" to "Estonian",
    "lav" to "Latvian", "lv" to "Latvian",
    "lit" to "Lithuanian", "lt" to "Lithuanian"
)

private fun resolveCleanLanguageName(langCode: String?): String? {
    if (langCode.isNullOrBlank()) return null
    val clean = langCode.trim().lowercase()
    if (clean == "und" || clean == "..." || clean == "zxx" || clean == "mis" || clean == "mul" || clean == "none") {
        return null
    }

    // Direct map lookup (covers 3-letter & 2-letter codes)
    ISO_639_LANG_MAP[clean]?.let { return it }

    // Strip dialect/region e.g. "en-US", "pt_BR", "zh-CN"
    val baseCode = clean.split('-', '_').firstOrNull()?.trim()
    if (!baseCode.isNullOrBlank()) {
        ISO_639_LANG_MAP[baseCode]?.let { baseName ->
            val region = clean.substringAfter('-', "").ifBlank { clean.substringAfter('_', "") }.uppercase()
            return if (region.isNotBlank() && region.length <= 4) "$baseName ($region)" else baseName
        }
    }

    // Fallback to Java Locale
    return runCatching {
        val localeFromTag = Locale.forLanguageTag(langCode)
        val name = localeFromTag.getDisplayLanguage(Locale.ENGLISH)
        if (name.isNotBlank() && !name.equals(langCode, ignoreCase = true)) {
            val country = localeFromTag.getDisplayCountry(Locale.ENGLISH)
            if (country.isNotBlank()) "$name ($country)" else name
        } else {
            val simpleLoc = Locale(langCode)
            val sName = simpleLoc.getDisplayLanguage(Locale.ENGLISH)
            if (sName.isNotBlank() && !sName.equals(langCode, ignoreCase = true)) sName else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: langCode.replaceFirstChar { it.uppercase() }
}

private fun formatAudioCodec(sampleMimeType: String?, codecs: String?): String? {
    val mime = sampleMimeType?.lowercase() ?: ""
    val c = codecs?.lowercase() ?: ""
    return when {
        mime.contains("eac3-joc") || (mime.contains("eac3") && c.contains("joc")) -> "Dolby Atmos"
        mime.contains("eac3") -> "Dolby Digital Plus (E-AC-3)"
        mime.contains("ac3") -> "Dolby Digital (AC-3)"
        mime.contains("true-hd") || mime.contains("truehd") -> "Dolby TrueHD"
        mime.contains("dts-hd") || mime.contains("dtshd") -> "DTS-HD MA"
        mime.contains("dts") -> "DTS Audio"
        mime.contains("flac") -> "FLAC Lossless"
        mime.contains("opus") -> "Opus"
        mime.contains("vorbis") -> "Ogg Vorbis"
        mime.contains("mp4a") || mime.contains("aac") -> "AAC"
        mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
        mime.contains("raw") || mime.contains("pcm") || mime.contains("wav") -> "PCM Audio"
        mime.isNotBlank() -> mime.substringAfterLast("/").substringAfter("x-").uppercase()
        else -> null
    }
}

private fun formatAudioChannels(channels: Int): String? = when (channels) {
    1 -> "1.0 Mono"
    2 -> "2.0 Stereo"
    6 -> "5.1 Surround"
    8 -> "7.1 Surround"
    in 3..5 -> "$channels Ch"
    else -> if (channels > 0) "$channels Channels" else null
}

private fun formatSubtitleCodec(sampleMimeType: String?): String? {
    val mime = sampleMimeType?.lowercase() ?: ""
    return when {
        mime.contains("subrip") || mime.contains("x-subrip") || mime.contains("srt") -> "SubRip (.srt)"
        mime.contains("x-ssa") || mime.contains("ssa") -> "SubStation Alpha (.ssa)"
        mime.contains("x-ass") || mime.contains("ass") -> "Advanced SSA (.ass)"
        mime.contains("vtt") || mime.contains("webvtt") -> "WebVTT"
        mime.contains("pgs") || mime.contains("hdmv") -> "PGS (Blu-ray)"
        mime.contains("vobsub") || mime.contains("dvd") -> "VobSub (DVD)"
        mime.contains("ttml") || mime.contains("xml") -> "TTML"
        mime.contains("tx3g") || mime.contains("mov_text") -> "MP4 Timed Text"
        mime.isNotBlank() -> mime.substringAfterLast("/").substringAfter("x-").uppercase()
        else -> "Subtitles"
    }
}

private data class TrackDisplayMeta(
    val title: String,
    val details: String,
    val badges: List<String>
)

// ─── Track Resolution Helpers (Eliminates "..." and blank labels) ────────────
private fun resolveAudioTrackDetails(format: Format, trackIndex: Int): TrackDisplayMeta {
    val rawLabel = format.label?.trim()?.takeIf {
        it.isNotBlank() && it != "..." && it != "und" && !it.equals("unknown", ignoreCase = true)
    }
    val langDisplay = resolveCleanLanguageName(format.language)

    val title = when {
        !rawLabel.isNullOrBlank() && !langDisplay.isNullOrBlank() -> {
            if (rawLabel.contains(langDisplay, ignoreCase = true)) rawLabel
            else "$langDisplay ($rawLabel)"
        }
        !langDisplay.isNullOrBlank() -> langDisplay
        !rawLabel.isNullOrBlank() -> rawLabel
        else -> "Audio Track $trackIndex"
    }

    // Technical details
    val codec = formatAudioCodec(format.sampleMimeType, format.codecs)
    val channels = formatAudioChannels(format.channelCount)
    val sampleRate = format.sampleRate.takeIf { it > 0 }?.let { "${it / 1000} kHz" }
    val bitrate = format.bitrate.takeIf { it > 0 }?.let {
        if (it >= 1_000_000) String.format(Locale.getDefault(), "%.1f Mbps", it / 1_000_000f)
        else "${it / 1000} kbps"
    }

    val detailsList = mutableListOf<String>()
    codec?.let { detailsList.add(it) }
    channels?.let { detailsList.add(it) }
    sampleRate?.let { detailsList.add(it) }
    bitrate?.let { detailsList.add(it) }

    // Badges
    val badges = mutableListOf<String>()
    if ((format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0) {
        badges.add("DEFAULT")
    }
    if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) {
        badges.add("FORCED")
    }
    if ((format.roleFlags and C.ROLE_FLAG_DUB) != 0) {
        badges.add("DUBBED")
    }
    if ((format.roleFlags and C.ROLE_FLAG_COMMENTARY) != 0) {
        badges.add("COMMENTARY")
    }
    if ((format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0) {
        badges.add("AUDIO DESC")
    }
    if ((format.roleFlags and C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY) != 0) {
        badges.add("CLEAR VOICE")
    }

    val details = if (detailsList.isNotEmpty()) detailsList.joinToString(" • ") else "Embedded Audio"
    return TrackDisplayMeta(title = title, details = details, badges = badges)
}

private fun resolveSubtitleTrackDetails(format: Format, trackIndex: Int): TrackDisplayMeta {
    val rawLabel = format.label?.trim()?.takeIf {
        it.isNotBlank() && it != "..." && it != "und" && !it.equals("unknown", ignoreCase = true)
    }
    val langDisplay = resolveCleanLanguageName(format.language)

    val title = when {
        !rawLabel.isNullOrBlank() && !langDisplay.isNullOrBlank() -> {
            if (rawLabel.contains(langDisplay, ignoreCase = true)) rawLabel
            else "$langDisplay ($rawLabel)"
        }
        !langDisplay.isNullOrBlank() -> langDisplay
        !rawLabel.isNullOrBlank() -> rawLabel
        else -> "Subtitle Track $trackIndex"
    }

    val formatName = formatSubtitleCodec(format.sampleMimeType)

    val detailsList = mutableListOf<String>()
    formatName?.let { detailsList.add(it) }
    detailsList.add("Track #$trackIndex")

    // Badges
    val badges = mutableListOf<String>()
    if ((format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0) {
        badges.add("DEFAULT")
    }
    if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) {
        badges.add("FORCED")
    }
    val isSdh = (format.roleFlags and C.ROLE_FLAG_CAPTION) != 0 ||
            rawLabel?.contains("sdh", ignoreCase = true) == true ||
            rawLabel?.contains("cc", ignoreCase = true) == true
    if (isSdh) {
        badges.add("SDH / CC")
    }
    if ((format.roleFlags and C.ROLE_FLAG_COMMENTARY) != 0) {
        badges.add("COMMENTARY")
    }

    val details = detailsList.joinToString(" • ")
    return TrackDisplayMeta(title = title, details = details, badges = badges)
}

// ─── Audio Tracks & Languages Dialog (Direct from Audio Button) ────────────────
@Composable
private fun AudioTracksDialog(
    exoPlayer: ExoPlayer,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val appPreferences = remember { AppPreferences(context) }
    val currentTracks = exoPlayer.currentTracks
    val audioGroups = remember(currentTracks) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(350.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = c.dropdownBg),
            border = BorderStroke(1.2.dp, c.glassBorder),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Audiotrack,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Audio Tracks",
                                color = c.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select audio stream & language",
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = c.glassBorder, thickness = 0.8.dp)
                Spacer(Modifier.height(12.dp))

                if (audioGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Audiotrack,
                                contentDescription = null,
                                tint = c.textHint,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Default Audio Stream",
                                color = c.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Standard embedded audio output",
                                color = c.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var trackCounter = 1
                        audioGroups.forEach { group ->
                            for (i in 0 until group.length) {
                                val currentIdx = trackCounter++
                                val isSelected = group.isTrackSelected(i)
                                val format = group.getTrackFormat(i)

                                val meta = resolveAudioTrackDetails(format, currentIdx)

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) accentColor.copy(alpha = 0.18f)
                                                else c.cardBgElevated
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentColor.copy(alpha = 0.85f)
                                                else c.glassBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .build()
                                                format.language?.let { appPreferences.setPreferredAudioLanguage(it) }
                                                Toast.makeText(context, "Audio: ${meta.title}", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = meta.title,
                                                    color = if (isSelected) accentColor else c.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(accentColor)
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            "ACTIVE",
                                                            color = Color.White,
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                                meta.badges.forEach { badge ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(c.glassBgNested)
                                                            .border(0.8.dp, c.glassBorder, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            badge,
                                                            color = c.textSecondary,
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            if (meta.details.isNotBlank()) {
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = meta.details,
                                                    color = c.textSecondary,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Subtitles & Captions Dialog (Direct from Subtitles Button) ────────────────
@Composable
private fun SubtitlesDialog(
    exoPlayer: ExoPlayer,
    subtitleDesign: Int,
    onSubtitleDesignChange: (Int) -> Unit,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val appPreferences = remember { AppPreferences(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentTracks = exoPlayer.currentTracks
    val textGroups = remember(currentTracks) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(350.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = c.dropdownBg),
            border = BorderStroke(1.2.dp, c.glassBorder),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Subtitles",
                                color = c.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage captions & styles",
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 2 Tabs: Tracks & Styles
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = c.cardBgElevated,
                    contentColor = accentColor,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                    indicator = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) accentColor.copy(alpha = 0.25f) else Color.Transparent),
                        text = {
                            Text(
                                "Tracks (${textGroups.sumOf { it.length }})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) accentColor else c.textSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) accentColor.copy(alpha = 0.25f) else Color.Transparent),
                        text = {
                            Text(
                                "Styles (4)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) accentColor else c.textSecondary
                            )
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                when (selectedTab) {
                    0 -> {
                        // Tracks Tab
                        val areDisabled = exoPlayer.trackSelectionParameters.ignoredTextSelectionFlags == C.SELECTION_FLAG_DEFAULT ||
                                !textGroups.any { it.isSelected }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Subtitles Off Item
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (areDisabled) accentColor.copy(alpha = 0.18f)
                                            else c.cardBgElevated
                                        )
                                        .border(
                                            1.dp,
                                            if (areDisabled) accentColor.copy(alpha = 0.85f)
                                            else c.glassBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                .build()
                                            appPreferences.setSubtitlesEnabled(false)
                                            Toast.makeText(context, "Subtitles turned off", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Subtitles Off",
                                            color = if (areDisabled) accentColor else c.textPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (areDisabled) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = "Disable subtitle rendering",
                                            color = c.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (areDisabled) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Subtitle track items
                            var trackCounter = 1
                            textGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    val currentIdx = trackCounter++
                                    val isSelected = !areDisabled && group.isTrackSelected(i)
                                    val format = group.getTrackFormat(i)

                                    val meta = resolveSubtitleTrackDetails(format, currentIdx)

                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                if (isSelected) accentColor.copy(alpha = 0.18f)
                                                else c.cardBgElevated
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentColor.copy(alpha = 0.85f)
                                                else c.glassBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setIgnoredTextSelectionFlags(0)
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .build()
                                                appPreferences.setSubtitlesEnabled(true)
                                                format.language?.let { appPreferences.setPreferredSubtitleLanguage(it) }
                                                Toast.makeText(context, "Subtitles: ${meta.title}", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = meta.title,
                                                        color = if (isSelected) accentColor else c.textPrimary,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                    )
                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(accentColor)
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                "ACTIVE",
                                                                color = Color.White,
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                        }
                                                    }
                                                    meta.badges.forEach { badge ->
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(c.glassBgNested)
                                                                .border(0.8.dp, c.glassBorder, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                badge,
                                                                color = c.textSecondary,
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                if (meta.details.isNotBlank()) {
                                                    Spacer(Modifier.height(3.dp))
                                                    Text(
                                                        text = meta.details,
                                                        color = c.textSecondary,
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Styles Tab (4 Cinema Styles)
                        val subtitleStyles = listOf(
                            Triple(0, "Classic Cinema", "White text with solid black outline"),
                            Triple(1, "Dark Box", "Crisp white text with slate box backing"),
                            Triple(2, "Golden Film", "Cinematic gold text with drop shadow"),
                            Triple(3, "Cyber Neon", "Vibrant cyan text with glowing neon border")
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 270.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(subtitleStyles) { (id, name, desc) ->
                                val isChosen = subtitleDesign == id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSubtitleDesignChange(id) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChosen) accentColor.copy(alpha = 0.18f) else c.cardBgElevated
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChosen) accentColor else c.glassBorder
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = name,
                                                color = if (isChosen) accentColor else c.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isChosen) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(accentColor)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("ACTIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            color = c.textSecondary,
                                            fontSize = 10.5.sp
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        // Visual Preview Box (Cinema contrast preview)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0B0C10))
                                                .padding(vertical = 6.dp, horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (id) {
                                                0 -> Text(
                                                    text = "Aa Subtitle Preview",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                1 -> Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF282A36))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Aa Subtitle Preview",
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                2 -> Text(
                                                    text = "Aa Subtitle Preview",
                                                    color = Color(0xFFFFD700),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                3 -> Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF101219))
                                                        .border(0.8.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Aa Subtitle Preview",
                                                        color = Color(0xFF00E5FF),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Video Info Bottom Sheet ──────────────────────────────────────────────────
// ─── Mini Playback Speed Dialog (User-Defined - / + Buttons & Presets) ────────
@Composable
private fun MiniSpeedDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.dropdownBg),
            border = BorderStroke(1.dp, c.glassBorder),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Text("Playback Speed", color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(18.dp))

                // User Defined Speed with [-] and [+] Increment/Decrement Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decrement Button (-)
                    IconButton(
                        onClick = {
                            val newSpd = ((currentSpeed - 0.05f) * 100).roundToInt() / 100f
                            onSpeedChange(newSpd.coerceIn(0.25f, 4.0f))
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(c.cardBgElevated)
                            .border(1.dp, c.glassBorder, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease speed", tint = c.textPrimary, modifier = Modifier.size(24.dp))
                    }

                    // Speed Display (e.g. 1.25x)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.2fx", currentSpeed),
                            color = accentColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (abs(currentSpeed - 1.0f) < 0.01f) "Normal Speed" else "Custom Speed",
                            color = c.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Increment Button (+)
                    IconButton(
                        onClick = {
                            val newSpd = ((currentSpeed + 0.05f) * 100).roundToInt() / 100f
                            onSpeedChange(newSpd.coerceIn(0.25f, 4.0f))
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(c.cardBgElevated)
                            .border(1.dp, c.glassBorder, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase speed", tint = c.textPrimary, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Reset to 1.0x Chip Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (abs(currentSpeed - 1.0f) < 0.01f) accentColor.copy(alpha = 0.25f) else c.cardBgElevated)
                        .border(1.dp, if (abs(currentSpeed - 1.0f) < 0.01f) accentColor else c.glassBorder, RoundedCornerShape(12.dp))
                        .clickable { onSpeedChange(1.0f) }
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Reset to 1.0x",
                        color = if (abs(currentSpeed - 1.0f) < 0.01f) accentColor else c.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(18.dp))

                HorizontalDivider(color = c.glassBorder)

                Spacer(Modifier.height(12.dp))

                // Presets Title & Row
                Text(
                    text = "Quick Presets",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.forEach { preset ->
                        val isSelected = abs(currentSpeed - preset) < 0.01f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentColor else c.cardBgElevated)
                                .border(1.dp, if (isSelected) accentColor else c.glassBorder, RoundedCornerShape(8.dp))
                                .clickable { onSpeedChange(preset) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset}x",
                                color = if (isSelected) Color.White else c.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Mini Sleep Timer Dialog ──────────────────────────────────────────────────
@Composable
private fun MiniSleepTimerDialog(
    currentMinutes: Int,
    onSelectTimer: (Int) -> Unit,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val options = listOf(
        0 to "Turn Off Timer",
        15 to "15 Minutes",
        30 to "30 Minutes",
        45 to "45 Minutes",
        60 to "60 Minutes",
        90 to "90 Minutes"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.dropdownBg),
            border = BorderStroke(1.dp, c.glassBorder),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Text("Sleep Timer", color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                if (currentMinutes > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                            Text(
                                text = "Active: $currentMinutes min remaining",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (mins, label) ->
                        val isSelected = currentMinutes == mins
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.22f) else c.cardBgElevated)
                                .border(1.dp, if (isSelected) accentColor else c.glassBorder, RoundedCornerShape(10.dp))
                                .clickable { onSelectTimer(mins) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else c.textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Mini Video Details Dialog (Comprehensive Metadata Popup) ─────────────────
@Composable
private fun MiniVideoDetailsDialog(
    exoPlayer: ExoPlayer,
    title: String,
    currentUrl: String,
    durationMs: Long,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val videoFormat = exoPlayer.videoFormat
    val audioFormat = exoPlayer.audioFormat

    var fileSize by remember(currentUrl) { mutableLongStateOf(0L) }
    var filePath by remember(currentUrl) { mutableStateOf(currentUrl) }

    LaunchedEffect(currentUrl) {
        withContext(Dispatchers.IO) {
            try {
                if (currentUrl.startsWith("content://")) {
                    val uri = android.net.Uri.parse(currentUrl)
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.DATA
                        ),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                            if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                            val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                            if (dataIdx >= 0) cursor.getString(dataIdx)?.let { filePath = it }
                        }
                    }
                } else if (currentUrl.startsWith("file://") || currentUrl.startsWith("/")) {
                    val file = java.io.File(currentUrl.removePrefix("file://"))
                    if (file.exists()) {
                        fileSize = file.length()
                        filePath = file.absolutePath
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(340.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.dropdownBg),
            border = BorderStroke(1.dp, c.glassBorder),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header with Title, Copy All Details Button & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Text("Video Details", color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val resText = videoFormat?.let { "${it.width} × ${it.height}" } ?: "Hardware Adapted"
                                val fpsText = videoFormat?.frameRate?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" } ?: "Auto"
                                val vCodec = videoFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "Hardware Decoded"
                                val aCodec = audioFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "AAC / Auto"
                                val channels = audioFormat?.channelCount?.let { if (it == 2) "Stereo (2ch)" else "$it Channels" } ?: "Stereo"
                                val sampleRate = audioFormat?.sampleRate?.takeIf { it > 0 }?.let { "${it} Hz" } ?: "48,000 Hz"
                                val allDetails = buildString {
                                    appendLine("Title: $title")
                                    appendLine("Duration: ${formatTime(durationMs)}")
                                    if (fileSize > 0) appendLine("File Size: ${formatSize(fileSize)}")
                                    appendLine("Resolution: $resText")
                                    appendLine("Frame Rate: $fpsText")
                                    appendLine("Video Codec: $vCodec")
                                    appendLine("Audio Codec: $aCodec")
                                    appendLine("Audio Channels: $channels")
                                    appendLine("Sample Rate: $sampleRate")
                                    if (fileSize > 0 && durationMs > 0) {
                                        val bitrateKbps = (fileSize * 8) / durationMs
                                        appendLine("Overall Bitrate: ${bitrateKbps} kbps")
                                    }
                                    appendLine("Storage Path: $filePath")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Video Details", allDetails))
                                Toast.makeText(context, "Video details copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy all details", tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Scrollable details list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailItemRow("Title", title, accentColor)
                    DetailItemRow("Duration", formatTime(durationMs), accentColor)
                    if (fileSize > 0) {
                        DetailItemRow("File Size", formatSize(fileSize), accentColor)
                    }
                    val resText = videoFormat?.let { "${it.width} × ${it.height}" } ?: "Hardware Adapted"
                    DetailItemRow("Resolution", resText, accentColor)
                    val fpsText = videoFormat?.frameRate?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" } ?: "Auto"
                    DetailItemRow("Frame Rate", fpsText, accentColor)
                    val vCodec = videoFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "Hardware Decoded"
                    DetailItemRow("Video Codec", vCodec, accentColor)
                    val aCodec = audioFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "AAC / Auto"
                    DetailItemRow("Audio Codec", aCodec, accentColor)
                    val channels = audioFormat?.channelCount?.let { if (it == 2) "Stereo (2ch)" else "$it Channels" } ?: "Stereo"
                    DetailItemRow("Audio Channels", channels, accentColor)
                    val sampleRate = audioFormat?.sampleRate?.takeIf { it > 0 }?.let { "${it} Hz" } ?: "48,000 Hz"
                    DetailItemRow("Sample Rate", sampleRate, accentColor)
                    if (fileSize > 0 && durationMs > 0) {
                        val bitrateKbps = (fileSize * 8) / (durationMs)
                        DetailItemRow("Overall Bitrate", "${bitrateKbps} kbps", accentColor)
                    }
                    DetailItemRow(
                        label = "Storage Path",
                        value = filePath,
                        accentColor = accentColor,
                        isPath = true,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("File Location", filePath))
                            Toast.makeText(context, "Location copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String,
    accentColor: Color,
    isPath: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.cardBgElevated)
            .border(0.6.dp, c.glassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = c.textPrimary,
                fontSize = if (isPath) 11.sp else 13.sp,
                fontWeight = if (isPath) FontWeight.Normal else FontWeight.SemiBold,
                fontFamily = if (isPath) FontFamily.Monospace else FontFamily.Default,
                maxLines = if (isPath) 3 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Portrait App Explorer (YouTube Style: Browse App While Playing) ─────────
@Composable
private fun PortraitAppExplorer(
    currentUrl: String,
    videoTitle: String,
    duration: Long,
    playlistVideos: List<VideoItem>,
    allFolders: List<VideoFolder>,
    allVideos: List<VideoItem>,
    repeatMode: Int,
    onRepeatToggle: () -> Unit,
    playbackSpeed: Float,
    onSpeedClick: () -> Unit,
    onResetSpeedToOne: () -> Unit = {},
    isNightMode: Boolean,
    onNightModeToggle: () -> Unit,
    resizeMode: Int,
    onAspectToggle: () -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onPiPClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    isAmbientMode: Boolean = true,
    onAmbientModeToggle: () -> Unit = {},
    primaryAccent: Color
) {
    val c = LocalAppColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var explorerSortOrder by remember { mutableStateOf(SortOrder.DATE) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.baseBackground)
    ) {
        // Video Header Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = videoTitle,
                color = c.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatTime(duration),
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text("•", color = c.textHint, fontSize = 12.sp)
                Text(
                    text = "${playlistVideos.size} in playlist",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Quick Action Row (Horizontal Scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatLabel = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "1"
                Player.REPEAT_MODE_ALL -> "All"
                else -> "Off"
            }
            ExplorerActionPill(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                label = "Loop: $repeatLabel",
                isActive = repeatMode != Player.REPEAT_MODE_OFF,
                accentColor = primaryAccent,
                onClick = onRepeatToggle
            )

            ExplorerActionPill(
                icon = Icons.Rounded.Speed,
                label = if (playbackSpeed == playbackSpeed.toInt().toFloat()) "${playbackSpeed.toInt()}x" else "${playbackSpeed}x",
                isActive = playbackSpeed != 1.0f,
                accentColor = primaryAccent,
                onLongClick = onResetSpeedToOne,
                onClick = onSpeedClick
            )

            ExplorerActionPill(
                icon = Icons.Rounded.Nightlight,
                label = "Night",
                isActive = isNightMode,
                accentColor = primaryAccent,
                onClick = onNightModeToggle
            )

            ExplorerActionPill(
                icon = Icons.Rounded.FitScreen,
                label = "Aspect",
                isActive = false,
                accentColor = primaryAccent,
                onClick = onAspectToggle
            )

            ExplorerActionPill(
                icon = Icons.Rounded.PictureInPicture,
                label = "PiP",
                isActive = false,
                accentColor = primaryAccent,
                onClick = onPiPClick
            )

            ExplorerActionPill(
                icon = if (isAmbientMode) Icons.Rounded.BlurOn else Icons.Rounded.BlurOff,
                label = if (isAmbientMode) "Ambient ON" else "Ambient",
                isActive = isAmbientMode,
                accentColor = primaryAccent,
                onClick = onAmbientModeToggle
            )

        }

        HorizontalDivider(color = c.glassBorder, thickness = 0.5.dp)

        // 3 Exploration Tabs: Queue, All Videos, Folders
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = c.dropdownBg,
            contentColor = primaryAccent,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 0) primaryAccent else c.textSecondary
                        )
                        Text(
                            "Queue (${playlistVideos.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) primaryAccent else c.textSecondary
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 1) primaryAccent else c.textSecondary
                        )
                        Text(
                            "All Videos (${allVideos.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) primaryAccent else c.textSecondary
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                    selectedFolder = null
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 2) primaryAccent else c.textSecondary
                        )
                        Text(
                            "Folders (${allFolders.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 2) primaryAccent else c.textSecondary
                        )
                    }
                }
            )
        }

        // Sort Sub-header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (selectedTab) {
                    0 -> "${playlistVideos.size} in Queue"
                    1 -> "${allVideos.size} Total Videos"
                    2 -> if (selectedFolder != null) "${selectedFolder!!.videos.size} in ${selectedFolder!!.name}" else "${allFolders.size} Folders"
                    else -> ""
                },
                color = c.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Box {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.glassBgNested,
                    border = BorderStroke(0.8.dp, c.glassBorder),
                    modifier = Modifier.clickable { showSortMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "Sort",
                            tint = primaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when (explorerSortOrder) {
                                SortOrder.NAME -> "Name (A-Z)"
                                SortOrder.NAME_DESC -> "Name (Z-A)"
                                SortOrder.DATE -> "Date (Newest)"
                                SortOrder.DATE_ASC -> "Date (Oldest)"
                                SortOrder.SIZE -> "Size (Largest)"
                                SortOrder.SIZE_ASC -> "Size (Smallest)"
                                SortOrder.DURATION -> "Duration (Long)"
                                SortOrder.DURATION_ASC -> "Duration (Short)"
                            },
                            color = c.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier
                        .background(c.dropdownBg)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        "Sort by",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(color = c.glassBorder)
                    listOf(
                        SortOrder.DATE to ("Date Added (Newest)" to Icons.Rounded.Schedule),
                        SortOrder.DATE_ASC to ("Date Added (Oldest)" to Icons.Rounded.Schedule),
                        SortOrder.NAME to ("Name (A to Z)" to Icons.Rounded.SortByAlpha),
                        SortOrder.NAME_DESC to ("Name (Z to A)" to Icons.Rounded.SortByAlpha),
                        SortOrder.SIZE to ("Size (Largest)" to Icons.Rounded.Storage),
                        SortOrder.SIZE_ASC to ("Size (Smallest)" to Icons.Rounded.Storage),
                        SortOrder.DURATION to ("Duration (Longest)" to Icons.Rounded.Schedule),
                        SortOrder.DURATION_ASC to ("Duration (Shortest)" to Icons.Rounded.Schedule)
                    ).forEach { (order, pair) ->
                        val (label, icon) = pair
                        val isSelected = explorerSortOrder == order
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) primaryAccent else c.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            text = {
                                Text(
                                    label,
                                    color = if (isSelected) primaryAccent else c.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                explorerSortOrder = order
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> {
                    // Queue / Playlist
                    val sortedPlaylist = remember(playlistVideos, explorerSortOrder) {
                        playlistVideos.sortVideosWithOrder(explorerSortOrder)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(sortedPlaylist, key = { it.uri.toString() }) { video ->
                            ExplorerVideoRow(
                                video = video,
                                isCurrent = video.uri.toString() == currentUrl,
                                primaryAccent = primaryAccent,
                                onClick = { onSelectVideo(video) }
                            )
                        }
                    }
                }
                1 -> {
                    // All Videos (Searchable & Sortable)
                    val filteredVideos = remember(searchQuery, allVideos, explorerSortOrder) {
                        val base = if (searchQuery.isBlank()) allVideos
                        else allVideos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        base.sortVideosWithOrder(explorerSortOrder)
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search videos...", color = c.textHint, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = primaryAccent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = c.cardBgElevated,
                                unfocusedContainerColor = c.cardBgElevated,
                                focusedTextColor = c.textPrimary,
                                unfocusedTextColor = c.textPrimary,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = c.glassBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .height(50.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(filteredVideos, key = { it.uri.toString() }) { video ->
                                ExplorerVideoRow(
                                    video = video,
                                    isCurrent = video.uri.toString() == currentUrl,
                                    primaryAccent = primaryAccent,
                                    onClick = { onSelectVideo(video) }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Folders
                    val currentDrillFolder = selectedFolder
                    if (currentDrillFolder != null) {
                        val sortedDrillVideos = remember(currentDrillFolder.videos, explorerSortOrder) {
                            currentDrillFolder.videos.sortVideosWithOrder(explorerSortOrder)
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolder = null }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = currentDrillFolder.name,
                                    color = c.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "(${currentDrillFolder.videos.size})",
                                    color = c.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            HorizontalDivider(color = c.glassBorder, thickness = 0.5.dp)

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(sortedDrillVideos, key = { it.uri.toString() }) { video ->
                                    ExplorerVideoRow(
                                        video = video,
                                        isCurrent = video.uri.toString() == currentUrl,
                                        primaryAccent = primaryAccent,
                                        onClick = { onSelectVideo(video) }
                                    )
                                }
                            }
                        }
                    } else {
                        val sortedFolders = remember(allFolders, explorerSortOrder) {
                            allFolders.sortFoldersWithOrder(explorerSortOrder)
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(sortedFolders, key = { it.id }) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedFolder = folder }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(primaryAccent.copy(alpha = 0.15f))
                                            .border(1.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = primaryAccent,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            color = c.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${folder.videos.size} videos",
                                            color = c.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = c.textHint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                HorizontalDivider(color = c.glassBorder, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val contentColor = if (isActive) Color.White else c.textPrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) accentColor else c.cardBgElevated)
            .border(
                1.dp,
                if (isActive) accentColor else c.glassBorder,
                RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ExplorerVideoRow(
    video: VideoItem,
    isCurrent: Boolean,
    primaryAccent: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) primaryAccent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(108.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.cardBgElevated)
                .border(
                    if (isCurrent) 1.5.dp else 0.5.dp,
                    if (isCurrent) primaryAccent else c.glassBorder,
                    RoundedCornerShape(8.dp)
                )
        ) {
            AsyncImage(
                model = remember(video.uri, video.duration) {
                    buildVideoThumbnailRequest(context, video.uri, video.duration)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = formatTime(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryAccent)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "PLAYING",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title.substringBeforeLast("."),
                color = if (isCurrent) primaryAccent else c.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatSize(video.size),
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "•",
                    color = c.textHint,
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(video.duration),
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    HorizontalDivider(color = c.glassBorder, thickness = 0.5.dp)
}

// ─── Floating Music Mini Bar in Video Section (100% Solid Opaque Background) ──
@Composable
private fun VideoMusicMiniBar(
    title: String,
    artist: String,
    artUri: android.net.Uri?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.85f), spotColor = Color.Black)
            .clip(RoundedCornerShape(18.dp))
            .background(c.dropdownBg)
            .border(
                1.dp,
                c.glassBorder,
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Album art or fallback
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cardBgElevated)
                    .border(0.8.dp, c.glassBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (artUri != null) {
                    AsyncImage(
                        model = artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = c.accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Title & Artist
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 180.dp)
            ) {
                Text(
                    text = title.ifBlank { "Music Playing" },
                    color = c.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist.ifBlank { "Background Audio" },
                    color = c.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Prev Button
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = c.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play / Pause Button
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.accentBlue)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Next Button
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = c.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Dismiss X Button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = c.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Resume Playback Floating Prompt ─────────────────────────────────────────
@Composable
private fun ResumePlaybackPrompt(
    resumePosition: Long,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
    primaryAccent: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(0.7f), spotColor = primaryAccent.copy(0.5f))
            .clip(RoundedCornerShape(16.dp))
            .background(c.dropdownBg)
            .border(1.2.dp, primaryAccent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Resumed playback",
                    color = c.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "At ${formatTime(resumePosition)}",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = onStartOver,
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Start Over", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = c.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─── Resolution Badge Intro Floating Card ────────────────────────────────────
@Composable
private fun ResolutionBadgeIntroCard(
    badge: String,
    width: Int,
    height: Int,
    primaryAccent: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(12.dp))
            .background(c.dropdownBg.copy(alpha = 0.92f))
            .border(1.dp, primaryAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(primaryAccent.copy(alpha = 0.25f))
                    .border(0.6.dp, primaryAccent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badge,
                    color = primaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "${width}×${height}",
                color = c.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}


