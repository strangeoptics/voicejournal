package com.example.voicejournal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.voicejournal.LocationTrackingService
import com.example.voicejournal.util.SecureApiKeyStore
import com.example.voicejournal.worker.LocationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(
    context: Context,
    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) {

    private val appContext: Context = context.applicationContext

    companion object {
        const val PREFS_NAME = "voice_journal_prefs"
        const val KEY_DEFAULT_CATEGORIES_ADDED = "default_categories_added"
        const val KEY_GPS_TRACKING_ENABLED = "gps_tracking_enabled"
        const val KEY_GPS_INTERVAL_MINUTES = "gps_interval_minutes"
        const val KEY_SPEECH_SERVICE = "speech_service"
        const val KEY_MAX_RECORDING_TIME = "max_recording_time"
        const val KEY_SILENCE_THRESHOLD = "silence_threshold"
        const val KEY_SILENCE_TIME_REQUIRED = "silence_time_required"
        const val KEY_TRUNCATION_LENGTH = "truncation_length"
        const val KEY_WEBSERVER_ENABLED = "webserver_enabled"
        const val KEY_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        const val KEY_SHOW_CATEGORY_TAGS = "show_category_tags"

        const val DEFAULT_GPS_INTERVAL_MINUTES = LocationWorker.MIN_INTERVAL_MINUTES
        const val MIN_GPS_INTERVAL_MINUTES = LocationTrackingService.MIN_SERVICE_INTERVAL_MINUTES
        const val MAX_GPS_INTERVAL_MINUTES = 60
        const val DEFAULT_MAX_RECORDING_TIME_SECONDS = 15
        const val DEFAULT_SILENCE_THRESHOLD = 500
        const val DEFAULT_SILENCE_TIME_REQUIRED_MS = 2000
        const val DEFAULT_TRUNCATION_LENGTH = 160
    }

    private val _isGpsTrackingEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_GPS_TRACKING_ENABLED, false)
    )
    val isGpsTrackingEnabled: StateFlow<Boolean> = _isGpsTrackingEnabled.asStateFlow()

    private val _isWebServerEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_WEBSERVER_ENABLED, false)
    )
    val isWebServerEnabled: StateFlow<Boolean> = _isWebServerEnabled.asStateFlow()

    private val _gpsIntervalMinutes = MutableStateFlow(
        sharedPreferences.getInt(KEY_GPS_INTERVAL_MINUTES, DEFAULT_GPS_INTERVAL_MINUTES)
            .coerceIn(MIN_GPS_INTERVAL_MINUTES, MAX_GPS_INTERVAL_MINUTES)
    )
    val gpsIntervalMinutes: StateFlow<Int> = _gpsIntervalMinutes.asStateFlow()

    private val _speechService = MutableStateFlow(
        SpeechService.fromPrefValue(sharedPreferences.getString(KEY_SPEECH_SERVICE, SpeechService.ANDROID.prefValue))
    )
    val speechService: StateFlow<SpeechService> = _speechService.asStateFlow()

    val googleCloudApiKey: StateFlow<String> = SecureApiKeyStore.apiKeyFlow

    private val _maxRecordingTime = MutableStateFlow(
        sharedPreferences.getInt(KEY_MAX_RECORDING_TIME, DEFAULT_MAX_RECORDING_TIME_SECONDS)
    )
    val maxRecordingTime: StateFlow<Int> = _maxRecordingTime.asStateFlow()

    private val _silenceThreshold = MutableStateFlow(
        sharedPreferences.getInt(KEY_SILENCE_THRESHOLD, DEFAULT_SILENCE_THRESHOLD)
    )
    val silenceThreshold: StateFlow<Int> = _silenceThreshold.asStateFlow()

    private val _silenceTimeRequired = MutableStateFlow(
        sharedPreferences.getInt(KEY_SILENCE_TIME_REQUIRED, DEFAULT_SILENCE_TIME_REQUIRED_MS)
    )
    val silenceTimeRequired: StateFlow<Int> = _silenceTimeRequired.asStateFlow()

    private val _truncationLength = MutableStateFlow(
        sharedPreferences.getInt(KEY_TRUNCATION_LENGTH, DEFAULT_TRUNCATION_LENGTH)
    )
    val truncationLength: StateFlow<Int> = _truncationLength.asStateFlow()

    private val _isDeveloperModeEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false)
    )
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    private val _showCategoryTags = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_SHOW_CATEGORY_TAGS, true)
    )
    val showCategoryTags: StateFlow<Boolean> = _showCategoryTags.asStateFlow()

    // Held strongly on the singleton repository so SharedPreferences' WeakHashMap never drops it
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_GPS_TRACKING_ENABLED -> _isGpsTrackingEnabled.value = prefs.getBoolean(key, false)
            KEY_WEBSERVER_ENABLED -> _isWebServerEnabled.value = prefs.getBoolean(key, false)
            KEY_GPS_INTERVAL_MINUTES -> {
                _gpsIntervalMinutes.value = prefs.getInt(key, DEFAULT_GPS_INTERVAL_MINUTES)
                    .coerceIn(MIN_GPS_INTERVAL_MINUTES, MAX_GPS_INTERVAL_MINUTES)
            }
            KEY_SPEECH_SERVICE -> {
                _speechService.value = SpeechService.fromPrefValue(
                    prefs.getString(key, SpeechService.ANDROID.prefValue)
                )
            }
            KEY_MAX_RECORDING_TIME -> {
                _maxRecordingTime.value = prefs.getInt(key, DEFAULT_MAX_RECORDING_TIME_SECONDS)
            }
            KEY_SILENCE_THRESHOLD -> {
                _silenceThreshold.value = prefs.getInt(key, DEFAULT_SILENCE_THRESHOLD)
            }
            KEY_SILENCE_TIME_REQUIRED -> {
                _silenceTimeRequired.value = prefs.getInt(key, DEFAULT_SILENCE_TIME_REQUIRED_MS)
            }
            KEY_TRUNCATION_LENGTH -> {
                _truncationLength.value = prefs.getInt(key, DEFAULT_TRUNCATION_LENGTH)
            }
            KEY_DEVELOPER_MODE_ENABLED -> {
                _isDeveloperModeEnabled.value = prefs.getBoolean(key, false)
            }
            KEY_SHOW_CATEGORY_TAGS -> {
                _showCategoryTags.value = prefs.getBoolean(key, true)
            }
        }
    }

    init {
        SecureApiKeyStore.getApiKey(appContext, sharedPreferences)
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun areDefaultCategoriesAdded(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEFAULT_CATEGORIES_ADDED, false)
    }

    fun markDefaultCategoriesAdded() {
        sharedPreferences.edit { putBoolean(KEY_DEFAULT_CATEGORIES_ADDED, true) }
    }

    fun setGpsTrackingEnabled(enabled: Boolean) {
        _isGpsTrackingEnabled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_GPS_TRACKING_ENABLED, enabled) }
    }

    fun setWebServerEnabled(enabled: Boolean) {
        _isWebServerEnabled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_WEBSERVER_ENABLED, enabled) }
    }

    fun setGpsIntervalMinutes(minutes: Int): Int {
        val clamped = minutes.coerceIn(MIN_GPS_INTERVAL_MINUTES, MAX_GPS_INTERVAL_MINUTES)
        _gpsIntervalMinutes.value = clamped
        sharedPreferences.edit { putInt(KEY_GPS_INTERVAL_MINUTES, clamped) }
        return clamped
    }

    fun setSpeechService(service: SpeechService) {
        _speechService.value = service
        sharedPreferences.edit { putString(KEY_SPEECH_SERVICE, service.prefValue) }
    }

    fun saveGoogleCloudApiKey(apiKey: String) {
        SecureApiKeyStore.saveApiKey(appContext, apiKey, sharedPreferences)
    }

    fun setMaxRecordingTime(seconds: Int) {
        _maxRecordingTime.value = seconds
        sharedPreferences.edit { putInt(KEY_MAX_RECORDING_TIME, seconds) }
    }

    fun setSilenceThreshold(threshold: Int) {
        _silenceThreshold.value = threshold
        sharedPreferences.edit { putInt(KEY_SILENCE_THRESHOLD, threshold) }
    }

    fun setSilenceTimeRequired(millis: Int) {
        _silenceTimeRequired.value = millis
        sharedPreferences.edit { putInt(KEY_SILENCE_TIME_REQUIRED, millis) }
    }

    fun setTruncationLength(length: Int) {
        _truncationLength.value = length
        sharedPreferences.edit { putInt(KEY_TRUNCATION_LENGTH, length) }
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _isDeveloperModeEnabled.value = enabled
        sharedPreferences.edit { putBoolean(KEY_DEVELOPER_MODE_ENABLED, enabled) }
    }

    fun toggleShowCategoryTags() {
        val newValue = !_showCategoryTags.value
        _showCategoryTags.value = newValue
        sharedPreferences.edit { putBoolean(KEY_SHOW_CATEGORY_TAGS, newValue) }
    }
}
