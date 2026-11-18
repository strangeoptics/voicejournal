package com.example.voicejournal

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

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        if (categoryId != -1) {
            viewModelScope.launch {
                _category.value = repository.realCategories.first().find { it.id == categoryId }
            }
        }
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun saveCategory(
        name: String,
        aliases: String,
        showAll: Boolean,
        orderIndex: Int,
        color: String,
        checkable: Boolean,
        showDate: Boolean
    ) {
        if (_isSaving.value || _saveCompleted.value) return

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _saveError.value = "Bitte einen gültigen Kategorienamen eingeben."
            return
        }
        if (JournalConstants.isVirtualDeletedName(trimmedName)) {
            _saveError.value = "Der Name '${JournalConstants.CATEGORY_DELETED_NAME}' ist für den Papierkorb reserviert."
            return
        }

        _isSaving.value = true
        _saveError.value = null

        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val resolvedOrderIndex = if (categoryId != -1) {
                        orderIndex.coerceIn(0, Int.MAX_VALUE - 2)
                    } else {
                        JournalConstants.nextSafeOrderIndex(repository.getRealCategoriesList())
                    }

                    val categoryToSave = Category(
                        id = categoryId.takeIf { it != -1 } ?: 0,
                        category = trimmedName,
                        aliases = aliases,
                        showAll = showAll,
                        orderIndex = resolvedOrderIndex,
                        color = color,
                        checkable = checkable,
                        showDate = showDate
                    )
                    if (categoryId != -1) {
                        repository.updateCategory(categoryToSave)
                    } else {
                        val existing = repository.getRealCategoriesList().find {
                            it.category.equals(trimmedName, ignoreCase = true)
                        }
                        if (existing != null) {
                            throw IllegalArgumentException("Eine Kategorie mit dem Namen '${existing.category}' existiert bereits.")
                        }
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

    fun getHighestOrderIndex(): Int {
        // This is a placeholder. In a real app, you might want to fetch this from the repository.
        // For this refactoring, we'll just pass it from the previous screen.
        return 100
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
