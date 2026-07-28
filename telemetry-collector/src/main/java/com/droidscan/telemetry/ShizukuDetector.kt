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
            val shizukuClass = Class.forName("moe.shizuku.api.Shizuku")
            val pingMethod = shizukuClass.getMethod("ping")
            val result = pingMethod.invoke(null) as? Int ?: -1
            val isAvail = result >= 0

            if (isAvail) {
                Log.i("ShizukuDetector", "Shizuku API detected — Power Mode available (ping=$result)")
            } else {
                Log.i("ShizukuDetector", "Shizuku ping returned $result")
            }
            isAvail
        } catch (e: ClassNotFoundException) {
            Log.i("ShizukuDetector", "Shizuku not installed — Standard Mode")
            false
        } catch (e: NoSuchMethodException) {
            Log.w("ShizukuDetector", "Shizuku API version mismatch: ${e.message}")
            false
        } catch (e: SecurityException) {
            Log.w("ShizukuDetector", "Shizuku permission denied: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w("ShizukuDetector", "Shizuku check failed: ${e.message}")
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
