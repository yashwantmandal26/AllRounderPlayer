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

    // ─── Reactive Compose State ───────────────────────────────────────────────
    val availableUpdate = mutableStateOf<ReleaseInfo?>(null)
    val isChecking = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableFloatStateOf(0f)
    val downloadStatusText = mutableStateOf("")
    val errorMessage = mutableStateOf<String?>(null)

    /**
     * Checks GitHub for the latest release.
     * @param isManual true when user clicked "Check for Updates" in Settings, false on startup.
     */
    suspend fun checkForUpdates(
        context: Context,
        appPreferences: AppPreferences,
        isManual: Boolean = false
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
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
                // Fallback: Download the committed app-release.apk directly from the GitHub repository
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

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isChecking.value = false
                if (isManual) errorMessage.value = e.message ?: "Failed to check for updates"
            }
            return@withContext Result.failure(e)
        }
    }

    /**
     * Downloads the APK file and triggers installation.
     */
    suspend fun downloadAndInstall(
        context: Context,
        release: ReleaseInfo
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            isDownloading.value = true
            downloadProgress.value = 0f
            downloadStatusText.value = "Starting download..."
            errorMessage.value = null
        }

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val apkFile = File(targetDir, "YMedia_${release.versionName}.apk")

        try {
            if (apkFile.exists()) {
                apkFile.delete()
            }

            // Download supporting redirects (GitHub -> AWS S3)
            var currentUrl = release.apkDownloadUrl
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
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (!newLocation.isNullOrEmpty() && redirects < 6) {
                        currentUrl = newLocation
                        redirects++
                        continue
                    }
                }
                break
            }

            val totalLength = connection.contentLengthLong.takeIf { it > 0 } ?: release.fileSize
            val input: InputStream = connection.inputStream
            val output = FileOutputStream(apkFile)

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var downloaded = 0L
            var lastUpdateMs = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 100 || downloaded == totalLength) {
                    lastUpdateMs = now
                    val prog = if (totalLength > 0) (downloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0f
                    val downloadedMb = downloaded.toFloat() / (1024 * 1024)
                    val totalMb = totalLength.toFloat() / (1024 * 1024)

                    withContext(Dispatchers.Main) {
                        downloadProgress.value = prog
                        downloadStatusText.value = if (totalLength > 0) {
                            String.format(java.util.Locale.getDefault(), "%.1f MB / %.1f MB (%.0f%%)", downloadedMb, totalMb, prog * 100f)
                        } else {
                            String.format(java.util.Locale.getDefault(), "%.1f MB downloaded", downloadedMb)
                        }
                    }
                }
            }

            output.flush()
            output.close()
            input.close()
            connection.disconnect()

            withContext(Dispatchers.Main) {
                isDownloading.value = false
                downloadStatusText.value = "Ready to install"
                // Trigger the installation intent
                triggerInstall(context, apkFile)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                isDownloading.value = false
                errorMessage.value = "Download failed: ${e.message ?: "Network error"}"
            }
        }
    }

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    fun triggerInstall(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            errorMessage.value = "Installation file not found"
            return
        }

        // On Android 8.0+ (Oreo, API 26+), check if unknown sources permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permissionIntent)
                return
            }
        }

        try {
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
        } catch (e: Exception) {
            errorMessage.value = "Failed to launch installer: ${e.message}"
        }
    }

    fun dismissUpdate() {
        availableUpdate.value = null
        isDownloading.value = false
        downloadProgress.value = 0f
        errorMessage.value = null
    }

    fun skipVersion(appPreferences: AppPreferences, tagName: String) {
        appPreferences.setSkippedUpdateTag(tagName)
        dismissUpdate()
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
