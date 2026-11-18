package com.example.voicejournal.data

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import com.example.voicejournal.util.TimeZoneUtils
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

internal enum class BackupJsonFormat {
    OBJECT_V3,
    OBJECT_V2,
    ARRAY_V3,
    ARRAY_V2,
    EMPTY_ARRAY
}

object JournalConstants {
    const val CATEGORY_DELETED_ID = -1
    const val CATEGORY_DELETED_NAME = "Gelöscht"
    const val SQLITE_MAX_BIND_PARAMS = 900
    const val MAX_BACKUP_IMPORT_BYTES = 50L * 1024L * 1024L // 50 MB

    internal fun readBoundedUtf8(
        inputStream: InputStream,
        maxBytes: Long = MAX_BACKUP_IMPORT_BYTES
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0L
        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break
            totalRead += bytesRead
            if (totalRead > maxBytes) {
                val mbLimit = (maxBytes / (1024L * 1024L)).coerceAtLeast(1L)
                throw IllegalArgumentException(
                    "Die ausgewählte Datei ist zu groß (maximal $mbLimit MB erlaubt)."
                )
            }
            output.write(buffer, 0, bytesRead)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    internal fun detectBackupJsonFormat(root: JsonElement): BackupJsonFormat {
        return when (root) {
            is JsonObject -> {
                val version = (root["version"] as? JsonPrimitive)?.intOrNull
                when {
                    version != null && version >= 3 -> BackupJsonFormat.OBJECT_V3
                    version != null -> BackupJsonFormat.OBJECT_V2
                    "entries" in root -> {
                        val entriesArray = root["entries"] as? JsonArray
                            ?: throw IllegalArgumentException("Ungültiges 'entries'-Feld im JSON-Backup.")
                        val firstEntry = entriesArray.firstOrNull() as? JsonObject
                        if (firstEntry != null && "start_datetime" in firstEntry) {
                            BackupJsonFormat.OBJECT_V3
                        } else {
                            BackupJsonFormat.OBJECT_V2
                        }
                    }
                    else -> throw IllegalArgumentException("Unbekanntes JSON-Backup-Format.")
                }
            }
            is JsonArray -> {
                if (root.isEmpty()) {
                    BackupJsonFormat.EMPTY_ARRAY
                } else {
                    val firstEntry = root.first() as? JsonObject
                        ?: throw IllegalArgumentException("Ungültiges JSON-Array-Backup-Format.")
                    when {
                        "start_datetime" in firstEntry -> BackupJsonFormat.ARRAY_V3
                        "timestamp" in firstEntry -> BackupJsonFormat.ARRAY_V2
                        else -> throw IllegalArgumentException("Unbekanntes JSON-Eintragsformat.")
                    }
                }
            }
            else -> throw IllegalArgumentException("Unbekanntes JSON-Backup-Format.")
        }
    }

    fun isVirtualDeletedCategory(category: Category): Boolean {
        return category.id == CATEGORY_DELETED_ID ||
            category.category.trim().equals(CATEGORY_DELETED_NAME, ignoreCase = true)
    }

    fun isVirtualDeletedName(name: String?): Boolean {
        return name != null && name.trim().equals(CATEGORY_DELETED_NAME, ignoreCase = true)
    }

    fun nextSafeOrderIndex(categories: Collection<Category>): Int {
        val realCategories = categories.filterNot { isVirtualDeletedCategory(it) }
        if (realCategories.isEmpty()) return 0
        val maxExisting = realCategories.maxOf { it.orderIndex }.coerceAtMost(Int.MAX_VALUE - 2)
        return maxOf(maxExisting + 1, realCategories.size)
    }

    /**
     * Normalisiert und escaped eine Suchanfrage für SQLite `LIKE ... ESCAPE '\'`
     * (inkl. deutscher Umlaute und `ẞ` -> `ß`). Gibt `null` zurück, wenn die Anfrage
     * nicht-unterstützte Unicode-Zeichen enthält, sodass auf In-Memory-Filterung ausgewichen wird.
     */
    fun normalizeSearchQueryForSql(trimmedQuery: String): String? {
        if (trimmedQuery.isEmpty()) return ""
        val lowered = trimmedQuery.lowercase(Locale.GERMAN).replace("ẞ", "ß")
        val hasUnsupportedNonAscii = lowered.any { ch -> ch.code > 127 && ch !in "äöüß" }
        if (hasUnsupportedNonAscii) return null

        return buildString(lowered.length) {
            for (ch in lowered) {
                when (ch) {
                    '\\', '%', '_' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
    }
}

class JournalRepository(
    private val database: AppDatabase,
    private val entryDao: JournalEntryDao,
    private val categoryDao: CategoryDao,
    private val gpsTrackPointDao: GpsTrackPointDao,
    private val context: Context
) {

    private val importJson = Json { ignoreUnknownKeys = true }
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val virtualDeletedCategory = Category(
        id = JournalConstants.CATEGORY_DELETED_ID,
        category = JournalConstants.CATEGORY_DELETED_NAME,
        aliases = "",
        orderIndex = Int.MAX_VALUE, // Put it at the end
        color = "#FFCDD2", // Light red
        checkable = false,
        showDate = true
    )

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories().map { categories ->
        categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) } + virtualDeletedCategory
    }

    val realCategories: Flow<List<Category>> = categoryDao.getAllCategories().map { categories ->
        categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
    }

    suspend fun getEntriesPager(
        categoryId: Int?,
        initialEntryId: UUID?
    ): Flow<PagingData<EntryWithCategories>> {
        val initialKey = initialEntryId?.let {
            if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
                entryDao.getPositionOfDeletedEntry(it)
            } else if (categoryId != null && categoryId != -1) {
                entryDao.getPositionOfEntryInCategory(it, categoryId)
            } else {
                entryDao.getPositionOfEntry(it)
            }
        }

        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            initialKey = initialKey,
            pagingSourceFactory = {
                if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
                    entryDao.getDeletedEntriesPagingSource()
                } else if (categoryId != null && categoryId != -1) {
                    entryDao.getEntriesPagingSourceForCategory(categoryId)
                } else {
                    entryDao.getEntriesPagingSource()
                }
            }
        ).flow
    }

    fun getEntriesForCalendar(startTime: Long, endTime: Long): Flow<List<EntryWithCategories>> =
        entryDao.getEntriesForCalendar(startTime, endTime)

    suspend fun getEntryById(entryId: UUID): EntryWithCategories? = entryDao.getEntryById(entryId)

    suspend fun getEntriesByIds(ids: Set<UUID>): List<EntryWithCategories> {
        if (ids.isEmpty()) return emptyList()
        if (ids.size <= JournalConstants.SQLITE_MAX_BIND_PARAMS) {
            return entryDao.getEntriesByIds(ids)
        }
        return ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS)
            .flatMap { chunk -> entryDao.getEntriesByIds(chunk) }
            .sortedBy { it.entry.start_datetime }
    }

    suspend fun getEntriesForClipboard(
        categoryName: String,
        selectedDate: LocalDate?
    ): List<EntryWithCategories> {
        if (JournalConstants.isVirtualDeletedName(categoryName)) {
            val deleted = entryDao.getPaginatedDeletedEntries(500, 0)
            return if (selectedDate != null) {
                deleted.filter { it.entry.localDate() == selectedDate }
            } else {
                deleted
            }
        }

        val category = if (categoryName.isNotBlank()) {
            categoryDao.getCategoryByName(categoryName)
        } else {
            null
        }

        if (selectedDate != null) {
            val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(selectedDate)
            val candidates = if (category != null) {
                entryDao.getEntriesWithCategoriesInDateRangeForCategory(category.id, queryStartMillis, queryEndMillis).first()
            } else {
                entryDao.getEntriesWithCategoriesInDateRange(queryStartMillis, queryEndMillis).first()
            }
            return candidates.filter { it.entry.localDate() == selectedDate }
        }

        return if (category != null) {
            entryDao.getEntriesForCategory(category.id)
        } else {
            entryDao.getPaginatedEntriesWithCategories(500, 0)
        }
    }

    fun getEntriesForLocalDate(selectedDate: LocalDate): Flow<List<EntryWithCategories>> {
        val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(selectedDate)
        return entryDao.getEntriesWithCategoriesInDateRange(queryStartMillis, queryEndMillis)
            .map { entries -> entries.filter { it.entry.localDate() == selectedDate } }
    }

    private suspend fun ensureCategoryExists(exportedCategory: Category) {
        if (JournalConstants.isVirtualDeletedCategory(exportedCategory) ||
            exportedCategory.category.isBlank()
        ) {
            return
        }
        val existing = categoryDao.getCategoryByName(exportedCategory.category)
        if (existing == null) {
            val safeOrderIndex = exportedCategory.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)
            // Reset auto-generated ID to 0 to avoid primary key collisions across devices while preserving metadata
            categoryDao.insertCategory(exportedCategory.copy(id = 0, orderIndex = safeOrderIndex))
        }
    }

    suspend fun importJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            JournalConstants.readBoundedUtf8(inputStream)
        } ?: throw IOException("Die ausgewählte Datei konnte nicht geöffnet werden.")

        val trimmed = content.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Die ausgewählte Datei ist leer.")
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val root = importJson.parseToJsonElement(trimmed)
                val format = JournalConstants.detectBackupJsonFormat(root)
                database.withTransaction {
                    when (format) {
                        BackupJsonFormat.OBJECT_V3 -> {
                            val journalExport = importJson.decodeFromJsonElement<JournalExportV3>(root)
                            journalExport.categories.forEach { ensureCategoryExists(it) }
                            journalExport.entries.forEach { entryExport ->
                                val entry = JournalEntry(
                                    id = entryExport.id,
                                    content = entryExport.content,
                                    start_datetime = entryExport.start_datetime,
                                    stop_datetime = entryExport.stop_datetime,
                                    hasImage = entryExport.hasImage,
                                    checked = entryExport.checked,
                                    deletedAt = null,
                                    zoneId = TimeZoneUtils.sanitizeZoneId(entryExport.zoneId)
                                )
                                val categories = entryExport.categories
                                    .filter { it.isNotBlank() && !JournalConstants.isVirtualDeletedName(it) }
                                    .map { categoryName ->
                                        Category(category = categoryName, aliases = "")
                                    }
                                entryDao.insertWithCategories(entry, categories, categoryDao)
                            }
                        }
                        BackupJsonFormat.OBJECT_V2 -> {
                            val journalExport = importJson.decodeFromJsonElement<JournalExport>(root)
                            journalExport.categories.forEach { ensureCategoryExists(it) }
                            journalExport.entries.forEach { v2Entry ->
                                val newEntry = JournalEntry(
                                    content = v2Entry.content,
                                    start_datetime = v2Entry.timestamp,
                                    hasImage = v2Entry.hasImage
                                )
                                val categoryName = v2Entry.title.ifBlank { "journal" }
                                entryDao.insertWithCategories(
                                    newEntry,
                                    listOf(Category(category = categoryName, aliases = "")),
                                    categoryDao
                                )
                            }
                        }
                        BackupJsonFormat.ARRAY_V3 -> {
                            val entries = importJson.decodeFromJsonElement<List<JournalEntry>>(root)
                            val fallbackCategory = Category(category = "journal", aliases = "")
                            entries.forEach { imported ->
                                val cleanEntry = imported.copy(
                                    deletedAt = null,
                                    zoneId = TimeZoneUtils.sanitizeZoneId(imported.zoneId)
                                )
                                val existing = entryDao.getEntryByIdIncludingDeleted(cleanEntry.id)
                                if (existing != null && existing.categories.isNotEmpty()) {
                                    entryDao.updateWithCategories(cleanEntry, existing.categories, categoryDao)
                                } else {
                                    entryDao.insertWithCategories(cleanEntry, listOf(fallbackCategory), categoryDao)
                                }
                            }
                        }
                        BackupJsonFormat.ARRAY_V2 -> {
                            val entries = importJson.decodeFromJsonElement<List<JournalEntryV2>>(root)
                            entries.forEach { v2Entry ->
                                val newEntry = JournalEntry(
                                    content = v2Entry.content,
                                    start_datetime = v2Entry.timestamp,
                                    hasImage = v2Entry.hasImage
                                )
                                val categoryName = v2Entry.title.ifBlank { "journal" }
                                entryDao.insertWithCategories(
                                    newEntry,
                                    listOf(Category(category = categoryName, aliases = "")),
                                    categoryDao
                                )
                            }
                        }
                        BackupJsonFormat.EMPTY_ARRAY -> Unit
                    }
                }
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Ungültiges JSON-Backup-Format: ${e.message}", e)
            }
        } else {
            val importedCount = database.withTransaction {
                parseLegacyFormat(trimmed)
            }
            if (importedCount == 0) {
                throw IllegalArgumentException("Keine gültigen Tagebucheinträge in der Datei gefunden.")
            }
        }
    }

    private suspend fun parseLegacyFormat(content: String): Int {
        val pattern = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\] (.*)")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        var importedCount = 0
        content.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.matches()) {
                val dateTimeString = matcher.group(1) ?: return@forEach
                val contentString = matcher.group(2) ?: ""
                val defaultZone = ZoneId.systemDefault()
                val startDatetime = LocalDateTime.parse(dateTimeString, formatter)
                    .atZone(defaultZone)
                    .toInstant()
                    .toEpochMilli()

                val entry = JournalEntry(
                    content = contentString,
                    start_datetime = startDatetime,
                    zoneId = defaultZone.id
                )
                entryDao.insertWithCategories(
                    entry,
                    listOf(Category(category = "journal", aliases = "")),
                    categoryDao
                )
                importedCount++
            }
        }
        return importedCount
    }

    suspend fun importEntriesToCategory(entries: List<JournalEntry>, targetCategory: Category) = withContext(Dispatchers.IO) {
        if (targetCategory.id == JournalConstants.CATEGORY_DELETED_ID ||
            targetCategory.category.equals("Gelöscht", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Einträge können nicht in die Kategorie 'Gelöscht' importiert werden.")
        }
        database.withTransaction {
            entries.forEach { imported ->
                val cleanEntry = imported.copy(
                    deletedAt = null,
                    zoneId = TimeZoneUtils.sanitizeZoneId(imported.zoneId)
                )
                val existing = entryDao.getEntryByIdIncludingDeleted(cleanEntry.id)
                if (existing != null) {
                    val mergedCategories = (existing.categories + targetCategory)
                        .filter { it.id != JournalConstants.CATEGORY_DELETED_ID && !it.category.equals("Gelöscht", ignoreCase = true) }
                        .distinctBy { it.category }
                    entryDao.updateWithCategories(cleanEntry, mergedCategories, categoryDao)
                } else {
                    entryDao.insertWithCategories(cleanEntry, listOf(targetCategory), categoryDao)
                }
            }
        }
    }

    suspend fun exportJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val entriesWithCategories = entryDao.getAllEntriesWithCategories()
        val categories = categoryDao.getAllCategoriesList()
        val exportEntries = entriesWithCategories.map {
            JournalEntryExport(
                id = it.entry.id,
                content = it.entry.content,
                start_datetime = it.entry.start_datetime,
                stop_datetime = it.entry.stop_datetime,
                hasImage = it.entry.hasImage,
                checked = it.entry.checked,
                categories = it.categories.map { c -> c.category },
                zoneId = it.entry.zoneId
            )
        }
        val exportData = JournalExportV3(entries = exportEntries, categories = categories)
        val jsonString = exportJson.encodeToString(exportData)

        // Use "wt" (write + truncate) so overwriting an existing longer file never leaves trailing bytes
        val pfd = context.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw IOException("Die Zieldatei konnte nicht zum Schreiben geöffnet werden.")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        }
    }

    suspend fun insertCategory(category: Category) {
        if (!JournalConstants.isVirtualDeletedCategory(category)) {
            val safeOrderIndex = category.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)
            categoryDao.insertCategory(category.copy(orderIndex = safeOrderIndex))
        }
    }

    suspend fun updateCategory(category: Category) {
        if (!JournalConstants.isVirtualDeletedCategory(category)) {
            val safeOrderIndex = category.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)
            categoryDao.updateCategory(category.copy(orderIndex = safeOrderIndex))
        }
    }

    suspend fun updateCategories(categories: List<Category>) {
        val filtered = categories
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
            .map { it.copy(orderIndex = it.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)) }
        categoryDao.updateCategories(filtered)
    }

    suspend fun insert(entry: JournalEntry, categories: List<Category>) {
        val filtered = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
        entryDao.insertWithCategories(entry, filtered, categoryDao)
    }

    suspend fun update(entry: JournalEntry, categories: List<Category>) {
        val filtered = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
        entryDao.updateWithCategories(entry, filtered, categoryDao)
    }

    suspend fun updateEntryChecked(entryId: UUID, checked: Boolean) = entryDao.updateChecked(entryId, checked)

    suspend fun updateEntriesZoneId(ids: Set<UUID>, zoneId: String?) {
        if (ids.isEmpty()) return
        val sanitized = TimeZoneUtils.sanitizeZoneId(zoneId)
        database.withTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.updateZoneIdByIds(chunk, sanitized)
            }
        }
    }

    suspend fun deleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        database.withTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.softDeleteByIds(chunk, timestamp)
            }
        }
    }

    suspend fun hardDeleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.hardDeleteByIds(chunk)
            }
        }
    }

    suspend fun restoreLatestDeletedEntry() {
        val latestDeleted = entryDao.getLatestDeletedEntry()
        if (latestDeleted != null) {
            entryDao.restoreEntry(latestDeleted.entry.id)
        }
    }

    suspend fun restoreEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.restoreByIds(chunk)
            }
        }
    }

    fun getDeletedEntriesCount(): Flow<Int> = entryDao.getDeletedEntriesCount()

    suspend fun deleteCategory(categoryId: Int) {
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) categoryDao.deleteCategory(categoryId)
    }

    suspend fun deleteAll() {
        entryDao.softDeleteAll(System.currentTimeMillis())
    }

    // GPS Track Point methods
    suspend fun insertGpsPoint(point: GpsTrackPoint) {
        gpsTrackPointDao.insert(point)
    }

    fun getTrackPointsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<GpsTrackPoint>> {
        return gpsTrackPointDao.getTrackPointsForDay(dayStartMillis, dayEndMillis)
    }

    suspend fun getAllCategoriesList(): List<Category> {
        val dbList = categoryDao.getAllCategoriesList()
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
        return dbList + virtualDeletedCategory
    }

    suspend fun getRealCategoriesList(): List<Category> {
        return categoryDao.getAllCategoriesList()
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
    }

    suspend fun normalizeCategoryOrderIndicesIfNeeded() {
        val dbCategories = getRealCategoriesList()
        if (dbCategories.any { it.orderIndex < 0 || it.orderIndex >= Int.MAX_VALUE - 1 }) {
            val normalized = dbCategories.mapIndexed { index, cat ->
                cat.copy(orderIndex = index)
            }
            categoryDao.updateCategories(normalized)
        }
    }
}