package com.example.ymediaplayer.mycinema.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.Image
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import com.example.ymediaplayer.R
import com.example.ymediaplayer.mycinema.data.DownloadSite
import com.example.ymediaplayer.mycinema.data.DownloadSitesManager
import com.example.ymediaplayer.mycinema.data.TmdbItem
import kotlinx.coroutines.launch
import com.example.ymediaplayer.mycinema.data.TorrentSource
import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.theme.LocalAppColors

private fun TorrentSource.qualityRank(): Int = when (normalizedQuality) {
    "2160p" -> 4
    "1080p" -> 3
    "720p" -> 2
    "480p" -> 1
    else -> 0
}

private fun TorrentSource.sizeForSorting(): Long = parsedSizeBytes.takeIf { it > 0L } ?: Long.MAX_VALUE

private fun sortTorrentSources(sources: List<TorrentSource>, sortOption: String): List<TorrentSource> = when (sortOption) {
    "SIZE" -> sources.sortedWith(
        compareBy<TorrentSource> { it.sizeForSorting() }
            .thenByDescending { it.seeds }
    )
    "QUALITY" -> sources.sortedWith(
        compareByDescending<TorrentSource> { it.qualityRank() }
            .thenByDescending { it.seeds }
            .thenBy { it.sizeForSorting() }
    )
    else -> sources.sortedWith(
        compareByDescending<TorrentSource> { it.seeds }
            .thenByDescending { it.peers }
            .thenBy { it.sizeForSorting() }
    )
}

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
    val coroutineScope = rememberCoroutineScope()

    var resolvingSiteId by remember { mutableStateOf<String?>(null) }
    var downloadSiteNotice by remember { mutableStateOf<String?>(null) }
    var packSourceForEpisodePicker by remember { mutableStateOf<TorrentSource?>(null) }

    fun handleDownloadSiteClick(site: DownloadSite) {
        if (resolvingSiteId != null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

    var selectedLangFilter by remember { mutableStateOf("All") } // "All", "Hindi", "Korean", "Tamil", "Telugu", "Malayalam", "Kannada", "Bangla", "English", "Dual"
    var selectedQualityFilter by remember { mutableStateOf("All") } // "All", "2160p", "1080p", "720p"
    var selectedSeasonFilter by remember { mutableStateOf("All") } // "All", "Packs", "S1", "S2", "S3"...
    var selectedSortOption by remember { mutableStateOf("SPEED") } // "SPEED", "SIZE", "QUALITY"

    val isSeriesItem = remember(item, sources) {
        item.isSeries || sources.any {
            val t = it.title.lowercase()
            t.contains(Regex("""s\d{1,2}|season\s*\d{1,2}|pack|batch"""))
        }
    }

    val hindiCount = remember(sources) { sources.count { it.hasHindi } }
    val koreanCount = remember(sources) { sources.count { it.hasKorean } }
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

    val packsCount = remember(sources) {
        sources.count {
            val t = it.title.lowercase()
            t.contains("pack") || t.contains("complete") || t.contains("batch") || t.contains("s01-") || t.contains("season 1-")
        }
    }

    val seasonCounts = remember(sources) {
        (1..8).associateWith { s ->
            val sPad = String.format(java.util.Locale.US, "s%02d", s)
            val sText = "season $s"
            val sDot = "season.$s"
            val sShort = "s$s"
            sources.count {
                val t = it.title.lowercase()
                t.contains(sPad) || t.contains("$sShort ") || t.contains("$sShort.") || t.contains(sText) || t.contains(sDot)
            }
        }
    }

    val filteredSources = remember(sources, selectedLangFilter, selectedQualityFilter, selectedSeasonFilter, selectedSortOption) {
        val filtered = sources.filter { source ->
            val langMatch = when (selectedLangFilter) {
                "Hindi" -> source.hasHindi
                "Korean" -> source.hasKorean
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
            val seasonMatch = when (selectedSeasonFilter) {
                "All" -> true
                "Packs" -> {
                    val t = source.title.lowercase()
                    t.contains("pack") || t.contains("complete") || t.contains("batch") || t.contains("s01-") || t.contains("season 1-")
                }
                else -> {
                    val sNum = selectedSeasonFilter.removePrefix("S").toIntOrNull() ?: 1
                    val sPad = String.format(java.util.Locale.US, "s%02d", sNum)
                    val sText = "season $sNum"
                    val sDot = "season.$sNum"
                    val sShort = "s$sNum"
                    val t = source.title.lowercase()
                    t.contains(sPad) || t.contains("$sShort ") || t.contains("$sShort.") || t.contains(sText) || t.contains(sDot) ||
                    (t.contains("complete") || t.contains("pack"))
                }
            }
            langMatch && qualityMatch && seasonMatch
        }
        sortTorrentSources(filtered, selectedSortOption)
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

            // Sort controls
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
            }

            // Keep language chips on their own row below sorting.
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterPill(
                        label = "🌐 All Languages",
                        isSelected = selectedLangFilter == "All",
                        onClick = { selectedLangFilter = "All" }
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
                if (koreanCount > 0) {
                    item {
                        FilterPill(
                            label = "🇰🇷 Korean ($koreanCount)",
                            isSelected = selectedLangFilter == "Korean",
                            onClick = { selectedLangFilter = if (selectedLangFilter == "Korean") "All" else "Korean" }
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

            // Series Season / Pack Filter Chips (Matching Image 2)
            if (isSeriesItem) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        QualityFilterPill(
                            label = "All Seasons",
                            isSelected = selectedSeasonFilter == "All",
                            onClick = { selectedSeasonFilter = "All" }
                        )
                    }
                    if (packsCount > 0) {
                        item {
                            QualityFilterPill(
                                label = "📦 Packs ($packsCount)",
                                isSelected = selectedSeasonFilter == "Packs",
                                onClick = { selectedSeasonFilter = if (selectedSeasonFilter == "Packs") "All" else "Packs" }
                            )
                        }
                    }
                    for (s in 1..8) {
                        val count = seasonCounts[s] ?: 0
                        if (count > 0 || (s <= 2 && sources.isEmpty())) {
                            item {
                                QualityFilterPill(
                                    label = "S$s (${if (count > 0) count else "—"})",
                                    isSelected = selectedSeasonFilter == "S$s",
                                    onClick = { selectedSeasonFilter = if (selectedSeasonFilter == "S$s") "All" else "S$s" }
                                )
                            }
                        }
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
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(c.glassBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (sources.isEmpty()) "No Sources Found" else "No Matching Sources",
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (sources.isEmpty())
                                "No stream sources found for this title. You can check official OTT options in Ways to Watch."
                            else
                                "No torrent sources matched your active filters. Try switching to 'All'.",
                            color = c.textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        if (selectedLangFilter != "All" || selectedQualityFilter != "All" || selectedSeasonFilter != "All") {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    selectedLangFilter = "All"
                                    selectedQualityFilter = "All"
                                    selectedSeasonFilter = "All"
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, c.accentBlue.copy(alpha = 0.5f))
                            ) {
                                Text("Reset Filters", color = c.accentBlue, fontSize = 12.sp)
                            }
                        } else {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Still can't find? Try web download sites:",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(0.9f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DownloadSitesManager.SITES.forEach { site ->
                                    DownloadSiteButton(
                                        site = site,
                                        isResolving = resolvingSiteId == site.id,
                                        enabled = resolvingSiteId == null,
                                        modifier = Modifier.weight(1f),
                                        onClick = { handleDownloadSiteClick(site) }
                                    )
                                }
                            }
                        }
                    }
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
                            onStreamClick = {
                                if (isPackSource(source.title)) {
                                    packSourceForEpisodePicker = source
                                } else {
                                    onStreamClick(source)
                                }
                            },
                            onPackClick = {
                                packSourceForEpisodePicker = source
                            },
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

            // ─── CAN'T FIND? BOTTOM DOCKED BAR ─────────────────────────────────────
            DownloadSitesFooter(
                resolvingSiteId = resolvingSiteId,
                downloadSiteNotice = downloadSiteNotice,
                onSiteClick = { site -> handleDownloadSiteClick(site) }
            )
        }

        // Pack Episode Picker Modal
        packSourceForEpisodePicker?.let { packSrc ->
            PackEpisodePickerBottomSheet(
                source = packSrc,
                item = item,
                onDismiss = { packSourceForEpisodePicker = null },
                onSelectEpisode = { src, epFile ->
                    packSourceForEpisodePicker = null
                    val rawHash = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                        .find(src.magnet)?.groupValues?.getOrNull(1)
                    val hash = rawHash?.let(TorrentEngine::normalizeInfoHash)
                    if (hash != null) {
                        TorrentEngine.setSelectedFileIndex(hash, epFile.index)
                    }
                    onStreamClick(src)
                }
            )
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
    onPackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCopyMagnet: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val c = LocalAppColors.current
    val isPack = isPackSource(source.title)

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
    val infoHash = hashMatch?.groupValues?.getOrNull(1)?.let(TorrentEngine::normalizeInfoHash)
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
            // Header Row: Quality, Size, Seeds on left | Provider Pill on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
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

                Spacer(Modifier.width(8.dp))

                // Size
                Text(
                    text = source.size,
                    color = c.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.width(8.dp))

                // Seeds
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    source.seeds >= 20 -> Color(0xFF4CAF50)
                                    source.seeds > 0 -> Color(0xFFFF9800)
                                    else -> Color(0xFFFF5252)
                                }
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (source.seeds > 0) "${source.seeds}" else "0 (Dead)",
                        color = if (source.seeds > 0) c.textSecondary else Color(0xFFFF5252),
                        fontSize = 11.5.sp,
                        fontWeight = if (source.seeds > 0) FontWeight.Medium else FontWeight.Bold
                    )
                }

                Spacer(Modifier.weight(1f))

                // Provider Tag Pill (clean dedicated surface so it never truncates to 'E...' or touches the border)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.glassBg)
                        .border(0.6.dp, c.glassBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = source.provider,
                        color = c.textSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Speed & Tag Badges using FlowRow (wraps safely without touching or clipping card borders)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Speed & ETA Badge
                val speedColor = when {
                    source.seeds >= 500 -> Color(0xFF00E676)
                    source.seeds >= 40 -> Color(0xFF00C0FF)
                    source.seeds >= 15 -> Color(0xFFFFB300)
                    else -> Color(0xFFE57373)
                }
                val speedText = if (isDownloading) {
                    "⬇ ${activeDownload.speedFormatted} • ${activeDownload.progressPercent}%"
                } else if (source.estimatedEtaFormatted.isNotEmpty()) {
                    "${source.estimatedSpeedFormatted} • ${source.estimatedEtaFormatted}"
                } else {
                    source.estimatedSpeedFormatted
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(speedColor.copy(alpha = 0.12f))
                        .border(0.6.dp, speedColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = speedText, color = speedColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }

                // RIP type
                source.ripType?.let { rip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = rip, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Korean
                if (source.hasKorean) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00B0FF).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF00B0FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "🇰🇷 KOREAN", color = Color(0xFF40C4FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Hindi
                if (source.hasHindi) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF5722).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFF5722).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "🇮🇳 HINDI", color = Color(0xFFFF7043), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Tamil
                if (source.hasTamil) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE91E63).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFE91E63).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "TAMIL", color = Color(0xFFF48FB1), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Telugu
                if (source.hasTelugu) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3F51B5).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF3F51B5).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "TELUGU", color = Color(0xFF9FA8DA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Malayalam
                if (source.hasMalayalam) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF009688).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF009688).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "MALAYALAM", color = Color(0xFF80CBC4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Kannada
                if (source.hasKannada) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF9C27B0).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF9C27B0).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "KANNADA", color = Color(0xFFCE93D8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bangla
                if (source.hasBengali) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF9800).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFF9800).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "BANGLA", color = Color(0xFFFFB74D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // English
                if (source.hasEnglish) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF2196F3).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "ENGLISH", color = Color(0xFF90CAF9), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Dual Audio
                if (source.isDualAudio) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00E5FF).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "🌐 DUAL AUDIO", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // HDR
                if (source.isHdr) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "✨ HDR", color = Color(0xFFFFD700), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // HEVC
                if (source.isHevc) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFAB47BC).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFAB47BC).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "⚡ HEVC", color = Color(0xFFCE93D8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Season Pack Badge
                if (isPack) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF6B00).copy(alpha = 0.18f))
                            .border(0.6.dp, Color(0xFFFF6B00).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(text = "📦 PACK", color = Color(0xFFFF9E00), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
                // Stream / Choose Episode Button
                if (isPack) {
                    Button(
                        onClick = onPackClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B00).copy(alpha = 0.18f),
                            contentColor = Color(0xFFFF9E00)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B00).copy(0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = "Episodes", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Choose Episode",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
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
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Stream", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "Stream Now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        if (isFinished) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = when {
                            isFinished -> "Downloaded"
                            isDownloading -> "${activeDownload.progressPercent}%"
                            else -> "Download"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Copy Magnet
                IconButton(
                    onClick = onCopyMagnet,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.glassBg)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Magnet", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }

                // Open External Client
                IconButton(
                    onClick = onOpenExternal,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.glassBg)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "External Client", tint = c.textSecondary, modifier = Modifier.size(16.dp))
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

@Composable
fun DownloadSiteButton(
    site: DownloadSite,
    isResolving: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !isResolving,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isResolving) Color(site.accentColorHex).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = modifier.height(42.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isResolving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = Color(site.accentColorHex),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Finding...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Image(
                    painter = painterResource(id = site.logoResId),
                    contentDescription = site.name,
                    modifier = Modifier
                        .height(21.dp)
                        .padding(horizontal = 8.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun DownloadSitesFooter(
    resolvingSiteId: String?,
    downloadSiteNotice: String?,
    onSiteClick: (DownloadSite) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1017))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CAN'T FIND?",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            DownloadSitesManager.SITES.forEach { site ->
                DownloadSiteButton(
                    site = site,
                    isResolving = resolvingSiteId == site.id,
                    enabled = resolvingSiteId == null,
                    modifier = Modifier.weight(1f),
                    onClick = { onSiteClick(site) }
                )
            }
        }

        if (!downloadSiteNotice.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = downloadSiteNotice,
                color = Color(0xFFFFB300),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun isPackSource(title: String): Boolean {
    val t = title.lowercase()
    return t.contains("pack") || t.contains("complete") || t.contains("batch") ||
            t.contains("s01-") || t.contains("season 1-") || t.contains("s01 -") ||
            t.contains("seasons") || t.contains("s1-") || t.contains("season 1 ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackEpisodePickerBottomSheet(
    source: TorrentSource,
    item: TmdbItem,
    onDismiss: () -> Unit,
    onSelectEpisode: (TorrentSource, TorrentEngine.TorrentFileInfo) -> Unit
) {
    val context = LocalContext.current
    val hash = remember(source.magnet) {
        Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
            .find(source.magnet)?.groupValues?.getOrNull(1)
            ?.let(TorrentEngine::normalizeInfoHash).orEmpty()
    }

    var videoFiles by remember { mutableStateOf<List<TorrentEngine.TorrentFileInfo>>(emptyList()) }
    var secondsWaiting by remember { mutableIntStateOf(0) }
    var metadataTimedOut by remember { mutableStateOf(false) }
    val selectionHandoff = remember(hash) { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(hash) {
        onDispose {
            if (hash.isNotBlank() && !selectionHandoff.get()) {
                TorrentEngine.cleanupStream(context, hash)
            }
        }
    }

    LaunchedEffect(hash) {
        if (hash.isBlank()) {
            metadataTimedOut = true
            return@LaunchedEffect
        }

        val startedHash = TorrentEngine.startStream(context, source, item.displayName)
        if (startedHash == null) {
            metadataTimedOut = true
            return@LaunchedEffect
        }

        for (attempt in 1..50) {
            kotlinx.coroutines.delay(600)
            secondsWaiting = attempt
            val files = TorrentEngine.getVideoFiles(startedHash)
            if (files.isNotEmpty()) {
                videoFiles = files.sortedWith(
                    compareBy<TorrentEngine.TorrentFileInfo> {
                        val epNum = Regex("""[Ee](\d{1,2})""").find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: 999
                        epNum
                    }.thenBy { it.name }
                )
                return@LaunchedEffect
            }
        }
        metadataTimedOut = true
        TorrentEngine.cleanupStream(context, startedHash)
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10121C),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "📦 SEASON PACK EPISODES",
                        color = Color(0xFFFF9E00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Choose Episode to Stream",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (videoFiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (metadataTimedOut) {
                        Text(
                            text = "Could not read the episode list. Try another source or close this picker.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    } else {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = Color(0xFFFF6B00),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Connecting to swarm & reading episode list...",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Metadata loading ($secondsWaiting s)",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "${videoFiles.size} Episodes Found in Pack",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isLandscape) 200.dp else 420.dp)
                ) {
                    items(videoFiles) { f ->
                        val sMatch = Regex("""(?i)[Ss](\d{1,2})[Ee](\d{1,2})""").find(f.name)
                        val xMatch = Regex("""(?i)(\d{1,2})x(\d{1,2})""").find(f.name)
                        val epMatch = Regex("""(?i)(?:ep|episode)\.?\s*(\d{1,2})""").find(f.name)
                        val epLabel = when {
                            sMatch != null -> "S${sMatch.groupValues[1]} E${sMatch.groupValues[2]}"
                            xMatch != null -> "S${xMatch.groupValues[1]} E${xMatch.groupValues[2]}"
                            epMatch != null -> "EP ${epMatch.groupValues[1].padStart(2, '0')}"
                            else -> "Episode ${f.index + 1}"
                        }

                        Surface(
                            onClick = {
                                selectionHandoff.set(true)
                                onSelectEpisode(source, f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF171926),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFF6B00).copy(alpha = 0.15f),
                                        border = BorderStroke(0.8.dp, Color(0xFFFF6B00).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = epLabel,
                                            color = Color(0xFFFF9E00),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = f.name,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = f.sizeFormatted,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                Button(
                                    onClick = {
                                        selectionHandoff.set(true)
                                        onSelectEpisode(source, f)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF6B00),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stream", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
