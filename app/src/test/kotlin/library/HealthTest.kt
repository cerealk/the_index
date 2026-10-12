package library

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import library.health.HealthCheck
import library.health.healthRoutes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class HealthTest {
    @AfterEach
    fun cleanUp() = TestDatabase.truncateAll()

    @Test
    fun `health answers 200 once the database is reachable`() = testApplication {
        application { module(TestDatabase.config()) }

        val response = client.get("/api/v1/health")

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldContain "\"database\":\"up\""
    }

    @Test
    fun `health answers 503 when the database is unreachable`() = testApplication {
        application {
            configureSerialization()
            routing { healthRoutes(HealthCheck { false }) }
        }

        val response = client.get("/api/v1/health")

        response.status shouldBe HttpStatusCode.ServiceUnavailable
        response.bodyAsText() shouldContain "\"database\":\"down\""
    }
}
