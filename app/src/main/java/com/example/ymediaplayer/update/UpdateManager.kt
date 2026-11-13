package com.example.ymediaplayer.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.ymediaplayer.data.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val fileSize: Long,
    val publishedAt: String
)

object UpdateManager {

    private const val GITHUB_REPO_OWNER = "yashwantmandal26"
    private const val GITHUB_REPO_NAME = "AllRounderPlayer"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/yashwantmandal26/AllRounderPlayer/releases/latest"
    private const val ALL_RELEASES_API = "https://api.github.com/repos/yashwantmandal26/AllRounderPlayer/releases"
    private const val LATEST_RELEASE_WEB_URL = "https://github.com/yashwantmandal26/AllRounderPlayer/releases/latest"
    private const val RELEASES_ATOM_URL = "https://github.com/yashwantmandal26/AllRounderPlayer/releases.atom"
    private const val AUTO_RECHECK_INTERVAL_MS = 15L * 60L * 1000L
    private const val FAILED_RETRY_COOLDOWN_MS = 20_000L

    // ─── Reactive Compose State ───────────────────────────────────────────────
    val availableUpdate = mutableStateOf<ReleaseInfo?>(null)
    val showUpdateDialog = mutableStateOf(false)
    val releaseHistory = mutableStateOf<List<ReleaseInfo>>(emptyList())
    val isLoadingHistory = mutableStateOf(false)
    val isChecking = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableFloatStateOf(0f)
    val downloadStatusText = mutableStateOf("")
    val errorMessage = mutableStateOf<String?>(null)
    val historyError = mutableStateOf<String?>(null)
    val readyToInstallTag = mutableStateOf<String?>(null)
    private val downloadMutex = Mutex()
    private val checkMutex = Mutex()
    @Volatile
    private var lastSuccessfulAutoCheckMs = 0L
    @Volatile
    private var lastFailedAutoCheckMs = 0L
    @Volatile
    private var dismissedTagThisSession: String? = null

    private const val UPDATE_CHANNEL_ID = "app_updates_channel"
    private const val UPDATE_NOTIFICATION_ID = 9001

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    internal enum class AutoCheckDecision {
        FETCH_NOW,
        USE_CACHED_RESULT,
        SKIP_COOLDOWN
    }

    internal fun evaluateAutoCheck(
        autoUpdatesEnabled: Boolean,
        isManual: Boolean,
        nowElapsedMs: Long,
        lastSuccessMs: Long,
        lastFailureMs: Long
    ): AutoCheckDecision {
        if (isManual) return AutoCheckDecision.FETCH_NOW
        if (!autoUpdatesEnabled) return AutoCheckDecision.SKIP_COOLDOWN
        if (lastSuccessMs > 0L && (nowElapsedMs - lastSuccessMs) in 0L until AUTO_RECHECK_INTERVAL_MS) {
            return AutoCheckDecision.USE_CACHED_RESULT
        }
        if (lastFailureMs > 0L && (nowElapsedMs - lastFailureMs) in 0L until FAILED_RETRY_COOLDOWN_MS) {
            return AutoCheckDecision.SKIP_COOLDOWN
        }
        return AutoCheckDecision.FETCH_NOW
    }

    internal fun resetAutoCheckStateForTest() {
        lastSuccessfulAutoCheckMs = 0L
        lastFailedAutoCheckMs = 0L
        dismissedTagThisSession = null
    }

