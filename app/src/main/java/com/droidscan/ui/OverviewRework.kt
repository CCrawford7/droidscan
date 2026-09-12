package com.droidscan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
    var showRingExplainer by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Left: ring cluster + tiny legend. The rings are decorative graphics;
        // the whole cluster reads as one spoken sentence for TalkBack. Tapping
        // the cluster opens a plain-language explainer (what each ring is +
        // what the current values imply); each metric drills into the full
        // glossary sheet.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                val ringInteraction = remember { MutableInteractionSource() }
                ConcentricRings(
                    rings = rings,
                    size = 96.dp,
                    thickness = 8.dp,
                    gap = 3.dp,
                    modifier = Modifier
                        .clickable(interactionSource = ringInteraction, indication = null) { showRingExplainer = true }
                        .semantics(mergeDescendants = true) {
                            contentDescription = signalAccessibilityLabel(simLabel, rsrp, rsrq, sinr) + " Tap for explanation."
                        }
                )
                if (showRingExplainer) {
                    Popup(
                        onDismissRequest = { showRingExplainer = false },
                        alignment = Alignment.Center,
                        properties = PopupProperties(focusable = true)
                    ) {
                        SignalRingExplainer(
                            simLabel = simLabel,
                            rsrp = rsrp,
                            rsrq = rsrq,
                            sinr = sinr,
                            onDismiss = { showRingExplainer = false },
                            onGlossary = { id ->
                                showRingExplainer = false
                                onTap(id)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "RSRP · RSRQ · SINR",
                color = TerminalTextSecondary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
            )
            Text(
                "tap for explanation",
                color = TerminalAccentBlue.copy(alpha = 0.75f),
                fontFamily = TerminalBody,
                fontSize = 9.sp
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
    // No reading is not the same as 0%. Coercing null to 0 rendered a crit-red
    // ring and announced "Battery level 0 percent, critical" on a device that
    // simply hadn't reported yet.
    val level = snapshot.power.batteryLevel
    val drainMa = snapshot.power.currentDrainMicroamps?.div(1000)
    val tier = level?.let { evaluateStatusInverted(it.toDouble(), BatteryThresholds.levelPercent) }
    val ringColor = tier?.toRingColor() ?: TerminalTextSecondary

    TierCard(
        "POWER",
        titleFontFamily = TerminalHeading,
        liveReadout = level?.let { "$it%" } ?: UNKNOWN_VALUE,
        liveColor = ringColor
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRing(
                progress = ringProgress((level ?: 0).toFloat(), 0f, 100f),
                color = ringColor,
                size = 92.dp,
                thickness = 10.dp,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = if (tier != null) {
                        "Battery level $level percent, ${tier.signalWord()}"
                    } else {
                        "Battery level not reported yet"
                    }
                }
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RingReadout(
                    "Temp",
                    formatCelsius(snapshot.power.batteryTemperature),
                    "temperature",
                    onTap
                )
                RingReadout(
                    "Charge",
                    formatMilliamps(snapshot.power.chargeRateMicroamps),
                    "chargeRate",
                    onTap
                )
                if (drainMa != null && drainMa > 0) RingReadout("Drain", "$drainMa mA")
            }
        }
    }
}

// ── Card 3: System ──────────────────────────────────────────

@Composable
fun SystemRingCard(snapshot: MetricSnapshot, onTap: (String) -> Unit) {
    val usedRam = snapshot.processes.usedMemoryBytes
    val totalRam = snapshot.processes.totalMemoryBytes
    TierCard("SYSTEM", titleFontFamily = TerminalHeading, liveReadout = snapshot.systemInfo.androidVersion, liveColor = TerminalTextSecondary) {
        BarGauge("RAM", usedRam, totalRam)
        Spacer(Modifier.height(2.dp))
        RingReadout("Pressure", memoryPressureLabel(snapshot.processes.memoryPressure), "memoryPressure", onTap)
        RingReadout("Uptime", formatUptimeOrUnknown(snapshot.systemInfo.uptimeMs), "uptime", onTap)
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

// ── Ring explainer tooltip ─────────────────────────────────
// Tapping the ring cluster opens this anchored card. Each metric gets the
// simplest-possible explanation PLUS a live "right now" implication line;
// tapping a row dismisses the popup and opens the full glossary sheet.

@Composable
private fun SignalRingExplainer(
    simLabel: String,
    rsrp: Int?,
    rsrq: Int?,
    sinr: Int?,
    onDismiss: () -> Unit,
    onGlossary: (String) -> Unit
) {
    val (verdict, verdictColor) = overallVerdict(rsrp, rsrq, sinr)
    Surface(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 480.dp),
        shape = RoundedCornerShape(14.dp),
        color = TerminalSurface,
        border = BorderStroke(1.dp, TerminalBorder),
        shadowElevation = 12.dp
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SIGNAL — $simLabel",
                    color = TerminalAccentBlue,
                    fontFamily = TerminalHeading,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "✕",
                    color = TerminalTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                        .semantics { contentDescription = "Close" }
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "What these rings mean, in plain English.",
                color = TerminalTextSecondary,
                fontFamily = TerminalBody,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))

            ExplainerRow(
                name = "RSRP", descriptor = "signal strength",
                explanation = "How loud the tower's signal is at your phone.",
                value = rsrp, unit = "dBm", threshold = RadioThresholds.rsrp,
                glossaryId = "rsrp", onGlossary = onGlossary
            )
            Spacer(Modifier.height(10.dp))
            ExplainerRow(
                name = "RSRQ", descriptor = "signal quality",
                explanation = "How much interference and noise is mixed into that signal.",
                value = rsrq, unit = "dB", threshold = RadioThresholds.rsrq,
                glossaryId = "rsrq", onGlossary = onGlossary
            )
            Spacer(Modifier.height(10.dp))
            ExplainerRow(
                name = "SINR", descriptor = "signal-to-noise",
                explanation = "Useful signal versus noise — the best gauge of real-world speed.",
                value = sinr, unit = "dB", threshold = RadioThresholds.sinr,
                glossaryId = "sinr", onGlossary = onGlossary
            )

            Spacer(Modifier.height(12.dp))
            TierDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                verdict,
                color = verdictColor,
                fontFamily = TerminalBody,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap any metric for the full breakdown.",
                color = TerminalTextSecondary.copy(alpha = 0.7f),
                fontFamily = TerminalBody,
                fontSize = 10.sp
            )
        }
    }
}

