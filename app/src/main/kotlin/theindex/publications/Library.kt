package theindex.publications

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import theindex.files.Format
import theindex.files.Hashes
import theindex.metadata.ExtractedIdentifier
import theindex.persistence.ContributorTable
import theindex.persistence.CreditTable
import theindex.persistence.FileTable
import theindex.persistence.IdentifierTable
import theindex.persistence.PublicationTable
import theindex.persistence.PublicationTagTable
import theindex.persistence.SeriesTable
import theindex.persistence.TagTable
import java.time.Clock

/** A new Publication's fields, with Contributors, Series and Tags still given by name. */
data class PublicationDraft(
    val kind: Kind,
    val title: String,
    val sortTitle: String,
    val credits: List<CreditDraft> = emptyList(),
    val series: SeriesDraft? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val publicationDate: String? = null,
    val identifiers: List<ExtractedIdentifier> = emptyList(),
)

/** [sortName] is used only when the Contributor is new. */
data class CreditDraft(val name: String, val sortName: String, val role: Role)

data class SeriesDraft(val name: String, val volume: Int?, val positionText: String?)

data class NewOriginal(val format: Format, val hashes: Hashes, val storedPath: String)

/** Publications, their Files and the Contributors, Series and Tags they refer to (spec §3). */
class Library(private val db: Database, private val clock: Clock) {

    /** Reserves an id up front, because the Publication's folder name contains it (spec §4). */
    fun reservePublicationId(): Long = transaction(db) {
        exec("SELECT nextval(pg_get_serial_sequence('publication', 'id'))") { rows ->
            rows.next()
            rows.getLong(1)
        }!!
    }

    /** The File with exactly these bytes, if the library already has it (Trashed Files included). */
    fun fileIdWithSha256(sha256: String): Long? = transaction(db) {
        FileTable.selectAll().where { FileTable.sha256 eq sha256 }.singleOrNull()?.get(FileTable.id)?.value
    }

    fun isStored(storedPath: String): Boolean = transaction(db) {
        FileTable.selectAll().where { FileTable.storedPath eq storedPath }.any()
    }

    /**
     * The names the folder layout will use: an existing Contributor or Series keeps its own
     * spelling. The prose author is the first author Credit, else the first Credit (spec §15).
     */
    fun layoutNames(draft: PublicationDraft): LayoutNames = transaction(db) {
        val author = (draft.credits.firstOrNull { it.role == Role.AUTHOR } ?: draft.credits.firstOrNull())
            ?.let { credit -> existingContributorName(credit.name) ?: Names.displayName(credit.name) }
        val series = draft.series?.let { series ->
            val name = SeriesTable.selectAll().where { SeriesTable.matchKey eq Names.seriesKey(series.name, series.volume) }
                .singleOrNull()?.get(SeriesTable.name) ?: series.name
            if (series.volume == null) name else "$name (${series.volume})"
        }
        LayoutNames(author, series)
    }

    data class LayoutNames(val author: String?, val series: String?)

    private fun existingContributorName(name: String): String? =
        ContributorTable.selectAll().where { ContributorTable.matchKey eq Names.contributorKey(name) }
            .singleOrNull()?.get(ContributorTable.name)

    /** Inserts a new Publication with its first Original; returns the File id. Runs inside the caller's transaction. */
    fun JdbcTransaction.insertPublication(id: Long, draft: PublicationDraft, original: NewOriginal): Long {
        val now = clock.instant()
        val seriesId = draft.series?.let { findOrCreateSeries(it) }
        PublicationTable.insert {
            it[PublicationTable.id] = id
            it[kind] = draft.kind.dbValue
            it[title] = draft.title
            it[sortTitle] = draft.sortTitle
            it[description] = draft.description
            it[publisher] = draft.publisher
            it[language] = draft.language
            it[publicationDate] = draft.publicationDate
            it[PublicationTable.seriesId] = seriesId
            it[seriesPositionText] = draft.series?.positionText
            it[seriesPositionSort] = draft.series?.positionText?.let(Names::positionSortKey)
            it[createdAt] = now
        }
        val credits = draft.credits.map { findOrCreateContributor(it) to it.role }.distinct()
        CreditTable.batchInsert(credits.withIndex()) { (position, credit) ->
            this[CreditTable.publicationId] = id
            this[CreditTable.position] = position
            this[CreditTable.contributorId] = credit.first
            this[CreditTable.role] = credit.second.dbValue
        }
        PublicationTagTable.batchInsert(draft.tags.map { findOrCreateTag(it) }.distinct()) { tagId ->
            this[PublicationTagTable.publicationId] = id
            this[PublicationTagTable.tagId] = tagId
        }
        IdentifierTable.batchInsert(draft.identifiers.distinct()) { identifier ->
            this[IdentifierTable.publicationId] = id
            this[IdentifierTable.type] = identifier.type
            this[IdentifierTable.value] = identifier.value
        }
        return FileTable.insertAndGetId {
            it[publicationId] = id
            it[format] = original.format.dbValue
            it[size] = original.hashes.size
            it[sha256] = original.hashes.sha256
            it[partialMd5] = original.hashes.partialMd5
            it[role] = "original"
            it[storedPath] = original.storedPath
            it[importedAt] = now
        }.value
    }

    /** Normalized exact match, otherwise a new Contributor (spec §3). */
    private fun findOrCreateContributor(credit: CreditDraft): Long {
        val key = Names.contributorKey(credit.name)
        return ContributorTable.selectAll().where { ContributorTable.matchKey eq key }.singleOrNull()?.get(ContributorTable.id)?.value
            ?: ContributorTable.insertAndGetId {
                it[name] = Names.displayName(credit.name)
                it[sortName] = credit.sortName
                it[matchKey] = key
            }.value
    }

    private fun findOrCreateSeries(series: SeriesDraft): Long {
        val key = Names.seriesKey(series.name, series.volume)
        return SeriesTable.selectAll().where { SeriesTable.matchKey eq key }.singleOrNull()?.get(SeriesTable.id)?.value
            ?: SeriesTable.insertAndGetId {
                it[name] = series.name.trim()
                it[volume] = series.volume
                it[matchKey] = key
            }.value
    }

    private fun findOrCreateTag(name: String): Long {
        val key = Names.tagKey(name)
        return TagTable.selectAll().where { TagTable.matchKey eq key }.singleOrNull()?.get(TagTable.id)?.value
            ?: TagTable.insertAndGetId {
                it[TagTable.name] = name.trim()
                it[matchKey] = key
            }.value
    }
}
