package theindex.imports

import org.jetbrains.exposed.v1.jdbc.Database
import theindex.TestDatabase
import theindex.files.Storage
import theindex.persistence.dataSource
import theindex.persistence.migrate
import theindex.publications.Library
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class MutableClock(private var now: Instant = Instant.parse("2026-10-12T10:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    fun advance(duration: Duration) {
        now += duration
    }
}

/** The import pipeline wired against the test database and fresh temp directories. */
class ImportHarness {
    val config = TestDatabase.config()
    val clock = MutableClock()
    private val dataSource = dataSource(config.database).also { migrate(it) }
    val db = Database.connect(dataSource)
    val storage = Storage(config.storageDir).also { it.createDirectories() }
    val jobs = ImportJobs(db, clock)
    val library = Library(db, clock)
    val worker = ImportWorker(db, jobs, library, storage)
    val inbox = Inbox(config.inboxDir, jobs, storage, clock, STABILITY)

    init {
        Files.createDirectories(config.inboxDir)
    }

    /** Two scans across the stability window: the first sees the files, the second enqueues them. */
    fun scanUntilStable(): List<Long> {
        inbox.scan()
        clock.advance(STABILITY)
        return inbox.scan()
    }

    fun drain() {
        while (worker.processNext()) Unit
    }

    fun close() = dataSource.close()

    companion object {
        val STABILITY: Duration = Duration.ofSeconds(3)
    }
}
