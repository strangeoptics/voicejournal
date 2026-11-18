package com.example.voicejournal.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.Dispatchers
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
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

internal enum class BackupJsonFormat {
    OBJECT_V3,
    OBJECT_V2,
    ARRAY_V3,
    ARRAY_V2,
    EMPTY_ARRAY
}

class JournalBackupManager(
    private val database: AppDatabase,
    private val entryDao: JournalEntryDao,
    private val categoryDao: CategoryDao,
    context: Context
) {

    private val appContext: Context = context.applicationContext
    private val importJson = Json { ignoreUnknownKeys = true }
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    class FileTooLargeException(message: String) : IllegalArgumentException(message)

    companion object {
        const val MAX_BACKUP_IMPORT_BYTES = 50L * 1024L * 1024L // 50 MB
        const val MAX_SHARED_IMPORT_BYTES = 10L * 1024L * 1024L // 10 MB

        private val LEGACY_LINE_PATTERN: Pattern =
            Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\] (.*)")
        private val LEGACY_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

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
                    throw FileTooLargeException(
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
        val content = appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
            readBoundedUtf8(inputStream)
        } ?: throw IOException("Die ausgewählte Datei konnte nicht geöffnet werden.")

        val trimmed = content.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Die ausgewählte Datei ist leer.")
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val root = importJson.parseToJsonElement(trimmed)
                val format = detectBackupJsonFormat(root)
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
        var importedCount = 0
        val defaultZone = ZoneId.systemDefault()
        content.lines().forEach { line ->
            val matcher = LEGACY_LINE_PATTERN.matcher(line)
            if (matcher.matches()) {
                val dateTimeString = matcher.group(1) ?: return@forEach
                val contentString = matcher.group(2) ?: ""
                val startDatetime = LocalDateTime.parse(dateTimeString, LEGACY_DATE_FORMATTER)
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
        if (JournalConstants.isVirtualDeletedCategory(targetCategory)) {
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
                        .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
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
        val pfd = appContext.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw IOException("Die Zieldatei konnte nicht zum Schreiben geöffnet werden.")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        }
    }
}
