package com.example.ymediaplayer.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.data.*
import com.example.ymediaplayer.service.MusicService
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val repository = remember { VideoRepository(context) }
    val appPreferences = remember { AppPreferences(context) }
    val fileManager = remember { FileManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var folder by remember { mutableStateOf<VideoFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Media View Type & Sorting
    var currentViewType by remember { mutableStateOf(appPreferences.getMediaViewType()) }
    var sortOrder by remember {
        mutableStateOf(runCatching { SortOrder.valueOf(appPreferences.getSortOrder()) }.getOrDefault(SortOrder.DATE))
    }
    var showSortMenu by remember { mutableStateOf(false) }

    // Search state
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // Multi-select state
    var selectedVideoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val inSelectionMode = selectedVideoIds.isNotEmpty()

    // Context & Dialog states
    var videoActionTarget by remember { mutableStateOf<VideoItem?>(null) }
    var renameVideoTarget by remember { mutableStateOf<VideoItem?>(null) }
    var deleteVideosConfirm by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var videoInfoTarget by remember { mutableStateOf<VideoItem?>(null) }
    var videoInfoData by remember { mutableStateOf<VideoInfo?>(null) }
    var favorites by remember { mutableStateOf(appPreferences.getFavorites()) }

    val toggleFavorite: (String) -> Unit = { uri ->
        appPreferences.toggleFavorite(uri)
        favorites = appPreferences.getFavorites()
    }

    // Android 11+ Recycle Bin Permission Launcher
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedVideoIds = emptySet()
            deleteVideosConfirm = emptyList()
            refreshTrigger++
        }
    }

    // Load folder content
    LaunchedEffect(folderId, refreshTrigger) {
        isLoading = true
        val allFolders = repository.getFoldersWithVideos()
        if (folderId == "all_videos") {
            val allVideos = allFolders.flatMap { it.videos }.distinctBy { it.id }
            folder = VideoFolder(id = "all_videos", name = "All Videos", videos = allVideos)
        } else if (folderId == "recently_added") {
            val recentVideos = allFolders.flatMap { it.videos }.sortedByDescending { it.id }.take(50)
            folder = VideoFolder(id = "recently_added", name = "Recent Added", videos = recentVideos)
        } else if (folderId == "last_played") {
            val playedUris = appPreferences.getPlayedUris()
            val allVideosMap = allFolders.flatMap { it.videos }.associateBy { it.uri.toString() }
            val playedVideos = playedUris.mapNotNull { allVideosMap[it] }
            folder = VideoFolder(id = "last_played", name = "Last Played", videos = playedVideos)
        } else {
            folder = allFolders.find { it.id == folderId }
        }
        isLoading = false
    }

    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
    }

    // Back handling: clear search, exit selection, or go back
    BackHandler(enabled = true) {
        when {
            inSelectionMode -> selectedVideoIds = emptySet()
            isSearching -> {
                isSearching = false
                searchQuery = ""
            }
            else -> onBack()
        }
    }

    // Background animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_offset"
    )

    // Processed and sorted videos
    val rawVideos = folder?.videos ?: emptyList()
    val displayVideos = remember(rawVideos, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isNotBlank()) {
            rawVideos.filter { it.title.contains(searchQuery, ignoreCase = true) }
        } else {
            rawVideos
        }
        filtered.sortVideosWithOrder(sortOrder)
    }

    val totalFolderSize = remember(rawVideos) { rawVideos.sumOf { it.size } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic Ambient Background (Draw-phase only to prevent 60fps recomposition)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.baseBackground)
                .drawBehind {
                    val offset = gradientOffset.value
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(c.gradientBlob1.copy(alpha = 0.8f), Color.Transparent),
                            center = Offset(offset, offset * 1.5f),
                            radius = 1200f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(c.gradientBlob2.copy(alpha = 0.9f), Color.Transparent),
                            center = Offset(1000f - offset, 2000f - offset),
                            radius = 1500f
                        )
                    )
                }
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (inSelectionMode) {
                    // Multi-Select Top Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.topBarScrim)
                            .border(1.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder)), RectangleShape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedVideoIds = emptySet() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = c.textPrimary)
                            }
                            Text(
                                "${selectedVideoIds.size} selected",
                                color = c.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                selectedVideoIds = displayVideos.map { it.id }.toSet()
                            }) {
                                Text("Select All", color = c.accentBlue, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = {
                                val uris = displayVideos.filter { selectedVideoIds.contains(it.id) }.map { it.uri }
                                val intent = fileManager.buildShareMultipleIntent(uris)
                                context.startActivity(Intent.createChooser(intent, "Share videos"))
                                selectedVideoIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = c.textPrimary)
                            }
                            IconButton(onClick = {
                                val items = displayVideos.filter { selectedVideoIds.contains(it.id) }
                                deleteVideosConfirm = items
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                            }
                        }
                    }
                } else {
                    // Standard Top Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.topBarScrim)
                            .border(1.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder)), RectangleShape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(6.dp, CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                    .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(20.dp))
                            }

                            Spacer(Modifier.width(12.dp))

                            if (isSearching) {
                                // Live In-Folder Search Input
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search in folder...", color = c.textHint, fontSize = 14.sp) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = c.glassBg,
                                        unfocusedContainerColor = c.glassBg,
                                        focusedTextColor = c.textPrimary,
                                        unfocusedTextColor = c.textPrimary,
                                        cursorColor = c.accentBlue,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .shadow(4.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                        .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), RoundedCornerShape(12.dp))
                                        .focusRequester(searchFocusRequester)
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search", tint = c.textPrimary)
                                }
                            } else {
                                // Folder Title & Subtitle
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        folderName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${rawVideos.size} videos · ${formatSize(totalFolderSize)}",
                                        fontSize = 11.5.sp,
                                        color = c.textSecondary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Search Icon
                                    IconButton(
                                        onClick = { isSearching = true },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .shadow(6.dp, CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                            .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), CircleShape)
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = c.textPrimary, modifier = Modifier.size(19.dp))
                                    }

                                    // Media View Switcher Button & Dropdown
                                    MediaViewSwitcherMenu(
                                        currentViewType = currentViewType,
                                        onViewTypeSelected = { newType ->
                                            currentViewType = newType
                                            appPreferences.saveMediaViewType(newType)
                                        }
                                    )

                                    // Sort Menu Button & Dropdown
                                    Box {
                                        IconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .shadow(6.dp, CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor)
                                                .clip(CircleShape)
                                                .background(Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
                                                .border(1.2.dp, Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), CircleShape)
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort", tint = c.textPrimary, modifier = Modifier.size(19.dp))
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
                    }
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accentBlue, strokeWidth = 3.dp)
                }
            } else if (displayVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(32.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.glassBg)
                            .border(1.2.dp, c.glassBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 36.dp, vertical = 28.dp)
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "No Matching Videos" else "Folder is Empty",
                            color = c.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "Try a different search keyword" else "No videos found in this folder",
                            color = c.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                MediaFilesView(
                    videos = displayVideos,
                    viewType = currentViewType,
                    inSelectionMode = inSelectionMode,
                    selectedVideoIds = selectedVideoIds,
                    favorites = favorites,
                    onVideoClick = { video -> onVideoClick(video.uri.toString()) },
                    onVideoLongPress = { video ->
                        if (!inSelectionMode) {
                            videoActionTarget = video
                        } else {
                            selectedVideoIds = if (selectedVideoIds.contains(video.id)) {
                                selectedVideoIds - video.id
                            } else {
                                selectedVideoIds + video.id
                            }
                        }
                    },
                    onFavoriteToggle = { video -> toggleFavorite(video.uri.toString()) },
                    onSelectToggle = { video ->
                        selectedVideoIds = if (selectedVideoIds.contains(video.id)) {
                            selectedVideoIds - video.id
                        } else {
                            selectedVideoIds + video.id
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()),
                    headerContent = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${displayVideos.size} VIDEOS",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                "View: ${currentViewType.displayName}",
                                color = c.accentBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    contentPadding = PaddingValues(bottom = 80.dp)
                )
            }
        }

        // Floating Music Mini Bar at bottom of FolderDetailScreen (Video Section)
        val isMusicPlaying by remember { MusicService.isMusicPlaying }
        val nowPlayingTitle by remember { MusicService.nowPlayingTitle }
        val nowPlayingArtist by remember { MusicService.nowPlayingArtist }
        val nowPlayingArtUri by remember { MusicService.nowPlayingArtUri }

        AnimatedVisibility(
            visible = isMusicPlaying || nowPlayingTitle.isNotBlank(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = c.accentBlue.copy(0.4f), spotColor = c.accentBlue)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141724)) // 100% solid, fully opaque background
                    .border(1.2.dp, Brush.horizontalGradient(listOf(c.accentBlue, c.cardBorderHighlight)), RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E2136))
                            .border(0.8.dp, c.cardBorderHighlight, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (nowPlayingArtUri != null) {
                            AsyncImage(
                                model = nowPlayingArtUri,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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

                    Column(
                        modifier = Modifier.widthIn(max = 130.dp)
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
                            text = nowPlayingArtist.ifBlank { "Background Audio" },
                            color = c.accentBlue,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // BOTTOM SHEETS & DIALOGS
        // ═══════════════════════════════════════════════════════════════════════

        // Video Context Bottom Sheet
        videoActionTarget?.let { video ->
            ModalBottomSheet(
                onDismissRequest = { videoActionTarget = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = c.dropdownBg,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                    // Header with thumbnail and title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF282A36))
                                .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(10.dp))
                        ) {
                            coil3.compose.AsyncImage(
                                model = remember(video.uri, video.duration) {
                                    buildVideoThumbnailRequest(context, video.uri, video.duration)
                                },
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                video.title.substringBeforeLast("."),
                                color = c.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(formatSize(video.size), color = c.textSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = c.glassBorder)
                    Spacer(Modifier.height(8.dp))

                    // Action Items
                    VideoActionItem(Icons.Rounded.PlayArrow, "Play", c.accentBlue) {
                        onVideoClick(video.uri.toString())
                        videoActionTarget = null
                    }
                    VideoActionItem(
                        if (favorites.contains(video.uri.toString())) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (favorites.contains(video.uri.toString())) "Remove from Favorites" else "Add to Favorites",
                        if (favorites.contains(video.uri.toString())) Color(0xFFFF4D6D) else c.textPrimary
                    ) {
                        toggleFavorite(video.uri.toString())
                        videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.DriveFileRenameOutline, "Rename", c.textPrimary) {
                        renameVideoTarget = video
                        videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.Share, "Share", c.textPrimary) {
                        val intent = fileManager.buildShareIntent(video.uri)
                        context.startActivity(Intent.createChooser(intent, "Share video"))
                        videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.Info, "Video Info", c.textPrimary) {
                        videoInfoTarget = video
                        videoActionTarget = null
                    }
                    if (appPreferences.getVideoProgress(video.uri.toString()) > 0) {
                        VideoActionItem(Icons.Rounded.History, "Clear Watch Progress", c.textSecondary) {
                            appPreferences.clearVideoProgress(video.uri.toString())
                            refreshTrigger++
                            videoActionTarget = null
                        }
                    }
                    VideoActionItem(Icons.Rounded.CheckBox, "Select", c.textPrimary) {
                        selectedVideoIds = selectedVideoIds + video.id
                        videoActionTarget = null
                    }
                    VideoActionItem(Icons.Rounded.Delete, "Move to Recycle Bin", Color(0xFFE53935)) {
                        deleteVideosConfirm = listOf(video)
                        videoActionTarget = null
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
                title = { Text("Rename Video", color = c.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New file name", color = c.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accentBlue,
                            unfocusedBorderColor = c.glassBorder,
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary,
                            cursorColor = c.accentBlue
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val finalName = if (ext.isNotEmpty()) "$newName.$ext" else newName
                        coroutineScope.launch {
                            val result = fileManager.renameVideo(video.id, finalName)
                            when (result) {
                                is FileOperationResult.NeedsConfirmation -> {
                                    deleteRequestLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                                }
                                is FileOperationResult.Success -> {
                                    refreshTrigger++
                                    renameVideoTarget = null
                                }
                                is FileOperationResult.Failure -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Text("Rename", color = c.accentBlue, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameVideoTarget = null }) {
                        Text("Cancel", color = c.textSecondary)
                    }
                }
            )
        }

        // Delete Confirm Dialog (Recycle Bin)
        if (deleteVideosConfirm.isNotEmpty()) {
            val count = deleteVideosConfirm.size
            AlertDialog(
                onDismissRequest = { deleteVideosConfirm = emptyList() },
                containerColor = c.dropdownBg,
                title = {
                    Text(
                        if (count == 1) "Move to Recycle Bin?" else "Move $count Videos to Bin?",
                        color = c.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Videos will be moved to your device's Recycle Bin (Google Files / Gallery) where they can be restored within 30 days.",
                        color = c.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val uris = deleteVideosConfirm.map { it.uri }
                            val result = fileManager.deleteVideos(uris)
                            when (result) {
                                is FileOperationResult.NeedsConfirmation -> {
                                    deleteRequestLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                                }
                                is FileOperationResult.Success -> {
                                    selectedVideoIds = emptySet()
                                    deleteVideosConfirm = emptyList()
                                    refreshTrigger++
                                }
                                is FileOperationResult.Failure -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Text("Move to Bin", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteVideosConfirm = emptyList() }) {
                        Text("Cancel", color = c.textSecondary)
                    }
                }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Video Information", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    val allDetails = buildString {
                                        appendLine("Filename: ${info.filename}")
                                        appendLine("Resolution: ${info.resolution}")
                                        appendLine("Duration: ${formatTime(info.duration)}")
                                        appendLine("File Size: ${formatSize(info.size)}")
                                        appendLine("Format: ${info.mimeType}")
                                        appendLine("Date Added: ${fileManager.formatDate(info.dateAdded)}")
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
                        FolderDetailInfoRow("Filename", info.filename, c)
                        FolderDetailInfoRow("Resolution", info.resolution, c)
                        FolderDetailInfoRow("Duration", formatTime(info.duration), c)
                        FolderDetailInfoRow("File Size", formatSize(info.size), c)
                        FolderDetailInfoRow("Format", info.mimeType, c)
                        FolderDetailInfoRow("Date Added", fileManager.formatDate(info.dateAdded), c)
                        if (info.path.isNotEmpty()) {
                            FolderDetailInfoRow(
                                label = "Storage Path",
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
    }
}

@Composable
private fun FolderDetailInfoRow(
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
            Text(label, fontSize = 11.5.sp, color = c.textSecondary)
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
