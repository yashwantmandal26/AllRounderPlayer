package com.example.ymediaplayer.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.History
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
import com.example.ymediaplayer.service.MusicService
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.LocalThemeController
import com.example.ymediaplayer.theme.ThemeMode
import com.example.ymediaplayer.ui.MusicSoundMode
import com.example.ymediaplayer.update.UpdateManager
import kotlinx.coroutines.launch

enum class SettingsCategory(val displayName: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.Apps),
    PLAYBACK("Playback", Icons.Rounded.PlayCircleOutline),
    GESTURES("Gestures", Icons.Rounded.TouchApp),
    SUBTITLES("Subtitles", Icons.Rounded.Subtitles),
    APPEARANCE("Appearance", Icons.Rounded.ColorLens),
    LIBRARY("Library", Icons.Rounded.FolderOpen),
    MUSIC("Music", Icons.Rounded.MusicNote),
    STORAGE("Storage", Icons.Rounded.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialCategory: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val themeController = LocalThemeController.current
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val currentVersionName = remember { UpdateManager.getCurrentVersionName(context) }
    var autoCheckUpdates by remember { mutableStateOf(appPreferences.isAutoCheckUpdatesEnabled()) }
    val updateError = UpdateManager.errorMessage.value

    val primaryAccent = themeController.colorTheme.primaryAccent
    val secondaryAccent = themeController.colorTheme.secondaryAccent

    // ─── Dialog States ────────────────────────────────────────────────────────
    var activeDialog by remember { mutableStateOf<SettingsDialogType?>(null) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var showUpdateHistoryDialog by remember { mutableStateOf(false) }
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
    var playDuringCalls by remember { mutableStateOf(appPreferences.isPlayDuringCallsEnabled()) }

    // ─── Gestures States ──────────────────────────────────────────────────────
    var brightnessGesture by remember { mutableStateOf(appPreferences.isBrightnessGestureEnabled()) }
    var volumeGesture by remember { mutableStateOf(appPreferences.isVolumeGestureEnabled()) }
    var seekGesture by remember { mutableStateOf(appPreferences.isSeekGestureEnabled()) }
    var videoSwitchGesture by remember { mutableStateOf(appPreferences.isVideoSwitchGestureEnabled()) }
    var doubleTapCenterPlayPause by remember { mutableStateOf(appPreferences.isDoubleTapCenterPlayPauseEnabled()) }
    var pressHoldSpeed by remember { mutableFloatStateOf(appPreferences.getPressHoldSpeed()) }
    var hapticsEnabled by remember { mutableStateOf(appPreferences.isHapticsEnabled()) }
    var hdrEnabled by remember { mutableStateOf(appPreferences.isHdrEnabled()) }

    // ─── Video Picture & Sharpness Enhancement States ──────────────────────
    var videoColorProfile by remember { mutableStateOf(appPreferences.getVideoColorProfile()) }
    var videoSharpnessLevel by remember { mutableStateOf(appPreferences.getVideoSharpnessLevel()) }
    var videoContrast by remember { mutableFloatStateOf(appPreferences.getVideoCustomContrast()) }
    var videoSaturation by remember { mutableFloatStateOf(appPreferences.getVideoCustomSaturation()) }
    var videoBrightness by remember { mutableFloatStateOf(appPreferences.getVideoCustomBrightness()) }
    var videoWarmth by remember { mutableFloatStateOf(appPreferences.getVideoCustomWarmth()) }
    var videoSharpness by remember { mutableFloatStateOf(appPreferences.getVideoCustomSharpness()) }
    var videoSoundMode by remember { mutableStateOf(appPreferences.getVideoSoundMode()) }

    // ─── Subtitles & Audio States ─────────────────────────────────────────────
    var subFontSize by remember { mutableIntStateOf(appPreferences.getSubtitleFontSize()) }
    var subColor by remember { mutableLongStateOf(appPreferences.getSubtitleColor()) }
    var subBgEnabled by remember { mutableStateOf(appPreferences.isSubtitleBackgroundEnabled()) }
    var subBgStyle by remember { mutableIntStateOf(appPreferences.getSubtitleBackgroundStyle()) }
    var subOutlineStyle by remember { mutableIntStateOf(appPreferences.getSubtitleOutlineStyle()) }
    var pauseHeadsetDisconnect by remember { mutableStateOf(appPreferences.isPauseOnHeadsetDisconnect()) }

    // ─── Search & Category Navigation States ──────────────────────────────────
    val initialSettingsCat = remember(initialCategory) {
        when (initialCategory?.lowercase()) {
            "music" -> SettingsCategory.MUSIC
            "playback" -> SettingsCategory.PLAYBACK
            "gestures" -> SettingsCategory.GESTURES
            "subtitles" -> SettingsCategory.SUBTITLES
            "appearance" -> SettingsCategory.APPEARANCE
            "library" -> SettingsCategory.LIBRARY
            "storage" -> SettingsCategory.STORAGE
            else -> SettingsCategory.ALL
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(initialSettingsCat) }
    val pagerState = rememberPagerState(initialPage = initialSettingsCat.ordinal) { SettingsCategory.entries.size }
    val categoryRowState = rememberLazyListState()

    LaunchedEffect(initialSettingsCat) {
        if (initialSettingsCat != SettingsCategory.ALL) {
            categoryRowState.scrollToItem(initialSettingsCat.ordinal)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val settledCat = SettingsCategory.entries.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (selectedCategory != settledCat) {
            selectedCategory = settledCat
            categoryRowState.animateScrollToItem(pagerState.settledPage)
        }
    }

    // ─── Appearance States ────────────────────────────────────────────────────
    var currentThemeMode by remember { mutableStateOf(themeController.mode) }
    var currentColorTheme by remember { mutableStateOf(themeController.colorTheme) }
    var ambientGlowStrength by remember { mutableFloatStateOf(appPreferences.getAmbientGlowStrength()) }
    var ambientGlowSpread by remember { mutableFloatStateOf(appPreferences.getAmbientGlowSpread()) }
    var ambientGlowSaturation by remember { mutableFloatStateOf(appPreferences.getAmbientGlowSaturation()) }
    var uiCornerStyle by remember { mutableStateOf(appPreferences.getUiCornerStyle()) }
    var signatureViewEnabled by remember { mutableStateOf(appPreferences.isSignatureViewEnabled()) }
    var signatureSaturation by remember { mutableFloatStateOf(appPreferences.getSignatureSaturation()) }
    var ambientGlowEnabled by remember { mutableStateOf(appPreferences.isAmbientGlowEnabled()) }
    var videoAmbientSpread by remember { mutableFloatStateOf(appPreferences.getVideoAmbientSpread()) }
    var videoAmbientSaturation by remember { mutableFloatStateOf(appPreferences.getVideoAmbientSaturation()) }
    var videoAmbientBatterySaver by remember { mutableStateOf(appPreferences.isVideoAmbientBatterySaver()) }

    // ─── Library States ───────────────────────────────────────────────────────
    var defaultStartTab by remember { mutableStateOf(appPreferences.getDefaultStartTab()) }
    var mediaViewType by remember { mutableStateOf(appPreferences.getMediaViewType()) }
    var defaultSortOrder by remember { mutableStateOf(SortOrder.fromString(appPreferences.getSortOrder())) }
    var showContinueWatching by remember { mutableStateOf(appPreferences.isShowContinueWatching()) }
    var continueWatchingLimit by remember { mutableIntStateOf(appPreferences.getContinueWatchingLimit()) }
    var showRecentlyAdded by remember { mutableStateOf(appPreferences.isShowRecentlyAdded()) }
    var excludeShortClipsSeconds by remember { mutableIntStateOf(appPreferences.getExcludeShortClipsSeconds()) }
    var excludeHiddenFolders by remember { mutableStateOf(appPreferences.isExcludeHiddenFolders()) }
    var confirmDelete by remember { mutableStateOf(appPreferences.isConfirmDeleteEnabled()) }
    var isWifiOnlyDownload by remember { mutableStateOf(appPreferences.isWifiOnlyDownloadEnabled()) }

    // ─── Music States ─────────────────────────────────────────────────────────
    var musicArtworkStyle by remember { mutableStateOf(appPreferences.getMusicArtworkStyle()) }
    var includeAudioRecordings by remember { mutableStateOf(appPreferences.isIncludeAudioRecordingsEnabled()) }
    var includeRingtones by remember { mutableStateOf(appPreferences.isIncludeRingtonesEnabled()) }
    var gaplessPlayback by remember { mutableStateOf(appPreferences.isGaplessPlaybackEnabled()) }
    var resumeOnHeadsetConnect by remember { mutableStateOf(appPreferences.isResumeOnHeadsetConnect()) }
    var crossfadeSeconds by remember { mutableIntStateOf(appPreferences.getCrossfadeDurationSeconds()) }
    var defaultSleepMinutes by remember { mutableIntStateOf(appPreferences.getDefaultSleepTimerMinutes()) }
    var sleepTimerFadeOut by remember { mutableStateOf(appPreferences.isSleepTimerFadeOutEnabled()) }
    var audioFocusDucking by remember { mutableStateOf(appPreferences.isAudioFocusDuckingEnabled()) }
    var lockscreenControls by remember { mutableStateOf(appPreferences.isLockscreenControlsEnabled()) }
    var musicSoundMode by remember { mutableStateOf(appPreferences.getMusicSoundMode()) }

    val glowVersion = appPreferences.lastProgressUpdate.longValue
    val satBlue = remember(c.accentBlue, ambientGlowSaturation) { c.accentBlue.adjustSaturation(ambientGlowSaturation) }
    val satGreen = remember(c.accentGreen, ambientGlowSaturation) { c.accentGreen.adjustSaturation(ambientGlowSaturation) }
    val coreAlpha = when {
        c.isDark && c.baseBackground == Color(0xFF000000) -> 0.70f * ambientGlowStrength
        c.isDark -> 0.55f * ambientGlowStrength
        else -> 0.45f * ambientGlowStrength
    }.coerceIn(0f, 0.98f)
    val midAlpha = (coreAlpha * 0.60f).coerceIn(0f, 0.85f)
    val edgeAlpha = (coreAlpha * 0.28f).coerceIn(0f, 0.45f)
    val secAlpha = (coreAlpha * 0.48f).coerceIn(0f, 0.65f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.baseBackground)
            .drawBehind {
                if (coreAlpha > 0.01f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                satBlue.copy(alpha = coreAlpha),
                                satBlue.copy(alpha = midAlpha),
                                satBlue.copy(alpha = edgeAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.width * (1.10f * ambientGlowSpread).coerceIn(0.5f, 3.8f)
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                satGreen.copy(alpha = secAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.75f),
                            radius = size.width * (0.95f * ambientGlowSpread).coerceIn(0.4f, 3.2f)
                        )
                    )
                }
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = c.topBarScrim,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                // Row 1: Header Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
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
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Row 2: Search Input Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = if (c.isDark) Color.Black.copy(alpha = 0.55f) else c.cardShadowColor,
                            spotColor = if (c.isDark) Color.Black.copy(alpha = 0.45f) else c.cardShadowColor
                        )
                        .clip(RoundedCornerShape(16.dp))
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
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
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
                                        text = "Search settings, features, gestures...",
                                        color = c.textSecondary.copy(alpha = 0.65f),
                                        fontSize = 13.sp
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
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = c.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Row 3: Horizontal Category Navigation Chips
                LazyRow(
                    state = categoryRowState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SettingsCategory.entries) { cat ->
                        val isSelected = selectedCategory == cat && searchQuery.isBlank()
                        val chipBg = if (isSelected) primaryAccent.copy(alpha = 0.22f) else if (c.isMatte) c.cardBgElevated else c.glassBg
                        val chipBorder = if (isSelected) primaryAccent else c.glassBorder
                        val textColor = if (isSelected) primaryAccent else c.textSecondary

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedCategory = cat
                                    if (searchQuery.isNotEmpty()) searchQuery = ""
                                    scope.launch {
                                        pagerState.animateScrollToPage(cat.ordinal)
                                    }
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = if (isSelected) primaryAccent else c.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = cat.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
) { paddingValues ->
        @Composable
        fun SettingsCategoryColumn(currentCat: SettingsCategory, queryStr: String, modifier: Modifier = Modifier) {
            val q = queryStr.trim()

            fun itemMatches(title: String, subtitle: String = ""): Boolean {
                if (q.isBlank()) return true
                return title.contains(q, ignoreCase = true) || subtitle.contains(q, ignoreCase = true)
            }

            val showPlayback = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.PLAYBACK)) ||
                    (q.isNotBlank() && (
                        "Playback & Video Engine".contains(q, ignoreCase = true) ||
                        itemMatches("Resume Playback", "Always auto-resume Start Over") ||
                        itemMatches("Remember Playback Speed", "Preserve custom playback speed") ||
                        itemMatches("Double-Tap Seek Step", "seconds") ||
                        itemMatches("Auto Picture-in-Picture", "floating PiP window") ||
                        itemMatches("Background Audio Play", "audio app minimized") ||
                        itemMatches("Auto-Play Next in Playlist", "next video finishes") ||
                        itemMatches("Remember Playlist Queue", "queue order remember") ||
                        itemMatches("Controls Auto-Hide Timeout", "seconds Never auto-hide") ||
                        itemMatches("Default Video Fit Mode", "Fit to Screen Zoom Crop Fixed Width Stretch Fill") ||
                        itemMatches("Hardware Acceleration", "GPU decoders 4K 60fps") ||
                        itemMatches("Keep Screen Awake", "display sleeping") ||
                        itemMatches("Play During Phone Calls", "video music phone calls")
                    ))

            val showGestures = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.GESTURES)) ||
                    (q.isNotBlank() && (
                        "Gestures & Touch Controls".contains(q, ignoreCase = true) ||
                        itemMatches("Left Swipe: Screen Brightness", "brightness") ||
                        itemMatches("Right Swipe: Media Volume", "volume") ||
                        itemMatches("Horizontal Seek Swipe", "scrub timeline preview") ||
                        itemMatches("Center Double-Tap: Play / Pause", "toggles playback") ||
                        itemMatches("Press & Hold Speed Boost", "speed boost hold 2x") ||
                        itemMatches("Haptic Vibration Feedback", "Tactile feedback sliders vibration")
                    ))

            val showSubtitles = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.SUBTITLES)) ||
                    (q.isNotBlank() && (
                        "Subtitles & Styling".contains(q, ignoreCase = true) ||
                        itemMatches("Subtitle Font Size", "size sp") ||
                        itemMatches("Subtitle Text Color", "color text White Yellow Cyan Neon") ||
                        itemMatches("Subtitle Background Box", "background box overlay toggle without") ||
                        itemMatches("Subtitle Background Style", "Semi-transparent Solid Deep Black Frosted Dark Glass Blur Slate Blue Amber") ||
                        itemMatches("Text Outline & Shadow", "Drop Shadow Bold Outline")
                    ))

            val showAppearance = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.APPEARANCE)) ||
                    (q.isNotBlank() && (
                        "Appearance & Themes".contains(q, ignoreCase = true) ||
                        itemMatches("Theme Mode", "Dark Light OLED Black System") ||
                        itemMatches("Color Accent Theme", "Cyber Neon Amber Emerald Rose Sapphire") ||
                        itemMatches("Ambient Gradient Glow", "Subtle Vibrant Neon Boost glow gradient") ||
                        itemMatches("Card Corner Style", "Sleek Modern Extra corners") ||
                        itemMatches("Dynamic Video Ambient Mode", "soft lighting halo video")
                    ))

            val showLibrary = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.LIBRARY)) ||
                    (q.isNotBlank() && (
                        "Library & Folders".contains(q, ignoreCase = true) ||
                        itemMatches("Default Starting Tab", "Video MYCinema Music home tab screen") ||
                        itemMatches("Default Media View", "Grid List Detailed") ||
                        itemMatches("Default Sort Order", "Sort Name Date Size") ||
                        itemMatches("Show Continue Watching", "in-progress videos carousel") ||
                        itemMatches("Continue Watching Limit", "limit videos") ||
                        itemMatches("Show Recently Added Feed", "newest added videos") ||
                        itemMatches("Exclude Short Clips", "Hide clips shorter than") ||
                        itemMatches("Exclude Hidden Folders", "nomedia hidden dot") ||
                        itemMatches("Confirm File Deletions", "warning dialog delete bin")
                    ))

            val showMusic = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.MUSIC)) ||
                    (q.isNotBlank() && (
                        "Music Player".contains(q, ignoreCase = true) ||
                        itemMatches("Now Playing Screen Style", "Vinyl Card Sonic Reactor Cassette Cyber Orb Wave") ||
                        itemMatches("Show Call Recordings & Voice Notes", "recordings voice whatsapp audio memos") ||
                        itemMatches("Show Ringtones & Short Audio", "ringtones notifications alerts short audio") ||
                        itemMatches("Gapless Playback", "silence songs zero preload") ||
                        itemMatches("Pause on Headset / Bluetooth Disconnect", "headphones Bluetooth disconnect pause unplug") ||
                        itemMatches("Resume on Headset Connect", "headphones Bluetooth reconnect auto play") ||
                        itemMatches("Crossfade Between Tracks", "seconds DJ transition fade blend") ||
                        itemMatches("Default Sleep Timer", "minutes timer sleep duration") ||
                        itemMatches("Sleep Timer Fade Out", "gradual volume decrease smooth fade") ||
                        itemMatches("Audio Focus & Call Ducking", "duck notifications phone calls") ||
                        itemMatches("Lock Screen Controls & Album Art", "lockscreen widget cover art") ||
                        itemMatches("Default Equalizer Sound Profile", "Balanced Bass Boost Vocal Rock Club Acoustic EQ preset")
                    ))

            val showStorage = (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.STORAGE)) ||
                    (q.isNotBlank() && (
                        "Data, Storage & About".contains(q, ignoreCase = true) ||
                        itemMatches("Clear Continue Watching History", "progress timestamps") ||
                        itemMatches("Clear Video Bookmarks", "bookmarks") ||
                        itemMatches("Clear Image & Thumbnail Cache", "cache storage memory") ||
                        itemMatches("Check for Updates", "update version release latest github") ||
                        itemMatches("Auto-Check for Updates", "automatic update notify") ||
                        itemMatches("Reset All Settings to Defaults", "restore factory defaults")
                    ))

            val anySectionVisible = showPlayback || showGestures || showSubtitles || showAppearance || showLibrary || showMusic || showStorage

            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── Header Profile Banner (Shown when browsing 'All' and not searching) ───
                if (q.isBlank() && currentCat == SettingsCategory.ALL) {
                    item {
                        SettingsHeaderCard(
                            themeName = currentColorTheme.displayName,
                            themeMode = currentThemeMode.displayName,
                            primaryAccent = primaryAccent,
                            secondaryAccent = secondaryAccent
                        )
                    }
                }

                // ─── 0. Top Update Available Banner (Shown only when a new release is detected) ───
                val detectedUpdate = UpdateManager.availableUpdate.value
                if (detectedUpdate != null && currentCat != SettingsCategory.STORAGE) {
                item {
                    val isCheckingUpdates = UpdateManager.isChecking.value
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.cardBg)
                            .border(1.2.dp, primaryAccent.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                            .clickable {
                                if (!isCheckingUpdates) {
                                    scope.launch {
                                        val res = UpdateManager.checkForUpdates(context, appPreferences, isManual = true)
                                        if (res.isSuccess && res.getOrNull() == null) {
                                            Toast.makeText(context, "You are on the latest version (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val detectedUpdate = UpdateManager.availableUpdate.value
                            val cardAccent = if (detectedUpdate != null) Color(0xFF10B981) else primaryAccent

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(cardAccent.copy(alpha = 0.18f))
                                    .border(1.dp, cardAccent.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = cardAccent,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = "Check for Updates",
                                        tint = cardAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (detectedUpdate != null) "Update Available!" else "Check for Updates",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(cardAccent.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (detectedUpdate != null) "v${detectedUpdate.versionName}" else "v$currentVersionName",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = cardAccent,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isCheckingUpdates) "Checking GitHub for latest release..."
                                           else if (detectedUpdate != null) "New version is ready to install • Tap to update"
                                           else "v$currentVersionName • Tap to check for new updates",
                                    fontSize = 12.sp,
                                    color = c.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (detectedUpdate != null) {
                                        UpdateManager.openUpdateDialog()
                                    } else if (!isCheckingUpdates) {
                                        scope.launch {
                                            val res = UpdateManager.checkForUpdates(context, appPreferences, isManual = true)
                                            if (res.isSuccess && res.getOrNull() == null) {
                                                Toast.makeText(context, "You are on the latest version (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = cardAccent),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isCheckingUpdates) "Checking..." else if (detectedUpdate != null) "Update" else "Check",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ─── 1. Playback & Video Engine ───────────────────────────────────
            if (showPlayback) {
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
                        title = "Auto Picture-in-Picture (PiP)",
                        subtitle = "Automatically enter floating PiP window when going to home screen during video playback",
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
                        subtitle = "Automatically play next track or video when playback finishes",
                        icon = Icons.Rounded.SkipNext,
                        checked = autoPlayNext,
                        onCheckedChange = {
                            autoPlayNext = it
                            appPreferences.setAutoPlayNextEnabled(it)
                            com.example.ymediaplayer.service.MusicService.setAutoPlayEnabled(it)
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

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Play During Phone Calls",
                        subtitle = "Keep video and music playing fully during phone calls",
                        icon = Icons.Rounded.PhoneInTalk,
                        checked = playDuringCalls,
                        onCheckedChange = {
                            playDuringCalls = it
                            appPreferences.setPlayDuringCallsEnabled(it)
                        }
                    )
                }
            }
        }

        // ─── 2. Gestures & Touch Controls ─────────────────────────────────
        if (showGestures) {
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
                        subtitle = "Swipe left or right anywhere to scrub timeline with preview",
                        icon = Icons.Rounded.SwapHoriz,
                        checked = seekGesture,
                        onCheckedChange = {
                            seekGesture = it
                            appPreferences.setSeekGestureEnabled(it)
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
                        subtitle = "Tactile feedback for sliders, gestures, and controls across the player",
                        icon = Icons.Rounded.Vibration,
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            hapticsEnabled = it
                            appPreferences.setHapticsEnabled(it)
                        }
                    )
                }
            }
        }

        // ─── 3. Subtitles & Audio Engine ──────────────────────────────────
        if (showSubtitles) {
            item {
                SettingsCategorySection(
                    title = "Subtitles & Styling",
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
                            val previewBgColor = if (!subBgEnabled) {
                                Color.Transparent
                            } else {
                                when (subBgStyle) {
                                    0 -> Color.Black.copy(alpha = 0.65f)
                                    1 -> Color.Black
                                    2 -> Color(0xD9141826) // Frosted Dark Glass
                                    3 -> Color(0xD90F172A) // Slate Blue Glass
                                    4 -> Color(0xD9241A12) // Warm Amber Mist
                                    else -> Color.Black.copy(alpha = 0.65f)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(previewBgColor)
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

                    SettingsItemToggle(
                        title = "Subtitle Background Box",
                        subtitle = if (subBgEnabled) "Show background box behind subtitle text" else "No background (clean overlay text)",
                        icon = Icons.Rounded.CheckBoxOutlineBlank,
                        checked = subBgEnabled,
                        onCheckedChange = {
                            subBgEnabled = it
                            appPreferences.setSubtitleBackgroundEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Subtitle Background Style",
                        subtitle = if (!subBgEnabled) "Disabled (Toggle background above)" else when (subBgStyle) {
                            0 -> "Semi-transparent Black (Default)"
                            1 -> "Solid Deep Black Box"
                            2 -> "Frosted Dark Glass (Blur BG)"
                            3 -> "Slate Blue Glass"
                            4 -> "Warm Amber Mist"
                            else -> "Semi-transparent Black"
                        },
                        icon = Icons.Rounded.Layers,
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


                }
            }
        }

        // ─── 4. Appearance & UI Themes ────────────────────────────────────
        if (showAppearance) {
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
                                        .border(
                                            2.dp,
                                            if (isSelected) {
                                                if (theme == PlayerTheme.WHITE) Color.Black else Color.White
                                            } else if (theme == PlayerTheme.WHITE) c.glassBorder else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            currentColorTheme = theme
                                            themeController.updateColorTheme(theme)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = if (theme == PlayerTheme.WHITE || theme == PlayerTheme.YELLOW) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Ambient Glow Intensity Selector
                        Text(
                            text = "Ambient Gradient Glow",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        val glowLevels = listOf(
                            "Off" to 0.0f,
                            "Subtle" to 0.6f,
                            "Vibrant" to 1.0f,
                            "Neon Boost" to 1.6f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            glowLevels.forEach { (label, strength) ->
                                val isSelected = kotlin.math.abs(ambientGlowStrength - strength) < 0.1f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) primaryAccent.copy(0.2f) else Color.White.copy(0.06f))
                                        .border(1.2.dp, if (isSelected) primaryAccent else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable {
                                            ambientGlowStrength = strength
                                            appPreferences.setAmbientGlowStrength(strength)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) primaryAccent else c.textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Glow Spread / Coverage",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = c.textPrimary
                            )
                            Text(
                                text = "${(ambientGlowSpread * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent
                            )
                        }
                        AppSlider(
                            value = ambientGlowSpread,
                            onValueChange = {
                                ambientGlowSpread = it
                                appPreferences.setAmbientGlowSpread(it)
                                if (ambientGlowStrength <= 0.05f) {
                                    ambientGlowStrength = 1.0f
                                    appPreferences.setAmbientGlowStrength(1.0f)
                                }
                            },
                            valueRange = 0.5f..2.5f,
                            accentColor = primaryAccent
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Compact (50%)", fontSize = 10.sp, color = c.textSecondary)
                            Text("Balanced (100%)", fontSize = 10.sp, color = c.textSecondary)
                            Text("Extreme Bleed (250%)", fontSize = 10.sp, color = c.textSecondary)
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Glow Saturation & Richness",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = c.textPrimary
                            )
                            Text(
                                text = "${(ambientGlowSaturation * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent
                            )
                        }
                        AppSlider(
                            value = ambientGlowSaturation,
                            onValueChange = {
                                ambientGlowSaturation = it
                                appPreferences.setAmbientGlowSaturation(it)
                                if (ambientGlowStrength <= 0.05f) {
                                    ambientGlowStrength = 1.0f
                                    appPreferences.setAmbientGlowStrength(1.0f)
                                }
                            },
                            valueRange = 0.5f..2.5f,
                            accentColor = primaryAccent
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Soft Pastel (50%)", fontSize = 10.sp, color = c.textSecondary)
                            Text("Natural (100%)", fontSize = 10.sp, color = c.textSecondary)
                            Text("Ultra Punch (250%)", fontSize = 10.sp, color = c.textSecondary)
                        }

                        Spacer(Modifier.height(16.dp))

                        // UI Corner Style Selector
                        Text(
                            text = "Card Corner Style",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        val cornerStyles = listOf(
                            "Sleek (16dp)" to "SLEEK",
                            "Modern (22dp)" to "MODERN",
                            "Extra (28dp)" to "EXTRA"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            cornerStyles.forEach { (label, style) ->
                                val isSelected = uiCornerStyle == style
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) primaryAccent.copy(0.2f) else Color.White.copy(0.06f))
                                        .border(1.2.dp, if (isSelected) primaryAccent else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable {
                                            uiCornerStyle = style
                                            appPreferences.setUiCornerStyle(style)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) primaryAccent else c.textSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
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

                    if (ambientGlowEnabled) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Video Ambient Spread", fontSize = 13.sp, color = c.textPrimary)
                                Text("${(videoAmbientSpread * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryAccent)
                            }
                            AppSlider(
                                value = videoAmbientSpread,
                                onValueChange = {
                                    videoAmbientSpread = it
                                    appPreferences.setVideoAmbientSpread(it)
                                },
                                valueRange = 0.2f..1.5f,
                                accentColor = primaryAccent
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Video Ambient Saturation", fontSize = 13.sp, color = c.textPrimary)
                                Text("${(videoAmbientSaturation * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryAccent)
                            }
                            AppSlider(
                                value = videoAmbientSaturation,
                                onValueChange = {
                                    videoAmbientSaturation = it
                                    appPreferences.setVideoAmbientSaturation(it)
                                },
                                valueRange = 0.4f..2.5f,
                                accentColor = primaryAccent
                            )

                            Spacer(Modifier.height(4.dp))

                            SettingsItemToggle(
                                title = "Video Ambient Battery Saver",
                                subtitle = "Lower frame capture rate to conserve battery on long movies",
                                icon = Icons.Rounded.BatterySaver,
                                checked = videoAmbientBatterySaver,
                                onCheckedChange = {
                                    videoAmbientBatterySaver = it
                                    appPreferences.setVideoAmbientBatterySaver(it)
                                }
                            )
                        }
                    }
                }
            }
        }

        // ─── 5. Library, Folders & Storage ────────────────────────────────
        if (showLibrary) {
            item {
                SettingsCategorySection(
                    title = "Library & Folders",
                    icon = Icons.Rounded.FolderOpen,
                    accentColor = primaryAccent
                ) {
                    SettingsItemToggle(
                        title = "Download Over Wi-Fi Only",
                        subtitle = "Save mobile data by downloading torrents only on Wi-Fi",
                        icon = Icons.Rounded.Wifi,
                        checked = isWifiOnlyDownload,
                        onCheckedChange = {
                            isWifiOnlyDownload = it
                            appPreferences.setWifiOnlyDownloadEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Default Starting Tab",
                        subtitle = when (defaultStartTab) {
                            "MYCinema" -> "MYCinema (Movies & Series)"
                            "Music" -> "Music (Audio & Tracks)"
                            else -> "Video (Default)"
                        },
                        icon = Icons.Rounded.Home,
                        onClick = { activeDialog = SettingsDialogType.DEFAULT_START_TAB }
                    )

                    SettingsDivider()

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
        }

        // ─── 6. Music Player Preferences ──────────────────────────────────
        if (showMusic) {
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
                            else -> "Modern Album Card"
                        },
                        icon = Icons.Rounded.Album,
                        onClick = { activeDialog = SettingsDialogType.MUSIC_ARTWORK }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Show Call Recordings & Voice Notes",
                        subtitle = "Include phone call recordings, WhatsApp audio, and voice memos in library",
                        icon = Icons.Rounded.RecordVoiceOver,
                        checked = includeAudioRecordings,
                        onCheckedChange = {
                            includeAudioRecordings = it
                            appPreferences.setIncludeAudioRecordingsEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Show Ringtones & Short Audio (< 45s)",
                        subtitle = "Include notification sounds, system ringtones, and brief audio clips",
                        icon = Icons.Rounded.Notifications,
                        checked = includeRingtones,
                        onCheckedChange = {
                            includeRingtones = it
                            appPreferences.setIncludeRingtonesEnabled(it)
                        }
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

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Pause on Headset / Bluetooth Disconnect",
                        subtitle = "Instantly pause playback when headphones or Bluetooth audio disconnect",
                        icon = Icons.Rounded.HeadsetOff,
                        checked = pauseHeadsetDisconnect,
                        onCheckedChange = {
                            pauseHeadsetDisconnect = it
                            appPreferences.setPauseOnHeadsetDisconnect(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Resume on Headset Connect",
                        subtitle = "Automatically resume playing when headphones or Bluetooth reconnect",
                        icon = Icons.Rounded.Headphones,
                        checked = resumeOnHeadsetConnect,
                        onCheckedChange = {
                            resumeOnHeadsetConnect = it
                            appPreferences.setResumeOnHeadsetConnect(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Crossfade Between Tracks",
                        subtitle = if (crossfadeSeconds == 0) "Off (Direct transition)" else "$crossfadeSeconds seconds",
                        icon = Icons.Rounded.Shuffle,
                        onClick = { activeDialog = SettingsDialogType.CROSSFADE_DURATION }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Default Sleep Timer Duration",
                        subtitle = if (defaultSleepMinutes == 0) "Off (Manual only)" else "$defaultSleepMinutes minutes",
                        icon = Icons.Rounded.Bedtime,
                        onClick = { activeDialog = SettingsDialogType.DEFAULT_SLEEP_TIMER }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Sleep Timer Fade Out",
                        subtitle = "Gradually decrease audio volume over the final 20 seconds before pausing",
                        icon = Icons.Rounded.VolumeDown,
                        checked = sleepTimerFadeOut,
                        onCheckedChange = {
                            sleepTimerFadeOut = it
                            appPreferences.setSleepTimerFadeOutEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Audio Focus & Call Ducking",
                        subtitle = "Temporarily lower or pause music when phone calls, alarms, or notifications sound",
                        icon = Icons.Rounded.VolumeMute,
                        checked = audioFocusDucking,
                        onCheckedChange = {
                            audioFocusDucking = it
                            appPreferences.setAudioFocusDuckingEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Lock Screen Controls & Album Art",
                        subtitle = "Display media player controls and high-resolution album cover on device lock screen",
                        icon = Icons.Rounded.ScreenLockPortrait,
                        checked = lockscreenControls,
                        onCheckedChange = {
                            lockscreenControls = it
                            appPreferences.setLockscreenControlsEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemPicker(
                        title = "Default Equalizer Sound Profile",
                        subtitle = MusicSoundMode.fromId(musicSoundMode).displayName,
                        icon = Icons.Rounded.Tune,
                        onClick = { activeDialog = SettingsDialogType.MUSIC_SOUND_MODE }
                    )
                }
            }
        }

        // ─── 7. Data, Storage & About ─────────────────────────────────────
        if (showStorage) {
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

                    val isCheckingUpdates = UpdateManager.isChecking.value
                    SettingsItemAction(
                        title = "Check for Updates",
                        subtitle = if (isCheckingUpdates) "Checking GitHub for latest release..." else "v$currentVersionName • Tap to check latest release",
                        icon = Icons.Rounded.SystemUpdate,
                        iconTint = primaryAccent,
                        onClick = {
                            if (!isCheckingUpdates) {
                                scope.launch {
                                    val res = UpdateManager.checkForUpdates(context, appPreferences, isManual = true)
                                    if (res.isSuccess && res.getOrNull() == null) {
                                        Toast.makeText(context, "You are using the latest version (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )

                    SettingsDivider()

                    SettingsItemToggle(
                        title = "Auto-Check for Updates",
                        subtitle = "Automatically notify when a new release is available",
                        icon = Icons.Rounded.CloudDownload,
                        checked = autoCheckUpdates,
                        onCheckedChange = {
                            autoCheckUpdates = it
                            appPreferences.setAutoCheckUpdatesEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsItemAction(
                        title = "Update History",
                        subtitle = "View past versions, changelogs and release notes",
                        icon = Icons.Rounded.History,
                        iconTint = Color(0xFF10B981),
                        onClick = {
                            showUpdateHistoryDialog = true
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
        }

        // ─── Empty Search State (No results found) ────────────────────────
        if (q.isNotBlank() && !anySectionVisible) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(c.cardBg)
                                .border(1.dp, c.glassBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "No settings found for \"$q\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try searching for a different keyword or browse categories above.",
                            fontSize = 12.5.sp,
                            color = c.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { searchQuery = "" },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f))
                        ) {
                            Text("Clear Search", color = primaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ─── App Info Card ────────────────────────────────────────────────
        if (q.isBlank() && (currentCat == SettingsCategory.ALL || currentCat == SettingsCategory.STORAGE)) {
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
                            text = "AllRounder Media Player",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Version $currentVersionName • Built with Jetpack Compose & Media3",
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
    } // end SettingsCategoryColumn

    if (searchQuery.isNotBlank()) {
        SettingsCategoryColumn(
            currentCat = selectedCategory,
            queryStr = searchQuery,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            beyondViewportPageCount = 1
        ) { page ->
            val pageCategory = SettingsCategory.entries.getOrElse(page) { SettingsCategory.ALL }
            SettingsCategoryColumn(
                currentCat = pageCategory,
                queryStr = "",
                modifier = Modifier.fillMaxSize()
            )
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
                title = "Subtitle Background Style",
                options = listOf(
                    0 to "Semi-transparent Black (Default)",
                    1 to "Solid Deep Black Box",
                    2 to "Frosted Dark Glass (Blur Effect)",
                    3 to "Slate Blue Glass",
                    4 to "Warm Amber Mist"
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
        SettingsDialogType.DEFAULT_START_TAB -> {
            SettingsOptionDialog(
                title = "Default Starting Tab",
                options = listOf(
                    "Video" to "Video (Default)",
                    "MYCinema" to "MYCinema (Movies & Series)",
                    "Music" to "Music (Audio & Tracks)"
                ),
                selectedKey = defaultStartTab,
                onSelect = {
                    defaultStartTab = it
                    appPreferences.setDefaultStartTab(it)
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
        SettingsDialogType.CROSSFADE_DURATION -> {
            SettingsOptionDialog(
                title = "Crossfade Between Tracks",
                options = listOf(
                    "0" to "Off (Seamless gapless)",
                    "2" to "2 seconds",
                    "4" to "4 seconds (Smooth blend)",
                    "6" to "6 seconds",
                    "8" to "8 seconds",
                    "10" to "10 seconds (Club DJ mix)"
                ),
                selectedKey = crossfadeSeconds.toString(),
                onSelect = {
                    val s = it.toIntOrNull() ?: 0
                    crossfadeSeconds = s
                    appPreferences.setCrossfadeDurationSeconds(s)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.DEFAULT_SLEEP_TIMER -> {
            SettingsOptionDialog(
                title = "Default Sleep Timer Duration",
                options = listOf(
                    "0" to "Off (Manual start only)",
                    "15" to "15 minutes",
                    "30" to "30 minutes (Standard)",
                    "45" to "45 minutes",
                    "60" to "60 minutes (1 hour)",
                    "90" to "90 minutes (1.5 hours)"
                ),
                selectedKey = defaultSleepMinutes.toString(),
                onSelect = {
                    val m = it.toIntOrNull() ?: 30
                    defaultSleepMinutes = m
                    appPreferences.setDefaultSleepTimerMinutes(m)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialogType.MUSIC_SOUND_MODE -> {
            SettingsOptionDialog(
                title = "Default Equalizer Sound Profile",
                options = MusicSoundMode.entries.map { it.id to it.displayName },
                selectedKey = musicSoundMode,
                onSelect = {
                    musicSoundMode = it
                    appPreferences.setMusicSoundMode(it)
                    val mode = MusicSoundMode.fromId(it)
                    MusicService.applySoundMode(context, mode)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        null -> {}
    }

    if (updateError != null && !UpdateManager.showUpdateDialog.value) {
        AlertDialog(
            onDismissRequest = { UpdateManager.errorMessage.value = null },
            title = { Text("Couldn't check for updates") },
            text = { Text(updateError) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val result = UpdateManager.checkForUpdates(context, appPreferences, isManual = true)
                    if (result.isSuccess && result.getOrNull() == null) {
                        Toast.makeText(context, "You are using the latest version", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { UpdateManager.errorMessage.value = null }) { Text("Close") } }
        )
    }

    // ─── Update History Dialog ──────────────────────────────────────────
    if (showUpdateHistoryDialog) {
        UpdateHistoryDialog(
            onDismiss = { showUpdateHistoryDialog = false },
            onInstallRelease = { release ->
                UpdateManager.availableUpdate.value = release
                UpdateManager.openUpdateDialog()
            }
        )
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
                        autoCheckUpdates = appPreferences.isAutoCheckUpdatesEnabled()
                        com.example.ymediaplayer.service.MusicService.initPreferencesState(context)
                        com.example.ymediaplayer.service.MusicService.setRepeatMode(0)
                        com.example.ymediaplayer.service.MusicService.setShuffleEnabled(false)
                        com.example.ymediaplayer.service.MusicService.setSleepTimer(0)
                        videoColorProfile = appPreferences.getVideoColorProfile()
                        videoSharpnessLevel = appPreferences.getVideoSharpnessLevel()
                        videoContrast = appPreferences.getVideoCustomContrast()
                        videoSaturation = appPreferences.getVideoCustomSaturation()
                        videoBrightness = appPreferences.getVideoCustomBrightness()
                        videoWarmth = appPreferences.getVideoCustomWarmth()
                        videoSharpness = appPreferences.getVideoCustomSharpness()
                        videoSoundMode = appPreferences.getVideoSoundMode()
                        ambientGlowStrength = appPreferences.getAmbientGlowStrength()
                        ambientGlowSpread = appPreferences.getAmbientGlowSpread()
                        ambientGlowSaturation = appPreferences.getAmbientGlowSaturation()
                        uiCornerStyle = appPreferences.getUiCornerStyle()
                        ambientGlowEnabled = appPreferences.isAmbientGlowEnabled()
                        videoAmbientSpread = appPreferences.getVideoAmbientSpread()
                        videoAmbientSaturation = appPreferences.getVideoAmbientSaturation()
                        videoAmbientBatterySaver = appPreferences.isVideoAmbientBatterySaver()
                        themeController.updateMode(ThemeMode.OLED_BLACK)
                        themeController.updateColorTheme(PlayerTheme.BLUE)
                        resumeMode = appPreferences.getResumeMode()
                        rememberPlaybackSpeed = appPreferences.isRememberPlaybackSpeed()
                        seekSeconds = appPreferences.getDoubleTapSeekSeconds()
                        autoPip = appPreferences.isAutoPipEnabled()
                        autoPlayNext = appPreferences.isAutoPlayNextEnabled()
                        com.example.ymediaplayer.service.MusicService.setAutoPlayEnabled(autoPlayNext)
                        rememberPlaylistQueue = appPreferences.isRememberPlaylistQueue()
                        backgroundPlay = appPreferences.isBackgroundPlayEnabled()
                        autoHideTimeoutMs = appPreferences.getControlsAutoHideTimeoutMs()
                        defaultResizeMode = appPreferences.getDefaultResizeMode()
                        hwAcceleration = appPreferences.isHwAccelerationEnabled()
                        keepScreenAwake = appPreferences.isKeepScreenAwake()
                        playDuringCalls = appPreferences.isPlayDuringCallsEnabled()
                        brightnessGesture = appPreferences.isBrightnessGestureEnabled()
                        volumeGesture = appPreferences.isVolumeGestureEnabled()
                        seekGesture = appPreferences.isSeekGestureEnabled()
                        videoSwitchGesture = appPreferences.isVideoSwitchGestureEnabled()
                        doubleTapCenterPlayPause = appPreferences.isDoubleTapCenterPlayPauseEnabled()
                        pressHoldSpeed = appPreferences.getPressHoldSpeed()
                        hapticsEnabled = appPreferences.isHapticsEnabled()
                        hdrEnabled = appPreferences.isHdrEnabled()
                        subFontSize = appPreferences.getSubtitleFontSize()
                        subColor = appPreferences.getSubtitleColor()
                        subBgEnabled = appPreferences.isSubtitleBackgroundEnabled()
                        subBgStyle = appPreferences.getSubtitleBackgroundStyle()
                        subOutlineStyle = appPreferences.getSubtitleOutlineStyle()
                        pauseHeadsetDisconnect = appPreferences.isPauseOnHeadsetDisconnect()
                        currentThemeMode = ThemeMode.OLED_BLACK
                        currentColorTheme = PlayerTheme.YELLOW
                        signatureViewEnabled = false
                        signatureSaturation = 1.25f
                        ambientGlowEnabled = true
                        ambientGlowSpread = 0.94f
                        ambientGlowSaturation = 2.32f
                        videoAmbientSpread = 1.34f
                        videoAmbientSaturation = 1.05f
                        videoAmbientBatterySaver = false
                        mediaViewType = MediaViewType.DETAILED_LIST
                        defaultSortOrder = SortOrder.DATE
                        showContinueWatching = true
                        continueWatchingLimit = 20
                        showRecentlyAdded = true
                        excludeShortClipsSeconds = 0
                        excludeHiddenFolders = true
                        confirmDelete = true
                        musicArtworkStyle = "VINYL"
                        includeAudioRecordings = false
                        includeRingtones = false
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
    val context = LocalContext.current
    val appPreferences = remember(context) { AppPreferences(context) }
    val glowVersion = appPreferences.lastProgressUpdate.longValue
    val cornerRadius = remember(glowVersion) {
        when (appPreferences.getUiCornerStyle()) {
            "SLEEK" -> 16.dp
            "EXTRA" -> 28.dp
            else -> 22.dp
        }
    }
    val ambientGlowStrength = remember(glowVersion) { appPreferences.getAmbientGlowStrength() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (ambientGlowStrength > 0.05f) accentColor.copy(alpha = (0.22f * ambientGlowStrength).coerceIn(0f, 0.45f)) else c.cardShadowColor,
                spotColor = if (ambientGlowStrength > 0.05f) accentColor.copy(alpha = (0.32f * ambientGlowStrength).coerceIn(0f, 0.55f)) else c.cardShadowColor
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(c.cardBg)
            .border(1.2.dp, c.glassBorder, RoundedCornerShape(cornerRadius))
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
    iconTint: Color = Color.Unspecified,
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
    val context = LocalContext.current
    val appPreferences = remember(context) { AppPreferences(context) }
    val glowVersion = appPreferences.lastProgressUpdate.longValue
    val cornerRadius = remember(glowVersion) {
        when (appPreferences.getUiCornerStyle()) {
            "SLEEK" -> 16.dp
            "EXTRA" -> 28.dp
            else -> 22.dp
        }
    }
    val ambientGlowStrength = remember(glowVersion) { appPreferences.getAmbientGlowStrength() }

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
        Modifier.border(1.2.dp, c.glassBorder, RoundedCornerShape(cornerRadius))
    } else {
        Modifier.border(1.2.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(cornerRadius))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (ambientGlowStrength > 0.05f) primaryAccent.copy(alpha = (0.22f * ambientGlowStrength).coerceIn(0f, 0.45f)) else c.cardShadowColor,
                spotColor = if (ambientGlowStrength > 0.05f) primaryAccent.copy(alpha = (0.32f * ambientGlowStrength).coerceIn(0f, 0.55f)) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(cornerRadius))
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
    DEFAULT_START_TAB,
    MEDIA_VIEW,
    DEFAULT_SORT,
    CW_LIMIT,
    EXCLUDE_SHORT_CLIPS,
    MUSIC_ARTWORK,
    CROSSFADE_DURATION,
    DEFAULT_SLEEP_TIMER,
    MUSIC_SOUND_MODE
}
