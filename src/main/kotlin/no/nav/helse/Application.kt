package no.nav.helse

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent.WriteChannelContent
import io.ktor.http.contentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.util.filter
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*

internal val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

const val BASE_PATH = "/syk/flexjar"

private data class AzureConfig(
    val appClientId: String,
    val appClientSecret: String,
    val appScope: String,
    val configTokenEndpoint: String
)

@Suppress("unused")
fun Application.main() {
    val log = LoggerFactory.getLogger("no.nav.helse.Application.main")
    val corsAllowHost = environment.config.property("security.cors_allow_host").getString()

    val azureConfig = AzureConfig(
        appClientId = environment.config.property("security.client_id").getString(),
        appClientSecret = environment.config.property("security.client_secret").getString(),
        appScope = environment.config.property("security.scope").getString(),
        configTokenEndpoint = environment.config.property("security.config_token_endpoint").getString()
    )

    install(CORS) {
        allowHost(corsAllowHost, schemes = listOf("https"))
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }

    val httpClient = createHttpClient()

    intercept(ApplicationCallPipeline.Call) {
        val httpMethod = call.request.httpMethod
        when (val requestUri = call.request.uri.replace(BASE_PATH, "")) {
            "/isAlive",
            "/isReady" -> {
                call.respond(HttpStatusCode(HttpStatusCode.OK.value, HttpStatusCode.OK.description))
            }

            else -> {
                log.info("Intercepted ${httpMethod.value} call to \"$requestUri\".")
                when (httpMethod) {
                    HttpMethod.Post -> {
                        val flexjarUrl = "http://flexjar-backend.flex$requestUri"

                        val azureResponse = hentAzureToken(httpClient, azureConfig, log)
                        val response = httpClient.post(flexjarUrl) {
                            contentType(ContentType.Application.Json)
                            headers {
                                append("Authorization", "Bearer ${azureResponse.accessToken}")
                            }
                            setBody(call.request.receiveChannel())
                        }

                        call.respond(object : WriteChannelContent() {
                            override val contentLength = response.headers[HttpHeaders.ContentLength]?.toLong()
                            override val contentType =
                                response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

                            // Filtrer bort Content-Type og Content-Length fra responsen siden de er satt eksplisitt.
                            override val headers = Headers.build {
                                appendAll(
                                    response.headers.filter { key, _ ->
                                        !key.equals(
                                            HttpHeaders.ContentType,
                                            ignoreCase = true
                                        ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                                    }
                                )
                            }
                            override val status = response.status

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                response.bodyAsChannel().copyAndClose(channel)
                            }
                        })
                    }

                    else -> {
                        log.info("Ignoring ${httpMethod.value} call to \"$requestUri\".")
                        call.respond(HttpStatusCode(405, "Method Not Allowed"))
                    }
                }
            }
        }
    }
}

private suspend fun hentAzureToken(httpClient: HttpClient, azureConfig: AzureConfig, log: Logger): AzureResponse {
    log.info("Henter Azure token: azureConfig.configTokenEndpoint=${azureConfig.configTokenEndpoint} med configTokenEndpoint=${azureConfig.serializeToString()}")

    return httpClient.submitForm(
        url = azureConfig.configTokenEndpoint,
        formParameters = Parameters.build {
            append("client_id", azureConfig.appClientId)
            append("client_secret", azureConfig.appClientSecret)
            append("scope", azureConfig.appScope)
            append("grant_type", "client_credentials")
        }
    ) {
        header(
            "Authorization",
            "Basic ${basicAuth(azureConfig.appClientId, azureConfig.appClientSecret)}"
        )
    }.body()
}

private fun createHttpClient() = HttpClient(CIO) {
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
}

private fun basicAuth(clientId: String, clientSecret: String) =
    Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8))

private data class AzureResponse(
    @JsonAlias("access_token")
    val accessToken: String,
    @JsonAlias("expires_in")
    val expiresIn: Long,
    @JsonAlias("token_type")
    val tokenType: String
)

fun Any.serializeToString(): String =
    objectMapper.writeValueAsString(this)
