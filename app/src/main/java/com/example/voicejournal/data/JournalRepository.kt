package com.example.voicejournal.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

class JournalRepository(
    private val dao: JournalEntryDao,
    private val gpsTrackPointDao: GpsTrackPointDao,
    private val context: Context
) {

    val allCategories = dao.getAllCategories()

    fun getAllEntriesFlow() = dao.getAllEntriesFlow()

    suspend fun importJournal(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val content = reader.readText()
                try {
                    // Try new format first
                    val journalExport = Json.decodeFromString<JournalExport>(content)
                    journalExport.entries.forEach { dao.insert(it) }
                    journalExport.categories.forEach { dao.insertCategory(it) }
                } catch (e1: Exception) {
                    try {
                        // Try old JSON format
                        val entries = Json.decodeFromString<List<JournalEntry>>(content)
                        entries.forEach { dao.insert(it) }
                    } catch (e2: Exception) {
                        // Fallback to legacy text format
                        parseLegacyFormat(content)
                    }
                }
            }
        }
    }

    private suspend fun parseLegacyFormat(content: String) {
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

    suspend fun exportJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val entries = dao.getAllEntries()
        val categories = dao.getAllCategoriesList()
        val exportData = JournalExport(entries, categories)
        val jsonString = Json.encodeToString(exportData)
        context.contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                fos.write(jsonString.toByteArray())
            }
        }
    }

    fun getEntriesSince(timestamp: Long) = dao.getEntriesSince(timestamp)

    suspend fun insertCategory(category: Category) = dao.insertCategory(category)

    suspend fun updateCategories(categories: List<Category>) = dao.updateCategories(categories)

    suspend fun insert(entry: JournalEntry) = dao.insert(entry)

    suspend fun update(entry: JournalEntry) = dao.update(entry)

    suspend fun delete(entry: JournalEntry) = dao.delete(entry)

    suspend fun updateAliasesForCategory(category: String, aliases: List<String>) {
        val aliasesString = aliases.joinToString(",")
        dao.updateAliasesForCategory(category, aliasesString)
    }

    suspend fun deleteCategory(category: String) = dao.deleteCategory(category)

    suspend fun deleteAll() = dao.deleteAll()

    // GPS Track Point methods
    suspend fun insertGpsPoint(point: GpsTrackPoint) {
        gpsTrackPointDao.insert(point)
    }

    fun getTrackPointsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<GpsTrackPoint>> {
        return gpsTrackPointDao.getTrackPointsForDay(dayStartMillis, dayEndMillis)
    }
}