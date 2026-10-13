package theindex.opds

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalPathContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.net.URLEncoder
import kotlin.io.path.isRegularFile

/** The OPDS 1.2 catalog under `/opds/` and its File downloads (spec §7). No auth. */
fun Route.opdsRoutes(catalog: OpdsCatalog) {
    route("/opds") {
        get { call.respondFeed(catalog.root()) }
        get("/") { call.respondFeed(catalog.root()) }
        get("/recent") { call.page()?.let { call.respondFeed(catalog.recentlyAdded(it)) } }
        get("/all") { call.page()?.let { call.respondFeed(catalog.all(it)) } }
        get("/files/{id}/{name?}") {
            val download = call.parameters["id"]?.toLongOrNull()?.let(catalog::download)
            if (download == null || !download.path.isRegularFile()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.response.header(HttpHeaders.ContentDisposition, attachment(download.fileName))
            call.respond(LocalPathContent(download.path, ContentType.parse(download.format.mediaType)))
        }
    }
}

private suspend fun RoutingCall.respondFeed(feed: Feed) =
    respondText(AtomSerializer.write(feed), ContentType.parse(AtomSerializer.contentType(feed.kind)).withParameter("charset", "utf-8"))

/** The `page` query parameter (1-based), or null after answering 400 when it isn't a positive number. */
private suspend fun RoutingCall.page(): Int? {
    val raw = request.queryParameters["page"] ?: return 1
    val page = raw.toIntOrNull()?.takeIf { it >= 1 }
    if (page == null) respond(HttpStatusCode.BadRequest, "page must be a positive number")
    return page
}

/** RFC 6266: an ASCII fallback for old clients plus the exact UTF-8 name. */
private fun attachment(fileName: String): String {
    val fallback = fileName.map { if (it.code in 0x20..0x7E && it != '"' && it != '\\') it else '_' }.joinToString("")
    val encoded = URLEncoder.encode(fileName, Charsets.UTF_8).replace("+", "%20")
    return "attachment; filename=\"$fallback\"; filename*=UTF-8''$encoded"
}
