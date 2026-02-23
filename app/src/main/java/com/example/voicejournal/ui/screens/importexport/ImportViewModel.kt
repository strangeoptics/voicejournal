package com.example.voicejournal.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
    }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val parsedEntries = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val content = reader.readText()
                            jsonFormat.decodeFromString<List<JournalEntry>>(content)
                        }
                    } ?: emptyList()
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
        viewModelScope.launch {
            val category = _selectedCategory.value ?: return@launch
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    _entries.value.forEach { entry ->
                        // Prüfen, ob der Eintrag anhand der exportierten UUID bereits existiert
                        val existing = repository.getEntryById(entry.id)
                        if (existing != null) {
                            // Wenn er existiert, aktualisieren wir den Content und weisen ihm ggf. die Ziel-Kategorie neu zu
                            repository.update(entry, listOf(category))
                        } else {
                            // Andernfalls speichern wir ihn als neuen Eintrag (aber behalten die Original-ID)
                            repository.insert(entry, listOf(category))
                        }
                    }
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