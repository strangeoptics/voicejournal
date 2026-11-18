package com.example.voicejournal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.voicejournal.util.TimeZoneUtils
import java.time.ZoneId

@Composable
fun TimeZonePickerDialog(
    selectedZoneId: String?,
    referenceMillis: Long = System.currentTimeMillis(),
    onZoneSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val systemZone = remember { ZoneId.systemDefault() }
    val allZones = remember(referenceMillis) {
        TimeZoneUtils.availableZoneIdsSorted().map { id ->
            val zone = ZoneId.of(id)
            id to TimeZoneUtils.formatZoneLabel(zone, referenceMillis)
        }
    }

    val filteredZones = remember(searchQuery, allZones) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            allZones
        } else {
            allZones.filter { (_, label) -> label.contains(trimmed, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeitzone auswählen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Suchen (z. B. Sydney, Berlin, UTC+10)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        item(key = "__device_zone__") {
                            val deviceLabel = "Geräte-Zeitzone (${TimeZoneUtils.formatZoneLabel(systemZone, referenceMillis)})"
                            TimeZoneOptionRow(
                                label = deviceLabel,
                                selected = selectedZoneId == systemZone.id,
                                onClick = { onZoneSelected(systemZone.id) }
                            )
                        }
                        item(key = "__system_default__") {
                            TimeZoneOptionRow(
                                label = "Dynamischer Systemstandard",
                                selected = selectedZoneId == null,
                                onClick = { onZoneSelected(null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    items(items = filteredZones, key = { it.first }) { (id, label) ->
                        TimeZoneOptionRow(
                            label = label,
                            selected = selectedZoneId == id,
                            onClick = { onZoneSelected(id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun TimeZoneOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
