package com.example.voicejournal.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ImportViewModel(
    private val repository: JournalRepository,
    private val context: Context
) : ViewModel() {
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val categories: StateFlow<List<Category>> = repository.allCategories
        .map { list -> list.filter { it.id != JournalConstants.CATEGORY_DELETED_ID } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun selectCategory(category: Category) {
        if (category.id != JournalConstants.CATEGORY_DELETED_ID) {
            _selectedCategory.value = category
        }
    }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val parsedEntries = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Datei konnte nicht geöffnet werden.")
                    stream.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                            val content = reader.readText()
                            jsonFormat.decodeFromString<List<JournalEntry>>(content)
                        }
                    }
                }
                _entries.value = parsedEntries
            } catch (e: Exception) {
                _error.value = "Fehler beim Lesen der Datei. Ist es eine gültige .vj Datei?\nDetails: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeEntry(entry: JournalEntry) {
        _entries.value = _entries.value.filter { it.id != entry.id }
    }

    fun saveEntries(onComplete: () -> Unit) {
        if (_isLoading.value) return
        val category = _selectedCategory.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                withContext(NonCancellable) {
                    repository.importEntriesToCategory(_entries.value, category)
                }
                onComplete()
            } catch (e: Exception) {
                _error.value = "Fehler beim Speichern: ${e.localizedMessage}"
                _isLoading.value = false
            }
        }
    }
}

class ImportViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportViewModel::class.java)) {
            val repository = Injector.provideJournalRepository(context)
            @Suppress("UNCHECKED_CAST")
            return ImportViewModel(repository, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}