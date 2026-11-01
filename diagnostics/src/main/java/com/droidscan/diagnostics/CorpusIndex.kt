package com.droidscan.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-memory index over the bundled EE-community corpus asset.
 *
 * The asset is produced by `diagnostics/tools/build_corpus.py` from the raw
 * EE dataset scrapes under the repo `/datasets` directory; it ships inside
 * the app so matching is fully offline (NFR6: no ML/vector runtime, no network).
 */
@Serializable
private data class CorpusFile(val threads: List<CorpusThread> = emptyList())

class CorpusIndex(val threads: List<CorpusThread>) {

    /**
     * Threads within [scope] (default "phone"). A phone app can reason about
     * cellular/SIM/roaming threads but cannot diagnose a home router or a
     * broadband line, so "broadband" threads are retained yet excluded here.
     */
    fun scoped(scope: String = "phone"): List<CorpusThread> =
        threads.filter { it.scope == scope }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun fromJson(raw: String): CorpusIndex =
            CorpusIndex(json.decodeFromString<CorpusFile>(raw).threads)
    }
}
