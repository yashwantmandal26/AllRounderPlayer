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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MyCinemaScreen(
    repository: MyCinemaRepository = remember { MyCinemaRepository() },
    onStreamSource: (TmdbItem, TorrentSource) -> Unit,
    onDownloadSource: (TmdbItem, TorrentSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }

    var recentlyReleased by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trendingMovies by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trendingSeries by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trendingIndia by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var upcomingMovies by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(true) }

    // Selected item for detail modal & source bottom sheet
    var activeDetailItem by remember { mutableStateOf<TmdbItem?>(null) }
    var activeItemForSources by remember { mutableStateOf<TmdbItem?>(null) }
    var itemSources by remember { mutableStateOf<List<TorrentSource>>(emptyList()) }
    var isLoadingSources by remember { mutableStateOf(false) }

    // Load initial feed concurrently
    LaunchedEffect(Unit) {
        isLoadingFeed = true
        coroutineScope.launch {
            try { recentlyReleased = repository.getRecentlyReleased() } catch (_: Exception) {}
        }
        coroutineScope.launch {
            try { trendingMovies = repository.getTrendingMovies() } catch (_: Exception) {}
        }
        coroutineScope.launch {
            try { trendingIndia = repository.getTrendingIndia() } catch (_: Exception) {}
        }
        coroutineScope.launch {
            try { trendingSeries = repository.getTrendingSeries() } catch (_: Exception) {}
        }
        coroutineScope.launch {
            try { upcomingMovies = repository.getUpcomingMovies() } catch (_: Exception) {}
            isLoadingFeed = false
        }
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
                // Default Feed (Ordered: Recently Released -> Trending -> Upcoming)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // 1. TOP: Recently Released (Movies & Web Series)
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

                    // 5. Upcoming Movies (with Release Date)
                    if (upcomingMovies.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(title = "📅 UPCOMING MOVIES")
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
