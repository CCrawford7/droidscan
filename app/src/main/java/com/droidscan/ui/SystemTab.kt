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

