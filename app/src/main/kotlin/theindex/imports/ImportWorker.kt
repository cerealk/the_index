package theindex.imports

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import theindex.files.Format
import theindex.files.FormatDetector
import theindex.files.Hasher
import theindex.files.Hashes
import theindex.files.Storage
import theindex.metadata.CbzRepack
import theindex.metadata.ComicArchive
import theindex.metadata.Extraction
import theindex.metadata.Extractors
import theindex.metadata.RarComicArchive
import theindex.metadata.UnreadableFileException
import theindex.publications.Kind
import theindex.publications.Library
import theindex.publications.Matcher
import theindex.publications.StrongMatch
import theindex.publications.WeakMatch
import theindex.publications.Names
import theindex.publications.NewOriginal
import theindex.publications.PublicationDraft
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.ExperimentalPathApi

/** Writes a comic archive's entries into a new cbz at the given path. */
typealias Converter = (ComicArchive, Path) -> Unit

/** Stage 3: what a File of the given Format holds. */
typealias Extractor = (Format, Path) -> Extraction

/**
 * Runs Import jobs through the pipeline one at a time (spec §5). Implemented stages:
 * detect Format, hash (exact duplicates skipped), extract metadata, decide Kind, match an existing
 * Publication, store the Original (new or attached), convert cbr to cbz.
 */
class ImportWorker(
    private val db: Database,
    private val jobs: ImportJobs,
    private val library: Library,
    private val storage: Storage,
    private val matcher: Matcher,
    private val convert: Converter = CbzRepack::repack,
    private val extract: Extractor = Extractors::extract,
) {
    private val log = LoggerFactory.getLogger(ImportWorker::class.java)

    fun stagedFile(job: ImportJob): Path = storage.staging.resolve(job.id.toString()).resolve(job.originalName)

    /**
     * Startup recovery: throw away copies a crash interrupted before their rows committed,
     * and put jobs that were mid-flight back in the queue. The staged file is still there.
     * Then make any missing cbz Conversion, so stage 7 is idempotent.
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
        for (fileId in library.cbrOriginalsWithoutConversion()) {
            convertOriginal(fileId)?.let { log.warn("File $fileId: $it") }
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

        // Stage 3: metadata, kept on the job as a raw snapshot.
        val extraction = try {
            extract(format, staged)
        } catch (e: UnreadableFileException) {
            return fail(job, e.message ?: "cannot open the file")
        }
        jobs.recordMetadata(job.id, extraction.metadata)

        // Stage 4: Kind, from the hint or the Format until inference exists.
        val kind = job.kindHint ?: Kind.defaultFor(format)
        val (draft, warnings) = Drafts.from(extraction.metadata, kind, job.originalName)

        // Stage 5: match an existing Publication; stage 6: store the Original.
        val file = NewFile(format, hashes, extraction.pageCount)
        val fileId = when (val match = matcher.match(draft)) {
            is StrongMatch -> attach(job, staged, match, draft, file, warnings)
            is WeakMatch -> store(job, staged, draft, file, warnings + "possible duplicate of Publication ${match.publicationId}", match.publicationId)
            null -> store(job, staged, draft, file, warnings)
        }

        // Stage 7: every cbr Original gets a cbz Conversion; failing that is only a warning.
        if (format == Format.CBR) convertOriginal(fileId)?.let { jobs.addWarning(job.id, it) }
    }

    private data class NewFile(val format: Format, val hashes: Hashes, val pageCount: Int?)

    /** Stores the Original as a new Publication; returns its File id. */
    private fun store(
        job: ImportJob,
        staged: Path,
        draft: PublicationDraft,
        file: NewFile,
        warnings: List<String>,
        possibleDuplicateOf: Long? = null,
    ): Long {
        val publicationId = library.reservePublicationId()
        val names = library.layoutNames(draft)
        val storedPath = storage.layoutPath(
            Storage.Layout(
                kind = draft.kind,
                publicationId = publicationId,
                title = draft.title,
                format = file.format,
                author = names.author,
                series = names.series,
                position = draft.series?.positionText,
                untitledIssue = draft.series?.let { draft.title == Names.issueTitle(it.name, it.positionText) } ?: false,
            ),
        )

        jobs.recordStoreTarget(job.id, storedPath)
        storage.copyAtomically(staged, storedPath)

        val fileId = transaction(db) {
            val original = NewOriginal(file.format, file.hashes, storedPath, file.pageCount)
            val fileId = with(library) { insertPublication(publicationId, draft, original, possibleDuplicateOf) }
            jobs.finishInTransaction(
                job.id,
                ImportState.DONE,
                warnings = warnings,
                publicationId = publicationId,
                fileId = fileId,
            )
            fileId
        }
        clearStaging(job)
        return fileId
    }

    /**
     * Stores the Original in a strongly matched Publication's folder, next to its Files and named
     * like them (spec §4), filling only that Publication's empty fields. Returns the File id.
     */
    private fun attach(job: ImportJob, staged: Path, match: StrongMatch, draft: PublicationDraft, file: NewFile, warnings: List<String>): Long {
        val (folder, name) = library.publicationFolder(match.publicationId)
        val storedPath = freePath(folder, name, file.format)

        jobs.recordStoreTarget(job.id, storedPath)
        storage.copyAtomically(staged, storedPath)

        val fileId = transaction(db) {
            val original = NewOriginal(file.format, file.hashes, storedPath, file.pageCount)
            val fileId = with(library) { attachOriginal(match.publicationId, draft, original) }
            jobs.finishInTransaction(
                job.id,
                ImportState.DONE,
                reason = "attached to Publication ${match.publicationId}: ${match.reason}",
                warnings = warnings,
                publicationId = match.publicationId,
                fileId = fileId,
            )
            fileId
        }
        clearStaging(job)
        return fileId
    }

    /**
     * Repacks a cbr Original into a cbz Conversion next to it (spec §5 stage 7), built in
     * staging and then moved in like any stored File. Returns a warning when it can't.
     */
    private fun convertOriginal(originalId: Long): String? {
        val original = library.storedFile(originalId)
        val target = freePath(
            folder = original.storedPath.substringBeforeLast('/'),
            name = original.storedPath.substringAfterLast('/').substringBeforeLast('.'),
            format = Format.CBZ,
        )
        val built = storage.staging.resolve("conversion-$originalId.${Format.CBZ.extension}")
        return try {
            Files.createDirectories(built.parent)
            RarComicArchive.open(storage.resolve(original.storedPath)).use { convert(it, built) }
            val hashes = Hasher.hash(built)
            library.fileIdWithSha256(hashes.sha256)?.let { return "cbz Conversion is already in the library as File $it" }
            storage.copyAtomically(built, target)
            library.insertConversion(original, hashes, target)
            null
        } catch (e: Exception) {
            log.warn("Conversion of File $originalId failed", e)
            "cbz Conversion failed: ${e.message ?: e::class.simpleName}"
        } finally {
            built.deleteIfExists()
        }
    }

    /**
     * `<name>.<ext>` in [folder], or `<name> (2).<ext>`, `(3)`… when a File already has that path:
     * a new File never replaces one. An unrecorded leftover from a crash is fair game.
     */
    private fun freePath(folder: String, name: String, format: Format): String =
        generateSequence(1) { it + 1 }
            .map { n -> "$folder/${if (n == 1) name else "$name ($n)"}.${format.extension}" }
            .first { !library.isStored(it) }

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
}
