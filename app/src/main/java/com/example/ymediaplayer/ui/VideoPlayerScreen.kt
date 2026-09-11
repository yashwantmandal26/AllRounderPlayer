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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.example.ymediaplayer.data.VideoFolder
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.data.VideoRepository
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.LocalThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

private val QuickActionBg = Color(0x33FFFFFF)       // Circular quick action button background
private val QuickActionBorder = Color(0x44FFFFFF)   // Circular quick action border

private enum class PlayerGestureMode {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
    SPEED_HOLD,
    ORIENTATION_SWIPE,
    IGNORED_DRAG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val componentActivity = context as? androidx.activity.ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val appPreferences = remember { AppPreferences(context) }
    val repository = remember { VideoRepository(context) }
    val themeController = LocalThemeController.current

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
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            }
    }

    val allVideos = remember(allFolders) { allFolders.flatMap { it.videos } }

    val playVideoItem: (VideoItem) -> Unit = { item ->
        currentUrl = item.uri.toString()
        videoTitle = item.title.substringBeforeLast(".")
        exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
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
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isLocked by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }
    var isBackgroundAudio by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Ensure screen capture and flags are always cleared (Privacy Shield removed)
    DisposableEffect(Unit) {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
    var speedHoldDisplaySpeed by remember { mutableFloatStateOf(1.5f) }
    var preHoldSpeed by remember { mutableFloatStateOf(1.0f) }

    // ─── Seek Thumbnail Preview State ─────────────────────────────────────────
    var seekThumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var seekThumbnailPositionMs by remember { mutableLongStateOf(0L) }
    var isThumbnailLoading by remember { mutableStateOf(false) }
    val frameCache = remember(currentUrl) { android.util.LruCache<Long, android.graphics.Bitmap>(80) }
    val thumbnailRetriever = remember(currentUrl) {
        android.media.MediaMetadataRetriever().apply {
            try {
                if (currentUrl.startsWith("content://")) {
                    setDataSource(context, android.net.Uri.parse(currentUrl))
                } else {
                    setDataSource(currentUrl)
                }
            } catch (_: Exception) {}
        }
    }
    DisposableEffect(currentUrl) {
        onDispose {
            try { thumbnailRetriever.release() } catch (_: Exception) {}
        }
    }
    var audioSubInitialTab by remember { mutableIntStateOf(0) }

    // ─── Fat Vertical Gesture Bars State ──────────────────────────────────────
    // Brightness on Left (0.01f..1f)
    var brightnessPct by remember {
        val lp = activity?.window?.attributes
        val currentVal = lp?.screenBrightness?.takeIf { it in 0.005f..1f }
        mutableFloatStateOf(if (currentVal != null) screenBrightnessToSlider(currentVal) else getSystemBrightness(context))
    }
    var showBrightnessBar by remember { mutableStateOf(false) }
    var brightnessTouchTrigger by remember { mutableLongStateOf(0L) }
    var hasUserAdjustedBrightness by remember { mutableStateOf(false) }

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

    // Center Seek HUD
    var centerSeekText by remember { mutableStateOf<String?>(null) }
    var centerSeekIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Sheets & Dialogs
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAudioSubSheet by remember { mutableStateOf(false) }
    var showThemePickerSheet by remember { mutableStateOf(false) }
    var showVideoInfoSheet by remember { mutableStateOf(false) }
    var showQuickControlsBar by remember { mutableStateOf(false) }
    var subtitleDesign by remember { mutableIntStateOf(appPreferences.getSubtitleDesign()) }

    // Sleep Timer
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }

    // Picture in Picture
    var isInPiP by remember { mutableStateOf(false) }

    // Repeat Mode (0: Off, 1: One, 2: All)
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    // ─── Back Press Handling ──────────────────────────────────────────────────
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showQuickControlsBar -> showQuickControlsBar = false
            showPlaylistSheet -> showPlaylistSheet = false
            showMoreMenuSheet -> showMoreMenuSheet = false
            showSpeedSheet -> showSpeedSheet = false
            showSleepTimerSheet -> showSleepTimerSheet = false
            showAudioSubSheet -> showAudioSubSheet = false
            showThemePickerSheet -> showThemePickerSheet = false
            showVideoInfoSheet -> showVideoInfoSheet = false
            isLocked -> isLocked = false
            else -> onBack()
        }
    }

    // ─── Helper Functions ─────────────────────────────────────────────────────
    fun safeSeek(targetMs: Long) {
        val target = targetMs.coerceIn(0L, duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        currentPosition = target
    }

    fun seekBy(deltaMs: Long) {
        safeSeek(exoPlayer.currentPosition + deltaMs)
    }

    val playNextVideo: () -> Unit = {
        if (playlistVideos.isNotEmpty() && currentVideoIndex < playlistVideos.size - 1) {
            val nextIndex = currentVideoIndex + 1
            val nextVideo = playlistVideos[nextIndex]
            currentVideoIndex = nextIndex
            currentUrl = nextVideo.uri.toString()
            videoTitle = nextVideo.title.substringBeforeLast(".")
            exoPlayer.setMediaItem(MediaItem.fromUri(nextVideo.uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    val playPreviousVideo: () -> Unit = {
        if (playlistVideos.isNotEmpty() && currentVideoIndex > 0) {
            val prevIndex = currentVideoIndex - 1
            val prevVideo = playlistVideos[prevIndex]
            currentVideoIndex = prevIndex
            currentUrl = prevVideo.uri.toString()
            videoTitle = prevVideo.title.substringBeforeLast(".")
            exoPlayer.setMediaItem(MediaItem.fromUri(prevVideo.uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    val toggleMute: () -> Unit = {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0f else 1f
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
        val label = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom / Crop"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
            else -> "Original Aspect"
        }
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    // ─── Video Ratio & Orientation Detection ──────────────────────────────────
    // Extract video dimensions immediately via retriever & ExoPlayer listener
    val initialDimensions = remember(currentUrl) {
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
            Pair(effectiveW, effectiveH)
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }
    var videoWidth by remember(currentUrl) { mutableIntStateOf(initialDimensions.first) }
    var videoHeight by remember(currentUrl) { mutableIntStateOf(initialDimensions.second) }
    val isVerticalVideo = remember(videoWidth, videoHeight) {
        videoHeight > 0 && videoWidth > 0 && videoHeight > videoWidth
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
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                if (System.currentTimeMillis() < manualOrientationOverrideTime) return

                // Check if device auto-rotate is enabled from phone system settings
                val isAutoRotateEnabled = try {
                    android.provider.Settings.System.getInt(
                        context.contentResolver,
                        android.provider.Settings.System.ACCELEROMETER_ROTATION,
                        0
                    ) == 1
                } catch (_: Exception) { false }

                if (!isAutoRotateEnabled) return

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
                // Horizontal video (e.g. 16:9) -> play in landscape!
                currentOrientationSetting = 1
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    // ─── PiP Callback ─────────────────────────────────────────────────────────
    DisposableEffect(componentActivity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPiP = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    // ─── Progress Loop ────────────────────────────────────────────────────────
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSeek) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            isPlaying = exoPlayer.isPlaying
            delay(200.milliseconds)
        }
    }

    // ─── Auto-hide Controls (2.5s on play, stay visible on pause) ─────────────
    LaunchedEffect(showControls, isPlaying, isDraggingSeek, isLocked) {
        if (!isPlaying && !isLocked) {
            showControls = true
        } else if (showControls && isPlaying && !isDraggingSeek && !isLocked) {
            delay(2500.milliseconds)
            showControls = false
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
                        safeSeek((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                    }
                    ACTION_VIDEO_FORWARD -> {
                        safeSeek((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L)))
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

    // Live update notification progress
    LaunchedEffect(currentPosition, isPlaying, duration, videoTitle) {
        if (duration <= 0L && currentPosition <= 0L) return@LaunchedEffect

        val progressPercent = if (duration > 0L) {
            ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else 0

        val contentIntent = PendingIntent.getActivity(
            context,
            2001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_VIDEO_URL, currentUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = PendingIntent.getBroadcast(
            context, 101,
            Intent(ACTION_VIDEO_REWIND).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            context, 102,
            Intent(ACTION_VIDEO_PLAY_PAUSE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val forwardIntent = PendingIntent.getBroadcast(
            context, 103,
            Intent(ACTION_VIDEO_FORWARD).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = videoTitle.ifEmpty { "Video Playing" }
        val timeText = "${formatTime(currentPosition)} / ${formatTime(duration)}"

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

    // ─── Lifecycle Handling ───────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!isBackgroundAudio && !isInPiP) {
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
                appPreferences.saveVideoProgress(currentUrl, pos)
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

    val playerView = remember(context) {
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            applySubtitleDesign(this, subtitleDesign)
        }
    }

    LaunchedEffect(subtitleDesign, playerView) {
        applySubtitleDesign(playerView, subtitleDesign)
        appPreferences.saveSubtitleDesign(subtitleDesign)
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
            val down = awaitFirstDown(requireUnconsumed = true)
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

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    break
                }

                val currentX = change.position.x
                val currentY = change.position.y
                val diffX = currentX - startX
                val diffY = currentY - startY
                val elapsed = System.currentTimeMillis() - downTime

                // Step 1: Detect mode if still NONE
                if (gestureMode == PlayerGestureMode.NONE) {
                    if (abs(diffX) > 20f || abs(diffY) > 20f) {
                        hasMoved = true
                        if (!isLandscape && !isVerticalVideo) {
                            // ─── PORTRAIT / SMALL WINDOWED MODE ───
                            // No brightness or volume controls in small windowed mode
                            if (diffY < -30f && abs(diffY) > abs(diffX)) {
                                // Gesturing swipe up on the screen makes it full screen
                                manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
                                currentOrientationSetting = 1
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                                activeGestureMode = gestureMode
                                change.consume()
                                break
                            } else if (abs(diffX) > 22f && abs(diffX) > abs(diffY)) {
                                // Horizontal swipe on screen disabled for seek; progress bar is used
                                gestureMode = PlayerGestureMode.IGNORED_DRAG
                                change.consume()
                            } else if (diffY > 30f && abs(diffY) > abs(diffX)) {
                                // Downward drag in portrait is ignored
                                gestureMode = PlayerGestureMode.IGNORED_DRAG
                                change.consume()
                            }
                        } else {
                            // ─── FULL-SCREEN VIDEO PLAYER MODE ───
                            // Extreme side areas have brightness (left) and volume (right).
                            // Screen center area has swipe down to return to windowed mode.
                            val isExtremeLeft = startX <= screenWidth * 0.22f
                            val isExtremeRight = startX >= screenWidth * 0.78f
                            val isCenterArea = startX in (screenWidth * 0.22f)..(screenWidth * 0.78f)

                            if (isExtremeLeft && abs(diffY) > abs(diffX)) {
                                gestureMode = PlayerGestureMode.BRIGHTNESS
                                activeGestureMode = gestureMode
                                change.consume()
                            } else if (isExtremeRight && abs(diffY) > abs(diffX)) {
                                gestureMode = PlayerGestureMode.VOLUME
                                activeGestureMode = gestureMode
                                change.consume()
                            } else if (isCenterArea) {
                                if (diffY > 35f && abs(diffY) > abs(diffX)) {
                                    // Gesturing down at screen center area returns to windowed mode
                                    manualOrientationOverrideTime = System.currentTimeMillis() + 3000L
                                    currentOrientationSetting = 0
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                                    activeGestureMode = gestureMode
                                    change.consume()
                                    break
                                } else if (abs(diffX) > 22f && abs(diffX) > abs(diffY)) {
                                    // Horizontal swipe on screen disabled for seek; progress bar is used
                                    gestureMode = PlayerGestureMode.IGNORED_DRAG
                                    change.consume()
                                } else if (diffY < -35f && abs(diffY) > abs(diffX)) {
                                    gestureMode = PlayerGestureMode.IGNORED_DRAG
                                    change.consume()
                                }
                            }
                        }
                    } else if (elapsed >= 420L && !isDoubleTap) {
                        // Stationary hold for 420ms -> Speed Boost!
                        gestureMode = PlayerGestureMode.SPEED_HOLD
                        activeGestureMode = gestureMode
                        didActivateSpeedHold = true
                        preHoldSpeed = playbackSpeed
                        speedHoldDisplaySpeed = 1.5f
                        isSpeedHolding = true
                        exoPlayer.setPlaybackSpeed(1.5f)
                        lastHoldX = currentX
                        change.consume()
                    }
                }

                // Step 2: Handle ongoing active gesture mode
                when (gestureMode) {
                    PlayerGestureMode.BRIGHTNESS -> {
                        val dragRatio = -diffY / screenHeight
                        val newB = (startBrightness + dragRatio * 1.4f).coerceIn(0.01f, 1f)
                        brightnessPct = newB
                        hasUserAdjustedBrightness = true
                        showBrightnessBar = true
                        showVolumeBar = false
                        brightnessTouchTrigger = System.currentTimeMillis()
                        change.consume()
                    }
                    PlayerGestureMode.VOLUME -> {
                        val dragRatio = -diffY / screenHeight
                        val newVolRatio = ((startVolume.toFloat() / maxVolume.toFloat()) + dragRatio * 1.3f).coerceIn(0f, 1f)
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
                        val newSpeed = (speedHoldDisplaySpeed + deltaX * 0.005f).coerceIn(0.25f, 4.0f)
                        speedHoldDisplaySpeed = newSpeed
                        exoPlayer.setPlaybackSpeed(newSpeed)
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
                PlayerGestureMode.ORIENTATION_SWIPE, PlayerGestureMode.IGNORED_DRAG -> {
                    // Swiped orientation or ignored drag - do not trigger tap
                }
                PlayerGestureMode.NONE -> {
                    if (!hasMoved) {
                        // Quick stationary tap!
                        if (isDoubleTap) {
                            lastTapTime = 0L // consume double tap
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                            showControls = true
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

    // ─── ADAPTIVE ORIENTATION LAYOUT (Landscape vs Portrait YouTube Style) ───
    if (isLandscape || isVerticalVideo) {
        // ─── FULLSCREEN PLAYER MODE (Landscape or Fullscreen Vertical 9:16) ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(playerGestureModifier)
        ) {
            // ─── 1. Video Surface ─────────────────────────────────────────────────
            AndroidView(
                factory = { _ ->
                    (playerView.parent as? ViewGroup)?.removeView(playerView)
                    playerView
                },
                update = { pv -> pv.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize()
            )

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
                label = "${(brightnessPct * 100).toInt()}%",
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
                icon = if (isMuted || volumePct == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                percentage = if (isMuted) 0f else volumePct,
                label = if (isMuted) "Muted" else "${(volumePct * 100).toInt()}%",
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

        // ─── 6b. Press-Hold Speed HUD ─────────────────────────────────────────
        AnimatedVisibility(
            visible = isSpeedHolding && !isInPiP,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.90f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.18f))
                    .blur(24.dp)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                primaryAccent.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(primaryAccent.copy(0.7f), primaryAccent.copy(0.25f), Color.Transparent)
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 40.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = String.format("%.2f", speedHoldDisplaySpeed) + "×",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hold & Slide ←→ to adjust",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ─── 7. Main Controls Overlay ─────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls && !isInPiP,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
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
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Color Theme Palette Button (Instant 1-tap cycle)
                                    IconButton(
                                        onClick = {
                                            val nextTheme = themeController.cycleColorTheme()
                                            Toast.makeText(context, "Theme: ${nextTheme.displayName}", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Palette,
                                            contentDescription = "Theme",
                                            tint = primaryAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

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
                                onOpenAudioDialog = {
                                    audioSubInitialTab = 0
                                    showAudioSubSheet = true
                                },
                                onOpenSubtitleDialog = {
                                    audioSubInitialTab = 1
                                    showAudioSubSheet = true
                                },
                                playbackSpeed = playbackSpeed,
                                onOpenSpeedDialog = { showSpeedSheet = true },
                                repeatMode = repeatMode,
                                onCycleRepeatMode = {
                                    repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    exoPlayer.repeatMode = repeatMode
                                    Toast.makeText(context, when(repeatMode) {
                                        Player.REPEAT_MODE_ONE -> "Repeat: Loop 1"
                                        Player.REPEAT_MODE_ALL -> "Repeat: Loop All"
                                        else -> "Repeat: Off"
                                    }, Toast.LENGTH_SHORT).show()
                                },
                                isNightMode = isNightMode,
                                onToggleNightMode = {
                                    isNightMode = !isNightMode
                                    Toast.makeText(context, if (isNightMode) "Night mode ON" else "Night mode OFF", Toast.LENGTH_SHORT).show()
                                },
                                sleepTimerMinutes = sleepTimerMinutes,
                                onOpenSleepTimerDialog = { showSleepTimerSheet = true },
                                isBackgroundAudio = isBackgroundAudio,
                                onToggleBackgroundAudio = {
                                    isBackgroundAudio = !isBackgroundAudio
                                    Toast.makeText(context, if (isBackgroundAudio) "Background audio ON" else "Background audio OFF", Toast.LENGTH_SHORT).show()
                                },
                                onOpenVideoInfo = { showVideoInfoSheet = true },
                                primaryAccent = primaryAccent,
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
                            // ─── Seek Thumbnail Preview (Floating Directly Above Progress Bar) ───
                            LaunchedEffect(scrubPosition, isDraggingSeek) {
                                if (isDraggingSeek) {
                                    val targetMs = scrubPosition.toLong()
                                    val bucket = targetMs / 1000L
                                    val cached = frameCache.get(bucket)
                                    if (cached != null) {
                                        seekThumbnail = cached
                                    } else {
                                        val bmp = withContext(Dispatchers.IO) {
                                            try {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                                    thumbnailRetriever.getScaledFrameAtTime(
                                                        targetMs * 1000L,
                                                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                                        240, 135
                                                    ) ?: thumbnailRetriever.getFrameAtTime(targetMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                } else {
                                                    thumbnailRetriever.getFrameAtTime(targetMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                }
                                            } catch (_: Exception) { null }
                                        }
                                        if (bmp != null) {
                                            frameCache.put(bucket, bmp)
                                            seekThumbnail = bmp
                                        }
                                    }
                                } else {
                                    seekThumbnail = null
                                }
                            }

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
                                    },
                                    onValueChangeFinished = {
                                        isDraggingSeek = false
                                        safeSeek(scrubPosition.toLong())
                                    },
                                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            modifier = Modifier.height(3.dp),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = primaryAccent,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                            ),
                                            drawStopIndicator = null
                                        )
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
                                    text = formatTime(duration),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // ─── Bottom Media Controls Bar (100% Symmetrical) ───
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
                                        onClick = { isLocked = true },
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
                                        onClick = { seekBy(-10000L) },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Replay10,
                                            contentDescription = "-10 seconds",
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
                                        onClick = { seekBy(10000L) },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Forward10,
                                            contentDescription = "+10 seconds",
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
                        }
                    }
                }

                // ─── Screen Locked Floating Unlock Button ─────────────────────
                if (isLocked) {
                    IconButton(
                        onClick = { isLocked = false },
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
                // 1. Video Surface
                AndroidView(
                    factory = { _ ->
                        (playerView.parent as? ViewGroup)?.removeView(playerView)
                        playerView
                    },
                    update = { pv -> pv.resizeMode = resizeMode },
                    modifier = Modifier.fillMaxSize()
                )

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

                // 5. Speed Hold HUD
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSpeedHolding && !isInPiP,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.82f))
                            .border(1.dp, primaryAccent, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(22.dp))
                            Text(
                                text = String.format("%.2f", speedHoldDisplaySpeed) + "×",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text("Hold & Slide to adjust", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
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
                    Box(Modifier.fillMaxSize()) {
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
                                QuickActionButton(
                                    icon = Icons.Rounded.Info,
                                    label = "Details",
                                    isActive = false,
                                    activeColor = primaryAccent,
                                    onClick = { showVideoInfoSheet = true }
                                )

                                IconButton(
                                    onClick = {
                                        val nextTheme = themeController.cycleColorTheme()
                                        Toast.makeText(context, "Theme: ${nextTheme.displayName}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Palette, contentDescription = "Theme", tint = primaryAccent, modifier = Modifier.size(17.dp))
                                }
                            }
                        }

                        // Center Controls (10s Back, Play/Pause, 10s Forward)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            IconButton(
                                onClick = { seekBy(-10000L) },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                            ) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(24.dp))
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
                                onClick = { seekBy(10000L) },
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                            ) {
                                Icon(Icons.Rounded.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(24.dp))
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
                                },
                                onValueChangeFinished = {
                                    isDraggingSeek = false
                                    safeSeek(scrubPosition.toLong())
                                },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(3.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = primaryAccent,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                        ),
                                        drawStopIndicator = null
                                    )
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
                                text = formatTime(duration),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
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
                },
                playbackSpeed = playbackSpeed,
                onSpeedClick = { showSpeedSheet = true },
                isNightMode = isNightMode,
                onNightModeToggle = { isNightMode = !isNightMode },
                resizeMode = resizeMode,
                onAspectToggle = cycleResizeMode,
                isMuted = isMuted,
                onMuteToggle = toggleMute,
                onPiPClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val params = android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(android.util.Rational(16, 9))
                                .build()
                            activity?.enterPictureInPictureMode(params)
                        } catch (_: Exception) {
                            try {
                                @Suppress("DEPRECATION")
                                activity?.enterPictureInPictureMode()
                            } catch (_: Exception) {}
                        }
                    }
                },
                onDetailsClick = {
                    val vf = exoPlayer.videoFormat
                    val res = vf?.let { "${it.width}x${it.height}" } ?: "Adapted"
                    val fps = vf?.frameRate?.takeIf { it > 0 }?.let { "${it.toInt()}fps" } ?: ""
                    val infoText = "$res ${if (fps.isNotEmpty()) "• $fps " else ""}• ${formatTime(duration)}"
                    Toast.makeText(context, infoText, Toast.LENGTH_LONG).show()
                },
                onSelectVideo = playVideoItem,
                primaryAccent = primaryAccent
            )
        }
    }

        // ─── Playlist Bottom Sheet ────────────────────────────────────────────
        if (showPlaylistSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPlaylistSheet = false },
                containerColor = Color(0xFF16161A),
                scrimColor = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playlist (${playlistVideos.size} Videos)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showPlaylistSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(playlistVideos) { index, item ->
                            val isCurrent = item.uri.toString() == currentUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) primaryAccent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) primaryAccent else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        currentVideoIndex = index
                                        currentUrl = item.uri.toString()
                                        videoTitle = item.title.substringBeforeLast(".")
                                        exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                        showPlaylistSheet = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) primaryAccent else Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text("${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isCurrent) primaryAccent else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatTime(item.duration),
                                        color = Color.White.copy(alpha = 0.60f),
                                        fontSize = 12.sp
                                    )
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
                    playbackSpeed = newSpeed
                    exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
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
                containerColor = Color(0xFF141418),
                scrimColor = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Choose Player Dynamic Theme", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerTheme.entries.forEach { theme ->
                            val isSelected = activeTheme == theme
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) theme.primaryAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                                    .border(1.5.dp, if (isSelected) theme.primaryAccent else Color.Transparent, RoundedCornerShape(14.dp))
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
                                    Text(theme.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(theme.description, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
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

        // ─── Audio & Subtitles Mini Dialog (Compact Screen with 4 Subtitle Styles) ───
        if (showAudioSubSheet) {
            MiniAudioSubtitlesDialog(
                exoPlayer = exoPlayer,
                subtitleDesign = subtitleDesign,
                onSubtitleDesignChange = { newDesign ->
                    subtitleDesign = newDesign
                    applySubtitleDesign(playerView, newDesign)
                    appPreferences.saveSubtitleDesign(newDesign)
                },
                accentColor = primaryAccent,
                initialTab = audioSubInitialTab,
                onDismiss = { showAudioSubSheet = false }
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
    val clampedPct = percentage.coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = clampedPct,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "smoothGesturePct"
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

// ─── Compact Quick Action Pill (Close together & space efficient) ────────────
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF22C55E),
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.22f)
                else Color.Black.copy(alpha = 0.55f)
            )
            .border(
                width = 0.8.dp,
                color = if (isActive) activeColor.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else Color.White.copy(alpha = 0.90f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.90f),
            fontSize = 11.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
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
fun applySubtitleDesign(playerView: PlayerView?, design: Int) {
    val subtitleView = playerView?.subtitleView ?: return
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)

    val captionStyle = when (design) {
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
    subtitleView.setStyle(captionStyle)
}

// ─── Floating Player Controls Bar (Direct On-Screen Actions) ─────────────────
@Composable
private fun FloatingPlayerControlsBar(
    exoPlayer: ExoPlayer,
    onOpenAudioDialog: () -> Unit,
    onOpenSubtitleDialog: () -> Unit,
    playbackSpeed: Float,
    onOpenSpeedDialog: () -> Unit,
    repeatMode: Int,
    onCycleRepeatMode: () -> Unit,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    sleepTimerMinutes: Int,
    onOpenSleepTimerDialog: () -> Unit,
    isBackgroundAudio: Boolean,
    onToggleBackgroundAudio: () -> Unit,
    onOpenVideoInfo: () -> Unit,
    primaryAccent: Color,
    modifier: Modifier = Modifier
) {
    val hasSubtitles = remember(exoPlayer.currentTracks) {
        exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Audio Track / Language (Opens Mini Popup Dialog on Audio Tab)
        QuickActionButton(
            icon = Icons.Rounded.Audiotrack,
            label = "Audio",
            isActive = false,
            activeColor = primaryAccent,
            onClick = onOpenAudioDialog
        )

        // 2. Subtitles (Opens Mini Popup Dialog with 4 Styles Preview + Audio/Sub tracks)
        QuickActionButton(
            icon = Icons.Rounded.Subtitles,
            label = "Subtitles",
            isActive = hasSubtitles,
            activeColor = primaryAccent,
            onClick = onOpenSubtitleDialog
        )

        // 3. Playback Speed (Opens Mini Popup Dialog with - / + user defined speed)
        QuickActionButton(
            icon = Icons.Rounded.Speed,
            label = if (playbackSpeed != 1f) String.format(Locale.getDefault(), "%.2fx", playbackSpeed) else "Speed",
            isActive = playbackSpeed != 1f,
            activeColor = primaryAccent,
            onClick = onOpenSpeedDialog
        )

        // 4. Sleep Timer (Opens Mini Popup Dialog)
        QuickActionButton(
            icon = Icons.Rounded.Timer,
            label = if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Timer",
            isActive = sleepTimerMinutes > 0,
            activeColor = primaryAccent,
            onClick = onOpenSleepTimerDialog
        )

        // 5. Repeat / Loop Mode
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

        // 6. Night Cinema Tint
        QuickActionButton(
            icon = Icons.Rounded.Bedtime,
            label = if (isNightMode) "Night ON" else "Night",
            isActive = isNightMode,
            activeColor = primaryAccent,
            onClick = onToggleNightMode
        )

        // 7. Background Audio Playback
        QuickActionButton(
            icon = Icons.Rounded.Headphones,
            label = if (isBackgroundAudio) "BG ON" else "BG Play",
            isActive = isBackgroundAudio,
            activeColor = primaryAccent,
            onClick = onToggleBackgroundAudio
        )

        // 8. Video Details (Moved at last position)
        QuickActionButton(
            icon = Icons.Rounded.Info,
            label = "Details",
            isActive = false,
            activeColor = primaryAccent,
            onClick = onOpenVideoInfo
        )
    }
}

// ─── Mini Audio & Subtitles Dialog (Compact Screen) ───────────────────────────
@Composable
private fun MiniAudioSubtitlesDialog(
    exoPlayer: ExoPlayer,
    subtitleDesign: Int,
    onSubtitleDesignChange: (Int) -> Unit,
    accentColor: Color,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222432)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio & Subtitles",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 3 Compact Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF191A24),
                    contentColor = accentColor,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Audio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Subtitles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Styles (4)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                val currentTracks = exoPlayer.currentTracks
                val trackGroups = currentTracks.groups

                when (selectedTab) {
                    0 -> { // Audio tracks
                        val audioGroups = trackGroups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        if (audioGroups.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Default Audio Stream", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                audioGroups.forEachIndexed { groupIdx, group ->
                                    for (i in 0 until group.length) {
                                        val isSelected = group.isTrackSelected(i)
                                        val format = group.getTrackFormat(i)
                                        val lang = format.language ?: "Audio Track ${groupIdx + 1}"
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                                                    .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                            .build()
                                                        onDismiss()
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(lang, color = if (isSelected) accentColor else Color.White, fontSize = 13.sp)
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
                    1 -> { // Subtitle tracks
                        val textGroups = trackGroups.filter { it.type == C.TRACK_TYPE_TEXT }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val areDisabled = exoPlayer.trackSelectionParameters.ignoredTextSelectionFlags == C.SELECTION_FLAG_DEFAULT
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (areDisabled) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, if (areDisabled) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                                        .clickable {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .build()
                                            onDismiss()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Subtitles Off", color = if (areDisabled) accentColor else Color.White, fontSize = 13.sp)
                                    if (areDisabled) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            textGroups.forEachIndexed { groupIdx, group ->
                                for (i in 0 until group.length) {
                                    val isSelected = !areDisabled && group.isTrackSelected(i)
                                    val format = group.getTrackFormat(i)
                                    val lang = format.language ?: "Subtitle ${groupIdx + 1}"
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                                                .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setIgnoredTextSelectionFlags(0)
                                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                        .build()
                                                    onDismiss()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(lang, color = if (isSelected) accentColor else Color.White, fontSize = 13.sp)
                                            if (isSelected) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Subtitle Styles (4 distinct designs)
                        val subtitleStyles = listOf(
                            Triple(0, "Classic Cinema", "White text with solid black outline"),
                            Triple(1, "Dark Box", "Crisp white text with slate box backing"),
                            Triple(2, "Golden Film", "Cinematic gold text with drop shadow"),
                            Triple(3, "Cyber Neon", "Vibrant cyan text with glowing neon border")
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
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
                                        containerColor = if (isChosen) accentColor.copy(alpha = 0.18f) else Color(0xFF181922)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChosen) accentColor else Color.White.copy(alpha = 0.08f)
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
                                                color = if (isChosen) accentColor else Color.White,
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
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 10.sp
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        // Visual Preview Box
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0F1015))
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222432)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
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
                        Text("Playback Speed", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
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
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease speed", tint = Color.White, modifier = Modifier.size(24.dp))
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
                            color = Color.White.copy(alpha = 0.55f),
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
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase speed", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Reset to 1.0x Chip Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (abs(currentSpeed - 1.0f) < 0.01f) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f))
                        .border(1.dp, if (abs(currentSpeed - 1.0f) < 0.01f) accentColor else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .clickable { onSpeedChange(1.0f) }
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Reset to 1.0x",
                        color = if (abs(currentSpeed - 1.0f) < 0.01f) accentColor else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(18.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Spacer(Modifier.height(12.dp))

                // Presets Title & Row
                Text(
                    text = "Quick Presets",
                    color = Color.White.copy(alpha = 0.6f),
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
                                .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.08f))
                                .clickable { onSpeedChange(preset) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset}x",
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222432)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
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
                        Text("Sleep Timer", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
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
                                .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { onSelectTimer(mins) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else Color.White,
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222432)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
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
                        Text("Video Details", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = Color.White,
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
    isNightMode: Boolean,
    onNightModeToggle: () -> Unit,
    resizeMode: Int,
    onAspectToggle: () -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onPiPClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    primaryAccent: Color
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1F26))
    ) {
        // Video Header Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = videoTitle,
                color = Color.White,
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
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text("•", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                Text(
                    text = "${playlistVideos.size} in playlist",
                    color = Color.White.copy(alpha = 0.65f),
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

        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

        // 3 Exploration Tabs: Queue, All Videos, Folders
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF252732),
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
                            tint = if (selectedTab == 0) primaryAccent else Color.White.copy(0.6f)
                        )
                        Text(
                            "Queue (${playlistVideos.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) primaryAccent else Color.White.copy(0.7f)
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
                            tint = if (selectedTab == 1) primaryAccent else Color.White.copy(0.6f)
                        )
                        Text(
                            "All Videos (${allVideos.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) primaryAccent else Color.White.copy(0.7f)
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
                            tint = if (selectedTab == 2) primaryAccent else Color.White.copy(0.6f)
                        )
                        Text(
                            "Folders (${allFolders.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 2) primaryAccent else Color.White.copy(0.7f)
                        )
                    }
                }
            )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> {
                    // Queue / Playlist
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(playlistVideos, key = { it.uri.toString() }) { video ->
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
                    // All Videos (Searchable)
                    val filteredVideos = remember(searchQuery, allVideos) {
                        if (searchQuery.isBlank()) allVideos
                        else allVideos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search videos...", color = Color.White.copy(0.4f), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = primaryAccent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF282A36),
                                unfocusedContainerColor = Color(0xFF282A36),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = Color.White.copy(0.12f)
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
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "(${currentDrillFolder.videos.size})",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(currentDrillFolder.videos, key = { it.uri.toString() }) { video ->
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(allFolders, key = { it.id }) { folder ->
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
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${folder.videos.size} videos",
                                            color = Color.White.copy(0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = Color.White.copy(0.35f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (isActive) accentColor else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) accentColor else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (isActive) accentColor else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
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
                .background(Color(0xFF282A36))
                .border(
                    if (isCurrent) 1.5.dp else 0.5.dp,
                    if (isCurrent) primaryAccent else Color.White.copy(0.12f),
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
                color = if (isCurrent) primaryAccent else Color.White,
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
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(video.duration),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), thickness = 0.5.dp)
}
