package com.example.voicejournal.ui.screens.search

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.voicejournal.MainActivity
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.localDate
import com.example.voicejournal.ui.components.JournalEntryItem
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateUp: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
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
        it.entry.localDate()
    }
    val context = LocalContext.current

    LazyColumn {
        groupedEntries.forEach { (date, entriesOnDate) ->
            item(key = "header_$date") {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd, EE", Locale.GERMAN)),
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(
                items = entriesOnDate,
                key = { it.entry.id }
            ) { entry ->
                JournalEntryItem(
                    entryWithCategories = entry,
                    showCheckbox = false,
                    expandOnClick = false,
                    truncationLength = 200,
                    onEntrySelected = {
                        val intent = EditEntryActivity.newIntent(context, it.entry.id)
                        context.startActivity(intent)
                    },
                    onLongClickEntry = {
                        val intent = EditEntryActivity.newIntent(context, it.entry.id)
                        context.startActivity(intent)
                    },
                    onGoToEntryClicked = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(MainActivity.EXTRA_ENTRY_ID, it.entry.id.toString())
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }
                )
            }
        }
    }
}