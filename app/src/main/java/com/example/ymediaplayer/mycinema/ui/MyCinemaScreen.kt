package com.example.ymediaplayer.mycinema.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.mycinema.data.MyCinemaRepository
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.mycinema.data.TorrentSource
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.ui.buildVideoThumbnailRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OfflineCinemaMovie(
    val file: java.io.File,
    val title: String,
    val sizeFormatted: String,
    val durationFormatted: String,
    val durationMs: Long = 0L,
    val posterUrl: String? = null
)

internal fun cleanMovieFileName(rawName: String): String {
    var name = rawName
    // Strip site/release group prefixes like "www.1TamilMV.solar - ", "[YTS.MX] ", etc.
    name = name.replace(Regex("(?i)^(www\\.[a-zA-Z0-9.-]+\\s*[-_]?\\s*|\\[[^\\]]+\\]\\s*)"), "")
    name = name.replace(Regex("(?i)\\[[^\\]]+\\]"), "")
    name = name.replace(".", " ").replace("_", " ").replace("-", " ")
    // Strip codec, resolution, and trailing quality tags
    name = name.replace(Regex("(?i)\\b(1080p|720p|4k|2160p|480p|bluray|blu-ray|web-?dl|webrip|hdr|hdr10|hevc|x264|x265|h264|h265|aac|ddp|ac3|dts|yify|yts|proper|repack|dvdrip|brrip|bdrip|remux|hindi|tamil|telugu|dual\\s*audio)\\b.*"), "")
    name = name.replace(Regex("\\b(19\\d\\d|20\\d\\d)\\b.*"), "")
    return name.trim()
}

