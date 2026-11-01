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

