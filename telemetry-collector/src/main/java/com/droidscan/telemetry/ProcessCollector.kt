package com.droidscan.telemetry

import android.app.ActivityManager
import android.util.Log
import com.droidscan.shared.core.*
import java.io.File

class ProcessCollector {
    private var am: ActivityManager? = null

    // Previous /proc/stat per-core tick sample, for delta-based (instantaneous) CPU load.
    private var prevCpuTicks: List<LongArray>? = null

    fun attach(activityManager: ActivityManager) {
        am = activityManager
    }

    fun read(shizukuAvailable: Boolean): ProcessSnapshot {
        val manager = am ?: return ProcessSnapshot()

        val memInfo = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memInfo)

        // Per-core load from /proc/stat
        val coreLoads = readCpuLoads()

        // Top processes from RunningAppProcessInfo
        val processes = manager.runningAppProcesses?.take(5) ?: emptyList()
        val topCpu = processes.mapNotNull { proc ->
            ProcessInfo(
                pid = proc.pid,
                name = proc.processName,
                cpuPercent = null,  // would need /proc/pid/stat sampling
                memoryBytes = null   // would need Debug.MemoryInfo
            )
        }
        val topMemory = processes.mapNotNull { proc ->
            val pids = intArrayOf(proc.pid)
            manager.getProcessMemoryInfo(pids).firstOrNull()?.let { mem ->
                ProcessInfo(
                    pid = proc.pid,
                    name = proc.processName,
                    memoryBytes = mem.totalPss * 1024L
                )
            }
        }.sortedByDescending { it.memoryBytes }.take(5)

        val ramUsed = memInfo.totalMem - memInfo.availMem
        val pressure = when {
            memInfo.availMem < memInfo.totalMem / 10 -> MemoryPressure.CRITICAL
            memInfo.availMem < memInfo.totalMem / 4 -> MemoryPressure.MODERATE
            else -> MemoryPressure.NORMAL
        }

        // Kernel wakelocks (Power Mode)
        val wakelocks = if (shizukuAvailable) readKernelWakelocks() else emptyList()

        return ProcessSnapshot(
            perCoreLoad = coreLoads,
            topCpuProcesses = topCpu,
            topMemoryProcesses = topMemory,
            memoryPressure = pressure,
            totalMemoryBytes = memInfo.totalMem,
            usedMemoryBytes = ramUsed,
            kernelWakelocks = wakelocks
        )
    }

    /**
     * Reads instantaneous per-core CPU load by diffing successive /proc/stat samples.
     * The first read returns all zeros (no baseline yet) rather than a misleading
     * since-boot average.
     */
    private fun readCpuLoads(): List<Float> {
        return try {
            val content = File("/proc/stat").readText()
            val current = parseProcStat(content)
            val previous = prevCpuTicks
            prevCpuTicks = current
            if (previous == null || previous.size != current.size) {
                current.map { 0f }
            } else {
                computeCpuLoads(previous, current)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read /proc/stat for CPU load: ${e.message}", e)
            emptyList()
        }
    }

    private fun readKernelWakelocks(): List<String> {
        return try {
            val wakeupSources = File("/d/wakeup_sources")
            if (wakeupSources.exists()) {
                wakeupSources.readLines().drop(1).mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
                }
            } else {
                val wakelocks = File("/proc/wakelocks")
                if (wakelocks.exists()) {
                    wakelocks.readLines().drop(1).mapNotNull { line ->
                        val name = line.trim().split("\\s+".toRegex()).firstOrNull()
                        name?.takeIf { it.isNotEmpty() }
                    }
                } else emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read kernel wakelocks: ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ProcessCollector"

        /**
         * Parses /proc/stat content into per-core tick arrays [user, nice, system, idle].
         * The aggregate "cpu" line is excluded; only "cpu0", "cpu1", ... are returned,
         * in file order.
         */
        internal fun parseProcStat(content: String): List<LongArray> {
            return content.lines().mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                val name = parts.getOrNull(0) ?: return@mapNotNull null
                if (!name.startsWith("cpu") || name.length <= 3) return@mapNotNull null
                if (parts.size < 5) return@mapNotNull null
                val user = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                val nice = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
                val system = parts.getOrNull(3)?.toLongOrNull() ?: return@mapNotNull null
                val idle = parts.getOrNull(4)?.toLongOrNull() ?: return@mapNotNull null
                longArrayOf(user, nice, system, idle)
            }
        }

        /** Computes instantaneous per-core load (0.0..1.0) from two tick samples. */
        internal fun computeCpuLoads(prev: List<LongArray>, curr: List<LongArray>): List<Float> {
            val size = minOf(prev.size, curr.size)
            return (0 until size).map { i ->
                val p = prev[i]
                val c = curr[i]
                val dUser = c[0] - p[0]
                val dNice = c[1] - p[1]
                val dSystem = c[2] - p[2]
                val dIdle = c[3] - p[3]
                val dTotal = dUser + dNice + dSystem + dIdle
                if (dTotal <= 0) 0f else (dUser + dNice + dSystem).toFloat() / dTotal
            }
        }
    }
}
