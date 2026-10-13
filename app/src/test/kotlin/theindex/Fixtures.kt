package theindex

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small, synthetic files of each Format. Only their structure matters, not their content. */
object Fixtures {
    fun epub(salt: String = ""): ByteArray = zip {
        val mimetype = "application/epub+zip".toByteArray()
        putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetype.size.toLong()
            crc = CRC32().apply { update(mimetype) }.value
        })
        write(mimetype)
        putNextEntry(ZipEntry("META-INF/container.xml"))
        write("<container/>".toByteArray())
        putNextEntry(ZipEntry("OEBPS/content.opf"))
        write("<package>$salt</package>".toByteArray())
    }

    fun cbz(salt: String = ""): ByteArray = zip {
        putNextEntry(ZipEntry("001.jpg"))
        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + salt.toByteArray())
        putNextEntry(ZipEntry("002.jpg"))
        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
    }

    fun pdf(salt: String = ""): ByteArray = "%PDF-1.4\n% $salt\n%%EOF\n".toByteArray()

    /** Just the RAR 5 signature and some bytes: enough for Format detection. */
    fun cbr(salt: String = ""): ByteArray =
        byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00) + salt.toByteArray()

    fun zipWithoutImages(): ByteArray = zip {
        putNextEntry(ZipEntry("notes.txt"))
        write("not a comic".toByteArray())
    }

    fun text(): ByteArray = "just some notes\n".toByteArray()

    fun write(path: Path, bytes: ByteArray): Path {
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return path
    }

    private fun zip(block: ZipOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { out -> ZipOutputStream(out).use(block) }.toByteArray()
}
