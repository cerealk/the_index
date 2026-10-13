package theindex.publications

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import theindex.files.Format
import theindex.files.Hashes
import theindex.persistence.FileTable
import theindex.persistence.PublicationTable
import java.time.Clock

data class NewPublication(val id: Long, val kind: Kind, val title: String, val sortTitle: String)

data class NewOriginal(val format: Format, val hashes: Hashes, val storedPath: String)

/** Publications and their Files as rows (spec §3). */
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

    /** Inserts a new Publication with its first Original; returns the File id. Runs inside the caller's transaction. */
    fun JdbcTransaction.insertPublicationWithOriginal(publication: NewPublication, original: NewOriginal): Long {
        val now = clock.instant()
        PublicationTable.insert {
            it[id] = publication.id
            it[kind] = publication.kind.dbValue
            it[title] = publication.title
            it[sortTitle] = publication.sortTitle
            it[createdAt] = now
        }
        return FileTable.insertAndGetId {
            it[publicationId] = publication.id
            it[format] = original.format.dbValue
            it[size] = original.hashes.size
            it[sha256] = original.hashes.sha256
            it[partialMd5] = original.hashes.partialMd5
            it[role] = "original"
            it[storedPath] = original.storedPath
            it[importedAt] = now
        }.value
    }
}
