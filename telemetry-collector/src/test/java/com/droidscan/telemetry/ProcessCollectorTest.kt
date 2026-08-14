package com.droidscan.telemetry

import com.droidscan.telemetry.ProcessCollector.Companion.computeCpuLoads
import com.droidscan.telemetry.ProcessCollector.Companion.parseProcStat
import com.droidscan.telemetry.ProcessCollector.Companion.parseWakeLocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessCollectorTest {

    @Test
    fun `parseProcStat extracts per-core ticks and skips aggregate`() {
        val content = """
            cpu  100 10 50 2000
            cpu0 10  1  5  100
            cpu1 20  2 10  200
            intr 1234
        """.trimIndent()
        val cores = parseProcStat(content)
        assertEquals(2, cores.size)
        assertEquals(10, cores[0][0]) // user
        assertEquals(1, cores[0][1])  // nice
        assertEquals(5, cores[0][2])  // system
        assertEquals(100, cores[0][3]) // idle
        assertEquals(20, cores[1][0])
    }

    @Test
    fun `parseProcStat handles garbage lines gracefully`() {
        assertEquals(0, parseProcStat("").size)
        assertEquals(0, parseProcStat("not-a-cpu-line 1 2 3").size)
        assertEquals(0, parseProcStat("cpu0 1 2").size) // too few columns
    }

    @Test
    fun `computeCpuLoads gives instantaneous delta not lifetime average`() {
        // cpu0: prev busy=10/110 (9%), curr busy=20/120 (16.7%).
        // Instantaneous delta between samples = 10/10 = 100%.
        val prev = listOf(longArrayOf(10, 0, 0, 100))
        val curr = listOf(longArrayOf(20, 0, 0, 100))
        val loads = computeCpuLoads(prev, curr)
        assertEquals(1, loads.size)
        assertEquals(1.0f, loads[0], 0.0001f)
    }

    @Test
    fun `computeCpuLoads returns zero when no ticks elapsed`() {
        val prev = listOf(longArrayOf(10, 0, 0, 100))
        val curr = listOf(longArrayOf(10, 0, 0, 100))
        assertEquals(0.0f, computeCpuLoads(prev, curr)[0], 0.0001f)
    }

    @Test
    fun `computeCpuLoads handles multiple cores independently`() {
        val prev = listOf(
            longArrayOf(100, 0, 0, 900),  // idle core
            longArrayOf(50, 0, 50, 0)     // fully busy core
        )
        val curr = listOf(
            longArrayOf(101, 0, 0, 901),  // +1 busy, +1 idle -> 1/2 = 0.5
            longArrayOf(50, 0, 50, 0)     // no ticks -> 0.0
        )
        val loads = computeCpuLoads(prev, curr)
        assertEquals(2, loads.size)
        assertEquals(0.5f, loads[0], 0.0001f)
        assertEquals(0.0f, loads[1], 0.0001f)
    }

    @Test
    fun `computeCpuLoads guards against tick counter resets`() {
        val prev = listOf(longArrayOf(100, 0, 0, 900))
        val curr = listOf(longArrayOf(5, 0, 0, 10)) // counters went backwards
        assertEquals(0.0f, computeCpuLoads(prev, curr)[0], 0.0001f)
    }

    @Test
    fun `parseWakeLocks extracts names from real dumpsys power output`() {
        // Real CPH2709 output: 2-space indent, single-quoted names, trailing sections.
        val dump = """
            Wake Locks: size=2
              PARTIAL_WAKE_LOCK '*job*r/@androidx.work.systemjobscheduler@com.wizconnected.wiz2/androidx.work.impl.background.systemjob.SystemJobService' ACQ=-2m31s334ms LONG (uid=1000 pid=3201 ws=WorkSource{ chains=WorkChain{(10525), (1000, JobScheduler)}})
              PARTIAL_WAKE_LOCK 'AudioMix' ACQUIRE_CAUSES_WAKEOFF
            Proxyed Wake Locks:

            Restored Wake Locks:

            Proxy uid infos:
            Wake lock history:
        """.trimIndent()
        val names = ProcessCollector.parseWakeLocks(dump)
        assertEquals(2, names.size)
        assertTrue(names.contains("AudioMix"))
        assertTrue(names.any { it.startsWith("*job*r/@androidx.work") })
    }

    @Test
    fun `parseWakeLocks handles zero wake locks`() {
        val dump = """
            Wake Locks: size=0
            Proxyed Wake Locks:

            Restored Wake Locks:
        """.trimIndent()
        assertEquals(0, ProcessCollector.parseWakeLocks(dump).size)
    }

    @Test
    fun `parseWakeLocks tolerates 4-space indentation`() {
        val dump = """
            Wake Locks: size=1
                PARTIAL_WAKE_LOCK 'AudioMix' ACQUIRE_CAUSES_WAKEOFF
            Proxyed Wake Locks:
        """.trimIndent()
        assertEquals(listOf("AudioMix"), ProcessCollector.parseWakeLocks(dump))
    }
}
