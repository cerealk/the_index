package library

import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import library.health.DataSourceHealthCheck
import library.health.HealthCheck
import library.health.healthRoutes
import library.persistence.dataSource
import library.persistence.migrate
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import javax.sql.DataSource

fun appModule(config: AppConfig) = module {
    single { config }
    single { dataSource(config.database) }
    single<DataSource> { get<HikariDataSource>() }
    single { Database.connect(get<DataSource>()) }
    single<HealthCheck> { DataSourceHealthCheck(get()) }
}

fun Application.module(config: AppConfig) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(config))
    }

    val dataSource = get<HikariDataSource>()
    migrate(dataSource)
    get<Database>()
    monitor.subscribe(ApplicationStopped) { dataSource.close() }

    configureSerialization()
    routing {
        healthRoutes(get())
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) { json() }
}
