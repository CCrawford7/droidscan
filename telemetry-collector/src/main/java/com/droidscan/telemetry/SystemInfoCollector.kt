package com.droidscan.telemetry

import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.droidscan.shared.core.SystemInfoSnapshot
import java.io.File

class SystemInfoCollector {
    companion object {
        private const val TAG = "SystemInfoCollector"
    }

    private var wm: WindowManager? = null
    private var displayMetrics: DisplayMetrics? = null

    fun attach(windowManager: WindowManager?) {
        wm = windowManager
        displayMetrics = try {
            val metrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getRealMetrics(metrics)
            metrics
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read display metrics: ${e.message}")
            null
        }
    }

    fun read(): SystemInfoSnapshot {
        // Kernel version via system property (works without /proc access)
        val kernelVersion = try {
            System.getProperty("os.version")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }

        // SELinux status — check via system property
        val selinuxEnforced = try {
            val prop = System.getProperty("selinux.enforce")
            if (prop == "1") true
            else null  // Runtime.exec("getenforce") blocked on Android 14+
        } catch (_: Exception) { null }

        // Uptime via SystemClock (reliable, no /proc needed)
        val uptimeMs = SystemClock.elapsedRealtime()

        val resolution = displayMetrics?.let {
            "${it.widthPixels}x${it.heightPixels}"
        }

        val densityDpi = displayMetrics?.densityDpi?.let { "${it} dpi" }

        return SystemInfoSnapshot(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            buildNumber = Build.DISPLAY,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            kernelVersion = kernelVersion,
            selinuxEnforced = selinuxEnforced,
            uptimeMs = uptimeMs,
            resolution = resolution,
            densityDpi = densityDpi
        )
    }

    private fun readBootTime(): Long {
        return try {
            val uptimeFile = File("/proc/stat")
            val btimeLine = uptimeFile.readLines().firstOrNull { it.startsWith("btime") }
            btimeLine?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull()?.times(1000)
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read boot time from /proc/stat: ${e.message}")
            System.currentTimeMillis()
        }
    }
}
