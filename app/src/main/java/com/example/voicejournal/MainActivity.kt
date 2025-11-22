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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.ui.components.AppDrawer
import com.example.voicejournal.ui.screens.HomeScreen
import com.example.voicejournal.ui.dialogs.SettingsScreen
import com.example.voicejournal.ui.dialogs.EditEntryDialog
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.SpeechRecognitionManager
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
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

    private lateinit var speechRecognitionManager: SpeechRecognitionManager
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(db.journalEntryDao(), getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        speechRecognitionManager = SpeechRecognitionManager(this, { recognizedText ->
            viewModel.processRecognizedText(recognizedText)
        })

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
                val canUndo by viewModel.canUndo.collectAsState()
                val categories by viewModel.categories.collectAsState() // Collect categories as State

                var showSettings by remember { mutableStateOf(false) }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        uri?.let {
                            try {
                                context.contentResolver.openInputStream(it)?.use { inputStream ->
                                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                        val content = reader.readText()
                                        viewModel.importJournalEntries(content)
                                        Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                                    val jsonString = viewModel.exportJournalToJson()
                                    context.contentResolver.openFileDescriptor(uri, "w")?.use {
                                        FileOutputStream(it.fileDescriptor).use { fos ->
                                            fos.write(jsonString.toByteArray())
                                        }
                                    }
                                    Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )

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

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(
                            onSettingsClicked = {
                                scope.launch { drawerState.close() }
                                showSettings = true
                            },
                            onManageCategoriesClicked = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent(context, CategoryManagerActivity::class.java))
                            },
                            onImportJournalClicked = {
                                scope.launch { drawerState.close() }
                                filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
                            },
                            onExportJournalClicked = {
                                scope.launch { drawerState.close() }
                                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                val fileName = "journal_${LocalDate.now().format(formatter)}.jrn"
                                exportLauncher.launch(fileName)
                            },
                            onShowNotificationClicked = {
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
                                scope.launch { drawerState.close() }
                            },
                            onAddTestDataClicked = {
                                viewModel.addTestData()
                                scope.launch { drawerState.close() }
                            }
                        )
                    },
                    content = {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = { Text("Voice Journal") },
                                    navigationIcon = {
                                        IconButton(onClick = {
                                            scope.launch { drawerState.open() }
                                        }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = "Navigation Menu")
                                        }
                                    },
                                    actions = {
                                        if (canUndo) {
                                            IconButton(onClick = viewModel::onUndoDelete) {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                            }
                                        }
                                        IconButton(onClick = {
                                            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("VoiceJournal", textToShow)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Filled.ContentPaste, contentDescription = "In die Zwischenablage kopieren")
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
                            if (showSettings) {
                                SettingsScreen(
                                    currentDays = daysToShow,
                                    onSave = {
                                        viewModel.saveDaysToShow(it)
                                        showSettings = false
                                    },
                                    onDismiss = { showSettings = false }
                                )
                            } else {
                                HomeScreen(
                                    modifier = Modifier.
                                    padding(innerPadding),
                                    groupedEntries = groupedEntries,
                                    categories = categories, // Pass the collected state
                                    selectedCategory = category,
                                    onCategoryChange = viewModel::onCategoryChange,
                                    onDeleteEntry = viewModel::onDeleteEntry,
                                    selectedEntry = selectedEntry,
                                    selectedDate = selectedDate,
                                    onDateSelected = viewModel::onDateSelected,
                                    onEntrySelected = viewModel::onEntrySelected,
                                    onEditEntry = viewModel::onEditEntry,
                                    onMoreClicked = viewModel::onMoreClicked,
                                    onDateLongClicked = { date ->
                                        val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)
                                        val url = "https://photos.google.com/search/${date.format(formatter)}"
                                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) { 
                                            Toast.makeText(context, "Could not open browser.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onPhotoIconClicked = { entry ->
                                        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault()).toLocalDate()
                                        val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)
                                        val url = "https://photos.google.com/search/${date.format(formatter)}"
                                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Could not open browser.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                )

                editingEntry?.let { entry ->
                    EditEntryDialog(
                        entry = entry,
                        onDismiss = viewModel::onDismissEditEntry,
                        onSave = { content, timestamp, hasImage ->
                            viewModel.onSaveEntry(content, timestamp, hasImage)
                        }
                    )
                }
            }
        }
    }

    fun startListening() {
        speechRecognitionManager.startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognitionManager.destroy()
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

    private fun showNotification(context: Context) {
        val channelId = "channel_id"
        val notificationId = 1
        val intent = Intent(context, MainActivity::class.java).apply {
            action = NOTIFICATION_ACTION
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
}
