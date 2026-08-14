package com.droidscan.telemetry

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService — the elevated half of the T2 bridge.
 *
 * Shizuku instantiates this class in an `app_process` running as **UID 2000**
 * (adb shell), so its methods perform privileged reads on our behalf. The class
 * is NOT an Android [android.app.Service] — Shizuku's starter loads it directly
 * from the APK. Required surface per the Shizuku server contract:
 *
 *  - public no-arg constructor (instantiation);
 *  - optional `@Keep` constructor taking [Context] (API 13+, recommended);
 *  - [destroy] wired to the reserved AIDL transaction so the server can stop us.
 *
 * @see <a href="https://github.com/RikkaApps/Shizuku-API/blob/master/demo/src/main/java/rikka/shizuku/demo/service/UserService.java">reference UserService</a>
 */
@Keep
class ShizukuTelemetryService @Keep constructor() : IShizukuTelemetry.Stub() {

    // Public no-arg primary constructor above is what Shizuku's starter calls for
    // instantiation. The @Keep Context constructor matches the API 13 contract
    // (starter calls it with createPackageContextAsUser when available).
    @Keep
    constructor(context: Context) : this() {
        Log.i(TAG, "constructed with context ${context.packageName}")
    }

    override fun destroy() {
        Log.i(TAG, "destroy requested — exiting")
        System.exit(0)
    }

    override fun runCommand(cmd: String): String {
        return try {
            Log.i(TAG, "runCommand: $cmd")
            val process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            process.waitFor(10, TimeUnit.SECONDS)
            output.trim()
        } catch (e: Exception) {
            Log.w(TAG, "runCommand failed: ${e.message}")
            ""
        }
    }

    override fun readFile(path: String): String {
        return try {
            File(path).readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "ShizukuTelemetrySvc"
    }
}
