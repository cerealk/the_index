package theindex.opds

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import theindex.files.Format
import theindex.files.Storage
import theindex.persistence.ContributorTable
import theindex.persistence.CreditTable
import theindex.persistence.FileTable
import theindex.persistence.PublicationTable
import theindex.publications.Role
import java.net.URLEncoder
import java.nio.file.Path
import java.time.Clock

/** A File as offered for download: where its bytes are and the name the client should save it under. */
data class Download(val path: Path, val fileName: String, val format: Format)

/** Builds the OPDS catalog from the library (spec §7). Trashed Publications and Files never appear. */
class OpdsCatalog(private val db: Database, private val storage: Storage, private val clock: Clock) {

    fun root(): Feed {
        val now = clock.instant()
        return Feed(
            id = "$URN:root",
            title = "the_index",
            updated = now,
            kind = FeedKind.NAVIGATION,
            self = ROOT,
            entries = listOf(
                NavigationEntry(
                    id = "$URN:recent",
                    title = "Recently added",
                    updated = now,
                    href = RECENT,
                    target = FeedKind.ACQUISITION,
                    content = "The newest Publications first",
                    sort = "new",
                ),
                NavigationEntry(
                    id = "$URN:all",
                    title = "All",
                    updated = now,
                    href = ALL,
                    target = FeedKind.ACQUISITION,
                    content = "Every Publication, A–Z",
                ),
            ),
        )
    }

    fun recentlyAdded(page: Int): Feed = publicationFeed("$URN:recent", "Recently added", RECENT, page) {
        orderBy(PublicationTable.createdAt to SortOrder.DESC, PublicationTable.id to SortOrder.DESC)
    }

    fun all(page: Int): Feed = publicationFeed("$URN:all", "All", ALL, page) {
        orderBy(PublicationTable.sortTitle.lowerCase() to SortOrder.ASC, PublicationTable.id to SortOrder.ASC)
    }

    fun download(fileId: Long): Download? = transaction(db) {
        (FileTable innerJoin PublicationTable).selectAll()
            .where { (FileTable.id eq fileId) and FileTable.trashedAt.isNull() and PublicationTable.trashedAt.isNull() }
            .singleOrNull()
            ?.let { row ->
                val format = Format.fromDb(row[FileTable.format])
                Download(storage.resolve(row[FileTable.storedPath]), fileName(row[PublicationTable.title], format), format)
            }
    }

    private fun publicationFeed(id: String, title: String, path: String, page: Int, order: Query.() -> Query): Feed {
        require(page >= 1) { "page must be at least 1" }
        val (rows, files, credits) = transaction(db) {
            val rows = PublicationTable.selectAll()
                .where { PublicationTable.trashedAt.isNull() }
                .order()
                .limit(PAGE_SIZE + 1)
                .offset((page - 1).toLong() * PAGE_SIZE)
                .toList()
            val ids = rows.take(PAGE_SIZE).map { it[PublicationTable.id].value }
            val files = if (ids.isEmpty()) emptyList() else FileTable.selectAll()
                .where { (FileTable.publicationId inList ids) and FileTable.trashedAt.isNull() }
                .toList()
            val credits = if (ids.isEmpty()) emptyList() else (CreditTable innerJoin ContributorTable).selectAll()
                .where { CreditTable.publicationId inList ids }
                .orderBy(CreditTable.position)
                .toList()
            Triple(rows, files, credits)
        }
        val filesByPublication = files.groupBy { it[FileTable.publicationId].value }
        val creditsByPublication = credits.groupBy { it[CreditTable.publicationId].value }
        return Feed(
            id = if (page == 1) id else "$id:page:$page",
            title = title,
            updated = clock.instant(),
            kind = FeedKind.ACQUISITION,
            self = pageHref(path, page),
            up = ROOT,
            previous = if (page > 1) pageHref(path, page - 1) else null,
            next = if (rows.size > PAGE_SIZE) pageHref(path, page + 1) else null,
            entries = rows.take(PAGE_SIZE).map { row ->
                val id = row[PublicationTable.id].value
                row.toEntry(filesByPublication[id].orEmpty(), creditsByPublication[id].orEmpty())
            },
        )
    }

    private fun ResultRow.toEntry(files: List<ResultRow>, credits: List<ResultRow>): PublicationEntry {
        val title = this[PublicationTable.title]
        return PublicationEntry(
            id = "$URN:publication:${this[PublicationTable.id].value}",
            title = title,
            updated = this[PublicationTable.createdAt],
            authors = authors(credits),
            acquisitions = files
                .sortedWith(compareBy({ Format.fromDb(it[FileTable.format]) }, { it[FileTable.id].value }))
                .map { file ->
                    val format = Format.fromDb(file[FileTable.format])
                    Acquisition(fileHref(file[FileTable.id].value, title, format), format, file[FileTable.size])
                },
        )
    }

    /** Authors and comic writers; any other Credit only when there are none of those. */
    private fun authors(credits: List<ResultRow>): List<String> {
        val names = credits.map { it[ContributorTable.name] to Role.fromDb(it[CreditTable.role]) }
        val primary = names.filter { (_, role) -> role == Role.AUTHOR || role == Role.WRITER }.ifEmpty { names.take(1) }
        return primary.map { it.first }.distinct()
    }

    companion object {
        const val PAGE_SIZE = 50
        const val ROOT = "/opds/"
        const val RECENT = "/opds/recent"
        const val ALL = "/opds/all"
        const val FILES = "/opds/files"
        private const val URN = "urn:the_index"

        private fun pageHref(path: String, page: Int) = if (page == 1) path else "$path?page=$page"

        /** The download name is the Publication's title, independent of where the File is stored (spec §4). */
        fun fileName(title: String, format: Format) = "${Storage.segment(title)}.${format.extension}"

        /**
         * The file name rides in the URL too, because some readers name the download after the
         * last path segment instead of reading Content-Disposition.
         */
        fun fileHref(fileId: Long, title: String, format: Format) =
            "$FILES/$fileId/" + URLEncoder.encode(fileName(title, format), Charsets.UTF_8).replace("+", "%20")
    }
}
