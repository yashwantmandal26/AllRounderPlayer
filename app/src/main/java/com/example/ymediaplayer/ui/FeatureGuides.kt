package com.example.ymediaplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ymediaplayer.theme.LocalAppColors
import kotlin.math.sin

/**
 * Visual "Ghost Hand" Gesture Simulation for Video Player (Approach 1 - MX Player & YouTube Style).
 * Zero text dialogs! Visually demonstrates left-edge brightness slide, right-edge volume slide,
 * double-tap 10s seek ripples, 2x hold speed, and Enhanced mode spotlight.
 */
@Composable
fun VideoPlayerFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "videoGuideAnim")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animProgress"
    )

    // Phase 1 (0.00..0.45): Edge gestures (Brightness & Volume slides)
    // Phase 2 (0.45..0.85): Center gestures (Double-tap seek & 2x speed hold)
    // Phase 3 (0.85..1.00): Rest / Fade
    val isPhase1 = animProgress < 0.45f
    val isPhase2 = animProgress in 0.45f..0.85f

    val edgeSlideFraction = if (isPhase1) {
        val p = (animProgress / 0.40f).coerceIn(0f, 1f)
        (0.5f - 0.5f * kotlin.math.cos(p * Math.PI)).toFloat()
    } else 0f

    val doubleTapFraction = if (isPhase2) {
        ((animProgress - 0.45f) / 0.38f).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // ─── Top Bar: Sleek Skip Pill ──────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { onDismiss() }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Skip", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Close, contentDescription = "Skip", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }

        // ─── Phase 1: Left Edge (Brightness) & Right Edge (Volume) Gestures ──
        if (animProgress < 0.48f) {
            val alphaFade = if (animProgress < 0.38f) 1f else (0.48f - animProgress) / 0.10f

            // Left Edge: Brightness Simulation
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .graphicsLayer { alpha = alphaFade }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Vertical HUD Bar filling up in sync with touch
                    VisualGestureHudBar(
                        icon = Icons.Rounded.BrightnessMedium,
                        percentage = 0.25f + edgeSlideFraction * 0.60f,
                        label = "Brightness",
                        accentColor = Color(0xFFFFB703),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFFFFB703), Color(0xFFFF8800)))
                    )

                    Spacer(Modifier.width(16.dp))

                    // Animated Glowing Ghost Hand
                    AnimatedGhostTouchPoint(
                        yTravelFraction = edgeSlideFraction,
                        accentColor = Color(0xFFFFB703),
                        label = "Slide edge"
                    )
                }
            }

            // Right Edge: Volume Simulation
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .graphicsLayer { alpha = alphaFade }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated Glowing Ghost Hand
                    AnimatedGhostTouchPoint(
                        yTravelFraction = edgeSlideFraction,
                        accentColor = Color(0xFF00F5D4),
                        label = "Slide edge"
                    )

                    Spacer(Modifier.width(16.dp))

                    // Vertical HUD Bar filling up in sync with touch
                    VisualGestureHudBar(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        percentage = 0.30f + edgeSlideFraction * 0.55f,
                        label = "Volume",
                        accentColor = Color(0xFF00F5D4),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFF00F5D4), Color(0xFF0096C7)))
                    )
                }
            }
        }

        // ─── Phase 2: Double-Tap Seek Ripples & 2x Hold Speed ──────────────
        if (animProgress in 0.43f..0.88f) {
            val alphaFade = when {
                animProgress < 0.48f -> (animProgress - 0.43f) / 0.05f
                animProgress > 0.80f -> (0.88f - animProgress) / 0.08f
                else -> 1f
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = alphaFade }
            ) {
                // Double-Tap Seek Right Side (+10s)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VisualDoubleTapRipple(
                        isForward = true,
                        progress = (doubleTapFraction * 2.2f).coerceIn(0f, 1f)
                    )
                }

                // Double-Tap Seek Left Side (-10s)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VisualDoubleTapRipple(
                        isForward = false,
                        progress = (doubleTapFraction * 2.2f).coerceIn(0f, 1f)
                    )
                }

                // Center: Touch & Hold 2x Turbo Speed
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VisualHoldSpeedPill(
                        progress = (doubleTapFraction * 1.5f).coerceIn(0f, 1f)
                    )
                }

                // Top Right Spotlight: Enhanced Mode
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 68.dp, end = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VisualEnhancedModeSpotlight()
                }
            }
        }

        // ─── Bottom Center: Subtle Tap to Dismiss Hint ─────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = Color.White.copy(0.85f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("Tap anywhere to start watching", color = Color.White.copy(alpha = 0.90f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Visual "Ghost Hand" Gesture Simulation for Music Player (Approach 1 - Modern Interactive Style).
 * Demonstrates left/right slide gestures for Brightness & Volume, horizontal swipe on album art to change tracks,
 * tap to toggle visualizer style, and spotlights Equalizer & Sleep Timer.
 */
