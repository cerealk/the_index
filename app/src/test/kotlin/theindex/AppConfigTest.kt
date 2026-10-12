package theindex

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AppConfigTest {
    private val database = mapOf(
        "THE_INDEX_DB_URL" to "jdbc:postgresql://db:5432/the_index",
        "THE_INDEX_DB_USER" to "the_index",
        "THE_INDEX_DB_PASSWORD" to "secret",
    )

    @Test
    fun `defaults the port and the data directories`() {
        val config = AppConfig.fromEnvironment(database)

        config.port shouldBe 8080
        config.database shouldBe DatabaseConfig("jdbc:postgresql://db:5432/the_index", "the_index", "secret")
        config.storageDir shouldBe Path.of("/data/storage")
        config.inboxDir shouldBe Path.of("/data/inbox")
        config.cacheDir shouldBe Path.of("/data/cache")
    }

    @Test
    fun `reads overrides from the environment`() {
        val config = AppConfig.fromEnvironment(
            database + mapOf("THE_INDEX_PORT" to "9000", "THE_INDEX_INBOX_DIR" to "/import")
        )

        config.port shouldBe 9000
        config.inboxDir shouldBe Path.of("/import")
    }

    @Test
    fun `fails fast when the database is not configured`() {
        val error = shouldThrow<IllegalStateException> { AppConfig.fromEnvironment(emptyMap()) }

        error.message shouldContain "THE_INDEX_DB_URL"
    }
}
