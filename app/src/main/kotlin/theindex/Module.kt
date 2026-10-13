package theindex

import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import theindex.files.Storage
import theindex.health.DataSourceHealthCheck
import theindex.health.HealthCheck
import theindex.health.healthRoutes
import theindex.imports.ImportJobs
import theindex.imports.ImportWorker
import theindex.imports.Inbox
import theindex.imports.importRoutes
import theindex.opds.OpdsCatalog
import theindex.opds.opdsRoutes
import theindex.persistence.dataSource
import theindex.persistence.migrate
import theindex.publications.Library
import java.nio.file.Files
import java.time.Clock
import javax.sql.DataSource

fun appModule(config: AppConfig, clock: Clock = Clock.systemUTC()) = module {
    single { config }
    single { clock }
    single { dataSource(config.database) }
    single<DataSource> { get<HikariDataSource>() }
    single { Database.connect(get<DataSource>()) }
    single<HealthCheck> { DataSourceHealthCheck(get()) }
    single { Storage(config.storageDir) }
    single { ImportJobs(get(), get()) }
    single { Library(get(), get()) }
    single { ImportWorker(get(), get(), get(), get()) }
    single { Inbox(config.inboxDir, get(), get(), get(), config.inboxStabilityWindow) }
    single { OpdsCatalog(get(), get(), get()) }
}

fun Application.module(config: AppConfig, clock: Clock = Clock.systemUTC()) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(config, clock))
    }

    val dataSource = get<HikariDataSource>()
    migrate(dataSource)
    get<Database>()
    monitor.subscribe(ApplicationStopped) { dataSource.close() }

    get<Storage>().createDirectories()
    Files.createDirectories(config.inboxDir)
    Files.createDirectories(config.cacheDir)
    get<ImportWorker>().recover()
    if (config.runBackgroundJobs) startImporting(config)

    configureSerialization()
    routing {
        healthRoutes(get())
        importRoutes(get())
        opdsRoutes(get())
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) { json() }
}

/** The inbox poller and the single import worker (spec §5). */
private fun Application.startImporting(config: AppConfig) {
    val log = LoggerFactory.getLogger("theindex.imports")
    val inbox = get<Inbox>()
    val worker = get<ImportWorker>()

    launch(Dispatchers.IO) {
        while (isActive) {
            runCatching { inbox.scan() }.onFailure { log.error("Inbox scan failed", it) }
            delay(config.inboxPollInterval.toMillis())
        }
    }
    launch(Dispatchers.IO) {
        while (isActive) {
            val worked = runCatching { worker.processNext() }
                .onFailure { log.error("Import worker failed", it) }
                .getOrDefault(false)
            if (!worked) delay(WORKER_IDLE_DELAY_MILLIS)
        }
    }
}

private const val WORKER_IDLE_DELAY_MILLIS = 1_000L
