package com.example.ymediaplayer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MediaViewType
import com.example.ymediaplayer.data.SortOrder
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.LocalThemeController
import com.example.ymediaplayer.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val themeController = LocalThemeController.current
    val c = LocalAppColors.current

    val primaryAccent = themeController.colorTheme.primaryAccent
    val secondaryAccent = themeController.colorTheme.secondaryAccent

    // ─── Dialog States ────────────────────────────────────────────────────────
    var activeDialog by remember { mutableStateOf<SettingsDialogType?>(null) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showClearBookmarksConfirm by remember { mutableStateOf(false) }

    // ─── Playback States ──────────────────────────────────────────────────────
    var resumeMode by remember { mutableStateOf(appPreferences.getResumeMode()) }
    var rememberPlaybackSpeed by remember { mutableStateOf(appPreferences.isRememberPlaybackSpeed()) }
    var seekSeconds by remember { mutableIntStateOf(appPreferences.getDoubleTapSeekSeconds()) }
    var autoPip by remember { mutableStateOf(appPreferences.isAutoPipEnabled()) }
    var autoPlayNext by remember { mutableStateOf(appPreferences.isAutoPlayNextEnabled()) }
    var rememberPlaylistQueue by remember { mutableStateOf(appPreferences.isRememberPlaylistQueue()) }
    var backgroundPlay by remember { mutableStateOf(appPreferences.isBackgroundPlayEnabled()) }
    var autoHideTimeoutMs by remember { mutableLongStateOf(appPreferences.getControlsAutoHideTimeoutMs()) }
    var defaultResizeMode by remember { mutableIntStateOf(appPreferences.getDefaultResizeMode()) }
    var hwAcceleration by remember { mutableStateOf(appPreferences.isHwAccelerationEnabled()) }
    var keepScreenAwake by remember { mutableStateOf(appPreferences.isKeepScreenAwake()) }

    // ─── Gestures States ──────────────────────────────────────────────────────
    var brightnessGesture by remember { mutableStateOf(appPreferences.isBrightnessGestureEnabled()) }
    var volumeGesture by remember { mutableStateOf(appPreferences.isVolumeGestureEnabled()) }
    var seekGesture by remember { mutableStateOf(appPreferences.isSeekGestureEnabled()) }
    var videoSwitchGesture by remember { mutableStateOf(appPreferences.isVideoSwitchGestureEnabled()) }
    var doubleTapCenterPlayPause by remember { mutableStateOf(appPreferences.isDoubleTapCenterPlayPauseEnabled()) }
    var pressHoldSpeed by remember { mutableFloatStateOf(appPreferences.getPressHoldSpeed()) }
    var hapticsEnabled by remember { mutableStateOf(appPreferences.isHapticsEnabled()) }

    // ─── Subtitles & Audio States ─────────────────────────────────────────────
    var subFontSize by remember { mutableIntStateOf(appPreferences.getSubtitleFontSize()) }
    var subColor by remember { mutableLongStateOf(appPreferences.getSubtitleColor()) }
    var subBgStyle by remember { mutableIntStateOf(appPreferences.getSubtitleBackgroundStyle()) }
    var subOutlineStyle by remember { mutableIntStateOf(appPreferences.getSubtitleOutlineStyle()) }
    var pauseHeadsetDisconnect by remember { mutableStateOf(appPreferences.isPauseOnHeadsetDisconnect()) }

    // ─── Appearance States ────────────────────────────────────────────────────
    var currentThemeMode by remember { mutableStateOf(themeController.mode) }
    var currentColorTheme by remember { mutableStateOf(themeController.colorTheme) }
    var signatureViewEnabled by remember { mutableStateOf(appPreferences.isSignatureViewEnabled()) }
    var signatureSaturation by remember { mutableFloatStateOf(appPreferences.getSignatureSaturation()) }
    var ambientGlowEnabled by remember { mutableStateOf(appPreferences.isAmbientGlowEnabled()) }

    // ─── Library States ───────────────────────────────────────────────────────
    var mediaViewType by remember { mutableStateOf(appPreferences.getMediaViewType()) }
    var defaultSortOrder by remember { mutableStateOf(SortOrder.fromString(appPreferences.getSortOrder())) }
    var showContinueWatching by remember { mutableStateOf(appPreferences.isShowContinueWatching()) }
    var continueWatchingLimit by remember { mutableIntStateOf(appPreferences.getContinueWatchingLimit()) }
    var showRecentlyAdded by remember { mutableStateOf(appPreferences.isShowRecentlyAdded()) }
    var excludeShortClipsSeconds by remember { mutableIntStateOf(appPreferences.getExcludeShortClipsSeconds()) }
    var excludeHiddenFolders by remember { mutableStateOf(appPreferences.isExcludeHiddenFolders()) }
    var confirmDelete by remember { mutableStateOf(appPreferences.isConfirmDeleteEnabled()) }

    // ─── Music States ─────────────────────────────────────────────────────────
    var musicArtworkStyle by remember { mutableStateOf(appPreferences.getMusicArtworkStyle()) }
    var gaplessPlayback by remember { mutableStateOf(appPreferences.isGaplessPlaybackEnabled()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (c.isDark) Brush.verticalGradient(
                    listOf(
                        Color(0xFF22242D),
                        Color(0xFF181921),
                        Color(0xFF14151B)
                    )
                ) else Brush.verticalGradient(listOf(c.baseBackground, c.baseBackground))
            )
            .drawBehind {
                if (c.isDark) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(c.gradientBlob1.copy(alpha = 0.09f), Color.Transparent),
                            center = Offset(150f, 150f),
                            radius = 650f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(c.gradientBlob2.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width - 80f, size.height * 0.65f),
                            radius = 700f
                        )
                    )
                }
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (c.isMatte) Modifier else Modifier.shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor))
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.glassBg)
                        .border(1.2.dp, c.glassBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (c.isMatte) c.cardBgElevated else Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = c.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showResetAllConfirm = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (c.isMatte) c.cardBgElevated else Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Defaults",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Header Profile Banner ────────────────────────────────────────
            item {
                SettingsHeaderCard(
                    themeName = currentColorTheme.displayName,
                    themeMode = currentThemeMode.displayName,
                    primaryAccent = primaryAccent,
                    secondaryAccent = secondaryAccent
                )
            }

            // ─── 1. Playback & Video Engine ───────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Playback & Video Engine",
                    icon = Icons.Rounded.PlayCircleOutline,
                    accentColor = primaryAccent
                ) {
                    SettingsItemPicker(
                        title = "Resume Playback",
                        subtitle = when (resumeMode) {
                            "AUTO" -> "Always auto-resume from saved position"
                            "PROMPT" -> "Show prompt with 'Start Over' option"
                            else -> "Always start from beginning (00:00)"
                        },
                        icon = Icons.Rounded.History,
                        onClick = { activeDialog = SettingsDialogType.RESUME_MODE }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Remember Playback Speed",
                        subtitle = "Preserve custom playback speed across videos",
                        icon = Icons.Rounded.Speed,
                        checked = rememberPlaybackSpeed,
                        onCheckedChange = {
                            rememberPlaybackSpeed = it
                            appPreferences.setRememberPlaybackSpeed(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Double-Tap Seek Step",
                        subtitle = "${seekSeconds} seconds",
                        icon = Icons.Rounded.FastForward,
                        onClick = { activeDialog = SettingsDialogType.SEEK_STEP }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Auto Picture-in-Picture",
                        subtitle = "Enter floating PiP window when swiping home",
                        icon = Icons.Rounded.PictureInPictureAlt,
                        checked = autoPip,
                        onCheckedChange = {
                            autoPip = it
                            appPreferences.setAutoPipEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Background Audio Play",
                        subtitle = "Continue playing audio when app is minimized",
                        icon = Icons.Rounded.Headphones,
                        checked = backgroundPlay,
                        onCheckedChange = {
                            backgroundPlay = it
                            appPreferences.setBackgroundPlayEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Auto-Play Next in Playlist",
                        subtitle = "Automatically play next video when current video finishes",
                        icon = Icons.Rounded.SkipNext,
                        checked = autoPlayNext,
                        onCheckedChange = {
                            autoPlayNext = it
                            appPreferences.setAutoPlayNextEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Remember Playlist Queue",
                        subtitle = "Keep video queue order and remember playlist state",
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        checked = rememberPlaylistQueue,
                        onCheckedChange = {
                            rememberPlaylistQueue = it
                            appPreferences.setRememberPlaylistQueue(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Controls Auto-Hide Timeout",
                        subtitle = if (autoHideTimeoutMs > 0) "${autoHideTimeoutMs / 1000.0} seconds" else "Never while watching",
                        icon = Icons.Rounded.Timer,
                        onClick = { activeDialog = SettingsDialogType.AUTO_HIDE_TIMEOUT }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Default Video Fit Mode",
                        subtitle = when (defaultResizeMode) {
                            0 -> "Fit to Screen"
                            3 -> "Fixed Width"
                            4 -> "Zoom / Crop"
                            else -> "Stretch / Fill"
                        },
                        icon = Icons.Rounded.AspectRatio,
                        onClick = { activeDialog = SettingsDialogType.DEFAULT_RESIZE }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Hardware Acceleration",
                        subtitle = "Use GPU decoders for smooth 4K/60fps playback",
                        icon = Icons.Rounded.Bolt,
                        checked = hwAcceleration,
                        onCheckedChange = {
                            hwAcceleration = it
                            appPreferences.setHwAccelerationEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Keep Screen Awake",
                        subtitle = "Prevent display from sleeping while video plays",
                        icon = Icons.Rounded.WbSunny,
                        checked = keepScreenAwake,
                        onCheckedChange = {
                            keepScreenAwake = it
                            appPreferences.setKeepScreenAwake(it)
                        }
                    )
                }
            }

            // ─── 2. Gestures & Touch Controls ─────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Gestures & Touch Controls",
                    icon = Icons.Rounded.TouchApp,
                    accentColor = secondaryAccent
                ) {
                    SettingsItemToggle(
                        title = "Left Swipe: Screen Brightness",
                        subtitle = "Vertical swipe on left edge adjusts brightness",
                        icon = Icons.Rounded.BrightnessMedium,
                        checked = brightnessGesture,
                        onCheckedChange = {
                            brightnessGesture = it
                            appPreferences.setBrightnessGestureEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Right Swipe: Media Volume",
                        subtitle = "Vertical swipe on right edge adjusts volume",
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        checked = volumeGesture,
                        onCheckedChange = {
                            volumeGesture = it
                            appPreferences.setVolumeGestureEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Horizontal Seek Swipe",
                        subtitle = "Swipe anywhere left or right to scrub timeline",
                        icon = Icons.Rounded.SwapHoriz,
                        checked = seekGesture,
                        onCheckedChange = {
                            seekGesture = it
                            appPreferences.setSeekGestureEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Center Swipe: Video Switch",
                        subtitle = "Swipe up or down in middle to switch next/prev video",
                        icon = Icons.Rounded.SwapVert,
                        checked = videoSwitchGesture,
                        onCheckedChange = {
                            videoSwitchGesture = it
                            appPreferences.setVideoSwitchGestureEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Center Double-Tap: Play / Pause",
                        subtitle = "Quick double tap on center of screen toggles playback",
                        icon = Icons.Rounded.PlayArrow,
                        checked = doubleTapCenterPlayPause,
                        onCheckedChange = {
                            doubleTapCenterPlayPause = it
                            appPreferences.setDoubleTapCenterPlayPauseEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Press & Hold Speed Boost",
                        subtitle = "${pressHoldSpeed}x speed",
                        icon = Icons.Rounded.Speed,
                        onClick = { activeDialog = SettingsDialogType.PRESS_HOLD_SPEED }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Haptic Vibration Feedback",
                        subtitle = "Tactile feedback when seeking or switching controls",
                        icon = Icons.Rounded.Vibration,
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            hapticsEnabled = it
                            appPreferences.setHapticsEnabled(it)
                        }
                    )
                }
            }

            // ─── 3. Subtitles & Audio Engine ──────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Subtitles & Audio Engine",
                    icon = Icons.Rounded.Subtitles,
                    accentColor = primaryAccent
                ) {
                    // Live Subtitle Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.radialGradient(listOf(Color(0xFF222638), Color(0xFF0F111A))))
                            .border(1.dp, primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LIVE SUBTITLE PREVIEW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = primaryAccent.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (subBgStyle) {
                                            0 -> Color.Transparent
                                            2 -> Color.Black
                                            else -> Color.Black.copy(alpha = 0.65f)
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "The quick brown fox jumps over the lazy dog.",
                                    color = Color(subColor),
                                    fontSize = subFontSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        shadow = when (subOutlineStyle) {
                                            1 -> Shadow(color = Color.Black, blurRadius = 8f)
                                            2 -> Shadow(color = Color.Black, blurRadius = 14f)
                                            else -> null
                                        }
                                    )
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Subtitle Font Size",
                        subtitle = "${subFontSize} sp",
                        icon = Icons.Rounded.FormatSize,
                        onClick = { activeDialog = SettingsDialogType.SUB_FONT_SIZE }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Subtitle Text Color",
                        subtitle = when (subColor) {
                            0xFFFFFFFFL -> "White"
                            0xFFFFEB3BL -> "Yellow"
                            0xFF00E5FFL -> "Cyan"
                            0xFF76FF03L -> "Neon Green"
                            else -> "Custom"
                        },
                        icon = Icons.Rounded.Palette,
                        onClick = { activeDialog = SettingsDialogType.SUB_COLOR }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Subtitle Background Box",
                        subtitle = when (subBgStyle) {
                            0 -> "None (Transparent)"
                            2 -> "Solid Black"
                            else -> "Semi-transparent Black"
                        },
                        icon = Icons.Rounded.CheckBoxOutlineBlank,
                        onClick = { activeDialog = SettingsDialogType.SUB_BG_STYLE }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Text Outline & Shadow",
                        subtitle = when (subOutlineStyle) {
                            0 -> "None"
                            1 -> "Soft Drop Shadow"
                            else -> "Bold Outline"
                        },
                        icon = Icons.Rounded.Layers,
                        onClick = { activeDialog = SettingsDialogType.SUB_OUTLINE_STYLE }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Pause on Headset Disconnect",
                        subtitle = "Auto-pause when headphones or Bluetooth disconnect",
                        icon = Icons.Rounded.HeadsetOff,
                        checked = pauseHeadsetDisconnect,
                        onCheckedChange = {
                            pauseHeadsetDisconnect = it
                            appPreferences.setPauseOnHeadsetDisconnect(it)
                        }
                    )
                }
            }

            // ─── 4. Appearance & UI Themes ────────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Appearance & Themes",
                    icon = Icons.Rounded.ColorLens,
                    accentColor = secondaryAccent
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Theme Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val isSelected = currentThemeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) primaryAccent.copy(0.2f) else Color.White.copy(0.06f))
                                        .border(1.2.dp, if (isSelected) primaryAccent else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable {
                                            currentThemeMode = mode
                                            themeController.updateMode(mode)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) primaryAccent else c.textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Color Accent Theme",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlayerTheme.entries.forEach { theme ->
                                val isSelected = currentColorTheme.id == theme.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.primaryAccent)
                                        .border(2.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                        .clickable {
                                            currentColorTheme = theme
                                            themeController.updateColorTheme(theme)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Signature Video Enhancement",
                        subtitle = "Boost colors, dynamic range and visual depth",
                        icon = Icons.Rounded.AutoAwesome,
                        checked = signatureViewEnabled,
                        onCheckedChange = {
                            signatureViewEnabled = it
                            appPreferences.setSignatureViewEnabled(it)
                        }
                    )

                    if (signatureViewEnabled) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vibrance & Saturation", fontSize = 12.sp, color = c.textSecondary)
                                Text("${(signatureSaturation * 100).toInt()}%", fontSize = 12.sp, color = primaryAccent, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = signatureSaturation,
                                onValueChange = {
                                    signatureSaturation = it
                                    appPreferences.setSignatureSaturation(it)
                                },
                                valueRange = 1.0f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = primaryAccent, activeTrackColor = primaryAccent)
                            )
                        }
                    }

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Dynamic Video Ambient Mode",
                        subtitle = "Immersive soft lighting halo cast from video onto player background",
                        icon = Icons.Rounded.BlurOn,
                        checked = ambientGlowEnabled,
                        onCheckedChange = {
                            ambientGlowEnabled = it
                            appPreferences.setAmbientGlowEnabled(it)
                        }
                    )
                }
            }

            // ─── 5. Library, Folders & Storage ────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Library & Folders",
                    icon = Icons.Rounded.FolderOpen,
                    accentColor = primaryAccent
                ) {
                    SettingsItemPicker(
                        title = "Default Media View",
                        subtitle = mediaViewType.displayName,
                        icon = Icons.Rounded.GridView,
                        onClick = { activeDialog = SettingsDialogType.MEDIA_VIEW }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Default Sort Order",
                        subtitle = defaultSortOrder.displayName,
                        icon = Icons.AutoMirrored.Rounded.Sort,
                        onClick = { activeDialog = SettingsDialogType.DEFAULT_SORT }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Show Continue Watching",
                        subtitle = "Display in-progress videos carousel on home",
                        icon = Icons.Rounded.PlayCircle,
                        checked = showContinueWatching,
                        onCheckedChange = {
                            showContinueWatching = it
                            appPreferences.setShowContinueWatching(it)
                        }
                    )

                    if (showContinueWatching) {
                        SettingsDivider()
                        SettingsItemPicker(
                            title = "Continue Watching Limit",
                            subtitle = "$continueWatchingLimit videos",
                            icon = Icons.Rounded.FormatListNumbered,
                            onClick = { activeDialog = SettingsDialogType.CW_LIMIT }
                        )
                    }

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Show Recently Added Feed",
                        subtitle = "Display newest added videos on home screen",
                        icon = Icons.Rounded.NewReleases,
                        checked = showRecentlyAdded,
                        onCheckedChange = {
                            showRecentlyAdded = it
                            appPreferences.setShowRecentlyAdded(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Exclude Short Clips",
                        subtitle = if (excludeShortClipsSeconds > 0) "Under $excludeShortClipsSeconds seconds" else "Show all videos",
                        icon = Icons.Rounded.FilterList,
                        onClick = { activeDialog = SettingsDialogType.EXCLUDE_SHORT_CLIPS }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Exclude Hidden Folders",
                        subtitle = "Hide folders starting with '.' or containing .nomedia",
                        icon = Icons.Rounded.VisibilityOff,
                        checked = excludeHiddenFolders,
                        onCheckedChange = {
                            excludeHiddenFolders = it
                            appPreferences.setExcludeHiddenFolders(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Confirm File Deletions",
                        subtitle = "Show warning dialog before moving items to bin",
                        icon = Icons.Rounded.DeleteForever,
                        checked = confirmDelete,
                        onCheckedChange = {
                            confirmDelete = it
                            appPreferences.setConfirmDeleteEnabled(it)
                        }
                    )
                }
            }

            // ─── 6. Music Player Preferences ──────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Music Player",
                    icon = Icons.Rounded.MusicNote,
                    accentColor = secondaryAccent
                ) {
                    SettingsItemPicker(
                        title = "Now Playing Screen Style",
                        subtitle = when (musicArtworkStyle) {
                            "VINYL" -> "Retro Vinyl Turntable"
                            "CARD" -> "Modern Album Card"
                            "SONIC_REACTOR" -> "Sonic Reactor (Audio-Reactive)"
                            "CASSETTE" -> "Retro Cassette Tape"
                            "CYBER_ORB" -> "Cyber Neon Orb"
                            "WAVE" -> "Waveform Stage"
                            else -> "Modern Album Card"
                        },
                        icon = Icons.Rounded.Album,
                        onClick = { activeDialog = SettingsDialogType.MUSIC_ARTWORK }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Gapless Playback",
                        subtitle = "Preload upcoming track for zero silence between songs",
                        icon = Icons.Rounded.GraphicEq,
                        checked = gaplessPlayback,
                        onCheckedChange = {
                            gaplessPlayback = it
                            appPreferences.setGaplessPlaybackEnabled(it)
                        }
                    )
                }
            }

            // ─── 7. Data, Storage & About ─────────────────────────────────────
            item {
                SettingsCategorySection(
                    title = "Data, Storage & About",
                    icon = Icons.Rounded.Storage,
                    accentColor = Color(0xFFFF7043)
                ) {
                    SettingsItemAction(
                        title = "Clear Continue Watching History",
                        subtitle = "Reset all video progress and playback timestamps",
                        icon = Icons.Rounded.HistoryToggleOff,
                        iconTint = primaryAccent,
                        onClick = { showClearHistoryConfirm = true }
                    )

                    SettingsDivider()

                    SettingsItemAction(
                        title = "Clear Video Bookmarks",
                        subtitle = "Remove all saved timeline bookmarks across videos",
                        icon = Icons.Rounded.BookmarkRemove,
                        iconTint = secondaryAccent,
                        onClick = { showClearBookmarksConfirm = true }
                    )

                    SettingsDivider()

                    SettingsItemAction(
                        title = "Clear Image & Thumbnail Cache",
                        subtitle = "Free internal storage by purging frame cache",
                        icon = Icons.Rounded.CleaningServices,
                        iconTint = Color(0xFFFFB300),
                        onClick = {
                            context.imageLoader.memoryCache?.clear()
                            Toast.makeText(context, "Thumbnail memory cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsDivider()

                    SettingsItemAction(
                        title = "Reset All Settings to Defaults",
                        subtitle = "Restore default settings across the entire player",
                        icon = Icons.Rounded.RestartAlt,
                        iconTint = Color(0xFFFF5252),
                        onClick = { showResetAllConfirm = true }
                    )
                }
            }

            // ─── App Info Card ────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.cardBg)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "YMedia Player",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Version 1.3.0 • Built with Jetpack Compose & Media3",
                            fontSize = 12.sp,
                            color = c.textSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Hardware Accelerated Audio & Video Playback",
                            fontSize = 11.sp,
                            color = primaryAccent.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────
    when (activeDialog) {
        SettingsDialogType.RESUME_MODE -> {
            SettingsOptionDialog(
                title = "Resume Playback Behavior",
                options = listOf(
                    "AUTO" to "Always auto-resume from saved position",
                    "PROMPT" to "Show prompt with 'Start Over' button",
                    "START" to "Always start from beginning (00:00)"
                ),
                selectedKey = resumeMode,
                onSelect = {
                    resumeMode = it
                    appPreferences.setResumeMode(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.SEEK_STEP -> {
            SettingsOptionDialog(
                title = "Double-Tap Seek Step",
                options = listOf(
                    5 to "5 seconds",
                    10 to "10 seconds (Default)",
                    15 to "15 seconds",
                    30 to "30 seconds",
                    60 to "60 seconds (1 minute)"
                ),
                selectedKey = seekSeconds,
                onSelect = {
                    seekSeconds = it
                    appPreferences.setDoubleTapSeekSeconds(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.AUTO_HIDE_TIMEOUT -> {
            SettingsOptionDialog(
                title = "Controls Auto-Hide Duration",
                options = listOf(
                    2000L to "2.0 seconds",
                    3500L to "3.5 seconds (Default)",
                    5000L to "5.0 seconds",
                    7000L to "7.0 seconds",
                    0L to "Never auto-hide"
                ),
                selectedKey = autoHideTimeoutMs,
                onSelect = {
                    autoHideTimeoutMs = it
                    appPreferences.setControlsAutoHideTimeoutMs(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.DEFAULT_RESIZE -> {
            SettingsOptionDialog(
                title = "Default Video Fit Mode",
                options = listOf(
                    0 to "Fit to Screen (Preserve aspect ratio)",
                    4 to "Zoom / Crop to fill",
                    3 to "Fixed Width",
                    1 to "Stretch / Fill screen"
                ),
                selectedKey = defaultResizeMode,
                onSelect = {
                    defaultResizeMode = it
                    appPreferences.setDefaultResizeMode(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.PRESS_HOLD_SPEED -> {
            SettingsOptionDialog(
                title = "Press & Hold Speed Boost",
                options = listOf(
                    1.25f to "1.25x speed",
                    1.5f to "1.5x speed",
                    2.0f to "2.0x speed (Default)",
                    2.5f to "2.5x speed",
                    3.0f to "3.0x speed"
                ),
                selectedKey = pressHoldSpeed,
                onSelect = {
                    pressHoldSpeed = it
                    appPreferences.setPressHoldSpeed(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.SUB_FONT_SIZE -> {
            SettingsOptionDialog(
                title = "Subtitle Font Size",
                options = listOf(
                    14 to "Small (14 sp)",
                    18 to "Medium (18 sp - Default)",
                    22 to "Large (22 sp)",
                    28 to "Extra Large (28 sp)"
                ),
                selectedKey = subFontSize,
                onSelect = {
                    subFontSize = it
                    appPreferences.setSubtitleFontSize(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.SUB_COLOR -> {
            SettingsOptionDialog(
                title = "Subtitle Color",
                options = listOf(
                    0xFFFFFFFFL to "White",
                    0xFFFFEB3BL to "Yellow",
                    0xFF00E5FFL to "Cyan",
                    0xFF76FF03L to "Neon Green"
                ),
                selectedKey = subColor,
                onSelect = {
                    subColor = it
                    appPreferences.setSubtitleColor(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.SUB_BG_STYLE -> {
            SettingsOptionDialog(
                title = "Subtitle Background Box",
                options = listOf(
                    0 to "None (Transparent)",
                    1 to "Semi-transparent Black (Default)",
                    2 to "Solid Black Box"
                ),
                selectedKey = subBgStyle,
                onSelect = {
                    subBgStyle = it
                    appPreferences.setSubtitleBackgroundStyle(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.SUB_OUTLINE_STYLE -> {
            SettingsOptionDialog(
                title = "Text Outline & Shadow",
                options = listOf(
                    0 to "None",
                    1 to "Soft Drop Shadow",
                    2 to "Bold Outline (Default)"
                ),
                selectedKey = subOutlineStyle,
                onSelect = {
                    subOutlineStyle = it
                    appPreferences.setSubtitleOutlineStyle(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.MEDIA_VIEW -> {
            SettingsOptionDialog(
                title = "Default Media View",
                options = MediaViewType.entries.map { it to it.displayName },
                selectedKey = mediaViewType,
                onSelect = {
                    mediaViewType = it
                    appPreferences.saveMediaViewType(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.DEFAULT_SORT -> {
            SettingsOptionDialog(
                title = "Default Sort Order",
                options = listOf(
                    SortOrder.DATE to "Date Added (Newest)",
                    SortOrder.DATE_ASC to "Date Added (Oldest)",
                    SortOrder.NAME to "Name (A to Z)",
                    SortOrder.SIZE to "Size (Largest)",
                    SortOrder.DURATION to "Duration (Longest)"
                ),
                selectedKey = defaultSortOrder,
                onSelect = {
                    defaultSortOrder = it
                    appPreferences.saveSortOrder(it.name)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.CW_LIMIT -> {
            SettingsOptionDialog(
                title = "Continue Watching Limit",
                options = listOf(
                    5 to "5 videos",
                    10 to "10 videos",
                    20 to "20 videos (Default)",
                    30 to "30 videos",
                    50 to "50 videos"
                ),
                selectedKey = continueWatchingLimit,
                onSelect = {
                    continueWatchingLimit = it
                    appPreferences.setContinueWatchingLimit(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.EXCLUDE_SHORT_CLIPS -> {
            SettingsOptionDialog(
                title = "Exclude Short Clips",
                options = listOf(
                    0 to "Show all videos (No filter)",
                    5 to "Hide clips shorter than 5s",
                    10 to "Hide clips shorter than 10s",
                    30 to "Hide clips shorter than 30s"
                ),
                selectedKey = excludeShortClipsSeconds,
                onSelect = {
                    excludeShortClipsSeconds = it
                    appPreferences.setExcludeShortClipsSeconds(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.MUSIC_ARTWORK -> {
            SettingsOptionDialog(
                title = "Now Playing Artwork Style",
                options = listOf(
                    "VINYL" to "Retro Vinyl Turntable",
                    "CARD" to "Modern Album Art Card",
                    "SONIC_REACTOR" to "Sonic Reactor (Audio-Reactive)",
                    "CASSETTE" to "Retro Cassette Tape",
                    "CYBER_ORB" to "Cyber Neon Orb",
                    "WAVE" to "Waveform Stage"
                ),
                selectedKey = musicArtworkStyle,
                onSelect = {
                    musicArtworkStyle = it
                    appPreferences.setMusicArtworkStyle(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        null -> {}
    }

    // ─── Confirmation Dialogs ─────────────────────────────────────────────────
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear Continue Watching?", fontWeight = FontWeight.Bold) },
            text = { Text("This will clear all saved progress and timestamps for in-progress videos.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appPreferences.clearContinueWatchingHistory()
                        showClearHistoryConfirm = false
                        Toast.makeText(context, "Continue Watching cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearBookmarksConfirm) {
        AlertDialog(
            onDismissRequest = { showClearBookmarksConfirm = false },
            title = { Text("Clear All Bookmarks?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all timeline bookmarks saved across all your videos.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appPreferences.clearAllBookmarks()
                        showClearBookmarksConfirm = false
                        Toast.makeText(context, "All bookmarks cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBookmarksConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            title = { Text("Reset All Settings?", fontWeight = FontWeight.Bold) },
            text = { Text("All playback, gesture, subtitle, and appearance preferences will be restored to their factory defaults. Your playlists and favorites will remain untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appPreferences.resetAllSettingsToDefaults()
                        themeController.updateMode(ThemeMode.DARK_GREY)
                        themeController.updateColorTheme(PlayerTheme.CYBER)
                        resumeMode = appPreferences.getResumeMode()
                        rememberPlaybackSpeed = appPreferences.isRememberPlaybackSpeed()
                        seekSeconds = appPreferences.getDoubleTapSeekSeconds()
                        autoPip = appPreferences.isAutoPipEnabled()
                        autoPlayNext = appPreferences.isAutoPlayNextEnabled()
                        rememberPlaylistQueue = appPreferences.isRememberPlaylistQueue()
                        backgroundPlay = appPreferences.isBackgroundPlayEnabled()
                        autoHideTimeoutMs = appPreferences.getControlsAutoHideTimeoutMs()
                        defaultResizeMode = appPreferences.getDefaultResizeMode()
                        hwAcceleration = appPreferences.isHwAccelerationEnabled()
                        keepScreenAwake = appPreferences.isKeepScreenAwake()
                        brightnessGesture = appPreferences.isBrightnessGestureEnabled()
                        volumeGesture = appPreferences.isVolumeGestureEnabled()
                        seekGesture = appPreferences.isSeekGestureEnabled()
                        videoSwitchGesture = appPreferences.isVideoSwitchGestureEnabled()
                        doubleTapCenterPlayPause = appPreferences.isDoubleTapCenterPlayPauseEnabled()
                        pressHoldSpeed = appPreferences.getPressHoldSpeed()
                        hapticsEnabled = appPreferences.isHapticsEnabled()
                        subFontSize = appPreferences.getSubtitleFontSize()
                        subColor = appPreferences.getSubtitleColor()
                        subBgStyle = appPreferences.getSubtitleBackgroundStyle()
                        subOutlineStyle = appPreferences.getSubtitleOutlineStyle()
                        pauseHeadsetDisconnect = appPreferences.isPauseOnHeadsetDisconnect()
                        currentThemeMode = ThemeMode.DARK_GREY
                        currentColorTheme = PlayerTheme.CYBER
                        signatureViewEnabled = false
                        signatureSaturation = 1.25f
                        ambientGlowEnabled = true
                        mediaViewType = MediaViewType.DETAILED_LIST
                        defaultSortOrder = SortOrder.DATE
                        showContinueWatching = true
                        continueWatchingLimit = 20
                        showRecentlyAdded = true
                        excludeShortClipsSeconds = 0
                        excludeHiddenFolders = true
                        confirmDelete = true
                        musicArtworkStyle = "VINYL"
                        gaplessPlayback = true
                        showResetAllConfirm = false
                        Toast.makeText(context, "All settings restored to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    }
}

// ─── Settings Category Card ──────────────────────────────────────────────────
@Composable
private fun SettingsCategorySection(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
            .clip(RoundedCornerShape(22.dp))
            .background(c.cardBg)
            .border(1.2.dp, c.glassBorder, RoundedCornerShape(22.dp))
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
        }

        Spacer(Modifier.height(4.dp))
        content()
    }
}

// ─── Settings Tile Types ─────────────────────────────────────────────────────
@Composable
private fun SettingsItemPicker(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsItemToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = c.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.accentBlue)
        )
    }
}

@Composable
private fun SettingsItemAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = c.textSecondary)
        }
    }
}

@Composable
private fun SettingsDivider() {
    val c = LocalAppColors.current
    HorizontalDivider(
        color = if (c.isMatte) c.glassBorder.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// ─── Header Card ─────────────────────────────────────────────────────────────
@Composable
private fun SettingsHeaderCard(
    themeName: String,
    themeMode: String,
    primaryAccent: Color,
    secondaryAccent: Color
) {
    val c = LocalAppColors.current
    val headerBgModifier = if (c.isMatte) {
        Modifier.background(c.cardBg)
    } else {
        Modifier.background(Brush.linearGradient(listOf(primaryAccent.copy(0.18f), secondaryAccent.copy(0.08f), c.cardBg)))
    }
    val iconBgModifier = if (c.isMatte) {
        Modifier.background(primaryAccent)
    } else {
        Modifier.background(Brush.linearGradient(listOf(primaryAccent, secondaryAccent)))
    }
    val borderModifier = if (c.isMatte) {
        Modifier.border(1.2.dp, c.glassBorder, RoundedCornerShape(22.dp))
    } else {
        Modifier.border(1.2.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (c.isMatte) Modifier else Modifier.shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor))
            .clip(RoundedCornerShape(22.dp))
            .then(headerBgModifier)
            .then(borderModifier)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(iconBgModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "Preferences & Tuning",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$themeName • $themeMode",
                    fontSize = 12.5.sp,
                    color = primaryAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Option Picker Dialog ────────────────────────────────────────────────────
@Composable
private fun <T> SettingsOptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (key, label) ->
                    val isSelected = key == selectedKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(key) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(key) },
                            colors = RadioButtonDefaults.colors(selectedColor = c.accentBlue)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) c.accentBlue else c.textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private enum class SettingsDialogType {
    RESUME_MODE,
    SEEK_STEP,
    AUTO_HIDE_TIMEOUT,
    DEFAULT_RESIZE,
    PRESS_HOLD_SPEED,
    SUB_FONT_SIZE,
    SUB_COLOR,
    SUB_BG_STYLE,
    SUB_OUTLINE_STYLE,
    MEDIA_VIEW,
    DEFAULT_SORT,
    CW_LIMIT,
    EXCLUDE_SHORT_CLIPS,
    MUSIC_ARTWORK
}
