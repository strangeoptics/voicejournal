package com.example.voicejournal.data

import kotlinx.serialization.Serializable

@Serializable
data class JournalExport(
    val version: Int = 2,
    val entries: List<JournalEntry>,
    val categories: List<Category>
)
