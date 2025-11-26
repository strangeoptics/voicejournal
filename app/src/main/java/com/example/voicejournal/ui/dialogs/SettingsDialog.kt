package com.example.voicejournal.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    currentDays: Int,
    isGpsTrackingEnabled: Boolean,
    gpsInterval: Int,
    currentSpeechService: String,
    currentApiKey: String,
    onSave: (days: Int, isGpsEnabled: Boolean, interval: Int, speechService: String, apiKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    var days by remember { mutableStateOf(currentDays.toString()) }
    var gpsEnabled by remember { mutableStateOf(isGpsTrackingEnabled) }
    var interval by remember { mutableFloatStateOf(gpsInterval.toFloat()) }
    var speechService by remember { mutableStateOf(currentSpeechService) }
    var apiKey by remember { mutableStateOf(currentApiKey) }

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

                Spacer(modifier = Modifier.height(24.dp))
                Text("Spracherkennung Dienst", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    val radioOptions = listOf("ANDROID" to "Android SpeechRecognizer", "GOOGLE_CLOUD" to "Google Cloud Speech API")
                    radioOptions.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (speechService == value),
                                    onClick = { speechService = value },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (speechService == value),
                                onClick = null // null recommended for accessibility with selectable
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

                if (speechService == "GOOGLE_CLOUD") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Google Cloud API Key")
                    TextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    days.toIntOrNull() ?: currentDays,
                    gpsEnabled,
                    interval.roundToInt(),
                    speechService,
                    apiKey
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
