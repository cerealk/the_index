package theindex.files

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.Fixtures
import java.nio.file.Path

class FormatDetectorTest {
    @TempDir
    lateinit var dir: Path

    private fun detect(name: String, bytes: ByteArray) = FormatDetector.detect(Fixtures.write(dir.resolve(name), bytes))

    @Test
    fun `recognizes each Format by its bytes`() {
        detect("a.epub", Fixtures.epub()) shouldBe Format.EPUB
        detect("a.cbz", Fixtures.cbz()) shouldBe Format.CBZ
        detect("a.pdf", Fixtures.pdf()) shouldBe Format.PDF
        detect("a.cbr", Fixtures.cbr()) shouldBe Format.CBR
    }

    @Test
    fun `ignores the file extension`() {
        detect("really-a-comic.epub", Fixtures.cbz()) shouldBe Format.CBZ
        detect("no-extension", Fixtures.pdf()) shouldBe Format.PDF
    }

    @Test
    fun `rejects files that are not ebooks or comics`() {
        detect("notes.txt", Fixtures.text()) shouldBe null
        detect("archive.zip", Fixtures.zipWithoutImages()) shouldBe null
        detect("empty", ByteArray(0)) shouldBe null
    }
}
