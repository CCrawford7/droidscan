package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MetricHistoryStoreTest {

    private fun tempFile(): File =
        Files.createTempFile("droidscan-history-test", ".jsonl").toFile().also { it.delete() }

    @Test
    fun `append then recent returns snapshots oldest-first`() {
        val store = MetricHistoryStore(tempFile(), maxEntries = 10)
        store.append(sampleSnapshot(ts = 1000))
        store.append(sampleSnapshot(ts = 2000))
        val recent = store.recent(10)
        assertEquals(2, recent.size)
        assertEquals(1000, recent[0].timestampMs)
        assertEquals(2000, recent[1].timestampMs)
    }

    @Test
    fun `recent limits to n most recent`() {
        val store = MetricHistoryStore(tempFile(), maxEntries = 10)
        repeat(5) { i -> store.append(sampleSnapshot(ts = i.toLong())) }
        val recent = store.recent(2)
        assertEquals(2, recent.size)
        assertEquals(3, recent[0].timestampMs)
        assertEquals(4, recent[1].timestampMs)
    }

    @Test
    fun `prunes to maxEntries`() {
        val store = MetricHistoryStore(tempFile(), maxEntries = 3)
        repeat(10) { i -> store.append(sampleSnapshot(ts = i.toLong())) }
        assertEquals(3, store.size())
        assertEquals(listOf(7L, 8L, 9L), store.recent(10).map { it.timestampMs })
    }

    @Test
    fun `clear removes all entries`() {
        val store = MetricHistoryStore(tempFile(), maxEntries = 10)
        store.append(sampleSnapshot(ts = 1))
        store.clear()
        assertEquals(0, store.size())
        assertTrue(store.recent(10).isEmpty())
    }

    @Test
    fun `survives close and reopen`() {
        val file = tempFile()
        MetricHistoryStore(file, maxEntries = 10).append(sampleSnapshot(ts = 42))
        val reopened = MetricHistoryStore(file, maxEntries = 10)
        assertEquals(1, reopened.size())
        assertEquals(42, reopened.recent(1).first().timestampMs)
    }

    private fun sampleSnapshot(ts: Long): MetricSnapshot =
        MetricSnapshot(
            radio = RadioSnapshot(rsrp = -95, timestampMs = ts),
            timestampMs = ts
        )
}
