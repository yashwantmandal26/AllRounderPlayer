package com.example.ymediaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.ymediaplayer.player.VideoPlaybackManager
import com.example.ymediaplayer.theme.LocalThemeController
import kotlin.math.roundToInt

/**
 * YouTube-style floating In-App Picture-in-Picture miniplayer.
 * Appears floating over the Folder/Home screen, with video preview,
 * Play/Pause toggle, Close ('X') button, and tap-to-expand.
 */
@Composable
fun InAppMiniPlayer(
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeController = LocalThemeController.current
    val primaryAccent = themeController.colorTheme.primaryAccent

    val isPlaying by VideoPlaybackManager.isPlaying
    val title by VideoPlaybackManager.currentVideoTitle
    val player = VideoPlaybackManager.player
    val vWidth by VideoPlaybackManager.videoWidth
    val vHeight by VideoPlaybackManager.videoHeight

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dynamic sizing strictly matching video's native aspect ratio (supports vertical 9:16, 4:3, 16:9, 21:9)
    val (miniWidth, miniHeight) = remember(vWidth, vHeight) {
        val aspect = if (vWidth > 0 && vHeight > 0) {
            (vWidth.toFloat() / vHeight.toFloat()).coerceIn(0.48f, 2.39f)
        } else {
            16f / 9f
        }
        if (aspect < 1.0f) {
            // Vertical / Portrait video (Reels / Shorts / TikTok / 9:16)
            val h = 188.dp
            val w = (188.dp * aspect).coerceIn(100.dp, 150.dp)
            Pair(w, h)
        } else {
            // Horizontal / Landscape video (16:9, 4:3, 21:9)
            val w = 202.dp
            val h = (202.dp / aspect).coerceIn(86.dp, 150.dp)
            Pair(w, h)
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(end = 16.dp, bottom = 80.dp)
            .width(miniWidth)
            .height(miniHeight)
            .shadow(16.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F0F14))
            .border(1.2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.10f))), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .clickable { onExpand() }
    ) {
        // ── Video Frame View (TextureView clips cleanly to rounded corners) ──
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        useArtwork = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                    view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle gradient scrim overlay for controls visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // ── Top Bar: Title & Close ('X') button ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title ?: "Playing Video",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )

            // Close button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Miniplayer",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Center Controls: Play / Pause Button ──
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.2.dp, primaryAccent, CircleShape)
                .clickable { VideoPlaybackManager.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
