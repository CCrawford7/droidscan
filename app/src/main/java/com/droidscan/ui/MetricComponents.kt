package com.droidscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

// ── Typography ──────────────────────────────────────────
// Design rule: labels are SANS (ink2), values are MONO (ink). This split is
// the single biggest typographic change from the old all-mono register.

private val LabelText = FontFamily.SansSerif
private val ValueText = FontFamily.Monospace

// ── Status helpers ──────────────────────────────────────

/** Resolves a value to its status color + tier. No threshold → secondary text. */
private fun statusOf(value: MetricValue): Pair<Color, StatusTier?> {
    if (value.numeric == null || value.threshold == null) return TerminalTextSecondary to null
    val tier = if (value.inverted) evaluateStatusInverted(value.numeric, value.threshold)
               else evaluateStatus(value.numeric, value.threshold)
    return (when (tier) {
        StatusTier.GREEN -> TerminalAccentGreen
        StatusTier.YELLOW -> TerminalAccentYellow
        StatusTier.RED -> TerminalAccentRed
    }) to tier
}

/** Spoken status word for TalkBack (dot is decorative; text carries state). */
private fun StatusTier?.spoken(): String = when (this) {
    StatusTier.GREEN -> "good"
    StatusTier.YELLOW -> "caution"
    StatusTier.RED -> "critical"
    null -> ""
}

// ── TierCard (replaces MetricCard) ──────────────────────
// Load-bearing container. Subtle vertical-gradient fill, 1dp `border` hairline,
// 14dp radius, and a 3dp colored accent bar anchoring the header. Header:
// uppercase cyan sans label + optional right-aligned live readout.

@Composable
fun TierCard(
    title: String,
    liveReadout: String? = null,
    liveColor: Color = TerminalAccentCyan,
    accent: Color = TerminalAccentBlue,
    titleFontFamily: FontFamily = FontFamily.SansSerif,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(TerminalSurface, Color(0xFF0D1728))),
                shape
            )
            .border(1.dp, TerminalBorder, shape)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Section accent — a 3dp colored bar that anchors the card's tier.
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = TerminalAccentBlue,
                fontFamily = titleFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.09.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            liveReadout?.let {
                Text(
                    it,
                    color = liveColor,
                    fontFamily = ValueText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Hairline above an emphasized last row (e.g. Peak overall in Power). */
@Composable
fun TierDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TerminalBorder))
}

// ─── Metric Row (Label + Status Dot + Value) ───────────

data class MetricValue(
    val text: String,
    val numeric: Double? = null,
    val threshold: ThresholdRange? = null,
    val inverted: Boolean = false
)

