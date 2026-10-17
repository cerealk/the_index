package theindex.metadata

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.Fixtures
import theindex.files.Format
import theindex.files.FormatDetector
import java.nio.file.Path

class CbzRepackTest {
    @TempDir
    lateinit var dir: Path

    private fun ComicArchive.contents(): Map<String, List<Byte>> = entryNames.associateWith { read(it).toList() }

    @Test
    fun `a cbr repacks into a cbz with the same entries, in order, byte for byte`() {
        for (fixture in listOf("rar4.rar", "rar5.rar", "rar4-solid.rar", "rar5-solid.rar")) {
            val cbr = Fixtures.write(dir.resolve(fixture), RarFixtures.bytes(fixture))
            val cbz = dir.resolve("$fixture.cbz")

            RarComicArchive.open(cbr).use { source -> CbzRepack.repack(source, cbz) }

            FormatDetector.detect(cbz) shouldBe null // no images in junrar's fixtures, so not detected as a comic…
            val repacked = ZipComicArchive.open(cbz).use { it.entryNames to it.contents() }
            RarComicArchive.open(cbr).use { source -> repacked shouldBe (source.entryNames to source.contents()) }
        }
    }

    @Test
    fun `the same cbr always repacks to the same bytes`() {
        val cbr = Fixtures.write(dir.resolve("in.cbr"), RarFixtures.bytes("rar5.rar"))
        val first = dir.resolve("first.cbz")
        val second = dir.resolve("second.cbz")

        RarComicArchive.open(cbr).use { CbzRepack.repack(it, first) }
        Thread.sleep(2100) // zip timestamps have two-second resolution
        RarComicArchive.open(cbr).use { CbzRepack.repack(it, second) }

        java.nio.file.Files.readAllBytes(first).toList() shouldBe java.nio.file.Files.readAllBytes(second).toList()
    }

    @Test
    fun `an image cbr would be detected as cbz after the repack`() {
        val source = ZipComicArchive.open(Fixtures.write(dir.resolve("in.cbz"), ComicFixtures.cbz(ComicFixtures.SAGA_1)))
        val cbz = dir.resolve("out.cbz")

        source.use { CbzRepack.repack(it, cbz) }

        FormatDetector.detect(cbz) shouldBe Format.CBZ
    }

    @Test
    fun `cbr files are extracted like cbz, with the file name as fallback`() {
        val extraction = Extractors.extract(Format.CBR, Fixtures.write(dir.resolve("Saga 003 (2012).cbr"), RarFixtures.bytes("rar5.rar")))

        extraction.metadata shouldBe ComicFileName.parse("Saga 003 (2012).cbr")
        extraction.pageCount shouldBe 0
    }
}
