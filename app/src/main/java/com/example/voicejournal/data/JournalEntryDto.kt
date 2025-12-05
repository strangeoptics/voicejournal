package com.example.voicejournal.data

import kotlinx.serialization.Serializable

// DTO für einen einzelnen Journaleintrag
@Serializable
data class JournalEntryDto(
    val id: Int,
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