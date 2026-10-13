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
     * Where a Publication's File lives, relative to [root]. Until Credits and Series exist,
     * prose goes under the "Unknown" author and comics have no Series folder.
     */
    fun layoutPath(kind: Kind, publicationId: Long, title: String, format: Format): String {
        val name = segment(title)
        val folder = "$name ($publicationId)"
        val file = "$name.${format.extension}"
        return when (kind) {
            Kind.PROSE -> "prose/${segment(UNKNOWN_AUTHOR)}/$folder/$file"
            Kind.COMIC -> "comics/$folder/$file"
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

    companion object {
        const val UNKNOWN_AUTHOR = "Unknown"
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
