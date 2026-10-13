package theindex.imports

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class ImportJobResponse(
    val id: Long,
    val source: String,
    val originalName: String,
    val kindHint: String?,
    val state: String,
    val reason: String?,
    val warnings: List<String>,
    val publicationId: Long?,
    val fileId: Long?,
    val createdAt: String,
    val updatedAt: String,
)

fun ImportJob.toResponse() = ImportJobResponse(
    id = id,
    source = source.dbValue,
    originalName = originalName,
    kindHint = kindHint?.dbValue,
    state = state.dbValue,
    reason = reason,
    warnings = warnings,
    publicationId = publicationId,
    fileId = fileId,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

/** `GET /api/v1/imports` and `GET /api/v1/imports/{id}`. Problem Details and pagination come later. */
fun Route.importRoutes(jobs: ImportJobs) {
    route("/api/v1/imports") {
        get {
            val stateParameter = call.request.queryParameters["state"]
            val state = stateParameter?.let { value -> ImportState.entries.find { it.dbValue == value } }
            if (stateParameter != null && state == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown state '$stateParameter'"))
                return@get
            }
            call.respond(jobs.list(state).map { it.toResponse() })
        }
        get("/{id}") {
            val job = call.parameters["id"]?.toLongOrNull()?.let(jobs::find)
            if (job == null) call.respond(HttpStatusCode.NotFound) else call.respond(job.toResponse())
        }
    }
}