@Composable
fun MusicPlayerFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "musicGuideAnim")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animProgress"
    )

    // Phase 1 (0.00..0.45): Left & Right edge slide gestures (Brightness & Volume)
    // Phase 2 (0.45..0.85): Center artwork swipe (change track) & tap (switch visualizer)
    val isPhase1 = animProgress < 0.45f
    val isPhase2 = animProgress in 0.45f..0.85f

    val edgeSlideFraction = if (isPhase1) {
        val p = (animProgress / 0.40f).coerceIn(0f, 1f)
        (0.5f - 0.5f * kotlin.math.cos(p * Math.PI)).toFloat()
    } else 0f

    val swipeFraction = if (isPhase2) {
        ((animProgress - 0.45f) / 0.38f).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // ─── Top Bar: Sleek Skip Pill ──────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { onDismiss() }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Skip", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Close, contentDescription = "Skip", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }

        // ─── Phase 1: Left Edge (Brightness) & Right Edge (Volume) ─────────
        if (animProgress < 0.48f) {
            val alphaFade = if (animProgress < 0.38f) 1f else (0.48f - animProgress) / 0.10f

            // Left Edge: Brightness Slide
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .graphicsLayer { alpha = alphaFade }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VisualGestureHudBar(
                        icon = Icons.Rounded.BrightnessMedium,
                        percentage = 0.25f + edgeSlideFraction * 0.60f,
                        label = "Brightness",
                        accentColor = Color(0xFFFFB703),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFFFFB703), Color(0xFFFF8800)))
                    )

                    Spacer(Modifier.width(14.dp))

                    AnimatedGhostTouchPoint(
                        yTravelFraction = edgeSlideFraction,
                        accentColor = Color(0xFFFFB703),
                        label = "Slide edge"
                    )
                }
            }

            // Right Edge: Volume Slide
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .graphicsLayer { alpha = alphaFade }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedGhostTouchPoint(
                        yTravelFraction = edgeSlideFraction,
                        accentColor = Color(0xFF00F5D4),
                        label = "Slide edge"
                    )

                    Spacer(Modifier.width(14.dp))

                    VisualGestureHudBar(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        percentage = 0.30f + edgeSlideFraction * 0.55f,
                        label = "Volume",
                        accentColor = Color(0xFF00F5D4),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFF00F5D4), Color(0xFF0096C7)))
                    )
                }
            }
        }

        // ─── Phase 2: Center Artwork Swipe & Tap Visualizer ────────────────
        if (animProgress in 0.43f..0.88f) {
            val alphaFade = when {
                animProgress < 0.48f -> (animProgress - 0.43f) / 0.05f
                animProgress > 0.80f -> (0.88f - animProgress) / 0.08f
                else -> 1f
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = alphaFade }
            ) {
                // Center Album Artwork: Horizontal Swipe Simulation
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VisualArtworkSwipeDemonstration(
                        swipeProgress = swipeFraction
                    )
                }

                // Bottom Spotlight: Equalizer Button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp, start = 44.dp)
                ) {
                    VisualButtonSpotlight(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Equalizer & Bass",
                        accentColor = Color(0xFFFFB703)
                    )
                }

                // Top Spotlight: Sleep Timer Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 18.dp, start = 64.dp)
                ) {
                    VisualButtonSpotlight(
                        icon = Icons.Rounded.Bedtime,
                        label = "Sleep Timer",
                        accentColor = Color(0xFF00F5D4)
                    )
                }
            }
        }

        // ─── Bottom Center: Subtle Tap to Dismiss Hint ─────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = Color.White.copy(0.85f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("Tap anywhere to start listening", color = Color.White.copy(alpha = 0.90f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VISUAL ANIMATION COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Animated Glowing Touch Hand circle that slides up & down vertically.
 */
