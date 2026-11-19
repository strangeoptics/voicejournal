package com.example.voicejournal

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
import com.example.voicejournal.data.SettingsRepository
import com.example.voicejournal.data.SpeechService
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class EditEntryViewModel(
    private val repository: JournalRepository,
    settingsRepository: SettingsRepository,
    private val entryId: UUID?,
    private val initialCategoryId: Int?
) : ViewModel() {

    companion object {
        internal fun mergeDictatedText(
            current: TextFieldValue,
            recognizedText: String
        ): TextFieldValue {
            if (recognizedText.isEmpty()) return current

            val originalText = current.text
            val selMin = current.selection.min.coerceIn(0, originalText.length)
            val selMax = current.selection.max.coerceIn(selMin, originalText.length)

            val newText: String
            val newCursorPos: Int

            if (selMax > selMin) {
                // 1. Selection exists (including reverse selection): replace selected range
                newText = originalText.replaceRange(selMin, selMax, recognizedText)
                newCursorPos = selMin + recognizedText.length
            } else if (selMin >= originalText.length) {
                // 2. Cursor at the very end: append on a new line
                newText = if (originalText.isNotEmpty()) {
                    originalText + "\n" + recognizedText
                } else {
                    recognizedText
                }
                newCursorPos = newText.length
            } else {
                // 3. Cursor is in the middle or at the start: insert at cursor
                newText = originalText.replaceRange(selMin, selMax, recognizedText)
                newCursorPos = selMin + recognizedText.length
            }

            return TextFieldValue(text = newText, selection = TextRange(newCursorPos))
        }
    }

    private val _entry = MutableStateFlow<EntryWithCategories?>(null)
    val entry: StateFlow<EntryWithCategories?> = _entry.asStateFlow()

    val allCategories: StateFlow<List<Category>> = repository.realCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speechService: StateFlow<SpeechService> = settingsRepository.speechService
    val googleCloudApiKey: StateFlow<String> = settingsRepository.googleCloudApiKey
    val maxRecordingTime: StateFlow<Int> = settingsRepository.maxRecordingTime
    val silenceThreshold: StateFlow<Int> = settingsRepository.silenceThreshold
    val silenceTimeRequired: StateFlow<Int> = settingsRepository.silenceTimeRequired

    // Form States
    private val _entryContent = MutableStateFlow(TextFieldValue(""))
    val entryContent: StateFlow<TextFieldValue> = _entryContent.asStateFlow()

    private val _hasImage = MutableStateFlow(false)
    val hasImage: StateFlow<Boolean> = _hasImage.asStateFlow()

    private val _startDateTime = MutableStateFlow(Instant.now().toEpochMilli())
    val startDateTime: StateFlow<Long> = _startDateTime.asStateFlow()

    private val _stopDateTime = MutableStateFlow<Long?>(null)
    val stopDateTime: StateFlow<Long?> = _stopDateTime.asStateFlow()

    private val _zoneId = MutableStateFlow<String?>(
        if (entryId == null) ZoneId.systemDefault().id else null
    )
    val zoneId: StateFlow<String?> = _zoneId.asStateFlow()

    private val _selectedCategoryIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedCategoryIds: StateFlow<Set<Int>> = _selectedCategoryIds.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    private val _createdEntryId = MutableStateFlow<UUID?>(null)
    val createdEntryId: StateFlow<UUID?> = _createdEntryId.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        viewModelScope.launch {
            if (entryId != null) {
                val dbEntry = repository.getEntryById(entryId)
                _entry.value = dbEntry
                dbEntry?.let {
                    _entryContent.value = TextFieldValue(
                        it.entry.content,
                        selection = TextRange(it.entry.content.length)
                    )
                    _startDateTime.value = it.entry.start_datetime
                    _stopDateTime.value = it.entry.stop_datetime
                    _zoneId.value = it.entry.zoneId
                    _hasImage.value = it.entry.hasImage
                    _selectedCategoryIds.value = it.categories
                        .filterNot { cat -> JournalConstants.isVirtualDeletedCategory(cat) }
                        .map { cat -> cat.id }
                        .filter { id -> id > 0 }
                        .toSet()
                }
            } else {
                val realCategories = repository.getRealCategoriesList()
                val initialCategories = when {
                    initialCategoryId != null && initialCategoryId > 0 -> {
                        val matched = realCategories.find { it.id == initialCategoryId }
                        listOfNotNull(matched ?: realCategories.firstOrNull())
                    }
                    else -> {
                        listOfNotNull(realCategories.firstOrNull())
                    }
                }
                val currentZoneId = ZoneId.systemDefault().id
                val newEntry = EntryWithCategories(
                    entry = JournalEntry(
                        id = UUID.randomUUID(),
                        content = "",
                        start_datetime = Instant.now().toEpochMilli(),
                        stop_datetime = null,
                        hasImage = false,
                        zoneId = currentZoneId
                    ),
                    categories = initialCategories
                )
                _entry.value = newEntry
                _entryContent.value = TextFieldValue(newEntry.entry.content)
                _startDateTime.value = newEntry.entry.start_datetime
                _stopDateTime.value = newEntry.entry.stop_datetime
                _zoneId.value = newEntry.entry.zoneId
                _hasImage.value = newEntry.entry.hasImage
                _selectedCategoryIds.value = newEntry.categories
                    .map { cat -> cat.id }
                    .filter { id -> id > 0 }
                    .toSet()
            }
        }
    }

    fun updateContent(newContent: TextFieldValue) {
        _entryContent.value = newContent
    }

    fun insertDictatedText(recognizedText: String) {
        _entryContent.value = mergeDictatedText(_entryContent.value, recognizedText)
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

    fun updateZoneId(newZoneId: String?) {
        _zoneId.value = TimeZoneUtils.sanitizeZoneId(newZoneId)
    }

    fun toggleCategory(categoryId: Int, isSelected: Boolean) {
        if (categoryId <= 0) return
        _selectedCategoryIds.value = _selectedCategoryIds.value.toMutableSet().apply {
            if (isSelected) add(categoryId) else remove(categoryId)
        }
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun saveEntry() {
        if (_isSaving.value || _saveCompleted.value) return
        val currentEntrySnapshot = _entry.value ?: return

        val selectedIds = _selectedCategoryIds.value.filter { it > 0 }
        val updatedContent = _entryContent.value.text
        val updatedStartDatetime = _startDateTime.value
        val updatedStopDatetime = _stopDateTime.value
        val updatedZoneId = TimeZoneUtils.sanitizeZoneId(_zoneId.value)
        val currentHasImage = _hasImage.value

        _isSaving.value = true
        _saveError.value = null

        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val categories = repository.getCategoriesByIds(selectedIds)
                    val entryToPersist = currentEntrySnapshot.entry.copy(
                        content = updatedContent,
                        start_datetime = updatedStartDatetime,
                        stop_datetime = updatedStopDatetime,
                        hasImage = currentHasImage,
                        zoneId = updatedZoneId
                    )
                    if (entryId == null) {
                        repository.insert(entryToPersist, categories)
                        _createdEntryId.value = entryToPersist.id
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
    private val settingsRepository: SettingsRepository,
    private val entryId: UUID?,
    private val initialCategoryId: Int?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditEntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditEntryViewModel(repository, settingsRepository, entryId, initialCategoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
