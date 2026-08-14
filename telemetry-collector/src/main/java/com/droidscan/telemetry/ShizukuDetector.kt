package com.droidscan.telemetry

import android.content.Context
import android.util.Log

/**
 * Shizuku availability facade — the single query point for "is Power Mode
 * usable right now?" consumed by the collectors and the dashboard.
 *
 * Previously a pure reflection probe; now it fronts [ShizukuBridge], which owns
 * the real API lifecycle (permission + UserService binding). [isAvailable] is
 * true only when the Shizuku server is running, permission is granted, AND the
 * elevated telemetry service is bound — i.e. the IPC is actually usable.
 */
class ShizukuDetector {
    private val bridge = ShizukuBridge()

    fun initialize(context: Context) {
        bridge.initialize(context)
        Log.i(TAG, "initialized (available=${bridge.isAvailable()})")
    }

    fun isAvailable(): Boolean = bridge.isAvailable()

    /** Shell-privileged command via Power Mode; null in Standard Mode. */
    fun runCommand(cmd: String): String? = bridge.runCommand(cmd)

    /** Shell-privileged file read via Power Mode; null in Standard Mode. */
    fun readFile(path: String): String? = bridge.readFile(path)

    /** Test/override hook for the UI tier (does not touch the bridge). */
    fun setAvailable(available: Boolean) {
        bridge.setAvailableOverride(available)
    }

    fun hasChecked(): Boolean = true

    private companion object {
        const val TAG = "ShizukuDetector"
    }
}
