package theindex.metadata

import theindex.Fixtures

/** cbz archives with or without a ComicInfo.xml, as comic taggers (ComicTagger, Mylar) write them. */
object ComicFixtures {
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    val SAGA_1 = """
        <Title>Chapter One</Title>
        <Series>Saga</Series>
        <Number>1</Number>
        <Volume>2012</Volume>
        <Count>54</Count>
        <Summary>When two soldiers from opposite sides of a never-ending galactic war fall in love...</Summary>
        <Year>2012</Year>
        <Month>3</Month>
        <Day>14</Day>
        <Writer>Brian K. Vaughan</Writer>
        <Penciller>Fiona Staples</Penciller>
        <Inker>Fiona Staples</Inker>
        <Colorist>Fiona Staples</Colorist>
        <Letterer>Fonografiks</Letterer>
        <CoverArtist>Fiona Staples</CoverArtist>
        <Editor>Eric Stephenson, Laura Tavishati</Editor>
        <Publisher>Image</Publisher>
        <Genre>Science Fiction, Fantasy, science fiction</Genre>
        <LanguageISO>en</LanguageISO>
        <PageCount>99</PageCount>
        <Characters>Alana, Marko</Characters>
    """.trimIndent()

    /** A ComicInfo.xml document around [fields]. */
    fun comicInfo(fields: String): String = """<?xml version="1.0" encoding="utf-8"?>
<ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
$fields
</ComicInfo>
"""

    /** A cbz with [pages] images and, when given, a ComicInfo.xml holding [fields]. */
    fun cbz(fields: String? = null, pages: Int = 3): ByteArray {
        val images = (1..pages).map { "pages/%03d.jpg".format(it) to JPEG + it.toByte() }
        val info = fields?.let { listOf("ComicInfo.xml" to comicInfo(it).toByteArray()) } ?: emptyList()
        return Fixtures.cbzEntries(*(info + images).toTypedArray())
    }

    /** One issue of a Series, optionally part of story arcs given as ComicInfo writes them. */
    fun issue(series: String, number: String, volume: Int? = null, storyArc: String? = null, storyArcNumber: String? = null) = cbz(
        listOfNotNull(
            "<Series>$series</Series>",
            "<Number>$number</Number>",
            volume?.let { "<Volume>$it</Volume>" },
            "<Writer>Brian K. Vaughan</Writer>",
            storyArc?.let { "<StoryArc>$it</StoryArc>" },
            storyArcNumber?.let { "<StoryArcNumber>$it</StoryArcNumber>" },
        ).joinToString("\n"),
    )
}
