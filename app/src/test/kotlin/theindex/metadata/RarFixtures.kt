package theindex.metadata

/** Real RAR archives from junrar's test suite; see `src/test/resources/rar/README.md`. */
object RarFixtures {
    fun bytes(name: String): ByteArray =
        RarFixtures::class.java.getResourceAsStream("/rar/$name")?.use { it.readAllBytes() }
            ?: error("no RAR fixture $name")
}
