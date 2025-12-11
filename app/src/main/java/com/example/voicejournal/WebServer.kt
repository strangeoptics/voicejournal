package com.example.voicejournal

import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.CreateJournalEntryDto
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryDto
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
import java.util.UUID

class WebServer(private val db: AppDatabase) {

    private var server: ApplicationEngine? = null

    val isRunning: Boolean
        get() = server != null

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
                    val categories = db.categoryDao().getAllCategoriesList()
                    call.respond(categories)
                }
                get("/journalentries") {
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10
                    val offset = (page - 1) * pageSize

                    val entries = db.journalEntryDao().getPaginatedEntriesWithCategories(pageSize, offset)
                    val dtos = entries.map { entryWithCategories ->
                        JournalEntryDto(
                            id = entryWithCategories.entry.id,
                            content = entryWithCategories.entry.content,
                            start_datetime = entryWithCategories.entry.start_datetime,
                            stop_datetime = entryWithCategories.entry.stop_datetime,
                            hasImage = entryWithCategories.entry.hasImage,
                            categoryIds = entryWithCategories.categories.map { it.id }
                        )
                    }
                    call.respond(dtos)
                }
                get("/journalentries/category/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing category ID")
                        return@get
                    }

                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10
                    val offset = (page - 1) * pageSize

                    val entries = db.journalEntryDao().getPaginatedEntriesForCategory(id, pageSize, offset)
                    val dtos = entries.map { entryWithCategories ->
                        JournalEntryDto(
                            id = entryWithCategories.entry.id,
                            content = entryWithCategories.entry.content,
                            start_datetime = entryWithCategories.entry.start_datetime,
                            stop_datetime = entryWithCategories.entry.stop_datetime,
                            hasImage = entryWithCategories.entry.hasImage,
                            categoryIds = entryWithCategories.categories.map { it.id }
                        )
                    }
                    call.respond(dtos)
                }
                get("/journalentries/{id}") {
                    val id = call.parameters["id"]?.let {
                        try {
                            UUID.fromString(it)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                        return@get
                    }

                    val entry = db.journalEntryDao().getEntryById(id)
                    if (entry == null) {
                        call.respond(HttpStatusCode.NotFound, "Entry not found")
                        return@get
                    }

                    val dto = JournalEntryDto(
                        id = entry.entry.id,
                        content = entry.entry.content,
                        start_datetime = entry.entry.start_datetime,
                        stop_datetime = entry.entry.stop_datetime,
                        hasImage = entry.entry.hasImage,
                        categoryIds = entry.categories.map { it.id }
                    )
                    call.respond(dto)
                }
                post("/journalentries") {
                    val createDto = call.receive<CreateJournalEntryDto>()

                    val entry = JournalEntry(
                        content = createDto.content,
                        start_datetime = createDto.start_datetime,
                        stop_datetime = createDto.stop_datetime,
                        hasImage = createDto.hasImage
                    )

                    val categories = db.categoryDao().getCategoriesByIds(createDto.categoryIds)
                    db.journalEntryDao().insertWithCategories(entry, categories, db.categoryDao())

                    val newEntry = db.journalEntryDao().getEntryById(entry.id)

                    if (newEntry != null) {
                        val dto = JournalEntryDto(
                            id = newEntry.entry.id,
                            content = newEntry.entry.content,
                            start_datetime = newEntry.entry.start_datetime,
                            stop_datetime = newEntry.entry.stop_datetime,
                            hasImage = newEntry.entry.hasImage,
                            categoryIds = newEntry.categories.map { it.id }
                        )
                        call.respond(HttpStatusCode.Created, dto)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Could not retrieve the newly created entry.")
                    }
                }
                put("/journalentries/{id}") {
                    val id = call.parameters["id"]?.let {
                        try {
                            UUID.fromString(it)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                        return@put
                    }

                    val journalEntryDto = call.receive<JournalEntryDto>()

                    val existingEntry = db.journalEntryDao().getEntryById(id)
                    if (existingEntry == null) {
                        call.respond(HttpStatusCode.NotFound, "Entry not found")
                        return@put
                    }

                    val entry = JournalEntry(
                        id = id,
                        content = journalEntryDto.content,
                        start_datetime = journalEntryDto.start_datetime,
                        stop_datetime = journalEntryDto.stop_datetime,
                        hasImage = journalEntryDto.hasImage
                    )
                    val categories = db.categoryDao().getCategoriesByIds(journalEntryDto.categoryIds)

                    db.journalEntryDao().updateWithCategories(entry, categories, db.categoryDao())
                    call.respond(HttpStatusCode.OK)
                }
                delete("/journalentries/{id}") {
                    val id = call.parameters["id"]?.let {
                        try {
                            UUID.fromString(it)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing entry ID")
                        return@delete
                    }

                    val entryWithCategories = db.journalEntryDao().getEntryById(id)
                    if (entryWithCategories == null) {
                        call.respond(HttpStatusCode.NotFound, "Entry not found")
                        return@delete
                    }

                    db.journalEntryDao().delete(entryWithCategories.entry)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}