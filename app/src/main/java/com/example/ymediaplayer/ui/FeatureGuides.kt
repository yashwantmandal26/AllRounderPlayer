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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Utility extension to adjust color saturation via HSV space.
 */
internal fun Color.adjustSaturation(factor: Float): Color {
    if (factor == 1.0f) return this
    val argb = this.toArgb()
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    if (factor > 1.0f) {
        // Amplify richness, luminescence and vibrancy for high saturation boost
        val boost = ((factor - 1.0f) * 0.40f).coerceIn(0f, 0.60f)
        hsv[2] = (hsv[2] * (1f + boost)).coerceIn(0f, 1f)
    } else if (factor < 1.0f) {
        // Soft pastel look for low saturation
        hsv[2] = (hsv[2] * (0.85f + 0.15f * factor)).coerceIn(0f, 1f)
    }
    val adjusted = android.graphics.Color.HSVToColor(hsv)
    return Color(adjusted).copy(alpha = this.alpha)
}

// ═══════════════════════════════════════════════════════════════════════════════
// 1. HOME PAGE FEATURE GUIDE (Dual-Engine: Video + Music + Customization)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Visual spotlight walkthrough for the Home Page.
 * Introduces the Dual-Engine architecture (Video Library vs Music Studio),
 * the zero-reloading bottom switcher, persistent floating mini-player,
 * and top-bar Theme & Search tools.
 */
