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
import androidx.core.content.ContextCompat
import com.droidscan.DroidscanApp
import com.droidscan.TelemetryService
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
    val healthyRange: String, val commonCauses: String,
    val expansion: String = ""
)

// ─── Main Screen ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(telemetryFlow: Flow<MetricSnapshot>) {
    var currentSnapshot by remember { mutableStateOf(MetricSnapshot()) }
    var selectedTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val glossary = remember { loadGlossary(context) }
    var tappedMetricId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(telemetryFlow) {
        telemetryFlow.collectLatest { snapshot -> currentSnapshot = snapshot }
    }

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            if (showSettings) SettingsTopBar { showSettings = false }
            else AppHeader(currentSnapshot) { showSettings = true }
        },
        bottomBar = {
            if (!showSettings) TabBar(selectedTab) { selectedTab = it }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSettings) {
                SettingsScreen(currentSnapshot)
            } else {
                val contentPadding = Modifier.padding(horizontal = 14.dp)
                when (selectedTab) {
                    DashboardTab.OVERVIEW -> OverviewTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                    DashboardTab.RADIO -> RadioTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                    DashboardTab.SYSTEM -> SystemTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                    DashboardTab.POWER -> PowerTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                    DashboardTab.SIM -> SimTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                }
            }
        }
    }

    // Glossary bottom sheet (swipe-to-dismiss) — replaces the old AlertDialog.
    tappedMetricId?.let { id ->
        val entry = glossary.find { it.id == id }
        if (entry != null) {
            GlossarySheet(
                entry = entry,
                liveValue = resolveLiveValue(id, currentSnapshot),
                onDismiss = { tappedMetricId = null }
            )
        }
    }
}

// ─── Top App Bar ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(snapshot: MetricSnapshot, onOpenSettings: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "droidscan",
                    color = TerminalAccentBlue,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                if (snapshot.shizukuAvailable) Chip("POWER MODE", TerminalAccentCyan)
            }
        },
        actions = {
            Text(
                snapshot.systemInfo.deviceModel?.take(12) ?: "",
                color = TerminalTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Gear → Settings (48dp touch target).
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp)
            ) {
                Text("⚙", color = TerminalAccentBlue, fontSize = 18.sp)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TerminalSurface,
            titleContentColor = TerminalAccentBlue,
            scrolledContainerColor = TerminalSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "SETTINGS",
                color = TerminalAccentBlue,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.09.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Text("←", color = TerminalTextSecondary, fontSize = 18.sp)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TerminalSurface,
            titleContentColor = TerminalAccentBlue,
            scrolledContainerColor = TerminalSurface
        )
    )
}

// ─── Tab Bar ─────────────────────────────────────────────
// 5 tabs, active cyan, inactive ink2, ≥44dp tall, 2dp cyan top indicator,
// raised scrim + top hairline.

