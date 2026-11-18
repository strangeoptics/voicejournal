package com.example.voicejournal.ui.screens.importexport

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.resolvedZoneId
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.TimeZoneUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ImportActivity : ComponentActivity() {

    private val viewModel: ImportViewModel by viewModels {
        ImportViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = extractSafeImportUri(intent)
        if (data == null) {
            Toast.makeText(this, "Keine gültige Datei zum Importieren gefunden.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Nur beim ersten Start laden, damit Rotationen verworfene Einträge nicht überschreiben
        if (savedInstanceState == null) {
            viewModel.loadFile(data)
        }

        setContent {
            VoicejournalTheme {
                ImportScreen(
                    viewModel = viewModel,
                    onClose = { finish() },
                    onComplete = {
                        Toast.makeText(this, "Erfolgreich importiert!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val data = extractSafeImportUri(intent)
        if (data != null) {
            viewModel.loadFile(data, forceReload = true)
        } else {
            Log.w("SECURITY_ALERT", "Blocked invalid or unsupported intent in ImportActivity.onNewIntent")
            Toast.makeText(this, "Ungültige Import-Anfrage abgelehnt.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractSafeImportUri(incomingIntent: Intent?): Uri? {
        if (incomingIntent == null) return null
        val action = incomingIntent.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) {
            Log.w("SECURITY_ALERT", "Blocked unsupported action on ImportActivity: $action")
            return null
        }

        val uri: Uri? = try {
            when (action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(incomingIntent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?: incomingIntent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                }
                Intent.ACTION_VIEW -> incomingIntent.data
                else -> null
            }
        } catch (e: BadParcelableException) {
            Log.w("SECURITY_ALERT", "Malformed parcelable extra in ImportActivity intent", e)
            null
        } catch (e: RuntimeException) {
            Log.w("SECURITY_ALERT", "Failed to read URI from ImportActivity intent", e)
            null
        }

        if (uri == null) {
            return null
        }
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            Log.w("SECURITY_ALERT", "Blocked non-content URI scheme in ImportActivity: ${uri.scheme}")
            return null
        }
        return uri
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onClose: () -> Unit,
    onComplete: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einträge importieren (${entries.size})") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                    }
                },
                actions = {
                    val canSave = entries.isNotEmpty() && selectedCategory != null
                    IconButton(
                        onClick = { viewModel.saveEntries(onComplete) },
                        enabled = canSave
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                if (error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (categories.isNotEmpty() && entries.isNotEmpty()) {
                    CategorySelectionDropdown(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { viewModel.selectCategory(it) }
                    )
                }

                if (entries.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            SwipeableEntryItem(
                                entry = entry,
                                onRemove = { viewModel.removeEntry(entry) }
                            )
                        }
                    }
                } else if (!isLoading && error == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Keine Einträge zum Importieren gefunden.")
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectionDropdown(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelect: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Filter out the 'Gelöscht' category which we shouldn't map imports to
    val selectableCategories = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }

    Box(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                value = selectedCategory?.category ?: "Ziel-Kategorie wählen...",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                selectableCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.category) },
                        onClick = {
                            onCategorySelect(category)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEntryItem(
    entry: JournalEntry,
    onRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.40f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> Color.Red
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                val entryZone = entry.resolvedZoneId()
                val viewerZone = ZoneId.systemDefault()
                val startInstant = Instant.ofEpochMilli(entry.start_datetime)
                val baseDateText = startInstant.atZone(entryZone).format(formatter)
                val entryOffset = entryZone.rules.getOffset(startInstant)
                val viewerOffset = viewerZone.rules.getOffset(startInstant)
                val dateText = if (entryOffset != viewerOffset) {
                    "$baseDateText (${TimeZoneUtils.formatUtcOffset(entryOffset)})"
                } else {
                    baseDateText
                }
                
                Text(text = dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}