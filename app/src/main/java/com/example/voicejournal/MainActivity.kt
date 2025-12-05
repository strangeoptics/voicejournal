package com.example.voicejournal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.ui.components.AppDrawer
import com.example.voicejournal.ui.screens.HomeScreen
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.NotificationHelper
import com.example.voicejournal.util.SpeechRecognitionManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext, getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        speechRecognitionManager = SpeechRecognitionManager(
            context = this,
            onTextRecognized = { recognizedText ->
                viewModel.processRecognizedText(recognizedText)
            },
            scope = lifecycleScope,
            onError = { error ->
                runOnUiThread {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error or invalid API key"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "An unknown error occurred ($error)"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        )

        setContent {
            VoicejournalTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                val category by viewModel.selectedCategory.collectAsState()
                val groupedEntries by viewModel.groupedEntries.collectAsState()
                val selectedEntry by viewModel.selectedEntry.collectAsState()
                val selectedDate by viewModel.selectedDate.collectAsState()
                val isGpsTrackingEnabled by viewModel.isGpsTrackingEnabled.collectAsState()
                val filteredEntries by viewModel.filteredEntries.collectAsState()
                val canUndo by viewModel.canUndo.collectAsState()
                val categories by viewModel.categories.collectAsState()
                val shouldShowMoreButton by viewModel.shouldShowMoreButton.collectAsState()
                val gpsTrackPoints by viewModel.gpsTrackPoints.collectAsState()
                val hasGpsTrackForSelectedDate by viewModel.hasGpsTrackForSelectedDate.collectAsState()
                val isRecording by speechRecognitionManager.isRecording.collectAsState()
                val truncationLength by viewModel.truncationLength.collectAsState()


                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var showDeleteConfirmationDialog by remember { mutableStateOf(false) }


                if (showDeleteConfirmationDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmationDialog = false },
                        title = { Text("Delete All Data") },
                        text = { Text("Are you sure you want to delete all journal entries and categories? This action cannot be undone.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.deleteAllData()
                                    showDeleteConfirmationDialog = false
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }


                // Permission launchers...
                val backgroundLocationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            Toast.makeText(this, "Background location permission granted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Background location is needed for tracking when the app is closed.", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissions ->
                        val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                        val isCoarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

                        if (isFineLocationGranted || isCoarseLocationGranted) {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        } else {
                            Toast.makeText(this, "Location permission is required for GPS tracking.", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                 LaunchedEffect(Unit) {
                    if (isGpsTrackingEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }


                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let {
                            scope.launch {
                                try {
                                    viewModel.importJournal(it)
                                    Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                    onResult = { uri ->
                        uri?.let {
                            scope.launch {
                                try {
                                    viewModel.exportJournal(it)
                                    Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )

                val textToShow = filteredEntries.joinToString("\n") { entryWithCategories ->
                    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(entryWithCategories.entry.start_datetime), ZoneId.systemDefault())
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    "[${date.format(formatter)}] ${entryWithCategories.entry.content}"
                }
                val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            startListening()
                        }
                    }
                )
                val notificationPermissions = arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.RECORD_AUDIO
                )
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissionsMap ->
                        val allGranted = permissionsMap.values.all { it }
                        if (allGranted) {
                            NotificationHelper.showNotification(context)
                        }
                    }
                )

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(
                            onSettingsClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            },
                            onManageCategoriesClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, CategoryManagerActivity::class.java))
                            },
                            onImportJournalClicked = {
                                scope.launch { drawerState.close() }
                                filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
                            },
                            onExportJournalClicked = {
                                scope.launch { drawerState.close() }
                                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
                                val fileName = "journal_${LocalDateTime.now().format(formatter)}.json"
                                exportLauncher.launch(fileName)
                            },
                            onShowNotificationClicked = {
                                scope.launch {
                                    val allPermissionsGranted = notificationPermissions.all {
                                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (allPermissionsGranted) {
                                        NotificationHelper.showNotification(context)
                                    } else {
                                        notificationPermissionLauncher.launch(notificationPermissions)
                                    }
                                }
                                scope.launch { drawerState.close() }
                            },
                            onAddTestDataClicked = {
                                viewModel.addTestData()
                                scope.launch { drawerState.close() }
                            },
                            onShowGpsTrackClicked = {
                                scope.launch { drawerState.close() }
                                openGoogleMapsWithTrack(gpsTrackPoints)
                            },
                            onDeleteAllClicked = {
                                scope.launch { drawerState.close() }
                                showDeleteConfirmationDialog = true
                            }
                        )
                    },
                    content = {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = { Text("Voice Journal") },
                                    navigationIcon = {
                                        IconButton(onClick = {
                                            scope.launch { drawerState.open() }
                                        }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = "Navigation Menu")
                                        }
                                    },
                                    actions = {
                                        if (hasGpsTrackForSelectedDate) {
                                            IconButton(onClick = { openGoogleMapsWithTrack(gpsTrackPoints) }) {
                                                Icon(Icons.Filled.Map, contentDescription = "Show GPS Track on Map")
                                            }
                                        }
                                        if (canUndo) {
                                            IconButton(onClick = viewModel::onUndoDelete) {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                            }
                                        }
                                        IconButton(onClick = {
                                            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("VoiceJournal", textToShow)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Filled.ContentPaste, contentDescription = "In die Zwischenablage kopiert")
                                        }
                                    }
                                )
                            },
                            floatingActionButton = {
                                FloatingActionButton(onClick = {
                                    if (isRecording) {
                                        speechRecognitionManager.stopListening()
                                    } else {
                                        when {
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED -> {
                                                startListening()
                                            }

                                            else -> {
                                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    }
                                }) {
                                    if (isRecording) {
                                        Icon(Icons.Filled.Stop, contentDescription = "Stop recording")
                                    } else {
                                        Icon(Icons.Filled.Add, contentDescription = "Sprechen")
                                    }

                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = "home",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable("home") {
                                    HomeScreen(
                                        modifier = Modifier,
                                        groupedEntries = groupedEntries,
                                        categories = categories,
                                        selectedCategory = category,
                                        onCategoryChange = viewModel::onCategoryChange,
                                        onDeleteEntry = viewModel::onDeleteEntry,
                                        selectedEntry = selectedEntry,
                                        selectedDate = selectedDate,
                                        onDateSelected = viewModel::onDateSelected,
                                        onEntrySelected = viewModel::onEntrySelected,
                                        onEditEntry = { entryToEdit ->
                                            val intent = EditEntryActivity.newIntent(context, entryToEdit.entry.id)
                                            context.startActivity(intent)
                                        },
                                        onMoreClicked = viewModel::onMoreClicked,
                                        onDateLongClicked = { date ->
                                            openGooglePhotos(date)
                                        },
                                        onPhotoIconClicked = { entryWithCategories ->
                                            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(entryWithCategories.entry.start_datetime), ZoneId.systemDefault()).toLocalDate()
                                            openGooglePhotos(date)
                                        },
                                        shouldShowMoreButton = shouldShowMoreButton,
                                        truncationLength = truncationLength
                                    )
                                 }
                            }
                        }
                    }
                )
            }
        }
    }

    fun startListening() {
        val service = viewModel.speechService.value
        val apiKey = viewModel.googleCloudApiKey.value
        val maxRecordingTime = viewModel.maxRecordingTime.value
        val silenceThreshold = viewModel.silenceThreshold.value
        val silenceTimeRequired = viewModel.silenceTimeRequired.value
        speechRecognitionManager.startListening(
            service = service,
            apiKey = apiKey,
            maxRecordingTimeSeconds = maxRecordingTime,
            silenceThreshold = silenceThreshold,
            silenceTimeRequired = silenceTimeRequired
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognitionManager.destroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }


    private fun handleIntent(intent: Intent) {
        if (intent.action == NotificationHelper.NOTIFICATION_ACTION) {
            handleNotificationAction()
        }
    }

    private fun handleNotificationAction() {
        runOnUiThread {
            Toast.makeText(this, "Starting speech recognition...", Toast.LENGTH_SHORT).show()
            startListening()
        }
    }

    private fun openGooglePhotos(date: LocalDate) {
        val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.getDefault())
        val url = "https://photos.google.com/search/${date.format(formatter)}"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open browser.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGoogleMapsWithTrack(points: List<GpsTrackPoint>) {
        if (points.size < 2) {
            Toast.makeText(this, "Not enough GPS points to show a track.", Toast.LENGTH_SHORT).show()
            return
        }

        val origin = "${points.first().latitude},${points.first().longitude}"
        val destination = "${points.last().latitude},${points.last().longitude}"
        val waypoints = points.subList(1, points.size - 1).joinToString("|") { "${it.latitude},${it.longitude}" }

        val url = "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&waypoints=$waypoints&travelmode=driving"

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Google Maps.", Toast.LENGTH_SHORT).show()
        }
    }
}
