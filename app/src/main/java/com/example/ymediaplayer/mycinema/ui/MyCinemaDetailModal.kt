package com.example.ymediaplayer.mycinema.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ymediaplayer.mycinema.data.MyCinemaRepository
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun MyCinemaDetailModal(
    item: TmdbItem,
    repository: MyCinemaRepository,
    onDismiss: () -> Unit,
    onChooseSource: (TmdbItem) -> Unit,
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

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(item.id) {
        youtubeTrailerKey = repository.getTrailerYoutubeKey(item.isSeries, item.id)
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

                Spacer(Modifier.height(20.dp))

                // Primary CTA Button: CHOOSE SOURCE
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
                    LibraryActionButton(
                        icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        isActive = isBookmarked,
                        onClick = {
                            isBookmarked = appPreferences.toggleCinemaWatchlist(itemKey)
                            Toast.makeText(
                                context,
                                if (isBookmarked) "Added to Watchlist" else "Removed from Watchlist",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // Collection / Layers
                    LibraryActionButton(
                        icon = Icons.Rounded.Layers,
                        isActive = false,
                        onClick = {
                            Toast.makeText(context, "Added to Collection", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Favorite / Heart
                    LibraryActionButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        isActive = isFavorite,
                        activeColor = Color(0xFFE50914),
                        onClick = {
                            isFavorite = appPreferences.toggleCinemaFavorite(itemKey)
                            Toast.makeText(
                                context,
                                if (isFavorite) "Added to Favorites" else "Removed from Favorites",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // Share
                    LibraryActionButton(
                        icon = Icons.Rounded.Share,
                        isActive = false,
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                val mediaType = if (item.isSeries) "tv" else "movie"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out \"${item.displayName}\" on AllRounder Media Player! https://www.themoviedb.org/$mediaType/${item.id}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share with"))
                        }
                    )
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

                // Row of external app / review buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. TRAILER (YouTube app or browser)
                    ReviewExternalPill(
                        label = "TRAILER",
                        icon = {
                            Icon(Icons.Rounded.Movie, contentDescription = "Trailer", tint = Color.White, modifier = Modifier.size(16.dp))
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

                    // 2. MOCTALE (Moctale app or web)
                    ReviewExternalPill(
                        label = "MOCTALE",
                        icon = {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Moctale", tint = Color.White, modifier = Modifier.size(15.dp))
                        },
                        onClick = {
                            val q = URLEncoder.encode(item.displayName, "UTF-8")
                            val url = "https://moctale.com/search?q=$q"
                            launchAppOrWeb(context, url)
                        }
                    )

                    // 3. GOOGLE (Google app or browser)
                    ReviewExternalPill(
                        label = "GOOGLE",
                        icon = {
                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 14.sp)
                        },
                        onClick = {
                            val q = URLEncoder.encode("${item.displayName} movie reviews", "UTF-8")
                            launchAppOrWeb(context, "https://www.google.com/search?q=$q")
                        }
                    )

                    // 4. LETTERBOXD (Letterboxd app or browser)
                    ReviewExternalPill(
                        label = "LETTERBOXD",
                        icon = {
                            Text("★", color = Color(0xFF00E054), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        },
                        onClick = {
                            val q = URLEncoder.encode(item.displayName, "UTF-8")
                            launchAppOrWeb(context, "https://letterboxd.com/search/$q/")
                        }
                    )

                    // 5. IMDB (IMDb app or browser)
                    ReviewExternalPill(
                        label = "IMDb",
                        icon = {
                            Text("IMDb", color = Color(0xFFF5C518), fontWeight = FontWeight.Black, fontSize = 12.sp)
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
