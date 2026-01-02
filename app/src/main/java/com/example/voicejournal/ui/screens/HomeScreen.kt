package com.example.voicejournal.ui.screens

import android.util.Log
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.ui.components.FastScroller
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
    pagedEntries: LazyPagingItems<EntryWithCategories>,
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

        LaunchedEffect(pagedEntries.itemCount) {
            Log.d("Paging", "List updated. New total item count: ${pagedEntries.itemCount}")
        }

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

        Box {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                items(
                    count = pagedEntries.itemCount,
                    key = { index -> pagedEntries.peek(index)?.entry?.id ?: UUID.randomUUID() }
                ) { index ->
                    val entryWithCategories = pagedEntries[index]
                    if (entryWithCategories != null) {

                        val currentDate = Instant.ofEpochMilli(entryWithCategories.entry.start_datetime).atZone(
                            ZoneId.systemDefault()).toLocalDate()
                        val prevDate = if (index > 0) {
                            pagedEntries.peek(index - 1)?.let {
                                Instant.ofEpochMilli(it.entry.start_datetime).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                        } else {
                            null
                        }

                        if (index == 0 || (prevDate != null && currentDate != prevDate)) {
                            val isDateSelected = selectedDate == currentDate
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onDateSelected(currentDate) },
                                        onLongClick = { onDateLongClicked(currentDate) }
                                    ),
                                color = if (isDateSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ) {
                                Text(
                                    text = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd, EE", Locale.GERMAN)),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }


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
            FastScroller(
                listState = lazyListState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
