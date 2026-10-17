package theindex.metadata

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.Fixtures
import java.nio.file.Path

class ComicExtractorTest {
    @TempDir
    lateinit var dir: Path

    private fun extract(bytes: ByteArray, name: String = "comic.cbz") = ComicExtractor.extract(Fixtures.write(dir.resolve(name), bytes))

    @Test
    fun `reads ComicInfo fields, credits by role, genres as tags`() {
        extract(ComicFixtures.cbz(ComicFixtures.SAGA_1)).metadata shouldBe ExtractedMetadata(
            source = "comicinfo",
            title = "Chapter One",
            contributors = listOf(
                ExtractedContributor("Brian K. Vaughan", role = "writer"),
                ExtractedContributor("Fiona Staples", role = "penciller"),
                ExtractedContributor("Fiona Staples", role = "inker"),
                ExtractedContributor("Fiona Staples", role = "colorist"),
                ExtractedContributor("Fonografiks", role = "letterer"),
                ExtractedContributor("Fiona Staples", role = "cover artist"),
                ExtractedContributor("Eric Stephenson", role = "editor"),
                ExtractedContributor("Laura Tavishati", role = "editor"),
            ),
            series = ExtractedSeries("Saga", position = "1", volume = 2012),
            tags = listOf("Science Fiction", "Fantasy"),
            description = "When two soldiers from opposite sides of a never-ending galactic war fall in love...",
            publisher = "Image",
            language = "en",
            publicationDate = "2012-03-14",
        )
    }

    @Test
    fun `page count is the number of images, not ComicInfo's PageCount`() {
        val bytes = Fixtures.cbzEntries(
            "ComicInfo.xml" to ComicFixtures.comicInfo("<PageCount>99</PageCount>").toByteArray(),
            "pages/" to ByteArray(0),
            "pages/001.jpg" to byteArrayOf(1),
            "pages/002.PNG" to byteArrayOf(2),
            "__MACOSX/pages/._001.jpg" to byteArrayOf(3),
            "pages/.hidden.jpg" to byteArrayOf(4),
            "Thumbs.db" to byteArrayOf(5),
        )
        extract(bytes).pageCount shouldBe 2
    }

    @Test
    fun `several story arcs become Reading Lists, each with its own number`() {
        val bytes = ComicFixtures.issue("Saga", "2", storyArc = "The Will, Marko's Journey", storyArcNumber = "3, 1")
        extract(bytes).metadata.readingLists shouldBe listOf(
            ExtractedReadingList("The Will", number = "3"),
            ExtractedReadingList("Marko's Journey", number = "1"),
        )
    }

    @Test
    fun `a story arc without numbers is listed without a number`() {
        extract(ComicFixtures.issue("Saga", "2", storyArc = "The Will")).metadata.readingLists shouldBe
            listOf(ExtractedReadingList("The Will"))
    }

    @Test
    fun `unset ComicInfo values are absent`() {
        val metadata = extract(ComicFixtures.cbz("<Series>Saga</Series><Number></Number><Volume>-1</Volume><Year>-1</Year>")).metadata
        metadata.series shouldBe ExtractedSeries("Saga")
        metadata.publicationDate shouldBe null
    }

    @Test
    fun `ComicInfo without a Series takes series and number from the file name`() {
        extract(ComicFixtures.cbz("<Writer>Kieron Gillen</Writer>"), name = "The Wicked + The Divine 005 (2014).cbz").metadata shouldBe
            ExtractedMetadata(
                source = "comicinfo",
                contributors = listOf(ExtractedContributor("Kieron Gillen", role = "writer")),
                series = ExtractedSeries("The Wicked + The Divine", position = "5", volume = 2014),
            )
    }

    @Test
    fun `without ComicInfo the file name is parsed`() {
        val extraction = extract(ComicFixtures.cbz(pages = 4), name = "Saga 007 (2012).cbz")
        extraction.metadata shouldBe ComicFileName.parse("Saga 007 (2012).cbz")
        extraction.pageCount shouldBe 4
    }

    @Test
    fun `a corrupt archive cannot be opened`() {
        val error = shouldThrow<UnreadableFileException> { extract(Fixtures.cbz().copyOf(20)) }
        error.message!! shouldStartWith "cannot open cbz"
    }
}
