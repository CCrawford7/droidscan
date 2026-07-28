package com.droidscan.telemetry

import android.os.Build
import com.droidscan.shared.core.SystemInfoSnapshot
import java.io.File

class SystemInfoCollector {

    fun read(): SystemInfoSnapshot {
        val kernelVersion = try {
            File("/proc/version").readText().trim().split(" ").getOrNull(2)
        } catch (_: Exception) { null }

        val selinuxEnforced = try {
            val selinux = File("/sys/fs/selinux/enforce")
            if (selinux.exists()) selinux.readText().trim() == "1" else null
        } catch (_: Exception) { null }

        return SystemInfoSnapshot(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            buildNumber = Build.DISPLAY,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            kernelVersion = kernelVersion,
            selinuxEnforced = selinuxEnforced,
            uptimeMs = System.currentTimeMillis() - readBootTime()
        )
    }

    private fun readBootTime(): Long {
        return try {
            val uptimeFile = File("/proc/stat")
            val btimeLine = uptimeFile.readLines().firstOrNull { it.startsWith("btime") }
            btimeLine?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull()?.times(1000)
                ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
