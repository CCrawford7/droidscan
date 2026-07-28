package com.droidscan.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
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

        // Health, temperature, and charging status from sticky intent
        val (health, tempC, isCharging) = ctx?.let { readBatteryIntent(it) }
            ?: Triple(null, null, false)

        val timeRemaining = if (isCharging) {
            manager.computeChargeTimeRemaining()
        } else null

        return PowerSnapshot(
            batteryLevel = if (level in 0..100) level else null,
            batteryHealth = health,
            batteryTemperature = tempC,
            chargeRateMicroamps = chargeRate,
            estimatedTimeRemainingMs = timeRemaining,
            currentDrainMicroamps = if (!isCharging && chargeRate < 0) -chargeRate else null,
            perProcessPower = emptyMap(), // requires PowerProfile access
            esimActive = false,           // determined by sim status
            bluetoothActive = false,      // determined by BT adapter state
            hotspotActive = false          // determined by network config
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
        } catch (_: Exception) {
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
