package com.droidscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droidscan.shared.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

@Composable
fun DashboardScreen(telemetryFlow: Flow<MetricSnapshot>) {
    val scrollState = rememberScrollState()
    var currentSnapshot by remember { mutableStateOf(MetricSnapshot()) }
    val context = LocalContext.current
    val glossary = remember { loadGlossary(context) }
    var tappedMetricId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(telemetryFlow) {
        telemetryFlow.collectLatest { snapshot ->
            currentSnapshot = snapshot
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                DashboardContent(
                    snapshot = currentSnapshot,
                    glossary = glossary,
                    onMetricTap = { tappedMetricId = it }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                DashboardContent(
                    snapshot = currentSnapshot,
                    glossary = glossary,
                    onMetricTap = { tappedMetricId = it }
                )
            }
        }
    }

    // Tooltip dialog
    tappedMetricId?.let { metricId ->
        val entry = glossary.find { it.id == metricId }
        if (entry != null) {
            AlertDialog(
                onDismissRequest = { tappedMetricId = null },
                title = { Text(entry.label, color = Color(0xFF58A6FF)) },
                text = {
                    Column {
                        Text("What it measures:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E))
                        Text(entry.whatItMeasures, color = Color(0xFFC9D1D9))
                        Spacer(Modifier.height(8.dp))
                        Text("Current value context:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E))
                        Text(entry.currentValueContext, color = Color(0xFFC9D1D9))
                        Spacer(Modifier.height(8.dp))
                        Text("Healthy range:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E))
                        Text(entry.healthyRange, color = Color(0xFFC9D1D9))
                        Spacer(Modifier.height(8.dp))
                        Text("Common causes:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E))
                        Text(entry.commonCauses, color = Color(0xFFC9D1D9))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { tappedMetricId = null }) {
                        Text("Close")
                    }
                },
                containerColor = Color(0xFF161B22),
                titleContentColor = Color(0xFF58A6FF)
            )
        }
    }
}

@Composable
fun DashboardContent(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    onMetricTap: (String) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        // Header
        Text(
            "droidscan v1.0  —  ${snapshot.systemInfo.deviceModel ?: "Android"}",
            color = Color(0xFF58A6FF),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            "Uptime: ${formatUptime(snapshot.systemInfo.uptimeMs)}  |  ${snapshot.systemInfo.androidVersion ?: ""}",
            color = Color(0xFF8B949E),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))

        // ── Radio ──
        SectionHeader("RADIO / CELLULAR")
        MetricRow("RSRP", "${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp, "rsrp", glossary, onMetricTap)
        MetricRow("RSRQ", "${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq, "rsrq", glossary, onMetricTap)
        MetricRow("SINR", "${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr, "sinr", glossary, onMetricTap)
        MetricRow("Band", snapshot.radio.activeBands.joinToString(", ").ifEmpty { "--" }, null, null, "activeBands", glossary, onMetricTap)
        MetricRow("IMS", if (snapshot.radio.imsRegistered == true) "REGISTERED" else if (snapshot.radio.imsRegistered == false) "NOT REGISTERED" else "--", null, null, "imsRegistered", glossary, onMetricTap)
        MetricRow("Network", snapshot.radio.dataNetworkType ?: "--", null, null, "dataNetworkType", glossary, onMetricTap)
        MetricRow("Roaming", if (snapshot.radio.isRoaming == true) "YES" else "NO", null, null, "isRoaming", glossary, onMetricTap)
        MetricRow("Operator", snapshot.radio.operatorName ?: "--", null, null, null, glossary, onMetricTap)
        SparklineRow("RSRP Trend", snapshot.radio.rsrp?.toFloat(), null)

        // ── SIM ──
        SectionHeader("SIM / eSIM")
        snapshot.simStatus.slots.forEachIndexed { i, slot ->
            MetricRow("SIM ${i + 1}", slot.carrierName ?: "--", null, null, "simProvisioning", glossary, onMetricTap)
            MetricRow("  eSIM Profiles", "${slot.esimProfileCount}", null, null, "esimProfiles", glossary, onMetricTap)
            MetricRow("  Provisioned", if (slot.isProvisioned) "YES" else "NO", null, null, "simProvisioning", glossary, onMetricTap)
        }
        if (snapshot.simStatus.slots.isEmpty()) {
            MetricRow("SIM", "No SIM detected", null, null, "simProvisioning", glossary, onMetricTap)
        }

        // ── Network ──
        SectionHeader("NETWORK CONFIG")
        MetricRow("Type", snapshot.networkConfig.activeNetworkType ?: "--", null, null, "activeNetwork", glossary, onMetricTap)
        MetricRow("IPv4", snapshot.networkConfig.ipv4Addresses.firstOrNull() ?: "--", null, null, "ipAddress", glossary, onMetricTap)
        MetricRow("VPN", if (snapshot.networkConfig.vpnActive) "${snapshot.networkConfig.vpnLabel ?: "Active"}" else "Inactive", null, null, null, glossary, onMetricTap)
        MetricRow("Hotspot", if (snapshot.networkConfig.hotspotActive) "ACTIVE" else "OFF", null, null, null, glossary, onMetricTap)
        MetricRow("APN", snapshot.networkConfig.apnName ?: "--", null, null, null, glossary, onMetricTap)

        // ── Storage ──
        SectionHeader("STORAGE")
        val usedGb = snapshot.storage.internalUsedBytes?.div(1_000_000_000.0)
        val freeGb = snapshot.storage.internalFreeBytes?.div(1_000_000_000.0)
        val totalGb = usedGb?.plus(freeGb ?: 0.0)
        val freePercent = if (totalGb != null && totalGb > 0) (freeGb ?: 0.0) / totalGb * 100 else null
        MetricRow("Internal", "${String.format("%.1f", usedGb ?: 0.0)}G / ${String.format("%.1f", totalGb ?: 0.0)}G used", freePercent, StorageThresholds.freePercent, "storageUsed", glossary, onMetricTap)
        MetricRow("External", if (snapshot.storage.externalMounted) "${String.format("%.1f", snapshot.storage.externalFreeBytes?.div(1_000_000_000.0) ?: 0.0)}G free" else "Not mounted", null, null, null, glossary, onMetricTap)
        snapshot.storage.topConsumers.forEach { c ->
            MetricRow("  ${c.label.take(15)}", "${String.format("%.1f", c.sizeBytes / 1_000_000.0)} MB", null, null, null, glossary, onMetricTap)
        }

        // ── Thermal ──
        SectionHeader("THERMAL")
        snapshot.thermal.zones.forEach { zone ->
            MetricRow(zone.name, "${String.format("%.1f", zone.temperatureCelsius)}°C", zone.temperatureCelsius.toDouble(), ThermalThresholds.zoneTemp, "temperature", glossary, onMetricTap)
        }
        MetricRow("Throttling", if (snapshot.thermal.isThrottling == true) "ACTIVE" else "Inactive", null, null, "throttling", glossary, onMetricTap)
        if (snapshot.shizukuAvailable) MetricRow("  (Power Mode)", "Kernel-level zones", null, null, null, glossary, onMetricTap)

        // ── Power ──
        SectionHeader("POWER / BATTERY")
        MetricRow("Level", "${snapshot.power.batteryLevel ?: "--"}%", snapshot.power.batteryLevel?.toDouble(), BatteryThresholds.levelPercent, "batteryLevel", glossary, onMetricTap)
        MetricRow("Health", snapshot.power.batteryHealth ?: "--", null, null, "batteryHealth", glossary, onMetricTap)
        MetricRow("Temp", "${String.format("%.1f", snapshot.power.batteryTemperature ?: 0.0)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius, null, glossary, onMetricTap)
        val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
        MetricRow("Charge", "${chargeMa} mA", null, null, "chargeRate", glossary, onMetricTap)
        if (snapshot.power.esimActive) MetricRow("  eSIM active", "See power overhead", null, null, null, glossary, onMetricTap)
        if (snapshot.power.bluetoothActive) MetricRow("  Bluetooth active", "See power overhead", null, null, null, glossary, onMetricTap)
        if (snapshot.power.hotspotActive) MetricRow("  Hotspot active", "See power overhead", null, null, "hotspotOverhead", glossary, onMetricTap)

        // ── Processes ──
        SectionHeader("PROCESSES / CPU / MEMORY")
        snapshot.processes.perCoreLoad.forEachIndexed { i, load ->
            MetricRow("Core $i", "${(load * 100).toInt()}%", load.toDouble(), MemoryThresholds.pressure, "cpuLoad", glossary, onMetricTap)
        }
        MetricRow("RAM", "${String.format("%.1f", snapshot.processes.usedMemoryBytes?.div(1_000_000_000.0) ?: 0.0)}G / ${String.format("%.1f", snapshot.processes.totalMemoryBytes?.div(1_000_000_000.0) ?: 0.0)}G", null, null, "memoryPressure", glossary, onMetricTap)
        MetricRow("Pressure", snapshot.processes.memoryPressure.name, null, null, "memoryPressure", glossary, onMetricTap)
        snapshot.processes.topMemoryProcesses.forEach { p ->
            val mb = p.memoryBytes?.div(1_000_000.0) ?: 0.0
            MetricRow("  ${p.name.take(20)}", "${String.format("%.1f", mb)} MB", null, null, null, glossary, onMetricTap)
        }
        if (snapshot.processes.kernelWakelocks.isNotEmpty()) {
            MetricRow("Wakelocks (PM)", "${snapshot.processes.kernelWakelocks.size} active", null, null, "wakelocks", glossary, onMetricTap)
        }

        // ── System ──
        SectionHeader("SYSTEM OVERVIEW")
        MetricRow("OS", "Android ${snapshot.systemInfo.androidVersion ?: "--"}", null, null, null, glossary, onMetricTap)
        MetricRow("Build", snapshot.systemInfo.buildNumber?.take(30) ?: "--", null, null, null, glossary, onMetricTap)
        MetricRow("Security", snapshot.systemInfo.securityPatch ?: "--", null, null, null, glossary, onMetricTap)
        MetricRow("Kernel", snapshot.systemInfo.kernelVersion ?: "--", null, null, "kernelVersion", glossary, onMetricTap)
        MetricRow("SELinux", if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else if (snapshot.systemInfo.selinuxEnforced == false) "Permissive" else "--", null, null, "selinuxStatus", glossary, onMetricTap)
        MetricRow("Uptime", formatUptime(snapshot.systemInfo.uptimeMs), null, null, "uptime", glossary, onMetricTap)

        // Shizuku status
        SectionHeader("SHIZUKU")
        MetricRow("Status", if (snapshot.shizukuAvailable) "Available — Power Mode active" else "Not available — Standard Mode", null, null, null, glossary, onMetricTap)
    }
}

// ── UI Components ──

@Composable
fun SectionHeader(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        "── ${title} ──",
        color = Color(0xFF58A6FF),
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    numericValue: Double?,
    threshold: ThresholdRange?,
    glossaryId: String?,
    glossary: List<GlossaryEntry>,
    onMetricTap: (String) -> Unit
) {
    val statusColor = if (numericValue != null && threshold != null) {
        val tier = evaluateStatus(numericValue, threshold)
        when (tier) {
            StatusTier.GREEN -> Color(0xFF4CAF50)
            StatusTier.YELLOW -> Color(0xFFFFC107)
            StatusTier.RED -> Color(0xFFF44336)
        }
    } else Color(0xFF8B949E)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(glossaryId) {
                detectTapGestures {
                    if (glossaryId != null) onMetricTap(glossaryId)
                }
            }
            .padding(vertical = 1.dp)
    ) {
        Text(
            label,
            color = Color(0xFFC9D1D9),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp)
        )
        if (threshold != null) {
            Canvas(modifier = Modifier.size(12.dp, 12.dp).padding(end = 4.dp)) {
                drawCircle(color = statusColor)
            }
        }
        Text(
            value,
            color = statusColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun SparklineRow(label: String, currentValue: Float?, values: List<Float>?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            label,
            color = Color(0xFF8B949E),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp)
        )
        if (currentValue != null) {
            Text(
                "(${if (currentValue > 0) "+" else ""}${String.format("%.1f", currentValue)})",
                color = Color(0xFF8B949E),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ── Helpers ──

fun formatUptime(ms: Long): String {
    val seconds = ms / 1000
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return if (days > 0) "${days}d ${hours}h ${minutes}m"
    else "${hours}h ${minutes}m"
}

data class GlossaryEntry(
    val id: String,
    val label: String,
    val section: String,
    val whatItMeasures: String,
    val currentValueContext: String,
    val healthyRange: String,
    val commonCauses: String
)

fun loadGlossary(context: android.content.Context): List<GlossaryEntry> {
    return try {
        val json = context.assets.open("glossary.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("metrics")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GlossaryEntry(
                id = obj.getString("id"),
                label = obj.getString("label"),
                section = obj.getString("section"),
                whatItMeasures = obj.getString("whatItMeasures"),
                currentValueContext = obj.getString("currentValueContext"),
                healthyRange = obj.getString("healthyRange"),
                commonCauses = obj.getString("commonCauses")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
