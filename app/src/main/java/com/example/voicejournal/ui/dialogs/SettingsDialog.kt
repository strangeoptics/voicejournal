package com.example.voicejournal.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    currentDays: Int,
    isGpsTrackingEnabled: Boolean,
    gpsInterval: Int,
    onSave: (days: Int, isGpsEnabled: Boolean, interval: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var days by remember { mutableStateOf(currentDays.toString()) }
    var gpsEnabled by remember { mutableStateOf(isGpsTrackingEnabled) }
    var interval by remember { mutableFloatStateOf(gpsInterval.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                Text("Wie viele Tage sollen angezeigt werden?")
                TextField(
                    value = days,
                    onValueChange = { days = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPS-Tracking aktivieren", modifier = Modifier.weight(1f))
                    Switch(
                        checked = gpsEnabled,
                        onCheckedChange = { gpsEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("GPS Abrufintervall: ${interval.roundToInt()} Minuten")
                Slider(
                    value = interval,
                    onValueChange = { interval = it },
                    valueRange = 5f..60f,
                    steps = 54, // (60-5) / 1
                    enabled = gpsEnabled
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    days.toIntOrNull() ?: currentDays,
                    gpsEnabled,
                    interval.roundToInt()
                )
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
