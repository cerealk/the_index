package theindex.metadata

import theindex.publications.Names
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

/**
 * The fallback for comics without ComicInfo.xml (spec §5 stage 3): reads series, number,
 * volume and title from names like `Saga #12 - Title (2018) (digital).cbz` or `Batman_v2_015_(2012).cbz`.
 * A year in parentheses is the series' start year, so it serves as the volume when no `v2` is given.
 */
object ComicFileName {
    private val BRACKETS = Regex("""\[[^\]]*]""")
    private val PARENTHESES = Regex("""\(([^)]*)\)""")
    private val YEAR = Regex("""^(19|20)\d{2}$""")
    private val SPACES = Regex("""\s+""")
    private val VOLUME = """v(?:ol(?:ume)?)?\.?\s*(\d+)"""

    /** `Series [v2] [#]12 [- Title]` */
    private val ISSUE = Regex("""^(.+?)\s+(?:$VOLUME\s+)?#?(\d+(?:\.\d+)?)(?:\s+-\s+(.+))?$""", RegexOption.IGNORE_CASE)

    /** `Series v3 [- Title]`: a collected volume, with no issue number. */
    private val COLLECTED = Regex("""^(.+?)\s+$VOLUME(?:\s+-\s+(.+))?$""", RegexOption.IGNORE_CASE)

    fun parse(fileName: String): ExtractedMetadata {
        val raw = Path.of(fileName).nameWithoutExtension.replace('_', ' ')
        val year = PARENTHESES.findAll(raw).map { it.groupValues[1].trim() }.firstOrNull { YEAR.matches(it) }?.toInt()
        val name = raw.replace(BRACKETS, " ").replace(PARENTHESES, " ").replace(SPACES, " ").trim()

        // First, or "Saga Vol. 3" would read as Series "Saga Vol." number 3.
        COLLECTED.matchEntire(name)?.let { match ->
            val (series, volume, title) = match.destructured
            return parsed(series, volume.toInt(), null, title)
        }
        ISSUE.matchEntire(name)?.let { match ->
            val (series, volume, number, title) = match.destructured
            return parsed(series, volume.toIntOrNull() ?: year, Names.positionText(number), title)
        }
        return ExtractedMetadata(source = SOURCE, title = name.ifEmpty { null })
    }

    private fun parsed(series: String, volume: Int?, position: String?, title: String) = ExtractedMetadata(
        source = SOURCE,
        title = title.trim().ifEmpty { null },
        series = ExtractedSeries(series.trim(), position = position, volume = volume),
    )

    const val SOURCE = "filename"
}
