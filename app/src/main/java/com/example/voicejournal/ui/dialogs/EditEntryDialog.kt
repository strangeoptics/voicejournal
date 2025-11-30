package com.example.voicejournal.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.EntryWithCategories
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryDialog(
    entry: EntryWithCategories,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>, String, Long, Boolean) -> Unit
) {
    var text by remember { mutableStateOf(entry.entry.content) }
    var hasImage by remember { mutableStateOf(entry.entry.hasImage) }
    val selectedCategories = remember { mutableStateOf(entry.categories.map { it.category }) }
    val context = LocalContext.current
    var currentDateTime by remember {
        mutableStateOf(
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.entry.timestamp),
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
                Text("Categories:")
                Column(Modifier.height(100.dp).verticalScroll(rememberScrollState())) {
                    categories.forEach { category ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedCategories.value.contains(category),
                                onCheckedChange = {
                                    val currentSelection = selectedCategories.value.toMutableList()
                                    if (it) {
                                        currentSelection.add(category)
                                    } else {
                                        currentSelection.remove(category)
                                    }
                                    selectedCategories.value = currentSelection
                                }
                            )
                            Text(category)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasImage,
                        onCheckedChange = { hasImage = it }
                    )
                    Text("Has Image")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newTimestamp = currentDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSave(selectedCategories.value, text, newTimestamp, hasImage)
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