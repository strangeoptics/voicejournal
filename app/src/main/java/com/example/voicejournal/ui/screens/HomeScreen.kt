package com.example.voicejournal.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    groupedEntries: Map<LocalDate, List<JournalEntry>> = emptyMap(),
    categories: List<String> = emptyList(),
    selectedCategory: String = "",
    onCategoryChange: (String) -> Unit = {},
    onDeleteEntry: (JournalEntry) -> Unit = {},
    selectedEntry: JournalEntry? = null,
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
    onEntrySelected: (JournalEntry) -> Unit = {},
    onEditEntry: (JournalEntry) -> Unit = {},
    onMoreClicked: () -> Unit = {},
    onDateLongClicked: (LocalDate) -> Unit = {},
    onPhotoIconClicked: (JournalEntry) -> Unit = {}
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
                            .combinedClickable(
                                onClick = { onDateSelected(date) },
                                onLongClick = { onDateLongClicked(date) }
                            ),
                        color = if (isDateSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd, EE", Locale.GERMAN)),
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
                        positionalThreshold = { it * 0.75f }
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
                                    modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp, end = 48.dp)
                                )
                                Text(
                                    text = date.format(formatter),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                                if (entry.hasImage) {
                                    IconButton(
                                        onClick = { onPhotoIconClicked(entry) },
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = "Open Photo",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("more")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    VoicejournalTheme {
        // Simulate categories from the database for the preview
        val sampleCategoriesData = remember {
            listOf(
                Category(category = "journal", aliases = "journal,tagebuch"),
                Category(category = "todo", aliases = "todo,to-do"),
                Category(category = "kaufen", aliases = "kaufen"),
                Category(category = "baumarkt", aliases = "baumarkt"),
                Category(category = "eloisa", aliases = "eloisa")
            )
        }
        val sampleCategories = remember(sampleCategoriesData) {
            sampleCategoriesData.map { it.category }
        }
        var selectedCategory by remember { mutableStateOf(sampleCategories.first()) }
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
                    timestamp = System.currentTimeMillis(),
                    hasImage = true
                )
            )
        }
        val groupedEntries = entries.filter { it.title == selectedCategory }.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        HomeScreen(
            groupedEntries = groupedEntries,
            categories = sampleCategories, // Pass the sample categories
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
            onDeleteEntry = {},
            selectedEntry = null,
            selectedDate = null,
            onDateSelected = {},
            onEntrySelected = {},
            onEditEntry = {},
            onMoreClicked = {},
            onDateLongClicked = {}
        )
    }
}