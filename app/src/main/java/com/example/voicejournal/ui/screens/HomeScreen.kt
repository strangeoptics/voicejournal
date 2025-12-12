package com.example.voicejournal.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    groupedEntries: Map<LocalDate, List<EntryWithCategories>> = emptyMap(),
    categories: List<String> = emptyList(),
    selectedCategory: String = "",
    truncationLength: Int,
    showCategoryTags: Boolean,
    onCategoryChange: (String) -> Unit = {},
    onDeleteEntry: (EntryWithCategories) -> Unit = {},
    selectedEntry: EntryWithCategories? = null,
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
    onEntrySelected: (EntryWithCategories) -> Unit = {},
    onEditEntry: (EntryWithCategories) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onDateLongClicked: (LocalDate) -> Unit = {},
    onPhotoIconClicked: (EntryWithCategories) -> Unit = {}
) {
    var expandedIds by remember { mutableStateOf<Set<UUID>>(emptySet()) }
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

        val lazyListState = rememberLazyListState()
        val buffer = 5
        val endOfListReached by remember {
            derivedStateOf {
                val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                val totalItems = lazyListState.layoutInfo.totalItemsCount
                val isCategoryShowAll = categories.find { it == selectedCategory }?.let { category ->
                    // This is a placeholder. You need to get the actual Category object.
                    // For now, let's assume if it's not "journal", "todo", etc., it might be a `showAll` category.
                    // A better approach is to pass the whole Category object to HomeScreen.
                    false // Assuming default behavior is not showAll
                } ?: false

                !isCategoryShowAll && totalItems > 0 && lastVisibleItem != null && lastVisibleItem.index >= totalItems - 1 - buffer
            }
        }

        LaunchedEffect(endOfListReached) {
            if (endOfListReached) {
                onLoadMore()
            }
        }

        LazyColumn(
            state = lazyListState,
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

                items(entries, key = { it.entry.id }) { entryWithCategories ->
                    val isSelected = selectedEntry == entryWithCategories
                    val isExpanded = entryWithCategories.entry.id in expandedIds
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                onDeleteEntry(entryWithCategories)
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
                                    onClick = {
                                        onEntrySelected(entryWithCategories)
                                        if (entryWithCategories.entry.content.length > truncationLength) {
                                            expandedIds = if (isExpanded) {
                                                expandedIds - entryWithCategories.entry.id
                                            } else {
                                                expandedIds + entryWithCategories.entry.id
                                            }
                                        }
                                    },
                                    onLongClick = { onEditEntry(entryWithCategories) }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                            ) {
                                val startDate = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(entryWithCategories.entry.start_datetime),
                                    ZoneId.systemDefault()
                                )
                                val stopDate = entryWithCategories.entry.stop_datetime?.let {
                                    LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(it),
                                        ZoneId.systemDefault()
                                    )
                                }
                                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                val timeText = if (stopDate != null) {
                                    "${startDate.format(formatter)} - ${stopDate.format(formatter)}"
                                } else {
                                    startDate.format(formatter)
                                }
                                val content = entryWithCategories.entry.content
                                val textToShow = if (!isExpanded && content.length > truncationLength) {
                                    "${content.take(truncationLength)}..."
                                } else {
                                    content
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = textToShow,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 4.dp, end = 8.dp)
                                    )
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (showCategoryTags) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            entryWithCategories.categories.forEach { category ->
                                                if (category.category != selectedCategory) {
                                                    Card(
                                                        modifier = Modifier.padding(end = 4.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                    ) {
                                                        Text(
                                                            text = category.category,
                                                            modifier = Modifier.padding(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (entryWithCategories.entry.hasImage) {
                                            IconButton(
                                                onClick = { onPhotoIconClicked(entryWithCategories) },
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
        val sampleCategoriesData = remember {
            listOf(
                Category(category = "journal", aliases = "journal,tagebuch"),
                Category(category = "todo", aliases = "todo,to-do")
            )
        }
        val sampleCategories = remember(sampleCategoriesData) {
            sampleCategoriesData.map { it.category }
        }
        var selectedCategory by remember { mutableStateOf(sampleCategories.first()) }
        val entries = remember {
            listOf(
                EntryWithCategories(
                    entry = JournalEntry(content = "This is a preview entry.".repeat(20), start_datetime = System.currentTimeMillis()),
                    categories = listOf(Category(1, "journal", aliases = "journal"), Category(2, "todo", aliases = "todo"))
                ),
                EntryWithCategories(
                    entry = JournalEntry(content = "This is a todo preview.", start_datetime = System.currentTimeMillis(), stop_datetime = System.currentTimeMillis() + 60000, hasImage = true),
                    categories = listOf(Category(2, "todo", aliases = "todo"))
                )
            )
        }
        val groupedEntries = entries.filter { it.categories.any { c -> c.category == selectedCategory } }.groupBy {
            Instant.ofEpochMilli(it.entry.start_datetime).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        HomeScreen(
            groupedEntries = groupedEntries,
            categories = sampleCategories,
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
            truncationLength = 160,
            showCategoryTags = true
        )
    }
}
