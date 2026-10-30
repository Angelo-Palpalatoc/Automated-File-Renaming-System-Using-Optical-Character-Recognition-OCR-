package com.local.secrename

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CopyIntegrityTest {
    @Test fun knownSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", CopyIntegrity.sha256(ByteArrayInputStream("abc".toByteArray())))
    }
    @Test fun verifiedCopyPreservesEveryByte() {
        val original = ByteArray(150000) { (it % 251).toByte() }
        val digest = CopyIntegrity.sha256(original.inputStream())
        val output = ByteArrayOutputStream()
        CopyIntegrity.copyChecked(original.inputStream(), output, digest)
        assertArrayEquals(original, output.toByteArray())
    }
    @Test(expected = IllegalStateException::class) fun rejectsChangedSource() {
        val pinned = CopyIntegrity.sha256("original".byteInputStream())
        CopyIntegrity.copyChecked("changed".byteInputStream(), ByteArrayOutputStream(), pinned)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedFile() {
        CopyIntegrity.sha256(ByteArray(11).inputStream(), limit = 10)
    }
    @Test fun failedBoundedCopyDoesNotWriteBeyondLimit() {
        val output = ByteArrayOutputStream()
        try { CopyIntegrity.copyChecked(ByteArray(11).inputStream(), output, "unused", limit = 10); fail("Expected size rejection") }
        catch (_: IllegalArgumentException) { assertEquals(0, output.size()) }
    }
}
