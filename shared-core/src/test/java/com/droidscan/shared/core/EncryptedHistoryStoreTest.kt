package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class EncryptedHistoryStoreTest {

    /** Deterministic reversible transform — stands in for the Keystore AES-GCM. */
    private class FakeEncryptor : LineEncryptor {
        override fun encrypt(line: String): String =
            Base64.getEncoder().encodeToString(line.toByteArray(Charsets.UTF_8))

        override fun decrypt(line: String): String =
            String(Base64.getDecoder().decode(line), Charsets.UTF_8)
    }

    private fun tempFile(): File =
        Files.createTempFile("droidscan-enc-test", ".jsonl").toFile().also { it.delete() }

    @Test
    fun `encrypted store round-trips snapshots`() {
        val store = MetricHistoryStore(tempFile(), maxEntries = 10, encryptor = FakeEncryptor())
        store.append(sampleSnapshot(ts = 1000))
        store.append(sampleSnapshot(ts = 2000))
        val recent = store.recent(10)
        assertEquals(2, recent.size)
        assertEquals(listOf(1000L, 2000L), recent.map { it.timestampMs })
    }

    @Test
    fun `encrypted store writes ciphertext not json`() {
        val file = tempFile()
        MetricHistoryStore(file, maxEntries = 10, encryptor = FakeEncryptor())
            .append(sampleSnapshot(ts = 7))
        val raw = file.readText()
        assertTrue("stored line must not be plaintext JSON", !raw.startsWith("{"))
        assertNotEquals("stored line must not equal the JSON", sampleSnapshot(ts = 7).toString(), raw.trim())
    }

    @Test
    fun `isEncrypted reflects store mode`() {
        val plain = MetricHistoryStore(tempFile(), maxEntries = 10)
        plain.append(sampleSnapshot(ts = 1))
        assertFalse(plain.isEncrypted())

        val enc = MetricHistoryStore(tempFile(), maxEntries = 10, encryptor = FakeEncryptor())
        enc.append(sampleSnapshot(ts = 1))
        assertTrue(enc.isEncrypted())
    }

    @Test
    fun `tampered line is skipped not surfaced`() {
        val file = tempFile()
        val store = MetricHistoryStore(file, maxEntries = 10, encryptor = FakeEncryptor())
        store.append(sampleSnapshot(ts = 1))
        store.append(sampleSnapshot(ts = 2))
        // Flip a byte in the first stored line -> Base64 garbage/tamper.
        val lines = file.readLines().toMutableList()
        lines[0] = "!!!tampered!!!"
        file.writeText(lines.joinToString("\n") + "\n")
        val recent = store.recent(10)
        assertEquals("tampered line must be dropped", 1, recent.size)
        assertEquals(2L, recent[0].timestampMs)
    }

    @Test
    fun `plaintext history migrates to encrypted in place`() {
        val file = tempFile()
        // Simulate a legacy pre-slice-4 store.
        val plain = MetricHistoryStore(file, maxEntries = 10)
        plain.append(sampleSnapshot(ts = 100))
        plain.append(sampleSnapshot(ts = 200))
        assertTrue(file.readText().startsWith("{"))

        // Reopen with an encryptor -> auto-migration.
        val migrated = MetricHistoryStore(file, maxEntries = 10, encryptor = FakeEncryptor())
        val recent = migrated.recent(10)
        assertEquals("legacy entries survive migration", 2, recent.size)
        assertEquals(listOf(100L, 200L), recent.map { it.timestampMs })
        assertTrue("file is now encrypted", !file.readText().startsWith("{"))
        assertTrue(migrated.isEncrypted())
    }

    @Test
    fun `already-encrypted file is not double-migrated`() {
        val file = tempFile()
        MetricHistoryStore(file, maxEntries = 10, encryptor = FakeEncryptor())
            .append(sampleSnapshot(ts = 500))
        // Reopening with the same encryptor must not corrupt.
        val reopened = MetricHistoryStore(file, maxEntries = 10, encryptor = FakeEncryptor())
        assertEquals(1, reopened.size())
        assertEquals(500L, reopened.recent(1).first().timestampMs)
    }

    private fun sampleSnapshot(ts: Long): MetricSnapshot =
        MetricSnapshot(radio = RadioSnapshot(rsrp = -95, timestampMs = ts), timestampMs = ts)
}
