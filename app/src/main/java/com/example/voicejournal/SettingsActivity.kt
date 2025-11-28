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
                val gpsInterval by viewModel.gpsInterval.collectAsState()
                val speechService by viewModel.speechService.collectAsState()
                val googleCloudApiKey by viewModel.googleCloudApiKey.collectAsState()
                val maxRecordingTime by viewModel.maxRecordingTime.collectAsState()

                SettingsScreen(
                    currentDays = daysToShow,
                    isGpsTrackingEnabled = isGpsTrackingEnabled,
                    gpsInterval = gpsInterval,
                    currentSpeechService = speechService,
                    currentApiKey = googleCloudApiKey,
                    maxRecordingTime = maxRecordingTime,
                    onSave = { days, isGpsEnabled, interval, service, apiKey, recordingTime ->
                        viewModel.saveSettings(days, isGpsEnabled, interval, service, apiKey, recordingTime)
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }
}
