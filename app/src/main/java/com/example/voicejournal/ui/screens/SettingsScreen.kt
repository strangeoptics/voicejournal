package com.example.voicejournal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.SpeechService
import com.example.voicejournal.worker.LocationWorker
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isGpsTrackingEnabled: Boolean,
    isWebServerEnabled: Boolean,
    ipAddress: String,
    dnsName: String,
    gpsInterval: Int,
    currentSpeechService: SpeechService,
    currentApiKey: String,
    maxRecordingTime: Int,
    silenceThreshold: Int,
    silenceTimeRequired: Int,
    truncationLength: Int,
    isDeveloperModeEnabled: Boolean,
    onGpsEnableChanged: (Boolean) -> Unit,
    onWebServerEnableChanged: (Boolean) -> Unit,
    onGpsIntervalChanged: (Int) -> Unit,
    onSpeechServiceChanged: (SpeechService) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onMaxRecordingTimeChanged: (Int) -> Unit,
    onSilenceThresholdChanged: (Int) -> Unit,
    onSilenceTimeRequiredChanged: (Int) -> Unit,
    onTruncationLengthChanged: (Int) -> Unit,
    onDeveloperModeEnableChanged: (Boolean) -> Unit,
    onBackPressed: () -> Unit
) {
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isApiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var truncationLengthState by rememberSaveable(truncationLength) { mutableStateOf(truncationLength.toString()) }

    val minGpsMinutes = LocationWorker.MIN_INTERVAL_MINUTES
    val clampedGpsInterval = gpsInterval.coerceIn(minGpsMinutes, 60)
    var gpsSliderValue by remember(clampedGpsInterval) { mutableFloatStateOf(clampedGpsInterval.toFloat()) }
    var maxRecordingSliderValue by remember(maxRecordingTime) { mutableFloatStateOf(maxRecordingTime.coerceIn(5, 60).toFloat()) }
    var silenceThresholdSliderValue by remember(silenceThreshold) { mutableFloatStateOf(silenceThreshold.coerceIn(100, 2000).toFloat()) }
    var silenceTimeSliderValue by remember(silenceTimeRequired) { mutableFloatStateOf(silenceTimeRequired.coerceIn(500, 5000).toFloat()) }

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
                    .imePadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                            it.toIntOrNull()?.takeIf { len -> len > 0 }?.let(onTruncationLengthChanged)
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

                Text("GPS Abrufintervall: ${gpsSliderValue.roundToInt()} Minuten")
                Slider(
                    value = gpsSliderValue,
                    onValueChange = { gpsSliderValue = it },
                    onValueChangeFinished = {
                        onGpsIntervalChanged(gpsSliderValue.roundToInt().coerceAtLeast(minGpsMinutes))
                    },
                    valueRange = minGpsMinutes.toFloat()..60f,
                    steps = 60 - minGpsMinutes - 1, // 1-Minuten-Schritte ab WorkManager-Minimum (15 Min.)
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
                    if (dnsName.isNotBlank()) {
                        Text(
                            text = "or http://$dnsName:8080",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer-Mode aktivieren", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isDeveloperModeEnabled,
                        onCheckedChange = onDeveloperModeEnableChanged
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Spracherkennung Dienst", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    SpeechService.entries.forEach { service ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (currentSpeechService == service),
                                    onClick = { onSpeechServiceChanged(service) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentSpeechService == service),
                                onClick = null
                            )
                            Text(
                                text = service.displayLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

                if (currentSpeechService == SpeechService.GOOGLE_CLOUD) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Google Cloud API Key")
                    TextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            onApiKeyChanged(it)
                        },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        trailingIcon = {
                            TextButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Text(if (isApiKeyVisible) "Verbergen" else "Anzeigen")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Maximale Aufnahmedauer: ${maxRecordingSliderValue.roundToInt()} Sekunden")
                    Slider(
                        value = maxRecordingSliderValue,
                        onValueChange = { maxRecordingSliderValue = it },
                        onValueChangeFinished = {
                            onMaxRecordingTimeChanged(maxRecordingSliderValue.roundToInt())
                        },
                        valueRange = 5f..60f,
                        steps = 54
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Stille-Schwellenwert: ${silenceThresholdSliderValue.roundToInt()}")
                    Slider(
                        value = silenceThresholdSliderValue,
                        onValueChange = { silenceThresholdSliderValue = it },
                        onValueChangeFinished = {
                            onSilenceThresholdChanged(silenceThresholdSliderValue.roundToInt())
                        },
                        valueRange = 100f..2000f,
                        steps = 189
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Benötigte Stille: ${silenceTimeSliderValue.roundToInt()} ms")
                    Slider(
                        value = silenceTimeSliderValue,
                        onValueChange = { silenceTimeSliderValue = it },
                        onValueChangeFinished = {
                            onSilenceTimeRequiredChanged(silenceTimeSliderValue.roundToInt())
                        },
                        valueRange = 500f..5000f,
                        steps = 89
                    )
                }
            }
        }
    )
}
