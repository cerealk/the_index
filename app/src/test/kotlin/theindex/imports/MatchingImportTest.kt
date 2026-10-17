package theindex.imports

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import theindex.Fixtures
import theindex.TestDatabase
import theindex.files.Format
import theindex.metadata.ComicFixtures
import theindex.metadata.ExtractedIdentifier
import theindex.metadata.ExtractedMetadata
import theindex.metadata.Extraction
import theindex.metadata.Extractors
import theindex.metadata.RarFixtures
import theindex.persistence.FileTable
import theindex.persistence.PublicationTable
import java.nio.file.Path
import java.time.Instant

class MatchingImportTest {
    /** pdf extraction arrives with its own ticket; until then the tests say what a pdf holds. */
    private var pdfMetadata = ExtractedMetadata.NONE
    private val harness = ImportHarness(extract = { format: Format, path: Path ->
        if (format == Format.PDF) Extraction(pdfMetadata, pageCount = 320) else Extractors.extract(format, path)
    })

    @AfterEach
    fun cleanUp() {
        harness.close()
        TestDatabase.truncateAll()
    }

    private fun import(name: String, bytes: ByteArray): ImportJob {
        Fixtures.write(harness.config.inboxDir.resolve(name), bytes)
        val (jobId) = harness.scanUntilStable()
        harness.drain()
        return harness.jobs.find(jobId).shouldNotBeNull()
    }

    private fun epub(metadata: String, salt: String = "") = Fixtures.epub(opf = Fixtures.opf(metadata, comment = salt))

    private fun publication(id: Long) = transaction(harness.db) {
        PublicationTable.selectAll().where { PublicationTable.id eq id }.single()
    }

    private fun storedPaths(publicationId: Long): List<String> = transaction(harness.db) {
        FileTable.selectAll().where { FileTable.publicationId eq publicationId }.orderBy(FileTable.id).map { it[FileTable.storedPath] }
    }

    private fun publicationCount() = transaction(harness.db) { PublicationTable.selectAll().count() }

    @Test
    fun `the epub and pdf of the same book become one Publication, and the pdf only fills empty fields`() {
        val epub = import(
            "dune.epub",
            epub("<dc:title>Dune</dc:title><dc:creator>Frank Herbert</dc:creator><dc:language>en</dc:language><dc:identifier>urn:isbn:9780441013593</dc:identifier>"),
        )
        pdfMetadata = ExtractedMetadata(
            source = "test",
            title = "DUNE (scan)",
            publisher = "Ace",
            language = "fr",
            identifiers = listOf(ExtractedIdentifier("isbn", "9780441013593")),
        )
        val pdf = import("whatever.pdf", Fixtures.pdf())

        val id = epub.publicationId.shouldNotBeNull()
        pdf.state shouldBe ImportState.DONE
        pdf.publicationId shouldBe id
        pdf.reason shouldBe "attached to Publication $id: same isbn 9780441013593"
        publicationCount() shouldBe 1
        val publication = publication(id)
        publication[PublicationTable.title] shouldBe "Dune"
        publication[PublicationTable.language] shouldBe "en"
        publication[PublicationTable.publisher] shouldBe "Ace"
        storedPaths(id) shouldBe listOf("prose/Frank Herbert/Dune ($id)/Dune.epub", "prose/Frank Herbert/Dune ($id)/Dune.pdf")
        harness.storage.resolve(storedPaths(id)[1]).toFile().exists() shouldBe true
        transaction(harness.db) {
            FileTable.selectAll().where { FileTable.id eq pdf.fileId!! }.single()[FileTable.pageCount]
        } shouldBe 320
    }

    @Test
    fun `the same title and author attach without an identifier, beside the File already there`() {
        val first = import("a.epub", epub("<dc:title>Dune</dc:title><dc:creator>Frank Herbert</dc:creator>"))
        val second = import("b.epub", epub("<dc:title>dune</dc:title><dc:creator>Herbert, Frank</dc:creator>", salt = "other bytes"))

        val id = first.publicationId.shouldNotBeNull()
        second.publicationId shouldBe id
        second.reason shouldBe "attached to Publication $id: same title and author"
        storedPaths(id) shouldBe listOf("prose/Frank Herbert/Dune ($id)/Dune.epub", "prose/Frank Herbert/Dune ($id)/Dune (2).epub")
    }

