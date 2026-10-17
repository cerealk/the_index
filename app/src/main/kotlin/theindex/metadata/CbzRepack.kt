package theindex.metadata

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The cbr → cbz Conversion (spec §5 stage 7): the same entries, in the same order, with
 * the same bytes, moved from RAR into zip. Nothing is re-encoded, and entries carry a fixed
 * timestamp, so the same cbr always gives the same cbz.
 */
object CbzRepack {
    /** 1980-01-01, the earliest time a zip entry can hold. */
    private val FIXED_TIME = FileTime.from(LocalDateTime.of(1980, 1, 1, 0, 0).toInstant(ZoneOffset.UTC))

    fun repack(source: ComicArchive, target: Path) {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            for (name in source.entryNames) {
                zip.putNextEntry(ZipEntry(safeName(name)).apply { lastModifiedTime = FIXED_TIME })
                zip.write(source.read(name))
                zip.closeEntry()
            }
        }
    }

    /** No absolute paths or `..` segments in the zip, whatever the RAR held. */
    private fun safeName(name: String): String =
        name.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")
}
