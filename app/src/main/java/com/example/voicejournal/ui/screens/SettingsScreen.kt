package com.example.voicejournal.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDays: Int,
    isGpsTrackingEnabled: Boolean,
    gpsInterval: Int,
    currentSpeechService: String,
    currentApiKey: String,
    maxRecordingTime: Int,
    silenceThreshold: Int,
    silenceTimeRequired: Int,
    onSave: (days: Int, isGpsEnabled: Boolean, interval: Int, speechService: String, apiKey: String, maxRecordingTime: Int, silenceThreshold: Int, silenceTimeRequired: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var days by remember { mutableStateOf(currentDays.toString()) }
    var gpsEnabled by remember { mutableStateOf(isGpsTrackingEnabled) }
    var interval by remember { mutableFloatStateOf(gpsInterval.toFloat()) }
    var speechService by remember { mutableStateOf(currentSpeechService) }
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var recordingTime by remember { mutableFloatStateOf(maxRecordingTime.toFloat()) }
    var threshold by remember { mutableFloatStateOf(silenceThreshold.toFloat()) }
    var silenceTime by remember { mutableFloatStateOf(silenceTimeRequired.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Maximale Aufnahmedauer: ${recordingTime.roundToInt()} Sekunden")
                    Slider(
                        value = recordingTime,
                        onValueChange = { recordingTime = it },
                        valueRange = 5f..60f,
                        steps = 54
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Stille-Schwellenwert: ${threshold.roundToInt()}")
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 100f..2000f,
                        steps = 189
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Benötigte Stille: ${silenceTime.roundToInt()} ms")
                    Slider(
                        value = silenceTime,
                        onValueChange = { silenceTime = it },
                        valueRange = 500f..5000f,
                        steps = 89
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    Button(onClick = {
                        onSave(
                            days.toIntOrNull() ?: currentDays,
                            gpsEnabled,
                            interval.roundToInt(),
                            speechService,
                            apiKey,
                            recordingTime.roundToInt(),
                            threshold.roundToInt(),
                            silenceTime.roundToInt()
                        )
                    }) {
                        Text("Speichern")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    )
}