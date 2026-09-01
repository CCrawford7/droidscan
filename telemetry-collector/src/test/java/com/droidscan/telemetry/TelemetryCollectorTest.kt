package com.droidscan.telemetry

import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.shared.core.RadioSnapshot
import com.droidscan.shared.core.SimSlot
import com.droidscan.shared.core.SimStatusSnapshot
import com.droidscan.shared.core.ThermalSnapshot
import com.droidscan.shared.core.ThermalZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryCollectorTest {

    private val collector = TelemetryCollector()

    @Test
    fun `buildDebugDump contains every major section`() {
        val dump = collector.buildDebugDump(MetricSnapshot())

        for (section in listOf(
            "==== DROIDSCAN SNAPSHOT ====",
            "--- RADIO / SUBSCRIBER ---",
            "--- IMS (ImsMmTelManager) ---",
            "--- GNSS ---",
            "--- NETWORK ---",
            "--- SIM ---",
            "--- STORAGE ---",
            "--- STORAGE WEAR (Power Mode) ---",
            "--- THERMAL ---",
            "--- POWER ---",
            "--- CPU / MEMORY ---",
            "--- SYSTEM ---",
            "Shizuku:",
            "==== END SNAPSHOT ===="
        )) {
            assertTrue("missing section: $section", dump.contains(section))
        }
    }

    @Test
    fun `buildDebugDump renders radio and thermal values`() {
        val snapshot = MetricSnapshot(
            radio = RadioSnapshot(rsrp = -95, sinr = 19, operatorName = "TestNet"),
            thermal = ThermalSnapshot(
                zones = listOf(ThermalZone("cpu-0", 52.5f)),
                maxTemperature = 52.5f,
                peakZoneName = "cpu-0"
            )
        )
        val dump = collector.buildDebugDump(snapshot)
        assertTrue(dump.contains("RSRP: -95 dBm"))
        assertTrue(dump.contains("SINR: 19 dB"))
        assertTrue(dump.contains("Operator: TestNet"))
        assertTrue(dump.contains("cpu-0: 52.5°C"))
        assertTrue(dump.contains("Max: 52.5"))
    }

    @Test
    fun `buildDebugDump masks SIM identity to last-4 ICCID and MCC-MNC prefix only`() {
        val snapshot = MetricSnapshot(
            simStatus = SimStatusSnapshot(
                slots = listOf(
                    SimSlot(
                        slotIndex = 0,
                        carrierName = "TestNet",
                        isProvisioned = true,
                        iccidLast4 = "4821",
                        homePlmn = "310260",
                        imsiPrefix = "310260-XXXX-XXXX",
                        msisdn = "15551234567"
                    )
                )
            )
        )
        val dump = collector.buildDebugDump(snapshot)

        // Full subscriber identity must never appear in a diagnostics dump.
        assertTrue(dump.contains("ICCID=...4821"))
        assertFalse(dump.contains("4821".padStart(20, '8'))) // no full 19-20 digit ICCID
        assertTrue(dump.contains("MSISDN=...4567")) // MSISDN masked to last-4
        assertFalse(dump.contains("15551234567")) // full phone number never dumped
        assertTrue(dump.contains("IMSI=310260-XXXX-XXXX"))
        assertTrue(dump.contains("homePLMN=310260"))
    }
}
