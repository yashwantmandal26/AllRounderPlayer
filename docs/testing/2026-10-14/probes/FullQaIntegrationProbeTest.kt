package com.example.ymediaplayer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.update.ReleaseInfo
import com.example.ymediaplayer.update.UpdateManager
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class FullQaIntegrationProbeTest {
    @get:Rule val rule = createComposeRule()
    @After fun cleanState() {
        rule.runOnIdle { UpdateManager.availableUpdate.value = null; UpdateManager.dismissUpdate() }
    }
    @Test fun settingsNotNowClosesUpdateDialog() {
        rule.runOnUiThread {
            UpdateManager.availableUpdate.value = ReleaseInfo("v99.0", "99.0", "QA test release", "**QA only**", "https://example.invalid/qa.apk", "qa.apk", 100, "2026-10-14")
            UpdateManager.showUpdateDialog.value = true
        }
        rule.setContent { AppRoot { SettingsScreen(onBack = {}) } }
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa-settings-dismiss-bug.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        rule.onNodeWithTag("update-dialog").assertDoesNotExist()
    }
}
