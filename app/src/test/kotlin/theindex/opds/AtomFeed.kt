package theindex.opds

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.w3c.dom.Element
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

private const val ATOM = "http://www.w3.org/2005/Atom"

/**
 * A parsed Atom document that has been checked against the OPDS 1.2 structural rules
 * the readers rely on: atom:id, atom:title and RFC 3339 atom:updated on the feed and every
 * entry, an author on the feed, and every link carrying rel, href and type.
 */
class AtomFeed(xml: String) {
    private val root: Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(xml.byteInputStream())
        .documentElement

    init {
        root.namespaceURI shouldBe ATOM
        root.localName shouldBe "feed"
        root.checkRequired()
        root.children("author").shouldNotBeEmpty()
        entries.forEach { it.element.checkRequired() }
    }

    val entries: List<AtomEntry> get() = root.children("entry").map(::AtomEntry)
    val titles: List<String> get() = entries.map { it.title }

    fun link(rel: String): String? = root.links().find { it.getAttribute("rel") == rel }?.getAttribute("href")

    private fun Element.checkRequired() {
        children("id").single().textContent.shouldNotBeNull()
        children("title").single()
        Instant.parse(children("updated").single().textContent)
        links().forEach { link ->
            link.getAttribute("rel").isNotEmpty() shouldBe true
            link.getAttribute("href").isNotEmpty() shouldBe true
            link.getAttribute("type").isNotEmpty() shouldBe true
        }
    }
}

class AtomEntry(val element: Element) {
    val title: String get() = element.children("title").single().textContent
    val content: String? get() = element.children("content").singleOrNull()?.textContent

    /** (href, type) of each acquisition link, in document order. */
    val acquisitions: List<Pair<String, String>>
        get() = element.links()
            .filter { it.getAttribute("rel") == "http://opds-spec.org/acquisition" }
            .map { it.getAttribute("href") to it.getAttribute("type") }

    fun link(rel: String): Element? = element.links().find { it.getAttribute("rel") == rel }
}

private fun Element.children(name: String): List<Element> =
    (0 until childNodes.length).map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.namespaceURI == ATOM && it.localName == name }

private fun Element.links() = children("link")
