package com.example.voicejournal.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class MainDialogType {
    BATCH_TIME_ZONE,
    HARD_DELETE_SELECTION,
    SOFT_DELETE_ALL
}

@Composable
fun MainActionDialogs(
    activeDialog: MainDialogType?,
    selectedCount: Int,
    onBatchZoneSelected: (String?) -> Unit,
    onConfirmHardDeleteSelection: () -> Unit,
    onConfirmSoftDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    when (activeDialog) {
        MainDialogType.BATCH_TIME_ZONE -> {
            TimeZonePickerDialog(
                selectedZoneId = null,
                onZoneSelected = onBatchZoneSelected,
                onDismiss = onDismiss
            )
        }
        MainDialogType.HARD_DELETE_SELECTION -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Auswahl endgültig löschen") },
                text = {
                    Text("Möchtest du diese $selectedCount Einträge wirklich unwiderruflich löschen?")
                },
                confirmButton = {
                    TextButton(onClick = onConfirmHardDeleteSelection) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            )
        }
        MainDialogType.SOFT_DELETE_ALL -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Alle Einträge löschen") },
                text = {
                    Text("Möchtest du wirklich alle aktiven Tagebucheinträge in den Papierkorb (\"Gelöscht\") verschieben?")
                },
                confirmButton = {
                    TextButton(onClick = onConfirmSoftDeleteAll) {
                        Text("Verschieben")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            )
        }
        null -> Unit
    }
}
