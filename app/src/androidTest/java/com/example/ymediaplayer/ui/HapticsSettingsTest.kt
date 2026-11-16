package com.example.ymediaplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.data.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HapticsSettingsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun intensitySavesAndLivePreviewCanBeStopped() {
        val prefs = AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = prefs.getVideoHapticTuning("speakers")
        val shown = mutableStateOf(true)
        try {
            rule.setContent { AppRoot {
                if (shown.value) Column(Modifier.verticalScroll(rememberScrollState())) {
                    HapticsSettingsPanel(false, {}, false, {}, 0)
                }
            } }
            rule.onNodeWithText("Speaker").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Overall intensity").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
            rule.onNodeWithContentDescription("Preview while adjusting").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Haptic waveform preview").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Stop", useUnmergedTree = true).performScrollTo().performClick()
            rule.runOnIdle { shown.value = false }
            rule.waitForIdle()
            assertEquals(0.75f, prefs.getVideoHapticTuning("speakers").strength, 0.01f)
        } finally {
            rule.runOnIdle { shown.value = false }
            rule.waitForIdle()
            prefs.setVideoHapticTuning("speakers", original)
        }
    }
}
