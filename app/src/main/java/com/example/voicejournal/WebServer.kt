package com.example.voicejournal

import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalEntryDto
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WebServer(private val db: AppDatabase) {

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            embeddedServer(Netty, port = 8080) {
                install(ContentNegotiation) {
                    json()
                }
                routing {
                    get("/categories") {
                        val categories = db.categoryDao().getAll()
                        call.respond(categories)
                    }
                    get("/journalentries") {
                        val entries = db.journalEntryDao().getAllEntriesWithCategories()
                        val dtos = entries.map { entryWithCategories ->
                            JournalEntryDto(
                                id = entryWithCategories.entry.id,
                                content = entryWithCategories.entry.content,
                                timestamp = entryWithCategories.entry.timestamp,
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

                        val entries = db.journalEntryDao().getEntriesForCategory(id)
                        val dtos = entries.map { entryWithCategories ->
                            JournalEntryDto(
                                id = entryWithCategories.entry.id,
                                content = entryWithCategories.entry.content,
                                timestamp = entryWithCategories.entry.timestamp,
                                hasImage = entryWithCategories.entry.hasImage,
                                categoryIds = entryWithCategories.categories.map { it.id }
                            )
                        }
                        call.respond(dtos)
                    }
                     get("/journalentries/{id}") {
                        val id = call.parameters["id"]?.toIntOrNull()
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
                            timestamp = entry.entry.timestamp,
                            hasImage = entry.entry.hasImage,
                            categoryIds = entry.categories.map { it.id }
                        )
                        call.respond(dto)
                    }
                }
            }.start(wait = true)
        }
    }
}