package com.example.voicejournal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditCategoryViewModel(
    private val repository: JournalRepository,
    private val categoryId: Int
) : ViewModel() {

    companion object {
        const val NEW_CATEGORY_ID = 0

        internal fun validateCategoryName(
            rawName: String,
            categoryId: Int,
            existingCategories: List<Category>
        ): String? {
            if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
                return "Die Papierkorb-Kategorie kann nicht bearbeitet werden."
            }
            val trimmedName = rawName.trim()
            if (trimmedName.isEmpty()) {
                return "Bitte einen gültigen Kategorienamen eingeben."
            }
            if (JournalConstants.isVirtualDeletedName(trimmedName)) {
                return "Der Name '${JournalConstants.CATEGORY_DELETED_NAME}' ist für den Papierkorb reserviert."
            }
            val conflicting = existingCategories.find { existing ->
                existing.id != categoryId &&
                    existing.category.equals(trimmedName, ignoreCase = true)
            }
            if (conflicting != null) {
                return "Eine Kategorie mit dem Namen '${conflicting.category}' existiert bereits."
            }
            return null
        }
    }

    private val isNewCategory: Boolean = categoryId == NEW_CATEGORY_ID

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _isLoaded = MutableStateFlow(isNewCategory)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _name = MutableStateFlow(TextFieldValue(""))
    val name: StateFlow<TextFieldValue> = _name.asStateFlow()

    private val _aliases = MutableStateFlow(TextFieldValue(""))
    val aliases: StateFlow<TextFieldValue> = _aliases.asStateFlow()

    private val _color = MutableStateFlow(TextFieldValue("#FFFFFF"))
    val color: StateFlow<TextFieldValue> = _color.asStateFlow()

    private val _checkable = MutableStateFlow(false)
    val checkable: StateFlow<Boolean> = _checkable.asStateFlow()

    private val _showDate = MutableStateFlow(true)
    val showDate: StateFlow<Boolean> = _showDate.asStateFlow()

    private val _deleteLineOnDoubleTap = MutableStateFlow(false)
    val deleteLineOnDoubleTap: StateFlow<Boolean> = _deleteLineOnDoubleTap.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        if (!isNewCategory) {
            viewModelScope.launch {
                val loaded = if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
                    null
                } else {
                    repository.realCategories.first().find { it.id == categoryId }
                }
                _category.value = loaded
                if (loaded != null) {
                    _name.value = TextFieldValue(loaded.category, selection = TextRange(loaded.category.length))
                    _aliases.value = TextFieldValue(loaded.aliases, selection = TextRange(loaded.aliases.length))
                    _color.value = TextFieldValue(loaded.color, selection = TextRange(loaded.color.length))
                    _checkable.value = loaded.checkable
                    _showDate.value = loaded.showDate
                    _deleteLineOnDoubleTap.value = loaded.deleteLineOnDoubleTap
                }
                _isLoaded.value = true
            }
        }
    }

    fun updateName(newName: TextFieldValue) {
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) {
            _name.value = newName
        }
    }

    fun updateAliases(newAliases: TextFieldValue) {
        _aliases.value = newAliases
    }

    fun updateColor(newColor: TextFieldValue) {
        _color.value = newColor
    }

    fun selectColorHex(hexColor: String) {
        _color.value = TextFieldValue(hexColor, selection = TextRange(hexColor.length))
    }

    fun updateCheckable(value: Boolean) {
        _checkable.value = value
    }

    fun updateShowDate(value: Boolean) {
        _showDate.value = value
    }

    fun updateDeleteLineOnDoubleTap(value: Boolean) {
        _deleteLineOnDoubleTap.value = value
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun saveCategory() {
        if (_isSaving.value || _saveCompleted.value) return

        val rawName = _name.value.text
        val trimmedName = rawName.trim()
        if (trimmedName.isEmpty()) {
            _saveError.value = "Bitte einen gültigen Kategorienamen eingeben."
            return
        }
        if (JournalConstants.isVirtualDeletedName(trimmedName)) {
            _saveError.value = "Der Name '${JournalConstants.CATEGORY_DELETED_NAME}' ist für den Papierkorb reserviert."
            return
        }
        if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
            _saveError.value = "Die Papierkorb-Kategorie kann nicht bearbeitet werden."
            return
        }

        val currentAliases = _aliases.value.text
        val currentColor = _color.value.text
        val currentCheckable = _checkable.value
        val currentShowDate = _showDate.value
        val currentDeleteLineOnDoubleTap = _deleteLineOnDoubleTap.value
        val existingOrderIndex = _category.value?.orderIndex ?: 0

        _isSaving.value = true
        _saveError.value = null

        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val realCategories = repository.getRealCategoriesList()
                    val validationError = validateCategoryName(
                        rawName = trimmedName,
                        categoryId = categoryId,
                        existingCategories = realCategories
                    )
                    if (validationError != null) {
                        throw IllegalArgumentException(validationError)
                    }

                    val resolvedOrderIndex = if (!isNewCategory) {
                        existingOrderIndex.coerceIn(0, Int.MAX_VALUE - 2)
                    } else {
                        JournalConstants.nextSafeOrderIndex(realCategories)
                    }

                    val categoryToSave = Category(
                        id = if (isNewCategory) 0 else categoryId,
                        category = trimmedName,
                        aliases = currentAliases,
                        orderIndex = resolvedOrderIndex,
                        color = currentColor,
                        checkable = currentCheckable,
                        showDate = currentShowDate,
                        deleteLineOnDoubleTap = currentDeleteLineOnDoubleTap
                    )
                    if (!isNewCategory) {
                        repository.updateCategory(categoryToSave)
                    } else {
                        repository.insertCategory(categoryToSave)
                    }
                }
                _saveCompleted.value = true
            } catch (e: Exception) {
                _isSaving.value = false
                _saveError.value = e.localizedMessage ?: "Failed to save category"
            }
        }
    }
}

class EditCategoryViewModelFactory(
    private val repository: JournalRepository,
    private val categoryId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditCategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditCategoryViewModel(repository, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
