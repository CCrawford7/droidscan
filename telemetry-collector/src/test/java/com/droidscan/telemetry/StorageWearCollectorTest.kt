package com.droidscan.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageWearCollectorTest {

    private val collector = StorageWearCollector()

    /** Real CPH2709 output (trimmed) — the fixture exercises every field. */
    private val realDump = """
        Latency: 2ms [512B Data Write]
        Recent Disk Write Speed (kB/s) = 18932
        Data-Free: 145762144K / 480365628K total = 30% free
        Cache-Free: 145762144K / 480365628K total = 30% free
        System-Free: 0K / 758676K total = 0% free
        Metadata-Free: 27536K / 63488K total = 43% free
        File-based Encryption: true
        App Size: 147581463552
        App Data Size: 116787297388
        App Cache Size: 33206512640
        Photos Size: 7083921408
        Videos Size: 23771009024
        Audio Size: 1453932544
        Downloads Size: 0
        System Size: 512000000000
        Other Size: 71785512960
        Package Names: ["it.devit.android", "com.oplus.securitykeyboard"]
    """.trimIndent()

    @Test
    fun `parse extracts all storage wear fields from real diskstats dump`() {
        val w = collector.parse(realDump)
        assertTrue(w.powerMode)
        assertEquals(2L, w.writeLatencyMs)
        assertEquals(18932, w.recentWriteKbps)
        assertEquals(30, w.dataFreePercent)
        assertEquals(true, w.fileBasedEncryption)
        assertEquals(147581463552L, w.appSizeBytes)
        assertEquals(116787297388L, w.appDataSizeBytes)
        assertEquals(33206512640L, w.appCacheSizeBytes)
        assertEquals(7083921408L, w.photosSizeBytes)
        assertEquals(23771009024L, w.videosSizeBytes)
        assertEquals(1453932544L, w.audioSizeBytes)
    }

    @Test
    fun `parse handles missing sections without crashing`() {
        // parse() is only ever reached when shell access exists, so powerMode
        // stays true even for an empty dump.
        val w = collector.parse("")
        assertTrue(w.powerMode)
        assertEquals(null, w.writeLatencyMs)
        assertEquals(null, w.dataFreePercent)
        assertEquals(null, w.fileBasedEncryption)
    }

    @Test
    fun `parse is tolerant of FBE being off`() {
        val dump = realDump.replace("File-based Encryption: true", "File-based Encryption: false")
        assertEquals(false, collector.parse(dump).fileBasedEncryption)
    }

    @Test
    fun `parse ignores non-matching size lines`() {
        val dump = "Downloads Size: 0\nSystem Size: 512000000000\nOther Size: 71785512960"
        val w = collector.parse(dump)
        assertEquals(null, w.appSizeBytes)
        assertEquals(null, w.photosSizeBytes)
    }
}
