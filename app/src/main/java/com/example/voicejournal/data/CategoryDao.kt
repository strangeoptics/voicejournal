package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id IN (:categoryIds)")
    suspend fun getCategoriesByIds(categoryIds: List<Int>): List<Category>
}