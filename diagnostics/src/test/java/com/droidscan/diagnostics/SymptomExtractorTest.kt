package com.droidscan.diagnostics

import com.droidscan.shared.core.MemoryPressure
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.shared.core.NetworkConfigSnapshot
import com.droidscan.shared.core.RadioSnapshot
import com.droidscan.shared.core.SimSlot
import com.droidscan.shared.core.SimStatusSnapshot
import com.droidscan.shared.core.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymptomExtractorTest {

    private fun symptom(snapshot: MetricSnapshot, id: String) =
        SymptomExtractor.extract(snapshot).first { it.id == id }

    @Test
    fun `weak signal fires on low RSRP and is silent on strong`() {
        val weak = symptom(MetricSnapshot(radio = RadioSnapshot(rsrp = -115)), "weakSignal")
        assertTrue(weak.present)
        assertTrue(weak.detail!!.contains("-115"))

        val strong = symptom(MetricSnapshot(radio = RadioSnapshot(rsrp = -90)), "weakSignal")
        assertFalse(strong.present)
    }

    @Test
    fun `missing RSRP is not treated as weak`() {
        // null ≠ 0: absence of a reading must not fabricate a symptom.
        val absent = symptom(MetricSnapshot(radio = RadioSnapshot(rsrp = null)), "weakSignal")
        assertFalse(absent.present)
        assertNull(absent.detail)
    }

    @Test
    fun `registered with no active network flags noData`() {
        val snap = MetricSnapshot(
            radio = RadioSnapshot(dataNetworkType = "NR"),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = null)
        )
        assertTrue(symptom(snap, "noData").present)
    }

    @Test
    fun `registered mobile with failing probe flags noData`() {
        val snap = MetricSnapshot(
            radio = RadioSnapshot(dataNetworkType = "LTE"),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = "MOBILE", latencyMs = null)
        )
        assertTrue(symptom(snap, "noData").present)
    }

    @Test
    fun `working mobile data does not flag noData`() {
        val snap = MetricSnapshot(
            radio = RadioSnapshot(dataNetworkType = "LTE"),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = "MOBILE", latencyMs = 45)
        )
        assertFalse(symptom(snap, "noData").present)
    }

    @Test
    fun `provisioned SIM without a network flags noService`() {
        val snap = MetricSnapshot(
            radio = RadioSnapshot(dataNetworkType = null, networkPlmn = null),
            simStatus = SimStatusSnapshot(slots = listOf(SimSlot(slotIndex = 0, isProvisioned = true)))
        )
        assertTrue(symptom(snap, "noService").present)
    }

    @Test
    fun `noService stays silent when cell info is permission-denied`() {
        val snap = MetricSnapshot(
            radio = RadioSnapshot(dataNetworkType = null, networkPlmn = null, cellInfoDenied = true),
            simStatus = SimStatusSnapshot(slots = listOf(SimSlot(slotIndex = 0, isProvisioned = true)))
        )
        assertFalse(symptom(snap, "noService").present)
    }

    @Test
    fun `IMS not registered fires only on explicit false`() {
        val explicit = symptom(MetricSnapshot(radio = RadioSnapshot(imsRegistered = false)), "imsNotRegistered")
        assertTrue(explicit.present)

        // null = "we don't know", not "not registered".
        val unknown = symptom(MetricSnapshot(radio = RadioSnapshot(imsRegistered = null)), "imsNotRegistered")
        assertFalse(unknown.present)
    }

    @Test
    fun `roaming with data roaming off flags roamingIssue`() {
        val snap = MetricSnapshot(radio = RadioSnapshot(isRoaming = true, dataRoamingEnabled = false))
        assertTrue(symptom(snap, "roamingIssue").present)

        val ok = MetricSnapshot(radio = RadioSnapshot(isRoaming = true, dataRoamingEnabled = true))
        assertFalse(symptom(ok, "roamingIssue").present)
    }

    @Test
    fun `storage low fires below 20 percent free`() {
        val low = MetricSnapshot(storage = StorageSnapshot(internalUsedBytes = 90_000, internalFreeBytes = 10_000))
        assertTrue(symptom(low, "storageLow").present)

        val fine = MetricSnapshot(storage = StorageSnapshot(internalUsedBytes = 50_000, internalFreeBytes = 50_000))
        assertFalse(symptom(fine, "storageLow").present)
    }

    @Test
    fun `critical memory pressure fires`() {
        val snap = MetricSnapshot()
            .let { it.copy(processes = it.processes.copy(memoryPressure = MemoryPressure.CRITICAL)) }
        assertEquals(true, symptom(snap, "memoryPressure").present)
    }
}
