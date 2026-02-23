package com.example.voicejournal.ui.screens

import android.util.Log
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.ui.components.FastScroller
import com.example.voicejournal.ui.components.JournalEntryItem
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
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
    selectedEntryIds: Set<UUID> = emptySet(),
    onToggleEntrySelection: (UUID) -> Unit = {},
    onCategoryChange: (String) -> Unit = {},
    onDeleteEntry: (EntryWithCategories) -> Unit = {},
    onHardDeleteEntry: (EntryWithCategories) -> Unit = {},
    selectedEntry: EntryWithCategories? = null,
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
    onEntrySelected: (EntryWithCategories) -> Unit = {},
    onEditEntry: (EntryWithCategories) -> Unit = {},
    onDateLongClicked: (LocalDate) -> Unit = {},
    onPhotoIconClicked: (EntryWithCategories) -> Unit = {},
    scrollToEntryId: UUID?,
    onScrolledToEntry: () -> Unit,
    onCheckedChange: (EntryWithCategories) -> Unit
) {
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

        LaunchedEffect(pagedEntries.itemCount, scrollToEntryId) {
            if (scrollToEntryId != null && pagedEntries.itemCount > 0) {
                val targetIndex = (0 until pagedEntries.itemCount).find { index ->
                    pagedEntries.peek(index)?.entry?.id == scrollToEntryId
                }
                if (targetIndex != null) {
                    lazyListState.scrollToItem(targetIndex)
                    // onScrolledToEntry() // DO NOT CALL THIS HERE TO PREVENT JUMP-BACK
                }
            }
        }

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
                        val isMultiSelected = selectedEntryIds.contains(entryWithCategories.entry.id)
                        val isSelectionMode = selectedEntryIds.isNotEmpty()
                        val isDeletedCategory = selectedCategory == "Gelöscht"
                        
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    if (!isDeletedCategory) {
                                        onDeleteEntry(entryWithCategories)
                                    } else {
                                        onHardDeleteEntry(entryWithCategories)
                                    }
                                    false
                                } else {
                                    false
                                }
                            },
                            positionalThreshold = { it * 0.60f }
                        )

                        val currentViewConfiguration = LocalViewConfiguration.current
                        val customViewConfiguration = remember(currentViewConfiguration) {
                            object : ViewConfiguration by currentViewConfiguration {
                                override val touchSlop: Float
                                    get() = currentViewConfiguration.touchSlop * 2.5f
                            }
                        }

                        CompositionLocalProvider(LocalViewConfiguration provides customViewConfiguration) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromEndToStart = false,
                                gesturesEnabled = true, // Enabled for both standard and hard delete
                                backgroundContent = {
                                    val color = when (dismissState.targetValue) {
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
                                            imageVector = if (isDeletedCategory) Icons.Default.DeleteForever else Icons.Default.Delete,
                                            contentDescription = if (isDeletedCategory) "Hard Delete" else "Delete",
                                            tint = Color.White
                                        )
                                    }
                                }
                            ) {
                                JournalEntryItem(
                                    entryWithCategories = entryWithCategories,
                                    isSelected = isSelected,
                                    isMultiSelected = isMultiSelected,
                                    isSelectionMode = isSelectionMode,
                                    showCategoryTags = showCategoryTags,
                                    truncationLength = truncationLength,
                                    onEntrySelected = onEntrySelected,
                                    onEditEntry = onEditEntry,
                                    onToggleSelection = onToggleEntrySelection,
                                    onPhotoIconClicked = onPhotoIconClicked,
                                    onCheckedChange = { onCheckedChange(entryWithCategories) }
                                )
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