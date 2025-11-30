package com.example.voicejournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    val category: String, // The canonical category name
    val aliases: String,     // A comma-separated list of aliases
    val showAll: Boolean = false,
    val orderIndex: Int = 0
)
