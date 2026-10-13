package theindex.metadata

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import java.util.Locale

object Languages {
    private val TWO_LETTER_BY_THREE: Map<String, String> =
        Locale.getISOLanguages().associateBy { Locale.of(it).isO3Language }

    /** "en-US" → "en", "eng" → "en", "ita" → "it"; null for "und" or nothing usable. */
    fun iso639(tag: String): String? {
        val primary = tag.trim().lowercase().split('-', '_').first()
        return when {
            primary == "und" || primary.isEmpty() -> null
            primary.length == 2 && primary.all(Char::isLetter) -> primary
            primary.length == 3 && primary.all(Char::isLetter) -> TWO_LETTER_BY_THREE[primary] ?: primary
            else -> null
        }
    }
}

object PartialDate {
    private val DATE = Regex("""^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?""")

    /**
     * The leading `yyyy`, `yyyy-MM` or `yyyy-MM-dd` of a date or timestamp. Calibre writes
     * year 101 for "unknown", so years before 1000 are treated as absent.
     */
    fun parse(text: String): String? {
        val match = DATE.find(text.trim()) ?: return null
        val (year, month, day) = match.destructured
        if (year.toInt() < 1000) return null
        if (month.isEmpty()) return year
        if (month.toInt() !in 1..12) return year
        if (day.isEmpty() || day.toInt() !in 1..31) return "$year-$month"
        return "$year-$month-$day"
    }
}

object Html {
    private val SAFE: Safelist = Safelist.basic().addTags("h1", "h2", "h3", "h4", "h5", "h6")
    private val OUTPUT = Document.OutputSettings().prettyPrint(false)

    /** Keeps a safe subset of HTML (spec §3); plain text passes through escaped. Null when nothing is left. */
    fun sanitize(html: String): String? =
        Jsoup.clean(html, "", SAFE, OUTPUT).trim().takeIf { Jsoup.parse(it).text().isNotBlank() }
}

object Identifiers {
    /** EPUB 3 may type an identifier with ONIX code list 5: 02 is ISBN-10, 15 is ISBN-13. */
    private val ONIX_ISBN_CODES = mapOf("02" to "isbn", "15" to "isbn")
    private val KNOWN_PREFIXES = setOf("isbn", "uuid", "calibre", "amazon", "asin", "mobi-asin", "google", "goodreads", "doi")
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * An identifier as (type, value): the type from the scheme, or from a `urn:x:` / `x:` prefix
     * on the value. ISBNs keep digits only and must be 10 or 13 long; an untyped value counts as an
     * ISBN only when it is a valid ISBN-13. Other untyped values are dropped.
     *
     * Files never yield the `calibre` type: calibre writes a per-library row number or a uuid
     * under it, inconsistently, so the Calibre identity comes from `metadata.db` only (spec §6).
     * For the same reason a `uuid` must look like one.
     */
    fun normalize(scheme: String?, value: String): ExtractedIdentifier? {
        var rest = value.trim().removePrefix("urn:").removePrefix("URN:")
        val prefix = rest.substringBefore(':', "").lowercase()
        if (prefix in KNOWN_PREFIXES) rest = rest.substringAfter(':')
        val cleaned = rest.trim()
        val type = scheme?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }?.let { ONIX_ISBN_CODES[it] ?: it }
            ?: prefix.takeIf { it in KNOWN_PREFIXES }
            ?: return if (isIsbn13(cleaned.filter(Char::isDigit))) ExtractedIdentifier("isbn", cleaned.filter(Char::isDigit)) else null
        return when (type) {
            "calibre" -> null
            "uuid" -> cleaned.lowercase().takeIf { UUID.matches(it) }?.let { ExtractedIdentifier("uuid", it) }
            "isbn" -> {
                val digits = cleaned.uppercase().filter { it.isDigit() || it == 'X' }
                if (digits.length == 10 || digits.length == 13) ExtractedIdentifier("isbn", digits) else null
            }
            else -> cleaned.takeIf { it.isNotEmpty() }?.let { ExtractedIdentifier(type, it) }
        }
    }

    private fun isIsbn13(digits: String): Boolean {
        if (digits.length != 13 || !(digits.startsWith("978") || digits.startsWith("979"))) return false
        val sum = digits.mapIndexed { index, char -> (char - '0') * if (index % 2 == 0) 1 else 3 }.sum()
        return sum % 10 == 0
    }
}
