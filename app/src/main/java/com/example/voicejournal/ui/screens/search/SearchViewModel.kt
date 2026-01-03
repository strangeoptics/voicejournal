package com.example.voicejournal.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(private val journalEntryDao: JournalEntryDao) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _submittedQuery = MutableStateFlow("")

    val searchResults = _submittedQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                MutableStateFlow(emptyList<EntryWithCategories>())
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
        _searchQuery.value = query
    }

    fun onSearchClicked() {
        _submittedQuery.value = _searchQuery.value
    }
}

class SearchViewModelFactory(private val journalEntryDao: JournalEntryDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(journalEntryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}