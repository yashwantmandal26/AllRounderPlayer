package com.example.ymediaplayer.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.example.ymediaplayer.data.*
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.LocalThemeController
import com.example.ymediaplayer.service.MusicService
import com.example.ymediaplayer.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    onFolderClick: (String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    hasVideoPermission: Boolean = true,
    hasAudioPermission: Boolean = true,
    onRequestPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val c = LocalAppColors.current
    val themeController = LocalThemeController.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val repository = remember { VideoRepository(context) }
    val appPreferences = remember { AppPreferences(context) }
    val fileManager = remember { FileManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Data state
    var allFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Navigation state
    var currentTab by rememberSaveable { mutableStateOf("Video") }
    var musicPlayerExpanded by rememberSaveable { mutableStateOf(false) }

    // Search & sort state
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by remember {
        mutableStateOf(runCatching { SortOrder.valueOf(appPreferences.getSortOrder()) }.getOrDefault(SortOrder.DATE))
    }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var activeChip by rememberSaveable { mutableStateOf<String?>(null) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var currentMediaViewType by remember { mutableStateOf(appPreferences.getMediaViewType()) }
    var favorites by remember { mutableStateOf(appPreferences.getFavorites()) }

    val searchFocusRequester = remember { FocusRequester() }

    // Multi-select state
    var selectedVideoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val inSelectionMode = selectedVideoIds.isNotEmpty()

    // Context menu / action state
    var videoActionTarget by remember { mutableStateOf<VideoItem?>(null) }
    var folderActionTarget by remember { mutableStateOf<VideoFolder?>(null) }
    var renameVideoTarget by remember { mutableStateOf<VideoItem?>(null) }
    var deleteVideosConfirm by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var videoInfoTarget by remember { mutableStateOf<VideoItem?>(null) }
    var videoInfoData by remember { mutableStateOf<VideoInfo?>(null) }
    var deleteFolderConfirm by remember { mutableStateOf<VideoFolder?>(null) }

    // ─── Rock-Solid Back Navigation ─────────────────────────────────────────
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        when {
            musicPlayerExpanded -> {
                musicPlayerExpanded = false
            }
            inSelectionMode -> {
                selectedVideoIds = emptySet<Long>()
            }
            isSearching -> {
                isSearching = false
                searchQuery = ""
            }
            activeChip != null -> {
                activeChip = null
            }
            currentTab != "Video" -> {
                currentTab = "Video"
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Background animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset = infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_offset"
    )

    // Delete launcher (Android 11+ requires user confirmation)
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedVideoIds = emptySet()
            deleteVideosConfirm = emptyList()
            deleteFolderConfirm = null
            refreshTrigger++
        }
    }

    // Live auto-refresh: watch MediaStore for file changes
    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                VideoRepository.invalidateCache()
                refreshTrigger++
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    // Refresh automatically whenever screen resumes (e.g. returning from video player)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load data
    LaunchedEffect(refreshTrigger) {
        if (allFolders.isEmpty()) {
            isLoading = true
        }
        allFolders = repository.getFoldersWithVideos()
        isLoading = false

        // Background preload music library into memory cache for instant, zero-jitter Music tab switching
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                if (hasAudioPermission && com.example.ymediaplayer.data.MusicRepository.getCachedMusic() == null) {
                    val mRepo = com.example.ymediaplayer.data.MusicRepository(context)
                    val songs = mRepo.getMusicFiles()
                    com.example.ymediaplayer.service.MusicService.currentPlaylist = songs
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
    }

    LaunchedEffect(com.example.ymediaplayer.MainActivity.openMusicTrigger.value) {
        if (com.example.ymediaplayer.MainActivity.openMusicTrigger.value) currentTab = "Music"
    }

    val toggleFavorite: (String) -> Unit = { uri ->
        appPreferences.toggleFavorite(uri)
        favorites = appPreferences.getFavorites()
    }

    // Computed display folders (processed in Dispatchers.Default to prevent main-thread jank)
    var displayFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var allUniqueVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    LaunchedEffect(allFolders, searchQuery, sortOrder, activeChip, favorites) {
        withContext(Dispatchers.Default) {
            var filteredFolders = allFolders
            if (activeChip == "Downloader") {
                filteredFolders = filteredFolders.filter { it.name.contains("Download", ignoreCase = true) }
            } else if (activeChip == "Favorites") {
                filteredFolders = filteredFolders.mapNotNull { folder ->
                    val favVideos = folder.videos.filter { favorites.contains(it.uri.toString()) }
                    if (favVideos.isNotEmpty()) folder.copy(videos = favVideos) else null
                }
            }
            var processed = filteredFolders.mapNotNull { folder ->
                val filteredVideos = folder.videos.filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }.sortVideosWithOrder(sortOrder)
                if (filteredVideos.isNotEmpty() || folder.name.contains(searchQuery, ignoreCase = true)) {
                    folder.copy(videos = filteredVideos)
                } else null
            }.sortFoldersWithOrder(sortOrder)

            if (searchQuery.isEmpty() && activeChip == null) {
                val allVideosList = allFolders.flatMap { it.videos }.distinctBy { it.id }.sortVideosWithOrder(sortOrder)
                if (allVideosList.isNotEmpty()) {
                    val allVideosFolder = VideoFolder(id = "all_videos", name = "All Videos", videos = allVideosList)
                    processed = listOf(allVideosFolder) + processed
                }
            }
            // Pinned folders float to top right under All Videos
            val pinnedIds = appPreferences.getPinnedFolders()
            if (pinnedIds.isNotEmpty()) {
                val allVideosF = processed.filter { it.id == "all_videos" }
                val otherF = processed.filter { it.id != "all_videos" }
                val pinned = otherF.filter { pinnedIds.contains(it.id) }
                val unpinned = otherF.filter { !pinnedIds.contains(it.id) }
                processed = allVideosF + pinned + unpinned
            }
            withContext(Dispatchers.Main) {
                displayFolders = processed
            }
        }
    }

    LaunchedEffect(allFolders) {
        withContext(Dispatchers.Default) {
            val unique = allFolders.flatMap { it.videos }.distinctBy { it.uri.toString() }
            withContext(Dispatchers.Main) {
                allUniqueVideos = unique
            }
        }
    }

    val searchMatchingVideos = remember(allUniqueVideos, searchQuery, sortOrder) {
        if (searchQuery.isBlank()) emptyList()
        else allUniqueVideos.filter {
            it.title.contains(searchQuery.trim(), ignoreCase = true)
        }.sortVideosWithOrder(sortOrder)
    }

    val searchMatchingFolders = remember(allFolders, searchQuery, sortOrder) {
        if (searchQuery.isBlank()) emptyList()
        else allFolders.filter {
            it.name.contains(searchQuery.trim(), ignoreCase = true)
        }.sortFoldersWithOrder(sortOrder)
    }

    val progressVersion = appPreferences.lastProgressUpdate.longValue

    val recentlyAddedVideos = remember(allUniqueVideos, progressVersion, refreshTrigger) {
        if (!appPreferences.isShowRecentlyAdded()) return@remember emptyList()
        val excludeSec = appPreferences.getExcludeShortClipsSeconds()
        allUniqueVideos.filter { video ->
            excludeSec == 0 || video.duration >= excludeSec * 1000L
        }.sortedWith(
            compareByDescending<VideoItem> { it.dateAdded }.thenByDescending { it.id }
        ).take(15)
    }

    val continueWatchingVideos = remember(allUniqueVideos, progressVersion, refreshTrigger) {
        if (!appPreferences.isShowContinueWatching()) return@remember emptyList()
        val limit = appPreferences.getContinueWatchingLimit()
        val playedUris = appPreferences.getPlayedUris()
        val playedOrder = playedUris.mapIndexed { index, uri -> uri to index }.toMap()

        allUniqueVideos.filter { video ->
            val uriStr = video.uri.toString()
            if (appPreferences.isCompleted(uriStr)) return@filter false
            val progress = appPreferences.getVideoProgress(uriStr)
            val duration = if (video.duration > 0L) video.duration else appPreferences.getVideoDuration(uriStr)
            if (progress <= 3000L) return@filter false
            if (duration > 0L) {
                val ratio = progress.toFloat() / duration
                ratio in 0.02f..0.96f && progress < (duration - 3000L)
            } else {
                true
            }
        }.sortedWith(
            compareByDescending<VideoItem> { appPreferences.getVideoLastPlayedTime(it.uri.toString()) }
                .thenBy { playedOrder[it.uri.toString()] ?: Int.MAX_VALUE }
        ).take(limit)
    }

    val totalVideoCount = remember(allUniqueVideos) { allUniqueVideos.size }
    val totalSize = remember(allUniqueVideos) { allUniqueVideos.sumOf { it.size } }
    val favoritesCount = remember(favorites, allUniqueVideos) {
        val allUris = allUniqueVideos.map { it.uri.toString() }.toSet()
        favorites.count { allUris.contains(it) }
    }
    val downloaderCount = remember(allFolders) {
        allFolders.filter { it.name.contains("Download", ignoreCase = true) }.sumOf { it.videos.size }
    }

    // Prevent exiting app directly; collapse active modes first
    var backPressedTime by remember { mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            inSelectionMode -> selectedVideoIds = emptySet()
            isSearching -> { isSearching = false; searchQuery = "" }
            activeChip != null -> activeChip = null
            videoActionTarget != null -> videoActionTarget = null
            folderActionTarget != null -> folderActionTarget = null
            currentTab != "Video" -> currentTab = "Video"
            else -> {
                val now = System.currentTimeMillis()
                if (now - backPressedTime < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    backPressedTime = now
                    android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── Main Layout ────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        CanvasBg(offsetProvider = { gradientOffset.value })

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!musicPlayerExpanded) {
                    if (inSelectionMode) {
                        MultiSelectTopBar(
                            selectedCount = selectedVideoIds.size,
                            onClearSelection = { selectedVideoIds = emptySet() },
                            onDeleteSelected = {
                                val items = displayFolders.flatMap { it.videos }.filter { selectedVideoIds.contains(it.id) }
                                deleteVideosConfirm = items
                            },
                            onShareSelected = {
                                val uris = displayFolders.flatMap { it.videos }.filter { selectedVideoIds.contains(it.id) }.map { it.uri }
                                val intent = fileManager.buildShareMultipleIntent(uris)
                                context.startActivity(android.content.Intent.createChooser(intent, "Share videos"))
                                selectedVideoIds = emptySet()
                            },
                            onSelectAll = {
                                selectedVideoIds = displayFolders.flatMap { it.videos }.map { it.id }.toSet()
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Frosted glass blur backdrop
                            if (!c.isMatte) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(c.glassBg.copy(alpha = 0.6f))
                                        .blur(20.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (c.isMatte) c.baseBackground else c.topBarScrim.copy(alpha = 0.75f))
                                    .border(if (c.isMatte) 1.dp else 0.5.dp, c.glassBorder)
                            ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (isSearching) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .then(if (c.isMatte) Modifier else Modifier.shadow(elevation = 6.dp, shape = RoundedCornerShape(23.dp), ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor))
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(if (c.isMatte) SolidColor(c.cardBgElevated) else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                            .border(1.2.dp, if (c.isMatte) SolidColor(c.glassBorder) else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), RoundedCornerShape(23.dp))
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = c.accentBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                            if (searchQuery.isEmpty()) {
                                                Text("Search any folder or video...", color = c.textHint, fontSize = 14.sp)
                                            }
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                                cursorBrush = SolidColor(c.accentBlue),
                                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester)
                                            )
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { isSearching = false; searchQuery = "" },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Cancel", color = c.accentBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(currentTab, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                        if (currentTab == "Video" && totalVideoCount > 0) {
                                            Text("$totalVideoCount videos · ${formatSize(totalSize)}", fontSize = 12.sp, color = c.textSecondary)
                                        } else if (currentTab == "Music" && com.example.ymediaplayer.service.MusicService.totalTracksCount.intValue > 0) {
                                            Text("${com.example.ymediaplayer.service.MusicService.totalTracksCount.intValue} tracks · ${formatSize(com.example.ymediaplayer.service.MusicService.totalTracksSize.longValue)}", fontSize = 12.sp, color = c.textSecondary)
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 0. Update Available Green Button / Indicator
                                        val detectedUpdate = UpdateManager.availableUpdate.value
                                        if (detectedUpdate != null) {
                                            Surface(
                                                onClick = {
                                                    view.performHaptic(HapticType.LIGHT)
                                                    UpdateManager.openUpdateDialog()
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.16f),
                                                border = BorderStroke(1.2.dp, Color(0xFF10B981).copy(alpha = 0.65f)),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.SystemUpdate,
                                                        contentDescription = "Update Available",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                    Text(
                                                        text = "Update Available",
                                                        color = Color(0xFF10B981),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }

                                        // 1. Color Theme Palette button
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = {
                                                        view.performHaptic(HapticType.LIGHT)
                                                        themeController.cycleColorTheme()
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        Toast.makeText(context, "Color Theme Palette", Toast.LENGTH_SHORT).show()
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Palette, contentDescription = "Theme", tint = c.accentBlue, modifier = Modifier.size(22.dp))
                                        }

                                        // 2. Light / Dark Mode button
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = {
                                                        view.performHaptic(HapticType.LIGHT)
                                                        themeController.cycleThemeMode()
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        Toast.makeText(context, "Light / Dark Mode", Toast.LENGTH_SHORT).show()
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (c.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                                contentDescription = "Theme: ${themeController.mode.displayName}",
                                                tint = c.textPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        // 3. Search button
                                        if (currentTab == "Video") {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .combinedClickable(
                                                        onClick = {
                                                            view.performHaptic(HapticType.LIGHT)
                                                            isSearching = true
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                            Toast.makeText(context, "Search Videos", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Search, contentDescription = "Search", tint = c.textPrimary, modifier = Modifier.size(22.dp))
                                            }
                                        }

                                        // 4. Settings button
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = {
                                                        view.performHaptic(HapticType.LIGHT)
                                                        onOpenSettings()
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show()
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = c.textPrimary, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                            }
                        }
                        } // end frosted glass Box
                    }
                }
            },
            modifier = modifier
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val enterAnim = if (targetState == "Music") {
                        slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing), initialOffsetX = { it / 6 }) + fadeIn(tween(200))
                    } else {
                        slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing), initialOffsetX = { -it / 6 }) + fadeIn(tween(200))
                    }
                    val exitAnim = if (targetState == "Music") {
                        slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing), targetOffsetX = { -it / 6 }) + fadeOut(tween(160))
                    } else {
                        slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing), targetOffsetX = { it / 6 }) + fadeOut(tween(160))
                    }
                    (enterAnim.togetherWith(exitAnim)).using(SizeTransform(clip = false))
                },
                label = "TabAnimatedContent",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                if (tab == "Music") {
                    MusicScreen(
                        modifier = Modifier.fillMaxSize().padding(top = if (musicPlayerExpanded) 0.dp else paddingValues.calculateTopPadding()),
                        onFullScreenChanged = { musicPlayerExpanded = it },
                        hasMediaPermission = hasAudioPermission,
                        onRequestPermission = onRequestPermissions
                    )
                } else if (isLoading && allFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accentBlue, strokeWidth = 3.dp)
                }
            } else if (allFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp).clip(RoundedCornerShape(28.dp))
                            .background(c.glassBg).border(0.5.dp, c.glassBorder, RoundedCornerShape(28.dp))
                            .padding(horizontal = 40.dp, vertical = 32.dp)
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(if (hasVideoPermission) "No Videos Found" else "Video Access Needed", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (hasVideoPermission) "Videos on your device will show up here" else "Allow video access to browse and play your videos",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { if (hasVideoPermission) refreshTrigger++ else onRequestPermissions() }, colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = Color.White)) {
                            Text(if (hasVideoPermission) "Rescan" else "Allow Access")
                        }
                    }
                }

            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {

                    if (isSearching) {
                        if (searchQuery.isBlank()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(c.glassBg)
                                            .border(1.dp, c.glassBorder, RoundedCornerShape(22.dp))
                                            .padding(horizontal = 24.dp, vertical = 28.dp)
                                    ) {
                                        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(44.dp))
                                        Spacer(Modifier.height(12.dp))
                                        Text("Search Folders or Videos", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Type a name above to search across your device", color = c.textSecondary, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        } else if (searchMatchingVideos.isEmpty() && searchMatchingFolders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(c.glassBg)
                                            .border(1.dp, c.glassBorder, RoundedCornerShape(22.dp))
                                            .padding(horizontal = 24.dp, vertical = 28.dp)
                                    ) {
                                        Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(44.dp))
                                        Spacer(Modifier.height(12.dp))
                                        Text("No Results Found", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))
                                        Text("No folders or videos match \"$searchQuery\"", color = c.textSecondary, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        } else {
                            // 1. Matching Folders Section
                            if (searchMatchingFolders.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("FOLDERS (${searchMatchingFolders.size})", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                items(searchMatchingFolders, key = { "search_folder_${it.id}" }) { folder ->
                                    Box(modifier = Modifier.animateItem()) {
                                        FolderItem(
                                            folder = folder,
                                            isPinned = appPreferences.isFolderPinned(folder.id),
                                            onClick = { onFolderClick(folder.id, folder.name) },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                folderActionTarget = folder
                                            }
                                        )
                                    }
                                }
                                item {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }

                            // 2. Matching Videos Section
                            if (searchMatchingVideos.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("VIDEOS (${searchMatchingVideos.size})", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                items(searchMatchingVideos, key = { "search_vid_${it.id}" }) { video ->
                                    Box(modifier = Modifier.animateItem()) {
                                        VideoDetailedListItem(
                                            video = video,
                                            isFavorite = favorites.contains(video.uri.toString()),
                                            inSelectionMode = inSelectionMode,
                                            isSelected = selectedVideoIds.contains(video.id),
                                            onClick = {
                                                if (inSelectionMode) {
                                                    selectedVideoIds = if (selectedVideoIds.contains(video.id)) selectedVideoIds - video.id else selectedVideoIds + video.id
                                                } else {
                                                    onVideoClick(video.uri.toString())
                                                }
                                            },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!inSelectionMode) videoActionTarget = video
                                                else selectedVideoIds = selectedVideoIds + video.id
                                            },
                                            onFavoriteToggle = { toggleFavorite(video.uri.toString()) }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeChip == "All Videos" || activeChip == "Favorites") {
                        val flatVideos = displayFolders.filter { it.id != "recently_added" }.flatMap { it.videos }.distinctBy { it.id }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${if (activeChip == "Favorites") "FAVORITES" else "ALL VIDEOS"} (${flatVideos.size})",
                                    color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                )
                                MediaViewSwitcherMenu(
                                    currentViewType = currentMediaViewType,
                                    onViewTypeSelected = {
                                        currentMediaViewType = it
                                        appPreferences.saveMediaViewType(it)
                                    }
                                )
                            }
                        }
                        if (flatVideos.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (activeChip == "Favorites") "Tap the ♥ on any video to add it here" else "No videos found",
                                        color = c.textSecondary, fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        when (currentMediaViewType) {
                            MediaViewType.DETAILED_LIST -> {
                                itemsIndexed(flatVideos, key = { _, v -> "flat_det_${v.id}" }) { _, video ->
                                    Box(modifier = Modifier.animateItem()) {
                                        SwipeableVideoRow(
                                            video = video,
                                            isFavorite = favorites.contains(video.uri.toString()),
                                            inSelectionMode = inSelectionMode,
                                            isSelected = selectedVideoIds.contains(video.id),
                                            onClick = { onVideoClick(video.uri.toString()) },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!inSelectionMode) videoActionTarget = video
                                                else selectedVideoIds = selectedVideoIds + video.id
                                            },
                                            onFavoriteToggle = { toggleFavorite(video.uri.toString()) },
                                            onDelete = { deleteVideosConfirm = listOf(video) },
                                            onShare = {
                                                val intent = fileManager.buildShareIntent(video.uri)
                                                context.startActivity(android.content.Intent.createChooser(intent, "Share video"))
                                            },
                                            onSelectToggle = {
                                                selectedVideoIds = if (selectedVideoIds.contains(video.id))
                                                    selectedVideoIds - video.id else selectedVideoIds + video.id
                                            }
                                        )
                                    }
                                }
                            }
                            MediaViewType.COMPACT_LIST -> {
                                itemsIndexed(flatVideos, key = { _, v -> "flat_cmp_${v.id}" }) { _, video ->
                                    Box(modifier = Modifier.animateItem()) {
                                        VideoCompactListItem(
                                            video = video,
                                            isFavorite = favorites.contains(video.uri.toString()),
                                            inSelectionMode = inSelectionMode,
                                            isSelected = selectedVideoIds.contains(video.id),
                                            onClick = {
                                                if (inSelectionMode) {
                                                    selectedVideoIds = if (selectedVideoIds.contains(video.id))
                                                        selectedVideoIds - video.id else selectedVideoIds + video.id
                                                } else {
                                                    onVideoClick(video.uri.toString())
                                                }
                                            },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!inSelectionMode) videoActionTarget = video
                                                else selectedVideoIds = selectedVideoIds + video.id
                                            },
                                            onFavoriteToggle = { toggleFavorite(video.uri.toString()) }
                                        )
                                    }
                                }
                            }
                            MediaViewType.LARGE_CARD -> {
                                itemsIndexed(flatVideos, key = { _, v -> "flat_lrg_${v.id}" }) { _, video ->
                                    Box(modifier = Modifier.animateItem()) {
                                        VideoLargeCard(
                                            video = video,
                                            isFavorite = favorites.contains(video.uri.toString()),
                                            inSelectionMode = inSelectionMode,
                                            isSelected = selectedVideoIds.contains(video.id),
                                            onClick = {
                                                if (inSelectionMode) {
                                                    selectedVideoIds = if (selectedVideoIds.contains(video.id))
                                                        selectedVideoIds - video.id else selectedVideoIds + video.id
                                                } else {
                                                    onVideoClick(video.uri.toString())
                                                }
                                            },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!inSelectionMode) videoActionTarget = video
                                                else selectedVideoIds = selectedVideoIds + video.id
                                            },
                                            onFavoriteToggle = { toggleFavorite(video.uri.toString()) }
                                        )
                                    }
                                }
                            }
                            MediaViewType.GRID_2 -> {
                                val chunked = flatVideos.chunked(2)
                                itemsIndexed(chunked, key = { _, pair -> "flat_g2_${pair.first().id}" }) { _, rowVideos ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (video in rowVideos) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                VideoGridItem2(
                                                    video = video,
                                                    isFavorite = favorites.contains(video.uri.toString()),
                                                    inSelectionMode = inSelectionMode,
                                                    isSelected = selectedVideoIds.contains(video.id),
                                                    onClick = {
                                                        if (inSelectionMode) {
                                                            selectedVideoIds = if (selectedVideoIds.contains(video.id))
                                                                selectedVideoIds - video.id else selectedVideoIds + video.id
                                                        } else {
                                                            onVideoClick(video.uri.toString())
                                                        }
                                                    },
                                                    onLongPress = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!inSelectionMode) videoActionTarget = video
                                                        else selectedVideoIds = selectedVideoIds + video.id
                                                    },
                                                    onFavoriteToggle = { toggleFavorite(video.uri.toString()) }
                                                )
                                            }
                                        }
                                        if (rowVideos.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            MediaViewType.GRID_3 -> {
                                val chunked = flatVideos.chunked(3)
                                itemsIndexed(chunked, key = { _, trio -> "flat_g3_${trio.first().id}" }) { _, rowVideos ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (video in rowVideos) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                VideoGridItem3(
                                                    video = video,
                                                    isFavorite = favorites.contains(video.uri.toString()),
                                                    inSelectionMode = inSelectionMode,
                                                    isSelected = selectedVideoIds.contains(video.id),
                                                    onClick = {
                                                        if (inSelectionMode) {
                                                            selectedVideoIds = if (selectedVideoIds.contains(video.id))
                                                                selectedVideoIds - video.id else selectedVideoIds + video.id
                                                        } else {
                                                            onVideoClick(video.uri.toString())
                                                        }
                                                    },
                                                    onLongPress = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!inSelectionMode) videoActionTarget = video
                                                        else selectedVideoIds = selectedVideoIds + video.id
                                                    }
                                                )
                                            }
                                        }
                                        for (i in 0 until (3 - rowVideos.size)) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Continue Watching
                        if (continueWatchingVideos.isNotEmpty() && !isSearching) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("CONTINUE WATCHING", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("${continueWatchingVideos.size}", fontSize = 12.sp, color = c.textSecondary)
                                    }
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        items(continueWatchingVideos, key = { "cw_${it.uri}" }) { video ->
                                            ContinueWatchingCard(
                                                video = video,
                                                appPreferences = appPreferences,
                                                onClick = { onVideoClick(video.uri.toString()) },
                                                onLongPress = {
                                                    view.performHaptic(HapticType.HEAVY)
                                                    if (!inSelectionMode) videoActionTarget = video
                                                },
                                                onRemove = {
                                                    appPreferences.clearVideoProgress(video.uri.toString())
                                                    Toast.makeText(context, "Removed from Continue Watching", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Recently Added
                        if (recentlyAddedVideos.isNotEmpty() && !isSearching && activeChip == null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("RECENTLY ADDED", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("See all", color = c.accentBlue, fontSize = 12.sp,
                                        modifier = Modifier.clickable { activeChip = "All Videos" }.padding(4.dp))
                                }
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    items(recentlyAddedVideos, key = { "recent_${it.id}" }) { video ->
                                        RecentlyAddedVideoCard(
                                            video = video,
                                            appPreferences = appPreferences,
                                            onClick = { onVideoClick(video.uri.toString()) },
                                            onLongPress = {
                                                view.performHaptic(HapticType.HEAVY)
                                                if (!inSelectionMode) videoActionTarget = video
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Folders header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${displayFolders.size} FOLDERS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                                }
                                Row(
                                    modifier = Modifier
                                        .then(if (c.isMatte) Modifier else Modifier.shadow(elevation = 6.dp, shape = CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor))
                                        .clip(CircleShape)
                                        .background(if (c.isMatte) SolidColor(c.cardBgElevated) else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                        .border(1.2.dp, if (c.isMatte) SolidColor(c.glassBorder) else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            isGridView = !isGridView
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                            contentDescription = "Toggle Layout",
                                            tint = if (isGridView) c.accentBlue else c.textSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(14.dp).background(c.glassBorder))
                                    Box {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showSortMenu = true
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Sort,
                                                contentDescription = "Sort",
                                                tint = c.textSecondary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            containerColor = c.dropdownBg,
                                            modifier = Modifier
                                                .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                                                .shadow(12.dp, RoundedCornerShape(14.dp))
                                        ) {
                                            Text(
                                                "SORT ORDER",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = c.textSecondary,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                                                val isSelected = sortOrder == order
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            icon,
                                                            contentDescription = null,
                                                            tint = if (isSelected) c.accentBlue else c.textSecondary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    text = {
                                                        Text(
                                                            label,
                                                            color = if (isSelected) c.accentBlue else c.textPrimary,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 14.sp
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (isSelected) {
                                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(18.dp))
                                                        }
                                                    },
                                                    onClick = {
                                                        sortOrder = order
                                                        appPreferences.saveSortOrder(order.name)
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Folder items
                        if (isGridView) {
                            val chunked = displayFolders.chunked(2)
                            items(chunked, key = { "chunk_${it.first().id}" }) { chunk ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).animateItem(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (folder in chunk) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            FolderGridItem(
                                                folder = folder,
                                                isPinned = appPreferences.isFolderPinned(folder.id),
                                                onClick = { onFolderClick(folder.id, folder.name) },
                                                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); folderActionTarget = folder }
                                            )
                                        }
                                    }
                                    if (chunk.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(displayFolders, key = { "folder_${it.id}" }) { folder ->
                                Box(modifier = Modifier.animateItem()) {
                                    FolderItem(
                                        folder = folder,
                                        isPinned = appPreferences.isFolderPinned(folder.id),
                                        onClick = { onFolderClick(folder.id, folder.name) },
                                        onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); folderActionTarget = folder }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Floating Nav Pill + Animated Music Playing Indicator in Video Mode
        if (!musicPlayerExpanded) {
            val isMusicPlaying by remember { MusicService.isMusicPlaying }
            val nowPlayingTitle by remember { MusicService.nowPlayingTitle }
            val nowPlayingArtist by remember { MusicService.nowPlayingArtist }
            val nowPlayingArtUri by remember { MusicService.nowPlayingArtUri }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Progressive blur & frosted dark gradient: low blur/translucent at top, increasing density downwards
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    c.baseBackground.copy(alpha = 0.35f),
                                    c.baseBackground.copy(alpha = 0.75f),
                                    c.baseBackground.copy(alpha = 0.94f),
                                    c.baseBackground
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 28.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Animated "Now Playing" pill visible on Video tab with controls & 100% opaque background
                AnimatedVisibility(
                    visible = currentTab == "Video" && (isMusicPlaying || nowPlayingTitle.isNotBlank()),
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp, start = 12.dp, end = 12.dp)
                            .then(if (c.isMatte) Modifier else Modifier.shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = c.accentBlue.copy(0.4f), spotColor = c.accentBlue))
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (c.isMatte) c.cardBg else Color(0xFF141724)) // 100% solid, fully opaque background
                            .border(1.2.dp, if (c.isMatte) androidx.compose.ui.graphics.SolidColor(c.glassBorder) else Brush.horizontalGradient(listOf(c.accentBlue, c.cardBorderHighlight)), RoundedCornerShape(24.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Thumbnail / Fallback (Click to open Music tab)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E2136))
                                    .border(0.8.dp, c.cardBorderHighlight, RoundedCornerShape(10.dp))
                                    .clickable { currentTab = "Music" },
                                contentAlignment = Alignment.Center
                            ) {
                                if (nowPlayingArtUri != null) {
                                    AsyncImage(
                                        model = nowPlayingArtUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = c.accentBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Song title & artist (Click to open Music tab)
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 130.dp)
                                    .clickable { currentTab = "Music" }
                            ) {
                                Text(
                                    text = nowPlayingTitle.ifBlank { "Music Playing" },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = nowPlayingArtist.ifBlank { "Tap to open" },
                                    color = c.accentBlue,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Previous button
                            IconButton(
                                onClick = { MusicService.playPrevious() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Play / Pause button
                            IconButton(
                                onClick = { MusicService.togglePlayPause() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(c.accentBlue)
                            ) {
                                Icon(
                                    imageVector = if (isMusicPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = c.onAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Next button
                            IconButton(
                                onClick = { MusicService.playNext() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Animated equalizer bars
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.height(16.dp).padding(end = 4.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingBars")
                                val bar1 by infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
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
                                    initialValue = 0.1f,
                                    targetValue = 0.9f,
                                    animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
                                    label = "b3"
                                )
                                val bar4 by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 0.8f,
                                    animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
                                    label = "b4"
                                )

                                listOf(bar1, bar2, bar3, bar4).forEach { heightFraction ->
                                    val actualHeight = if (isMusicPlaying) heightFraction else 0.2f
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(actualHeight)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.White, c.accentBlue)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // Adaptive Theme Nav Pill
                Box(
                    modifier = Modifier
                        .then(if (c.isMatte) Modifier.shadow(10.dp, CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor) else Modifier.shadow(16.dp, CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor))
                        .clip(CircleShape)
                        .background(c.cardBgElevated)
                        .border(1.dp, c.glassBorder, CircleShape)
                ) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val view = androidx.compose.ui.platform.LocalView.current
                        listOf("Video" to Icons.Rounded.Movie, "Music" to Icons.Rounded.MusicNote).forEach { (label, icon) ->
                            val selected = currentTab == label
                            val pillBgColor by animateColorAsState(
                                targetValue = if (selected) {
                                    if (c.isDark) c.accentBlue.copy(alpha = 0.22f) else c.accentBlue.copy(alpha = 0.14f)
                                } else Color.Transparent,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "pillBgColor"
                            )
                            val pillTextColor by animateColorAsState(
                                targetValue = if (selected) c.accentBlue else c.textSecondary,
                                animationSpec = tween(220),
                                label = "pillTextColor"
                            )
                            val pillElevation by animateDpAsState(
                                targetValue = if (selected && !c.isMatte) 4.dp else 0.dp,
                                animationSpec = tween(250),
                                label = "pillElevation"
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .shadow(pillElevation, CircleShape, ambientColor = if (selected) c.accentBlue.copy(0.45f) else Color.Transparent)
                                    .clip(CircleShape)
                                    .background(pillBgColor)
                                    .then(
                                        if (selected) {
                                            Modifier.border(1.dp, c.accentBlue.copy(alpha = 0.45f), CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .bounceClick { 
                                        if (currentTab != label) view.performHaptic(HapticType.LIGHT)
                                        currentTab = label
                                        if (label != "Video") selectedVideoIds = emptySet() 
                                    }
                                    .padding(horizontal = 15.dp, vertical = 9.dp)
                            ) {
                                Icon(icon, contentDescription = label, tint = pillTextColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(label, color = pillTextColor, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            }
        }

        // Video Context Bottom Sheet
        videoActionTarget?.let { video ->
            ModalBottomSheet(
                onDismissRequest = { videoActionTarget = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = c.dropdownBg,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(c.glassBg)) {
                            AsyncImage(
                                model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
                                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.title.substringBeforeLast("."), color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatSize(video.size), color = c.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = c.glassBorder)
                    Spacer(Modifier.height(8.dp))
                    // Actions
                    VideoActionItem(Icons.Rounded.PlayArrow, "Play", c.accentBlue) { onVideoClick(video.uri.toString()); videoActionTarget = null }
                    VideoActionItem(if (favorites.contains(video.uri.toString())) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (favorites.contains(video.uri.toString())) "Remove from Favorites" else "Add to Favorites",
                        if (favorites.contains(video.uri.toString())) Color(0xFFFF4D6D) else c.textPrimary) {
                        toggleFavorite(video.uri.toString()); videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.DriveFileRenameOutline, "Rename", c.textPrimary) { renameVideoTarget = video; videoActionTarget = null }
                    VideoActionItem(Icons.Rounded.Share, "Share", c.textPrimary) {
                        val intent = fileManager.buildShareIntent(video.uri)
                        context.startActivity(android.content.Intent.createChooser(intent, "Share video"))
                        videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.Info, "Video Info", c.textPrimary) { videoInfoTarget = video; videoActionTarget = null }
                    if (appPreferences.getVideoProgress(video.uri.toString()) > 0) {
                        VideoActionItem(Icons.Rounded.History, "Clear Watch Progress", c.textSecondary) {
                            appPreferences.clearVideoProgress(video.uri.toString())
                            refreshTrigger++; videoActionTarget = null
                        }
                    }
                    VideoActionItem(Icons.Rounded.CheckBox, "Select", c.textPrimary) {
                        selectedVideoIds = selectedVideoIds + video.id; videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.Delete, "Move to Bin", Color(0xFFE53935)) { deleteVideosConfirm = listOf(video); videoActionTarget = null }
                }
            }
        }

        // Folder Context Bottom Sheet
        folderActionTarget?.let { folder ->
            val isPinned = appPreferences.isFolderPinned(folder.id)
            ModalBottomSheet(
                onDismissRequest = { folderActionTarget = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = c.dropdownBg,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(folder.name, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("${folder.videos.size} videos", color = c.textSecondary, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = c.glassBorder)
                    Spacer(Modifier.height(8.dp))
                    VideoActionItem(Icons.Rounded.FolderOpen, "Open Folder", c.accentBlue) { onFolderClick(folder.id, folder.name); folderActionTarget = null }
                    VideoActionItem(
                        if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin,
                        if (isPinned) "Unpin from Top" else "Pin to Top", c.textPrimary
                    ) { appPreferences.togglePinFolder(folder.id); refreshTrigger++; folderActionTarget = null }
                    VideoActionItem(Icons.Rounded.Share, "Share All Videos", c.textPrimary) {
                        val uris = folder.videos.map { it.uri }
                        val intent = fileManager.buildShareMultipleIntent(uris)
                        context.startActivity(android.content.Intent.createChooser(intent, "Share folder"))
                        folderActionTarget = null
                    }
                    if (folder.id != "last_played" && folder.id != "all_videos") {
                        VideoActionItem(Icons.Rounded.Delete, "Move All to Bin", Color(0xFFE53935)) { deleteFolderConfirm = folder; folderActionTarget = null }
                    }
                }
            }
        }

        // Rename Dialog
        renameVideoTarget?.let { video ->
            var newName by remember { mutableStateOf(video.title.substringBeforeLast(".")) }
            val ext = video.title.substringAfterLast(".", "")
            AlertDialog(
                onDismissRequest = { renameVideoTarget = null },
                containerColor = c.dropdownBg,
                title = { Text("Rename", color = c.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New name", color = c.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accentBlue, unfocusedBorderColor = c.glassBorder,
                            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accentBlue
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val finalName = if (ext.isNotEmpty()) "$newName.$ext" else newName
                        coroutineScope.launch {
                            val result = fileManager.renameVideo(video.id, finalName)
                            when (result) {
                                is com.example.ymediaplayer.data.FileOperationResult.NeedsConfirmation -> deleteRequestLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build())
                                is com.example.ymediaplayer.data.FileOperationResult.Success -> { refreshTrigger++; renameVideoTarget = null }
                                is com.example.ymediaplayer.data.FileOperationResult.Failure -> android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Rename", color = c.accentBlue) }
                },
                dismissButton = { TextButton(onClick = { renameVideoTarget = null }) { Text("Cancel", color = c.textSecondary) } }
            )
        }

        // Delete Videos Confirm Dialog
        if (deleteVideosConfirm.isNotEmpty()) {
            val count = deleteVideosConfirm.size
            AlertDialog(
                onDismissRequest = { deleteVideosConfirm = emptyList() },
                containerColor = c.dropdownBg,
                title = { Text(if (count == 1) "Move to Recycle Bin?" else "Move $count Videos to Bin?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Videos will be moved to your device's Recycle Bin (Google Files / Gallery) where they can be restored within 30 days.", color = c.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val uris = deleteVideosConfirm.map { it.uri }
                            val result = fileManager.deleteVideos(uris)
                            when (result) {
                                is com.example.ymediaplayer.data.FileOperationResult.NeedsConfirmation -> deleteRequestLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build())
                                is com.example.ymediaplayer.data.FileOperationResult.Success -> {
                                    selectedVideoIds = emptySet<Long>()
                                    deleteVideosConfirm = emptyList()
                                    refreshTrigger++
                                }
                                is com.example.ymediaplayer.data.FileOperationResult.Failure -> android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Move to Bin", color = Color(0xFFE53935)) }
                },
                dismissButton = { TextButton(onClick = { deleteVideosConfirm = emptyList() }) { Text("Cancel", color = c.textSecondary) } }
            )
        }

        // Delete Folder Confirm Dialog
        deleteFolderConfirm?.let { folder ->
            AlertDialog(
                onDismissRequest = { deleteFolderConfirm = null },
                containerColor = c.dropdownBg,
                title = { Text("Move \"${folder.name}\" to Recycle Bin?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("All ${folder.videos.size} videos in this folder will be moved to your device's Recycle Bin (Google Files / Gallery) where they can be restored.", color = c.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val uris = folder.videos.map { it.uri }
                            val result = fileManager.deleteVideos(uris)
                            when (result) {
                                is com.example.ymediaplayer.data.FileOperationResult.NeedsConfirmation -> deleteRequestLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build())
                                is com.example.ymediaplayer.data.FileOperationResult.Success -> {
                                    deleteFolderConfirm = null
                                    refreshTrigger++
                                }
                                is com.example.ymediaplayer.data.FileOperationResult.Failure -> android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Move All to Bin", color = Color(0xFFE53935)) }
                },
                dismissButton = { TextButton(onClick = { deleteFolderConfirm = null }) { Text("Cancel", color = c.textSecondary) } }
            )
        }

        // Video Info Sheet
        videoInfoTarget?.let { video ->
            LaunchedEffect(video.id) {
                videoInfoData = fileManager.getVideoInfo(video.id)
            }
            videoInfoData?.let { info ->
                ModalBottomSheet(
                    onDismissRequest = { videoInfoTarget = null; videoInfoData = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = c.dropdownBg,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Video Info", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    val allDetails = buildString {
                                        appendLine("Filename: ${info.filename}")
                                        appendLine("Resolution: ${info.resolution}")
                                        appendLine("Duration: ${formatTime(info.duration)}")
                                        appendLine("File Size: ${formatSize(info.size)}")
                                        appendLine("Format: ${info.mimeType}")
                                        appendLine("Added: ${fileManager.formatDate(info.dateAdded)}")
                                        if (info.path.isNotEmpty()) appendLine("Path: ${info.path}")
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Video Details", allDetails))
                                    Toast.makeText(context, "Video details copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy All Details", tint = c.accentBlue, modifier = Modifier.size(19.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        VideoInfoRow("Filename", info.filename, c)
                        VideoInfoRow("Resolution", info.resolution, c)
                        VideoInfoRow("Duration", formatTime(info.duration), c)
                        VideoInfoRow("File Size", formatSize(info.size), c)
                        VideoInfoRow("Format", info.mimeType, c)
                        VideoInfoRow("Added", fileManager.formatDate(info.dateAdded), c)
                        if (info.path.isNotEmpty()) {
                            VideoInfoRow(
                                label = "Path",
                                value = info.path,
                                c = c,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("File Location", info.path))
                                    Toast.makeText(context, "Location copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Theme Dialog
        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = themeController.colorTheme,
                onSelectTheme = { themeController.updateColorTheme(it) },
                onDismiss = { showThemeDialog = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
internal fun CanvasBg(offsetProvider: () -> Float = { 0f }) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember(context) { AppPreferences(context) }
    val glowVersion = appPreferences.lastProgressUpdate.longValue
    val glowStrength = remember(glowVersion) { appPreferences.getAmbientGlowStrength() }

    // Enhanced vivid core & falloff for all 3 modes
    val coreAlpha = when {
        c.isDark && c.baseBackground == Color(0xFF000000) -> 0.45f * glowStrength // OLED Black (rich vibrant halo)
        c.isDark -> 0.38f * glowStrength                                          // Slate Grey
        else -> 0.28f * glowStrength                                              // Clean Light
    }.coerceIn(0f, 0.95f)
    val midAlpha = (coreAlpha * 0.42f).coerceIn(0f, 0.55f)
    val edgeAlpha = (coreAlpha * 0.12f).coerceIn(0f, 0.20f)
    val secAlpha = (coreAlpha * 0.25f).coerceIn(0f, 0.30f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.baseBackground)
            .drawBehind {
                if (coreAlpha > 0.01f) {
                    // Centered circular radial ambient glow based on the theme color, softly fading outwards
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.accentBlue.copy(alpha = coreAlpha),
                                c.accentBlue.copy(alpha = midAlpha),
                                c.accentBlue.copy(alpha = edgeAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.width * 0.92f
                        )
                    )
                    // Secondary corner ambient accent for rich dual-tone depth
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.accentGreen.copy(alpha = secAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.75f),
                            radius = size.width * 0.75f
                        )
                    )
                }
            }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// MULTI-SELECT TOP BAR
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onSelectAll: () -> Unit
) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().background(if (c.isMatte) c.baseBackground else c.topBarScrim).border(1.dp, if (c.isMatte) androidx.compose.ui.graphics.SolidColor(c.glassBorder) else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder)), RectangleShape)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = c.textPrimary)
            }
            Text("$selectedCount selected", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll) { Text("Select All", color = c.accentBlue) }
            IconButton(onClick = onShareSelected) { Icon(Icons.Rounded.Share, contentDescription = "Share", tint = c.textPrimary) }
            IconButton(onClick = onDeleteSelected) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFE53935)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CHIP
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChipItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isSelected: Boolean,
    count: Int? = null,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = CircleShape,
                ambientColor = if (isSelected) c.accentBlue.copy(0.45f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.55f) else c.cardShadowColor
            ))
            .clip(CircleShape)
            .background(
                if (isSelected) Brush.verticalGradient(listOf(Color(0xFF353848), Color(0xFF2B2E3C)))
                else Brush.verticalGradient(listOf(Color(0xFF282A36), Color(0xFF20222C)))
            )
            .border(
                width = 1.dp,
                brush = if (isSelected) SolidColor(c.accentBlue.copy(alpha = 0.65f))
                else SolidColor(Color.White.copy(alpha = 0.10f)),
                shape = CircleShape
            )
            .bounceClick {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(start = 14.dp, end = if (count != null && count > 0) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) c.accentBlue else c.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (isSelected) Color.White else c.textSecondary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1E202A))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.accentBlue
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONTINUE WATCHING CARD
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ContinueWatchingCard(
    video: VideoItem,
    appPreferences: AppPreferences,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val progress = appPreferences.getVideoProgress(video.uri.toString())
    val dur = if (video.duration > 0L) video.duration else appPreferences.getVideoDuration(video.uri.toString())
    val ratio = if (dur > 0L) (progress.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val pct = (ratio * 100).toInt()

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(96.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = c.cardShadowColor,
                spotColor = c.cardShadowColor
            ))
            .clip(RoundedCornerShape(18.dp))
            .background(if (c.isMatte) SolidColor(c.cardBg) else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
            .border(
                width = 1.4.dp,
                brush = if (c.isMatte) SolidColor(c.glassBorder) else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)),
                shape = RoundedCornerShape(18.dp)
            )
            .bounceClick(onClick = onClick, onLongClick = onLongPress)
    ) {
        AsyncImage(
            model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        // Top right duration + percentage badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .then(if (c.isMatte) Modifier else Modifier.shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(0.6f)))
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xD90E0F16))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (dur > 0L) {
                    Text(
                        formatTime(dur),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                }
                Text("$pct%", color = c.accentBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Bottom scrim & title
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f))))
        )
        Text(
            video.title.substringBeforeLast("."), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 9.dp)
        )
        // Progress bar
        Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.25f))) {
            Box(modifier = Modifier.fillMaxWidth(ratio).height(3.dp).background(c.accentBlue))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// RECENTLY ADDED CARD (200x130dp)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun RecentlyAddedVideoCard(
    video: VideoItem,
    appPreferences: AppPreferences,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(96.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = c.cardShadowColor,
                spotColor = c.cardShadowColor
            ))
            .clip(RoundedCornerShape(18.dp))
            .background(if (c.isMatte) SolidColor(c.cardBg) else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
            .border(
                width = 1.4.dp,
                brush = if (c.isMatte) {
                    SolidColor(c.glassBorder)
                } else if (progress > 0) Brush.verticalGradient(listOf(Color.White.copy(0.5f), c.accentBlue.copy(0.7f)))
                else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)),
                shape = RoundedCornerShape(18.dp)
            )
            .bounceClick(onClick = onClick, onLongClick = onLongPress)
    ) {
        AsyncImage(
            model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        // Top right total duration badge
        if (video.duration > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .then(if (c.isMatte) Modifier else Modifier.shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(0.6f)))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xD90E0F16))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Text(
                    formatTime(video.duration),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f)))))
        Text(
            video.title.substringBeforeLast("."), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = if (progress > 0) 9.dp else 7.dp)
        )
        if (progress > 0 && video.duration > 0) {
            val ratio = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.25f))) {
                Box(modifier = Modifier.fillMaxWidth(ratio).height(3.dp).background(c.accentBlue))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FOLDER ITEM (enhanced with thumbnail + path)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun FolderItem(
    folder: VideoFolder,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val themeCtrl = LocalThemeController.current
    val haptic = LocalHapticFeedback.current
    val isAllVideos = folder.id == "all_videos"
    val isHistory = isAllVideos || folder.id == "last_played" || folder.id == "recently_added"
    val context = LocalContext.current
    val firstVideo = folder.videos.firstOrNull()

    val totalFolderSize = remember(folder.videos) { folder.videos.sumOf { it.size } }
    val relativePath = folder.videos.firstOrNull()?.relativePath?.trim('/') ?: ""
    val pathLabel = if (relativePath.contains('/')) {
        relativePath.substringBeforeLast('/')
    } else if (relativePath.isNotEmpty() && !relativePath.equals(folder.name, ignoreCase = true)) {
        relativePath
    } else if (!isHistory) {
        "Internal storage"
    } else {
        ""
    }
    val subtitleText = buildString {
        if (pathLabel.isNotEmpty() && !isHistory) {
            append(pathLabel)
            append(" · ")
        }
        append(formatSize(totalFolderSize))
    }

    val appPreferences = remember(context) { AppPreferences(context) }
    val cornerRadius = when (appPreferences.getUiCornerStyle()) {
        "SLEEK" -> 16.dp
        "EXTRA" -> 28.dp
        else -> 22.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = c.cardShadowColor,
                spotColor = c.cardShadowColor
            ))
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (c.isMatte) SolidColor(c.cardBg)
                else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
            )
            .border(
                width = 1.dp,
                brush = if (c.isMatte) SolidColor(c.glassBorder)
                else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder)),
                shape = RoundedCornerShape(cornerRadius)
            )
            .bounceClick(
                scaleDown = 0.965f,
                onLongClick = onLongPress,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder icon / thumbnail
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.cardBgElevated)
                    .border(
                        width = 1.dp,
                        brush = SolidColor(c.glassBorder),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isAllVideos) {
                    Icon(
                        Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = c.accentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (firstVideo != null && !isHistory) {
                    AsyncImage(
                        model = remember(firstVideo.uri) { buildVideoThumbnailRequest(context, firstVideo.uri, firstVideo.duration) },
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Icon(
                        if (isHistory) Icons.Rounded.History else Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = if (isHistory) c.accentBlue else c.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitleText.isNotEmpty()) {
                    Text(subtitleText, fontSize = 11.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            // Count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cardBgElevated)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(folder.videos.size.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accentBlue)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
        }

        if (isPinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 7.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c.cardBgElevated)
                    .border(0.8.dp, c.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    tint = c.accentBlue,
                    modifier = Modifier.size(10.dp).rotate(-35f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FOLDER GRID ITEM
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun FolderGridItem(
    folder: VideoFolder,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val isAllVideos = folder.id == "all_videos"
    val isHistory = isAllVideos || folder.id == "last_played" || folder.id == "recently_added"
    val context = LocalContext.current
    val previewVideos = folder.videos.take(4)

    val appPreferences = remember(context) { AppPreferences(context) }
    val gridCornerRadius = when (appPreferences.getUiCornerStyle()) {
        "SLEEK" -> 14.dp
        "EXTRA" -> 26.dp
        else -> 20.dp
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(gridCornerRadius),
                ambientColor = c.cardShadowColor,
                spotColor = c.cardShadowColor
            ))
            .clip(RoundedCornerShape(gridCornerRadius))
            .background(
                if (c.isMatte) SolidColor(c.cardBg)
                else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
            )
            .border(
                width = 1.dp,
                brush = if (c.isMatte) SolidColor(c.glassBorder)
                else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder)),
                shape = RoundedCornerShape(gridCornerRadius)
            )
            .bounceClick(scaleDown = 0.95f, onLongClick = onLongPress, onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 2x2 mosaic preview (or icon)
            if (previewVideos.isNotEmpty() && !isHistory) {
                Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            previewVideos.getOrNull(0)?.let { video ->
                                AsyncImage(
                                    model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
                                    contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                            previewVideos.getOrNull(2)?.let { video ->
                                AsyncImage(
                                    model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
                                    contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            previewVideos.getOrNull(1)?.let { video ->
                                AsyncImage(
                                    model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
                                    contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                            previewVideos.getOrNull(3)?.let { video ->
                                AsyncImage(
                                    model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
                                    contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(c.accentBlue.copy(0.12f)), contentAlignment = Alignment.Center) {
                    Icon(if (isAllVideos) Icons.Rounded.VideoLibrary else if (isHistory) Icons.Rounded.History else Icons.Rounded.Folder, contentDescription = null, tint = if (isAllVideos || isHistory) c.accentBlue else c.textSecondary, modifier = Modifier.size(36.dp))
                }
            }
            // Info row
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${folder.videos.size}", fontSize = 12.sp, color = c.accentBlue, fontWeight = FontWeight.Bold)
            }
        }

        if (isPinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 7.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c.cardBgElevated)
                    .border(0.8.dp, c.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    tint = c.accentBlue,
                    modifier = Modifier.size(10.dp).rotate(-35f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SWIPEABLE VIDEO ROW
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableVideoRow(
    video: VideoItem,
    isFavorite: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSelectToggle: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                SwipeToDismissBoxValue.StartToEnd -> { onShare(); false }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = !inSelectionMode,
        enableDismissFromStartToEnd = !inSelectionMode,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935)
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1565C0)
                    else -> Color.Transparent
                }, label = "swipe_bg"
            )
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(18.dp)).background(bgColor),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.padding(end = 24.dp))
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.padding(start = 24.dp))
                    else -> {}
                }
            }
        },
        content = {
            VideoListItem(
                video = video, isFavorite = isFavorite, inSelectionMode = inSelectionMode, isSelected = isSelected,
                onClick = if (inSelectionMode) onSelectToggle else onClick,
                onLongPress = onLongPress, onFavoriteToggle = onFavoriteToggle
            )
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// VIDEO LIST ITEM
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoListItem(
    video: VideoItem,
    isFavorite: Boolean = false,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 10.dp else 6.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = if (isSelected) c.accentBlue.copy(0.35f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.45f) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (c.isMatte) {
                    SolidColor(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.cardBg)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(c.accentBlue.copy(0.28f), c.cardBg))
                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                }
            )
            .border(
                width = if (isSelected) 1.8.dp else 1.3.dp,
                brush = if (c.isMatte) {
                    androidx.compose.ui.graphics.SolidColor(if (isSelected) c.accentBlue else c.glassBorder)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(Color.White.copy(0.65f), c.accentBlue))
                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                },
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            }
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Selection checkbox
            AnimatedVisibility(visible = inSelectionMode) {
                Box(
                    modifier = Modifier.size(24.dp)
                        .shadow(4.dp, CircleShape, ambientColor = if (isSelected) c.accentBlue else Color.Black.copy(0.5f))
                        .clip(CircleShape)
                        .background(if (isSelected) c.accentBlue else c.cardBg)
                        .border(1.5.dp, if (isSelected) Color.White else c.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            // Thumbnail with 3D bezel
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(64.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(0.65f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF282A36))
                    .border(1.2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = remember(video.uri) { buildVideoThumbnailRequest(context, video.uri, video.duration) },
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .shadow(2.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(formatTime(video.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (progress > 0 && video.duration > 0) {
                    val r = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(0.5f))) {
                        Box(modifier = Modifier.fillMaxWidth(r).height(3.dp).background(c.accentBlue))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title.substringBeforeLast("."), fontSize = 15.sp, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (progress > 0 && video.duration > 0) {
                        val pct = ((progress.toFloat() / video.duration) * 100).toInt()
                        Text("Resume: ${formatTime(progress)} ($pct%)", fontSize = 12.sp, color = c.accentBlue, fontWeight = FontWeight.SemiBold)
                        Text("·", fontSize = 10.sp, color = c.textSecondary)
                    }
                    Text(formatSize(video.size), fontSize = 12.sp, color = c.textSecondary)
                }
            }
            if (!inSelectionMode) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null, tint = if (isFavorite) Color(0xFFFF4D6D) else c.textSecondary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoInfoRow(
    label: String,
    value: String,
    c: com.example.ymediaplayer.theme.AppColors,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = c.textSecondary)
            Text(value, fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = c.accentBlue,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
    HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(top = 6.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
// THEME DIALOG
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ThemeSelectionDialog(
    currentTheme: com.example.ymediaplayer.ui.PlayerTheme,
    onSelectTheme: (com.example.ymediaplayer.ui.PlayerTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(c.dropdownBg).border(1.dp, c.glassBorder, RoundedCornerShape(24.dp)).padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = c.accentBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("App Color Theme", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.textSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Select a color theme for the app:", fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.example.ymediaplayer.ui.PlayerTheme.entries.forEach { theme ->
                    val isSelected = currentTheme == theme
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.glassBgNested)
                            .border(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) theme.primaryAccent else c.glassBorder, RoundedCornerShape(16.dp))
                            .clickable { onSelectTheme(theme); onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(theme.previewGradient)).border(1.dp, Color.White.copy(0.4f), CircleShape))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(theme.displayName, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, color = if (isSelected) theme.primaryAccent else c.textPrimary)
                            Text(theme.description, fontSize = 11.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isSelected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
