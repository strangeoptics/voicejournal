package com.example.voicejournal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.example.voicejournal.ui.screens.SettingsScreen
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.net.Inet4Address
import java.net.NetworkInterface

class SettingsActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext, getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
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

                val ipAddress = remember(isWebServerEnabled) {
                    getIpAddress(applicationContext) ?: "Unavailable"
                }
                val dnsName = remember { getDnsName(applicationContext) }

                val backgroundLocationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (!isGranted) {
                            Toast.makeText(
                                this,
                                "Hintergrund-Standort wird für GPS-Tracking bei geschlossener App empfohlen.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissions ->
                        val granted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                            (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
                        if (granted) {
                            viewModel.saveGpsTrackingEnabled(true)
                            if (ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        } else {
                            viewModel.saveGpsTrackingEnabled(false)
                            Toast.makeText(
                                this,
                                "Standort-Berechtigung wird für GPS-Tracking benötigt.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )

                SettingsScreen(
                    isGpsTrackingEnabled = isGpsTrackingEnabled,
                    isWebServerEnabled = isWebServerEnabled,
                    ipAddress = ipAddress,
                    dnsName = dnsName,
                    gpsInterval = gpsInterval,
                    currentSpeechService = speechService,
                    currentApiKey = googleCloudApiKey,
                    maxRecordingTime = maxRecordingTime,
                    silenceThreshold = silenceThreshold,
                    silenceTimeRequired = silenceTimeRequired,
                    truncationLength = truncationLength,
                    isDeveloperModeEnabled = isDeveloperModeEnabled,
                    onGpsEnableChanged = { enabled ->
                        if (!enabled) {
                            viewModel.saveGpsTrackingEnabled(false)
                        } else {
                            val hasForegroundLocation = ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasForegroundLocation) {
                                viewModel.saveGpsTrackingEnabled(true)
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    },
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
        val activeIpv4 = linkProperties?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
        if (!activeIpv4.isNullOrBlank()) {
            return activeIpv4
        }

        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun getDnsName(context: Context): String {
        return runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
}
