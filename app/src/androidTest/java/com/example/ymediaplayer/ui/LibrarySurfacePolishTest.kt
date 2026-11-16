package com.example.ymediaplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.theme.CleanLightAppColors
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.OledBlackAppColors
import com.example.ymediaplayer.theme.SlateGreyAppColors
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibrarySurfacePolishTest {
    @get:Rule val rule = createComposeRule()

    @Test fun folderMaterialHasNoBandsOrBackdropBleedAcrossThemes() {
        var palette by mutableStateOf(OledBlackAppColors)
        var stripe by mutableStateOf(Color.Yellow)
        rule.setContent {
            CompositionLocalProvider(LocalAppColors provides palette) {
                Box(Modifier.size(320.dp, 76.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Color.Magenta)
                        drawRect(stripe, Offset(0f, size.height * 0.35f), Size(size.width, size.height * 0.3f))
                    }
                    Box(Modifier.fillMaxSize().liquidFolderGlass(22.dp).testTag("card")) {
                        Column(Modifier.padding(16.dp)) {
                            Text("All Videos", color = palette.textPrimary)
                            Text("28.0 GB", color = palette.textSecondary)
                        }
                    }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-polish")
        output.mkdirs()
        for ((name, colors) in listOf("oled" to OledBlackAppColors, "slate" to SlateGreyAppColors,
            "light" to CleanLightAppColors, "matte" to OledBlackAppColors.copy(isMatte = true))) {
            rule.runOnIdle { palette = colors; stripe = Color.Yellow }
            val capture = rule.onNodeWithTag("card").captureToImage()
            File(output, "folder-$name.png").outputStream().use {
                capture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val before = capture.toPixelMap()
            val x = (before.width * 0.75f).toInt() // Clear space beside the labels.
            for (y in before.height / 5 until before.height * 4 / 5) {
                val a = before[x, y]; val b = before[x, y + 1]
                assertTrue("$name has a horizontal seam at $y: $a -> $b",
                    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.025f)
            }
            rule.runOnIdle { stripe = Color.Cyan }
            val after = rule.onNodeWithTag("card").captureToImage().toPixelMap()
            for (y in before.height / 5 until before.height * 4 / 5 step 5) {
                assertEquals("$name must not sample the stripe underneath", before[x, y], after[x, y])
            }
        }
    }

    @Test fun pressUsesCurrentCallbackAndCancelledGestureDoesNotClick() {
        var version by mutableIntStateOf(1)
        var clicked = 0
        rule.setContent {
            val callbackVersion = version
            Box(Modifier.size(120.dp).testTag("button")
                .bounceClick { clicked = callbackVersion }.background(Color.Gray))
        }
        rule.onNodeWithTag("button").assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, clicked); version = 2; clicked = 0 }
        rule.onNodeWithTag("button").performTouchInput { down(center); cancel() }
        rule.runOnIdle { assertEquals(0, clicked) }
        rule.onNodeWithTag("button").performTouchInput { click() }
        rule.runOnIdle { assertEquals(2, clicked) }
    }

    @Test fun missingMusicArtworkStaysStillBetweenInteractions() {
        rule.setContent {
            MusicAlbumArtImage(null, Modifier.size(72.dp).testTag("art"), seedText = "Golden")
        }
        rule.mainClock.autoAdvance = false
        val before = rule.onNodeWithTag("art").captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(1500)
        val after = rule.onNodeWithTag("art").captureToImage().toPixelMap()
        for (y in 0 until before.height step 3) for (x in 0 until before.width step 3) {
            assertEquals("Idle artwork must not animate every visible row", before[x, y], after[x, y])
        }
    }
}
