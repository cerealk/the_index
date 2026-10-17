package theindex.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.json.jsonb
import theindex.metadata.ExtractedMetadata
import org.jetbrains.exposed.v1.javatime.timestamp

object PublicationTable : LongIdTable("publication") {
    val kind = text("kind")
    val title = text("title")
    val sortTitle = text("sort_title")
    val createdAt = timestamp("created_at")
    val trashedAt = timestamp("trashed_at").nullable()
    val description = text("description").nullable()
    val publisher = text("publisher").nullable()
    val language = text("language").nullable()
    val publicationDate = text("publication_date").nullable()
    val seriesId = reference("series_id", SeriesTable).nullable()
    val seriesPositionText = text("series_position_text").nullable()
    val seriesPositionSort = decimal("series_position_sort", 20, 6).nullable()
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
    val metadata = jsonb<ExtractedMetadata>("metadata", Json).nullable()
}

object ContributorTable : LongIdTable("contributor") {
    val name = text("name")
    val sortName = text("sort_name")
    val matchKey = text("match_key")
}

object SeriesTable : LongIdTable("series") {
    val name = text("name")
    val volume = integer("volume").nullable()
    val matchKey = text("match_key")
}

object TagTable : LongIdTable("tag") {
    val name = text("name")
    val matchKey = text("match_key")
}

object CreditTable : Table("credit") {
    val publicationId = reference("publication_id", PublicationTable)
    val position = integer("position")
    val contributorId = reference("contributor_id", ContributorTable)
    val role = text("role")
    override val primaryKey = PrimaryKey(publicationId, position)
}

object PublicationTagTable : Table("publication_tag") {
    val publicationId = reference("publication_id", PublicationTable)
    val tagId = reference("tag_id", TagTable)
    override val primaryKey = PrimaryKey(publicationId, tagId)
}

object IdentifierTable : Table("identifier") {
    val publicationId = reference("publication_id", PublicationTable)
    val type = text("type")
    val value = text("value")
    override val primaryKey = PrimaryKey(publicationId, type, value)
}

object ReadingListTable : LongIdTable("reading_list") {
    val name = text("name")
    val matchKey = text("match_key")
}

object ReadingListEntryTable : Table("reading_list_entry") {
    val readingListId = reference("reading_list_id", ReadingListTable)
    val publicationId = reference("publication_id", PublicationTable)
    val position = integer("position")
    val numberSort = decimal("number_sort", 20, 6).nullable()
    override val primaryKey = PrimaryKey(readingListId, publicationId)
}
