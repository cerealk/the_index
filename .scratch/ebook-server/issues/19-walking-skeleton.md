# Walking skeleton: Ktor + Postgres in Docker Compose

Type: implementation
Status: claimed

## What to build

Stand up the empty server end to end so every later ticket has a place to land. Spec: [§2](../spec.md#2-runtime-and-stack).

- Turn the Gradle skeleton (`app`, `utils`, `buildSrc`, version catalog) into the server project: Ktor, Exposed, Flyway, Koin, kotlinx.serialization. Rename the placeholder `org.example` package.
- Flyway runs on startup. The first migration enables `pg_trgm` and `unaccent`.
- Configuration from the environment: database URL, storage, inbox and cache paths ([§13](../spec.md#13-configuration)).
- `GET /api/v1/health` returns 200 once the database is reachable.
- Docker image for the app, plus `compose.yaml` with `app` and `postgres` and volumes for storage, inbox and Postgres data.
- Test setup: JUnit 5 + Kotest assertions, Ktor `testApplication`, Testcontainers Postgres as a reused singleton with per-test cleanup.

## Acceptance criteria

- [ ] `docker compose up` starts both services, and `GET /api/v1/health` answers 200 from the host.
- [ ] One integration test boots the app against Testcontainers Postgres, and Flyway migrations apply.
- [ ] `./gradlew test` passes.

## Comments

- 2026-10-12 (agent): Implemented: version catalog, `app` server (`library` package: `AppConfig`, `Module.kt` with Koin, Hikari + Flyway + Exposed, `GET /api/v1/health`), migration `V1__search_extensions.sql`, `Dockerfile`, `compose.yaml`, README. `AppConfigTest` passes (3/3). **Not verified:** no Docker engine was running on the dev machine, so `MigrationTest`, `HealthTest` and `docker compose up` failed with "Could not find a valid Docker environment". Rerun `./gradlew test` and `docker compose up --build` with Docker running before closing. The Gradle sample `utils` module is still in the build: deleting it was not permitted to the agent, so remove `utils/` and `include(":utils")` by hand (and the `COPY utils utils` line in the `Dockerfile`).
