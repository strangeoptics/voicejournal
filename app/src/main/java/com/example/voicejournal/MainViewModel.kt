package com.example.voicejournal

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Collections
import java.util.Locale

class MainViewModel(
    private val repository: JournalRepository,
    private val sharedPreferences: SharedPreferences,
    private val applicationContext: Context,
    val db: AppDatabase
) : ViewModel() {

    companion object {
        const val PREFS_NAME = "voice_journal_prefs"
        const val KEY_DAYS_TO_SHOW = "days_to_show"
        const val KEY_DEFAULT_CATEGORIES_ADDED = "default_categories_added"
        const val KEY_GPS_TRACKING_ENABLED = "gps_tracking_enabled"
        const val KEY_GPS_INTERVAL_MINUTES = "gps_interval_minutes"
        const val KEY_SPEECH_SERVICE = "speech_service"
        const val KEY_GOOGLE_CLOUD_API_KEY = "google_cloud_api_key"
        const val KEY_MAX_RECORDING_TIME = "max_recording_time"
        const val KEY_SILENCE_THRESHOLD = "silence_threshold"
        const val KEY_SILENCE_TIME_REQUIRED = "silence_time_required"
        const val KEY_TRUNCATION_LENGTH = "truncation_length"
        const val KEY_WEBSERVER_ENABLED = "webserver_enabled"
    }

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedEntry = MutableStateFlow<EntryWithCategories?>(null)
    val selectedEntry: StateFlow<EntryWithCategories?> = _selectedEntry.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _editingEntry = MutableStateFlow<EntryWithCategories?>(null)
    val editingEntry: StateFlow<EntryWithCategories?> = _editingEntry.asStateFlow()

    private val _daysToShow = MutableStateFlow(sharedPreferences.getInt(KEY_DAYS_TO_SHOW, 3))
    val daysToShow: StateFlow<Int> = _daysToShow.asStateFlow()

    private val _isGpsTrackingEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_GPS_TRACKING_ENABLED, false))
    val isGpsTrackingEnabled: StateFlow<Boolean> = _isGpsTrackingEnabled.asStateFlow()

    private val _isWebServerEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_WEBSERVER_ENABLED, false))
    val isWebServerEnabled: StateFlow<Boolean> = _isWebServerEnabled.asStateFlow()

    private val _gpsInterval = MutableStateFlow(sharedPreferences.getInt(KEY_GPS_INTERVAL_MINUTES, 10))
    val gpsInterval: StateFlow<Int> = _gpsInterval.asStateFlow()

    private val _speechService = MutableStateFlow(sharedPreferences.getString(KEY_SPEECH_SERVICE, "ANDROID") ?: "ANDROID")
    val speechService: StateFlow<String> = _speechService.asStateFlow()

    private val _googleCloudApiKey = MutableStateFlow(sharedPreferences.getString(KEY_GOOGLE_CLOUD_API_KEY, "") ?: "")
    val googleCloudApiKey: StateFlow<String> = _googleCloudApiKey.asStateFlow()

    private val _maxRecordingTime = MutableStateFlow(sharedPreferences.getInt(KEY_MAX_RECORDING_TIME, 15))
    val maxRecordingTime: StateFlow<Int> = _maxRecordingTime.asStateFlow()

    private val _silenceThreshold = MutableStateFlow(sharedPreferences.getInt(KEY_SILENCE_THRESHOLD, 500))
    val silenceThreshold: StateFlow<Int> = _silenceThreshold.asStateFlow()

    private val _silenceTimeRequired = MutableStateFlow(sharedPreferences.getInt(KEY_SILENCE_TIME_REQUIRED, 2000))
    val silenceTimeRequired: StateFlow<Int> = _silenceTimeRequired.asStateFlow()

    private val _truncationLength = MutableStateFlow(sharedPreferences.getInt(KEY_TRUNCATION_LENGTH, 160))
    val truncationLength: StateFlow<Int> = _truncationLength.asStateFlow()


    private val _recentlyDeleted = MutableStateFlow<List<EntryWithCategories>>(emptyList())
    val canUndo: StateFlow<Boolean> = _recentlyDeleted.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    val categoriesFlow: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = categoriesFlow.map { categories ->
        categories.map { it.category }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryKeywordsMap: StateFlow<Map<String, String>> = categoriesFlow.map { categories ->
        categories.flatMap { category ->
            category.aliases.split(',').map { alias ->
                alias.trim().lowercase(Locale.ROOT) to category.category.lowercase(Locale.ROOT)
            }
        }.associate { it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val shouldShowMoreButton: StateFlow<Boolean> =
        combine(selectedCategory, categoriesFlow) { selectedCat, categories ->
            val category = categories.find { it.category == selectedCat }
            category?.showAll != true
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val gpsTrackPoints: StateFlow<List<GpsTrackPoint>> = selectedDate
        .flatMapLatest { date ->
            val startOfDay = (date ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = (date ?: LocalDate.now()).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.getTrackPointsForDay(startOfDay, endOfDay)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasGpsTrackForSelectedDate: StateFlow<Boolean> = combine(selectedDate, gpsTrackPoints) { date, points ->
        date != null && points.size >= 2
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    init {
        viewModelScope.launch {
            val areDefaultCategoriesAdded = sharedPreferences.getBoolean(KEY_DEFAULT_CATEGORIES_ADDED, false)
            if (!areDefaultCategoriesAdded) {
                addDefaultCategories()
                sharedPreferences.edit { putBoolean(KEY_DEFAULT_CATEGORIES_ADDED, true) }
            }
            categories.collect {
                if (it.isNotEmpty() && _selectedCategory.value.isEmpty()) {
                    _selectedCategory.value = it.first()
                }
            }
        }
    }

    private suspend fun addDefaultCategories() {
        val defaultCategories = listOf(
            Category(category = "journal", aliases = "journal,tagebuch", orderIndex = 0),
            Category(category = "todo", aliases = "todo,to-do,todoo", orderIndex = 1),
            Category(category = "kaufen", aliases = "kaufen,einkaufen", orderIndex = 2),
            Category(category = "baumarkt", aliases = "baumarkt", orderIndex = 3),
            Category(category = "eloisa", aliases = "eloisa,luisa", showAll = true, orderIndex = 4)
        )
        defaultCategories.forEach { repository.insertCategory(it) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<EntryWithCategories>> =
        combine(daysToShow, selectedCategory, categoriesFlow) { days, selectedCat, categories ->
            Triple(days, selectedCat, categories)
        }.flatMapLatest { (days, selectedCat, categories) ->
            val category = categories.find { it.category == selectedCat }
            if (category?.showAll == true) {
                repository.getAllEntriesWithCategories()
            } else {
                val daysAgoMillis = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -days)
                }.timeInMillis
                repository.getEntriesWithCategoriesSince(daysAgoMillis)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val filteredEntries: StateFlow<List<EntryWithCategories>> =
        combine(entries, selectedCategory) { entries, category ->
            if (category.isEmpty()) {
                entries
            } else {
                entries.filter { entryWithCategories ->
                    entryWithCategories.categories.any { it.category == category }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedEntries: StateFlow<Map<LocalDate, List<EntryWithCategories>>> =
        filteredEntries.map { entries ->
            entries.groupBy {
                Instant.ofEpochMilli(it.entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    fun onCategoryChange(category: String) {
        _selectedCategory.value = category
    }
    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }
    fun onEntrySelected(entry: EntryWithCategories) {
        _selectedEntry.value = if (_selectedEntry.value == entry) null else entry
        if (_selectedEntry.value != null) {
            _selectedDate.value = null
        }
    }

    fun onEditEntry(entry: EntryWithCategories) {
        _editingEntry.value = entry
    }

    fun onDismissEditEntry() {
        _editingEntry.value = null
    }

    fun onSaveEntry(updatedCategories: List<String>, updatedContent: String, updatedTimestamp: Long, hasImage: Boolean) {
        val sanitizedContent = updatedContent.replace("luisa", "Eloisa", ignoreCase = true)
        viewModelScope.launch {
            _editingEntry.value?.let {
                val updatedEntry = it.entry.copy(content = sanitizedContent, timestamp = updatedTimestamp, hasImage = hasImage)
                val categories = updatedCategories.map { categoryName ->
                    categoriesFlow.value.find { c -> c.category == categoryName } ?: Category(category = categoryName, aliases = "")
                }
                repository.update(updatedEntry, categories)
                _editingEntry.value = null
            }
        }
    }

    fun onDeleteEntry(entry: EntryWithCategories) {
        viewModelScope.launch {
            val currentDeleted = _recentlyDeleted.value.toMutableList()
            currentDeleted.add(entry)
            if (currentDeleted.size > 3) {
                currentDeleted.removeAt(0)
            }
            _recentlyDeleted.value = currentDeleted
            repository.delete(entry.entry)
        }
    }

    fun onUndoDelete() {
        viewModelScope.launch {
            val lastDeleted = _recentlyDeleted.value.lastOrNull()
            if (lastDeleted != null) {
                repository.insert(lastDeleted.entry, lastDeleted.categories)
                _recentlyDeleted.value = _recentlyDeleted.value.dropLast(1)
            }
        }
    }

    fun onMoreClicked() {
        _daysToShow.value += 3
        sharedPreferences.edit { putInt(KEY_DAYS_TO_SHOW, _daysToShow.value) }
    }
    
    fun saveDaysToShow(days: Int) {
        _daysToShow.value = days
        sharedPreferences.edit { putInt(KEY_DAYS_TO_SHOW, days) }
    }

    fun saveGpsTrackingEnabled(isEnabled: Boolean) {
        _isGpsTrackingEnabled.value = isEnabled
        sharedPreferences.edit { putBoolean(KEY_GPS_TRACKING_ENABLED, isEnabled) }
        VoiceJournalApplication.setupLocationWorker(applicationContext)
    }

    fun saveWebServerEnabled(isEnabled: Boolean) {
        _isWebServerEnabled.value = isEnabled
        sharedPreferences.edit { putBoolean(KEY_WEBSERVER_ENABLED, isEnabled) }

        val intent = Intent(applicationContext, WebServerService::class.java).apply {
            action = if (isEnabled) WebServerService.ACTION_START else WebServerService.ACTION_STOP
        }
        applicationContext.startService(intent)
    }

    fun saveGpsInterval(interval: Int) {
        _gpsInterval.value = interval
        sharedPreferences.edit { putInt(KEY_GPS_INTERVAL_MINUTES, interval) }
        VoiceJournalApplication.setupLocationWorker(applicationContext)
    }

    fun saveSpeechService(service: String) {
        _speechService.value = service
        sharedPreferences.edit { putString(KEY_SPEECH_SERVICE, service) }
    }

    fun saveApiKey(apiKey: String) {
        _googleCloudApiKey.value = apiKey
        sharedPreferences.edit { putString(KEY_GOOGLE_CLOUD_API_KEY, apiKey) }
    }

    fun saveMaxRecordingTime(time: Int) {
        _maxRecordingTime.value = time
        sharedPreferences.edit { putInt(KEY_MAX_RECORDING_TIME, time) }
    }

    fun saveSilenceThreshold(threshold: Int) {
        _silenceThreshold.value = threshold
        sharedPreferences.edit { putInt(KEY_SILENCE_THRESHOLD, threshold) }
    }

    fun saveSilenceTimeRequired(time: Int) {
        _silenceTimeRequired.value = time
        sharedPreferences.edit { putInt(KEY_SILENCE_TIME_REQUIRED, time) }
    }

    fun saveTruncationLength(length: Int) {
        _truncationLength.value = length
        sharedPreferences.edit { putInt(KEY_TRUNCATION_LENGTH, length) }
    }

    fun addOrUpdateCategory(categoryName: String, aliasesString: String, showAll: Boolean) {
        viewModelScope.launch {
            val existingCategory = categoriesFlow.value.find { it.category == categoryName }
            val category = Category(
                id = existingCategory?.id ?: 0,
                category = categoryName,
                aliases = aliasesString,
                showAll = showAll,
                orderIndex = existingCategory?.orderIndex ?: categoriesFlow.value.size
            )
            repository.insertCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category.id)
        }
    }

    fun moveCategory(category: Category, moveUp: Boolean) {
        viewModelScope.launch {
            val currentList = categoriesFlow.value.toMutableList()
            val fromIndex = currentList.indexOf(category)
            if (fromIndex == -1) return@launch

            val toIndex = if (moveUp) fromIndex - 1 else fromIndex + 1

            if (toIndex >= 0 && toIndex < currentList.size) {
                Collections.swap(currentList, fromIndex, toIndex)
                val updatedCategories = currentList.mapIndexed { index, cat ->
                    cat.copy(orderIndex = index)
                }
                repository.updateCategories(updatedCategories)
            }
        }
    }

    suspend fun exportJournal(uri: Uri) {
        repository.exportJournal(uri)
    }

    suspend fun importJournal(uri: Uri) {
        repository.importJournal(uri)
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun addTestData() {
        viewModelScope.launch {
            repository.deleteAll()

            fun timestampFromString(dateTimeString: String): Long {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val localDateTime = java.time.LocalDateTime.parse(dateTimeString, formatter)
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            val today = LocalDate.now().toString()
            val yesterday = LocalDate.now().minusDays(1).toString()
            val twoDaysAgo = LocalDate.now().minusDays(2).toString()

            val journal = Category(category = "journal", aliases = "")
            val todo = Category(category = "todo", aliases = "")
            val kaufen = Category(category = "kaufen", aliases = "")
            val baumarkt = Category(category = "baumarkt", aliases = "")
            val eloisa = Category(category = "eloisa", aliases = "")

            val testEntries: List<Pair<JournalEntry, List<Category>>> = listOf(
                JournalEntry(content = "Habe beim Ausschalten versehentlich den dritten Wecker diesen Monat zerdrückt...", timestamp = timestampFromString("${today}T06:15:00")) to listOf(journal),
                JournalEntry(content = "Nach drei Tassen schwarzem Kaffee...", timestamp = timestampFromString("${today}T07:00:00")) to listOf(journal),
                JournalEntry(content = "Die Verfolgungsjagd auf der Autobahn war erfolgreich...", timestamp = timestampFromString("${today}T09:30:00"), hasImage = true) to listOf(journal),
                JournalEntry(content = "Während im Fernsehen Berichte über meine Heldentaten laufen...", timestamp = timestampFromString("${today}T21:00:00")) to listOf(journal),
                JournalEntry(content = "This is a test todo item from today.", timestamp = timestampFromString("${today}T12:00:00")) to listOf(todo),
                JournalEntry(content = "Milk, eggs, bread.", timestamp = timestampFromString("${today}T14:00:00")) to listOf(kaufen),
                JournalEntry(content = "A great new app idea from today.", timestamp = timestampFromString("${today}T16:00:00")) to listOf(baumarkt),
                JournalEntry(content = "Mittagspause mit zwei Dönern...", timestamp = timestampFromString("${yesterday}T12:30:00"), hasImage = true) to listOf(journal),
                JournalEntry(content = "Ein kleinerer Schurke wollte die U-Bahn sabotieren...", timestamp = timestampFromString("${yesterday}T10:00:00")) to listOf(journal),
                JournalEntry(content = "Todo item from yesterday.", timestamp = timestampFromString("${yesterday}T15:00:00")) to listOf(todo),
                JournalEntry(content = "Apples, bananas.", timestamp = timestampFromString("${yesterday}T17:00:00")) to listOf(kaufen),
                JournalEntry(content = "Journal entry from two days ago.", timestamp = timestampFromString("${twoDaysAgo}T18:00:00")) to listOf(journal),
                JournalEntry(content = "Another app idea from two days ago.", timestamp = timestampFromString("${twoDaysAgo}T20:00:00")) to listOf(eloisa)
            )
            testEntries.forEach { (entry, categories) -> repository.insert(entry, categories) }
        }
    }
    fun processRecognizedText(recognizedText: String) {
        viewModelScope.launch {
            val entryToUpdate = _selectedEntry.value
            if (entryToUpdate != null) {
                val sanitizedText = recognizedText.replace("luisa", "Eloisa", ignoreCase = true)
                val updatedEntry = entryToUpdate.entry.copy(
                    content = entryToUpdate.entry.content + "\n" + sanitizedText
                )
                repository.update(updatedEntry, entryToUpdate.categories)
                _selectedEntry.value = null // Deselect after update
            } else {
                val currentCategoryKeywords = categoryKeywordsMap.value
                val foundKeyword = currentCategoryKeywords.keys.find { keyword ->
                    recognizedText.startsWith(keyword, ignoreCase = true) &&
                            (recognizedText.length == keyword.length || recognizedText.getOrNull(keyword.length)?.isWhitespace() == true)
                }
                val now = java.time.LocalDateTime.now()
                val timestamp = _selectedDate.value?.atTime(now.toLocalTime())?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    ?: System.currentTimeMillis()
                _selectedDate.value = null

                val (targetCategory, contentToAdd) = if (foundKeyword != null) {
                    val categoryName = currentCategoryKeywords[foundKeyword]!!
                    val content = recognizedText.substring(foundKeyword.length).trim()
                    _selectedCategory.value = categoryName
                    val category = categoriesFlow.value.find { it.category == categoryName } ?: Category(category = categoryName, aliases = "")
                    category to content
                } else {
                    val category = categoriesFlow.value.find { it.category == _selectedCategory.value } ?: Category(category = _selectedCategory.value, aliases = "")
                    category to recognizedText
                }

                if (contentToAdd.isNotEmpty()) {
                    val sanitizedContent = contentToAdd.replace("luisa", "Eloisa", ignoreCase = true)
                    val entry = JournalEntry(
                        content = sanitizedContent,
                        timestamp = timestamp
                    )
                    repository.insert(entry, listOf(targetCategory))
                }
            }
        }
    }
}

class MainViewModelFactory(private val context: Context, private val sharedPreferences: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val repository = Injector.provideJournalRepository(context)
            val db = Injector.getDatabase(context)
            return MainViewModel(repository, sharedPreferences, context.applicationContext, db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
