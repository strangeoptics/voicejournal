package com.example.voicejournal.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import kotlin.math.abs

class SpeechRecognitionManager(
    context: Context,
    private val onTextRecognized: (String) -> Unit,
    private val scope: CoroutineScope,
    private val onError: ((Int) -> Unit)? = null
) {

    private val appContext: Context = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var recordingJob: Job? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    _isRecording.value = false
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
                    _isRecording.value = false
                    onError?.invoke(error)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isRecording.value = false
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(
        service: String = "ANDROID",
        apiKey: String? = null,
        maxRecordingTimeSeconds: Int = 15,
        silenceThreshold: Int = 500,
        silenceTimeRequired: Int = 2000
    ) {
        if (_isRecording.value) {
            return
        }
        if (service == "GOOGLE_CLOUD" && !apiKey.isNullOrEmpty()) {
            startGoogleCloudListening(apiKey, maxRecordingTimeSeconds, silenceThreshold, silenceTimeRequired)
        } else {
            startAndroidListening()
        }
    }

    fun stopListening() {
        if (_isRecording.value) {
            // For Google Cloud, setting _isRecording to false exits the PCM read loop cleanly
            _isRecording.value = false
            // For Android SpeechRecognizer
            scope.launch(Dispatchers.Main) {
                speechRecognizer?.stopListening()
            }
        }
    }

    private fun startAndroidListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        _isRecording.value = true
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            recognizer.startListening(speechRecognizerIntent)
        } else {
            scope.launch(Dispatchers.Main) {
                recognizer.startListening(speechRecognizerIntent)
            }
        }
    }

    private fun startGoogleCloudListening(
        apiKey: String,
        maxRecordingTimeSeconds: Int,
        silenceThreshold: Int,
        silenceTimeRequired: Int
    ) {
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError?.invoke(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        _isRecording.value = true
        recordingJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            val audioData = ByteArrayOutputStream()
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                if (minBufferSize <= 0) {
                    Log.e("SpeechRecognitionManager", "Invalid minBufferSize from AudioRecord: $minBufferSize")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(SpeechRecognizer.ERROR_AUDIO)
                    }
                    return@launch
                }
                val bufferSize = minBufferSize * 2

                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                audioRecord = record

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("SpeechRecognitionManager", "AudioRecord failed to initialize (state=${record.state})")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(SpeechRecognizer.ERROR_AUDIO)
                    }
                    return@launch
                }

                val buffer = ByteArray(bufferSize)
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e("SpeechRecognitionManager", "AudioRecord failed to start recording")
                    withContext(Dispatchers.Main) {
                        onError?.invoke(SpeechRecognizer.ERROR_AUDIO)
                    }
                    return@launch
                }

                val startTime = System.currentTimeMillis()
                val maxRecordingTimeMs = maxRecordingTimeSeconds.coerceAtLeast(1) * 1000L
                var lastSoundTime = startTime

                while (isActive && _isRecording.value && (System.currentTimeMillis() - startTime < maxRecordingTimeMs)) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        audioData.write(buffer, 0, read)

                        var hasSound = false
                        var i = 0
                        // Ensure i + 1 < read so odd byte counts never cause IndexOutOfBoundsException
                        while (i + 1 < read) {
                            val lowByte = buffer[i].toInt() and 0xFF
                            val highByte = buffer[i + 1].toInt()
                            val sample = ((highByte shl 8) or lowByte).toShort().toInt()
                            if (abs(sample) > silenceThreshold) {
                                hasSound = true
                                break
                            }
                            i += 2
                        }

                        if (hasSound) {
                            lastSoundTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - lastSoundTime > silenceTimeRequired) {
                            break // Stop recording due to silence
                        }
                    } else if (read < 0) {
                        Log.e("SpeechRecognitionManager", "AudioRecord read error: $read")
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SpeechRecognitionManager", "Error during Google Cloud audio recording", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(SpeechRecognizer.ERROR_AUDIO)
                }
                return@launch
            } finally {
                _isRecording.value = false
                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            record.stop()
                        } catch (e: IllegalStateException) {
                            Log.w("SpeechRecognitionManager", "Error stopping AudioRecord", e)
                        }
                    }
                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.w("SpeechRecognitionManager", "Error releasing AudioRecord", e)
                    }
                }
            }

            if (audioData.size() == 0) {
                withContext(Dispatchers.Main) {
                    onError?.invoke(SpeechRecognizer.ERROR_AUDIO)
                }
                return@launch
            }

            val base64Audio = Base64.encodeToString(audioData.toByteArray(), Base64.NO_WRAP)
            sendToGoogleCloud(base64Audio, apiKey)
        }
    }

    private suspend fun sendToGoogleCloud(base64Audio: String, apiKey: String) {
        val url = URL("https://speech.googleapis.com/v1/speech:recognize")
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
                val connection = (url.openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.setRequestProperty("X-Goog-Api-Key", apiKey.trim())

                    connection.outputStream.use { os ->
                        OutputStreamWriter(os, Charsets.UTF_8).use { osw ->
                            osw.write(json.toString())
                        }
                    }

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                        Log.e("SpeechRecognitionManager", "Google API Error: ${connection.responseCode} - $errorBody")
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (result != null) {
                val jsonResponse = JSONObject(result)
                val results = jsonResponse.optJSONArray("results")
                val transcript = results
                    ?.optJSONObject(0)
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                    ?.optString("transcript", "")
                    ?.trim()
                    .orEmpty()

                if (transcript.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onTextRecognized(capitalizeFirstLetter(transcript))
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e("SpeechRecognitionManager", "Timeout sending audio to Google Cloud", e)
            withContext(Dispatchers.Main) {
                onError?.invoke(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
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
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        speechRecognizer?.let { recognizer ->
            recognizer.setRecognitionListener(null)
            try {
                recognizer.cancel()
            } catch (_: Exception) {
            }
            try {
                recognizer.destroy()
            } catch (_: Exception) {
            }
        }
        speechRecognizer = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}