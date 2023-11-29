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
import java.nio.charset.StandardCharsets
import java.util.*

internal val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

private const val PROXY_BASE_PATH = "/syk/flexjar"
private const val FLEXJAR_SERVICE_DISCOVERY_URL = "http://flexjar-backend.flex"

private data class AzureConfig(
    val appClientId: String,
    val appClientSecret: String,
    val appScope: String,
    val configTokenEndpoint: String
)

@Suppress("unused")
fun Application.main() {
    val azureConfig = AzureConfig(
        appClientId = environment.config.property("security.client_id").getString(),
        appClientSecret = environment.config.property("security.client_secret").getString(),
        appScope = "api://${environment.config.property("security.scope").getString()}/.default",
        configTokenEndpoint = environment.config.property("security.config_token_endpoint").getString()
    )

    val corsAllowHost = environment.config.property("security.cors_allow_host").getString()
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
        when (val requestUri = call.request.uri.replace(PROXY_BASE_PATH, "")) {
            "/isAlive",
            "/isReady" -> {
                call.respond(HttpStatusCode(HttpStatusCode.OK.value, HttpStatusCode.OK.description))
            }

            else -> {
                when (httpMethod) {
                    HttpMethod.Post -> {
                        val azureResponse = hentAzureToken(httpClient, azureConfig)
                        val flexjarResponse = httpClient.post("$FLEXJAR_SERVICE_DISCOVERY_URL$requestUri") {
                            contentType(ContentType.Application.Json)
                            headers {
                                append("Authorization", "Bearer ${azureResponse.accessToken}")
                            }
                            setBody(call.request.receiveChannel())
                        }

                        call.respond(object : WriteChannelContent() {
                            override val contentLength = flexjarResponse.headers[HttpHeaders.ContentLength]?.toLong()
                            override val contentType =
                                flexjarResponse.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

                            // Filtrer bort Content-Type og Content-Length fra responsen siden de er satt eksplisitt.
                            override val headers = Headers.build {
                                appendAll(
                                    flexjarResponse.headers.filter { key, _ ->
                                        !key.equals(
                                            HttpHeaders.ContentType,
                                            ignoreCase = true
                                        ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                                    }
                                )
                            }
                            override val status = flexjarResponse.status

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                flexjarResponse.bodyAsChannel().copyAndClose(channel)
                            }
                        })
                    }

                    else -> {
                        call.respond(HttpStatusCode(405, "Method Not Allowed"))
                    }
                }
            }
        }
    }
}

private suspend fun hentAzureToken(httpClient: HttpClient, azureConfig: AzureConfig): AzureResponse {
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
