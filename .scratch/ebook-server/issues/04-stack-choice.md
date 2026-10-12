# Which server stack: framework and database?

Type: grilling
Status: resolved
Labels: wayfinder:grilling

## Question

Pick the HTTP framework (Ktor vs Spring Boot vs other) and the database (SQLite vs Postgres), plus the data-access approach (Exposed, jOOQ, JPA, SQLDelight…), for a single-user, LAN-only Kotlin learning project storing thousands of Publications.

## Answer

| Concern | Choice |
|---|---|
| HTTP framework | **Ktor** (explicit wiring suits the learning goal) |
| Database | **PostgreSQL** (chosen over SQLite) |
| Data access | **Exposed DSL**, dropping to raw SQL for Postgres-specific features (full-text search, JSONB) |
| Migrations | **Flyway** |
| DI | **Koin** |
| Serialization | **kotlinx.serialization** |
| Test framework | **JUnit 5** with **Kotest assertions only** (not the Kotest runner) |
| HTTP tests | Ktor `testApplication` |
| DB tests | **Testcontainers** PostgreSQL, one container shared by all tests in a run (singleton) and kept between runs (`.withReuse(true)` + `testcontainers.reuse.enable=true`). Tests clean up their own data (truncate or roll back per test). |

Logging and config-file format are left to the implementer.
