package com.example.voicejournal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class EditEntryActivity : ComponentActivity() {

    private val entryId by lazy {
        val idString = intent.getStringExtra(EXTRA_ENTRY_ID)
        requireNotNull(idString) { "Entry ID must be provided" }
        UUID.fromString(idString)
    }

    private val viewModel: EditEntryViewModel by viewModels {
        EditEntryViewModelFactory(Injector.provideJournalRepository(this), entryId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                val entry by viewModel.entry.collectAsState()
                val allCategories by viewModel.allCategories.collectAsState()

                entry?.let {
                    EditEntryScreen(
                        entry = it,
                        allCategories = allCategories,
                        onSave = { updatedCategories, content, start_datetime, stop_datetime, hasImage ->
                            viewModel.saveEntry(updatedCategories, content, start_datetime, stop_datetime, hasImage)
                            finish()
                        },
                        onNavigateUp = { finish() }
                    )
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "extra_entry_id"
        fun newIntent(context: Context, entryId: UUID): Intent {
            return Intent(context, EditEntryActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId.toString())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    entry: EntryWithCategories,
    allCategories: List<Category>,
    onSave: (List<String>, String, Long, Long?, Boolean) -> Unit,
    onNavigateUp: () -> Unit
) {
    var text by remember { mutableStateOf(entry.entry.content) }
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


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newStartDatetime = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val newStopDatetime = stopDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                onSave(selectedCategories.value, text, newStartDatetime, newStopDatetime, hasImage)
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
                value = text,
                onValueChange = { text = it },
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