@Composable
private fun AnimatedGhostTouchPoint(
    yTravelFraction: Float,
    accentColor: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    val travelPx = 140.dp
    val currentY = travelPx * (0.5f - yTravelFraction)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.offset(y = currentY)
    ) {
        // Outer pulsing touch circle
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(12.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.28f))
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner solid core dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Mini Label
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.70f))
                .border(0.8.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(label, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Real-time HUD Vertical Slider Bar filling up smoothly.
 */
@Composable
private fun VisualGestureHudBar(
    icon: ImageVector,
    percentage: Float,
    label: String,
    accentColor: Color,
    glowBrush: Brush,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFF14141A).copy(alpha = 0.88f))
            .border(1.5.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(21.dp))
            .padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))

        Spacer(Modifier.height(8.dp))

        // Vertical track
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(percentage.coerceIn(0.05f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(glowBrush)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${(percentage * 100).toInt()}%",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Double-Tap Seek Pulse (+10s or -10s).
 */
@Composable
private fun VisualDoubleTapRipple(
    isForward: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val scale = 0.7f + progress * 0.6f
    val alpha = (1f - progress).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color.White, spotColor = Color(0xFF00F5D4))
                .clip(CircleShape)
                .background(Color(0xFF00F5D4).copy(alpha = 0.22f))
                .border(2.dp, Color(0xFF00F5D4), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isForward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .border(1.dp, Color(0xFF00F5D4).copy(0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isForward) "Double-Tap: +10s" else "Double-Tap: -10s",
                color = Color(0xFF00F5D4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Visual pill for 2x Turbo Speed hold gesture.
 */
@Composable
private fun VisualHoldSpeedPill(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val pulseAlpha = 0.6f + 0.4f * sin(progress * Math.PI.toFloat())

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .border(1.5.dp, Color(0xFFFFB703).copy(alpha = pulseAlpha), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Hold Anywhere: 2x Turbo Speed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Top-Right spotlight badge pointing out Enhanced Mode.
 */
@Composable
private fun VisualEnhancedModeSpotlight(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1528).copy(alpha = 0.88f))
            .border(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFF007F), Color(0xFF7B2CBF))), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text("Enhanced Mode", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Text("AI Clarity & Saturation", color = Color(0xFFFF007F), fontSize = 9.5.sp)
            }
        }
    }
}

/**
 * Center album artwork swipe & tap visualizer demonstration for Music Player.
 */
@Composable
private fun VisualArtworkSwipeDemonstration(
    swipeProgress: Float,
    modifier: Modifier = Modifier
) {
    val xOffset = 90.dp * sin(swipeProgress * 2 * Math.PI.toFloat())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Floating Animated Hand
        Box(
            modifier = Modifier
                .offset(x = xOffset)
                .size(48.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF00F5D4), spotColor = Color(0xFF00F5D4))
                .clip(CircleShape)
                .background(Color(0xFF00F5D4).copy(alpha = 0.30f))
                .border(2.dp, Color(0xFF00F5D4), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(Modifier.height(14.dp))

        // Badge: Swipe left / right
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .border(1.2.dp, Color(0xFF00F5D4).copy(0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Swipe, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Swipe Artwork: Switch Songs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Badge: Tap artwork
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.70f))
                .border(1.dp, Color(0xFFFF007F).copy(0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tap Artwork: Change Visualizer Theme", color = Color.White.copy(0.9f), fontSize = 10.5.sp)
            }
        }
    }
}

/**
 * Small glowing spotlight badge pointing directly to specific on-screen buttons.
 */
@Composable
private fun VisualButtonSpotlight(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.80f))
            .border(1.2.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
