package com.example.voicejournal.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

class SpeechRecognitionManager(
    private val context: Context,
    private val onTextRecognized: (String) -> Unit,
    private val scope: CoroutineScope,
    private val onError: ((Int) -> Unit)? = null
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false

    init {
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches.isNullOrEmpty()) {
                        onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH)
                        return
                    }

                    val recognizedText = matches[0].trim()
                    if (recognizedText.isEmpty()) {
                        onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH)
                        return
                    }
                    onTextRecognized(capitalizeFirstLetter(recognizedText))
                }

                override fun onError(error: Int) {
                    onError?.invoke(error)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(service: String = "ANDROID", apiKey: String? = null, maxRecordingTimeSeconds: Int = 15) {
        if (service == "GOOGLE_CLOUD" && !apiKey.isNullOrEmpty()) {
            startGoogleCloudListening(apiKey, maxRecordingTimeSeconds)
        } else {
            startAndroidListening()
        }
    }

    private fun startAndroidListening() {
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        speechRecognizer?.startListening(speechRecognizerIntent)
    }

    private fun startGoogleCloudListening(apiKey: String, maxRecordingTimeSeconds: Int) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError?.invoke(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = minBufferSize * 2

                val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)

                val audioData = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)

                audioRecord.startRecording()
                isRecording = true

                val startTime = System.currentTimeMillis()
                val maxRecordingTime = maxRecordingTimeSeconds * 1000 // Convert to milliseconds
                val silenceThreshold = 500 // Amplitude threshold for silence
                val silenceTimeRequired = 2000 // 2 seconds of silence to stop
                var lastSoundTime = System.currentTimeMillis()

                while (isRecording && (System.currentTimeMillis() - startTime < maxRecordingTime)) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        audioData.write(buffer, 0, read)

                        var hasSound = false
                        var i = 0
                        while (i < read) {
                            val lowByte = buffer[i].toInt() and 0xFF
                            val highByte = buffer[i + 1].toInt()
                            val sample = (highByte shl 8) or lowByte
                            if (abs(sample.toShort().toInt()) > silenceThreshold) {
                                hasSound = true
                                break
                            }
                            i += 2
                        }

                        if (hasSound) {
                            lastSoundTime = System.currentTimeMillis()
                        } else {
                            if (System.currentTimeMillis() - lastSoundTime > silenceTimeRequired) {
                                break // Stop recording due to silence
                            }
                        }
                    } else if (read < 0) {
                        Log.e("SpeechRecognitionManager", "AudioRecord read error: $read")
                        break
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                isRecording = false

                if (audioData.size() == 0) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(SpeechRecognizer.ERROR_AUDIO)
                    }
                    return@launch
                }

                val audioBytes = audioData.toByteArray()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                sendToGoogleCloud(base64Audio, apiKey)

            } catch (e: Exception) {
                Log.e("SpeechRecognitionManager", "Error during Google Cloud listening", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(SpeechRecognizer.ERROR_NETWORK)
                }
            }
        }
    }

    private suspend fun sendToGoogleCloud(base64Audio: String, apiKey: String) {
        val url = URL("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
        val json = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", 16000)
                put("languageCode", Locale.getDefault().toLanguageTag())
            })
            put("audio", JSONObject().apply {
                put("content", base64Audio)
            })
        }

        try {
            val result = withContext(Dispatchers.IO) {
                (url.openConnection() as HttpURLConnection).run {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { os ->
                        OutputStreamWriter(os).use { osw ->
                            osw.write(json.toString())
                        }
                    }

                    if (responseCode == 200) {
                        inputStream.bufferedReader().use { it.readText() }
                    } else {
                        Log.e("SpeechRecognitionManager", "Google API Error: $responseCode - ${errorStream.bufferedReader().use { it.readText() }}")
                        null
                    }
                }
            }

            if (result != null) {
                val jsonResponse = JSONObject(result)
                val results = jsonResponse.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val transcript = results.getJSONObject(0).getJSONArray("alternatives").getJSONObject(0).getString("transcript")
                    withContext(Dispatchers.Main) {
                        onTextRecognized(capitalizeFirstLetter(transcript.trim()))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError?.invoke(SpeechRecognizer.ERROR_NETWORK)
                }
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error sending to Google Cloud", e)
            withContext(Dispatchers.Main) {
                onError?.invoke(SpeechRecognizer.ERROR_NETWORK)
            }
        }
    }

    private fun capitalizeFirstLetter(text: String): String {
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecording = false
    }
}