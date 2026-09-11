package com.example.ymediaplayer.ui

import kotlin.math.pow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context
import android.net.Uri
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.theme.LocalAppColors
import java.util.Locale

/** Formats a millisecond duration as H:MM:SS or MM:SS. */
internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

/** Formats a byte count as B, KB, MB, or GB cleanly so small files never show as 0 MB. */
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), if (mb < 10) "%.1f MB" else "%.0f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}

/**
 * Builds an ImageRequest for video thumbnails that extracts a frame past timestamp 0
 * (e.g. 1 second or 10% of duration) to avoid blank white or black intro frames.
 */
internal fun buildVideoThumbnailRequest(context: Context, uri: Uri, durationMs: Long = 0L): ImageRequest {
    // If video duration is available and > 2s, seek to 1 second or 10% of duration
    val frameMicros = when {
        durationMs > 3000L -> 1_000_000L // 1 second in
        durationMs > 1000L -> (durationMs * 1000L) / 4 // 25% in
        else -> 500_000L // 0.5 sec default
    }
    return ImageRequest.Builder(context)
        .data(uri)
        .decoderFactory(VideoFrameDecoder.Factory())
        .videoFrameMicros(frameMicros)
        .build()
}

fun Modifier.bounceClick(
    scaleDown: Float = 0.91f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.62f, // Snappy tactile bounce
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable default grey ripple in favor of glass compression
            onClick = onClick
        )
}

/**
 * Apple Liquid Glass Material Modifier:
 * Applies a multi-pass optical material with incident light refraction on the top-left,
 * caustic translucency in the body, and bevelled specular highlights along the perimeter.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.White,
    alpha: Float = 0.14f,
    borderWidth: Dp = 0.dp,
    blurRadius: Dp = 20.dp
): Modifier {
    val base = this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    tint.copy(alpha = alpha * 1.5f), // Soft specular highlight
                    tint.copy(alpha = alpha * 0.7f), // Body translucency
                    Color.Black.copy(alpha = 0.45f)  // Depth absorption
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        )
    return if (borderWidth > 0.dp) {
        base.border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            shape = shape
        )
    } else {
        base
    }
}

/**
 * Sweeps a subtle specular light gleam across the glass surface.
 */
fun Modifier.liquidGlassShimmer(
    shape: Shape,
    durationMs: Int = 3200
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "liquidShimmer")
    val progress by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "shimmerProgress"
    )

    this.drawWithContent {
        drawContent()
        val width = size.width
        val shimmerOffset = width * progress
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.25f),
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                start = Offset(shimmerOffset - 120f, 0f),
                end = Offset(shimmerOffset + 120f, size.height)
            ),
            blendMode = BlendMode.Screen
        )
    }
}

/**
 * 5 Handcrafted Distinct Liquid Glass Themes
 */
enum class PlayerTheme(
    val id: String,
    val displayName: String,
    val description: String,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val highlightColor: Color,
    val orbBrush: Brush,
    val gestureBrush: Brush,
    val glowColor: Color,
    val previewGradient: List<Color>
) {
    CYBER(
        id = "CYBER",
        displayName = "Electric Cyber",
        description = "Apple iOS 18 Electric Cyan & Deep Indigo",
        primaryAccent = Color(0xFF0A84FF),
        secondaryAccent = Color(0xFFBF5AF2),
        highlightColor = Color(0xFF5AC8FA),
        orbBrush = Brush.radialGradient(
            listOf(Color(0xFF5AC8FA), Color(0xFF0A84FF), Color(0xFF003FCC))
        ),
        gestureBrush = Brush.verticalGradient(
            listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF0072FF))
        ),
        glowColor = Color(0xFF0A84FF),
        previewGradient = listOf(Color(0xFF0A84FF), Color(0xFF003FCC))
    ),
    SUNSET(
        id = "SUNSET",
        displayName = "Sunset Mirage",
        description = "Radiant Amber, Coral Flame & Golden Sunburst",
        primaryAccent = Color(0xFFFF9F0A),
        secondaryAccent = Color(0xFFFF453A),
        highlightColor = Color(0xFFFFD60A),
        orbBrush = Brush.radialGradient(
            listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF3B30))
        ),
        gestureBrush = Brush.verticalGradient(
            listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF453A))
        ),
        glowColor = Color(0xFFFF9F0A),
        previewGradient = listOf(Color(0xFFFF9F0A), Color(0xFFFF453A))
    ),
    AURORA(
        id = "AURORA",
        displayName = "Emerald Aurora",
        description = "Cyber Mint, Radiant Teal & Deep Forest",
        primaryAccent = Color(0xFF30D158),
        secondaryAccent = Color(0xFF00F5D4),
        highlightColor = Color(0xFF64FFDA),
        orbBrush = Brush.radialGradient(
            listOf(Color(0xFF64FFDA), Color(0xFF30D158), Color(0xFF00796B))
        ),
        gestureBrush = Brush.verticalGradient(
            listOf(Color(0xFF64FFDA), Color(0xFF00F5D4), Color(0xFF00B0FF))
        ),
        glowColor = Color(0xFF30D158),
        previewGradient = listOf(Color(0xFF30D158), Color(0xFF00796B))
    ),
    NEBULA(
        id = "NEBULA",
        displayName = "Amethyst Nebula",
        description = "Ultraviolet, Vivid Violet & Cosmic Neon Pink",
        primaryAccent = Color(0xFFBF5AF2),
        secondaryAccent = Color(0xFFFF2D55),
        highlightColor = Color(0xFFE056FD),
        orbBrush = Brush.radialGradient(
            listOf(Color(0xFFE056FD), Color(0xFFBF5AF2), Color(0xFF5E17EB))
        ),
        gestureBrush = Brush.verticalGradient(
            listOf(Color(0xFFFF2D55), Color(0xFFBF5AF2), Color(0xFF7000FF))
        ),
        glowColor = Color(0xFFBF5AF2),
        previewGradient = listOf(Color(0xFFBF5AF2), Color(0xFF5E17EB))
    ),
    ONYX(
        id = "ONYX",
        displayName = "Titanium Onyx",
        description = "Frosted Platinum, Stealth Chrome & Silver",
        primaryAccent = Color(0xFFF2F2F7),
        secondaryAccent = Color(0xFF8E8E93),
        highlightColor = Color(0xFFFFFFFF),
        orbBrush = Brush.radialGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFD1D1D6), Color(0xFF3A3A3C))
        ),
        gestureBrush = Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFF8E8E93), Color(0xFF2C2C2E))
        ),
        glowColor = Color(0xFFE5E5EA),
        previewGradient = listOf(Color(0xFFFFFFFF), Color(0xFF48484A))
    );

    companion object {
        fun fromId(id: String): PlayerTheme {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: CYBER
        }
    }
}

