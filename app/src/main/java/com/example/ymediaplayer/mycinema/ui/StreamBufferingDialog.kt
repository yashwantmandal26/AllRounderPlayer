package com.example.ymediaplayer.mycinema.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.ymediaplayer.mycinema.service.TorrentEngine
import com.example.ymediaplayer.mycinema.service.TorrentDownloadState
import kotlinx.coroutines.delay

@Composable
fun StreamBufferingDialog(
    hash: String,
    title: String,
    posterUrl: String?,
    backdropUrl: String? = null,
    logoUrl: String? = null,
    onStreamReady: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

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
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "dialogProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "dialogLupinGlow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dialogPulseScale"
    )

    val downloadedMb = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024.0 * 1024.0))
    val requiredMb = String.format(java.util.Locale.US, "%.1f", requiredBytes / (1024.0 * 1024.0))
    val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF080D16),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Blurred backdrop background
                val effectiveBg = backdropUrl?.takeIf { it.isNotBlank() } ?: posterUrl?.takeIf { it.isNotBlank() }
                if (effectiveBg != null) {
                    AsyncImage(
                        model = effectiveBg,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.18f)
                            .blur(28.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top header: Badge and Cancel X
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6B00))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "STREAMING CINEMA",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Cancel",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Title / Logo with horizontal clip reveal fill
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = title,
                                modifier = Modifier
                                    .heightIn(min = 48.dp, max = 80.dp)
                                    .fillMaxWidth(0.8f)
                                    .alpha(0.22f),
                                contentScale = ContentScale.Fit
                            )
                            Box(
                                modifier = Modifier.drawWithContent {
                                    clipRect(right = size.width * animatedProgress) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            ) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = title,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp, max = 80.dp)
                                        .fillMaxWidth(0.8f),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Text(
                                text = title.uppercase(),
                                color = Color.White.copy(alpha = 0.22f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier.drawWithContent {
                                    clipRect(right = size.width * animatedProgress) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            ) {
                                Text(
                                    text = title.uppercase(),
                                    color = Color(0xFFFF6B00),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Sleek Orange to Gold Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF6B00), Color(0xFFFF9E00), Color(0xFFFFD54F))
                                    )
                                )
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Telemetry row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color(0xFFFF9E00), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = downloadState?.speedFormatted ?: "0 KB/s",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${downloadState?.seeds ?: 0} Seeds",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "$downloadedMb / $requiredMb MB ($percent%)",
                            color = Color(0xFFFF9E00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Stalled / Slow Source Warning
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
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Slow or Stalled Swarm ($secondsWaiting s)", color = Color(0xFFFFB74D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text("This source has few active seeders. We recommend trying another source with more seeds.", color = Color.White.copy(alpha = 0.7f), fontSize = 10.5.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Action Button
                    if (isStalled) {
                        Button(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Try Another Source", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Cancel Streaming", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
