package com.example.voicejournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.voicejournal.ui.screens.SettingsScreen
import com.example.voicejournal.ui.theme.VoicejournalTheme

class SettingsActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext, getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                val daysToShow by viewModel.daysToShow.collectAsState()
                val isGpsTrackingEnabled by viewModel.isGpsTrackingEnabled.collectAsState()
                val isWebServerEnabled by viewModel.isWebServerEnabled.collectAsState()
                val gpsInterval by viewModel.gpsInterval.collectAsState()
                val speechService by viewModel.speechService.collectAsState()
                val googleCloudApiKey by viewModel.googleCloudApiKey.collectAsState()
                val maxRecordingTime by viewModel.maxRecordingTime.collectAsState()
                val silenceThreshold by viewModel.silenceThreshold.collectAsState()
                val silenceTimeRequired by viewModel.silenceTimeRequired.collectAsState()
                val truncationLength by viewModel.truncationLength.collectAsState()

                SettingsScreen(
                    currentDays = daysToShow,
                    isGpsTrackingEnabled = isGpsTrackingEnabled,
                    isWebServerEnabled = isWebServerEnabled,
                    gpsInterval = gpsInterval,
                    currentSpeechService = speechService,
                    currentApiKey = googleCloudApiKey,
                    maxRecordingTime = maxRecordingTime,
                    silenceThreshold = silenceThreshold,
                    silenceTimeRequired = silenceTimeRequired,
                    truncationLength = truncationLength,
                    onDaysChanged = viewModel::saveDaysToShow,
                    onGpsEnableChanged = viewModel::saveGpsTrackingEnabled,
                    onWebServerEnableChanged = viewModel::saveWebServerEnabled,
                    onGpsIntervalChanged = viewModel::saveGpsInterval,
                    onSpeechServiceChanged = viewModel::saveSpeechService,
                    onApiKeyChanged = viewModel::saveApiKey,
                    onMaxRecordingTimeChanged = viewModel::saveMaxRecordingTime,
                    onSilenceThresholdChanged = viewModel::saveSilenceThreshold,
                    onSilenceTimeRequiredChanged = viewModel::saveSilenceTimeRequired,
                    onTruncationLengthChanged = viewModel::saveTruncationLength,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}
