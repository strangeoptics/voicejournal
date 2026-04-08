package com.example.voicejournal

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.UUID

class MainViewModel(
    private val repository: JournalRepository,
    private val sharedPreferences: SharedPreferences,
    private val applicationContext: Context,
    val db: AppDatabase
) : ViewModel() {

    companion object {
        const val PREFS_NAME = "voice_journal_prefs"
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
        const val KEY_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        const val KEY_SHOW_CATEGORY_TAGS = "show_category_tags"

        private val jsonFormat = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedEntry = MutableStateFlow<EntryWithCategories?>(null)
    val selectedEntry: StateFlow<EntryWithCategories?> = _selectedEntry.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _editingEntry = MutableStateFlow<EntryWithCategories?>(null)
    val editingEntry: StateFlow<EntryWithCategories?> = _editingEntry.asStateFlow()

    private val _selectedEntryIds = MutableStateFlow<Set<UUID>>(emptySet())
    val selectedEntryIds: StateFlow<Set<UUID>> = _selectedEntryIds.asStateFlow()

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

    private val _isDeveloperModeEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false))
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    private val _showCategoryTags = MutableStateFlow(sharedPreferences.getBoolean(KEY_SHOW_CATEGORY_TAGS, true))
    val showCategoryTags: StateFlow<Boolean> = _showCategoryTags.asStateFlow()

    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1)


    val canUndo: StateFlow<Boolean> = repository.getDeletedEntriesCount().map { it > 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    val categoriesFlow: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = categoriesFlow.map { categories ->
        categories.map { it.category }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_GPS_TRACKING_ENABLED -> _isGpsTrackingEnabled.value = prefs.getBoolean(key, false)
            KEY_WEBSERVER_ENABLED -> _isWebServerEnabled.value = prefs.getBoolean(key, false)
            KEY_GPS_INTERVAL_MINUTES -> _gpsInterval.value = prefs.getInt(key, 10)
            KEY_SPEECH_SERVICE -> _speechService.value = prefs.getString(key, "ANDROID") ?: "ANDROID"
            KEY_GOOGLE_CLOUD_API_KEY -> _googleCloudApiKey.value = prefs.getString(key, "") ?: ""
            KEY_MAX_RECORDING_TIME -> _maxRecordingTime.value = prefs.getInt(key, 15)
            KEY_SILENCE_THRESHOLD -> _silenceThreshold.value = prefs.getInt(key, 500)
            KEY_SILENCE_TIME_REQUIRED -> _silenceTimeRequired.value = prefs.getInt(key, 2000)
            KEY_TRUNCATION_LENGTH -> _truncationLength.value = prefs.getInt(key, 160)
            KEY_DEVELOPER_MODE_ENABLED -> _isDeveloperModeEnabled.value = prefs.getBoolean(key, false)
            KEY_SHOW_CATEGORY_TAGS -> _showCategoryTags.value = prefs.getBoolean(key, true)
        }
    }

    private val _scrollToEntryId = MutableStateFlow<UUID?>(null)
    val scrollToEntryId: StateFlow<UUID?> = _scrollToEntryId.asStateFlow()

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
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
        viewModelScope.launch {
            _refreshTrigger.emit(Unit) // Emit initial value to trigger loading
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
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
    val pagedEntries: Flow<PagingData<EntryWithCategories>> =
        combine(
            selectedCategory,
            categoriesFlow,
            _scrollToEntryId,
            _refreshTrigger // Just to trigger
        ) { selectedCat, categories, entryId, _ ->
            Triple(categories.find { it.category == selectedCat }, entryId, selectedCat)
        }.flatMapLatest { (category, entryId, selectedCat) ->
            repository.getEntriesPager(category?.id, entryId)
        }.cachedIn(viewModelScope)


    // The groupedEntries, filteredEntries and entries are now replaced by pagedEntries.
    // The UI will need to be updated to consume PagingData.
    // For now, I will provide a placeholder for groupedEntries.
    // The user's request is about loading, not grouping, so I can handle grouping later.
    val groupedEntries: StateFlow<Map<LocalDate, List<EntryWithCategories>>> = 
        MutableStateFlow(emptyMap<LocalDate, List<EntryWithCategories>>())
            .asStateFlow()

    val filteredEntries: StateFlow<List<EntryWithCategories>> =
        MutableStateFlow(emptyList<EntryWithCategories>())
            .asStateFlow()

    fun toggleEntrySelection(id: UUID) {
        _selectedEntryIds.value = _selectedEntryIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun clearSelection() {
        _selectedEntryIds.value = emptySet()
    }

    suspend fun getEntriesForSharing(ids: Set<UUID>): String {
        val entries = repository.getEntriesByIds(ids)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
        return entries.joinToString(separator = "\n\n") { entryWithCats ->
            val start = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(entryWithCats.entry.start_datetime),
                ZoneId.systemDefault()
            )
            val stop = entryWithCats.entry.stop_datetime?.let {
                java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            }
            val timeText = if (stop != null) {
                "${start.format(formatter)} - ${stop.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            } else {
                start.format(formatter)
            }
            "[$timeText]\n${entryWithCats.entry.content}"
        }
    }

    suspend fun getEntriesForSharingAsJson(ids: Set<UUID>): String {
        val entries = repository.getEntriesByIds(ids).map { it.entry }
        return jsonFormat.encodeToString(entries)
    }

    fun onCategoryChange(category: String) {
        _scrollToEntryId.value = null
        _selectedCategory.value = category
        _selectedEntry.value = null
        clearSelection()
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

    fun onSaveEntry(updatedCategories: List<String>, updatedContent: String, updatedStartDatetime: Long, updatedStopDatetime: Long?, hasImage: Boolean) {
        val sanitizedContent = updatedContent.replace("luisa", "Eloisa", ignoreCase = true)
        viewModelScope.launch {
            _editingEntry.value?.let {
                val updatedEntry = it.entry.copy(
                    content = sanitizedContent,
                    start_datetime = updatedStartDatetime,
                    stop_datetime = updatedStopDatetime,
                    hasImage = hasImage
                )
                val categories = updatedCategories.map { categoryName ->
                    categoriesFlow.value.find { c -> c.category == categoryName } ?: Category(category = categoryName, aliases = "")
                }
                repository.update(updatedEntry, categories)
                _editingEntry.value = null
                _refreshTrigger.emit(Unit)
            }
        }
    }

    fun deleteEntries(ids: Set<UUID>) {
        viewModelScope.launch {
            val entries = repository.getEntriesByIds(ids)
            entries.forEach { repository.delete(it.entry) }
            _selectedEntryIds.value = emptySet()
            _refreshTrigger.emit(Unit)
        }
    }

    fun onUndoDelete() {
        viewModelScope.launch {
            repository.restoreLatestDeletedEntry()
            _refreshTrigger.emit(Unit)
        }
    }

    fun hardDeleteEntries(ids: Set<UUID>) {
        viewModelScope.launch {
            if (_selectedCategory.value == "Gelöscht") {
                val entries = repository.getEntriesByIds(ids)
                entries.forEach { repository.hardDelete(it.entry) }
                _selectedEntryIds.value = emptySet()
                _refreshTrigger.emit(Unit)
            }
        }
    }

    fun restoreEntries(ids: Set<UUID>) {
        viewModelScope.launch {
            if (_selectedCategory.value == "Gelöscht") {
                ids.forEach { repository.restoreEntry(it) }
                _selectedEntryIds.value = emptySet()
                _refreshTrigger.emit(Unit)
            }
        }
    }

    fun scrollToEntry(entryId: UUID) {
        viewModelScope.launch {
            val entry = repository.getEntryById(entryId)
            if (entry != null) {
                val entryCategory = entry.categories.firstOrNull()?.category
                if (entryCategory != null && entryCategory != _selectedCategory.value) {
                    _selectedCategory.value = entryCategory
                }
                _scrollToEntryId.value = entryId
            }
        }
    }

    fun onScrolledToEntry() {
        _scrollToEntryId.value = null
    }
    
    fun saveGpsTrackingEnabled(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_GPS_TRACKING_ENABLED, isEnabled) }
        VoiceJournalApplication.setupLocationWorker(applicationContext)
    }

    fun saveWebServerEnabled(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_WEBSERVER_ENABLED, isEnabled) }

        val intent = Intent(applicationContext, WebServerService::class.java).apply {
            action = if (isEnabled) WebServerService.ACTION_START else WebServerService.ACTION_STOP
        }
        applicationContext.startService(intent)
    }

    fun saveGpsInterval(interval: Int) {
        sharedPreferences.edit { putInt(KEY_GPS_INTERVAL_MINUTES, interval) }
        VoiceJournalApplication.setupLocationWorker(applicationContext)
    }

    fun saveSpeechService(service: String) {
        sharedPreferences.edit { putString(KEY_SPEECH_SERVICE, service) }
    }

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit { putString(KEY_GOOGLE_CLOUD_API_KEY, apiKey) }
    }

    fun saveMaxRecordingTime(time: Int) {
        sharedPreferences.edit { putInt(KEY_MAX_RECORDING_TIME, time) }
    }

    fun saveSilenceThreshold(threshold: Int) {
        sharedPreferences.edit { putInt(KEY_SILENCE_THRESHOLD, threshold) }
    }

    fun saveSilenceTimeRequired(time: Int) {
        sharedPreferences.edit { putInt(KEY_SILENCE_TIME_REQUIRED, time) }
    }

    fun saveTruncationLength(length: Int) {
        sharedPreferences.edit { putInt(KEY_TRUNCATION_LENGTH, length) }
    }

    fun saveDeveloperModeEnabled(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DEVELOPER_MODE_ENABLED, isEnabled) }
    }

    fun toggleShowCategoryTags() {
        val newValue = !showCategoryTags.value
        sharedPreferences.edit { putBoolean(KEY_SHOW_CATEGORY_TAGS, newValue) }
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

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
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

            fun startDatetimeFromString(dateTimeString: String): Long {
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
                JournalEntry(content = "Habe beim Ausschalten versehentlich den dritten Wecker diesen Monat zerdrückt...", start_datetime = startDatetimeFromString("${today}T06:15:00"), stop_datetime = startDatetimeFromString("${today}T06:20:00")) to listOf(journal),
                JournalEntry(content = "Nach drei Tassen schwarzem Kaffee...", start_datetime = startDatetimeFromString("${today}T07:00:00")) to listOf(journal),
                JournalEntry(content = "Die Verfolgungsjagd auf der Autobahn war erfolgreich...", start_datetime = startDatetimeFromString("${today}T09:30:00"), hasImage = true) to listOf(journal),
                JournalEntry(content = "Während im Fernsehen Berichte über meine Heldentaten laufen...", start_datetime = startDatetimeFromString("${today}T21:00:00")) to listOf(journal),
                JournalEntry(content = "This is a test todo item from today.", start_datetime = startDatetimeFromString("${today}T12:00:00"), stop_datetime = startDatetimeFromString("${today}T12:30:00")) to listOf(todo),
                JournalEntry(content = "Milk, eggs, bread.", start_datetime = startDatetimeFromString("${today}T14:00:00")) to listOf(kaufen),
                JournalEntry(content = "A great new app idea from today.", start_datetime = startDatetimeFromString("${today}T16:00:00")) to listOf(baumarkt),
                JournalEntry(content = "Mittagspause mit zwei Dönern...", start_datetime = startDatetimeFromString("${yesterday}T12:30:00"), hasImage = true) to listOf(journal),
                JournalEntry(content = "Ein kleinerer Schurke wollte die U-Bahn sabotieren...", start_datetime = startDatetimeFromString("${yesterday}T10:00:00")) to listOf(journal),
                JournalEntry(content = "Todo item from yesterday.", start_datetime = startDatetimeFromString("${yesterday}T15:00:00")) to listOf(todo),
                JournalEntry(content = "Apples, bananas.", start_datetime = startDatetimeFromString("${yesterday}T17:00:00")) to listOf(kaufen),
                JournalEntry(content = "Journal entry from two days ago.", start_datetime = startDatetimeFromString("${twoDaysAgo}T18:00:00")) to listOf(journal),
                JournalEntry(content = "Another app idea from two days ago.", start_datetime = startDatetimeFromString("${twoDaysAgo}T20:00:00")) to listOf(eloisa)
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
                _refreshTrigger.emit(Unit)
            } else {
                val categories = categoriesFlow.value
                val currentCategoryName = _selectedCategory.value

                val (targetCategory, contentToAdd) = parseRecognizedTextForCategory(recognizedText, categories, currentCategoryName)

                val now = java.time.LocalDateTime.now()
                val start_datetime = _selectedDate.value?.atTime(now.toLocalTime())?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    ?: System.currentTimeMillis()
                _selectedDate.value = null

                if (contentToAdd.isNotEmpty()) {
                    val sanitizedContent = contentToAdd.replace("luisa", "Eloisa", ignoreCase = true)
                    val entry = JournalEntry(
                        content = sanitizedContent,
                        start_datetime = start_datetime
                    )
                    repository.insert(entry, listOf(targetCategory))
                    
                    if (_selectedCategory.value != targetCategory.category) {
                        _selectedCategory.value = targetCategory.category
                    }
                    _refreshTrigger.emit(Unit)
                }
            }
        }
    }

    private fun parseRecognizedTextForCategory(
        text: String,
        categories: List<Category>,
        currentCategoryName: String
    ): Pair<Category, String> {
        val trimmedText = text.trim()
        val defaultCat = categories.find { it.category == currentCategoryName }
            ?: Category(category = currentCategoryName, aliases = "")
            
        if (trimmedText.isEmpty()) {
            return Pair(defaultCat, "")
        }

        // a) Extrahiere das erste Wort (ignoriere Satzzeichen und beachte Case-Insensitivity)
        val wordRegex = Regex("^([^\\s\\p{Punct}]+)")
        val matchResult = wordRegex.find(trimmedText)

        if (matchResult != null) {
            val firstWord = matchResult.groupValues[1].lowercase(Locale.ROOT)

            // b) Prüfe ob dieses Wort category oder aliases entspricht
            val matchedCategory = categories.find { category ->
                val catName = category.category.lowercase(Locale.ROOT)
                val aliases = category.aliases.split(',')
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }
                
                firstWord == catName || aliases.contains(firstWord)
            }

            if (matchedCategory != null) {
                // c) Entferne das Trigger-Wort aus dem Text
                val removePrefixRegex = Regex("^[^\\s\\p{Punct}]+[\\s\\p{Punct}]*")
                var remainingText = trimmedText.replaceFirst(removePrefixRegex, "").trim()
                
                // Capitalize the first letter of the remaining text
                if (remainingText.isNotEmpty()) {
                    remainingText = remainingText.replaceFirstChar { 
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                    }
                }

                return Pair(matchedCategory, remainingText)
            }
        }

        return Pair(defaultCat, trimmedText)
    }

    fun toggleEntryChecked(entry: JournalEntry) {
        viewModelScope.launch {
            val newCheckedState = !entry.checked
            repository.updateEntryChecked(entry.id, newCheckedState)
            // No need to emit refreshTrigger if we use Flow from DB and update only one item?
            // But if the list comes from Pager, invalidating it might be needed.
            // However, with Room PagingSource, invalidation happens automatically on table change.
            // But verify: updateChecked is an @Query.
            // So PagingSource should invalidate.
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