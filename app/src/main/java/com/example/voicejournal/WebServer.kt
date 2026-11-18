package com.example.voicejournal

import com.example.voicejournal.data.CreateJournalEntryDto
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryDto
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.util.TimeZoneUtils
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
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
import java.time.ZoneId
import java.util.UUID

class WebServer(private val repository: JournalRepository) {

    @Volatile
    private var server: ApplicationEngine? = null

    val isRunning: Boolean
        get() = server != null

    @Synchronized
    fun start() {
        if (isRunning) return
        server = embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                json()
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
                    val categories = repository.getAllCategoriesList()
                    call.respond(categories)
                }
                get("/journalentries") {
                    val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10).coerceIn(1, 500)
                    val offset = (page - 1) * pageSize

                    val entries = repository.getPaginatedEntries(pageSize, offset)
                    call.respond(entries.map { it.toDto() })
                }
                get("/journalentries/category/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing category ID")
                        return@get
                    }

                    val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10).coerceIn(1, 500)
                    val offset = (page - 1) * pageSize

                    val entries = repository.getPaginatedEntriesForCategory(id, pageSize, offset)
                    call.respond(entries.map { it.toDto() })
                }
                get("/journalentries/{id}") {
                    val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                        return@get
                    }

                    val entry = repository.getEntryById(id)
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

                    val categories = repository.getCategoriesByIds(createDto.categoryIds)
                    repository.insert(entry, categories)

                    val newEntry = repository.getEntryById(entry.id)
                    if (newEntry != null) {
                        call.respond(HttpStatusCode.Created, newEntry.toDto())
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Could not retrieve the newly created entry.")
                    }
                }
                put("/journalentries/{id}") {
                    val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                        return@put
                    }

                    val journalEntryDto = call.receive<JournalEntryDto>()

                    val existingEntry = repository.getEntryById(id)
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
                    val categories = repository.getCategoriesByIds(journalEntryDto.categoryIds)
                    repository.update(entry, categories)
                    call.respond(HttpStatusCode.OK)
                }
                delete("/journalentries/{id}") {
                    val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                        return@delete
                    }

                    val entryWithCategories = repository.getEntryById(id)
                    if (entryWithCategories == null) {
                        call.respond(HttpStatusCode.NotFound, "Entry not found")
                        return@delete
                    }

                    repository.softDeleteEntry(entryWithCategories.entry.id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }.start(wait = false)
    }

    @Synchronized
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    private fun EntryWithCategories.toDto(): JournalEntryDto {
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
}
