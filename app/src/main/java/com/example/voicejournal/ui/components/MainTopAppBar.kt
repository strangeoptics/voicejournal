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
import androidx.compose.ui.res.stringResource
import com.example.voicejournal.R
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
                Text(stringResource(R.string.selection_count, selectedEntryIds.size))
            } else {
                Text(stringResource(R.string.app_name))
            }
        },
        navigationIcon = {
            if (isMultiSelectActive) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.nav_open_menu))
                }
            }
        },
        actions = {
            if (isMultiSelectActive) {
                if (selectedEntryIds.size == 1) {
                    IconButton(onClick = { onEditEntryById(selectedEntryIds.first()) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit_entry))
                    }
                }

                IconButton(onClick = onChangeTimeZoneClicked) {
                    Icon(PublicIcon, contentDescription = stringResource(R.string.action_change_timezone))
                }

                var shareMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { shareMenuExpanded = true }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    DropdownMenu(
                        expanded = shareMenuExpanded,
                        onDismissRequest = { shareMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share_as_text)) },
                            onClick = {
                                shareMenuExpanded = false
                                onShareAsTextClicked()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_export_as_vj)) },
                            onClick = {
                                shareMenuExpanded = false
                                onExportAsVjClicked()
                            }
                        )
                    }
                }

                if (isRecycleBinSelected) {
                    IconButton(onClick = onRestoreSelectedClicked) {
                        Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.action_restore_entries))
                    }
                    IconButton(onClick = onRequestHardDeleteSelectedClicked) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.action_hard_delete))
                    }
                } else {
                    IconButton(onClick = onDeleteSelectedClicked) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_entries))
                    }
                }
            } else {
                if (selectedEntry != null) {
                    IconButton(onClick = { onEditSelectedSingleEntry(selectedEntry) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit_entry))
                    }
                }
                IconButton(onClick = onToggleCategoryTagsClicked) {
                    Icon(
                        imageVector = if (showCategoryTags) {
                            Icons.AutoMirrored.Filled.Label
                        } else {
                            Icons.AutoMirrored.Filled.LabelOff
                        },
                        contentDescription = stringResource(R.string.action_toggle_category_tags)
                    )
                }
                if (hasGpsTrackForSelectedDate) {
                    IconButton(onClick = onOpenGpsTrackClicked) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.action_show_gps_on_map))
                    }
                }
                if (canUndo) {
                    IconButton(onClick = onUndoDeleteClicked) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
                    }
                }
                IconButton(onClick = onCopyToClipboardClicked) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.action_copy_to_clipboard))
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
