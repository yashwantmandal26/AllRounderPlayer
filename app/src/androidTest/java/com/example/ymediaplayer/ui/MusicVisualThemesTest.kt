package com.example.ymediaplayer.ui

import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.data.MusicItem
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.CleanLightAppColors
import com.example.ymediaplayer.theme.OledBlackAppColors
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicVisualThemesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allRebuiltThemesAnimateAndRemainStillWithAFrozenClock() {
        var style by mutableStateOf("CARD")
        var phase by mutableFloatStateOf(0f)
        var dark by mutableStateOf(false)
        val song = MusicItem(-1, Uri.EMPTY, "Theme preview", "Studio", "Preview", -9999, 180000, 0)
        rule.setContent {
            AppRoot {
                CompositionLocalProvider(LocalAppColors provides if (dark) OledBlackAppColors else CleanLightAppColors) {
                Box(Modifier.size(320.dp, 290.dp).background(LocalAppColors.current.baseBackground).testTag("visual")) {
                    MusicVisualTheme(style, song, 0.42f, { phase }, { phase * 35f }, { 0.45f },
                        { FloatArray(64) { 0.1f + (it % 8) / 12f } },
                        { FloatArray(128) { kotlin.math.sin(it / 7f) * 0.4f } })
                }
                }
            }
        }
        for (darkMode in listOf(false, true)) for (theme in listOf("CARD", "CASSETTE", "CYBER_ORB", "SONIC_REACTOR", "WAVE", "ECLIPSE")) {
            rule.runOnIdle { dark = darkMode; style = theme; phase = 0f }
            rule.waitForIdle()
            val before = rule.onNodeWithTag("visual").captureToImage().toPixelMap()
            rule.runOnIdle { phase = 1.1f }
            rule.waitForIdle()
            val preview = rule.onNodeWithTag("visual").captureToImage()
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "theme-previews")
            output.mkdirs()
            File(output, "$theme-${if(darkMode) "dark" else "light"}.png").outputStream().use { preview.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val after = preview.toPixelMap()
            var changed = 0
            for (y in 4 until before.height step 7) for (x in 4 until before.width step 7) {
                if (before[x,y] != after[x,y]) changed++
            }
            assertTrue("$theme must visibly respond to its animation clock", changed > 10)
            rule.waitForIdle()
            val still = rule.onNodeWithTag("visual").captureToImage().toPixelMap()
            var drift = 0
            for (y in 4 until after.height step 7) for (x in 4 until after.width step 7) {
                if (after[x,y] != still[x,y]) drift++
            }
            assertEquals("$theme must not keep animating when its clock is paused", 0, drift)
        }
    }
}
