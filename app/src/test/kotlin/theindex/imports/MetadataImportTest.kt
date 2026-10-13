package theindex.imports

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import theindex.Fixtures
import theindex.TestDatabase
import theindex.metadata.EpubFixtures
import theindex.persistence.ContributorTable
import theindex.persistence.CreditTable
import theindex.persistence.FileTable
import theindex.persistence.IdentifierTable
import theindex.persistence.PublicationTable
import theindex.persistence.PublicationTagTable
import theindex.persistence.SeriesTable
import theindex.persistence.TagTable
import kotlin.io.path.exists

class MetadataImportTest {
    private val harness = ImportHarness()

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

    private fun credits(publicationId: Long) = transaction(harness.db) {
        (CreditTable innerJoin ContributorTable).selectAll()
            .where { CreditTable.publicationId eq publicationId }
            .orderBy(CreditTable.position)
            .map { Triple(it[ContributorTable.name], it[ContributorTable.sortName], it[CreditTable.role]) }
    }

    @Test
    fun `an EPUB 3 imports with its fields, Credits, Series position, Tags and identifiers`() {
        val job = import("whatever.epub", EpubFixtures.epub3())

        job.state shouldBe ImportState.DONE
        job.warnings.shouldBeEmpty()
        val id = job.publicationId.shouldNotBeNull()
        transaction(harness.db) {
            val publication = (PublicationTable innerJoin SeriesTable).selectAll().where { PublicationTable.id eq id }.single()
            publication[PublicationTable.title] shouldBe "The Fellowship of the Ring"
            publication[PublicationTable.sortTitle] shouldBe "Fellowship of the Ring, The"
            publication[PublicationTable.description] shouldBe "<p>The <b>first</b> part.</p>"
            publication[PublicationTable.publisher] shouldBe "HarperCollins"
            publication[PublicationTable.language] shouldBe "en"
            publication[PublicationTable.publicationDate] shouldBe "1954-07-29"
            publication[SeriesTable.name] shouldBe "The Lord of the Rings"
            publication[PublicationTable.seriesPositionText] shouldBe "1"
            publication[PublicationTable.seriesPositionSort]!!.compareTo("1".toBigDecimal()) shouldBe 0

            (PublicationTagTable innerJoin TagTable).selectAll().where { PublicationTagTable.publicationId eq id }
                .map { it[TagTable.name] }.toSet() shouldBe setOf("Fantasy", "Classics")
            IdentifierTable.selectAll().where { IdentifierTable.publicationId eq id }
                .map { it[IdentifierTable.type] to it[IdentifierTable.value] }.toSet() shouldBe
                setOf("uuid" to "5f0e3b52-0000-4000-8000-000000000001", "isbn" to "9780261102354")

            FileTable.selectAll().where { FileTable.id eq job.fileId!! }.single()[FileTable.storedPath] shouldBe
                "prose/J. R. R. Tolkien/The Lord of the Rings/01 - The Fellowship of the Ring ($id)/The Fellowship of the Ring.epub"
        }
        credits(id) shouldBe listOf(
            Triple("J. R. R. Tolkien", "Tolkien, J. R. R.", "author"),
            Triple("Alan Lee", "Lee, Alan", "illustrator"),
        )
        job.metadata.shouldNotBeNull().contributors.size shouldBe 2
    }

    @Test
    fun `an EPUB 2 by calibre imports with its sort title, translator and Series index`() {
        val job = import("two-towers.epub", EpubFixtures.epub2())

        val id = job.publicationId.shouldNotBeNull()
        transaction(harness.db) {
            val publication = PublicationTable.selectAll().where { PublicationTable.id eq id }.single()
            publication[PublicationTable.sortTitle] shouldBe "Two Towers, The"
            publication[PublicationTable.language] shouldBe "it"
            publication[PublicationTable.seriesPositionText] shouldBe "2"
            IdentifierTable.selectAll().where { IdentifierTable.publicationId eq id }
                .map { it[IdentifierTable.type] }.toSet() shouldBe setOf("uuid", "isbn")
        }
        credits(id) shouldBe listOf(
            Triple("J.R.R. Tolkien", "Tolkien, J.R.R.", "author"),
            Triple("Vittoria Alliata", "Alliata, Vittoria", "translator"),
        )
    }

    @Test
    fun `two epubs by differently written Tolkiens share one Contributor and one Series`() {
        val first = import("one.epub", EpubFixtures.epub3())
        val second = import("two.epub", EpubFixtures.epub2())

        transaction(harness.db) {
            val tolkiens = ContributorTable.selectAll().where { ContributorTable.matchKey eq "j r r tolkien" }.toList()
            tolkiens.size shouldBe 1
            CreditTable.selectAll().where { CreditTable.contributorId eq tolkiens.single()[ContributorTable.id] }
                .map { it[CreditTable.publicationId].value }.toSet() shouldBe setOf(first.publicationId, second.publicationId)

            SeriesTable.selectAll().count() shouldBe 1
            PublicationTable.selectAll().where { PublicationTable.seriesId.isNotNull() }
                .orderBy(PublicationTable.seriesPositionSort, SortOrder.ASC)
                .map { it[PublicationTable.title] } shouldBe listOf("The Fellowship of the Ring", "The Two Towers")
        }
        // The second book lands in the folder named after the existing Contributor.
        harness.storage.resolve(
            "prose/J. R. R. Tolkien/The Lord of the Rings/02 - The Two Towers (${second.publicationId})/The Two Towers.epub",
        ).exists() shouldBe true
    }

    @Test
    fun `missing metadata falls back to the file name with warnings`() {
        val job = import("Le_Petit_Prince.epub", Fixtures.epub())

        job.state shouldBe ImportState.DONE
        job.warnings shouldBe listOf("no title in the metadata, the file name was used", "no Contributors in the metadata")
        transaction(harness.db) {
            PublicationTable.selectAll().where { PublicationTable.id eq job.publicationId!! }.single()[PublicationTable.title] shouldBe
                "Le Petit Prince"
        }
    }

    @Test
    fun `a placeholder author such as calibre's Unknown is not a Contributor`() {
        val job = import("math.epub", Fixtures.epub(opf = Fixtures.opf("<dc:title>Math</dc:title><dc:creator>Unknown</dc:creator>")))

        job.warnings shouldBe listOf("no Contributors in the metadata")
        transaction(harness.db) { ContributorTable.selectAll().count() } shouldBe 0
    }

    @Test
    fun `a corrupt epub yields a failed job and is kept in failed`() {
        val bytes = Fixtures.epubEntries("OEBPS/chapter1.xhtml" to "<html/>")
        val job = import("broken.epub", bytes)

        job.state shouldBe ImportState.FAILED
        job.reason.shouldNotBeNull() shouldStartWith "cannot open epub"
        harness.storage.failed.resolve("${job.id}/broken.epub").exists() shouldBe true
        transaction(harness.db) { PublicationTable.selectAll().count() } shouldBe 0
    }
}
