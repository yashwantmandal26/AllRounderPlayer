package com.example.ymediaplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

internal data class PlayerModeOption(val id: String, val title: String, val description: String, val enabled: Boolean = true)

@Composable
internal fun TextOnlyPlayerFeedback(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    val shadow = Shadow(Color.Black, Offset(0f, 2f), 5f)
    Column(modifier.widthIn(max = 540.dp).padding(horizontal = 20.dp, vertical = 8.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = TextStyle(shadow = shadow))
        if (!detail.isNullOrBlank()) Text(detail, color = Color(0xFFE8E9EE), fontSize = 12.sp,
            lineHeight = 16.sp, maxLines = 3, textAlign = TextAlign.Center, style = TextStyle(shadow = shadow))
    }
}

@Composable
internal fun PlayerModeButton(label: String, value: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xD91B1C24),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))) {
        Column(
            Modifier.widthIn(min = 58.dp).heightIn(min = 56.dp)
                .semantics { contentDescription = "$label: $value. Choose mode" }
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            Text(label, color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
            Text(value, color = Color(0xFFD1D3DB), fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
internal fun PlayerModePicker(
    title: String, options: List<PlayerModeOption>, selected: String, accent: Color,
    unavailable: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit,
    strength: Float = 1f, onStrengthChange: (Float) -> Unit = {}, onReset: () -> Unit = {},
    previewOriginal: Boolean = false, onPreview: (Boolean) -> Unit = {},
    normalize: Boolean? = null, onNormalizeChange: (Boolean) -> Unit = {}, routeLabel: String = ""

) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF16171E)) {
            Column(Modifier.widthIn(max = 440.dp).heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("$title mode", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(unavailable ?: "Choose a preset Â· applied immediately", color = Color(0xFFB9BBC6),
                    fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                options.forEach { option ->
                    val active = option.id == selected
                    Surface(
                        color = if (active) accent.copy(alpha = 0.14f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().selectable(selected = active, enabled = unavailable == null && option.enabled,
                            role = Role.RadioButton, onClick = { onSelect(option.id) }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = active, onClick = null, enabled = unavailable == null && option.enabled,
                                colors = RadioButtonDefaults.colors(selectedColor = accent))
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(option.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(if (option.enabled) option.description else "Unavailable on this audio output", color = Color(0xFFB9BBC6), fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (unavailable == null) {
                    Text("Intensity · ${(strength * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    Slider(value = strength, onValueChange = onStrengthChange,
                        modifier = Modifier.semantics { contentDescription = "$title intensity" },
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                }
                normalize?.let {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Volume leveling", color = Color.White, fontSize = 14.sp)
                            Text("Smooth volume differences between videos", color = Color(0xFFB9BBC6), fontSize = 12.sp)
                        }
                        Switch(checked = it, onCheckedChange = onNormalizeChange)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    OriginalPreviewButton(previewOriginal, onPreview)
                    TextButton(onClick = onReset) { Text("Reset", color = accent) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = accent)
                }
            }
        }
    }
}

/** The release/cancellation path always restores enhancements, including interrupted touches. */
@Composable
internal fun OriginalPreviewButton(active: Boolean, onPreview: (Boolean) -> Unit) {
    val latestPreview = rememberUpdatedState(onPreview)
    Text(if (active) "Original" else "Hold · Original", color = Color.White, fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.heightIn(min = 48.dp)
            .semantics {
                contentDescription = "Compare with original picture and sound"
                onClick(label = if (active) "End original preview" else "Preview original") { onPreview(!active); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    latestPreview.value(true)
                    try { tryAwaitRelease() } finally { latestPreview.value(false) }
                })
            }.padding(horizontal = 10.dp, vertical = 16.dp))
}
