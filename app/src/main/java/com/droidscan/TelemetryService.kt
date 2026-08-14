package com.droidscan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.droidscan.shared.core.MetricSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service (slice #3) that keeps the telemetry loop alive when the
 * activity is backgrounded or the screen is off.
 *
 * OnePlus and other OEMs aggressively kill backgrounded apps; a foreground
 * service with a visible notification is the only sanctioned way to keep a
 * long-running collector going. The service:
 *
 *  - runs [TelemetryCollector.observeAll] on [Dispatchers.IO];
 *  - surfaces a live summary notification (throttled to [NOTIFY_INTERVAL_MS]);
 *  - is [START_STICKY] so the system relaunches it after a kill (subject to
 *    OEM battery policy — see the Settings "battery exemption" button).
 *
 * The service is idempotent: multiple startService calls collapse onto one
 * running instance, and [TelemetryService.Companion.start]/[stop] are safe to
 * call from anywhere.
 */
class TelemetryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        startCollecting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: relaunch with a null intent if the system kills us.
        if (collectorJob?.isActive != true) startCollecting()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        ensureChannel()
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCollecting() {
        collectorJob = scope.launch {
            try {
                val collector = DroidscanApp.instance.telemetryCollector
                var lastNotify = 0L
                collector.observeAll().collectLatest { snapshot ->
                    val now = System.currentTimeMillis()
                    if (now - lastNotify >= NOTIFY_INTERVAL_MS) {
                        lastNotify = now
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIFICATION_ID, buildNotification(snapshot))
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "collector stopped: ${t.message}")
            }
        }
    }

    private fun buildNotification(s: MetricSnapshot?): Notification {
        val title = if (s == null) "droidscan collecting…" else telemetrySummary(s)
        val text = when {
            s == null -> "Starting telemetry collection"
            else -> "RAM ${fmt(s.processes.usedMemoryBytes)} | ${s.power.batteryLevel ?: "--"}% | ${s.thermal.maxTemperature?.let { "${"%.1f".format(it)}°C" } ?: "--"}"
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // system icon; app icon is not a monochrome drawable
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps signal/power/thermal monitoring running in the background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun telemetrySummary(s: MetricSnapshot): String {
        val parts = mutableListOf<String>()
        s.radio.rsrp?.let { parts += "RSRP ${it} dBm" }
        s.radio.sinr?.let { parts += "SINR ${it} dB" }
        s.power.batteryLevel?.let { parts += "${it}%" }
        s.thermal.maxTemperature?.let { parts += "${"%.1f".format(it)}°C" }
        if (parts.isEmpty()) return "droidscan collecting…"
        return parts.joinToString(" · ")
    }

    companion object {
        private const val TAG = "TelemetryService"
        private const val CHANNEL_ID = "telemetry_live"
        private const val NOTIFICATION_ID = 0xD50C5A
        private const val NOTIFY_INTERVAL_MS = 10_000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, TelemetryService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TelemetryService::class.java))
        }

        /** Whether the foreground service is currently running. */
        fun isRunning(context: Context): Boolean {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.getRunningServices(100).any { it.service.className == TelemetryService::class.java.name }
            } catch (_: Exception) {
                false
            }
        }
    }
}

private fun fmt(bytes: Long?): String {
    val b = bytes ?: return "--"
    return when {
        b >= 1L shl 30 -> String.format("%.1fG", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format("%.0fM", b / (1L shl 20).toDouble())
        else -> "$b"
    }
}
