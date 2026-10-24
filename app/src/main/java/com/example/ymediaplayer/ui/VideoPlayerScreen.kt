@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.ui

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.R
import com.example.ymediaplayer.player.VideoPlaybackManager
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.example.ymediaplayer.player.OnlineSubtitleManager
import com.example.ymediaplayer.player.OnlineSubtitle
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.view.LayoutInflater
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import androidx.compose.foundation.lazy.items
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.ContentUris
import android.net.Uri
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.FileManager
import com.example.ymediaplayer.data.FileOperationResult
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
import kotlinx.coroutines.launch
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
private const val ACTION_VIDEO_CLOSE = "com.example.ymediaplayer.ACTION_VIDEO_CLOSE"
private const val ACTION_PIP_PLAY_PAUSE = "com.example.ymediaplayer.ACTION_PIP_PLAY_PAUSE"
private const val ACTION_PIP_PREV = "com.example.ymediaplayer.ACTION_PIP_PREV"
private const val ACTION_PIP_NEXT = "com.example.ymediaplayer.ACTION_PIP_NEXT"
private const val ACTION_PIP_BG_PLAY = "com.example.ymediaplayer.ACTION_PIP_BG_PLAY"
private const val ACTION_PIP_REWIND = "com.example.ymediaplayer.ACTION_PIP_REWIND"
private const val ACTION_PIP_FORWARD = "com.example.ymediaplayer.ACTION_PIP_FORWARD"

private val QuickActionBg = Color(0x33FFFFFF)       // Circular quick action button background
private val QuickActionBorder = Color(0x44FFFFFF)   // Circular quick action border

// ─── Video Sound Profiles / Audio Modes (Acoustically Tuned for Video & Movies) ───
enum class VideoSoundMode(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val bands: List<Float>, // 50Hz, 250Hz, 1kHz, 4kHz, 12kHz in dB
    val bassBoost: Float,   // 0.0f .. 1.0f
    val virtualizer: Float, // 0.0f .. 1.0f
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    NORMAL(
        id = "NORMAL",
        displayName = "Original Audio",
        shortName = "Normal",
        description = "Natural unaltered audio with flat acoustic response",
        bands = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        bassBoost = 0.0f,
        virtualizer = 0.0f,
        icon = Icons.Rounded.MusicNote
    ),
    CINEMA(
        id = "CINEMA",
        displayName = "Cinema",
        shortName = "Cinema",
        description = "Warm bass and clear detail; spatial effects depend on your device",
        bands = listOf(4.0f, 1.5f, 0.0f, 3.5f, 5.0f),
        bassBoost = 0.40f,
        virtualizer = 0.40f,
        icon = Icons.Rounded.Theaters
    ),
    CLEAR_VOICE(
        id = "CLEAR_VOICE",
        displayName = "Dialogue",
        shortName = "Voice",
        description = "Cuts low-end boom to make speech & dialogue crisp and front-and-center",
        bands = listOf(-4.0f, -1.0f, 6.0f, 4.5f, 1.5f),
        bassBoost = 0.0f,
        virtualizer = 0.10f,
        icon = Icons.Rounded.RecordVoiceOver
    ),
    ACTION_PUNCH(
        id = "ACTION_PUNCH",
        displayName = "Bass",
        shortName = "Bass",
        description = "High-energy sub-bass punch tuned for explosions, battles & impact",
        bands = listOf(4.0f, 2.5f, -1.0f, 1.5f, 2.0f),
        bassBoost = 0.30f,
        virtualizer = 0.25f,
        icon = Icons.Rounded.Bolt
    ),
    NIGHT_MODE(
        id = "NIGHT_MODE",
        displayName = "Night Mode",
        shortName = "Night",
        description = "Reduced bass and clearer dialogue for quieter listening",
        bands = listOf(-5.0f, -2.0f, 2.0f, 1.0f, -1.0f),
        bassBoost = 0.0f,
        virtualizer = 0.05f,
        icon = Icons.Rounded.NightsStay
    );

    fun next(): VideoSoundMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromId(id: String?): VideoSoundMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: NORMAL
        }
    }
}

// ─── Video Picture Profiles (Calibrated Color Enhancement without Sharpness Alteration) ───
enum class VideoColorProfile(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val contrast: Float,
    val saturation: Float,
    val brightness: Float,
    val warmth: Float,
    val icon: ImageVector
) {
    NORMAL(
        id = "NORMAL",
        displayName = "Natural",
        shortName = "Natural",
        description = "Untouched original video colors & natural dynamic range",
        contrast = 1.0f,
        saturation = 1.0f,
        brightness = 0.0f,
        warmth = 0.0f,
        icon = Icons.Rounded.Tune
    ),
    HDR_DYNAMIC(
        id = "HDR_DYNAMIC",
        displayName = "Dynamic",
        shortName = "Dynamic",
        description = "Brighter picture with gentle contrast and richer color",
        contrast = 1.04f,
        saturation = 1.12f,
        brightness = 0.04f,
        warmth = 0.0f,
        icon = Icons.Rounded.HdrOn
    ),
    VIVID_POP(
        id = "VIVID_POP",
        displayName = "Vivid Pop",
        shortName = "Vivid",
        description = "Vibrant rich colors & punchy clarity without crushing details",
        contrast = 1.07f,
        saturation = 1.18f,
        brightness = 0.02f,
        warmth = 0.0f,
        icon = Icons.Rounded.AutoFixHigh
    ),
    NIGHT_CLARITY(
        id = "NIGHT_CLARITY",
        displayName = "Low Light",
        shortName = "Night",
        description = "Boosts dark scenes & shadows so hidden video details become visible",
        contrast = 1.02f,
        saturation = 1.06f,
        brightness = 0.07f,
        warmth = 0.0f,
        icon = Icons.Rounded.BrightnessMedium
    );

    fun next(): VideoColorProfile {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromId(id: String?): VideoColorProfile {
            return when (id?.uppercase()) {
                "HDR_DYNAMIC" -> HDR_DYNAMIC
                "VIVID_POP", "VIVID_PUNCH" -> VIVID_POP
                "NIGHT_CLARITY", "AMOLED_CONTRAST" -> NIGHT_CLARITY
                "CINEMA_WARM" -> HDR_DYNAMIC
                "NORMAL" -> NORMAL
                else -> entries.find { it.id.equals(id, ignoreCase = true) } ?: NORMAL
            }
        }
    }
}

// ─── Video Sharpness Levels (AGSL Luminance Unsharp Mask) ───
enum class VideoSharpnessLevel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val intensity: Float,
    val icon: ImageVector
) {
    OFF(
        id = "OFF",
        displayName = "Sharpness: Off",
        shortName = "Off",
        description = "Original unaltered video sharpness",
        intensity = 0.0f,
        icon = Icons.Rounded.BlurOff
    ),
    SUBTLE(
        id = "SUBTLE",
        displayName = "Sharpness: 5% (Subtle)",
        shortName = "5%",
        description = "Delicate 5% edge clarity polish without noise or harsh halos",
        intensity = 0.05f,
        icon = Icons.Rounded.Details
    ),
    ENHANCED(
        id = "ENHANCED",
        displayName = "Sharpness: 15% (Enhanced)",
        shortName = "15%",
        description = "Crisp definition enhancing facial textures, hair & details",
        intensity = 0.15f,
        icon = Icons.Rounded.HighQuality
    ),
    ULTRA(
        id = "ULTRA",
        displayName = "Sharpness: 25% (Ultra)",
        shortName = "25%",
        description = "High-definition edge punch & fine detail texture recovery",
        intensity = 0.25f,
        icon = Icons.Rounded.CenterFocusStrong
    );

    fun next(): VideoSharpnessLevel {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromId(id: String?): VideoSharpnessLevel {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: OFF
        }
    }
}

private const val AGSL_LUMA_UNSHARP_SHADER = """
    uniform shader image;
    uniform float sharpness;

    half4 main(float2 fragCoord) {
        half4 c = image.eval(fragCoord);
        if (sharpness <= 0.005) {
            return c;
        }
        
        // Adaptive multi-tap kernel sampling across bilinear filter space
        const float step = 1.35;
        const float diag = 0.95;
        
        half4 up    = image.eval(fragCoord + float2(0.0, -step));
        half4 down  = image.eval(fragCoord + float2(0.0, step));
        half4 left  = image.eval(fragCoord + float2(-step, 0.0));
        half4 right = image.eval(fragCoord + float2(step, 0.0));
        
        half4 ul    = image.eval(fragCoord + float2(-diag, -diag));
        half4 ur    = image.eval(fragCoord + float2(diag, -diag));
        half4 dl    = image.eval(fragCoord + float2(-diag, diag));
        half4 dr    = image.eval(fragCoord + float2(diag, diag));
        
        const half3 lumaWeight = half3(0.2126, 0.7152, 0.0722);
        half cLuma      = dot(c.rgb, lumaWeight);
        half cardLuma   = dot(up.rgb + down.rgb + left.rgb + right.rgb, lumaWeight);
        half cornerLuma = dot(ul.rgb + ur.rgb + dl.rgb + dr.rgb, lumaWeight);
        
        // 9-tap Laplacian weighted filter (cardinals weighted 1.0, corners weighted 0.707)
        half blurLuma = (cardLuma + cornerLuma * 0.7071) / (4.0 + 4.0 * 0.7071);
        half lumaDiff = cLuma - blurLuma;
        half absDiff  = abs(lumaDiff);
        
        // Soft noise gate to avoid amplifying sensor grain/compression artifacts
        half edgeMask = smoothstep(0.003, 0.025, absDiff);
        // Anti-ringing boundary damping for high-contrast edges
        half boundaryDamping = clamp(1.0 - (absDiff - 0.05) * 8.0, 0.35, 1.0);
        // Scaled high-frequency boost clamped strictly to sharpness limit (e.g. +/- 0.05 for 5%)
        half boost = clamp(lumaDiff * (sharpness * 5.0) * edgeMask * boundaryDamping, -sharpness, sharpness);
        half3 sharpRgb = c.rgb + half3(boost);
        
        return half4(clamp(sharpRgb, half3(0.0), half3(1.0)), c.a);
    }
"""

// Enhanced Mode SDR spatial enhancement: multiscale texture definition, dynamic contrast and intelligent vibrance.
// Internal visibility lets device tests compile the actual production shader.
internal const val SMART_CLARITY_SHADER = """
    uniform shader image;
    uniform float aiStrength;

    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        if (aiStrength <= 0.01 || c.a <= 0.001) return c;
        const half3 lumaW = half3(0.2126, 0.7152, 0.0722);
        half y = dot(c.rgb, lumaW);
        half n = dot(image.eval(coord + float2(0.0, -1.0)).rgb, lumaW);
        half s = dot(image.eval(coord + float2(0.0,  1.0)).rgb, lumaW);
        half w = dot(image.eval(coord + float2(-1.0, 0.0)).rgb, lumaW);
        half e = dot(image.eval(coord + float2( 1.0, 0.0)).rgb, lumaW);
        half nw = dot(image.eval(coord + float2(-1.0, -1.0)).rgb, lumaW);
        half ne = dot(image.eval(coord + float2( 1.0, -1.0)).rgb, lumaW);
        half sw = dot(image.eval(coord + float2(-1.0,  1.0)).rgb, lumaW);
        half se = dot(image.eval(coord + float2( 1.0,  1.0)).rgb, lumaW);
        half nearMean = (n + s + w + e) * 0.25;
        half wideMean = (dot(image.eval(coord + float2(-2.0, 0.0)).rgb, lumaW)
                       + dot(image.eval(coord + float2( 2.0, 0.0)).rgb, lumaW)
                       + dot(image.eval(coord + float2(0.0, -2.0)).rgb, lumaW)
                       + dot(image.eval(coord + float2(0.0,  2.0)).rgb, lumaW)) * 0.25;
        half localMin = min(y, min(min(n, s), min(w, e)));
        half localMax = max(y, max(max(n, s), max(w, e)));
        half range = localMax - localMin;
        half fine = y - (nearMean * 0.75 + (nw + ne + sw + se) * 0.0625);
        // Gate tiny fluctuations and isolated noise; dark regions get less sharpening.
        half edge = max(abs(e - w), abs(s - n));
        half coherence = smoothstep(0.004, 0.045, edge);
        half noiseGate = smoothstep(0.003, 0.020, abs(fine));
        half shadowGuard = 0.50 + 0.50 * smoothstep(0.02, 0.16, y);
        half edgeGuard = 1.0 - 0.45 * smoothstep(0.15, 0.55, range);
        // Multiscale detail extraction: crisp texture, facial features and fabric definition
        half detail = (fine * 1.85 + (nearMean - wideMean) * 0.55)
                    * noiseGate * coherence * shadowGuard * edgeGuard * aiStrength;
        // Bounded extrema strictly within local envelope - zero halo, zero whitening
        half detailedY = clamp(y + clamp(detail, -0.09, 0.09), localMin, localMax);
        // Zero-DC micro-contrast curve: keeps blacks deep and highlights crisp without lifting shadow luma
        half microContrast = (y - 0.5) * y * (1.0 - y) * 0.20 * aiStrength;
        half targetY = clamp(detailedY + microContrast, 0.0, c.a);
        half3 rgb = c.rgb + half3(targetY - y);
        half chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
        half skin = (c.r > c.g && c.g > c.b && c.r - c.b > 0.06 && c.r - c.g < 0.32) ? 0.35 : 1.0;
        half vibrance = 0.15 * (1.0 - smoothstep(0.12, 0.70, chroma)) * skin * aiStrength;
        rgb = mix(half3(targetY), rgb, 1.0 + vibrance);
        return half4(clamp(rgb, half3(0.0), half3(c.a)), c.a);
    }
"""

fun buildVideoColorMatrix(contrast: Float, saturation: Float, brightness: Float, warmth: Float): ColorMatrix {
    val matrix = ColorMatrix()
    matrix.setSaturation(saturation.coerceIn(0.0f, 2.5f))
    
    val c = contrast.coerceIn(0.5f, 2.5f)
    val b = brightness.coerceIn(-0.5f, 0.5f)
    val offset = (-0.5f * c + 0.5f + b) * 255.0f
    val contrastMatrix = ColorMatrix(floatArrayOf(
        c, 0f, 0f, 0f, offset,
        0f, c, 0f, 0f, offset,
        0f, 0f, c, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    ))
    matrix.postConcat(contrastMatrix)
    
    val w = warmth.coerceIn(-1.0f, 1.0f)
    if (Math.abs(w) > 0.005f) {
        val rScale = 1.0f + w * 0.15f
        val gScale = 1.0f + w * 0.04f
        val bScale = 1.0f - w * 0.15f
        val warmthMatrix = ColorMatrix(floatArrayOf(
            rScale, 0f, 0f, 0f, 0f,
            0f, gScale, 0f, 0f, 0f,
            0f, 0f, bScale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(warmthMatrix)
    }
    return matrix
}

// Shadow lift tapers toward white rather than adding a flat offset to every pixel.
private const val AGSL_PICTURE_SHADER = """
    uniform shader image;
    uniform float contrast;
    uniform float saturation;
    uniform float brightness;
    uniform float warmth;
    half4 main(float2 coord) {
        half4 source = image.eval(coord);
        half3 rgb = source.rgb;
        half luma = dot(rgb, half3(0.2126, 0.7152, 0.0722));
        half lift = brightness * (1.0 - luma) * (1.0 - luma);
        rgb += half3(lift);
        // A bounded S-curve increases separation without clipping highlights.
        rgb += (contrast - 1.0) * (rgb - half3(0.5)) * 4.0 * rgb * (half3(1.0) - rgb);
        luma = dot(rgb, half3(0.2126, 0.7152, 0.0722));
        half chroma = max(rgb.r, max(rgb.g, rgb.b)) - min(rgb.r, min(rgb.g, rgb.b));
        half amount = 1.0 + (saturation - 1.0) * (1.0 - chroma);
        rgb = mix(half3(luma), rgb, amount);
        rgb *= half3(1.0 + warmth * 0.15, 1.0 + warmth * 0.04, 1.0 - warmth * 0.15);
        return half4(clamp(rgb, half3(0.0), half3(1.0)), source.a);
    }
"""

fun createVideoRenderEffect(
    contrast: Float,
    saturation: Float,
    brightness: Float,
    warmth: Float,
    sharpness: Float,
    isSuperAi: Boolean = false
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return null
    }

    if (isSuperAi) {
        // Enhanced Mode:
        // 1. Saturation boosted (+14%) for punchy, vibrant cinematic colors
        // 2. Subtle warmth boost of +2% (+0.02f) for pleasant skin tones & golden depth
        // 3. Crisp micro-contrast boost (+3%) with strictly ZERO whitening (brightness = 0.0f)
        val effectiveWarmth = (warmth + 0.02f).coerceIn(-1.0f, 1.0f)
        val effectiveSaturation = (saturation * 1.14f).coerceIn(0.5f, 2.5f)
        val effectiveContrast = (contrast * 1.03f).coerceIn(0.5f, 2.5f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val aiShader = RuntimeShader(SMART_CLARITY_SHADER)
                aiShader.setFloatUniform("aiStrength", 1.30f)
                val aiEffect = RenderEffect.createRuntimeShaderEffect(aiShader, "image")

                val cm = buildVideoColorMatrix(
                    contrast = effectiveContrast,
                    saturation = effectiveSaturation,
                    brightness = 0.0f,
                    warmth = effectiveWarmth
                )
                val colorEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
                return RenderEffect.createChainEffect(colorEffect, aiEffect)
            } catch (_: Throwable) {}
        }

        // Fallback for API 31-32: Calibrated Enhanced Mode Color Matrix (crisp contrast + punchy saturation + 2% warmth, strictly zero whitening)
        val aiCm = buildVideoColorMatrix(
            contrast = 1.05f,
            saturation = 1.15f,
            brightness = 0.0f,
            warmth = effectiveWarmth
        )
        return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(aiCm))
    }

    val isColorModified = Math.abs(contrast - 1.0f) > 0.01f ||
            Math.abs(saturation - 1.0f) > 0.01f ||
            Math.abs(brightness) > 0.005f ||
            Math.abs(warmth) > 0.005f

    val colorEffect = if (isColorModified) {
        val fallback = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(
            buildVideoColorMatrix(contrast, saturation, brightness, warmth)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val shader = RuntimeShader(AGSL_PICTURE_SHADER)
                shader.setFloatUniform("contrast", contrast.coerceIn(0.5f, 2.5f))
                shader.setFloatUniform("saturation", saturation.coerceIn(0f, 2.5f))
                shader.setFloatUniform("brightness", brightness.coerceIn(-0.5f, 0.5f))
                shader.setFloatUniform("warmth", warmth.coerceIn(-1f, 1f))
                RenderEffect.createRuntimeShaderEffect(shader, "image")
            } catch (_: IllegalArgumentException) { fallback }
        } else fallback
    } else null

    var sharpnessEffect: RenderEffect? = null
    if (sharpness > 0.005f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val shader = RuntimeShader(AGSL_LUMA_UNSHARP_SHADER)
            shader.setFloatUniform("sharpness", sharpness)
            sharpnessEffect = RenderEffect.createRuntimeShaderEffect(shader, "image")
        } catch (_: Throwable) {}
    }

    return when {
        sharpnessEffect != null && colorEffect != null -> {
            RenderEffect.createChainEffect(colorEffect, sharpnessEffect)
        }
        sharpnessEffect != null -> sharpnessEffect
        colorEffect != null -> colorEffect
        else -> null
    }
}

fun applyVideoEffectsToPlayerView(pv: PlayerView?, effect: RenderEffect?) {
    if (pv == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        if (pv.tag === effect) return
        pv.tag = effect
        // Process only video pixels; subtitles and player overlays retain their original appearance.
        pv.setRenderEffect(null)
        (pv.videoSurfaceView as? android.view.TextureView)?.setRenderEffect(effect)
    } catch (_: Throwable) {}
}

private enum class PlayerGestureMode {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
    SPEED_HOLD,
    ORIENTATION_SWIPE,
    PINCH_ZOOM,
    IGNORED_DRAG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    streamTorrentHash: String? = null,
    streamTitle: String = "",
    streamPosterUrl: String = "",
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
    var videoTitle by remember {
        mutableStateOf(
            if (streamTitle.isNotBlank()) streamTitle
            else videoUrl.substringAfterLast("/").substringBeforeLast(".")
        )
    }

    var isStreamReady by remember {
        mutableStateOf(
            streamTorrentHash == null || (videoUrl.isNotBlank() && com.example.ymediaplayer.mycinema.service.TorrentEngine.isStreamBufferReady(context, streamTorrentHash))
        )
    }

    val cinemaDownloads by TorrentEngine.downloads.collectAsState()

