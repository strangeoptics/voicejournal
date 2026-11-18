package com.example.voicejournal

import com.example.voicejournal.data.Category
import com.example.voicejournal.data.JournalConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
