package com.example.ymediaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class SmartClarityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun productionShaderCompilesAndAcceptsItsUniforms() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val shader = RuntimeShader(SMART_CLARITY_SHADER)
        shader.setFloatUniform("aiStrength", 1f)
        assertNotNull(RenderEffect.createRuntimeShaderEffect(shader, "image"))
        assertNotNull(createVideoRenderEffect(1f, 1f, 0f, 0f, 0f, isSuperAi = true))
    }

    @Test fun enhancementChangesMidtonesButPreservesBlackWhiteAndFlatRegions() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val input = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) for (x in 0 until 96) {
            val value = when {
                x < 24 -> 0
                x < 48 -> 72
                x < 72 -> 144
                else -> 255
            }
            input.setPixel(x, y, Color.rgb(value, value, value))
        }
        val enabled = mutableStateOf(false)
        val effect = createVideoRenderEffect(1f, 1f, 0f, 0f, 0f, isSuperAi = true)!!
        rule.setContent {
            MaterialTheme {
                Image(input.asImageBitmap(), null, Modifier.size(192.dp, 128.dp).testTag("clarity-image")
                    .graphicsLayer { renderEffect = if (enabled.value) effect.asComposeRenderEffect() else null })
            }
        }
        val original = rule.onNodeWithTag("clarity-image").captureToImage().asAndroidBitmap()
        rule.runOnIdle { enabled.value = true }
        val enhanced = rule.onNodeWithTag("clarity-image").captureToImage().asAndroidBitmap()
        fun sample(image: Bitmap, fraction: Float): Int = Color.red(image.getPixel((image.width * fraction).toInt(), image.height / 2))
        assertTrue("Black must remain black", sample(enhanced, 0.125f) <= 1)
        assertTrue("White must remain white", sample(enhanced, 0.875f) >= 254)
        assertTrue("Shadows must remain deep and unbleached", sample(enhanced, 0.375f) in (sample(original, 0.375f) - 6)..(sample(original, 0.375f) + 6))
        val flat = sample(enhanced, 0.375f)
        for (x in (enhanced.width * 0.30f).toInt()..(enhanced.width * 0.45f).toInt()) {
            assertTrue("Flat areas must not gain texture", abs(Color.red(enhanced.getPixel(x, enhanced.height / 2)) - flat) <= 1)
        }
        enhanced.recycle()
        original.recycle()
    }
}
