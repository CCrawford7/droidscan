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

        val shizukuDetector = ShizukuDetector().apply {
            initialize()
        }

        telemetryCollector = TelemetryCollector(
            radioCollector = radioCollector,
            thermalCollector = thermalCollector,
            storageCollector = StorageCollector(),
            powerCollector = powerCollector,
            processCollector = processCollector,
            networkCollector = networkCollector,
            simCollector = simCollector,
            systemCollector = SystemInfoCollector(),
            shizukuDetector = shizukuDetector
        )
    }

    companion object {
        lateinit var instance: DroidscanApp
            private set
    }
}
