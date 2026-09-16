package com.example.ymediaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.update.ReleaseInfo
import com.example.ymediaplayer.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences(context) }
    val c = LocalAppColors.current
    val primaryAccent = c.accentBlue
    val currentVersion = UpdateManager.getCurrentVersionName(context)

    val isDownloading = UpdateManager.isDownloading.value
    val progress = UpdateManager.downloadProgress.floatValue
    val statusText = UpdateManager.downloadStatusText.value
    val errorMsg = UpdateManager.errorMessage.value

    Dialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.cardBg)
                .border(1.2.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ─── Header Icon with Ambient Glow ───────────────────────────
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(primaryAccent, primaryAccent.copy(alpha = 0.75f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (progress >= 1f) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                        contentDescription = "Update Available",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Update Available!",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )

                Spacer(Modifier.height(6.dp))

                // ─── Version Badge ────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryAccent.copy(alpha = 0.12f))
                        .border(1.dp, primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v",
                        color = c.textSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "→",
                        color = primaryAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v",
                        color = primaryAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ─── Changelog Box ────────────────────────────────────────────
                if (release.changelog.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.cardBgElevated)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "What's New:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = release.changelog.trim(),
                                fontSize = 12.5.sp,
                                color = c.textPrimary.copy(alpha = 0.85f),
                                lineHeight = 17.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ─── Downloading State / Error ────────────────────────────────
                if (isDownloading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = primaryAccent,
                            trackColor = c.glassBorder
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.5.sp,
                            color = c.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                } else if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ─── Action Buttons ───────────────────────────────────────────
                if (!isDownloading) {
                    Button(
                        onClick = {
                            scope.launch {
                                UpdateManager.downloadAndInstall(context, release)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Update Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                UpdateManager.skipVersion(appPreferences, release.tagName)
                            }
                        ) {
                            Text(
                                text = "Skip this version",
                                fontSize = 11.5.sp,
                                color = c.textSecondary
                            )
                        }

                        TextButton(
                            onClick = {
                                UpdateManager.dismissUpdate()
                            }
                        ) {
                            Text(
                                text = "Remind me later",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateHistoryDialog(
    onDismiss: () -> Unit,
    onInstallRelease: (ReleaseInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = LocalAppColors.current
    val primaryAccent = c.accentBlue
    val emeraldGreen = Color(0xFF10B981)
    val currentVersion = UpdateManager.getCurrentVersionName(context)

    val history = UpdateManager.releaseHistory.value
    val isLoading = UpdateManager.isLoadingHistory.value

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (history.isEmpty()) {
            UpdateManager.fetchReleaseHistory(context)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(c.cardBg)
                .border(1.2.dp, c.glassBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(emeraldGreen.copy(alpha = 0.15f))
                                .border(1.dp, emeraldGreen.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = emeraldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Update History",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            Text(
                                text = "Currently installed: v$currentVersion",
                                fontSize = 11.5.sp,
                                color = c.textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = c.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.glassBorder, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                if (isLoading && history.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = emeraldGreen,
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Fetching release history...",
                                color = c.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (history.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No release history found",
                                color = c.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Current version: v$currentVersion",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        UpdateManager.fetchReleaseHistory(context)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen)
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(history) { release ->
                            val isCurrent = release.versionName == currentVersion
                            val isNewer = UpdateManager.isNewer(release.versionName, currentVersion)

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = c.cardBgElevated),
                                border = BorderStroke(
                                    1.dp,
                                    if (isNewer) emeraldGreen.copy(alpha = 0.5f)
                                    else if (isCurrent) primaryAccent.copy(alpha = 0.45f)
                                    else c.glassBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "v${release.versionName}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isNewer) emeraldGreen else if (isCurrent) primaryAccent else c.textPrimary,
                                                maxLines = 1,
                                                softWrap = false
                                            )

                                            if (isNewer) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(emeraldGreen.copy(alpha = 0.18f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Update Available",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = emeraldGreen
                                                    )
                                                }
                                            } else if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(primaryAccent.copy(alpha = 0.18f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Current",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryAccent
                                                    )
                                                }
                                            }
                                        }

                                        if (release.publishedAt.isNotBlank()) {
                                            val dateStr = release.publishedAt.take(10)
                                            Text(
                                                text = dateStr,
                                                fontSize = 11.5.sp,
                                                color = c.textSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    if (release.title.isNotBlank() && release.title != "Version ${release.versionName}" && release.title != release.tagName) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = release.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = c.textSecondary
                                        )
                                    }

                                    if (release.changelog.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(c.cardBg)
                                                .border(0.8.dp, c.glassBorder, RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = release.changelog.trim(),
                                                fontSize = 12.sp,
                                                color = c.textPrimary.copy(alpha = 0.85f),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    if (isNewer) {
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onInstallRelease(release)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp).align(Alignment.End)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Install v${release.versionName}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