/**
 * Calculates seek target position bounded by [0, duration].
 * If duration is unset or unknown (<= 0), forward seeking is prevented to avoid negative seeks.
 */
internal fun calculateSeekTarget(currentPosition: Long, deltaMs: Long, duration: Long): Long {
    if (deltaMs > 0 && duration <= 0L) {
        return currentPosition
    }
    val maxBound = if (duration > 0L) duration else Long.MAX_VALUE
    return (currentPosition + deltaMs).coerceIn(0L, maxBound)
}

@Composable
fun VideoActionItem(
    icon: ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SwipeableVideoRow(
    video: VideoItem,
    isFavorite: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onFavoriteToggle: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.glassBgNested)
            .border(0.5.dp, if (isSelected) c.accentBlue else c.glassBorder, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (inSelectionMode) onSelectToggle()
                    else onClick()
                },
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (inSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = c.accentBlue)
                )
                Spacer(Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = remember(video.uri, video.duration) {
                        buildVideoThumbnailRequest(context, video.uri, video.duration)
                    },
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        formatTime(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (progress > 0 && video.duration > 0) {
                    val progressRatio = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .height(3.dp)
                                .background(c.accentBlue)
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title.substringBeforeLast("."),
                    fontSize = 15.sp,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatSize(video.size), fontSize = 12.sp, color = c.textSecondary)
                }
            }

            if (!inSelectionMode) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4D6D) else c.textSecondary
                    )
                }
                IconButton(onClick = onMenuClick) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = c.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Reads the device's system brightness and converts it to a perceived slider value (0.01f..1.0f).
 */
internal fun getSystemBrightness(context: Context): Float {
    return try {
        val sysVal = android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
            128
        )
        val linear = (sysVal.toFloat() / 255f).coerceIn(0.01f, 1f)
        linear.toDouble().pow(1.0 / 2.2).toFloat().coerceIn(0.01f, 1f)
    } catch (_: Exception) {
        0.5f
    }
}

/**
 * Converts linear slider percentage (0.01f..1.0f) to human-eye perceptual hardware brightness.
 * Uses a gamma 2.2 power curve so that:
 * - 1% to 20% increases gently and proportionally instead of making a drastic jump.
 * - 20% to 100% continues increasing with equal perceived luminance steps throughout.
 */
internal fun sliderToScreenBrightness(sliderPct: Float): Float {
    val clamped = sliderPct.coerceIn(0.01f, 1f)
    return clamped.toDouble().pow(2.2).toFloat().coerceIn(0.005f, 1f)
}

/**
 * Converts hardware screenBrightness value (0.005f..1.0f) back to perceived UI slider percentage (0.01f..1.0f).
 */
internal fun screenBrightnessToSlider(brightness: Float): Float {
    val clamped = brightness.coerceIn(0.005f, 1f)
    return clamped.toDouble().pow(1.0 / 2.2).toFloat().coerceIn(0.01f, 1f)
}

