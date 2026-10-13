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
    fun `lays out prose under the author and Series, comics under their Series`() {
        val storage = Storage(root)
        fun prose(author: String? = null, series: String? = null, position: String? = null) =
            storage.layoutPath(Storage.Layout(Kind.PROSE, 7, "Dune", Format.EPUB, author, series, position))
        fun comic(series: String? = null, position: String? = null) =
            storage.layoutPath(Storage.Layout(Kind.COMIC, 8, "The Last Stand", Format.CBZ, series = series, position = position))

        prose() shouldBe "prose/Unknown/Dune (7)/Dune.epub"
        prose("Frank Herbert") shouldBe "prose/Frank Herbert/Dune (7)/Dune.epub"
        prose("Frank Herbert", "Dune Chronicles", "1") shouldBe "prose/Frank Herbert/Dune Chronicles/01 - Dune (7)/Dune.epub"
        prose("Frank Herbert", "Dune Chronicles", "1.5") shouldBe "prose/Frank Herbert/Dune Chronicles/01.5 - Dune (7)/Dune.epub"
        prose("Frank Herbert", "Dune Chronicles") shouldBe "prose/Frank Herbert/Dune Chronicles/Dune (7)/Dune.epub"
        comic() shouldBe "comics/The Last Stand (8)/The Last Stand.cbz"
        comic("Saga (2012)", "12") shouldBe
            "comics/Saga (2012)/Saga (2012) #12 - The Last Stand (8)/Saga (2012) #12 - The Last Stand.cbz"
        comic("Saga (2012)") shouldBe "comics/Saga (2012)/Saga (2012) - The Last Stand (8)/Saga (2012) - The Last Stand.cbz"
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
