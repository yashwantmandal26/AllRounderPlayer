package com.example.ymediaplayer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.ymediaplayer.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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

    /**
     * Checks GitHub for the latest release.
     * @param isManual true when user clicked "Check for Updates" in Settings, false on startup.
     */
    suspend fun checkForUpdates(
        context: Context,
        appPreferences: AppPreferences,
        isManual: Boolean = false
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        var activeConnection: HttpURLConnection? = null
        if (!isManual && !appPreferences.isAutoCheckUpdatesEnabled()) {
            return@withContext Result.success(null)
        }

        // Throttle automatic checks: at most once every 6 hours
        val now = System.currentTimeMillis()
        if (!isManual) {
            val lastCheck = appPreferences.getLastUpdateCheckTime()
            if (now - lastCheck < 6 * 60 * 60 * 1000L) {
                return@withContext Result.success(null)
            }
        }

        withContext(Dispatchers.Main) {
            isChecking.value = true
            errorMessage.value = null
        }

        try {
            val url = URL(LATEST_RELEASE_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "YMedia-Player-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            activeConnection = connection

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // No releases published yet on this repository
                appPreferences.setLastUpdateCheckTime(now)
                withContext(Dispatchers.Main) { isChecking.value = false }
                return@withContext Result.success(null)
            }

            if (responseCode !in 200..299) {
                val err = "GitHub API returned status $responseCode"
                withContext(Dispatchers.Main) {
                    isChecking.value = false
                    if (isManual) errorMessage.value = err
                }
                return@withContext Result.failure(Exception(err))
            }

            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val body = json.optString("body", "Bug fixes and performance enhancements.")
            val publishedAt = json.optString("published_at", "")

            // Find APK asset in assets array
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        // Prefer release build APK if both debug and release exist
                        if (name.contains("release", ignoreCase = true)) {
                            break
                        }
                    }
                }
            }

            appPreferences.setLastUpdateCheckTime(now)

            if (apkUrl.isNullOrBlank()) {
                // Some releases distribute the APK from the repository. The downloaded
                // package version is verified below so a stale file cannot be installed.
                apkUrl = "https://github.com/yashwantmandal26/AllRounderPlayer/raw/main/app-release.apk"
                apkName = "YMedia_${cleanVersion(tagName)}.apk"
            }

            val currentVersionName = getCurrentVersionName(context)
            val remoteVersionName = cleanVersion(tagName)

            if (isNewer(remoteVersionName, currentVersionName)) {
                // Check if user previously chose "Skip version" for automatic checks
                val skippedTag = appPreferences.getSkippedUpdateTag()
                if (!isManual && skippedTag == tagName) {
                    withContext(Dispatchers.Main) { isChecking.value = false }
                    return@withContext Result.success(null)
                }

                val release = ReleaseInfo(
                    tagName = tagName,
                    versionName = remoteVersionName,
                    title = title.ifBlank { "Version $remoteVersionName" },
                    changelog = body,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName ?: "YMedia_v${remoteVersionName}.apk",
                    fileSize = apkSize,
                    publishedAt = publishedAt
                )

                withContext(Dispatchers.Main) {
                    availableUpdate.value = release
                    if (isManual) showUpdateDialog.value = true
                    isChecking.value = false
                }
                return@withContext Result.success(release)
            } else {
                withContext(Dispatchers.Main) {
                    availableUpdate.value = null
                    isChecking.value = false
                }
                return@withContext Result.success(null)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isChecking.value = false
                if (isManual) errorMessage.value = e.message ?: "Failed to check for updates"
            }
            return@withContext Result.failure(e)
        } finally {
            activeConnection?.disconnect()
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { isChecking.value = false }
        }
    }

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
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "YMedia-Player-Android")
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
        showUpdateDialog.value = true
    }

    fun dismissUpdate() {
        if (isDownloading.value) return
        showUpdateDialog.value = false
        downloadProgress.floatValue = 0f
        errorMessage.value = null
    }

    fun skipVersion(appPreferences: AppPreferences, tagName: String) {
        appPreferences.setSkippedUpdateTag(tagName)
        dismissUpdate()
    }

    /**
     * Fetches the complete release history from GitHub Releases API for Update History.
     */
    suspend fun fetchReleaseHistory(context: Context): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        var activeConnection: HttpURLConnection? = null
        withContext(Dispatchers.Main) {
            isLoadingHistory.value = true
            historyError.value = null
        }
        try {
            val url = URL(ALL_RELEASES_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "YMedia-Player-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            activeConnection = connection

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                withContext(Dispatchers.Main) { isLoadingHistory.value = false }
                throw IllegalStateException("Unable to load releases (HTTP $responseCode). Please try again.")
            }

            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ReleaseInfo>()

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) continue
                val tagName = json.optString("tag_name", "")
                val title = json.optString("name", tagName)
                val body = json.optString("body", "Bug fixes and performance enhancements.")
                val publishedAt = json.optString("published_at", "")
                val remoteVersionName = cleanVersion(tagName)

                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize = 0L

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            if (name.contains("release", ignoreCase = true)) break
                        }
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    apkUrl = ""
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
            activeConnection?.disconnect()
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
        if (remoteVer.equals(currentVer, ignoreCase = true)) return false
        val rParts = remoteVer.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = currentVer.split(".").mapNotNull { it.toIntOrNull() }

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
