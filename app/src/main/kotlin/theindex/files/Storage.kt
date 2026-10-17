package theindex.files

import theindex.publications.Kind
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * The storage volume: the per-Kind layout of Publication folders (spec §4) plus the
 * staging and failed areas used by the import pipeline (spec §5).
 */
class Storage(val root: Path) {
    val staging: Path = root.resolve(".staging")
    val failed: Path = root.resolve("failed")

    fun createDirectories() {
        listOf(root, staging, failed).forEach { Files.createDirectories(it) }
    }

    /**
     * Where a Publication's File lives, relative to [root] (spec §4):
     * - prose: `prose/<Author>/<Series>/<NN> - <Title> (<id>)/<Title>.<ext>`, or `prose/<Author>/<Title> (<id>)/…`
     * - comic: `comics/<Series>/<Series> #<Number> - <Title> (<id>)/<same name>.<ext>`, or `comics/<Title> (<id>)/…`;
     *   an untitled issue drops ` - <Title>`.
     */
    fun layoutPath(layout: Layout): String {
        val title = segment(layout.title)
        val ext = layout.format.extension
        val id = layout.publicationId
        val series = layout.series?.let(::segment)
        val position = layout.position?.takeIf { it.isNotBlank() }
        return when (layout.kind) {
            Kind.PROSE -> {
                val author = segment(layout.author ?: UNKNOWN_AUTHOR)
                when {
                    series == null -> "prose/$author/$title ($id)/$title.$ext"
                    position == null -> "prose/$author/$series/$title ($id)/$title.$ext"
                    else -> "prose/$author/$series/${segment(padded(position))} - $title ($id)/$title.$ext"
                }
            }
            Kind.COMIC -> {
                if (series == null) {
                    "comics/$title ($id)/$title.$ext"
                } else {
                    val issue = if (position == null) series else "$series #$position"
                    val name = segment(if (layout.untitledIssue) issue else "$issue - $title")
                    "comics/$series/$name ($id)/$name.$ext"
                }
            }
        }
    }

    fun resolve(relativePath: String): Path = root.resolve(relativePath)

    /** Copies [source] to [relativeTarget] so that the target is either absent or complete, never partial. */
    fun copyAtomically(source: Path, relativeTarget: String): Path {
        val target = resolve(relativeTarget)
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.copy(source, temporary)
            FileChannel.open(temporary, READ, WRITE).use { it.force(true) }
            Files.move(temporary, target, ATOMIC_MOVE)
            syncDirectory(target.parent)
        } finally {
            temporary.deleteIfExists()
        }
        return target
    }

    /** Deletes a stored file and any Publication folders left empty above it. */
    fun deleteStored(relativePath: String) {
        val path = resolve(relativePath)
        path.deleteIfExists()
        var directory = path.parent
        while (directory != root && directory.startsWith(root) && directory.isDirectory() && directory.listDirectoryEntries().isEmpty()) {
            Files.delete(directory)
            directory = directory.parent
        }
    }

    private fun syncDirectory(directory: Path) {
        // Not every platform lets a directory be opened for fsync (macOS refuses); the rename is still atomic.
        runCatching { FileChannel.open(directory, READ).use { it.force(true) } }
    }

    /** Everything that decides a stored path. [series] is the display name, volume included. */
    data class Layout(
        val kind: Kind,
        val publicationId: Long,
        val title: String,
        val format: Format,
        val author: String? = null,
        val series: String? = null,
        val position: String? = null,
        /** The title only repeats the Series and number ("Saga #12"), so the folder leaves it out. */
        val untitledIssue: Boolean = false,
    )

    companion object {
        const val UNKNOWN_AUTHOR = "Unknown"
        private val INTEGER_PREFIX = Regex("""^(\d+)(.*)$""")

        /** Two-digit positions keep a Series folder in reading order: "1" → "01", "1.5" → "01.5". */
        private fun padded(position: String): String =
            INTEGER_PREFIX.matchEntire(position.trim())?.let { it.groupValues[1].padStart(2, '0') + it.groupValues[2] } ?: position
        private const val MAX_SEGMENT_LENGTH = 100
        private val ILLEGAL = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

        /** Makes one path segment safe on every filesystem the volume might live on. */
        fun segment(text: String): String {
            val cleaned = text.replace(ILLEGAL, "_").trim().trimEnd('.', ' ').trimStart('.')
            val shortened = if (cleaned.length > MAX_SEGMENT_LENGTH) cleaned.take(MAX_SEGMENT_LENGTH).trimEnd() else cleaned
            return shortened.ifEmpty { "Untitled" }
        }
    }
}
