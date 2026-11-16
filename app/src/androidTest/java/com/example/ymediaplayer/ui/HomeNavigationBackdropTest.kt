package com.example.ymediaplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 31)
class HomeNavigationBackdropTest {
    @get:Rule val rule = createComposeRule()

    @Test fun blankFooterBlocksUnderlyingMediaWithoutBlockingFloatingControls() {
        var mediaOpens = 0
        var mediaMenus = 0
        var contentDrags = 0
        var navActions = 0
        rule.setContent {
            val layer = rememberGraphicsLayer()
            Box(Modifier.size(240.dp).testTag("touch-scene")) {
                Box(Modifier.matchParentSize().homeBackdropSource(layer)
                    .bounceClick(onLongClick = { mediaMenus++ }) { mediaOpens++ }
                    .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); contentDrags++ } }
                    .background(Color.Blue))
                HomeNavigationBackdrop(layer, Offset.Zero, Color.Black,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp).testTag("footer"))
                Box(Modifier.align(Alignment.BottomCenter).size(40.dp).testTag("floating-control")
                    .bounceClick { navActions++ }.background(Color.White))
            }
        }
        rule.onNodeWithTag("footer").assertHasNoClickAction()
        rule.onNodeWithTag("touch-scene").performTouchInput {
            // Same normalized position marked in the report, beyond the nav pill's right edge.
            click(Offset(width * 0.819f, height * 0.939f))
            click(Offset(width * 0.05f, height * 0.95f))
            click(Offset(width * 0.85f, height * 0.70f))
            longClick(Offset(width * 0.85f, height * 0.85f))
            swipe(Offset(width * 0.85f, height * 0.95f), Offset(width * 0.85f, height * 0.30f))
        }
        rule.runOnIdle {
            assertEquals("Blur gaps never open media", 0, mediaOpens)
            assertEquals("Blur gaps never open context menus", 0, mediaMenus)
            assertEquals("A gesture beginning in the blur cannot scroll the media below", 0, contentDrags)
        }
        rule.onNodeWithTag("floating-control").performTouchInput { click() }
        rule.runOnIdle { assertEquals("Foreground navigation remains usable", 1, navActions) }
        rule.onNodeWithTag("touch-scene").performTouchInput { click(Offset(width * 0.50f, height * 0.25f)) }
        rule.runOnIdle { assertEquals("Visible content above the footer still opens", 1, mediaOpens) }
    }

    @Test fun footerBlursLiveContentWhileSourceAndControlsStaySharp() {
        var changed by mutableStateOf(false)
        rule.setContent {
            val layer = rememberGraphicsLayer()
            var sourcePosition by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.padding(12.dp)) {
                Box(Modifier.size(240.dp).testTag("scene")) {
                    Canvas(Modifier.matchParentSize()
                        .onGloballyPositioned { sourcePosition = it.positionInRoot() }
                        .homeBackdropSource(layer)) {
                        val stripe = 32.dp.toPx()
                        repeat((size.width / stripe).toInt() + 1) { index ->
                            val color = if (changed) {
                                if (index % 2 == 0) Color.Green else Color.Black
                            } else {
                                if (index % 2 == 0) Color.Red else Color.Blue
                            }
                            drawRect(color, Offset(index * stripe, 0f), Size(stripe, size.height))
                        }
                    }
                    HomeNavigationBackdrop(layer, sourcePosition, Color.Black,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(80.dp))
                    Box(Modifier.align(Alignment.BottomCenter).size(20.dp).background(Color.White))
                }
            }
        }
        rule.waitForIdle()
        val before = rule.onNodeWithTag("scene").captureToImage().toPixelMap()
        val x = (before.width * 80f / 240f).toInt() // Center of a red stripe, away from controls.
        val upper = before[x, before.height / 3]
        val footer = before[x, (before.height * 0.85f).toInt()]
        assertTrue("Content above footer stays sharp: $upper", upper.red > 0.95f && upper.blue < 0.05f)
        assertTrue("Footer mixes adjacent red/blue content: $footer", footer.red > 0.1f && footer.blue > 0.05f)
        // Normalize out the dark tint: this measures lost stripe detail, not opacity.
        fun blueShare(y: Int): Float {
            val pixel = before[x, y]
            return pixel.blue / (pixel.red + pixel.blue).coerceAtLeast(0.001f)
        }
        val shallow = blueShare((before.height * 0.75f).toInt())
        val middle = blueShare((before.height * 0.85f).toInt())
        val deep = blueShare((before.height * 0.97f).toInt())
        assertTrue("Blur grows down the footer: $shallow, $middle, $deep",
            middle > shallow + 0.05f && deep > middle + 0.03f)
        val control = before[before.width / 2, before.height - 5]
        assertTrue("Foreground control stays sharp: $control", control.red > 0.95f && control.green > 0.95f)

        rule.runOnIdle { changed = true }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("scene").captureToImage().toPixelMap()
        val updated = after[x, (after.height * 0.85f).toInt()]
        assertTrue("Blur follows live content changes: $updated", updated.green > 0.1f && updated.red < 0.05f && updated.blue < 0.05f)
    }
}
