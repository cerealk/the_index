# the_index

A personal, LAN-only server that stores and organizes ebooks and comics, serves them over OPDS 1.2 and syncs Reading Progress over kosync. The iteration-1 spec is in [`.scratch/ebook-server/spec.md`](.scratch/ebook-server/spec.md) and the vocabulary in [`CONTEXT.md`](CONTEXT.md).

The server lives in the `app` subproject, package `theindex` (Ktor, PostgreSQL, Exposed, Flyway, Koin). Shared build logic is in the `buildSrc` convention plugin and versions in `gradle/libs.versions.toml`.

## Run it

```sh
docker compose up --build
curl http://localhost:8080/api/v1/health   # {"database":"up"}
cp book.epub data/inbox/                    # imported within one poll
curl http://localhost:8080/api/v1/imports
```

Compose starts the app and PostgreSQL. Host directories for the app default to `./data/{storage,inbox,cache}` and can be changed with `THE_INDEX_STORAGE`, `THE_INDEX_INBOX` and `THE_INDEX_CACHE`. `THE_INDEX_PORT` changes the published port and `THE_INDEX_DB_PASSWORD` the database password.

The app reads its configuration from the environment:

| Variable | Default |
|---|---|
| `THE_INDEX_DB_URL`, `THE_INDEX_DB_USER`, `THE_INDEX_DB_PASSWORD` | required |
| `THE_INDEX_PORT` | `8080` |
| `THE_INDEX_STORAGE_DIR` / `THE_INDEX_INBOX_DIR` / `THE_INDEX_CACHE_DIR` | `/data/storage` / `/data/inbox` / `/data/cache` |
| `THE_INDEX_INBOX_POLL_SECONDS` | `10` |
| `THE_INDEX_INBOX_STABILITY_SECONDS` (how long a file must stay unchanged before it's imported) | `3` |

## Test it

`./gradlew test` needs a running Docker engine: database tests use one Testcontainers PostgreSQL shared by the whole run. To keep that container between runs too, enable reuse once:

```sh
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```
