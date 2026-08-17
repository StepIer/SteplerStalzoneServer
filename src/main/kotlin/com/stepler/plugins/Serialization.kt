package com.stepler.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                // Tolerate fields the client sends that we don't model yet.
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
}
