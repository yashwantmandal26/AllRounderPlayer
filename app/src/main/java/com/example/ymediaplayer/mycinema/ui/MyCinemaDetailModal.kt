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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.mycinema.data.BoxOfficeVerdict
import com.example.ymediaplayer.mycinema.data.CinemaExtendedInfo
import com.example.ymediaplayer.mycinema.data.MyCinemaRepository
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.mycinema.data.TmdbWatchProviderItem
import com.example.ymediaplayer.theme.LocalAppColors
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

    var isOverviewExpanded by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(appPreferences.isCinemaWatchlisted(itemKey)) }
    var isFavorite by remember { mutableStateOf(appPreferences.isCinemaFavorite(itemKey)) }
    var youtubeTrailerKey by remember { mutableStateOf<String?>(null) }
    var extendedInfo by remember(item.id) { mutableStateOf<CinemaExtendedInfo?>(null) }
    var isLoadingExtended by remember(item.id) { mutableStateOf(true) }

    val isUnreleased = item.isUnreleased

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
            // Top Nav Row: Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
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

                // Primary Action: If unreleased -> Show Coming Soon Card (NO Choose Source); If released -> Show CHOOSE SOURCE Button
                if (isUnreleased) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF141926),
                        border = BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.HourglassTop,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "UNRELEASED • COMING SOON",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.countdownLabel,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "This title has not been released yet. Sources will become available after its official release.",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )

                            Spacer(Modifier.height(14.dp))
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
                        }
                    }
                } else {
                    // Released Movie: CHOOSE SOURCE
                    Button(
                        onClick = { onChooseSource(item) },
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
                }

                // 1. WHERE TO WATCH (OFFICIAL AVAILABILITY)
                if (extendedInfo != null) {
                    Spacer(Modifier.height(16.dp))
                    OfficialAvailabilityCard(
                        extendedInfo = extendedInfo!!,
                        isUnreleased = isUnreleased,
                        releaseDateFormatted = releaseDateFormatted
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

                // 3. PREVIOUS PARTS & PREQUELS (Franchise / Collection)
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
                                context.startActivity(appIntent)
                            } else {
                                val q = URLEncoder.encode("${item.displayName} moctale reviews", "UTF-8")
                                launchAppOrWeb(context, "https://www.google.com/search?q=$q")
                            }
                        }
                    )

                    // 3. GOOGLE (Official 4-Color Google 'G' Logo)
                    ReviewExternalPill(
                        label = "GOOGLE",
                        icon = {
                            Box(
                                modifier = Modifier.size(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Google_%22G%22_logo.svg/120px-Google_%22G%22_logo.svg.png",
                                    contentDescription = "Google",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        },
                        onClick = {
                            val q = URLEncoder.encode("${item.displayName} movie reviews", "UTF-8")
                            launchAppOrWeb(context, "https://www.google.com/search?q=$q")
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
    releaseDateFormatted: String
) {
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
                    extendedInfo.watchProviders.take(5).forEach { provider ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
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
                            }
                        }
                    }
                }
            } else if (extendedInfo.isDirectOtt) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0288D1).copy(alpha = 0.2f))
                            .border(0.8.dp, Color(0xFF0288D1).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📺 DIRECT OTT PREMIERE",
                            color = Color(0xFF81D4FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                    text = "PREVIOUS PARTS & PREQUELS",
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
