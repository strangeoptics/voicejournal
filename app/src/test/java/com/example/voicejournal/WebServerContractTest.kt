package com.example.voicejournal

import com.example.voicejournal.data.Category
import com.example.voicejournal.data.CategoryDto
import com.example.voicejournal.data.CreateJournalEntryDto
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryDto
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Verifies that the embedded Ktor REST API ([WebServer]) and its Kotlin DTOs stay in sync
 * with the shared OpenAPI contract (`api/openapi.yaml`) consumed by the Node.js web client.
 */
class WebServerContractTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun openApiSchemasMatchKotlinDtoDescriptors() {
        val yamlLines = resolveOpenApiFile().readLines()

        assertDtoMatchesOpenApiSchema(
            yamlLines = yamlLines,
            schemaName = "CategoryDto",
            descriptor = CategoryDto.serializer().descriptor
        )
        assertDtoMatchesOpenApiSchema(
            yamlLines = yamlLines,
            schemaName = "JournalEntryDto",
            descriptor = JournalEntryDto.serializer().descriptor
        )
        assertDtoMatchesOpenApiSchema(
            yamlLines = yamlLines,
            schemaName = "CreateJournalEntryDto",
            descriptor = CreateJournalEntryDto.serializer().descriptor
        )
    }

    @Test
    fun openApiPageSizeLimitMatchesWebServerConstant() {
        val yamlLines = resolveOpenApiFile().readLines()
        val openApiMaxPageSize = parsePageSizeMaximum(yamlLines)
        assertEquals(
            "PageSizeParam maximum in api/openapi.yaml must match WebServer.MAX_PAGE_SIZE",
            WebServer.MAX_PAGE_SIZE,
            openApiMaxPageSize
        )
    }

    @Test
    fun openApiPathsMatchImplementedRoutes() {
        val yamlLines = resolveOpenApiFile().readLines()
        val expectedPaths = mapOf(
            "/categories" to setOf("get"),
            "/journalentries" to setOf("get", "post"),
            "/journalentries/category/{id}" to setOf("get"),
            "/journalentries/{id}" to setOf("get", "put", "delete")
        )
        assertEquals(expectedPaths, parseOpenApiPaths(yamlLines))
    }

    @Test
    fun getCategories_returnsCategoryDtoMatchingOpenApiContract() = testApplication {
        val yamlLines = resolveOpenApiFile().readLines()
        val expectedKeys = parseSchemaProperties(yamlLines, "CategoryDto")
        val fakeDataSource = FakeWebServerDataSource()

        application {
            configureWebServerApi(fakeDataSource)
        }

        val response = client.get("/categories")
        assertEquals(HttpStatusCode.OK, response.status)

        val payload = json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(2, payload.size)
        payload.forEach { element ->
            assertEquals(expectedKeys, element.jsonObject.keys)
        }
    }

    @Test
    fun getJournalEntries_clampsPageSizeAndMatchesOpenApiContract() = testApplication {
        val yamlLines = resolveOpenApiFile().readLines()
        val expectedKeys = parseSchemaProperties(yamlLines, "JournalEntryDto")
        val fakeDataSource = FakeWebServerDataSource()

        application {
            configureWebServerApi(fakeDataSource)
        }

        val response = client.get("/journalentries?page=1&pageSize=10000")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(WebServer.MAX_PAGE_SIZE, fakeDataSource.lastLimit)
        assertEquals(0, fakeDataSource.lastOffset)

        val entries = json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(1, entries.size)
        assertEquals(expectedKeys, entries.first().jsonObject.keys)

        val categoryResponse = client.get("/journalentries/category/1?page=2&pageSize=25")
        assertEquals(HttpStatusCode.OK, categoryResponse.status)
        assertEquals(1, fakeDataSource.lastCategoryId)
        assertEquals(25, fakeDataSource.lastLimit)
        assertEquals(25, fakeDataSource.lastOffset)

        val invalidCategoryResponse = client.get("/journalentries/category/not-a-number")
        assertEquals(HttpStatusCode.BadRequest, invalidCategoryResponse.status)
    }

    @Test
    fun crudLifecycle_postGetPutDelete_matchesOpenApiContract() = testApplication {
        val yamlLines = resolveOpenApiFile().readLines()
        val expectedKeys = parseSchemaProperties(yamlLines, "JournalEntryDto")
        val fakeDataSource = FakeWebServerDataSource()

        application {
            configureWebServerApi(fakeDataSource)
        }

        // POST /journalentries
        val createPayload = CreateJournalEntryDto(
            content = "Reisenotiz aus New York",
            start_datetime = 1730000000000L,
            stop_datetime = 1730003600000L,
            hasImage = false,
            checked = false,
            categoryIds = listOf(1),
            zoneId = "America/New_York"
        )
        val postResponse = client.post("/journalentries") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateJournalEntryDto.serializer(), createPayload))
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)

        val createdJson = json.parseToJsonElement(postResponse.bodyAsText()).jsonObject
        assertEquals(expectedKeys, createdJson.keys)
        val createdDto = json.decodeFromString(JournalEntryDto.serializer(), postResponse.bodyAsText())
        assertEquals("Reisenotiz aus New York", createdDto.content)
        assertEquals("America/New_York", createdDto.zoneId)
        assertEquals(createdDto.id, fakeDataSource.lastNotifiedEntryId)

        // GET /journalentries/{id}
        val getResponse = client.get("/journalentries/${createdDto.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetchedDto = json.decodeFromString(JournalEntryDto.serializer(), getResponse.bodyAsText())
        assertEquals(createdDto, fetchedDto)

        // Invalid & missing IDs
        assertEquals(HttpStatusCode.BadRequest, client.get("/journalentries/not-a-uuid").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/journalentries/${UUID.randomUUID()}").status)

        // PUT /journalentries/{id} with null zoneId preserves stored zoneId
        val updatePayload = createdDto.copy(
            content = "Aktualisierte Reisenotiz",
            checked = true,
            zoneId = null
        )
        val putResponse = client.put("/journalentries/${createdDto.id}") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JournalEntryDto.serializer(), updatePayload))
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)

        val updatedEntry = fakeDataSource.getEntryById(createdDto.id)
        assertNotNull(updatedEntry)
        assertEquals("Aktualisierte Reisenotiz", updatedEntry!!.entry.content)
        assertTrue(updatedEntry.entry.checked)
        assertEquals("America/New_York", updatedEntry.entry.zoneId)

        // DELETE /journalentries/{id}
        val deleteResponse = client.delete("/journalentries/${createdDto.id}")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/journalentries/${createdDto.id}").status)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun assertDtoMatchesOpenApiSchema(
        yamlLines: List<String>,
        schemaName: String,
        descriptor: SerialDescriptor
    ) {
        val openApiProps = parseSchemaProperties(yamlLines, schemaName)
        val openApiRequired = parseSchemaRequired(yamlLines, schemaName)

        val kotlinProps = (0 until descriptor.elementsCount)
            .map { descriptor.getElementName(it) }
            .toSet()
        val kotlinMandatory = (0 until descriptor.elementsCount)
            .filter { !descriptor.isElementOptional(it) }
            .map { descriptor.getElementName(it) }
            .toSet()

        assertEquals(
            "Property names of Kotlin $schemaName must match api/openapi.yaml",
            openApiProps,
            kotlinProps
        )
        assertTrue(
            "All required properties in api/openapi.yaml for $schemaName must exist in Kotlin DTO",
            kotlinProps.containsAll(openApiRequired)
        )
        assertTrue(
            "All mandatory Kotlin properties of $schemaName ($kotlinMandatory) must be marked required in api/openapi.yaml ($openApiRequired)",
            openApiRequired.containsAll(kotlinMandatory)
        )
    }

    private fun resolveOpenApiFile(): File {
        val candidates = listOf(
            File("../api/openapi.yaml"),
            File("api/openapi.yaml")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate api/openapi.yaml from ${File(".").absolutePath}")
    }

    private fun parseSchemaProperties(lines: List<String>, schemaName: String): Set<String> {
        val schemaBlock = extractIndentedBlock(lines, "    $schemaName:")
        val propsBlock = extractIndentedBlock(schemaBlock, "      properties:")
        return propsBlock
            .mapNotNull { line ->
                val match = Regex("^ {8}([A-Za-z0-9_]+):\\s*$").matchEntire(line)
                match?.groupValues?.get(1)
            }
            .toSet()
    }

    private fun parseSchemaRequired(lines: List<String>, schemaName: String): Set<String> {
        val schemaBlock = extractIndentedBlock(lines, "    $schemaName:")
        val reqBlock = extractIndentedBlock(schemaBlock, "      required:")
        return reqBlock
            .mapNotNull { line ->
                val match = Regex("^ {8}-\\s+([A-Za-z0-9_]+)\\s*$").matchEntire(line)
                match?.groupValues?.get(1)
            }
            .toSet()
    }

    private fun parseOpenApiPaths(lines: List<String>): Map<String, Set<String>> {
        val pathsBlock = extractIndentedBlock(lines, "paths:")
        val result = linkedMapOf<String, MutableSet<String>>()
        var currentPath: String? = null
        for (line in pathsBlock) {
            val pathMatch = Regex("^ {2}(/[^:]+):\\s*$").matchEntire(line)
            if (pathMatch != null) {
                currentPath = pathMatch.groupValues[1]
                result.putIfAbsent(currentPath, linkedSetOf())
                continue
            }
            val methodMatch = Regex("^ {4}(get|post|put|patch|delete):\\s*$").matchEntire(line)
            if (methodMatch != null && currentPath != null) {
                result.getValue(currentPath).add(methodMatch.groupValues[1])
            }
        }
        return result
    }

    private fun parsePageSizeMaximum(lines: List<String>): Int {
        val paramBlock = extractIndentedBlock(lines, "    PageSizeParam:")
        val maxLine = paramBlock.firstOrNull { it.trim().startsWith("maximum:") }
            ?: error("maximum not found under PageSizeParam in api/openapi.yaml")
        return maxLine.substringAfter("maximum:").trim().toInt()
    }

    private fun extractIndentedBlock(lines: List<String>, header: String): List<String> {
        val startIndex = lines.indexOfFirst { it == header }
        if (startIndex == -1) {
            error("Section '$header' not found in api/openapi.yaml")
        }
        val baseIndent = header.takeWhile { it == ' ' }.length
        val block = mutableListOf<String>()
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.takeWhile { it == ' ' }.length
            if (indent <= baseIndent) break
            block.add(line)
        }
        return block
    }

    private class FakeWebServerDataSource : WebServerDataSource {
        private val categories = listOf(
            Category(
                id = 1,
                category = "Journal",
                aliases = "tagebuch",
                showAll = false,
                orderIndex = 0,
                color = "#FFFFFF",
                checkable = false,
                showDate = true,
                deleteLineOnDoubleTap = false
            ),
            Category(
                id = 2,
                category = "Einkauf",
                aliases = "kaufen",
                showAll = false,
                orderIndex = 1,
                color = "#E3F2FD",
                checkable = true,
                showDate = false,
                deleteLineOnDoubleTap = true
            )
        )

        private val entries = linkedMapOf<UUID, EntryWithCategories>().apply {
            val initialId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            put(
                initialId,
                EntryWithCategories(
                    entry = JournalEntry(
                        id = initialId,
                        content = "Erster Eintrag",
                        start_datetime = 1720000000000L,
                        stop_datetime = null,
                        hasImage = false,
                        checked = false,
                        zoneId = "Europe/Berlin"
                    ),
                    categories = listOf(categories.first())
                )
            )
        }

        var lastLimit: Int = -1
        var lastOffset: Int = -1
        var lastCategoryId: Int? = null
        var lastNotifiedEntryId: UUID? = null

        override suspend fun getAllCategoriesList(): List<Category> = categories

        override suspend fun getPaginatedEntries(limit: Int, offset: Int): List<EntryWithCategories> {
            lastLimit = limit
            lastOffset = offset
            return entries.values
                .filter { it.entry.deletedAt == null }
                .sortedByDescending { it.entry.start_datetime }
                .drop(offset)
                .take(limit)
        }

        override suspend fun getPaginatedEntriesForCategory(
            categoryId: Int,
            limit: Int,
            offset: Int
        ): List<EntryWithCategories> {
            lastCategoryId = categoryId
            lastLimit = limit
            lastOffset = offset
            return entries.values
                .filter { it.entry.deletedAt == null && it.categories.any { c -> c.id == categoryId } }
                .sortedByDescending { it.entry.start_datetime }
                .drop(offset)
                .take(limit)
        }

        override suspend fun getEntryById(id: UUID): EntryWithCategories? =
            entries[id]?.takeIf { it.entry.deletedAt == null }

        override suspend fun getCategoriesByIds(ids: List<Int>): List<Category> =
            categories.filter { it.id in ids }

        override suspend fun insert(entry: JournalEntry, categories: List<Category>) {
            entries[entry.id] = EntryWithCategories(entry = entry, categories = categories)
        }

        override fun notifyEntryCreated(entryId: UUID) {
            lastNotifiedEntryId = entryId
        }

        override suspend fun update(entry: JournalEntry, categories: List<Category>) {
            entries[entry.id] = EntryWithCategories(entry = entry, categories = categories)
        }

        override suspend fun softDeleteEntry(entryId: UUID) {
            val existing = entries[entryId] ?: return
            entries[entryId] = existing.copy(
                entry = existing.entry.copy(deletedAt = System.currentTimeMillis())
            )
        }
    }
}
