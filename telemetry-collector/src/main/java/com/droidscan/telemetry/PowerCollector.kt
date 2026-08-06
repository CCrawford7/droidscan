package com.droidscan.telemetry

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.droidscan.shared.core.PowerSnapshot

class PowerCollector {
    private var bm: BatteryManager? = null
    private var pm: PowerManager? = null
    private var ctx: Context? = null

    fun attach(batteryManager: BatteryManager, powerManager: PowerManager, context: Context? = null) {
        bm = batteryManager
        pm = powerManager
        ctx = context
    }

    fun read(): PowerSnapshot {
        val manager = bm ?: return PowerSnapshot()

        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargeRate = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toInt()

        val (health, tempC, isCharging) = ctx?.let { readBatteryIntent(it) }
            ?: Triple(null, null, false)

        val timeRemaining = if (isCharging) {
            try { manager.computeChargeTimeRemaining() } catch (_: Exception) { null }
        } else null

        // Rolling average draw + remaining charge counter (µAh). Both return a
        // sentinel (Integer/Long.MIN_VALUE) when unsupported, so guard on sane
        // magnitudes as well.
        val currentAverage = try {
            manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                .takeIf { it in -20_000_000L..20_000_000L }
                ?.toInt()
        } catch (_: Exception) { null }
        val chargeCounter = try {
            manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                .takeIf { it in 0L..30_000_000L }  // 0..30 Ah upper bound
        } catch (_: Exception) { null }

        // Detect eSIM presence via subscription manager
        val esimActive = ctx?.let {
            try {
                val subMgr = it.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                subMgr?.activeSubscriptionInfoList?.any { sub -> sub.isEmbedded } ?: false
            } catch (_: Exception) { false }
        } ?: false

        // Detect Bluetooth active
        val btActive = try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            bt?.isEnabled ?: false
        } catch (_: Exception) { false }

        // Detect hotspot active
        val hotspotActive = ctx?.let {
            try {
                val wm = it.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val method = wm?.javaClass?.getMethod("isWifiApEnabled")
                method?.invoke(wm) as? Boolean ?: false
            } catch (_: Exception) { false }
        } ?: false

        return PowerSnapshot(
            batteryLevel = if (level in 0..100) level else null,
            batteryHealth = health,
            batteryTemperature = tempC,
            chargeRateMicroamps = chargeRate,
            estimatedTimeRemainingMs = timeRemaining,
            currentDrainMicroamps = if (!isCharging && chargeRate < 0) -chargeRate else null,
            batteryCurrentAverageMicroamps = currentAverage,
            batteryChargeCounterMicroAh = chargeCounter,
            perProcessPower = emptyMap(),
            esimActive = esimActive,
            bluetoothActive = btActive,
            hotspotActive = hotspotActive
        )
    }

    private fun readBatteryIntent(context: Context): Triple<String?, Float?, Boolean> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val health = mapHealth(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1)
            val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            Triple(health, temp, charging)
        } catch (e: Exception) {
            Log.w("PowerCollector", "Failed to read battery intent: ${e.message}")
            Triple(null, null, false)
        }
    }

    private fun mapHealth(healthCode: Int): String = when (healthCode) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "overvoltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        else -> "unknown"
    }
}
