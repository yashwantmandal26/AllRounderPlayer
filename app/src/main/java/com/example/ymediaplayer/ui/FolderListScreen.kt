package com.example.ymediaplayer.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.SortOrder
import com.example.ymediaplayer.data.VideoFolder
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.data.VideoRepository
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.LocalThemeController
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderListScreen(
    onFolderClick: (String, String) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val themeController = LocalThemeController.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val repository = remember { VideoRepository(context) }
    val appPreferences = remember { AppPreferences(context) }
    var allFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Bottom Nav
    var currentTab by remember { mutableStateOf("Video") }

    // Features state
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.valueOf(appPreferences.getSortOrder())) }
    var showSortMenu by remember { mutableStateOf(false) }
    var activeChip by remember { mutableStateOf<String?>(null) } // null means default view
    var isGridView by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(appPreferences.getFavorites()) }

    // Mesh gradient animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_offset"
    )

    LaunchedEffect(Unit) {
        isLoading = true
        allFolders = repository.getFoldersWithVideos()
        isLoading = false
    }

    // Process search, sort, and chips
    val displayFolders = remember(allFolders, searchQuery, sortOrder, activeChip, favorites) {
        var filteredFolders = allFolders

        // Filter by Chip
        if (activeChip == "Downloader") {
            filteredFolders = filteredFolders.filter { it.name.contains("Download", ignoreCase = true) }
        } else if (activeChip == "Favorites") {
            filteredFolders = filteredFolders.mapNotNull { folder ->
                val favVideos = folder.videos.filter { favorites.contains(it.uri.toString()) }
                if (favVideos.isNotEmpty()) folder.copy(videos = favVideos) else null
            }
        }

        // Search & Sort inside folders
        var processed = filteredFolders.mapNotNull { folder ->
            val filteredVideos = folder.videos.filter { 
                it.title.contains(searchQuery, ignoreCase = true) 
            }.let { list ->
                when (sortOrder) {
                    SortOrder.NAME -> list.sortedBy { it.title.lowercase() }
                    SortOrder.DATE -> list.sortedByDescending { it.id }
                    SortOrder.SIZE -> list.sortedByDescending { it.size }
                }
            }
            if (filteredVideos.isNotEmpty() || folder.name.contains(searchQuery, ignoreCase = true)) {
                folder.copy(videos = filteredVideos)
            } else null
        }

        // Add "Recently Added" synthetic folder at the top
        if (searchQuery.isEmpty() && activeChip != "Downloader") {
            val recentVideos = allFolders.flatMap { it.videos }.sortedByDescending { it.id }.take(30)
            if (recentVideos.isNotEmpty()) {
                val recentFolder = VideoFolder(id = "recently_added", name = "Recent Added", videos = recentVideos)
                processed = listOf(recentFolder) + processed
            }
        }
        
        processed
    }

    // Top 10 recent videos across all folders for the top carousel
    val recentVideosCarousel = remember(allFolders) {
        allFolders.flatMap { it.videos }.sortedByDescending { it.id }.take(10)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ─── Dynamic Animated Mesh Background ──────────────────────────────
        CanvasBg(gradientOffset)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.topBarScrim) // Dark glass look for TopBar
                        .border(0.5.dp, c.glassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search videos...", color = c.textHint) },
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
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(0.5.dp, c.glassBorder, RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Cancel",
                                color = c.accentBlue,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable { isSearching = false; searchQuery = "" }
                            )
                        } else {
                            Text(currentTab, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { themeController.toggle(systemDark) }) {
                                    Icon(
                                        if (c.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Toggle theme",
                                        tint = c.textPrimary
                                    )
                                }
                                IconButton(onClick = { Toast.makeText(context, "Searching for Cast devices...", Toast.LENGTH_SHORT).show() }) {
                                    Icon(Icons.Filled.Cast, contentDescription = "Cast", tint = c.textPrimary)
                                }
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = c.textPrimary)
                                }
                                IconButton(onClick = { Toast.makeText(context, "App Settings Opened", Toast.LENGTH_SHORT).show() }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = c.textPrimary)
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // ─── Floating Pill Navigation ──────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.4f))
                            .clip(CircleShape)
                            .background(c.navBarScrim)
                            .border(0.5.dp, c.glassBorder, CircleShape)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val navItems = listOf(
                            "Video" to Icons.Rounded.Movie,
                            "Music" to Icons.Rounded.MusicNote
                        )
                        navItems.forEach { (label, icon) ->
                            val selected = currentTab == label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) c.accentBlue else Color.Transparent)
                                    .bounceClick { currentTab = label }
                                    .padding(horizontal = if (selected) 20.dp else 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    tint = if (selected) c.onAccent else c.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                AnimatedVisibility(visible = selected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            label,
                                            color = c.onAccent,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            modifier = modifier
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accentBlue, strokeWidth = 3.dp)
                }
            } else if (currentTab != "Video") {
                // Music tab — render the real MusicScreen
                MusicScreen(modifier = Modifier.fillMaxSize().padding(paddingValues))
            } else if (activeChip == "Privacy") {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).clip(RoundedCornerShape(24.dp)).background(c.glassBg).border(1.dp, c.glassBorder, RoundedCornerShape(24.dp)).padding(32.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Privacy Vault Locked", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { activeChip = null }, colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue)) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // ─── Chips Row ──────────────────────────────────────────────
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item { ChipItem(icon = Icons.Rounded.PlayArrow, text = "All Videos", isSelected = activeChip == "All Videos") { activeChip = if (activeChip == "All Videos") null else "All Videos" } }
                            item { ChipItem(icon = Icons.Rounded.Favorite, text = "Favorites", isSelected = activeChip == "Favorites") { activeChip = if (activeChip == "Favorites") null else "Favorites" } }
                            item { ChipItem(icon = Icons.Rounded.Download, text = "Downloader", isSelected = activeChip == "Downloader") { activeChip = if (activeChip == "Downloader") null else "Downloader" } }
                            item { ChipItem(icon = Icons.Rounded.Lock, text = "Privacy", isSelected = activeChip == "Privacy") { activeChip = "Privacy" } }
                        }
                    }

                    if (activeChip == "All Videos") {
                        val allVideosFlat = allFolders.flatMap { it.videos }
                        item {
                            Text("ALL VIDEOS (${allVideosFlat.size})", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        itemsIndexed(allVideosFlat, key = { _, v -> "flat_${v.id}" }) { _, video ->
                            Box(modifier = Modifier.animateItem()) {
                                VideoListItem(video = video, onClick = { onVideoClick(video.uri.toString()) }, onMoreClick = {
                                    Toast.makeText(context, "Options for ${video.title}", Toast.LENGTH_SHORT).show()
                                })
                            }
                        }
                    } else {
                        // ─── Recent Videos Carousel ────────────────────────────────
                        if (recentVideosCarousel.isNotEmpty() && !isSearching && activeChip == null) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(bottom = 20.dp)
                                ) {
                                    items(recentVideosCarousel, key = { "carousel_${it.id}" }) { video ->
                                        Box(modifier = Modifier.animateItem()) {
                                            RecentVideoCard(video = video, onClick = { onVideoClick(video.uri.toString()) })
                                        }
                                    }
                                }
                            }
                        }

                        // ─── Folders Header ─────────────────────────────────────────
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${displayFolders.size} FOLDERS",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.textSecondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                            contentDescription = "Toggle Grid/List",
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            containerColor = c.dropdownBg
                                        ) {
                                            DropdownMenuItem(text = { Text("Sort by Name", color = c.textPrimary) }, onClick = { sortOrder = SortOrder.NAME; appPreferences.saveSortOrder(SortOrder.NAME.name); showSortMenu = false })
                                            DropdownMenuItem(text = { Text("Sort by Date", color = c.textPrimary) }, onClick = { sortOrder = SortOrder.DATE; appPreferences.saveSortOrder(SortOrder.DATE.name); showSortMenu = false })
                                            DropdownMenuItem(text = { Text("Sort by Size", color = c.textPrimary) }, onClick = { sortOrder = SortOrder.SIZE; appPreferences.saveSortOrder(SortOrder.SIZE.name); showSortMenu = false })
                                        }
                                    }
                                }
                            }
                        }

                        // ─── Folders List ───────────────────────────────────────────
                        if (isGridView) {
                            val chunkedFolders = displayFolders.chunked(2)
                            items(chunkedFolders, key = { chunk -> "chunk_${chunk.first().id}" }) { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (folder in chunk) {
                                        Box(modifier = Modifier.weight(1f).animateItem()) {
                                            FolderGridItem(
                                                folder = folder,
                                                onClick = { onFolderClick(folder.id, folder.name) },
                                                onMoreClick = {
                                                    Toast.makeText(context, "Options for ${folder.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                    if (chunk.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(displayFolders, key = { "folder_${it.id}" }) { folder ->
                                Box(modifier = Modifier.animateItem()) {
                                    FolderItem(
                                        folder = folder,
                                        onClick = { onFolderClick(folder.id, folder.name) },
                                        onMoreClick = {
                                            Toast.makeText(context, "Options for ${folder.name}", Toast.LENGTH_SHORT).show()
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

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CanvasBg(offset: Float) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.baseBackground)
            .blur(80.dp) // Massive blur for frosted glass mesh effect
    ) {
        // Deep purple and blue mesh gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(c.gradientBlob1.copy(alpha = 0.8f), Color.Transparent),
                        center = Offset(offset, offset * 1.5f),
                        radius = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(c.gradientBlob2.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(1000f - offset, 2000f - offset),
                        radius = 1500f
                    )
                )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UI COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChipItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isSelected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) c.accentBlue else c.glassBg)
            .border(0.5.dp, c.glassBorder, CircleShape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) c.onAccent else c.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (isSelected) c.onAccent else c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RecentVideoCard(video: VideoItem, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.glassBg)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(20.dp))
            .bounceClick(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .build(),
            contentDescription = "Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Duration badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(2.dp))
                Text(formatDuration(video.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun FolderItem(folder: VideoFolder, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.glassBg)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(24.dp))
            .bounceClick(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (folder.id == "recently_added") Icons.Rounded.Schedule else Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (folder.id == "recently_added") c.accentBlue else c.textSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = folder.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(text = folder.videos.size.toString(), fontSize = 14.sp, color = c.textSecondary)
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onMoreClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun FolderGridItem(folder: VideoFolder, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.glassBg)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(24.dp))
            .bounceClick(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(20.dp).offset(x = 8.dp, y = (-8).dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = c.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Icon(
            if (folder.id == "recently_added") Icons.Rounded.Schedule else Icons.Rounded.Folder,
            contentDescription = null,
            tint = if (folder.id == "recently_added") c.accentBlue else c.textSecondary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = folder.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(text = "${folder.videos.size} videos", fontSize = 13.sp, color = c.textSecondary)
    }
}

@Composable
fun VideoListItem(video: VideoItem, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    var isFavorite by remember { mutableStateOf(appPreferences.isFavorite(video.uri.toString())) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.glassBgNested) // slightly lighter than glassBg for nested look
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(16.dp))
            .bounceClick(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(video.uri).decoderFactory(VideoFrameDecoder.Factory()).build(),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(formatDuration(video.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                
                if (progress > 0 && video.duration > 0) {
                    val progressRatio = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(0.5f))) {
                        Box(modifier = Modifier.fillMaxWidth(progressRatio).height(3.dp).background(c.accentBlue))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title.substringBeforeLast("."), fontSize = 15.sp, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatSize(video.size), fontSize = 12.sp, color = c.textSecondary)
                }
            }
            IconButton(onClick = { 
                appPreferences.toggleFavorite(video.uri.toString())
                isFavorite = !isFavorite
                onMoreClick() 
            }) {
                Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (isFavorite) c.accentBlue else c.textSecondary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILS
// ═══════════════════════════════════════════════════════════════════════════════
private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val s = durationMs / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
