package theindex.imports

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import theindex.Fixtures
import theindex.TestDatabase
import theindex.files.Format
import theindex.files.Hasher
import theindex.files.Storage
import theindex.persistence.FileTable
import theindex.persistence.PublicationTable
import theindex.publications.Kind
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes

class ImportPipelineTest {
    private val harness = ImportHarness()
    private val inboxDir: Path = harness.config.inboxDir

    @AfterEach
    fun cleanUp() {
        harness.close()
        TestDatabase.truncateAll()
    }

    @Test
    fun `an epub dropped in the inbox is stored byte-identical as the Original of a new Publication`() {
        val bytes = Fixtures.epub()
        Fixtures.write(inboxDir.resolve("Dune.epub"), bytes)

        val (jobId) = harness.scanUntilStable()
        harness.drain()

        val job = harness.jobs.find(jobId).shouldNotBeNull()
        job.state shouldBe ImportState.DONE
        val publicationId = job.publicationId.shouldNotBeNull()
        val storedPath = "prose/Unknown/Dune ($publicationId)/Dune.epub"
        harness.storage.resolve(storedPath).readBytes() shouldBe bytes

        transaction(harness.db) {
            val publication = PublicationTable.selectAll().where { PublicationTable.id eq publicationId }.single()
            publication[PublicationTable.title] shouldBe "Dune"
            publication[PublicationTable.kind] shouldBe "prose"
            val file = FileTable.selectAll().where { FileTable.id eq job.fileId!! }.single()
            file[FileTable.storedPath] shouldBe storedPath
            file[FileTable.format] shouldBe "epub"
            file[FileTable.role] shouldBe "original"
            file[FileTable.sha256] shouldBe Hasher.hash(harness.storage.resolve(storedPath)).sha256
        }
        inboxDir.listDirectoryEntries().shouldBeEmpty()
        harness.storage.staging.listDirectoryEntries().shouldBeEmpty()
    }

    @Test
    fun `comic archives are stored as comics`() {
        Fixtures.write(inboxDir.resolve("Saga 001.cbz"), Fixtures.cbz())

        val (jobId) = harness.scanUntilStable()
        harness.drain()

        val job = harness.jobs.find(jobId).shouldNotBeNull()
        harness.storage.resolve("comics/Saga/Saga #1 (${job.publicationId})/Saga #1.cbz").exists() shouldBe true
    }

    @Test
    fun `the same bytes dropped again are skipped and the inbox copy is gone`() {
        val bytes = Fixtures.epub()
        Fixtures.write(inboxDir.resolve("Dune.epub"), bytes)
        harness.scanUntilStable()
        harness.drain()

        Fixtures.write(inboxDir.resolve("Dune again.epub"), bytes)
        val (jobId) = harness.scanUntilStable()
        harness.drain()

        val job = harness.jobs.find(jobId).shouldNotBeNull()
        job.state shouldBe ImportState.SKIPPED
        job.reason.shouldNotBeNull() shouldStartWith "already in library"
        inboxDir.listDirectoryEntries().shouldBeEmpty()
        harness.storage.staging.listDirectoryEntries().shouldBeEmpty()
        transaction(harness.db) { FileTable.selectAll().count() } shouldBe 1
    }

    @Test
    fun `a file that is not an ebook fails and is kept in failed`() {
        Fixtures.write(inboxDir.resolve("notes.txt"), Fixtures.text())

        val (jobId) = harness.scanUntilStable()
        harness.drain()

        val job = harness.jobs.find(jobId).shouldNotBeNull()
        job.state shouldBe ImportState.FAILED
        job.reason shouldBe "Format not recognized"
        harness.storage.failed.resolve("$jobId/notes.txt").readBytes() shouldBe Fixtures.text()
    }

    @Test
    fun `files still changing, hidden files and the hint folders are left alone`() {
        val growing = Fixtures.write(inboxDir.resolve("copying.epub"), Fixtures.epub())
        Fixtures.write(inboxDir.resolve(".DS_Store"), Fixtures.text())
        Files.createDirectories(inboxDir.resolve("comics"))

        harness.inbox.scan()
        harness.clock.advance(ImportHarness.STABILITY)
        Files.write(growing, Fixtures.epub("more bytes arrived"))
        harness.inbox.scan().shouldBeEmpty()

        harness.clock.advance(ImportHarness.STABILITY)
        harness.inbox.scan() shouldHaveSize 1
        inboxDir.resolve(".DS_Store").exists() shouldBe true
        inboxDir.resolve("comics").exists() shouldBe true
    }

    @Test
    fun `drained subfolders are removed and hint folders force the Kind`() {
        Fixtures.write(inboxDir.resolve("comics/batch/Odd.epub"), Fixtures.epub())

        val (jobId) = harness.scanUntilStable()
        harness.drain()

        harness.jobs.find(jobId)!!.kindHint shouldBe Kind.COMIC
        inboxDir.resolve("comics/batch").exists() shouldBe false
        inboxDir.resolve("comics").exists() shouldBe true
    }

    @Test
    fun `a crash mid-store is recovered without duplicates`() {
        Fixtures.write(inboxDir.resolve("Dune.epub"), Fixtures.epub())
        val (jobId) = harness.scanUntilStable()

        // Simulate a crash after the copy, before the rows committed.
        val claimed = harness.jobs.claimNext().shouldNotBeNull()
        val orphan = harness.storage.layoutPath(
            Storage.Layout(Kind.PROSE, harness.library.reservePublicationId(), "Dune", Format.EPUB),
        )
        harness.jobs.recordStoreTarget(claimed.id, orphan)
        harness.storage.copyAtomically(harness.worker.stagedFile(claimed), orphan)

        harness.worker.recover()
        harness.drain()

        harness.jobs.find(jobId)!!.state shouldBe ImportState.DONE
        harness.storage.resolve(orphan).exists() shouldBe false
        transaction(harness.db) { FileTable.selectAll().count() } shouldBe 1
    }

    @Test
    fun `nothing is enqueued before the stability window has passed`() {
        Fixtures.write(inboxDir.resolve("Dune.epub"), Fixtures.epub())

        harness.inbox.scan().shouldBeEmpty()
        harness.clock.advance(Duration.ofSeconds(1))
        harness.inbox.scan().shouldBeEmpty()
    }
}
