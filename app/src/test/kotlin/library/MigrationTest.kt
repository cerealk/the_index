package library

import io.kotest.matchers.collections.shouldContainAll
import library.persistence.dataSource
import library.persistence.migrate
import org.junit.jupiter.api.Test

class MigrationTest {
    @Test
    fun `migrations install the search extensions`() {
        dataSource(TestDatabase.config().database).use { source ->
            migrate(source)

            val extensions = source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT extname FROM pg_extension").use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
            }

            extensions shouldContainAll listOf("pg_trgm", "unaccent")
        }
    }
}
