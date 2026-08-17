package com.stepler

import com.stepler.model.ErrorResponse
import com.stepler.model.HealthResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun `test root endpoint`() = testApplication {
        // loads default configuration
        configure()
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun `health endpoint returns JSON`() = testApplication {
        configure()

        val response = jsonClient().get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(HealthResponse(status = "UP"), response.body<HealthResponse>())
    }

    @Test
    fun `unknown route returns a JSON 404 body`() = testApplication {
        configure()

        val response = jsonClient().get("/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorResponse("Resource not found"), response.body<ErrorResponse>())
    }

    @Test
    fun `unhandled exception is masked as a 500 without leaking details`() = testApplication {
        configure()
        application {
            routing {
                get("/explode") { throw IllegalStateException("secret internal detail") }
            }
        }

        val response = jsonClient().get("/explode")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ErrorResponse("Internal server error"), response.body<ErrorResponse>())
    }
}
