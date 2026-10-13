package theindex.files

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.security.MessageDigest

/** A File's content identity: SHA-256 for the library, KOReader partial MD5 for kosync (spec §3). */
data class Hashes(val sha256: String, val partialMd5: String, val size: Long)

object Hasher {
    private const val SAMPLE_SIZE = 1024

    /**
     * KOReader's sample offsets are `1024 << 2i` for i = -1..10, computed with 32-bit shifts:
     * `1024 << -2` wraps to a shift of 30 and overflows to 0, so the first sample is at offset 0.
     */
    private val PARTIAL_MD5_OFFSETS = listOf(0L) + (0..10).map { 1024L shl (2 * it) }

    fun hash(path: Path): Hashes = FileChannel.open(path, READ).use { channel ->
        Hashes(sha256 = sha256(channel), partialMd5 = partialMd5(channel), size = channel.size())
    }

    private fun sha256(channel: FileChannel): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(1 shl 16)
        channel.position(0)
        while (channel.read(buffer) >= 0) {
            buffer.flip()
            digest.update(buffer)
            buffer.clear()
        }
        return digest.digest().toHex()
    }

    private fun partialMd5(channel: FileChannel): String {
        val digest = MessageDigest.getInstance("MD5")
        val size = channel.size()
        for (offset in PARTIAL_MD5_OFFSETS) {
            if (offset >= size) break
            val buffer = ByteBuffer.allocate(minOf(SAMPLE_SIZE.toLong(), size - offset).toInt())
            while (buffer.hasRemaining() && channel.read(buffer, offset + buffer.position()) >= 0) Unit
            digest.update(buffer.flip())
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
