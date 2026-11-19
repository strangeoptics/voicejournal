package com.example.voicejournal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.localDate
import com.example.voicejournal.ui.components.AppDrawer
import com.example.voicejournal.ui.components.MainFabMenu
import com.example.voicejournal.ui.components.MainTopAppBar
import com.example.voicejournal.ui.components.TimeZonePickerDialog
import com.example.voicejournal.ui.screens.HomeScreen
import com.example.voicejournal.ui.screens.search.SearchActivity
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import com.example.voicejournal.util.NotificationHelper
import com.example.voicejournal.util.ShareExportHelper
import com.example.voicejournal.util.SpeechRecognitionManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (viewModel.isWebServerEnabled.value) {
            WebServerService.start(this)
        }
        speechRecognitionManager = SpeechRecognitionManager(
            context = applicationContext,
            onTextRecognized = { recognizedText ->
                viewModel.processRecognizedText(recognizedText)
            },
            scope = lifecycleScope,
            onError = { error ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        val errorMessage = SpeechRecognitionManager.errorMessageForCode(error)
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        handleIntent(intent)

        setContent {
            VoicejournalTheme {
                val context = LocalContext.current
                val category by viewModel.selectedCategory.collectAsState()
                val selectedEntry by viewModel.selectedEntry.collectAsState()
                val selectedEntryIds by viewModel.selectedEntryIds.collectAsState()
                val selectedDate by viewModel.selectedDate.collectAsState()
                val isGpsTrackingEnabled by viewModel.isGpsTrackingEnabled.collectAsState()
                val canUndo by viewModel.canUndo.collectAsState()
                val categories by viewModel.categories.collectAsState()
                val gpsTrackPoints by viewModel.gpsTrackPoints.collectAsState()
                val hasGpsTrackForSelectedDate by viewModel.hasGpsTrackForSelectedDate.collectAsState()
                val isRecording by speechRecognitionManager.isRecording.collectAsState()
                val truncationLength by viewModel.truncationLength.collectAsState()
                val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsState()
                val showCategoryTags by viewModel.showCategoryTags.collectAsState()
                val pagedEntries = viewModel.pagedEntries.collectAsLazyPagingItems()
                val scrollToEntryId by viewModel.scrollToEntryId.collectAsState()

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
                var showHardDeleteSelectionDialog by rememberSaveable { mutableStateOf(false) }
                var showBatchTimeZoneDialog by rememberSaveable { mutableStateOf(false) }
                var isFabMenuExpanded by rememberSaveable { mutableStateOf(false) }

                if (showBatchTimeZoneDialog) {
                    TimeZonePickerDialog(
                        selectedZoneId = null,
                        onZoneSelected = { chosenZoneId ->
                            viewModel.updateEntriesZoneId(selectedEntryIds, chosenZoneId)
                            showBatchTimeZoneDialog = false
                            Toast.makeText(context, "Zeitzone aktualisiert", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showBatchTimeZoneDialog = false }
                    )
                }

                if (showHardDeleteSelectionDialog) {
                    AlertDialog(
                        onDismissRequest = { showHardDeleteSelectionDialog = false },
                        title = { Text("Auswahl endgültig löschen") },
                        text = { Text("Möchtest du diese ${selectedEntryIds.size} Einträge wirklich unwiderruflich löschen?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.hardDeleteEntries(selectedEntryIds)
                                    showHardDeleteSelectionDialog = false
                                }
                            ) {
                                Text("Löschen")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showHardDeleteSelectionDialog = false }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }

                if (showDeleteConfirmationDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmationDialog = false },
                        title = { Text("Alle Einträge löschen") },
                        text = { Text("Möchtest du wirklich alle aktiven Tagebucheinträge in den Papierkorb (\"Gelöscht\") verschieben?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.deleteAllData()
                                    showDeleteConfirmationDialog = false
                                }
                            ) {
                                Text("Verschieben")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }

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
                LaunchedEffect(isGpsTrackingEnabled) {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (isGpsTrackingEnabled && !hasFine && !hasCoarse) {
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

                val requestOrStartVoiceRecording = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startListening()
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        AppDrawer(
                            isDeveloperModeEnabled = isDeveloperModeEnabled,
                            onSettingsClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            },
                            onManageCategoriesClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, CategoryManagerActivity::class.java))
                            },
                            onCalendarClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, CalendarActivity::class.java))
                            },
                            onSearchClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, SearchActivity::class.java))
                            },
                            onImportJournalClicked = {
                                scope.launch { drawerState.close() }
                                filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
                            },
                            onExportJournalClicked = {
                                scope.launch { drawerState.close() }
                                val fileName = "journal_${LocalDateTime.now().format(DateFormatters.EXPORT_FILE_TIMESTAMP)}.json"
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
                                MainTopAppBar(
                                    selectedEntryIds = selectedEntryIds,
                                    selectedEntry = selectedEntry,
                                    isRecycleBinSelected = JournalConstants.isVirtualDeletedName(category),
                                    showCategoryTags = showCategoryTags,
                                    hasGpsTrackForSelectedDate = hasGpsTrackForSelectedDate,
                                    canUndo = canUndo,
                                    onClearSelection = viewModel::clearSelection,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onEditEntryById = { entryId ->
                                        context.startActivity(EditEntryActivity.newIntent(context, entryId))
                                        viewModel.clearSelection()
                                    },
                                    onEditSelectedSingleEntry = { entry ->
                                        context.startActivity(EditEntryActivity.newIntent(context, entry.entry.id))
                                        viewModel.onEntrySelected(entry)
                                    },
                                    onChangeTimeZoneClicked = { showBatchTimeZoneDialog = true },
                                    onShareAsTextClicked = {
                                        scope.launch {
                                            val text = viewModel.getEntriesForSharing(selectedEntryIds)
                                            startActivity(ShareExportHelper.createPlainTextShareIntent(text))
                                            viewModel.clearSelection()
                                        }
                                    },
                                    onExportAsVjClicked = {
                                        scope.launch {
                                            try {
                                                val json = viewModel.getEntriesForSharingAsJson(selectedEntryIds)
                                                val uri = ShareExportHelper.writeSharedExportUri(context, json)
                                                startActivity(ShareExportHelper.createVjFileShareIntent(uri))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                viewModel.clearSelection()
                                            }
                                        }
                                    },
                                    onRestoreSelectedClicked = { viewModel.restoreEntries(selectedEntryIds) },
                                    onRequestHardDeleteSelectedClicked = { showHardDeleteSelectionDialog = true },
                                    onDeleteSelectedClicked = { viewModel.deleteEntries(selectedEntryIds) },
                                    onToggleCategoryTagsClicked = viewModel::toggleShowCategoryTags,
                                    onOpenGpsTrackClicked = { openGoogleMapsWithTrack(gpsTrackPoints) },
                                    onUndoDeleteClicked = viewModel::onUndoDelete,
                                    onCopyToClipboardClicked = {
                                        scope.launch {
                                            val clipboardText = viewModel.getEntriesTextForClipboard()
                                            if (clipboardText.isBlank()) {
                                                Toast.makeText(context, "Keine Einträge zum Kopieren vorhanden", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("VoiceJournal", clipboardText)
                                                clipboardManager.setPrimaryClip(clip)
                                                Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            },
                            floatingActionButton = {
                                val createEntryLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.StartActivityForResult(),
                                    onResult = { result ->
                                        if (result.resultCode == RESULT_OK) {
                                            val createdId = result.data?.getStringExtra(EXTRA_ENTRY_ID)
                                            if (createdId != null) {
                                                try {
                                                    viewModel.scrollToEntry(UUID.fromString(createdId))
                                                } catch (_: IllegalArgumentException) {
                                                    // Ignore invalid UUID
                                                }
                                            }
                                        }
                                    }
                                )
                                MainFabMenu(
                                    isRecording = isRecording,
                                    isExpanded = isFabMenuExpanded,
                                    onExpandedChange = { isFabMenuExpanded = it },
                                    onCreateManualEntryClicked = {
                                        val initialCat = viewModel.selectedCategory.value
                                            .takeUnless { JournalConstants.isVirtualDeletedName(it) }
                                        createEntryLauncher.launch(
                                            EditEntryActivity.newIntentForNewEntry(context, initialCat)
                                        )
                                    },
                                    onStartVoiceEntryClicked = requestOrStartVoiceRecording,
                                    onStopRecordingClicked = speechRecognitionManager::stopListening
                                )
                            }
                        ) { innerPadding ->
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                pagedEntries = pagedEntries,
                                categories = categories,
                                selectedCategory = category,
                                onCategoryChange = viewModel::onCategoryChange,
                                selectedEntry = selectedEntry,
                                selectedEntryIds = selectedEntryIds,
                                onToggleEntrySelection = viewModel::toggleEntrySelection,
                                selectedDate = selectedDate,
                                onDateSelected = viewModel::onDateSelected,
                                onEntrySelected = viewModel::onEntrySelected,
                                onDateLongClicked = ::openGooglePhotos,
                                onPhotoIconClicked = { entryWithCategories ->
                                    openGooglePhotos(entryWithCategories.entry.localDate())
                                },
                                truncationLength = truncationLength,
                                showCategoryTags = showCategoryTags,
                                scrollToEntryId = scrollToEntryId,
                                onScrolledToEntry = viewModel::onScrolledToEntry,
                                onCheckedChange = { entry -> viewModel.toggleEntryChecked(entry.entry) }
                            )
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
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.destroy()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == NotificationHelper.NOTIFICATION_ACTION) {
            handleNotificationAction()
        }
        if (intent.hasExtra(EXTRA_ENTRY_ID)) {
            val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
            if (entryId != null) {
                try {
                    viewModel.scrollToEntry(UUID.fromString(entryId))
                } catch (_: IllegalArgumentException) {
                    // Ignore invalid UUID
                }
            }
        }
    }

    private fun handleNotificationAction() {
        runOnUiThread {
            Toast.makeText(this, "Starting speech recognition...", Toast.LENGTH_SHORT).show()
            startListening()
        }
    }

    private fun openGooglePhotos(date: LocalDate) {
        val formatter = DateFormatters.googlePhotosQuery(Locale.getDefault())
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
        val sampledIntermediate = sampleWaypoints(points.subList(1, points.size - 1))
        val waypointsParam = if (sampledIntermediate.isNotEmpty()) {
            "&waypoints=" + sampledIntermediate.joinToString("|") { "${it.latitude},${it.longitude}" }
        } else {
            ""
        }

        val url = "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination$waypointsParam&travelmode=driving"

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Google Maps.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "ENTRY_ID"
        internal const val MAX_MAPS_WAYPOINTS = 9

        internal fun <T> sampleWaypoints(intermediate: List<T>, maxWaypoints: Int = MAX_MAPS_WAYPOINTS): List<T> {
            if (maxWaypoints <= 0 || intermediate.isEmpty()) return emptyList()
            if (intermediate.size <= maxWaypoints) return intermediate
            if (maxWaypoints == 1) return listOf(intermediate[intermediate.size / 2])
            return (0 until maxWaypoints).map { step ->
                val index = ((step.toLong() * (intermediate.size - 1)) / (maxWaypoints - 1)).toInt()
                intermediate[index]
            }
        }
    }
}