    // Load playlist (supports custom active queue or folder playlist)
    LaunchedEffect(videoUrl, streamTorrentHash) {
        currentUrl = videoUrl
        if (streamTorrentHash != null) {
            playlistVideos = emptyList()
            currentVideoIndex = 0
            if (streamTitle.isNotBlank()) {
                videoTitle = streamTitle
            }
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val normTarget = videoUrl.removePrefix("file://")
            val targetUri = try { android.net.Uri.parse(videoUrl) } catch (_: Exception) { null }
            val customQueue = VideoPlaybackManager.activePlaylistQueue
            if (!customQueue.isNullOrEmpty() && customQueue.any { 
                it.uri.toString().removePrefix("file://") == normTarget || (targetUri != null && it.uri == targetUri) 
            }) {
                playlistVideos = customQueue
            } else {
                val folders = repository.getFoldersWithVideos()
                allFolders = folders
                val parentFolder = folders.find { folder ->
                    folder.id != "all_videos" && folder.videos.any { v ->
                        v.uri.toString().removePrefix("file://") == normTarget || (targetUri != null && v.uri == targetUri)
                    }
                } ?: folders.find { folder ->
                    folder.videos.any { v ->
                        v.uri.toString().removePrefix("file://") == normTarget || (targetUri != null && v.uri == targetUri)
                    }
                }

                if (parentFolder != null) {
                    playlistVideos = parentFolder.videos
                } else if (!customQueue.isNullOrEmpty()) {
                    playlistVideos = customQueue
                } else {
                    playlistVideos = folders.flatMap { it.videos }.distinctBy { it.id }
                }
            }

            val index = playlistVideos.indexOfFirst {
                it.uri.toString().removePrefix("file://") == normTarget || (targetUri != null && it.uri == targetUri)
            }
            currentVideoIndex = if (index >= 0) index else 0
            if (index >= 0) {
                videoTitle = if (streamTitle.isNotBlank()) streamTitle else playlistVideos[index].title.substringBeforeLast(".")
            } else if (streamTitle.isNotBlank()) {
                videoTitle = streamTitle
            } else {
                videoTitle = java.io.File(normTarget).nameWithoutExtension
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

    val exoPlayer = remember(videoUrl) {
        val initialProgress = appPreferences.getVideoStartPosition(videoUrl)
        val shouldPreload = streamTorrentHash == null || isStreamReady
        VideoPlaybackManager.getOrCreatePlayer(
            context = context,
            url = if (shouldPreload) videoUrl else "",
            title = videoTitle,
            initialProgress = initialProgress,
            playbackSpeed = initialPlaybackSpeed,
            repeatMode = initialRepeatMode,
            isMuted = initialMuted,
            preferredAudioLang = preferredAudioLang,
            preferredSubLang = preferredSubLang,
            subtitlesEnabled = subtitlesEnabled
        )
    }
    val safePlaybackMode by VideoPlaybackManager.safeModeActive
    val playbackRecoveryMessage by VideoPlaybackManager.recoveryMessage
    val compatibilityFallback by VideoPlaybackManager.compatibilityFallbackRequest

    LaunchedEffect(streamTorrentHash) {
        val hash = streamTorrentHash ?: return@LaunchedEffect
        while (true) {
            if (com.example.ymediaplayer.mycinema.service.TorrentEngine.isStreamBufferReady(context, hash)) {
                val path = com.example.ymediaplayer.mycinema.service.TorrentEngine.getFilePath(context, hash)
                if (path != null && java.io.File(path).exists()) {
                    android.util.Log.i("StreamDebug", "Stream buffer ready. Starting ExoPlayer with path: $path")
                    currentUrl = path
                    com.example.ymediaplayer.player.VideoPlaybackManager.currentVideoUrl.value = path
                    if (streamTitle.isNotBlank()) {
                        videoTitle = streamTitle
                        com.example.ymediaplayer.player.VideoPlaybackManager.currentVideoTitle.value = streamTitle
                    }
                    val mItem = androidx.media3.common.MediaItem.Builder()
                        .setUri(android.net.Uri.fromFile(java.io.File(path)))
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(if (streamTitle.isNotBlank()) streamTitle else videoTitle)
                                .setArtist("Cinema")
                                .build()
                        )
                        .build()
                    exoPlayer.setMediaItem(mItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    isStreamReady = true
                    break
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }

    LaunchedEffect(compatibilityFallback?.requestId) {
        val request = compatibilityFallback ?: return@LaunchedEffect
        // Always stay in the original VideoPlayerScreen with all controls and gestures.
        VideoPlaybackManager.consumeCompatibilityFallback(request.requestId)
    }

    val allVideos = remember(allFolders) { allFolders.flatMap { it.videos } }

    val playVideoItem: (VideoItem) -> Unit = { item ->
        val curPos = exoPlayer.currentPosition
        if (curPos > 1000L) {
            appPreferences.saveVideoProgress(currentUrl, curPos, exoPlayer.duration.coerceAtLeast(0L))
        }
        currentUrl = item.uri.toString()
        videoTitle = item.title.substringBeforeLast(".")
        val itemProgress = appPreferences.getVideoStartPosition(item.uri.toString())
        if (itemProgress > 0L) {
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

    LaunchedEffect(currentUrl, videoTitle) {
        VideoPlaybackManager.currentVideoUrl.value = currentUrl
        VideoPlaybackManager.currentVideoTitle.value = videoTitle
        val poster = streamPosterUrl.ifBlank { appPreferences.getCinemaPoster(videoTitle) ?: "" }
        VideoPlaybackManager.currentPosterUrl.value = poster
        com.example.ymediaplayer.service.VideoPlaybackService.notifyPlayerUpdated()
    }

    // ─── Player State ─────────────────────────────────────────────────────────
    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var downloadedRanges by remember(currentUrl) { mutableStateOf<List<ClosedFloatingPointRange<Float>>>(emptyList()) }
    var duration by remember(exoPlayer) {
        val d = exoPlayer.duration
        mutableLongStateOf(if (d > 0L) d else 0L)
    }
    var showControls by remember { mutableStateOf(true) }
    var showFeatureGuide by remember { mutableStateOf(!appPreferences.hasSeenVideoPlayerGuide()) }
    var isTouchingControls by remember { mutableStateOf(false) }
    var controlsInteractionTimestamp by remember { mutableLongStateOf(0L) }
    val optionsScrollState = rememberScrollState()
    var isMuted by remember { mutableStateOf(initialMuted) }
    var playbackSpeed by remember(exoPlayer) { mutableFloatStateOf(exoPlayer.playbackParameters.speed) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val c = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    var isLocked by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(appPreferences.isNightMode()) }
    var isBackgroundAudio by remember { mutableStateOf(appPreferences.isBackgroundPlayEnabled()) }
    var resizeMode by remember(currentUrl) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isAmbientMode by remember { mutableStateOf(appPreferences.isAmbientModeEnabled()) }
    var ambientIntensity by remember { mutableFloatStateOf(appPreferences.getAmbientGlowStrength().coerceIn(0.2f, 1.8f)) }
    var ambientSpread by remember { mutableFloatStateOf(appPreferences.getVideoAmbientSpread()) }
    var ambientSaturation by remember { mutableFloatStateOf(appPreferences.getVideoAmbientSaturation()) }
    var ambientBatterySaver by remember { mutableStateOf(appPreferences.isVideoAmbientBatterySaver()) }
    var showAmbientSettings by remember { mutableStateOf(false) }


    val toggleAmbientMode: () -> Unit = {
        view.performHaptic(HapticType.LIGHT)
        val newState = !isAmbientMode
        isAmbientMode = newState
        appPreferences.setAmbientModeEnabled(newState)
    }

    var isAutoPlayEnabled by remember { mutableStateOf(appPreferences.isAutoPlayNextEnabled()) }
    val toggleAutoPlay: () -> Unit = {
        view.performHaptic(HapticType.LIGHT)
        val newState = !isAutoPlayEnabled
        isAutoPlayEnabled = newState
        appPreferences.setAutoPlayNextEnabled(newState)
    }

    // ─── Stop background music when video player is opened ─────────────────
    LaunchedEffect(Unit) {
        MusicService.pauseMusic()
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
    var lastCustomSpeedBeforeNormal by remember { mutableFloatStateOf(appPreferences.getPressHoldSpeed()) }

    // ─── Resume Playback Prompt State (Disabled - no prompt asking for Start Over) ───
    var resumePromptPosition by remember(currentUrl) { mutableLongStateOf(0L) }

    // ─── A-B Looping State ───────────────────────────────────────────────────
    var loopStartMs by remember(currentUrl) { mutableStateOf<Long?>(null) }
    var loopEndMs by remember(currentUrl) { mutableStateOf<Long?>(null) }

    // ─── Bookmarks State ─────────────────────────────────────────────────────
    var bookmarksList by remember(currentUrl) { mutableStateOf(appPreferences.getBookmarks(currentUrl)) }

    // ─── Resolution Badge Intro Overlay (Disabled per user request) ──────────
    var showResolutionBadgeIntro by remember(currentUrl) { mutableStateOf(false) }
    var isQuickActionsExpanded by rememberSaveable { mutableStateOf(false) }

    var lastSeekMilestone by remember { mutableIntStateOf(-1) }
    var showRewindJumpMenu by remember { mutableStateOf(false) }
    var showForwardJumpMenu by remember { mutableStateOf(false) }


    // ─── Fat Vertical Gesture Bars State ──────────────────────────────────────
    // Brightness on Left (0.01f..1f)
    var isAutoBrightness by remember {
        mutableStateOf(appPreferences.isAutoBrightnessEnabled())
    }
    val savedBrightness = remember { appPreferences.getLastPlayerBrightness() }
    var brightnessPct by remember {
        val lp = activity?.window?.attributes
        val currentVal = lp?.screenBrightness?.takeIf { it in 0.005f..1f }
        mutableFloatStateOf(
            if (!isAutoBrightness && savedBrightness in 0.01f..1.0f) savedBrightness
            else if (currentVal != null) screenBrightnessToSlider(currentVal)
            else getSystemBrightness(context)
        )
    }
    var showBrightnessBar by remember { mutableStateOf(false) }
    var brightnessTouchTrigger by remember { mutableLongStateOf(0L) }
    var hasUserAdjustedBrightness by remember { mutableStateOf(!isAutoBrightness && savedBrightness in 0.01f..1.0f) }

    // When auto brightness is enabled, ensure window follows phone's system brightness
    LaunchedEffect(isAutoBrightness) {
        val act = activity ?: return@LaunchedEffect
        val lp = act.window?.attributes ?: return@LaunchedEffect
        if (isAutoBrightness) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            act.window?.attributes = lp
            brightnessPct = getSystemBrightness(context)
        }
    }

    // Smooth screen brightness batch updater using perceptual gamma curve (alters brightness when user changes it)
    LaunchedEffect(brightnessPct, hasUserAdjustedBrightness, isAutoBrightness) {
        if (isAutoBrightness || !hasUserAdjustedBrightness) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        val lp = act.window?.attributes ?: return@LaunchedEffect
        val targetHardware = sliderToScreenBrightness(brightnessPct)
        if (lp.screenBrightness != targetHardware) {
            lp.screenBrightness = targetHardware
            act.window?.attributes = lp
        }
    }

    // Volume & Audio Boost (0f..2f, where >1f is software loudness gain)
    val audioSessionId by VideoPlaybackManager.currentAudioSessionId
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    DisposableEffect(audioSessionId) {
        if (audioSessionId > 0) {
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            } catch (_: Exception) {
                loudnessEnhancer = null
            }
        }
        onDispose {
            try {
                loudnessEnhancer?.release()
            } catch (_: Exception) {}
            loudnessEnhancer = null
        }
    }

    var volumePct by remember {
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(vol.toFloat() / maxVolume.toFloat())
    }
    var showVolumeBar by remember { mutableStateOf(false) }
    var volumeTouchTrigger by remember { mutableLongStateOf(0L) }

    fun applyVolumeAndBoost(ratio: Float) {
        val clampedRatio = ratio.coerceIn(0f, 2.0f)
        volumePct = clampedRatio
        if (clampedRatio <= 1.0f) {
            val newVol = (clampedRatio * maxVolume).roundToInt().coerceIn(0, maxVolume)
            if (newVol != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            }
            try {
                if (loudnessEnhancer?.enabled == true) {
                    loudnessEnhancer?.setTargetGain(0)
                    loudnessEnhancer?.enabled = false
                }
            } catch (_: Exception) {}
        } else {
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != maxVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            }
            val boostRatio = clampedRatio - 1.0f
            val targetGain = (boostRatio * 1200f).roundToInt()
            try {
                loudnessEnhancer?.let { enhancer ->
                    enhancer.setTargetGain(targetGain)
                    if (!enhancer.enabled) {
                        enhancer.enabled = true
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(loudnessEnhancer, volumePct) {
        loudnessEnhancer?.let { enhancer ->
            try {
                if (volumePct > 1.0f) {
                    val boostRatio = volumePct - 1.0f
                    val targetGain = (boostRatio * 1200f).roundToInt()
                    enhancer.setTargetGain(targetGain)
                    enhancer.enabled = true
                } else {
                    if (enhancer.enabled) {
                        enhancer.setTargetGain(0)
                        enhancer.enabled = false
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Seek Gestures
    var isDraggingSeek by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var currentDragSeekTarget by remember { mutableLongStateOf(0L) }
    var activeGestureMode by remember { mutableStateOf(PlayerGestureMode.NONE) }
    var doubleTapSeekSeconds by remember { mutableIntStateOf(0) }
    var doubleTapIsForward by remember { mutableStateOf(true) }
    var centerDoubleTapPlayPause by remember { mutableStateOf<Boolean?>(null) }
    var centerDoubleTapKey by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }

    // Center Seek HUD
    var centerSeekText by remember { mutableStateOf<String?>(null) }
    var centerSeekIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Sheets & Dialogs
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSettingsOverlay by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var isMoreExpanded by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleTools by remember { mutableStateOf(false) }
    var showThemePickerSheet by remember { mutableStateOf(false) }
    var showVideoInfoSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val fileManager = remember { FileManager(context) }
    var modePicker by remember { mutableStateOf<String?>(null) }
    var showQuickControlsBar by remember { mutableStateOf(false) }
    var subtitleDesign by remember { mutableIntStateOf(appPreferences.getSubtitleDesign()) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var activeElementTooltip by remember { mutableStateOf<ElementTooltipData?>(null) }
    LaunchedEffect(activeElementTooltip) {
        if (activeElementTooltip != null) {
            delay(2000L)
            activeElementTooltip = null
        }
    }

    // ─── HDR Video Stream Detection & Hardware Color Mode ─────────────────────
    var currentVideoFormat by remember { mutableStateOf<Format?>(null) }
    DisposableEffect(exoPlayer) {
        val formatListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                currentVideoFormat = exoPlayer.videoFormat
                val d = exoPlayer.duration
                if (d > 0L) duration = d

                // Ensure an audio track is always selected and active
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.isNotEmpty()) {
                    val hasSelectedAudio = audioGroups.any { group ->
                        (0 until group.length).any { group.isTrackSelected(it) }
                    }
                    if (!hasSelectedAudio) {
                        val targetGroup = audioGroups.firstOrNull { it.isSupported } ?: audioGroups.first()
                        val trackIdx = (0 until targetGroup.length).firstOrNull { targetGroup.isTrackSupported(it) } ?: 0
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIdx))
                            .build()
                    }
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = exoPlayer.duration
                if (d > 0L) duration = d
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val d = exoPlayer.duration
                if (d > 0L) duration = d
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                currentVideoFormat = exoPlayer.videoFormat
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (currentVideoFormat != exoPlayer.videoFormat) {
                    currentVideoFormat = exoPlayer.videoFormat
                }
            }
        }
        exoPlayer.addListener(formatListener)
        currentVideoFormat = exoPlayer.videoFormat
        onDispose {
            exoPlayer.removeListener(formatListener)
        }
    }

    val isHdrVideo = remember(currentVideoFormat, appPreferences.isHdrEnabled()) {
        if (!appPreferences.isHdrEnabled()) return@remember false
        val format = currentVideoFormat ?: exoPlayer.videoFormat
        val colorInfo = format?.colorInfo
        val isTransferHdr = colorInfo != null && ColorInfo.isTransferHdr(colorInfo)
        val isDolby = format?.sampleMimeType?.contains("dolby", ignoreCase = true) == true
        val isHdrTransfer = colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 || colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG
        isTransferHdr || isDolby || isHdrTransfer
    }

    val hdrTypeLabel = remember(currentVideoFormat) {
        val format = currentVideoFormat ?: exoPlayer.videoFormat
        val colorInfo = format?.colorInfo
        when {
            format?.sampleMimeType?.contains("dolby", ignoreCase = true) == true -> "Dolby Vision"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
            colorInfo != null && ColorInfo.isTransferHdr(colorInfo) -> "HDR"
            else -> null
        }
    }

    // Dynamic Hardware Window Color Mode for True 10-bit HDR Playback (Zero Yellow Tint)
    DisposableEffect(isHdrVideo) {
        val act = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act != null) {
            act.window.colorMode = if (isHdrVideo) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act != null) {
                act.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }

    val playerView = remember(context, isHdrVideo, exoPlayer) {
        val pv = if (isHdrVideo) {
            // Pure 10-bit SurfaceView pipeline (prevents yellow tint, renders native BT.2020 / HLG / Dolby Vision)
            PlayerView(context)
        } else {
            // High-performance TextureView pipeline for SDR (supports real-time AGSL sharpness & color filters)
            try {
                LayoutInflater.from(context).inflate(com.example.ymediaplayer.R.layout.player_texture_view, null) as PlayerView
            } catch (_: Throwable) {
                PlayerView(context)
            }
        }
        pv.apply {
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

    DisposableEffect(playerView, exoPlayer) {
        playerView.player = exoPlayer
        onDispose {
            playerView.player = null
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
    val subtitleState = rememberVideoSubtitles(currentUrl, exoPlayer, playerView, appPreferences, isInPiP)
    var prePipSubtitleFlags by remember { mutableIntStateOf(-1) }
    var prePipSubtitlesDisabled by remember { mutableStateOf(false) } // stores subtitle flags before PiP entry

    // Pinch-to-Zoom & Pan
    var videoZoomScale by remember(currentUrl) { mutableFloatStateOf(1f) }
    var videoPanX by remember(currentUrl) { mutableFloatStateOf(0f) }
    var videoPanY by remember(currentUrl) { mutableFloatStateOf(0f) }
    var showZoomHUD by remember { mutableStateOf(false) }
    var zoomHUDText by remember { mutableStateOf("") }

    var speedBeforeReset by remember(currentUrl) { mutableStateOf<Float?>(null) }

    val resetSpeedToOne: () -> Unit = {
        view.performHaptic(HapticType.MEDIUM)
        if (speedBeforeReset == null) {
            speedBeforeReset = playbackSpeed
            playbackSpeed = 1.0f
        } else {
            playbackSpeed = speedBeforeReset!!
            speedBeforeReset = null
        }
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        if (appPreferences.isRememberPlaybackSpeed()) {
            appPreferences.setLastPlaybackSpeed(playbackSpeed)
        }
    }

    // Repeat Mode (0: Off, 1: One, 2: All)
    var repeatMode by remember { mutableIntStateOf(initialRepeatMode) }

    // ─── Video Sound Profiles & Hardware Audio Effects ────────────────────────
    val activeAudioSessionId by remember { VideoPlaybackManager.currentAudioSessionId }
    val audioRoute = rememberVideoAudioRoute(audioManager)
    var videoSoundMode by remember(audioRoute) { mutableStateOf(VideoSoundMode.fromId(appPreferences.getVideoRouteSoundMode(audioRoute))) }
    var audioStrength by remember(audioRoute) { mutableFloatStateOf(appPreferences.getVideoRouteSoundStrength(audioRoute)) }
    var volumeLeveling by remember(audioRoute) { mutableStateOf(appPreferences.isVideoVolumeLevelingEnabled(audioRoute)) }
    var pictureStrength by remember { mutableFloatStateOf(appPreferences.getVideoModeStrength("picture")) }
    var sharpnessStrength by remember { mutableFloatStateOf(appPreferences.getVideoModeStrength("sharpness")) }
    var previewOriginal by remember { mutableStateOf(false) }

    var videoColorProfile by remember { mutableStateOf(VideoColorProfile.fromId(appPreferences.getVideoColorProfile())) }
    var videoSharpnessLevel by remember { mutableStateOf(VideoSharpnessLevel.fromId(appPreferences.getVideoSharpnessLevel())) }

    // Instant HUD Indicator (Shared across Sound, Picture, and Sharpness controls)
    var quickHUDText by remember { mutableStateOf<String?>(null) }
    var quickHUDSubtext by remember { mutableStateOf<String?>(null) }
    var quickHUDJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val showQuickHUD: (String, String, androidx.compose.ui.graphics.vector.ImageVector) -> Unit = { text, subtext, _ ->
        quickHUDJob?.cancel()
        quickHUDText = text
        quickHUDSubtext = subtext
        quickHUDJob = coroutineScope.launch {
            delay(1800)
            quickHUDText = null
            quickHUDSubtext = null
        }
    }

    LaunchedEffect(playbackRecoveryMessage) {
        playbackRecoveryMessage?.let { message ->
            showQuickHUD(
                if (safePlaybackMode) "Compatibility mode" else "Playback recovery",
                message,
                Icons.Rounded.HealthAndSafety
            )
        }
    }

    // Dynamic Video RenderEffect (Color Profile + AGSL Luminance Unsharp Mask + Super AI Engine)
    var currentContrast by remember { mutableStateOf(appPreferences.getVideoCustomContrast()) }
    var currentSaturation by remember { mutableStateOf(appPreferences.getVideoCustomSaturation()) }
    var currentBrightness by remember { mutableStateOf(appPreferences.getVideoCustomBrightness()) }
    var currentWarmth by remember { mutableStateOf(appPreferences.getVideoCustomWarmth()) }
    var currentSharpness by remember { mutableStateOf(appPreferences.getVideoCustomSharpness()) }

    // Smart Clarity: adaptive spatial detail, bounded shadow lift and restrained colour.
    var isSuperAiEnhance by remember { mutableStateOf(appPreferences.isSuperAiEnhanceEnabled()) }

    val currentVideoRenderEffect = remember(currentContrast, currentSaturation, currentBrightness, currentWarmth, currentSharpness, isSuperAiEnhance, safePlaybackMode, previewOriginal, pictureStrength, sharpnessStrength, isHdrVideo) {
        if (safePlaybackMode || previewOriginal || isHdrVideo) {
            null
        } else {
            createVideoRenderEffect(
                contrast = 1f + (currentContrast - 1f) * pictureStrength,
                saturation = 1f + (currentSaturation - 1f) * pictureStrength,
                brightness = currentBrightness * pictureStrength,
                warmth = currentWarmth * pictureStrength,
                sharpness = currentSharpness * sharpnessStrength,
                isSuperAi = isSuperAiEnhance
            )
        }
    }

    LaunchedEffect(currentVideoRenderEffect, playerView) {
        applyVideoEffectsToPlayerView(playerView, currentVideoRenderEffect)
    }

    val toggleSuperAiEnhance: () -> Unit = {
        view.performHaptic(HapticType.MEDIUM)
        val newState = !isSuperAiEnhance
        if (newState && (isHdrVideo || safePlaybackMode || Build.VERSION.SDK_INT < 31)) {
            showQuickHUD("Enhanced mode unavailable", "Available for SDR playback on Android 12 or newer", Icons.Rounded.Info)
        } else {
            isSuperAiEnhance = newState
            appPreferences.setSuperAiEnhanceEnabled(newState)

            if (newState) {
                if (isNightMode) {
                    isNightMode = false
                    appPreferences.setNightMode(false)
                }

                showQuickHUD(
                    "Enhanced mode: ON",
                    "Crisp detail • Punchy color • Gentle warmth",
                    Icons.Rounded.AutoAwesome
                )
            } else {

                showQuickHUD(
                    "Enhanced mode: OFF",
                    "Standard video playback restored",
                    Icons.Rounded.AutoAwesome
                )
            }
        }
    }

    val selectVideoColorProfile: (VideoColorProfile) -> Unit = { nextProfile ->
        videoColorProfile = nextProfile
        appPreferences.setVideoColorProfile(nextProfile.id)
        currentContrast = nextProfile.contrast
        currentSaturation = nextProfile.saturation
        currentBrightness = nextProfile.brightness
        currentWarmth = nextProfile.warmth
        appPreferences.setVideoCustomContrast(nextProfile.contrast)
        appPreferences.setVideoCustomSaturation(nextProfile.saturation)
        appPreferences.setVideoCustomBrightness(nextProfile.brightness)
        appPreferences.setVideoCustomWarmth(nextProfile.warmth)
        showQuickHUD(nextProfile.displayName, nextProfile.description, nextProfile.icon)
    }

    val selectVideoSharpness: (VideoSharpnessLevel) -> Unit = { nextLevel ->
        videoSharpnessLevel = nextLevel
        appPreferences.setVideoSharpnessLevel(nextLevel.id)
        currentSharpness = nextLevel.intensity
        appPreferences.setVideoCustomSharpness(nextLevel.intensity)
        showQuickHUD(nextLevel.displayName, nextLevel.description, nextLevel.icon)
    }

    // Hardware Audio Effects (Equalizer, BassBoost, Virtualizer) attached to active session ID
    // Only instantiated when a non-standard sound mode is active, avoiding AudioTrack reinit / audio stalls
    val audioEffects = remember(activeAudioSessionId) {
        if (activeAudioSessionId <= 0) {
            Triple(null, null, null)
        } else {
            var eq: Equalizer? = null
            var bass: BassBoost? = null
            var virt: Virtualizer? = null
            try { eq = Equalizer(0, activeAudioSessionId) } catch (_: Exception) {}
            try { bass = BassBoost(0, activeAudioSessionId) } catch (_: Exception) {}
            try { virt = Virtualizer(0, activeAudioSessionId) } catch (_: Exception) {}
            Triple(eq, bass, virt)
        }
    }

    DisposableEffect(audioEffects) {
        onDispose {
            val (eq, bass, virt) = audioEffects
            try { eq?.release() } catch (_: Exception) {}
            try { bass?.release() } catch (_: Exception) {}
            try { virt?.release() } catch (_: Exception) {}
        }
    }

    val applyAcoustics: (VideoSoundMode) -> Unit = remember(audioEffects, audioStrength, audioRoute) {
        { mode ->
            val (eq, bass, virt) = audioEffects
            if (mode == VideoSoundMode.NORMAL) {
                try { eq?.enabled = false } catch (_: Exception) {}
                try { bass?.enabled = false } catch (_: Exception) {}
                try { virt?.enabled = false } catch (_: Exception) {}
            } else {
                if (eq != null) {
                    try {
                        val minRange = eq.bandLevelRange[0]
                        val maxRange = eq.bandLevelRange[1]
                        for (index in 0 until eq.numberOfBands.toInt()) {
                            val band = index.toShort()
                            val db = interpolateVideoEqGain(eq.getCenterFreq(band) / 1000f, mode.bands) * audioStrength
                            val level = (db * 100).toInt().coerceIn(minRange.toInt(), maxRange.toInt())
                            eq.setBandLevel(band, level.toShort())
                        }
                        eq.enabled = true
                    } catch (_: Exception) {}
                }
                if (bass != null) {
                    try {
                        bass.setStrength((mode.bassBoost * audioStrength * 1000).toInt().toShort())
                        bass.enabled = (mode.bassBoost > 0f)
                    } catch (_: Exception) {}
                }
                if (virt != null) {
                    try {
                        virt.setStrength((mode.virtualizer * audioStrength * 1000).toInt().toShort())
                        virt.enabled = (mode.virtualizer > 0f && audioRoute == "headphones")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(audioEffects, videoSoundMode, audioStrength, volumeLeveling, previewOriginal, exoPlayer) {
        applyAcoustics(if (previewOriginal) VideoSoundMode.NORMAL else videoSoundMode)
        VideoPlaybackManager.setAudioDynamics(
            if (!previewOriginal && videoSoundMode == VideoSoundMode.NIGHT_MODE) audioStrength else 0f,
            volumeLeveling && !previewOriginal)
    }

    val selectVideoSoundMode: (VideoSoundMode) -> Unit = { nextMode ->
        videoSoundMode = nextMode
        appPreferences.setVideoRouteSoundMode(audioRoute, nextMode.id)
        showQuickHUD(nextMode.displayName, nextMode.description, nextMode.icon)
    }


    val configuredSeekStepSec = remember { appPreferences.getDoubleTapSeekSeconds().coerceAtLeast(1) }
    val configuredSeekStepMs = remember(configuredSeekStepSec) { configuredSeekStepSec * 1000L }

    val activeTorrentHash = remember(videoUrl, streamTorrentHash, currentUrl) {
        streamTorrentHash
            ?: com.example.ymediaplayer.mycinema.service.TorrentEngine.getActiveTorrent(videoUrl)
            ?: com.example.ymediaplayer.mycinema.service.TorrentEngine.getActiveTorrent(currentUrl)
    }
    LaunchedEffect(activeTorrentHash) {
        val hash = activeTorrentHash ?: return@LaunchedEffect
        com.example.ymediaplayer.mycinema.service.TorrentEngine.setSequential(hash, true)
    }

    // ─── 2-Minute Sliding Buffer Window for Torrent Streaming ─────────────────
    LaunchedEffect(activeTorrentHash, isStreamReady) {
        val hash = activeTorrentHash ?: return@LaunchedEffect
        if (streamTorrentHash == null && cinemaDownloads[hash]?.isStream != true) return@LaunchedEffect
        while (true) {
            if (isStreamReady) {
                val durSec = if (duration > 0) duration / 1000L else 6600L
                val frac = if (duration > 0) (currentPosition.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0) else 0.0
                com.example.ymediaplayer.mycinema.service.TorrentEngine.updateStreamSlidingWindow(hash, frac, durSec)
            }
            kotlinx.coroutines.delay(2000L)
        }
    }
    var torrentSeekBufferingTarget by remember { mutableStateOf<Long?>(null) }
    var torrentSeekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // ─── Helper Functions ─────────────────────────────────────────────────────
    fun safeSeek(targetMs: Long) {
        val maxDur = maxOf(duration, exoPlayer.duration.coerceAtLeast(0L))
        val target = if (maxDur > 0L) targetMs.coerceIn(0L, maxDur) else targetMs.coerceAtLeast(0L)
        val hash = activeTorrentHash

        // Only guard and wait for pieces if this is an actively downloading/streaming torrent
        val isActivelyDownloading = hash != null && run {
            val download = cinemaDownloads[hash]
            download != null &&
                (download.status == com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.DOWNLOADING ||
                 download.status == com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.STREAMING) &&
                download.progress < 0.99f
        }

        if (isActivelyDownloading && hash != null && maxDur > 0L) {
            val frac = (target.toDouble() / maxDur.toDouble()).coerceIn(0.0, 1.0)
            if (!com.example.ymediaplayer.mycinema.service.TorrentEngine.isPieceAvailable(hash, frac)) {
                exoPlayer.pause()
                torrentSeekBufferingTarget = target
                currentPosition = target
                val durSec = if (maxDur > 0) maxDur / 1000L else 6600L
                com.example.ymediaplayer.mycinema.service.TorrentEngine.prioritizeStreamSeek(hash, frac, durSec)
                torrentSeekJob?.cancel()
                torrentSeekJob = coroutineScope.launch {
                    val startWait = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startWait < 15_000L) {
                        if (com.example.ymediaplayer.mycinema.service.TorrentEngine.isSeekBufferReady(hash, frac)) {
                            break
                        }
                        kotlinx.coroutines.delay(150L)
                    }
                    exoPlayer.seekTo(target)
                    currentPosition = target
                    torrentSeekBufferingTarget = null
                    VideoPlaybackManager.manualPlay()
                }
                return
            }
        }
        torrentSeekJob?.cancel()
        torrentSeekBufferingTarget = null
        exoPlayer.seekTo(target)
        currentPosition = target
        if (!exoPlayer.isPlaying) {
            VideoPlaybackManager.manualPlay()
        }
    }

    fun seekBy(deltaMs: Long) {
        safeSeek(exoPlayer.currentPosition + deltaMs)
    }

    val togglePlayPause: () -> Unit = {
        if (isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED || (duration > 0L && exoPlayer.currentPosition >= duration - 500L)) {
                safeSeek(0L)
            }
            VideoPlaybackManager.manualPlay()
        }
    }

    val playNextVideo: () -> Unit = {
        if (playlistVideos.isNotEmpty()) {
            val curPos = exoPlayer.currentPosition
            if (curPos > 1000L) {
                appPreferences.saveVideoProgress(currentUrl, curPos, exoPlayer.duration.coerceAtLeast(0L))
            }
            val nextIndex = if (isShuffleOn && playlistVideos.size > 1) {
                var r = (playlistVideos.indices).random()
                while (r == currentVideoIndex && playlistVideos.size > 1) {
                    r = (playlistVideos.indices).random()
                }
                r
            } else if (currentVideoIndex >= 0 && currentVideoIndex < playlistVideos.size - 1) {
                currentVideoIndex + 1
            } else {
                0
            }
            val nextVideo = playlistVideos[nextIndex]
            currentVideoIndex = nextIndex
            currentUrl = nextVideo.uri.toString()
            videoTitle = nextVideo.title.substringBeforeLast(".")
            VideoPlaybackManager.currentVideoUrl.value = currentUrl
            VideoPlaybackManager.currentVideoTitle.value = videoTitle
            val nextProgress = appPreferences.getVideoStartPosition(nextVideo.uri.toString())
            val isNextCompleted = appPreferences.isCompleted(nextVideo.uri.toString())

            val initialMeta = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(videoTitle)
                .setArtist("Video")
                .build()
            val mItem = androidx.media3.common.MediaItem.Builder()
                .setUri(nextVideo.uri)
                .setMediaMetadata(initialMeta)
                .build()

            if (nextProgress > 3000L && !isNextCompleted) {
                exoPlayer.setMediaItem(mItem, nextProgress)
            } else {
                exoPlayer.setMediaItem(mItem, 0L)
            }
            exoPlayer.prepare()
            exoPlayer.play()

            val targetNextUri = nextVideo.uri
            coroutineScope.launch(Dispatchers.IO) {
                val thumbBytes = try {
                    com.example.ymediaplayer.util.MediaArtworkHelper.getVideoThumbnailBytes(context, targetNextUri)
                } catch (_: Throwable) { null }
                if (thumbBytes != null) {
                    withContext(Dispatchers.Main) {
                        if (currentUrl == targetNextUri.toString()) {
                            val enrichedMeta = initialMeta.buildUpon()
                                .setArtworkData(thumbBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                            val updatedItem = mItem.buildUpon().setMediaMetadata(enrichedMeta).build()
                            val curIdx = exoPlayer.currentMediaItemIndex
                            if (curIdx >= 0 && curIdx < exoPlayer.mediaItemCount) {
                                exoPlayer.replaceMediaItem(curIdx, updatedItem)
                            }
                        }
                    }
                }
            }
        }
    }

    val playPreviousVideo: () -> Unit = {
        if (playlistVideos.isNotEmpty()) {
            val curPos = exoPlayer.currentPosition
            if (curPos > 1000L) {
                appPreferences.saveVideoProgress(currentUrl, curPos, exoPlayer.duration.coerceAtLeast(0L))
            }
            val prevIndex = if (currentVideoIndex > 0) {
                currentVideoIndex - 1
            } else {
                playlistVideos.size - 1
            }
            val prevVideo = playlistVideos[prevIndex]
            currentVideoIndex = prevIndex
            currentUrl = prevVideo.uri.toString()
            videoTitle = prevVideo.title.substringBeforeLast(".")
            VideoPlaybackManager.currentVideoUrl.value = currentUrl
            VideoPlaybackManager.currentVideoTitle.value = videoTitle
            val prevProgress = appPreferences.getVideoStartPosition(prevVideo.uri.toString())
            val isPrevCompleted = appPreferences.isCompleted(prevVideo.uri.toString())

            val initialPrevMeta = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(videoTitle)
                .setArtist("Video")
                .build()
            val mItem = androidx.media3.common.MediaItem.Builder()
                .setUri(prevVideo.uri)
                .setMediaMetadata(initialPrevMeta)
                .build()

            if (prevProgress > 3000L && !isPrevCompleted) {
                exoPlayer.setMediaItem(mItem, prevProgress)
            } else {
                exoPlayer.setMediaItem(mItem, 0L)
            }
            exoPlayer.prepare()
            exoPlayer.play()

            val targetPrevUri = prevVideo.uri
            coroutineScope.launch(Dispatchers.IO) {
                val thumbBytes = try {
                    com.example.ymediaplayer.util.MediaArtworkHelper.getVideoThumbnailBytes(context, targetPrevUri)
                } catch (_: Throwable) { null }
                if (thumbBytes != null) {
                    withContext(Dispatchers.Main) {
                        if (currentUrl == targetPrevUri.toString()) {
                            val enrichedMeta = initialPrevMeta.buildUpon()
                                .setArtworkData(thumbBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                            val updatedItem = mItem.buildUpon().setMediaMetadata(enrichedMeta).build()
                            val curIdx = exoPlayer.currentMediaItemIndex
                            if (curIdx >= 0 && curIdx < exoPlayer.mediaItemCount) {
                                exoPlayer.replaceMediaItem(curIdx, updatedItem)
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(playNextVideo, playPreviousVideo) {
        VideoPlaybackManager.onPlayNextAction = playNextVideo
        VideoPlaybackManager.onPlayPrevAction = playPreviousVideo
        onDispose {
            VideoPlaybackManager.onPlayNextAction = null
            VideoPlaybackManager.onPlayPrevAction = null
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
        view.performHaptic(HapticType.LIGHT)
        if (abs(videoZoomScale - 1f) > 0.01f || abs(videoPanX) > 1f || abs(videoPanY) > 1f) {
            videoZoomScale = 1f
            videoPanX = 0f
            videoPanY = 0f
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            zoomHUDText = "Original (Fit)"
            showZoomHUD = true
        } else {
            resizeMode = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            val label = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Original (Fit)"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop / Zoom"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch (Fill)"
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "100%"
                else -> "Original (Fit)"
            }
            zoomHUDText = label
            showZoomHUD = true
        }
    }

    // ─── Video Ratio & Orientation Detection ──────────────────────────────────
    val initialCachedDim = remember(currentUrl) {
        VideoPlaybackManager.videoWidth.value.takeIf { it > 0 }?.let { w ->
            Pair(w, VideoPlaybackManager.videoHeight.value)
        } ?: VideoRepository.getVideoDimensions(currentUrl)
    }
    var videoWidth by remember(currentUrl) { mutableIntStateOf(initialCachedDim?.first ?: 0) }
    var videoHeight by remember(currentUrl) { mutableIntStateOf(initialCachedDim?.second ?: 0) }
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
                    isPlaying = false
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (isAutoPlayEnabled || repeatMode == Player.REPEAT_MODE_ALL) {
                        if (playlistVideos.isNotEmpty()) {
                            val isLast = currentVideoIndex >= playlistVideos.size - 1
                            if (!isLast || repeatMode == Player.REPEAT_MODE_ALL) {
                                playNextVideo()
                            } else {
                                showControls = true
                            }
                        } else {
                            showControls = true
                        }
                    } else {
                        showControls = true
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
    var userOrientationOverride by remember { mutableStateOf<Int?>(null) } // null: auto, 0: portrait, 1: landscape
    val currentOrientationSetting = remember { intArrayOf(-1) } // 0: portrait, 1: landscape
    val lastSensorOrientation = remember { intArrayOf(-1) } // 0: portrait, 1: landscape
    var sensorBucketAtOverride by remember { mutableIntStateOf(-1) }
    // Pick an initial orientation once for this player session.  A following video
    // must not rotate the device merely because it has a different aspect ratio.
    var hasSetInitialOrientation by remember { mutableStateOf(false) }

    val orientationEventListener = remember(context, activity) {
        object : android.view.OrientationEventListener(context, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            private var lastAutoRotateCheckTime = 0L
            private var cachedAutoRotateEnabled = true

            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val isLandscapeSensor = (orientation in 65..115) || (orientation in 245..295)
                val isPortraitSensor = (orientation in 335..360) || (orientation in 0..25) || (orientation in 155..205)

                val sensorBucket = when {
                    isLandscapeSensor -> 1
                    isPortraitSensor -> 0
                    else -> -1
                }
                if (sensorBucket == -1) return
                lastSensorOrientation[0] = sensorBucket

                // If user manually pressed the rotate button:
                if (userOrientationOverride != null) {
                    // Do not override user's manual choice while physical orientation has not changed
                    if (sensorBucket == sensorBucketAtOverride) {
                        return
                    }
                    // Physical orientation actually changed, so clear manual override
                    userOrientationOverride = null
                }

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

                if (sensorBucket == 1 && currentOrientationSetting[0] != 1) {
                    currentOrientationSetting[0] = 1
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (sensorBucket == 0 && currentOrientationSetting[0] != 0) {
                    currentOrientationSetting[0] = 0
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                currentOrientationSetting[0] = 0
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                // Horizontal video (e.g. 16:9)
                // When phone is in vertical (portrait) orientation, open in vertical mode only!
                val isPhonePortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                if (isPhonePortrait) {
                    currentOrientationSetting[0] = 0
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    currentOrientationSetting[0] = 1
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    val insetsController = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(Unit) {
        // Android 12+ can keep the video surface attached during device rotation,
        // avoiding the abrupt black flash of the default rotation transition.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
                }
            }
        }
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
    DisposableEffect(componentActivity, exoPlayer, playerView) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPiP = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                // Entering PiP: physically hide subtitle view, clear cues, and disable text tracks
                playerView.subtitleView?.visibility = android.view.View.GONE
                playerView.subtitleView?.setCues(emptyList())
                playerView.subtitleView?.alpha = 0f
                prePipSubtitlesDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
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
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, prePipSubtitlesDisabled)
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
                        togglePlayPause()
                    }
                    ACTION_PIP_PREV -> {
                        playPreviousVideo()
                    }
                    ACTION_PIP_NEXT -> {
                        playNextVideo()
                    }
                    ACTION_PIP_BG_PLAY -> {
                        isBackgroundAudio = true
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
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            pipReceiver,
            pipFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
            try { context.unregisterReceiver(pipReceiver) } catch (_: Exception) {}
        }
    }

    // ─── PiP Params Builder (clamped aspect ratio, remote actions, auto-enter) ─
    val buildPipParams = remember(exoPlayer, videoWidth, videoHeight, isPlaying, isVerticalVideo, currentVideoIndex, playlistVideos, currentUrl) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val vs = exoPlayer.videoSize
                    val rot = vs.unappliedRotationDegrees
                    val exoW = if (rot == 90 || rot == 270) vs.height else vs.width
                    val exoH = if (rot == 90 || rot == 270) vs.width else vs.height
                    val aspectW = if (videoWidth > 0) videoWidth else (exoW.takeIf { it > 0 } ?: (if (isVerticalVideo) 9 else 16))
                    val aspectH = if (videoHeight > 0) videoHeight else (exoH.takeIf { it > 0 } ?: (if (isVerticalVideo) 16 else 9))
                    val rawRatio = aspectW.toFloat() / aspectH.toFloat()
                    val clampedRatio = rawRatio.coerceIn(0.41841f, 2.39f)
                    val rational = if (clampedRatio < 1f) {
                        android.util.Rational((clampedRatio * 10000).toInt(), 10000)
                    } else {
                        android.util.Rational(10000, (10000 / clampedRatio).toInt())
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
                        if (canPrev) actionsList.add(prevAction)
                        actionsList.add(playPauseAction)
                        if (canNext) actionsList.add(nextAction)
                        actionsList.add(bgPlayAction)
                    } else if (maxAllowed == 3) {
                        if (canPrev && canNext) {
                            actionsList.add(prevAction)
                            actionsList.add(playPauseAction)
                            actionsList.add(nextAction)
                        } else if (canPrev) {
                            actionsList.add(prevAction)
                            actionsList.add(playPauseAction)
                            actionsList.add(bgPlayAction)
                        } else if (canNext) {
                            actionsList.add(bgPlayAction)
                            actionsList.add(playPauseAction)
                            actionsList.add(nextAction)
                        } else {
                            actionsList.add(bgPlayAction)
                            actionsList.add(playPauseAction)
                        }
                    } else {
                        actionsList.add(playPauseAction)
                    }

                    val paramsBuilder = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .setActions(actionsList.take(maxAllowed))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        paramsBuilder.setAutoEnterEnabled(appPreferences.isAutoPipEnabled())
                        paramsBuilder.setSeamlessResizeEnabled(true)
                    }

                    try {
                        val rect = android.graphics.Rect()
                        playerView.getGlobalVisibleRect(rect)
                        if (!rect.isEmpty) {
                            paramsBuilder.setSourceRectHint(rect)
                        }
                    } catch (_: Exception) {}

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
    LaunchedEffect(videoWidth, videoHeight, isPlaying, isVerticalVideo, currentVideoIndex, currentUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val act = activity ?: return@LaunchedEffect
            val params = buildPipParams()
            if (params != null) {
                try {
                    act.setPictureInPictureParams(params)
                } catch (_: Exception) {}
            }
        }
    }

    // Connect to onUserLeaveHintListener so pressing Home button enters PiP on all Android versions
    DisposableEffect(exoPlayer, isPlaying, buildPipParams) {
        val leaveHintHandler = {
            if (appPreferences.isAutoPipEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val act = activity
                if (act != null && !act.isInPictureInPictureMode) {
                    try {
                        playerView.subtitleView?.visibility = android.view.View.GONE
                        playerView.subtitleView?.setCues(emptyList())
                        playerView.subtitleView?.alpha = 0f
                        val params = buildPipParams()
                        if (params != null) {
                            act.enterPictureInPictureMode(params)
                        } else {
                            @Suppress("DEPRECATION")
                            act.enterPictureInPictureMode()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        MainActivity.onUserLeaveHintListener = leaveHintHandler
        VideoPlayerActivity.onUserLeaveHintListener = leaveHintHandler
        onDispose {
            MainActivity.onUserLeaveHintListener = null
            VideoPlayerActivity.onUserLeaveHintListener = null
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            } catch (e: Exception) {
                Toast.makeText(context, "Could not enter Picture-in-Picture", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Progress Loop & A-B Looping Check ─────────────────────────────────────
    LaunchedEffect(exoPlayer, loopStartMs, loopEndMs, showControls) {
        while (true) {
            val isPlayingNow = exoPlayer.isPlaying
            isPlaying = isPlayingNow
            if (!isDraggingSeek) {
                currentPosition = torrentSeekBufferingTarget ?: exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            val lStart = loopStartMs
            val lEnd = loopEndMs
            if (lStart != null && lEnd != null && lEnd > lStart && currentPosition >= lEnd) {
                safeSeek(lStart)
            }
            val hash = activeTorrentHash
            if (hash != null) {
                val state = cinemaDownloads[hash]
                val isActivelyStreaming = state != null &&
                    (state.status == com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.DOWNLOADING ||
                     state.status == com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.STREAMING) &&
                    state.progress < 0.999f
                downloadedRanges = if (isActivelyStreaming) {
                    val ranges = com.example.ymediaplayer.mycinema.service.TorrentEngine.getDownloadedRanges(hash)
                    if (ranges.isNotEmpty()) ranges else emptyList()
                } else {
                    emptyList()
                }
            } else {
                downloadedRanges = emptyList()
            }
            // When controls are hidden and A-B loop is not active, relaxing delay frees CPU/GPU for 60/120fps video rendering
            val pollDelay = if (!showControls && lStart == null) 1000.milliseconds else if (isPlayingNow) 200.milliseconds else 500.milliseconds
            delay(pollDelay)
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
                    if (streamTorrentHash != null) {
                        val historyList = appPreferences.getCinemaWatchHistory()
                        val existing = historyList.find {
                            it.title.equals(streamTitle, ignoreCase = true) ||
                            it.title.equals(videoTitle, ignoreCase = true)
                        }
                        if (existing != null) {
                            appPreferences.saveCinemaWatchHistory(
                                id = existing.id,
                                title = existing.title,
                                posterUrl = existing.posterUrl ?: streamPosterUrl,
                                isSeries = existing.isSeries,
                                year = existing.year,
                                progressMs = pos,
                                durationMs = dur
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Auto-hide Controls (Stay visible while touching, dragging, dialogs open, or paused) ──
    val isAnySheetOrMenuOpen = showSubtitleTools || showAmbientSettings || modePicker != null || showPlaylistSheet ||
            showSettingsOverlay ||
            showMoreMenuSheet ||
            isMoreExpanded ||
            showDeleteConfirmDialog ||
            showSpeedSheet ||
            showSleepTimerSheet ||
            showAudioTrackSheet ||
            showSubtitleSheet ||
            showThemePickerSheet ||
            showVideoInfoSheet ||
            showRewindJumpMenu ||
            showForwardJumpMenu

    val isControlsBusy = isDraggingSeek ||
            isAnySheetOrMenuOpen

    val autoHideTimeoutMs = remember { appPreferences.getControlsAutoHideTimeoutMs() }

    LaunchedEffect(
        showControls,
        isPlaying,
        isLocked,
        isControlsBusy,
        controlsInteractionTimestamp
    ) {
        if (isLocked || !showControls || isControlsBusy || !isPlaying) {
            return@LaunchedEffect
        }
        val timeout = if (autoHideTimeoutMs > 0L) autoHideTimeoutMs else 3000L
        delay(timeout.milliseconds)
        isQuickActionsExpanded = false
        isMoreExpanded = false
        showControls = false
    }

    LaunchedEffect(showControls) {
        if (!showControls) {
            isMoreExpanded = false
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

    // ─── Sleep Timer Countdown ────────────────────────────────────────────────
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            delay((sleepTimerMinutes * 60).seconds)
            exoPlayer.pause()
            sleepTimerMinutes = 0
            Toast.makeText(context, "Sleep timer finished. Playback stopped.", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Video Playback Notification with Live Progress, MediaStyle & Tap-to-Open ────────
    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    var isVideoNotificationDismissed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VIDEO_NOTIFICATION_CHANNEL_ID,
                "Video Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live playback progress and controls for the playing video"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_VIDEO_PLAY_PAUSE -> {
                        isVideoNotificationDismissed = false
                        togglePlayPause()
                    }
                    ACTION_VIDEO_REWIND -> {
                        safeSeek((exoPlayer.currentPosition - configuredSeekStepMs).coerceAtLeast(0L))
                    }
                    ACTION_VIDEO_FORWARD -> {
                        safeSeek((exoPlayer.currentPosition + configuredSeekStepMs).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L)))
                    }
                    ACTION_VIDEO_CLOSE -> {
                        isVideoNotificationDismissed = true
                        exoPlayer.pause()
                        try {
                            notificationManager.cancel(VIDEO_NOTIFICATION_ID)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_VIDEO_PLAY_PAUSE)
            addAction(ACTION_VIDEO_REWIND)
            addAction(ACTION_VIDEO_FORWARD)
            addAction(ACTION_VIDEO_CLOSE)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
    val closeIntent = remember(context) {
        PendingIntent.getBroadcast(
            context, 104,
            Intent(ACTION_VIDEO_CLOSE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    var isAppInBackground by remember { mutableStateOf(false) }

    // ─── Lifecycle Handling ───────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    previewOriginal = false
                    if (isSpeedHolding) {
                        isSpeedHolding = false
                        val restoreSpeed = if (preHoldSpeed > 0.1f) preHoldSpeed else 1.0f
                        exoPlayer.playbackParameters = PlaybackParameters(restoreSpeed)
                        exoPlayer.setPlaybackSpeed(restoreSpeed)
                    }
                    // In Android, ON_PAUSE is called during PiP transitions and multi-window.
                    // Do NOT pause playback here so PiP continues smoothly!
                    val pos = exoPlayer.currentPosition
                    if (pos > 1000L) {
                        appPreferences.saveVideoProgress(currentUrl, pos, exoPlayer.duration.coerceAtLeast(0L))
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (isSpeedHolding) {
                        isSpeedHolding = false
                        val restoreSpeed = if (preHoldSpeed > 0.1f) preHoldSpeed else 1.0f
                        exoPlayer.playbackParameters = PlaybackParameters(restoreSpeed)
                        exoPlayer.setPlaybackSpeed(restoreSpeed)
                    }
                    isAppInBackground = true
                    val pos = exoPlayer.currentPosition
                    if (pos > 1000L) {
                        appPreferences.saveVideoProgress(currentUrl, pos, exoPlayer.duration.coerceAtLeast(0L))
                    }
                    val isActivityInPip = activity?.isInPictureInPictureMode == true || isInPiP
                    val allowBg = isBackgroundAudio || appPreferences.isBackgroundPlayEnabled() || appPreferences.isPlayDuringCallsEnabled()
                    if (!allowBg && !isActivityInPip) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppInBackground = false
                    ambientIntensity = appPreferences.getAmbientGlowStrength().coerceIn(0.2f, 1.8f)
                    ambientSpread = appPreferences.getVideoAmbientSpread()
                    ambientSaturation = appPreferences.getVideoAmbientSaturation()
                    ambientBatterySaver = appPreferences.isVideoAmbientBatterySaver()
                    isVideoNotificationDismissed = false
                    // Re-apply settings on resume (preserve current video's resizeMode)
                    isAmbientMode = appPreferences.isAmbientModeEnabled()
                    isNightMode = appPreferences.isNightMode()
                    if (appPreferences.isKeepScreenAwake()) {
                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
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
            if (activity?.isFinishing == true && !VideoPlaybackManager.isMiniPlayerActive.value) {
                VideoPlaybackManager.releasePlayer()
            }
            val lp = activity?.window?.attributes
            if (lp != null && lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity?.window?.attributes = lp
            }
        }
    }

    // ─── Back Press & Exit Handling ──────────────────────────────────────────
    var isExitingToMiniPlayer by remember { mutableStateOf(false) }
    var isCurrentVideoDeleted by remember { mutableStateOf(false) }
    val wasPlayingBeforeDeleteDialog = remember { mutableStateOf(false) }

    val handleExit: () -> Unit = {
        if (!isExitingToMiniPlayer) {
            isExitingToMiniPlayer = true
            val hash = activeTorrentHash
            if (hash != null) {
                if (streamTorrentHash != null && !isStreamReady) {
                    com.example.ymediaplayer.mycinema.service.TorrentEngine.cancel(hash)
                    com.example.ymediaplayer.mycinema.service.TorrentEngine.cleanupStream(context, hash)
                } else if (streamTorrentHash != null || cinemaDownloads[hash]?.isStream == true) {
                    com.example.ymediaplayer.mycinema.service.TorrentEngine.pause(hash)
                }
            }
            // Immediately stop audio & playback with zero delay
            try {
                exoPlayer.volume = 0f
                exoPlayer.pause()
                exoPlayer.stop()
            } catch (_: Exception) {}
            try {
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                playerView.player = null
            } catch (_: Exception) {}
            val pos = exoPlayer.currentPosition
            if (!isCurrentVideoDeleted && pos > 1000L) {
                appPreferences.saveVideoProgress(currentUrl, pos, exoPlayer.duration.coerceAtLeast(0L))
            }
            if (!VideoPlaybackManager.isMiniPlayerActive.value) {
                VideoPlaybackManager.releasePlayer()
            }
            onBack()
        }
    }

    val onVideoDeletedSuccessfully: () -> Unit = {
        Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
        val deletedUrl = currentUrl
        isCurrentVideoDeleted = true
        appPreferences.clearVideoProgress(deletedUrl)
        VideoRepository.invalidateCache()

        val normCurrent = deletedUrl.removePrefix("file://")
        val actualIndex = playlistVideos.indexOfFirst {
            it.uri.toString().removePrefix("file://") == normCurrent ||
            it.uri.toString() == deletedUrl
        }.let { if (it >= 0) it else currentVideoIndex }

        val remaining = if (actualIndex in playlistVideos.indices) {
            playlistVideos.filterIndexed { index, _ -> index != actualIndex }
        } else {
            playlistVideos.filter {
                it.uri.toString().removePrefix("file://") != normCurrent &&
                it.uri.toString() != deletedUrl
            }
        }

        VideoPlaybackManager.activePlaylistQueue = remaining.ifEmpty { null }
        if (remaining.isNotEmpty()) {
            isCurrentVideoDeleted = false
            playlistVideos = remaining
            val nextIdx = actualIndex.coerceIn(0, remaining.size - 1)
            val nextVideo = remaining[nextIdx]
            currentVideoIndex = nextIdx
            currentUrl = nextVideo.uri.toString()
            videoTitle = nextVideo.title.substringBeforeLast(".")
            VideoPlaybackManager.currentVideoUrl.value = currentUrl
            VideoPlaybackManager.currentVideoTitle.value = videoTitle
            val nextProgress = appPreferences.getVideoStartPosition(nextVideo.uri.toString())
            val isNextCompleted = appPreferences.isCompleted(nextVideo.uri.toString())

            val initialMeta = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(videoTitle)
                .setArtist("Video")
                .build()
            val mItem = androidx.media3.common.MediaItem.Builder()
                .setUri(nextVideo.uri)
                .setMediaMetadata(initialMeta)
                .build()

            if (nextProgress > 3000L && !isNextCompleted) {
                exoPlayer.setMediaItem(mItem, nextProgress)
            } else {
                exoPlayer.setMediaItem(mItem, 0L)
            }
            exoPlayer.prepare()
            exoPlayer.play()

            val targetNextUri = nextVideo.uri
            coroutineScope.launch(Dispatchers.IO) {
                val thumbBytes = try {
                    com.example.ymediaplayer.util.MediaArtworkHelper.getVideoThumbnailBytes(context, targetNextUri)
                } catch (_: Throwable) { null }
                if (thumbBytes != null) {
                    withContext(Dispatchers.Main) {
                        if (currentUrl == targetNextUri.toString()) {
                            val enrichedMeta = initialMeta.buildUpon()
                                .setArtworkData(thumbBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                            val updatedItem = mItem.buildUpon().setMediaMetadata(enrichedMeta).build()
                            val curIdx = exoPlayer.currentMediaItemIndex
                            if (curIdx >= 0 && curIdx < exoPlayer.mediaItemCount) {
                                exoPlayer.replaceMediaItem(curIdx, updatedItem)
                            }
                        }
                    }
                }
            }
        } else {
            handleExit()
        }
    }

    val handleFileOperation = rememberFileOperationHandler(
        onSuccess = {
            onVideoDeletedSuccessfully()
        },
        onFailure = { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
        }
    )

    val executeDeleteCurrentVideo: () -> Unit = {
        coroutineScope.launch {
            val hash = activeTorrentHash
            if (hash != null) {
                TorrentEngine.cancel(hash)
                TorrentEngine.cleanupStream(context, hash)
                onVideoDeletedSuccessfully()
                return@launch
            }

            val normCurrent = currentUrl.removePrefix("file://")
            val videoItem = playlistVideos.firstOrNull {
                it.uri.toString().removePrefix("file://") == normCurrent ||
                it.uri.toString() == currentUrl
            } ?: if (currentVideoIndex in playlistVideos.indices) playlistVideos[currentVideoIndex] else null

            var targetUri: Uri? = videoItem?.uri

            if (targetUri == null || (targetUri.scheme != "content" && targetUri.authority != "media")) {
                targetUri = try {
                    if (currentUrl.startsWith("content://")) {
                        val parsed = Uri.parse(currentUrl)
                        if (parsed.authority == "com.android.providers.media.documents") {
                            val docId = android.provider.DocumentsContract.getDocumentId(parsed)
                            val id = docId.split(":").getOrNull(1)?.toLongOrNull()
                            if (id != null) {
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            } else parsed
                        } else parsed
                    } else {
                        val cleanPath = Uri.decode(currentUrl.removePrefix("file://"))
                        val file = java.io.File(cleanPath)
                        val proj = arrayOf(MediaStore.Video.Media._ID)
                        val sel = "${MediaStore.Video.Media.DATA} = ?"
                        val args = arrayOf(file.absolutePath)
                        var foundUri: Uri? = null
                        context.contentResolver.query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            proj, sel, args, null
                        )?.use { cur ->
                            if (cur.moveToFirst()) {
                                val id = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                                foundUri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                                )
                            }
                        }
                        foundUri ?: Uri.fromFile(file)
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (targetUri != null) {
                if (targetUri.scheme == "content") {
                    val result = fileManager.deleteVideos(listOf(targetUri))
                    handleFileOperation(result)
                } else {
                    val cleanPath = Uri.decode(targetUri.path ?: currentUrl.removePrefix("file://"))
                    val f = java.io.File(cleanPath)
                    if (f.exists() && f.delete()) {
                        onVideoDeletedSuccessfully()
                    } else {
                        Toast.makeText(context, "Could not delete video file", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Video file not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            streamTorrentHash != null && !isStreamReady -> handleExit()
            isLocked -> isLocked = false
            showDeleteConfirmDialog -> showDeleteConfirmDialog = false
            showQuickControlsBar -> showQuickControlsBar = false
            showSettingsOverlay -> showSettingsOverlay = false
            showSubtitleTools -> showSubtitleTools = false
            showAmbientSettings -> showAmbientSettings = false
            modePicker != null -> { previewOriginal = false; modePicker = null }
            showPlaylistSheet -> showPlaylistSheet = false
            isMoreExpanded -> isMoreExpanded = false
            showMoreMenuSheet -> showMoreMenuSheet = false
            showSpeedSheet -> showSpeedSheet = false
            showSleepTimerSheet -> showSleepTimerSheet = false
            showAudioTrackSheet -> showAudioTrackSheet = false
            showSubtitleSheet -> showSubtitleSheet = false
            showThemePickerSheet -> showThemePickerSheet = false
            showVideoInfoSheet -> showVideoInfoSheet = false
            isLandscape && !isVerticalVideo -> {
                userOrientationOverride = 0
                sensorBucketAtOverride = lastSensorOrientation[0]
                currentOrientationSetting[0] = 0
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            else -> handleExit()
        }
    }

    val controlsTouchModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            var lastTouchResetTime = 0L
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val anyPressed = event.changes.any { it.pressed }
                    if (isTouchingControls != anyPressed) {
                        isTouchingControls = anyPressed
                    }
                    val now = System.currentTimeMillis()
                    if (anyPressed && now - lastTouchResetTime > 1000L) {
                        lastTouchResetTime = now
                        controlsInteractionTimestamp = now
                    }
                }
            } finally {
                isTouchingControls = false
            }
        }
    }

    val currentPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val currentDuration by rememberUpdatedState(duration)
    val currentIsLandscape by rememberUpdatedState(isLandscape)
    val currentIsVerticalVideo by rememberUpdatedState(isVerticalVideo)

    val playerGestureModifier = Modifier.pointerInput(isLocked) {
        if (isLocked) {
            detectTapGestures(
                onTap = { showControls = !showControls }
            )
            return@pointerInput
        }

        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        var preTapShowControls = showControls

        awaitEachGesture {
            preTapShowControls = showControls
            var didActivateSpeedHold = false
            var speedHoldPreSpeed = currentPlaybackSpeed
            try {
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
                val startVolumeRatio = volumePct
                val startPosition = exoPlayer.currentPosition
                currentDragSeekTarget = startPosition
                var lastHapticBrightnessStep = ((startBrightness * 100f) / 5f).roundToInt().coerceIn(0, 20)
                var lastHapticVolumeStep = ((startVolumeRatio * 100f) / 5f).roundToInt().coerceIn(0, 40)
                var lastHapticSeekStep = (startPosition / 5000L).toInt()
                var lastSeekX = startX
                var accumulatedSeek = startPosition.toDouble()
                var lastHoldX = startX
                var speedHoldAccumulatorX = 0f
                var finalDiffX = 0f
                var finalDiffY = 0f
                var currentX = startX
                var currentY = startY
                var pendingOrientationAction: (() -> Unit)? = null
                var lastPinchDist = 0f
                var lastPinchCentroid = Offset.Zero
                var lastPanX = startX
                var lastPanY = startY

                while (true) {
                    val elapsed = System.currentTimeMillis() - downTime

                    // ─── 0.5-Second Speed Hold Activation (Tapping & holding anywhere on screen) ───
                    if (gestureMode == PlayerGestureMode.NONE && elapsed >= 450L && !isDoubleTap) {
                        gestureMode = PlayerGestureMode.SPEED_HOLD
                        activeGestureMode = gestureMode
                        didActivateSpeedHold = true
                        speedHoldPreSpeed = currentPlaybackSpeed
                        preHoldSpeed = currentPlaybackSpeed

                        val targetHoldSpeed = appPreferences.getPressHoldSpeed().let { if (it > 0.1f) it else 2.0f }

                        speedHoldDisplaySpeed = targetHoldSpeed
                        speedHoldAccumulatorX = 0f
                        isSpeedHolding = true
                        showControls = false
                        exoPlayer.playbackParameters = PlaybackParameters(targetHoldSpeed)
                        exoPlayer.setPlaybackSpeed(targetHoldSpeed)
                        lastHoldX = currentX
                        view.performHaptic(HapticType.HEAVY)
                    }

                    val remainingMs = if (gestureMode == PlayerGestureMode.NONE && !isDoubleTap && !didActivateSpeedHold) {
                        (500L - elapsed).coerceAtLeast(1L)
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
                    // Exactly 0.5 second reached while finger is held stationary
                    continue
                }

                // ─── Check for 3 or More Fingers: IGNORE COMPLETELY ───
                val pressedPointers = event.changes.filter { it.pressed }
                if (event.changes.size >= 3 || pressedPointers.size >= 3) {
                    if (gestureMode == PlayerGestureMode.SPEED_HOLD && didActivateSpeedHold) {
                        isSpeedHolding = false
                        exoPlayer.playbackParameters = PlaybackParameters(preHoldSpeed)
                        exoPlayer.setPlaybackSpeed(preHoldSpeed)
                        didActivateSpeedHold = false
                    }
                    if (gestureMode == PlayerGestureMode.SEEK) {
                        isDraggingSeek = false
                        centerSeekText = null
                        centerSeekIcon = null
                    }
                    gestureMode = PlayerGestureMode.IGNORED_DRAG
                    activeGestureMode = PlayerGestureMode.NONE
                    // Do NOT consume pointers so system gestures (e.g. 3-finger screenshot) pass through cleanly!
                    continue
                }

                if (gestureMode == PlayerGestureMode.IGNORED_DRAG) {
                    // Ongoing ignored gesture (e.g. 3 fingers) - do nothing
                    continue
                }

                // ─── Multi-touch Pinch-to-Zoom & Pan Detection (Exactly 2 fingers) ───
                if (pressedPointers.size == 2) {
                    if (gestureMode == PlayerGestureMode.SPEED_HOLD && didActivateSpeedHold) {
                        isSpeedHolding = false
                        exoPlayer.playbackParameters = PlaybackParameters(preHoldSpeed)
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

                    val isExtremeLeft = startX <= screenWidth * 0.35f
                    val isExtremeRight = startX >= screenWidth * 0.65f
                    val isCenterArea = startX in (screenWidth * 0.35f)..(screenWidth * 0.65f)

                    // 1. Edge vertical swipe for Brightness & Volume (Allowed even when pinch-zoomed!)
                    if (isExtremeLeft && moveDistY > 45f && moveDistY > moveDistX * 1.3f && appPreferences.isBrightnessGestureEnabled()) {
                        hasMoved = true
                        gestureMode = PlayerGestureMode.BRIGHTNESS
                        activeGestureMode = gestureMode
                        change.consume()
                    } else if (isExtremeRight && moveDistY > 45f && moveDistY > moveDistX * 1.3f && appPreferences.isVolumeGestureEnabled()) {
                        hasMoved = true
                        gestureMode = PlayerGestureMode.VOLUME
                        activeGestureMode = gestureMode
                        change.consume()
                    } else if (videoZoomScale > 1.1f && (moveDistX > 15f || moveDistY > 15f)) {
                        // 2. Pan zoomed video when dragging across screen or horizontally
                        hasMoved = true
                        gestureMode = PlayerGestureMode.PINCH_ZOOM
                        activeGestureMode = gestureMode
                        lastPanX = currentX
                        lastPanY = currentY
                    } else if (moveDistX > 45f || moveDistY > 45f) {
                        hasMoved = true
                        if (isCenterArea && diffY > 50f && moveDistY > moveDistX * 1.3f && currentIsLandscape && !currentIsVerticalVideo) {
                            // Center downward swipe in landscape returns to portrait
                            gestureMode = PlayerGestureMode.ORIENTATION_SWIPE
                            activeGestureMode = gestureMode
                            pendingOrientationAction = {
                                userOrientationOverride = 0
                                sensorBucketAtOverride = lastSensorOrientation[0]
                                currentOrientationSetting[0] = 0
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                            change.consume()
                        } else if (moveDistX > 45f && moveDistX > moveDistY * 1.2f) {
                            // Horizontal swipe anywhere on the screen = SEEK with preview!
                            if (appPreferences.isSeekGestureEnabled()) {
                                gestureMode = PlayerGestureMode.SEEK
                                activeGestureMode = gestureMode
                                isDraggingSeek = true
                                showControls = true
                                scrubPosition = startPosition.toFloat()
                                change.consume()
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
                        if (isAutoBrightness) {
                            isAutoBrightness = false
                            appPreferences.setAutoBrightnessEnabled(false)
                        }
                        showBrightnessBar = true
                        showVolumeBar = false
                        brightnessTouchTrigger = System.currentTimeMillis()

                        val bStep = ((newB * 100f) / 5f).roundToInt().coerceIn(0, 20)
                        if (bStep != lastHapticBrightnessStep) {
                            lastHapticBrightnessStep = bStep
                            if (bStep == 0 || bStep == 20) {
                                view.performHaptic(HapticType.MEDIUM)
                            } else {
                                view.performLevelHaptic(bStep / 20f)
                            }
                        }
                        change.consume()
                    }
                    PlayerGestureMode.VOLUME -> {
                        val dragRatio = -diffY / screenHeight
                        val newVolRatio = (startVolumeRatio + dragRatio * 2.2f).coerceIn(0f, 2.0f)
                        applyVolumeAndBoost(newVolRatio)
                        val vStep = ((newVolRatio * 100f) / 5f).roundToInt().coerceIn(0, 40)
                        if (vStep != lastHapticVolumeStep) {
                            lastHapticVolumeStep = vStep
                            if (vStep == 0 || vStep == 20 || vStep == 40) {
                                view.performHaptic(HapticType.MEDIUM)
                            } else {
                                view.performLevelHaptic((vStep % 20) / 20f)
                            }
                        }
                        showVolumeBar = true
                        showBrightnessBar = false
                        volumeTouchTrigger = System.currentTimeMillis()
                        change.consume()
                    }
                    PlayerGestureMode.SEEK -> {
                        val fineSeek = currentY < startY - 80f * view.resources.displayMetrics.density
                        val dragRatio = (currentX - lastSeekX) / screenWidth
                        lastSeekX = currentX
                        val fullScreenSeekMs = if (currentDuration > 0L) (currentDuration * 0.25f).toLong().coerceIn(60000L, 600000L) else 120000L
                        accumulatedSeek = (accumulatedSeek + dragRatio * fullScreenSeekMs * if (fineSeek) 0.1 else 1.0)
                            .coerceIn(0.0, currentDuration.coerceAtLeast(0L).toDouble())
                        currentDragSeekTarget = accumulatedSeek.toLong()
                        val delta = currentDragSeekTarget - startPosition
                        scrubPosition = currentDragSeekTarget.toFloat()
                        isDraggingSeek = true
                        showControls = true
                        centerSeekIcon = if (delta >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind
                        val sign = if (delta >= 0) "+" else "-"
                        val absDeltaSec = abs(delta) / 1000L
                        centerSeekText = "${formatTime(currentDragSeekTarget)} (${sign}${absDeltaSec}s)"

                        val seekStep = (currentDragSeekTarget / 5000L).toInt()
                        if (seekStep != lastHapticSeekStep) {
                            lastHapticSeekStep = seekStep
                            view.performHaptic(HapticType.TICK)
                        }
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
                                if (kotlin.math.abs(newSpeed - 1.0f) > 0.05f) {
                                    lastCustomSpeedBeforeNormal = newSpeed
                                }
                                exoPlayer.playbackParameters = PlaybackParameters(newSpeed)
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
                    appPreferences.setLastPlayerBrightness(brightnessPct)
                }
                PlayerGestureMode.VOLUME -> {
                    volumeTouchTrigger = System.currentTimeMillis()
                }
                PlayerGestureMode.SPEED_HOLD -> {
                    if (didActivateSpeedHold) {
                        isSpeedHolding = false
                        exoPlayer.playbackParameters = PlaybackParameters(preHoldSpeed)
                        exoPlayer.setPlaybackSpeed(preHoldSpeed)
                        didActivateSpeedHold = false
                    }
                }
                PlayerGestureMode.SEEK -> {
                    safeSeek(currentDragSeekTarget)
                    isDraggingSeek = false
                    centerSeekText = null
                    centerSeekIcon = null
                }
                PlayerGestureMode.ORIENTATION_SWIPE -> {
                    pendingOrientationAction?.invoke()
                    pendingOrientationAction = null
                }
                PlayerGestureMode.IGNORED_DRAG -> {
                    // Ignored drag - do not trigger tap
                }
                PlayerGestureMode.NONE -> {
                    if (!hasMoved && !didActivateSpeedHold) {
                        // Quick stationary tap!
                        if (isDoubleTap) {
                            lastTapTime = 0L // consume double tap
                            showControls = preTapShowControls // Restore controls state so seeking doesn't flash the UI
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
                                        togglePlayPause()
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
                            if (!showControls || !wasConsumed) {
                                preTapShowControls = showControls
                                lastTapTime = downTime
                                lastTapX = startX
                                lastTapY = startY
                                showControls = !showControls
                                controlsInteractionTimestamp = System.currentTimeMillis()
                            }
                        }
                    }
                }
                else -> {}
            }
        } finally {
            if (isSpeedHolding || didActivateSpeedHold) {
                isSpeedHolding = false
                val restoreSpeed = if (preHoldSpeed > 0.1f) preHoldSpeed else speedHoldPreSpeed
                exoPlayer.playbackParameters = PlaybackParameters(restoreSpeed)
                exoPlayer.setPlaybackSpeed(restoreSpeed)
                didActivateSpeedHold = false
            }
            if (isDraggingSeek) {
                isDraggingSeek = false
                centerSeekText = null
                centerSeekIcon = null
            }
            activeGestureMode = PlayerGestureMode.NONE
        }
    }
}

    LaunchedEffect(showZoomHUD, zoomHUDText) {
        if (showZoomHUD) {
            delay(1500L)
            showZoomHUD = false
        }
    }

    // ─── Fast Exit Check to Prevent Double Screen Overlay ─────────────────────
    if (isExitingToMiniPlayer) {
        Box(modifier = Modifier.fillMaxSize().background(c.baseBackground))
        return
    }

    // ─── PiP PURE VIDEO MODE: SHOW ONLY VIDEO SURFACE (NO SUBTITLES) ──────────
    if (isInPiP) {
        androidx.compose.runtime.key(playerView) {
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
                    applyVideoEffectsToPlayerView(pv, currentVideoRenderEffect)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    CompositionLocalProvider(
        LocalElementTooltipHandler provides { name, desc, icon ->
            activeElementTooltip = ElementTooltipData(name, desc, icon)
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ─── FULLSCREEN VIDEO PLAYER (Landscape & Portrait) ───
            Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(playerGestureModifier)
        ) {
            // Sample the displayed surface, keeping frame updates out of screen recomposition.
            if (isAmbientMode && !isInPiP) {
                VideoAmbientBackdrop(
                    playerView = playerView,
                    mediaUrl = currentUrl,
                    isPlaying = isPlaying,
                    glowStrength = ambientIntensity,
                    spread = ambientSpread,
                    saturation = ambientSaturation,
                    batterySaver = ambientBatterySaver
                )
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
                androidx.compose.runtime.key(playerView) {
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
                            applyVideoEffectsToPlayerView(pv, currentVideoRenderEffect)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                .padding(start = if (isLandscape) 28.dp else 12.dp)
        ) {
            VerticalGestureBar(
                icon = if (isAutoBrightness) Icons.Rounded.BrightnessAuto else Icons.Rounded.LightMode,
                percentage = brightnessPct,
                label = if (isAutoBrightness) "Auto (${(brightnessPct * 100).roundToInt()}%)" else "${(brightnessPct * 100).roundToInt()}%",
                gradient = gestureBrush,
                glowColor = glowColor,
                onHeaderClick = {
                    val nextAuto = !isAutoBrightness
                    isAutoBrightness = nextAuto
                    appPreferences.setAutoBrightnessEnabled(nextAuto)
                    val act = activity
                    val lp = act?.window?.attributes
                    if (lp != null) {
                        if (nextAuto) {
                            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            brightnessPct = getSystemBrightness(context)
                        } else {
                            lp.screenBrightness = sliderToScreenBrightness(brightnessPct)
                            hasUserAdjustedBrightness = true
                        }
                        act.window?.attributes = lp
                    }
                    brightnessTouchTrigger = System.currentTimeMillis()
                },
                onValueChange = {
                    brightnessPct = it.coerceIn(0.01f, 1f)
                    hasUserAdjustedBrightness = true
                    if (isAutoBrightness) {
                        isAutoBrightness = false
                        appPreferences.setAutoBrightnessEnabled(false)
                    }
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
                .padding(end = if (isLandscape) 28.dp else 12.dp)
        ) {
            val isBoost = volumePct > 1.0f
            val boostBrush = remember {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFF3D00),
                        Color(0xFFFF9100),
                        Color(0xFFFFD600)
                    )
                )
            }
            VerticalGestureBar(
                icon = if (isMuted || volumePct <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else if (volumePct < 0.5f) Icons.AutoMirrored.Rounded.VolumeDown else Icons.AutoMirrored.Rounded.VolumeUp,
                percentage = if (isMuted) 0f else volumePct,
                maxPercentage = 2.0f,
                label = if (isMuted) "Muted" else if (isBoost) "${(volumePct * 100).roundToInt()}% ⚡" else "${(volumePct * 100).roundToInt()}%",
                gradient = if (isBoost) boostBrush else gestureBrush,
                glowColor = if (isBoost) Color(0xFFFF9100) else glowColor,
                onValueChange = {
                    if (isMuted) isMuted = false
                    applyVolumeAndBoost(it)
                    showVolumeBar = true
                    volumeTouchTrigger = System.currentTimeMillis()
                }
            )
        }



        // ─── 5. Horizontal Seek Center HUD ────────────────────────────────────
        AnimatedVisibility(
            visible = centerSeekText != null && !isDraggingSeek,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = centerSeekText ?: "",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ─── 6. Double Tap Ripple Indicator (MX/YouTube Style Ripple) ─────────
        AnimatedVisibility(
            visible = doubleTapSeekSeconds > 0 && !isInPiP,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f, animationSpec = tween(120)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.88f, animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                val align = if (doubleTapIsForward) Alignment.CenterEnd else Alignment.CenterStart
                val padding = if (doubleTapIsForward) {
                    PaddingValues(end = if (isLandscape) 96.dp else 28.dp)
                } else {
                    PaddingValues(start = if (isLandscape) 96.dp else 28.dp)
                }
                DoubleTapSeekRippleIndicator(
                    seconds = doubleTapSeekSeconds,
                    isForward = doubleTapIsForward,
                    accentColor = primaryAccent,
                    modifier = Modifier
                        .align(align)
                        .padding(padding)
                )
            }
        }

        // ─── 6a. Center Double-Tap Play/Pause Ripple Indicator (Disabled per user request) ───

        // ─── 6c. Pinch-to-Zoom HUD Indicator ──────────────────────────────────
        AnimatedVisibility(
            visible = showZoomHUD && !isInPiP,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
        ) {
            TextOnlyPlayerFeedback(zoomHUDText)
        }

        // ─── 6d. Quick HUD Indicator (Instant Feedback for Picture, Audio & Sharpness) ─
        AnimatedVisibility(
            visible = quickHUDText != null && !isInPiP,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
        ) {
            TextOnlyPlayerFeedback(quickHUDText.orEmpty(), quickHUDSubtext)
        }

        // ─── Torrent Stream Seek Buffering HUD ─────────────────────────────
        AnimatedVisibility(
            visible = torrentSeekBufferingTarget != null && !isInPiP,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.90f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.90f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.2.dp, Color(0xFF00E676).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF00E676),
                        strokeWidth = 2.5.dp
                    )
                    Column {
                        Text(
                            text = "Buffering stream at ${formatTime(torrentSeekBufferingTarget ?: 0L)}",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val speedStr = activeTorrentHash?.let { com.example.ymediaplayer.mycinema.service.TorrentEngine.getDownloadSpeedFormatted(it) } ?: "Connecting..."
                        Text(
                            text = "Downloading pieces • $speedStr",
                            color = Color(0xFF00E676),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                modifier = Modifier.clickable {
                    isSpeedHolding = false
                    val restoreSpeed = if (preHoldSpeed > 0.1f) preHoldSpeed else 1.0f
                    exoPlayer.playbackParameters = PlaybackParameters(restoreSpeed)
                    exoPlayer.setPlaybackSpeed(restoreSpeed)
                }
            )
        }





        // ─── 7. Main Controls Overlay ─────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls && !isInPiP,
            enter = fadeIn(animationSpec = tween(250, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(250, easing = FastOutLinearInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().then(controlsTouchModifier)) {
                // Top Clean Black Gradient Scrim (No colored washes)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isMoreExpanded) (if (isLandscape) 170.dp else 230.dp) else (if (isLandscape) 100.dp else 130.dp))
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
                    // ─── TOP BAR (Back, Title, Playlist, Speed, Audio, Subtitles, More) ───
                    // ─── TOP BAR & INLINE OVERFLOW CONTROLS ───
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = if (!isLandscape) 14.dp else 4.dp)
                            .fillMaxWidth()
                    ) {
                        val topBarPaddingH = if (isLandscape) 16.dp else 8.dp
                        val topBtnSize = if (isLandscape) 38.dp else 32.dp
                        val topBtnSpacing = if (isLandscape) 6.dp else 3.dp
                        val topIconSize = if (isLandscape) 20.dp else 17.dp

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = topBarPaddingH, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Back button + Video Title + Resolution Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = if (isLandscape) 8.dp else 4.dp)
                            ) {
                                PlayerIconButton(
                                    name = "Back",
                                    description = "Exit video player and return to media list",
                                    onClick = {
                                        view.performHaptic(HapticType.LIGHT)
                                        handleExit()
                                    },
                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                    modifier = Modifier
                                        .size(topBtnSize)
                                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(if (isLandscape) 22.dp else 18.dp)
                                    )
                                }
                                Spacer(Modifier.width(if (isLandscape) 10.dp else 5.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = videoTitle,
                                            color = Color.White,
                                            fontSize = if (isLandscape) 15.sp else 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isHdrVideo) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFF9800).copy(alpha = 0.22f),
                                                border = BorderStroke(0.8.dp, Color(0xFFFFB74D).copy(alpha = 0.8f))
                                            ) {
                                                Text(
                                                    text = hdrTypeLabel ?: "HDR",
                                                    color = Color(0xFFFFCC80),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (resolutionBadge != null) {
                                        Text(
                                            text = resolutionBadge,
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Right: Playlist, Speed, Audio, Subtitles, More
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(topBtnSpacing)
                            ) {
                                // 1. Playlist / Queue
                                PlayerIconButton(
                                    name = "Playlist",
                                    description = "View and select queued videos in current folder",
                                    onClick = { showPlaylistSheet = true },
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    modifier = Modifier
                                        .size(topBtnSize)
                                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = "Playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(topIconSize)
                                    )
                                }

                                // 2. Playback Speed (Shows set speed in expanded form if set or custom)
                                val isCustomSpeed = playbackSpeed != 1.0f
                                Row(
                                    modifier = Modifier
                                        .height(topBtnSize)
                                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                        .clip(CircleShape)
                                        .background(
                                            if (isCustomSpeed) Brush.radialGradient(listOf(primaryAccent.copy(alpha = 0.35f), Color(0xFF11121C)))
                                            else Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C)))
                                        )
                                        .border(
                                            1.2.dp,
                                            if (isCustomSpeed) BorderStroke(1.2.dp, primaryAccent).brush
                                            else Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))),
                                            CircleShape
                                        )
                                        .pointerInput(playbackSpeed) {
                                            detectTapGestures(
                                                onTap = { showSpeedSheet = true },
                                                onLongPress = {
                                                    view.performHaptic(HapticType.MEDIUM)
                                                    activeElementTooltip = ElementTooltipData(
                                                        name = "Playback speed",
                                                        description = "Adjust video playback speed (${playbackSpeed}x)",
                                                        icon = Icons.Rounded.Speed
                                                    )
                                                }
                                            )
                                        }
                                        .padding(horizontal = if (isCustomSpeed) 8.dp else (if (isLandscape) 9.dp else 7.dp)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Speed,
                                        contentDescription = "Playback speed",
                                        tint = if (isCustomSpeed) primaryAccent else Color.White,
                                        modifier = Modifier.size(topIconSize)
                                    )
                                    if (isCustomSpeed) {
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "${playbackSpeed}x",
                                            color = primaryAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // 3. Audio Tracks
                                PlayerIconButton(
                                    name = "Audio tracks",
                                    description = "Select multi-language audio or alternate audio stream",
                                    onClick = { showAudioTrackSheet = true },
                                    icon = Icons.Rounded.MusicNote,
                                    modifier = Modifier
                                        .size(topBtnSize)
                                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = "Audio track",
                                        tint = Color.White,
                                        modifier = Modifier.size(topIconSize)
                                    )
                                }

                                // 4. Subtitles
                                PlayerIconButton(
                                    name = "Subtitles",
                                    description = "Choose embedded subtitles or load external subtitle file",
                                    onClick = { showSubtitleSheet = true },
                                    icon = Icons.Rounded.Subtitles,
                                    modifier = Modifier
                                        .size(topBtnSize)
                                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Rounded.Subtitles,
                                        contentDescription = "Subtitles",
                                        tint = Color.White,
                                        modifier = Modifier.size(topIconSize)
                                    )
                                }

                                // 5. More Options (Toggles inline expanded controls without extra menu)
                                PlayerIconButton(
                                    name = if (isMoreExpanded) "Collapse options" else "More options",
                                    description = "Toggle additional controls, sleep timer, theme and settings",
                                    onClick = {
                                        view.performHaptic(HapticType.LIGHT)
                                        isMoreExpanded = !isMoreExpanded
                                    },
                                    icon = Icons.Default.MoreVert,
                                    modifier = Modifier
                                        .size(topBtnSize)
                                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.7f))
                                        .clip(CircleShape)
                                        .background(
                                            if (isMoreExpanded) Brush.radialGradient(listOf(primaryAccent.copy(alpha = 0.35f), Color(0xFF11121C)))
                                            else Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C)))
                                        )
                                        .border(
                                            1.2.dp,
                                            if (isMoreExpanded) BorderStroke(1.2.dp, primaryAccent).brush
                                            else Brush.verticalGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.15f), Color.Black.copy(0.5f))),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = if (isMoreExpanded) primaryAccent else Color.White,
                                        modifier = Modifier.size(topIconSize)
                                    )
                                }
                            }
                        }

                        // ─── INLINE EXPANDED CONTROLS BAR (Row-wise, no sliding) ─────────
                        AnimatedVisibility(
                            visible = isMoreExpanded,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Autoplay Toggle Chip
                                ExpandedOptionChip(
                                    icon = Icons.Rounded.Autorenew,
                                    label = if (isAutoPlayEnabled) "Auto ON" else "Auto OFF",
                                    isActive = isAutoPlayEnabled,
                                    accentColor = primaryAccent,
                                    onClick = { toggleAutoPlay() }
                                )

                                // 2. Sleep Timer Chip (Shows set duration if set)
                                ExpandedOptionChip(
                                    icon = Icons.Rounded.Timer,
                                    label = if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Sleep Timer",
                                    isActive = sleepTimerMinutes > 0,
                                    accentColor = primaryAccent,
                                    onClick = { showSleepTimerSheet = true }
                                )

                                // 3. Night Mode Chip
                                ExpandedOptionChip(
                                    icon = Icons.Rounded.DarkMode,
                                    label = if (isNightMode) "Night ON" else "Night Mode",
                                    isActive = isNightMode,
                                    accentColor = primaryAccent,
                                    onClick = {
                                        isNightMode = !isNightMode
                                        appPreferences.setNightMode(isNightMode)
                                    }
                                )

                                // 4. Ambient Background Toggle Chip
                                ExpandedOptionChip(
                                    icon = if (isAmbientMode) Icons.Rounded.BlurOn else Icons.Rounded.BlurOff,
                                    label = if (isAmbientMode) "Ambient ON" else "Ambient OFF",
                                    isActive = isAmbientMode,
                                    accentColor = primaryAccent,
                                    onClick = {
                                        toggleAmbientMode()
                                        view.performHaptic(HapticType.LIGHT)
                                    },
                                    onLongClick = {
                                        showAmbientSettings = true
                                        view.performHaptic(HapticType.MEDIUM)
                                    }
                                )

                                // 5. Dynamic Theme Chip (Single tap cycles to next color theme directly)
                                ExpandedOptionChip(
                                    icon = Icons.Rounded.Palette,
                                    label = activeTheme.displayName,
                                    isActive = true,
                                    accentColor = primaryAccent,
                                    onClick = {
                                        themeController.cycleColorTheme()
                                        view.performHaptic(HapticType.LIGHT)
                                    },
                                    onLongClick = {
                                        showThemePickerSheet = true
                                        view.performHaptic(HapticType.MEDIUM)
                                    }
                                )

                                // 6. Video Media Info Chip
                                ExpandedOptionChip(
                                    icon = Icons.Rounded.Info,
                                    label = "Media Info",
                                    isActive = false,
                                    accentColor = primaryAccent,
                                    onClick = { showVideoInfoSheet = true }
                                )

                                // 7. Delete Video Chip
                                ExpandedOptionChip(
                                    icon = Icons.Rounded.Delete,
                                    label = "Delete",
                                    isActive = false,
                                    accentColor = Color(0xFFEF5350),
                                    isDestructive = true,
                                    onClick = {
                                        view.performHaptic(HapticType.LIGHT)
                                        wasPlayingBeforeDeleteDialog.value = exoPlayer.isPlaying
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        }
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // ─── CENTER PLAYBACK CONTROLS (Centered on Active Video Frame) ─
                    if (centerSeekText == null && doubleTapSeekSeconds == 0) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 36.dp else 22.dp)
                        ) {
                            // Previous Video
                            PlayerIconButton(
                                name = "Previous video",
                                description = "Play previous video in playlist queue",
                                onClick = playPreviousVideo,
                                icon = Icons.Rounded.SkipPrevious,
                                enabled = playlistVideos.size > 1,
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), primaryAccent.copy(alpha = 0.35f))), CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous video",
                                    tint = if (playlistVideos.size > 1) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Play/Pause (66dp Radiant Translucent Button)
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .shadow(8.dp, CircleShape, ambientColor = primaryAccent.copy(0.35f), spotColor = Color.Black.copy(0.35f))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                primaryAccent.copy(alpha = 0.65f),
                                                primaryAccent.copy(alpha = 0.40f),
                                                Color.Black.copy(alpha = 0.45f)
                                            )
                                        )
                                    )
                                    .border(1.8.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                    .pointerInput(isPlaying) {
                                        detectTapGestures(
                                            onTap = {
                                                togglePlayPause()
                                            },
                                            onLongPress = {
                                                view.performHaptic(HapticType.MEDIUM)
                                                activeElementTooltip = ElementTooltipData(
                                                    name = if (isPlaying) "Pause" else "Play",
                                                    description = if (isPlaying) "Pause video playback" else "Resume video playback",
                                                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
                                                )
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val isEnded = exoPlayer.playbackState == Player.STATE_ENDED || (duration > 0L && exoPlayer.currentPosition >= duration - 500L)
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else if (isEnded) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            // Next Video
                            PlayerIconButton(
                                name = "Next video",
                                description = "Play next video in playlist queue",
                                onClick = playNextVideo,
                                icon = Icons.Rounded.SkipNext,
                                enabled = playlistVideos.size > 1,
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), primaryAccent.copy(alpha = 0.35f))), CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = "Next video",
                                    tint = if (playlistVideos.size > 1) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // ─── BOTTOM SECTION: Time/Rotate, Progress Bar & 6 Media Control Buttons ──
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // Above Seekbar Row: Left has "00:00 / 00:10", Right has Screen Rotation Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .offset(y = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentT = formatTime(if (isDraggingSeek) scrubPosition.toLong() else currentPosition)
                            val durT = formatTime(duration)
                            Text(
                                text = "$currentT / $durT",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Enhanced mode toggle with original-picture comparison.
                                PlayerIconButton(
                                    name = "Enhanced mode",
                                    description = "Crisp detail, deep dynamic range, and rich cinematic color",
                                    onClick = toggleSuperAiEnhance,
                                    icon = Icons.Rounded.AutoAwesome,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(
                                            elevation = if (isSuperAiEnhance) 8.dp else 4.dp,
                                            shape = CircleShape,
                                            ambientColor = if (isSuperAiEnhance) Color(0xFF00F5D4).copy(alpha = 0.65f) else Color.Black.copy(0.4f),
                                            spotColor = if (isSuperAiEnhance) Color(0xFF00BBF9).copy(alpha = 0.65f) else Color.Black.copy(0.4f)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                            if (isSuperAiEnhance) {
                                                Brush.sweepGradient(
                                                    listOf(
                                                        Color(0xFF00F5D4),
                                                        Color(0xFF00BBF9),
                                                        Color(0xFF9B5DE5),
                                                        Color(0xFFF15BB5),
                                                        Color(0xFF00F5D4)
                                                    )
                                                )
                                            } else {
                                                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.35f)))
                                            }
                                        )
                                        .border(
                                            width = if (isSuperAiEnhance) 1.6.dp else 1.2.dp,
                                            brush = if (isSuperAiEnhance) {
                                                Brush.linearGradient(listOf(Color.White, Color(0xFF00F5D4)))
                                            } else {
                                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), primaryAccent.copy(alpha = 0.35f)))
                                            },
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Enhanced mode",
                                        tint = if (isSuperAiEnhance) Color.White else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // Rotate Screen Button (Floating above seekbar right end)
                                PlayerIconButton(
                                    name = "Rotate screen",
                                    description = "Toggle landscape and portrait video orientation",
                                    onClick = {
                                        val nextOrientation = if (isLandscape) 0 else 1
                                        userOrientationOverride = nextOrientation
                                        sensorBucketAtOverride = lastSensorOrientation[0]
                                        currentOrientationSetting[0] = nextOrientation
                                        activity?.requestedOrientation = if (nextOrientation == 1) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    },
                                    icon = Icons.Rounded.ScreenRotation,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.35f))))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), primaryAccent.copy(alpha = 0.35f))), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Rounded.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // Full-Width Seekbar Slider with Moving Finger Seek Preview
                        val effectiveDuration = maxOf(duration, exoPlayer.duration.coerceAtLeast(0L)).toFloat().coerceAtLeast(1f)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                        ) {
                            val totalWidth = maxWidth
                            val curVal = (if (isDraggingSeek) scrubPosition else currentPosition.toFloat()).coerceIn(0f, effectiveDuration)
                            val playFraction = (curVal / effectiveDuration).coerceIn(0f, 1f)

                            // Finger seeking preview directly above the slider circle (moves with the slider thumb)
                            if (isDraggingSeek && !isInPiP) {
                                val cardLeft = (totalWidth * playFraction - 75.dp).coerceIn(0.dp, (totalWidth - 150.dp).coerceAtLeast(0.dp))
                                val pointerOffset = (totalWidth * playFraction - cardLeft).coerceIn(10.dp, 140.dp)
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = cardLeft,
                                            y = (-145).dp
                                        )
                                        .width(150.dp)
                                        .zIndex(20f)
                                ) {
                                    VideoSeekPreview(
                                        url = currentUrl,
                                        positionMs = scrubPosition.toLong(),
                                        primaryAccent = primaryAccent,
                                        pointerOffset = pointerOffset
                                    )
                                }
                            }

                            Slider(
                                value = curVal,
                                onValueChange = {
                                    isDraggingSeek = true
                                    scrubPosition = it
                                    val dur = effectiveDuration
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
                                valueRange = 0f..effectiveDuration,
                                track = { sliderState ->
                                    val dur = effectiveDuration
                                    val trackVal = (if (isDraggingSeek) scrubPosition else currentPosition.toFloat()).coerceIn(0f, dur)
                                    val trackFraction = (trackVal / dur).coerceIn(0f, 1f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.22f))
                                            .drawBehind {
                                                // Draw true downloaded piece portions in YELLOW
                                                for (range in downloadedRanges) {
                                                    val left = size.width * range.start
                                                    val right = size.width * range.endInclusive
                                                    if (right > left) {
                                                        drawRect(
                                                            color = Color(0xFFFFD600).copy(alpha = 0.90f),
                                                            topLeft = Offset(left, 0f),
                                                            size = Size(right - left, size.height)
                                                        )
                                                    }
                                                }
                                                // Draw active played track in primaryAccent
                                                if (trackFraction > 0f) {
                                                    drawRect(
                                                        color = primaryAccent,
                                                        topLeft = Offset.Zero,
                                                        size = Size(size.width * trackFraction, size.height)
                                                    )
                                                }
                                            }
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
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Bottom controls: Lock, Aspect Ratio and Picture-in-Picture.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .widthIn(min = (configuration.screenWidthDp - 36).coerceAtLeast(0).dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Lock Controls
                            PlayerIconButton(
                                name = "Lock controls",
                                description = "Prevent accidental touches on screen during playback",
                                onClick = {
                                    view.performHaptic(HapticType.MEDIUM)
                                    isLocked = true
                                },
                                icon = Icons.Rounded.LockOpen,
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

                            // 5. Aspect Ratio / Fit Screen
                            PlayerIconButton(
                                name = "Aspect ratio",
                                description = "Cycle fit screen, zoom, stretch, and 16:9 / 4:3 ratios",
                                onClick = cycleResizeMode,
                                icon = Icons.Rounded.FitScreen,
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

                            // 6. Picture-in-Picture
                            PlayerIconButton(
                                name = "Picture-in-Picture",
                                description = "Continue watching video in floating window over other apps",
                                onClick = enterPiP,
                                icon = Icons.Rounded.PictureInPicture,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C))))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(primaryAccent.copy(0.6f), primaryAccent.copy(0.2f))), CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.PictureInPicture,
                                    contentDescription = "PiP",
                                    tint = primaryAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // ─── Screen Locked Floating Unlock Button ─────────────────────
                if (isLocked) {
                    PlayerIconButton(
                        name = "Unlock controls",
                        description = "Restore touch gestures and on-screen playback controls",
                        onClick = { 
                            view.performHaptic(HapticType.MEDIUM)
                            isLocked = false 
                        },
                        icon = Icons.Rounded.Lock,
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
            // ─── Sleek Instant Element Tooltip HUD Badge (Auto-Collapses in 2 Seconds) ───
            AnimatedVisibility(
                visible = activeElementTooltip != null,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.90f, animationSpec = tween(140)),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.90f, animationSpec = tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (!isLandscape) 78.dp else 56.dp)
                    .zIndex(9999f)
            ) {
                activeElementTooltip?.let { tooltip ->
                    ElementTooltipPill(tooltip, primaryAccent)
                }
            }
        } // End of outer Box

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

    LaunchedEffect(showSettingsOverlay) {
        if (!showSettingsOverlay) {
            isAmbientMode = appPreferences.isAmbientModeEnabled()
            ambientIntensity = appPreferences.getAmbientGlowStrength().coerceIn(0.2f, 1.8f)
            ambientSpread = appPreferences.getVideoAmbientSpread()
            ambientBatterySaver = appPreferences.isVideoAmbientBatterySaver()
            pictureStrength = appPreferences.getVideoModeStrength("picture")
            sharpnessStrength = appPreferences.getVideoModeStrength("sharpness")
            videoColorProfile = VideoColorProfile.fromId(appPreferences.getVideoColorProfile())
            videoSharpnessLevel = VideoSharpnessLevel.fromId(appPreferences.getVideoSharpnessLevel())
            currentContrast = appPreferences.getVideoCustomContrast()
            currentSaturation = appPreferences.getVideoCustomSaturation()
            currentBrightness = appPreferences.getVideoCustomBrightness()
            currentWarmth = appPreferences.getVideoCustomWarmth()
            currentSharpness = appPreferences.getVideoCustomSharpness()
            isSuperAiEnhance = appPreferences.isSuperAiEnhanceEnabled()
            videoSoundMode = VideoSoundMode.fromId(appPreferences.getVideoRouteSoundMode(audioRoute))
            audioStrength = appPreferences.getVideoRouteSoundStrength(audioRoute)
            volumeLeveling = appPreferences.isVideoVolumeLevelingEnabled(audioRoute)
            isNightMode = appPreferences.isNightMode()
            isBackgroundAudio = appPreferences.isBackgroundPlayEnabled()
            resizeMode = appPreferences.getDefaultResizeMode()
            subtitleDesign = appPreferences.getSubtitleDesign()
            applySubtitleDesign(playerView, subtitleDesign, appPreferences)
            if (appPreferences.isKeepScreenAwake()) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                            .fillMaxWidth(0.52f)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF161824),
                                        Color(0xFF10111A),
                                        primaryAccent.copy(alpha = 0.14f)
                                    )
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        primaryAccent.copy(alpha = 0.6f),
                                        Color.White.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                            .pointerInput(Unit) {
                                detectTapGestures { /* consume taps inside drawer */ }
                            }
                            .systemBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                                                model = remember(item.uri) {
                                                    buildVideoThumbnailRequest(context, item.uri, item.duration)
                                                },
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
                },
                accentColor = primaryAccent,
                onDismiss = { showSleepTimerSheet = false }
            )
        }

        // ─── Theme Picker Dialog (Right-Docked) ───────────────────────────────
        if (showThemePickerSheet) {
            RightSidePanelDialog(
                onDismiss = { showThemePickerSheet = false },
                accentColor = primaryAccent,
                title = "Player Themes",
                icon = Icons.Rounded.Palette,
                maxWidthDp = 350
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(theme.primaryAccent, theme.secondaryAccent)))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(theme.displayName, color = if (isSelected) theme.primaryAccent else c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(theme.description, color = c.textSecondary, fontSize = 11.5.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
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

        // ─── Subtitles & Captions Dialog (Tracks + Online Subtitles + Styles) ───
        if (showSubtitleSheet) {
            SubtitlesDialog(
                exoPlayer = exoPlayer,
                videoTitle = videoTitle,
                subtitleState = subtitleState,
                onOpenTools = { showSubtitleSheet = false; showSubtitleTools = true },
                onSelectEmbedded = { subtitleState.useEmbedded(preserveSelection = true) },
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
        if (showSubtitleTools) {
            VideoSubtitleTools(subtitleState, playerView, subtitleDesign,
                onSeek = { safeSeek(it) }, onDismiss = { showSubtitleTools = false })
        }

        if (showAmbientSettings) {
            PlayerAmbientSettings(
                enabled = isAmbientMode,
                intensity = ambientIntensity,
                spread = ambientSpread,
                saturation = ambientSaturation,
                batterySaver = ambientBatterySaver,
                onEnabled = { isAmbientMode = it; appPreferences.setAmbientModeEnabled(it) },
                onIntensity = { ambientIntensity = it; appPreferences.setAmbientGlowStrength(it) },
                onSpread = { ambientSpread = it; appPreferences.setVideoAmbientSpread(it) },
                onSaturation = { ambientSaturation = it; appPreferences.setVideoAmbientSaturation(it) },
                onBatterySaver = { ambientBatterySaver = it; appPreferences.setVideoAmbientBatterySaver(it) },
                onDismiss = { showAmbientSettings = false }
            )
        }

        modePicker?.let { category ->
            val unavailable = when {
                category == "Audio" -> null
                safePlaybackMode -> "Picture adjustments are unavailable in compatibility mode."
                isHdrVideo -> "Original HDR is preserved. Picture adjustments are available for SDR videos."
                isSuperAiEnhance -> "Turn off Enhanced mode to adjust these settings."
                Build.VERSION.SDK_INT < 31 -> "Picture adjustments require Android 12 or newer."
                category == "Sharpness" && Build.VERSION.SDK_INT < 33 -> "Sharpness requires Android 13 or newer."
                else -> null
            }
            val choices = when (category) {
                "Video" -> VideoColorProfile.entries.map { PlayerModeOption(it.id, it.displayName, it.description) }
                "Sharpness" -> VideoSharpnessLevel.entries.map { PlayerModeOption(it.id, it.displayName, it.description) }
                else -> VideoSoundMode.entries.map { PlayerModeOption(it.id, it.displayName, it.description,
                    it == VideoSoundMode.NORMAL || it == VideoSoundMode.NIGHT_MODE || audioEffects.first != null) }
            }
            val selected = when (category) {
                "Video" -> videoColorProfile.id
                "Sharpness" -> videoSharpnessLevel.id
                else -> videoSoundMode.id
            }
            PlayerModePicker(category, choices, selected, primaryAccent, unavailable,
                onDismiss = { previewOriginal = false; modePicker = null },
                strength = when (category) { "Video" -> pictureStrength; "Sharpness" -> sharpnessStrength; else -> audioStrength },
                onStrengthChange = {
                    when (category) {
                        "Video" -> { pictureStrength = it; appPreferences.setVideoModeStrength("picture", it) }
                        "Sharpness" -> { sharpnessStrength = it; appPreferences.setVideoModeStrength("sharpness", it) }
                        else -> { audioStrength = it; appPreferences.setVideoRouteSoundStrength(audioRoute, it) }
                    }
                },
                onReset = {
                    previewOriginal = false
                    if (category != "Audio") {
                        isSuperAiEnhance = false
                        appPreferences.setSuperAiEnhanceEnabled(false)
                    }
                    when (category) {
                        "Video" -> { selectVideoColorProfile(VideoColorProfile.NORMAL); pictureStrength = 1f; appPreferences.setVideoModeStrength("picture", 1f) }
                        "Sharpness" -> { selectVideoSharpness(VideoSharpnessLevel.OFF); sharpnessStrength = 1f; appPreferences.setVideoModeStrength("sharpness", 1f) }
                        else -> { selectVideoSoundMode(VideoSoundMode.NORMAL); audioStrength = 1f; volumeLeveling = false
                            appPreferences.setVideoRouteSoundStrength(audioRoute, 1f); appPreferences.setVideoVolumeLevelingEnabled(audioRoute, false) }
                    }
                },
                previewOriginal = previewOriginal, onPreview = { previewOriginal = it },
                normalize = if (category == "Audio") volumeLeveling else null,
                onNormalizeChange = { volumeLeveling = it; appPreferences.setVideoVolumeLevelingEnabled(audioRoute, it) },
                routeLabel = if (category == "Audio") audioRoute else "",
                onSelect = { id ->
                    view.performHaptic(HapticType.LIGHT)
                    when (category) {
                        "Video" -> selectVideoColorProfile(VideoColorProfile.fromId(id))
                        "Sharpness" -> selectVideoSharpness(VideoSharpnessLevel.fromId(id))
                        else -> selectVideoSoundMode(VideoSoundMode.fromId(id))
                    }
                })
        }

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

        if (showDeleteConfirmDialog) {
            val hasRecycleBin = FileManager.supportsRecycleBin()
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    if (wasPlayingBeforeDeleteDialog.value) {
                        exoPlayer.play()
                    }
                },
                title = {
                    Text(
                        if (hasRecycleBin) "Move to Recycle Bin?" else "Delete Video?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (hasRecycleBin)
                            "\"$videoTitle\" will be moved to your device's Recycle Bin where it can be restored for a limited time."
                        else
                            "Are you sure you want to permanently delete \"$videoTitle\"? This action cannot be undone.",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            isMoreExpanded = false
                            executeDeleteCurrentVideo()
                        }
                    ) {
                        Text(
                            if (hasRecycleBin) "Move to Bin" else "Delete permanently",
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirmDialog = false
                        if (wasPlayingBeforeDeleteDialog.value) {
                            exoPlayer.play()
                        }
                    }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.70f))
                    }
                },
                containerColor = Color(0xFF1E202C),
                shape = RoundedCornerShape(20.dp)
            )
        }
        // ─── Player More Options Menu Bottom Sheet ───────────────────────────
        if (showMoreMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreMenuSheet = false },
                containerColor = c.dropdownBg,
                scrimColor = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        "Player Controls & Features",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))

                    TextButton(onClick = { showMoreMenuSheet = false; showAmbientSettings = true }) {
                        Text("Ambient lighting", color = primaryAccent)
                    }
                    TextButton(onClick = { showMoreMenuSheet = false; showSubtitleTools = true }) {
                        Text("Subtitle timing & search", color = primaryAccent)
                    }

                    // 1. Autoplay Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                toggleAutoPlay()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Autorenew,
                            contentDescription = null,
                            tint = if (isAutoPlayEnabled) primaryAccent else c.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Autoplay Next", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isAutoPlayEnabled) "Auto-advance to next video on completion" else "Disabled", color = c.textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isAutoPlayEnabled,
                            onCheckedChange = { toggleAutoPlay() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryAccent)
                        )
                    }

                    // 2. Sleep Timer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMoreMenuSheet = false
                                showSleepTimerSheet = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (sleepTimerMinutes > 0) primaryAccent else c.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sleep Timer", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m remaining" else "Off", color = c.textSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    // 3. Night Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isNightMode = !isNightMode
                                appPreferences.setNightMode(isNightMode)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = if (isNightMode) primaryAccent else c.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Night Mode", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isNightMode) "Dimmed screen filter active" else "Normal", color = c.textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isNightMode,
                            onCheckedChange = {
                                isNightMode = it
                                appPreferences.setNightMode(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryAccent)
                        )
                    }

                    // 4. A-B Repeat Loop
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (loopStartMs == null) {
                                    loopStartMs = currentPosition
                                } else if (loopEndMs == null) {
                                    if (currentPosition > loopStartMs!!) {
                                        loopEndMs = currentPosition
                                    }
                                } else {
                                    loopStartMs = null
                                    loopEndMs = null
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AllInclusive,
                            contentDescription = null,
                            tint = if (loopStartMs != null) primaryAccent else c.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("A-B Repeat Loop", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val statusText = when {
                                loopStartMs != null && loopEndMs != null -> "Looping [${formatTime(loopStartMs!!)} - ${formatTime(loopEndMs!!)}]"
                                loopStartMs != null -> "Point A set at ${formatTime(loopStartMs!!)}. Tap to set Point B"
                                else -> "Tap to set Point A at current position"
                            }
                            Text(statusText, color = c.textSecondary, fontSize = 12.sp)
                        }
                    }

                    // 5. Add Bookmark
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                appPreferences.saveBookmark(currentUrl, currentPosition, "")
                                bookmarksList = appPreferences.getBookmarks(currentUrl)
                                Toast.makeText(context, "Bookmark saved at ${formatTime(currentPosition)}", Toast.LENGTH_SHORT).show()
                                showMoreMenuSheet = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Add Bookmark", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Save bookmark at ${formatTime(currentPosition)}", color = c.textSecondary, fontSize = 12.sp)
                        }
                    }

                    // 6. Video Media Details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMoreMenuSheet = false
                                showVideoInfoSheet = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Video Details & Media Info", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Codec, resolution, bitrate, audio tracks", color = c.textSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    // 7. Visual Dynamic Theme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMoreMenuSheet = false
                                showThemePickerSheet = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Player Dynamic Theme", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(activeTheme.displayName, color = c.textSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    // 8. Player Settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMoreMenuSheet = false
                                showSettingsOverlay = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Player Settings", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Gestures, decoder, audio boost, subtitle styles", color = c.textSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ─── Pre-Roll Torrent Stream Buffering Overlay ────────────────────────
        if (streamTorrentHash != null && !isStreamReady && !isInPiP) {
            StreamPreRollBufferingOverlay(
                title = if (streamTitle.isNotBlank()) streamTitle else videoTitle,
                posterUrl = streamPosterUrl,
                streamHash = streamTorrentHash,
                onCancel = { handleExit() }
            )
        }

        // ─── First-Time Contextual Feature & Gesture Guide ───────────────────────
        if (showFeatureGuide && !isInPiP && isStreamReady) {
            VideoPlayerFeatureGuide(
                onDismiss = {
                    showFeatureGuide = false
                    appPreferences.setSeenVideoPlayerGuide(true)
                }
            )
        }

    }
}

// ─── PRE-ROLL TORRENT STREAM BUFFERING OVERLAY ──────────────────────────────
@Composable
private fun StreamPreRollBufferingOverlay(
    title: String,
    posterUrl: String,
    streamHash: String,
    onCancel: () -> Unit
) {
    val allDownloads by TorrentEngine.downloads.collectAsState()
    val state = allDownloads[streamHash]
    val dlBytes = state?.downloadedBytes ?: 0L
    val targetBufferBytes = com.example.ymediaplayer.mycinema.service.TorrentEngine.DEFAULT_STREAM_BUFFER_BYTES // ~8MB initial playback buffer
    val progressFraction = (dlBytes.toFloat() / targetBufferBytes.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D14))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
    ) {
        // Blurred backdrop poster if available
        if (posterUrl.isNotBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.2f)
                    .blur(32.dp)
            )
        }

        // Top bar with Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Cancel Stream",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "STREAMING CINEMA",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Center card with loading indicators
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (posterUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Status message
            val statusText = when {
                state?.status == com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.ERROR ->
                    "Connection error: " + (state.error ?: "Failed to connect")
                state == null || state.seeds == 0 -> "Finding seeders & connecting..."
                dlBytes < 1024 * 1024 -> "Connecting to peers..."
                else -> "Buffering stream chunks..."
            }

            Text(
                text = statusText,
                color = Color(0xFF29B6F6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(18.dp))

            // Smooth linear progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF29B6F6))
                            )
                        )
                )
            }

            Spacer(Modifier.height(10.dp))

            // Downloaded vs Buffer size
            val mbBuffered = String.format(java.util.Locale.US, "%.1f MB", dlBytes / (1024.0 * 1024.0))
            Text(
                text = "$mbBuffered / ~8 MB ready",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // Peer & Speed Stats Row
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = state?.speedFormatted ?: "0 KB/s",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFF29B6F6),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${state?.seeds ?: 0} seeds",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Cancel Button
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.9f)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── DOUBLE TAP SEEK RIPPLE INDICATOR (MX/YouTube Style Pulse & Chevrons) ───
@Composable
private fun DoubleTapSeekRippleIndicator(
    seconds: Int,
    isForward: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "seekRipple")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha"
    )
    val chevronShift by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronShift"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ripple ring
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    scaleX = waveScale
                    scaleY = waveScale
                    alpha = waveAlpha
                }
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.35f))
        )

        // Main glass pill
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF012131A),
            border = BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(accentColor.copy(alpha = 0.8f), accentColor.copy(alpha = 0.3f))
                )
            ),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isForward) {
                    Icon(
                        Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { translationX = -chevronShift }
                    )
                    Text(
                        "-$seconds s",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    )
                } else {
                    Text(
                        "+$seconds s",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { translationX = chevronShift }
                    )
                }
            }
        }
    }
}

// ─── FAT VERTICAL GESTURE BAR COMPONENT (Supports up to 200% Volume Boost) ──
@Composable
private fun VerticalGestureBar(
    icon: ImageVector,
    percentage: Float,
    label: String,
    gradient: Brush,
    glowColor: Color,
    onValueChange: (Float) -> Unit,
    onHeaderClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    maxPercentage: Float = 1.0f
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val haptic = LocalHapticFeedback.current
    val clampedPct = percentage.coerceIn(0f, maxPercentage)
    val fillRatio = (clampedPct / maxPercentage).coerceIn(0f, 1f)
    val animatedPct by animateFloatAsState(
        targetValue = fillRatio,
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "smoothGesturePct"
    )

    var lastHapticMilestone by remember { mutableIntStateOf(((fillRatio * 100f) / 5f).roundToInt().coerceIn(0, 20)) }
    val updateValueWithHaptic: (Float) -> Unit = { newVal ->
        val cl = newVal.coerceIn(0f, maxPercentage)
        val fRatio = (cl / maxPercentage).coerceIn(0f, 1f)
        val milestone = ((fRatio * 100f) / 5f).roundToInt().coerceIn(0, 20)
        if (milestone != lastHapticMilestone) {
            lastHapticMilestone = milestone
            if (milestone == 0 || milestone == 20) {
                view.performHaptic(HapticType.MEDIUM)
            } else {
                view.performLevelHaptic(milestone / 20f)
            }
        }
        onValueChange(cl)
    }

    // Outer touch area (48.dp wide: streamlined touch target preventing overlap with center controls)
    Box(
        modifier = modifier
            .width(48.dp)
            .height(200.dp)
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
                            val targetPct = ((1f - (currentY / barHeight)) * maxPercentage).coerceIn(0f, maxPercentage)
                            updateValueWithHaptic(targetPct)
                            change.consume()
                        }
                    }

                    if (!hasMoved) {
                        // Quick stationary tap on the bar
                        view.performHaptic(HapticType.TICK)
                        if (onHeaderClick != null && downY < 48.dp.toPx()) {
                            onHeaderClick()
                        } else if (downY < barHeight * 0.5f) {
                            updateValueWithHaptic((clampedPct + 0.05f * maxPercentage).coerceAtMost(maxPercentage))
                        } else {
                            updateValueWithHaptic((clampedPct - 0.05f * maxPercentage).coerceAtLeast(0f))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Main Frosted Glass Capsule (42.dp wide, 192.dp tall) with native rounded shadow
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(192.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(21.dp),
                    ambientColor = Color.Black.copy(alpha = 0.65f),
                    spotColor = glowColor.copy(alpha = 0.50f)
                )
                .clip(RoundedCornerShape(21.dp))
                // Dark tinted acrylic base
                .background(Color(0xDD0B0C13))
                // Frosted glass micro-gradient
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.35f)
                        )
                    )
                )
                // Specular Glass Rim Border
                .border(
                    width = 1.3.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.45f), // Top glass specular rim
                            glowColor.copy(alpha = 0.65f),   // Mid accent rim
                            Color.White.copy(alpha = 0.12f)  // Bottom subtle rim
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            // Fluid Track Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedPct)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = if (animatedPct > 0.95f) 22.dp else 6.dp, topEnd = if (animatedPct > 0.95f) 22.dp else 6.dp))
                    .background(gradient)
            )

            // 100% Volume boundary divider notch (when bar supports boost > 1.0f)
            if (maxPercentage > 1.0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(1.2.dp)
                        .align(Alignment.Center)
                        .background(Color.White.copy(alpha = 0.45f))
                )
            }

            // 5. Glowing Fluid Meniscus (Top surface line of liquid)
            if (animatedPct in 0.02f..0.98f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .offset { androidx.compose.ui.unit.IntOffset(0, ((-192.dp.toPx() * animatedPct) + 2.dp.toPx()).roundToInt()) }
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.White.copy(alpha = 0.95f),
                                    Color.White.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }

            // 6. Top Specular Glass Reflection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 7. High-Contrast Inner Icons & Typography
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Icon with subtle frosted circular backing for contrast
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (onHeaderClick != null) glowColor.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.28f))
                        .then(
                            if (onHeaderClick != null) {
                                Modifier.border(1.dp, glowColor.copy(alpha = 0.70f), CircleShape)
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Bottom Percentage Label (only keep text, no dark bg card)
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        style = TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.85f),
                                blurRadius = 3f
                            )
                        )
                    )
                }
            }
        }
    }
}

