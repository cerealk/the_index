package theindex.publications

/** The fixed set of Credit roles (spec §3). */
enum class Role(val dbValue: String) {
    AUTHOR("author"),
    WRITER("writer"),
    PENCILLER("penciller"),
    INKER("inker"),
    COLORIST("colorist"),
    LETTERER("letterer"),
    COVER_ARTIST("cover artist"),
    ILLUSTRATOR("illustrator"),
    TRANSLATOR("translator"),
    EDITOR("editor");

    companion object {
        fun fromDb(value: String): Role = entries.single { it.dbValue == value }

        /** The role of a Credit whose source gave none. */
        fun defaultFor(kind: Kind): Role = when (kind) {
            Kind.PROSE -> AUTHOR
            Kind.COMIC -> WRITER
        }

        /** MARC relator codes and plain role words onto the fixed set; null when the source role has no place in it. */
        fun fromSource(value: String): Role? = when (value.trim().lowercase()) {
            "aut", "author", "cre", "creator" -> AUTHOR
            "wri", "writer", "aus", "scenarist" -> WRITER
            "pencils", "penciller", "penciler" -> PENCILLER
            "inker", "inks" -> INKER
            "clr", "colorist", "colourist", "colors" -> COLORIST
            "letterer", "letters" -> LETTERER
            "cov", "cover artist", "coverartist", "cover" -> COVER_ARTIST
            "ill", "illustrator", "art", "artist" -> ILLUSTRATOR
            "trl", "translator" -> TRANSLATOR
            "edt", "editor" -> EDITOR
            else -> null
        }
    }
}
