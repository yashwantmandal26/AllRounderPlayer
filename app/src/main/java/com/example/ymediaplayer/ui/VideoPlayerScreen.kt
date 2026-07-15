package com.example.ymediaplayer.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.FrameLayout
import android.view.ViewGroup
import android.widget.Toast
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.ui.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val PlayerAccentBlue   = Color(0xFF3D8EFF)
private val PlayerAccentPurple = Color(0xFF7B5EFF)
private val PlayerWhite        = Color(0xFFFFFFFF)
private val PlayerGlass        = Color(0x28FFFFFF)
private val PlayerGlassBorder  = Color(0x33FFFFFF)
private val PlayerPreviewBg    = Color(0xE6121212)
private val PlayerTimeLabelColor = Color(0x99FFFFFF)
private val PlayerAccentGradient = Brush.linearGradient(listOf(PlayerAccentBlue, PlayerAccentPurple))

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var showContinueBanner by remember { mutableStateOf(false) }
    
    val appPreferences = remember { AppPreferences(context) }
    
    // Load saved progress and show Continue Watching banner if meaningful
    LaunchedEffect(Unit) {
        val savedProgress = appPreferences.getVideoProgress(videoUrl)
        if (savedProgress > 5000L) { // only if more than 5 seconds in
            exoPlayer.seekTo(savedProgress)
            showContinueBanner = true
        }
    }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var isLandscape by remember(configuration.orientation) { 
        mutableStateOf(configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 
    }

    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var gesturePct by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var currentDragSide by remember { mutableIntStateOf(0) }

    var isLocked by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val coroutineScope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var showAudioTracks by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(250.milliseconds)
        }
    }

    LaunchedEffect(showControls, isPlaying, isDragging) {
        if (showControls && isPlaying && !isDragging) {
            delay(4.seconds)
            showControls = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                    appPreferences.saveVideoProgress(videoUrl, exoPlayer.currentPosition)
                }
                Lifecycle.Event.ON_STOP -> {
                    appPreferences.saveVideoProgress(videoUrl, exoPlayer.currentPosition)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            appPreferences.saveVideoProgress(videoUrl, exoPlayer.currentPosition)
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        } else {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                        }
                        showControls = true
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                var startX = 0f; var startY = 0f
                var isVerticalDrag = false; var isHorizontalDrag = false
                var dragTarget = 0
                var startVolume = 0; var startBrightness = 0f; var startPosition = 0L
                var currentSeekTarget = 0L

                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x; startY = offset.y
                        isVerticalDrag = false; isHorizontalDrag = false
                        dragTarget = 0; isDragging = true
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val layoutParams = activity?.window?.attributes
                        startBrightness = layoutParams?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                        startPosition = exoPlayer.currentPosition
                        currentSeekTarget = startPosition
                    },
                    onDragEnd = {
                        gestureLabel = null; gestureIcon = null
                        isDragging = false; currentDragSide = 0
                        if (dragTarget == 3) exoPlayer.seekTo(currentSeekTarget)
                    },
                    onDragCancel = { gestureLabel = null; gestureIcon = null; isDragging = false; currentDragSide = 0 },
                    onDrag = { change, _ ->
                        change.consume()
                        val diffX = change.position.x - startX
                        val diffY = change.position.y - startY

                        if (!isVerticalDrag && !isHorizontalDrag) {
                            if (abs(diffX) > 20f && abs(diffX) > abs(diffY)) {
                                isHorizontalDrag = true; dragTarget = 3
                            } else if (abs(diffY) > 20f && abs(diffY) > abs(diffX)) {
                                isVerticalDrag = true; dragTarget = if (startX < size.width / 2) 1 else 2
                            }
                        }

                        if (isVerticalDrag) {
                            val dragRatio = -diffY / size.height
                            if (dragTarget == 1) {
                                currentDragSide = 1
                                var newBrightness = startBrightness + dragRatio * 2
                                newBrightness = newBrightness.coerceIn(0f, 1f)
                                val layoutParams = activity?.window?.attributes
                                layoutParams?.screenBrightness = newBrightness
                                activity?.window?.attributes = layoutParams
                                gesturePct = newBrightness
                                gestureIcon = Icons.Rounded.BrightnessMedium
                                gestureLabel = "%"
                            } else if (dragTarget == 2) {
                                currentDragSide = 2
                                val newVolume = (startVolume + (dragRatio * maxVolume * 2).toInt()).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                gesturePct = newVolume.toFloat() / maxVolume
                                gestureIcon = if (newVolume == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
                                gestureLabel = "%"
                            }
                        } else if (isHorizontalDrag && dragTarget == 3) {
                            currentDragSide = 3
                            val dragRatio = diffX / size.width
                            val seekOffset = (dragRatio * 180000L).toLong() // 3 mins seek per screen width
                            currentSeekTarget = (startPosition + seekOffset).coerceIn(0, exoPlayer.duration)
                            gesturePct = currentSeekTarget.toFloat() / exoPlayer.duration.coerceAtLeast(1)
                            gestureIcon = if (seekOffset > 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind
                            gestureLabel = formatTime(currentSeekTarget)
                            exoPlayer.seekTo(currentSeekTarget)
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { playerView -> playerView.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.fillMaxSize()
        ) {
            val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false
            if (!isInPiP) {
                Box(Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent))))
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.95f)))))

                if (!isLocked) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            PlayerTopBar(
                                title = videoUrl.substringAfterLast("/").substringBeforeLast("."),
                                onBack = onBack,
                                onSettings = { showSettings = true },
                                onAudioSelect = { showAudioTracks = true }
                            )
                            Spacer(Modifier.height(16.dp))
                            PlayerSecondaryBar(
                                isMuted = isMuted,
                                onMuteToggle = { isMuted = !isMuted; exoPlayer.volume = if (isMuted) 0f else 1f },
                                isLandscape = isLandscape,
                                onRotate = {
                                    isLandscape = !isLandscape
                                    activity?.requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                },
                                playbackSpeed = playbackSpeed,
                                onSpeedCycle = {
                                    playbackSpeed = when (playbackSpeed) {
                                        0.25f -> 0.5f; 0.5f -> 0.75f; 0.75f -> 1.0f;
                                        1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f;
                                        else -> 0.25f
                                    }
                                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            PlayerGlassButton(
                                size = 48.dp, iconSize = 24.dp, 
                                onClick = { takeScreenshot(context, coroutineScope, videoUrl, exoPlayer.currentPosition) },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp)
                            ) { Icon(Icons.Rounded.PhotoCamera, contentDescription = "Snapshot", tint = PlayerWhite) }
                        }

                        Column {
                            if (showContinueBanner) {
                                ContinueWatchingBanner(
                                    onDismiss = { showContinueBanner = false },
                                    onStartOver = { exoPlayer.seekTo(0); showContinueBanner = false }
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                            PlayerBottomBar(
                                currentPosition = currentPosition, duration = duration, isPlaying = isPlaying, videoUrl = videoUrl,
                                onSeek = { exoPlayer.seekTo(it) }, onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                                onForward = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                                onLock = { isLocked = true },
                                onResize = {
                                    resizeMode = when (resizeMode) { AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                                }
                            )
                        }
                    }
                }

                if (isLocked) {
                    PlayerGlassButton(size = 56.dp, iconSize = 28.dp, onClick = { isLocked = false }, modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 24.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Unlock", tint = PlayerAccentBlue)
                    }
                }
            }
        }
    }

        if (gestureLabel != null) {
            when (currentDragSide) {
                1 -> VerticalGestureBar(icon = gestureIcon, percentage = gesturePct, modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp))
                2 -> VerticalGestureBar(icon = gestureIcon, percentage = gesturePct, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp))
                else -> {
                    Box(modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp)).background(PlayerGlass).border(1.dp, PlayerGlassBorder, RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            gestureIcon?.let { Icon(it, contentDescription = null, tint = PlayerWhite, modifier = Modifier.size(48.dp)) }
                            Spacer(Modifier.height(12.dp))
                            Text(gestureLabel ?: "", color = PlayerWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun VerticalGestureBar(icon: ImageVector?, percentage: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(36.dp).height(180.dp).clip(RoundedCornerShape(18.dp)).background(PlayerGlass).border(1.dp, PlayerGlassBorder, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(percentage).clip(RoundedCornerShape(18.dp)).background(PlayerAccentBlue))
        icon?.let { Icon(imageVector = it, contentDescription = null, tint = PlayerWhite, modifier = Modifier.align(Alignment.Center).size(24.dp)) }
    }
}

@Composable
private fun PlayerTopBar(title: String, onBack: () -> Unit, onSettings: () -> Unit, onAudioSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = PlayerWhite, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = title.take(40), color = PlayerWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val context = LocalContext.current
            val activity = context as? Activity
            IconButton(onClick = { /* Dummy Cast */ }) { Icon(Icons.Rounded.Cast, contentDescription = "Cast", tint = PlayerWhite) }
            IconButton(onClick = { 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    activity?.enterPictureInPictureMode(params)
                }
            }) { Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", tint = PlayerWhite) }
            IconButton(onClick = onAudioSelect) { Icon(Icons.Rounded.ClosedCaption, contentDescription = "CC", tint = PlayerWhite) }
            IconButton(onClick = { /* Dummy Playlist */ }) { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Playlist", tint = PlayerWhite) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.MoreVert, contentDescription = "Settings", tint = PlayerWhite) }
        }
    }
}

