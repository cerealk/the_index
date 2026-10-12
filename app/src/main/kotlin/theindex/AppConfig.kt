package theindex

import java.nio.file.Path

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
)

data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val storageDir: Path,
    val inboxDir: Path,
    val cacheDir: Path,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            fun required(name: String) = env[name] ?: error("Missing environment variable $name")

            return AppConfig(
                port = env["THE_INDEX_PORT"]?.toInt() ?: 8080,
                database = DatabaseConfig(
                    url = required("THE_INDEX_DB_URL"),
                    user = required("THE_INDEX_DB_USER"),
                    password = required("THE_INDEX_DB_PASSWORD"),
                ),
                storageDir = Path.of(env["THE_INDEX_STORAGE_DIR"] ?: "/data/storage"),
                inboxDir = Path.of(env["THE_INDEX_INBOX_DIR"] ?: "/data/inbox"),
                cacheDir = Path.of(env["THE_INDEX_CACHE_DIR"] ?: "/data/cache"),
            )
        }
    }
}
