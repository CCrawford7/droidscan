package com.droidscan.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.droidscan.DroidscanApp
import com.droidscan.TelemetryService
import com.droidscan.shared.core.*
import com.droidscan.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

// ─── Power ───────────────────────────────────────────────

@Composable
fun PowerTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    trendHistory: List<MetricSnapshot>,
    onTap: (String) -> Unit
) {
    val proFeatures by DroidscanApp.instance.proFeaturesEnabled.collectAsState()
    val isThrottling = snapshot.thermal.isThrottling == true
    var showRawSensors by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // CrisisBanner — top of Power only.
        if (isThrottling) {
            CrisisBanner(snapshot)
        }

        TierCard("BATTERY", liveReadout = snapshot.power.batteryLevel?.let { "$it%" }, liveColor = TerminalAccentCyan) {
            val level = snapshot.power.batteryLevel
            MetricRow("Level", MetricValue(level?.let { "$it%" } ?: UNKNOWN_VALUE, level?.toDouble(), BatteryThresholds.levelPercent, inverted = true), "batteryLevel", onTap)
            BarGauge("Charge", level?.toLong(), 100L, warnThreshold = 0.2, critThreshold = 0.05)
            DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: UNKNOWN_VALUE), "Temp", MetricValue(formatCelsius(snapshot.power.batteryTemperature), snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius), "batteryHealth", null, onTap)
            val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000)
            val drainMa = snapshot.power.currentDrainMicroamps?.div(1000)
            val avgMa = snapshot.power.batteryCurrentAverageMicroamps?.div(1000)
            if (chargeMa != null && chargeMa > 0) MetricRow("Charge Rate", MetricValue("${chargeMa} mA"), "chargeRate", onTap)
            if (drainMa != null && drainMa > 0) MetricRow("Discharge Rate", MetricValue("${drainMa} mA"))
            if (avgMa != null && avgMa != 0) MetricRow("Avg Current", MetricValue("${avgMa} mA"))
            snapshot.power.batteryChargeCounterMicroAh?.let { MetricRow("Charge Counter", MetricValue("${it / 1000} mAh")) }
            snapshot.power.estimatedTimeRemainingMs?.let { MetricRow("Est. Remaining", MetricValue(formatUptime(it))) }
        }

        TrendCard(
            "BATTERY TREND", trendBattery(trendHistory), "%", TerminalAccentGreen,
            windowLabel = "30 min", minY = 0f, maxY = 100f,
            formatValue = { v -> "${v.toInt()}%" }
        )

        TierCard("RADIO / SENSORS") {
            DualMetricRow("Bluetooth", MetricValue(if (snapshot.power.bluetoothActive) "ON" else "OFF"), "eSIM", MetricValue(if (snapshot.power.esimActive) "YES" else "NO"))
            DualMetricRow("Hotspot", MetricValue(if (snapshot.power.hotspotActive) "ON" else "OFF"), "WiFi Call", MetricValue(snapshot.radio.wifiCallingStatus ?: "--"))
        }

        TierCard("THERMAL", liveReadout = snapshot.thermal.maxTemperature?.let { String.format("%.1f", it) + "°C" }, liveColor = if ((snapshot.thermal.maxTemperature ?: 0f) > 70) TerminalAccentRed else if ((snapshot.thermal.maxTemperature ?: 0f) > 50) TerminalAccentOrange else TerminalTextSecondary) {
            val zones = snapshot.thermal.zones
            if (zones.isEmpty()) {
                MetricRow("No sensors", MetricValue("--"))
            } else {
                val cpuZones = zones.filter { it.name.startsWith("cpu") }
                val gpuZones = zones.filter { it.name.startsWith("gpu") || it.name.startsWith("gpuss") }
                val batteryZones = zones.filter { it.name.contains("battery", ignoreCase = true) || it.name.startsWith("pmxr2230") }
                val chassisZones = zones.filter { it.name.startsWith("sys-therm") || it.name.startsWith("shell") || it.name.startsWith("aoss") }
                val modemZones = zones.filter { it.name.startsWith("mdmss") || it.name.startsWith("sub") }

                if (cpuZones.isNotEmpty()) {
                    val peak = cpuZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = cpuZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = cpuZones.any { it.throttling }
                    ThermalSummaryRow("CPU Cores", avg, peak, throttling)
                }
                if (gpuZones.isNotEmpty()) {
                    val peak = gpuZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = gpuZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = gpuZones.any { it.throttling }
                    ThermalSummaryRow("GPU", avg, peak, throttling)
                }
                if (batteryZones.isNotEmpty()) {
                    val peak = batteryZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = batteryZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = batteryZones.any { it.throttling }
                    if (peak > -200.0) ThermalSummaryRow("Battery/Power", avg, peak, throttling)
                }
                if (chassisZones.isNotEmpty()) {
                    val peak = chassisZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = chassisZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = chassisZones.any { it.throttling }
                    if (peak > -200.0) ThermalSummaryRow("Chassis", avg, peak, throttling)
                }
                if (modemZones.isNotEmpty()) {
                    val peaks = modemZones.filter { it.temperatureCelsius > -200f }.map { it.temperatureCelsius.toDouble() }
                    if (peaks.isNotEmpty()) {
                        val peak = peaks.max()
                        val avg = peaks.average()
                        ThermalSummaryRow("Modem/RF", avg, peak, modemZones.any { it.throttling })
                    }
                }

                Spacer(Modifier.height(2.dp))
                TierDivider()

                // Peak overall (emphasized last row, hairline above).
                snapshot.thermal.maxTemperature?.let { maxFloat ->
                    val max = maxFloat.toDouble()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Peak overall: ", color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
                        Text(
                            "${String.format("%.1f", max)}°C",
                            color = if (max > 70) TerminalAccentRed else if (max > 50) TerminalAccentOrange else TerminalTextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isThrottling) {
                            Spacer(Modifier.width(8.dp))
                            Chip("THROTTLING", TerminalAccentRed)
                        }
                    }
                }

                // Raw kernel sensors — only meaningful with Shizuku (Power Mode).
                if (proFeatures && snapshot.shizukuAvailable) {
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = { showRawSensors = !showRawSensors },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (showRawSensors) "▲ Hide raw sensors" else "▼ Show all ${zones.size} raw sensors",
                            color = TerminalAccentBlue,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                    if (showRawSensors) {
                        zones.forEach { zone ->
                            MetricRow(
                                zone.name,
                                MetricValue(
                                    "${String.format("%.1f", zone.temperatureCelsius)}°C",
                                    zone.temperatureCelsius.toDouble(),
                                    ThermalThresholds.zoneTemp
                                ),
                                "temperature",
                                onTap
                            )
                        }
                    }
                }
            }
        }

        TrendCard(
            "THERMAL TREND", trendThermal(trendHistory), "°C", TerminalAccentOrange,
            windowLabel = "30 min"
        )

        // MODE tier — hidden in Standard Mode (Pro features OFF); reveals with
        // its existing behaviour so we can still test availability states.
        if (proFeatures) {
            TierCard(if (snapshot.shizukuAvailable) "POWER MODE (Shizuku)" else "MODE") {
                if (snapshot.shizukuAvailable) {
                    MetricRow("Status", MetricValue("Available — kernel-level data"))
                    snapshot.processes.kernelWakelocks.takeIf { it.isNotEmpty() }?.let {
                        MetricRow("Wakelocks", MetricValue("${it.size} active"))
                    }
                } else {
                    MetricRow("Status", MetricValue("Standard Mode — no shell data"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shizuku enables:", color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Chip("Wake locks", TerminalAccentCyan)
                        Spacer(Modifier.width(4.dp))
                        Chip("Storage wear", TerminalAccentCyan)
                    }
                }
            }
        }

        // TOP PROCESSES — from ActivityManager, available even without Shizuku.
        TierCard("TOP PROCESSES") {
            val top = snapshot.processes.topMemoryProcesses.take(5)
            if (top.isEmpty()) {
                MetricRow("Status", MetricValue("No process data"))
            } else {
                top.forEach { p ->
                    MetricRow(
                        "${p.name.take(24)}",
                        MetricValue(p.memoryBytes?.let { String.format("%.1f", it / 1_000_000.0) + " MB" } ?: UNKNOWN_VALUE),
                        indent = true
                    )
                }
            }
        }
    }
}

@Composable
fun CrisisBanner(snapshot: MetricSnapshot) {
    val max = snapshot.thermal.maxTemperature
    val zone = snapshot.thermal.peakZoneName
    val subtitle = when {
        max != null && zone != null -> "Peak ${String.format("%.1f", max)}°C — $zone"
        max != null -> "Peak ${String.format("%.1f", max)}°C"
        else -> "CPU cores hottest"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(TerminalAccentRed.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .background(TerminalAccentRed.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚠", color = TerminalAccentRed, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
        Column {
            Text("Thermal throttling active", color = TerminalAccentRed, fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 10.5.sp)
        }
    }
}

@Composable
fun ThermalSummaryRow(label: String, avg: Double, peak: Double, throttling: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TerminalTextPrimary, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (throttling) {
                Spacer(Modifier.width(6.dp))
                Text("⚠", fontSize = 11.sp, color = TerminalAccentRed)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val peakColor = when {
                peak > 80 -> TerminalAccentRed
                peak > 60 -> TerminalAccentOrange
                else -> TerminalTextSecondary
            }
            Text(
                "${String.format("%.1f", avg)}°C avg",
                color = TerminalTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${String.format("%.1f", peak)}°C peak",
                color = peakColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

