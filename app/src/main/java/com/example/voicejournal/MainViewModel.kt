package com.example.voicejournal

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.data.SettingsRepository
import com.example.voicejournal.data.SpeechService
import com.example.voicejournal.data.resolvedZoneId
import com.example.voicejournal.data.startLocalDateTime
import com.example.voicejournal.data.stopLocalDateTime
import com.example.voicejournal.di.Injector
import com.example.voicejournal.util.TimeZoneUtils
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
import java.util.Locale
import java.util.UUID

class MainViewModel(
    private val repository: JournalRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private val jsonFormat = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        internal fun parseRecognizedTextForCategory(
            text: String,
            categories: List<Category>,
            currentCategoryName: String
        ): Pair<Category, String> {
            val trimmedText = text.trim()
            val realCategories = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
            val defaultCat = if (currentCategoryName.isNotBlank() && !JournalConstants.isVirtualDeletedName(currentCategoryName)) {
                realCategories.find { it.category == currentCategoryName }
                    ?: Category(category = currentCategoryName, aliases = "")
            } else {
                realCategories.firstOrNull() ?: Category(category = "journal", aliases = "")
            }

            if (trimmedText.isEmpty()) {
                return Pair(defaultCat, "")
            }

            // a) Extrahiere das erste Wort (ignoriere Satzzeichen und beachte Case-Insensitivity)
            val wordRegex = Regex("^([^\\s\\p{Punct}]+)")
            val matchResult = wordRegex.find(trimmedText)

            if (matchResult != null) {
                val firstWord = matchResult.groupValues[1].lowercase(Locale.ROOT)

                // b) Prüfe ob dieses Wort einer echten Kategorie oder deren Aliases entspricht (niemals 'Gelöscht')
                val matchedCategory = realCategories.find { category ->
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
    }

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedEntry = MutableStateFlow<EntryWithCategories?>(null)
    val selectedEntry: StateFlow<EntryWithCategories?> = _selectedEntry.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _selectedEntryIds = MutableStateFlow<Set<UUID>>(emptySet())
    val selectedEntryIds: StateFlow<Set<UUID>> = _selectedEntryIds.asStateFlow()

    val isGpsTrackingEnabled: StateFlow<Boolean> = settingsRepository.isGpsTrackingEnabled
    val isWebServerEnabled: StateFlow<Boolean> = settingsRepository.isWebServerEnabled
    val speechService: StateFlow<SpeechService> = settingsRepository.speechService
    val googleCloudApiKey: StateFlow<String> = settingsRepository.googleCloudApiKey
    val maxRecordingTime: StateFlow<Int> = settingsRepository.maxRecordingTime
    val silenceThreshold: StateFlow<Int> = settingsRepository.silenceThreshold
    val silenceTimeRequired: StateFlow<Int> = settingsRepository.silenceTimeRequired
    val truncationLength: StateFlow<Int> = settingsRepository.truncationLength
    val isDeveloperModeEnabled: StateFlow<Boolean> = settingsRepository.isDeveloperModeEnabled
    val showCategoryTags: StateFlow<Boolean> = settingsRepository.showCategoryTags

    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1)


    val canUndo: StateFlow<Boolean> = repository.getDeletedEntriesCount().map { it > 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    val categoriesFlow: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = categoriesFlow.map { categories ->
        categories.map { it.category }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val gpsTrackPoints: StateFlow<List<GpsTrackPoint>> = selectedDate
        .flatMapLatest { date ->
            val targetDate = date ?: LocalDate.now()
            repository.getEntriesForLocalDate(targetDate).flatMapLatest { entriesOnDate ->
                val dayZone = entriesOnDate
                    .mapNotNull { it.entry.zoneId }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?.let { TimeZoneUtils.resolveZoneId(it) }
                    ?: ZoneId.systemDefault()
                val startOfDay = targetDate.atStartOfDay(dayZone).toInstant().toEpochMilli()
                val endOfDay = targetDate.atTime(LocalTime.MAX).atZone(dayZone).toInstant().toEpochMilli()
                repository.getTrackPointsForDay(startOfDay, endOfDay)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasGpsTrackForSelectedDate: StateFlow<Boolean> = combine(selectedDate, gpsTrackPoints) { date, points ->
        date != null && points.size >= 2
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _pagerAnchorEntryId = MutableStateFlow<UUID?>(null)
    private val _scrollToEntryId = MutableStateFlow<UUID?>(null)
    val scrollToEntryId: StateFlow<UUID?> = _scrollToEntryId.asStateFlow()

    init {
        viewModelScope.launch {
            if (!settingsRepository.areDefaultCategoriesAdded()) {
                addDefaultCategories()
                settingsRepository.markDefaultCategoriesAdded()
            }
            repository.normalizeCategoryOrderIndicesIfNeeded()
            categories.collect {
                if (it.isNotEmpty() && _selectedCategory.value.isEmpty()) {
                    _selectedCategory.value = it.firstOrNull { name ->
                        !JournalConstants.isVirtualDeletedName(name)
                    } ?: it.first()
                }
            }
        }
        viewModelScope.launch {
            _refreshTrigger.emit(Unit) // Emit initial value to trigger loading
        }
    }

    private suspend fun addDefaultCategories() {
        val defaultCategories = listOf(
            Category(category = "journal", aliases = "journal,tagebuch", orderIndex = 0),
            Category(category = "todo", aliases = "todo,to-do,todoo", orderIndex = 1),
            Category(category = "kaufen", aliases = "kaufen,einkaufen", orderIndex = 2),
            Category(category = "baumarkt", aliases = "baumarkt", orderIndex = 3),
            Category(category = "eloisa", aliases = "eloisa,luisa", orderIndex = 4)
        )
        defaultCategories.forEach { repository.insertCategory(it) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedEntries: Flow<PagingData<EntryWithCategories>> =
        combine(
            selectedCategory,
            categoriesFlow,
            _pagerAnchorEntryId,
            _refreshTrigger // Just to trigger
        ) { selectedCat, categories, anchorEntryId, _ ->
            Triple(categories.find { it.category == selectedCat }, anchorEntryId, selectedCat)
        }.flatMapLatest { (category, anchorEntryId, _) ->
            repository.getEntriesPager(category?.id, anchorEntryId)
        }.cachedIn(viewModelScope)

    fun toggleEntrySelection(id: UUID) {
        _selectedEntryIds.value = _selectedEntryIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun clearSelection() {
        _selectedEntryIds.value = emptySet()
    }

    private fun formatEntriesAsText(entries: List<EntryWithCategories>): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
        val viewerZone = ZoneId.systemDefault()
        return entries.joinToString(separator = "\n\n") { entryWithCats ->
            val entry = entryWithCats.entry
            val entryZone = entry.resolvedZoneId(viewerZone)
            val start = entry.startLocalDateTime(viewerZone)
            val stop = entry.stopLocalDateTime(viewerZone)
            val baseTimeText = if (stop != null) {
                "${start.format(formatter)} - ${stop.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            } else {
                start.format(formatter)
            }
            val startInstant = java.time.Instant.ofEpochMilli(entry.start_datetime)
            val entryOffset = entryZone.rules.getOffset(startInstant)
            val viewerOffset = viewerZone.rules.getOffset(startInstant)
            val timeText = if (entryOffset != viewerOffset) {
                "$baseTimeText (${TimeZoneUtils.formatUtcOffset(entryOffset)})"
            } else {
                baseTimeText
            }
            "[$timeText]\n${entry.content}"
        }
    }

    suspend fun getEntriesForSharing(ids: Set<UUID>): String {
        val entries = repository.getEntriesByIds(ids)
        return formatEntriesAsText(entries)
    }

    suspend fun getEntriesTextForClipboard(): String {
        val singleSelected = _selectedEntry.value
        val entries = when {
            singleSelected != null -> listOf(singleSelected)
            _selectedEntryIds.value.isNotEmpty() -> repository.getEntriesByIds(_selectedEntryIds.value)
            else -> repository.getEntriesForClipboard(_selectedCategory.value, _selectedDate.value)
        }
        return formatEntriesAsText(entries)
    }

    suspend fun getEntriesForSharingAsJson(ids: Set<UUID>): String {
        val entries = repository.getEntriesByIds(ids).map { it.entry }
        return jsonFormat.encodeToString(entries)
    }

    fun onCategoryChange(category: String) {
        _pagerAnchorEntryId.value = null
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

    fun deleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteEntries(ids)
            _selectedEntryIds.value = emptySet()
            _refreshTrigger.emit(Unit)
        }
    }

    fun updateEntriesZoneId(ids: Set<UUID>, zoneId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateEntriesZoneId(ids, zoneId)
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
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (JournalConstants.isVirtualDeletedName(_selectedCategory.value)) {
                repository.hardDeleteEntries(ids)
                _selectedEntryIds.value = emptySet()
                _refreshTrigger.emit(Unit)
            }
        }
    }

    fun restoreEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (JournalConstants.isVirtualDeletedName(_selectedCategory.value)) {
                repository.restoreEntries(ids)
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
                _pagerAnchorEntryId.value = entryId
                _scrollToEntryId.value = entryId
            }
        }
    }

    fun onScrolledToEntry() {
        _scrollToEntryId.value = null
    }

    fun toggleShowCategoryTags() {
        settingsRepository.toggleShowCategoryTags()
    }

    suspend fun exportJournal(uri: Uri) {
        repository.exportJournal(uri)
    }

    suspend fun importJournal(uri: Uri) {
        repository.importJournal(uri)
        _refreshTrigger.emit(Unit)
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            _refreshTrigger.emit(Unit)
        }
    }

    fun addTestData() {
        viewModelScope.launch {
            val defaultZone = ZoneId.systemDefault()
            fun startDatetimeFromString(dateTimeString: String): Long {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val localDateTime = java.time.LocalDateTime.parse(dateTimeString, formatter)
                return localDateTime.atZone(defaultZone).toInstant().toEpochMilli()
            }

            val today = LocalDate.now(defaultZone).toString()
            val yesterday = LocalDate.now(defaultZone).minusDays(1).toString()
            val twoDaysAgo = LocalDate.now(defaultZone).minusDays(2).toString()

            val journal = Category(category = "journal", aliases = "")
            val todo = Category(category = "todo", aliases = "")
            val kaufen = Category(category = "kaufen", aliases = "")
            val baumarkt = Category(category = "baumarkt", aliases = "")
            val eloisa = Category(category = "eloisa", aliases = "")

            val testEntries: List<Pair<JournalEntry, List<Category>>> = listOf(
                JournalEntry(content = "Habe beim Ausschalten versehentlich den dritten Wecker diesen Monat zerdrückt...", start_datetime = startDatetimeFromString("${today}T06:15:00"), stop_datetime = startDatetimeFromString("${today}T06:20:00"), zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "Nach drei Tassen schwarzem Kaffee...", start_datetime = startDatetimeFromString("${today}T07:00:00"), zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "Die Verfolgungsjagd auf der Autobahn war erfolgreich...", start_datetime = startDatetimeFromString("${today}T09:30:00"), hasImage = true, zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "Während im Fernsehen Berichte über meine Heldentaten laufen...", start_datetime = startDatetimeFromString("${today}T21:00:00"), zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "This is a test todo item from today.", start_datetime = startDatetimeFromString("${today}T12:00:00"), stop_datetime = startDatetimeFromString("${today}T12:30:00"), zoneId = defaultZone.id) to listOf(todo),
                JournalEntry(content = "Milk, eggs, bread.", start_datetime = startDatetimeFromString("${today}T14:00:00"), zoneId = defaultZone.id) to listOf(kaufen),
                JournalEntry(content = "A great new app idea from today.", start_datetime = startDatetimeFromString("${today}T16:00:00"), zoneId = defaultZone.id) to listOf(baumarkt),
                JournalEntry(content = "Mittagspause mit zwei Dönern...", start_datetime = startDatetimeFromString("${yesterday}T12:30:00"), hasImage = true, zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "Ein kleinerer Schurke wollte die U-Bahn sabotieren...", start_datetime = startDatetimeFromString("${yesterday}T10:00:00"), zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "Todo item from yesterday.", start_datetime = startDatetimeFromString("${yesterday}T15:00:00"), zoneId = defaultZone.id) to listOf(todo),
                JournalEntry(content = "Apples, bananas.", start_datetime = startDatetimeFromString("${yesterday}T17:00:00"), zoneId = defaultZone.id) to listOf(kaufen),
                JournalEntry(content = "Journal entry from two days ago.", start_datetime = startDatetimeFromString("${twoDaysAgo}T18:00:00"), zoneId = defaultZone.id) to listOf(journal),
                JournalEntry(content = "Another app idea from two days ago.", start_datetime = startDatetimeFromString("${twoDaysAgo}T20:00:00"), zoneId = defaultZone.id) to listOf(eloisa)
            )
            testEntries.forEach { (entry, categories) -> repository.insert(entry, categories) }
            _refreshTrigger.emit(Unit)
        }
    }
    
    fun processRecognizedText(recognizedText: String) {
        viewModelScope.launch {
            val entryToUpdate = _selectedEntry.value
            if (entryToUpdate != null) {
                val updatedEntry = entryToUpdate.entry.copy(
                    content = entryToUpdate.entry.content + "\n" + recognizedText
                )
                repository.update(updatedEntry, entryToUpdate.categories)
                _selectedEntry.value = null // Deselect after update
                _refreshTrigger.emit(Unit)
            } else {
                val categories = categoriesFlow.value
                val currentCategoryName = _selectedCategory.value

                val (targetCategory, contentToAdd) = parseRecognizedTextForCategory(recognizedText, categories, currentCategoryName)

                val currentZone = ZoneId.systemDefault()
                val now = java.time.LocalDateTime.now(currentZone)
                val start_datetime = _selectedDate.value?.atTime(now.toLocalTime())?.atZone(currentZone)?.toInstant()?.toEpochMilli()
                    ?: System.currentTimeMillis()
                _selectedDate.value = null

                if (contentToAdd.isNotEmpty()) {
                    val entry = JournalEntry(
                        content = contentToAdd,
                        start_datetime = start_datetime,
                        zoneId = currentZone.id
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

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val appContext = context.applicationContext
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                repository = Injector.provideJournalRepository(appContext),
                settingsRepository = Injector.provideSettingsRepository(appContext)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}