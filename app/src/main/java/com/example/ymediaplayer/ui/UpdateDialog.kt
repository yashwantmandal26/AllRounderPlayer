package com.example.ymediaplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.update.ReleaseInfo
import com.example.ymediaplayer.update.UpdateManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun UpdateDialog(release: ReleaseInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val downloading = UpdateManager.isDownloading.value
    val configuration = LocalConfiguration.current
    Dialog(onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = !downloading, dismissOnClickOutside = !downloading)) {
        UpdateDialogContent(release, UpdateManager.getCurrentVersionName(context),
            isDownloading = downloading, progress = UpdateManager.downloadProgress.floatValue,
            statusText = UpdateManager.downloadStatusText.value,
            errorMessage = UpdateManager.errorMessage.value,
            readyToInstall = UpdateManager.readyToInstallTag.value == release.tagName,
            onDownload = { scope.launch { UpdateManager.downloadAndInstall(context, release) } },
            onLater = { UpdateManager.dismissUpdate(context); onDismiss() },
            onSkip = { UpdateManager.skipVersion(preferences, release.tagName, context); onDismiss() },
            onClose = { UpdateManager.dismissUpdate(context); onDismiss() },
            modifier = Modifier.padding(16.dp).heightIn(max = (configuration.screenHeightDp * 0.9f).dp))
    }
}

