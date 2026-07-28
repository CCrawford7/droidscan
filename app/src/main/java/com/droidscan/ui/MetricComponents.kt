package com.droidscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.shared.core.*
import com.droidscan.ui.theme.*

// ─── Section Divider ────────────────────────────────────

@Composable
fun SectionDivider(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "──",
            color = TerminalAccentBlue,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            title,
            color = TerminalAccentBlue,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "─".repeat(30),
            color = TerminalBorder,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
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
    indent: Boolean = false
) {
    val statusColor = if (value.numeric != null && value.threshold != null) {
        val tier = if (value.inverted) {
            evaluateStatusInverted(value.numeric, value.threshold)
        } else {
            evaluateStatus(value.numeric, value.threshold)
        }
        when (tier) {
            StatusTier.GREEN -> TerminalAccentGreen
            StatusTier.YELLOW -> TerminalAccentYellow
            StatusTier.RED -> TerminalAccentRed
        }
    } else TerminalTextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .then(
                if (onTap != null && glossaryId != null)
                    Modifier.pointerInput(glossaryId) { detectTapGestures { onTap(glossaryId) } }
                else Modifier
            ),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Status dot
        if (value.threshold != null && value.numeric != null) {
            Canvas(modifier = Modifier.size(6.dp).padding(end = 4.dp)) {
                drawCircle(color = statusColor)
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }

        // Label
        Text(
            if (indent) "  $label" else label,
            color = TerminalTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        // Value
        Text(
            value.text,
            color = statusColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (value.numeric != null) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// ─── Dual Metric Row (two metrics side-by-side) ─────────

@Composable
fun DualMetricRow(
    leftLabel: String, leftValue: MetricValue,
    rightLabel: String, rightValue: MetricValue,
    leftGlossaryId: String? = null,
    rightGlossaryId: String? = null,
    onTap: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MetricRow(leftLabel, leftValue, leftGlossaryId, onTap)
        }
        Column(modifier = Modifier.weight(1f)) {
            MetricRow(rightLabel, rightValue, rightGlossaryId, onTap)
        }
    }
}

// ─── Bar Gauge ──────────────────────────────────────────

@Composable
fun BarGauge(
    label: String,
    current: Long,
    total: Long,
    color: Color = TerminalAccentGreen,
    warnThreshold: Double = 0.8,
    critThreshold: Double = 0.95
) {
    val fraction = if (total > 0) current.toFloat() / total else 0f
    val barColor = when {
        fraction >= critThreshold -> TerminalAccentRed
        fraction >= warnThreshold -> TerminalAccentYellow
        else -> color
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(formatBytes(current), color = TerminalTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 1.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(TerminalBorder, size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
            drawRoundRect(barColor, size = androidx.compose.ui.geometry.Size(w * fraction, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "${(fraction * 100).toInt()}%",
                color = barColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

// ─── Sparkline ──────────────────────────────────────────

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
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
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
