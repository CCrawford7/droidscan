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
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            Class.forName("rikka.shizuku.ShizukuProvider")

            // Real service-bound + permission check (not just classpath presence).
            // Class.forName only proves the runtime is installed; Power Mode is only
            // usable when the binder is live AND the app holds the Shizuku permission.
            val bound = try {
                shizukuClass.getMethod("pingBinder").invoke(null) as? Boolean ?: false
            } catch (e: Exception) {
                false
            }

            val permissionGranted = try {
                val result = shizukuClass.getMethod("checkSelfPermission").invoke(null) as? Int
                result == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

            if (bound && permissionGranted) {
                Log.i("ShizukuDetector", "Shizuku service bound + permission granted — Power Mode available")
                true
            } else {
                Log.i("ShizukuDetector", "Shizuku runtime present but not ready (bound=$bound, permGranted=$permissionGranted) — Standard Mode")
                false
            }
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
