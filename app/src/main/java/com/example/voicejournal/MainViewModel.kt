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
import com.example.voicejournal.di.Injector
import com.example.voicejournal.util.DateFormatters
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal sealed interface LineRemovalResult {
    data object NoChange : LineRemovalResult
    data class ContentUpdated(val updatedContent: String) : LineRemovalResult
    data object DeleteEntireEntry : LineRemovalResult
}

internal sealed interface UndoAction {
    data class RestoreEntryContent(
        val entryId: UUID,
        val previousContent: String
    ) : UndoAction

    data class RestoreSoftDeletedEntries(
        val entryIds: Set<UUID>
    ) : UndoAction
}

enum class UndoPromptType {
    LINE_DELETED,
    ENTRY_DELETED
}

internal sealed interface LineDeleteOutcome {
    data object Unchanged : LineDeleteOutcome
    data class EntryUpdated(val updated: EntryWithCategories) : LineDeleteOutcome
    data class EntrySoftDeleted(val entryId: UUID) : LineDeleteOutcome
}

internal class LineDeleteUndoManager(
    private val repository: JournalRepository
) {
    private val _undoStack = MutableStateFlow<List<UndoAction>>(emptyList())
    val undoStack: StateFlow<List<UndoAction>> = _undoStack.asStateFlow()

    private val _undoPromptEvents = MutableSharedFlow<UndoPromptType>(extraBufferCapacity = 1)
    val undoPromptEvents: SharedFlow<UndoPromptType> = _undoPromptEvents.asSharedFlow()

    val canUndo: Flow<Boolean> = combine(
        _undoStack,
        repository.getDeletedEntriesCount()
    ) { stack, deletedCount ->
        stack.isNotEmpty() || deletedCount > 0
    }

    private fun pushUndoAction(action: UndoAction) {
        _undoStack.update { current ->
            (current + action).takeLast(MainViewModel.MAX_UNDO_HISTORY)
        }
    }

    suspend fun deleteLineInEntry(
        entryWithCategories: EntryWithCategories,
        lineIndex: Int,
        isRecycleBinSelected: Boolean
    ): LineDeleteOutcome {
        if (isRecycleBinSelected) return LineDeleteOutcome.Unchanged
        if (entryWithCategories.entry.deletedAt != null) return LineDeleteOutcome.Unchanged
        if (entryWithCategories.categories.none { it.deleteLineOnDoubleTap }) return LineDeleteOutcome.Unchanged

        val current = repository.getEntryById(entryWithCategories.entry.id) ?: return LineDeleteOutcome.Unchanged
        if (current.categories.none { it.deleteLineOnDoubleTap }) return LineDeleteOutcome.Unchanged
        if (current.entry.content != entryWithCategories.entry.content) return LineDeleteOutcome.Unchanged

        return when (val result = MainViewModel.removeLineAt(current.entry.content, lineIndex)) {
            LineRemovalResult.NoChange -> LineDeleteOutcome.Unchanged
            is LineRemovalResult.ContentUpdated -> {
                pushUndoAction(
                    UndoAction.RestoreEntryContent(
                        entryId = current.entry.id,
                        previousContent = current.entry.content
                    )
                )
                val updatedEntry = current.entry.copy(content = result.updatedContent)
                repository.update(updatedEntry, current.categories)
                _undoPromptEvents.tryEmit(UndoPromptType.LINE_DELETED)
                LineDeleteOutcome.EntryUpdated(current.copy(entry = updatedEntry))
            }
            LineRemovalResult.DeleteEntireEntry -> {
                val idSet = setOf(current.entry.id)
                repository.deleteEntries(idSet)
                pushUndoAction(UndoAction.RestoreSoftDeletedEntries(idSet))
                _undoPromptEvents.tryEmit(UndoPromptType.ENTRY_DELETED)
                LineDeleteOutcome.EntrySoftDeleted(current.entry.id)
            }
        }
    }

    suspend fun deleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        repository.deleteEntries(ids)
        pushUndoAction(UndoAction.RestoreSoftDeletedEntries(ids))
    }

    suspend fun undo(): EntryWithCategories? {
        while (true) {
            var popped: UndoAction? = null
            _undoStack.update { current ->
                if (current.isEmpty()) {
                    current
                } else {
                    popped = current.last()
                    current.dropLast(1)
                }
            }
            val action = popped
            if (action == null) {
                repository.restoreLatestDeletedEntry()
                return null
            }
            when (action) {
                is UndoAction.RestoreEntryContent -> {
                    val existing = repository.getEntryByIdIncludingDeleted(action.entryId)
                    if (existing != null) {
                        if (existing.entry.deletedAt != null) {
                            repository.restoreEntries(setOf(action.entryId))
                        }
                        val restoredEntry = existing.entry.copy(
                            content = action.previousContent,
                            deletedAt = null
                        )
                        repository.update(restoredEntry, existing.categories)
                        return existing.copy(entry = restoredEntry)
                    }
                }
                is UndoAction.RestoreSoftDeletedEntries -> {
                    val existingDeleted = repository.getEntriesByIds(action.entryIds)
                        .filter { it.entry.deletedAt != null }
                    if (existingDeleted.isNotEmpty()) {
                        repository.restoreEntries(existingDeleted.mapTo(LinkedHashSet()) { it.entry.id })
                        return null
                    }
                }
            }
        }
    }

    suspend fun hardDeleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        repository.hardDeleteEntries(ids)
        _undoStack.update { stack ->
            stack.mapNotNull { action ->
                when (action) {
                    is UndoAction.RestoreEntryContent -> action.takeUnless { it.entryId in ids }
                    is UndoAction.RestoreSoftDeletedEntries -> {
                        val remainingIds = action.entryIds - ids
                        if (remainingIds.isEmpty()) null else action.copy(entryIds = remainingIds)
                    }
                }
            }
        }
    }

    suspend fun restoreEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        repository.restoreEntries(ids)
        _undoStack.update { stack ->
            stack.mapNotNull { action ->
                when (action) {
                    is UndoAction.RestoreEntryContent -> action
                    is UndoAction.RestoreSoftDeletedEntries -> {
                        val remainingIds = action.entryIds - ids
                        if (remainingIds.isEmpty()) null else action.copy(entryIds = remainingIds)
                    }
                }
            }
        }
    }
}

