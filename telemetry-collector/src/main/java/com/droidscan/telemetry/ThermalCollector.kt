package com.droidscan.telemetry

import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.droidscan.shared.core.ThermalSnapshot
import com.droidscan.shared.core.ThermalZone
import java.io.File

class ThermalCollector {
    private var pm: PowerManager? = null

    fun attach(powerManager: PowerManager) {
        pm = powerManager
    }

    fun read(shizukuAvailable: Boolean): ThermalSnapshot {
        // Standard Mode: use PowerManager thermal API (API 30+)
        val standardZones = if (Build.VERSION.SDK_INT >= 30) {
            pm?.currentThermalStatus?.let { status ->
                listOf(
                    ThermalZone(
                        name = "skin",
                        temperatureCelsius = mapThermalStatusToTemp(status),
                        throttling = status >= PowerManager.THERMAL_STATUS_MODERATE
                    )
                )
            } ?: emptyList()
        } else emptyList()

        // Power Mode: kernel thermal zones
        // These are read via ShizukuShell in production
        val powerZones = if (shizukuAvailable) {
            readKernelThermalZones()
        } else emptyList()

        val allZones = if (powerZones.isNotEmpty()) powerZones else standardZones
        val maxTemp = allZones.maxOfOrNull { it.temperatureCelsius }
        val isThrottling = allZones.any { it.throttling }

        return ThermalSnapshot(
            zones = allZones,
            isThrottling = isThrottling,
            maxTemperature = maxTemp
        )
    }

    private fun readKernelThermalZones(): List<ThermalZone> {
        val zones = mutableListOf<ThermalZone>()
        try {
            val thermalDir = File("/sys/class/thermal")
            val dirs = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }
            dirs?.forEach { dir ->
                val typeFile = File(dir, "type")
                val tempFile = File(dir, "temp")
                if (typeFile.exists() && tempFile.exists()) {
                    val type = typeFile.readText().trim()
                    val rawTemp = tempFile.readText().trim().toIntOrNull() ?: 0
                    val tempCelsius = rawTemp / 1000.0f
                    zones.add(ThermalZone(
                        name = type,
                        temperatureCelsius = tempCelsius,
                        throttling = tempCelsius > 55
                    ))
                }
            }
        } catch (e: Exception) {
            // Cannot read kernel zones without Shizuku — but log WHY so Power Mode
            // failures aren't invisible.
            Log.w("ThermalCollector", "Failed to read /sys/class/thermal zones: ${e.message}")
        }
        return zones
    }

    private fun mapThermalStatusToTemp(status: Int): Float = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> 30.0f
        PowerManager.THERMAL_STATUS_LIGHT -> 38.0f
        PowerManager.THERMAL_STATUS_MODERATE -> 45.0f
        PowerManager.THERMAL_STATUS_SEVERE -> 55.0f
        PowerManager.THERMAL_STATUS_CRITICAL -> 65.0f
        PowerManager.THERMAL_STATUS_EMERGENCY -> 75.0f
        else -> 30.0f
    }

    companion object {
        const val THERMAL_CRITICAL_THRESHOLD = 55f
    }
}
