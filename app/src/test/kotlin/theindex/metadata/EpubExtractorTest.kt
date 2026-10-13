package theindex.metadata

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.Fixtures
import java.nio.file.Path

class EpubExtractorTest {
    @TempDir
    lateinit var dir: Path

    private fun extract(bytes: ByteArray) = EpubExtractor.extract(Fixtures.write(dir.resolve("book.epub"), bytes))

    @Test
    fun `reads an EPUB 3 package with refines and belongs-to-collection`() {
        extract(EpubFixtures.epub3()) shouldBe ExtractedMetadata(
            source = "opf",
            title = "The Fellowship of the Ring",
            sortTitle = null,
            contributors = listOf(
                ExtractedContributor("J. R. R. Tolkien", fileAs = "Tolkien, J. R. R.", role = "aut"),
                ExtractedContributor("Alan Lee", role = "ill"),
            ),
            series = ExtractedSeries("The Lord of the Rings", position = "1"),
            tags = listOf("Fantasy", "Classics"),
            description = "<p>The <b>first</b> part.</p>",
            publisher = "HarperCollins",
            language = "en",
            publicationDate = "1954-07-29",
            identifiers = listOf(
                ExtractedIdentifier("uuid", "5f0e3b52-0000-4000-8000-000000000001"),
                ExtractedIdentifier("isbn", "9780261102354"),
            ),
        )
    }

    @Test
    fun `reads an EPUB 2 package as calibre writes it`() {
        extract(EpubFixtures.epub2()) shouldBe ExtractedMetadata(
            source = "opf",
            title = "The Two Towers",
            sortTitle = "Two Towers, The",
            contributors = listOf(
                ExtractedContributor("Tolkien, J.R.R.", fileAs = "Tolkien, J.R.R.", role = "aut"),
                ExtractedContributor("Vittoria Alliata", role = "trl"),
            ),
            series = ExtractedSeries("The Lord of the Rings", position = "2"),
            description = "A plain description &amp; nothing else",
            language = "it",
            publicationDate = "1954-11-11",
            identifiers = listOf(
                ExtractedIdentifier("uuid", "0b1c2d3e-0000-4000-8000-000000000002"),
                ExtractedIdentifier("isbn", "0261102362"),
            ),
        )
    }

    @Test
    fun `identifiers as calibre really writes them`() {
        fun identifiers(vararg markup: String) =
            extract(Fixtures.epub(opf = Fixtures.opf(markup.joinToString("\n")))).identifiers

        // EPUB 3 by calibre: a row number, the uuid, and a bare ISBN as the package identifier.
        identifiers(
            "<dc:identifier>calibre:734</dc:identifier>",
            "<dc:identifier>uuid:A96193F2-15B3-497B-B0C6-2738ED3FE04F</dc:identifier>",
            "<dc:identifier id=\"bookid\">9791224202684</dc:identifier>",
        ) shouldBe listOf(
            ExtractedIdentifier("uuid", "a96193f2-15b3-497b-b0c6-2738ed3fe04f"),
            ExtractedIdentifier("isbn", "9791224202684"),
        )
        // EPUB 2 with a garbage uuid; a bare number that is not an ISBN-13 is dropped.
        identifiers(
            "<dc:identifier opf:scheme=\"MOBI-ASIN\">B0798DH1K8</dc:identifier>",
            "<dc:identifier opf:scheme=\"calibre\">bb778dbb-2643-4ae3-ac0f-f3fa760458d2</dc:identifier>",
            "<dc:identifier id=\"uuid_id\" opf:scheme=\"uuid\">123</dc:identifier>",
            "<dc:identifier>9791224202685</dc:identifier>",
        ) shouldBe listOf(ExtractedIdentifier("mobi-asin", "B0798DH1K8"))
    }

    @Test
    fun `an epub with no metadata yields empty fields`() {
        extract(Fixtures.epub()) shouldBe ExtractedMetadata(source = "opf")
    }

    @Test
    fun `belongs-to-collection that is not a series is ignored`() {
        val opf = Fixtures.opf(
            """
            <meta property="belongs-to-collection" id="c">Penguin Classics</meta>
            <meta refines="#c" property="collection-type">set</meta>
            """,
        )
        extract(Fixtures.epub(opf = opf)).series shouldBe null
    }

    @Test
    fun `an epub without a package document cannot be opened`() {
        shouldThrow<UnreadableFileException> { extract(Fixtures.epubEntries("OEBPS/chapter1.xhtml" to "<html/>")) }
            .message shouldBe "cannot open epub: no META-INF/container.xml"
    }

    @Test
    fun `a DRM-encrypted epub cannot be opened, but font obfuscation is fine`() {
        fun encryption(algorithm: String) = """
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData><enc:EncryptionMethod Algorithm="$algorithm"/></enc:EncryptedData>
            </encryption>
        """.trimIndent()

        val drm = Fixtures.epubEntries(
            "META-INF/container.xml" to Fixtures.CONTAINER,
            "META-INF/encryption.xml" to encryption("http://www.w3.org/2001/04/xmlenc#aes128-cbc"),
            "OEBPS/content.opf" to Fixtures.opf(),
        )
        shouldThrow<UnreadableFileException> { extract(drm) }.message shouldStartWith "epub is encrypted"

        val fonts = Fixtures.epubEntries(
            "META-INF/container.xml" to Fixtures.CONTAINER,
            "META-INF/encryption.xml" to encryption("http://www.idpf.org/2008/embedding"),
            "OEBPS/content.opf" to Fixtures.opf("<dc:title>Fine</dc:title>"),
        )
        extract(fonts).title shouldBe "Fine"
    }

    @Test
    fun `a truncated zip cannot be opened`() {
        val bytes = Fixtures.epub()
        shouldThrow<UnreadableFileException> { extract(bytes.copyOf(bytes.size / 2)) }
    }
}
