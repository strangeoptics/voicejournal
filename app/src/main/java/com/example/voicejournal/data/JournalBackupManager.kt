package com.example.voicejournal.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.voicejournal.util.DateFormatters
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PushbackInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.regex.Pattern

internal enum class BackupJsonFormat {
    OBJECT_V3,
    OBJECT_V2,
    ARRAY_V3,
    ARRAY_V2,
    EMPTY_ARRAY
}

internal sealed interface ParsedEntryId {
    data class UuidValue(val uuid: UUID) : ParsedEntryId
    data object LegacyNumeric : ParsedEntryId
}

internal object FlexibleEntryIdSerializer : KSerializer<ParsedEntryId?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleEntryId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ParsedEntryId?) {
        when (value) {
            is ParsedEntryId.UuidValue -> encoder.encodeString(value.uuid.toString())
            ParsedEntryId.LegacyNumeric, null -> encoder.encodeString("")
        }
    }

    override fun deserialize(decoder: Decoder): ParsedEntryId? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return ParsedEntryId.UuidValue(parseUuidOrThrow(decoder.decodeString()))
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (element.isString) {
                    ParsedEntryId.UuidValue(parseUuidOrThrow(element.content))
                } else if (element.longOrNull != null) {
                    ParsedEntryId.LegacyNumeric
                } else {
                    throw SerializationException("Ungültiges Eintrags-ID-Format: $element")
                }
            }
            else -> throw SerializationException("Ungültiger JSON-Typ für Eintrags-ID.")
        }
    }

    private fun parseUuidOrThrow(raw: String): UUID {
        return try {
            UUID.fromString(raw)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Ungültige UUID: $raw", e)
        }
    }
}

@Serializable
internal data class StreamingImportEntry(
    @Serializable(with = FlexibleEntryIdSerializer::class)
    val id: ParsedEntryId? = null,
    val title: String? = null,
    val content: String? = null,
    @SerialName("start_datetime")
    val startDateTime: Long? = null,
    val timestamp: Long? = null,
    @SerialName("stop_datetime")
    val stopDateTime: Long? = null,
    val hasImage: Boolean = false,
    val checked: Boolean = false,
    val deletedAt: Long? = null,
    val categories: List<String>? = null,
    val zoneId: String? = null,
)

@Serializable
internal data class StreamingImportEnvelope(
    val version: Int? = null,
    val entries: List<StreamingImportEntry>? = null,
    val categories: List<Category> = emptyList()
)

