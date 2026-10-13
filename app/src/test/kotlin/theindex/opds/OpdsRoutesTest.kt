package theindex.opds

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import theindex.Fixtures
import theindex.TestDatabase
import theindex.files.Format
import theindex.files.Hasher
import theindex.imports.ImportHarness
import theindex.module
import theindex.persistence.FileTable
import theindex.persistence.PublicationTable
import java.security.MessageDigest
import java.time.Instant

class OpdsRoutesTest {
    private val harness = ImportHarness()

    @AfterEach
    fun cleanUp() {
        harness.close()
        TestDatabase.truncateAll()
    }

    private fun opds(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(harness.config, harness.clock) }
        test()
    }

    private suspend fun ApplicationTestBuilder.feed(path: String): AtomFeed {
        val response = client.get(path)
        response.status shouldBe HttpStatusCode.OK
        return AtomFeed(response.bodyAsText())
    }

    @Test
    fun `the root is a navigation feed matching the fixture`() = opds {
        for (path in listOf("/opds/", "/opds")) {
            val response = client.get(path)

            response.contentType() shouldBe atom(AtomSerializer.NAVIGATION_TYPE)
            response.bodyAsText() shouldBe AtomSerializerTest.fixture("navigation-feed.xml")
        }
    }

    @Test
    fun `All lists Publications A-Z by sort title`() = opds {
        seed.publication("The Cherry Orchard", sortTitle = "Cherry Orchard, The")
        seed.publication("apple pie")
        seed.publication("Banana Split")

        val all = client.get("/opds/all")

        all.contentType() shouldBe atom(AtomSerializer.ACQUISITION_TYPE)
        AtomFeed(all.bodyAsText()).titles shouldBe listOf("apple pie", "Banana Split", "The Cherry Orchard")
    }

    @Test
    fun `Recently added lists the newest Publications first`() = opds {
        seed.publication("Older", createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        seed.publication("Newest", createdAt = Instant.parse("2026-03-01T00:00:00Z"))
        seed.publication("Middle", createdAt = Instant.parse("2026-02-01T00:00:00Z"))

        feed("/opds/recent").titles shouldBe listOf("Newest", "Middle", "Older")
    }

    @Test
    fun `long feeds are paginated by 50 with next and previous links`() = opds {
        repeat(51) { seed.publication("Book %03d".format(it)) }

        val first = feed("/opds/all")
        first.entries shouldHaveSize 50
        first.link("next") shouldBe "/opds/all?page=2"
        first.link("previous").shouldBeNull()

        val second = feed("/opds/all?page=2")
        second.titles shouldBe listOf("Book 050")
        second.link("previous") shouldBe "/opds/all"
        second.link("next").shouldBeNull()
        second.link("self") shouldBe "/opds/all?page=2"
    }

    @Test
    fun `every File is offered with its MIME type, preferred File first`() = opds {
        seed.publication("Dune", Format.PDF to Fixtures.pdf(), Format.EPUB to Fixtures.epub())
        seed.publication("Saga 001", Format.CBR to Fixtures.cbr(), Format.CBZ to Fixtures.cbz())

        val entries = feed("/opds/all").entries

        entries[0].acquisitions.map { it.second } shouldBe listOf("application/epub+zip", "application/pdf")
        entries[1].acquisitions.map { it.second } shouldBe
            listOf("application/vnd.comicbook+zip", "application/vnd.comicbook-rar")
        entries[1].acquisitions.first().first shouldBe "/opds/files/4/Saga%20001.cbz"
    }

    @Test
    fun `a downloaded File is byte-identical and named after the Publication`() = opds {
        val bytes = Fixtures.epub("café")
        val publicationId = seed.publication("Café: L'été", Format.EPUB to bytes)
        val href = feed("/opds/all").entries.single().acquisitions.single().first

        val response = client.get(href)

        response.status shouldBe HttpStatusCode.OK
        response.headers[HttpHeaders.ContentType] shouldBe "application/epub+zip"
        response.headers[HttpHeaders.ContentDisposition] shouldBe
            "attachment; filename=\"Caf__ L'_t_.epub\"; filename*=UTF-8''Caf%C3%A9_%20L%27%C3%A9t%C3%A9.epub"
        sha256(response.bodyAsBytes()) shouldBe seed.sha256Of(publicationId)
    }

    @Test
    fun `Trashed Publications and Files never appear and cannot be downloaded`() = opds {
        val trashed = seed.publication("Gone", Format.EPUB to Fixtures.epub("gone"))
        seed.publication("Kept", Format.EPUB to Fixtures.epub("kept"), Format.PDF to Fixtures.pdf("kept"))
        seed.trashPublication(trashed)
        seed.trashFile(Format.PDF)

        val entries = feed("/opds/all").entries
        entries.map { it.title } shouldBe listOf("Kept")
        entries.single().acquisitions.map { it.second } shouldBe listOf("application/epub+zip")
        feed("/opds/recent").titles shouldBe listOf("Kept")

        client.get("/opds/files/1/Gone.epub").status shouldBe HttpStatusCode.NotFound
        client.get("/opds/files/3/Kept.pdf").status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `answers 400 for a bad page and 404 for an unknown File`() = opds {
        client.get("/opds/all?page=0").status shouldBe HttpStatusCode.BadRequest
        client.get("/opds/recent?page=two").status shouldBe HttpStatusCode.BadRequest
        client.get("/opds/files/999/x.epub").status shouldBe HttpStatusCode.NotFound
    }

    private val seed = Seed()

    /** Writes Publications and their stored Files straight into the library. */
    private inner class Seed {
        private var files = 0

        fun publication(
            title: String,
            vararg files: Pair<Format, ByteArray>,
            sortTitle: String = title,
            createdAt: Instant = harness.clock.instant(),
        ): Long = transaction(harness.db) {
            val id = PublicationTable.insertAndGetId {
                it[kind] = if (files.any { (format) -> format == Format.CBZ || format == Format.CBR }) "comic" else "prose"
                it[PublicationTable.title] = title
                it[PublicationTable.sortTitle] = sortTitle
                it[PublicationTable.createdAt] = createdAt
            }.value
            files.forEach { (format, bytes) ->
                val storedPath = "seed/${++this@Seed.files}.${format.extension}"
                val hashes = Hasher.hash(Fixtures.write(harness.storage.resolve(storedPath), bytes))
                FileTable.insert {
                    it[publicationId] = id
                    it[FileTable.format] = format.dbValue
                    it[size] = hashes.size
                    it[sha256] = hashes.sha256
                    it[partialMd5] = hashes.partialMd5
                    it[role] = "original"
                    it[FileTable.storedPath] = storedPath
                    it[importedAt] = createdAt
                }
            }
            id
        }

        fun sha256Of(publicationId: Long): String = transaction(harness.db) {
            FileTable.select(FileTable.sha256).where { FileTable.publicationId eq publicationId }.single()[FileTable.sha256]
        }

        fun trashPublication(id: Long) = transaction(harness.db) {
            PublicationTable.update({ PublicationTable.id eq id }) { it[trashedAt] = harness.clock.instant() }
        }

        fun trashFile(format: Format) = transaction(harness.db) {
            FileTable.update({ FileTable.format eq format.dbValue }) { it[trashedAt] = harness.clock.instant() }
        }
    }

    private fun atom(type: String) = ContentType.parse(type).withParameter("charset", "utf-8")

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
