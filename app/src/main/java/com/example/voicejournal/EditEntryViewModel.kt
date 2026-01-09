package com.example.voicejournal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class EditEntryViewModel(
    private val repository: JournalRepository,
    private val entryId: UUID?,
    private val categoryId: String?
) : ViewModel() {

    private val _entry = MutableStateFlow<EntryWithCategories?>(null)
    val entry: StateFlow<EntryWithCategories?> = _entry.asStateFlow()

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (entryId != null) {
                _entry.value = repository.getEntryById(entryId)
            } else {
                val initialCategory = if (categoryId != null) {
                    listOf(Category(category = categoryId, aliases = ""))
                } else {
                    emptyList()
                }
                _entry.value = EntryWithCategories(
                    entry = JournalEntry(
                        id = UUID.randomUUID(),
                        content = "",
                        start_datetime = Instant.now().toEpochMilli(),
                        stop_datetime = null,
                        hasImage = false
                    ),
                    categories = initialCategory
                )
            }
        }
    }

    fun saveEntry(updatedCategories: List<String>, updatedContent: String, updatedStartDatetime: Long, updatedStopDatetime: Long?, hasImage: Boolean) {
        viewModelScope.launch {
            val categories = updatedCategories.map { categoryName ->
                allCategories.value.find { c -> c.category == categoryName } ?: Category(category = categoryName, aliases = "")
            }
            if (entryId == null) {
                // Creating a new entry
                val newEntry = _entry.value!!.entry.copy(
                    content = updatedContent,
                    start_datetime = updatedStartDatetime,
                    stop_datetime = updatedStopDatetime,
                    hasImage = hasImage
                )
                repository.insert(newEntry, categories)
            } else {
                // Updating an existing entry
                _entry.value?.let { currentEntry ->
                    val updatedEntry = currentEntry.entry.copy(
                        content = updatedContent,
                        start_datetime = updatedStartDatetime,
                        stop_datetime = updatedStopDatetime,
                        hasImage = hasImage
                    )
                    repository.update(updatedEntry, categories)
                }
            }
        }
    }
}

class EditEntryViewModelFactory(
    private val repository: JournalRepository,
    private val entryId: UUID?,
    private val categoryId: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditEntryViewModel(repository, entryId, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}