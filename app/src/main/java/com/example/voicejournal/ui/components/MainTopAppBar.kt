package com.example.voicejournal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.voicejournal.data.EntryWithCategories
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    selectedEntryIds: Set<UUID>,
    selectedEntry: EntryWithCategories?,
    isRecycleBinSelected: Boolean,
    showCategoryTags: Boolean,
    hasGpsTrackForSelectedDate: Boolean,
    canUndo: Boolean,
    onClearSelection: () -> Unit,
    onOpenDrawer: () -> Unit,
    onEditEntryById: (UUID) -> Unit,
    onEditSelectedSingleEntry: (EntryWithCategories) -> Unit,
    onChangeTimeZoneClicked: () -> Unit,
    onShareAsTextClicked: () -> Unit,
    onExportAsVjClicked: () -> Unit,
    onRestoreSelectedClicked: () -> Unit,
    onRequestHardDeleteSelectedClicked: () -> Unit,
    onDeleteSelectedClicked: () -> Unit,
    onToggleCategoryTagsClicked: () -> Unit,
    onOpenGpsTrackClicked: () -> Unit,
    onUndoDeleteClicked: () -> Unit,
    onCopyToClipboardClicked: () -> Unit
) {
    val isMultiSelectActive = selectedEntryIds.isNotEmpty()

    TopAppBar(
        title = {
            if (isMultiSelectActive) {
                Text("${selectedEntryIds.size} ausgewählt")
            } else {
                Text("Voice Journal")
            }
        },
        navigationIcon = {
            if (isMultiSelectActive) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Auswahl aufheben")
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Navigation Menu")
                }
            }
        },
        actions = {
            if (isMultiSelectActive) {
                if (selectedEntryIds.size == 1) {
                    IconButton(onClick = { onEditEntryById(selectedEntryIds.first()) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Eintrag bearbeiten")
                    }
                }

                IconButton(onClick = onChangeTimeZoneClicked) {
                    Icon(public, contentDescription = "Zeitzone ändern")
                }

                var shareMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { shareMenuExpanded = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Teilen")
                    }
                    DropdownMenu(
                        expanded = shareMenuExpanded,
                        onDismissRequest = { shareMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Als Text teilen") },
                            onClick = {
                                shareMenuExpanded = false
                                onShareAsTextClicked()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Als .vj Datei exportieren") },
                            onClick = {
                                shareMenuExpanded = false
                                onExportAsVjClicked()
                            }
                        )
                    }
                }

                if (isRecycleBinSelected) {
                    IconButton(onClick = onRestoreSelectedClicked) {
                        Icon(Icons.Filled.Restore, contentDescription = "Einträge wiederherstellen")
                    }
                    IconButton(onClick = onRequestHardDeleteSelectedClicked) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = "Endgültig löschen")
                    }
                } else {
                    IconButton(onClick = onDeleteSelectedClicked) {
                        Icon(Icons.Filled.Delete, contentDescription = "Einträge löschen")
                    }
                }
            } else {
                if (selectedEntry != null) {
                    IconButton(onClick = { onEditSelectedSingleEntry(selectedEntry) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Eintrag bearbeiten")
                    }
                }
                IconButton(onClick = onToggleCategoryTagsClicked) {
                    Icon(
                        imageVector = if (showCategoryTags) {
                            Icons.AutoMirrored.Filled.Label
                        } else {
                            Icons.AutoMirrored.Filled.LabelOff
                        },
                        contentDescription = "Toggle Category Tags"
                    )
                }
                if (hasGpsTrackForSelectedDate) {
                    IconButton(onClick = onOpenGpsTrackClicked) {
                        Icon(Icons.Filled.Map, contentDescription = "Show GPS Track on Map")
                    }
                }
                if (canUndo) {
                    IconButton(onClick = onUndoDeleteClicked) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                }
                IconButton(onClick = onCopyToClipboardClicked) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "In die Zwischenablage kopieren")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isMultiSelectActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    )
}
