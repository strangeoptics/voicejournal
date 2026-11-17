package com.example.voicejournal

import android.content.SharedPreferences
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    private val sharedPreferences: SharedPreferences,
    private val entryId: UUID?,
    private val categoryId: String?
) : ViewModel() {

    private val _entry = MutableStateFlow<EntryWithCategories?>(null)
    val entry: StateFlow<EntryWithCategories?> = _entry.asStateFlow()

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _speechService = MutableStateFlow("Android Speech Recognizer")
    val speechService: StateFlow<String> = _speechService.asStateFlow()

    private val _googleCloudApiKey = MutableStateFlow("")
    val googleCloudApiKey: StateFlow<String> = _googleCloudApiKey.asStateFlow()

    // Form States
    private val _entryContent = MutableStateFlow(TextFieldValue(""))
    val entryContent: StateFlow<TextFieldValue> = _entryContent.asStateFlow()

    private val _hasImage = MutableStateFlow(false)
    val hasImage: StateFlow<Boolean> = _hasImage.asStateFlow()

    private val _startDateTime = MutableStateFlow(Instant.now().toEpochMilli())
    val startDateTime: StateFlow<Long> = _startDateTime.asStateFlow()

    private val _stopDateTime = MutableStateFlow<Long?>(null)
    val stopDateTime: StateFlow<Long?> = _stopDateTime.asStateFlow()

    private val _selectedCategories = MutableStateFlow<List<String>>(emptyList())
    val selectedCategories: StateFlow<List<String>> = _selectedCategories.asStateFlow()

    init {
        _speechService.value = sharedPreferences.getString("speech_service", "Android Speech Recognizer") ?: "Android Speech Recognizer"
        _googleCloudApiKey.value = sharedPreferences.getString("google_cloud_api_key", "") ?: ""

        viewModelScope.launch {
            if (entryId != null) {
                val dbEntry = repository.getEntryById(entryId)
                _entry.value = dbEntry
                dbEntry?.let {
                    _entryContent.value = TextFieldValue(it.entry.content, selection = TextRange(it.entry.content.length))
                    _startDateTime.value = it.entry.start_datetime
                    _stopDateTime.value = it.entry.stop_datetime
                    _hasImage.value = it.entry.hasImage
                    _selectedCategories.value = it.categories.map { cat -> cat.category }
                }
            } else {
                val initialCategory = if (categoryId != null) {
                    listOf(Category(category = categoryId, aliases = ""))
                } else {
                    emptyList()
                }
                val newEntry = EntryWithCategories(
                    entry = JournalEntry(
                        id = UUID.randomUUID(),
                        content = "",
                        start_datetime = Instant.now().toEpochMilli(),
                        stop_datetime = null,
                        hasImage = false
                    ),
                    categories = initialCategory
                )
                _entry.value = newEntry
                _entryContent.value = TextFieldValue(newEntry.entry.content)
                _startDateTime.value = newEntry.entry.start_datetime
                _stopDateTime.value = newEntry.entry.stop_datetime
                _hasImage.value = newEntry.entry.hasImage
                _selectedCategories.value = newEntry.categories.map { cat -> cat.category }
            }
        }
    }

    fun updateContent(newContent: TextFieldValue) {
        _entryContent.value = newContent
    }

    fun updateHasImage(hasImage: Boolean) {
        _hasImage.value = hasImage
    }

    fun updateStartDateTime(timeInMillis: Long) {
        _startDateTime.value = timeInMillis
    }

    fun updateStopDateTime(timeInMillis: Long?) {
        _stopDateTime.value = timeInMillis
    }

    fun toggleCategory(category: String, isSelected: Boolean) {
        val currentSelection = _selectedCategories.value.toMutableList()
        if (isSelected) {
            currentSelection.add(category)
        } else {
            currentSelection.remove(category)
        }
        _selectedCategories.value = currentSelection
    }

    fun saveEntry() {
        val updatedCategories = _selectedCategories.value
        val updatedContent = _entryContent.value.text
        val updatedStartDatetime = _startDateTime.value
        val updatedStopDatetime = _stopDateTime.value
        val currentHasImage = _hasImage.value
        
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
                    hasImage = currentHasImage
                )
                repository.insert(newEntry, categories)
            } else {
                // Updating an existing entry
                _entry.value?.let { currentEntry ->
                    val updatedEntry = currentEntry.entry.copy(
                        content = updatedContent,
                        start_datetime = updatedStartDatetime,
                        stop_datetime = updatedStopDatetime,
                        hasImage = currentHasImage
                    )
                    repository.update(updatedEntry, categories)
                }
            }
        }
    }
}

class EditEntryViewModelFactory(
    private val repository: JournalRepository,
    private val sharedPreferences: SharedPreferences,
    private val entryId: UUID?,
    private val categoryId: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditEntryViewModel(repository, sharedPreferences, entryId, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}