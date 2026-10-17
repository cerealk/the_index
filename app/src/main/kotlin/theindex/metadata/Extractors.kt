package theindex.metadata

import theindex.files.Format
import java.nio.file.Path

/** Stage 3 of the import pipeline, per Format. pdf gets its extractor in a later ticket. */
object Extractors {
    fun extract(format: Format, path: Path): Extraction = when (format) {
        Format.EPUB -> Extraction(EpubExtractor.extract(path))
        Format.CBZ -> ComicExtractor.extract(path)
        Format.CBR -> RarComicArchive.open(path).use { ComicExtractor.extract(it, path.fileName.toString()) }
        Format.PDF -> Extraction(ExtractedMetadata.NONE)
    }
}
