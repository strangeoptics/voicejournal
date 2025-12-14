package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface JournalEntryDao {
    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY start_datetime DESC")
    fun getEntriesWithCategories(): Flow<List<EntryWithCategories>>

    @Transaction
    @Query("SELECT * FROM journal_entries")
    suspend fun getAllEntriesWithCategories(): List<EntryWithCategories>

    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY start_datetime DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginatedEntriesWithCategories(limit: Int, offset: Int): List<EntryWithCategories>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE start_datetime >= :since ORDER BY start_datetime DESC")
    fun getEntriesWithCategoriesSince(since: Long): Flow<List<EntryWithCategories>>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id IN (SELECT entryId FROM journal_entry_category_cross_ref WHERE categoryId = :categoryId) ORDER BY start_datetime DESC")
    suspend fun getEntriesForCategory(categoryId: Int): List<EntryWithCategories>
    
    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id IN (SELECT entryId FROM journal_entry_category_cross_ref WHERE categoryId = :categoryId) ORDER BY start_datetime DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaginatedEntriesForCategory(categoryId: Int, limit: Int, offset: Int): List<EntryWithCategories>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :entryId")
    suspend fun getEntryById(entryId: UUID): EntryWithCategories?
    
    @Transaction
    @Query("SELECT * FROM journal_entries WHERE start_datetime >= :startTime AND start_datetime < :endTime AND stop_datetime IS NOT NULL ORDER BY start_datetime ASC")
    fun getEntriesForCalendar(startTime: Long, endTime: Long): Flow<List<EntryWithCategories>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntry)

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntryCategoryCrossRef(crossRef: JournalEntryCategoryCrossRef)

    @Query("DELETE FROM journal_entry_category_cross_ref WHERE entryId = :entryId")
    suspend fun deleteCrossRefsForEntry(entryId: UUID)

    @Transaction
    suspend fun insertWithCategories(entry: JournalEntry, categories: List<Category>, categoryDao: CategoryDao) {
        insert(entry)
        categories.forEach { category ->
            var cat = categoryDao.getCategoryByName(category.category)
            if (cat == null) {
                val newId = categoryDao.insertCategory(category)
                cat = category.copy(id = newId.toInt())
            }
            insertJournalEntryCategoryCrossRef(
                JournalEntryCategoryCrossRef(entry.id, cat.id)
            )
        }
    }

    @Transaction
    suspend fun updateWithCategories(entry: JournalEntry, categories: List<Category>, categoryDao: CategoryDao) {
        update(entry)
        deleteCrossRefsForEntry(entry.id)
        categories.forEach { category ->
             var cat = categoryDao.getCategoryByName(category.category)
            if (cat == null) {
                val newId = categoryDao.insertCategory(category)
                cat = category.copy(id = newId.toInt())
            }
            insertJournalEntryCategoryCrossRef(
                JournalEntryCategoryCrossRef(entry.id, cat.id)
            )
        }
    }

    @Query("SELECT MAX(je.start_datetime) FROM journal_entries AS je INNER JOIN journal_entry_category_cross_ref AS jecr ON je.id = jecr.entryId WHERE jecr.categoryId = :categoryId")
    suspend fun getLatestEntryDatetimeForCategory(categoryId: Int): Long?

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE start_datetime >= :startDateMillis AND start_datetime < :endDateMillis AND id IN (SELECT entryId FROM journal_entry_category_cross_ref WHERE categoryId = :categoryId) ORDER BY start_datetime DESC")
    fun getEntriesWithCategoriesInDateRangeForCategory(categoryId: Int, startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE start_datetime >= :startDateMillis AND start_datetime < :endDateMillis ORDER BY start_datetime DESC")
    fun getEntriesWithCategoriesInDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>>
}
