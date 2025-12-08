package com.example.voicejournal.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "journal_entry_category_cross_ref",
    primaryKeys = ["entryId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class JournalEntryCategoryCrossRef(
    @Serializable(with = UuidSerializer::class)
    val entryId: UUID,
    @ColumnInfo(index = true)
    val categoryId: Int
)