package com.droidscan.diagnostics

import com.droidscan.shared.core.MetricSnapshot

/**
 * Public entry point for the diagnostic engine. Wires the four-stage pipeline
 * from `docs/ARCHITECTURE.md` §3:
 *
 * ```
 * MetricSnapshot ──> SymptomExtractor ──> CorpusMatcher ──> RecommendationBuilder
 *                        │  (present symptoms)     │ (ranked threads)      │ (tiered steps)
 *                        └─────────────────────────┴───────────────────────┘
 *                                              DiagnosticResult
 * ```
 *
 * Construct with [fromCorpusJson] (the bundled `corpus/ee_corpus.json` asset),
 * then call [diagnose] on any snapshot. Pure Kotlin — no Android, no network.
 */
class DiagnosticEngine(private val corpus: CorpusIndex) {

    fun diagnose(snapshot: MetricSnapshot): DiagnosticResult {
        val symptoms = SymptomExtractor.extract(snapshot)
        val present = symptoms.filter { it.present }
        val matches = CorpusMatcher.match(present, corpus)
        val steps = RecommendationBuilder.build(present, matches)
        return DiagnosticResult(
            symptoms = symptoms,
            matches = matches,
            steps = steps,
            generatedAtMs = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromCorpusJson(json: String): DiagnosticEngine =
            DiagnosticEngine(CorpusIndex.fromJson(json))
    }
}
