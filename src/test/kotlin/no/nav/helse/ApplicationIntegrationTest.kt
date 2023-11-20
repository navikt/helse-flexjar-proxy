package no.nav.helse

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationIntegrationTest {

    @Test
    fun kallTilIsAliveSvarer200() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.get("/isAlive")

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertEquals(HttpStatusCode.OK.description, httpResponse.status.description)
    }

    @Test
    fun kallTilIsReadySvarer200() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.get("/isReady")

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertEquals(HttpStatusCode.OK.description, httpResponse.status.description)
    }

    @Test
    fun kallTilGet() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.get("/test")

        assertEquals(HttpStatusCode.OK, httpResponse.status)
    }

    @Test
    fun kallTilPost() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.post("/test")

        assertEquals(HttpStatusCode.Accepted, httpResponse.status)
    }

    @Test
    fun kallTilPut() = testApplication {
        val httpClient = createHttpClient()

        val httpResponse: HttpResponse = httpClient.put("/test")

        assertEquals(HttpStatusCode.MethodNotAllowed, httpResponse.status)
    }
}
