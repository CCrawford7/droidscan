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

enum class DashboardTab(val label: String, val icon: String) {
    OVERVIEW("Overview", "⌂"),
    SIGNAL("Signal", "◉"),
    SYSTEM("System", "⚙"),
    POWER("Power", "⚡"),
    SIM("SIM", "○"),
    DIAGNOSIS("Diagnosis", "✚︎")
}

data class GlossaryEntry(
    val id: String, val label: String, val section: String,
    val whatItMeasures: String, val currentValueContext: String,
    val healthyRange: String, val commonCauses: String,
    val expansion: String = ""
)

/** Telemetry emits at 1s cadence — 8 missed ticks means the loop is stuck. */
private const val STALE_AFTER_SECONDS = 8L

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

    // ── Telemetry liveness ──────────────────────────────
    // The collection loop lives in DroidscanApp for the process lifetime, so a
    // silent freeze means the loop died (unhandled collector error). Track the
    // last arrival and surface a restart affordance when updates stop.
    var hasReceivedSnapshot by remember { mutableStateOf(false) }
    var lastSnapshotAtMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(telemetryFlow) {
        telemetryFlow.collectLatest { snapshot ->
            currentSnapshot = snapshot
            lastSnapshotAtMs = System.currentTimeMillis()
            hasReceivedSnapshot = true
        }
    }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val secondsSinceLast = ((nowMs - lastSnapshotAtMs) / 1000L).coerceAtLeast(0L)
    val isStale = hasReceivedSnapshot && secondsSinceLast > STALE_AFTER_SECONDS

    // Sampled persisted history for the trend charts — shared by every tab so
    // the encrypted file is read+decrypted by exactly one loop.
    val trendHistory = rememberTrendHistory()

    Scaffold(
        containerColor = Color.Transparent,
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
                .background(TerminalBackgroundGradient)
                .padding(padding)
        ) {
            if (showSettings) {
                SettingsScreen(currentSnapshot)
            } else {
                Column(Modifier.fillMaxSize()) {
                    // Telemetry liveness strip: "collecting…" until the first
                    // tick, then a red stall + Restart if updates stop.
                    TelemetryStatusStrip(
                        hasReceived = hasReceivedSnapshot,
                        isStale = isStale,
                        secondsSinceLast = secondsSinceLast
                    )
                    Box(Modifier.weight(1f)) {
                        val contentPadding = Modifier.padding(horizontal = 14.dp)
                        when (selectedTab) {
                            DashboardTab.OVERVIEW -> OverviewTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                            DashboardTab.SIGNAL -> SignalTab(currentSnapshot, glossary, contentPadding, trendHistory) { tappedMetricId = it }
                            DashboardTab.SYSTEM -> SystemTab(currentSnapshot, glossary, contentPadding, trendHistory) { tappedMetricId = it }
                            DashboardTab.POWER -> PowerTab(currentSnapshot, glossary, contentPadding, trendHistory) { tappedMetricId = it }
                            DashboardTab.SIM -> SimTab(currentSnapshot, glossary, contentPadding) { tappedMetricId = it }
                            DashboardTab.DIAGNOSIS -> DiagnosisTab(currentSnapshot, contentPadding)
                        }
                    }
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
    val proFeatures by DroidscanApp.instance.proFeaturesEnabled.collectAsState()
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
                if (proFeatures && snapshot.shizukuAvailable) Chip("POWER MODE", TerminalAccentCyan)
            }
        },
        actions = {
            // Live status readout — signal tier + battery, glanceable at a look.
            val rsrp = snapshot.radio.rsrp
            val signalColor = when (rsrp?.let { evaluateStatusInverted(it.toDouble(), RadioThresholds.rsrp) }) {
                StatusTier.GREEN -> TerminalAccentGreen
                StatusTier.YELLOW -> TerminalAccentYellow
                StatusTier.RED -> TerminalAccentRed
                null -> TerminalTextSecondary
            }
            Text(
                "${snapshot.radio.dataNetworkType ?: "—"} · ${rsrp ?: "—"} dBm",
                color = signalColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${snapshot.power.batteryLevel ?: "—"}%",
                color = TerminalTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(2.dp))
            // Gear → Settings (48dp touch target).
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Open settings" }
            ) {
                Text("⚙", color = TerminalAccentBlue, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {})
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
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Back" }
            ) {
                Text("←", color = TerminalTextSecondary, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {})
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
            // The Surface paints edge-to-edge behind the gesture handle, but the
            // tappable content must clear it. Without this the labels rendered
            // under the system gesture pill (verified on CPH2709, 42px inset).
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardTab.entries.forEach { tab ->
                    val sel = tab == selectedTab
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .selectable(
                                selected = sel,
                                onClick = { onSelect(tab) },
                                role = Role.Tab
                            )
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
                        // Icon glyph is decorative — suppress so TalkBack reads
                        // only the label ("Radio, tab, 2 of 5, selected").
                        Text(
                            tab.icon,
                            color = if (sel) TerminalAccentBlue else TerminalTextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.clearAndSetSemantics {}
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
// NETWORK & SIGNAL · POWER · SYSTEM — Activity-Ring rework
// (see OverviewRework.kt for the card implementations).

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
        NetworkSignalCard(snapshot, onTap)
        PowerRingCard(snapshot, onTap)
        SystemRingCard(snapshot, onTap)
    }
}

// ─── Signal (deep dive) ──────────────────────────────────
// The hero content is now the Activity-Ring Overview; this tab keeps the
// deep cellular telemetry (trends, cell identity, IMS, GNSS, network config).

@Composable
fun SignalTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
    trendHistory: List<MetricSnapshot>,
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
            // Meta strip: serving-cell summary + live dot (scannable header).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(TerminalAccentGreen, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(7.dp))
                Text(
                    listOfNotNull(
                        snapshot.radio.operatorName,
                        snapshot.radio.dataNetworkType,
                        radioBandDisplay(snapshot.radio).takeIf { it != "--" }
                    ).joinToString(" · ").ifEmpty { "No serving cell" },
                    color = TerminalTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            TierDivider()
            Spacer(Modifier.height(4.dp))
            if (snapshot.radio.cellInfoDenied == true) {
                MetricRow("RSRP", MetricValue("-- dBm"))
                MetricRow("RSRQ", MetricValue("-- dB"))
                MetricRow("SINR", MetricValue("-- dB"))
            } else {
                SignalStrengthBar(snapshot.radio.rsrp, snapshot.radio.dataNetworkType)
                Spacer(Modifier.height(2.dp))
                SignalMeter("RSRQ", snapshot.radio.rsrq, RSRQ_MIN_DB.toFloat(), RSRQ_MAX_DB.toFloat(), "dB", RadioThresholds.rsrq, "rsrq", onTap)
                SignalMeter("SINR", snapshot.radio.sinr, SINR_MIN_DB.toFloat(), SINR_MAX_DB.toFloat(), "dB", RadioThresholds.sinr, "sinr", onTap)
                // NR: sync-signal RSRP/RSRQ/SINR (SS-*) — the canonical 5G field metric.
                if (snapshot.radio.nrarfcn != null || snapshot.radio.dataNetworkType?.startsWith("NR") == true) {
                    MetricRow("SS-RSRP", MetricValue("${snapshot.radio.ssRsrp ?: "--"} dBm", snapshot.radio.ssRsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "ssRsrp", onTap)
                    MetricRow("SS-RSRQ", MetricValue("${snapshot.radio.ssRsrq ?: "--"} dB", snapshot.radio.ssRsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "ssRsrp", onTap)
                    MetricRow("SS-SINR", MetricValue("${snapshot.radio.ssSinr ?: "--"} dB", snapshot.radio.ssSinr?.toDouble(), RadioThresholds.sinr, inverted = true), "ssRsrp", onTap)
                }
            }
        }

        TrendCard(
            "RSRP TREND", trendRsrp(trendHistory), " dBm", TerminalAccentBlue,
            windowLabel = "30 min", minY = -130f, maxY = -30f
        )
        TrendCard(
            "SINR TREND", trendSinr(trendHistory), " dB", TerminalAccentGreen,
            windowLabel = "30 min", minY = -10f, maxY = 40f
        )

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
    trendHistory: List<MetricSnapshot>,
    onTap: (String) -> Unit
) {
    val proFeatures by DroidscanApp.instance.proFeaturesEnabled.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .then(padding)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Per-core CPU is privilege-gated (/proc/stat). In Standard Mode (Pro
        // features OFF) the card only renders when data is actually available —
        // no "requires privilege" nag. Enable Pro features to see the diagnostic.
        if (proFeatures || snapshot.processes.perCoreLoad.isNotEmpty()) {
            TierCard("CPU", liveReadout = snapshot.processes.perCoreLoad.maxOrNull()?.let { "${(it * 100).toInt()}%" }, liveColor = TerminalAccentCyan) {
                // Non-empty means the bridge read /proc/stat successfully; an idle
                // device legitimately reads all-zero loads and must not be mistaken
                // for a privilege failure.
                val hasCpuData = snapshot.processes.perCoreLoad.isNotEmpty()
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
        }

        TierCard("MEMORY") {
            BarGauge("RAM", snapshot.processes.usedMemoryBytes, snapshot.processes.totalMemoryBytes)
            MetricRow("Pressure", MetricValue(memoryPressureLabel(snapshot.processes.memoryPressure), snapshot.processes.usedMemoryBytes?.toDouble()?.div(snapshot.processes.totalMemoryBytes?.toDouble() ?: 1.0), MemoryThresholds.pressure), "memoryPressure", onTap)
            snapshot.processes.topMemoryProcesses.take(4).forEach { p ->
                MetricRow("${p.name.take(22)}", MetricValue(p.memoryBytes?.let { formatBytes(it) } ?: UNKNOWN_VALUE), indent = true)
            }
        }

        TrendCard(
            "CPU LOAD TREND", trendCpuLoad(trendHistory), "%", TerminalAccentCyan,
            windowLabel = "30 min", minY = 0f, maxY = 100f,
            formatValue = { v -> "${(v).toInt()}%" }
        )
        TrendCard(
            "RAM USED TREND", trendRamUsedGiB(trendHistory), " GiB", TerminalAccentBlue,
            windowLabel = "30 min", minY = 0f
        )

        if (proFeatures && snapshot.processes.kernelWakelocks.isNotEmpty()) {
            TierCard("WAKELOCKS (Power Mode)") {
                snapshot.processes.kernelWakelocks.take(5).forEach {
                    MetricRow("${it.take(24)}", MetricValue("active"), indent = true)
                }
            }
        }

        TierCard("STORAGE") {
            val used = snapshot.storage.internalUsedBytes
            val free = snapshot.storage.internalFreeBytes
            BarGauge("Internal", used, if (used != null && free != null) used + free else null)
            snapshot.storage.topConsumers.take(4).forEach { c ->
                MetricRow("${c.label.take(22)}", MetricValue(formatBytes(c.sizeBytes)), indent = true)
            }
            if (snapshot.storage.externalMounted) MetricRow("External", MetricValue(snapshot.storage.externalFreeBytes?.let { formatBytes(it) + " free" } ?: UNKNOWN_VALUE))
        }

        // Deep storage / wear — dumpsys diskstats via the T2 shell bridge.
        // Hidden in Standard Mode (Pro features OFF); reveals with its existing
        // degraded state so we can still test it.
        if (proFeatures) {
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
        }

        TierCard("DEVICE") {
            MetricRow("Model", MetricValue(snapshot.systemInfo.deviceModel ?: "--"))
            MetricRow("Manufacturer", MetricValue(snapshot.systemInfo.manufacturer ?: "--"))
            MetricRow("Android", MetricValue(snapshot.systemInfo.androidVersion ?: "--"))
            MetricRow("Build", MetricValue(snapshot.systemInfo.buildNumber?.take(36) ?: "--"))
            MetricRow("Security Patch", MetricValue(snapshot.systemInfo.securityPatch ?: "--"), null, onTap)
            MetricRow("Kernel", MetricValue(snapshot.systemInfo.kernelVersion?.take(40) ?: "--"), "kernelVersion", onTap)
            DualMetricRow("SELinux", MetricValue(if (snapshot.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive"), "Resolution", MetricValue(snapshot.systemInfo.resolution ?: "--"), "selinuxStatus", null, onTap)
            DualMetricRow("Density", MetricValue(snapshot.systemInfo.densityDpi ?: "--"), "Uptime", MetricValue(formatUptimeOrUnknown(snapshot.systemInfo.uptimeMs)))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "(tap uptime to dump snapshot)",
                    color = TerminalTextSecondary.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clickable { DroidscanApp.instance.telemetryCollector.debugDump(snapshot) }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Dump telemetry snapshot to logcat"
                        }
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
                MetricRow("IMSI", MetricValue(slot.imsiPrefix ?: "N/A (privilege-restricted)"))
            }

            TierCard("CAPABILITIES") {
                DualMetricRow("VoLTE", MetricValue(if (slot.volteEnabled == true) "ON" else if (slot.volteEnabled == false) "OFF" else "?"), "WiFi Call", MetricValue(slot.wifiCallingStatus ?: "?"))
                DualMetricRow("NR-NSA", MetricValue(if (slot.nrNsaAvailable == true) "YES" else if (slot.nrNsaAvailable == false) "NO" else "?"), "IMS Reg", MetricValue(if (slot.imsRegistered == true) "REG" else if (slot.imsRegistered == false) "NO" else "?"))
                DualMetricRow("IMS Capable", MetricValue(if (slot.imsCapable == true) "YES" else if (slot.imsCapable == false) "NO" else "?"), "Pref NW", MetricValue(slot.preferredNetworkType ?: "--"))
                DualMetricRow("Call State", MetricValue(slot.callState ?: "--"), "Roaming", MetricValue(if (slot.isRoaming == true) "YES" else if (slot.isRoaming == false) "NO" else "?"))
            }

            TierCard("SIGNAL") {
                MetricRow("Network", MetricValue(slot.dataNetworkType ?: "--"))
                MetricRow("RSRP", MetricValue(if (slot.rsrp != null) "${slot.rsrp} dBm" else "--", slot.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
                MetricRow("RSRQ", MetricValue(if (slot.rsrq != null) "${slot.rsrq} dB" else "--", slot.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "rsrq", onTap)
                MetricRow("SINR", MetricValue(if (slot.sinr != null) "${slot.sinr} dB" else "--", slot.sinr?.toDouble(), RadioThresholds.sinr, inverted = true), "sinr", onTap)
                MetricRow("Band", MetricValue(slot.band ?: "--"), "activeBands", onTap)
                DualMetricRow("PCI", MetricValue(slot.pci?.toString() ?: "--"), "TAC", MetricValue(slot.tac?.toString() ?: "--"), "pci", null, onTap)
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

// ─── Telemetry liveness strip ───────────────────────────
// Thin strip under the top bar. Renders nothing when healthy; shows a
// "collecting…" affordance until the first snapshot, and a red stall strip
// with a Restart action if the collection loop dies.

@Composable
private fun TelemetryStatusStrip(
    hasReceived: Boolean,
    isStale: Boolean,
    secondsSinceLast: Long
) {
    when {
        !hasReceived -> StatusStrip(
            text = "Collecting telemetry…",
            color = TerminalAccentCyan
        )
        isStale -> StatusStrip(
            text = "Telemetry stalled — no update for ${secondsSinceLast}s",
            color = TerminalAccentRed,
            actionLabel = "Restart",
            onAction = { DroidscanApp.instance.restartTelemetry() }
        )
    }
}

@Composable
private fun StatusStrip(
    text: String,
    color: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (actionLabel != null) "$text, $actionLabel" else text
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = color,
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = color,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

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
    // Merged semantics: the bars are decorative Boxes, so one concise spoken
    // summary replaces the glyph noise for TalkBack.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$ratLabel, $bars of 5 signal bars, ${rsrp} dBm"
        }
    ) {
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
        // Text block announced as one live region; the Button stays an
        // independent focusable target.
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        ) {
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
    "batteryLevel" -> MetricValue(s.power.batteryLevel?.let { "$it%" } ?: UNKNOWN_VALUE, s.power.batteryLevel?.toDouble(), BatteryThresholds.levelPercent, inverted = true).let { it to statusColorOf(it) }
    "chargeRate" -> MetricValue(formatMilliamps(s.power.chargeRateMicroamps)) to null
    "cpuLoad" -> MetricValue(s.processes.perCoreLoad.maxOrNull()?.let { "${(it * 100).toInt()}%" } ?: "--") to null
    "memoryPressure" -> MetricValue(memoryPressureLabel(s.processes.memoryPressure)) to null
    "temperature" -> MetricValue(formatCelsius(s.thermal.maxTemperature), s.thermal.maxTemperature?.toDouble(), ThermalThresholds.zoneTemp).let { it to statusColorOf(it) }
    "kernelVersion" -> MetricValue(s.systemInfo.kernelVersion?.take(30) ?: "--") to null
    "selinuxStatus" -> MetricValue(if (s.systemInfo.selinuxEnforced == true) "Enforced" else "Permissive") to null
    "uptime" -> MetricValue(formatUptimeOrUnknown(s.systemInfo.uptimeMs)) to null
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
