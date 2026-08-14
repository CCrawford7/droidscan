package com.droidscan.telemetry

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Client-side bridge to the Shizuku UserService (T2).
 *
 * Owns the full Shizuku lifecycle for droidscan:
 *
 *  1. Watches for the Shizuku binder (server running via root or adb shell).
 *  2. Requests the runtime permission on first contact.
 *  3. Binds [ShizukuTelemetryService] once permission is granted.
 *  4. Exposes [runCommand]/[readFile] for shell-privileged telemetry.
 *
 * The whole Shizuku dependency is optional — if the server isn't running or
 * permission is denied, [isAvailable] stays false and all collectors degrade
 * to Standard Mode (the existing behavior). Nothing here crashes without
 * Shizuku present; the `rikka.shizuku.*` classes arrive at runtime via the
 * app's `shizuku-provider` dependency.
 */
class ShizukuBridge {

    @Volatile
    private var telemetryService: IShizukuTelemetry? = null

    @Volatile
    private var permissionGranted = false

    private var appContext: Context? = null

    private val userServiceComponentName: ComponentName? by lazy {
        appContext?.let { ComponentName(it, ShizukuTelemetryService::class.java) }
    }

    private val userServiceArgs by lazy {
        val componentName = userServiceComponentName ?: return@lazy null
        Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .processNameSuffix("shizuku")
            .debuggable(true)
            .version(SERVICE_VERSION)
    }

    /** True when the elevated service is bound AND permission is granted. */
    fun isAvailable(): Boolean = permissionGranted && telemetryService != null

    /** Test/demo override — forces availability without touching the binder. */
    fun setAvailableOverride(available: Boolean) {
        permissionGranted = available
        if (!available) telemetryService = null
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        try {
            if (Shizuku.isPreV11()) {
                Log.i(TAG, "Shizuku pre-v11 not supported — Standard Mode")
                return
            }
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            onBinderReceived()
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku init failed: ${e.message}")
        }
    }

    /** Called when the Shizuku binder is (re)available. */
    private fun onBinderReceived() {
        try {
            if (!Shizuku.pingBinder()) {
                Log.i(TAG, "Shizuku binder not present — Standard Mode")
                return
            }
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    permissionGranted = true
                    bind()
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    Log.i(TAG, "Shizuku permission denied previously — Standard Mode")
                }
                else -> {
                    Log.i(TAG, "Requesting Shizuku permission")
                    Shizuku.requestPermission(REQUEST_CODE)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku permission check failed: ${e.message}")
        }
    }

    private fun bind() {
        val args = userServiceArgs ?: return
        val componentName = userServiceComponentName
        try {
            Log.i(TAG, "Binding UserService ${componentName?.shortClassName ?: "(unknown)"}")
            Shizuku.bindUserService(args, connection)
        } catch (e: Throwable) {
            Log.w(TAG, "bindUserService failed: ${e.message}")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onBinderReceived()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.i(TAG, "Shizuku binder dead — Standard Mode")
        telemetryService = null
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Shizuku permission granted — Power Mode")
            permissionGranted = true
            bind()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "UserService connected: $name")
            telemetryService = IShizukuTelemetry.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "UserService disconnected: $name")
            telemetryService = null
        }
    }

    /** Shell-privileged command; null when Power Mode is unavailable. */
    fun runCommand(cmd: String): String? {
        val service = telemetryService ?: return null
        return try {
            service.runCommand(cmd).takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            Log.w(TAG, "runCommand failed: ${e.message}")
            null
        }
    }

    /** Shell-privileged file read; null when Power Mode is unavailable. */
    fun readFile(path: String): String? {
        val service = telemetryService ?: return null
        return try {
            service.readFile(path).takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            Log.w(TAG, "readFile failed: ${e.message}")
            null
        }
    }

    fun shutdown() {
        val args = userServiceArgs ?: return
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Throwable) {
        }
        telemetryService = null
    }

    companion object {
        private const val TAG = "ShizukuBridge"
        const val REQUEST_CODE = 13491
        private const val SERVICE_VERSION = 1
    }
}
