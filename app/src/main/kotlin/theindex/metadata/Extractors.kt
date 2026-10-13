package theindex.metadata

import theindex.files.Format
import java.nio.file.Path

/** Stage 3 of the import pipeline, per Format. pdf and comic archives get their extractors in later tickets. */
object Extractors {
    fun extract(format: Format, path: Path): ExtractedMetadata = when (format) {
        Format.EPUB -> EpubExtractor.extract(path)
        Format.PDF, Format.CBZ, Format.CBR -> ExtractedMetadata.NONE
    }
}
