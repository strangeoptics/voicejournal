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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.ui.theme.VoicejournalTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    companion object {
        const val NOTIFICATION_ACTION = "com.example.voicejournal.NOTIFICATION_ACTION"
    }

    private lateinit var speechRecognizer: SpeechRecognizer
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val dao by lazy { db.journalEntryDao() }

    private val categories = listOf("journal", "todo", "kaufen", "ideen")
    private val selectedCategory = mutableStateOf(categories.first())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setupSpeechRecognizer()

        setContent {
            VoicejournalTheme {
                val context = LocalContext.current
                val category by selectedCategory

                val threeDaysAgo = remember {
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -3)
                    }.timeInMillis
                }
                val entries by dao.getEntriesSince(threeDaysAgo).collectAsState(initial = emptyList())
                val filteredEntries = entries.filter { it.title == category }
                val textToShow = filteredEntries.joinToString("\n") { entry ->
                    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault())
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    "[${date.format(formatter)}] ${entry.content}"
                }


                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Voice Journal") },
                            actions = {
                                IconButton(onClick = {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("VoiceJournal", textToShow)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = "In die Zwischenablage kopieren")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding),
                        entries = filteredEntries,
                        categories = categories,
                        selectedCategory = category,
                        onCategoryChange = { selectedCategory.value = it },
                        onSpeakClick = ::startListening,
                        onDeleteClick = {
                            lifecycleScope.launch {
                                dao.deleteLatestByCategory(selectedCategory.value)
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
                val lowerCaseText = recognizedText.lowercase(Locale.getDefault())

                val categoryKeywords = mapOf(
                    "journal" to "journal",
                    "todo" to "todo",
                    "to-do" to "todo",
                    "todoo" to "todo",
                    "kaufen" to "kaufen",
                    "ideen" to "ideen"
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
                    lifecycleScope.launch {
                        dao.insert(entry)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    entries: List<JournalEntry>,
    categories: List<String>,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    onSpeakClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                onSpeakClick()
            }
        }
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = {
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
                Text(text = "Notification")
            }
            Button(onClick = {
                when {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        onSpeakClick()
                    }
                    else -> {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }) {
                Text(text = "Sprechen")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = onDeleteClick) {
                Text(text = "Löschen")
            }
        }

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Kategorie") },
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
            items(entries) { entry ->
                val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                Text(text = "[${date.format(formatter)}] ${entry.content}")
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

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VoicejournalTheme {
        val categories = listOf("journal", "todo", "kaufen", "ideen")
        var selectedCategory by remember { mutableStateOf(categories.first()) }
        val entries = remember {
            listOf(
                JournalEntry(id = 1, title = "journal", content = "This is a preview entry.", timestamp = System.currentTimeMillis()),
                JournalEntry(id = 2, title = "todo", content = "This is a todo preview.", timestamp = System.currentTimeMillis())
            )
        }
        val filteredEntries = entries.filter { it.title == selectedCategory }

        Greeting(
            entries = filteredEntries,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
            onSpeakClick = {},
            onDeleteClick = {}
        )
    }
}
