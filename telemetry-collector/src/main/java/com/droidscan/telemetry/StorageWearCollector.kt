package com.droidscan.telemetry

import android.util.Log
import com.droidscan.shared.core.StorageWearSnapshot

/**
 * Deep storage / NAND wear telemetry via `dumpsys diskstats`.
 *
 * `dumpsys diskstats` aggregates per-partition free space, 512B write latency,
 * sustained write speed, and app/data/cache usage. On this device it requires
 * shell privileges (Power Mode) — even `/proc/diskstats` is root-gated. In
 * Standard Mode the collector returns an empty [StorageWearSnapshot] and the
 * UI shows the "requires Shizuku" degraded state.
 *
 * The dump is expensive (it stats the whole /data tree), so [read] self-throttles
 * to once per [POLL_INTERVAL_MS].
 */
class StorageWearCollector {

    private var lastReadMs = 0L
    private var cached: StorageWearSnapshot = StorageWearSnapshot()

    fun read(shizuku: ShizukuDetector?): StorageWearSnapshot {
        if (shizuku?.isAvailable() != true) {
            cached = StorageWearSnapshot()
            return cached
        }
        val now = System.currentTimeMillis()
        if (now - lastReadMs < POLL_INTERVAL_MS) return cached
        lastReadMs = now

        cached = try {
            val dump = shizuku.runCommand("dumpsys diskstats") ?: return cached
            parse(dump)
        } catch (e: Exception) {
            Log.w(TAG, "dumpsys diskstats failed: ${e.message}")
            cached
        }
        return cached
    }

    internal fun parse(dump: String): StorageWearSnapshot {
        val wear = StorageWearSnapshot(powerMode = true)

        var latency: Long? = null
        var writeKbps: Int? = null
        var dataFreePct: Int? = null
        var fbe: Boolean? = null
        var appSize: Long? = null
        var appDataSize: Long? = null
        var appCacheSize: Long? = null
        var photosSize: Long? = null
        var videosSize: Long? = null
        var audioSize: Long? = null

        dump.lines().forEach { line ->
            val l = line.trim()
            Regex("""Latency:\s*(\d+)ms.*512B Data Write""").find(l)?.let {
                latency = it.groupValues[1].toLongOrNull()
            }
            Regex("""Recent Disk Write Speed \(kB/s\)\s*=\s*(\d+)""").find(l)?.let {
                writeKbps = it.groupValues[1].toIntOrNull()
            }
            Regex("""Data-Free:\s*\d+K\s*/\s*\d+K\s*total\s*=\s*(\d+)%\s*free""").find(l)?.let {
                dataFreePct = it.groupValues[1].toIntOrNull()
            }
            if (l.startsWith("File-based Encryption:")) {
                fbe = l.substringAfter(':').trim().toBooleanStrictOrNull()
            }
            Regex("""^(.+) Size:\s*(\d+)$""").find(l)?.let { m ->
                val v = m.groupValues[2].toLongOrNull() ?: return@let
                when (m.groupValues[1]) {
                    "App" -> appSize = v
                    "App Data" -> appDataSize = v
                    "App Cache" -> appCacheSize = v
                    "Photos" -> photosSize = v
                    "Videos" -> videosSize = v
                    "Audio" -> audioSize = v
                }
            }
        }

        return wear.copy(
            writeLatencyMs = latency,
            recentWriteKbps = writeKbps,
            dataFreePercent = dataFreePct,
            fileBasedEncryption = fbe,
            appSizeBytes = appSize,
            appDataSizeBytes = appDataSize,
            appCacheSizeBytes = appCacheSize,
            photosSizeBytes = photosSize,
            videosSizeBytes = videosSize,
            audioSizeBytes = audioSize
        )
    }

    companion object {
        private const val TAG = "StorageWearCollector"
        private const val POLL_INTERVAL_MS = 60_000L
    }
}
