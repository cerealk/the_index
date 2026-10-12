# library

A personal, LAN-only server that stores and organizes ebooks and comics, serves them over OPDS 1.2 and syncs Reading Progress over kosync. The iteration-1 spec is in [`.scratch/ebook-server/spec.md`](.scratch/ebook-server/spec.md) and the vocabulary in [`CONTEXT.md`](CONTEXT.md).

The server lives in the `app` subproject (Ktor, PostgreSQL, Exposed, Flyway, Koin). Shared build logic is in the `buildSrc` convention plugin and versions in `gradle/libs.versions.toml`.

## Run it

```sh
docker compose up --build
curl http://localhost:8080/api/v1/health   # {"database":"up"}
```

Compose starts the app and PostgreSQL. Host directories for the app default to `./data/{storage,inbox,cache}` and can be changed with `LIBRARY_STORAGE`, `LIBRARY_INBOX` and `LIBRARY_CACHE`. `LIBRARY_PORT` changes the published port and `LIBRARY_DB_PASSWORD` the database password.

The app reads its configuration from the environment:

| Variable | Default |
|---|---|
| `LIBRARY_DB_URL`, `LIBRARY_DB_USER`, `LIBRARY_DB_PASSWORD` | required |
| `LIBRARY_PORT` | `8080` |
| `LIBRARY_STORAGE_DIR` / `LIBRARY_INBOX_DIR` / `LIBRARY_CACHE_DIR` | `/data/storage` / `/data/inbox` / `/data/cache` |

## Test it

`./gradlew test` needs a running Docker engine: database tests use one Testcontainers PostgreSQL shared by the whole run. To keep that container between runs too, enable reuse once:

```sh
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```
