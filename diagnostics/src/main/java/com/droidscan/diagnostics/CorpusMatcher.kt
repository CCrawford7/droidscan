package com.droidscan.diagnostics

/**
 * Deterministic, keyword-based matcher over the corpus. No ML, no network.
 *
 * For each phone-scope thread, score = Σ over present symptoms of
 * (severity weight × keyword hits in the normalized title), plus a small
 * solved-thread preference. Threads with no overlap score 0 and drop out.
 */
object CorpusMatcher {

    private const val SOLVED_BONUS = 0.5

    fun match(
        presentSymptoms: List<DiagnosticSymptom>,
        corpus: CorpusIndex,
        maxResults: Int = 5
    ): List<CorpusMatch> {
        if (presentSymptoms.isEmpty()) return emptyList()

        val scored = corpus.scoped("phone").mapNotNull { thread ->
            if (isDeviceSpecific(thread.title)) return@mapNotNull null
            val title = normalize(thread.title)
            var score = 0.0
            for (symptom in presentSymptoms) {
                val weight = severityWeight(symptom.severity)
                for (keyword in SymptomCatalog.keywordsFor(symptom.id)) {
                    if (normalize(keyword).let { it.isNotEmpty() && title.contains(it) }) {
                        score += weight
                    }
                }
            }
            if (thread.isSolved) score += SOLVED_BONUS
            if (score <= 0.0) null
            else CorpusMatch(
                title = thread.title,
                category = thread.category,
                url = thread.url,
                isSolved = thread.isSolved,
                score = score
            )
        }

        return scored.sortedByDescending { it.score }.take(maxResults)
    }

    private fun severityWeight(severity: Severity): Double = when (severity) {
        Severity.HIGH -> 3.0
        Severity.MEDIUM -> 2.0
        Severity.LOW -> 1.0
    }

    /**
     * Router / CPE / wearable titles that hide inside phone-scoped categories
     * (e.g. "5gee router which antenna" is filed under Mobile Network
     * discussions). A phone app cannot diagnose these — filter them out.
     */
    private val NEGATIVE_GUARD = listOf(
        "router", "cpe", "smart hub", "hub manager", "192.168", "broadband",
        "full fibre", "mini wifi", "mifi", "openreach", "ont", "watch"
    )

    private fun isDeviceSpecific(title: String): Boolean {
        val t = normalize(title)
        return NEGATIVE_GUARD.any { t.contains(normalize(it)) }
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .removePrefix("re:")
            .filter { it.isLetterOrDigit() || it == ' ' }
            .trim()
}