    @Test
    fun `a comic attaches by Series and number`() {
        val first = import("a.cbz", ComicFixtures.cbz("<Series>Saga</Series><Number>1</Number><Volume>2012</Volume>", pages = 2))
        val second = import("b.cbz", ComicFixtures.cbz("<Series>saga</Series><Number>001</Number><Volume>2012</Volume><Publisher>Image</Publisher>", pages = 3))

        second.publicationId shouldBe first.publicationId
        second.reason shouldBe "attached to Publication ${first.publicationId}: same Series and number"
        publication(first.publicationId!!)[PublicationTable.publisher] shouldBe "Image"
    }

    @Test
    fun `an attached cbr gets its Conversion without touching the cbz already there`() {
        val cbzBytes = ComicFixtures.cbz("<Series>Saga</Series><Number>1</Number><Volume>2012</Volume>")
        val cbz = import("a.cbz", cbzBytes)
        val cbr = import("Saga 001 (2012).cbr", RarFixtures.bytes("rar5.rar"))

        val id = cbz.publicationId.shouldNotBeNull()
        cbr.publicationId shouldBe id
        val folder = "comics/Saga (2012)/Saga (2012) #1 ($id)"
        storedPaths(id) shouldBe listOf("$folder/Saga (2012) #1.cbz", "$folder/Saga (2012) #1.cbr", "$folder/Saga (2012) #1 (2).cbz")
        harness.storage.resolve("$folder/Saga (2012) #1.cbz").toFile().readBytes().toList() shouldBe cbzBytes.toList()
    }

    @Test
    fun `a different Kind or a different volume never matches`() {
        val isbn = "<dc:identifier>urn:isbn:9780441013593</dc:identifier>"
        import("dune.epub", epub("<dc:title>Dune</dc:title>$isbn"))
        val comic = import("comics/dune.epub", epub("<dc:title>Dune</dc:title>$isbn", salt = "comic"))
        import("a.cbz", ComicFixtures.issue("Saga", "1", volume = 2012))
        val otherVolume = import("b.cbz", ComicFixtures.issue("Saga", "1", volume = 2018))

        comic.reason shouldBe null
        otherVolume.reason shouldBe null
        publicationCount() shouldBe 4
    }

    @Test
    fun `a near-identical title is a new Publication flagged as a Possible Duplicate`() {
        val first = import("a.epub", epub("<dc:title>The Lord of the Rings</dc:title><dc:creator>J. R. R. Tolkien</dc:creator>"))
        val second = import("b.epub", epub("<dc:title>Lord of the Rings</dc:title><dc:creator>Tolkien, J.R.R.</dc:creator>"))

        val id = second.publicationId.shouldNotBeNull()
        id shouldBe (first.publicationId!! + 1)
        publication(id)[PublicationTable.possibleDuplicateOf]?.value shouldBe first.publicationId
        second.warnings shouldContain "possible duplicate of Publication ${first.publicationId}"
        publication(first.publicationId)[PublicationTable.possibleDuplicateOf] shouldBe null
    }

    @Test
    fun `different books by the same author are not flagged`() {
        import("a.epub", epub("<dc:title>Dune</dc:title><dc:creator>Frank Herbert</dc:creator>"))
        val messiah = import("b.epub", epub("<dc:title>Dune Messiah</dc:title><dc:creator>Frank Herbert</dc:creator>"))

        publication(messiah.publicationId!!)[PublicationTable.possibleDuplicateOf] shouldBe null
        messiah.warnings shouldNotContain "possible duplicate of Publication 1"
    }

    @Test
    fun `the same title by another author is not flagged`() {
        import("a.epub", epub("<dc:title>Persuasion</dc:title><dc:creator>Jane Austen</dc:creator>"))
        val other = import("b.epub", epub("<dc:title>Persuasion</dc:title><dc:creator>Robert Cialdini</dc:creator>"))

        publication(other.publicationId!!)[PublicationTable.possibleDuplicateOf] shouldBe null
        publicationCount() shouldBe 2
    }

    @Test
    fun `Trashed Publications are not matched, but a Trashed File's bytes are still skipped`() {
        val bytes = epub("<dc:title>Dune</dc:title><dc:creator>Frank Herbert</dc:creator>")
        val first = import("a.epub", bytes)
        transaction(harness.db) {
            PublicationTable.update({ PublicationTable.id eq first.publicationId!! }) { it[trashedAt] = Instant.parse("2026-10-17T00:00:00Z") }
            FileTable.update({ FileTable.id eq first.fileId!! }) { it[trashedAt] = Instant.parse("2026-10-17T00:00:00Z") }
        }

        import("again.epub", bytes).state shouldBe ImportState.SKIPPED
        val other = import("b.epub", epub("<dc:title>Dune</dc:title><dc:creator>Frank Herbert</dc:creator>", salt = "new"))
        other.publicationId shouldBe (first.publicationId!! + 1)
        other.reason shouldBe null
    }
}
