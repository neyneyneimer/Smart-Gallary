package ai.sakana.aura.gallery.data.deduplication

import java.io.InputStream
import java.security.MessageDigest

/** Streams bytes through SHA-256 using a 64KB buffer; never loads a whole file into memory. */
object Sha256Hasher {
    const val BUFFER_SIZE_BYTES: Int = 64 * 1024

    /** Returns the lowercase hex SHA-256 of every byte in [input]. Does not close [input]. */
    fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
