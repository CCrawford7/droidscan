package com.droidscan.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.shared.core.*
import com.droidscan.ui.theme.*

// ─────────────────────────────────────────────────────────────
// Overview rework — Apple Watch "Activity Ring" design language.
// Three vertical cards (Network & Signal, Power, System) built from
// reusable ring primitives (Rings.kt). All live text rows are
// tap-to-glossary and use merged semantics (NO liveRegion on the
// 1s-updating values, so TalkBack isn't spammed).
// ─────────────────────────────────────────────────────────────

// ── Card 1: Network & Signal ────────────────────────────────

@Composable
fun NetworkSignalCard(snapshot: MetricSnapshot, onTap: (String) -> Unit) {
    val activeSlots = snapshot.simStatus.slots.filter { it.isUsable() }
    val header = if (activeSlots.isEmpty()) {
        "SIGNAL"
    } else {
        "SIGNAL — " + activeSlots.joinToString(" · ") { "SIM ${it.slotIndex + 1}" }
    }
    TierCard(
        title = header,
        titleFontFamily = TerminalHeading,
        liveReadout = wifiSummary(snapshot.networkConfig),
        liveColor = TerminalAccentCyan
    ) {
        if (snapshot.radio.cellInfoDenied == true) {
            LocationRequiredRow()
            return@TierCard
        }
        if (activeSlots.isEmpty()) {
            MetricRow("Status", MetricValue("No SIM detected"))
            return@TierCard
        }
        activeSlots.forEachIndexed { i, slot ->
            if (i > 0) {
                Spacer(Modifier.height(6.dp))
                TierDivider()
                Spacer(Modifier.height(6.dp))
            }
            SignalBlock(
                slot = slot,
                simLabel = "SIM ${slot.slotIndex + 1}",
                ims = imsFor(snapshot, slot.subscriptionId),
                onTap = onTap
            )
        }
    }
}

/**
 * One SIM's block: concentric RSRP/RSRQ/SINR rings on the left, tight text
 * readouts (Network · Band · IMS · Roaming) anchored on the right. The ring
 * cluster exposes ONE merged accessibility label; the text rows stay plain
 * semantics (updated every second — never a live region).
 */
@Composable
private fun SignalBlock(
    slot: SimSlot,
    simLabel: String,
    ims: ImsRegistration?,
    onTap: (String) -> Unit
) {
    val rsrp = slot.rsrp
    val rsrq = slot.rsrq
    val sinr = slot.sinr
    val rings = listOf(
        RingData("RSRP", ringProgress(rsrp?.toFloat(), RSRP_MIN_DBM.toFloat(), RSRP_MAX_DBM.toFloat()), signalRingColor(rsrp, RadioThresholds.rsrp)),
        RingData("RSRQ", ringProgress(rsrq?.toFloat(), RSRQ_MIN_DB.toFloat(), RSRQ_MAX_DB.toFloat()), signalRingColor(rsrq, RadioThresholds.rsrq)),
        RingData("SINR", ringProgress(sinr?.toFloat(), SINR_MIN_DB.toFloat(), SINR_MAX_DB.toFloat()), signalRingColor(sinr, RadioThresholds.sinr))
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Left: ring cluster + tiny legend. The rings are decorative graphics;
        // the whole cluster reads as one spoken sentence for TalkBack.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ConcentricRings(
                rings = rings,
                size = 96.dp,
                thickness = 8.dp,
                gap = 3.dp,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = signalAccessibilityLabel(simLabel, rsrp, rsrq, sinr)
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "RSRP · RSRQ · SINR",
                color = TerminalTextSecondary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
            )
        }
        Spacer(Modifier.width(16.dp))
        // Right: text readouts.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                simLabel,
                color = TerminalAccentBlue,
                fontFamily = TerminalBody,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            RingReadout("Network", slot.dataNetworkType ?: "--", "dataNetworkType", onTap)
            RingReadout("Band", slot.band ?: "--", "activeBands", onTap)
            RingReadout("IMS", imsStateText(ims, slot), "imsRegistered", onTap, imsStateColor(ims, slot))
            RingReadout("Roaming", roamingText(slot.isRoaming), "isRoaming", onTap, roamingColor(slot.isRoaming))
        }
    }
}

// ── Card 2: Power ───────────────────────────────────────────

@Composable
fun PowerRingCard(snapshot: MetricSnapshot, onTap: (String) -> Unit) {
    val level = snapshot.power.batteryLevel ?: 0
    val temp = snapshot.power.batteryTemperature
    val chargeMa = snapshot.power.chargeRateMicroamps?.div(1000) ?: 0
    val drainMa = snapshot.power.currentDrainMicroamps?.div(1000) ?: 0
    val tier = evaluateStatusInverted(level.toDouble(), BatteryThresholds.levelPercent)
    val ringColor = tier.toRingColor()

    TierCard("POWER", titleFontFamily = TerminalHeading, liveReadout = "$level%", liveColor = ringColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRing(
                progress = ringProgress(level.toFloat(), 0f, 100f),
                color = ringColor,
                size = 92.dp,
                thickness = 10.dp,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Battery level $level percent, ${tier.signalWord()}"
                }
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RingReadout("Temp", String.format("%.1f", temp ?: 0f) + "°C", "temperature", onTap)
                RingReadout("Charge", "$chargeMa mA", "chargeRate", onTap)
                if (drainMa > 0) RingReadout("Drain", "$drainMa mA")
            }
        }
    }
}

