package com.example.ymediaplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MediaViewType
import com.example.ymediaplayer.data.VideoItem
import com.example.ymediaplayer.theme.LocalAppColors

// ═══════════════════════════════════════════════════════════════════════════════
// 1. DETAILED LIST ITEM
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoDetailedListItem(
    video: VideoItem,
    isFavorite: Boolean = false,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isSelected) c.accentBlue.copy(0.35f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.45f) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (c.isMatte) {
                    SolidColor(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.cardBg)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(c.accentBlue.copy(0.28f), c.cardBg))
                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                }
            )
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                brush = if (c.isMatte) {
                    androidx.compose.ui.graphics.SolidColor(if (isSelected) c.accentBlue else c.glassBorder)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(Color.White.copy(0.65f), c.accentBlue))
                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                },
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Checkbox
            AnimatedVisibility(visible = inSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(4.dp, CircleShape, ambientColor = if (isSelected) c.accentBlue else Color.Black.copy(0.5f))
                        .clip(CircleShape)
                        .background(if (isSelected) c.accentBlue else c.cardBg)
                        .border(1.5.dp, if (isSelected) Color.White else c.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            // Thumbnail with 3D Bezel
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
                    model = remember(video.uri, video.duration) {
                        buildVideoThumbnailRequest(context, video.uri, video.duration)
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Duration Pill
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
                // Watch Progress
                if (progress > 0 && video.duration > 0) {
                    val r = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(0.5f))) {
                        Box(modifier = Modifier.fillMaxWidth(r).height(3.dp).background(c.accentBlue))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Metadata Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title.substringBeforeLast("."),
                    fontSize = 15.sp,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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

            // Favorite Button
            if (!inSelectionMode) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFF4D6D) else c.textSecondary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. COMPACT LIST ITEM
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoCompactListItem(
    video: VideoItem,
    isFavorite: Boolean = false,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 1.5.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = if (isSelected) c.accentBlue.copy(0.35f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.45f) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (c.isMatte) {
                    SolidColor(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.cardBg)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(c.accentBlue.copy(0.24f), c.cardBg))
                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                brush = if (c.isMatte) {
                    androidx.compose.ui.graphics.SolidColor(if (isSelected) c.accentBlue else c.glassBorder)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(Color.White.copy(0.6f), c.accentBlue))
                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                },
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            }
            .padding(horizontal = 10.dp, vertical = 4.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox
        AnimatedVisibility(visible = inSelectionMode) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isSelected) c.accentBlue else c.cardBg)
                    .border(1.2.dp, if (isSelected) Color.White else c.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        // Dense Thumbnail
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282A36))
                .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = remember(video.uri, video.duration) {
                    buildVideoThumbnailRequest(context, video.uri, video.duration)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(formatTime(video.duration), color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
            }
            if (progress > 0 && video.duration > 0) {
                val r = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.5.dp).background(Color.Black.copy(0.5f))) {
                    Box(modifier = Modifier.fillMaxWidth(r).height(2.5.dp).background(c.accentBlue))
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // Dense info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.title.substringBeforeLast("."),
                fontSize = 13.5.sp,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatSize(video.size), fontSize = 11.sp, color = c.textSecondary)
                if (progress > 0 && video.duration > 0) {
                    val pct = ((progress.toFloat() / video.duration) * 100).toInt()
                    Text("·", fontSize = 10.sp, color = c.textSecondary)
                    Text("$pct% watched", fontSize = 11.sp, color = c.accentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (!inSelectionMode) {
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFFF4D6D) else c.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. GRID ITEM 2 (2 COLUMNS)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoGridItem2(
    video: VideoItem,
    isFavorite: Boolean = false,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 8.dp else 5.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isSelected) c.accentBlue.copy(0.35f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.45f) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (c.isMatte) {
                    SolidColor(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.cardBg)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(c.accentBlue.copy(0.24f), c.cardBg))
                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                }
            )
            .border(
                width = if (isSelected) 1.6.dp else 1.2.dp,
                brush = if (c.isMatte) {
                    androidx.compose.ui.graphics.SolidColor(if (isSelected) c.accentBlue else c.glassBorder)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(Color.White.copy(0.6f), c.accentBlue))
                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                },
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            }
    ) {
        // Thumbnail with 16:9 ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.5f)
                .background(Color(0xFF282A36))
        ) {
            AsyncImage(
                model = remember(video.uri, video.duration) {
                    buildVideoThumbnailRequest(context, video.uri, video.duration)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge at bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(formatTime(video.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }

            // Top controls: Selection / Favorite
            if (inSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) c.accentBlue else Color.Black.copy(0.5f))
                        .border(1.2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onFavoriteToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Progress bar
            if (progress > 0 && video.duration > 0) {
                val r = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(0.5f))) {
                    Box(modifier = Modifier.fillMaxWidth(r).height(3.dp).background(c.accentBlue))
                }
            }
        }

        // Body Info
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                video.title.substringBeforeLast("."),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatSize(video.size), fontSize = 11.sp, color = c.textSecondary)
                if (progress > 0 && video.duration > 0) {
                    val pct = ((progress.toFloat() / video.duration) * 100).toInt()
                    Text("$pct%", fontSize = 10.sp, color = c.accentBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. GRID ITEM 3 (3 COLUMNS)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoGridItem3(
    video: VideoItem,
    isFavorite: Boolean = false,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 6.dp else 3.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = if (isSelected) c.accentBlue.copy(0.35f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.45f) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (c.isMatte) {
                    SolidColor(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.cardBg)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(c.accentBlue.copy(0.25f), c.cardBg))
                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                brush = if (c.isMatte) {
                    androidx.compose.ui.graphics.SolidColor(if (isSelected) c.accentBlue else c.glassBorder)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(Color.White.copy(0.6f), c.accentBlue))
                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                },
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            }
    ) {
        // Square 1:1 Aspect Ratio Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF282A36))
        ) {
            AsyncImage(
                model = remember(video.uri, video.duration) {
                    buildVideoThumbnailRequest(context, video.uri, video.duration)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge at bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(formatTime(video.duration), color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
            }

            // Selection indicator
            if (inSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(c.accentBlue)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }

            if (isFavorite && !inSelectionMode) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF4D6D),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(13.dp)
                )
            }

            // Progress line
            if (progress > 0 && video.duration > 0) {
                val r = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.5.dp).background(Color.Black.copy(0.5f))) {
                    Box(modifier = Modifier.fillMaxWidth(r).height(2.5.dp).background(c.accentBlue))
                }
            }
        }

        // Minimalist label
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
            Text(
                video.title.substringBeforeLast("."),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatSize(video.size), fontSize = 9.5.sp, color = c.textSecondary)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 5. LARGE CARD (CINEMATIC HERO)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun VideoLargeCard(
    video: VideoItem,
    isFavorite: Boolean = false,
    inSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val progress = appPreferences.getVideoProgress(video.uri.toString())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(
                elevation = if (isSelected) 14.dp else 10.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = if (isSelected) c.accentBlue.copy(0.4f) else c.cardShadowColor,
                spotColor = if (isSelected) c.accentBlue.copy(0.5f) else c.cardShadowColor
            ))
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (c.isMatte) {
                    SolidColor(if (isSelected) c.accentBlue.copy(alpha = 0.15f) else c.cardBg)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(c.accentBlue.copy(0.28f), c.cardBg))
                    else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg))
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.4.dp,
                brush = if (c.isMatte) {
                    androidx.compose.ui.graphics.SolidColor(if (isSelected) c.accentBlue else c.glassBorder)
                } else {
                    if (isSelected) Brush.verticalGradient(listOf(Color.White.copy(0.7f), c.accentBlue))
                    else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow))
                },
                shape = RoundedCornerShape(22.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            }
    ) {
        // Hero Wide Aspect Preview (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF282A36))
        ) {
            AsyncImage(
                model = remember(video.uri, video.duration) {
                    buildVideoThumbnailRequest(context, video.uri, video.duration)
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Cinematic Scrim Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Centered 3D Glass Play Button
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .shadow(10.dp, CircleShape, ambientColor = c.accentBlue, spotColor = c.accentBlue)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(c.accentBlue.copy(alpha = 0.95f), c.accentBlue.copy(alpha = 0.75f))))
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(30.dp))
            }

            // Top Bar: Selection / Category / Favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (isSelected) c.accentBlue else Color.Black.copy(0.6f))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("HD VIDEO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!inSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { onFavoriteToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Overlay: Duration and Resume state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(formatTime(video.duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                if (progress > 0 && video.duration > 0) {
                    val pct = ((progress.toFloat() / video.duration) * 100).toInt()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.accentBlue.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Watched $pct%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Progress bar along the very bottom of the thumbnail
            if (progress > 0 && video.duration > 0) {
                val r = (progress.toFloat() / video.duration).coerceIn(0f, 1f)
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.5.dp).background(Color.Black.copy(0.5f))) {
                    Box(modifier = Modifier.fillMaxWidth(r).height(3.5.dp).background(c.accentBlue))
                }
            }
        }

        // Details below thumbnail
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                video.title.substringBeforeLast("."),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatSize(video.size), fontSize = 13.sp, color = c.textSecondary)
                    Text("·", fontSize = 12.sp, color = c.textSecondary)
                    Text(formatTime(video.duration), fontSize = 13.sp, color = c.textSecondary)
                }
                if (progress > 0 && video.duration > 0) {
                    Text("Resume at ${formatTime(progress)}", fontSize = 12.sp, color = c.accentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 6. VIEW SWITCHER BUTTON & DROPDOWN MENU
// ═══════════════════════════════════════════════════════════════════════════════
fun getIconForMediaViewType(type: MediaViewType): ImageVector = when (type) {
    MediaViewType.DETAILED_LIST -> Icons.AutoMirrored.Rounded.ViewList
    MediaViewType.COMPACT_LIST -> Icons.Rounded.DensitySmall
    MediaViewType.GRID_2 -> Icons.Rounded.GridView
    MediaViewType.GRID_3 -> Icons.Rounded.Apps
    MediaViewType.LARGE_CARD -> Icons.Rounded.ViewAgenda
}

@Composable
fun MediaViewSwitcherMenu(
    currentViewType: MediaViewType,
    onViewTypeSelected: (MediaViewType) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val context = LocalContext.current

    IconButton(
        onClick = {
            val entries = MediaViewType.entries
            val nextIndex = (currentViewType.ordinal + 1) % entries.size
            val nextType = entries[nextIndex]
            onViewTypeSelected(nextType)
            android.widget.Toast.makeText(context, nextType.displayName, android.widget.Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
            .size(38.dp)
            .then(if (c.isMatte) Modifier else Modifier.shadow(6.dp, CircleShape, ambientColor = c.cardShadowColor, spotColor = c.cardShadowColor))
            .clip(CircleShape)
            .background(if (c.isMatte) SolidColor(c.cardBgElevated) else Brush.verticalGradient(listOf(c.cardBgElevated, c.cardBg)))
            .border(1.2.dp, if (c.isMatte) androidx.compose.ui.graphics.SolidColor(c.glassBorder) else Brush.verticalGradient(listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)), CircleShape)
    ) {
        Icon(
            getIconForMediaViewType(currentViewType),
            contentDescription = "Switch View: ${currentViewType.displayName}",
            tint = c.accentBlue,
            modifier = Modifier.size(19.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 7. UNIFIED MEDIA FILES VIEW
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun MediaFilesView(
    videos: List<VideoItem>,
    viewType: MediaViewType,
    inSelectionMode: Boolean = false,
    selectedVideoIds: Set<Long> = emptySet(),
    favorites: Set<String> = emptySet(),
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongPress: (VideoItem) -> Unit = {},
    onFavoriteToggle: (VideoItem) -> Unit = {},
    onSelectToggle: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp)
) {
    when (viewType) {
        MediaViewType.DETAILED_LIST -> {
            LazyColumn(modifier = modifier, contentPadding = contentPadding) {
                if (headerContent != null) {
                    item { headerContent() }
                }
                itemsIndexed(videos, key = { _, v -> "detailed_${v.id}" }) { _, video ->
                    VideoDetailedListItem(
                        video = video,
                        isFavorite = favorites.contains(video.uri.toString()),
                        inSelectionMode = inSelectionMode,
                        isSelected = selectedVideoIds.contains(video.id),
                        onClick = { if (inSelectionMode) onSelectToggle(video) else onVideoClick(video) },
                        onLongPress = { onVideoLongPress(video) },
                        onFavoriteToggle = { onFavoriteToggle(video) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        MediaViewType.COMPACT_LIST -> {
            LazyColumn(modifier = modifier, contentPadding = contentPadding) {
                if (headerContent != null) {
                    item { headerContent() }
                }
                itemsIndexed(videos, key = { _, v -> "compact_${v.id}" }) { _, video ->
                    VideoCompactListItem(
                        video = video,
                        isFavorite = favorites.contains(video.uri.toString()),
                        inSelectionMode = inSelectionMode,
                        isSelected = selectedVideoIds.contains(video.id),
                        onClick = { if (inSelectionMode) onSelectToggle(video) else onVideoClick(video) },
                        onLongPress = { onVideoLongPress(video) },
                        onFavoriteToggle = { onFavoriteToggle(video) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        MediaViewType.GRID_2 -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier.padding(horizontal = 10.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (headerContent != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        headerContent()
                    }
                }
                itemsIndexed(videos, key = { _, v -> "grid2_${v.id}" }) { _, video ->
                    VideoGridItem2(
                        video = video,
                        isFavorite = favorites.contains(video.uri.toString()),
                        inSelectionMode = inSelectionMode,
                        isSelected = selectedVideoIds.contains(video.id),
                        onClick = { if (inSelectionMode) onSelectToggle(video) else onVideoClick(video) },
                        onLongPress = { onVideoLongPress(video) },
                        onFavoriteToggle = { onFavoriteToggle(video) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        MediaViewType.GRID_3 -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier.padding(horizontal = 8.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (headerContent != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        headerContent()
                    }
                }
                itemsIndexed(videos, key = { _, v -> "grid3_${v.id}" }) { _, video ->
                    VideoGridItem3(
                        video = video,
                        isFavorite = favorites.contains(video.uri.toString()),
                        inSelectionMode = inSelectionMode,
                        isSelected = selectedVideoIds.contains(video.id),
                        onClick = { if (inSelectionMode) onSelectToggle(video) else onVideoClick(video) },
                        onLongPress = { onVideoLongPress(video) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        MediaViewType.LARGE_CARD -> {
            LazyColumn(modifier = modifier, contentPadding = contentPadding) {
                if (headerContent != null) {
                    item { headerContent() }
                }
                itemsIndexed(videos, key = { _, v -> "large_${v.id}" }) { _, video ->
                    VideoLargeCard(
                        video = video,
                        isFavorite = favorites.contains(video.uri.toString()),
                        inSelectionMode = inSelectionMode,
                        isSelected = selectedVideoIds.contains(video.id),
                        onClick = { if (inSelectionMode) onSelectToggle(video) else onVideoClick(video) },
                        onLongPress = { onVideoLongPress(video) },
                        onFavoriteToggle = { onFavoriteToggle(video) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
