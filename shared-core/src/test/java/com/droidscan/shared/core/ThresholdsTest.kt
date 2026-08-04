package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdsTest {

    // ── Default: LOWER is better ─────────────────────────────────────

    @Test
    fun `lower is better - green yellow red boundaries`() {
        val range = ThresholdRange(greenUpper = 38.0, yellowUpper = 45.0)
        assertEquals(StatusTier.GREEN, evaluateStatus(30.0, range))
        assertEquals(StatusTier.GREEN, evaluateStatus(38.0, range))
        assertEquals(StatusTier.YELLOW, evaluateStatus(40.0, range))
        assertEquals(StatusTier.YELLOW, evaluateStatus(45.0, range))
        assertEquals(StatusTier.RED, evaluateStatus(46.0, range))
    }

    @Test
    fun `memory pressure is lower-is-better`() {
        val range = MemoryThresholds.pressure
        assertEquals(StatusTier.GREEN, evaluateStatus(0.3, range))
        assertEquals(StatusTier.YELLOW, evaluateStatus(0.6, range))
        assertEquals(StatusTier.RED, evaluateStatus(0.9, range))
    }

    @Test
    fun `battery temperature is lower-is-better`() {
        val range = BatteryThresholds.temperatureCelsius
        assertEquals(StatusTier.GREEN, evaluateStatus(35.0, range))
        assertEquals(StatusTier.YELLOW, evaluateStatus(40.0, range))
        assertEquals(StatusTier.RED, evaluateStatus(50.0, range))
    }

    // ── Inverted: HIGHER is better ───────────────────────────────────

    @Test
    fun `rsrp higher is better - strong signal is green not red`() {
        // Regression: -90 dBm is a GOOD signal and must be GREEN.
        val range = RadioThresholds.rsrp
        assertEquals(StatusTier.GREEN, evaluateStatus(-90.0, range, inverted = true))
        assertEquals(StatusTier.GREEN, evaluateStatus(-100.0, range, inverted = true))
        assertEquals(StatusTier.YELLOW, evaluateStatus(-110.0, range, inverted = true))
        assertEquals(StatusTier.RED, evaluateStatus(-125.0, range, inverted = true))
    }

    @Test
    fun `rsrq and sinr higher is better`() {
        assertEquals(StatusTier.GREEN, evaluateStatus(-8.0, RadioThresholds.rsrq, inverted = true))
        assertEquals(StatusTier.RED, evaluateStatus(-18.0, RadioThresholds.rsrq, inverted = true))
        assertEquals(StatusTier.GREEN, evaluateStatus(20.0, RadioThresholds.sinr, inverted = true))
        assertEquals(StatusTier.RED, evaluateStatus(2.0, RadioThresholds.sinr, inverted = true))
    }

    @Test
    fun `battery level higher is better - full battery is green`() {
        // Regression: 80% battery must be GREEN, not RED.
        val range = BatteryThresholds.levelPercent
        assertEquals(StatusTier.GREEN, evaluateStatus(80.0, range, inverted = true))
        assertEquals(StatusTier.GREEN, evaluateStatus(20.0, range, inverted = true))
        assertEquals(StatusTier.YELLOW, evaluateStatus(10.0, range, inverted = true))
        assertEquals(StatusTier.RED, evaluateStatus(3.0, range, inverted = true))
    }

    @Test
    fun `evaluateStatusInverted is the higher-is-better alias`() {
        val range = RadioThresholds.rsrp
        assertEquals(evaluateStatus(-90.0, range, inverted = true), evaluateStatusInverted(-90.0, range))
        assertEquals(evaluateStatus(-125.0, range, inverted = true), evaluateStatusInverted(-125.0, range))
    }
}
