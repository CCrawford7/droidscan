package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalPresentationTest {

    // ── ringProgress ─────────────────────────────────────────────

    @Test
    fun `ringProgress maps min to 0 and max to 1`() {
        assertEquals(0f, ringProgress(-140f, -140f, -44f))
        assertEquals(1f, ringProgress(-44f, -140f, -44f))
    }

    @Test
    fun `ringProgress maps midpoint`() {
        // -92 is halfway between -140 and -44
        assertEquals(0.5f, ringProgress(-92f, -140f, -44f), 0.001f)
    }

    @Test
    fun `ringProgress clamps out of range`() {
        assertEquals(0f, ringProgress(-200f, -140f, -44f))
        assertEquals(1f, ringProgress(-20f, -140f, -44f))
    }

    @Test
    fun `ringProgress null is zero`() {
        assertEquals(0f, ringProgress(null, -140f, -44f))
    }

    @Test
    fun `ringProgress zero range is zero`() {
        assertEquals(0f, ringProgress(5f, 5f, 5f))
    }

    @Test
    fun `ringProgress handles ascending and descending ranges`() {
        // battery level 0..100
        assertEquals(0.6f, ringProgress(60f, 0f, 100f), 0.001f)
        // RSRQ -20..-3
        assertEquals(0.5f, ringProgress(-11.5f, -20f, -3f), 0.001f)
    }

    // ── signalWord ───────────────────────────────────────────────

    @Test
    fun `signalWord maps tiers`() {
        assertEquals("good", StatusTier.GREEN.signalWord())
        assertEquals("fair", StatusTier.YELLOW.signalWord())
        assertEquals("critical", StatusTier.RED.signalWord())
    }

    // ── signalAccessibilityLabel ─────────────────────────────────

    @Test
    fun `signalAccessibilityLabel builds full sentence`() {
        val s = signalAccessibilityLabel("SIM 1", -85, -11, 18)
        assertTrue(s.startsWith("SIM 1 network health."))
        assertTrue(s.contains("Signal strength good, -85 dBm."))
        assertTrue(s.contains("Interference fair, -11 dB."))
        assertTrue(s.contains("Signal quality good, 18 dB."))
    }

    @Test
    fun `signalAccessibilityLabel critical tier words`() {
        val s = signalAccessibilityLabel("SIM 2", -130, -19, -8)
        assertTrue(s.contains("Signal strength critical, -130 dBm."))
        assertTrue(s.contains("Interference critical, -19 dB."))
        assertTrue(s.contains("Signal quality critical, -8 dB."))
    }

    @Test
    fun `signalAccessibilityLabel omits missing metrics`() {
        val s = signalAccessibilityLabel("SIM 1", -85, null, null)
        assertTrue(s.contains("Signal strength good, -85 dBm."))
        assertTrue(!s.contains("Interference"))
        assertTrue(!s.contains("Signal quality"))
    }

    @Test
    fun `signalAccessibilityLabel no data fallback`() {
        assertEquals(
            "SIM 1 network health. No signal data.",
            signalAccessibilityLabel("SIM 1", null, null, null)
        )
    }
}
