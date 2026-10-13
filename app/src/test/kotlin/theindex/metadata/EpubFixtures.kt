package theindex.metadata

import theindex.Fixtures

/** Realistic OPF packages: an EPUB 3 with refines, and an EPUB 2 as calibre writes it. */
object EpubFixtures {
    val EPUB3_METADATA = """
        <dc:identifier id="uid">urn:uuid:5f0e3b52-0000-4000-8000-000000000001</dc:identifier>
        <dc:identifier id="isbn">978-0-261-10235-4</dc:identifier>
        <meta refines="#isbn" property="identifier-type" scheme="onix:codelist5">15</meta>
        <dc:title id="t2">Being the First Part of The Lord of the Rings</dc:title>
        <meta refines="#t2" property="title-type">subtitle</meta>
        <dc:title id="t1">The Fellowship of the Ring</dc:title>
        <meta refines="#t1" property="title-type">main</meta>
        <dc:creator id="c1">J. R. R. Tolkien</dc:creator>
        <meta refines="#c1" property="role" scheme="marc:relators">aut</meta>
        <meta refines="#c1" property="file-as">Tolkien, J. R. R.</meta>
        <dc:creator id="c2">Alan Lee</dc:creator>
        <meta refines="#c2" property="role" scheme="marc:relators">ill</meta>
        <dc:contributor id="c3">calibre (7.0.0) [https://calibre-ebook.com]</dc:contributor>
        <meta refines="#c3" property="role" scheme="marc:relators">bkp</meta>
        <dc:language>en-GB</dc:language>
        <dc:publisher>HarperCollins</dc:publisher>
        <dc:date>1954-07-29</dc:date>
        <dc:subject>Fantasy</dc:subject>
        <dc:subject>fantasy</dc:subject>
        <dc:subject>Classics</dc:subject>
        <dc:description>&lt;p onclick="x()"&gt;The &lt;b&gt;first&lt;/b&gt; part.&lt;/p&gt;&lt;script&gt;alert(1)&lt;/script&gt;</dc:description>
        <meta property="belongs-to-collection" id="s1">The Lord of the Rings</meta>
        <meta refines="#s1" property="collection-type">series</meta>
        <meta refines="#s1" property="group-position">1</meta>
        <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
    """.trimIndent()

    val EPUB2_METADATA = """
        <dc:title>The Two Towers</dc:title>
        <dc:creator opf:role="aut" opf:file-as="Tolkien, J.R.R.">Tolkien, J.R.R.</dc:creator>
        <dc:contributor opf:role="bkp">calibre (5.0.0) [https://calibre-ebook.com]</dc:contributor>
        <dc:contributor opf:role="trl">Vittoria Alliata</dc:contributor>
        <dc:identifier opf:scheme="calibre" id="calibre_id">42</dc:identifier>
        <dc:identifier opf:scheme="uuid" id="uuid_id">0b1c2d3e-0000-4000-8000-000000000002</dc:identifier>
        <dc:identifier opf:scheme="ISBN">0-261-10236-2</dc:identifier>
        <dc:date opf:event="modification">2020-01-01</dc:date>
        <dc:date>1954-11-11T00:00:00+00:00</dc:date>
        <dc:language>ita</dc:language>
        <dc:description>A plain description &amp; nothing else</dc:description>
        <meta name="calibre:series" content="The Lord of the Rings"/>
        <meta name="calibre:series_index" content="2.0"/>
        <meta name="calibre:title_sort" content="Two Towers, The"/>
    """.trimIndent()

    fun epub3(): ByteArray = Fixtures.epub(opf = Fixtures.opf(EPUB3_METADATA))

    fun epub2(): ByteArray = Fixtures.epub(opf = Fixtures.opf(EPUB2_METADATA, version = "2.0"))

    /** An EPUB 3 with a title and an author but no Series. */
    fun standalone(title: String, author: String): ByteArray = Fixtures.epub(
        opf = Fixtures.opf("<dc:title>$title</dc:title><dc:creator>$author</dc:creator><dc:language>en</dc:language>"),
    )
}
