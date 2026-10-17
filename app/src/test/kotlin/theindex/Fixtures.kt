package theindex

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small, synthetic files of each Format. Only their structure matters, not their content. */
object Fixtures {
    /** A structurally valid epub around [opf]; [salt] only changes the bytes. */
    fun epub(salt: String = "", opf: String = opf(comment = salt)): ByteArray = epubEntries(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to opf,
    )

    /** An OPF package document; [metadata] goes inside `<metadata>` with the dc and opf prefixes declared. */
    fun opf(metadata: String = "", version: String = "3.0", comment: String = ""): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="$version" unique-identifier="uid">
<!-- $comment -->
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
$metadata
</metadata>
</package>
"""

    /** An epub (by its mimetype) whose zip holds [entries] after the mimetype. */
    fun epubEntries(vararg entries: Pair<String, String>): ByteArray = zip {
        val mimetype = "application/epub+zip".toByteArray()
        putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetype.size.toLong()
            crc = CRC32().apply { update(mimetype) }.value
        })
        write(mimetype)
        entries.forEach { (name, content) ->
            putNextEntry(ZipEntry(name))
            write(content.toByteArray())
        }
    }

    const val CONTAINER = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""

    fun cbz(salt: String = ""): ByteArray = zip {
        putNextEntry(ZipEntry("001.jpg"))
        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + salt.toByteArray())
        putNextEntry(ZipEntry("002.jpg"))
        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
    }

    /** A cbz holding exactly [entries], in order. */
    fun cbzEntries(vararg entries: Pair<String, ByteArray>): ByteArray = zip {
        entries.forEach { (name, content) ->
            putNextEntry(ZipEntry(name))
            write(content)
        }
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
