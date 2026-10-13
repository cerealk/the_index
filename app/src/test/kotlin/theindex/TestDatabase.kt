package theindex

import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files

/**
 * One Postgres container shared by every test in the run. With
 * `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`
 * it also survives between runs.
 */
object TestDatabase {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:18-alpine")
            .withReuse(true)
            .also { it.start() }
    }

    fun config(): AppConfig {
        val root = Files.createTempDirectory("the_index-test")
        return AppConfig(
            port = 0,
            database = DatabaseConfig(
                url = container.jdbcUrl,
                user = container.username,
                password = container.password,
            ),
            storageDir = root.resolve("storage"),
            inboxDir = root.resolve("inbox"),
            cacheDir = root.resolve("cache"),
            runBackgroundJobs = false,
        )
    }

    /** Empties every application table, so tests don't see each other's data. */
    fun truncateAll() {
        val db = config().database
        java.sql.DriverManager.getConnection(db.url, db.user, db.password).use { connection ->
            val tables = connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                    """.trimIndent()
                ).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
            if (tables.isNotEmpty()) {
                connection.createStatement().use {
                    it.execute("TRUNCATE ${tables.joinToString { name -> "\"$name\"" }} RESTART IDENTITY CASCADE")
                }
            }
        }
    }
}
