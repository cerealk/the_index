package theindex.publications

import java.math.BigDecimal
import java.text.Normalizer

/** The naming rules of spec §3: how names are matched, displayed and sorted. */
object Names {
    private val MARKS = Regex("\\p{M}+")
    private val SPACES = Regex("\\s+")
    private val SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv", "phd", "md")
    private val POSITION_PREFIX = Regex("""^\s*(-?\d+(?:[.,]\d+)?)""")

    /** Case-folded, accents stripped, dots and runs of spaces collapsed to one space. */
    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace('.', ' ')
            .replace(SPACES, " ")
            .trim()

    /** "Tolkien, J.R.R." and "J. R. R. Tolkien" share the key "j r r tolkien". */
    fun contributorKey(name: String): String = fold(displayName(name))

    /**
     * A Contributor name as it should be shown: "Last, First" flips to "First Last",
     * unless the part after the comma is a suffix ("King, Jr.").
     */
    fun displayName(name: String): String {
        val clean = name.replace(SPACES, " ").trim()
        val parts = clean.split(",").map { it.trim() }
        if (parts.size != 2 || parts.any { it.isEmpty() }) return clean
        val (last, first) = parts
        if (fold(first) in SUFFIXES) return clean
        return "$first $last"
    }

    /** "J. R. R. Tolkien" → "Tolkien, J. R. R."; a single word stays as it is. */
    fun deriveSortName(displayName: String): String {
        val words = displayName.trim().split(" ").filter { it.isNotEmpty() }
        if (words.size < 2) return displayName.trim()
        return "${words.last()}, ${words.dropLast(1).joinToString(" ")}"
    }

    fun seriesKey(name: String, volume: Int?): String = fold(name) + "|" + (volume?.toString() ?: "")

    fun tagKey(name: String): String = fold(name)

    /** Titles match the way names do: "DUNE" and "Dune" are the same title. */
    fun titleKey(title: String): String = fold(title)

    fun readingListKey(name: String): String = fold(name)

    /** The title of a comic issue that has none of its own: "Saga #7", or the Series name without a position. */
    fun issueTitle(seriesName: String, positionText: String?): String =
        if (positionText == null) seriesName else "$seriesName #$positionText"

    /** Leading articles moved to the end, by language; English ones when the language is unknown. */
    private val ARTICLES = mapOf(
        "en" to listOf("the", "a", "an"),
        "it" to listOf("il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "l'", "un'"),
        "fr" to listOf("le", "la", "les", "un", "une", "l'"),
        "es" to listOf("el", "la", "los", "las", "un", "una"),
        "de" to listOf("der", "die", "das", "ein", "eine"),
    )

    /** "The Hobbit" → "Hobbit, The"; "L'amica geniale" (it) → "amica geniale, L'". */
    fun deriveSortTitle(title: String, language: String?): String {
        val trimmed = title.trim()
        val articles = if (language == null) ARTICLES.getValue("en") else ARTICLES[language] ?: return trimmed
        for (article in articles) {
            val pattern = if (article.endsWith("'")) {
                Regex("^(${Regex.escape(article.dropLast(1))}['’])(.+)$", RegexOption.IGNORE_CASE)
            } else {
                Regex("^(${Regex.escape(article)})\\s+(.+)$", RegexOption.IGNORE_CASE)
            }
            pattern.matchEntire(trimmed)?.let { return "${it.groupValues[2].trim()}, ${it.groupValues[1]}" }
        }
        return trimmed
    }

    /** "1.5" → 1.5, "12 (variant)" → 12, "Annual 1" → null, which sorts last. */
    fun positionSortKey(text: String): BigDecimal? =
        POSITION_PREFIX.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toBigDecimal()

    /** Calibre writes series indexes as floats: "1.0" → "1", "1.50" → "1.5". */
    fun positionText(raw: String): String {
        val trimmed = raw.trim()
        val number = trimmed.toBigDecimalOrNull() ?: return trimmed
        return number.stripTrailingZeros().toPlainString()
    }
}