@Composable
fun MyCinemaScreen(
    repository: MyCinemaRepository = remember { MyCinemaRepository() },
    refreshTrigger: Int = 0,
    onDetailStateChanged: (Boolean) -> Unit = {},
    onPlayOfflineMovie: (OfflineCinemaMovie, List<OfflineCinemaMovie>) -> Unit = { _, _ -> },
    onStreamSource: (TmdbItem, TorrentSource) -> Unit,
    onDownloadSource: (TmdbItem, TorrentSource) -> Unit,
    libraryOpenType: String? = null,
    onCloseLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val appPreferences = remember { AppPreferences(context) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Card width dynamically calculated so 2.45 cards fit horizontally:
    // This guarantees Card 1 and Card 2 are shown in full, and Card 3 is partially cut off on the right edge!
    val movieCardWidth = remember(screenWidth) {
        ((screenWidth - 32.dp - 24.dp) / 2.45f).coerceIn(122.dp, 150.dp)
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }

    var offlineMovies by remember { mutableStateOf<List<OfflineCinemaMovie>>(emptyList()) }
    val cinemaDownloadsMap by com.example.ymediaplayer.mycinema.service.TorrentEngine.downloads.collectAsState()

    var recentlyReleased by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trendingMovies by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trendingSeries by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trendingIndia by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }

    var upcomingFilter by remember { mutableStateOf("ALL") } // "ALL", "THEATRICAL", "OTT"
    var upcomingMovies by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var isLoadingUpcoming by remember { mutableStateOf(false) }

    var isLoadingFeed by remember { mutableStateOf(true) }

    // Selected item for detail modal & source bottom sheet
    var activeDetailItem by remember { mutableStateOf<TmdbItem?>(null) }
    var activeItemForSources by remember { mutableStateOf<TmdbItem?>(null) }
    var itemSources by remember { mutableStateOf<List<TorrentSource>>(emptyList()) }
    var isLoadingSources by remember { mutableStateOf(false) }
    var sourcesSearchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var localOfflineRefreshTrigger by remember { mutableIntStateOf(0) }
    var selectedOfflineMovieForOptions by remember { mutableStateOf<OfflineCinemaMovie?>(null) }
    var movieToDelete by remember { mutableStateOf<OfflineCinemaMovie?>(null) }
    var movieForDetails by remember { mutableStateOf<OfflineCinemaMovie?>(null) }

    LaunchedEffect(activeDetailItem, libraryOpenType) {
        onDetailStateChanged(activeDetailItem != null || libraryOpenType != null)
    }

    // Scan offline downloaded movies
    LaunchedEffect(refreshTrigger, cinemaDownloadsMap, localOfflineRefreshTrigger) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<OfflineCinemaMovie>()
            val downloadDir = com.example.ymediaplayer.mycinema.service.TorrentEngine.getDownloadDir(context)
            val inProgressPaths = cinemaDownloadsMap.values.filter {
                it.status != com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.FINISHED
            }.mapNotNull { it.savePath }.toSet()
            val inProgressIds = cinemaDownloadsMap.values.filter {
                it.status != com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.FINISHED
            }.map { it.id.lowercase() }.toSet()

            if (downloadDir.exists() && downloadDir.isDirectory) {
                downloadDir.walkTopDown().filter { file ->
                    file.isFile && file.length() > 0 &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    (file.extension.equals("mp4", true) ||
                     file.extension.equals("mkv", true) ||
                     file.extension.equals("webm", true) ||
                     file.extension.equals("avi", true) ||
                     file.extension.equals("mov", true) ||
                     file.extension.equals("ts", true)) &&
                    com.example.ymediaplayer.mycinema.service.TorrentEngine.getActiveTorrent(file.absolutePath) == null &&
                    !inProgressPaths.contains(file.absolutePath) &&
                    !inProgressIds.any { file.name.lowercase().contains(it) }
                }.forEach { file ->
                    val isExplicitlyCompleted = appPreferences.isCinemaDownloadCompleted(file.absolutePath) ||
                        cinemaDownloadsMap.values.any {
                            it.status == com.example.ymediaplayer.mycinema.service.TorrentDownloadState.Status.FINISHED &&
                            (it.savePath == file.absolutePath || it.title.equals(file.nameWithoutExtension, true))
                        }

                    val durationMs = try {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } finally {
                            try { retriever.release() } catch (_: Exception) {}
                        }
                    } catch (_: Exception) { 0L }

                    // Strict check: An offline movie must be fully completed and NOT an uncompleted sparse torrent file
                    if (!isExplicitlyCompleted || com.example.ymediaplayer.mycinema.service.TorrentEngine.isSparseUncompletedFile(file)) {
                        return@forEach
                    }

                    val sizeStr = com.example.ymediaplayer.mycinema.service.formatTorrentBytes(file.length())
                    val durStr = if (durationMs > 0) {
                        val totalSecs = durationMs / 1000
                        val hrs = totalSecs / 3600
                        val mins = (totalSecs % 3600) / 60
                        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                    } else ""

                    val cleanTitle = cleanMovieFileName(file.nameWithoutExtension)
                    var poster = appPreferences.getCinemaPoster(file.nameWithoutExtension)
                        ?: appPreferences.getCinemaPoster(file.name)
                        ?: appPreferences.getCinemaPoster(cleanTitle)

                    if (poster.isNullOrBlank() && cleanTitle.isNotBlank()) {
                        try {
                            val searchResults = repository.search(cleanTitle)
                            val match = searchResults.firstOrNull { !it.fullPosterUrl.isNullOrBlank() }
                            if (match != null && !match.fullPosterUrl.isNullOrBlank()) {
                                poster = match.fullPosterUrl
                                appPreferences.saveCinemaPoster(cleanTitle, poster)
                                appPreferences.saveCinemaPoster(file.nameWithoutExtension, poster)
                                appPreferences.saveCinemaPoster(file.name, poster)
                            }
                        } catch (_: Exception) {}
                    }

                    list.add(
                        OfflineCinemaMovie(
                            file = file,
                            title = if (cleanTitle.isNotBlank()) cleanTitle else file.nameWithoutExtension,
                            sizeFormatted = sizeStr,
                            durationFormatted = durStr,
                            durationMs = durationMs,
                            posterUrl = poster
                        )
                    )
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                offlineMovies = list.sortedByDescending { it.file.lastModified() }
            }
        }
    }

    // Load initial feed concurrently with structured concurrency
    LaunchedEffect(refreshTrigger) {
        isLoadingFeed = true
        val force = refreshTrigger > 0
        try {
            coroutineScope {
                val d1 = async { runCatching { repository.getRecentlyReleased(forceRefresh = force) }.getOrDefault(emptyList()) }
                val d2 = async { runCatching { repository.getTrendingMovies(forceRefresh = force) }.getOrDefault(emptyList()) }
                val d3 = async { runCatching { repository.getTrendingIndia(forceRefresh = force) }.getOrDefault(emptyList()) }
                val d4 = async { runCatching { repository.getTrendingSeries(forceRefresh = force) }.getOrDefault(emptyList()) }
                val d5 = async { runCatching { repository.getUpcomingMovies(filter = upcomingFilter, forceRefresh = force) }.getOrDefault(emptyList()) }

                recentlyReleased = d1.await()
                trendingMovies = d2.await()
                trendingIndia = d3.await()
                trendingSeries = d4.await()
                upcomingMovies = d5.await()
            }
        } finally {
            isLoadingFeed = false
        }
    }

    // Refetch upcoming when filter changes
    LaunchedEffect(upcomingFilter) {
        isLoadingUpcoming = true
        upcomingMovies = runCatching {
            repository.getUpcomingMovies(filter = upcomingFilter, forceRefresh = false)
        }.getOrDefault(emptyList())
        isLoadingUpcoming = false
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
        } else {
            isSearching = true
            delay(350)
            searchResults = repository.search(trimmed)
            isSearching = false
        }
    }

    // Detail Modal trigger
    val onMovieCardClick: (TmdbItem) -> Unit = { item ->
        activeDetailItem = item
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Bar Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.cardBgElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(c.accentBlue),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search movies, web series...",
                                    color = c.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(28.dp)
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

            // Main Content Area
            if (searchQuery.isNotBlank()) {
                // Search Results Grid
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = c.accentBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No movies or shows found for \"$searchQuery\"",
                            color = c.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { "${it.mediaType}_${it.id}" }) { item ->
                            MyCinemaCard(item = item, onClick = { onMovieCardClick(item) })
                        }
                    }
                }
            } else {
                // Default Feed (Ordered: Downloaded & Offline -> Recently Released -> Trending -> Upcoming)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // 0. TOP: Downloaded & Offline Movies
                    if (offlineMovies.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "OFFLINE (${offlineMovies.size})",
                                titleColor = Color(0xFF00E676)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(offlineMovies, key = { it.file.absolutePath }) { movie ->
                                    OfflineMovieCinemaCard(
                                        movie = movie,
                                        onClick = { onPlayOfflineMovie(movie, offlineMovies) },
                                        onLongClick = { selectedOfflineMovieForOptions = movie },
                                        modifier = Modifier.width(movieCardWidth)
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }

                    // 1. Recently Released (Movies & Web Series)
                    if (recentlyReleased.isNotEmpty()) {
                        item {
                            SectionHeader(title = "🆕 RECENTLY RELEASED (MOVIES & SERIES)")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(recentlyReleased, key = { "recent_${it.mediaType}_${it.id}" }) { item ->
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(movieCardWidth))
                                }
                            }
                        }
                    }

                    // 2. Trending Worldwide
                    if (trendingMovies.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(title = "🔥 TRENDING MOVIES")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trendingMovies, key = { "trend_m_${it.id}" }) { item ->
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(movieCardWidth))
                                }
                            }
                        }
                    }

                    // 3. Popular in India
                    if (trendingIndia.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(title = "🇮🇳 POPULAR IN INDIA")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trendingIndia, key = { "trend_in_${it.id}" }) { item ->
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(movieCardWidth))
                                }
                            }
                        }
                    }

                    // 4. Trending Series
                    if (trendingSeries.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(title = "📺 TOP WEB SERIES")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trendingSeries, key = { "trend_s_${it.id}" }) { item ->
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(movieCardWidth))
                                }
                            }
                        }
                    }

                    // 5. Upcoming Movies (with Release Date & OTT / Theatrical Filters)
                    item {
                        Spacer(Modifier.height(18.dp))
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            SectionHeader(title = "📅 UPCOMING MOVIES")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UpcomingFilterChip(
                                    label = "All",
                                    isSelected = upcomingFilter == "ALL",
                                    onClick = { upcomingFilter = "ALL" }
                                )
                                UpcomingFilterChip(
                                    label = "🎬 Theatrical",
                                    isSelected = upcomingFilter == "THEATRICAL",
                                    onClick = { upcomingFilter = "THEATRICAL" }
                                )
                                UpcomingFilterChip(
                                    label = "📺 OTT / Digital",
                                    isSelected = upcomingFilter == "OTT",
                                    onClick = { upcomingFilter = "OTT" }
                                )
                            }
                        }

                        if (isLoadingUpcoming) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = c.accentBlue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            }
                        } else if (upcomingMovies.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "No upcoming ${if (upcomingFilter == "OTT") "OTT" else if (upcomingFilter == "THEATRICAL") "theatrical" else ""} movies found after today",
                                    color = c.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(upcomingMovies, key = { "upcoming_${it.id}" }) { item ->
                                    MyCinemaCard(
                                        item = item,
                                        onClick = { onMovieCardClick(item) },
                                        showReleaseDate = true,
                                        modifier = Modifier.width(movieCardWidth)
                                    )
                                }
                            }
                        }
                    }

                    if (isLoadingFeed && recentlyReleased.isEmpty() && trendingMovies.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = c.accentBlue, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }

        // Movie Detail Modal Window
        AnimatedVisibility(
            visible = activeDetailItem != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            activeDetailItem?.let { item ->
                MyCinemaDetailModal(
                    item = item,
                    repository = repository,
                    onDismiss = { activeDetailItem = null },
                    onChooseSource = { targetItem ->
                        activeItemForSources = targetItem
                        itemSources = emptyList()
                        isLoadingSources = true
                        sourcesSearchJob?.cancel()
                        sourcesSearchJob = coroutineScope.launch {
                            repository.getSourcesFlow(targetItem).collect { progressiveSources ->
                                itemSources = progressiveSources
                            }
                            isLoadingSources = false
                        }
                    },
                    onOpenMovie = { prevMovie ->
                        activeDetailItem = prevMovie
                    }
                )
            }
        }

        // Cinema Library Modal (Favorites & Watchlist)
        AnimatedVisibility(
            visible = libraryOpenType != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            libraryOpenType?.let { tab ->
                MyCinemaLibraryModal(
                    initialTab = tab,
                    onDismiss = onCloseLibrary,
                    onItemClick = { item ->
                        activeDetailItem = item
                    }
                )
            }
        }

        // Torrent Sources Sheet
        activeItemForSources?.let { item ->
            TorrentSourcesBottomSheet(
                item = item,
                sources = itemSources,
                isLoading = isLoadingSources,
                onDismiss = { 
                    activeItemForSources = null
                    sourcesSearchJob?.cancel()
                },
                onStreamClick = { source ->
                    activeItemForSources = null
                    activeDetailItem = null
                    onStreamSource(item, source)
                },
                onDownloadClick = { source ->
                    onDownloadSource(item, source)
                }
            )
        }

        // Offline Movie Options Bottom Sheet (Long Press)
        selectedOfflineMovieForOptions?.let { movie ->
            OfflineMovieOptionsBottomSheet(
                movie = movie,
                onDismiss = { selectedOfflineMovieForOptions = null },
                onPlay = {
                    selectedOfflineMovieForOptions = null
                    onPlayOfflineMovie(movie, offlineMovies)
                },
                onShare = {
                    selectedOfflineMovieForOptions = null
                    try {
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            movie.file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            putExtra(Intent.EXTRA_TEXT, movie.title)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Movie File"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onShowInfo = {
                    selectedOfflineMovieForOptions = null
                    movieForDetails = movie
                },
                onDelete = {
                    selectedOfflineMovieForOptions = null
                    movieToDelete = movie
                }
            )
        }

        // Delete Confirmation Dialog
        movieToDelete?.let { movie ->
            AlertDialog(
                onDismissRequest = { movieToDelete = null },
                title = {
                    Text("Delete Movie?", fontWeight = FontWeight.Bold, color = c.textPrimary)
                },
                text = {
                    Text(
                        "Are you sure you want to permanently delete \"${movie.title}\"?\nThis will free up ${movie.sizeFormatted} of storage.",
                        color = c.textSecondary,
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = movie
                            movieToDelete = null
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val hash = com.example.ymediaplayer.mycinema.service.TorrentEngine.getActiveTorrent(target.file.absolutePath)
                                    if (hash != null) {
                                        com.example.ymediaplayer.mycinema.service.TorrentEngine.cancel(hash)
                                    }
                                    if (target.file.exists()) {
                                        target.file.delete()
                                    }
                                    try {
                                        appPreferences.removeCinemaDownloadCompleted(target.file.absolutePath)
                                    } catch (_: Exception) {}
                                    val parent = target.file.parentFile
                                    val baseDownloadDir = com.example.ymediaplayer.mycinema.service.TorrentEngine.getDownloadDir(context)
                                    if (parent != null && parent != baseDownloadDir && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                                        parent.delete()
                                    }
                                } catch (_: Exception) {}
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    localOfflineRefreshTrigger++
                                    Toast.makeText(context, "Deleted: ${target.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { movieToDelete = null }) {
                        Text("Cancel", color = c.textSecondary)
                    }
                },
                containerColor = c.cardBgElevated,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Movie Details Dialog
        movieForDetails?.let { movie ->
            AlertDialog(
                onDismissRequest = { movieForDetails = null },
                title = {
                    Text("Movie Details", fontWeight = FontWeight.Bold, color = c.textPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflineDetailRow("Title", movie.title)
                        OfflineDetailRow("File Name", movie.file.name)
                        OfflineDetailRow("File Size", movie.sizeFormatted)
                        if (movie.durationFormatted.isNotEmpty()) {
                            OfflineDetailRow("Duration", movie.durationFormatted)
                        }
                        OfflineDetailRow("Format", movie.file.extension.uppercase())
                        OfflineDetailRow("Path", movie.file.absolutePath)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { movieForDetails = null }) {
                        Text("Close", color = c.accentBlue, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = c.cardBgElevated,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    titleColor: Color? = null
) {
    val c = LocalAppColors.current
    Text(
        text = title,
        color = titleColor ?: c.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
    )
}

@Composable
private fun MyCinemaCard(
    item: TmdbItem,
    onClick: () -> Unit,
    showReleaseDate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.cardBgElevated)
                .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.fullPosterUrl,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top Badges (Type & Rating)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge: Web Series or Movie
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (item.isSeries) Color(0xFF6A1B9A).copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.75f)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (item.isSeries) "📺 SERIES" else "🎬 MOVIE",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rating badge
                if (item.voteAverage > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE50914).copy(alpha = 0.9f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ ${item.ratingFormatted}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Release Date Banner (for upcoming movies)
            if (showReleaseDate && item.formattedReleaseDate.isNotEmpty()) {
                val isOtt = item.ottPlatformHint != null && item.ottPlatformHint != "Theatres"
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(if (isOtt) Color(0xFF0277BD).copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.85f))
                        .padding(vertical = 3.5.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOtt) "📺 ${item.ottPlatformHint} • ${item.formattedReleaseDate}" else "🎬 Theatres • ${item.formattedReleaseDate}",
                        color = if (isOtt) Color(0xFFE1F5FE) else Color(0xFFFFD54F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.displayName,
            color = c.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.displayYear.isNotEmpty()) {
                Text(
                    text = item.displayYear,
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (item.isSeries) {
                Text(
                    text = "• Web Series",
                    color = Color(0xFFCE93D8),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineMovieCinemaCard(
    movie: OfflineCinemaMovie,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier.width(128.dp)
) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.cardBgElevated)
                .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
        ) {
            val thumbModel = remember(movie.file.absolutePath, movie.posterUrl) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    movie.posterUrl
                } else {
                    buildVideoThumbnailRequest(context, android.net.Uri.fromFile(movie.file), movie.durationMs)
                }
            }

            AsyncImage(
                model = thumbModel,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bottom size & duration banner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (movie.durationFormatted.isNotEmpty()) "${movie.sizeFormatted} • ${movie.durationFormatted}" else movie.sizeFormatted,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = movie.title,
            color = c.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Offline Movie",
            color = Color(0xFF00E676).copy(alpha = 0.85f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UpcomingFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) c.accentBlue.copy(alpha = 0.22f) else c.cardBgElevated,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, c.accentBlue) else androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder)
    ) {
        Text(
            text = label,
            color = if (isSelected) c.accentBlue else c.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineMovieOptionsBottomSheet(
    movie: OfflineCinemaMovie,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalAppColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.dropdownBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header: Poster + Title + Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.cardBgElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(10.dp))
                ) {
                    val thumb = movie.posterUrl ?: movie.file
                    AsyncImage(
                        model = thumb,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        color = c.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (movie.durationFormatted.isNotEmpty()) "${movie.sizeFormatted} • ${movie.durationFormatted}" else movie.sizeFormatted,
                        color = Color(0xFF00E676),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = movie.file.name,
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = c.glassBorder, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))

            // Action: Play
            OfflineActionRow(
                icon = Icons.Rounded.PlayArrow,
                title = "Play Movie",
                subtitle = "Watch in video player",
                iconColor = c.accentBlue,
                onClick = onPlay
            )

            // Action: Share
            OfflineActionRow(
                icon = Icons.Rounded.Share,
                title = "Share Movie File",
                subtitle = "Send via WhatsApp, Bluetooth, Nearby Share",
                iconColor = Color(0xFF29B6F6),
                onClick = onShare
            )

            // Action: File Details
            OfflineActionRow(
                icon = Icons.Rounded.Info,
                title = "File Details",
                subtitle = "Size, format, storage location",
                iconColor = Color(0xFFFFB74D),
                onClick = onShowInfo
            )

            // Action: Delete
            OfflineActionRow(
                icon = Icons.Rounded.Delete,
                title = "Delete File",
                subtitle = "Remove from device and free up ${movie.sizeFormatted}",
                iconColor = Color(0xFFFF5252),
                titleColor = Color(0xFFFF5252),
                onClick = onDelete
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OfflineActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    titleColor: Color? = null,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor ?: c.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = c.textSecondary,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun OfflineDetailRow(label: String, value: String) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = c.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
