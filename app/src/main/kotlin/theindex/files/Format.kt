package theindex.files

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Declared in preference order: when a Publication has several Files, the earlier Format is offered first (spec §7). */
enum class Format(val extension: String, val mediaType: String) {
    CBZ("cbz", "application/vnd.comicbook+zip"),
    CBR("cbr", "application/vnd.comicbook-rar"),
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf");

    val dbValue: String get() = extension

    companion object {
        fun fromDb(value: String): Format = entries.single { it.dbValue == value }
    }
}

/** Detects a File's Format from its bytes, never from its name (spec §5, stage 1). */
object FormatDetector {
    private val PDF_MAGIC = "%PDF-".toByteArray()
    private val RAR_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) // "Rar!\u001A\u0007"
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl", "bmp")

    fun detect(path: Path): Format? {
        val head = Files.newInputStream(path).use { it.readNBytes(8) }
        return when {
            head.startsWith(PDF_MAGIC) -> Format.PDF
            head.startsWith(RAR_MAGIC) -> Format.CBR
            head.startsWith(ZIP_MAGIC) -> detectZip(path)
            else -> null
        }
    }

    /** A zip is an epub when it declares so in `mimetype`, a cbz when it holds images, otherwise unknown. */
    private fun detectZip(path: Path): Format? = runCatching {
        ZipFile(path.toFile()).use { zip ->
            val mimetype = zip.getEntry("mimetype")
                ?.let { entry -> zip.getInputStream(entry).use { String(it.readNBytes(64)).trim() } }
            when {
                mimetype == "application/epub+zip" -> Format.EPUB
                zip.entries().asSequence().any { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS } -> Format.CBZ
                else -> null
            }
        }
    }.getOrNull()

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
