package com.example.voicejournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val content: String,
    val start_datetime: Long,
    var stop_datetime: Long? = null,
    val hasImage: Boolean = false
)