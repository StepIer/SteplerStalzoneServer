package com.stepler.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Cross-origin access is closed by default. Grant browser clients explicitly by
 * listing them under `cors.allowedHosts` in application.yaml, as host[:port]
 * without a scheme — for example `localhost:3000`.
 */
fun Application.configureHTTP() {
    val allowedHosts = environment.config
        .propertyOrNull("cors.allowedHosts")
        ?.getList()
        .orEmpty()
        .filter { it.isNotBlank() }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true

        allowedHosts.forEach { host ->
            allowHost(host, schemes = listOf("http", "https"))
        }
    }

    if (allowedHosts.isEmpty()) {
        log.info("CORS: no allowed hosts configured; cross-origin browser requests will be rejected")
    } else {
        log.info("CORS: allowing origins {}", allowedHosts)
    }
}
