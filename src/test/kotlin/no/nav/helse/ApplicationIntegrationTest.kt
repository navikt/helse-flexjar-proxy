package no.nav.helse

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationIntegrationTest {

    @Test
    fun `Kall til isAlive svarer med 200 OK`() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.get("/isAlive")

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertEquals(HttpStatusCode.OK.description, httpResponse.status.description)
    }

    @Test
    fun `Kall til isReady svarer med 200 OK`() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.get("/isReady")

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertEquals(HttpStatusCode.OK.description, httpResponse.status.description)
    }

    @Test
    fun `Alle POSTkall blir akseptert`() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.post("/test")

        assertEquals(HttpStatusCode.Accepted, httpResponse.status)
    }

    @Test
    fun `Ingen PUT-kall blir akseptert`() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.put("/test")

        assertEquals(HttpStatusCode.MethodNotAllowed, httpResponse.status)
    }

    @Test
    fun `Kall til debug returnerer azure-app-client-id`() = testApplication {
        val httpClient = createHttpClient()

        httpClient.get("/debug").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("azure-app-client-id", bodyAsText())
        }
    }
}
