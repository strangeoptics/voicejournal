package com.example.voicejournal.ui.screens.importexport

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.system.Os
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

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
    private var loadedUri: Uri? = null

    fun selectCategory(category: Category) {
        if (category.id != JournalConstants.CATEGORY_DELETED_ID) {
            _selectedCategory.value = category
        }
    }

    fun loadFile(uri: Uri, forceReload: Boolean = false) {
        if (!forceReload && loadedUri == uri && (_entries.value.isNotEmpty() || _isLoading.value)) {
            return
        }
        loadedUri = uri
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val parsedEntries = withContext(Dispatchers.IO) {
                    val content = validateAndReadBoundedUtf8(uri)
                    jsonFormat.decodeFromString<List<JournalEntry>>(content)
                }
                _entries.value = parsedEntries
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.e("SECURITY_ERROR", "Blocked unauthorized URI access in ImportViewModel: $uri", e)
                _entries.value = emptyList()
                _error.value = "Zugriff verweigert: Die gewählte Dateiquelle ist nicht zulässig."
            } catch (e: FileTooLargeException) {
                Log.w("ImportViewModel", "Rejected oversized import file", e)
                _entries.value = emptyList()
                _error.value = "Die ausgewählte Datei ist zu groß (maximal 10 MB erlaubt)."
            } catch (e: SerializationException) {
                // Do not expose raw parser snippets (which may contain file fragments) in UI
                Log.w("ImportViewModel", "Invalid .vj JSON format during import", e)
                _entries.value = emptyList()
                _error.value = "Ungültiges Dateiformat. Bitte wähle eine gültige .vj-Exportdatei aus."
            } catch (e: Exception) {
                Log.e("ImportViewModel", "Failed to read import file", e)
                _entries.value = emptyList()
                _error.value = "Die Datei konnte nicht gelesen werden."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun validateAndReadBoundedUtf8(uri: Uri): String {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw SecurityException("Only content:// URIs are permitted for import.")
        }

        val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: throw IOException("AssetFileDescriptor is null")

        return afd.use { descriptor ->
            verifyNotInternalAppFile(descriptor)

            val declaredLength = descriptor.declaredLength
            if (declaredLength != AssetFileDescriptor.UNKNOWN_LENGTH && declaredLength > MAX_IMPORT_BYTES) {
                throw FileTooLargeException("Declared length $declaredLength exceeds limit $MAX_IMPORT_BYTES")
            }
            val statSize = descriptor.parcelFileDescriptor.statSize
            if (statSize > MAX_IMPORT_BYTES) {
                throw FileTooLargeException("Stat size $statSize exceeds limit $MAX_IMPORT_BYTES")
            }

            descriptor.createInputStream().use { inputStream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalRead = 0L
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    totalRead += bytesRead
                    if (totalRead > MAX_IMPORT_BYTES) {
                        throw FileTooLargeException("Stream exceeds maximum size of $MAX_IMPORT_BYTES bytes")
                    }
                    output.write(buffer, 0, bytesRead)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }
    }

    private fun verifyNotInternalAppFile(descriptor: AssetFileDescriptor) {
        val fdPath = runCatching {
            Os.readlink("/proc/self/fd/${descriptor.parcelFileDescriptor.fd}")
        }.getOrNull() ?: return

        if (!fdPath.startsWith("/")) {
            // Non-filesystem descriptor (e.g. pipe/socket from a remote DocumentProvider)
            return
        }

        val canonicalTarget = File(fdPath).canonicalPath
        if (canonicalTarget.startsWith("/proc/") || canonicalTarget.startsWith("/sys/")) {
            throw SecurityException("Blocked system pseudo-filesystem path")
        }

        val appDataRoot = context.dataDir.canonicalPath
        val allowedExportDir = File(context.cacheDir, "shared_exports").canonicalPath

        val isInsideAppData = canonicalTarget == appDataRoot ||
            canonicalTarget.startsWith("$appDataRoot${File.separator}")
        val isInsideAllowedExportDir = canonicalTarget == allowedExportDir ||
            canonicalTarget.startsWith("$allowedExportDir${File.separator}")

        if (isInsideAppData && !isInsideAllowedExportDir) {
            throw SecurityException("Blocked confused-deputy access to private app file")
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
                Log.e("ImportViewModel", "Error saving imported entries", e)
                _error.value = "Fehler beim Speichern der importierten Einträge."
                _isLoading.value = false
            }
        }
    }

    private class FileTooLargeException(message: String) : IOException(message)

    companion object {
        private const val MAX_IMPORT_BYTES = 10L * 1024L * 1024L // 10 MB
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