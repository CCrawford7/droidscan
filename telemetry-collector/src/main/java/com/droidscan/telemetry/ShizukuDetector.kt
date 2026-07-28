package com.droidscan.telemetry

import android.util.Log

/**
 * Shizuku availability detector.
 *
 * Uses reflection to check whether Shizuku API 13+ is available.
 * This avoids compile-time linkage issues — Shizuku is purely optional.
 * When not installed (or permission denied), [isAvailable] returns false
 * and all collectors fall back to Standard Mode.
 *
 * Power Mode (Shizuku) adds:
 *   - Kernel-level thermal zone readings (via sysfs)
 *   - Wakeup source / wakelock data from debugfs
 *   - Future: RIL-level telephony detail via dumpsys
 */
class ShizukuDetector {
    private var available = false
    private var checked = false

    fun initialize() {
        available = try {
            // Check if Shizuku runtime classes are present on the classpath.
            // The actual service connection happens asynchronously; we just
            // verify the infrastructure exists. Collectors that need Shizuku
            // will fall back gracefully if the service isn't connected.
            Class.forName("rikka.shizuku.Shizuku")
            Class.forName("rikka.shizuku.ShizukuProvider")
            Log.i("ShizukuDetector", "Shizuku API 13+ runtime detected — Power Mode may be available")
            true
        } catch (e: ClassNotFoundException) {
            Log.i("ShizukuDetector", "Shizuku not installed — Standard Mode")
            false
        } catch (e: Exception) {
            Log.w("ShizukuDetector", "Shizuku check error: ${e.message ?: e.javaClass.simpleName}")
            false
        }
        checked = true
    }

    fun setAvailable(available: Boolean) {
        this.available = available
        this.checked = true
    }

    fun isAvailable(): Boolean = available
    fun hasChecked(): Boolean = checked
}
