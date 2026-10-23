package com.example.ymediaplayer.mycinema.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.theme.LocalAppColors

@Composable
fun MyCinemaLibraryModal(
    initialTab: String = "favorites", // "favorites" or "watchlist"
    onDismiss: () -> Unit,
    onItemClick: (TmdbItem) -> Unit
) {
    BackHandler { onDismiss() }

    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }

    var selectedTab by remember { mutableStateOf(initialTab) } // "favorites" or "watchlist"
    var refreshKey by remember { mutableIntStateOf(0) }

    val favorites = remember(refreshKey) { appPreferences.getCinemaFavorites() }
    val watchlist = remember(refreshKey) { appPreferences.getCinemaWatchlist() }

    val currentList = if (selectedTab == "favorites") favorites else watchlist

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "MY CINEMA LIBRARY",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tab Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Favorites Tab Pill
                val isFavSelected = selectedTab == "favorites"
                Surface(
                    onClick = { selectedTab = "favorites" },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isFavSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, if (isFavSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = if (isFavSelected) Color.White else Color(0xFFE50914),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Favorites (${favorites.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isFavSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Watchlist Tab Pill
                val isWatchSelected = selectedTab == "watchlist"
                Surface(
                    onClick = { selectedTab = "watchlist" },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isWatchSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, if (isWatchSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = if (isWatchSelected) Color.Black else Color(0xFFFFB300),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Watchlist (${watchlist.size})",
                            color = if (isWatchSelected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isWatchSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Main Content Area
            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (selectedTab == "favorites") Icons.Rounded.FavoriteBorder else Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                tint = if (selectedTab == "favorites") Color(0xFFE50914).copy(alpha = 0.6f) else Color(0xFFFFB300).copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = if (selectedTab == "favorites") "No Favorites Yet" else "Watchlist is Empty",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = if (selectedTab == "favorites")
                                "Tap the ❤️ Favorite button on any movie or series detail sheet to save it here for quick access."
                            else
                                "Tap the 🔖 Watchlist button on any movie or series to save it for your next watch.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentList, key = { it.id }) { item ->
                        SavedCinemaCard(
                            item = item,
                            isWatchlistTab = selectedTab == "watchlist",
                            onClick = { onItemClick(item) },
                            onRemove = {
                                if (selectedTab == "favorites") {
                                    appPreferences.toggleCinemaFavorite(item)
                                } else {
                                    appPreferences.toggleCinemaWatchlist(item)
                                }
                                refreshKey++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedCinemaCard(
    item: TmdbItem,
    isWatchlistTab: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.fullPosterUrl,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top gradient for badge and remove button readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            )

            // Series / Movie Badge
            if (item.isSeries) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.88f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SERIES",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Quick Remove Button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Bottom Rating Badge
            if (item.voteAverage > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = item.ratingFormatted,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.displayName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (item.displayYear.isNotEmpty()) {
            Text(
                text = item.displayYear,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}
