package com.example.voicejournal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Collections

class CategoryManagerViewModel(
    private val repository: JournalRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteCategory(category: Category) {
        if (JournalConstants.isVirtualDeletedCategory(category)) return
        viewModelScope.launch {
            repository.deleteCategory(category.id)
        }
    }

    fun moveCategory(category: Category, moveUp: Boolean) {
        if (JournalConstants.isVirtualDeletedCategory(category)) return
        viewModelScope.launch {
            val realList = categories.value
                .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
                .toMutableList()
            val fromIndex = realList.indexOfFirst { it.id == category.id }
            if (fromIndex == -1) return@launch

            val toIndex = if (moveUp) fromIndex - 1 else fromIndex + 1
            if (toIndex in 0 until realList.size) {
                Collections.swap(realList, fromIndex, toIndex)
                val updatedCategories = realList.mapIndexed { index, cat ->
                    cat.copy(orderIndex = index)
                }
                repository.updateCategories(updatedCategories)
            }
        }
    }
}

class CategoryManagerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CategoryManagerViewModel(
                repository = Injector.provideJournalRepository(context.applicationContext)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
