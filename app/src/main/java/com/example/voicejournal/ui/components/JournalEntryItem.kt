package com.example.voicejournal.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.voicejournal.MainViewModel
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.resolvedZoneId
import com.example.voicejournal.util.TimeZoneUtils
import java.util.UUID

internal fun shouldTruncateEntryContent(
    categories: List<Category>,
    isExpanded: Boolean,
    truncationLength: Int,
    contentLength: Int
): Boolean {
    if (categories.any { it.deleteLineOnDoubleTap }) return false
    return !isExpanded && truncationLength > 0 && contentLength > truncationLength
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalEntryItem(
    entryWithCategories: EntryWithCategories,
    isSelected: Boolean = false,
    isMultiSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    showCategoryTags: Boolean = true,
    showCheckbox: Boolean = true,
    expandOnClick: Boolean = true,
    truncationLength: Int,
    onEntrySelected: (EntryWithCategories) -> Unit,
    onLongClickEntry: ((EntryWithCategories) -> Unit)? = null,
    onToggleSelection: (UUID) -> Unit = {},
    onPhotoIconClicked: (EntryWithCategories) -> Unit = {},
    onGoToEntryClicked: ((EntryWithCategories) -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    onDeleteLineAt: ((Int) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    val content = entryWithCategories.entry.content
    val isCheckable = showCheckbox && entryWithCategories.categories.any { it.checkable }
    val isLineDeleteCategory = entryWithCategories.categories.any { it.deleteLineOnDoubleTap }
    val isLineDeleteInteractive = isLineDeleteCategory &&
        onDeleteLineAt != null &&
        !isSelectionMode &&
        entryWithCategories.entry.deletedAt == null
    val canTruncate = !isLineDeleteCategory && truncationLength > 0 && content.length > truncationLength
    val shouldShowDate = entryWithCategories.categories.isEmpty() || entryWithCategories.categories.any { it.showDate }

    val lines = remember(content, isLineDeleteInteractive) {
        if (isLineDeleteInteractive) MainViewModel.splitContentIntoLines(content) else emptyList()
    }

    val handleCardClick: () -> Unit = {
        if (isSelectionMode) {
            onToggleSelection(entryWithCategories.entry.id)
        } else {
            onEntrySelected(entryWithCategories)
            if (expandOnClick && canTruncate) {
                isExpanded = !isExpanded
            }
        }
    }

    val handleCardLongClick: () -> Unit = {
        if (onLongClickEntry != null) {
            onLongClickEntry(entryWithCategories)
        } else {
            onToggleSelection(entryWithCategories.entry.id)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = handleCardClick,
                onLongClick = handleCardLongClick,
                onDoubleClick = if (isLineDeleteInteractive && lines.size <= 1) {
                    { onDeleteLineAt.invoke(0) }
                } else {
                    null
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isMultiSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
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
            val textToShow = if (
                shouldTruncateEntryContent(
                    categories = entryWithCategories.categories,
                    isExpanded = isExpanded,
                    truncationLength = truncationLength,
                    contentLength = content.length
                )
            ) {
                "${content.take(truncationLength)}..."
            } else {
                content
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCheckable) {
                    Checkbox(
                        checked = entryWithCategories.entry.checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isLineDeleteInteractive && lines.size > 1) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 2.dp, end = 8.dp)
                            ) {
                                lines.forEachIndexed { lineIndex, lineText ->
                                    Text(
                                        text = lineText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = handleCardClick,
                                                onLongClick = handleCardLongClick,
                                                onDoubleClick = { onDeleteLineAt.invoke(lineIndex) }
                                            )
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = textToShow,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 4.dp, end = 8.dp)
                            )
                        }
                        if (shouldShowDate) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
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
                    if (onGoToEntryClicked != null) {
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
}
