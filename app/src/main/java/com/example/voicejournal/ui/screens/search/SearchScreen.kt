package com.example.voicejournal.ui.screens.search

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.EntryWithCategories
import java.text.SimpleDateFormat
import java.util.Date
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
        // Group by day
        val date = Date(it.entry.start_datetime)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }

    LazyColumn {
        groupedEntries.forEach { (date, entriesOnDate) ->
            item {
                Text(
                    text = date,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(entriesOnDate) { entry ->
                // This is a simplified representation. You might want to use your existing EntryItem composable
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = entry.entry.content, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Categories: ${entry.categories.joinToString { it.category }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}