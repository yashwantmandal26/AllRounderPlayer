package com.example.ymediaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Isolates the once-per-second time label from the high-frequency slider state. */
@Composable
internal fun PlaybackTimeLabel(
    positionMs: () -> Long,
    durationMs: Long,
    showRemaining: Boolean,
    onClick: () -> Unit
) {
    val latestPosition by rememberUpdatedState(positionMs)
    val text by remember(durationMs, showRemaining) {
        derivedStateOf {
            val (remaining, shownMs) = VideoZoomMath.computeDisplayPositionMs(
                latestPosition(), durationMs, showRemaining
            )
            "${if (remaining) "-" else ""}${formatTime(shownMs)} / ${formatTime(durationMs)}"
        }
    }
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.offset(y = 2.dp).clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
