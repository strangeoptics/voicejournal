package com.example.voicejournal.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class EntryWithCategories(
    @Embedded val entry: JournalEntry,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = JournalEntryCategoryCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "categoryId"
        )
    )
    val categories: List<Category>
)