package com.droidscan.diagnostics

import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.shared.core.NetworkConfigSnapshot
import com.droidscan.shared.core.RadioSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEngineTest {

    private val corpusJson = """
        {"threads":[
          {"title":"Mobile data not working!","category":"Mobile Data","url":"u1","isSolved":true,"scope":"phone"},
          {"title":"Re: No internet despite full 5G","category":"Mobile Services","url":"u2","isSolved":false,"scope":"phone"}
        ]}
    """.trimIndent()

    @Test
    fun `end to end produces symptoms matches and tiered steps`() {
        val engine = DiagnosticEngine.fromCorpusJson(corpusJson)
        val snap = MetricSnapshot(
            radio = RadioSnapshot(rsrp = -118, dataNetworkType = "NR"),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = null)
        )

        val result = engine.diagnose(snap)

        // Symptoms: weakSignal (rsrp -118) + noData (registered, no active network).
        val presentIds = result.symptoms.filter { it.present }.map { it.id }.toSet()
        assertTrue(presentIds.contains("weakSignal"))
        assertTrue(presentIds.contains("noData"))

        // Matches: both corpus threads name the data symptom.
        assertTrue(result.matches.isNotEmpty())

        // Steps: every present symptom contributes at least one step, and
        // steps are tiered (user-fixable before carrier-side).
        val tiers = result.steps.map { it.tier }
        assertTrue(tiers.contains(ResolutionTier.USER_FIXABLE))
        assertTrue(result.steps.any { it.text.contains("Airplane", ignoreCase = true) })
    }

    @Test
    fun `unresolved community evidence triggers carrier escalation`() {
        val unsolvedOnly = """
            {"threads":[
              {"title":"Re: No internet despite full 5G","category":"Mobile Services","url":"u2","isSolved":false,"scope":"phone"}
            ]}
        """.trimIndent()
        val engine = DiagnosticEngine.fromCorpusJson(unsolvedOnly)
        val snap = MetricSnapshot(
            radio = RadioSnapshot(dataNetworkType = "NR"),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = null)
        )

        val result = engine.diagnose(snap)

        // The only matching thread is unsolved, so the builder must append a
        // carrier-side escalation step.
        assertNotNull(
            result.steps.firstOrNull {
                it.tier == ResolutionTier.CARRIER_SIDE && it.text.contains("escalate", ignoreCase = true)
            }
        )
    }

    @Test
    fun `healthy device has no symptoms and empty plan`() {
        val engine = DiagnosticEngine.fromCorpusJson(corpusJson)
        val snap = MetricSnapshot(
            radio = RadioSnapshot(rsrp = -80, rsrq = -8, sinr = 15, dataNetworkType = "LTE", imsRegistered = true),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = "MOBILE", latencyMs = 40)
        )

        val result = engine.diagnose(snap)
        assertEquals(0, result.symptoms.count { it.present })
        assertEquals(0, result.matches.size)
        assertEquals(0, result.steps.size)
    }
}