@Composable
fun MetricRow(
    label: String,
    value: MetricValue,
    glossaryId: String? = null,
    onTap: ((String) -> Unit)? = null,
    indent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (statusColor, tier) = statusOf(value)
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (onTap != null && glossaryId != null)
                Modifier.pointerInput(glossaryId) { detectTapGestures { onTap(glossaryId) } }
            else Modifier
        )
        .semantics(mergeDescendants = true) {
            // Announce label + value + status as ONE utterance for TalkBack.
            val spoken = tier.spoken()
            contentDescription = if (spoken.isNotEmpty()) "$label, ${value.text}, $spoken" else "$label, ${value.text}"
        }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label (sans, ink2)
        Text(
            if (indent) "    $label" else label,
            color = TerminalTextSecondary,
            fontFamily = LabelText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Status dot — decorative (7dp), immediately before the value.
        if (value.threshold != null && value.numeric != null) {
            Canvas(modifier = Modifier.size(7.dp).padding(end = 6.dp)) {
                drawCircle(color = statusColor)
            }
        } else {
            Spacer(Modifier.width(13.dp))
        }

        // Value (mono, ink, 600)
        Text(
            value.text,
            color = statusColor,
            fontFamily = ValueText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Dual Pair (two metrics side-by-side, label above value) ─────────

@Composable
fun DualMetricRow(
    leftLabel: String, leftValue: MetricValue,
    rightLabel: String, rightValue: MetricValue,
    leftGlossaryId: String? = null,
    rightGlossaryId: String? = null,
    onTap: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DualCell(leftLabel, leftValue, leftGlossaryId, onTap, Modifier.weight(1f))
        DualCell(rightLabel, rightValue, rightGlossaryId, onTap, Modifier.weight(1f))
    }
}

@Composable
private fun DualCell(
    label: String,
    value: MetricValue,
    glossaryId: String?,
    onTap: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val (statusColor, tier) = statusOf(value)
    val cellModifier = modifier
        .then(
            if (onTap != null && glossaryId != null)
                Modifier.pointerInput(glossaryId) { detectTapGestures { onTap(glossaryId) } }
            else Modifier
        )
        .semantics(mergeDescendants = true) {
            val spoken = tier.spoken()
            contentDescription = if (spoken.isNotEmpty()) "$label, ${value.text}, $spoken" else "$label, ${value.text}"
        }
    Column(cellModifier) {
        Text(
            label,
            color = TerminalTextSecondary,
            fontFamily = LabelText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.threshold != null && value.numeric != null) {
                Canvas(modifier = Modifier.size(7.dp).padding(end = 6.dp)) {
                    drawCircle(color = statusColor)
                }
            } else {
                Spacer(Modifier.width(13.dp))
            }
            Text(
                value.text,
                color = statusColor,
                fontFamily = ValueText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Bar Gauge ──────────────────────────────────────────

@Composable
fun BarGauge(
    label: String,
    current: Long?,
    total: Long?,
    color: Color = TerminalAccentGreen,
    warnThreshold: Double = 0.8,
    critThreshold: Double = 0.95
) {
    // `known` guards against a missing reading rendering as a confident 0% in
    // crit red — see UNKNOWN_VALUE below.
    val known = current != null && total != null && total > 0L
    val fraction =
        if (current != null && total != null && total > 0L) current.toFloat() / total.toFloat()
        else 0f
    val barColor = when {
        !known -> TerminalBorder
        fraction >= critThreshold -> TerminalAccentRed
        fraction >= warnThreshold -> TerminalAccentYellow
        else -> color
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TerminalTextSecondary, fontFamily = LabelText, fontSize = 11.sp)
            Text(
                current?.let { formatBytes(it) } ?: UNKNOWN_VALUE,
                color = if (known) TerminalTextPrimary else TerminalTextSecondary,
                fontFamily = ValueText,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(3.dp))
        // 6dp tall track, 3dp radius, `border` track.
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(TerminalBorder, size = Size(w, h), cornerRadius = CornerRadius(3f))
            drawRoundRect(barColor, size = Size(w * fraction, h), cornerRadius = CornerRadius(3f))
        }
        Spacer(Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                if (known) "${(fraction * 100).toInt()}%" else UNKNOWN_VALUE,
                color = barColor,
                fontFamily = ValueText,
                fontSize = 10.sp
            )
        }
    }
}

// ─── Chip (status / capability) ─────────────────────────
// 9sp semibold, status-color text on status@~12% tint, 5dp radius.
// Variants: THROTTLING (crit), POWER MODE / SHIZUKU (live), PROVISIONED (ok).

@Composable
fun Chip(text: String, color: Color = TerminalAccentCyan) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            color = color,
            fontFamily = LabelText,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.03.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ─── Sparkline (reserved for v1.1 — not wired) ──────────

@Composable
fun MiniSparkline(
    values: List<Float>,
    color: Color = TerminalAccentGreen,
    height: Int = 24
) {
    if (values.size < 2) return
    Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
        val w = size.width
        val h = size.height
        val max = values.maxOrNull() ?: 1f
        val min = values.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(0.1f)
        val stepX = w / (values.size - 1)

        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - ((v - min) / range * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2f))
    }
}

// ─── Helpers ────────────────────────────────────────────

fun formatBytes(bytes: Long): String {
    return when {
        bytes > 1_000_000_000 -> String.format("%.1fG", bytes / 1_000_000_000.0)
        bytes > 1_000_000 -> String.format("%.1fM", bytes / 1_000_000.0)
        bytes > 1_000 -> String.format("%.1fK", bytes / 1_000.0)
        else -> "$bytes"
    }
}

fun formatUptime(ms: Long): String {
    val sec = ms / 1000
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
}

// ─── Unknown values ─────────────────────────────────────
// A sensor that hasn't reported is NOT a sensor reporting zero. Coercing null
// to 0 produced a red "0%" battery ring that announced "Battery level 0
// percent, critical" on a device that simply had no telemetry yet — a false
// alarm. Anything unknown renders as UNKNOWN_VALUE.

/** Canonical placeholder for telemetry that hasn't arrived. */
const val UNKNOWN_VALUE = "--"

/** Celsius to one decimal, or [UNKNOWN_VALUE] when no sensor reported. */
fun formatCelsius(celsius: Float?): String =
    celsius?.let { String.format("%.1f", it) + "°C" } ?: UNKNOWN_VALUE

/** Microamps as whole milliamps, or [UNKNOWN_VALUE]. */
fun formatMilliamps(microamps: Int?): String =
    microamps?.let { "${it / 1000} mA" } ?: UNKNOWN_VALUE

/**
 * Uptime, or [UNKNOWN_VALUE] before the first snapshot lands. `uptimeMs`
 * defaults to 0 in [SystemInfoSnapshot] and is only ever set from
 * `SystemClock.elapsedRealtime()`, so 0 means "not collected yet" rather than
 * a device that just booted.
 */
fun formatUptimeOrUnknown(ms: Long): String =
    if (ms > 0L) formatUptime(ms) else UNKNOWN_VALUE

/**
 * Memory-pressure label, or [UNKNOWN_VALUE] before the first read. UNKNOWN is
 * the enum's default, so it means "not measured yet" rather than a real state.
 */
fun memoryPressureLabel(pressure: MemoryPressure): String =
    if (pressure == MemoryPressure.UNKNOWN) UNKNOWN_VALUE else pressure.name
