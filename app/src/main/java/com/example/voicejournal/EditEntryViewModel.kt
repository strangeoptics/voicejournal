package com.example.voicejournal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditEntryViewModel(
    private val repository: JournalRepository,
    private val entryId: Int
) : ViewModel() {

    private val _entry = MutableStateFlow<EntryWithCategories?>(null)
    val entry: StateFlow<EntryWithCategories?> = _entry.asStateFlow()

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _entry.value = repository.getAllEntriesWithCategories().first().find { it.entry.id == entryId }
        }
    }

    fun saveEntry(updatedCategories: List<String>, updatedContent: String, updatedTimestamp: Long, hasImage: Boolean) {
        viewModelScope.launch {
            _entry.value?.let { currentEntry ->
                val updatedEntry = currentEntry.entry.copy(
                    content = updatedContent,
                    timestamp = updatedTimestamp,
                    hasImage = hasImage
                )
                val categories = updatedCategories.map { categoryName ->
                    allCategories.value.find { c -> c.category == categoryName } ?: Category(category = categoryName, aliases = "")
                }
                repository.update(updatedEntry, categories)
            }
        }
    }
}

class EditEntryViewModelFactory(
    private val repository: JournalRepository,
    private val entryId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditEntryViewModel(repository, entryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}