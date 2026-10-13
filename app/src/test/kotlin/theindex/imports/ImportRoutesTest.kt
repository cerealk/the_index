package theindex.imports

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import theindex.TestDatabase
import theindex.module
import theindex.publications.Kind

class ImportRoutesTest {
    private val harness = ImportHarness()

    @AfterEach
    fun cleanUp() {
        harness.close()
        TestDatabase.truncateAll()
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ContentNegotiation) { json() } }

    @Test
    fun `lists Import jobs newest first, optionally by state`() = testApplication {
        application { module(harness.config) }
        val first = harness.jobs.create(ImportSource.INBOX, "a.epub", null)
        val second = harness.jobs.create(ImportSource.INBOX, "b.cbz", Kind.COMIC)
        harness.jobs.finish(first, ImportState.FAILED, reason = "Format not recognized")
        val client = jsonClient()

        client.get("/api/v1/imports").body<List<ImportJobResponse>>().map { it.id } shouldBe listOf(second, first)

        val failed = client.get("/api/v1/imports?state=failed").body<List<ImportJobResponse>>()
        failed.map { it.id } shouldBe listOf(first)
        failed.single().reason shouldBe "Format not recognized"
    }

    @Test
    fun `gets one Import job`() = testApplication {
        application { module(harness.config) }
        val id = harness.jobs.create(ImportSource.INBOX, "b.cbz", Kind.COMIC)

        val job = jsonClient().get("/api/v1/imports/$id").body<ImportJobResponse>()

        job.originalName shouldBe "b.cbz"
        job.kindHint shouldBe "comic"
        job.state shouldBe "pending"
    }

    @Test
    fun `answers 404 for an unknown job and 400 for an unknown state`() = testApplication {
        application { module(harness.config) }

        client.get("/api/v1/imports/999999").status shouldBe HttpStatusCode.NotFound
        client.get("/api/v1/imports?state=lost").status shouldBe HttpStatusCode.BadRequest
    }
}
