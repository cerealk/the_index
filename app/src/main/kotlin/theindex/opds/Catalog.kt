package theindex.opds

import theindex.files.Format
import java.time.Instant

/**
 * The catalog as plain data, independent of any wire format (spec §7). [AtomSerializer]
 * turns it into OPDS 1.2; an OPDS 2.0 serializer can be added beside it later.
 */
data class Feed(
    val id: String,
    val title: String,
    val updated: Instant,
    val kind: FeedKind,
    val self: String,
    val up: String? = null,
    val previous: String? = null,
    val next: String? = null,
    val entries: List<Entry>,
)

enum class FeedKind { NAVIGATION, ACQUISITION }

sealed interface Entry {
    val id: String
    val title: String
    val updated: Instant
}

/** A link to another feed. [sort] marks a feed with a well-known ordering, such as "new" for Recently added. */
data class NavigationEntry(
    override val id: String,
    override val title: String,
    override val updated: Instant,
    val href: String,
    val target: FeedKind,
    val content: String? = null,
    val sort: String? = null,
) : Entry

data class PublicationEntry(
    override val id: String,
    override val title: String,
    override val updated: Instant,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    /** Preferred File first. */
    val acquisitions: List<Acquisition>,
) : Entry

data class Acquisition(val href: String, val format: Format, val size: Long)
