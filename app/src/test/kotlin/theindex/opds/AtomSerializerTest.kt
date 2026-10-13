package theindex.opds

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import theindex.files.Format
import java.time.Instant

class AtomSerializerTest {
    @Test
    fun `writes an acquisition feed exactly as the fixture`() {
        val feed = Feed(
            id = "urn:the_index:all:page:2",
            title = "All",
            updated = Instant.parse("2026-10-12T10:00:00Z"),
            kind = FeedKind.ACQUISITION,
            self = "/opds/all?page=2",
            up = "/opds/",
            previous = "/opds/all",
            next = "/opds/all?page=3",
            entries = listOf(
                PublicationEntry(
                    id = "urn:the_index:publication:7",
                    title = "Tom & Jerry <1>",
                    updated = Instant.parse("2026-10-11T09:30:00Z"),
                    authors = listOf("Frank Herbert"),
                    summary = "A \"desert\" planet",
                    acquisitions = listOf(
                        Acquisition("/opds/files/12/Dune.epub", Format.EPUB, 1234),
                        Acquisition("/opds/files/13/Dune.pdf", Format.PDF, 5678),
                    ),
                ),
                PublicationEntry(
                    id = "urn:the_index:publication:8",
                    title = "Saga 001",
                    updated = Instant.parse("2026-10-11T09:31:00Z"),
                    acquisitions = listOf(Acquisition("/opds/files/14/Saga%20001.cbz", Format.CBZ, 99)),
                ),
            ),
        )

        val xml = AtomSerializer.write(feed)

        xml shouldBe fixture("acquisition-feed.xml")
        AtomFeed(xml).titles shouldBe listOf("Tom & Jerry <1>", "Saga 001")
    }

    companion object {
        fun fixture(name: String): String =
            AtomSerializerTest::class.java.getResource("/opds/$name")!!.readText()
    }
}
