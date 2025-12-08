package com.example.voicejournal.data

import kotlinx.serialization.Serializable
import java.util.UUID

// DTO für einen einzelnen Journaleintrag
@Serializable
data class JournalEntryDto(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val content: String,
    val start_datetime: Long,
    var stop_datetime: Long? = null,
    val hasImage: Boolean,
    val categoryIds: List<Int> // Hier verwenden wir eine Liste von IDs (Integer)
)

@Serializable
data class CreateJournalEntryDto(
    val content: String,
    val start_datetime: Long,
    var stop_datetime: Long? = null,
    val hasImage: Boolean,
    val categoryIds: List<Int>
)