package com.example.voicejournal.ui.screens.search

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.voicejournal.EditEntryActivity
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.ui.components.SearchJournalEntryItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Search") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.onSearchClicked()
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.onSearchClicked()
                    keyboardController?.hide()
                })
            )

            SearchResultsList(entries = searchResults)
        }
    }
}

@Composable
fun SearchResultsList(entries: List<EntryWithCategories>) {
    val groupedEntries = entries.groupBy {
        // Group by day using java.time
        Instant.ofEpochMilli(it.entry.start_datetime)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
    val context = LocalContext.current

    LazyColumn {
        groupedEntries.forEach { (date, entriesOnDate) ->
            item {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd, EE", Locale.GERMAN)),
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(entriesOnDate) { entry ->
                SearchJournalEntryItem(
                    entryWithCategories = entry,
                    isSelected = false, // No selection in search results
                    showCategoryTags = true, // Always show tags in search
                    truncationLength = 200, // A reasonable default
                    onEntrySelected = {
                        // Navigate to edit screen on click
                        val intent = EditEntryActivity.newIntent(context, it.entry.id)
                        context.startActivity(intent)
                    },
                    onEditEntry = {
                        val intent = EditEntryActivity.newIntent(context, it.entry.id)
                        context.startActivity(intent)
                    },
                    onPhotoIconClicked = {} // No action for photo icon in search
                )
            }
        }
    }
}