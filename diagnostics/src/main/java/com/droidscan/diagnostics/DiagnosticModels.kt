package com.droidscan.diagnostics

import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────
// Diagnostic engine data model.
// Pure Kotlin + kotlinx.serialization, mirroring the
// MetricSnapshot envelope so a DiagnosticResult can be
// serialised, exported, or shipped off-device later.
// ──────────────────────────────────────────────

@Serializable
enum class Severity { LOW, MEDIUM, HIGH }

@Serializable
enum class ResolutionTier {
    USER_FIXABLE,   // under 60 seconds, no settings changes
    DEEP_SETTINGS,  // guided settings / hidden-menu redirect
    CARRIER_SIDE    // needs the carrier; verbatim cheat-sheet
}

@Serializable
data class DiagnosticSymptom(
    val id: String,
    val label: String,
    val severity: Severity,
    val present: Boolean,
    val detail: String? = null
)

@Serializable
data class CorpusThread(
    val title: String,
    val category: String,
    val url: String,
    val isSolved: Boolean,
    val views: Int = 0,
    val replies: Int = 0,
    val scope: String = "phone"
)

@Serializable
data class CorpusMatch(
    val title: String,
    val category: String,
    val url: String,
    val isSolved: Boolean,
    val score: Double
)

@Serializable
data class RecommendationStep(
    val text: String,
    val tier: ResolutionTier,
    val glossaryId: String? = null
)

@Serializable
data class DiagnosticResult(
    val symptoms: List<DiagnosticSymptom>,
    val matches: List<CorpusMatch>,
    val steps: List<RecommendationStep>,
    val generatedAtMs: Long
)
