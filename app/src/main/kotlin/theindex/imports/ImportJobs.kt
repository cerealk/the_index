package theindex.imports

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import theindex.persistence.ImportJobTable
import theindex.publications.Kind
import java.time.Clock
import java.time.Instant

enum class ImportSource(val dbValue: String) {
    INBOX("inbox"),
    UPLOAD("upload");

    companion object {
        fun fromDb(value: String) = entries.single { it.dbValue == value }
    }
}

enum class ImportState(val dbValue: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    DONE("done"),
    SKIPPED("skipped"),
    FAILED("failed");

    companion object {
        fun fromDb(value: String) = entries.single { it.dbValue == value }
    }
}

data class ImportJob(
    val id: Long,
    val source: ImportSource,
    val originalName: String,
    val kindHint: Kind?,
    val state: ImportState,
    val reason: String?,
    val warnings: List<String>,
    val storeTarget: String?,
    val publicationId: Long?,
    val fileId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Persistence for Import jobs (spec §5): every arrival is a row, kept forever as history. */
class ImportJobs(private val db: Database, private val clock: Clock) {

    fun create(source: ImportSource, originalName: String, kindHint: Kind?): Long = transaction(db) {
        val now = clock.instant()
        ImportJobTable.insertAndGetId {
            it[ImportJobTable.importSource] = source.dbValue
            it[ImportJobTable.originalName] = originalName
            it[ImportJobTable.kindHint] = kindHint?.dbValue
            it[state] = ImportState.PENDING.dbValue
            it[warnings] = emptyList()
            it[createdAt] = now
            it[updatedAt] = now
        }.value
    }

    fun find(id: Long): ImportJob? = transaction(db) {
        ImportJobTable.selectAll().where { ImportJobTable.id eq id }.singleOrNull()?.toImportJob()
    }

    /** Newest first, optionally filtered by state. */
    fun list(state: ImportState? = null, offset: Long = 0, limit: Int = 50): List<ImportJob> = transaction(db) {
        val query = ImportJobTable.selectAll()
        if (state != null) query.where { ImportJobTable.state eq state.dbValue }
        query.orderBy(ImportJobTable.id, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toImportJob() }
    }

    /**
     * Takes the oldest pending job and marks it processing. `SKIP LOCKED` keeps this safe
     * even if a second worker ever ran.
     */
    fun claimNext(): ImportJob? = transaction(db) {
        val id = exec(
            """
            UPDATE import_job SET state = 'processing', updated_at = now()
            WHERE id = (
                SELECT id FROM import_job WHERE state = 'pending'
                ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
            )
            RETURNING id
            """.trimIndent(),
            explicitStatementType = StatementType.SELECT,
        ) { rows -> if (rows.next()) rows.getLong(1) else null }
        id?.let { ImportJobTable.selectAll().where { ImportJobTable.id eq it }.single().toImportJob() }
    }

    fun recordStoreTarget(id: Long, target: String?) = transaction(db) {
        ImportJobTable.update({ ImportJobTable.id eq id }) {
            it[storeTarget] = target
            it[updatedAt] = clock.instant()
        }
    }

    fun finish(
        id: Long,
        state: ImportState,
        reason: String? = null,
        warnings: List<String> = emptyList(),
        publicationId: Long? = null,
        fileId: Long? = null,
    ) = transaction(db) { finishInTransaction(id, state, reason, warnings, publicationId, fileId) }

    /** For callers that must commit the job's outcome together with their own rows. */
    fun finishInTransaction(
        id: Long,
        state: ImportState,
        reason: String? = null,
        warnings: List<String> = emptyList(),
        publicationId: Long? = null,
        fileId: Long? = null,
    ) {
        ImportJobTable.update({ ImportJobTable.id eq id }) {
            it[ImportJobTable.state] = state.dbValue
            it[ImportJobTable.reason] = reason
            it[ImportJobTable.warnings] = warnings
            it[ImportJobTable.publicationId] = publicationId
            it[ImportJobTable.fileId] = fileId
            it[storeTarget] = null
            it[updatedAt] = clock.instant()
        }
    }

    /** Jobs a crash may have left half-way: still unfinished, or with a copy in flight. */
    fun unfinished(): List<ImportJob> = transaction(db) {
        ImportJobTable.selectAll()
            .where {
                (ImportJobTable.state inList listOf(ImportState.PENDING.dbValue, ImportState.PROCESSING.dbValue)) or
                    ImportJobTable.storeTarget.isNotNull()
            }
            .orderBy(ImportJobTable.id)
            .map { it.toImportJob() }
    }

    fun resetToPending(id: Long) = transaction(db) {
        ImportJobTable.update({ (ImportJobTable.id eq id) and (ImportJobTable.state eq ImportState.PROCESSING.dbValue) }) {
            it[state] = ImportState.PENDING.dbValue
            it[updatedAt] = clock.instant()
        }
    }

    private fun ResultRow.toImportJob() = ImportJob(
        id = this[ImportJobTable.id].value,
        source = ImportSource.fromDb(this[ImportJobTable.importSource]),
        originalName = this[ImportJobTable.originalName],
        kindHint = this[ImportJobTable.kindHint]?.let(Kind::fromDb),
        state = ImportState.fromDb(this[ImportJobTable.state]),
        reason = this[ImportJobTable.reason],
        warnings = this[ImportJobTable.warnings],
        storeTarget = this[ImportJobTable.storeTarget],
        publicationId = this[ImportJobTable.publicationId]?.value,
        fileId = this[ImportJobTable.fileId]?.value,
        createdAt = this[ImportJobTable.createdAt],
        updatedAt = this[ImportJobTable.updatedAt],
    )
}