class MainViewModel(
    private val repository: JournalRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private data class PagerAnchor(
        val entryId: UUID?,
        val requestVersion: Long = 0L
    )

    companion object {
        internal const val MAX_UNDO_HISTORY = 50

        private val jsonFormat = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        internal fun splitContentIntoLines(content: String): List<String> {
            return content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        }

        internal fun removeLineAt(content: String, lineIndex: Int): LineRemovalResult {
            val lines = splitContentIntoLines(content)
            if (lineIndex !in lines.indices) {
                return LineRemovalResult.NoChange
            }
            val remaining = lines.filterIndexed { idx, _ -> idx != lineIndex }
            if (remaining.isEmpty() || remaining.all { it.isBlank() }) {
                return LineRemovalResult.DeleteEntireEntry
            }
            return LineRemovalResult.ContentUpdated(remaining.joinToString("\n"))
        }

        internal fun parseRecognizedTextForCategory(
            text: String,
            categories: List<Category>,
            currentCategoryId: Int?
        ): Pair<Category, String> {
            val trimmedText = text.trim()
            val realCategories = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
            val defaultCat = if (currentCategoryId != null && currentCategoryId > 0) {
                realCategories.find { it.id == currentCategoryId }
                    ?: realCategories.firstOrNull()
                    ?: Category(category = "journal", aliases = "")
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

        internal fun parseRecognizedTextForCategory(
            text: String,
            categories: List<Category>,
            currentCategoryName: String
        ): Pair<Category, String> {
            val realCategories = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
            val matchedId = if (
                currentCategoryName.isNotBlank() &&
                !JournalConstants.isVirtualDeletedName(currentCategoryName)
            ) {
                realCategories.find { it.category == currentCategoryName }?.id
            } else {
                null
            }
            val effectiveCategories = if (
                matchedId == null &&
                currentCategoryName.isNotBlank() &&
                !JournalConstants.isVirtualDeletedName(currentCategoryName)
            ) {
                val synthetic = Category(id = Int.MAX_VALUE, category = currentCategoryName, aliases = "")
                listOf(synthetic) + realCategories
            } else {
                categories
            }
            val effectiveId = matchedId ?: effectiveCategories.firstOrNull { it.id == Int.MAX_VALUE }?.id
            val (chosen, remaining) = parseRecognizedTextForCategory(
                text = text,
                categories = effectiveCategories,
                currentCategoryId = effectiveId
            )
            return if (chosen.id == Int.MAX_VALUE) {
                Pair(chosen.copy(id = 0), remaining)
            } else {
                Pair(chosen, remaining)
            }
        }

        internal fun resolveTargetCategoryIdForEntry(
            entryCategories: List<Category>,
            currentCategoryId: Int?
        ): Int? {
            val realCategories = entryCategories.filterNot {
                JournalConstants.isVirtualDeletedCategory(it)
            }
            if (currentCategoryId != null && currentCategoryId > 0 && realCategories.any { it.id == currentCategoryId }) {
                return currentCategoryId
            }
            return realCategories.firstOrNull()?.id
        }

        internal fun resolveTargetCategoryForEntry(
            entryCategories: List<Category>,
            currentCategory: String
        ): String? {
            val realCategories = entryCategories.filterNot {
                JournalConstants.isVirtualDeletedCategory(it)
            }
            if (realCategories.any { it.category == currentCategory }) {
                return currentCategory
            }
            return realCategories.firstOrNull()?.category
        }
    }

    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedCategory: StateFlow<Category?> = combine(_selectedCategoryId, categories) { selectedId, allCats ->
        allCats.find { it.id == selectedId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private val undoManager = LineDeleteUndoManager(repository)
    val undoPromptEvents: SharedFlow<UndoPromptType> = undoManager.undoPromptEvents

    val canUndo: StateFlow<Boolean> = undoManager.canUndo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    private val anchorVersionCounter = AtomicLong(0L)
    private val _pagerAnchor = MutableStateFlow(PagerAnchor(null))
    private val _scrollToEntryId = MutableStateFlow<UUID?>(null)
    val scrollToEntryId: StateFlow<UUID?> = _scrollToEntryId.asStateFlow()

    init {
        viewModelScope.launch {
            if (!settingsRepository.areDefaultCategoriesAdded()) {
                addDefaultCategories()
                settingsRepository.markDefaultCategoriesAdded()
            }
            repository.normalizeCategoryOrderIndicesIfNeeded()
            categories.collect { cats ->
                val currentId = _selectedCategoryId.value
                if (cats.isNotEmpty() && (currentId == null || cats.none { it.id == currentId })) {
                    val fallback = cats.firstOrNull { !JournalConstants.isVirtualDeletedCategory(it) }
                        ?: cats.first()
                    _selectedCategoryId.value = fallback.id
                }
            }
        }
        viewModelScope.launch {
            repository.entryCreatedEvents.collect { createdEntryId ->
                scrollToEntry(createdEntryId)
            }
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
        combine(_selectedCategoryId, _pagerAnchor) { categoryId, anchor ->
            categoryId to anchor
        }
            .distinctUntilChanged()
            .flatMapLatest { (categoryId, anchor) ->
                repository.getEntriesPager(categoryId, anchor.entryId)
            }
            .cachedIn(viewModelScope)

    fun toggleEntrySelection(id: UUID) {
        _selectedEntryIds.value = _selectedEntryIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun clearSelection() {
        _selectedEntryIds.value = emptySet()
    }

    private fun formatEntriesAsText(entries: List<EntryWithCategories>): String {
        val viewerZone = ZoneId.systemDefault()
        return entries.joinToString(separator = "\n\n") { entryWithCats ->
            val entry = entryWithCats.entry
            val timeText = TimeZoneUtils.formatEntryDateTimeText(
                startMillis = entry.start_datetime,
                stopMillis = entry.stop_datetime,
                entryZone = entry.resolvedZoneId(viewerZone),
                viewerZone = viewerZone
            )
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
            else -> repository.getEntriesForClipboard(_selectedCategoryId.value, _selectedDate.value)
        }
        return formatEntriesAsText(entries)
    }

    suspend fun getEntriesForSharingAsJson(ids: Set<UUID>): String {
        val entries = repository.getEntriesByIds(ids).map { it.entry }
        return jsonFormat.encodeToString(entries)
    }

    fun onCategoryChange(category: Category) {
        _pagerAnchor.value = PagerAnchor(null)
        _scrollToEntryId.value = null
        _selectedCategoryId.value = category.id
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

    fun deleteLineInEntry(entryWithCategories: EntryWithCategories, lineIndex: Int) {
        viewModelScope.launch {
            val isRecycleBin = _selectedCategoryId.value == JournalConstants.CATEGORY_DELETED_ID
            when (val outcome = undoManager.deleteLineInEntry(entryWithCategories, lineIndex, isRecycleBin)) {
                LineDeleteOutcome.Unchanged -> Unit
                is LineDeleteOutcome.EntryUpdated -> {
                    if (_selectedEntry.value?.entry?.id == outcome.updated.entry.id) {
                        _selectedEntry.value = outcome.updated
                    }
                }
                is LineDeleteOutcome.EntrySoftDeleted -> {
                    if (_selectedEntry.value?.entry?.id == outcome.entryId) {
                        _selectedEntry.value = null
                    }
                    _selectedEntryIds.value = _selectedEntryIds.value - outcome.entryId
                }
            }
        }
    }

    fun deleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            undoManager.deleteEntries(ids)
            if (_selectedEntry.value?.entry?.id in ids) {
                _selectedEntry.value = null
            }
            _selectedEntryIds.value = emptySet()
        }
    }

    fun updateEntriesZoneId(ids: Set<UUID>, zoneId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateEntriesZoneId(ids, zoneId)
            _selectedEntryIds.value = emptySet()
        }
    }

    fun onUndoDelete() {
        viewModelScope.launch {
            val restored = undoManager.undo()
            if (restored != null && _selectedEntry.value?.entry?.id == restored.entry.id) {
                _selectedEntry.value = restored
            }
        }
    }

    fun hardDeleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (_selectedCategoryId.value == JournalConstants.CATEGORY_DELETED_ID) {
                undoManager.hardDeleteEntries(ids)
                _selectedEntryIds.value = emptySet()
            }
        }
    }

    fun restoreEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (_selectedCategoryId.value == JournalConstants.CATEGORY_DELETED_ID) {
                undoManager.restoreEntries(ids)
                _selectedEntryIds.value = emptySet()
            }
        }
    }

    fun scrollToEntry(entryId: UUID) {
        _scrollToEntryId.value = entryId
        viewModelScope.launch {
            val entry = repository.getEntryById(entryId)
            if (entry == null) {
                if (_scrollToEntryId.value == entryId) {
                    _scrollToEntryId.value = null
                }
                return@launch
            }
            val currentCategoryId = _selectedCategoryId.value
            val targetCategoryId = resolveTargetCategoryIdForEntry(entry.categories, currentCategoryId)
            val categoryChanged = if (targetCategoryId != null && targetCategoryId != currentCategoryId) {
                _selectedCategoryId.value = targetCategoryId
                true
            } else {
                false
            }
            if (categoryChanged || _scrollToEntryId.value == entryId) {
                _pagerAnchor.value = PagerAnchor(entryId, anchorVersionCounter.incrementAndGet())
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
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun addTestData() {
        viewModelScope.launch {
            val defaultZone = ZoneId.systemDefault()
            fun startDatetimeFromString(dateTimeString: String): Long {
                val localDateTime = java.time.LocalDateTime.parse(dateTimeString, DateFormatters.ISO_LOCAL_DATE_TIME_SECONDS)
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
            testEntries.forEach { (entry, entryCategories) -> repository.insert(entry, entryCategories) }
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
            } else {
                val availableCategories = categories.value
                val currentCategoryId = _selectedCategoryId.value

                val (targetCategory, contentToAdd) = parseRecognizedTextForCategory(
                    text = recognizedText,
                    categories = availableCategories,
                    currentCategoryId = currentCategoryId
                )

                val currentZone = ZoneId.systemDefault()
                val now = java.time.LocalDateTime.now(currentZone)
                val startDatetime = _selectedDate.value?.atTime(now.toLocalTime())?.atZone(currentZone)?.toInstant()?.toEpochMilli()
                    ?: System.currentTimeMillis()
                _selectedDate.value = null

                if (contentToAdd.isNotEmpty()) {
                    val entry = JournalEntry(
                        content = contentToAdd,
                        start_datetime = startDatetime,
                        zoneId = currentZone.id
                    )
                    val persistedCategories = repository.insert(entry, listOf(targetCategory))
                    val persistedCategoryId = persistedCategories.firstOrNull()?.id
                        ?: targetCategory.id.takeIf { it > 0 }

                    if (persistedCategoryId != null && _selectedCategoryId.value != persistedCategoryId) {
                        _selectedCategoryId.value = persistedCategoryId
                    }
                    _pagerAnchor.value = PagerAnchor(entry.id, anchorVersionCounter.incrementAndGet())
                    _scrollToEntryId.value = entry.id
                }
            }
        }
    }

    fun toggleEntryChecked(entry: JournalEntry) {
        viewModelScope.launch {
            repository.updateEntryChecked(entry.id, !entry.checked)
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
