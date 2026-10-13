package theindex.opds

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Writes a [Feed] as an OPDS 1.2 Atom document. */
object AtomSerializer {
    const val NAVIGATION_TYPE = "application/atom+xml;profile=opds-catalog;kind=navigation"
    const val ACQUISITION_TYPE = "application/atom+xml;profile=opds-catalog;kind=acquisition"

    private const val ATOM = "http://www.w3.org/2005/Atom"
    private const val OPDS = "http://opds-spec.org/2010/catalog"
    private const val DC = "http://purl.org/dc/terms/"
    private const val ACQUISITION_REL = "http://opds-spec.org/acquisition"

    fun contentType(kind: FeedKind) = when (kind) {
        FeedKind.NAVIGATION -> NAVIGATION_TYPE
        FeedKind.ACQUISITION -> ACQUISITION_TYPE
    }

    fun write(feed: Feed): String = Xml().apply {
        raw("""<?xml version="1.0" encoding="UTF-8"?>""")
        element("feed", "xmlns" to ATOM, "xmlns:opds" to OPDS, "xmlns:dc" to DC) {
            text("id", feed.id)
            text("title", feed.title)
            text("updated", feed.updated.rfc3339())
            element("author") { text("name", AUTHOR) }
            link("self", feed.self, contentType(feed.kind))
            link("start", ROOT, NAVIGATION_TYPE)
            feed.up?.let { link("up", it, NAVIGATION_TYPE) }
            feed.previous?.let { link("previous", it, contentType(feed.kind)) }
            feed.next?.let { link("next", it, contentType(feed.kind)) }
            feed.entries.forEach { entry ->
                element("entry") {
                    text("id", entry.id)
                    text("title", entry.title)
                    text("updated", entry.updated.rfc3339())
                    when (entry) {
                        is NavigationEntry -> navigation(entry)
                        is PublicationEntry -> publication(entry)
                    }
                }
            }
        }
    }.toString()

    private fun Xml.navigation(entry: NavigationEntry) {
        entry.content?.let { text("content", it, "type" to "text") }
        val rel = entry.sort?.let { "http://opds-spec.org/sort/$it" } ?: "subsection"
        link(rel, entry.href, contentType(entry.target))
    }

    private fun Xml.publication(entry: PublicationEntry) {
        entry.authors.forEach { name -> element("author") { text("name", name) } }
        entry.summary?.let { text("summary", it) }
        entry.acquisitions.forEach { acquisition ->
            empty(
                "link",
                "rel" to ACQUISITION_REL,
                "href" to acquisition.href,
                "type" to acquisition.format.mediaType,
                "length" to acquisition.size.toString(),
            )
        }
    }

    private fun Instant.rfc3339() = truncatedTo(ChronoUnit.SECONDS).toString()

    private fun Xml.link(rel: String, href: String, type: String) = empty("link", "rel" to rel, "href" to href, "type" to type)

    private const val AUTHOR = "the_index"
    private const val ROOT = "/opds/"
}

/** A tiny indenting XML writer: enough for Atom, with predictable output for fixture tests. */
private class Xml {
    private val out = StringBuilder()
    private var depth = 0

    fun raw(line: String) {
        out.append(line).append('\n')
    }

    fun element(name: String, vararg attributes: Pair<String, String>, children: Xml.() -> Unit) {
        indent().append('<').append(name).attributes(attributes).append(">\n")
        depth++
        children()
        depth--
        indent().append("</").append(name).append(">\n")
    }

    fun text(name: String, value: String, vararg attributes: Pair<String, String>) {
        indent().append('<').append(name).attributes(attributes).append('>').append(escape(value)).append("</").append(name).append(">\n")
    }

    fun empty(name: String, vararg attributes: Pair<String, String>) {
        indent().append('<').append(name).attributes(attributes).append("/>\n")
    }

    override fun toString() = out.toString()

    private fun indent(): StringBuilder = out.append("  ".repeat(depth))

    private fun StringBuilder.attributes(attributes: Array<out Pair<String, String>>): StringBuilder = apply {
        attributes.forEach { (key, value) -> append(' ').append(key).append("=\"").append(escape(value)).append('"') }
    }

    private fun escape(value: String) = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (char < ' ' && char != '\n' && char != '\t' && char != '\r') Unit else append(char)
            }
        }
    }
}
