package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllEntriesFlow(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries")
    suspend fun getAllEntries(): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getEntriesSince(since: Long): Flow<List<JournalEntry>>

    @Insert
    suspend fun insert(entry: JournalEntry)

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = (SELECT id FROM journal_entries WHERE title = :category ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLatestByCategory(category: String)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()

    // Category DAO methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<Category>>

    @Query("UPDATE categories SET aliases = :aliases WHERE category = :categoryName")
    suspend fun updateAliasesForCategory(categoryName: String, aliases: String)

    @Query("DELETE FROM categories WHERE category = :categoryName")
    suspend fun deleteCategory(categoryName: String)
}