package com.example.voicejournal

import com.example.voicejournal.data.Category
import com.example.voicejournal.data.CreateJournalEntryDto
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryDto
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.data.toDto
import com.example.voicejournal.util.TimeZoneUtils
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.util.UUID

internal interface WebServerDataSource {
    suspend fun getAllCategoriesList(): List<Category>
    suspend fun getPaginatedEntries(limit: Int, offset: Int): List<EntryWithCategories>
    suspend fun getPaginatedEntriesForCategory(
        categoryId: Int,
        limit: Int,
        offset: Int
    ): List<EntryWithCategories>
    suspend fun getEntryById(id: UUID): EntryWithCategories?
    suspend fun getCategoriesByIds(ids: List<Int>): List<Category>
    suspend fun insert(entry: JournalEntry, categories: List<Category>)
    fun notifyEntryCreated(entryId: UUID)
    suspend fun update(entry: JournalEntry, categories: List<Category>)
    suspend fun softDeleteEntry(entryId: UUID)
}

private class RepositoryWebServerDataSource(
    private val repository: JournalRepository
) : WebServerDataSource {
    override suspend fun getAllCategoriesList(): List<Category> =
        repository.getAllCategoriesList()

    override suspend fun getPaginatedEntries(limit: Int, offset: Int): List<EntryWithCategories> =
        repository.getPaginatedEntries(limit, offset)

    override suspend fun getPaginatedEntriesForCategory(
        categoryId: Int,
        limit: Int,
        offset: Int
    ): List<EntryWithCategories> =
        repository.getPaginatedEntriesForCategory(categoryId, limit, offset)

    override suspend fun getEntryById(id: UUID): EntryWithCategories? =
        repository.getEntryById(id)

    override suspend fun getCategoriesByIds(ids: List<Int>): List<Category> =
        repository.getCategoriesByIds(ids)

    override suspend fun insert(entry: JournalEntry, categories: List<Category>) {
        repository.insert(entry, categories)
    }

    override fun notifyEntryCreated(entryId: UUID) =
        repository.notifyEntryCreated(entryId)

    override suspend fun update(entry: JournalEntry, categories: List<Category>) =
        repository.update(entry, categories)

    override suspend fun softDeleteEntry(entryId: UUID) =
        repository.softDeleteEntry(entryId)
}

class WebServer(private val repository: JournalRepository) {

    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_PAGE_SIZE = 10
        const val MAX_PAGE_SIZE = 500
    }

    @Volatile
    private var server: ApplicationEngine? = null

    val isRunning: Boolean
        get() = server != null

    @Synchronized
    fun start() {
        if (isRunning) return
        val dataSource = RepositoryWebServerDataSource(repository)
        server = embeddedServer(Netty, port = DEFAULT_PORT) {
            configureWebServerApi(dataSource)
        }.start(wait = false)
    }

    @Synchronized
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}

internal fun Application.configureWebServerApi(dataSource: WebServerDataSource) {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
        )
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    routing {
        get("/categories") {
            val categories = dataSource.getAllCategoriesList()
            call.respond(categories.map { it.toDto() })
        }
        get("/journalentries") {
            val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
            val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull()
                ?: WebServer.DEFAULT_PAGE_SIZE).coerceIn(1, WebServer.MAX_PAGE_SIZE)
            val offset = (page - 1) * pageSize

            val entries = dataSource.getPaginatedEntries(pageSize, offset)
            call.respond(entries.map { it.toDto() })
        }
        get("/journalentries/category/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing category ID")
                return@get
            }

            val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
            val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull()
                ?: WebServer.DEFAULT_PAGE_SIZE).coerceIn(1, WebServer.MAX_PAGE_SIZE)
            val offset = (page - 1) * pageSize

            val entries = dataSource.getPaginatedEntriesForCategory(id, pageSize, offset)
            call.respond(entries.map { it.toDto() })
        }
        get("/journalentries/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                return@get
            }

            val entry = dataSource.getEntryById(id)
            if (entry == null) {
                call.respond(HttpStatusCode.NotFound, "Entry not found")
                return@get
            }

            call.respond(entry.toDto())
        }
        post("/journalentries") {
            val createDto = call.receive<CreateJournalEntryDto>()

            val entry = JournalEntry(
                content = createDto.content,
                start_datetime = createDto.start_datetime,
                stop_datetime = createDto.stop_datetime,
                hasImage = createDto.hasImage,
                checked = createDto.checked,
                zoneId = TimeZoneUtils.sanitizeZoneId(createDto.zoneId) ?: ZoneId.systemDefault().id
            )

            val categories = dataSource.getCategoriesByIds(createDto.categoryIds)
            dataSource.insert(entry, categories)
            dataSource.notifyEntryCreated(entry.id)

            val newEntry = dataSource.getEntryById(entry.id)
            if (newEntry != null) {
                call.respond(HttpStatusCode.Created, newEntry.toDto())
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Could not retrieve the newly created entry."
                )
            }
        }
        put("/journalentries/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                return@put
            }

            val journalEntryDto = call.receive<JournalEntryDto>()

            val existingEntry = dataSource.getEntryById(id)
            if (existingEntry == null) {
                call.respond(HttpStatusCode.NotFound, "Entry not found")
                return@put
            }

            val resolvedZoneId = if (journalEntryDto.zoneId != null) {
                TimeZoneUtils.sanitizeZoneId(journalEntryDto.zoneId)
            } else {
                existingEntry.entry.zoneId
            }

            val entry = JournalEntry(
                id = id,
                content = journalEntryDto.content,
                start_datetime = journalEntryDto.start_datetime,
                stop_datetime = journalEntryDto.stop_datetime,
                hasImage = journalEntryDto.hasImage,
                checked = journalEntryDto.checked,
                zoneId = resolvedZoneId
            )
            val categories = dataSource.getCategoriesByIds(journalEntryDto.categoryIds)
            dataSource.update(entry, categories)
            call.respond(HttpStatusCode.OK)
        }
        delete("/journalentries/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                return@delete
            }

            val entryWithCategories = dataSource.getEntryById(id)
            if (entryWithCategories == null) {
                call.respond(HttpStatusCode.NotFound, "Entry not found")
                return@delete
            }

            dataSource.softDeleteEntry(entryWithCategories.entry.id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun EntryWithCategories.toDto(): JournalEntryDto {
    return JournalEntryDto(
        id = entry.id,
        content = entry.content,
        start_datetime = entry.start_datetime,
        stop_datetime = entry.stop_datetime,
        hasImage = entry.hasImage,
        checked = entry.checked,
        categoryIds = categories.map { it.id },
        zoneId = entry.zoneId
    )
}