// ─── Temporary Press-Hold Speed Badge (Small Capsule with Bold Icon, No Animation) ───
@Composable
private fun TemporarySpeedHoldBadge(
    speed: Float,
    modifier: Modifier = Modifier
) {
    val label = String.format(LocalConfiguration.current.locales[0], "%.1f× speed", speed)
    TextOnlyPlayerFeedback(label, modifier = modifier)
}

// ─── Element Tooltip State & Provider ───────────────────────────────────────
data class ElementTooltipData(
    val name: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Sleek Instant Element Tooltip HUD Pill (Frosted Card with Crisp Separation) ───
@Composable
private fun ElementTooltipPill(
    tooltip: ElementTooltipData,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE161724),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = 10.dp,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tooltip.icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tooltip.icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                horizontalAlignment = if (tooltip.icon == null) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Text(
                    text = tooltip.name,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!tooltip.description.isNullOrBlank()) {
                    Text(
                        text = tooltip.description,
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

val LocalElementTooltipHandler = staticCompositionLocalOf<((String, String?, ImageVector?) -> Unit)?> { null }

// ─── Reusable Icon Button with Instant Tap-and-Hold Tooltip / Name Display ────
@Composable
private fun PlayerIconButton(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val tooltipHandler = LocalElementTooltipHandler.current
    Box(
        modifier = Modifier.minimumInteractiveComponentSize().then(modifier)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) { contentDescription = name }
            .combinedClickable(enabled = enabled, role = Role.Button,
                onClick = onClick,
                onLongClick = {
                    view.performHaptic(HapticType.MEDIUM)
                    tooltipHandler?.invoke(name, description, icon)
                }),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ─── Compact Quick Action Pill (Close together & space efficient) ────────────
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF22C55E),
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val tooltipHandler = LocalElementTooltipHandler.current
    val coroutineScope = rememberCoroutineScope()
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
            .pointerInput(label, description, icon, onLongClick) {
                detectTapGestures(
                    onPress = {
                        var isHeld = false
                        val job = coroutineScope.launch {
                            delay(220L)
                            isHeld = true
                            view.performHaptic(HapticType.MEDIUM)
                            if (onLongClick != null) {
                                onLongClick()
                            } else {
                                tooltipHandler?.invoke(label, description, icon)
                            }
                        }
                        val released = tryAwaitRelease()
                        job.cancel()
                        if (released && !isHeld) {
                            onClick()
                        }
                    }
                )
            }
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

// ─── Subtitle Style Applicator (Expanded Designs & Custom BG) ────────────────
fun applySubtitleDesign(playerView: PlayerView?, design: Int, appPreferences: AppPreferences? = null) {
    val subtitleView = playerView?.subtitleView ?: return
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    val fontSize = appPreferences?.getSubtitleFontSize()?.toFloat() ?: 18f
    subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)

    val isBgEnabled = appPreferences?.isSubtitleBackgroundEnabled() ?: true
    val textColor = appPreferences?.getSubtitleColor()?.toInt() ?: android.graphics.Color.WHITE
    val bgStyle = appPreferences?.getSubtitleBackgroundStyle() ?: 0
    val outlineStyle = appPreferences?.getSubtitleOutlineStyle() ?: 2

    val customBgColor = if (!isBgEnabled) {
        android.graphics.Color.TRANSPARENT
    } else {
        when (bgStyle) {
            0 -> android.graphics.Color.argb(165, 0, 0, 0) // Semi-transparent Black
            1 -> android.graphics.Color.argb(255, 0, 0, 0) // Solid Deep Black
            2 -> android.graphics.Color.argb(210, 20, 24, 38) // Frosted Dark Glass (Blur Effect)
            3 -> android.graphics.Color.argb(215, 15, 23, 42) // Slate Blue Glass
            4 -> android.graphics.Color.argb(215, 36, 26, 18) // Warm Amber Mist
            else -> android.graphics.Color.argb(165, 0, 0, 0)
        }
    }

    val edgeType = when (outlineStyle) {
        1 -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        0 -> CaptionStyleCompat.EDGE_TYPE_NONE
        else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
    }
    val edgeColor = if (outlineStyle == 0) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK

    val captionStyle = if (appPreferences != null && design == 0) {
        CaptionStyleCompat(
            textColor,
            customBgColor,
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
                customBgColor,
                android.graphics.Color.TRANSPARENT,
                edgeType,
                edgeColor,
                Typeface.DEFAULT_BOLD
            )
            // Design 1: Dark Box - Clean white text on translucent dark-grey box backing
            1 -> CaptionStyleCompat(
                android.graphics.Color.WHITE,
                if (isBgEnabled) android.graphics.Color.argb(215, 26, 27, 34) else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT,
                Typeface.SANS_SERIF
            )
            // Design 2: Golden Film - Warm golden-yellow text with drop shadow
            2 -> CaptionStyleCompat(
                android.graphics.Color.rgb(255, 215, 0),
                if (isBgEnabled) customBgColor else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                android.graphics.Color.argb(220, 0, 0, 0),
                Typeface.DEFAULT_BOLD
            )
            // Design 3: Cyber Neon - High-contrast cyan text with deep backing and cyan neon outline
            3 -> CaptionStyleCompat(
                android.graphics.Color.rgb(0, 240, 255),
                if (isBgEnabled) android.graphics.Color.argb(220, 16, 18, 25) else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.rgb(0, 130, 160),
                Typeface.MONOSPACE
            )
            else -> CaptionStyleCompat(
                textColor,
                customBgColor,
                android.graphics.Color.TRANSPARENT,
                edgeType,
                edgeColor,
                Typeface.DEFAULT_BOLD
            )
        }
    }
    subtitleView.setBottomPaddingFraction(appPreferences?.getSubtitleBottomPadding() ?: 0.08f)
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
            .animateContentSize(animationSpec = tween(240, easing = FastOutSlowInEasing))
            .horizontalScroll(scrollState)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Playback Speed (Press & hold resets to 1.0x)
        QuickActionButton(
            icon = Icons.Rounded.Speed,
            label = if (playbackSpeed != 1f) String.format(LocalConfiguration.current.locales[0], "%.2fx", playbackSpeed) else "Speed",
            isActive = playbackSpeed != 1f,
            activeColor = primaryAccent,
            onLongClick = onResetSpeedToOne,
            onClick = onOpenSpeedDialog
        )

        // 2. Audio Track / Language (Opens Mini Popup Dialog on Audio Tab)
        QuickActionButton(
            icon = Icons.Rounded.Audiotrack,
            label = "Audio",
            description = "Select multi-language audio or alternate audio stream",
            isActive = false,
            activeColor = primaryAccent,
            onClick = onOpenAudioDialog
        )

        // 3. Subtitles (Opens Mini Popup Dialog with 4 Styles Preview + Audio/Sub tracks)
        QuickActionButton(
            icon = Icons.Rounded.Subtitles,
            label = "Subtitles",
            description = "Choose embedded subtitles or load external subtitle file",
            isActive = hasSubtitles,
            activeColor = primaryAccent,
            onClick = onOpenSubtitleDialog
        )

        // 4. Video Details
        QuickActionButton(
            icon = Icons.Rounded.Info,
            label = "Details",
            description = "Display video resolution, codecs, and audio format",
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
                description = "Set point A and point B to loop a specific video section",
                isActive = isLoopActive,
                activeColor = primaryAccent,
                onClick = onToggleLoop
            )

            // Sleep Timer (Opens Mini Popup Dialog)
            QuickActionButton(
                icon = Icons.Rounded.Timer,
                label = if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Timer",
                description = "Set countdown timer to pause video automatically",
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
                description = "Toggle loop off, repeat single video, or repeat all",
                isActive = repeatMode != Player.REPEAT_MODE_OFF,
                activeColor = primaryAccent,
                onClick = onCycleRepeatMode
            )

            // Night Cinema Tint
            QuickActionButton(
                icon = Icons.Rounded.Bedtime,
                label = if (isNightMode) "Night ON" else "Night",
                description = "Reduce screen brightness and apply eye-care dark tint",
                isActive = isNightMode,
                activeColor = primaryAccent,
                onClick = onToggleNightMode
            )

            // Background Audio Playback
            QuickActionButton(
                icon = Icons.Rounded.Headphones,
                label = if (isBackgroundAudio) "BG ON" else "BG Play",
                description = "Continue audio playback when screen is turned off or app minimized",
                isActive = isBackgroundAudio,
                activeColor = primaryAccent,
                onClick = onToggleBackgroundAudio
            )

            // Dynamic Video Ambient Mode
            QuickActionButton(
                icon = if (isAmbientMode) Icons.Rounded.BlurOn else Icons.Rounded.BlurOff,
                label = if (isAmbientMode) "Ambient ON" else "Ambient",
                description = "Cast dynamic colorful glow around video borders",
                isActive = isAmbientMode,
                activeColor = primaryAccent,
                onClick = onToggleAmbientMode
            )

            // Settings
            if (onOpenSettings != null) {
                QuickActionButton(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    description = "Open media player settings and options",
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

// ─── Expanded Option Chip for 3-Dots Inline Menu ─────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedOptionChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    accentColor: Color,
    isDestructive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val effectiveColor = if (isDestructive) Color(0xFFEF5350) else accentColor
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isDestructive) Brush.linearGradient(listOf(Color(0xFFEF5350).copy(alpha = 0.22f), Color(0xFF161824)))
                else if (isActive) Brush.linearGradient(listOf(effectiveColor.copy(alpha = 0.38f), Color(0xFF161824)))
                else Brush.verticalGradient(listOf(Color(0xFF252636), Color(0xFF11121C)))
            )
            .border(
                1.dp,
                if (isDestructive) BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.55f)).brush
                else if (isActive) BorderStroke(1.dp, effectiveColor).brush
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.10f))),
                RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isDestructive) Color(0xFFEF5350) else if (isActive) effectiveColor else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (isDestructive) Color(0xFFEF5350) else if (isActive) effectiveColor else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isDestructive || isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ─── Adaptive Player Sheet Dialog (Bottom Sheet in Portrait, Right-Docked Panel in Landscape) ──
