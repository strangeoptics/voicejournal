package com.example.voicejournal

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.Category
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
    private val applicationContext: Context
) : ViewModel() {

    companion object {
        const val PREFS_NAME = "voice_journal_prefs"
        const val KEY_DAYS_TO_SHOW = "days_to_show"
        const val KEY_DEFAULT_CATEGORIES_ADDED = "default_categories_added"
        const val KEY_GPS_TRACKING_ENABLED = "gps_tracking_enabled"
        const val KEY_GPS_INTERVAL_MINUTES = "gps_interval_minutes"
    }

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedEntry = MutableStateFlow<JournalEntry?>(null)
    val selectedEntry: StateFlow<JournalEntry?> = _selectedEntry.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _editingEntry = MutableStateFlow<JournalEntry?>(null)
    val editingEntry: StateFlow<JournalEntry?> = _editingEntry.asStateFlow()

    private val _daysToShow = MutableStateFlow(sharedPreferences.getInt(KEY_DAYS_TO_SHOW, 3))
    val daysToShow: StateFlow<Int> = _daysToShow.asStateFlow()

    private val _isGpsTrackingEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_GPS_TRACKING_ENABLED, true))
    val isGpsTrackingEnabled: StateFlow<Boolean> = _isGpsTrackingEnabled.asStateFlow()

    private val _gpsInterval = MutableStateFlow(sharedPreferences.getInt(KEY_GPS_INTERVAL_MINUTES, 20))
    val gpsInterval: StateFlow<Int> = _gpsInterval.asStateFlow()

    private val _recentlyDeleted = MutableStateFlow<List<JournalEntry>>(emptyList())
    val canUndo: StateFlow<Boolean> = combine(_recentlyDeleted) { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
    val entries: StateFlow<List<JournalEntry>> =
        combine(daysToShow, selectedCategory, categoriesFlow) { days, selectedCat, categories ->
            Triple(days, selectedCat, categories)
        }.flatMapLatest { (days, selectedCat, categories) ->
            val category = categories.find { it.category == selectedCat }
            if (category?.showAll == true) {
                repository.getAllEntriesFlow()
            } else {
                val daysAgoMillis = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -days)
                }.timeInMillis
                repository.getEntriesSince(daysAgoMillis)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val filteredEntries: StateFlow<List<JournalEntry>> =
        combine(entries, selectedCategory) { entries, category ->
            entries.filter { it.title == category }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedEntries: StateFlow<Map<LocalDate, List<JournalEntry>>> =
        filteredEntries.combine(selectedCategory) { entries, _ ->
            entries.groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    fun onCategoryChange(category: String) {
        _selectedCategory.value = category
    }
    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }
    fun onEntrySelected(entry: JournalEntry) {
        _selectedEntry.value = if (_selectedEntry.value == entry) null else entry
    }

    fun onEditEntry(entry: JournalEntry) {
        _editingEntry.value = entry
    }

    fun onDismissEditEntry() {
        _editingEntry.value = null
    }

    fun onSaveEntry(updatedTitle: String, updatedContent: String, updatedTimestamp: Long, hasImage: Boolean) {
        val sanitizedContent = updatedContent.replace("luisa", "Eloisa", ignoreCase = true)
        viewModelScope.launch {
            _editingEntry.value?.let {
                repository.update(it.copy(title = updatedTitle, content = sanitizedContent, timestamp = updatedTimestamp, hasImage = hasImage))
                _editingEntry.value = null
            }
        }
    }

    fun onDeleteEntry(entry: JournalEntry) {
        viewModelScope.launch {
            val currentDeleted = _recentlyDeleted.value.toMutableList()
            currentDeleted.add(entry)
            if (currentDeleted.size > 3) {
                currentDeleted.removeAt(0)
            }
            _recentlyDeleted.value = currentDeleted
            repository.delete(entry)
        }
    }

    fun onUndoDelete() {
        viewModelScope.launch {
            val lastDeleted = _recentlyDeleted.value.lastOrNull()
            if (lastDeleted != null) {
                repository.insert(lastDeleted)
                _recentlyDeleted.value = _recentlyDeleted.value.dropLast(1)
            }
        }
    }

    fun onMoreClicked() {
        _daysToShow.value += 3
        sharedPreferences.edit { putInt(KEY_DAYS_TO_SHOW, _daysToShow.value) }
    }
    fun saveSettings(days: Int, isGpsEnabled: Boolean, interval: Int) {
        _daysToShow.value = days
        _isGpsTrackingEnabled.value = isGpsEnabled
        _gpsInterval.value = interval
        sharedPreferences.edit {
            putInt(KEY_DAYS_TO_SHOW, days)
            putBoolean(KEY_GPS_TRACKING_ENABLED, isGpsEnabled)
            putInt(KEY_GPS_INTERVAL_MINUTES, interval)
        }
        // This is a bit of a hack, but it triggers the worker to be re-enqueued
        VoiceJournalApplication.setupLocationWorker(applicationContext)
    }

    fun addOrUpdateCategory(categoryName: String, aliasesString: String, showAll: Boolean) {
        viewModelScope.launch {
            val existingCategory = categoriesFlow.value.find { it.category == categoryName }
            val category = Category(
                category = categoryName,
                aliases = aliasesString,
                showAll = showAll,
                orderIndex = existingCategory?.orderIndex ?: categoriesFlow.value.size
            )
            repository.insertCategory(category)
        }
    }

    fun deleteCategory(category: String) {
        viewModelScope.launch {
            repository.deleteCategory(category)
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
            val threeDaysAgo = LocalDate.now().minusDays(3).toString()
            val fourDaysAgo = LocalDate.now().minusDays(4).toString()

            val testEntries = listOf(
                JournalEntry(title = "journal", content = "Habe beim Ausschalten versehentlich den dritten Wecker diesen Monat zerdrückt; mein Rücken erinnert mich schmerzhaft an den gestrigen Kampf.", timestamp = timestampFromString("${today}T06:15:00")),
                JournalEntry(title = "journal", content = "Nach drei Tassen schwarzem Kaffee ignoriere ich die Online-Kritik an meinem Kostüm und starte vom Balkon in die kalte Morgenluft.", timestamp = timestampFromString("${today}T07:00:00")),
                JournalEntry(title = "journal", content = "Die Verfolgungsjagd auf der Autobahn war erfolgreich, aber meine Landung auf der Motorhaube des Fluchtwagens wird wieder Probleme mit der Versicherung geben.", timestamp = timestampFromString("${today}T09:30:00"), hasImage = true),

                JournalEntry(title = "journal", content = "Während im Fernsehen Berichte über meine Heldentaten laufen, sitze ich in Jogginghose da, esse Nudeln mit Ketchup und nähe den Riss in meinem Kostüm.", timestamp = timestampFromString("${today}T21:00:00")),

                JournalEntry(title = "todo", content = "This is a test todo item from today.", timestamp = timestampFromString("${today}T12:00:00")),
                JournalEntry(title = "kaufen", content = "Milk, eggs, bread.", timestamp = timestampFromString("${today}T14:00:00")),
                JournalEntry(title = "baumarkt", content = "A great new app idea from today.", timestamp = timestampFromString("${today}T16:00:00")),

                JournalEntry(title = "journal", content = "Mittagspause mit zwei Dönern auf einem Wasserspeier über der Stadt; habe dabei leider Knoblauchsoße auf meinen Umhang getropft.", timestamp = timestampFromString("${yesterday}T12:30:00"), hasImage = true),
                JournalEntry(title = "journal", content = "Ein kleinerer Schurke wollte die U-Bahn sabotieren, aber sein Monolog über den \"Masterplan\" dauerte länger als der eigentliche Kampf.", timestamp = timestampFromString("${yesterday}T10:00:00")),
                JournalEntry(title = "todo", content = "Todo item from yesterday.", timestamp = timestampFromString("${yesterday}T15:00:00")),
                JournalEntry(title = "kaufen", content = "Apples, bananas.", timestamp = timestampFromString("${yesterday}T17:00:00")),

                JournalEntry(title = "journal", content = "Journal entry from two days ago.", timestamp = timestampFromString("${twoDaysAgo}T18:00:00")),
                JournalEntry(title = "journal", content = "Journal entry from 3 days ago.", timestamp = timestampFromString("${threeDaysAgo}T18:00:00")),
                JournalEntry(title = "journal", content = "Journal entry from 4 days ago.", timestamp = timestampFromString("${fourDaysAgo}T18:00:00")),

                JournalEntry(title = "eloisa", content = "Another app idea from two days ago.", timestamp = timestampFromString("${twoDaysAgo}T20:00:00"))
            )
            testEntries.forEach { repository.insert(it) }
        }
    }
    fun processRecognizedText(recognizedText: String) {
        viewModelScope.launch {
            val entryToUpdate = _selectedEntry.value
            if (entryToUpdate != null) {
                val sanitizedText = recognizedText.replace("luisa", "Eloisa", ignoreCase = true)
                val updatedEntry = entryToUpdate.copy(
                    content = entryToUpdate.content + "\n" + sanitizedText
                )
                repository.update(updatedEntry)
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
                    val category = currentCategoryKeywords[foundKeyword]!!
                    val content = recognizedText.substring(foundKeyword.length).trim()
                    _selectedCategory.value = category
                    category to content
                } else {
                    _selectedCategory.value to recognizedText
                }

                if (contentToAdd.isNotEmpty()) {
                    val sanitizedContent = contentToAdd.replace("luisa", "Eloisa", ignoreCase = true)
                    val entry = JournalEntry(
                        title = targetCategory,
                        content = sanitizedContent,
                        timestamp = timestamp
                    )
                    repository.insert(entry)
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
            return MainViewModel(repository, sharedPreferences, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
