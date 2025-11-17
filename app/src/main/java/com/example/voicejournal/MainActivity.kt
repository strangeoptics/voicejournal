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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.ui.theme.VoicejournalTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    companion object {
        const val NOTIFICATION_ACTION = "com.example.voicejournal.NOTIFICATION_ACTION"
        const val PREFS_NAME = "voice_journal_prefs"
        const val KEY_DAYS_TO_SHOW = "days_to_show"
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val dao by lazy { db.journalEntryDao() }

    private val categories = listOf("journal", "todo", "kaufen", "baumarkt", "eloisa")
    private val selectedCategory = mutableStateOf(categories.first())
    private var selectedEntry by mutableStateOf<JournalEntry?>(null)
    private var editingEntry by mutableStateOf<JournalEntry?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setupSpeechRecognizer()

        setContent {
            VoicejournalTheme {
                val context = LocalContext.current
                val category by selectedCategory
                var showSettings by remember { mutableStateOf(false) }

                val sharedPrefs = remember {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                }

                var daysToShow by remember {
                    mutableIntStateOf(sharedPrefs.getInt(KEY_DAYS_TO_SHOW, 3))
                }

                val daysAgoMillis = remember(daysToShow) {
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -daysToShow)
                    }.timeInMillis
                }

                val entries by dao.getEntriesSince(daysAgoMillis).collectAsState(initial = emptyList())
                val filteredEntries = entries.filter { it.title == category }
                val groupedEntries = filteredEntries.groupBy {
                    Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                }

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
                            sharedPrefs.edit {
                                putInt(KEY_DAYS_TO_SHOW, newDays)
                            }
                            daysToShow = newDays
                            showSettings = false
                        },
                        onDismiss = { showSettings = false }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("Voice Journal") },
                                actions = {
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                                    }
                                    IconButton(onClick = {
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
                                    }) {
                                        Icon(Icons.Filled.Notifications, contentDescription = "Benachrichtigung anzeigen")
                                    }
                                    IconButton(onClick = {
                                        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("VoiceJournal", textToShow)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = "In die Zwischenablage kopieren")
                                    }
                                    IconButton(onClick = {
                                        lifecycleScope.launch {
                                            dao.deleteAll()
                                            val now = System.currentTimeMillis()
                                            val oneDay = 24 * 60 * 60 * 1000
                                            val yesterday = now - oneDay
                                            val twoDaysAgo = now - 2 * oneDay

                                            val testEntries = listOf(
                                                JournalEntry(title = "journal", content = "This is a test journal entry from today.", timestamp = now),
                                                JournalEntry(title = "journal", content = "Etwas gegessen.", timestamp = now + 100),
                                                JournalEntry(title = "todo", content = "This is a test todo item from today.", timestamp = now),
                                                JournalEntry(title = "kaufen", content = "Milk, eggs, bread.", timestamp = now),
                                                JournalEntry(title = "baumarkt", content = "A great new app idea from today.", timestamp = now),

                                                JournalEntry(title = "journal", content = "Journal entry from yesterday.", timestamp = yesterday),
                                                JournalEntry(title = "todo", content = "Todo item from yesterday.", timestamp = yesterday),
                                                JournalEntry(title = "kaufen", content = "Apples, bananas.", timestamp = yesterday),

                                                JournalEntry(title = "journal", content = "Journal entry from two days ago.", timestamp = twoDaysAgo),
                                                JournalEntry(title = "eloisa", content = "Another app idea from two days ago.", timestamp = twoDaysAgo)
                                            )
                                            testEntries.forEach { dao.insert(it) }
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add test data")
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
                            categories = categories,
                            selectedCategory = category,
                            onCategoryChange = { selectedCategory.value = it },
                            onDeleteEntry = { entry ->
                                lifecycleScope.launch {
                                    dao.delete(entry)
                                }
                            },
                            selectedEntry = selectedEntry,
                            onEntrySelected = { entry ->
                                selectedEntry = if (selectedEntry == entry) null else entry
                            },
                            onEditEntry = { entry ->
                                editingEntry = entry
                            }
                        )
                    }
                }

                editingEntry?.let { entry ->
                    EditEntryDialog(
                        entry = entry,
                        onDismiss = { editingEntry = null },
                        onSave = { updatedContent ->
                            lifecycleScope.launch {
                                dao.update(entry.copy(content = updatedContent))
                                editingEntry = null
                            }
                        }
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

                lifecycleScope.launch {
                    val entryToUpdate = selectedEntry
                    if (entryToUpdate != null) {
                        val updatedEntry = entryToUpdate.copy(
                            content = entryToUpdate.content + "\n" + recognizedText
                        )
                        dao.update(updatedEntry)
                        selectedEntry = null // Deselect after update
                    } else {
                        val lowerCaseText = recognizedText.lowercase(Locale.getDefault())

                        val categoryKeywords = mapOf(
                            "journal" to "journal",
                            "todo" to "todo",
                            "to-do" to "todo",
                            "todoo" to "todo",
                            "kaufen" to "kaufen",
                            "baumarkt" to "baumarkt",
                            "eloisa" to "eloisa"
                        )

                        // Find a keyword that matches the start of the recognized text
                        val foundKeyword = categoryKeywords.keys.find { keyword ->
                            lowerCaseText.startsWith(keyword) &&
                                    (lowerCaseText.length == keyword.length || lowerCaseText.getOrNull(keyword.length)?.isWhitespace() == true)
                        }

                        val timestamp = System.currentTimeMillis()

                        val (targetCategory, contentToAdd) = if (foundKeyword != null) {
                            // Keyword was found
                            val category = categoryKeywords[foundKeyword]!!
                            val content = recognizedText.substring(foundKeyword.length).trim()

                            // Switch the selected category in the UI
                            if (selectedCategory.value != category) {
                                selectedCategory.value = category
                            }
                            category to content
                        } else {
                            // No keyword found, add the entire text to the "journal" category
                            "journal" to recognizedText
                        }

                        if (contentToAdd.isNotEmpty()) {
                            val entry = JournalEntry(
                                title = targetCategory,
                                content = contentToAdd,
                                timestamp = timestamp
                            )
                            dao.insert(entry)
                        }
                    }
                }
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
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(entry.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
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
    onEntrySelected: (JournalEntry) -> Unit,
    onEditEntry: (JournalEntry) -> Unit
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent
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
                        }
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
                            Column(modifier = Modifier.padding(8.dp)) {
                                val date = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(entry.timestamp),
                                    ZoneId.systemDefault()
                                )
                                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                Text(
                                    text = date.format(formatter),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = entry.content,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
            onEntrySelected = {},
            onEditEntry = {}
        )
    }
}