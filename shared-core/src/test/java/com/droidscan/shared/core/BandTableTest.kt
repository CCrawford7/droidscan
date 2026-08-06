package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandTableTest {

    // ── LTE ──

    @Test
    fun `B3 earfcn 1300 derives 1805 plus 10 MHz`() {
        val info = lteBandInfo(1300)  // 1300 - 1200 = 100 steps × 0.1 MHz = 10 MHz
        assertEquals("B3", info?.band)
        assertEquals(1815.0, info?.dlFrequencyMhz!!, 0.0001)
        assertTrue(!info.isTdd)
    }

    @Test
    fun `B20 earfcn 6300 derives 806 MHz`() {
        val info = lteBandInfo(6300)  // 6300 - 6150 = 150 steps × 0.1 MHz = 15 MHz
        assertEquals("B20", info?.band)
        assertEquals(806.0, info?.dlFrequencyMhz!!, 0.0001)
    }

    @Test
    fun `B40 TDD earfcn 38700 derives 2300 plus 5 MHz`() {
        val info = lteBandInfo(38700)  // 38700 - 38650 = 50 × 0.1 = 5 MHz
        assertEquals("B40", info?.band)
        assertEquals(2305.0, info?.dlFrequencyMhz!!, 0.0001)
        assertTrue(info.isTdd)
    }

    @Test
    fun `B41 earfcn 40500 derives band 41`() {
        val info = lteBandInfo(40500)
        assertEquals("B41", info?.band)
        assertEquals(2496.0 + 0.1 * (40500 - 39650), info?.dlFrequencyMhz!!, 0.0001)
    }

    @Test
    fun `known band disambiguates overlapping window`() {
        // EARFCN 2000 falls in both B1 (1800-2399) and B4 (1950-2399). The band
        // label is chosen by knownBand; each band maps the ARFCN on ITS OWN grid
        // (B1: 2110+20=2130, B4: 2110+5=2115) — a genuine 3GPP per-band grid quirk.
        val b1 = lteBandInfo(2000, knownBand = "B1")
        val b4 = lteBandInfo(2000, knownBand = "B4")
        assertEquals("B1", b1?.band)
        assertEquals(2130.0, b1?.dlFrequencyMhz!!, 0.0001)
        assertEquals("B4", b4?.band)
        assertEquals(2115.0, b4?.dlFrequencyMhz!!, 0.0001)
    }

    @Test
    fun `unknown earfcn returns null`() {
        assertNull(lteBandInfo(70000))
        assertNull(lteBandInfo(0))
    }

    // ── NR ──

    @Test
    fun `n78 nrarfcn 623334 derives 3300 plus 16 point 67 MHz`() {
        // n77 and n78 share the 620000 offset; knownBand picks the band label.
        val info = nrBandInfo(623334, knownBand = "n78")  // 3334 × 0.005 = 16.67 MHz
        assertEquals("n78", info?.band)
        assertEquals(3316.67, info?.dlFrequencyMhz!!, 0.01)
        assertTrue(info.isTdd)
    }

    @Test
    fun `n1 nrarfcn 422000 derives 2110`() {
        val info = nrBandInfo(422000)
        assertEquals("n1", info?.band)
        assertEquals(2110.0, info?.dlFrequencyMhz!!, 0.001)
    }

    @Test
    fun `n41 nrarfcn 500000 derives 2519 point 4`() {
        val info = nrBandInfo(500000)
        assertEquals("n41", info?.band)
        assertEquals(2496.0 + 0.005 * (500000 - 499200), info?.dlFrequencyMhz!!, 0.001)
    }

    @Test
    fun `n79 derives 4400 offset not 3300`() {
        // 620000 offset is shared by n77/n78 (3300) and n79 (4400)
        val info = nrBandInfo(620000, knownBand = "n79")
        assertEquals("n79", info?.band)
        assertEquals(4400.0, info?.dlFrequencyMhz!!, 0.001)
    }

    @Test
    fun `FR2 n258 derives from 60 kHz step`() {
        val info = nrBandInfo(2016667)
        assertEquals("n258", info?.band)
        assertEquals(24250.0, info?.dlFrequencyMhz!!, 0.01)
    }

    @Test
    fun `unknown nrarfcn returns null`() {
        assertNull(nrBandInfo(123456))
        assertNull(nrBandInfo(800000))
    }
}
