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

        SpeedTestCard()

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

