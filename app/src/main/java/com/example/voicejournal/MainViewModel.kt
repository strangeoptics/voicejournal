package com.example.voicejournal

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.CategoryAlias
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryDao
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class MainViewModel(private val dao: JournalEntryDao, private val sharedPreferences: SharedPreferences) : ViewModel() {

    companion object {
        const val PREFS_NAME = "voice_journal_prefs"
        const val KEY_DAYS_TO_SHOW = "days_to_show"
        const val KEY_DEFAULT_CATEGORIES_ADDED = "default_categories_added"
    }

    // Removed hardcoded categories

    private val _selectedCategory = MutableStateFlow("") // Initialize with empty string, will be set after categories are loaded
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedEntry = MutableStateFlow<JournalEntry?>(null)
    val selectedEntry: StateFlow<JournalEntry?> = _selectedEntry.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _editingEntry = MutableStateFlow<JournalEntry?>(null)
    val editingEntry: StateFlow<JournalEntry?> = _editingEntry.asStateFlow()

    private val _daysToShow = MutableStateFlow(sharedPreferences.getInt(KEY_DAYS_TO_SHOW, 3))
    val daysToShow: StateFlow<Int> = _daysToShow.asStateFlow()

    private val _recentlyDeleted = MutableStateFlow<List<JournalEntry>>(emptyList())
    val canUndo: StateFlow<Boolean> = combine(_recentlyDeleted) { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // New StateFlows for categories and aliases from Room
    val categoryAliases: StateFlow<List<CategoryAlias>> = dao.getAllCategoryAliases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = categoryAliases.map { aliases ->
        aliases.map { it.category }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryKeywordsMap: StateFlow<Map<String, String>> = categoryAliases.map { aliases ->
        aliases.associate { it.alias.lowercase(Locale.ROOT) to it.category.lowercase(Locale.ROOT) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            val areDefaultCategoriesAdded = sharedPreferences.getBoolean(KEY_DEFAULT_CATEGORIES_ADDED, false)
            if (!areDefaultCategoriesAdded) {
                addDefaultCategories()
                sharedPreferences.edit { putBoolean(KEY_DEFAULT_CATEGORIES_ADDED, true) }
            }
            // Set the selected category to the first one available after loading
            categories.collect {
                if (it.isNotEmpty() && _selectedCategory.value.isEmpty()) {
                    _selectedCategory.value = it.first()
                }
            }
        }
    }

    private suspend fun addDefaultCategories() {
        val defaultCategories = listOf(
            CategoryAlias(category = "journal", alias = "journal"),
            CategoryAlias(category = "journal", alias = "tagebuch"),
            CategoryAlias(category = "todo", alias = "todo"),
            CategoryAlias(category = "todo", alias = "to-do"),
            CategoryAlias(category = "todo", alias = "todoo"),
            CategoryAlias(category = "kaufen", alias = "kaufen"),
            CategoryAlias(category = "kaufen", alias = "einkaufen"),
            CategoryAlias(category = "baumarkt", alias = "baumarkt"),
            CategoryAlias(category = "eloisa", alias = "eloisa"),
            CategoryAlias(category = "eloisa", alias = "luisa")
        )
        defaultCategories.forEach { dao.insertCategoryAlias(it) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<JournalEntry>> =
        daysToShow.flatMapLatest { days ->
            val daysAgoMillis = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -days)
            }.timeInMillis
            dao.getEntriesSince(daysAgoMillis)
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

    fun onSaveEntry(updatedContent: String, updatedTimestamp: Long, hasImage: Boolean) {
        viewModelScope.launch {
            _editingEntry.value?.let {
                dao.update(it.copy(content = updatedContent, timestamp = updatedTimestamp, hasImage = hasImage))
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
            dao.delete(entry)
        }
    }

    fun onUndoDelete() {
        viewModelScope.launch {
            val lastDeleted = _recentlyDeleted.value.lastOrNull()
            if (lastDeleted != null) {
                dao.insert(lastDeleted)
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

    fun updateAliasesForCategory(category: String, aliasesString: String) {
        viewModelScope.launch {
            val newAliases = aliasesString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            dao.updateAliasesForCategory(category, newAliases)
        }
    }

    fun deleteCategory(category: String) {
        viewModelScope.launch {
            dao.deleteCategory(category)
        }
    }

    fun importJournalEntries(content: String) {
        viewModelScope.launch {
            val pattern = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\] (.*)")
            content.lines().forEach { line ->
                val matcher = pattern.matcher(line)
                if (matcher.matches()) {
                    val dateTimeString = matcher.group(1)
                    val contentString = matcher.group(2)
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    val timestamp = LocalDateTime.parse(dateTimeString, formatter)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()

                    val entry = JournalEntry(
                        title = "journal",
                        content = contentString,
                        timestamp = timestamp
                    )
                    dao.insert(entry)
                }
            }
        }
    }


    fun addTestData() {
        viewModelScope.launch {
            dao.deleteAll()

            fun timestampFromString(dateTimeString: String): Long {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val localDateTime = LocalDateTime.parse(dateTimeString, formatter)
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
            testEntries.forEach { dao.insert(it) }
        }
    }
    fun processRecognizedText(recognizedText: String) {
        viewModelScope.launch {
            val entryToUpdate = _selectedEntry.value
            if (entryToUpdate != null) {
                val updatedEntry = entryToUpdate.copy(
                    content = entryToUpdate.content + "\n" + recognizedText
                )
                dao.update(updatedEntry)
                _selectedEntry.value = null // Deselect after update
            } else {
                // Use categoryKeywordsMap from StateFlow
                val currentCategoryKeywords = categoryKeywordsMap.value

                // Find a keyword that matches the start of the recognized text
                val foundKeyword = currentCategoryKeywords.keys.find { keyword ->
                    recognizedText.startsWith(keyword, ignoreCase = true) &&
                            (recognizedText.length == keyword.length || recognizedText.getOrNull(keyword.length)?.isWhitespace() == true)
                }
                val now = LocalDateTime.now()
                val timestamp = _selectedDate.value?.atTime(now.toLocalTime())?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    ?: System.currentTimeMillis()
                _selectedDate.value = null


                val (targetCategory, contentToAdd) = if (foundKeyword != null) {
                    // Keyword was found
                    val category = currentCategoryKeywords[foundKeyword]!!
                    val content = recognizedText.substring(foundKeyword.length).trim()
                    _selectedCategory.value = category
                    category to content
                } else {
                    // No keyword found, add the entire text to the currently selected category
                    _selectedCategory.value to recognizedText
                }

                if (contentToAdd.isNotEmpty()) {
                    val entry = JournalEntry(
                        title = targetCategory,
                        content = contentToAdd,
                        timestamp = timestamp
                    )
                    dao.insert(entry)
                }
            }
        }
    }
}

class MainViewModelFactory(private val dao: JournalEntryDao,  private val sharedPreferences: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}