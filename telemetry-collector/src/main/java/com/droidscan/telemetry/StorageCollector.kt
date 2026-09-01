package com.droidscan.telemetry

import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.droidscan.shared.core.StorageSnapshot
import com.droidscan.shared.core.StorageConsumer
import java.io.File

class StorageCollector {

    // The /data/app top-consumer walk stats the whole tree (thousands of files)
    // and is called from the 1s observeAll loop — cache the result and only
    // re-walk on the poll interval. The StatFs used/free reads are cheap, so
    // those always refresh; only the expensive consumer enumeration is cached.
    private var lastWalkMs = 0L
    private var cachedConsumers: List<StorageConsumer> = emptyList()

    fun read(): StorageSnapshot {
        val internal = StatFs(Environment.getDataDirectory().absolutePath)
        val internalBlockSize = internal.blockSizeLong
        val internalTotal = internal.blockCountLong * internalBlockSize
        val internalFree = internal.availableBlocksLong * internalBlockSize

        val externalMounted = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
        val externalUsed: Long?
        val externalFree: Long?
        if (externalMounted) {
            val extStat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val extBlockSize = extStat.blockSizeLong
            val extTotal = extStat.blockCountLong * extBlockSize
            val free = extStat.availableBlocksLong * extBlockSize
            externalUsed = extTotal - free
            externalFree = free
        } else {
            externalUsed = null
            externalFree = null
        }

        // Top consumers — approximate by querying /data/app, throttled: walking
        // the tree is O(files) and must not run every second.
        val now = System.currentTimeMillis()
        if (now - lastWalkMs >= POLL_INTERVAL_MS) {
            lastWalkMs = now
            cachedConsumers = walkTopConsumers()
        }

        return StorageSnapshot(
            internalUsedBytes = internalTotal - internalFree,
            internalFreeBytes = internalFree,
            systemBytes = null, // requires StorageStatsManager on API 26+
            userBytes = internalTotal,
            externalMounted = externalMounted,
            externalUsedBytes = externalUsed,
            externalFreeBytes = externalFree,
            topConsumers = cachedConsumers
        )
    }

    private fun walkTopConsumers(): List<StorageConsumer> {
        val consumers = mutableListOf<StorageConsumer>()
        try {
            val dataApp = File("/data/app")
            if (dataApp.exists()) {
                dataApp.listFiles()?.forEach { dir ->
                    val size = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                    if (size > 50_000_000) { // >50MB
                        consumers.add(StorageConsumer(
                            packageName = dir.name,
                            label = dir.name.removeSuffix("-1").removeSuffix("-2").removeSuffix("-3"),
                            sizeBytes = size
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("StorageCollector", "Failed to enumerate /data/app consumers: ${e.message}", e)
        }
        return consumers.sortedByDescending { it.sizeBytes }.take(3)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
    }
}
