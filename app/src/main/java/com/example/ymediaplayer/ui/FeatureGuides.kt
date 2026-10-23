package com.example.ymediaplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ymediaplayer.theme.LocalAppColors

/**
 * Interactive, one-time contextual feature & gesture guide for Video Player.
 * Visually spotlights edge gestures (brightness/volume), tap-to-seek, speed hold, and enhanced mode.
 */
@Composable
fun VideoPlayerFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // ─── Left Edge Indicator (Brightness) ──────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xFFFFB703), Color(0xFFFFB703).copy(alpha = 0.2f))), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(16.dp))
                Icon(Icons.Rounded.BrightnessMedium, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(22.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                Text("BRIGHTNESS", color = Color(0xFFFFB703), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text("Slide edge", color = Color.White.copy(alpha = 0.75f), fontSize = 9.5.sp)
            }
        }

        // ─── Right Edge Indicator (Volume) ─────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xFF00F5D4), Color(0xFF00F5D4).copy(alpha = 0.2f))), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(22.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                Text("VOLUME", color = Color(0xFF00F5D4), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text("Up to 200%", color = Color.White.copy(alpha = 0.75f), fontSize = 9.5.sp)
            }
        }

        // ─── Center Feature Explanation Card ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 420.dp)
                .padding(horizontal = 12.dp)
                .shadow(28.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = c.accentBlue.copy(0.4f))
                .clip(RoundedCornerShape(26.dp))
                .background(c.cardBgElevated)
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), c.accentBlue.copy(alpha = 0.25f))), RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* keep clicks on card from dismissing instantly */ }
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.accentBlue.copy(alpha = 0.2f))
                            .border(1.dp, c.accentBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Video Controls & Gestures",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                        Text(
                            text = "Quick interactive guide for first-time use",
                            fontSize = 11.5.sp,
                            color = c.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = c.glassBorder, thickness = 0.8.dp)

                // Feature items
                GuideFeatureRow(
                    icon = Icons.Rounded.TouchApp,
                    iconTint = Color(0xFF00F5D4),
                    title = "Double Tap Left / Right",
                    description = "Instantly skips 10 seconds backward or forward."
                )

                GuideFeatureRow(
                    icon = Icons.Rounded.Speed,
                    iconTint = Color(0xFFFFB703),
                    title = "Touch & Hold Anywhere",
                    description = "Activates 2x Turbo Speed playback smoothly."
                )

                GuideFeatureRow(
                    icon = Icons.Rounded.AutoAwesome,
                    iconTint = Color(0xFFFF007F),
                    title = "Enhanced Mode (Top Bar)",
                    description = "One-tap AI clarity, punchy saturation boost & natural warmth."
                )

                GuideFeatureRow(
                    icon = Icons.Rounded.LinearScale,
                    iconTint = c.accentBlue,
                    title = "Precision Timeline Scrub",
                    description = "Slide horizontally to preview and jump to any frame."
                )

                Spacer(Modifier.height(4.dp))

                // Bottom Button Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Skip Tour", color = c.textSecondary, fontSize = 13.5.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Got It, Play!", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

/**
 * Interactive, one-time contextual feature & gesture guide for Music Player.
 * Visually spotlights hidden slide gestures (volume/brightness), swipe to switch tracks,
 * visualizer theme switcher, equalizer, and sleep timer.
 */
@Composable
fun MusicPlayerFeatureGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // ─── Left Edge Indicator (Brightness) ──────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xFFFFB703), Color(0xFFFFB703).copy(alpha = 0.2f))), RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(16.dp))
                Icon(Icons.Rounded.BrightnessMedium, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(20.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                Text("BRIGHTNESS", color = Color(0xFFFFB703), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text("Slide edge", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            }
        }

        // ─── Right Edge Indicator (Volume) ─────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xFF00F5D4), Color(0xFF00F5D4).copy(alpha = 0.2f))), RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(20.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(2.dp))
                Text("VOLUME", color = Color(0xFF00F5D4), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text("Slide edge", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            }
        }

        // ─── Center Feature Explanation Card ───────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 420.dp)
                .padding(horizontal = 12.dp)
                .shadow(28.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = c.accentBlue.copy(0.4f))
                .clip(RoundedCornerShape(26.dp))
                .background(c.cardBgElevated)
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), c.accentBlue.copy(alpha = 0.25f))), RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent outside click */ }
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.accentBlue.copy(alpha = 0.2f))
                            .border(1.dp, c.accentBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Music Controls & Gestures",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                        Text(
                            text = "Discover hidden gestures & audio features",
                            fontSize = 11.5.sp,
                            color = c.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = c.glassBorder, thickness = 0.8.dp)

                // Feature items
                GuideFeatureRow(
                    icon = Icons.Rounded.Swipe,
                    iconTint = Color(0xFF00F5D4),
                    title = "Swipe Artwork Left / Right",
                    description = "Swipe the album art horizontally to switch tracks instantly."
                )

                GuideFeatureRow(
                    icon = Icons.Rounded.Palette,
                    iconTint = Color(0xFFFF007F),
                    title = "Tap Artwork or Visual Palette",
                    description = "Switch between 5 themes: Vinyl, Sonic Reactor, Cassette, Cyber Orb & 3D Glass."
                )

                GuideFeatureRow(
                    icon = Icons.Rounded.GraphicEq,
                    iconTint = Color(0xFFFFB703),
                    title = "5-Band Equalizer & Bass Boost",
                    description = "Tap the EQ button in the bottom bar for audiophile sound shaping."
                )

                GuideFeatureRow(
                    icon = Icons.Rounded.Bedtime,
                    iconTint = c.accentBlue,
                    title = "Sleep Timer (Top Bar)",
                    description = "Set auto shut-off countdown for relaxing bedtime listening."
                )

                Spacer(Modifier.height(4.dp))

                // Bottom Button Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Skip Tour", color = c.textSecondary, fontSize = 13.5.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Got It, Let's Play!", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.16f))
                .border(0.8.dp, iconTint.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = c.textSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
