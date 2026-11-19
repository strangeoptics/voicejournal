package com.example.voicejournal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.components.TimeZonePickerDialog
import com.example.voicejournal.ui.components.PublicIcon
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import com.example.voicejournal.util.SpeechRecognitionManager
import com.example.voicejournal.util.TimeZoneUtils
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class EditEntryActivity : ComponentActivity() {

    private val entryId: UUID? by lazy {
        intent.getStringExtra(EXTRA_ENTRY_ID)?.let { UUID.fromString(it) }
    }

    private val categoryId: String? by lazy {
        intent.getStringExtra(EXTRA_CATEGORY_ID)
    }

    private val viewModel: EditEntryViewModel by viewModels {
        EditEntryViewModelFactory(
            Injector.provideJournalRepository(this),
            Injector.provideSettingsRepository(this),
            entryId,
            categoryId
        )
    }

    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechRecognitionManager = SpeechRecognitionManager(
            context = applicationContext,
            onTextRecognized = { recognizedText ->
                val textState = viewModel.entryContent.value
                val originalText = textState.text
                val selMin = textState.selection.min.coerceIn(0, originalText.length)
                val selMax = textState.selection.max.coerceIn(selMin, originalText.length)

                val newText: String
                val newCursorPos: Int

                if (selMax > selMin) {
                    // 1. Selection exists (including reverse selection): replace selected range
                    newText = originalText.replaceRange(selMin, selMax, recognizedText)
                    newCursorPos = selMin + recognizedText.length
                } else if (selMin >= originalText.length) {
                    // 2. Cursor at the very end: append on a new line
                    newText = if (originalText.isNotEmpty()) {
                        originalText + "\n" + recognizedText
                    } else {
                        recognizedText
                    }
                    newCursorPos = newText.length
                } else {
                    // 3. Cursor is in the middle or at the start: insert at cursor
                    newText = originalText.replaceRange(selMin, selMax, recognizedText)
                    newCursorPos = selMin + recognizedText.length
                }

                viewModel.updateContent(TextFieldValue(newText, selection = TextRange(newCursorPos)))
            },
            scope = lifecycleScope,
            onError = { error ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this,
                            SpeechRecognitionManager.errorMessageForCode(error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

        setContent {
            VoicejournalTheme {
                val entry by viewModel.entry.collectAsState()
                val allCategories by viewModel.allCategories.collectAsState()

                entry?.let {
                    EditEntryScreen(
                        isNewEntry = entryId == null,
                        allCategories = allCategories,
                        onNavigateUp = { finish() },
                        onSaveCompleted = { createdId ->
                            if (createdId != null) {
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(MainActivity.EXTRA_ENTRY_ID, createdId.toString())
                                )
                            } else {
                                setResult(RESULT_OK)
                            }
                            finish()
                        },
                        speechRecognitionManager = speechRecognitionManager,
                        viewModel = viewModel
                    )
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.stopListening()
        }
    }

    override fun onDestroy() {
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "extra_entry_id"
        private const val EXTRA_CATEGORY_ID = "extra_category_id"

        fun newIntent(context: Context, entryId: UUID): Intent {
            return Intent(context, EditEntryActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId.toString())
            }
        }
        fun newIntentForNewEntry(context: Context, categoryId: String? = null): Intent {
            return Intent(context, EditEntryActivity::class.java).apply {
                categoryId?.let { putExtra(EXTRA_CATEGORY_ID, it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    isNewEntry: Boolean,
    allCategories: List<Category>,
    onNavigateUp: () -> Unit,
    onSaveCompleted: (UUID?) -> Unit = { onNavigateUp() },
    speechRecognitionManager: SpeechRecognitionManager,
    viewModel: EditEntryViewModel
) {
    val entryContent by viewModel.entryContent.collectAsState()
    val hasImage by viewModel.hasImage.collectAsState()
    val startDateTimeMillis by viewModel.startDateTime.collectAsState()
    val stopDateTimeMillis by viewModel.stopDateTime.collectAsState()
    val zoneId by viewModel.zoneId.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveCompleted by viewModel.saveCompleted.collectAsState()
    val createdEntryId by viewModel.createdEntryId.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    val activeZone = remember(zoneId) {
        TimeZoneUtils.resolveZoneId(zoneId)
    }

    val startDateTime = remember(startDateTimeMillis, activeZone) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(startDateTimeMillis), activeZone)
    }

    val stopDateTime = remember(stopDateTimeMillis, activeZone) {
        stopDateTimeMillis?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), activeZone)
        }
    }

    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
    var showStopDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStopTimePicker by rememberSaveable { mutableStateOf(false) }
    var showTimeZonePicker by rememberSaveable { mutableStateOf(false) }

    if (showStartDatePicker) {
        val startDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeZoneUtils.localDateToDatePickerMillis(startDateTime.toLocalDate())
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = TimeZoneUtils.datePickerMillisToLocalDate(millis)
                        val newDateTime = startDateTime.with(selectedDate)
                        viewModel.updateStartDateTime(newDateTime.atZone(activeZone).toInstant().toEpochMilli())
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (showStartTimePicker) {
        val startTimePickerState = rememberTimePickerState(
            initialHour = startDateTime.hour,
            initialMinute = startDateTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Select Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = startTimePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newDateTime = startDateTime.withHour(startTimePickerState.hour).withMinute(startTimePickerState.minute)
                    viewModel.updateStartDateTime(newDateTime.atZone(activeZone).toInstant().toEpochMilli())
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showStopDatePicker) {
        val initialStopDate = stopDateTime?.toLocalDate() ?: LocalDateTime.now(activeZone).toLocalDate()
        val stopDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeZoneUtils.localDateToDatePickerMillis(initialStopDate)
        )
        DatePickerDialog(
            onDismissRequest = { showStopDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    stopDatePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = TimeZoneUtils.datePickerMillisToLocalDate(millis)
                        val newDateTime = (stopDateTime ?: LocalDateTime.now(activeZone)).with(selectedDate)
                        viewModel.updateStopDateTime(newDateTime.atZone(activeZone).toInstant().toEpochMilli())
                    }
                    showStopDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = stopDatePickerState)
        }
    }

    if (showStopTimePicker) {
        val initialStopDateTime = stopDateTime ?: LocalDateTime.now(activeZone)
        val stopTimePickerState = rememberTimePickerState(
            initialHour = initialStopDateTime.hour,
            initialMinute = initialStopDateTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStopTimePicker = false },
            title = { Text("Select Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = stopTimePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newDateTime = (stopDateTime ?: LocalDateTime.now(activeZone))
                        .withHour(stopTimePickerState.hour)
                        .withMinute(stopTimePickerState.minute)
                    viewModel.updateStopDateTime(newDateTime.atZone(activeZone).toInstant().toEpochMilli())
                    showStopTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStopTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showTimeZonePicker) {
        TimeZonePickerDialog(
            selectedZoneId = zoneId,
            referenceMillis = startDateTimeMillis,
            onZoneSelected = { selected ->
                viewModel.updateZoneId(selected)
                showTimeZonePicker = false
            },
            onDismiss = { showTimeZonePicker = false }
        )
    }

    val context = LocalContext.current
    val isRecording by speechRecognitionManager.isRecording.collectAsState()

    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            onSaveCompleted(createdEntryId)
        }
    }

    LaunchedEffect(saveError) {
        saveError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearSaveError()
        }
    }

    val speechService by viewModel.speechService.collectAsState()
    val googleCloudApiKey by viewModel.googleCloudApiKey.collectAsState()
    val maxRecordingTime by viewModel.maxRecordingTime.collectAsState()
    val silenceThreshold by viewModel.silenceThreshold.collectAsState()
    val silenceTimeRequired by viewModel.silenceTimeRequired.collectAsState()

    val startDictation = {
        speechRecognitionManager.startListening(
            service = speechService,
            apiKey = googleCloudApiKey,
            maxRecordingTimeSeconds = maxRecordingTime,
            silenceThreshold = silenceThreshold,
            silenceTimeRequired = silenceTimeRequired
        )
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startDictation()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewEntry) "New Entry" else "Edit Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isRecording) {
                            speechRecognitionManager.stopListening()
                        } else {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                PackageManager.PERMISSION_GRANTED -> startDictation()
                                else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.saveEntry() },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Done, contentDescription = "Save Entry")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val configuration = LocalConfiguration.current
            val screenHeight = configuration.screenHeightDp.dp
            OutlinedTextField(
                value = entryContent,
                onValueChange = { viewModel.updateContent(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight / 3),
                label = { Text("Content") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val startDateInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(startDateInteractionSource) {
                startDateInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showStartDatePicker = true }
                }
            }

            val startTimeInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(startTimeInteractionSource) {
                startTimeInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showStartTimePicker = true }
                }
            }
            
            val stopDateInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(stopDateInteractionSource) {
                stopDateInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showStopDatePicker = true }
                }
            }

            val stopTimeInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(stopTimeInteractionSource) {
                stopTimeInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showStopTimePicker = true }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startDateTime.format(DateFormatters.ISO_DATE),
                    onValueChange = {},
                    label = { Text("Start Date") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = startDateInteractionSource,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Select Start Date") }
                )
                OutlinedTextField(
                    value = startDateTime.format(DateFormatters.TIME_HM),
                    onValueChange = {},
                    label = { Text("Start Time") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = startTimeInteractionSource,
                    trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = "Select Start Time") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stopDateTime?.format(DateFormatters.ISO_DATE) ?: "",
                    onValueChange = {},
                    label = { Text("Stop Date") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = stopDateInteractionSource,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Select Stop Date") }
                )
                OutlinedTextField(
                    value = stopDateTime?.format(DateFormatters.TIME_HM) ?: "",
                    onValueChange = {},
                    label = { Text("Stop Time") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = stopTimeInteractionSource,
                    trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = "Select Stop Time") }
                )
                IconButton(onClick = { viewModel.updateStopDateTime(null) }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear Stop Time")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val timeZoneInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(timeZoneInteractionSource) {
                timeZoneInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showTimeZonePicker = true }
                }
            }
            val zoneDisplayText = remember(zoneId, activeZone, startDateTimeMillis) {
                val formatted = TimeZoneUtils.formatZoneLabel(activeZone, startDateTimeMillis)
                if (zoneId == null) "System ($formatted)" else formatted
            }
            OutlinedTextField(
                value = zoneDisplayText,
                onValueChange = {},
                label = { Text("Time Zone") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = timeZoneInteractionSource,
                trailingIcon = { Icon(PublicIcon, contentDescription = "Select Time Zone") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasImage, onCheckedChange = { viewModel.updateHasImage(it) })
                Text("Has Image")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("Categories:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            allCategories.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = selectedCategories.contains(category.category),
                        onCheckedChange = { isChecked ->
                            viewModel.toggleCategory(category.category, isChecked)
                        }
                    )
                    Text(category.category)
                }
            }
        }
    }
}