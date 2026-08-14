package com.droidscan.telemetry

import android.content.Context
import android.util.Log

/**
 * Power-Mode availability facade — the single query point for "is elevated
 * telemetry usable right now?" consumed by the collectors and the dashboard.
 *
 * Two transports, used in preference order:
 *
 *  1. [RootShellBridge] — when the device is rooted, `su` provides the same
 *     shell-level reads with zero setup (T3).
 *  2. [ShizukuBridge] — the T2 path: Shizuku server running + permission
 *     granted + UserService bound (UID 2000).
 *
 * [isAvailable] is true when either transport can actually execute. Collectors
 * call [runCommand]/[readFile] which route to whichever is live.
 */
class ShizukuDetector {
    private val bridge = ShizukuBridge()
    private val rootShell = RootShellBridge()

    /** Whether a Power-Mode transport is usable right now. */
    fun isAvailable(): Boolean = rootShell.isAvailable() || bridge.isAvailable()

    /** Shell-privileged command; null in Standard Mode (no root, no Shizuku). */
    fun runCommand(cmd: String): String? =
        rootShell.runCommand(cmd) ?: bridge.runCommand(cmd)

    /** Shell-privileged file read; null in Standard Mode. */
    fun readFile(path: String): String? =
        rootShell.readFile(path) ?: bridge.readFile(path)

    /** Which transport is live (for the UI). */
    fun transport(): String = when {
        rootShell.isAvailable() -> "ROOT"
        bridge.isAvailable() -> "SHIZUKU"
        else -> "NONE"
    }

    /** Test/override hook for the UI tier (forces the whole detector). */
    fun setAvailable(available: Boolean) {
        bridge.setAvailableOverride(available)
        if (!available) rootShell.invalidate()
    }

    fun initialize(context: Context) {
        bridge.initialize(context)
        Log.i(TAG, "initialized (available=${isAvailable()}, transport=${transport()})")
    }

    fun hasChecked(): Boolean = true

    private companion object {
        const val TAG = "ShizukuDetector"
    }
}
