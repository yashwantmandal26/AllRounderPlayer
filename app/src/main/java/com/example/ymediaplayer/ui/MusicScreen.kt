package com.example.ymediaplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.data.MusicRepository
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.Player

@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val repository = remember { MusicRepository(context) }
    
    var musicList by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Player State
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var currentlyPlaying by remember { mutableStateOf<MusicItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showFullScreenPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        musicList = repository.getMusicFiles()
        isLoading = false
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accentBlue, strokeWidth = 3.dp)
            }
        } else if (musicList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Music Found", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                item {
                    Text(
                        "ALL SONGS (${musicList.size})",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(musicList, key = { it.id }) { song ->
                    Box(modifier = Modifier.animateItem()) {
                        MusicListItem(
                            song = song,
                            isCurrentlyPlaying = currentlyPlaying?.id == song.id,
                            isPlaying = isPlaying,
                            onClick = {
                                if (currentlyPlaying?.id != song.id) {
                                    currentlyPlaying = song
                                    exoPlayer.setMediaItem(MediaItem.fromUri(song.uri))
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                    isPlaying = true
                                } else {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        exoPlayer.play()
                                        isPlaying = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Mini Player
        AnimatedVisibility(
            visible = currentlyPlaying != null && !showFullScreenPlayer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            currentlyPlaying?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        isPlaying = !isPlaying
                    },
                    onClick = { showFullScreenPlayer = true }
                )
            }
        }

        // Full Screen Player
        AnimatedVisibility(
            visible = showFullScreenPlayer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            currentlyPlaying?.let { song ->
                FullScreenMusicPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        isPlaying = !isPlaying
                    },
                    onClose = { showFullScreenPlayer = false }
                )
            }
        }
    }
}

@Composable
fun MusicListItem(song: MusicItem, isCurrentlyPlaying: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isCurrentlyPlaying) c.glassBg else Color.Transparent)
            .border(0.5.dp, if (isCurrentlyPlaying) c.glassBorder else Color.Transparent, RoundedCornerShape(20.dp))
            .bounceClick(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri)
                    .build(),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentlyPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    BeatVisualizer(
                        isPlaying = isPlaying,
                        barCount = 5,
                        height = 24.dp,
                        withReflection = false
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrentlyPlaying) c.accentBlue else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist,
                fontSize = 13.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { /* More */ }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = c.textSecondary)
        }
    }
}

@Composable
fun MiniPlayer(song: MusicItem, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(0.5f), spotColor = Color.Black.copy(0.5f))
            .clip(RoundedCornerShape(24.dp))
            .background(c.navBarScrim)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(24.dp))
            .bounceClick(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(song.albumArtUri).build(),
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.DarkGray)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = c.textPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun FullScreenMusicPlayer(song: MusicItem, isPlaying: Boolean, onPlayPause: () -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "spin_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.baseBackground)
    ) {
        // Blurred background of the album art
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(80.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)))

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { /* More */ }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            // Spinning Album Art
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .shadow(32.dp, CircleShape, ambientColor = Color.Black.copy(0.8f))
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .border(2.dp, Color.White.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(song.albumArtUri).build(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        rotationZ = if (isPlaying) rotation else 0f
                    }
                )
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black).border(2.dp, Color.White.copy(0.1f), CircleShape))
            }

            Spacer(Modifier.height(48.dp))

            // Premium Beat Visualizer
            BeatVisualizer(
                isPlaying = isPlaying,
                barCount = 26,
                height = 72.dp,
                withReflection = true
            )
            
            Spacer(Modifier.height(24.dp))

            Text(song.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(song.artist, color = Color.White.copy(0.7f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Prev */ }) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(48.dp)) }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(c.accentBlue)
                        .bounceClick(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { /* Next */ }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp)) }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Premium Colourful Beat Visualizer
// ─────────────────────────────────────────────────────────────────

/** Spectrum colours that cycle across the bars */
private val spectrumColors = listOf(
    Color(0xFF7C3AED), // violet
    Color(0xFF6D28D9),
    Color(0xFF2563EB), // electric blue
    Color(0xFF0891B2), // cyan
    Color(0xFF059669), // emerald
    Color(0xFF65A30D), // lime
    Color(0xFFF59E0B), // amber
    Color(0xFFEA580C), // orange
    Color(0xFFDC2626), // red
    Color(0xFFDB2777), // pink
    Color(0xFF7C3AED), // back to violet
)

/**
 * Returns a smooth interpolated colour across the spectrum palette
 * given a fraction [0, 1].
 */
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
        blue  = a.blue  + (b.blue  - a.blue)  * t,
    )
}

/**
 * Premium beat visualizer with colourful gradient bars and an optional
 * mirror/reflection beneath, giving a club-style spectrum look.
 *
 * @param isPlaying    Whether playback is active.
 * @param barCount     Number of frequency bars (recommend 16–32).
 * @param height       Total height of the upward-going bars.
 * @param withReflection  If true, draws a faded mirror image below.
 * @param barWidthDp   Width of each individual bar.
 * @param gapDp        Gap between bars.
 */
@Composable
fun BeatVisualizer(
    isPlaying: Boolean,
    barCount: Int = 24,
    height: Dp = 64.dp,
    withReflection: Boolean = true,
    barWidthDp: Dp = 5.dp,
    gapDp: Dp = 3.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beatVis")

    // Pre-compute per-bar animation durations once
    val durations = remember(barCount) {
        List(barCount) { i ->
            // Create a realistic spectrum shape: peaks in the low-mid and mid
            // frequencies, quieter at extremes.
            val posNorm = i.toFloat() / (barCount - 1) // 0..1
            val shapeFactor = 1f - kotlin.math.abs(posNorm - 0.35f) * 1.4f
            val baseDuration = (180 + (shapeFactor * 220).toInt()).coerceIn(140, 500)
            // Randomise a little so bars don't move in unison
            (baseDuration + (-60..60).random()).coerceIn(120, 600)
        }
    }

    // Stagger initial values so bars start at different phases
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

            // Gradient for each bar: bright top → mid hue → slightly darker base
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
                // Main upward bar
                Box(
                    modifier = Modifier
                        .width(barWidthDp)
                        .fillMaxHeight(activeScale)
                        .drawBehind {
                            // Soft glow behind the bar
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
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    x = -this.size.width * 1.25f,
                                    y = 0f
                                )
                            )
                        }
                        .clip(RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp))
                        .background(barGradient)
                )

                if (withReflection) {
                    // Reflected bar below (flipped, faded)
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
// Legacy thin visualizer kept for internal use (MusicListItem overlay)
// ─────────────────────────────────────────────────────────────────
@Composable
fun AnimatedVisualizer(isPlaying: Boolean, barCount: Int, color: Color, height: Dp) {
    BeatVisualizer(
        isPlaying = isPlaying,
        barCount = barCount,
        height = height,
        withReflection = false,
        barWidthDp = 4.dp,
        gapDp = 3.dp
    )
}
