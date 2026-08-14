package com.droidscan

import android.app.Application
import com.droidscan.telemetry.*

class DroidscanApp : Application() {

    lateinit var telemetryCollector: TelemetryCollector
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Wire up collectors with system services
        val radioCollector = RadioCollector().also {
            val tm = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            it.attach(tm)
            it.attachContext(this)
        }

        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager

        val thermalCollector = ThermalCollector().also {
            it.attach(powerManager)
        }

        val powerCollector = PowerCollector().also {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            it.attach(bm, powerManager, this)
        }

        val processCollector = ProcessCollector().also {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            it.attach(am)
        }

        val networkCollector = NetworkConfigCollector().also {
            it.attach(this)
        }

        val simCollector = SimStatusCollector().also {
            it.attach(this)
        }

        val systemCollector = SystemInfoCollector().also {
            val wm = getSystemService(WINDOW_SERVICE) as? android.view.WindowManager
            it.attach(wm)
        }

        val gnssCollector = GnssCollector().also {
            it.attach(this)
        }

        val imsCollector = ImsCollector().also {
            it.attach(this)
        }

        // T2: Shizuku bridge. initialize() binds the UserService lifecycle and
        // requests permission when the server is running; collectors consult
        // isAvailable()/runCommand() to fetch shell-privileged telemetry.
        val shizukuDetector = ShizukuDetector().apply {
            initialize(this@DroidscanApp)
        }

        // File-backed metric history (P1): powers trend charts + the future
        // diagnostic engine. Bounded to ~1h of 1s snapshots.
        val historyStore = com.droidscan.shared.core.MetricHistoryStore(
            file = java.io.File(filesDir, "metrics_history.jsonl")
        )

        telemetryCollector = TelemetryCollector(
            radioCollector = radioCollector,
            thermalCollector = thermalCollector,
            storageCollector = StorageCollector(),
            powerCollector = powerCollector,
            processCollector = processCollector,
            networkCollector = networkCollector,
            simCollector = simCollector,
            systemCollector = systemCollector,
            gnssCollector = gnssCollector,
            imsCollector = imsCollector,
            shizukuDetector = shizukuDetector,
            historyStore = historyStore
        )
    }

    companion object {
        lateinit var instance: DroidscanApp
            private set
    }
}
