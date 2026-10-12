package library.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import javax.sql.DataSource

fun interface HealthCheck {
    fun databaseUp(): Boolean
}

class DataSourceHealthCheck(private val dataSource: DataSource) : HealthCheck {
    override fun databaseUp(): Boolean =
        runCatching { dataSource.connection.use { it.isValid(2) } }.getOrDefault(false)
}

@Serializable
data class HealthResponse(val database: String)

fun Route.healthRoutes(check: HealthCheck) {
    get("/api/v1/health") {
        if (check.databaseUp()) {
            call.respond(HealthResponse(database = "up"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(database = "down"))
        }
    }
}
