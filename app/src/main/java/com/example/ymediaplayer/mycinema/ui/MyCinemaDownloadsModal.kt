package com.example.ymediaplayer.mycinema.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.ymediaplayer.data.VideoRepository
import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.theme.LocalAppColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedFileItem(
    val file: File,
    val name: String,
    val sizeFormatted: String,
    val dateFormatted: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCinemaDownloadsModal(
    onDismiss: () -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current

    val downloadsMap by TorrentEngine.downloads.collectAsState()
    val activeDownloads = remember(downloadsMap) {
        downloadsMap.values.filter {
            it.status == TorrentDownloadState.Status.DOWNLOADING ||
            it.status == TorrentDownloadState.Status.STREAMING ||
            it.status == TorrentDownloadState.Status.CONNECTING ||
            it.status == TorrentDownloadState.Status.PAUSED
        }
    }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var downloadedFiles by remember { mutableStateOf<List<DownloadedFileItem>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<DownloadedFileItem?>(null) }

    BackHandler {
        onDismiss()
    }

    // Scan downloaded files on disk
    LaunchedEffect(refreshTrigger, downloadsMap) {
        val dir = TorrentEngine.getDownloadDir(context)
        val files = mutableListOf<DownloadedFileItem>()
        val inProgressPaths = activeDownloads.mapNotNull { it.savePath }.toSet()
        val inProgressIds = activeDownloads.map { it.id.lowercase() }.toSet()
        if (dir.exists() && dir.isDirectory) {
            dir.walkTopDown().filter { file ->
                file.isFile && file.length() > 0 && isVideoFile(file.name) &&
                TorrentEngine.getActiveTorrent(file.absolutePath) == null &&
                !inProgressPaths.contains(file.absolutePath) &&
                !inProgressIds.any { file.name.lowercase().contains(it) }
            }.forEach { file ->
                val sizeStr = formatFileSize(file.length())
                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
                files.add(
                    DownloadedFileItem(
                        file = file,
                        name = file.nameWithoutExtension,
                        sizeFormatted = sizeStr,
                        dateFormatted = dateStr
                    )
                )
            }
        }
        downloadedFiles = files.sortedByDescending { it.file.lastModified() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.cardBgElevated,
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🍿 MYCinema Downloads",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = c.glassBorder, thickness = 1.dp)

            if (activeDownloads.isEmpty() && downloadedFiles.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            contentDescription = null,
                            tint = c.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No downloads yet",
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Downloaded or streaming movies from MYCinema\nwill appear here for instant offline playback.",
                            color = c.textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Active Downloads Section
                    if (activeDownloads.isNotEmpty()) {
                        item {
                            Text(
                                text = "ACTIVE DOWNLOADS (${activeDownloads.size})",
                                color = c.accentBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        items(activeDownloads, key = { it.id }) { dl ->
                            ActiveDownloadCard(
                                download = dl,
                                onPause = { TorrentEngine.pause(dl.id) },
                                onResume = { TorrentEngine.resume(dl.id) },
                                onCancel = {
                                    TorrentEngine.cancel(dl.id)
                                },
                                onPlay = {
                                    TorrentEngine.setSequential(dl.id, true)
                                    val path = TorrentEngine.getFilePath(context, dl.id)
                                    if (path != null && TorrentEngine.isStreamBufferReady(context, dl.id)) {
                                        onPlayVideo(path)
                                    } else {
                                        Toast.makeText(context, "Buffering initial chunks... Playback will begin shortly", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    // Completed Downloads Section
                    if (downloadedFiles.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "DOWNLOADED MOVIES (${downloadedFiles.size})",
                                color = c.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        items(downloadedFiles, key = { it.file.absolutePath }) { item ->
                            DownloadedMovieCard(
                                item = item,
                                onPlay = { onPlayVideo(item.file.absolutePath) },
                                onDelete = { fileToDelete = item },
                                onShare = {
                                    try {
                                        val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            item.file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not share video: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    fileToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Download?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${target.name}\"? This file will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            target.file.delete()
                            VideoRepository.invalidateCache()
                            refreshTrigger++
                            Toast.makeText(context, "Movie deleted", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                        fileToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = c.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    download: TorrentDownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onPlay: () -> Unit
) {
    val c = LocalAppColors.current
    val isPaused = download.status == TorrentDownloadState.Status.PAUSED

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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = download.title,
                    color = c.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPaused) Color(0xFFFF9800).copy(0.18f) else Color(0xFF00E676).copy(0.18f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPaused) "PAUSED" else "DOWNLOADING",
                        color = if (isPaused) Color(0xFFFF9800) else Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { download.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isPaused) Color(0xFFFF9800) else c.accentBlue,
                trackColor = c.glassBorder
            )

            Spacer(Modifier.height(8.dp))

            // Speed & Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⬇ ${download.speedFormatted} • ${download.sizeFormatted} (${download.progressPercent}%)",
                        color = c.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${download.etaFormatted} • ${download.seeds} seeds",
                        color = c.textSecondary,
                        fontSize = 11.sp
                    )
                }

                // Actions: Pause/Resume, Play, Cancel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Play if buffer ready
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = c.accentBlue, modifier = Modifier.size(20.dp))
                    }

                    // Pause / Resume
                    IconButton(
                        onClick = { if (isPaused) onResume() else onPause() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = "Pause",
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Cancel
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedMovieCard(
    item: DownloadedFileItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val c = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardBg)
            .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.cardBgElevated)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Play overlay icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title & Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = c.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.sizeFormatted} • ${item.dateFormatted}",
                    color = c.textSecondary,
                    fontSize = 11.5.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Action Buttons: Play, Delete, Share
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = "Play", tint = c.accentBlue, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun isVideoFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return ext in setOf("mp4", "mkv", "avi", "webm", "mov", "ts", "m4v")
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
