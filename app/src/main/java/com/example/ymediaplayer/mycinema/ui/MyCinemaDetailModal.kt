package com.example.ymediaplayer.mycinema.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Build
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.R
import com.example.ymediaplayer.mycinema.data.BoxOfficeVerdict
import com.example.ymediaplayer.mycinema.data.CinemaExtendedInfo
import com.example.ymediaplayer.mycinema.data.MyCinemaRepository
import com.example.ymediaplayer.mycinema.data.TmdbCastMember
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.mycinema.data.TmdbMediaImage
import com.example.ymediaplayer.mycinema.data.TmdbWatchProviderItem
import com.example.ymediaplayer.mycinema.data.TmdbSeasonInfo
import com.example.ymediaplayer.mycinema.data.TmdbEpisodeItem
import com.example.ymediaplayer.mycinema.data.DownloadSite
import com.example.ymediaplayer.mycinema.data.DownloadSitesManager
import com.example.ymediaplayer.mycinema.data.currentCinemaDateIso
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun MyCinemaDetailModal(
    item: TmdbItem,
    repository: MyCinemaRepository,
    onDismiss: () -> Unit,
    onChooseSource: (TmdbItem) -> Unit,
    onOpenMovie: (TmdbItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val appPreferences = remember(context) { com.example.ymediaplayer.data.AppPreferences(context) }
    val itemKey = remember(item.id, item.mediaType) { "${item.mediaType}_${item.id}" }
    var currentDate by remember { mutableStateOf(currentCinemaDateIso()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentDate = currentCinemaDateIso()
        }
    }

    var isOverviewExpanded by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(appPreferences.isCinemaWatchlisted(itemKey)) }
    var isFavorite by remember { mutableStateOf(appPreferences.isCinemaFavorite(itemKey)) }
    var youtubeTrailerKey by remember { mutableStateOf<String?>(null) }
    var extendedInfo by remember(item.id) { mutableStateOf<CinemaExtendedInfo?>(null) }
    var isLoadingExtended by remember(item.id) { mutableStateOf(true) }

    val isUnreleased = remember(item, extendedInfo, currentDate) {
        val raw = item.releaseDate ?: item.firstAirDate
        val status = extendedInfo?.status?.lowercase()?.trim()
        val releaseDate = raw?.take(10).orEmpty()
        when {
            releaseDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> releaseDate > currentDate
            status != null && status in listOf("post production", "in production", "planned", "rumored") -> true
            else -> false
        }
    }

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(item.id) {
        youtubeTrailerKey = repository.getTrailerYoutubeKey(item.isSeries, item.id)
    }

    LaunchedEffect(item.id) {
        isLoadingExtended = true
        extendedInfo = repository.getCinemaExtendedInfo(item)
        isLoadingExtended = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        // High-res backdrop image covering top portion
        val backdropUrl = item.fullBackdropUrl ?: item.fullPosterUrl
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
            )
        }

        // Deep cinematic dark gradient overlays (vertical and bottom fade)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f),
                        0.30f to Color.Black.copy(alpha = 0.45f),
                        0.55f to Color(0xFF0B0E14).copy(alpha = 0.85f),
                        0.75f to Color(0xFF0B0E14),
                        1.0f to Color(0xFF0B0E14)
                    )
                )
        )

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Top Nav Row: Back button (Left) and Share button (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share movie/series (icon only)
                IconButton(
                    onClick = {
                        val shareType = if (item.isSeries) "tv" else "movie"
                        val shareUrl = "https://www.themoviedb.org/$shareType/${item.id}"
                        val shareText = "Watch \"${item.displayName}\" on AllRounder Player:\n$shareUrl"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, item.displayName)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ${item.displayName}"))
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Space down to position content nicely over the lower backdrop
            Spacer(Modifier.height(180.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Title
                Text(
                    text = item.displayName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 34.sp
                )

                Spacer(Modifier.height(10.dp))

                // Badges Row: Rating, Year, Type
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // TMDB Rating Pill
                    if (item.voteAverage > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⭐", fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "TMDB ${item.ratingFormatted}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Release Year / Date Pill
                    if (item.displayYear.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = item.displayYear,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Series / Movie Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.isSeries) Color(0xFF6A1B9A).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (item.isSeries) Color(0xFFAB47BC).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            text = if (item.isSeries) "📺 SERIES" else "🎬 MOVIE",
                            color = if (item.isSeries) Color(0xFFE1BEE7) else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Overview Synopsis + Read More
                val overviewText = item.overview?.trim()
                if (!overviewText.isNullOrEmpty()) {
                    Column {
                        Text(
                            text = overviewText,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (overviewText.length > 140) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isOverviewExpanded) "SHOW LESS" else "READ MORE",
                                color = Color(0xFFE50914), // Red link
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clickable { isOverviewExpanded = !isOverviewExpanded }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // Actual Release Date below About Movie
                val releaseDateFormatted = item.formattedReleaseDate.ifBlank { item.displayYear }
                if (releaseDateFormatted.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Event,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (item.isSeries) "First Aired: " else "Release Date: ",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = releaseDateFormatted,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Primary Action: If unreleased -> Show "Coming on {Date}" Button (NO Choose Source); If released -> Show CHOOSE SOURCE Button
                if (isUnreleased) {
                    val comingDateText = if (releaseDateFormatted.isNotBlank() && releaseDateFormatted != "Unknown") {
                        "Coming on $releaseDateFormatted"
                    } else {
                        "Coming Soon"
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF191F2C),
                        border = BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Event,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = comingDateText,
                                color = Color(0xFF00E5FF),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Watch Official Trailer Button
                        Button(
                            onClick = {
                                openOfficialTrailer(context, youtubeTrailerKey, item.displayName)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF0000),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("TRAILER", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Remind Me / Add to Watchlist Button
                        OutlinedButton(
                            onClick = {
                                isBookmarked = appPreferences.toggleCinemaWatchlist(item)
                                Toast.makeText(
                                    context,
                                    if (isBookmarked) "Added to Watchlist (Reminder set)" else "Removed from Watchlist",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isBookmarked) Color(0xFFFFC107) else Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isBookmarked) Color(0xFFFFC107).copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (isBookmarked) Color(0xFFFFC107) else Color.White
                            ),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBookmarked) "SAVED" else "REMIND ME", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Released Movie: CHOOSE SOURCE
                    Button(
                        onClick = { onChooseSource(item.copy(logoPath = extendedInfo?.logoPath ?: item.logoPath)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914), // Bold Netflix / Cinema Red
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Choose Source",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CHOOSE SOURCE",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // DIRECT DOWNLOAD WEBSITES (HDHub4u & KatMovieHD) - Directly below Main Stream/Download Button (Only for Released Movies)
                    Spacer(Modifier.height(14.dp))
                    DirectDownloadSitesCard(
                        item = item,
                        context = context
                    )
                }

                // BROWSE THE SHOW (Seasons & Episodes System) - Immediately visible for Series
                if (item.isSeries) {
                    Spacer(Modifier.height(18.dp))
                    BrowseTheShowSection(
                        item = item,
                        seasons = extendedInfo?.seasons ?: emptyList(),
                        repository = repository,
                        onChooseEpisodeSource = { ep ->
                            val epCode = String.format(java.util.Locale.US, "S%02dE%02d", ep.seasonNumber, ep.episodeNumber)
                            val epQuery = "${item.displayName} $epCode"
                            onChooseSource(
                                item.copy(
                                    title = epQuery,
                                    name = epQuery,
                                    overview = ep.overview ?: item.overview,
                                    logoPath = extendedInfo?.logoPath ?: item.logoPath
                                )
                            )
                        }
                    )
                }

                // TOP CAST SECTION (Just after the movie/series introduction & episodes)
                if (extendedInfo?.topCast?.isNotEmpty() == true) {
                    Spacer(Modifier.height(18.dp))
                    TopCastSection(cast = extendedInfo!!.topCast)
                }

                // 1. WHERE TO WATCH (OFFICIAL AVAILABILITY)
                if (extendedInfo != null) {
                    Spacer(Modifier.height(16.dp))
                    OfficialAvailabilityCard(
                        extendedInfo = extendedInfo!!,
                        isUnreleased = isUnreleased,
                        releaseDateFormatted = releaseDateFormatted,
                        movieTitle = item.displayName,
                        movieYear = item.displayYear
                    )
                }

                // 2. BOX OFFICE & OFFICIAL BUDGET
                if (extendedInfo != null && (extendedInfo!!.budget > 0 || extendedInfo!!.revenue > 0 || !isUnreleased)) {
                    Spacer(Modifier.height(14.dp))
                    BoxOfficeBudgetCard(
                        extendedInfo = extendedInfo!!,
                        isUnreleased = isUnreleased
                    )
                }

                // 3. PREQUELS & SEQUELS (Franchise / Collection)
                if (extendedInfo?.previousParts?.isNotEmpty() == true) {
                    Spacer(Modifier.height(20.dp))
                    PreviousPartsSection(
                        collectionName = extendedInfo?.collectionName,
                        parts = extendedInfo!!.previousParts,
                        onOpen = onOpenMovie
                    )
                }

                Spacer(Modifier.height(22.dp))

                // MY LIBRARY Section
                Text(
                    text = "MY LIBRARY",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Watchlist / Bookmark
                    Surface(
                        onClick = {
                            isBookmarked = appPreferences.toggleCinemaWatchlist(item)
                            Toast.makeText(
                                context,
                                if (isBookmarked) "Added to Watchlist" else "Removed from Watchlist",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBookmarked) Color(0xFFFFC107).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (isBookmarked) Color(0xFFFFC107) else Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isBookmarked) Color(0xFFFFC107) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isBookmarked) "Watchlisted" else "Watchlist",
                                color = if (isBookmarked) Color(0xFFFFC107) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Favorite / Heart
                    Surface(
                        onClick = {
                            isFavorite = appPreferences.toggleCinemaFavorite(item)
                            Toast.makeText(
                                context,
                                if (isFavorite) "Added to Favorites" else "Removed from Favorites",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFavorite) Color(0xFFE50914).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (isFavorite) Color(0xFFE50914) else Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) Color(0xFFE50914) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isFavorite) "Favorited" else "Favorite",
                                color = if (isFavorite) Color(0xFFE50914) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // RESEARCH & REVIEWS Section
                Text(
                    text = "RESEARCH & REVIEWS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(10.dp))

                // Flow of external app / review buttons (wraps cleanly to next line, no sliding)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. TRAILER (Official YouTube app or browser with real YouTube logo)
                    ReviewExternalPill(
                        label = "TRAILER",
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 15.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFF0000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "YouTube",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        },
                        onClick = {
                            if (!youtubeTrailerKey.isNullOrBlank()) {
                                launchYouTubeOrWeb(context, youtubeTrailerKey!!)
                            } else {
                                val q = URLEncoder.encode("${item.displayName} official trailer", "UTF-8")
                                launchAppOrWeb(context, "https://www.youtube.com/results?search_query=$q")
                            }
                        }
                    )

                    // 2. MOCTALE (Official Moctale App or Web Movie Reviews)
                    ReviewExternalPill(
                        label = "MOCTALE",
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF673AB7)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = "https://play-lh.googleusercontent.com/3FlgjFk6If6cGdYc19cJoZqgFdWG9JOCeLn_NNPQ8ypxg9QYzNi7dlX0dv1Z-UyUFNDU_A1ZiHqhlXNKPxFpfA=s128",
                                    contentDescription = "Moctale",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        },
                        onClick = {
                            val pm = context.packageManager
                            val appIntent = pm.getLaunchIntentForPackage("moctale.android.app")
                            if (appIntent != null) {
                                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                appIntent.putExtra("query", item.displayName)
                                appIntent.putExtra(Intent.EXTRA_TEXT, item.displayName)
                                try {
                                    context.startActivity(appIntent)
                                } catch (_: Exception) {
                                    val q = URLEncoder.encode("${item.displayName} moctale reviews", "UTF-8")
                                    launchBrowserUrl(context, "https://www.google.com/search?q=$q")
                                }
                            } else {
                                // Moctale app is not installed -> Open reviews directly in browser
                                val q = URLEncoder.encode("${item.displayName} moctale reviews", "UTF-8")
                                launchBrowserUrl(context, "https://www.google.com/search?q=$q")
                            }
                        }
                    )

                    // 3. GOOGLE (Official 4-Color Google 'G' Logo)
                    ReviewExternalPill(
                        label = "GOOGLE",
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google",
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            val q = URLEncoder.encode("${item.displayName} movie reviews", "UTF-8")
                            launchBrowserUrl(context, "https://www.google.com/search?q=$q")
                        }
                    )

                    // 4. LETTERBOXD (Authentic Letterboxd 3-Dots Logo)
                    ReviewExternalPill(
                        label = "LETTERBOXD",
                        icon = {
                            Row(
                                modifier = Modifier.height(14.dp),
                                horizontalArrangement = Arrangement.spacedBy((-3).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E054))) // Green
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF8000))) // Orange
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF40BCF4))) // Blue
                            }
                        },
                        onClick = {
                            val q = URLEncoder.encode(item.displayName, "UTF-8")
                            launchAppOrWeb(context, "https://letterboxd.com/search/$q/")
                        }
                    )

                    // 5. IMDB (Authentic Yellow IMDb Badge)
                    ReviewExternalPill(
                        label = "IMDb",
                        icon = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFF5C518))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "IMDb",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.5.sp,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        },
                        onClick = {
                            val q = URLEncoder.encode(item.displayName, "UTF-8")
                            launchAppOrWeb(context, "https://www.imdb.com/find/?q=$q")
                        }
                    )
                }

                // POSTERS & PHOTOS GALLERY (After Research & Reviews)
                if (extendedInfo?.galleryImages?.isNotEmpty() == true) {
                    Spacer(Modifier.height(24.dp))
                    PostersGallerySection(images = extendedInfo!!.galleryImages)
                }

                Spacer(Modifier.height(56.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun LibraryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color = Color(0xFF00C0FF),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = if (isActive) 0.2f else 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier.size(46.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isActive) activeColor else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ReviewExternalPill(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun OfficialAvailabilityCard(
    extendedInfo: CinemaExtendedInfo,
    isUnreleased: Boolean,
    releaseDateFormatted: String,
    movieTitle: String,
    movieYear: String
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141722),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (extendedInfo.isDirectOtt) Icons.Rounded.Tv else Icons.Rounded.Theaters,
                    contentDescription = null,
                    tint = if (extendedInfo.isDirectOtt) Color(0xFF00E5FF) else Color(0xFFFFB300),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "WHERE TO WATCH (OFFICIAL)",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            if (extendedInfo.watchProviders.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    extendedInfo.watchProviders.take(8).forEach { provider ->
                        Surface(
                            onClick = {
                                val browseUrl = getOttProviderBrowseUrl(
                                    providerName = provider.providerName,
                                    title = movieTitle,
                                    year = movieYear,
                                    tmdbWatchLink = extendedInfo.tmdbWatchLink
                                )
                                launchBrowserUrl(context, browseUrl)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.20f)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                provider.fullLogoUrl?.let { logoUrl ->
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = provider.providerName,
                                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = provider.providerName,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Watch on ${provider.providerName}",
                                    tint = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            } else if (extendedInfo.isDirectOtt) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val platform = extendedInfo.officialPlatformName ?: "OTT"
                        val browseUrl = getOttProviderBrowseUrl(
                            providerName = platform,
                            title = movieTitle,
                            year = movieYear,
                            tmdbWatchLink = extendedInfo.tmdbWatchLink
                        )
                        launchBrowserUrl(context, browseUrl)
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0288D1).copy(alpha = 0.2f))
                            .border(0.8.dp, Color(0xFF0288D1).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📺 DIRECT OTT PREMIERE",
                                color = Color(0xFF81D4FA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = Color(0xFF81D4FA),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    if (releaseDateFormatted.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Streaming from $releaseDateFormatted",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFB300).copy(alpha = 0.18f))
                            .border(0.8.dp, Color(0xFFFFB300).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🎬 THEATRICAL RELEASE",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (releaseDateFormatted.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isUnreleased) "In Theatres $releaseDateFormatted" else "Released in Cinemas Worldwide",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectDownloadSitesCard(
    item: TmdbItem,
    context: Context
) {
    val coroutineScope = rememberCoroutineScope()
    var resolvingSiteId by remember { mutableStateOf<String?>(null) }
    var downloadSiteNotice by remember { mutableStateOf<String?>(null) }

    fun handleSiteClick(site: DownloadSite) {
        if (resolvingSiteId != null) return
        resolvingSiteId = site.id
        downloadSiteNotice = null
        coroutineScope.launch {
            try {
                val result = DownloadSitesManager.resolveDownloadSiteUrl(
                    context = context,
                    siteId = site.id,
                    title = item.displayName,
                    year = item.displayYear
                )
                if (result.notice != null) {
                    downloadSiteNotice = result.notice
                }
                DownloadSitesManager.openInBrowser(context, result.searchUrl)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open ${site.name}: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                resolvingSiteId = null
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141722),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "DOWNLOAD FROM WEBSITES",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "HDHub4u & KatMovieHD",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DownloadSitesManager.SITES.forEach { site ->
                    DownloadSiteButton(
                        site = site,
                        isResolving = resolvingSiteId == site.id,
                        enabled = resolvingSiteId == null,
                        modifier = Modifier.weight(1f),
                        onClick = { handleSiteClick(site) }
                    )
                }
            }

            if (!downloadSiteNotice.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = downloadSiteNotice!!,
                    color = Color(0xFFFFB300),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BoxOfficeBudgetCard(
    extendedInfo: CinemaExtendedInfo,
    isUnreleased: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141722),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AttachMoney,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "BOX OFFICE & BUDGET",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Verdict Badge
                val verdict = extendedInfo.boxOfficeVerdict
                if (verdict != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(verdict.colorHex).copy(alpha = 0.18f))
                            .border(0.8.dp, Color(verdict.colorHex).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = verdict.label,
                            color = Color(verdict.colorHex),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Official Budget Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "TOTAL BUDGET",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = extendedInfo.budgetFormatted,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Box Office Gross Collection Box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isUnreleased) "BOX OFFICE" else "WORLDWIDE GROSS",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isUnreleased) "Pending Release" else extendedInfo.revenueFormatted,
                        color = if (isUnreleased) Color(0xFFB0BEC5) else Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviousPartsSection(
    collectionName: String?,
    parts: List<TmdbItem>,
    onOpen: (TmdbItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "PREQUELS & SEQUELS",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            collectionName?.let { cName ->
                Text(
                    text = cName,
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(parts, key = { "prev_${it.id}" }) { prevMovie ->
                PreviousPartCard(
                    item = prevMovie,
                    onOpen = { onOpen(prevMovie) }
                )
            }
        }
    }
}

@Composable
private fun PreviousPartCard(
    item: TmdbItem,
    onOpen: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161922),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.fullPosterUrl,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Rating badge
                if (item.voteAverage > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ ${item.ratingFormatted}",
                            color = Color(0xFFFFD54F),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Release Year
                if (item.displayYear.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.displayYear,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = item.displayName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // OPEN Button
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE50914),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().height(30.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "OPEN",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

private fun openOfficialTrailer(context: Context, key: String?, title: String) {
    if (!key.isNullOrBlank()) {
        launchYouTubeOrWeb(context, key)
    } else {
        val q = URLEncoder.encode("$title official trailer", "UTF-8")
        launchAppOrWeb(context, "https://www.youtube.com/results?search_query=$q")
    }
}

private fun launchYouTubeOrWeb(context: Context, key: String) {
    try {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(appIntent)
    } catch (_: Exception) {
        launchAppOrWeb(context, "https://www.youtube.com/watch?v=$key")
    }
}

/**
 * Opens url in its dedicated Android app (e.g. YouTube, Letterboxd, IMDb, Moctale) if installed,
 * or gracefully falls back to the default web browser.
 */
private fun launchAppOrWeb(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens url directly in the web browser.
 */
private fun launchBrowserUrl(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Resolves the official watch / search URL for an OTT streaming platform.
 */
private fun getOttProviderBrowseUrl(
    providerName: String,
    title: String,
    year: String = "",
    tmdbWatchLink: String? = null
): String {
    val cleanTitle = title.trim()
    val cleanYear = year.trim().takeIf { it.matches(Regex("\\d{4}")) }
    val searchTitle = if (cleanYear != null) "$cleanTitle ($cleanYear)" else cleanTitle
    val encodedTitle = try {
        URLEncoder.encode(searchTitle, "UTF-8")
    } catch (_: Exception) {
        searchTitle.replace(" ", "+")
    }
    val lower = providerName.lowercase()

    return when {
        (lower.contains("hotstar") || lower.contains("disney")) && !tmdbWatchLink.isNullOrBlank() -> tmdbWatchLink
        lower.contains("netflix") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Netflix"
        lower.contains("prime") || lower.contains("amazon") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Prime+Video"
        lower.contains("hotstar") || lower.contains("disney") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Disney+Hotstar"
        lower.contains("jiocinema") || lower.contains("jio cinema") -> "https://www.google.com/search?q=watch+$encodedTitle+on+JioCinema"
        lower.contains("zee5") || lower.contains("zee") -> "https://www.google.com/search?q=watch+$encodedTitle+on+ZEE5"
        lower.contains("sonyliv") || lower.contains("sony liv") -> "https://www.google.com/search?q=watch+$encodedTitle+on+SonyLIV"
        lower.contains("apple") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Apple+TV"
        lower.contains("paramount") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Paramount+Plus"
        lower.contains("hulu") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Hulu"
        lower.contains("peacock") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Peacock"
        lower.contains("max") || lower.contains("hbo") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Max"
        lower.contains("youtube") || lower.contains("google play") -> "https://www.youtube.com/results?search_query=$encodedTitle+movie"
        lower.contains("crunchyroll") -> "https://www.google.com/search?q=watch+$encodedTitle+on+Crunchyroll"
        !tmdbWatchLink.isNullOrBlank() -> tmdbWatchLink
        else -> {
            val encodedProvider = try {
                URLEncoder.encode(providerName, "UTF-8")
            } catch (_: Exception) {
                providerName.replace(" ", "+")
            }
            "https://www.google.com/search?q=watch+$encodedTitle+on+$encodedProvider"
        }
    }
}

// ─── TOP CAST SECTION (With Direct Google Search on Click) ───────────────────
@Composable
private fun TopCastSection(
    cast: List<TmdbCastMember>
) {
    if (cast.isEmpty()) return
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "TOP CAST",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Tap to search Google",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.5.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(cast, key = { "cast_${it.id}" }) { member ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(76.dp)
                        .clickable {
                            try {
                                val query = URLEncoder.encode("${member.name} actor", "UTF-8")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), CircleShape)
                    ) {
                        if (!member.profilePath.isNullOrBlank()) {
                            AsyncImage(
                                model = member.fullProfileUrl,
                                contentDescription = member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = member.name,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (member.character.isNotBlank()) {
                        Text(
                            text = member.character,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── POSTERS & PHOTOS GALLERY (After Research & Reviews) ─────────────────────
@Composable
private fun PostersGallerySection(
    images: List<TmdbMediaImage>
) {
    if (images.isEmpty()) return

    var activeViewerIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "POSTERS & GALLERY",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            itemsIndexed(images, key = { _, img -> "img_${img.filePath}" }) { index, img ->
                val isBackdrop = img.isBackdrop
                Box(
                    modifier = Modifier
                        .size(
                            width = if (isBackdrop) 195.dp else 115.dp,
                            height = 135.dp
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141722))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                        .clickable {
                            activeViewerIndex = index
                        }
                ) {
                    AsyncImage(
                        model = img.fullImageUrl,
                        contentDescription = "Movie Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    activeViewerIndex?.let { index ->
        FullscreenImageGallery(
            images = images,
            initialIndex = index,
            onDismiss = { activeViewerIndex = null }
        )
    }
}

@Composable
private fun FullscreenImageGallery(
    images: List<TmdbMediaImage>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    var isCurrentZoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val currentImage = images.getOrNull(pagerState.currentPage)

            // Horizontal Pager for swiping between photos
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentZoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val img = images[page]
                ZoomableGalleryPage(
                    imageUrl = img.fullImageUrl,
                    isCurrentPage = pagerState.currentPage == page,
                    onZoomStateChanged = { zoomed ->
                        if (pagerState.currentPage == page) {
                            isCurrentZoomed = zoomed
                        }
                    }
                )
            }

            // Top Control Bar with Glass overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.40f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Close button & Image counter
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "${pagerState.currentPage + 1} / ${images.size}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Action buttons: Copy, Save, Share - sleek & non-overlapping
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Copy link button
                        IconButton(
                            onClick = {
                                if (currentImage != null) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Image URL", currentImage.fullImageUrl)
                                    cm?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Image link copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy link",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Save image to Gallery button
                        IconButton(
                            onClick = {
                                if (currentImage != null) {
                                    saveImageToGallery(context, coroutineScope, currentImage.fullImageUrl)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Save to Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Share image button
                        IconButton(
                            onClick = {
                                if (currentImage != null) {
                                    shareGalleryImage(context, coroutineScope, currentImage.fullImageUrl)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableGalleryPage(
    imageUrl: String,
    isCurrentPage: Boolean,
    onZoomStateChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            onZoomStateChanged(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.2f) {
                            offset = Offset.Zero
                            onZoomStateChanged(false)
                            1f
                        } else {
                            onZoomStateChanged(true)
                            2.5f
                        }
                    }
                )
            }
            .pointerInput(scale) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount > 1) {
                            // Two fingers: Pinch-to-zoom & pan
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 4.5f)
                            scale = newScale
                            onZoomStateChanged(newScale > 1.05f)

                            val maxOffsetX = (newScale - 1f) * 600f
                            val maxOffsetY = (newScale - 1f) * 900f
                            val newOffset = offset + panChange
                            offset = Offset(
                                newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.05f) {
                            // Single finger while zoomed in: pan around the photo
                            val panChange = event.calculatePan()
                            val maxOffsetX = (scale - 1f) * 600f
                            val maxOffsetY = (scale - 1f) * 900f
                            val newOffset = offset + panChange
                            offset = Offset(
                                newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                            event.changes.forEach { it.consume() }
                        }
                        // When scale <= 1.05f and single finger: DO NOT CONSUME. Allow HorizontalPager to swipe smoothly!
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Full Image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

private fun saveImageToGallery(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    imageUrl: String
) {
    scope.launch(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saving photo to gallery...", Toast.LENGTH_SHORT).show()
            }
            val conn = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val bytes = conn.inputStream.use { it.readBytes() }

            val fileName = "AllRounder_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AllRounderPlayer")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } else {
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "AllRounderPlayer")
                dir.mkdirs()
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Gallery (Pictures/AllRounderPlayer)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun shareGalleryImage(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    imageUrl: String
) {
    scope.launch(Dispatchers.IO) {
        try {
            val conn = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            val bytes = conn.inputStream.use { it.readBytes() }

            val cacheDir = File(context.cacheDir, "shared_images")
            cacheDir.mkdirs()
            val file = File(cacheDir, "shared_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, imageUrl)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, imageUrl)
                }
                context.startActivity(Intent.createChooser(textIntent, "Share Image Link"))
            }
        }
    }
}

// ─── BROWSE THE SHOW (SEASONS & EPISODES CATALOG) ───────────────────────────
@Composable
private fun BrowseTheShowSection(
    item: TmdbItem,
    seasons: List<TmdbSeasonInfo>,
    repository: MyCinemaRepository,
    onChooseEpisodeSource: (TmdbEpisodeItem) -> Unit
) {
    val displaySeasons = remember(seasons) {
        if (seasons.isEmpty()) {
            listOf(TmdbSeasonInfo(seasonNumber = 1, name = "Season 1", episodeCount = 0))
        } else {
            val regular = seasons.filter { it.seasonNumber > 0 }
            if (regular.isNotEmpty()) regular else seasons
        }
    }

    var selectedSeasonNumber by remember(displaySeasons) {
        mutableIntStateOf(displaySeasons.firstOrNull()?.seasonNumber ?: 1)
    }

    var episodes by remember { mutableStateOf<List<TmdbEpisodeItem>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }

    LaunchedEffect(item.id, selectedSeasonNumber) {
        isLoadingEpisodes = true
        episodes = repository.getSeasonEpisodes(item.id, selectedSeasonNumber)
        isLoadingEpisodes = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "BROWSE THE SHOW",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Seasons & Episode Guide",
                    color = Color.White,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            val currentSeason = displaySeasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
            if (currentSeason != null && currentSeason.episodeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Text(
                        text = "${currentSeason.episodeCount} Episodes",
                        color = Color(0xFF64B5F6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Season Selector Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(displaySeasons) { s ->
                val isSelected = s.seasonNumber == selectedSeasonNumber
                Surface(
                    onClick = { selectedSeasonNumber = s.seasonNumber },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFFFF6B00) else Color(0xFF141722),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFFF6B00) else Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.name,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                        if (s.episodeCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(${s.episodeCount})",
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Episode List Cards
        if (isLoadingEpisodes) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color(0xFFFF6B00),
                    modifier = Modifier.size(28.dp)
                )
            }
        } else if (episodes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF141722),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Episode details pending release or unavailable for this season.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                episodes.forEach { ep ->
                    EpisodeCardItem(
                        item = item,
                        episode = ep,
                        onChooseSource = { onChooseEpisodeSource(ep) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCardItem(
    item: TmdbItem,
    episode: TmdbEpisodeItem,
    onChooseSource: () -> Unit
) {
    val context = LocalContext.current
    val appPrefs = remember { com.example.ymediaplayer.data.AppPreferences(context) }
    val progressVersion = appPrefs.lastProgressUpdate.longValue
    val isWatched = remember(progressVersion, episode.seasonNumber, episode.episodeNumber) {
        appPrefs.isEpisodeCompleted(item.id, episode.seasonNumber, episode.episodeNumber)
    }
    val (progressMs, durationMs) = remember(progressVersion, episode.seasonNumber, episode.episodeNumber) {
        appPrefs.getEpisodeProgress(item.id, episode.seasonNumber, episode.episodeNumber)
    }
    val progressFraction = if (durationMs > 0L) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val stillUrl = episode.fullStillUrl ?: item.fullBackdropUrl ?: item.fullPosterUrl

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131520),
        border = BorderStroke(
            1.dp,
            if (isWatched) Color(0xFF00E676).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Still Image thumbnail with 16:9 ratio, badges & bottom progress bar
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2130))
                ) {
                    if (stillUrl != null) {
                        AsyncImage(
                            model = stillUrl,
                            contentDescription = episode.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (isWatched) 0.65f else 1f)
                        )
                    }

                    // S01 E01 Overlay Badge
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = episode.episodeCode,
                            color = Color(0xFFFF9E00),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Watched Status Checkmark Badge
                    if (isWatched) {
                        Surface(
                            shape = RoundedCornerShape(bottomStart = 6.dp),
                            color = Color(0xFF00E676),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "WATCHED",
                                    color = Color.Black,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // In-Progress Bar across thumbnail bottom
                    if (progressFraction > 0.05f && !isWatched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction)
                                    .background(Color(0xFFFF6B00))
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title, Air Date, Release Status, Watched Toggle
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = episode.name.ifBlank { "Episode ${episode.episodeNumber}" },
                            color = if (isWatched) Color.White.copy(alpha = 0.75f) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Interactive Watched Toggle Checkmark Button
                        IconButton(
                            onClick = {
                                val newWatched = appPrefs.toggleEpisodeCompleted(item.id, episode.seasonNumber, episode.episodeNumber)
                                Toast.makeText(
                                    context,
                                    if (newWatched) "Marked ${episode.episodeCode} as Watched" else "Marked ${episode.episodeCode} as Unwatched",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isWatched) "Mark as Unwatched" else "Mark as Watched",
                                tint = if (isWatched) Color(0xFF00E676) else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Release status pill
                        if (episode.released) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "RELEASED",
                                    color = Color(0xFF00E676),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFF9100).copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, Color(0xFFFF9100).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "UPCOMING",
                                    color = Color(0xFFFF9100),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                )
                            }
                        }

                        if (!episode.airDate.isNullOrBlank()) {
                            Text(
                                text = "Aired: ${episode.airDate}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }

                        if (progressFraction > 0.05f && !isWatched) {
                            Text(
                                text = "• ${(progressFraction * 100).toInt()}% watched",
                                color = Color(0xFFFF9E00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!episode.overview.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = episode.overview,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action: Choose Source button (or Coming Soon if unreleased)
            if (episode.released) {
                Button(
                    onClick = onChooseSource,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B00),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "CHOOSE SOURCE FOR ${episode.episodeCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Event,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (!episode.airDate.isNullOrBlank()) "Coming on ${episode.airDate}" else "Coming Soon",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