/** One metric's explainer block: name + value row, plain explanation, live implication. */
@Composable
private fun ExplainerRow(
    name: String,
    descriptor: String,
    explanation: String,
    value: Int?,
    unit: String,
    threshold: ThresholdRange,
    glossaryId: String,
    onGlossary: (String) -> Unit
) {
    val (implication, color) = liveImplication(value, unit, threshold)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onGlossary(glossaryId) }
            .semantics(mergeDescendants = true) {
                contentDescription = "$name — $descriptor. $explanation $implication"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                "$name — $descriptor",
                color = TerminalTextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                value?.let { "$it $unit" } ?: "--",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            explanation,
            color = TerminalTextSecondary,
            fontFamily = TerminalBody,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Text(
            implication,
            color = color,
            fontFamily = TerminalBody,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

/** "What the current reading implies" — live value + tier verdict in one line. */
private fun liveImplication(value: Int?, unit: String, threshold: ThresholdRange): Pair<String, Color> {
    if (value == null) return "No reading right now — the radio isn't reporting (common when idle)." to TerminalTextSecondary
    return when (evaluateStatusInverted(value.toDouble(), threshold)) {
        StatusTier.GREEN -> "$value $unit — strong, no concern." to TerminalAccentGreen
        StatusTier.YELLOW -> "$value $unit — usable, but watch for slower speeds." to TerminalAccentYellow
        StatusTier.RED -> "$value $unit — weak; expect slow data or dropped calls." to TerminalAccentRed
    }
}

/** Overall takeaway for the cluster — the worst (most critical) live tier. */
private fun overallVerdict(rsrp: Int?, rsrq: Int?, sinr: Int?): Pair<String, Color> {
    val tiers = listOfNotNull(
        rsrp?.let { evaluateStatusInverted(it.toDouble(), RadioThresholds.rsrp) },
        rsrq?.let { evaluateStatusInverted(it.toDouble(), RadioThresholds.rsrq) },
        sinr?.let { evaluateStatusInverted(it.toDouble(), RadioThresholds.sinr) }
    )
    if (tiers.isEmpty()) return "No signal readings right now — data resumes when the radio reports." to TerminalTextSecondary
    return when (tiers.maxBy { it.ordinal }) {
        StatusTier.GREEN -> "Bottom line: your connection is in good shape." to TerminalAccentGreen
        StatusTier.YELLOW -> "Bottom line: usable, but noise is starting to bite." to TerminalAccentYellow
        StatusTier.RED -> "Bottom line: weak — expect slow data or dropped calls." to TerminalAccentRed
    }
}