    /**
     * Checks GitHub for the latest release.
     * @param isManual true when user clicked "Check for Updates" in Settings, false on startup/resume.
     */
    suspend fun checkForUpdates(
        context: Context,
        appPreferences: AppPreferences,
        isManual: Boolean = false
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val decision = evaluateAutoCheck(
            autoUpdatesEnabled = appPreferences.isAutoCheckUpdatesEnabled(),
            isManual = isManual,
            nowElapsedMs = nowElapsed,
            lastSuccessMs = lastSuccessfulAutoCheckMs,
            lastFailureMs = lastFailedAutoCheckMs
        )
        when (decision) {
            AutoCheckDecision.SKIP_COOLDOWN -> return@withContext Result.success(null)
            AutoCheckDecision.USE_CACHED_RESULT -> {
                val cached = availableUpdate.value
                val currentVersionName = getCurrentVersionName(context)
                if (cached != null && isNewer(cached.versionName, currentVersionName) &&
                    !isTagSkipped(appPreferences, cached.tagName) &&
                    dismissedTagThisSession != cached.tagName
                ) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog.value = true
                    }
                }
                return@withContext Result.success(cached)
            }
            AutoCheckDecision.FETCH_NOW -> Unit
        }

        if (!checkMutex.tryLock()) {
            return@withContext Result.success(availableUpdate.value)
        }

        val now = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            isChecking.value = true
            if (isManual) errorMessage.value = null
        }

        try {
            val remoteRelease = fetchLatestReleaseWithFallback()

            lastSuccessfulAutoCheckMs = android.os.SystemClock.elapsedRealtime()
            lastFailedAutoCheckMs = 0L
            appPreferences.setLastUpdateCheckTime(now)

            if (remoteRelease == null) {
                withContext(Dispatchers.Main) {
                    availableUpdate.value = null
                    isChecking.value = false
                }
                return@withContext Result.success(null)
            }

            val currentVersionName = getCurrentVersionName(context)
            if (isNewer(remoteRelease.versionName, currentVersionName)) {
                val isSkipped = !isManual && isTagSkipped(appPreferences, remoteRelease.tagName)
                val isDismissedNow = !isManual && dismissedTagThisSession == remoteRelease.tagName
                if (isManual) {
                    dismissedTagThisSession = null
                }

                withContext(Dispatchers.Main) {
                    availableUpdate.value = remoteRelease
                    isChecking.value = false
                    if (!isSkipped && !isDismissedNow) {
                        showUpdateDialog.value = true
                        postUpdateNotification(context, remoteRelease)
                    }
                }
                return@withContext Result.success(remoteRelease)
            } else {
                withContext(Dispatchers.Main) {
                    availableUpdate.value = null
                    showUpdateDialog.value = false
                    isChecking.value = false
                }
                return@withContext Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isManual) {
                lastFailedAutoCheckMs = android.os.SystemClock.elapsedRealtime()
            }
            withContext(Dispatchers.Main) {
                isChecking.value = false
                if (isManual) errorMessage.value = e.message ?: "Failed to check for updates"
            }
            return@withContext Result.failure(e)
        } finally {
            checkMutex.unlock()
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { isChecking.value = false }
        }
    }

    private fun isTagSkipped(appPreferences: AppPreferences, tagName: String): Boolean {
        val skippedTag = appPreferences.getSkippedUpdateTag() ?: return false
        return skippedTag == tagName || cleanVersion(skippedTag) == cleanVersion(tagName)
    }

    private fun fetchLatestReleaseWithFallback(): ReleaseInfo? {
        var primaryError: Exception? = null
        // 1. Primary: GitHub Releases REST API
        try {
            val (code, body) = httpGetNoCache(
                LATEST_RELEASE_API,
                acceptHeader = "application/vnd.github.v3+json"
            )
            if (code in 200..299 && body != null) {
                return parseLatestReleaseJson(body)
            }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return null
            }
            primaryError = IllegalStateException("GitHub API returned status $code")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            primaryError = e
        }

        // 2. Fallback 1: GitHub Releases Atom feed (not subject to REST API 60 req/hr rate limit)
        try {
            val (atomCode, atomBody) = httpGetNoCache(
                RELEASES_ATOM_URL,
                acceptHeader = "application/atom+xml, application/xml, text/xml"
            )
            if (atomCode in 200..299 && !atomBody.isNullOrBlank()) {
                val parsed = parseReleasesAtom(atomBody).firstOrNull()
                if (parsed != null) return parsed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        // 3. Fallback 2: GitHub web /releases/latest redirect -> /releases/tag/<tag>
        try {
            val redirectedTag = fetchTagFromLatestRedirect()
            if (!redirectedTag.isNullOrBlank()) {
                val ver = cleanVersion(redirectedTag)
                return ReleaseInfo(
                    tagName = redirectedTag,
                    versionName = ver,
                    title = "Version $ver",
                    changelog = "Performance improvements and bug fixes.",
                    apkDownloadUrl = defaultApkDownloadUrl(redirectedTag),
                    apkFileName = "app-release.apk",
                    fileSize = 0L,
                    publishedAt = ""
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        throw (primaryError ?: IllegalStateException("Unable to reach update server"))
    }

    private fun httpGetNoCache(urlString: String, acceptHeader: String): Pair<Int, String?> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                useCaches = false
                connectTimeout = 12000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "YMedia-Player-Android")
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
            code to body
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchTagFromLatestRedirect(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(LATEST_RELEASE_WEB_URL).openConnection() as HttpURLConnection).apply {
                useCaches = false
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 12000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "YMedia-Player-Android")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location").orEmpty()
                parseTagFromReleaseUrl(location)
            } else null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseTagFromReleaseUrl(url: String): String? {
        val marker = "/releases/tag/"
        val idx = url.indexOf(marker)
        if (idx < 0) return null
        return url.substring(idx + marker.length)
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore('/')
            .trim()
            .takeIf { it.isNotBlank() }
    }

    internal fun defaultApkDownloadUrl(tagName: String): String =
        "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/app-release.apk"

    internal fun parseLatestReleaseJson(jsonStr: String): ReleaseInfo? {
        val obj = jsonParser.parseToJsonElement(jsonStr).jsonObject
        val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (tagName.isBlank()) return null
        val isDraft = obj["draft"]?.jsonPrimitive?.booleanOrNull ?: false
        val isPre = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
        if (isDraft || isPre) return null

        val title = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            .ifBlank { "Bug fixes and performance enhancements." }
        val publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val remoteVersionName = cleanVersion(tagName)

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L

        val assets = obj["assets"]?.jsonArray
        if (assets != null) {
            for (el in assets) {
                val asset = el.jsonObject
                val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                    apkName = name
                    apkSize = asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    if (name.contains("release", ignoreCase = true)) break
                }
            }
        }

        if (apkUrl.isNullOrBlank()) {
            apkUrl = defaultApkDownloadUrl(tagName)
            apkName = "app-release.apk"
        }

        return ReleaseInfo(
            tagName = tagName,
            versionName = remoteVersionName,
            title = title.ifBlank { "Version $remoteVersionName" },
            changelog = body,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName ?: "YMedia_v${remoteVersionName}.apk",
            fileSize = apkSize,
            publishedAt = publishedAt
        )
    }

    internal fun parseReleaseHistoryJson(jsonStr: String): List<ReleaseInfo> {
        val jsonArray = jsonParser.parseToJsonElement(jsonStr).jsonArray
        val list = mutableListOf<ReleaseInfo>()
        for (el in jsonArray) {
            val obj = el.jsonObject
            val isDraft = obj["draft"]?.jsonPrimitive?.booleanOrNull ?: false
            val isPre = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isDraft || isPre) continue
            val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (tagName.isBlank()) continue
            val title = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
                .ifBlank { "Bug fixes and performance enhancements." }
            val publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val remoteVersionName = cleanVersion(tagName)

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = obj["assets"]?.jsonArray
            if (assets != null) {
                for (assetEl in assets) {
                    val asset = assetEl.jsonObject
                    val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                        apkName = name
                        apkSize = asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                        if (name.contains("release", ignoreCase = true)) break
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) {
                apkUrl = defaultApkDownloadUrl(tagName)
                apkName = "YMedia_${remoteVersionName}.apk"
            }

            list.add(
                ReleaseInfo(
                    tagName = tagName,
                    versionName = remoteVersionName,
                    title = title.ifBlank { "Version $remoteVersionName" },
                    changelog = body,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName ?: "YMedia_v${remoteVersionName}.apk",
                    fileSize = apkSize,
                    publishedAt = publishedAt
                )
            )
        }
        return list
    }

    internal fun parseReleasesAtom(xmlStr: String): List<ReleaseInfo> {
        val entries = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xmlStr)
        val result = mutableListOf<ReleaseInfo>()
        for (entryMatch in entries) {
            val block = entryMatch.groupValues[1]
            val href = Regex("<link[^>]*href=\"([^\"]+)\"").find(block)?.groupValues?.get(1).orEmpty()
            val tagName = parseTagFromReleaseUrl(href)
                ?: Regex("<id>[^<]*/([^/<]+)</id>").find(block)?.groupValues?.get(1)?.trim()
                ?: continue
            if (tagName.isBlank()) continue
            val rawTitle = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val title = decodeXmlEntities(rawTitle).ifBlank { "Version ${cleanVersion(tagName)}" }
            val updated = Regex("<updated>(.*?)</updated>").find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val rawContent = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1).orEmpty()
            val decodedHtml = decodeXmlEntities(rawContent)
            val changelog = decodedHtml
                .replace(Regex("(?i)</?(li|p|br|h[1-6]|div)[^>]*>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .ifBlank { "Bug fixes and performance enhancements." }
            val ver = cleanVersion(tagName)
            result.add(
                ReleaseInfo(
                    tagName = tagName,
                    versionName = ver,
                    title = title,
                    changelog = changelog,
                    apkDownloadUrl = defaultApkDownloadUrl(tagName),
                    apkFileName = "app-release.apk",
                    fileSize = 0L,
                    publishedAt = updated
                )
            )
        }
        return result
    }

    private fun decodeXmlEntities(input: String): String = input
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    /**
     * Downloads the APK file and triggers installation.
     */
    suspend fun downloadAndInstall(
        context: Context,
        release: ReleaseInfo,
        installationAllowed: () -> Boolean = { hasInstallPermission(context) }
    ) = withContext(Dispatchers.IO) {
        if (!downloadMutex.tryLock()) return@withContext
        var activeConnection: HttpURLConnection? = null
        var completed = false
        // Remote version names must never be interpreted as filesystem paths.
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val apkFile = File(targetDir, "YMedia_${release.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
        try {
            withContext(Dispatchers.Main) {
                isDownloading.value = true
                downloadProgress.floatValue = 0f
                downloadStatusText.value = "Starting download..."
                errorMessage.value = null
                readyToInstallTag.value = null
            }
            val installState = context.getSharedPreferences("update_install_state", Context.MODE_PRIVATE)
            if (installState.getString("ready_tag", null) == release.tagName && apkFile.exists() &&
                isValidApkArchive(apkFile) && isApkSignedByThisApp(context, apkFile)) {
                completed = true
                withContext(Dispatchers.Main) {
                    isDownloading.value = false
                    readyToInstallTag.value = release.tagName
                    downloadStatusText.value = "Ready to install"
                    triggerInstall(context, apkFile, installationAllowed)
                }
                return@withContext
            }
            if (apkFile.exists()) apkFile.delete()

            // Download supporting redirects (GitHub -> AWS S3)
            var currentUrl = release.apkDownloadUrl
            require(isAllowedDownloadUrl(currentUrl)) { "Update URL is not trusted" }
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                val u = URL(currentUrl)
                connection = (u.openConnection() as HttpURLConnection).apply {
                    useCaches = false
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "YMedia-Player-Android")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                activeConnection = connection
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (!newLocation.isNullOrEmpty() && redirects < 6) {
                        val redirectedUrl = URL(u, newLocation).toString()
                        require(isAllowedDownloadUrl(redirectedUrl)) { "Update redirect is not trusted" }
                        currentUrl = redirectedUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            if (connection.responseCode !in 200..299) {
                val responseCode = connection.responseCode
                connection.disconnect()
                throw IllegalStateException("Update server returned $responseCode")
            }

            val totalLength = connection.contentLengthLong.takeIf { it > 0 } ?: release.fileSize
            require(totalLength <= MAX_APK_BYTES || totalLength <= 0L) { "Update file is unexpectedly large" }
            withContext(Dispatchers.Main) { downloadProgress.floatValue = if (totalLength > 0) 0f else -1f }

            val buffer = ByteArray(32 * 1024)
            var downloaded = 0L
            var lastUpdateMs = 0L

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        downloaded += bytesRead
                        require(downloaded <= MAX_APK_BYTES) { "Update file is unexpectedly large" }
                        output.write(buffer, 0, bytesRead)

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMs > 100 || downloaded == totalLength) {
                            lastUpdateMs = now
                            val prog = if (totalLength > 0) (downloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else -1f
                            val downloadedMb = downloaded.toFloat() / (1024 * 1024)
                            val totalMb = totalLength.toFloat() / (1024 * 1024)

                            withContext(Dispatchers.Main) {
                                downloadProgress.floatValue = prog
                                downloadStatusText.value = if (totalLength > 0) {
                                    String.format(java.util.Locale.getDefault(), "%.1f MB / %.1f MB (%.0f%%)", downloadedMb, totalMb, prog * 100f)
                                } else {
                                    String.format(java.util.Locale.getDefault(), "%.1f MB downloaded", downloadedMb)
                                }
                            }
                        }
                    }
                }
            }
            connection.disconnect()
            require(downloaded > 0 && (totalLength <= 0 || downloaded == totalLength)) {
                "Download was interrupted. Please try again."
            }
            withContext(Dispatchers.Main) {
                downloadProgress.floatValue = 1f
                downloadStatusText.value = "Checking installation file…"
            }

            require(isValidApkArchive(apkFile)) { "Downloaded file is not a valid APK" }
            require(isApkSignedByThisApp(context, apkFile)) { "Update signature does not match this app" }
            @Suppress("DEPRECATION")
            val downloadedPackage = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            require(cleanVersion(downloadedPackage?.versionName.orEmpty()) == cleanVersion(release.versionName)) {
                "The installation file does not match this release. Please check again later."
            }

            context.getSharedPreferences("update_install_state", Context.MODE_PRIVATE).edit()
                .putString("ready_tag", release.tagName).putString("ready_path", apkFile.absolutePath).commit()
            completed = true
            withContext(Dispatchers.Main) {
                isDownloading.value = false
                readyToInstallTag.value = release.tagName
                downloadStatusText.value = "Ready to install"
                // Trigger the installation intent
                triggerInstall(context, apkFile, installationAllowed)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isDownloading.value = false
                errorMessage.value = "Download failed: ${e.message ?: "Network error"}"
            }
        } finally {
            activeConnection?.disconnect()
            if (!completed) apkFile.delete()
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { isDownloading.value = false }
            downloadMutex.unlock()
        }
    }

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    private fun hasInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun triggerInstall(context: Context, apkFile: File,
        installationAllowed: () -> Boolean = { hasInstallPermission(context) }) {
        if (!apkFile.exists()) {
            errorMessage.value = "Installation file not found"
            return
        }

        // On Android 8.0+ (Oreo, API 26+), check if unknown sources permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!installationAllowed()) {
                val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.getSharedPreferences("update_install_state", Context.MODE_PRIVATE).edit()
                    .putString("ready_path", apkFile.absolutePath).putBoolean("awaiting_permission", true).commit()
                downloadStatusText.value = "Allow installation, then return to continue"
                try { context.startActivity(permissionIntent) }
                catch (e: Exception) { errorMessage.value = "Could not open install permission: ${e.message}" }
                return
            }
        }

        try {
            context.getSharedPreferences("update_install_state", Context.MODE_PRIVATE).edit()
                .putBoolean("awaiting_permission", false).apply()
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            downloadStatusText.value = "Installer opened. If you cancel, tap Install update to try again."
        } catch (e: Exception) {
            errorMessage.value = "Failed to launch installer: ${e.message}"
        }
    }

    suspend fun resumePendingInstall(context: Context,
        installationAllowed: () -> Boolean = { hasInstallPermission(context) }) {
        val state = context.getSharedPreferences("update_install_state", Context.MODE_PRIVATE)
        if (!state.getBoolean("awaiting_permission", false)) return
        if (!installationAllowed()) return
        val path = state.getString("ready_path", null) ?: return
        val file = File(path)
        val valid = withContext(Dispatchers.IO) {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            runCatching {
                file.parentFile?.canonicalFile == directory.canonicalFile && file.exists() &&
                    isValidApkArchive(file) && isApkSignedByThisApp(context, file)
            }.getOrDefault(false)
        }
        state.edit().putBoolean("awaiting_permission", false).apply()
        if (valid) triggerInstall(context, file, installationAllowed)
        else errorMessage.value = "Installation file is unavailable or invalid. Please download the update again."
    }

    fun openUpdateDialog() {
        dismissedTagThisSession = null
        showUpdateDialog.value = true
    }

    fun dismissUpdate(context: Context? = null) {
        if (isDownloading.value) return
        dismissedTagThisSession = availableUpdate.value?.tagName
        showUpdateDialog.value = false
        downloadProgress.floatValue = 0f
        errorMessage.value = null
        context?.let { dismissNotification(it) }
    }

    fun skipVersion(appPreferences: AppPreferences, tagName: String, context: Context? = null) {
        appPreferences.setSkippedUpdateTag(tagName)
        dismissedTagThisSession = tagName
        dismissUpdate(context)
    }

    fun postUpdateNotification(context: Context, release: ReleaseInfo) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when a new version of AllRounder Player is available"
                }
                nm.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, com.example.ymediaplayer.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_update_dialog", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(com.example.ymediaplayer.R.mipmap.ic_launcher)
                .setContentTitle("New Update Available: ${release.title}")
                .setContentText("Version ${release.versionName} is ready to install. Tap to update or skip.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Version ${release.versionName} is ready to install.\n\n${release.changelog.take(200)}")
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(UPDATE_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Notification post may fail if permissions are not yet granted
        }
    }

    fun dismissNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(UPDATE_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    /**
     * Fetches the complete release history from GitHub Releases API (with Atom fallback) for Update History.
     */
    suspend fun fetchReleaseHistory(context: Context): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            isLoadingHistory.value = true
            historyError.value = null
        }
        try {
            val (code, jsonStr) = httpGetNoCache(
                ALL_RELEASES_API,
                acceptHeader = "application/vnd.github.v3+json"
            )
            val list = if (code in 200..299 && jsonStr != null) {
                parseReleaseHistoryJson(jsonStr)
            } else {
                val (atomCode, atomXml) = httpGetNoCache(
                    RELEASES_ATOM_URL,
                    acceptHeader = "application/atom+xml, application/xml, text/xml"
                )
                if (atomCode in 200..299 && !atomXml.isNullOrBlank()) {
                    parseReleasesAtom(atomXml)
                } else {
                    throw IllegalStateException("Unable to load releases (HTTP $code). Please try again.")
                }
            }

            withContext(Dispatchers.Main) {
                releaseHistory.value = list
                isLoadingHistory.value = false
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isLoadingHistory.value = false
                historyError.value = e.message ?: "Check your connection and try again."
            }
            Result.failure(e)
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { isLoadingHistory.value = false }
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) {
            1L
        }
    }

    private fun cleanVersion(tag: String): String {
        return tag.trim().removePrefix("v").removePrefix("V")
    }

    private const val MAX_APK_BYTES = 500L * 1024L * 1024L

    private fun isAllowedDownloadUrl(rawUrl: String): Boolean = runCatching {
        val parsed = URL(rawUrl)
        val host = parsed.host.lowercase(java.util.Locale.US)
        parsed.protocol.equals("https", ignoreCase = true) && (
            host == "github.com" ||
                host == "api.github.com" ||
                host == "release-assets.githubusercontent.com" ||
                host.endsWith(".githubusercontent.com")
            )
    }.getOrDefault(false)

    private fun isValidApkArchive(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            input.read(header) == header.size &&
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun isApkSignedByThisApp(context: Context, apkFile: File): Boolean = runCatching {
        val packageManager = context.packageManager

        fun extractSignatures(info: android.content.pm.PackageInfo?): Set<String> {
            if (info == null) return emptySet()
            val sigArray: Array<android.content.pm.Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signInfo = info.signingInfo
                when {
                    signInfo == null -> info.signatures
                    signInfo.hasMultipleSigners() -> signInfo.apkContentsSigners
                    else -> signInfo.signingCertificateHistory
                }
            } else {
                info.signatures
            }
            val validArray = sigArray ?: info.signatures ?: emptyArray()
            return validArray.mapNotNull { signature ->
                runCatching {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(signature.toByteArray())
                    digest.joinToString("") { byte -> "%02x".format(byte) }
                }.getOrNull()
            }.toSet()
        }

        fun getSignaturesForPackage(packageName: String): Set<String> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val modern = runCatching {
                    packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                }.getOrNull()
                val modernSigs = extractSignatures(modern)
                if (modernSigs.isNotEmpty()) return modernSigs
            }
            val legacy = runCatching {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }.getOrNull()
            return extractSignatures(legacy)
        }

        fun getArchiveSignatures(path: String): Pair<String?, Set<String>> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val modern = runCatching {
                    packageManager.getPackageArchiveInfo(path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                }.getOrNull()
                val modernSigs = extractSignatures(modern)
                if (modern != null && modernSigs.isNotEmpty()) {
                    return modern.packageName to modernSigs
                }
            }
            val legacy = runCatching {
                packageManager.getPackageArchiveInfo(path, android.content.pm.PackageManager.GET_SIGNATURES)
            }.getOrNull()
            return (legacy?.packageName) to extractSignatures(legacy)
        }

        val (archivePackageName, archiveSignatures) = getArchiveSignatures(apkFile.absolutePath)
        if (archivePackageName != context.packageName) return false

        val installedSignatures = getSignaturesForPackage(context.packageName)
        if (installedSignatures.isEmpty() || archiveSignatures.isEmpty()) {
            return true
        }

        archiveSignatures == installedSignatures
    }.getOrDefault(false)

    /**
     * Compares semantic versions (e.g. 1.5.1 > 1.5.0, 2.0 > 1.9.9)
     */
    fun isNewer(remoteVer: String, currentVer: String): Boolean {
        val cleanRemote = cleanVersion(remoteVer)
        val cleanCurrent = cleanVersion(currentVer)
        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false
        val rParts = cleanRemote.split(".").mapNotNull { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull()
        }
        val cParts = cleanCurrent.split(".").mapNotNull { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull()
        }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