@Composable
internal fun UpdateDialogContent(
    release: ReleaseInfo, currentVersion: String,
    isDownloading: Boolean = false, progress: Float = 0f,
    statusText: String = "", errorMessage: String? = null,
    readyToInstall: Boolean = false,
    onDownload: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val scroll = rememberScrollState()
    Surface(modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("update-dialog"),
        shape = RoundedCornerShape(28.dp), color = c.cardBg,
        border = BorderStroke(1.dp, c.glassBorder)) {
        Column {
            // Keep the action area visible while long release notes scroll independently.
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).testTag("update-release-notes")) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
                    listOf(c.accentBlue.copy(alpha = 0.16f), c.cardBg))).padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(16.dp), color = c.accentBlue.copy(alpha = 0.16f)) {
                            Icon(Icons.Rounded.SystemUpdate, null, tint = c.accentBlue,
                                modifier = Modifier.padding(12.dp).size(28.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onClose, enabled = !isDownloading) {
                            Icon(Icons.Rounded.Close, "Close update", tint = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("A better player awaits", color = c.textPrimary, fontSize = 24.sp,
                        lineHeight = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text("Update AllRounder Player", color = c.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        VersionTile("Installed", currentVersion, Modifier.weight(1f), false)
                        Icon(Icons.Rounded.ArrowForward, null, tint = c.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp).size(20.dp))
                        VersionTile("New version", release.versionName, Modifier.weight(1f), true)
                    }
                    val metadata = listOfNotNull(formatReleaseDate(release.publishedAt).takeIf { it.isNotEmpty() },
                        if (release.fileSize > 0) formatSize(release.fileSize) else null).joinToString("  ·  ")
                    if (metadata.isNotEmpty()) Text(metadata, color = c.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp))
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    Text("What's new", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(12.dp))
                    ReleaseNotesContent(release.changelog.ifBlank { "Performance improvements and bug fixes." })
                }
            }
            Column(Modifier.background(c.cardBgElevated).padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (isDownloading) {
                    val knownProgress = progress >= 0f
                    val bounded = progress.coerceIn(0f, 1f)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (bounded >= 1f) "Verifying update…" else "Downloading update…",
                            color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        if (knownProgress) Text("${(bounded * 100).toInt()}%", color = c.accentBlue,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    val bar = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).testTag("update-progress")
                    if (knownProgress) LinearProgressIndicator(progress = { bounded }, modifier = bar,
                        color = c.accentBlue, trackColor = c.glassBorder)
                    else LinearProgressIndicator(modifier = bar, color = c.accentBlue, trackColor = c.glassBorder)
                    Text(statusText.ifBlank { "Connecting to update server…" }, color = c.textSecondary,
                        fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    if (!errorMessage.isNullOrBlank()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.heightIn(max = 76.dp).verticalScroll(rememberScrollState())
                                .padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    } else if (readyToInstall) {
                        Text(statusText.ifBlank { "Download complete. Ready to install." }, color = c.textSecondary,
                            fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        .testTag("update-download"), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = c.onAccent)) {
                        Icon(if (readyToInstall) Icons.Rounded.SystemUpdate else Icons.Rounded.Download, null,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(when { !errorMessage.isNullOrBlank() -> "Try again"
                            readyToInstall -> "Install update"
                            else -> "Download & install" }, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onSkip) { Text("Skip version", color = c.textSecondary, fontSize = 12.sp) }
                        TextButton(onClick = onLater) { Text("Not now", color = c.accentBlue, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionTile(label: String, version: String, modifier: Modifier, available: Boolean) {
    val c = LocalAppColors.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.cardBgElevated).padding(12.dp)) {
        Text(label, color = c.textSecondary, fontSize = 11.sp)
        Text(updateVersionLabel(version), color = if (available) c.accentBlue else c.textPrimary,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).testTag(if (available) "update-release-version" else "update-installed-version"))
    }
}

internal fun updateVersionLabel(version: String): String = version.trim().removePrefix("v").removePrefix("V")
    .let { if (it.isBlank()) "—" else "v$it" }

internal fun formatReleaseDate(raw: String): String = runCatching {
    if (raw.isBlank()) return ""
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
    val date = parser.parse(raw.take(10)) ?: return ""
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}.getOrDefault("")

@Composable
fun UpdateHistoryDialog(onDismiss: () -> Unit, onInstallRelease: (ReleaseInfo) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = LocalAppColors.current
    val history = UpdateManager.releaseHistory.value
    val loading = UpdateManager.isLoadingHistory.value
    val error = UpdateManager.historyError.value
    val current = UpdateManager.getCurrentVersionName(context)
    LaunchedEffect(Unit) { if (history.isEmpty()) UpdateManager.fetchReleaseHistory(context) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp), color = c.cardBg, border = BorderStroke(1.dp, c.glassBorder)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Release history", color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() })
                        Text("Installed ${updateVersionLabel(current)}", color = c.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close history", tint = c.textSecondary) }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = c.glassBorder)
                if (loading && history.isEmpty()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = c.accentBlue)
                        Text("Loading releases…", color = c.textSecondary, modifier = Modifier.padding(top = 16.dp))
                    }
                } else if (history.isEmpty()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.History, null, tint = c.textSecondary, modifier = Modifier.size(40.dp))
                        Text(if (error == null) "No releases yet" else "Couldn't load releases",
                            color = c.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                        if (error != null) Text(error, color = c.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = { scope.launch { UpdateManager.fetchReleaseHistory(context) } }, enabled = !loading) {
                            Text("Try again", color = c.accentBlue)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(history, key = { _, release -> release.tagName }) { index, release ->
                            ReleaseHistoryCard(release, current, initiallyExpanded = index == 0,
                                onInstall = { onDismiss(); onInstallRelease(release) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseHistoryCard(release: ReleaseInfo, current: String, initiallyExpanded: Boolean, onInstall: () -> Unit) {
    val c = LocalAppColors.current
    var expanded by rememberSaveable(release.tagName) { mutableStateOf(initiallyExpanded) }
    val newer = UpdateManager.isNewer(release.versionName, current)
    val installed = updateVersionLabel(release.versionName) == updateVersionLabel(current)
    Surface(shape = RoundedCornerShape(18.dp), color = c.cardBgElevated,
        border = BorderStroke(1.dp, if (newer) c.accentBlue.copy(alpha = 0.4f) else c.glassBorder)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(updateVersionLabel(release.versionName), color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(if (installed) "Currently installed" else if (newer) "Update available" else "Previous release",
                        color = if (newer || installed) c.accentBlue else c.textSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        if (expanded) "Hide ${release.versionName} details" else "Show ${release.versionName} details", tint = c.textSecondary)
                }
            }
            val date = formatReleaseDate(release.publishedAt)
            if (date.isNotEmpty()) Text(date, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = c.glassBorder)
                ReleaseNotesContent(release.changelog.ifBlank { "Performance improvements and bug fixes." }, compact = true)
                if (newer && release.apkDownloadUrl.isNotBlank()) {
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accentBlue, contentColor = c.onAccent)) {
                        Text("View update", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
