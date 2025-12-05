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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EditEntryActivity : ComponentActivity() {

    private val entryId by lazy { intent.getIntExtra(EXTRA_ENTRY_ID, -1) }

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
                        onSave = { updatedCategories, content, timestamp, hasImage ->
                            viewModel.saveEntry(updatedCategories, content, timestamp, hasImage)
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
        fun newIntent(context: Context, entryId: Int): Intent {
            return Intent(context, EditEntryActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    entry: EntryWithCategories,
    allCategories: List<Category>,
    onSave: (List<String>, String, Long, Boolean) -> Unit,
    onNavigateUp: () -> Unit
) {
    var text by remember { mutableStateOf(entry.entry.content) }
    var hasImage by remember { mutableStateOf(entry.entry.hasImage) }
    val selectedCategories = remember { mutableStateOf(entry.categories.map { it.category }) }
    var currentDateTime by remember {
        mutableStateOf(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.entry.timestamp),
                ZoneId.systemDefault()
            )
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = currentDateTime.hour,
        initialMinute = currentDateTime.minute,
        is24Hour = true
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        currentDateTime = currentDateTime.with(selectedDate)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    currentDateTime = currentDateTime.withHour(timePickerState.hour).withMinute(timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
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
                val newTimestamp = currentDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSave(selectedCategories.value, text, newTimestamp, hasImage)
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
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f),
                label = { Text("Content") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val dateInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(dateInteractionSource) {
                dateInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showDatePicker = true }
                }
            }

            val timeInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(timeInteractionSource) {
                timeInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) { showTimePicker = true }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = dateInteractionSource,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Select Date") }
                )
                OutlinedTextField(
                    value = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    onValueChange = {},
                    label = { Text("Time") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    interactionSource = timeInteractionSource,
                    trailingIcon = { Icon(Icons.Default.Schedule, contentDescription = "Select Time") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasImage, onCheckedChange = { hasImage = it })
                Text("Has Image")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Categories:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                allCategories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
}