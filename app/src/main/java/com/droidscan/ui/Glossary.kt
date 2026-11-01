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

data class GlossaryEntry(
    val id: String, val label: String, val section: String,
    val whatItMeasures: String, val currentValueContext: String,
    val healthyRange: String, val commonCauses: String,
    val expansion: String = ""
)

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
