package theindex.imports

import org.slf4j.LoggerFactory
import theindex.files.Storage
import theindex.publications.Kind
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

/**
 * The inbox folder, polled for new files (spec §5). A file becomes an Import job once its
 * size and modification time have held still for [stabilityWindow], so half-copied files wait.
 */
class Inbox(
    private val root: Path,
    private val jobs: ImportJobs,
    private val storage: Storage,
    private val clock: Clock,
    private val stabilityWindow: Duration,
) {
    private val log = LoggerFactory.getLogger(Inbox::class.java)

    private data class Observation(val size: Long, val modified: Instant, val since: Instant)

    private val observed = mutableMapOf<Path, Observation>()

    /** One polling pass; returns the ids of the jobs it created. */
    @Synchronized
    fun scan(): List<Long> {
        if (!Files.isDirectory(root)) return emptyList()
        val now = clock.instant()
        val candidates = Files.walk(root).use { paths ->
            paths.asSequence()
                .filter { it.isRegularFile() && !isHidden(it) }
                .toList()
        }
        observed.keys.retainAll(candidates.toSet())

        val created = candidates.mapNotNull { file ->
            val size = Files.size(file)
            val modified = Files.getLastModifiedTime(file).toInstant()
            val previous = observed[file]
            when {
                previous == null || previous.size != size || previous.modified != modified -> {
                    observed[file] = Observation(size, modified, since = now)
                    null
                }
                Duration.between(previous.since, now) >= stabilityWindow -> {
                    observed.remove(file)
                    enqueue(file)
                }
                else -> null
            }
        }
        removeDrainedFolders()
        return created
    }

    private fun enqueue(file: Path): Long {
        val id = jobs.create(ImportSource.INBOX, file.name, kindHint(file))
        try {
            val staged = storage.staging.resolve(id.toString()).resolve(file.name)
            Files.createDirectories(staged.parent)
            Files.move(file, staged)
        } catch (e: Exception) {
            log.error("Could not stage inbox file $file", e)
            jobs.finish(id, ImportState.FAILED, reason = "could not stage: ${e.message}")
        }
        return id
    }

    /** `inbox/prose/…` and `inbox/comics/…` force the Kind; files elsewhere are inferred. */
    private fun kindHint(file: Path): Kind? {
        val relative = file.relativeTo(root)
        if (relative.nameCount < 2) return null
        return HINT_FOLDERS[relative.getName(0).toString()]
    }

    private fun isHidden(file: Path): Boolean = file.relativeTo(root).any { it.toString().startsWith(".") }

    /** Removes subfolders the scan has emptied, deepest first; never the root or the hint folders. */
    private fun removeDrainedFolders() {
        val folders = Files.walk(root).use { paths ->
            paths.asSequence()
                .filter { it != root && it.isDirectory() && !isHidden(it) }
                .sortedByDescending { it.nameCount }
                .toList()
        }
        for (folder in folders) {
            val relative = folder.relativeTo(root)
            val isHintFolder = relative.nameCount == 1 && relative.toString() in HINT_FOLDERS
            if (!isHintFolder && folder.listDirectoryEntries().isEmpty()) {
                runCatching { Files.delete(folder) }
            }
        }
    }

    companion object {
        val HINT_FOLDERS = mapOf("prose" to Kind.PROSE, "comics" to Kind.COMIC)
    }
}
