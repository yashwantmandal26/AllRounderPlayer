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
