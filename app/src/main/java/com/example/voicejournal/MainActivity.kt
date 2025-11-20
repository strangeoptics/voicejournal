package com.example.voicejournal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.ui.theme.VoicejournalTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    companion object {
        const val NOTIFICATION_ACTION = "com.example.voicejournal.NOTIFICATION_ACTION"
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(db.journalEntryDao(), getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setupSpeechRecognizer()

        setContent {
            VoicejournalTheme {
                val context = LocalContext.current
                val category by viewModel.selectedCategory.collectAsState()
                val groupedEntries by viewModel.groupedEntries.collectAsState()
                val selectedEntry by viewModel.selectedEntry.collectAsState()
                val selectedDate by viewModel.selectedDate.collectAsState()
                val editingEntry by viewModel.editingEntry.collectAsState()
                val daysToShow by viewModel.daysToShow.collectAsState()
                val filteredEntries by viewModel.filteredEntries.collectAsState()

                var showSettings by remember { mutableStateOf(false) }

                val textToShow = filteredEntries.joinToString("\n") { entry ->
                    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault())
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    "[${date.format(formatter)}] ${entry.content}"
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
                            showNotification(context)
                        }
                    }
                )
                val scope = rememberCoroutineScope()


                if (showSettings) {
                    SettingsScreen(
                        currentDays = daysToShow,
                        onSave = { newDays ->
                            viewModel.saveDaysToShow(newDays)
                            showSettings = false
                        },
                        onDismiss = { showSettings = false }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            var menuExpanded by remember { mutableStateOf(false) }
                            TopAppBar(
                                title = { Text("Voice Journal") },
                                actions = {
                                    IconButton(onClick = {
                                        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("VoiceJournal", textToShow)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = "In die Zwischenablage kopieren")
                                    }
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = {
                                                showSettings = true
                                                menuExpanded = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Show Notification") },
                                            onClick = {
                                                scope.launch {
                                                    val allPermissionsGranted = notificationPermissions.all {
                                                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                                    }
                                                    if (allPermissionsGranted) {
                                                        showNotification(context)
                                                    } else {
                                                        notificationPermissionLauncher.launch(notificationPermissions)
                                                    }
                                                }
                                                menuExpanded = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = "Benachrichtigung anzeigen") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add Test Data") },
                                            onClick = {
                                                viewModel.addTestData()
                                                menuExpanded = false
                                            },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add test data") }
                                        )
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            FloatingActionButton(onClick = {
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
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "Sprechen")
                            }
                        }
                    ) { innerPadding ->
                        Greeting(
                            modifier = Modifier.padding(innerPadding),
                            groupedEntries = groupedEntries,
                            categories = viewModel.categories,
                            selectedCategory = category,
                            onCategoryChange = viewModel::onCategoryChange,
                            onDeleteEntry = viewModel::onDeleteEntry,
                            selectedEntry = selectedEntry,
                            selectedDate = selectedDate,
                            onDateSelected = viewModel::onDateSelected,
                            onEntrySelected = viewModel::onEntrySelected,
                            onEditEntry = viewModel::onEditEntry,
                            onMoreClicked = viewModel::onMoreClicked
                        )
                    }
                }

                editingEntry?.let { entry ->
                    EditEntryDialog(
                        entry = entry,
                        onDismiss = viewModel::onDismissEditEntry,
                        onSave = viewModel::onSaveEntry
                    )
                }
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches.isNullOrEmpty()) return

                val recognizedText = matches[0].trim()
                if (recognizedText.isEmpty()) return
                viewModel.processRecognizedText(recognizedText)
            }

            override fun onError(error: Int) {
                // Handle error
            }

            // Other listener methods...
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        speechRecognizer.startListening(speechRecognizerIntent)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == NOTIFICATION_ACTION) {
            handleNotificationAction()
        }
    }

    private fun handleNotificationAction() {
        runOnUiThread {
            Toast.makeText(this, "Starting speech recognition...", Toast.LENGTH_SHORT).show()
            startListening()
        }
    }
}