@Composable
fun HomePageFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 2

    val transition = rememberInfiniteTransition(label = "homeGuideAnim")
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.70f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (currentStep < totalSteps - 1) currentStep++ else onDismiss()
                }
            )
    ) {
        // Top Right: Skip Pill
        GuideSkipButton(
            onSkip = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
        )

        // ─── STEP 0: Dual-Engine Switch & Persistent Mini-Player ───────────────
        if (currentStep == 0) {
            val handSlide = sin(cycleProgress * Math.PI.toFloat())

            // Spotlight bottom switcher and mini-player
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Feature Card: Persistent Playback
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF161722).copy(alpha = 0.94f))
                            .border(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF))), RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F5D4).copy(alpha = 0.20f))
                                    .border(1.2.dp, Color(0xFF00F5D4), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Persistent Mini-Player", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text("Music plays continuously without stopping while you browse video folders or change settings.", color = Color.White.copy(0.75f), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Dual Engine Highlights
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Video Engine Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1C29).copy(alpha = 0.90f))
                                .border(1.2.dp, Color(0xFFFFB703).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Video Engine", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Continue Watching, Grid/List & Folder Pinning", color = Color.White.copy(0.7f), fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }

                        // Music Engine Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1C29).copy(alpha = 0.90f))
                                .border(1.2.dp, Color(0xFF00F5D4).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Music Studio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("5 Dynamic Visualizers, EQ, Playlists & Artists", color = Color.White.copy(0.7f), fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Pointer to Bottom Switcher
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer { translationY = handSlide * 8f }
                    ) {
                        Text("Instant Bottom Tab Switch · Zero Reloading", color = Color(0xFF00F5D4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // ─── STEP 1: Pro Customization & Quick Tools ───────────────────────────
        if (currentStep == 1) {
            val arrowBounce = sin(cycleProgress * Math.PI.toFloat())

            // Spotlight Top Bar Tools
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 58.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Arrow pointing up to top bar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer { translationY = -arrowBounce * 8f }
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("Top Bar Quick Actions", color = Color(0xFFFF007F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(14.dp))

                    // Card 1: Theme Studio
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF161722).copy(alpha = 0.94f))
                            .border(1.3.dp, Color(0xFFFF007F).copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF007F).copy(alpha = 0.20f))
                                    .border(1.dp, Color(0xFFFF007F), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Palette, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Theme Studio & Ambient Glow", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text("Choose from 6 neon themes, OLED True Black, and customize background glow spread & saturation.", color = Color.White.copy(0.75f), fontSize = 11.5.sp, lineHeight = 15.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Card 2: Global Instant Search
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF161722).copy(alpha = 0.94f))
                            .border(1.3.dp, Color(0xFF00F5D4).copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F5D4).copy(alpha = 0.20f))
                                    .border(1.dp, Color(0xFF00F5D4), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Instant Global Search", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text("Filter any folder, video or song instantly with keyword search & sort options.", color = Color.White.copy(0.75f), fontSize = 11.5.sp, lineHeight = 15.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Card 3: Deep Settings
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF161722).copy(alpha = 0.94f))
                            .border(1.3.dp, Color(0xFFFFB703).copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB703).copy(alpha = 0.20f))
                                    .border(1.dp, Color(0xFFFFB703), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Deep Customization Settings", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text("Fine-tune Audio EQ, Subtitle Sync, Dynamic Video Ambilight, and Library layout.", color = Color.White.copy(0.75f), fontSize = 11.5.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        // ─── Bottom Navigation Bar ─────────────────────────────────────────────
        GuideNavigationBar(
            currentStep = currentStep,
            totalSteps = totalSteps,
            stepTitle = if (currentStep == 0) "Dual-Engine Switch & Mini Player" else "Themes, Search & Pro Tools",
            accentColor = if (currentStep == 0) Color(0xFF00F5D4) else Color(0xFFFF007F),
            onBack = if (currentStep > 0) { { currentStep-- } } else null,
            onNext = {
                if (currentStep < totalSteps - 1) currentStep++ else onDismiss()
            },
            onSkip = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. VIDEO PLAYER FEATURE GUIDE (Edge Slides & Quick Pro Gestures)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Visual "Ghost Hand" Gesture Simulation for Video Player.
 * Step 1: Smooth left-edge brightness slide & right-edge volume slide with slow, readable timing.
 * Step 2: Double-tap seek ripples (+/-10s), 2x hold turbo speed, pinch-to-zoom & Enhanced Mode spotlight.
 */
@Composable
fun VideoPlayerFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 2

    // Smooth, relaxed loop for gesture motion (3.8 seconds per cycle)
    val transition = rememberInfiniteTransition(label = "videoCycleAnim")
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    // Smooth sinusoidal motion with hold at top
    val slideTravel = if (cycleProgress < 0.60f) {
        val p = (cycleProgress / 0.60f).coerceIn(0f, 1f)
        (0.5f - 0.5f * cos(p * Math.PI)).toFloat()
    } else {
        val p = ((cycleProgress - 0.60f) / 0.40f).coerceIn(0f, 1f)
        (0.5f + 0.5f * cos(p * Math.PI)).toFloat()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (currentStep < totalSteps - 1) currentStep++ else onDismiss()
                }
            )
    ) {
        // Top Right: Skip Pill
        GuideSkipButton(
            onSkip = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
        )

        // ─── STEP 0: Left & Right Edge Gestures ────────────────────────────────
        if (currentStep == 0) {
            // Left Edge: Brightness Simulation
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VisualGestureHudBar(
                        icon = Icons.Rounded.BrightnessMedium,
                        percentage = 0.25f + slideTravel * 0.60f,
                        label = "Brightness",
                        accentColor = Color(0xFFFFB703),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFFFFB703), Color(0xFFFF8800)))
                    )

                    Spacer(Modifier.width(18.dp))

                    AnimatedGhostTouchPoint(
                        yTravelFraction = slideTravel,
                        accentColor = Color(0xFFFFB703),
                        label = "Slide Edge · Brightness"
                    )
                }
            }

            // Right Edge: Volume Simulation
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedGhostTouchPoint(
                        yTravelFraction = slideTravel,
                        accentColor = Color(0xFF00F5D4),
                        label = "Slide Edge · Volume"
                    )

                    Spacer(Modifier.width(18.dp))

                    VisualGestureHudBar(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        percentage = 0.30f + slideTravel * 0.55f,
                        label = "Volume",
                        accentColor = Color(0xFF00F5D4),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFF00F5D4), Color(0xFF0096C7)))
                    )
                }
            }
        }

        // ─── STEP 1: Double-Tap Seek, 2x Hold & Enhanced Mode ──────────────────
        if (currentStep == 1) {
            val ripplePulse = (cycleProgress * 2f).coerceIn(0f, 1f)

            // Double-Tap Seek Right Side (+10s)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                VisualDoubleTapRipple(
                    isForward = true,
                    progress = ripplePulse
                )
            }

            // Double-Tap Seek Left Side (-10s)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                VisualDoubleTapRipple(
                    isForward = false,
                    progress = ripplePulse
                )
            }

            // Center: Touch & Hold 2x Turbo Speed
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                VisualHoldSpeedPill(progress = ripplePulse)
            }

            // Top Right Spotlight: Enhanced Mode
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 66.dp, end = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                VisualEnhancedModeSpotlight()
            }

            // Bottom Center: Pinch to Zoom hint
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color(0xFF00F5D4).copy(0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Pinch anywhere to Zoom & Pan video up to 400%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ─── Bottom Navigation Bar ─────────────────────────────────────────────
        GuideNavigationBar(
            currentStep = currentStep,
            totalSteps = totalSteps,
            stepTitle = if (currentStep == 0) "Edge Gestures: Brightness & Volume" else "Quick Gestures & Pro Features",
            accentColor = if (currentStep == 0) Color(0xFFFFB703) else Color(0xFF00F5D4),
            onBack = if (currentStep > 0) { { currentStep-- } } else null,
            onNext = {
                if (currentStep < totalSteps - 1) currentStep++ else onDismiss()
            },
            onSkip = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. MUSIC PLAYER FEATURE GUIDE (Edge Slides, Artwork Gestures & Visualizers)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Visual "Ghost Hand" Gesture Simulation for Music Player.
 * Step 1: Smooth edge slides (Brightness & Volume) + horizontal artwork swipe to skip tracks.
 * Step 2: Tap artwork to switch between 5 visualizers + Equalizer & Sleep Timer spotlight.
 */
@Composable
fun MusicPlayerFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 2

    val transition = rememberInfiniteTransition(label = "musicCycleAnim")
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    val slideTravel = if (cycleProgress < 0.60f) {
        val p = (cycleProgress / 0.60f).coerceIn(0f, 1f)
        (0.5f - 0.5f * cos(p * Math.PI)).toFloat()
    } else {
        val p = ((cycleProgress - 0.60f) / 0.40f).coerceIn(0f, 1f)
        (0.5f + 0.5f * cos(p * Math.PI)).toFloat()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (currentStep < totalSteps - 1) currentStep++ else onDismiss()
                }
            )
    ) {
        // Top Right: Skip Pill
        GuideSkipButton(
            onSkip = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
        )

        // ─── STEP 0: Edge Slides & Artwork Swipe ───────────────────────────────
        if (currentStep == 0) {
            // Left Edge: Brightness Slide
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VisualGestureHudBar(
                        icon = Icons.Rounded.BrightnessMedium,
                        percentage = 0.25f + slideTravel * 0.60f,
                        label = "Brightness",
                        accentColor = Color(0xFFFFB703),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFFFFB703), Color(0xFFFF8800)))
                    )

                    Spacer(Modifier.width(16.dp))

                    AnimatedGhostTouchPoint(
                        yTravelFraction = slideTravel,
                        accentColor = Color(0xFFFFB703),
                        label = "Slide Edge"
                    )
                }
            }

            // Right Edge: Volume Slide
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedGhostTouchPoint(
                        yTravelFraction = slideTravel,
                        accentColor = Color(0xFF00F5D4),
                        label = "Slide Edge"
                    )

                    Spacer(Modifier.width(16.dp))

                    VisualGestureHudBar(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        percentage = 0.30f + slideTravel * 0.55f,
                        label = "Volume",
                        accentColor = Color(0xFF00F5D4),
                        glowBrush = Brush.verticalGradient(listOf(Color(0xFF00F5D4), Color(0xFF0096C7)))
                    )
                }
            }

            // Center Artwork Swipe Simulation
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                VisualArtworkSwipeDemonstration(swipeProgress = cycleProgress)
            }
        }

        // ─── STEP 1: 5 Visualizers, Equalizer & Sleep Timer ───────────────────
        if (currentStep == 1) {
            val pulse = sin(cycleProgress * Math.PI.toFloat())

            // Center: Tap Artwork to Switch Visualizer
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Tap Ripple Hand
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(16.dp, CircleShape, ambientColor = Color(0xFFFF007F), spotColor = Color(0xFFFF007F))
                            .clip(CircleShape)
                            .background(Color(0xFFFF007F).copy(alpha = 0.28f + pulse * 0.2f))
                            .border(2.dp, Color(0xFFFF007F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.5.dp, Color(0xFFFF007F).copy(0.8f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tap Artwork: Switch Visualizer Theme", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Sonic Reactor · Cyber Orb · Cassette · Vinyl · Card", color = Color.White.copy(0.75f), fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Bottom Spotlight: Equalizer Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp, start = 36.dp)
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

        // ─── Bottom Navigation Bar ─────────────────────────────────────────────
        GuideNavigationBar(
            currentStep = currentStep,
            totalSteps = totalSteps,
            stepTitle = if (currentStep == 0) "Edge Gestures & Swipe Tracks" else "Visualizers, Equalizer & Timer",
            accentColor = if (currentStep == 0) Color(0xFF00F5D4) else Color(0xFFFF007F),
            onBack = if (currentStep > 0) { { currentStep-- } } else null,
            onNext = {
                if (currentStep < totalSteps - 1) currentStep++ else onDismiss()
            },
            onSkip = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHARED NAVIGATION & CONTROL PILLS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Modern interactive bottom bar for the feature guides.
 * Displays step indicators, step title, Back button, and Next / Got It button.
 */
@Composable
private fun GuideNavigationBar(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String,
    accentColor: Color,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF14141E).copy(alpha = 0.95f))
            .border(1.2.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Step progress dots & Step Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dots
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 0 until totalSteps) {
                        val isCurrent = i == currentStep
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isCurrent) 18.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) accentColor else Color.White.copy(alpha = 0.25f))
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = "${currentStep + 1}/$totalSteps · $stepTitle",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // Right: Navigation Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button (if on step > 0)
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { onBack() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }

                // Next or Got It Button
                val isLast = currentStep >= totalSteps - 1
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor)
                        .clickable { onNext() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLast) "Got It" else "Next",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top-Right Skip button pill with close icon.
 */
@Composable
private fun GuideSkipButton(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable { onSkip() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Skip", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Close, contentDescription = "Skip", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ANIMATION SUB-COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

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
    val travelPx = 130.dp
    val currentY = travelPx * (0.5f - yTravelFraction)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.offset(y = currentY)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(12.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.28f))
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.75f))
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
 * Center album artwork swipe demonstration for Music Player.
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

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.82f))
                .border(1.2.dp, Color(0xFF00F5D4).copy(0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Swipe, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Swipe Artwork: Switch Songs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
