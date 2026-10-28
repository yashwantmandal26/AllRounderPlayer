package com.example.ymediaplayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.theme.LocalAppColors
import kotlin.math.abs

/**
 * Unified stadium pill mini music player used consistently across the entire app
 * (Music tab, Video folder list, Folder detail screen, etc.).
 *
 * Tapping on the pill immediately expands the full-screen music player.
 * Also supports horizontal drag gesture (swipe left to next, swipe right to previous).
 */
@Composable
fun MusicMiniPlayerPill(
    title: String,
    artist: String,
    artUri: Any?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
) {
    val c = LocalAppColors.current
    val view = LocalView.current
    val pillShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (c.isMatte) Modifier
                else Modifier.shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    ambientColor = c.accentBlue.copy(alpha = 0.4f),
                    spotColor = c.accentBlue
                )
            )
            .clip(pillShape)
            .background(if (c.isMatte) c.cardBg else Color(0xFF141724))
            .border(
                1.3.dp,
                if (c.isMatte) SolidColor(c.glassBorder)
                else Brush.horizontalGradient(
                    listOf(
                        c.accentBlue,
                        c.cardBorderHighlight,
                        c.accentBlue.copy(alpha = 0.7f)
                    )
                ),
                pillShape
            )
            .clickable(onClick = {
                view.performHaptic(HapticType.LIGHT)
                onClick()
            })
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                        if (abs(totalDragX) > 20f || abs(totalDragY) > 20f) {
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (totalDragY < -30f && abs(totalDragY) > abs(totalDragX)) {
                            view.performHaptic(HapticType.LIGHT)
                            onClick()
                        } else if (totalDragX < -45f) {
                            view.performHaptic(HapticType.LIGHT)
                            onNext()
                        } else if (totalDragX > 45f) {
                            view.performHaptic(HapticType.LIGHT)
                            onPrev()
                        }
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Album Art Box ──────────────────────────────────────────
            val hasArt = remember(artUri) {
                when (artUri) {
                    null -> false
                    is String -> artUri.isNotBlank()
                    is android.net.Uri -> artUri != android.net.Uri.EMPTY && artUri.toString().isNotBlank()
                    else -> true
                }
            }
            var imageLoadFailed by remember(artUri) { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2136))
                    .border(0.8.dp, c.cardBorderHighlight, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasArt && !imageLoadFailed) {
                    AsyncImage(
                        model = artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { imageLoadFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!hasArt || imageLoadFailed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        c.accentBlue.copy(alpha = 0.28f),
                                        Color(0xFF161928)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Music",
                            tint = c.accentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ─── Song Title & Artist ────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = title.ifBlank { "Now Playing" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = artist.ifBlank { "Unknown Artist" },
                    color = c.accentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Keep a fixed slot immediately before the transport controls. The
            // bars animate in place, so play/pause never moves the buttons.
            Box(
                modifier = Modifier.size(width = 16.dp, height = 14.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingBars")
                        val bar1 by infiniteTransition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
                            label = "b1"
                        )
                        val bar2 by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 0.15f,
                            animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
                            label = "b2"
                        )
                        val bar3 by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
                            label = "b3"
                        )

                        listOf(bar1, bar2, bar3).forEach { factor ->
                            Box(
                                modifier = Modifier
                                    .width(2.2.dp)
                                    .fillMaxHeight(factor)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(c.accentBlue)
                            )
                        }
                    }
                }
            }

            // ─── Skip Previous Button ───────────────────────────────────
            IconButton(
                onClick = {
                    view.performHaptic(HapticType.LIGHT)
                    onPrev()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ─── Circular Play / Pause Button ───────────────────────────
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = if (c.isMatte) 0.dp else 6.dp,
                        shape = CircleShape,
                        ambientColor = c.accentBlue,
                        spotColor = c.accentBlue
                    )
                    .clip(CircleShape)
                    .background(c.accentBlue)
                    .clickable {
                        view.performHaptic(HapticType.LIGHT)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = c.onAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ─── Skip Next Button ───────────────────────────────────────
            IconButton(
                onClick = {
                    view.performHaptic(HapticType.LIGHT)
                    onNext()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ─── Close / Exit (X) Button ────────────────────────────────
            IconButton(
                onClick = {
                    view.performHaptic(HapticType.LIGHT)
                    onClose()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Player",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
