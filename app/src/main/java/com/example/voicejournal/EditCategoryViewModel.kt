package com.example.voicejournal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditCategoryViewModel(
    private val repository: JournalRepository,
    private val categoryId: Int
) : ViewModel() {

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    init {
        if (categoryId != -1) {
            viewModelScope.launch {
                _category.value = repository.allCategories.first().find { it.id == categoryId }
            }
        }
    }

    fun saveCategory(name: String, aliases: String, showAll: Boolean, orderIndex: Int, color: String) {
        viewModelScope.launch {
            val categoryToSave = Category(
                id = categoryId.takeIf { it != -1 } ?: 0,
                category = name,
                aliases = aliases,
                showAll = showAll,
                orderIndex = orderIndex,
                color = color
            )
            if (categoryId != -1) {
                repository.updateCategory(categoryToSave)
            } else {
                repository.insertCategory(categoryToSave)
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