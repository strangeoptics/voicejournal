package com.example.voicejournal.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.resolvedZoneId
import com.example.voicejournal.util.TimeZoneUtils

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchJournalEntryItem(
    entryWithCategories: EntryWithCategories,
    isSelected: Boolean,
    showCategoryTags: Boolean,
    truncationLength: Int,
    onEntrySelected: (EntryWithCategories) -> Unit,
    onEditEntry: (EntryWithCategories) -> Unit,
    onPhotoIconClicked: (EntryWithCategories) -> Unit,
    onGoToEntryClicked: (EntryWithCategories) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val shouldShowDate = entryWithCategories.categories.isEmpty() || entryWithCategories.categories.any { it.showDate }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = {
                    onEntrySelected(entryWithCategories)
                    if (truncationLength > 0 && entryWithCategories.entry.content.length > truncationLength) {
                        isExpanded = !isExpanded
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
            val timeText = TimeZoneUtils.formatEntryTimeText(
                startMillis = entryWithCategories.entry.start_datetime,
                stopMillis = entryWithCategories.entry.stop_datetime,
                entryZone = entryWithCategories.entry.resolvedZoneId()
            )
            val content = entryWithCategories.entry.content
            val textToShow = if (!isExpanded && truncationLength > 0 && content.length > truncationLength) {
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
                if (shouldShowDate) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
                    IconButton(onClick = { onGoToEntryClicked(entryWithCategories) }) {
                        Icon(
                            imageVector = Icons.Default.ArrowCircleRight,
                            contentDescription = "Go to entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}