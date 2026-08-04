package com.droidscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.DroidscanApp
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

// ─── Main Screen ─────────────────────────────────────────

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
        topBar = { AppHeader(currentSnapshot) },
        bottomBar = { TabBar(selectedTab) { selectedTab = it } }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 6.dp)
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

// ─── Top App Bar ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(snapshot: MetricSnapshot) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "droidscan",
                    color = TerminalAccentBlue,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "v1.0",
                    color = TerminalTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        },
        actions = {
            Text(
                snapshot.systemInfo.deviceModel?.take(12) ?: "",
                color = TerminalTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TerminalSurface,
            titleContentColor = TerminalAccentBlue,
            scrolledContainerColor = TerminalSurface
        )
    )
}

// ─── Tab Bar ─────────────────────────────────────────────

@Composable
fun TabBar(selectedTab: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    Surface(
        color = TerminalSurface,
        tonalElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DashboardTab.entries.forEach { tab ->
                val sel = tab == selectedTab
                TextButton(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).then(
                        if (sel) Modifier.background(
                            TerminalAccentBlue.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        ) else Modifier
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tab.icon,
                            color = if (sel) TerminalAccentBlue else TerminalTextSecondary,
                            fontSize = 16.sp
                        )
                        Text(
                            tab.label,
                            color = if (sel) TerminalAccentBlue else TerminalTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Card Wrapper ────────────────────────────────────────

@Composable
fun MetricCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalSurfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                title,
                color = TerminalAccentBlue,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ─── Tab Content ─────────────────────────────────────────

@Composable
fun OverviewTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        MetricCard("SIGNAL") {
            MetricRow("RSRP", MetricValue("${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
            MetricRow("RSRQ", MetricValue("${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "rsrq", onTap)
            MetricRow("SINR", MetricValue("${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr, inverted = true), "sinr", onTap)
            Spacer(Modifier.height(4.dp))
            SignalStrengthBar(snapshot.radio.rsrp, snapshot.radio.dataNetworkType)
            Spacer(Modifier.height(4.dp))
            DualMetricRow("Network", MetricValue(snapshot.radio.dataNetworkType ?: "--"), "Band", MetricValue(snapshot.radio.activeBands.joinToString(", ").ifEmpty { "--" }), "dataNetworkType", "activeBands", onTap)
            DualMetricRow("IMS", MetricValue(if (snapshot.radio.imsRegistered == true) "REG" else if (snapshot.radio.imsRegistered == false) "NO" else "--"), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), "imsRegistered", "isRoaming", onTap)
        }

        MetricCard("SUBSCRIBER") {
            // Show EVERY provisioned SIM (1..N) — not just the first. On a DSDS
            // phone this renders SIM 1 + SIM 2; devices with more slots render all.
            val sims = snapshot.simStatus.slots.filter { it.isProvisioned }
            if (sims.isEmpty()) {
                DualMetricRow("Home PLMN", MetricValue("--"), "SPN", MetricValue("--"))
            } else {
                sims.forEachIndexed { i, sim ->
                    DualMetricRow(
                        "SIM ${i + 1} PLMN", MetricValue(sim.homePlmn ?: "--"),
                        "SIM ${i + 1} SPN", MetricValue(sim.simOperatorName ?: "--")
                    )
                }
            }
            DualMetricRow("Call", MetricValue(snapshot.radio.callState ?: "--"), "Radio", MetricValue(snapshot.radio.radioPowerState ?: "--"))
            DualMetricRow("IMS-Cap", MetricValue(if (snapshot.radio.imsCapable == true) "YES" else if (snapshot.radio.imsCapable == false) "NO" else "--"), "VoLTE", MetricValue(if (snapshot.radio.volteEnabled == true) "ON" else if (snapshot.radio.volteEnabled == false) "OFF" else "--"))
            MetricRow("WiFi Calling", MetricValue(snapshot.radio.wifiCallingStatus ?: "--"))
        }

        MetricCard("BATTERY / THERMAL") {
            DualMetricRow("Level", MetricValue("${snapshot.power.batteryLevel ?: "--"}%", snapshot.power.batteryLevel?.toDouble(), BatteryThresholds.levelPercent, inverted = true), "Temp", MetricValue("${String.format("%.1f", snapshot.power.batteryTemperature ?: 0.0)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius), "batteryLevel", null, onTap)
            val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
            val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
            DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: "--"), "Charge", MetricValue("${chargeMa} mA"), "batteryHealth", "chargeRate", onTap)
            if (drainMa > 0) MetricRow("Drain", MetricValue("${drainMa} mA"), "chargeRate", onTap)
            snapshot.thermal.zones.firstOrNull()?.let { zone ->
                MetricRow("Thermal", MetricValue("${String.format("%.1f", zone.temperatureCelsius)}°C ${if (zone.throttling) "⚠ THROTTLING" else ""}", zone.temperatureCelsius.toDouble(), ThermalThresholds.zoneTemp), "temperature", onTap)
            }
        }

        MetricCard("CPU / MEMORY") {
            snapshot.processes.perCoreLoad.forEachIndexed { i, load ->
                MetricRow("Core $i", MetricValue("${(load * 100).toInt()}%", load.toDouble(), MemoryThresholds.pressure), "cpuLoad", onTap)
            }
            val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
            val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
            BarGauge("RAM", usedRam, totalRam)
            MetricRow("Pressure", MetricValue(snapshot.processes.memoryPressure.name), "memoryPressure", onTap)
        }

        MetricCard("STORAGE") {
            val used = snapshot.storage.internalUsedBytes ?: 0L
            val free = snapshot.storage.internalFreeBytes ?: 0L
            BarGauge("Internal Storage", used, used + free)
            snapshot.storage.topConsumers.take(3).forEach { c ->
                MetricRow("  ${c.label.take(18)}", MetricValue(formatBytes(c.sizeBytes)), indent = true)
            }
        }

        MetricCard("SYSTEM") {
            MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
            DualMetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(30) ?: "--"), "SELinux", MetricValue(if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive/?"), "kernelVersion", "selinuxStatus", onTap)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRow("Shizuku", MetricValue(if (snapshot.shizukuAvailable) "POWER MODE" else "Standard"), modifier = Modifier.weight(1f))
                if (!snapshot.shizukuAvailable) PermissionBadge("Install Shizuku for kernel data")
            }
        }
    }
}

@Composable
fun RadioTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        MetricCard("CELLULAR SIGNAL") {
            MetricRow("RSRP", MetricValue("${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
            MetricRow("RSRQ", MetricValue("${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "rsrq", onTap)
            MetricRow("SINR", MetricValue("${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr, inverted = true), "sinr", onTap)
            Spacer(Modifier.height(4.dp))
            SignalStrengthBar(snapshot.radio.rsrp, snapshot.radio.dataNetworkType)
        }

        MetricCard("SERVING NETWORK") {
            DualMetricRow("Operator", MetricValue(snapshot.radio.operatorName ?: "--"), "Network", MetricValue(snapshot.radio.dataNetworkType ?: "--"), null, "dataNetworkType", onTap)
            DualMetricRow("PLMN", MetricValue(snapshot.radio.networkPlmn ?: "--"), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else if (snapshot.radio.isRoaming == false) "NO" else "--"), null, "isRoaming", onTap)
            DualMetricRow("Band", MetricValue(snapshot.radio.activeBands.joinToString(", ").ifEmpty { "--" }), "Cell ID", MetricValue(snapshot.radio.cellId?.toString() ?: "--"), "activeBands", "ecid", onTap)
            DualMetricRow("TAC", MetricValue(snapshot.radio.tac?.toString() ?: "--"), "Call State", MetricValue(snapshot.radio.callState ?: "--"))
            DualMetricRow("Radio Power", MetricValue(snapshot.radio.radioPowerState ?: "--"), "Data Roam", MetricValue(if (snapshot.radio.dataRoamingEnabled == true) "ON" else if (snapshot.radio.dataRoamingEnabled == false) "OFF" else "--"))
        }

        MetricCard("IMS / REGISTRATION") {
            MetricRow("IMS Registration", MetricValue(if (snapshot.radio.imsRegistered == true) "REGISTERED" else if (snapshot.radio.imsRegistered == false) "NOT REG" else "Unknown"), "imsRegistered", onTap)
            DualMetricRow("IMS Capable", MetricValue(if (snapshot.radio.imsCapable == true) "YES" else if (snapshot.radio.imsCapable == false) "NO" else "--"), "VoLTE", MetricValue(if (snapshot.radio.volteEnabled == true) "ENABLED" else if (snapshot.radio.volteEnabled == false) "DISABLED" else if (snapshot.radio.volteEnabled == null) "?" else "--"))
            DualMetricRow("WiFi Calling", MetricValue(snapshot.radio.wifiCallingStatus ?: "--"), "ePDG", MetricValue(if (snapshot.radio.epdgTunnelActive == true) "ACTIVE" else if (snapshot.radio.epdgTunnelActive == false) "INACTIVE" else "N/A"))
            DualMetricRow("NR-NSA", MetricValue(if (snapshot.radio.nrNsaAvailable == true) "AVAILABLE" else if (snapshot.radio.nrNsaAvailable == false) "NO" else "--"), "Pref NW", MetricValue(snapshot.radio.preferredNetworkType ?: "--"))
        }

        MetricCard("NETWORK CONFIG") {
            DualMetricRow("Type", MetricValue(snapshot.networkConfig.activeNetworkType ?: "--"), "APN", MetricValue(snapshot.networkConfig.apnName ?: "--"), "activeNetwork", null, onTap)
            MetricRow("IPv4", MetricValue(snapshot.networkConfig.ipv4Addresses.firstOrNull() ?: "--"), "ipAddress", onTap)
            MetricRow("IPv6", MetricValue(snapshot.networkConfig.ipv6Addresses.firstOrNull() ?: "--"))
            if (snapshot.networkConfig.wifiSsid != null)
                MetricRow("WiFi SSID", MetricValue(snapshot.networkConfig.wifiSsid ?: "--"))
            DualMetricRow("VPN", MetricValue(if (snapshot.networkConfig.vpnActive) snapshot.networkConfig.vpnLabel ?: "Active" else "Off"), "Hotspot", MetricValue(if (snapshot.networkConfig.hotspotActive) "ON" else "OFF"), null, null, onTap)
        }
    }
}

@Composable
fun SystemTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        MetricCard("CPU") {
            snapshot.processes.perCoreLoad.forEachIndexed { i, load ->
                MetricRow("Core $i", MetricValue("${(load * 100).toInt()}%", load.toDouble(), MemoryThresholds.pressure), "cpuLoad", onTap)
            }
            snapshot.processes.topCpuProcesses.take(3).forEach { p ->
                MetricRow("  ${p.name.take(24)}", MetricValue("${p.pid}"), indent = true)
            }
        }

        MetricCard("MEMORY") {
            val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
            val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
            BarGauge("RAM", usedRam, totalRam)
            MetricRow("Pressure", MetricValue(snapshot.processes.memoryPressure.name, snapshot.processes.usedMemoryBytes?.toDouble()?.div(snapshot.processes.totalMemoryBytes?.toDouble() ?: 1.0), MemoryThresholds.pressure), "memoryPressure", onTap)
            snapshot.processes.topMemoryProcesses.take(4).forEach { p ->
                MetricRow("  ${p.name.take(22)}", MetricValue(formatBytes(p.memoryBytes ?: 0L)), indent = true)
            }
        }

        if (snapshot.processes.kernelWakelocks.isNotEmpty()) {
            MetricCard("WAKELOCKS (Power Mode)") {
                snapshot.processes.kernelWakelocks.take(5).forEach {
                    MetricRow("  ${it.take(24)}", MetricValue("active"), indent = true)
                }
            }
        }

        MetricCard("STORAGE") {
            val used = snapshot.storage.internalUsedBytes ?: 0L
            val free = snapshot.storage.internalFreeBytes ?: 0L
            BarGauge("Internal", used, used + free)
            snapshot.storage.topConsumers.take(4).forEach { c ->
                MetricRow("  ${c.label.take(22)}", MetricValue(formatBytes(c.sizeBytes)), indent = true)
            }
            if (snapshot.storage.externalMounted) MetricRow("External", MetricValue(formatBytes(snapshot.storage.externalFreeBytes ?: 0L) + " free"))
        }

        MetricCard("DEVICE") {
            MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
            MetricRow("Manufacturer", MetricValue(snapshot.systemInfo.manufacturer ?: "--"))
            MetricRow("Android", MetricValue(snapshot.systemInfo.androidVersion ?: "--"))
            MetricRow("Build", MetricValue(snapshot.systemInfo.buildNumber?.take(36) ?: "--"))
            MetricRow("Security Patch", MetricValue(snapshot.systemInfo.securityPatch ?: "--"), null, onTap)
            MetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(40) ?: "--"), "kernelVersion", onTap)
            DualMetricRow("SELinux", MetricValue(if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive"), "Resolution", MetricValue(snapshot.systemInfo.resolution ?: "--"), "selinuxStatus", null, onTap)
            DualMetricRow("Density", MetricValue(snapshot.systemInfo.densityDpi ?: "--"), "Uptime", MetricValue(formatUptime(snapshot.systemInfo.uptimeMs)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    "(tap uptime to dump snapshot)",
                    color = TerminalTextSecondary.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.clickable { DroidscanApp.instance.telemetryCollector.debugDump(snapshot) }
                )
            }
        }
    }
}

@Composable
fun PowerTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    val isThrottling = snapshot.thermal.isThrottling == true
    var showRawSensors by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        // Throttling alert banner at TOP for visibility
        if (isThrottling) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = TerminalAccentRed.copy(alpha = 0.15f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text("THERMAL THROTTLING ACTIVE", color = TerminalAccentRed, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        snapshot.thermal.maxTemperature?.let {
                            Text("Peak: ${String.format("%.1f", it)}°C", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        MetricCard("BATTERY") {
            val level = snapshot.power.batteryLevel ?: 0
            MetricRow("Level", MetricValue("$level%", level.toDouble(), BatteryThresholds.levelPercent, inverted = true), "batteryLevel", onTap)
            BarGauge("Charge", level.toLong(), 100L, warnThreshold = 0.2, critThreshold = 0.05)
            DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: "--"), "Temp", MetricValue("${String.format("%.1f", snapshot.power.batteryTemperature ?: 0.0)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius), "batteryHealth", null, onTap)
            val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
            val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
            if (chargeMa > 0) MetricRow("Charge Rate", MetricValue("${chargeMa} mA"), "chargeRate", onTap)
            if (drainMa > 0) MetricRow("Discharge Rate", MetricValue("${drainMa} mA"))
            snapshot.power.estimatedTimeRemainingMs?.let { MetricRow("Est. Remaining", MetricValue(formatUptime(it))) }
        }

        MetricCard("RADIO / SENSORS") {
            DualMetricRow("Bluetooth", MetricValue(if (snapshot.power.bluetoothActive) "ON" else "OFF"), "eSIM", MetricValue(if (snapshot.power.esimActive) "YES" else "NO"))
            DualMetricRow("Hotspot", MetricValue(if (snapshot.power.hotspotActive) "ON" else "OFF"), "WiFi Call", MetricValue(snapshot.radio.wifiCallingStatus ?: "--"))
        }

        // Aggregated thermal summary
        MetricCard("THERMAL") {
            val zones = snapshot.thermal.zones
            if (zones.isEmpty()) {
                MetricRow("No sensors", MetricValue("--"))
            } else {
                // Group into categories
                val cpuZones = zones.filter { it.name.startsWith("cpu") }
                val gpuZones = zones.filter { it.name.startsWith("gpu") || it.name.startsWith("gpuss") }
                val batteryZones = zones.filter { it.name.contains("battery", ignoreCase = true) || it.name.startsWith("pmxr2230") }
                val chassisZones = zones.filter { it.name.startsWith("sys-therm") || it.name.startsWith("shell") || it.name.startsWith("aoss") }
                val modemZones = zones.filter { it.name.startsWith("mdmss") || it.name.startsWith("sub") }

                // CPU temps
                if (cpuZones.isNotEmpty()) {
                    val peak = cpuZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = cpuZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = cpuZones.any { it.throttling }
                    ThermalSummaryRow("CPU Cores", avg, peak, throttling)
                }

                // GPU temps
                if (gpuZones.isNotEmpty()) {
                    val peak = gpuZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = gpuZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = gpuZones.any { it.throttling }
                    ThermalSummaryRow("GPU", avg, peak, throttling)
                }

                // Battery
                if (batteryZones.isNotEmpty()) {
                    val peak = batteryZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = batteryZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = batteryZones.any { it.throttling }
                    if (peak > -200.0) ThermalSummaryRow("Battery/Power", avg, peak, throttling)
                }

                // Chassis
                if (chassisZones.isNotEmpty()) {
                    val peak = chassisZones.maxOf { it.temperatureCelsius.toDouble() }
                    val avg = chassisZones.map { it.temperatureCelsius.toDouble() }.average()
                    val throttling = chassisZones.any { it.throttling }
                    if (peak > -200.0) ThermalSummaryRow("Chassis", avg, peak, throttling)
                }

                // Modem
                if (modemZones.isNotEmpty()) {
                    val peaks = modemZones.filter { it.temperatureCelsius > -200f }.map { it.temperatureCelsius.toDouble() }
                    if (peaks.isNotEmpty()) {
                        val peak = peaks.max()
                        val avg = peaks.average()
                        ThermalSummaryRow("Modem/RF", avg, peak, modemZones.any { it.throttling })
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Peak overall
                snapshot.thermal.maxTemperature?.let { maxFloat ->
                    val max = maxFloat.toDouble()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Peak overall: ", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text(
                            "${String.format("%.1f", max)}°C",
                            color = if (max > 70) TerminalAccentRed else if (max > 50) TerminalAccentOrange else TerminalTextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isThrottling) {
                            Spacer(Modifier.width(8.dp))
                            PermissionBadge("THROTTLING")
                        }
                    }
                }

                // Expandable raw sensor list
                Spacer(Modifier.height(4.dp))
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

        MetricCard(if (snapshot.shizukuAvailable) "POWER MODE (Shizuku)" else "MODE") {
            if (snapshot.shizukuAvailable) {
                MetricRow("Status", MetricValue("Available — kernel-level data"))
                snapshot.processes.kernelWakelocks.takeIf { it.isNotEmpty() }?.let {
                    MetricRow("Wakelocks", MetricValue("${it.size} active"))
                }
            } else {
                MetricRow("Status", MetricValue("Standard Mode — no kernel data"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Install Shizuku for:", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    PermissionBadge("Kernel wakelocks")
                    Spacer(Modifier.width(4.dp))
                    PermissionBadge("CPU freq")
                }
            }
        }

        MetricCard("TOP PROCESSES") {
            snapshot.processes.topMemoryProcesses.take(5).forEach { p ->
                MetricRow(
                    "  ${p.name.take(24)}",
                    MetricValue("${String.format("%.1f", p.memoryBytes?.div(1_000_000.0) ?: 0.0)} MB"),
                    indent = true
                )
            }
        }
    }
}

@Composable
fun ThermalSummaryRow(label: String, avg: Double, peak: Double, throttling: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TerminalTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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

@Composable
fun SimTab(snapshot: MetricSnapshot, glossary: List<GlossaryEntry>, onTap: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        if (snapshot.simStatus.slots.isEmpty()) {
            MetricCard("NO SIM") {
                MetricRow("Status", MetricValue("No SIM cards detected"))
            }
            return@Column
        }
        snapshot.simStatus.slots.forEachIndexed { i, slot ->
            val provisioned = slot.isProvisioned

            MetricCard("SIM ${i + 1} — ${slot.carrierName ?: "Empty slot"}") {
                DualMetricRow("Carrier", MetricValue(slot.carrierName ?: "--"), "Provisioned", MetricValue(if (provisioned) "YES" else "NO"), "simProvisioning", "simProvisioning", onTap)
                MetricRow("eSIM Profiles", MetricValue("${slot.esimProfileCount}"), "esimProfiles", onTap)
                slot.iccidLast4?.let { MetricRow("ICCID (last 4)", MetricValue("•••• •••• •••• $it")) }
            }

            MetricCard("IDENTITY") {
                MetricRow("Home PLMN", MetricValue(slot.homePlmn ?: "--"))
                MetricRow("SPN (SIM Name)", MetricValue(slot.simOperatorName ?: "--"))
                MetricRow("MSISDN", MetricValue(slot.msisdn ?: "N/A (carrier)"))
                MetricRow("IMSI", MetricValue(slot.imsiPrefix ?: "N/A (priv. API 29+)"))
            }

            MetricCard("CAPABILITIES") {
                // Per-SIM radio state (read via createForSubscriptionId) — SIM 2's
                // capabilities now reflect SIM 2's radio, not the default sub's.
                DualMetricRow("VoLTE", MetricValue(if (slot.volteEnabled == true) "ON" else if (slot.volteEnabled == false) "OFF" else "?"), "WiFi Call", MetricValue(slot.wifiCallingStatus ?: "?"))
                DualMetricRow("NR-NSA", MetricValue(if (slot.nrNsaAvailable == true) "YES" else if (slot.nrNsaAvailable == false) "NO" else "?"), "IMS Reg", MetricValue(if (slot.imsRegistered == true) "REG" else if (slot.imsRegistered == false) "NO" else "?"))
                DualMetricRow("Pref NW", MetricValue(slot.preferredNetworkType ?: "--"), "Call State", MetricValue(slot.callState ?: "--"))
            }

            MetricCard("SIGNAL") {
                MetricRow("Network", MetricValue(slot.dataNetworkType ?: "--"))
                MetricRow("RSRP", MetricValue(if (slot.rsrp != null) "${slot.rsrp} dBm" else "--", slot.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
                MetricRow("Roaming", MetricValue(if (slot.isRoaming == true) "YES" else if (slot.isRoaming == false) "NO" else "?"))
            }
        }

        // Current connection
        MetricCard("CURRENT CONNECTION") {
            MetricRow("Active Network", MetricValue(snapshot.networkConfig.activeNetworkType ?: "--"), "activeNetwork", onTap)
            MetricRow("APN", MetricValue(snapshot.networkConfig.apnName ?: "--"))
            MetricRow("DNS", MetricValue(snapshot.networkConfig.dnsServers.firstOrNull() ?: "--"))
            DualMetricRow("VPN", MetricValue(if (snapshot.networkConfig.vpnActive) snapshot.networkConfig.vpnLabel ?: "Active" else "Off"), "Hotspot", MetricValue(if (snapshot.networkConfig.hotspotActive) "ON" else "OFF"), null, null, onTap)
            DualMetricRow("Network PLMN", MetricValue(snapshot.radio.networkPlmn ?: "--"), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), null, "isRoaming", onTap)
        }
    }
}

// ─── Visual Components ───────────────────────────────────

@Composable
fun SignalStrengthBar(rsrp: Int?, networkType: String? = null) {
    if (rsrp == null) {
        Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("No signal data", color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        return
    }
    val bars = when {
        rsrp >= -90 -> 5
        rsrp >= -100 -> 4
        rsrp >= -110 -> 3
        rsrp >= -120 -> 2
        else -> 1
    }
    val barColor = when {
        bars >= 4 -> TerminalAccentGreen
        bars >= 3 -> TerminalAccentYellow
        else -> TerminalAccentOrange
    }
    // Show the actual RAT rather than hardcoding "LTE" (which is wrong on 5G/3G).
    val ratLabel = networkType?.let {
        when {
            it.startsWith("NR", ignoreCase = true) -> "NR"
            it.startsWith("LTE", ignoreCase = true) -> "LTE"
            it.contains("HSPA") || it.contains("UMTS") -> "3G"
            it.contains("CDMA") || it.contains("EDGE") || it.contains("GPRS") -> "2G"
            it.contains("IWLAN") -> "WiFi"
            else -> "SIG"
        }
    } ?: "SIG"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ratLabel, color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.padding(end = 4.dp))
        repeat(5) { i ->
            val height = (4 + i * 4).dp
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(height)
                    .padding(horizontal = 1.dp)
                    .background(
                        if (i < bars) barColor else TerminalBorder,
                        RoundedCornerShape(1.dp)
                    )
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "${rsrp} dBm",
            color = barColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun PermissionBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = TerminalAccentYellow.copy(alpha = 0.15f)
    ) {
        Text(
            text,
            color = TerminalAccentYellow,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
