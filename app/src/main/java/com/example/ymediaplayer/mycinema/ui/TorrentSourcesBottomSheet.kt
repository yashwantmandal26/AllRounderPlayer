package com.example.ymediaplayer.mycinema.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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

    var selectedLangFilter by remember { mutableStateOf("All") } // "All", "Hindi", "Tamil", "Telugu", "Malayalam", "Kannada", "Bangla", "English", "Dual"
    var selectedQualityFilter by remember { mutableStateOf("All") } // "All", "2160p", "1080p", "720p"
    var selectedSortOption by remember { mutableStateOf("SPEED") } // "SPEED", "SIZE", "QUALITY"

    val hindiCount = remember(sources) { sources.count { it.hasHindi } }
    val tamilCount = remember(sources) { sources.count { it.hasTamil } }
    val teluguCount = remember(sources) { sources.count { it.hasTelugu } }
    val malayalamCount = remember(sources) { sources.count { it.hasMalayalam } }
    val kannadaCount = remember(sources) { sources.count { it.hasKannada } }
    val bengaliCount = remember(sources) { sources.count { it.hasBengali } }
    val englishCount = remember(sources) { sources.count { it.hasEnglish } }
    val dualAudioCount = remember(sources) { sources.count { it.isDualAudio } }
    val q4kCount = remember(sources) { sources.count { it.normalizedQuality == "2160p" } }
    val q1080Count = remember(sources) { sources.count { it.normalizedQuality == "1080p" } }
    val q720Count = remember(sources) { sources.count { it.normalizedQuality == "720p" } }

    val filteredSources = remember(sources, selectedLangFilter, selectedQualityFilter, selectedSortOption) {
        val filtered = sources.filter { source ->
            val langMatch = when (selectedLangFilter) {
                "Hindi" -> source.hasHindi
                "Tamil" -> source.hasTamil
                "Telugu" -> source.hasTelugu
                "Malayalam" -> source.hasMalayalam
                "Kannada" -> source.hasKannada
                "Bangla" -> source.hasBengali
                "English" -> source.hasEnglish
                "Dual" -> source.isDualAudio
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
        when (selectedSortOption) {
            "SIZE" -> filtered.sortedBy { it.parsedSizeBytes.let { b -> if (b > 0) b else Long.MAX_VALUE } }
            "QUALITY" -> filtered.sortedByDescending { it.healthScore }
            else -> filtered.sortedByDescending { it.seeds }
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
                        text = "${item.displayYear} • ${if (isLoading && sources.isEmpty()) "Searching fastest scrapers..." else if (isLoading) "Found ${sources.size} • Searching more..." else "${sources.size} sources available"}",
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

            // Sort & Language Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterPill(
                        label = "⚡ Fastest (Seeders)",
                        isSelected = selectedSortOption == "SPEED",
                        onClick = { selectedSortOption = "SPEED" }
                    )
                }
                item {
                    FilterPill(
                        label = "💾 Smallest Size",
                        isSelected = selectedSortOption == "SIZE",
                        onClick = { selectedSortOption = "SIZE" }
                    )
                }
                item {
                    FilterPill(
                        label = "🌟 Best Quality",
                        isSelected = selectedSortOption == "QUALITY",
                        onClick = { selectedSortOption = "QUALITY" }
                    )
                }
                if (hindiCount > 0) {
                    item {
                        FilterPill(
                            label = "🇮🇳 Hindi ($hindiCount)",
                            isSelected = selectedLangFilter == "Hindi",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Hindi") "All" else "Hindi" }
                        )
                    }
                }
                if (tamilCount > 0) {
                    item {
                        FilterPill(
                            label = "🎬 Tamil ($tamilCount)",
                            isSelected = selectedLangFilter == "Tamil",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Tamil") "All" else "Tamil" }
                        )
                    }
                }
                if (teluguCount > 0) {
                    item {
                        FilterPill(
                            label = "🎬 Telugu ($teluguCount)",
                            isSelected = selectedLangFilter == "Telugu",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Telugu") "All" else "Telugu" }
                        )
                    }
                }
                if (malayalamCount > 0) {
                    item {
                        FilterPill(
                            label = "🌴 Malayalam ($malayalamCount)",
                            isSelected = selectedLangFilter == "Malayalam",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Malayalam") "All" else "Malayalam" }
                        )
                    }
                }
                if (kannadaCount > 0) {
                    item {
                        FilterPill(
                            label = "🎬 Kannada ($kannadaCount)",
                            isSelected = selectedLangFilter == "Kannada",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Kannada") "All" else "Kannada" }
                        )
                    }
                }
                if (bengaliCount > 0) {
                    item {
                        FilterPill(
                            label = "🎭 Bangla ($bengaliCount)",
                            isSelected = selectedLangFilter == "Bangla",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Bangla") "All" else "Bangla" }
                        )
                    }
                }
                if (englishCount > 0) {
                    item {
                        FilterPill(
                            label = "🇺🇸 English ($englishCount)",
                            isSelected = selectedLangFilter == "English",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "English") "All" else "English" }
                        )
                    }
                }
                if (dualAudioCount > 0) {
                    item {
                        FilterPill(
                            label = "🌐 Dual Audio ($dualAudioCount)",
                            isSelected = selectedLangFilter == "Dual",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Dual") "All" else "Dual" }
                        )
                    }
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

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = c.accentBlue,
                    trackColor = c.accentBlue.copy(alpha = 0.15f)
                )
            }

            // Sources List or Loading State
            if (isLoading && sources.isEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = c.accentBlue,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Searching high-speed sources (Torrentio, YTS)...",
                                color = c.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    items(4) {
                        TorrentSourceSkeletonCard()
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

                Spacer(Modifier.weight(1f))

                // Provider Tag (single-line, never hyphenates)
                Text(
                    text = source.provider,
                    color = c.textSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Tag Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                source.ripType?.let { rip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = rip, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasHindi) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF5722).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFF5722).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "🇮🇳 HINDI", color = Color(0xFFFF7043), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasTamil) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE91E63).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFE91E63).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "TAMIL", color = Color(0xFFF48FB1), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasTelugu) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3F51B5).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF3F51B5).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "TELUGU", color = Color(0xFF9FA8DA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasMalayalam) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF009688).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF009688).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "MALAYALAM", color = Color(0xFF80CBC4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasKannada) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF9C27B0).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF9C27B0).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "KANNADA", color = Color(0xFFCE93D8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasBengali) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF9800).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFF9800).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "BANGLA", color = Color(0xFFFFB74D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.hasEnglish) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF2196F3).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "ENGLISH", color = Color(0xFF90CAF9), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.isDualAudio) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00E5FF).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "🌐 DUAL AUDIO", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.isHdr) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "✨ HDR", color = Color(0xFFFFD700), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (source.isHevc) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFAB47BC).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFAB47BC).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(text = "⚡ HEVC", color = Color(0xFFCE93D8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

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

@Composable
private fun TorrentSourceSkeletonCard() {
    val c = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardBg)
            .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.textSecondary.copy(alpha = alpha * 0.45f))
                )
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.textSecondary.copy(alpha = alpha * 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.textSecondary.copy(alpha = alpha * 0.35f))
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(width = 65.dp, height = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.textSecondary.copy(alpha = alpha * 0.3f))
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.textSecondary.copy(alpha = alpha * 0.3f))
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accentBlue.copy(alpha = alpha * 0.25f))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.textSecondary.copy(alpha = alpha * 0.2f))
                )
            }
        }
    }
}
