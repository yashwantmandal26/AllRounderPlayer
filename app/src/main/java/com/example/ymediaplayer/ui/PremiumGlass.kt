package com.example.ymediaplayer.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.ymediaplayer.theme.LocalAppColors

internal class PremiumBackdrop(val layer: GraphicsLayer) {
    var position by mutableStateOf(Offset.Zero)
}
internal val LocalPremiumBackdrop = staticCompositionLocalOf<PremiumBackdrop?> { null }

/** A single, opaque material keeps underlying shadows and recorded layers out of card labels. */
internal fun Modifier.liquidFolderGlass(radius: Dp): Modifier = composed {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    clip(shape).drawWithCache {
        val base = if (c.isMatte) c.cardBg else if (c.isDark) Color(0xFF242936) else Color(0xFFF8FAFD)
        // Composite the tint once, rather than stacking transparent passes over live content.
        val material = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (c.isDark) 0.045f else 0.55f).compositeOver(base),
                c.accentBlue.copy(alpha = if (c.isDark) 0.025f else 0.035f).compositeOver(base),
                base
            ),
            start = Offset.Zero, end = Offset(size.width, size.height * 2f)
        )
        onDrawBehind {
            if (c.isMatte) drawRect(base) else drawRect(material)
        }
    }.border(1.dp, Brush.verticalGradient(listOf(
        c.cardBorderHighlight.copy(alpha = if (c.isMatte) 0.20f else 0.38f),
        c.glassBorder.copy(alpha = 0.45f),
        c.cardBorderShadow.copy(alpha = 0.30f)
    )), shape)
}

/** Identical search surface across all three home tabs, outside the backdrop feedback path. */
internal fun Modifier.homeSearchSurface(active: Boolean): Modifier = composed {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(24.dp)
    fillMaxWidth().height(48.dp)
        .shadow(8.dp, shape,
            ambientColor = if (c.isDark) Color.Black.copy(alpha = 0.55f) else c.cardShadowColor,
            spotColor = if (c.isDark) Color.Black.copy(alpha = 0.45f) else c.cardShadowColor)
        .clip(shape)
        .premiumGlass(useBackdrop = false)
        .border(1.3.dp, Brush.verticalGradient(
            if (active) listOf(c.accentBlue, c.cardBorderHighlight, c.accentBlue.copy(alpha = 0.4f))
            else listOf(c.cardBorderHighlight, c.glassBorder, c.cardBorderShadow)
        ), shape)
        .padding(horizontal = 14.dp)
}

/** Blur a separate source, then draw the foreground normally so labels remain sharp. */
internal fun Modifier.premiumGlass(useBackdrop: Boolean = true): Modifier = composed {
    val colors = LocalAppColors.current
    val source = LocalPremiumBackdrop.current.takeIf { useBackdrop && !colors.isMatte }
    val background = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val tint = if (colors.isDark) Color(0xFF151A26) else Color(0xFFF5F9FF)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blur = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = with(density) { 22.dp.toPx() }
            android.graphics.RenderEffect.createBlurEffect(
                radius, radius, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        } else null
    }
    onGloballyPositioned { position = it.positionOnScreen() }.drawWithContent {
        if (source != null && Build.VERSION.SDK_INT >= 31) {
            background.renderEffect = blur
            background.record {
                val offset = source.position - position
                translate(offset.x, offset.y) { drawLayer(source.layer) }
            }
            drawLayer(background)
        }
        drawRect(tint.copy(alpha = if (colors.isMatte) 1f else if (source != null) 0.78f else 0.70f))
        drawRect(Brush.verticalGradient(listOf(colors.accentBlue.copy(alpha = 0.06f), Color.Transparent)))
        drawContent()
    }
}
