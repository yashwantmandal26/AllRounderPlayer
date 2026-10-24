package com.example.ymediaplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerAmbientSettings(
    enabled: Boolean,
    intensity: Float,
    spread: Float,
    saturation: Float = 1.0f,
    batterySaver: Boolean,
    onEnabled: (Boolean) -> Unit,
    onIntensity: (Float) -> Unit,
    onSpread: (Float) -> Unit,
    onSaturation: (Float) -> Unit = {},
    onBatterySaver: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ambient lighting") }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(enabled, onEnabled)
            }
            Text("Intensity · ${(intensity * 100).toInt()}%")
            AppSlider(
                value = intensity,
                onValueChange = onIntensity,
                valueRange = 0.2f..2.0f,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Ambient intensity" }
            )
            Text("Spread · ${(spread * 100).toInt()}%")
            AppSlider(
                value = spread,
                onValueChange = onSpread,
                valueRange = 0.2f..1.5f,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Ambient spread" }
            )
            Text("Saturation · ${(saturation * 100).toInt()}%")
            AppSlider(
                value = saturation,
                onValueChange = onSaturation,
                valueRange = 0.4f..2.5f,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Ambient saturation" }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Battery saver")
                    Text("Lower capture rate. Paused video keeps its last color.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(batterySaver, onBatterySaver)
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