@Composable
fun SettingsScreen(
    currentDays: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var days by remember { mutableStateOf(currentDays.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                Text("Wie viele Tage sollen angezeigt werden?")
                TextField(
                    value = days,
                    onValueChange = { days = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(days.toIntOrNull() ?: currentDays) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun EditEntryDialog(
    entry: JournalEntry,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var text by remember { mutableStateOf(entry.content) }
    val context = LocalContext.current
    var currentDateTime by remember {
        mutableStateOf(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.timestamp),
                ZoneId.systemDefault()
            )
        )
    }

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            currentDateTime = currentDateTime.withHour(hourOfDay).withMinute(minute)
        },
        currentDateTime.hour,
        currentDateTime.minute,
        true
    )

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            currentDateTime = currentDateTime.withYear(year).withMonth(month + 1).withDayOfMonth(dayOfMonth)
        },
        currentDateTime.year,
        currentDateTime.monthValue - 1,
        currentDateTime.dayOfMonth
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
        text = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Current: ${currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { datePickerDialog.show() }) {
                        Text("Change Date")
                    }
                    Button(onClick = { timePickerDialog.show() }) {
                        Text("Change Time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newTimestamp = currentDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSave(text, newTimestamp)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    groupedEntries: Map<LocalDate, List<JournalEntry>>,
    categories: List<String>,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    onDeleteEntry: (JournalEntry) -> Unit,
    selectedEntry: JournalEntry?,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onEntrySelected: (JournalEntry) -> Unit,
    onEditEntry: (JournalEntry) -> Unit,
    onMoreClicked: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                categories.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onCategoryChange(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            groupedEntries.forEach { (date, entries) ->
                stickyHeader {
                    val isDateSelected = selectedDate == date
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDateSelected(date) },
                        color = if (isDateSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                items(entries, key = { it.id }) { entry ->
                    val isSelected = selectedEntry == entry
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.StartToEnd) {
                                onDeleteEntry(entry)
                                true
                            } else {
                                false
                            }
                        },
                        positionalThreshold = { it * 0.80f }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.StartToEnd -> Color.Red
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = { onEntrySelected(entry) },
                                    onLongClick = { onEditEntry(entry) }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                val date = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(entry.timestamp),
                                    ZoneId.systemDefault()
                                )
                                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                Text(
                                    text = entry.content,
                                    modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp)
                                )
                                Text(
                                    text = date.format(formatter),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }
            if (groupedEntries.isNotEmpty()) {
                item {
                    Button(
                        onClick = onMoreClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("more")
                    }
                }
            }
        }
    }
}

fun showNotification(context: Context) {
    val channelId = "channel_id"
    val notificationId = 1
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.NOTIFICATION_ACTION
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(
        channelId,
        "Channel Name",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Notification Title")
        .setContentText("Ready to listen!")
        .addAction(R.drawable.ic_launcher_foreground, "Start Listening", pendingIntent)
        .build()

    notificationManager.notify(notificationId, notification)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VoicejournalTheme {
        val categories = listOf("journal", "todo", "kaufen", "baumarkt", "eloisa")
        var selectedCategory by remember { mutableStateOf(categories.first()) }
        val entries = remember {
            listOf(
                JournalEntry(
                    id = 1,
                    title = "journal",
                    content = "This is a preview entry.",
                    timestamp = System.currentTimeMillis()
                ),
                JournalEntry(
                    id = 2,
                    title = "todo",
                    content = "This is a todo preview.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        val groupedEntries = entries.filter { it.title == selectedCategory }.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        Greeting(
            groupedEntries = groupedEntries,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
            onDeleteEntry = {},
            selectedEntry = null,
            selectedDate = null,
            onDateSelected = {},
            onEntrySelected = {},
            onEditEntry = {},
            onMoreClicked = {}
        )
    }
}