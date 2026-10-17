package theindex.publications

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import theindex.persistence.ContributorTable
import theindex.persistence.CreditTable
import theindex.persistence.IdentifierTable
import theindex.persistence.PublicationTable
import theindex.persistence.SeriesTable

/** What stage 5 found for a new File (spec §5). */
sealed interface Match {
    val publicationId: Long
}

/** The File belongs to this Publication; [reason] is reported on the Import job. */
data class StrongMatch(override val publicationId: Long, val reason: String) : Match

/** The File is probably this Publication too: a new Publication flagged as a Possible Duplicate of it. */
data class WeakMatch(override val publicationId: Long) : Match

/**
 * Stage 5 of the import pipeline (spec §5): finds the existing Publication a new File belongs to.
 * Only Publications of the same Kind and outside the Trash are candidates.
 */
class Matcher(private val db: Database) {
    fun match(draft: PublicationDraft): Match? = transaction(db) {
        byIdentifier(draft) ?: byTitleAndAuthor(draft) ?: bySeriesAndNumber(draft) ?: weak(draft)
    }

    private fun live(draft: PublicationDraft) =
        (PublicationTable.kind eq draft.kind.dbValue) and PublicationTable.trashedAt.isNull()

    /** Any identifier in common: ISBN, uuid, Calibre uuid… */
    private fun byIdentifier(draft: PublicationDraft): StrongMatch? = draft.identifiers.distinct().firstNotNullOfOrNull { identifier ->
        (IdentifierTable innerJoin PublicationTable).selectAll()
            .where { (IdentifierTable.type eq identifier.type) and (IdentifierTable.value eq identifier.value) and live(draft) }
            .orderBy(PublicationTable.id)
            .firstOrNull()
            ?.let { StrongMatch(it[PublicationTable.id].value, "same ${identifier.type} ${identifier.value}") }
    }

    /** Prose: the same title, as matched names are, and an author in common. */
    private fun byTitleAndAuthor(draft: PublicationDraft): StrongMatch? {
        if (draft.kind != Kind.PROSE) return null
        val authors = authorKeys(draft).ifEmpty { return null }
        val title = Names.titleKey(draft.title)
        return (CreditTable innerJoin ContributorTable innerJoin PublicationTable).selectAll()
            .where { (ContributorTable.matchKey inList authors) and live(draft) }
            .orderBy(PublicationTable.id)
            .firstOrNull { Names.titleKey(it[PublicationTable.title]) == title }
            ?.let { StrongMatch(it[PublicationTable.id].value, "same title and author") }
    }

    /** Comics: the same Series (name and volume) and the same number. */
    private fun bySeriesAndNumber(draft: PublicationDraft): StrongMatch? {
        if (draft.kind != Kind.COMIC) return null
        val series = draft.series ?: return null
        val position = series.positionText ?: return null
        return (PublicationTable innerJoin SeriesTable).selectAll()
            .where { (SeriesTable.matchKey eq Names.seriesKey(series.name, series.volume)) and live(draft) }
            .orderBy(PublicationTable.id)
            .firstOrNull { it[PublicationTable.seriesPositionText]?.lowercase() == position.lowercase() }
            ?.let { StrongMatch(it[PublicationTable.id].value, "same Series and number") }
    }

    /**
     * Fuzzy: a similar Series name with the same number and a compatible volume for comics in a
     * Series; otherwise a similar title and, when both sides have one, a similar author.
     */
    private fun JdbcTransaction.weak(draft: PublicationDraft): WeakMatch? {
        val series = draft.series
        val id = if (draft.kind == Kind.COMIC && series?.positionText != null) {
            query(
                """
                SELECT p.id FROM publication p JOIN series s ON s.id = p.series_id
                WHERE p.kind = 'comic' AND p.trashed_at IS NULL
                  AND lower(p.series_position_text) = ?
                  AND (s.volume IS NULL OR ?::int IS NULL OR s.volume = ?::int)
                  AND similarity(lower(unaccent(s.name)), ?) >= $THRESHOLD
                ORDER BY similarity(lower(unaccent(s.name)), ?) DESC, p.id
                LIMIT 1
                """,
                text(series.positionText.lowercase()), int(series.volume), int(series.volume),
                text(Names.titleKey(series.name)), text(Names.titleKey(series.name)),
            )
        } else {
            val title = Names.titleKey(draft.title)
            val author = authorKeys(draft).firstOrNull() ?: ""
            query(
                """
                SELECT p.id FROM publication p
                WHERE p.kind = ? AND p.trashed_at IS NULL
                  AND similarity(lower(unaccent(p.title)), ?) >= $THRESHOLD
                  AND (? = ''
                       OR NOT EXISTS (SELECT 1 FROM credit c WHERE c.publication_id = p.id)
                       OR EXISTS (SELECT 1 FROM credit c JOIN contributor k ON k.id = c.contributor_id
                                  WHERE c.publication_id = p.id AND similarity(k.match_key, ?) >= $THRESHOLD))
                ORDER BY similarity(lower(unaccent(p.title)), ?) DESC, p.id
                LIMIT 1
                """,
                text(draft.kind.dbValue), text(title), text(author), text(author), text(title),
            )
        }
        return id?.let(::WeakMatch)
    }

    private fun JdbcTransaction.query(sql: String, vararg args: Pair<IColumnType<*>, Any?>): Long? =
        exec(sql.trimIndent(), args.toList()) { rows -> if (rows.next()) rows.getLong(1) else null }

    private fun text(value: String) = TextColumnType() to value

    private fun int(value: Int?) = IntegerColumnType() to value

    /** The Contributors a title match must share: the authors (writers for comics), else every Credit. */
    private fun authorKeys(draft: PublicationDraft): List<String> {
        val primary = draft.credits.filter { it.role == Role.AUTHOR || it.role == Role.WRITER }.ifEmpty { draft.credits }
        return primary.map { Names.contributorKey(it.name) }.distinct()
    }

    companion object {
        /** pg_trgm similarity from which two titles (or names) count as near-identical. */
        const val THRESHOLD = 0.6
    }
}
