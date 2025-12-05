package com.example.voicejournal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    isWebServerEnabled: Boolean,
    ipAddress: String,
    gpsInterval: Int,
    currentSpeechService: String,
    currentApiKey: String,
    maxRecordingTime: Int,
    silenceThreshold: Int,
    silenceTimeRequired: Int,
    truncationLength: Int,
    onDaysChanged: (Int) -> Unit,
    onGpsEnableChanged: (Boolean) -> Unit,
    onWebServerEnableChanged: (Boolean) -> Unit,
    onGpsIntervalChanged: (Int) -> Unit,
    onSpeechServiceChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onMaxRecordingTimeChanged: (Int) -> Unit,
    onSilenceThresholdChanged: (Int) -> Unit,
    onSilenceTimeRequiredChanged: (Int) -> Unit,
    onTruncationLengthChanged: (Int) -> Unit,
    onBackPressed: () -> Unit
) {
    var days by remember { mutableStateOf(currentDays.toString()) }
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var truncationLengthState by remember { mutableStateOf(truncationLength.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Anzeigetage")
                    TextField(
                        value = days,
                        onValueChange = {
                            days = it
                            it.toIntOrNull()?.let(onDaysChanged)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Textlänge für Vorschau")
                    TextField(
                        value = truncationLengthState,
                        onValueChange = {
                            truncationLengthState = it
                            it.toIntOrNull()?.let(onTruncationLengthChanged)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp)
                    )
                }


                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPS-Tracking aktivieren", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isGpsTrackingEnabled,
                        onCheckedChange = onGpsEnableChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("GPS Abrufintervall: ${gpsInterval} Minuten")
                Slider(
                    value = gpsInterval.toFloat(),
                    onValueChange = { onGpsIntervalChanged(it.roundToInt()) },
                    valueRange = 5f..60f,
                    steps = 54, // (60-5) / 1
                    enabled = isGpsTrackingEnabled
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Webserver aktivieren", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isWebServerEnabled,
                        onCheckedChange = onWebServerEnableChanged
                    )
                }
                if (isWebServerEnabled) {
                    Text(
                        text = "Server running at: http://$ipAddress:8080",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }


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
                                    selected = (currentSpeechService == value),
                                    onClick = { onSpeechServiceChanged(value) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentSpeechService == value),
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

                if (currentSpeechService == "GOOGLE_CLOUD") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Google Cloud API Key")
                    TextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            onApiKeyChanged(it)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Maximale Aufnahmedauer: $maxRecordingTime Sekunden")
                    Slider(
                        value = maxRecordingTime.toFloat(),
                        onValueChange = { onMaxRecordingTimeChanged(it.roundToInt()) },
                        valueRange = 5f..60f,
                        steps = 54
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Stille-Schwellenwert: $silenceThreshold")
                    Slider(
                        value = silenceThreshold.toFloat(),
                        onValueChange = { onSilenceThresholdChanged(it.roundToInt()) },
                        valueRange = 100f..2000f,
                        steps = 189
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Benötigte Stille: $silenceTimeRequired ms")
                    Slider(
                        value = silenceTimeRequired.toFloat(),
                        onValueChange = { onSilenceTimeRequiredChanged(it.roundToInt()) },
                        valueRange = 500f..5000f,
                        steps = 89
                    )
                }
            }
        }
    )
}