@Composable
fun TabBar(selectedTab: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TerminalBorder))
        Surface(color = TerminalSurfaceVariant, tonalElevation = 0.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardTab.entries.forEach { tab ->
                    val sel = tab == selectedTab
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clickable { onSelect(tab) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top indicator: 2dp cyan underline on the active tab.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (sel) TerminalAccentBlue else Color.Transparent)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tab.icon,
                            color = if (sel) TerminalAccentBlue else TerminalTextSecondary,
                            fontSize = 15.sp
                        )
                        Text(
                            tab.label,
                            color = if (sel) TerminalAccentBlue else TerminalTextSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ─── Overview ────────────────────────────────────────────
// SIGNAL · POWER · SYSTEM (three-tier health summary).

@Composable
fun OverviewTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    onTap: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TierCard("SIGNAL", liveReadout = if (snapshot.radio.cellInfoDenied == true) "denied" else null, liveColor = TerminalAccentYellow) {
            if (snapshot.radio.cellInfoDenied == true) {
                LocationRequiredRow()
            } else {
                MetricRow("RSRP", MetricValue("${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
                MetricRow("RSRQ", MetricValue("${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "rsrq", onTap)
                MetricRow("SINR", MetricValue("${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr, inverted = true), "sinr", onTap)
                SignalStrengthBar(snapshot.radio.rsrp, snapshot.radio.dataNetworkType)
                val bandDisplay = radioBandDisplay(snapshot.radio)
                DualMetricRow("Network", MetricValue(snapshot.radio.dataNetworkType ?: "--"), "Band", MetricValue(bandDisplay), "dataNetworkType", "activeBands", onTap)
                val overviewIms = snapshot.ims.perSub.firstOrNull()?.registrationState
                    ?: if (snapshot.radio.imsRegistered == true) "REG" else if (snapshot.radio.imsRegistered == false) "NO" else "--"
                DualMetricRow("IMS", MetricValue(overviewIms), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), "imsRegistered", "isRoaming", onTap)
            }
        }

        TierCard("POWER", liveReadout = snapshot.power.batteryLevel?.let { "$it%" }, liveColor = TerminalAccentCyan) {
            DualMetricRow("Level", MetricValue("${snapshot.power.batteryLevel ?: "--"}%", snapshot.power.batteryLevel?.toDouble(), BatteryThresholds.levelPercent, inverted = true), "Temp", MetricValue("${String.format("%.1f", snapshot.power.batteryTemperature ?: 0f)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius), "batteryLevel", null, onTap)
            val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
            val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
            DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: "--"), "Charge", MetricValue("${chargeMa} mA"), "batteryHealth", "chargeRate", onTap)
            if (drainMa > 0) MetricRow("Drain", MetricValue("${drainMa} mA"), "chargeRate", onTap)
            snapshot.thermal.zones.firstOrNull()?.let { zone ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricRow("Thermal", MetricValue("${String.format("%.1f", zone.temperatureCelsius)}°C", zone.temperatureCelsius.toDouble(), ThermalThresholds.zoneTemp), "temperature", onTap, modifier = Modifier.weight(1f))
                    if (snapshot.thermal.isThrottling == true) {
                        Spacer(Modifier.width(6.dp))
                        Chip("THROTTLING", TerminalAccentRed)
                    }
                }
            }
        }

        TierCard("SYSTEM", liveReadout = snapshot.systemInfo.androidVersion, liveColor = TerminalTextSecondary) {
            val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
            val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
            BarGauge("RAM", usedRam, totalRam)
            MetricRow("Pressure", MetricValue(snapshot.processes.memoryPressure.name), "memoryPressure", onTap)
            MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
            MetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(30) ?: "--"))
            DualMetricRow("Uptime", MetricValue(formatUptime(snapshot.systemInfo.uptimeMs)), "Shizuku", MetricValue(if (snapshot.shizukuAvailable) "POWER MODE" else "Standard"))
        }
    }
}

// ─── Radio ───────────────────────────────────────────────

@Composable
fun RadioTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    onTap: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Location-denied banner (Radio): title + body + Open Settings pill.
        if (snapshot.radio.cellInfoDenied == true) {
            LocationRequiredBanner()
        }

        TierCard("CELLULAR SIGNAL") {
            if (snapshot.radio.cellInfoDenied == true) {
                MetricRow("RSRP", MetricValue("-- dBm"))
                MetricRow("RSRQ", MetricValue("-- dB"))
                MetricRow("SINR", MetricValue("-- dB"))
            } else {
                MetricRow("RSRP", MetricValue("${snapshot.radio.rsrp ?: "--"} dBm", snapshot.radio.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
                MetricRow("RSRQ", MetricValue("${snapshot.radio.rsrq ?: "--"} dB", snapshot.radio.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "rsrq", onTap)
                MetricRow("SINR", MetricValue("${snapshot.radio.sinr ?: "--"} dB", snapshot.radio.sinr?.toDouble(), RadioThresholds.sinr, inverted = true), "sinr", onTap)
                SignalStrengthBar(snapshot.radio.rsrp, snapshot.radio.dataNetworkType)
                // NR: sync-signal RSRP/RSRQ/SINR (SS-*) — the canonical 5G field metric.
                if (snapshot.radio.nrarfcn != null || snapshot.radio.dataNetworkType?.startsWith("NR") == true) {
                    MetricRow("SS-RSRP", MetricValue("${snapshot.radio.ssRsrp ?: "--"} dBm", snapshot.radio.ssRsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "ssRsrp", onTap)
                    MetricRow("SS-RSRQ", MetricValue("${snapshot.radio.ssRsrq ?: "--"} dB", snapshot.radio.ssRsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "ssRsrp", onTap)
                    MetricRow("SS-SINR", MetricValue("${snapshot.radio.ssSinr ?: "--"} dB", snapshot.radio.ssSinr?.toDouble(), RadioThresholds.sinr, inverted = true), "ssRsrp", onTap)
                }
            }
        }

        TierCard("SERVING NETWORK") {
            DualMetricRow("Operator", MetricValue(snapshot.radio.operatorName ?: "--"), "Network", MetricValue(snapshot.radio.dataNetworkType ?: "--"), null, "dataNetworkType", onTap)
            DualMetricRow("PLMN", MetricValue(snapshot.radio.networkPlmn ?: "--"), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else if (snapshot.radio.isRoaming == false) "NO" else "--"), null, "isRoaming", onTap)
            DualMetricRow("Call State", MetricValue(snapshot.radio.callState ?: "--"), "Radio Power", MetricValue(snapshot.radio.radioPowerState ?: "--"))
            DualMetricRow("Data Roam", MetricValue(if (snapshot.radio.dataRoamingEnabled == true) "ON" else if (snapshot.radio.dataRoamingEnabled == false) "OFF" else "--"), "Pref NW", MetricValue(snapshot.radio.preferredNetworkType ?: "--"))
        }

        TierCard("CELL / FREQUENCY") {
            val bwMhz = snapshot.radio.bandwidthKhz?.div(1000f)?.let { "${it} MHz" }
            DualMetricRow("Band", MetricValue(radioBandDisplay(snapshot.radio)), "Bandwidth", MetricValue(bwMhz ?: "--"), "activeBands", "bandwidth", onTap)
            DualMetricRow("EARFCN", MetricValue(snapshot.radio.earfcn?.toString() ?: "--"), "NR-ARFCN", MetricValue(snapshot.radio.nrarfcn?.toString() ?: "--"), "earfcn", "nrarfcn", onTap)
            DualMetricRow("PCI", MetricValue(snapshot.radio.pci?.toString() ?: "--"), "Cell ID", MetricValue(snapshot.radio.cellId?.toString() ?: "--"), "pci", "ecid", onTap)
            DualMetricRow("TAC", MetricValue(snapshot.radio.tac?.toString() ?: "--"), "DL Freq", MetricValue(snapshot.radio.dlFrequencyMhz?.let(::formatMhz) ?: "--"), null, "earfcn", onTap)
        }

        TierCard("IMS / REGISTRATION") {
            val ims = snapshot.ims
            if (ims.apiAvailable && ims.accessDenied) {
                MetricRow("Status", MetricValue("Precise phone perm required"))
                MetricRow("Hint", MetricValue("Grant via Settings → Permissions"))
            } else if (ims.perSub.isNotEmpty()) {
                val reg = ims.perSub.first()
                MetricRow("Registration", MetricValue(reg.registrationState ?: "--"), "imsRegistered", onTap)
                DualMetricRow("Transport", MetricValue(reg.transportType ?: "--"), "VoLTE", MetricValue(reg.volteEnabled?.let { if (it) "ENABLED" else "DISABLED" } ?: "--"), null, "imsRegistered", onTap)
                DualMetricRow("WiFi Calling", MetricValue(reg.wfcEnabled?.let { if (it) "ENABLED" else "DISABLED" } ?: "--"), "VT", MetricValue(reg.vtEnabled?.let { if (it) "ENABLED" else "DISABLED" } ?: "--"), null, "imsCapable", onTap)
                if (ims.perSub.size > 1) {
                    ims.perSub.drop(1).forEach { r ->
                        MetricRow("Sub ${r.subscriptionId}", MetricValue(r.registrationState ?: "--"), indent = true)
                    }
                }
                DualMetricRow("NR-NSA", MetricValue(if (snapshot.radio.nrNsaAvailable == true) "AVAILABLE" else if (snapshot.radio.nrNsaAvailable == false) "NO" else "--"), "Pref NW", MetricValue(snapshot.radio.preferredNetworkType ?: "--"))
            } else {
                MetricRow("IMS Registration", MetricValue(if (snapshot.radio.imsRegistered == true) "REGISTERED" else if (snapshot.radio.imsRegistered == false) "NOT REG" else "Unknown"), "imsRegistered", onTap)
                DualMetricRow("IMS Capable", MetricValue(if (snapshot.radio.imsCapable == true) "YES" else if (snapshot.radio.imsCapable == false) "NO" else "--"), "VoLTE", MetricValue(if (snapshot.radio.volteEnabled == true) "ENABLED" else if (snapshot.radio.volteEnabled == false) "DISABLED" else if (snapshot.radio.volteEnabled == null) "?" else "--"))
                DualMetricRow("WiFi Calling", MetricValue(snapshot.radio.wifiCallingStatus ?: "--"), "ePDG", MetricValue(if (snapshot.radio.epdgTunnelActive == true) "ACTIVE" else if (snapshot.radio.epdgTunnelActive == false) "INACTIVE" else "N/A"))
                DualMetricRow("NR-NSA", MetricValue(if (snapshot.radio.nrNsaAvailable == true) "AVAILABLE" else if (snapshot.radio.nrNsaAvailable == false) "NO" else "--"), "Pref NW", MetricValue(snapshot.radio.preferredNetworkType ?: "--"))
            }
        }

        GnssCard(snapshot, onTap)

        TierCard("NETWORK CONFIG") {
            DualMetricRow("Type", MetricValue(snapshot.networkConfig.activeNetworkType ?: "--"), "APN", MetricValue(snapshot.networkConfig.apnName ?: "--"), "activeNetwork", null, onTap)
            MetricRow("IPv4", MetricValue(snapshot.networkConfig.ipv4Addresses.firstOrNull() ?: "--"), "ipAddress", onTap)
            MetricRow("IPv6", MetricValue(snapshot.networkConfig.ipv6Addresses.firstOrNull() ?: "--"))
            if (snapshot.networkConfig.wifiSsid != null)
                MetricRow("WiFi SSID", MetricValue(snapshot.networkConfig.wifiSsid ?: "--"))
            DualMetricRow("VPN", MetricValue(if (snapshot.networkConfig.vpnActive) snapshot.networkConfig.vpnLabel ?: "Active" else "Off"), "Hotspot", MetricValue(if (snapshot.networkConfig.hotspotActive) "ON" else "OFF"), null, null, onTap)
        }
    }
}

// ─── GNSS / Satellites ───────────────────────────────────

@Composable
fun GnssCard(snapshot: MetricSnapshot, onTap: (String) -> Unit) {
    TierCard("GNSS / SATELLITES", liveReadout = snapshot.gnss.satellites.maxOfOrNull { it.cn0DbHz }?.let { "%.0f dB-Hz".format(it) }, liveColor = TerminalAccentCyan) {
        val g = snapshot.gnss
        when {
            g.locationDenied -> {
                MetricRow("Status", MetricValue("Location denied"))
                MetricRow("Hint", MetricValue("Grant location to read satellites"))
            }
            !g.available -> MetricRow("Status", MetricValue("No GNSS receiver"))
            g.satellites.isEmpty() -> MetricRow("Status", MetricValue(if (g.hasFix) "FIX ACQUIRED" else "Searching…"))
            else -> {
                val used = g.satellites.count { it.usedInFix }
                MetricRow("Fix", MetricValue(if (g.hasFix) "ACQUIRED" else "searching"))
                DualMetricRow("Visible", MetricValue("${g.satellites.size}"), "In Fix", MetricValue("$used"), null, "gnss", onTap)

                // Per-constellation summary: visible · in-fix · peak C/N0.
                g.satellites.groupBy { it.constellation }.toSortedMap().forEach { (const, list) ->
                    val peak = list.maxOf { it.cn0DbHz }
                    val usedCount = list.count { it.usedInFix }
                    MetricRow(
                        const,
                        MetricValue("${list.size} vis · $usedCount fix · ${"%.1f".format(peak)} dB-Hz", list.maxOfOrNull { it.cn0DbHz }?.toDouble(), null),
                        "cn0", onTap
                    )
                }

                Spacer(Modifier.height(2.dp))
                TierDivider()

                // Strongest satellites (top 4 by C/N0) — the constellation sky view.
                g.satellites.sortedByDescending { it.cn0DbHz }.take(4).forEach { sat ->
                    MetricRow(
                        "PRN ${sat.prn} · ${sat.constellation}",
                        MetricValue("${"%.1f".format(sat.cn0DbHz)} dB-Hz" + if (sat.usedInFix) " · FIX" else ""),
                        "cn0", onTap, indent = true
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "L1 1575.42 · L5 1176.45 MHz · ${g.satellites.count { it.hasEphemeris }} ephemeris",
                        color = TerminalTextSecondary.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

// ─── System ──────────────────────────────────────────────

@Composable
fun SystemTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    onTap: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TierCard("CPU", liveReadout = snapshot.processes.perCoreLoad.maxOrNull()?.let { "${(it * 100).toInt()}%" }, liveColor = TerminalAccentCyan) {
            val hasCpuData = snapshot.processes.perCoreLoad.any { it > 0f }
            if (!hasCpuData) {
                // Android 16: /proc/stat is EACCES for non-privileged apps.
                MetricRow("Status", MetricValue("CPU data requires privilege"))
            } else {
                snapshot.processes.perCoreLoad.forEachIndexed { i, load ->
                    MetricRow("Core $i", MetricValue("${(load * 100).toInt()}%", load.toDouble(), MemoryThresholds.pressure), "cpuLoad", onTap)
                }
                snapshot.processes.topCpuProcesses.take(3).forEach { p ->
                    MetricRow("${p.name.take(24)}", MetricValue("${p.pid}"), indent = true)
                }
            }
        }

        TierCard("MEMORY") {
            val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
            val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
            BarGauge("RAM", usedRam, totalRam)
            MetricRow("Pressure", MetricValue(snapshot.processes.memoryPressure.name, snapshot.processes.usedMemoryBytes?.toDouble()?.div(snapshot.processes.totalMemoryBytes?.toDouble() ?: 1.0), MemoryThresholds.pressure), "memoryPressure", onTap)
            snapshot.processes.topMemoryProcesses.take(4).forEach { p ->
                MetricRow("${p.name.take(22)}", MetricValue(formatBytes(p.memoryBytes ?: 0L)), indent = true)
            }
        }

        if (snapshot.processes.kernelWakelocks.isNotEmpty()) {
            TierCard("WAKELOCKS (Power Mode)") {
                snapshot.processes.kernelWakelocks.take(5).forEach {
                    MetricRow("${it.take(24)}", MetricValue("active"), indent = true)
                }
            }
        }

        TierCard("STORAGE") {
            val used = snapshot.storage.internalUsedBytes ?: 0L
            val free = snapshot.storage.internalFreeBytes ?: 0L
            BarGauge("Internal", used, used + free)
            snapshot.storage.topConsumers.take(4).forEach { c ->
                MetricRow("${c.label.take(22)}", MetricValue(formatBytes(c.sizeBytes)), indent = true)
            }
            if (snapshot.storage.externalMounted) MetricRow("External", MetricValue(formatBytes(snapshot.storage.externalFreeBytes ?: 0L) + " free"))
        }

        // Deep storage / wear — dumpsys diskstats via the T2 shell bridge.
        TierCard("STORAGE WEAR (Power Mode)") {
            val sw = snapshot.storageWear
            if (sw.powerMode) {
                DualMetricRow("512B Write Lat", MetricValue(sw.writeLatencyMs?.let { "$it ms" } ?: "--"), "Write Speed", MetricValue(sw.recentWriteKbps?.let { "$it kB/s" } ?: "--"))
                DualMetricRow("Data Free", MetricValue(sw.dataFreePercent?.let { "$it%" } ?: "--"), "FBE", MetricValue(sw.fileBasedEncryption?.let { if (it) "ON" else "OFF" } ?: "--"))
                DualMetricRow("App Size", MetricValue(sw.appSizeBytes?.let(::formatBytes) ?: "--"), "Data Size", MetricValue(sw.appDataSizeBytes?.let(::formatBytes) ?: "--"))
                DualMetricRow("Cache", MetricValue(sw.appCacheSizeBytes?.let(::formatBytes) ?: "--"), "Media", MetricValue(listOfNotNull(sw.photosSizeBytes, sw.videosSizeBytes, sw.audioSizeBytes).sum().let { if (it > 0) formatBytes(it) else "--" }))
            } else {
                MetricRow("Status", MetricValue(if (snapshot.shizukuAvailable) "No data" else "Requires Shizuku"))
                MetricRow("Hint", MetricValue("Start Shizuku → grant → Power Mode"))
            }
        }

        TierCard("DEVICE") {
            MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
            MetricRow("Manufacturer", MetricValue(snapshot.systemInfo.manufacturer ?: "--"))
            MetricRow("Android", MetricValue(snapshot.systemInfo.androidVersion ?: "--"))
            MetricRow("Build", MetricValue(snapshot.systemInfo.buildNumber?.take(36) ?: "--"))
            MetricRow("Security Patch", MetricValue(snapshot.systemInfo.securityPatch ?: "--"), null, onTap)
            MetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(40) ?: "--"), "kernelVersion", onTap)
            DualMetricRow("SELinux", MetricValue(if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive"), "Resolution", MetricValue(snapshot.systemInfo.resolution ?: "--"), "selinuxStatus", null, onTap)
            DualMetricRow("Density", MetricValue(snapshot.systemInfo.densityDpi ?: "--"), "Uptime", MetricValue(formatUptime(snapshot.systemInfo.uptimeMs)))
            Row(Modifier.fillMaxWidth()) {
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

// ─── Power ───────────────────────────────────────────────

@Composable
fun PowerTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    onTap: (String) -> Unit
) {
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
            val level = snapshot.power.batteryLevel ?: 0
            MetricRow("Level", MetricValue("$level%", level.toDouble(), BatteryThresholds.levelPercent, inverted = true), "batteryLevel", onTap)
            BarGauge("Charge", level.toLong(), 100L, warnThreshold = 0.2, critThreshold = 0.05)
            DualMetricRow("Health", MetricValue(snapshot.power.batteryHealth ?: "--"), "Temp", MetricValue("${String.format("%.1f", snapshot.power.batteryTemperature ?: 0f)}°C", snapshot.power.batteryTemperature?.toDouble(), BatteryThresholds.temperatureCelsius), "batteryHealth", null, onTap)
            val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
            val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
            val avgMa = snapshot.power.batteryCurrentAverageMicroamps?.div(1000)
            if (chargeMa > 0) MetricRow("Charge Rate", MetricValue("${chargeMa} mA"), "chargeRate", onTap)
            if (drainMa > 0) MetricRow("Discharge Rate", MetricValue("${drainMa} mA"))
            if (avgMa != null && avgMa != 0) MetricRow("Avg Current", MetricValue("${avgMa} mA"))
            snapshot.power.batteryChargeCounterMicroAh?.let { MetricRow("Charge Counter", MetricValue("${it / 1000} mAh")) }
            snapshot.power.estimatedTimeRemainingMs?.let { MetricRow("Est. Remaining", MetricValue(formatUptime(it))) }
        }

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
                if (snapshot.shizukuAvailable) {
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

        // MODE tier — Shizuku guidance lives in Settings, no nag banner here.
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

        // TOP PROCESSES — from ActivityManager, available even without Shizuku.
        TierCard("TOP PROCESSES") {
            snapshot.processes.topMemoryProcesses.take(5).forEach { p ->
                MetricRow(
                    "${p.name.take(24)}",
                    MetricValue("${String.format("%.1f", p.memoryBytes?.div(1_000_000.0) ?: 0.0)} MB"),
                    indent = true
                )
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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

// ─── SIM ─────────────────────────────────────────────────

@Composable
fun SimTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    onTap: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // No-SIM = no slot has a card present (slots always emit per physical
        // slot, so an empty list is not the trigger).
        val anyCard = snapshot.simStatus.slots.any { s ->
            !s.carrierName.isNullOrEmpty() && s.carrierName != "Empty"
        }
        if (!anyCard) {
            TierCard("NO SIM") {
                MetricRow("Status", MetricValue("No SIM cards detected"))
            }
            return@Column
        }

        snapshot.simStatus.slots.forEachIndexed { i, slot ->
            val provisioned = slot.isProvisioned
            val disabled = !provisioned && slot.subscriptionId == null && slot.carrierName != "Empty"

            TierCard("SIM ${i + 1} — ${slot.carrierName ?: "Empty slot"}") {
                DualMetricRow("Carrier", MetricValue(slot.carrierName ?: "--"), "Provisioned", MetricValue(if (provisioned) "YES" else "NO"), "simProvisioning", "simProvisioning", onTap)
                MetricRow("eSIM Profiles", MetricValue("${slot.esimProfileCount}"), "esimProfiles", onTap)
                slot.iccidLast4?.let { MetricRow("ICCID (last 4)", MetricValue("•••• •••• •••• $it")) }
                if (disabled) {
                    Spacer(Modifier.height(2.dp))
                    Chip("DISABLED", TerminalAccentYellow)
                }
            }

            TierCard("IDENTITY") {
                MetricRow("Home PLMN", MetricValue(slot.homePlmn ?: "--"))
                MetricRow("SPN (SIM Name)", MetricValue(slot.simOperatorName ?: "--"))
                MetricRow("MSISDN", MetricValue(slot.msisdn ?: "N/A (carrier)"))
                MetricRow("IMSI", MetricValue(slot.imsiPrefix ?: "N/A (priv. API 29+)"))
            }

            TierCard("CAPABILITIES") {
                DualMetricRow("VoLTE", MetricValue(if (slot.volteEnabled == true) "ON" else if (slot.volteEnabled == false) "OFF" else "?"), "WiFi Call", MetricValue(slot.wifiCallingStatus ?: "?"))
                DualMetricRow("NR-NSA", MetricValue(if (slot.nrNsaAvailable == true) "YES" else if (slot.nrNsaAvailable == false) "NO" else "?"), "IMS Reg", MetricValue(if (slot.imsRegistered == true) "REG" else if (slot.imsRegistered == false) "NO" else "?"))
                DualMetricRow("Pref NW", MetricValue(slot.preferredNetworkType ?: "--"), "Call State", MetricValue(slot.callState ?: "--"))
            }

            TierCard("SIGNAL") {
                MetricRow("Network", MetricValue(slot.dataNetworkType ?: "--"))
                MetricRow("RSRP", MetricValue(if (slot.rsrp != null) "${slot.rsrp} dBm" else "--", slot.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
                MetricRow("Roaming", MetricValue(if (slot.isRoaming == true) "YES" else if (slot.isRoaming == false) "NO" else "?"))
            }
        }

        // Current connection
        TierCard("CURRENT CONNECTION") {
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
        Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("No signal data", color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 10.sp)
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
    // Active fills: ok (≥4) / warn (3) / crit (≤2); inactive = border.
    val barColor = when {
        bars >= 4 -> TerminalAccentGreen
        bars >= 3 -> TerminalAccentYellow
        else -> TerminalAccentRed
    }
    // Actual RAT label, not hardcoded "LTE".
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
                    .width(4.dp)
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

/** Compact location-denied hint for Overview's SIGNAL tier. */
@Composable
fun LocationRequiredRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Location required for cell metrics",
            color = TerminalAccentYellow,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Chip("Open Settings", TerminalAccentYellow)
    }
}

/** Location-denied banner (Radio tab): title + body + Open Settings pill. */
@Composable
fun LocationRequiredBanner() {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(TerminalAccentYellow.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .background(TerminalAccentYellow.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Location required for cell metrics", color = TerminalAccentYellow, fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("Radio needs location access to read cell identity. Other tiers still render.", color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 10.5.sp)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentBlue, contentColor = TerminalBackground)
        ) {
            Text("Open Settings", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Settings ────────────────────────────────────────────

@Composable
fun SettingsScreen(snapshot: MetricSnapshot) {
    val context = LocalContext.current
    val runtimePermissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION to "Location (cell metrics)",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Approximate location",
        Manifest.permission.NEARBY_WIFI_DEVICES to "Nearby devices",
        "android.permission.READ_SIM_STATE" to "SIM info",
        Manifest.permission.READ_PHONE_STATE to "Phone state",
        Manifest.permission.READ_BASIC_PHONE_STATE to "Basic phone state",
        Manifest.permission.READ_PRECISE_PHONE_STATE to "Precise phone (PCI/IMS)"
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TierCard("SHIZUKU", liveReadout = if (snapshot.shizukuAvailable) "LIVE" else null, liveColor = TerminalAccentCyan) {
            val transport = DroidscanApp.instance.telemetryCollector.transport()
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRow("Status", MetricValue(if (snapshot.shizukuAvailable) "Available — shell-level data" else "Not available"), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Chip(
                    when (transport) {
                        "ROOT" -> "ROOT"
                        "SHIZUKU" -> "POWER MODE"
                        else -> "STANDARD"
                    },
                    if (snapshot.shizukuAvailable) TerminalAccentCyan else TerminalTextSecondary
                )
            }
            if (!snapshot.shizukuAvailable) {
                Spacer(Modifier.height(4.dp))
                MetricRow("How", MetricValue("Open Shizuku → start server → return"))
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:moe.shizuku.privileged.api"))
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentCyan.copy(alpha = 0.18f), contentColor = TerminalAccentCyan)
                ) {
                    Text("Open Shizuku", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        TierCard("BACKGROUND") {
            val serviceRunning = TelemetryService.isRunning(context)
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRow("Foreground service", MetricValue(if (serviceRunning) "RUNNING" else "STOPPED"), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Chip(if (serviceRunning) "ON" else "OFF", if (serviceRunning) TerminalAccentGreen else TerminalTextSecondary)
            }
            MetricRow("Battery exemption", MetricValue(if (batteryExempt) "GRANTED" else "NOT SET"))
            Spacer(Modifier.height(4.dp))
            MetricRow(
                "Hint",
                MetricValue("Keep collection alive in the background; disable battery optimization to survive OEM kills (OnePlus is aggressive).")
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (TelemetryService.isRunning(context)) TelemetryService.stop(context)
                        else TelemetryService.start(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serviceRunning) TerminalAccentRed.copy(alpha = 0.18f) else TerminalAccentGreen.copy(alpha = 0.18f),
                        contentColor = if (serviceRunning) TerminalAccentRed else TerminalAccentGreen
                    )
                ) {
                    Text(if (serviceRunning) "Stop" else "Start", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentBlue, contentColor = TerminalBackground)
                ) {
                    Text("Battery opt-out", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        TierCard("EXPORT") {
            MetricRow("Diagnostic report", MetricValue("Full telemetry snapshot as text"))
            MetricRow("Includes", MetricValue("radio · power · thermal · storage · GNSS · IMS"))
            Spacer(Modifier.height(4.dp))
            MetricRow("Privacy", MetricValue("Report contains location-derived data — review before sharing"))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    try {
                        val report = DroidscanApp.instance.telemetryCollector.buildDebugDump(snapshot)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "droidscan diagnostic report — ${snapshot.systemInfo.deviceModel} ${snapshot.systemInfo.androidVersion}")
                            putExtra(Intent.EXTRA_TEXT, report)
                            clipData = android.content.ClipData.newPlainText("droidscan_report", report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share diagnostic report"))
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentCyan.copy(alpha = 0.18f), contentColor = TerminalAccentCyan)
            ) {
                Text("Share report", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        TierCard("PERMISSIONS") {
            runtimePermissions.forEach { (perm, label) ->
                val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricRow(label, MetricValue(if (granted) "GRANTED" else "DENIED"), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Chip(if (granted) "OK" else "OFF", if (granted) TerminalAccentGreen else TerminalAccentRed)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentBlue, contentColor = TerminalBackground)
            ) {
                Text("Open app settings", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        TierCard("ABOUT") {
            MetricRow("Version", MetricValue("1.0.0"))
            MetricRow("Privacy", MetricValue("All data stays on-device"))
        }
    }
}

// ─── Glossary Bottom Sheet ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossarySheet(
    entry: GlossaryEntry,
    liveValue: Pair<MetricValue, Color?>?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TerminalSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            // Title: term (bold, cyan) + full acronym (expansion).
            Text(entry.label, color = TerminalAccentBlue, fontFamily = FontFamily.SansSerif, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (entry.expansion.isNotEmpty()) {
                Text(entry.expansion, color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 0.05.sp)
            }

            Spacer(Modifier.height(16.dp))
            SheetSection("WHAT IT MEASURES") { Text(entry.whatItMeasures, color = TerminalTextPrimary, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp) }

            Spacer(Modifier.height(14.dp))
            SheetSection("CURRENT CONTEXT") {
                // Live value + tier dot when resolvable; else the static guidance.
                if (liveValue != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = liveValue.second ?: TerminalTextSecondary
                        Box(Modifier.size(7.dp).background(dotColor, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(liveValue.first.text, color = dotColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(entry.currentValueContext, color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp)
            }

            Spacer(Modifier.height(14.dp))
            SheetSection("HEALTHY RANGE") { Text(entry.healthyRange, color = TerminalTextPrimary, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp) }

            Spacer(Modifier.height(14.dp))
            SheetSection("COMMON CAUSES") { Text(entry.commonCauses, color = TerminalTextPrimary, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp) }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = TerminalAccentBlue, fontFamily = FontFamily.SansSerif, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.09.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

// ─── Band / frequency display helpers ────────────────────

/** Formats a MHz value as "806", "2110", "3316.67" (no trailing .00). */
fun formatMhz(freqMhz: Double): String {
    val s = "%.2f".format(freqMhz).trimEnd('0').trimEnd('.')
    return "$s MHz"
}

/**
 * Best-effort human label for the serving band. Prefers the 3GPP-derived
 * band+frequency ("n78 @ 3316.67 MHz"), falls back to the raw CellIdentity
 * band list ("B3, B7"), then "--".
 */
fun radioBandDisplay(radio: RadioSnapshot): String {
    val knownLte = radio.activeBands.firstOrNull { it.startsWith("B") }
    val knownNr = radio.activeBands.firstOrNull { it.startsWith("n") }
    val earfcn = radio.earfcn
    val nrarfcn = radio.nrarfcn
    val bandInfo = when {
        earfcn != null -> lteBandInfo(earfcn, knownLte)
        nrarfcn != null -> nrBandInfo(nrarfcn, knownNr)
        else -> null
    }
    return bandInfo?.let { "${it.band} @ ${formatMhz(it.dlFrequencyMhz)}" }
        ?: radio.activeBands.joinToString(", ").ifEmpty { "--" }
}

// ─── Glossary Loader + live-value resolution ────────────

fun loadGlossary(context: Context): List<GlossaryEntry> {
    return try {
        val json = context.assets.open("glossary.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("metrics")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GlossaryEntry(
                id = obj.getString("id"), label = obj.getString("label"), section = obj.getString("section"),
                whatItMeasures = obj.getString("whatItMeasures"), currentValueContext = obj.getString("currentValueContext"),
                healthyRange = obj.getString("healthyRange"), commonCauses = obj.getString("commonCauses"),
                expansion = obj.optString("expansion", "")
            )
        }
    } catch (_: Exception) { emptyList() }
}

/**
 * Resolves a live value (text + status color) for a tapped glossary metric, so
 * the bottom sheet can show "now" alongside the static guidance. Unknown ids
 * return null → sheet renders the static context only.
 */
fun resolveLiveValue(id: String, s: MetricSnapshot): Pair<MetricValue, Color?>? = when (id) {
    "rsrp" -> MetricValue("${s.radio.rsrp ?: "--"} dBm", s.radio.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true).let { it to statusColorOf(it) }
    "rsrq" -> MetricValue("${s.radio.rsrq ?: "--"} dB", s.radio.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true).let { it to statusColorOf(it) }
    "sinr" -> MetricValue("${s.radio.sinr ?: "--"} dB", s.radio.sinr?.toDouble(), RadioThresholds.sinr, inverted = true).let { it to statusColorOf(it) }
    "dataNetworkType" -> MetricValue(s.radio.dataNetworkType ?: "--") to null
    "activeBands" -> MetricValue(s.radio.activeBands.joinToString(", ").ifEmpty { "--" }) to null
    "isRoaming" -> MetricValue(if (s.radio.isRoaming == true) "YES" else if (s.radio.isRoaming == false) "NO" else "--") to null
    "imsRegistered" -> MetricValue(s.ims.perSub.firstOrNull()?.registrationState
        ?: if (s.radio.imsRegistered == true) "REG" else if (s.radio.imsRegistered == false) "NO" else "--") to null
    "ecid" -> MetricValue(s.radio.cellId?.toString() ?: "--") to null
    "pci" -> MetricValue(s.radio.pci?.toString() ?: "--") to null
    "earfcn" -> MetricValue(s.radio.earfcn?.toString() ?: "--") to null
    "nrarfcn" -> MetricValue(s.radio.nrarfcn?.toString() ?: "--") to null
    "bandwidth" -> MetricValue(s.radio.bandwidthKhz?.div(1000f)?.let { "${it} MHz" } ?: "--") to null
    "ssRsrp" -> MetricValue("${s.radio.ssRsrp ?: "--"} dBm", s.radio.ssRsrp?.toDouble(), RadioThresholds.rsrp, inverted = true).let { it to statusColorOf(it) }
    "cn0" -> MetricValue(s.gnss.satellites.maxOfOrNull { it.cn0DbHz }?.let { "%.1f dB-Hz".format(it) } ?: "--") to null
    "gnss" -> MetricValue("${s.gnss.satellites.size} vis · ${s.gnss.satellites.count { it.usedInFix }} fix") to null
    "batteryLevel" -> MetricValue("${s.power.batteryLevel ?: "--"}%", s.power.batteryLevel?.toDouble(), BatteryThresholds.levelPercent, inverted = true).let { it to statusColorOf(it) }
    "chargeRate" -> MetricValue("${s.power.chargeRateMicroamps?.div(1000) ?: 0} mA") to null
    "cpuLoad" -> MetricValue(s.processes.perCoreLoad.maxOrNull()?.let { "${(it * 100).toInt()}%" } ?: "--") to null
    "memoryPressure" -> MetricValue(s.processes.memoryPressure.name) to null
    "temperature" -> MetricValue("${String.format("%.1f", s.thermal.maxTemperature ?: 0f)}°C", s.thermal.maxTemperature?.toDouble(), ThermalThresholds.zoneTemp).let { it to statusColorOf(it) }
    "kernelVersion" -> MetricValue(s.systemInfo.kernelVersion?.take(30) ?: "--") to null
    "selinuxStatus" -> MetricValue(if (s.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive") to null
    "uptime" -> MetricValue(formatUptime(s.systemInfo.uptimeMs)) to null
    "activeNetwork" -> MetricValue(s.networkConfig.activeNetworkType ?: "--") to null
    "ipAddress" -> MetricValue(s.networkConfig.ipv4Addresses.firstOrNull() ?: "--") to null
    "simProvisioning" -> MetricValue(s.simStatus.slots.filter { it.isProvisioned }.size.toString()) to null
    "esimProfiles" -> MetricValue(s.simStatus.slots.sumOf { it.esimProfileCount }.toString()) to null
    else -> null
}

/** Status color for a MetricValue (used by the sheet's live-value dot). */
private fun statusColorOf(v: MetricValue): Color? {
    if (v.numeric == null || v.threshold == null) return null
    val tier = if (v.inverted) evaluateStatusInverted(v.numeric, v.threshold)
               else evaluateStatus(v.numeric, v.threshold)
    return when (tier) {
        StatusTier.GREEN -> TerminalAccentGreen
        StatusTier.YELLOW -> TerminalAccentYellow
        StatusTier.RED -> TerminalAccentRed
    }
}
