package theindex.metadata

import kotlinx.serialization.Serializable

/**
 * Metadata as one File's embedded sources state it (spec §5 stage 3), before any matching.
 * It is also the raw snapshot kept on the Import job, so it stays close to the source:
 * roles are the source's own words, names are as written.
 */
@Serializable
data class ExtractedMetadata(
    val source: String,
    val title: String? = null,
    /** From `file-as` or `calibre:title_sort`; derived later when absent. */
    val sortTitle: String? = null,
    val contributors: List<ExtractedContributor> = emptyList(),
    val series: ExtractedSeries? = null,
    val tags: List<String> = emptyList(),
    /** Already sanitized to the safe HTML subset. */
    val description: String? = null,
    val publisher: String? = null,
    /** One ISO 639 code. */
    val language: String? = null,
    /** Partial date: `yyyy`, `yyyy-MM` or `yyyy-MM-dd`. */
    val publicationDate: String? = null,
    val identifiers: List<ExtractedIdentifier> = emptyList(),
) {
    companion object {
        val NONE = ExtractedMetadata(source = "none")
    }
}

@Serializable
data class ExtractedContributor(
    val name: String,
    val fileAs: String? = null,
    /** The source's role, e.g. a MARC relator code; null when the source gave none. */
    val role: String? = null,
)

@Serializable
data class ExtractedSeries(val name: String, val position: String? = null, val volume: Int? = null)

@Serializable
data class ExtractedIdentifier(val type: String, val value: String)

/** The File can't be read as its Format (corrupt or encrypted): the Import fails (spec §5 stage 3). */
class UnreadableFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
