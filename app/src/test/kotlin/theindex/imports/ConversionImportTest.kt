package theindex.imports

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
import theindex.metadata.ComicArchive
import theindex.metadata.RarComicArchive
import theindex.metadata.RarFixtures
import theindex.metadata.ZipComicArchive
import theindex.opds.OpdsCatalog
import theindex.opds.PublicationEntry
import theindex.persistence.FileTable
import java.io.IOException
import java.nio.file.Files

class ConversionImportTest {
    private var harness = ImportHarness()

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

    private data class StoredFile(val id: Long, val format: Format, val role: String, val derivedFrom: Long?, val path: String, val sha256: String)

    private fun files(publicationId: Long): List<StoredFile> = transaction(harness.db) {
        FileTable.selectAll().where { FileTable.publicationId eq publicationId }.orderBy(FileTable.id).map {
            StoredFile(
                id = it[FileTable.id].value,
                format = Format.fromDb(it[FileTable.format]),
                role = it[FileTable.role],
                derivedFrom = it[FileTable.derivedFrom]?.value,
                path = it[FileTable.storedPath],
                sha256 = it[FileTable.sha256].trim(),
            )
        }
    }

    private fun ComicArchive.contents(): Map<String, List<Byte>> = entryNames.associateWith { read(it).toList() }

    @Test
    fun `a RAR 4 cbr imports as an Original plus a cbz Conversion with the same contents`() = importsWithConversion("rar4.rar")

    @Test
    fun `a RAR 5 cbr imports as an Original plus a cbz Conversion with the same contents`() = importsWithConversion("rar5.rar")

    @Test
    fun `a Conversion identical to a File already in the library is skipped with a warning`() {
        import("Saga 001 (2012).cbr", RarFixtures.bytes("rar4.rar"))
        // Different RAR bytes, same entries: the cbz would be the first one's Conversion again.
        val job = import("Saga 002 (2012).cbr", RarFixtures.bytes("rar5.rar"))

        job.state shouldBe ImportState.DONE
        job.warnings.last() shouldStartWith "cbz Conversion is already in the library as File"
        files(job.publicationId!!).map { it.format } shouldBe listOf(Format.CBR)
    }

    private fun importsWithConversion(fixture: String) {
        val bytes = RarFixtures.bytes(fixture)
        val job = import("Saga 001 (2012).cbr", bytes)

        job.state shouldBe ImportState.DONE
        job.warnings shouldBe listOf("no ComicInfo.xml, the file name was parsed", "no Contributors in the metadata")
        val (original, conversion) = files(job.publicationId.shouldNotBeNull())
        original.format shouldBe Format.CBR
        original.role shouldBe "original"
        job.fileId shouldBe original.id
        conversion.format shouldBe Format.CBZ
        conversion.role shouldBe "conversion"
        conversion.derivedFrom shouldBe original.id
        conversion.path shouldBe original.path.removeSuffix(".cbr") + ".cbz"

        val originalPath = harness.storage.resolve(original.path)
        Files.readAllBytes(originalPath).toList() shouldBe bytes.toList()
        ZipComicArchive.open(harness.storage.resolve(conversion.path)).use { it.contents() } shouldBe
            RarComicArchive.open(originalPath).use { it.contents() }
    }

    @Test
    fun `OPDS offers both Files, the cbz Conversion first`() {
        val job = import("Saga 001 (2012).cbr", RarFixtures.bytes("rar5.rar"))

        val entry = OpdsCatalog(harness.db, harness.storage, harness.clock).all(1).entries.single() as PublicationEntry
        entry.acquisitions.map { it.format } shouldBe listOf(Format.CBZ, Format.CBR)
        files(job.publicationId!!).map { it.format } shouldBe listOf(Format.CBR, Format.CBZ)
    }

    @Test
    fun `a failed Conversion is a warning, and startup recovery makes the missing Conversion`() {
        harness.close()
        harness = ImportHarness(convert = { _, _ -> throw IOException("disk full") })

        val job = import("Saga 001 (2012).cbr", RarFixtures.bytes("rar5-solid.rar"))

        job.state shouldBe ImportState.DONE
        job.warnings.last() shouldBe "cbz Conversion failed: disk full"
        files(job.publicationId!!).map { it.format } shouldBe listOf(Format.CBR)

        ImportWorker(harness.db, harness.jobs, harness.library, harness.storage).recover()

        val (original, conversion) = files(job.publicationId)
        conversion.derivedFrom shouldBe original.id
        ZipComicArchive.open(harness.storage.resolve(conversion.path)).use { it.entryNames.size } shouldBe 9
    }

    @Test
    fun `an encrypted cbr fails the Import and is kept in failed`() {
        val job = import("secret.cbr", RarFixtures.bytes("rar5-encrypted-junrar.rar"))

        job.state shouldBe ImportState.FAILED
        job.reason shouldBe "cannot open cbr: the archive is encrypted"
        harness.storage.failed.resolve("${job.id}/secret.cbr").toFile().exists() shouldBe true
    }
}
