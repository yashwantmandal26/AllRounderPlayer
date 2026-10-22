package com.example.ymediaplayer.update

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Uses a signed copy of the debug APK; captures intents and never installs anything. */
class UpdateInstallContinuationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val intents = mutableListOf<Intent>()
    private val context = object : ContextWrapper(target) {
        override fun startActivity(intent: Intent) { intents += intent }
    }
    private val release = ReleaseInfo("qa-cached", "qa-install-test", "QA", "QA", "https://example.invalid/must-not-download.apk", "qa.apk", 0, "")
    private lateinit var apk: File

    // Simulate the permission provider without changing OS app-ops, which can kill
    // the instrumented process. APK signatures and installation intents remain real.
    private var permissionGranted = false

    @Before fun seedValidatedDownload() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        check(target.packageName.endsWith(".debug"))
        apk = File(target.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "YMedia_${release.versionName}.apk")
        File(target.applicationInfo.sourceDir).copyTo(apk, overwrite = true)
        target.getSharedPreferences("update_install_state", Context.MODE_PRIVATE).edit().clear()
            .putString("ready_tag", release.tagName).putString("ready_path", apk.absolutePath).commit()
        UpdateManager.errorMessage.value = null
        permissionGranted = false
    }

    @After fun cleanup() {
        if (::apk.isInitialized) apk.delete()
        target.getSharedPreferences("update_install_state", Context.MODE_PRIVATE).edit().clear().commit()
        UpdateManager.dismissUpdate()
    }

    @Test fun cachedApkSurvivesPermissionApprovalAndResumesExactlyOnce() = runBlocking {
        val bytes = apk.length()
        val modified = apk.lastModified()
        UpdateManager.downloadAndInstall(context, release) { permissionGranted }
        assertNull(UpdateManager.errorMessage.value)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intents.single().action)
        assertEquals(bytes, apk.length())
        assertEquals(modified, apk.lastModified())
        intents.clear()
        withContext(Dispatchers.Main) { UpdateManager.resumePendingInstall(context) { permissionGranted } }
        assertTrue("Denying permission must not relaunch Settings repeatedly", intents.isEmpty())
        permissionGranted = true
        withContext(Dispatchers.Main) {
            UpdateManager.resumePendingInstall(context) { permissionGranted }
            UpdateManager.resumePendingInstall(context) { permissionGranted }
        }
        assertEquals(1, intents.size)
        assertEquals(Intent.ACTION_VIEW, intents.single().action)
        assertEquals("application/vnd.android.package-archive", intents.single().type)
        assertEquals(bytes, apk.length())
    }

    @Test fun invalidPendingApkIsRejectedInsteadOfOpeningInstaller() = runBlocking {
        apk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        target.getSharedPreferences("update_install_state", Context.MODE_PRIVATE).edit()
            .putBoolean("awaiting_permission", true).commit()
        permissionGranted = true
        withContext(Dispatchers.Main) { UpdateManager.resumePendingInstall(context) { permissionGranted } }
        assertTrue(intents.isEmpty())
        assertNotNull(UpdateManager.errorMessage.value)
    }
}
