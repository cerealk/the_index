package theindex.metadata

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import theindex.publications.Names
import java.io.IOException
import java.nio.file.Path

/**
 * Reads a comic archive (spec §5 stage 3): ComicInfo.xml, filling its gaps from the file name,
 * or the file name alone when there is no ComicInfo. Pages are counted from the archive,
 * because ComicInfo's PageCount is often stale.
 */
object ComicExtractor {
    /** ComicInfo credit elements and the role each one gives, in Credit order. */
    private val CREDITS = listOf(
        "Writer" to "writer",
        "Penciller" to "penciller",
        "Inker" to "inker",
        "Colorist" to "colorist",
        "Letterer" to "letterer",
        "CoverArtist" to "cover artist",
        "Editor" to "editor",
    )

    fun extract(path: Path): Extraction = ZipComicArchive.open(path).use { extract(it, path.fileName.toString()) }

    fun extract(archive: ComicArchive, fileName: String): Extraction {
        val pageCount = archive.entryNames.count(ComicArchive::isPage)
        val fromName = ComicFileName.parse(fileName)
        val comicInfo = archive.entryNames
            .filter { it.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
            .minByOrNull { it.count { char -> char == '/' } }
            ?: return Extraction(fromName, pageCount)
        val document = try {
            Jsoup.parse(String(archive.read(comicInfo), Charsets.UTF_8), "", Parser.xmlParser())
        } catch (e: IOException) {
            throw UnreadableFileException("cannot open cbz: ${e.message}", e)
        }
        return Extraction(ComicInfo(document).metadata().fillGapsFrom(fromName), pageCount)
    }

    /** The file name only speaks where ComicInfo is silent: a Series comes whole, with its number and volume. */
    private fun ExtractedMetadata.fillGapsFrom(fromName: ExtractedMetadata) = copy(
        title = title ?: fromName.title?.takeIf { series == null },
        series = series ?: fromName.series,
    )

    private class ComicInfo(document: Document) {
        private val fields: Map<String, String> = document.allElements
            .filter { it.children().isEmpty() && it.text().isNotBlank() }
            .associate { it.localName().lowercase() to it.text().trim() }

        private fun field(name: String): String? = fields[name.lowercase()]

        /** ComicInfo writes -1 for "unset" numbers. */
        private fun number(name: String): Int? = field(name)?.toIntOrNull()?.takeIf { it > 0 }

        private fun list(name: String): List<String> =
            field(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        fun metadata() = ExtractedMetadata(
            source = SOURCE,
            title = field("Title"),
            contributors = CREDITS.flatMap { (element, role) -> list(element).map { ExtractedContributor(it, role = role) } },
            series = field("Series")?.let { ExtractedSeries(it, field("Number")?.let(Names::positionText), number("Volume")) },
            tags = list("Genre").distinctBy(Names::tagKey),
            description = field("Summary")?.let(Html::sanitize),
            publisher = field("Publisher"),
            language = field("LanguageISO")?.let(Languages::iso639),
            publicationDate = publicationDate(),
            readingLists = readingLists(),
        )

        private fun publicationDate(): String? {
            val year = number("Year") ?: return null
            val month = number("Month") ?: return PartialDate.parse("$year")
            val day = number("Day") ?: return PartialDate.parse("%04d-%02d".format(year, month))
            return PartialDate.parse("%04d-%02d-%02d".format(year, month, day))
        }

        /** `StoryArc` and `StoryArcNumber` are parallel comma-separated lists. */
        private fun readingLists(): List<ExtractedReadingList> {
            val numbers = list("StoryArcNumber")
            return list("StoryArc")
                .mapIndexed { index, name -> ExtractedReadingList(name, numbers.getOrNull(index)?.let(Names::positionText)) }
                .distinctBy { Names.readingListKey(it.name) }
        }
    }

    const val SOURCE = "comicinfo"
}
