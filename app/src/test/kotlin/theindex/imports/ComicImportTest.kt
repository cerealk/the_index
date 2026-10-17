package theindex.imports

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import theindex.Fixtures
import theindex.TestDatabase
import theindex.metadata.ComicFixtures
import theindex.persistence.ContributorTable
import theindex.persistence.CreditTable
import theindex.persistence.FileTable
import theindex.persistence.PublicationTable
import theindex.persistence.PublicationTagTable
import theindex.persistence.ReadingListEntryTable
import theindex.persistence.ReadingListTable
import theindex.persistence.SeriesTable
import theindex.persistence.TagTable

class ComicImportTest {
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

    private fun publication(id: Long) = transaction(harness.db) {
        PublicationTable.selectAll().where { PublicationTable.id eq id }.single()
    }

    private fun seriesOf(id: Long) = transaction(harness.db) {
        val seriesId = publication(id)[PublicationTable.seriesId].shouldNotBeNull()
        SeriesTable.selectAll().where { SeriesTable.id eq seriesId }.single()
            .let { it[SeriesTable.name] to it[SeriesTable.volume] }
    }

    /** Each Reading List's Publications, in list order. */
    private fun readingLists(): Map<String, List<Long>> = transaction(harness.db) {
        (ReadingListEntryTable innerJoin ReadingListTable).selectAll()
            .orderBy(ReadingListEntryTable.position)
            .groupBy({ it[ReadingListTable.name] }, { it[ReadingListEntryTable.publicationId].value })
    }

    @Test
    fun `a cbz with ComicInfo imports its Series, position, Credits, Tags and page count`() {
        val job = import("whatever.cbz", ComicFixtures.cbz(ComicFixtures.SAGA_1, pages = 5))

        job.state shouldBe ImportState.DONE
        job.warnings.shouldBeEmpty()
        val id = job.publicationId.shouldNotBeNull()
        val publication = publication(id)
        publication[PublicationTable.kind] shouldBe "comic"
        publication[PublicationTable.title] shouldBe "Chapter One"
        publication[PublicationTable.publisher] shouldBe "Image"
        publication[PublicationTable.publicationDate] shouldBe "2012-03-14"
        publication[PublicationTable.seriesPositionText] shouldBe "1"
        publication[PublicationTable.seriesPositionSort]!!.compareTo("1".toBigDecimal()) shouldBe 0
        seriesOf(id) shouldBe ("Saga" to 2012)

        transaction(harness.db) {
            (CreditTable innerJoin ContributorTable).selectAll()
                .where { CreditTable.publicationId eq id }
                .orderBy(CreditTable.position)
                .map { it[ContributorTable.name] to it[CreditTable.role] } shouldBe listOf(
                "Brian K. Vaughan" to "writer",
                "Fiona Staples" to "penciller",
                "Fiona Staples" to "inker",
                "Fiona Staples" to "colorist",
                "Fonografiks" to "letterer",
                "Fiona Staples" to "cover artist",
                "Eric Stephenson" to "editor",
                "Laura Tavishati" to "editor",
            )
            ContributorTable.selectAll().count() shouldBe 5
            (PublicationTagTable innerJoin TagTable).selectAll().where { PublicationTagTable.publicationId eq id }
                .map { it[TagTable.name] }.toSet() shouldBe setOf("Science Fiction", "Fantasy")

            val file = FileTable.selectAll().where { FileTable.id eq job.fileId!! }.single()
            file[FileTable.pageCount] shouldBe 5
            file[FileTable.storedPath] shouldBe
                "comics/Saga (2012)/Saga (2012) #1 - Chapter One ($id)/Saga (2012) #1 - Chapter One.cbz"
        }
    }

    @Test
    fun `the same Series name with two volumes is two Series`() {
        val first = import("a.cbz", ComicFixtures.issue("Saga", "1", volume = 2012))
        val second = import("b.cbz", ComicFixtures.issue("Saga", "1", volume = 2018))

        seriesOf(first.publicationId!!) shouldBe ("Saga" to 2012)
        seriesOf(second.publicationId!!) shouldBe ("Saga" to 2018)
        transaction(harness.db) { SeriesTable.selectAll().count() } shouldBe 2
    }

    @Test
    fun `story arc issues land in their Reading Lists in number order`() {
        val five = import("5.cbz", ComicFixtures.issue("Saga", "5", storyArc = "The Will", storyArcNumber = "3"))
        val six = import("6.cbz", ComicFixtures.issue("Saga", "6", storyArc = "The Will, Marko's Journey", storyArcNumber = "1, 2"))
        val seven = import("7.cbz", ComicFixtures.issue("Saga", "7", storyArc = "Marko's Journey", storyArcNumber = "1"))
        val eight = import("8.cbz", ComicFixtures.issue("Saga", "8", storyArc = "the will"))
        val nine = import("9.cbz", ComicFixtures.issue("Saga", "9", storyArc = "The Will", storyArcNumber = "2"))

        readingLists() shouldBe mapOf(
            // Numbered issues in number order; the unnumbered one stays where it was appended.
            "The Will" to listOf(six, nine, five, eight).map { it.publicationId },
            "Marko's Journey" to listOf(seven, six).map { it.publicationId },
        )
    }

    @Test
    fun `a cbz without ComicInfo is read from its file name, with a warning`() {
        val job = import("Saga 007 (2012).cbz", ComicFixtures.cbz(pages = 2))

        job.state shouldBe ImportState.DONE
        job.warnings shouldBe listOf("no ComicInfo.xml, the file name was parsed", "no Contributors in the metadata")
        val id = job.publicationId.shouldNotBeNull()
        publication(id)[PublicationTable.title] shouldBe "Saga #7"
        publication(id)[PublicationTable.seriesPositionText] shouldBe "7"
        seriesOf(id) shouldBe ("Saga" to 2012)
        transaction(harness.db) {
            FileTable.selectAll().where { FileTable.id eq job.fileId!! }.single()[FileTable.storedPath]
        } shouldBe "comics/Saga (2012)/Saga (2012) #7 ($id)/Saga (2012) #7.cbz"
    }
}
