package com.droidscan.telemetry

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import com.droidscan.shared.core.*
import java.io.File

class ProcessCollector {
    private var am: ActivityManager? = null

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

    private fun readCpuLoads(): List<Float> {
        return try {
            val stat = File("/proc/stat").readText()
            val cpuLines = stat.lines().filter { it.startsWith("cpu") && it.length > 3 }
            cpuLines.map { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 5) 0f else {
                    val user = parts[1].toLongOrNull() ?: 0L
                    val nice = parts[2].toLongOrNull() ?: 0L
                    val system = parts[3].toLongOrNull() ?: 0L
                    val idle = parts[4].toLongOrNull() ?: 0L
                    val total = user + nice + system + idle
                    if (total == 0L) 0f else (user + nice + system).toFloat() / total
                }
            }
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            emptyList()
        }
    }
}
