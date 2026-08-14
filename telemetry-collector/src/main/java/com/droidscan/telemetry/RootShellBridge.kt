package com.droidscan.telemetry

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root fallback (T3): when the device is rooted, run shell-privileged reads via
 * `su` directly — no Shizuku needed. Detected by probing for a usable `su`
 * binary; every call is gated on [isAvailable] so an absent/denied root never
 * degrades the app.
 *
 * The probe deliberately exercises the full path (`su -c id`) rather than just
 * checking `which su`, because some ROMs ship a `su` stub that silently refuses
 * (Magisk `su` returns 0 only when granted; denials return non-zero).
 *
 * Used by [ShizukuDetector] as a strictly-better-than-nothing alternative to
 * Shizuku: root first, Shizuku second.
 */
class RootShellBridge {

    @Volatile
    private var probed = false

    @Volatile
    private var rootAvailable = false

    /** True once `su -c id` succeeds (cached after the first probe). */
    fun isAvailable(): Boolean {
        if (probed) return rootAvailable
        synchronized(this) {
            if (probed) return rootAvailable
            rootAvailable = probe()
            probed = true
            Log.i(TAG, "root shell ${if (rootAvailable) "available" else "not available"}")
            return rootAvailable
        }
    }

    /** Run a command as root; null when root is unavailable or the call fails. */
    fun runCommand(cmd: String): String? {
        if (!isAvailable()) return null
        return try {
            val process = ProcessBuilder("/system/bin/su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            val ok = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
            if (ok && output.isNotBlank()) output else null
        } catch (e: Exception) {
            Log.w(TAG, "su run failed: ${e.message}")
            null
        }
    }

    /** Read a root-gated file; null when unavailable or unreadable. */
    fun readFile(path: String): String? = runCommand("cat $path")

    /** Force re-probe (used after toggling root state in tests). */
    fun invalidate() {
        probed = false
        rootAvailable = false
    }

    private fun probe(): Boolean {
        return try {
            val process = ProcessBuilder("/system/bin/su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val ok = process.waitFor(5, TimeUnit.SECONDS)
            ok && process.exitValue() == 0 && out.contains("uid=0") &&
                File("/system/bin/su").canExecute()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "RootShellBridge"
    }
}