class JournalBackupManager internal constructor(
    private val entryDao: JournalEntryDao,
    private val categoryDao: CategoryDao,
    context: Context?,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit
) {

    constructor(
        database: AppDatabase,
        entryDao: JournalEntryDao,
        categoryDao: CategoryDao,
        context: Context
    ) : this(
        entryDao = entryDao,
        categoryDao = categoryDao,
        context = context.applicationContext,
        runInTransaction = { block -> database.withTransaction { block() } }
    )

    private val appContext: Context? = context?.applicationContext
    private val importJson = Json { ignoreUnknownKeys = true }
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    class FileTooLargeException(message: String) : IllegalArgumentException(message)

    internal class BoundedInputStream(
        delegate: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(delegate) {
        private var totalRead = 0L

        override fun read(): Int {
            val byteVal = super.read()
            if (byteVal != -1) {
                incrementAndCheck(1L)
            }
            return byteVal
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = super.read(b, off, len)
            if (bytesRead > 0) {
                incrementAndCheck(bytesRead.toLong())
            }
            return bytesRead
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            if (skipped > 0) {
                incrementAndCheck(skipped)
            }
            return skipped
        }

        private fun incrementAndCheck(bytes: Long) {
            totalRead += bytes
            if (totalRead > maxBytes) {
                val mbLimit = (maxBytes / (1024L * 1024L)).coerceAtLeast(1L)
                throw FileTooLargeException(
                    "Die ausgewählte Datei ist zu groß (maximal $mbLimit MB erlaubt)."
                )
            }
        }
    }

    companion object {
        const val MAX_BACKUP_IMPORT_BYTES = 50L * 1024L * 1024L // 50 MB
        const val MAX_SHARED_IMPORT_BYTES = 10L * 1024L * 1024L // 10 MB
        internal const val IMPORT_BATCH_SIZE = 500
        private const val STREAM_PEEK_BYTES = 64

        private val LEGACY_LINE_PATTERN: Pattern =
            Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})] (.*)")
        private val LEGACY_HEADER_PREFIX_PATTERN: Pattern =
            Pattern.compile("^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]")

        internal fun readBoundedUtf8(
            inputStream: InputStream,
            maxBytes: Long = MAX_BACKUP_IMPORT_BYTES
        ): String {
            val bounded = BoundedInputStream(inputStream, maxBytes)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = bounded.read(buffer)
                if (bytesRead == -1) break
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

    private enum class StreamTopLevelKind {
        JSON_OBJECT,
        JSON_ARRAY,
        LEGACY_TEXT
    }

    private suspend fun loadCategoryCache(): MutableMap<String, Category> {
        return categoryDao.getAllCategoriesList()
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
            .associateByTo(LinkedHashMap()) { it.category }
    }

    private fun inspectAndPrepareStream(
        rawStream: InputStream,
        maxBytes: Long
    ): Pair<StreamTopLevelKind, PushbackInputStream> {
        val bounded = BoundedInputStream(rawStream, maxBytes)
        val pushback = PushbackInputStream(bounded, STREAM_PEEK_BYTES)

        val peekBuffer = ByteArray(STREAM_PEEK_BYTES)
        var peekLen = 0
        while (peekLen < STREAM_PEEK_BYTES) {
            val read = pushback.read(peekBuffer, peekLen, STREAM_PEEK_BYTES - peekLen)
            if (read == -1) break
            peekLen += read
        }

        // Strip UTF-8 BOM (EF BB BF) if present at start of stream
        val startOffset = if (
            peekLen >= 3 &&
            peekBuffer[0] == 0xEF.toByte() &&
            peekBuffer[1] == 0xBB.toByte() &&
            peekBuffer[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }

        var firstNonWsIndex = startOffset
        while (firstNonWsIndex < peekLen && isAsciiWhitespace(peekBuffer[firstNonWsIndex])) {
            firstNonWsIndex++
        }

        if (firstNonWsIndex >= peekLen) {
            // Stream might still have non-whitespace bytes after 64 leading whitespace bytes
            var b: Int
            do {
                b = pushback.read()
            } while (b != -1 && isAsciiWhitespace(b.toByte()))

            if (b == -1) {
                throw IllegalArgumentException("Die ausgewählte Datei ist leer.")
            }

            // Re-peek from first non-whitespace byte
            val tailPeek = ByteArray(STREAM_PEEK_BYTES)
            tailPeek[0] = b.toByte()
            var tailLen = 1
            while (tailLen < STREAM_PEEK_BYTES) {
                val r = pushback.read(tailPeek, tailLen, STREAM_PEEK_BYTES - tailLen)
                if (r == -1) break
                tailLen += r
            }
            val preview = String(tailPeek, 0, tailLen, Charsets.UTF_8)
            pushback.unread(tailPeek, 0, tailLen)
            return Pair(classifyPreview(preview), pushback)
        }

        val remainingLen = peekLen - firstNonWsIndex
        val preview = String(peekBuffer, firstNonWsIndex, remainingLen, Charsets.UTF_8)
        pushback.unread(peekBuffer, firstNonWsIndex, remainingLen)
        return Pair(classifyPreview(preview), pushback)
    }

    private fun classifyPreview(preview: String): StreamTopLevelKind {
        return when {
            LEGACY_HEADER_PREFIX_PATTERN.matcher(preview).find() -> StreamTopLevelKind.LEGACY_TEXT
            preview.startsWith("{") -> StreamTopLevelKind.JSON_OBJECT
            preview.startsWith("[") -> StreamTopLevelKind.JSON_ARRAY
            else -> StreamTopLevelKind.LEGACY_TEXT
        }
    }

    private fun isAsciiWhitespace(b: Byte): Boolean {
        val ch = b.toInt() and 0xFF
        return ch == ' '.code || ch == '\t'.code || ch == '\n'.code || ch == '\r'.code
    }

    suspend fun importJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val ctx = requireNotNull(appContext) { "Application context required for URI import" }
        ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
            importFromStream(inputStream, MAX_BACKUP_IMPORT_BYTES)
        } ?: throw IOException("Die ausgewählte Datei konnte nicht geöffnet werden.")
    }

    internal suspend fun importFromStream(
        rawInputStream: InputStream,
        maxBytes: Long = MAX_BACKUP_IMPORT_BYTES
    ) {
        val (kind, stream) = inspectAndPrepareStream(rawInputStream, maxBytes)
        when (kind) {
            StreamTopLevelKind.JSON_OBJECT -> importJsonObjectStream(stream)
            StreamTopLevelKind.JSON_ARRAY -> importJsonArrayStream(stream)
            StreamTopLevelKind.LEGACY_TEXT -> importLegacyTextStream(stream)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun importJsonObjectStream(stream: InputStream) {
        val envelope = try {
            importJson.decodeFromStream<StreamingImportEnvelope>(stream)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Ungültiges JSON-Backup-Format: ${e.message}", e)
        }

        val format = when {
            envelope.version != null && envelope.version >= 3 -> BackupJsonFormat.OBJECT_V3
            envelope.version != null -> BackupJsonFormat.OBJECT_V2
            envelope.entries != null -> {
                val firstEntry = envelope.entries.firstOrNull()
                if (firstEntry?.startDateTime != null) {
                    BackupJsonFormat.OBJECT_V3
                } else {
                    BackupJsonFormat.OBJECT_V2
                }
            }
            else -> throw IllegalArgumentException("Unbekanntes JSON-Backup-Format.")
        }

        val rawEntries = envelope.entries
            ?: throw IllegalArgumentException("Ungültiges 'entries'-Feld im JSON-Backup.")

        runInTransaction {
            val categoryCache = loadCategoryCache()
            entryDao.resolveCategories(envelope.categories, categoryDao, categoryCache)

            when (format) {
                BackupJsonFormat.OBJECT_V3 -> {
                    rawEntries.chunked(IMPORT_BATCH_SIZE).forEach { chunk ->
                        val entriesBatch = ArrayList<JournalEntry>(chunk.size)
                        val crossRefsBatch = ArrayList<JournalEntryCategoryCrossRef>(chunk.size * 2)

                        for (item in chunk) {
                            val uuid = (item.id as? ParsedEntryId.UuidValue)?.uuid
                                ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (UUID fehlt).")
                            val content = item.content
                                ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (content fehlt).")
                            val startMillis = item.startDateTime
                                ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (start_datetime fehlt).")

                            val entry = JournalEntry(
                                id = uuid,
                                content = content,
                                start_datetime = startMillis,
                                stop_datetime = item.stopDateTime,
                                hasImage = item.hasImage,
                                checked = item.checked,
                                deletedAt = null,
                                zoneId = TimeZoneUtils.sanitizeZoneId(item.zoneId)
                            )
                            entriesBatch.add(entry)

                            val categoryModels = item.categories.orEmpty()
                                .filter { it.isNotBlank() && !JournalConstants.isVirtualDeletedName(it) }
                                .map { name -> Category(category = name, aliases = "") }
                            val resolvedCats = entryDao.resolveCategories(categoryModels, categoryDao, categoryCache)
                            for (cat in resolvedCats) {
                                crossRefsBatch.add(JournalEntryCategoryCrossRef(entry.id, cat.id))
                            }
                        }

                        entryDao.upsertEntries(entriesBatch)
                        if (crossRefsBatch.isNotEmpty()) {
                            entryDao.insertJournalEntryCategoryCrossRefs(crossRefsBatch)
                        }
                    }
                }
                BackupJsonFormat.OBJECT_V2 -> {
                    rawEntries.chunked(IMPORT_BATCH_SIZE).forEach { chunk ->
                        insertV2Chunk(chunk, categoryCache)
                    }
                }
                else -> Unit
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun importJsonArrayStream(stream: PushbackInputStream) {
        if (isEmptyJsonArray(stream)) {
            return
        }

        try {
            runInTransaction {
                val categoryCache = loadCategoryCache()
                var detectedArrayFormat: BackupJsonFormat? = null
                var fallbackJournalCategory: Category? = null

                importJson.decodeToSequence<StreamingImportEntry>(
                    stream = stream,
                    format = DecodeSequenceMode.ARRAY_WRAPPED
                ).chunked(IMPORT_BATCH_SIZE).forEach { chunk ->
                    if (chunk.isEmpty()) return@forEach

                    val format = detectedArrayFormat ?: run {
                        val first = chunk.first()
                        val detected = when {
                            first.startDateTime != null -> BackupJsonFormat.ARRAY_V3
                            first.timestamp != null -> BackupJsonFormat.ARRAY_V2
                            else -> throw IllegalArgumentException("Unbekanntes JSON-Eintragsformat.")
                        }
                        detectedArrayFormat = detected
                        detected
                    }

                    when (format) {
                        BackupJsonFormat.ARRAY_V3 -> {
                            val fallbackCat = fallbackJournalCategory ?: run {
                                val resolved = entryDao.resolveCategories(
                                    listOf(Category(category = "journal", aliases = "")),
                                    categoryDao,
                                    categoryCache
                                ).first()
                                fallbackJournalCategory = resolved
                                resolved
                            }

                            val deduplicatedEntries = LinkedHashMap<UUID, JournalEntry>(chunk.size)
                            for (item in chunk) {
                                if (item.id is ParsedEntryId.LegacyNumeric) {
                                    throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (numerische ID in V3-Array).")
                                }
                                val uuid = (item.id as? ParsedEntryId.UuidValue)?.uuid ?: UUID.randomUUID()
                                val content = item.content
                                    ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (content fehlt).")
                                val startMillis = item.startDateTime
                                    ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (start_datetime fehlt).")

                                deduplicatedEntries[uuid] = JournalEntry(
                                    id = uuid,
                                    content = content,
                                    start_datetime = startMillis,
                                    stop_datetime = item.stopDateTime,
                                    hasImage = item.hasImage,
                                    checked = item.checked,
                                    deletedAt = null,
                                    zoneId = TimeZoneUtils.sanitizeZoneId(item.zoneId)
                                )
                            }

                            val cleanEntries = deduplicatedEntries.values.toList()
                            val existingById = entryDao.getEntriesByIds(deduplicatedEntries.keys)
                                .associateBy { it.entry.id }

                            entryDao.upsertEntries(cleanEntries)

                            val idsWithExistingCategories = ArrayList<UUID>()
                            val crossRefsBatch = ArrayList<JournalEntryCategoryCrossRef>(cleanEntries.size * 2)

                            for (entry in cleanEntries) {
                                val existing = existingById[entry.id]
                                if (existing != null && existing.categories.isNotEmpty()) {
                                    idsWithExistingCategories.add(entry.id)
                                    val resolvedCats = entryDao.resolveCategories(
                                        existing.categories,
                                        categoryDao,
                                        categoryCache
                                    )
                                    for (cat in resolvedCats) {
                                        crossRefsBatch.add(JournalEntryCategoryCrossRef(entry.id, cat.id))
                                    }
                                } else {
                                    crossRefsBatch.add(JournalEntryCategoryCrossRef(entry.id, fallbackCat.id))
                                }
                            }

                            if (idsWithExistingCategories.isNotEmpty()) {
                                entryDao.deleteCrossRefsForEntries(idsWithExistingCategories)
                            }
                            if (crossRefsBatch.isNotEmpty()) {
                                entryDao.insertJournalEntryCategoryCrossRefs(crossRefsBatch)
                            }
                        }
                        BackupJsonFormat.ARRAY_V2 -> {
                            insertV2Chunk(chunk, categoryCache)
                        }
                        else -> Unit
                    }
                }
            }
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Ungültiges JSON-Backup-Format: ${e.message}", e)
        }
    }

    private fun isEmptyJsonArray(stream: PushbackInputStream): Boolean {
        val openBracket = stream.read()
        if (openBracket != '['.code) {
            if (openBracket != -1) stream.unread(openBracket)
            return false
        }
        var next = stream.read()
        while (next != -1 && isAsciiWhitespace(next.toByte())) {
            next = stream.read()
        }
        if (next == ']'.code) {
            // Ensure no trailing garbage after ']'
            var trailing = stream.read()
            while (trailing != -1 && isAsciiWhitespace(trailing.toByte())) {
                trailing = stream.read()
            }
            if (trailing != -1) {
                throw IllegalArgumentException("Ungültiges JSON-Backup-Format: unerwartete Zeichen nach '[]'.")
            }
            return true
        }
        if (next != -1) {
            stream.unread(next)
        }
        stream.unread(openBracket)
        return false
    }

    private suspend fun insertV2Chunk(
        chunk: List<StreamingImportEntry>,
        categoryCache: MutableMap<String, Category>
    ) {
        val entriesBatch = ArrayList<JournalEntry>(chunk.size)
        val crossRefsBatch = ArrayList<JournalEntryCategoryCrossRef>(chunk.size)

        for (v2Entry in chunk) {
            val title = v2Entry.title
                ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (title fehlt).")
            val content = v2Entry.content
                ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (content fehlt).")
            val timestamp = v2Entry.timestamp
                ?: throw IllegalArgumentException("Ungültiges JSON-Eintragsformat (timestamp fehlt).")

            val newEntry = JournalEntry(
                content = content,
                start_datetime = timestamp,
                hasImage = v2Entry.hasImage
            )
            entriesBatch.add(newEntry)

            val categoryName = title.ifBlank { "journal" }
            val resolvedCats = entryDao.resolveCategories(
                listOf(Category(category = categoryName, aliases = "")),
                categoryDao,
                categoryCache
            )
            for (cat in resolvedCats) {
                crossRefsBatch.add(JournalEntryCategoryCrossRef(newEntry.id, cat.id))
            }
        }

        entryDao.upsertEntries(entriesBatch)
        if (crossRefsBatch.isNotEmpty()) {
            entryDao.insertJournalEntryCategoryCrossRefs(crossRefsBatch)
        }
    }

    private suspend fun importLegacyTextStream(stream: InputStream) {
        var importedCount = 0
        val defaultZone = ZoneId.systemDefault()

        runInTransaction {
            val categoryCache = loadCategoryCache()
            var journalCategory: Category? = null

            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                lines.mapNotNull { line ->
                    val matcher = LEGACY_LINE_PATTERN.matcher(line)
                    if (!matcher.matches()) return@mapNotNull null
                    val dateTimeString = matcher.group(1) ?: return@mapNotNull null
                    val contentString = matcher.group(2) ?: ""
                    val startDatetime = LocalDateTime.parse(dateTimeString, DateFormatters.LEGACY_TEXT_EXPORT)
                        .atZone(defaultZone)
                        .toInstant()
                        .toEpochMilli()
                    JournalEntry(
                        content = contentString,
                        start_datetime = startDatetime,
                        zoneId = defaultZone.id
                    )
                }.chunked(IMPORT_BATCH_SIZE).forEach { entryChunk ->
                    val targetCat = journalCategory ?: run {
                        val resolved = entryDao.resolveCategories(
                            listOf(Category(category = "journal", aliases = "")),
                            categoryDao,
                            categoryCache
                        ).first()
                        journalCategory = resolved
                        resolved
                    }

                    entryDao.upsertEntries(entryChunk)
                    entryDao.insertJournalEntryCategoryCrossRefs(
                        entryChunk.map { entry -> JournalEntryCategoryCrossRef(entry.id, targetCat.id) }
                    )
                    importedCount += entryChunk.size
                }
            }
        }

        if (importedCount == 0) {
            throw IllegalArgumentException("Keine gültigen Tagebucheinträge in der Datei gefunden.")
        }
    }

    suspend fun importEntriesToCategory(entries: List<JournalEntry>, targetCategory: Category) = withContext(Dispatchers.IO) {
        if (JournalConstants.isVirtualDeletedCategory(targetCategory)) {
            throw IllegalArgumentException("Einträge können nicht in die Kategorie 'Gelöscht' importiert werden.")
        }
        if (entries.isEmpty()) return@withContext

        runInTransaction {
            val categoryCache = loadCategoryCache()
            val resolvedTarget = entryDao.resolveCategories(
                listOf(targetCategory),
                categoryDao,
                categoryCache
            ).firstOrNull() ?: return@runInTransaction

            entries.chunked(IMPORT_BATCH_SIZE).forEach { chunk ->
                val deduplicated = LinkedHashMap<UUID, JournalEntry>(chunk.size)
                for (imported in chunk) {
                    deduplicated[imported.id] = imported.copy(
                        deletedAt = null,
                        zoneId = TimeZoneUtils.sanitizeZoneId(imported.zoneId)
                    )
                }

                val cleanEntries = deduplicated.values.toList()
                val existingById = entryDao.getEntriesByIds(deduplicated.keys)
                    .associateBy { it.entry.id }

                entryDao.upsertEntries(cleanEntries)

                if (existingById.isNotEmpty()) {
                    entryDao.deleteCrossRefsForEntries(existingById.keys)
                }

                val crossRefsBatch = ArrayList<JournalEntryCategoryCrossRef>(cleanEntries.size * 2)
                for (entry in cleanEntries) {
                    val existing = existingById[entry.id]
                    if (existing != null) {
                        val mergedCategories = (existing.categories + resolvedTarget)
                            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
                            .distinctBy { it.category }
                        val resolvedMerged = entryDao.resolveCategories(
                            mergedCategories,
                            categoryDao,
                            categoryCache
                        )
                        for (cat in resolvedMerged) {
                            crossRefsBatch.add(JournalEntryCategoryCrossRef(entry.id, cat.id))
                        }
                    } else {
                        crossRefsBatch.add(JournalEntryCategoryCrossRef(entry.id, resolvedTarget.id))
                    }
                }

                if (crossRefsBatch.isNotEmpty()) {
                    entryDao.insertJournalEntryCategoryCrossRefs(crossRefsBatch)
                }
            }
        }
    }

    suspend fun exportJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val ctx = requireNotNull(appContext) { "Application context required for URI export" }
        // Use "wt" (write + truncate) so overwriting an existing longer file never leaves trailing bytes
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw IOException("Die Zieldatei konnte nicht zum Schreiben geöffnet werden.")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { fos ->
                exportToStream(fos)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun exportToStream(outputStream: OutputStream) {
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
        BufferedOutputStream(outputStream).let { bos ->
            exportJson.encodeToStream(exportData, bos)
            bos.flush()
        }
    }
}
