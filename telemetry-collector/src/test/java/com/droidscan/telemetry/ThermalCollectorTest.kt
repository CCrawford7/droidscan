package com.droidscan.telemetry

import android.os.PowerManager
import com.droidscan.shared.core.ThermalZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalCollectorTest {

    private val collector = ThermalCollector()

    @Test
    fun `power zones replace the standard skin zone when present`() {
        val standard = listOf(ThermalZone("skin", 45f))
        val power = listOf(ThermalZone("cpu-0", 52f), ThermalZone("bat", 40f))
        val s = collector.aggregate(standard, power)
        assertEquals(2, s.zones.size)
        assertEquals("cpu-0", s.peakZoneName)
        assertEquals(52f, s.maxTemperature!!, 0.001f)
        assertEquals(false, s.isThrottling)
    }

    @Test
    fun `empty power zones fall back to the standard skin zone`() {
        val standard = listOf(ThermalZone("skin", 45f))
        val s = collector.aggregate(standard, emptyList())
        assertEquals(1, s.zones.size)
        assertEquals("skin", s.peakZoneName)
        assertEquals(45f, s.maxTemperature!!, 0.001f)
    }

    @Test
    fun `throttling and peak derived from the power zones`() {
        val zones = listOf(
            ThermalZone("cpu-0", 51f, throttling = false),
            ThermalZone("gpu", 58f, throttling = true)
        )
        val s = collector.aggregate(emptyList(), zones)
        assertEquals(true, s.isThrottling)
        assertEquals("gpu", s.peakZoneName)
    }

    @Test
    fun `empty everything yields an empty snapshot`() {
        val s = collector.aggregate(emptyList(), emptyList())
        assertTrue(s.zones.isEmpty())
        assertNull(s.maxTemperature)
        assertNull(s.peakZoneName)
        assertEquals(false, s.isThrottling)
    }

    @Test
    fun `thermal status maps to estimated temperatures`() {
        assertEquals(30.0f, collector.mapThermalStatusToTemp(PowerManager.THERMAL_STATUS_NONE), 0.001f)
        assertEquals(38.0f, collector.mapThermalStatusToTemp(PowerManager.THERMAL_STATUS_LIGHT), 0.001f)
        assertEquals(45.0f, collector.mapThermalStatusToTemp(PowerManager.THERMAL_STATUS_MODERATE), 0.001f)
        assertEquals(55.0f, collector.mapThermalStatusToTemp(PowerManager.THERMAL_STATUS_SEVERE), 0.001f)
        assertEquals(65.0f, collector.mapThermalStatusToTemp(PowerManager.THERMAL_STATUS_CRITICAL), 0.001f)
        assertEquals(75.0f, collector.mapThermalStatusToTemp(PowerManager.THERMAL_STATUS_EMERGENCY), 0.001f)
    }
}
