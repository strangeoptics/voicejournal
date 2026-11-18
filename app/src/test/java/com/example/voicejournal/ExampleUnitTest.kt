package com.example.voicejournal

import com.example.voicejournal.data.BackupJsonFormat
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {

    private val sampleCategories = listOf(
        Category(id = 1, category = "journal", aliases = "journal,tagebuch", orderIndex = 0),
        Category(id = 2, category = "todo", aliases = "todo,to-do,todoo", orderIndex = 1),
        Category(id = 3, category = "kaufen", aliases = "kaufen,einkaufen", orderIndex = 2),
        Category(id = 4, category = "eloisa", aliases = "eloisa,luisa", showAll = true, orderIndex = 3),
        Category(id = -1, category = "Gelöscht", aliases = "", orderIndex = Int.MAX_VALUE)
    )

    @Test
    fun smartCategoryParsing_matchesCategoryNameAndStripsPrefix() {
        val (cat, content) = MainViewModel.parseRecognizedTextForCategory(
            text = "kaufen Milch und Brot",
            categories = sampleCategories,
            currentCategoryName = "journal"
        )
        assertEquals("kaufen", cat.category)
        assertEquals("Milch und Brot", content)
    }

    @Test
    fun smartCategoryParsing_matchesAliasCaseInsensitivelyWithPunctuation() {
        val (cat, content) = MainViewModel.parseRecognizedTextForCategory(
            text = "Luisa: heute hat sie gelacht",
            categories = sampleCategories,
            currentCategoryName = "journal"
        )
        assertEquals("eloisa", cat.category)
        assertEquals("Heute hat sie gelacht", content)
    }

    @Test
    fun smartCategoryParsing_preservesLuisaInsideNormalTextWithoutUnwantedReplacement() {
        val (cat, content) = MainViewModel.parseRecognizedTextForCategory(
            text = "Treffen mit Luisa im Park",
            categories = sampleCategories,
            currentCategoryName = "journal"
        )
        assertEquals("journal", cat.category)
        assertEquals("Treffen mit Luisa im Park", content)
    }

    @Test
    fun smartCategoryParsing_neverAssignsVirtualDeletedCategory() {
        val (cat, content) = MainViewModel.parseRecognizedTextForCategory(
            text = "Gelöscht sollte nicht als Kategorie gewählt werden",
            categories = sampleCategories,
            currentCategoryName = "Gelöscht"
        )
        assertTrue(JournalConstants.isVirtualDeletedName("  gelöscht "))
        assertFalse(JournalConstants.isVirtualDeletedCategory(cat))
        assertEquals("journal", cat.category)
        assertEquals("Gelöscht sollte nicht als Kategorie gewählt werden", content)
    }

    @Test
    fun nextSafeOrderIndex_ignoresIntMaxValueFromVirtualDeletedCategory() {
        val nextIndex = JournalConstants.nextSafeOrderIndex(sampleCategories)
        assertEquals(4, nextIndex)

        val corruptedCategories = listOf(
            Category(id = 1, category = "journal", aliases = "", orderIndex = Int.MAX_VALUE),
            Category(id = -1, category = "Gelöscht", aliases = "", orderIndex = Int.MAX_VALUE)
        )
        val clampedNext = JournalConstants.nextSafeOrderIndex(corruptedCategories)
        assertEquals(Int.MAX_VALUE - 1, clampedNext)
    }

    @Test
    fun sampleWaypoints_downsamplesLargeTracksDeterministicallyToNineWaypoints() {
        val shortList = (1..5).toList()
        assertEquals(shortList, MainActivity.sampleWaypoints(shortList))

        val exactNine = (1..9).toList()
        assertEquals(exactNine, MainActivity.sampleWaypoints(exactNine))

        val largeList = (0..80).toList() // 81 intermediate points
        val sampled = MainActivity.sampleWaypoints(largeList)
        assertEquals(9, sampled.size)
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80), sampled)
    }

    @Test
    fun normalizeSearchQueryForSql_handlesGermanUmlautsWildcardsAndUnicodeFallback() {
        assertEquals("äpfel & öl ß", JournalConstants.normalizeSearchQueryForSql("ÄPFEL & ÖL ẞ"))
        assertEquals("100\\%\\_test\\\\ok", JournalConstants.normalizeSearchQueryForSql("100%_Test\\OK"))
        // Non-German Unicode characters return null to trigger exact Unicode in-memory filtering
        assertNull(JournalConstants.normalizeSearchQueryForSql("Café éclair"))
    }

    @Test
    fun detectBackupJsonFormat_distinguishesV2AndV3EvenWithKeywordsInsideContent() {
        val v2ArrayWithKeywordInContent = """
            [
              {
                "id": 1,
                "title": "dev",
                "content": "Refactored \"start_datetime\" and \"version\": 3 in SQLite",
                "timestamp": 1700000000000
              }
            ]
        """.trimIndent()
        assertEquals(
            BackupJsonFormat.ARRAY_V2,
            JournalConstants.detectBackupJsonFormat(Json.parseToJsonElement(v2ArrayWithKeywordInContent))
        )

        val v3Array = """[{"id":"123e4567-e89b-12d3-a456-426614174000","content":"Test","start_datetime":1700000000000}]"""
        assertEquals(
            BackupJsonFormat.ARRAY_V3,
            JournalConstants.detectBackupJsonFormat(Json.parseToJsonElement(v3Array))
        )

        val v2ObjectWithoutExplicitVersion = """{"entries":[{"id":1,"title":"journal","content":"Hi","timestamp":1700000000000}]}"""
        assertEquals(
            BackupJsonFormat.OBJECT_V2,
            JournalConstants.detectBackupJsonFormat(Json.parseToJsonElement(v2ObjectWithoutExplicitVersion))
        )

        val v3Object = """{"version":3,"entries":[],"categories":[]}"""
        assertEquals(
            BackupJsonFormat.OBJECT_V3,
            JournalConstants.detectBackupJsonFormat(Json.parseToJsonElement(v3Object))
        )

        assertEquals(
            BackupJsonFormat.EMPTY_ARRAY,
            JournalConstants.detectBackupJsonFormat(Json.parseToJsonElement("[]"))
        )
    }

    @Test
    fun readBoundedUtf8_readsWithinLimitAndRejectsOversizedStreams() {
        val text = "Tagebucheintrag mit Umlauten: äöüß"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, JournalConstants.readBoundedUtf8(ByteArrayInputStream(bytes), maxBytes = 1024L))

        assertThrows(IllegalArgumentException::class.java) {
            JournalConstants.readBoundedUtf8(ByteArrayInputStream(bytes), maxBytes = (bytes.size - 1).toLong())
        }
    }
}
