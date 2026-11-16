package com.example.ymediaplayer.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Record only the screen behind the floating controls, preventing backdrop feedback. */
internal fun Modifier.homeBackdropSource(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** Live screen blur across the entire footer, including the system navigation inset. */
@Composable
internal fun HomeNavigationBackdrop(
    source: GraphicsLayer,
    sourcePosition: Offset,
    tint: Color,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current
    val blurPasses = remember(density, supportsBlur) {
        if (supportsBlur) listOf(
            Triple(6.dp, 0f, 0.30f),
            Triple(18.dp, 0.20f, 0.70f),
            Triple(40.dp, 0.50f, 1f)
        ).map { (radius, start, end) ->
            val pixels = with(density) { radius.toPx() }
            Triple(android.graphics.RenderEffect.createBlurEffect(
                pixels, pixels, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect(), start, end)
        } else emptyList()
    }
    Box(modifier.clipToBounds().onGloballyPositioned { position = it.positionInRoot() }
        // The blur is an opaque touch boundary even where its pixels are transparent.
        // Nav/mini-player controls are separate siblings above this layer and keep their input.
        // Do not use clickable: empty footer space has no action, ripple or accessibility click.
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }) {
        if (supportsBlur) {
            // Each stronger copy replaces the preceding copy gradually. Fading a single
            // fixed-radius blur would change opacity while retaining the same blur size.
            blurPasses.forEach { (blurEffect, fadeStart, fadeEnd) ->
                Box(
                    Modifier.matchParentSize()
                        // Mask the blurred copy independently; never blur the controls above it.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val mask = Brush.verticalGradient(
                                fadeStart to Color.Transparent,
                                fadeEnd to Color.White
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(mask, blendMode = BlendMode.DstIn)
                            }
                        }
                        .graphicsLayer {
                            renderEffect = blurEffect
                        }
                        .drawWithContent {
                            val offset = sourcePosition - position
                            translate(offset.x, offset.y) { drawLayer(source) }
                        }
                )
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.25f to tint.copy(alpha = if (supportsBlur) 0.12f else 0.40f),
                    0.65f to tint.copy(alpha = if (supportsBlur) 0.30f else 0.80f),
                    1f to tint.copy(alpha = if (supportsBlur) 0.48f else 0.96f)
                )
            )
        )
    }
}
