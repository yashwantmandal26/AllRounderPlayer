package com.example.ymediaplayer.mycinema.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.mycinema.data.MyCinemaRepository
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.mycinema.data.TorrentSource
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OfflineCinemaMovie(
    val file: java.io.File,
    val title: String,
    val sizeFormatted: String,
    val durationFormatted: String
)

@Composable
fun MyCinemaScreen(
    repository: MyCinemaRepository = remember { MyCinemaRepository() },
    refreshTrigger: Int = 0,
    onDetailStateChanged: (Boolean) -> Unit = {},
    onPlayOfflineMovie: (String) -> Unit = {},
    onStreamSource: (TmdbItem, TorrentSource) -> Unit,
    onDownloadSource: (TmdbItem, TorrentSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

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

    LaunchedEffect(activeDetailItem) {
        onDetailStateChanged(activeDetailItem != null)
    }

    // Scan offline downloaded movies
    LaunchedEffect(refreshTrigger, cinemaDownloadsMap) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<OfflineCinemaMovie>()
            val downloadDir = com.example.ymediaplayer.mycinema.service.TorrentEngine.getDownloadDir(context)
            if (downloadDir.exists() && downloadDir.isDirectory) {
                downloadDir.walkTopDown().filter { file ->
                    file.isFile && file.length() > 0 &&
                    (file.extension.equals("mp4", true) ||
                     file.extension.equals("mkv", true) ||
                     file.extension.equals("webm", true) ||
                     file.extension.equals("avi", true) ||
                     file.extension.equals("mov", true) ||
                     file.extension.equals("ts", true))
                }.forEach { file ->
                    val sizeStr = com.example.ymediaplayer.mycinema.service.formatTorrentBytes(file.length())
                    val durationMs = try {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(file.absolutePath)
                            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } finally {
                            try { retriever.release() } catch (_: Exception) {}
                        }
                    } catch (_: Exception) { 0L }
                    val durStr = if (durationMs > 0) {
                        val totalSecs = durationMs / 1000
                        val hrs = totalSecs / 3600
                        val mins = (totalSecs % 3600) / 60
                        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                    } else ""
                    list.add(
                        OfflineCinemaMovie(
                            file = file,
                            title = file.nameWithoutExtension,
                            sizeFormatted = sizeStr,
                            durationFormatted = durStr
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
                            SectionHeader(title = "💾 DOWNLOADED & OFFLINE (${offlineMovies.size})")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(offlineMovies, key = { it.file.absolutePath }) { movie ->
                                    OfflineMovieCinemaCard(
                                        movie = movie,
                                        onClick = { onPlayOfflineMovie(movie.file.absolutePath) }
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
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(115.dp))
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
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(115.dp))
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
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(115.dp))
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
                                    MyCinemaCard(item = item, onClick = { onMovieCardClick(item) }, modifier = Modifier.width(115.dp))
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
                                        modifier = Modifier.width(115.dp)
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
                        coroutineScope.launch {
                            val sources = repository.getSources(targetItem)
                            itemSources = sources
                            isLoadingSources = false
                        }
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
                onDismiss = { activeItemForSources = null },
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
    }
}

@Composable
private fun SectionHeader(title: String) {
    val c = LocalAppColors.current
    Text(
        text = title,
        color = c.textSecondary,
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(vertical = 3.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📅 ${item.formattedReleaseDate}",
                        color = Color(0xFFFFD54F),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
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

@Composable
private fun OfflineMovieCinemaCard(
    movie: OfflineCinemaMovie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(135.dp)
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
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.cardBgElevated)
                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = movie.file,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Play icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Top OFFLINE badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00E676))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "💾 OFFLINE",
                    color = Color.Black,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom size & duration banner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(vertical = 3.5.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (movie.durationFormatted.isNotEmpty()) "${movie.sizeFormatted} • ${movie.durationFormatted}" else movie.sizeFormatted,
                    color = Color.White,
                    fontSize = 9.sp,
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
