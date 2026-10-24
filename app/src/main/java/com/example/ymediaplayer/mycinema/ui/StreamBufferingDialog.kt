package com.example.ymediaplayer.mycinema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun StreamBufferingDialog(
    hash: String,
    title: String,
    posterUrl: String?,
    onStreamReady: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val c = LocalAppColors.current

    val downloads by TorrentEngine.downloads.collectAsState()
    val downloadState = downloads[hash]

    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var requiredBytes by remember { mutableLongStateOf(TorrentEngine.DEFAULT_STREAM_BUFFER_BYTES) }
    var secondsWaiting by remember { mutableIntStateOf(0) }

    LaunchedEffect(hash) {
        var elapsed = 0
        while (true) {
            delay(500)
            elapsed += 500
            secondsWaiting = elapsed / 1000
            downloadedBytes = TorrentEngine.getDownloadedBytes(hash)
            requiredBytes = TorrentEngine.getRequiredBufferBytes(hash)

            if (TorrentEngine.isStreamBufferReady(context, hash)) {
                val path = TorrentEngine.getFilePath(context, hash)
                if (path != null) {
                    onStreamReady(path)
                    break
                }
            }
        }
    }

    val progress = (downloadedBytes.toFloat() / requiredBytes.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val downloadedMb = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024.0 * 1024.0))
    val requiredMb = String.format(java.util.Locale.US, "%.1f", requiredBytes / (1024.0 * 1024.0))
    val percent = (progress * 100).toInt().coerceIn(0, 100)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.cardBgElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Title & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🍿 Preparing Stream",
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Poster & Title Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 52.dp, height = 75.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, c.glassBorder, RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = c.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Downloading stream buffer...",
                            color = c.accentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (downloadState != null && downloadState.downloadSpeed > 0)
                                "⚡ ${downloadState.speedFormatted} • ${downloadState.seeds} seeds • ${downloadState.etaFormatted}"
                            else if (downloadState != null && downloadState.seeds > 0)
                                "Connecting to ${downloadState.seeds} seeds..."
                            else "Finding peers & trackers...",
                            color = c.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = c.accentBlue,
                    trackColor = c.glassBorder
                )

                Spacer(Modifier.height(8.dp))

                // Progress Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$downloadedMb MB / $requiredMb MB",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$percent%",
                        color = c.accentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Stalled / Slow Source Detection Banner
                val isStalled = secondsWaiting >= 12 && downloadedBytes == 0L && (downloadState?.downloadSpeed ?: 0L) == 0L
                if (isStalled) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Slow or Stalled Source ($secondsWaiting s)",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "This source has few active seeders. We recommend trying another source with more seeds.",
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Action / Cancel Button
                if (isStalled) {
                    Button(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Try Another Source", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text("Cancel Streaming", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
