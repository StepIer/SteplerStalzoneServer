package com.stepler

import com.stepler.model.HealthResponse
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/health") {
            call.respond(HealthResponse(status = "UP"))
        }
    }
}
