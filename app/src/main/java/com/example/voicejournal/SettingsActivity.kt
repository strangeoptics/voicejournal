package com.example.voicejournal

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.voicejournal.ui.screens.SettingsScreen
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.util.Formatter

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
                val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsState()

                val ipAddress = getIpAddress(applicationContext) ?: "Unavailable"

                SettingsScreen(
                    currentDays = daysToShow,
                    isGpsTrackingEnabled = isGpsTrackingEnabled,
                    isWebServerEnabled = isWebServerEnabled,
                    ipAddress = ipAddress,
                    gpsInterval = gpsInterval,
                    currentSpeechService = speechService,
                    currentApiKey = googleCloudApiKey,
                    maxRecordingTime = maxRecordingTime,
                    silenceThreshold = silenceThreshold,
                    silenceTimeRequired = silenceTimeRequired,
                    truncationLength = truncationLength,
                    isDeveloperModeEnabled = isDeveloperModeEnabled,
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
                    onDeveloperModeEnableChanged = viewModel::saveDeveloperModeEnabled,
                    onBackPressed = { finish() }
                )
            }
        }
    }

    private fun getIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress == 0) return null
        return (ipAddress and 0xFF).toString() + "." +
               (ipAddress shr 8 and 0xFF) + "." +
               (ipAddress shr 16 and 0xFF) + "." +
               (ipAddress shr 24 and 0xFF)
    }
}
