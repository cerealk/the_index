package theindex.publications

import theindex.files.Format

enum class Kind(val dbValue: String) {
    PROSE("prose"),
    COMIC("comic");

    companion object {
        fun fromDb(value: String): Kind = entries.single { it.dbValue == value }

        /** The default before any metadata is read: comic archives are comics, everything else is prose. */
        fun defaultFor(format: Format): Kind = when (format) {
            Format.CBZ, Format.CBR -> COMIC
            Format.EPUB, Format.PDF -> PROSE
        }
    }
}
