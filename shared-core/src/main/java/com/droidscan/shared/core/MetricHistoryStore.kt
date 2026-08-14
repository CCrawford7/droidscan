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
    private val encryptor: LineEncryptor? = null
) {
    private val lock = Any()

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

    /** Appends [snapshot] to the history, pruning to [maxEntries] if needed. */
    fun append(snapshot: MetricSnapshot) {
        synchronized(lock) {
            try {
                val line = json.encodeToString(MetricSnapshot.serializer(), snapshot)
                file.appendText(encode(line) + "\n")
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
                    val plain = decode(line) ?: return@mapNotNull null
                    try {
                        json.decodeFromString(MetricSnapshot.serializer(), plain)
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

    /** True when the backing file stores ciphertext (best-effort). */
    fun isEncrypted(): Boolean = synchronized(lock) {
        try {
            encryptor != null && file.readLines().firstOrNull()?.let { !it.startsWith("{") } == true
        } catch (_: Exception) {
            false
        }
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
        val lines = file.readLines()
        if (lines.size > maxEntries) {
            file.writeText(lines.takeLast(maxEntries).joinToString("\n") + "\n")
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 3600 // 1 hour @ 1 snapshot/sec
    }
}
