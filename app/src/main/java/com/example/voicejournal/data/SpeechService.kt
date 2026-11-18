package com.example.voicejournal.data

enum class SpeechService(
    val prefValue: String,
    val displayLabel: String
) {
    ANDROID(prefValue = "ANDROID", displayLabel = "Android SpeechRecognizer"),
    GOOGLE_CLOUD(prefValue = "GOOGLE_CLOUD", displayLabel = "Google Cloud Speech API");

    companion object {
        fun fromPrefValue(value: String?): SpeechService {
            return entries.find { it.prefValue.equals(value?.trim(), ignoreCase = true) } ?: ANDROID
        }
    }
}
