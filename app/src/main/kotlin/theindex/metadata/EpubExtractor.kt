package theindex.metadata

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import theindex.publications.Names
import theindex.publications.Role
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Reads an epub's OPF package metadata (spec §5 stage 3): Dublin Core, EPUB 3 `refines`
 * and EPUB 2 `opf:` attributes, Series from `belongs-to-collection` then `calibre:series`.
 */
object EpubExtractor {
    /** Font obfuscation is not DRM; any other encryption makes the epub unreadable to us and to readers. */
    private val FONT_OBFUSCATION = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")

    fun extract(path: Path): ExtractedMetadata {
        val opf = try {
            ZipFile(path.toFile()).use { zip -> readOpf(zip) }
        } catch (e: ZipException) {
            throw UnreadableFileException("cannot open epub: ${e.message}", e)
        } catch (e: IOException) {
            throw UnreadableFileException("cannot open epub: ${e.message}", e)
        }
        return Opf(opf).metadata()
    }

    private fun readOpf(zip: ZipFile): Document {
        if (zip.getEntry("META-INF/rights.xml") != null || isEncrypted(zip)) {
            throw UnreadableFileException("epub is encrypted (DRM)")
        }
        val container = zip.getEntry("META-INF/container.xml")
            ?: throw UnreadableFileException("cannot open epub: no META-INF/container.xml")
        val opfPath = xml(zip.getInputStream(container).use { it.readAllBytes() })
            .allElements.firstOrNull { it.localName() == "rootfile" }
            ?.attr("full-path")?.takeIf { it.isNotBlank() }
            ?: throw UnreadableFileException("cannot open epub: container.xml names no package document")
        val opf = zip.getEntry(opfPath)
            ?: throw UnreadableFileException("cannot open epub: package document $opfPath is missing")
        return xml(zip.getInputStream(opf).use { it.readAllBytes() })
    }

    private fun isEncrypted(zip: ZipFile): Boolean {
        val entry = zip.getEntry("META-INF/encryption.xml") ?: return false
        return xml(zip.getInputStream(entry).use { it.readAllBytes() }).allElements
            .filter { it.localName() == "EncryptionMethod" }
            .any { it.attr("Algorithm") !in FONT_OBFUSCATION }
    }

    private fun xml(bytes: ByteArray): Document = Jsoup.parse(String(bytes, Charsets.UTF_8), "", Parser.xmlParser())

    private class Opf(document: Document) {
        private val elements: List<Element> = document.allElements
            .firstOrNull { it.localName() == "metadata" }
            ?.allElements?.drop(1)
            ?: throw UnreadableFileException("cannot open epub: the package document has no metadata")

        /** EPUB 3 `<meta refines="#id" property="…">` values, by target id then property. */
        private val refinements: Map<String, Map<String, String>> = elements
            .filter { it.localName() == "meta" && it.attr("refines").startsWith("#") && it.hasAttr("property") }
            .groupBy { it.attr("refines").removePrefix("#") }
            .mapValues { (_, metas) -> metas.associate { it.attr("property") to it.text().trim() } }

        private fun dc(name: String) = elements.filter { it.localName() == name && it.text().isNotBlank() }

        private fun refinement(element: Element, property: String): String? =
            element.id().takeIf { it.isNotEmpty() }?.let { refinements[it]?.get(property) }?.takeIf { it.isNotBlank() }

        /** EPUB 2 attributes, with or without their `opf:` prefix. */
        private fun Element.opfAttribute(name: String): String? =
            attributes().firstOrNull { it.key.substringAfter(':') == name }?.value?.trim()?.takeIf { it.isNotEmpty() }

        private fun namedMeta(name: String): String? =
            elements.firstOrNull { it.localName() == "meta" && it.attr("name") == name }
                ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

        fun metadata(): ExtractedMetadata {
            val title = dc("title").let { titles ->
                titles.firstOrNull { refinement(it, "title-type") == "main" } ?: titles.firstOrNull()
            }
            val language = dc("language").firstNotNullOfOrNull { Languages.iso639(it.text()) }
            return ExtractedMetadata(
                source = "opf",
                title = title?.text()?.trim(),
                sortTitle = title?.let { refinement(it, "file-as") ?: it.opfAttribute("file-as") }
                    ?: namedMeta("calibre:title_sort"),
                contributors = contributors(),
                series = series(),
                tags = dc("subject").map { it.text().trim() }.distinctBy(Names::tagKey),
                description = dc("description").firstOrNull()?.let(::description),
                publisher = dc("publisher").firstOrNull()?.text()?.trim(),
                language = language,
                publicationDate = publicationDate(),
                identifiers = dc("identifier").mapNotNull(::identifier).distinct(),
            )
        }

        /** Every `dc:creator`; a `dc:contributor` only in a role we keep (calibre lists itself as "bkp"). */
        private fun contributors(): List<ExtractedContributor> =
            dc("creator").map { contributor(it) } +
                dc("contributor").map { contributor(it) }.filter { it.role != null && Role.fromSource(it.role) != null }

        private fun contributor(element: Element) = ExtractedContributor(
            name = element.text().trim(),
            fileAs = refinement(element, "file-as") ?: element.opfAttribute("file-as"),
            role = refinement(element, "role") ?: element.opfAttribute("role"),
        )

        private fun series(): ExtractedSeries? {
            val collection = elements.firstOrNull { meta ->
                meta.localName() == "meta" && meta.attr("property") == "belongs-to-collection" && meta.text().isNotBlank() &&
                    (refinement(meta, "collection-type") ?: "series") == "series"
            }
            if (collection != null) {
                return ExtractedSeries(
                    name = collection.text().trim(),
                    position = refinement(collection, "group-position")?.let(Names::positionText),
                )
            }
            val name = namedMeta("calibre:series") ?: return null
            return ExtractedSeries(name, namedMeta("calibre:series_index")?.let(Names::positionText))
        }

        /** The publication date; EPUB 2 creation or modification events are not it. */
        private fun publicationDate(): String? {
            val dates = dc("date")
            val preferred = dates.filter { it.opfAttribute("event") == "publication" } +
                dates.filter { it.opfAttribute("event") == null }
            return preferred.firstNotNullOfOrNull { PartialDate.parse(it.text()) }
        }

        private fun description(element: Element): String? {
            // Usually escaped HTML, which the XML parser has already unescaped into text.
            val html = if (element.children().isEmpty()) element.wholeText() else element.html()
            return Html.sanitize(html)
        }

        private fun identifier(element: Element): ExtractedIdentifier? = Identifiers.normalize(
            scheme = refinement(element, "identifier-type") ?: element.opfAttribute("scheme"),
            value = element.text(),
        )
    }
}

internal fun Element.localName(): String = tagName().substringAfter(':')