@Composable
private fun RightSidePanelDialog(
    onDismiss: () -> Unit,
    accentColor: Color,
    title: String,
    icon: ImageVector,
    maxWidthDp: Int = 340,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalAppColors.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        ) {
            val panelShape = if (isLandscape) {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            } else {
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            }

            val panelModifier = if (isLandscape) {
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = maxWidthDp.dp)
                    .fillMaxWidth(0.85f)
                    .systemBarsPadding()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .wrapContentHeight()
                    .heightIn(max = (configuration.screenHeightDp * 0.75f).dp)
                    .navigationBarsPadding()
            }

            Box(
                modifier = panelModifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF161824),
                                Color(0xFF10111A),
                                accentColor.copy(alpha = 0.14f)
                            )
                        ),
                        shape = panelShape
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        shape = panelShape
                    )
                    .clip(panelShape)
                    .pointerInput(Unit) { detectTapGestures { /* consume inside */ } }
                    .padding(horizontal = 18.dp, vertical = if (isLandscape) 16.dp else 12.dp)
            ) {
                Column(
                    modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    // Top drag handle indicator in portrait
                    if (!isLandscape) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.32f))
                        )
                    }

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.22f))
                                    .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                            Text(title, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (headerAction != null) {
                                headerAction()
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c.glassBgNested)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = c.glassBorder.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 12.dp))

                    content()
                }
            }
        }
    }
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
    var trackRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { trackRevision++ }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) { trackRevision++ }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE") val revision = trackRevision
    val currentTracks = exoPlayer.currentTracks
    val audioGroups = remember(currentTracks) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }

    RightSidePanelDialog(
        onDismiss = onDismiss,
        accentColor = accentColor,
        title = "Audio Tracks",
        icon = Icons.Rounded.Audiotrack
    ) {
        if (audioGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = c.textHint,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Default Audio Stream",
                        color = c.textPrimary,
                        fontSize = 15.sp,
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
                    .weight(1f),
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

// ─── Subtitles & Captions Dialog (Direct from Subtitles Button) ────────────────
@Composable
private fun SubtitlesDialog(
    exoPlayer: ExoPlayer,
    videoTitle: String = "",
    subtitleState: VideoSubtitleState? = null,
    onOpenTools: () -> Unit,
    onSelectEmbedded: () -> Unit,
    subtitleDesign: Int,
    onSubtitleDesignChange: (Int) -> Unit,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val appPreferences = remember { AppPreferences(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var trackRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { trackRevision++ }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) { trackRevision++ }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE") val revision = trackRevision
    val currentTracks = exoPlayer.currentTracks
    val textGroups = remember(currentTracks) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    }

    RightSidePanelDialog(
        onDismiss = onDismiss,
        accentColor = accentColor,
        title = "Subtitles & Captions",
        icon = Icons.Rounded.Subtitles,
        headerAction = {
            IconButton(
                onClick = onOpenTools,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c.glassBgNested)
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Subtitle tools & timing", tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
    ) {

                // 3 Tabs: Tracks, Online Subtitles, Styles
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
                                fontSize = 11.5.sp,
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
                                "Online 🌐",
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) accentColor else c.textSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 2) accentColor.copy(alpha = 0.25f) else Color.Transparent),
                        text = {
                            Text(
                                "Styles (4)",
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) accentColor else c.textSecondary
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
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                .build()
                                            onSelectEmbedded()
                                            appPreferences.setSubtitlesEnabled(false)
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
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setIgnoredTextSelectionFlags(0)
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .build()
                                                onSelectEmbedded()
                                                appPreferences.setSubtitlesEnabled(true)
                                                format.language?.let { appPreferences.setPreferredSubtitleLanguage(it) }
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
                        // Online Subtitles Tab
                        var onlineQuery by remember(videoTitle) {
                            mutableStateOf(OnlineSubtitleManager.cleanMediaTitle(videoTitle))
                        }
                        var isSearching by remember { mutableStateOf(false) }
                        var searchResults by remember { mutableStateOf<List<OnlineSubtitle>>(emptyList()) }
                        var hasSearched by remember { mutableStateOf(false) }
                        var selectedLanguageFilter by remember { mutableStateOf("All") }
                        var downloadingId by remember { mutableStateOf<String?>(null) }
                        val scope = rememberCoroutineScope()

                        val performSearch = {
                            if (onlineQuery.isNotBlank() && !isSearching) {
                                isSearching = true
                                hasSearched = true
                                scope.launch {
                                    val list = OnlineSubtitleManager.searchOnlineSubtitles(onlineQuery)
                                    searchResults = list
                                    isSearching = false
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            if (!hasSearched && onlineQuery.isNotBlank()) {
                                performSearch()
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 290.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.cardBgElevated)
                                    .border(1.dp, c.glassBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = onlineQuery,
                                    onValueChange = { onlineQuery = it },
                                    textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 12.5.sp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { performSearch() },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Search", tint = accentColor, modifier = Modifier.size(16.dp))
                                }
                            }

                            // Language Filter Chips
                            if (searchResults.isNotEmpty()) {
                                val availableLangs = remember(searchResults) {
                                    listOf("All") + searchResults.map { it.languageName }.distinct().sorted()
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(availableLangs) { lang ->
                                        val isChosen = selectedLanguageFilter == lang
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isChosen) accentColor else c.cardBgElevated)
                                                .border(0.8.dp, if (isChosen) accentColor else c.glassBorder, RoundedCornerShape(8.dp))
                                                .clickable { selectedLanguageFilter = lang }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 10.sp,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChosen) Color.White else c.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            if (isSearching) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    color = accentColor,
                                    trackColor = c.glassBorder
                                )
                            }

                            val filteredResults = remember(searchResults, selectedLanguageFilter) {
                                if (selectedLanguageFilter == "All") searchResults
                                else searchResults.filter { it.languageName == selectedLanguageFilter }
                            }

                            if (!isSearching && hasSearched && filteredResults.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No subtitles found. Try editing the search title above.",
                                        fontSize = 11.5.sp,
                                        color = c.textSecondary
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredResults) { sub ->
                                    val isDownloading = downloadingId == sub.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(c.cardBgElevated)
                                            .border(1.dp, c.glassBorder, RoundedCornerShape(10.dp))
                                            .clickable(enabled = !isDownloading) {
                                                downloadingId = sub.id
                                                scope.launch {
                                                    val file = OnlineSubtitleManager.downloadSubtitle(context, sub)
                                                    downloadingId = null
                                                    if (file != null) {
                                                        val uri = android.net.Uri.fromFile(file).toString()
                                                        subtitleState?.let { s ->
                                                            s.uri = uri
                                                            s.prefs.setExternalSubtitleUri(s.videoUrl, uri)
                                                            s.prefs.setSubtitlesEnabled(true)
                                                        }
                                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                            .build()
                                                        Toast.makeText(context, "Applied: ${sub.languageName} Subtitles", Toast.LENGTH_SHORT).show()
                                                        onDismiss()
                                                    } else {
                                                        Toast.makeText(context, "Failed to download subtitle", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(accentColor.copy(alpha = 0.2f))
                                                        .border(0.8.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        sub.languageName,
                                                        color = accentColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = sub.fileName,
                                                    color = c.textPrimary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (!sub.releaseName.isNullOrBlank()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = sub.releaseName,
                                                    color = c.textSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = accentColor,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Rounded.Download,
                                                contentDescription = "Download and Apply",
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
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
    val view = LocalView.current

    RightSidePanelDialog(
        onDismiss = onDismiss,
        accentColor = accentColor,
        title = "Playback Speed",
        icon = Icons.Rounded.Speed,
        headerAction = {
            if (abs(currentSpeed - 1.0f) >= 0.01f) {
                TextButton(
                    onClick = {
                        view.performHaptic(HapticType.LIGHT)
                        onSpeedChange(1.0f)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Reset 1.0x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User Defined Speed with [-] and [+] Increment/Decrement Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.cardBgElevated)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                    .padding(vertical = 10.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decrement Button (-)
                    IconButton(
                        onClick = {
                            view.performHaptic(HapticType.LIGHT)
                            val newSpd = ((currentSpeed - 0.05f) * 100).roundToInt() / 100f
                            onSpeedChange(newSpd.coerceIn(0.25f, 3.0f))
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(c.glassBgNested)
                            .border(1.dp, c.glassBorder, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease speed", tint = c.textPrimary, modifier = Modifier.size(20.dp))
                    }

                    // Speed Display (e.g. 1.25x)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(LocalConfiguration.current.locales[0], "%.2fx", currentSpeed),
                            color = accentColor,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = when {
                                abs(currentSpeed - 1.0f) < 0.01f -> "Normal Speed"
                                currentSpeed < 1.0f -> "Slower"
                                else -> "Faster"
                            },
                            color = c.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Increment Button (+)
                    IconButton(
                        onClick = {
                            view.performHaptic(HapticType.LIGHT)
                            val newSpd = ((currentSpeed + 0.05f) * 100).roundToInt() / 100f
                            onSpeedChange(newSpd.coerceIn(0.25f, 3.0f))
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(c.glassBgNested)
                            .border(1.dp, c.glassBorder, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase speed", tint = c.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Smooth Continuous Slider with Round Thumb!
            AppSlider(
                value = currentSpeed.coerceIn(0.25f, 3.0f),
                onValueChange = {
                    val rounded = (it * 20).roundToInt() / 20f
                    if (abs(rounded - currentSpeed) >= 0.02f) {
                        view.performHaptic(HapticType.LIGHT)
                        onSpeedChange(rounded)
                    }
                },
                valueRange = 0.25f..3.0f,
                accentColor = accentColor,
                trackHeight = 5.dp,
                thumbSize = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.25x", fontSize = 10.sp, color = c.textSecondary)
                Text("1.0x", fontSize = 10.sp, color = if (abs(currentSpeed - 1.0f) < 0.05f) accentColor else c.textSecondary, fontWeight = FontWeight.Bold)
                Text("2.0x", fontSize = 10.sp, color = c.textSecondary)
                Text("3.0x", fontSize = 10.sp, color = c.textSecondary)
            }

            Spacer(Modifier.height(12.dp))

            // Presets Title & Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Presets",
                    color = c.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))

            val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = abs(currentSpeed - preset) < 0.01f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor else c.cardBgElevated)
                            .border(1.dp, if (isSelected) accentColor else c.glassBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                view.performHaptic(HapticType.LIGHT)
                                onSpeedChange(preset)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${preset}x",
                            color = if (isSelected) Color.White else c.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
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

    RightSidePanelDialog(
        onDismiss = onDismiss,
        accentColor = accentColor,
        title = "Sleep Timer",
        icon = Icons.Rounded.Timer
    ) {
        if (currentMinutes > 0) {
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
            Spacer(Modifier.height(14.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

    RightSidePanelDialog(
        onDismiss = onDismiss,
        accentColor = accentColor,
        title = "Video Details",
        icon = Icons.Rounded.Info,
        maxWidthDp = 380,
        headerAction = {
            IconButton(
                onClick = {
                    val resText = videoFormat?.let { "${it.width} × ${it.height}" } ?: "Hardware Adapted"
                    val fpsText = videoFormat?.frameRate?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" } ?: "Auto"
                    val vCodec = videoFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "Hardware Decoded"
                    val colorInfo = videoFormat?.colorInfo
                    val isHdr = colorInfo != null && (ColorInfo.isTransferHdr(colorInfo) || colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 || colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) || videoFormat?.sampleMimeType?.contains("dolby", ignoreCase = true) == true
                    val hdrText = when {
                        videoFormat?.sampleMimeType?.contains("dolby", ignoreCase = true) == true -> "Dolby Vision"
                        colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG (BT.2020)"
                        colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10 (ST2084 / BT.2020)"
                        isHdr -> "HDR (Wide Color Gamut)"
                        else -> "SDR (Standard Dynamic Range)"
                    }
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
                        appendLine("Dynamic Range: $hdrText")
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
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c.glassBgNested)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy all details", tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
    ) {
        val resText = videoFormat?.let { "${it.width} × ${it.height}" } ?: "Hardware Adapted"
        val fpsText = videoFormat?.frameRate?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" } ?: "Auto"
        val vCodec = videoFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "Hardware Decoded"
        val colorInfo = videoFormat?.colorInfo
        val isHdr = colorInfo != null && (ColorInfo.isTransferHdr(colorInfo) || colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 || colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) || videoFormat?.sampleMimeType?.contains("dolby", ignoreCase = true) == true
        val hdrText = when {
            videoFormat?.sampleMimeType?.contains("dolby", ignoreCase = true) == true -> "Dolby Vision"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG (BT.2020)"
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10 (ST2084 / BT.2020)"
            isHdr -> "HDR (Wide Color Gamut)"
            else -> "SDR (Standard Dynamic Range)"
        }
        val aCodec = audioFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "AAC / Auto"
        val channels = audioFormat?.channelCount?.let { if (it == 2) "Stereo (2ch)" else "$it Channels" } ?: "Stereo"
        val sampleRate = audioFormat?.sampleRate?.takeIf { it > 0 }?.let { "${it} Hz" } ?: "48,000 Hz"

        // Scrollable details list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Title card
            DetailItemRow("Title", title, accentColor)

            // Specs in 2-column grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailSpecCell("Resolution", resText)
                DetailSpecCell("Frame Rate", fpsText)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailSpecCell("Duration", formatTime(durationMs))
                DetailSpecCell("File Size", if (fileSize > 0) formatSize(fileSize) else "—")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailSpecCell("Video Codec", vCodec)
                DetailSpecCell("Dynamic Range", hdrText)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailSpecCell("Audio Codec", aCodec)
                DetailSpecCell("Channels", channels)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailSpecCell("Sample Rate", sampleRate)
                val bitrateText = if (fileSize > 0 && durationMs > 0) "${(fileSize * 8) / durationMs} kbps" else "—"
                DetailSpecCell("Bitrate", bitrateText)
            }

            // Path Card
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

@Composable
private fun RowScope.DetailSpecCell(label: String, value: String) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(c.cardBgElevated)
            .border(0.7.dp, c.glassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column {
            Text(label, color = c.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(value, color = c.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(14.dp),
                                    ambientColor = if (c.isDark) Color.Black.copy(alpha = 0.55f) else c.cardShadowColor,
                                    spotColor = if (c.isDark) Color.Black.copy(alpha = 0.45f) else c.cardShadowColor
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            if (c.isDark) c.cardBgElevated else Color.White,
                                            c.cardBg
                                        )
                                    )
                                )
                                .border(
                                    1.3.dp,
                                    if (searchQuery.isNotBlank()) {
                                        Brush.verticalGradient(listOf(primaryAccent, c.cardBorderHighlight, primaryAccent.copy(alpha = 0.4f)))
                                    } else {
                                        Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                                    },
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = if (searchQuery.isNotBlank()) primaryAccent else c.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    cursorBrush = SolidColor(primaryAccent),
                                    textStyle = TextStyle(
                                        color = c.textPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search videos...",
                                                color = c.textHint,
                                                fontSize = 13.5.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Clear",
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

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

            // Duration text at bottom right of thumbnail (clean translucent pill badge, no text border)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 3.dp, end = 4.dp)
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.5.dp)
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


