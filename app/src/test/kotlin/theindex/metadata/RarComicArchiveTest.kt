package theindex.metadata

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.Fixtures
import java.nio.file.Path

class RarComicArchiveTest {
    @TempDir
    lateinit var dir: Path

    private fun open(fixture: String) = RarComicArchive.open(Fixtures.write(dir.resolve("comic.cbr"), RarFixtures.bytes(fixture)))

    private fun ComicArchive.contents(): Map<String, String> = entryNames.associateWith { String(read(it)) }

    @Test
    fun `reads a RAR 4 archive`() {
        open("rar4.rar").use { it.contents() } shouldBe mapOf("FILE1.TXT" to "file1\r\n", "FILE2.TXT" to "file2\r\n")
    }

    @Test
    fun `reads a RAR 5 archive`() {
        open("rar5.rar").use { it.contents() } shouldBe mapOf("FILE1.TXT" to "file1\r\n", "FILE2.TXT" to "file2\r\n")
    }

    @Test
    fun `reads solid archives in any order`() {
        open("rar5-solid.rar").use { archive ->
            archive.entryNames shouldBe (1..9).map { "file$it.txt" }
            // Solid archives decompress sequentially; reading backwards must still work.
            archive.entryNames.reversed().forEach { name -> String(archive.read(name)) shouldBe "${name.removeSuffix(".txt")}\n" }
        }
        open("rar4-solid.rar").use { archive ->
            archive.entryNames.shouldNotBeEmpty()
            archive.entryNames.forEach { name -> String(archive.read(name)) shouldBe "${name.removeSuffix(".txt")}\n" }
        }
    }

    @Test
    fun `an encrypted archive cannot be opened`() {
        shouldThrow<UnreadableFileException> { open("rar5-encrypted-junrar.rar") }.message shouldBe
            "cannot open cbr: the archive is encrypted"
    }

    @Test
    fun `a truncated archive cannot be opened`() {
        shouldThrow<UnreadableFileException> {
            RarComicArchive.open(Fixtures.write(dir.resolve("bad.cbr"), Fixtures.cbr())).use { it.contents() }
        }.message!! shouldStartWith "cannot open cbr"
    }
}
