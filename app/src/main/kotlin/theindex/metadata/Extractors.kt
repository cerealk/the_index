package theindex.metadata

import theindex.files.Format
import java.nio.file.Path

/** Stage 3 of the import pipeline, per Format. pdf and cbr get their extractors in later tickets. */
object Extractors {
    fun extract(format: Format, path: Path): Extraction = when (format) {
        Format.EPUB -> Extraction(EpubExtractor.extract(path))
        Format.CBZ -> ComicExtractor.extract(path)
        Format.PDF, Format.CBR -> Extraction(ExtractedMetadata.NONE)
    }
}
