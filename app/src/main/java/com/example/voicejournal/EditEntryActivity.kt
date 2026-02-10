package com.example.voicejournal

import android.Manifest
import android.content.Context
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
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.SpeechRecognitionManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE),
            entryId,
            categoryId
        )
    }

    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoicejournalTheme {
                val entry by viewModel.entry.collectAsState()
                val allCategories by viewModel.allCategories.collectAsState()
                var textState by remember { mutableStateOf(TextFieldValue(entry?.entry?.content ?: "")) }

                // Initialize SpeechRecognitionManager here, where we can use composable functions
                speechRecognitionManager = SpeechRecognitionManager(
                    context = this,
                    onTextRecognized = { recognizedText ->
                        val selection = textState.selection
                        val originalText = textState.text

                        val newText: String
                        val newCursorPos: Int

                        if (selection.length > 0) {
                            // 1. Selection exists: replace
                            newText = originalText.replaceRange(selection.start, selection.end, recognizedText)
                            newCursorPos = selection.start + recognizedText.length
                        } else if (selection.start >= originalText.length) {
                            // 2. Cursor at the very end: append
                            newText = if (originalText.isNotEmpty()) {
                                originalText + "\n" + recognizedText
                            } else {
                                recognizedText
                            }
                            newCursorPos = newText.length
                        } else {
                            // 3. Cursor is in the middle or at the start: insert
                            newText = originalText.replaceRange(selection.start, selection.end, recognizedText)
                            newCursorPos = selection.start + recognizedText.length
                        }

                        textState = TextFieldValue(newText, selection = TextRange(newCursorPos))
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

                entry?.let {
                    // Update textState when entry is loaded
                    LaunchedEffect(it) {
                        if (textState.text.isEmpty()) {
                            textState = TextFieldValue(it.entry.content, selection = TextRange(it.entry.content.length))
                        }
                    }

                    EditEntryScreen(
                        entry = it,
                        isNewEntry = entryId == null,
                        allCategories = allCategories,
                        textValue = textState,
                        onTextValueChange = { newTextState -> textState = newTextState },
                        onSave = { updatedCategories, content, start_datetime, stop_datetime, hasImage ->
                            viewModel.saveEntry(updatedCategories, content, start_datetime, stop_datetime, hasImage)
                            finish()
                        },
                        onNavigateUp = { finish() },
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

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.destroy()
        }
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
    entry: EntryWithCategories,
    isNewEntry: Boolean,
    allCategories: List<Category>,
    textValue: TextFieldValue,
    onTextValueChange: (TextFieldValue) -> Unit,
    onSave: (List<String>, String, Long, Long?, Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    speechRecognitionManager: SpeechRecognitionManager,
    viewModel: EditEntryViewModel
) {
    var hasImage by remember { mutableStateOf(entry.entry.hasImage) }
    val selectedCategories = remember { mutableStateOf(entry.categories.map { it.category }) }
    var startDateTime by remember {
        mutableStateOf(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.entry.start_datetime),
                ZoneId.systemDefault()
            )
        )
    }
    var stopDateTime by remember {
        mutableStateOf(
            entry.entry.stop_datetime?.let {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            }
        )
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showStopDatePicker by remember { mutableStateOf(false) }
    var showStopTimePicker by remember { mutableStateOf(false) }

    val startDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    val startTimePickerState = rememberTimePickerState(
        initialHour = startDateTime.hour,
        initialMinute = startDateTime.minute,
        is24Hour = true
    )
    val stopDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = stopDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    )
    val stopTimePickerState = rememberTimePickerState(
        initialHour = stopDateTime?.hour ?: 0,
        initialMinute = stopDateTime?.minute ?: 0,
        is24Hour = true
    )

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        startDateTime = startDateTime.with(selectedDate)
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
                    startDateTime = startDateTime.withHour(startTimePickerState.hour).withMinute(startTimePickerState.minute)
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showStopDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStopDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    stopDatePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        stopDateTime = stopDateTime?.with(selectedDate) ?: LocalDateTime.now().with(selectedDate)
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
                    stopDateTime = (stopDateTime ?: LocalDateTime.now()).withHour(stopTimePickerState.hour).withMinute(stopTimePickerState.minute)
                    showStopTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStopTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    val context = LocalContext.current
    val isRecording by speechRecognitionManager.isRecording.collectAsState()

    val speechService by viewModel.speechService.collectAsState()
    val googleCloudApiKey by viewModel.googleCloudApiKey.collectAsState()

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                speechRecognitionManager.startListening(
                    service = speechService,
                    apiKey = googleCloudApiKey
                )
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewEntry) "New Entry" else "Edit Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isRecording) {
                            speechRecognitionManager.stopListening()
                        } else {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                PackageManager.PERMISSION_GRANTED -> speechRecognitionManager.startListening(
                                    service = speechService,
                                    apiKey = googleCloudApiKey
                                )
                                else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newStartDatetime = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val newStopDatetime = stopDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                onSave(selectedCategories.value, textValue.text, newStartDatetime, newStopDatetime, hasImage)
            }) {
                Icon(Icons.Default.Done, contentDescription = "Save Entry")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val configuration = LocalConfiguration.current
            val screenHeight = configuration.screenHeightDp.dp
            OutlinedTextField(
                value = textValue,
                onValueChange = onTextValueChange,
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
                    value = startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    onValueChange = {},
                    label = { Text("Start Date") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = startDateInteractionSource,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Select Start Date") }
                )
                OutlinedTextField(
                    value = startDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
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
                    value = stopDateTime?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "",
                    onValueChange = {},
                    label = { Text("Stop Date") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = stopDateInteractionSource,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Select Stop Date") }
                )
                OutlinedTextField(
                    value = stopDateTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                    onValueChange = {},
                    label = { Text("Stop Time") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = stopTimeInteractionSource,
                    trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = "Select Stop Time") }
                )
                IconButton(onClick = { stopDateTime = null }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear Stop Time")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasImage, onCheckedChange = { hasImage = it })
                Text("Has Image")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("Categories:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            allCategories.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = selectedCategories.value.contains(category.category),
                        onCheckedChange = {
                            val currentSelection = selectedCategories.value.toMutableList()
                            if (it) {
                                currentSelection.add(category.category)
                            } else {
                                currentSelection.remove(category.category)
                            }
                            selectedCategories.value = currentSelection
                        }
                    )
                    Text(category.category)
                }
            }
        }
    }
}