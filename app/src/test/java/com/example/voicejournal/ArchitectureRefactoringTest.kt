package com.example.voicejournal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.data.JournalConstants
import com.example.voicejournal.util.ExternalNavigationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ArchitectureRefactoringTest {

    private val categories = listOf(
        Category(id = 10, category = "journal", aliases = "journal,tagebuch", orderIndex = 0),
        Category(id = 20, category = "arbeit", aliases = "job,büro", orderIndex = 1),
        Category(id = 30, category = "kaufen", aliases = "einkaufen", orderIndex = 2),
        Category(
            id = JournalConstants.CATEGORY_DELETED_ID,
            category = JournalConstants.CATEGORY_DELETED_NAME,
            aliases = "",
            orderIndex = Int.MAX_VALUE
        )
    )

    @Test
    fun mergeDictatedText_insertsCleanlyIntoEmptyField() {
        val initial = TextFieldValue("", selection = TextRange(0))
        val merged = EditEntryViewModel.mergeDictatedText(initial, "Erster Satz")
        assertEquals("Erster Satz", merged.text)
        assertEquals(TextRange("Erster Satz".length), merged.selection)
    }

    @Test
    fun mergeDictatedText_appendsOnNewLineWhenCursorAtEnd() {
        val initialText = "Zeile 1"
        val initial = TextFieldValue(initialText, selection = TextRange(initialText.length))
        val merged = EditEntryViewModel.mergeDictatedText(initial, "Zeile 2")
        assertEquals("Zeile 1\nZeile 2", merged.text)
        assertEquals(TextRange("Zeile 1\nZeile 2".length), merged.selection)
    }

    @Test
    fun mergeDictatedText_insertsInlineWhenCursorInMiddle() {
        val initial = TextFieldValue("Hallo Welt", selection = TextRange(6))
        val merged = EditEntryViewModel.mergeDictatedText(initial, "schöne ")
        assertEquals("Hallo schöne Welt", merged.text)
        assertEquals(TextRange(13), merged.selection)
    }

    @Test
    fun mergeDictatedText_replacesForwardAndReverseSelection() {
        val forward = TextFieldValue("Hallo alte Welt", selection = TextRange(6, 10))
        val mergedForward = EditEntryViewModel.mergeDictatedText(forward, "neue")
        assertEquals("Hallo neue Welt", mergedForward.text)
        assertEquals(TextRange(10), mergedForward.selection)

        // Reverse selection (end < start)
        val reverse = TextFieldValue("Hallo alte Welt", selection = TextRange(10, 6))
        val mergedReverse = EditEntryViewModel.mergeDictatedText(reverse, "frische")
        assertEquals("Hallo frische Welt", mergedReverse.text)
        assertEquals(TextRange(13), mergedReverse.selection)
    }

    @Test
    fun mergeDictatedText_ignoresEmptyRecognitionWithoutMutatingSelection() {
        val initial = TextFieldValue("Unverändert", selection = TextRange(2, 5))
        val merged = EditEntryViewModel.mergeDictatedText(initial, "")
        assertEquals(initial, merged)
    }

    @Test
    fun validateCategoryName_allowsRenamingAndCaseChangesOnSameCategoryId() {
        // Rename category 20 ("arbeit") to a brand-new name
        assertNull(
            EditCategoryViewModel.validateCategoryName(
                rawName = "projekte",
                categoryId = 20,
                existingCategories = categories
            )
        )

        // Change capitalization of category 20 ("arbeit" -> "Arbeit")
        assertNull(
            EditCategoryViewModel.validateCategoryName(
                rawName = "Arbeit",
                categoryId = 20,
                existingCategories = categories
            )
        )
    }

    @Test
    fun validateCategoryName_rejectsDuplicatesReservedAndBlankNames() {
        // Renaming category 20 to existing category 10's name (case-insensitive)
        val duplicateRenameError = EditCategoryViewModel.validateCategoryName(
            rawName = "JOURNAL",
            categoryId = 20,
            existingCategories = categories
        )
        assertNotNull(duplicateRenameError)
        assertTrue(duplicateRenameError!!.contains("journal"))

        // Creating new category with duplicate name
        val duplicateNewError = EditCategoryViewModel.validateCategoryName(
            rawName = "kaufen",
            categoryId = EditCategoryViewModel.NEW_CATEGORY_ID,
            existingCategories = categories
        )
        assertNotNull(duplicateNewError)

        // Reserved recycle bin name
        assertNotNull(
            EditCategoryViewModel.validateCategoryName(
                rawName = "  gelöscht ",
                categoryId = 20,
                existingCategories = categories
            )
        )

        // Blank name
        assertNotNull(
            EditCategoryViewModel.validateCategoryName(
                rawName = "   ",
                categoryId = EditCategoryViewModel.NEW_CATEGORY_ID,
                existingCategories = categories
            )
        )

        // Virtual deleted category ID itself
        assertNotNull(
            EditCategoryViewModel.validateCategoryName(
                rawName = "gültig",
                categoryId = JournalConstants.CATEGORY_DELETED_ID,
                existingCategories = categories
            )
        )
    }

    @Test
    fun parseRecognizedTextForCategoryById_preservesCategoryAcrossRenameAndExcludesRecycleBin() {
        // Category 20 was renamed from "todo" to "arbeit" — ID 20 still resolves cleanly
        val (fallbackCat, fallbackContent) = MainViewModel.parseRecognizedTextForCategory(
            text = "Meeting vorbereiten",
            categories = categories,
            currentCategoryId = 20
        )
        assertEquals(20, fallbackCat.id)
        assertEquals("arbeit", fallbackCat.category)
        assertEquals("Meeting vorbereiten", fallbackContent)

        // Alias match switches to category 30 ("kaufen")
        val (matchedCat, strippedContent) = MainViewModel.parseRecognizedTextForCategory(
            text = "einkaufen Hafermilch",
            categories = categories,
            currentCategoryId = 20
        )
        assertEquals(30, matchedCat.id)
        assertEquals("Hafermilch", strippedContent)

        // Recycle bin (-1) as currentCategoryId falls back to first real category (ID 10)
        val (fromTrashCat, _) = MainViewModel.parseRecognizedTextForCategory(
            text = "Notiz aus Papierkorb-Ansicht",
            categories = categories,
            currentCategoryId = JournalConstants.CATEGORY_DELETED_ID
        )
        assertEquals(10, fromTrashCat.id)
    }

    @Test
    fun resolveTargetCategoryIdForEntry_prefersCurrentCategoryIdWhenAssigned() {
        val entryCats = listOf(categories[0], categories[1]) // IDs 10 and 20
        assertEquals(
            20,
            MainViewModel.resolveTargetCategoryIdForEntry(entryCats, currentCategoryId = 20)
        )
        assertEquals(
            10,
            MainViewModel.resolveTargetCategoryIdForEntry(entryCats, currentCategoryId = 30)
        )
        assertNull(
            MainViewModel.resolveTargetCategoryIdForEntry(
                listOf(categories.last()),
                currentCategoryId = 10
            )
        )
    }

    @Test
    fun externalNavigationHelper_buildsUrlsWithDeterministicSampling() {
        assertNull(
            ExternalNavigationHelper.buildGoogleMapsTrackUrl(
                listOf(GpsTrackPoint(timestamp = 1L, latitude = 48.0, longitude = 11.0))
            )
        )

        val twoPointsUrl = ExternalNavigationHelper.buildGoogleMapsTrackUrl(
            listOf(
                GpsTrackPoint(timestamp = 1L, latitude = 48.1, longitude = 11.5),
                GpsTrackPoint(timestamp = 2L, latitude = 48.2, longitude = 11.6)
            )
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=48.1,11.5&destination=48.2,11.6&travelmode=driving",
            twoPointsUrl
        )

        val twentyPoints = (0..19).map { idx ->
            GpsTrackPoint(timestamp = idx.toLong(), latitude = 48.0 + idx * 0.01, longitude = 11.0)
        }
        val manyPointsUrl = ExternalNavigationHelper.buildGoogleMapsTrackUrl(twentyPoints)
        assertNotNull(manyPointsUrl)
        assertTrue(manyPointsUrl!!.contains("&waypoints="))
        val waypointsCount = manyPointsUrl.substringAfter("&waypoints=").substringBefore("&travelmode=").split("|").size
        assertEquals(ExternalNavigationHelper.MAX_MAPS_WAYPOINTS, waypointsCount)

        val photosUrl = ExternalNavigationHelper.buildGooglePhotosUrl(LocalDate.of(2026, 3, 5), Locale.GERMAN)
        assertTrue(photosUrl.startsWith("https://photos.google.com/search/"))
    }
}
