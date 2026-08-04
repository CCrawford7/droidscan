package com.droidscan.shared.core

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Append-only, file-backed history of [MetricSnapshot]s stored as JSON lines.
 *
 * This gives the app time-series persistence across restarts — the foundation
 * for trend charts and the future diagnostic engine. Writes are appended in
 * O(1) (no full rewrite per tick); the file is pruned to [maxEntries] when it
 * overflows so it can never grow unbounded.
 *
 * Thread-safe: all operations are serialized on an internal lock. Callers on
 * Dispatchers.IO are fine; heavy concurrent writers will briefly serialize.
 */
class MetricHistoryStore(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val lock = Any()

    init {
        file.parentFile?.mkdirs()
    }

    /** Appends [snapshot] to the history, pruning to [maxEntries] if needed. */
    fun append(snapshot: MetricSnapshot) {
        synchronized(lock) {
            try {
                val line = json.encodeToString(MetricSnapshot.serializer(), snapshot)
                file.appendText(line + "\n")
                prune()
            } catch (e: Exception) {
                // Never let a history failure break the telemetry loop.
                // (Logging isn't available in shared-core; failures are best-effort.)
            }
        }
    }

    /** Returns up to [n] most-recent snapshots, oldest-first. */
    fun recent(n: Int): List<MetricSnapshot> {
        synchronized(lock) {
            return try {
                file.readLines().takeLast(n).mapNotNull { line ->
                    try {
                        json.decodeFromString(MetricSnapshot.serializer(), line)
                    } catch (_: Exception) {
                        null // skip corrupt/partial line
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /** Number of persisted entries. */
    fun size(): Int = synchronized(lock) {
        try {
            file.readLines().size
        } catch (_: Exception) {
            0
        }
    }

    /** Removes all persisted history. */
    fun clear() {
        synchronized(lock) {
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun prune() {
        val lines = file.readLines()
        if (lines.size > maxEntries) {
            file.writeText(lines.takeLast(maxEntries).joinToString("\n") + "\n")
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 3600 // 1 hour @ 1 snapshot/sec
    }
}
