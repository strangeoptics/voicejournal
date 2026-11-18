package com.example.voicejournal.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import com.example.voicejournal.data.localDate
import com.example.voicejournal.ui.components.FastScroller
import com.example.voicejournal.ui.components.JournalEntryItem
import java.time.LocalDate
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
    selectedEntry: EntryWithCategories? = null,
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
    onEntrySelected: (EntryWithCategories) -> Unit = {},
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

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                    onScrolledToEntry()
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
                    key = { index -> pagedEntries.peek(index)?.entry?.id ?: "placeholder_$index" }
                ) { index ->
                    val entryWithCategories = pagedEntries[index]
                    if (entryWithCategories != null) {

                        val currentDate = entryWithCategories.entry.localDate()
                        val prevDate = if (index > 0) {
                            pagedEntries.peek(index - 1)?.entry?.localDate()
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

                        JournalEntryItem(
                            entryWithCategories = entryWithCategories,
                            isSelected = isSelected,
                            isMultiSelected = isMultiSelected,
                            isSelectionMode = isSelectionMode,
                            showCategoryTags = showCategoryTags,
                            truncationLength = truncationLength,
                            onEntrySelected = onEntrySelected,
                            onToggleSelection = onToggleEntrySelection,
                            onPhotoIconClicked = onPhotoIconClicked,
                            onCheckedChange = { onCheckedChange(entryWithCategories) }
                        )
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