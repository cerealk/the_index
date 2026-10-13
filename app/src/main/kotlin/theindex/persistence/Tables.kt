package theindex.persistence

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp

object PublicationTable : LongIdTable("publication") {
    val kind = text("kind")
    val title = text("title")
    val sortTitle = text("sort_title")
    val createdAt = timestamp("created_at")
    val trashedAt = timestamp("trashed_at").nullable()
}

object FileTable : LongIdTable("file") {
    val publicationId = reference("publication_id", PublicationTable)
    val format = text("format")
    val size = long("size")
    val sha256 = varchar("sha256", 64)
    val partialMd5 = varchar("partial_md5", 32)
    val role = text("role")
    val derivedFrom = reference("derived_from", FileTable).nullable()
    val pageCount = integer("page_count").nullable()
    val storedPath = text("stored_path")
    val importedAt = timestamp("imported_at")
    val trashedAt = timestamp("trashed_at").nullable()
}

object ImportJobTable : LongIdTable("import_job") {
    val importSource = text("source")
    val originalName = text("original_name")
    val kindHint = text("kind_hint").nullable()
    val state = text("state")
    val reason = text("reason").nullable()
    val warnings = array<String>("warnings")
    val storeTarget = text("store_target").nullable()
    val publicationId = reference("publication_id", PublicationTable).nullable()
    val fileId = reference("file_id", FileTable).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
