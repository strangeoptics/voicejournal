package com.example.voicejournal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.voicejournal.data.SettingsRepository
import com.example.voicejournal.data.SpeechService
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val applicationContext: Context
) : ViewModel() {

    val isGpsTrackingEnabled: StateFlow<Boolean> = settingsRepository.isGpsTrackingEnabled
    val isWebServerEnabled: StateFlow<Boolean> = settingsRepository.isWebServerEnabled
    val gpsInterval: StateFlow<Int> = settingsRepository.gpsIntervalMinutes
    val speechService: StateFlow<SpeechService> = settingsRepository.speechService
    val googleCloudApiKey: StateFlow<String> = settingsRepository.googleCloudApiKey
    val maxRecordingTime: StateFlow<Int> = settingsRepository.maxRecordingTime
    val silenceThreshold: StateFlow<Int> = settingsRepository.silenceThreshold
    val silenceTimeRequired: StateFlow<Int> = settingsRepository.silenceTimeRequired
    val truncationLength: StateFlow<Int> = settingsRepository.truncationLength
    val isDeveloperModeEnabled: StateFlow<Boolean> = settingsRepository.isDeveloperModeEnabled

    fun saveGpsTrackingEnabled(isEnabled: Boolean) {
        settingsRepository.setGpsTrackingEnabled(isEnabled)
        VoiceJournalApplication.setupLocationWorker(
            context = applicationContext,
            replaceExisting = true,
            isAppInForeground = true
        )
    }

    fun saveWebServerEnabled(isEnabled: Boolean) {
        settingsRepository.setWebServerEnabled(isEnabled)
        if (isEnabled) {
            WebServerService.start(applicationContext)
        } else {
            WebServerService.stop(applicationContext)
        }
    }

    fun saveGpsInterval(interval: Int) {
        settingsRepository.setGpsIntervalMinutes(interval)
        VoiceJournalApplication.setupLocationWorker(
            context = applicationContext,
            replaceExisting = true,
            isAppInForeground = true
        )
    }

    fun saveSpeechService(service: SpeechService) {
        settingsRepository.setSpeechService(service)
    }

    fun saveApiKey(apiKey: String) {
        settingsRepository.saveGoogleCloudApiKey(apiKey)
    }

    fun saveMaxRecordingTime(time: Int) {
        settingsRepository.setMaxRecordingTime(time)
    }

    fun saveSilenceThreshold(threshold: Int) {
        settingsRepository.setSilenceThreshold(threshold)
    }

    fun saveSilenceTimeRequired(time: Int) {
        settingsRepository.setSilenceTimeRequired(time)
    }

    fun saveTruncationLength(length: Int) {
        settingsRepository.setTruncationLength(length)
    }

    fun saveDeveloperModeEnabled(isEnabled: Boolean) {
        settingsRepository.setDeveloperModeEnabled(isEnabled)
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val appContext = context.applicationContext
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                settingsRepository = Injector.provideSettingsRepository(appContext),
                applicationContext = appContext
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
