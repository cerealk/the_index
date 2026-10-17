package theindex.publications

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import theindex.files.Format
import theindex.files.Hashes
import theindex.metadata.ExtractedIdentifier
import theindex.persistence.ContributorTable
import theindex.persistence.CreditTable
import theindex.persistence.FileTable
import theindex.persistence.IdentifierTable
import theindex.persistence.PublicationTable
import theindex.persistence.PublicationTagTable
import theindex.persistence.ReadingListEntryTable
import theindex.persistence.ReadingListTable
import theindex.persistence.SeriesTable
import theindex.persistence.TagTable
import java.math.BigDecimal
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
    val readingLists: List<ReadingListDraft> = emptyList(),
)

/** [sortName] is used only when the Contributor is new. */
data class CreditDraft(val name: String, val sortName: String, val role: Role)

data class SeriesDraft(val name: String, val volume: Int?, val positionText: String?)

/** A Reading List to join by name; [number] places the Publication among the list's numbered entries. */
data class ReadingListDraft(val name: String, val number: String?)

data class NewOriginal(val format: Format, val hashes: Hashes, val storedPath: String, val pageCount: Int? = null)

data class StoredFile(val id: Long, val publicationId: Long, val storedPath: String, val pageCount: Int?)

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

    fun storedFile(id: Long): StoredFile = transaction(db) {
        FileTable.selectAll().where { FileTable.id eq id }.single().let {
            StoredFile(it[FileTable.id].value, it[FileTable.publicationId].value, it[FileTable.storedPath], it[FileTable.pageCount])
        }
    }

    /** cbr Originals outside the Trash with no Conversion, e.g. after a crash or a failed conversion (spec §5 stage 7). */
    fun cbrOriginalsWithoutConversion(): List<Long> = transaction(db) {
        exec(
            """
            SELECT o.id FROM file o
            WHERE o.format = 'cbr' AND o.role = 'original' AND o.trashed_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM file c WHERE c.derived_from = o.id)
            ORDER BY o.id
            """.trimIndent(),
        ) { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } } ?: emptyList()
    }

    /** Records a cbz Conversion stored next to its Original; returns the new File id. */
    fun insertConversion(original: StoredFile, hashes: Hashes, storedPath: String): Long = transaction(db) {
        FileTable.insertAndGetId {
            it[publicationId] = original.publicationId
            it[format] = Format.CBZ.dbValue
            it[size] = hashes.size
            it[sha256] = hashes.sha256
            it[partialMd5] = hashes.partialMd5
            it[role] = "conversion"
            it[derivedFrom] = original.id
            it[pageCount] = original.pageCount
            it[FileTable.storedPath] = storedPath
            it[importedAt] = clock.instant()
        }.value
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

    /**
     * Inserts a new Publication with its first Original; returns the File id. Runs inside the
     * caller's transaction. [possibleDuplicateOf] flags a weak match (spec §5 stage 5).
     */
    fun JdbcTransaction.insertPublication(
        id: Long,
        draft: PublicationDraft,
        original: NewOriginal,
        possibleDuplicateOf: Long? = null,
    ): Long {
        PublicationTable.insert {
            it[PublicationTable.id] = id
            it[kind] = draft.kind.dbValue
            it[title] = draft.title
            it[sortTitle] = draft.sortTitle
            it[description] = draft.description
            it[publisher] = draft.publisher
            it[language] = draft.language
            it[publicationDate] = draft.publicationDate
            it[seriesId] = draft.series?.let { series -> findOrCreateSeries(series) }
            it[seriesPositionText] = draft.series?.positionText
            it[seriesPositionSort] = draft.series?.positionText?.let(Names::positionSortKey)
            it[PublicationTable.possibleDuplicateOf] = possibleDuplicateOf
            it[createdAt] = clock.instant()
        }
        insertCredits(id, draft.credits)
        insertTags(id, draft.tags)
        insertIdentifiers(id, draft.identifiers)
        joinReadingLists(id, draft.readingLists)
        return insertOriginal(id, original)
    }

    /**
     * Attaches a strongly matched File to an existing Publication (spec §5 stage 5). The draft
     * fills empty fields only, and never overwrites. Returns the File id. Runs inside the caller's transaction.
     */
    fun JdbcTransaction.attachOriginal(publicationId: Long, draft: PublicationDraft, original: NewOriginal): Long {
        val publication = PublicationTable.selectAll().where { PublicationTable.id eq publicationId }.single()
        val fillSeries = publication[PublicationTable.seriesId] == null && draft.series != null
        PublicationTable.update({ PublicationTable.id eq publicationId }) {
            if (publication[description] == null) it[description] = draft.description
            if (publication[publisher] == null) it[publisher] = draft.publisher
            if (publication[language] == null) it[language] = draft.language
            if (publication[publicationDate] == null) it[publicationDate] = draft.publicationDate
            if (fillSeries) {
                it[seriesId] = findOrCreateSeries(draft.series!!)
                it[seriesPositionText] = draft.series.positionText
                it[seriesPositionSort] = draft.series.positionText?.let(Names::positionSortKey)
            }
        }
        if (CreditTable.selectAll().where { CreditTable.publicationId eq publicationId }.empty()) insertCredits(publicationId, draft.credits)
        if (PublicationTagTable.selectAll().where { PublicationTagTable.publicationId eq publicationId }.empty()) insertTags(publicationId, draft.tags)
        if (IdentifierTable.selectAll().where { IdentifierTable.publicationId eq publicationId }.empty()) {
            insertIdentifiers(publicationId, draft.identifiers)
        }
        if (ReadingListEntryTable.selectAll().where { ReadingListEntryTable.publicationId eq publicationId }.empty()) {
            joinReadingLists(publicationId, draft.readingLists)
        }
        return insertOriginal(publicationId, original)
    }

    /** The folder holding a Publication's Files and the name its Files share, from its first File (spec §4). */
    fun publicationFolder(publicationId: Long): FolderAndName = transaction(db) {
        val path = FileTable.selectAll().where { FileTable.publicationId eq publicationId }.orderBy(FileTable.id)
            .first()[FileTable.storedPath]
        FolderAndName(path.substringBeforeLast('/'), path.substringAfterLast('/').substringBeforeLast('.'))
    }

    data class FolderAndName(val folder: String, val name: String)

    private fun insertCredits(publicationId: Long, credits: List<CreditDraft>) {
        val rows = credits.map { findOrCreateContributor(it) to it.role }.distinct()
        CreditTable.batchInsert(rows.withIndex()) { (position, credit) ->
            this[CreditTable.publicationId] = publicationId
            this[CreditTable.position] = position
            this[CreditTable.contributorId] = credit.first
            this[CreditTable.role] = credit.second.dbValue
        }
    }

    private fun insertTags(publicationId: Long, tags: List<String>) {
        PublicationTagTable.batchInsert(tags.map { findOrCreateTag(it) }.distinct()) { tagId ->
            this[PublicationTagTable.publicationId] = publicationId
            this[PublicationTagTable.tagId] = tagId
        }
    }

    private fun insertIdentifiers(publicationId: Long, identifiers: List<ExtractedIdentifier>) {
        IdentifierTable.batchInsert(identifiers.distinct()) { identifier ->
            this[IdentifierTable.publicationId] = publicationId
            this[IdentifierTable.type] = identifier.type
            this[IdentifierTable.value] = identifier.value
        }
    }

    private fun JdbcTransaction.joinReadingLists(publicationId: Long, lists: List<ReadingListDraft>) {
        lists.distinctBy { Names.readingListKey(it.name) }.forEach { list ->
            joinReadingList(findOrCreateReadingList(list.name), publicationId, list.number?.let(Names::positionSortKey))
        }
    }

    private fun insertOriginal(publicationId: Long, original: NewOriginal): Long = FileTable.insertAndGetId {
        it[FileTable.publicationId] = publicationId
        it[format] = original.format.dbValue
        it[size] = original.hashes.size
        it[sha256] = original.hashes.sha256
        it[partialMd5] = original.hashes.partialMd5
        it[role] = "original"
        it[pageCount] = original.pageCount
        it[storedPath] = original.storedPath
        it[importedAt] = clock.instant()
    }.value

    /**
     * Places a numbered entry before the first entry with a higher number, and appends an
     * unnumbered one (spec §3). Unnumbered entries, and the reader's own reordering, stay put.
     */
    private fun JdbcTransaction.joinReadingList(listId: Long, publicationId: Long, number: BigDecimal?) {
        val entries = ReadingListEntryTable.selectAll().where { ReadingListEntryTable.readingListId eq listId }
            .orderBy(ReadingListEntryTable.position)
            .map { it[ReadingListEntryTable.position] to it[ReadingListEntryTable.numberSort] }
        val position = number
            ?.let { entries.firstOrNull { (_, other) -> other != null && other > number }?.first }
            ?: ((entries.lastOrNull()?.first ?: -1) + 1)
        exec("UPDATE reading_list_entry SET position = position + 1 WHERE reading_list_id = $listId AND position >= $position")
        ReadingListEntryTable.insert {
            it[readingListId] = listId
            it[ReadingListEntryTable.publicationId] = publicationId
            it[ReadingListEntryTable.position] = position
            it[numberSort] = number
        }
    }

    private fun findOrCreateReadingList(name: String): Long {
        val key = Names.readingListKey(name)
        return ReadingListTable.selectAll().where { ReadingListTable.matchKey eq key }.singleOrNull()?.get(ReadingListTable.id)?.value
            ?: ReadingListTable.insertAndGetId {
                it[ReadingListTable.name] = name.trim()
                it[matchKey] = key
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
