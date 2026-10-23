package com.example.ymediaplayer.mycinema.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.mycinema.data.TmdbItem
import com.example.ymediaplayer.mycinema.data.TorrentSource
import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentSourcesBottomSheet(
    item: TmdbItem,
    sources: List<TorrentSource>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onStreamClick: (TorrentSource) -> Unit,
    onDownloadClick: (TorrentSource) -> Unit
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedLangFilter by remember { mutableStateOf("All") } // "All" or "Hindi"
    var selectedQualityFilter by remember { mutableStateOf("All") } // "All", "2160p", "1080p", "720p"

    val hindiCount = remember(sources) { sources.count { it.isHindi } }
    val q4kCount = remember(sources) { sources.count { it.normalizedQuality == "2160p" } }
    val q1080Count = remember(sources) { sources.count { it.normalizedQuality == "1080p" } }
    val q720Count = remember(sources) { sources.count { it.normalizedQuality == "720p" } }

    val filteredSources = remember(sources, selectedLangFilter, selectedQualityFilter) {
        sources.filter { source ->
            val langMatch = when (selectedLangFilter) {
                "Hindi" -> source.isHindi
                else -> true
            }
            val qualityMatch = when (selectedQualityFilter) {
                "2160p" -> source.normalizedQuality == "2160p"
                "1080p" -> source.normalizedQuality == "1080p"
                "720p" -> source.normalizedQuality == "720p"
                else -> true
            }
            langMatch && qualityMatch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.cardBgElevated,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.fullPosterUrl,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 46.dp, height = 64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.glassBg)
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${item.displayYear} • ${if (isLoading) "Searching sources..." else "${sources.size} sources available"}",
                        color = c.textSecondary,
                        fontSize = 12.5.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.glassBg)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = c.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = "All (${sources.size})",
                    isSelected = selectedLangFilter == "All",
                    onClick = { selectedLangFilter = "All" }
                )
                if (hindiCount > 0) {
                    FilterPill(
                        label = "🇮🇳 Hindi ($hindiCount)",
                        isSelected = selectedLangFilter == "Hindi",
                        onClick = { selectedLangFilter = "Hindi" }
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    QualityFilterPill("All", selectedQualityFilter == "All") { selectedQualityFilter = "All" }
                }
                if (q4kCount > 0) {
                    item {
                        QualityFilterPill("4K ($q4kCount)", selectedQualityFilter == "2160p") { selectedQualityFilter = "2160p" }
                    }
                }
                if (q1080Count > 0) {
                    item {
                        QualityFilterPill("1080p ($q1080Count)", selectedQualityFilter == "1080p") { selectedQualityFilter = "1080p" }
                    }
                }
                if (q720Count > 0) {
                    item {
                        QualityFilterPill("720p ($q720Count)", selectedQualityFilter == "720p") { selectedQualityFilter = "720p" }
                    }
                }
            }

            HorizontalDivider(color = c.glassBorder, thickness = 1.dp)

            // Sources List or Loading State
            if (isLoading && sources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = c.accentBlue,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Querying Torrentio, MediaFusion & YTS...",
                            color = c.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (filteredSources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (sources.isEmpty()) "No torrent sources found for this title" else "No sources matching selected filters",
                        color = c.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSources, key = { it.magnet }) { source ->
                        TorrentSourceCard(
                            source = source,
                            onStreamClick = { onStreamClick(source) },
                            onDownloadClick = { onDownloadClick(source) },
                            onCopyMagnet = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Magnet", source.magnet))
                                Toast.makeText(context, "Magnet Link Copied!", Toast.LENGTH_SHORT).show()
                            },
                            onOpenExternal = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.magnet)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No external torrent client found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) c.accentBlue.copy(alpha = 0.22f) else c.glassBg,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, c.accentBlue) else null
    ) {
        Text(
            text = label,
            color = if (isSelected) c.accentBlue else c.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun QualityFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) c.accentBlue.copy(alpha = 0.18f) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, c.accentBlue.copy(0.4f)) else androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder)
    ) {
        Text(
            text = label,
            color = if (isSelected) c.accentBlue else c.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TorrentSourceCard(
    source: TorrentSource,
    onStreamClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCopyMagnet: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val c = LocalAppColors.current

    val qualityBadgeColor = when (source.normalizedQuality) {
        "2160p" -> Color(0xFFFFB300) // Gold / Amber
        "1080p" -> Color(0xFF00C0FF) // Cyan / Blue
        "720p" -> Color(0xFF9C27B0)  // Purple
        else -> Color(0xFF888888)
    }

    val allDownloads by TorrentEngine.downloads.collectAsState()
    val hashMatch = remember(source.magnet) {
        Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(source.magnet)
    }
    val infoHash = hashMatch?.groupValues?.getOrNull(1)?.lowercase()
    val activeDownload = infoHash?.let { allDownloads[it] }

    val isDownloading = activeDownload != null && (activeDownload.status == TorrentDownloadState.Status.DOWNLOADING || activeDownload.status == TorrentDownloadState.Status.CONNECTING)
    val isFinished = activeDownload != null && activeDownload.status == TorrentDownloadState.Status.FINISHED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardBg)
            .border(1.dp, if (isDownloading) Color(0xFF00E676).copy(0.4f) else c.glassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            // Quality, Size, Seeds, Speed Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quality badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(qualityBadgeColor.copy(alpha = 0.18f))
                        .border(1.dp, qualityBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = source.normalizedQuality.uppercase(),
                        color = qualityBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Size
                Text(
                    text = source.size,
                    color = c.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Seeds
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (source.seeds >= 20) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${source.seeds}",
                        color = c.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Speed & ETA
                val speedColor = when {
                    source.seeds >= 500 -> Color(0xFF00E676)
                    source.seeds >= 40 -> Color(0xFF00C0FF)
                    source.seeds >= 15 -> Color(0xFFFFB300)
                    else -> Color(0xFFE57373)
                }

                if (isDownloading && activeDownload != null) {
                    Text(
                        text = "⬇ ${activeDownload.speedFormatted} • ${activeDownload.progressPercent}%",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.estimatedSpeedFormatted,
                            color = speedColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (source.estimatedEtaFormatted.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "• ${source.estimatedEtaFormatted}",
                                color = c.textSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (source.isHevc) {
                    Text(
                        text = "HEVC",
                        color = Color(0xFFAB47BC),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (source.isHindi) {
                    Text(
                        text = "🇮🇳 HINDI",
                        color = Color(0xFFFF7043),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.weight(1f))

                // Provider Tag
                Text(
                    text = source.provider,
                    color = c.textSecondary.copy(alpha = 0.6f),
                    fontSize = 10.5.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Release Title
            Text(
                text = source.title,
                color = c.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stream Now Button
                Button(
                    onClick = onStreamClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accentBlue.copy(alpha = 0.15f),
                        contentColor = c.accentBlue
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.accentBlue.copy(0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Stream", modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stream Now", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // Download Button
                Button(
                    onClick = onDownloadClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFinished) c.accentBlue else Color(0xFF00C853),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        if (isFinished) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            isFinished -> "Downloaded"
                            isDownloading && activeDownload != null -> "${activeDownload.progressPercent}%"
                            else -> "Download"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Copy Magnet
                IconButton(
                    onClick = onCopyMagnet,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.glassBg)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Magnet", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }

                // Open External Client
                IconButton(
                    onClick = onOpenExternal,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.glassBg)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "External App", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
