package theindex.files

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.publications.Kind
import java.nio.file.Files
import java.nio.file.Path

class StorageTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `lays out prose under the author and comics under their own folder`() {
        val storage = Storage(root)

        storage.layoutPath(Kind.PROSE, 7, "Dune", Format.EPUB) shouldBe "prose/Unknown/Dune (7)/Dune.epub"
        storage.layoutPath(Kind.COMIC, 8, "Saga 001", Format.CBZ) shouldBe "comics/Saga 001 (8)/Saga 001.cbz"
    }

    @Test
    fun `sanitizes path segments`() {
        Storage.segment("""AC/DC: "Live" <1979>?""") shouldBe "AC_DC_ _Live_ _1979__"
        Storage.segment("  ...hidden. ") shouldBe "hidden"
        Storage.segment("   ") shouldBe "Untitled"
        Storage.segment("x".repeat(300)).length shouldBe 100
    }

    @Test
    fun `deleting a stored file also removes the folders it leaves empty`() {
        val storage = Storage(root)
        val source = Files.writeString(root.resolve("source.epub"), "book")
        storage.copyAtomically(source, "prose/Unknown/Dune (7)/Dune.epub")

        storage.deleteStored("prose/Unknown/Dune (7)/Dune.epub")

        Files.exists(root.resolve("prose")) shouldBe false
        Files.exists(source) shouldBe true
    }
}
