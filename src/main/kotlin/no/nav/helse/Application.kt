package no.nav.helse

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

internal val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun Application.main() {
    val log = LoggerFactory.getLogger("no.nav.helse.Application.main")

    intercept(ApplicationCallPipeline.Call) {
        val httpMethod = call.request.httpMethod
        log.info("Intercepted ${httpMethod.value} call to \"${call.request.uri}\".")

        when (call.request.uri) {
            "/isAlive",
            "/isReady" -> {
                call.respond(HttpStatusCode(HttpStatusCode.OK.value, HttpStatusCode.OK.description))
                return@intercept finish()
            }

            else -> {
                when (httpMethod) {
                    Post -> {
                        call.respond(HttpStatusCode.Accepted)
                    }

                    Get -> {
                        call.respond(HttpStatusCode.OK)
                    }

                    else -> {
                        log.info("Ignoring ${httpMethod.value} call to \"${call.request.uri}\".")
                        call.respond(HttpStatusCode(405, "Method Not Allowed"))
                    }
                }
            }
        }
    }
}