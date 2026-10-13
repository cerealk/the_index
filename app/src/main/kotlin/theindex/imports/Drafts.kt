package theindex.imports

import theindex.metadata.ExtractedMetadata
import theindex.publications.CreditDraft
import theindex.publications.Kind
import theindex.publications.Names
import theindex.publications.PublicationDraft
import theindex.publications.Role
import theindex.publications.SeriesDraft
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

/** A draft plus the warnings its gaps raise on the Import job. */
data class DraftResult(val draft: PublicationDraft, val warnings: List<String>)

/** Turns extracted metadata into a new Publication's draft, filling gaps from the file name (spec §5 stage 3). */
object Drafts {
    fun from(metadata: ExtractedMetadata, kind: Kind, originalName: String): DraftResult {
        val warnings = mutableListOf<String>()

        val title = metadata.title?.takeIf { it.isNotBlank() }?.trim()
            ?: titleFromFileName(originalName).also { warnings += "no title in the metadata, the file name was used" }
        val contributors = metadata.contributors.filter { it.name.isNotBlank() && it.name.trim().lowercase() !in PLACEHOLDER_NAMES }
        if (contributors.isEmpty()) warnings += "no Contributors in the metadata"

        val credits = contributors
            .map { contributor ->
                val name = Names.displayName(contributor.name)
                CreditDraft(
                    name = name,
                    sortName = contributor.fileAs ?: sortNameFromSource(contributor.name) ?: Names.deriveSortName(name),
                    role = contributor.role?.let(Role::fromSource) ?: Role.defaultFor(kind),
                )
            }
            .distinctBy { Names.contributorKey(it.name) to it.role }

        return DraftResult(
            PublicationDraft(
                kind = kind,
                title = title,
                sortTitle = metadata.sortTitle ?: Names.deriveSortTitle(title, metadata.language),
                credits = credits,
                series = metadata.series?.takeIf { it.name.isNotBlank() }
                    ?.let { SeriesDraft(it.name.trim(), it.volume, it.position) },
                tags = metadata.tags.filter { it.isNotBlank() },
                description = metadata.description,
                publisher = metadata.publisher,
                language = metadata.language,
                publicationDate = metadata.publicationDate,
                identifiers = metadata.identifiers,
            ),
            warnings,
        )
    }

    /** What tools write when they know no author; these are not Contributors. */
    private val PLACEHOLDER_NAMES = setOf("unknown", "unknown author", "sconosciuto", "autore sconosciuto", "inconnu")

    /** A name written "Last, First" is already its own sort name. */
    private fun sortNameFromSource(name: String): String? =
        name.trim().takeIf { Names.displayName(it) != it.replace(Regex("\\s+"), " ") }

    /** The last fallback: the file name without its extension, underscores read as spaces. */
    fun titleFromFileName(name: String): String =
        Path.of(name).nameWithoutExtension.replace('_', ' ').trim().ifEmpty { name }
}
