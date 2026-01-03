package com.example.voicejournal.ui.screens.search

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(
    private val journalEntryDao: JournalEntryDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // This StateFlow holds the current text in the search field.
    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(SEARCH_QUERY_KEY, "")

    // This holds the query that has been submitted. We initialize it from the saved state.
    private val _submittedQuery = MutableStateFlow(savedStateHandle.get<String>(SUBMITTED_QUERY_KEY) ?: "")

    // When the ViewModel is created (or restored), if there was a submitted query,
    // ensure the search text field is updated to match it.
    init {
        if (_submittedQuery.value.isNotBlank() && searchQuery.value.isBlank()) {
            savedStateHandle[SEARCH_QUERY_KEY] = _submittedQuery.value
        }
    }

    val searchResults: StateFlow<List<EntryWithCategories>> = _submittedQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                journalEntryDao.searchEntries(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(query: String) {
        savedStateHandle[SEARCH_QUERY_KEY] = query
    }

    fun onSearchClicked() {
        val currentQuery = searchQuery.value
        // Trigger the search by updating the flow
        _submittedQuery.value = currentQuery
        // Save the submitted query for process death restoration
        savedStateHandle[SUBMITTED_QUERY_KEY] = currentQuery
    }

    companion object {
        private const val SEARCH_QUERY_KEY = "searchQuery"
        private const val SUBMITTED_QUERY_KEY = "submittedQuery"
    }
}

class SearchViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val journalEntryDao: JournalEntryDao,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(journalEntryDao, handle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}