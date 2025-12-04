package com.example.voicejournal

import com.example.voicejournal.data.AppDatabase
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
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
                }
            }.start(wait = true)
        }
    }
}