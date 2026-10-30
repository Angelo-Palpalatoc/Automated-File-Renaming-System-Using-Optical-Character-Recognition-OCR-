package com.local.secrename

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Byte-based integrity checks, independent of Android providers and usable in JVM tests. */
internal object CopyIntegrity {
    const val MAX_BYTES = 200L * 1024 * 1024
    fun sha256(input: InputStream, limit: Long = MAX_BYTES): String = transfer(input, null, limit)

    fun copyChecked(input: InputStream, output: OutputStream, expected: String, limit: Long = MAX_BYTES) {
        val actual = transfer(input, output, limit)
        check(actual == expected) { "Source changed after analysis; incomplete output must not be used. Analyze again." }
    }

    private fun transfer(input: InputStream, output: OutputStream?, limit: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            size += count
            require(size <= limit) { "Document exceeds the size limit ($limit bytes)." }
            md.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
