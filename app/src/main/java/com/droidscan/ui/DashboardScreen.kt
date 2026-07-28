package com.droidscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.shared.core.*
import com.droidscan.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

enum class DashboardTab(val label: String, val icon: String) {
    OVERVIEW("Overview", "⌂"),
    RADIO("Radio", "◉"),
    SYSTEM("System", "⚙"),
    POWER("Power", "⚡"),
    SIM("SIM", "○")
}

data class GlossaryEntry(
    val id: String, val label: String, val section: String,
    val whatItMeasures: String, val currentValueContext: String,
    val healthyRange: String, val commonCauses: String
)

@Composable
fun DashboardScreen(telemetryFlow: Flow<MetricSnapshot>) {
    var currentSnapshot by remember { mutableStateOf(MetricSnapshot()) }
    var selectedTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    val context = LocalContext.current
    val glossary = remember { loadGlossary(context) }
    var tappedMetricId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(telemetryFlow) {
        telemetryFlow.collectLatest { snapshot -> currentSnapshot = snapshot }
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = { HeaderBar(currentSnapshot) },
        bottomBar = { TabBar(selectedTab) { selectedTab = it } }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 4.dp)
        ) {
            when (selectedTab) {
                DashboardTab.OVERVIEW -> OverviewTab(currentSnapshot, glossary) { tappedMetricId = it }
                DashboardTab.RADIO -> RadioTab(currentSnapshot, glossary) { tappedMetricId = it }
                DashboardTab.SYSTEM -> SystemTab(currentSnapshot, glossary) { tappedMetricId = it }
                DashboardTab.POWER -> PowerTab(currentSnapshot, glossary) { tappedMetricId = it }
                DashboardTab.SIM -> SimTab(currentSnapshot, glossary) { tappedMetricId = it }
            }
        }
    }

    tappedMetricId?.let { id ->
        val entry = glossary.find { it.id == id }
        if (entry != null) {
            AlertDialog(
                onDismissRequest = { tappedMetricId = null },
                title = { Text(entry.label, color = TerminalAccentBlue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("What it measures:", color = TerminalTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(entry.whatItMeasures, color = TerminalTextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Current value context:", color = TerminalTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(entry.currentValueContext, color = TerminalTextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Healthy range:", color = TerminalTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(entry.healthyRange, color = TerminalTextPrimary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Common causes:", color = TerminalTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(entry.commonCauses, color = TerminalTextPrimary, fontSize = 13.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { tappedMetricId = null }) { Text("Close", color = TerminalAccentBlue) } },
                containerColor = TerminalSurface,
                titleContentColor = TerminalAccentBlue
            )
        }
    }
}

// ─── Header ──────────────────────────────────────────────

@Composable
fun HeaderBar(snapshot: MetricSnapshot) {
    Surface(color = TerminalSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("droidscan v1.0", color = TerminalAccentBlue, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(snapshot.systemInfo.deviceModel ?: "Android", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(snapshot.systemInfo.androidVersion?.let { "Android $it" } ?: "", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text("Uptime ${formatUptime(snapshot.systemInfo.uptimeMs)}", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

// ─── Tab Bar ──────────────────────────────────────────────

@Composable
fun TabBar(selectedTab: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    Surface(color = TerminalSurface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            DashboardTab.entries.forEach { tab ->
                val sel = tab == selectedTab
                TextButton(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).then(if (sel) Modifier.background(TerminalAccentBlue.copy(alpha = 0.12f), RoundedCornerShape(6.dp)) else Modifier),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.icon, color = if (sel) TerminalAccentBlue else TerminalTextSecondary, fontSize = 16.sp)
                        Text(tab.label, color = if (sel) TerminalAccentBlue else TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// ─── Tab Content ──────────────────────────────────────────

@Composable
fun OverviewTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        SectionDivider("SIGNAL")
        MetricRow("RSRP", MetricValue("${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp), "rsrp", onTap)
        MetricRow("RSRQ", MetricValue("${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq), "rsrq", onTap)
        MetricRow("SINR", MetricValue("${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr), "sinr", onTap)
        DualMetricRow("Network", MetricValue(snapshot.radio.dataNetworkType ?: "--"), "Band", MetricValue(snapshot.radio.activeBands.joinToString(", ").ifEmpty { "--" }), "dataNetworkType", "activeBands", onTap)
        DualMetricRow("IMS", MetricValue(if (snapshot.radio.imsRegistered == true) "REG" else if (snapshot.radio.imsRegistered == false) "NO" else "--"), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), "imsRegistered", "isRoaming", onTap)

        SectionDivider("BATTERY / THERMAL")
        DualMetricRow("Level", MetricValue("${snapshot.power.batteryLevel ?: "--"}%", snapshot.power.batteryLevel?.toDouble(), BatteryThresholds.levelPercent), "Temp", MetricValue("${String.format("%.1f", snapshot.power.batteryTemperature ?: 0.0)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius, inverted = true), "batteryLevel", null, onTap)
        val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
        val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
        DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: "--"), "Charge", MetricValue("${chargeMa} mA"), "batteryHealth", "chargeRate", onTap)
        if (drainMa > 0) MetricRow("Drain", MetricValue("${drainMa} mA"), "chargeRate", onTap)
        snapshot.thermal.zones.firstOrNull()?.let { zone ->
            MetricRow("Thermal", MetricValue("${String.format("%.1f", zone.temperatureCelsius)}°C ${if (zone.throttling) "THROTTLING" else ""}", zone.temperatureCelsius.toDouble(), ThermalThresholds.zoneTemp), "temperature", onTap)
        }

        SectionDivider("CPU / MEMORY")
        snapshot.processes.perCoreLoad.forEachIndexed { i, load ->
            MetricRow("Core $i", MetricValue("${(load * 100).toInt()}%", load.toDouble(), MemoryThresholds.pressure), "cpuLoad", onTap)
        }
        val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
        val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
        BarGauge("RAM", usedRam, totalRam)
        MetricRow("Pressure", MetricValue(snapshot.processes.memoryPressure.name), "memoryPressure", onTap)

        SectionDivider("STORAGE")
        val used = snapshot.storage.internalUsedBytes ?: 0L
        val free = snapshot.storage.internalFreeBytes ?: 0L
        BarGauge("Internal Storage", used, used + free)
        snapshot.storage.topConsumers.take(3).forEach { c ->
            MetricRow("  ${c.label.take(18)}", MetricValue(formatBytes(c.sizeBytes)), indent = true)
        }

        SectionDivider("SYSTEM")
        MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
        DualMetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(30) ?: "--"), "SELinux", MetricValue(if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive/?"), "kernelVersion", "selinuxStatus", onTap)
        MetricRow("Shizuku", MetricValue(if (snapshot.shizukuAvailable) "POWER MODE" else "Standard"))
    }
}

@Composable
fun RadioTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        SectionDivider("CELLULAR SIGNAL")
        MetricRow("RSRP", MetricValue("${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp), "rsrp", onTap)
        MetricRow("RSRQ", MetricValue("${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq), "rsrq", onTap)
        MetricRow("SINR", MetricValue("${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr), "sinr", onTap)
        SectionDivider("NETWORK INFO")
        DualMetricRow("Operator", MetricValue(snapshot.radio.operatorName ?: "--"), "Network", MetricValue(snapshot.radio.dataNetworkType ?: "--"), null, "dataNetworkType", onTap)
        DualMetricRow("Band", MetricValue(snapshot.radio.activeBands.joinToString(", ").ifEmpty { "--" }), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), "activeBands", "isRoaming", onTap)
        DualMetricRow("Cell ID", MetricValue(snapshot.radio.cellId?.toString() ?: "--"), "TAC", MetricValue(snapshot.radio.tac?.toString() ?: "--"), null, null, onTap)
        MetricRow("IMS Registration", MetricValue(if (snapshot.radio.imsRegistered == true) "REGISTERED" else if (snapshot.radio.imsRegistered == false) "NOT REGISTERED" else "Unknown"), "imsRegistered", onTap)
        MetricRow("ePDG Tunnel", MetricValue(if (snapshot.radio.epdgTunnelActive == true) "Active" else if (snapshot.radio.epdgTunnelActive == false) "Inactive" else "N/A"))
        SectionDivider("NETWORK CONFIG")
        DualMetricRow("Type", MetricValue(snapshot.networkConfig.activeNetworkType ?: "--"), "APN", MetricValue(snapshot.networkConfig.apnName ?: "--"), "activeNetwork", null, onTap)
        MetricRow("IPv4", MetricValue(snapshot.networkConfig.ipv4Addresses.firstOrNull() ?: "--"), "ipAddress", onTap)
        MetricRow("IPv6", MetricValue(snapshot.networkConfig.ipv6Addresses.firstOrNull() ?: "--"))
        DualMetricRow("VPN", MetricValue(if (snapshot.networkConfig.vpnActive) snapshot.networkConfig.vpnLabel ?: "Active" else "Off"), "Hotspot", MetricValue(if (snapshot.networkConfig.hotspotActive) "ON" else "OFF"), null, null, onTap)
    }
}

@Composable
fun SystemTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        SectionDivider("CPU")
        snapshot.processes.perCoreLoad.forEachIndexed { i, load ->
            MetricRow("Core $i", MetricValue("${(load * 100).toInt()}%", load.toDouble(), MemoryThresholds.pressure), "cpuLoad", onTap)
        }
        snapshot.processes.topCpuProcesses.take(3).forEach { p ->
            MetricRow("  ${p.name.take(24)}", MetricValue("${p.pid}"), indent = true)
        }
        SectionDivider("MEMORY")
        val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
        val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
        BarGauge("RAM", usedRam, totalRam)
        MetricRow("Pressure", MetricValue(snapshot.processes.memoryPressure.name, snapshot.processes.usedMemoryBytes?.toDouble()?.div(snapshot.processes.totalMemoryBytes?.toDouble() ?: 1.0), MemoryThresholds.pressure), "memoryPressure", onTap)
        snapshot.processes.topMemoryProcesses.take(4).forEach { p ->
            MetricRow("  ${p.name.take(22)}", MetricValue(formatBytes(p.memoryBytes ?: 0L)), indent = true)
        }
        if (snapshot.processes.kernelWakelocks.isNotEmpty()) {
            SectionDivider("WAKELOCKS (Power Mode)")
            snapshot.processes.kernelWakelocks.take(5).forEach { MetricRow("  $it", MetricValue("active"), indent = true) }
        }
        SectionDivider("STORAGE")
        val used = snapshot.storage.internalUsedBytes ?: 0L
        val free = snapshot.storage.internalFreeBytes ?: 0L
        BarGauge("Internal", used, used + free)
        snapshot.storage.topConsumers.take(4).forEach { c ->
            MetricRow("  ${c.label.take(22)}", MetricValue(formatBytes(c.sizeBytes)), indent = true)
        }
        if (snapshot.storage.externalMounted) MetricRow("External", MetricValue(formatBytes(snapshot.storage.externalFreeBytes ?: 0L) + " free"))
        SectionDivider("DEVICE")
        MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
        MetricRow("Manufacturer", MetricValue(snapshot.systemInfo.manufacturer ?: "--"))
        MetricRow("Android", MetricValue(snapshot.systemInfo.androidVersion ?: "--"))
        MetricRow("Build", MetricValue(snapshot.systemInfo.buildNumber?.take(36) ?: "--"))
        MetricRow("Security Patch", MetricValue(snapshot.systemInfo.securityPatch ?: "--"), null, onTap)
        MetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(40) ?: "--"), "kernelVersion", onTap)
        DualMetricRow("SELinux", MetricValue(if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive"), "Uptime", MetricValue(formatUptime(snapshot.systemInfo.uptimeMs)), "selinuxStatus", "uptime", onTap)
    }
}

@Composable
fun PowerTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        SectionDivider("BATTERY")
        val level = snapshot.power.batteryLevel ?: 0
        MetricRow("Level", MetricValue("$level%", level.toDouble(), BatteryThresholds.levelPercent), "batteryLevel", onTap)
        BarGauge("Charge", level.toLong(), 100L, warnThreshold = 0.2, critThreshold = 0.05)
        DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: "--"), "Temp", MetricValue("${String.format("%.1f", snapshot.power.batteryTemperature ?: 0.0)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius, inverted = true), "batteryHealth", null, onTap)
        val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
        val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
        if (chargeMa > 0) MetricRow("Charge Rate", MetricValue("${chargeMa} mA"), "chargeRate", onTap)
        if (drainMa > 0) MetricRow("Discharge Rate", MetricValue("${drainMa} mA"))
        snapshot.power.estimatedTimeRemainingMs?.let { MetricRow("Est. Remaining", MetricValue(formatUptime(it))) }
        SectionDivider("THERMAL")
        snapshot.thermal.zones.forEach { zone ->
            MetricRow(zone.name, MetricValue("${String.format("%.1f", zone.temperatureCelsius)}°C", zone.temperatureCelsius.toDouble(), ThermalThresholds.zoneTemp), "temperature", onTap)
        }
        MetricRow("Throttling", MetricValue(if (snapshot.thermal.isThrottling == true) "⚠ ACTIVE" else "Normal"), "throttling", onTap)
        snapshot.thermal.maxTemperature?.let { MetricRow("Max Temp", MetricValue("${String.format("%.1f", it)}°C", it.toDouble(), ThermalThresholds.zoneTemp)) }
        SectionDivider(if (snapshot.shizukuAvailable) "POWER MODE (Shizuku)" else "MODE")
        if (snapshot.shizukuAvailable) {
            MetricRow("Status", MetricValue("Available — kernel-level data"))
            snapshot.processes.kernelWakelocks.takeIf { it.isNotEmpty() }?.let { MetricRow("Wakelocks", MetricValue("${it.size} active")) }
        } else {
            MetricRow("Status", MetricValue("Standard Mode"))
            MetricRow("(Install Shizuku)", MetricValue("for kernel-level data"))
        }
        SectionDivider("TOP PROCESSES")
        snapshot.processes.topMemoryProcesses.take(5).forEach { p ->
            MetricRow("  ${p.name.take(24)}", MetricValue("${String.format("%.1f", p.memoryBytes?.div(1_000_000.0) ?: 0.0)} MB"), indent = true)
        }
    }
}

@Composable
fun SimTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        if (snapshot.simStatus.slots.isEmpty()) {
            SectionDivider("NO SIM")
            MetricRow("Status", MetricValue("No SIM cards detected"))
            return@Column
        }
        snapshot.simStatus.slots.forEachIndexed { i, slot ->
            SectionDivider("SIM ${i + 1} — ${slot.carrierName ?: "Empty slot"}")
            DualMetricRow("Carrier", MetricValue(slot.carrierName ?: "--"), "Provisioned", MetricValue(if (slot.isProvisioned) "YES" else "NO"), "simProvisioning", "simProvisioning", onTap)
            MetricRow("eSIM Profiles", MetricValue("${slot.esimProfileCount}"), "esimProfiles", onTap)
            slot.iccidLast4?.let { MetricRow("ICCID (last 4)", MetricValue("•••• •••• •••• $it")) }
        }
        SectionDivider("CURRENT CONNECTION")
        MetricRow("Active Network", MetricValue(snapshot.networkConfig.activeNetworkType ?: "--"), "activeNetwork", onTap)
        MetricRow("APN", MetricValue(snapshot.networkConfig.apnName ?: "--"))
        MetricRow("DNS", MetricValue(snapshot.networkConfig.dnsServers.firstOrNull() ?: "--"))
        DualMetricRow("VPN", MetricValue(if (snapshot.networkConfig.vpnActive) snapshot.networkConfig.vpnLabel ?: "Active" else "Off"), "Hotspot", MetricValue(if (snapshot.networkConfig.hotspotActive) "ON" else "OFF"), null, null, onTap)
        MetricRow("Operator", MetricValue(snapshot.radio.operatorName ?: "--"))
        MetricRow("Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), "isRoaming", onTap)
    }
}

// ─── Glossary Loader ─────────────────────────────────────

fun loadGlossary(context: android.content.Context): List<GlossaryEntry> {
    return try {
        val json = context.assets.open("glossary.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("metrics")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GlossaryEntry(id = obj.getString("id"), label = obj.getString("label"), section = obj.getString("section"), whatItMeasures = obj.getString("whatItMeasures"), currentValueContext = obj.getString("currentValueContext"), healthyRange = obj.getString("healthyRange"), commonCauses = obj.getString("commonCauses"))
        }
    } catch (_: Exception) { emptyList() }
}
