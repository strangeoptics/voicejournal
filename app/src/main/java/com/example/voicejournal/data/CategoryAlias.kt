package com.example.voicejournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_aliases")
data class CategoryAlias(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // The canonical category name
    val alias: String     // An alias for the category
)
