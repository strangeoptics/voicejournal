package com.example.voicejournal

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.util.SecureApiKeyStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class EditEntryViewModel(
    private val repository: JournalRepository,
    private val sharedPreferences: SharedPreferences,
    private val applicationContext: Context,
    private val entryId: UUID?,
    private val categoryId: String?
) : ViewModel() {

    private val _entry = MutableStateFlow<EntryWithCategories?>(null)
    val entry: StateFlow<EntryWithCategories?> = _entry.asStateFlow()

    val allCategories: StateFlow<List<Category>> = repository.realCategories
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _speechService = MutableStateFlow("ANDROID")
    val speechService: StateFlow<String> = _speechService.asStateFlow()

    val googleCloudApiKey: StateFlow<String> = SecureApiKeyStore.apiKeyFlow

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

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        _speechService.value = sharedPreferences.getString(MainViewModel.KEY_SPEECH_SERVICE, "ANDROID") ?: "ANDROID"
        SecureApiKeyStore.getApiKey(applicationContext, sharedPreferences)

        viewModelScope.launch {
            if (entryId != null) {
                val dbEntry = repository.getEntryById(entryId)
                _entry.value = dbEntry
                dbEntry?.let {
                    _entryContent.value = TextFieldValue(it.entry.content, selection = TextRange(it.entry.content.length))
                    _startDateTime.value = it.entry.start_datetime
                    _stopDateTime.value = it.entry.stop_datetime
                    _hasImage.value = it.entry.hasImage
                    _selectedCategories.value = it.categories
                        .filterNot { cat -> JournalConstants.isVirtualDeletedCategory(cat) }
                        .map { cat -> cat.category }
                }
            } else {
                val realCategories = repository.getRealCategoriesList()
                val initialCategory = when {
                    !categoryId.isNullOrBlank() && !JournalConstants.isVirtualDeletedName(categoryId) -> {
                        val matched = realCategories.find { it.category == categoryId }
                        listOf(matched ?: Category(category = categoryId, aliases = ""))
                    }
                    else -> {
                        listOfNotNull(realCategories.firstOrNull())
                    }
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
        if (JournalConstants.isVirtualDeletedName(category)) return
        val currentSelection = _selectedCategories.value.toMutableList()
        if (isSelected) {
            if (!currentSelection.contains(category)) {
                currentSelection.add(category)
            }
        } else {
            currentSelection.remove(category)
        }
        _selectedCategories.value = currentSelection
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun saveEntry() {
        if (_isSaving.value || _saveCompleted.value) return
        val currentEntrySnapshot = _entry.value ?: return

        val updatedCategories = _selectedCategories.value
            .filterNot { JournalConstants.isVirtualDeletedName(it) }
        val updatedContent = _entryContent.value.text
        val updatedStartDatetime = _startDateTime.value
        val updatedStopDatetime = _stopDateTime.value
        val currentHasImage = _hasImage.value

        _isSaving.value = true
        _saveError.value = null

        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val realCategories = repository.getRealCategoriesList()
                    val categories = updatedCategories.map { categoryName ->
                        realCategories.find { c -> c.category == categoryName }
                            ?: Category(category = categoryName, aliases = "")
                    }
                    val entryToPersist = currentEntrySnapshot.entry.copy(
                        content = updatedContent,
                        start_datetime = updatedStartDatetime,
                        stop_datetime = updatedStopDatetime,
                        hasImage = currentHasImage
                    )
                    if (entryId == null) {
                        repository.insert(entryToPersist, categories)
                    } else {
                        repository.update(entryToPersist, categories)
                    }
                }
                _saveCompleted.value = true
            } catch (e: Exception) {
                _isSaving.value = false
                _saveError.value = e.localizedMessage ?: "Failed to save entry"
            }
        }
    }
}

class EditEntryViewModelFactory(
    private val repository: JournalRepository,
    private val sharedPreferences: SharedPreferences,
    private val applicationContext: Context,
    private val entryId: UUID?,
    private val categoryId: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditEntryViewModel(repository, sharedPreferences, applicationContext, entryId, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}