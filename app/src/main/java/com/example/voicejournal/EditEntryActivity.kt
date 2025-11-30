package com.example.voicejournal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                    // Show a loading indicator or an error message
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
    val context = LocalContext.current

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            currentDateTime = currentDateTime.withHour(hourOfDay).withMinute(minute)
        },
        currentDateTime.hour,
        currentDateTime.minute,
        true
    )

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            currentDateTime = currentDateTime.withYear(year).withMonth(month + 1).withDayOfMonth(dayOfMonth)
        },
        currentDateTime.year,
        currentDateTime.monthValue - 1,
        currentDateTime.dayOfMonth
    )

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
                .verticalScroll(rememberScrollState())
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                label = { Text("Content") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Date and Time pickers
            Text(text = "Current: ${currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { datePickerDialog.show() }) { Text("Change Date") }
                Button(onClick = { timePickerDialog.show() }) { Text("Change Time") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasImage, onCheckedChange = { hasImage = it })
                Text("Has Image")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Category selection
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