package com.example.voicejournal

import android.Manifest
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.localDate
import com.example.voicejournal.ui.components.AppDrawer
import com.example.voicejournal.ui.components.MainActionDialogs
import com.example.voicejournal.ui.components.MainDialogType
import com.example.voicejournal.ui.components.MainFabMenu
import com.example.voicejournal.ui.components.MainTopAppBar
import com.example.voicejournal.ui.screens.HomeScreen
import com.example.voicejournal.ui.screens.search.SearchActivity
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import com.example.voicejournal.util.ExternalNavigationHelper
import com.example.voicejournal.util.NotificationHelper
import com.example.voicejournal.util.ShareExportHelper
import com.example.voicejournal.util.SpeechRecognitionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime
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
        VoiceJournalApplication.setupLocationWorker(
            context = this,
            replaceExisting = false,
            isAppInForeground = true
        )
        speechRecognitionManager = SpeechRecognitionManager(
            context = applicationContext,
            onTextRecognized = viewModel::processRecognizedText,
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
                val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
                val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
                val selectedEntry by viewModel.selectedEntry.collectAsStateWithLifecycle()
                val selectedEntryIds by viewModel.selectedEntryIds.collectAsStateWithLifecycle()
                val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
                val isGpsTrackingEnabled by viewModel.isGpsTrackingEnabled.collectAsStateWithLifecycle()
                val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val gpsTrackPoints by viewModel.gpsTrackPoints.collectAsStateWithLifecycle()
                val hasGpsTrackForSelectedDate by viewModel.hasGpsTrackForSelectedDate.collectAsStateWithLifecycle()
                val isRecording by speechRecognitionManager.isRecording.collectAsStateWithLifecycle()
                val truncationLength by viewModel.truncationLength.collectAsStateWithLifecycle()
                val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
                val showCategoryTags by viewModel.showCategoryTags.collectAsStateWithLifecycle()
                val pagedEntries = viewModel.pagedEntries.collectAsLazyPagingItems()
                val scrollToEntryId by viewModel.scrollToEntryId.collectAsStateWithLifecycle()

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var activeDialog by rememberSaveable { mutableStateOf<MainDialogType?>(null) }
                var isFabMenuExpanded by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(viewModel) {
                    viewModel.undoPromptEvents.collectLatest { promptType ->
                        val message = when (promptType) {
                            UndoPromptType.LINE_DELETED -> context.getString(R.string.snackbar_line_deleted)
                            UndoPromptType.ENTRY_DELETED -> context.getString(R.string.snackbar_entry_deleted)
                        }
                        val result = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = context.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onUndoDelete()
                        }
                    }
                }

                MainActionDialogs(
                    activeDialog = activeDialog,
                    selectedCount = selectedEntryIds.size,
                    onBatchZoneSelected = { chosenZoneId ->
                        viewModel.updateEntriesZoneId(selectedEntryIds, chosenZoneId)
                        activeDialog = null
                        Toast.makeText(context, "Zeitzone aktualisiert", Toast.LENGTH_SHORT).show()
                    },
                    onConfirmHardDeleteSelection = {
                        viewModel.hardDeleteEntries(selectedEntryIds)
                        activeDialog = null
                    },
                    onConfirmSoftDeleteAll = {
                        viewModel.deleteAllData()
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )

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
                                ExternalNavigationHelper.openGoogleMapsWithTrack(context, gpsTrackPoints)
                            },
                            onDeleteAllClicked = {
                                scope.launch { drawerState.close() }
                                activeDialog = MainDialogType.SOFT_DELETE_ALL
                            }
                        )
                    },
                    content = {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            topBar = {
                                MainTopAppBar(
                                    selectedEntryIds = selectedEntryIds,
                                    selectedEntry = selectedEntry,
                                    isRecycleBinSelected = selectedCategoryId == JournalConstants.CATEGORY_DELETED_ID,
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
                                    onChangeTimeZoneClicked = { activeDialog = MainDialogType.BATCH_TIME_ZONE },
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
                                    onRequestHardDeleteSelectedClicked = {
                                        activeDialog = MainDialogType.HARD_DELETE_SELECTION
                                    },
                                    onDeleteSelectedClicked = { viewModel.deleteEntries(selectedEntryIds) },
                                    onToggleCategoryTagsClicked = viewModel::toggleShowCategoryTags,
                                    onOpenGpsTrackClicked = {
                                        ExternalNavigationHelper.openGoogleMapsWithTrack(context, gpsTrackPoints)
                                    },
                                    onUndoDeleteClicked = {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        viewModel.onUndoDelete()
                                    },
                                    onCopyToClipboardClicked = {
                                        scope.launch {
                                            val clipboardText = viewModel.getEntriesTextForClipboard()
                                            ExternalNavigationHelper.copyEntriesTextToClipboard(context, clipboardText)
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
                                        val initialCatId = selectedCategoryId?.takeIf { it > 0 }
                                        createEntryLauncher.launch(
                                            EditEntryActivity.newIntentForNewEntry(context, initialCatId)
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
                                selectedCategory = selectedCategory,
                                onCategoryChange = viewModel::onCategoryChange,
                                selectedEntry = selectedEntry,
                                selectedEntryIds = selectedEntryIds,
                                onToggleEntrySelection = viewModel::toggleEntrySelection,
                                selectedDate = selectedDate,
                                onDateSelected = viewModel::onDateSelected,
                                onEntrySelected = viewModel::onEntrySelected,
                                onDateLongClicked = { date ->
                                    ExternalNavigationHelper.openGooglePhotos(context, date)
                                },
                                onPhotoIconClicked = { entryWithCategories ->
                                    ExternalNavigationHelper.openGooglePhotos(
                                        context,
                                        entryWithCategories.entry.localDate()
                                    )
                                },
                                truncationLength = truncationLength,
                                showCategoryTags = showCategoryTags,
                                scrollToEntryId = scrollToEntryId,
                                onScrolledToEntry = viewModel::onScrolledToEntry,
                                onCheckedChange = { entry -> viewModel.toggleEntryChecked(entry.entry) },
                                onDeleteLineInEntry = viewModel::deleteLineInEntry
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

    companion object {
        const val EXTRA_ENTRY_ID = "ENTRY_ID"

        internal fun <T> sampleWaypoints(
            intermediate: List<T>,
            maxWaypoints: Int = ExternalNavigationHelper.MAX_MAPS_WAYPOINTS
        ): List<T> = ExternalNavigationHelper.sampleWaypoints(intermediate, maxWaypoints)
    }
}
