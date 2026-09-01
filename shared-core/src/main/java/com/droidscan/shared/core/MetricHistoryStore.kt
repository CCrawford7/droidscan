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
 * At-rest protection: pass a [LineEncryptor] to encrypt each line (AES-GCM via
 * Android Keystore in [KeystoreAesGcmLineEncryptor]). On first load with an
 * encryptor, any legacy plaintext file (first line starts with `{`) is migrated
 * in place so previously captured history survives the upgrade. A null
 * encryptor keeps the store plaintext — the default and the JVM-testable path.
 *
 * Thread-safe: all operations are serialized on an internal lock. Callers on
 * Dispatchers.IO are fine; heavy concurrent writers will briefly serialize.
 */
class MetricHistoryStore(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val encryptor: LineEncryptor? = null,
    /**
     * Minimum gap between full-file prune rewrites. Once the store exceeds
     * [maxEntries], every append would otherwise read+rewrite the whole file —
     * after ~1h of 1s appends that's a multi-MB rewrite EVERY second. A cooldown
     * bounds the cost to at most one rewrite per interval (the raw file may
     * briefly exceed [maxEntries] between prunes; the in-memory cache and every
     * read path stay hard-bounded to [maxEntries]). 0 = prune on every overflow
     * (exact boundedness — the test path).
     */
    private val pruneIntervalMs: Long = 0L
) {
    private val lock = Any()

    private var lastPruneMs = 0L

    // In-memory view of the decrypted JSON lines, loaded once from the file and
    // kept in sync by append(). This is what makes chart refreshes cheap: the
    // alternative — re-reading a multi-MB file and re-decrypting every line on
    // every 10s trend refresh — pins the heap and lags the UI on small devices.
    // Only the decrypted STRINGS are cached (~5KB/line); snapshots are decoded
    // on demand, so the chart decodes just its ~120 sampled points.
    private val cacheLines = ArrayDeque<String>()
    private var cacheLoaded = false

    init {
        file.parentFile?.mkdirs()
        encryptor?.let { migrateLegacyPlaintext(it) }
    }

    private fun encode(line: String): String = encryptor?.encrypt(line) ?: line

    private fun decode(line: String): String? = try {
        encryptor?.decrypt(line) ?: line
    } catch (_: Exception) {
        null // tampered / corrupt line — never surface garbage
    }

    private fun parseSnapshot(line: String): MetricSnapshot? = try {
        json.decodeFromString(MetricSnapshot.serializer(), line)
    } catch (_: Exception) {
        null // skip corrupt/partial line
    }

    /** Loads the decrypted line cache from the file, once per process. */
    private fun ensureLinesLoaded() {
        if (cacheLoaded) return
        file.readLines().takeLast(maxEntries).forEach { line ->
            decode(line)?.let { cacheLines.addLast(it) }
        }
        cacheLoaded = true
    }

    /** Appends [snapshot] to the history, pruning to [maxEntries] if needed. */
    fun append(snapshot: MetricSnapshot) {
        synchronized(lock) {
            try {
                val line = json.encodeToString(MetricSnapshot.serializer(), snapshot)
                file.appendText(encode(line) + "\n")
                prune()
                if (cacheLoaded) {
                    cacheLines.addLast(line)
                    while (cacheLines.size > maxEntries) cacheLines.removeFirst()
                }
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
                ensureLinesLoaded()
                cacheLines.takeLast(n).mapNotNull { parseSnapshot(it) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * The chart read path: the last [window] entries, downsampled to at most
     * [maxPoints], anchored so the newest snapshot is always the last point.
     * Only the sampled lines are decoded to objects — cheap even with a large
     * backing file.
     */
    fun recentSampled(window: Int, maxPoints: Int): List<MetricSnapshot> {
        synchronized(lock) {
            return try {
                ensureLinesLoaded()
                val lines = cacheLines.takeLast(window)
                val n = lines.size
                if (n <= maxPoints) {
                    lines.mapNotNull { parseSnapshot(it) }
                } else {
                    val step = (n - 1).toDouble() / (maxPoints - 1)
                    (0 until maxPoints).map { i ->
                        parseSnapshot(lines[(i * step).toInt()])
                    }.filterNotNull()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /** Number of persisted entries. */
    fun size(): Int = synchronized(lock) {
        try {
            ensureLinesLoaded()
            cacheLines.size
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
            cacheLines.clear()
            cacheLoaded = true
        }
    }

    /** True when the backing file stores ciphertext (best-effort). */
    fun isEncrypted(): Boolean = synchronized(lock) {
        try {
            encryptor != null && firstLine(file)?.let { !it.startsWith("{") } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun firstLine(f: File): String? = try {
        f.bufferedReader(Charsets.UTF_8).use { it.readLine() }
    } catch (_: Exception) {
        null
    }

    /** Rewrites a legacy plaintext JSONL file to encrypted lines, in place. */
    private fun migrateLegacyPlaintext(enc: LineEncryptor) {
        synchronized(lock) {
            try {
                if (!file.exists()) return
                val lines = file.readLines()
                val isPlaintext = lines.firstOrNull()?.startsWith("{") ?: false
                if (!isPlaintext) return
                file.writeText(lines.joinToString("\n") { enc.encrypt(it) } + "\n")
            } catch (_: Exception) {
                // Migration is best-effort; if it fails the old file is preserved
                // untouched and the next append/read still works line-by-line.
            }
        }
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        if (now - lastPruneMs < pruneIntervalMs) return
        lastPruneMs = now
        val lines = file.readLines()
        if (lines.size > maxEntries) {
            file.writeText(lines.takeLast(maxEntries).joinToString("\n") + "\n")
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 3600 // 1 hour @ 1 snapshot/sec
    }
}
