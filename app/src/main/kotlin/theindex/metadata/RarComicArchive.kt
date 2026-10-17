package theindex.metadata

import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.exception.UnsupportedRarEncryptedException
import com.github.junrar.exception.WrongPasswordException
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path

/**
 * A cbr read with junrar, pure JVM (RAR 4 and RAR 5). Encrypted archives are refused,
 * as readers can't open them either. Entry names use `/` whatever the archiver wrote.
 */
class RarComicArchive private constructor(private val archive: Archive) : ComicArchive {
    private val headers: Map<String, FileHeader> = archive.fileHeaders
        .filterNot { it.isDirectory }
        .associateBy { it.fileName.replace('\\', '/') }

    override val entryNames: List<String> = headers.keys.toList()

    override fun read(name: String): ByteArray {
        val header = headers[name] ?: throw UnreadableFileException("cannot open cbr: no entry $name")
        return unreadable {
            ByteArrayOutputStream().also { archive.extractFile(header, it) }.toByteArray()
        }
    }

    override fun close() = archive.close()

    companion object {
        fun open(path: Path): RarComicArchive {
            val archive = try {
                unreadable { Archive(path.toFile()) }
            } catch (e: UnreadableFileException) {
                // Header-encrypted RAR 5 can't even be listed without the password.
                if (e.cause is WrongPasswordException || e.cause is UnsupportedRarEncryptedException) {
                    throw UnreadableFileException("cannot open cbr: the archive is encrypted", e.cause)
                }
                throw e
            }
            val problem = unreadable {
                when {
                    archive.isEncrypted || archive.fileHeaders.any { it.isEncrypted } -> "the archive is encrypted"
                    archive.hasBrokenHeaders() -> "the archive is damaged"
                    archive.fileHeaders.none { !it.isDirectory } -> "the archive is empty"
                    else -> null
                }
            }
            if (problem != null) {
                archive.close()
                throw UnreadableFileException("cannot open cbr: $problem")
            }
            return RarComicArchive(archive)
        }

        private fun <T> unreadable(block: () -> T): T = try {
            block()
        } catch (e: RarException) {
            throw UnreadableFileException("cannot open cbr: ${e.message ?: e::class.simpleName}", e)
        } catch (e: IOException) {
            throw UnreadableFileException("cannot open cbr: ${e.message ?: e::class.simpleName}", e)
        }
    }
}
