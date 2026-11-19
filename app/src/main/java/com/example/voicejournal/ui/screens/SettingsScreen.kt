package com.example.voicejournal.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.voicejournal.R
import com.example.voicejournal.data.SettingsRepository
import com.example.voicejournal.data.SpeechService
import com.example.voicejournal.ui.theme.VoicejournalTheme
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

    val minGpsMinutes = SettingsRepository.MIN_GPS_INTERVAL_MINUTES
    val maxGpsMinutes = SettingsRepository.MAX_GPS_INTERVAL_MINUTES
    val clampedGpsInterval = gpsInterval.coerceIn(minGpsMinutes, maxGpsMinutes)
    var gpsSliderValue by remember(clampedGpsInterval) { mutableFloatStateOf(clampedGpsInterval.toFloat()) }
    val selectedGpsMinutes = gpsSliderValue.roundToInt()
    val isIntensiveGpsMode = selectedGpsMinutes < LocationWorker.MIN_INTERVAL_MINUTES
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isGpsTrackingEnabled) 1f else 0.6f)
                ) {
                    Text(
                        text = stringResource(R.string.gps_interval_label, selectedGpsMinutes),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (isIntensiveGpsMode) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (isIntensiveGpsMode) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (isIntensiveGpsMode) {
                                stringResource(R.string.gps_mode_badge_intensive)
                            } else {
                                stringResource(R.string.gps_mode_badge_eco)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Slider(
                    value = gpsSliderValue,
                    onValueChange = { gpsSliderValue = it },
                    onValueChangeFinished = {
                        onGpsIntervalChanged(
                            gpsSliderValue.roundToInt().coerceIn(minGpsMinutes, maxGpsMinutes)
                        )
                    },
                    valueRange = minGpsMinutes.toFloat()..maxGpsMinutes.toFloat(),
                    steps = maxGpsMinutes - minGpsMinutes - 1,
                    enabled = isGpsTrackingEnabled,
                    colors = if (isIntensiveGpsMode) {
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.error,
                            activeTrackColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        SliderDefaults.colors()
                    }
                )
                AnimatedVisibility(visible = isIntensiveGpsMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .alpha(if (isGpsTrackingEnabled) 1f else 0.6f)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.gps_battery_warning_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.gps_battery_warning_body),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = !isIntensiveGpsMode) {
                    Text(
                        text = stringResource(R.string.gps_eco_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .alpha(if (isGpsTrackingEnabled) 1f else 0.6f)
                    )
                }

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

@Preview(name = "Settings - GPS Intensiv (5 Min.)", locale = "de")
@Composable
private fun SettingsScreenIntensiveGpsPreview() {
    VoicejournalTheme {
        SettingsScreen(
            isGpsTrackingEnabled = true,
            isWebServerEnabled = false,
            ipAddress = "192.168.1.10",
            dnsName = "pixel",
            gpsInterval = 5,
            currentSpeechService = SpeechService.ANDROID,
            currentApiKey = "",
            maxRecordingTime = 15,
            silenceThreshold = 500,
            silenceTimeRequired = 2000,
            truncationLength = 160,
            isDeveloperModeEnabled = false,
            onGpsEnableChanged = {},
            onWebServerEnableChanged = {},
            onGpsIntervalChanged = {},
            onSpeechServiceChanged = {},
            onApiKeyChanged = {},
            onMaxRecordingTimeChanged = {},
            onSilenceThresholdChanged = {},
            onSilenceTimeRequiredChanged = {},
            onTruncationLengthChanged = {},
            onDeveloperModeEnableChanged = {},
            onBackPressed = {}
        )
    }
}

@Preview(name = "Settings - GPS Akkuschonend (15 Min.)", locale = "de")
@Composable
private fun SettingsScreenEcoGpsPreview() {
    VoicejournalTheme {
        SettingsScreen(
            isGpsTrackingEnabled = true,
            isWebServerEnabled = false,
            ipAddress = "192.168.1.10",
            dnsName = "pixel",
            gpsInterval = 15,
            currentSpeechService = SpeechService.ANDROID,
            currentApiKey = "",
            maxRecordingTime = 15,
            silenceThreshold = 500,
            silenceTimeRequired = 2000,
            truncationLength = 160,
            isDeveloperModeEnabled = false,
            onGpsEnableChanged = {},
            onWebServerEnableChanged = {},
            onGpsIntervalChanged = {},
            onSpeechServiceChanged = {},
            onApiKeyChanged = {},
            onMaxRecordingTimeChanged = {},
            onSilenceThresholdChanged = {},
            onSilenceTimeRequiredChanged = {},
            onTruncationLengthChanged = {},
            onDeveloperModeEnableChanged = {},
            onBackPressed = {}
        )
    }
}
