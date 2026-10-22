package com.example.ymediaplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ymediaplayer.theme.LocalAppColors

/** Keeps optional notices from consuming the width needed by the library title. */
@Composable
internal fun LibraryHeader(
    title: String,
    summary: String?,
    actionCount: Int,
    updateVersion: String?,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit
) {
    val c = LocalAppColors.current
    val fontScale = LocalDensity.current.fontScale

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val actionsWidth = (actionCount * 40 + (actionCount - 1).coerceAtLeast(0) * 4).dp
            // Keep header on a single row across phones like S24; stack only on very narrow windows or extreme font scaling.
            val stacked = maxWidth < 240.dp || (fontScale > 1.5f && maxWidth < 280.dp)
            if (stacked) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LibraryTitle(title, summary, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Row(
                            modifier = Modifier.testTag("library_header_actions")
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibraryTitle(title, summary, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.testTag("library_header_actions"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }

        if (updateVersion != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onUpdateClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("library_update_notice"),
                shape = RoundedCornerShape(14.dp),
                color = c.accentBlue.copy(alpha = if (c.isDark) 0.12f else 0.07f),
                border = BorderStroke(1.dp, c.accentBlue.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = c.accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Update available",
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (updateVersion.isNotBlank()) {
                            Text(
                                "Version $updateVersion is ready",
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = c.accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTitle(title: String, summary: String?, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(modifier = modifier) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().testTag("library_header_title").semantics { heading() },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        if (!summary.isNullOrBlank()) {
            Text(
                summary,
                modifier = Modifier.fillMaxWidth().testTag("library_header_summary"),
                fontSize = 12.sp,
                color = c.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