@Composable
private fun PlayerSecondaryBar(isMuted: Boolean, onMuteToggle: () -> Unit, isLandscape: Boolean, onRotate: () -> Unit, playbackSpeed: Float, onSpeedCycle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = onRotate) {
            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate", tint = if (isLandscape) PlayerAccentBlue else PlayerWhite)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = onMuteToggle) {
            Icon(if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Mute", tint = PlayerWhite)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = { /* Background Audio */ }) {
            Icon(Icons.Rounded.Headset, contentDescription = "Audio Mode", tint = PlayerWhite)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 28.dp, onClick = onSpeedCycle) {
            val speedText = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x"
            Text(speedText, color = PlayerWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = { /* Next Video */ }) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", tint = PlayerWhite)
        }
    }
}

@Composable
private fun ContinueWatchingBanner(onDismiss: () -> Unit, onStartOver: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = PlayerWhite, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Continue playing from where you stopped.", color = PlayerWhite, fontSize = 14.sp)
        }
        Text(
            text = "|  Start Over", 
            color = Color(0xFF4CAF50), 
            fontSize = 14.sp, 
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onStartOver() }.padding(8.dp)
        )
    }
}

@Composable
private fun PlayerBottomBar(
    currentPosition: Long, duration: Long, isPlaying: Boolean, videoUrl: String,
    onSeek: (Long) -> Unit, onPlayPause: () -> Unit, onRewind: () -> Unit, onForward: () -> Unit,
    onLock: () -> Unit, onResize: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = formatTime(currentPosition), color = PlayerWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            PlayerSeekBar(currentPosition = currentPosition, duration = duration, onSeek = onSeek, videoUrl = videoUrl, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(text = formatTime(duration), color = PlayerWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLock) { Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = PlayerWhite) }
            
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRewind) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = PlayerWhite, modifier = Modifier.size(32.dp)) }
                
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).border(1.dp, PlayerWhite, CircleShape).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = PlayerWhite, modifier = Modifier.size(32.dp))
                }
                
                IconButton(onClick = onForward) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = PlayerWhite, modifier = Modifier.size(32.dp)) }
            }
            
            IconButton(onClick = onResize) { Icon(Icons.Rounded.AspectRatio, contentDescription = "Resize", tint = PlayerWhite) }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBar(currentPosition: Long, duration: Long, onSeek: (Long) -> Unit, videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // Only sync slider with real position when NOT dragging
    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sliderPosition, isDragging) {
        if (isDragging) {
            delay(130)
            val bmp = withContext(Dispatchers.IO) {
                try {
                    MediaMetadataRetriever().run {
                        setDataSource(context, android.net.Uri.parse(videoUrl))
                        val f = getFrameAtTime(sliderPosition.toLong() * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        release()
                        f
                    }
                } catch (_: Exception) { null }
            }
            if (bmp != null) previewBitmap = bmp
        }
    }

    Column(modifier = modifier) {
        AnimatedVisibility(visible = isDragging, enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.75f, animationSpec = tween(200)), exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.75f, animationSpec = tween(160))) {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.width(190.dp).height(107.dp).shadow(20.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(PlayerPreviewBg).border(1.dp, PlayerGlassBorder, RoundedCornerShape(14.dp))) {
                        previewBitmap?.let {
                            Image(it.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PlayerPreviewBg).border(0.5.dp, PlayerGlassBorder, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(formatTime(sliderPosition.toLong()), color = PlayerWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Slider(
            value = if (isDragging) sliderPosition else currentPosition.toFloat(),
            onValueChange = { isDragging = true; sliderPosition = it },
            onValueChangeFinished = { isDragging = false; onSeek(sliderPosition.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = SliderDefaults.colors(thumbColor = PlayerWhite, activeTrackColor = PlayerAccentBlue, inactiveTrackColor = PlayerWhite.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun PlayerGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec) else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

private fun takeScreenshot(context: Context, scope: kotlinx.coroutines.CoroutineScope, videoUrl: String, timeMs: Long) {
    scope.launch(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, android.net.Uri.parse(videoUrl))
            val bitmap = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (bitmap != null) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "YMedia_Screenshot_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IndiPlayer")
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Screenshot saved to Pictures", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to capture", Toast.LENGTH_SHORT).show() }
        }
    }
}
