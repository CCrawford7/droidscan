package com.droidscan.diagnostics

import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.shared.core.NetworkConfigSnapshot
import com.droidscan.shared.core.RadioSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusMatcherTest {

    private val corpusJson = """
        {"threads":[
          {"title":"Mobile data not working!","category":"Mobile Data","url":"u1","isSolved":true,"scope":"phone"},
          {"title":"Mobile data not working and I've tried everything","category":"Mobile Data","url":"u1b","isSolved":false,"scope":"phone"},
          {"title":"Not registered on network","category":"Mobile Network discussions","url":"u2","isSolved":true,"scope":"phone"},
          {"title":"5gee router which antenna","category":"Mobile Network discussions","url":"u3","isSolved":false,"scope":"phone"},
          {"title":"Unable to connect to 192.168.1.254","category":"Broadband & Landline","url":"u4","isSolved":false,"scope":"broadband"}
        ]}
    """.trimIndent()

    private val noDataSnapshot = MetricSnapshot(
        radio = RadioSnapshot(dataNetworkType = "NR"),
        networkConfig = NetworkConfigSnapshot(activeNetworkType = null)
    )

    @Test
    fun `matches phone threads and drops broadband and device-specific`() {
        val corpus = CorpusIndex.fromJson(corpusJson)
        val present = SymptomExtractor.present(noDataSnapshot)
        val matches = CorpusMatcher.match(present, corpus, maxResults = 10)

        val titles = matches.map { it.title }
        assertTrue("expected a mobile-data match", titles.any { it.contains("Mobile data not working") })
        assertTrue("router title must be filtered", titles.none { it.contains("router") })
        assertTrue("broadband title must be filtered", titles.none { it.contains("192.168") })
    }

    @Test
    fun `solved thread outranks unresolved with identical keyword match`() {
        val corpus = CorpusIndex.fromJson(corpusJson)
        val present = SymptomExtractor.present(noDataSnapshot)
        val matches = CorpusMatcher.match(present, corpus, maxResults = 10)

        val solved = matches.firstOrNull { it.title == "Mobile data not working!" }
        val unsolved = matches.firstOrNull { it.title.contains("tried everything") }
        assertEquals(true, solved != null && unsolved != null)
        assertTrue("solved should score above unresolved", solved!!.score > unsolved!!.score)
    }

    @Test
    fun `no present symptoms yields no matches`() {
        val corpus = CorpusIndex.fromJson(corpusJson)
        val healthy = MetricSnapshot(
            radio = RadioSnapshot(rsrp = -80, dataNetworkType = "LTE"),
            networkConfig = NetworkConfigSnapshot(activeNetworkType = "MOBILE", latencyMs = 40)
        )
        assertEquals(0, SymptomExtractor.present(healthy).size)
        assertEquals(0, CorpusMatcher.match(SymptomExtractor.present(healthy), corpus).size)
    }
}
