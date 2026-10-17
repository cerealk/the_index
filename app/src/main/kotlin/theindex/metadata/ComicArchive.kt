package theindex.metadata

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** A comic archive's entries, whatever the container; cbr gets its own implementation. */
interface ComicArchive : AutoCloseable {
    /** File entries (not directories), in archive order. */
    val entryNames: List<String>

    fun read(name: String): ByteArray

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl", "bmp")

        /** A page is an image that isn't macOS resource-fork debris or a hidden file. */
        fun isPage(name: String): Boolean {
            val fileName = name.substringAfterLast('/')
            return fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS &&
                !fileName.startsWith(".") && "__MACOSX/" !in name
        }
    }
}

class ZipComicArchive private constructor(private val zip: ZipFile) : ComicArchive {
    override val entryNames: List<String> = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()

    override fun read(name: String): ByteArray = zip.getInputStream(zip.getEntry(name)).use { it.readAllBytes() }

    override fun close() = zip.close()

    companion object {
        fun open(path: Path): ZipComicArchive = try {
            ZipComicArchive(ZipFile(path.toFile()))
        } catch (e: ZipException) {
            throw UnreadableFileException("cannot open cbz: ${e.message}", e)
        } catch (e: IOException) {
            throw UnreadableFileException("cannot open cbz: ${e.message}", e)
        }
    }
}