// ── Card 3: System ──────────────────────────────────────────

@Composable
fun SystemRingCard(snapshot: MetricSnapshot, onTap: (String) -> Unit) {
    val usedRam = snapshot.processes.usedMemoryBytes ?: 0L
    val totalRam = snapshot.processes.totalMemoryBytes ?: 1L
    TierCard("SYSTEM", titleFontFamily = TerminalHeading, liveReadout = snapshot.systemInfo.androidVersion, liveColor = TerminalTextSecondary) {
        BarGauge("RAM", usedRam, totalRam)
        Spacer(Modifier.height(2.dp))
        RingReadout("Pressure", snapshot.processes.memoryPressure.name, "memoryPressure", onTap)
        RingReadout("Uptime", formatUptime(snapshot.systemInfo.uptimeMs), "uptime", onTap)
        RingReadout("Model", snapshot.systemInfo.deviceModel ?: "--")
        RingReadout("Kernel", snapshot.systemInfo.kernelVersion?.take(30) ?: "--")
    }
}

// ── Shared row / helpers ─────────────────────────────────────

/** Label + value row with optional tap-to-glossary. Values are mono; labels use the body face. */
@Composable
private fun RingReadout(
    label: String,
    value: String,
    glossaryId: String? = null,
    onTap: ((String) -> Unit)? = null,
    valueColor: Color = TerminalTextPrimary
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (glossaryId != null && onTap != null)
                    Modifier.pointerInput(glossaryId) { detectTapGestures { onTap(glossaryId) } }
                else Modifier
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $value"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TerminalTextSecondary, fontFamily = TerminalBody, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun SimSlot.isUsable(): Boolean =
    !carrierName.isNullOrEmpty() && carrierName != "Empty" && carrierName != "Disabled"

private fun imsFor(snapshot: MetricSnapshot, subId: Int?): ImsRegistration? {
    if (subId == null) return null
    return snapshot.ims.perSub.firstOrNull { it.subscriptionId == subId }
}

private fun imsStateText(ims: ImsRegistration?, slot: SimSlot): String {
    val short = when (ims?.registrationState) {
        "REGISTERED" -> "REG"
        "REGISTERING" -> "REG…"
        "NOT REGISTERED" -> "NO"
        else -> null
    }
    if (short != null) return short
    return when {
        slot.imsRegistered == true -> "REG"
        slot.imsRegistered == false -> "NO"
        else -> "--"
    }
}

private fun imsStateColor(ims: ImsRegistration?, slot: SimSlot): Color = when {
    ims?.registrationState == "REGISTERED" -> TerminalAccentGreen
    ims?.registrationState == "REGISTERING" -> TerminalAccentYellow
    ims?.registrationState == "NOT REGISTERED" -> TerminalAccentRed
    slot.imsRegistered == true -> TerminalAccentGreen
    slot.imsRegistered == false -> TerminalAccentRed
    else -> TerminalTextSecondary
}

private fun roamingText(roaming: Boolean?): String = when (roaming) {
    true -> "YES"
    false -> "NO"
    null -> "--"
}

private fun roamingColor(roaming: Boolean?): Color = when (roaming) {
    true -> TerminalAccentYellow
    false -> TerminalAccentGreen
    null -> TerminalTextSecondary
}

/** Ring fill color for an inverted-threshold signal metric; empty → track color. */
private fun signalRingColor(value: Int?, threshold: ThresholdRange): Color {
    if (value == null) return TerminalBorder
    return when (evaluateStatusInverted(value.toDouble(), threshold)) {
        StatusTier.GREEN -> TerminalAccentGreen
        StatusTier.YELLOW -> TerminalAccentYellow
        StatusTier.RED -> TerminalAccentRed
    }
}

/** Compact contextual wifi / active-network line for the SIGNAL card header. */
private fun wifiSummary(nw: NetworkConfigSnapshot): String? {
    val ssid = nw.wifiSsid?.takeIf { it.isNotEmpty() }
    return if (ssid != null) {
        val rssi = nw.wifiRssi?.let { "$it dBm" } ?: ""
        listOfNotNull("wifi", ssid, rssi).joinToString(" · ")
    } else if (nw.activeNetworkType != null) {
        nw.activeNetworkType
    } else {
        null
    }
}

private fun StatusTier.toRingColor(): Color = when (this) {
    StatusTier.GREEN -> TerminalAccentGreen
    StatusTier.YELLOW -> TerminalAccentYellow
    StatusTier.RED -> TerminalAccentRed
}
