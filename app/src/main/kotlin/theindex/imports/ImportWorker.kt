package theindex.imports

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import theindex.files.Format
import theindex.files.FormatDetector
import theindex.files.Hasher
import theindex.files.Hashes
import theindex.files.Storage
import theindex.publications.Kind
import theindex.publications.Library
import theindex.publications.NewOriginal
import theindex.publications.NewPublication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.ExperimentalPathApi

/**
 * Runs Import jobs through the pipeline one at a time (spec §5). Implemented stages:
 * detect Format, hash (exact duplicates skipped), decide Kind, store the Original.
 */
class ImportWorker(
    private val db: Database,
    private val jobs: ImportJobs,
    private val library: Library,
    private val storage: Storage,
) {
    private val log = LoggerFactory.getLogger(ImportWorker::class.java)

    fun stagedFile(job: ImportJob): Path = storage.staging.resolve(job.id.toString()).resolve(job.originalName)

    /**
     * Startup recovery: throw away copies a crash interrupted before their rows committed,
     * and put jobs that were mid-flight back in the queue. The staged file is still there.
     */
    fun recover() {
        for (job in jobs.unfinished()) {
            val target = job.storeTarget
            if (target != null && !library.isStored(target)) {
                storage.deleteStored(target)
                jobs.recordStoreTarget(job.id, null)
            }
            if (job.state == ImportState.PROCESSING) jobs.resetToPending(job.id)
        }
    }

    /** Processes the oldest pending job; returns false when the queue is empty. */
    fun processNext(): Boolean {
        val job = jobs.claimNext() ?: return false
        try {
            process(job)
        } catch (e: Exception) {
            log.error("Import job ${job.id} failed unexpectedly", e)
            fail(job, "unexpected error: ${e.message ?: e::class.simpleName}")
        }
        return true
    }

    private fun process(job: ImportJob) {
        val staged = stagedFile(job)
        if (!staged.exists()) {
            jobs.finish(job.id, ImportState.FAILED, reason = "staged file is missing")
            return
        }

        // Stage 1: Format, from the bytes.
        val format = FormatDetector.detect(staged)
            ?: return fail(job, "Format not recognized")

        // Stage 2: hashes; exact duplicates are skipped.
        val hashes = Hasher.hash(staged)
        library.fileIdWithSha256(hashes.sha256)?.let { existing ->
            jobs.finish(job.id, ImportState.SKIPPED, reason = "already in library as File $existing")
            clearStaging(job)
            return
        }

        // Stage 4: Kind, from the hint or the Format until metadata extraction exists.
        val kind = job.kindHint ?: Kind.defaultFor(format)
        val title = titleFromFileName(job.originalName)

        // Stage 6: store the Original.
        store(job, staged, NewPublicationDraft(kind, title, format), hashes)
    }

    private data class NewPublicationDraft(val kind: Kind, val title: String, val format: Format)

    private fun store(job: ImportJob, staged: Path, draft: NewPublicationDraft, hashes: Hashes) {
        val publicationId = library.reservePublicationId()
        val storedPath = storage.layoutPath(draft.kind, publicationId, draft.title, draft.format)

        jobs.recordStoreTarget(job.id, storedPath)
        storage.copyAtomically(staged, storedPath)

        transaction(db) {
            val fileId = with(library) {
                insertPublicationWithOriginal(
                    NewPublication(publicationId, draft.kind, draft.title, sortTitle = draft.title),
                    NewOriginal(draft.format, hashes, storedPath),
                )
            }
            jobs.finishInTransaction(job.id, ImportState.DONE, publicationId = publicationId, fileId = fileId)
        }
        clearStaging(job)
    }

    /** A failed job keeps its file in `failed/<jobId>/` so it can be fixed and dropped in again. */
    private fun fail(job: ImportJob, reason: String) {
        val staged = stagedFile(job)
        if (staged.exists()) {
            val destination = storage.failed.resolve(job.id.toString()).resolve(job.originalName)
            Files.createDirectories(destination.parent)
            Files.move(staged, destination)
        }
        jobs.finish(job.id, ImportState.FAILED, reason = reason)
        clearStaging(job)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun clearStaging(job: ImportJob) {
        storage.staging.resolve(job.id.toString()).deleteRecursively()
    }

    companion object {
        fun titleFromFileName(name: String): String = Path.of(name).nameWithoutExtension.trim().ifEmpty { name }
    }
}
