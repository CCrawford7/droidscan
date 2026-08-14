package com.droidscan.telemetry

import com.droidscan.shared.core.MetricHistoryStore
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.shared.core.ProcessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Locks the anchored downsampling in [TelemetryCollector.historyForCharts]:
 * the newest snapshot must always be the last chart point, and a metric that
 * only exists in the tail of the window (e.g. CPU right after a Power-Mode
 * bridge upgrade, where the pre-upgrade history has empty perCoreLoad) must
 * still surface as chart points rather than being sampled away.
 */
class HistoryForChartsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `newest snapshot is always the last point`() {
        val store = storeWith(3600, cpuFrom = -1) // no CPU anywhere
        val collector = TelemetryCollector(historyStore = store)

        // Append a final CPU snapshot AFTER the window-filling data.
        store.append(MetricSnapshot(processes = ProcessSnapshot(perCoreLoad = listOf(0.42f))))

        val chart = collector.historyForCharts()
        val last = chart.last()
        assertEquals(0.42f, last.processes.perCoreLoad.firstOrNull() ?: -1f, 0f)
    }

    @Test
    fun `tail-only cpu metric surfaces as chart points`() {
        // 3600 entries with NO cpu, then 150 entries WITH cpu — mirrors the
        // post-upgrade state where the pre-fix ring buffer is all empty.
        val store = storeWith(3600, cpuFrom = -1)
        val collector = TelemetryCollector(historyStore = store)
        repeat(150) { store.append(MetricSnapshot(processes = ProcessSnapshot(perCoreLoad = listOf(0.5f)))) }

        val chart = collector.historyForCharts()
        val cpuPoints = chart.count { it.processes.perCoreLoad.isNotEmpty() }
        assertTrue("expected ≥2 cpu points in chart, got $cpuPoints", cpuPoints >= 2)
    }

    @Test
    fun `uniform sample when cpu fills the whole window`() {
        val store = storeWith(1800, cpuFrom = 0)
        val collector = TelemetryCollector(historyStore = store)
        val chart = collector.historyForCharts()
        assertEquals(TelemetryCollector.CHART_MAX_POINTS, chart.size)
        assertTrue(chart.all { it.processes.perCoreLoad.isNotEmpty() })
    }

    private fun storeWith(entries: Int, cpuFrom: Int): MetricHistoryStore {
        val store = MetricHistoryStore(tmp.newFile())
        repeat(entries) { i ->
            val perCore = if (cpuFrom >= 0 && i >= cpuFrom) listOf(0.3f) else emptyList()
            store.append(MetricSnapshot(processes = ProcessSnapshot(perCoreLoad = perCore)))
        }
        return store
    }
}
