package theindex.files

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import theindex.Fixtures
import java.nio.file.Path

class HasherTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `partial MD5 matches KOReader's algorithm on a reference file`() {
        // Expected value computed independently (Python, with JavaScript's 32-bit shift semantics
        // as used by Readest and LuaJIT): samples at offsets 0, 1 KiB, 4 KiB, 16 KiB, 64 KiB, 256 KiB.
        val bytes = ByteArray(300_000) { ((it * 31 + 7) % 251).toByte() }

        val hashes = Hasher.hash(Fixtures.write(dir.resolve("reference.bin"), bytes))

        hashes.partialMd5 shouldBe "3d87fe2dd28ddc7895ae5e290bfa539e"
        hashes.size shouldBe 300_000
    }

    @Test
    fun `a file smaller than one sample hashes like plain MD5 and SHA-256`() {
        val hashes = Hasher.hash(Fixtures.write(dir.resolve("hello.txt"), "hello".toByteArray()))

        hashes.partialMd5 shouldBe "5d41402abc4b2a76b9719d911017c592"
        hashes.sha256 shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }
}
