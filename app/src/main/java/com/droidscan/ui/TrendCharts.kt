package com.droidscan.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.DroidscanApp
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.telemetry.TelemetryCollector
import com.droidscan.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ── Trend history (persisted, sampled) ────────────────────────────────

/**
 * Loads the sampled trend history on Dispatchers.IO, refreshed every [refreshMs].
 * The history file is encrypted at rest; decryption + sampling happens off the
 * main thread. One instance per DashboardScreen — all TrendCards share it.
 */
@Composable
fun rememberTrendHistory(
    collector: TelemetryCollector = DroidscanApp.instance.telemetryCollector,
    refreshMs: Long = TREND_REFRESH_MS
): List<MetricSnapshot> {
    var history by remember(collector) { mutableStateOf<List<MetricSnapshot>>(emptyList()) }
    LaunchedEffect(collector) {
        while (true) {
            try {
                history = withContext(Dispatchers.IO) { collector.historyForCharts() }
            } catch (t: Throwable) {
                // Never let a history-read failure kill the refresh loop.
                Log.w(TAG, "history refresh failed: ${t.message}")
            }
            delay(refreshMs)
        }
    }
    return history
}

// ── Series extractors ─────────────────────────────────────────────────

fun trendRsrp(h: List<MetricSnapshot>): List<Float?> = h.map { it.radio.rsrp?.toFloat() }
fun trendSinr(h: List<MetricSnapshot>): List<Float?> = h.map { it.radio.sinr?.toFloat() }
fun trendBattery(h: List<MetricSnapshot>): List<Float?> = h.map { it.power.batteryLevel?.toFloat() }
fun trendThermal(h: List<MetricSnapshot>): List<Float?> = h.map { it.thermal.maxTemperature }
/** Per-core average as a percentage (0-100) — the charts/format treat it as %. */
fun trendCpuLoad(h: List<MetricSnapshot>): List<Float?> = h.map { s ->
    s.processes.perCoreLoad.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.times(100f)
}
fun trendRamUsedGiB(h: List<MetricSnapshot>): List<Float?> = h.map { s ->
    s.processes.usedMemoryBytes?.div(1_073_741_824f)
}

// ── Line chart (area fill + stroke) ───────────────────────────────────

/**
 * Terminal-styled line chart. Draws a filled gradient area under a 2dp stroke,
 * skipping null points (treated as gaps). Y is scaled to [minY]..[maxY] with
 * ~7% headroom top/bottom so the trace never clips.
 */
@Composable
fun TrendChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    minY: Float,
    maxY: Float
) {
    if (values.count { it != null } < 2) return
    val range = (maxY - minY).coerceAtLeast(0.0001f)

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1).coerceAtLeast(1)

        fun yOf(v: Float): Float = h - ((v - minY) / range) * (h * 0.86f) - (h * 0.07f)

        val line = Path()
        val fill = Path()
        var firstX = -1f
        var lastX = -1f
        values.forEachIndexed { i, v ->
            if (v != null) {
                val x = i * stepX
                val yy = yOf(v)
                if (firstX < 0f) {
                    line.moveTo(x, yy)
                    fill.moveTo(x, yy)
                    firstX = x
                } else {
                    line.lineTo(x, yy)
                    fill.lineTo(x, yy)
                }
                lastX = x
            }
        }

        if (lastX >= 0f && firstX >= 0f) {
            fill.lineTo(lastX, h)
            fill.lineTo(firstX, h)
            fill.close()
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f)),
                    endY = h
                )
            )
        }
        drawPath(line, color, style = Stroke(width = 2f, cap = StrokeCap.Round))

        // Faint baseline at the bottom of the range (when 0 is in range).
        val baselineY = h - (0f - minY) / range * (h * 0.86f) - (h * 0.07f)
        if (baselineY in 0f..h) {
            drawLine(
                TerminalBorder.copy(alpha = 0.7f),
                start = androidx.compose.ui.geometry.Offset(0f, baselineY),
                end = androidx.compose.ui.geometry.Offset(w, baselineY),
                strokeWidth = 1f
            )
        }
    }
}

// ── TrendCard (TierCard + chart + range/latest labels) ────────────────

/**
 * A full-width TierCard wrapping a trend: header live-readout shows the latest
 * value, a row under it shows min / window / max, then the chart.
 */
@Composable
fun TrendCard(
    title: String,
    series: List<Float?>,
    unit: String,
    color: Color,
    windowLabel: String,
    minY: Float? = null,
    maxY: Float? = null,
    formatValue: (Float) -> String = { v ->
        if (v % 1f == 0f) String.format("%.0f", v) else String.format("%.1f", v)
    }
) {
    val valid = series.filterNotNull()
    val latest = valid.lastOrNull()
    TierCard(title, liveReadout = latest?.let { formatValue(it) + unit }) {
        if (valid.size < 2) {
            MetricRow("Trend", MetricValue("collecting…"))
            MetricRow("Hint", MetricValue("History accumulates every second"))
            return@TierCard
        }
        val lo = minY ?: valid.min()
        val hi = maxY ?: valid.max()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatValue(lo) + unit, color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text(windowLabel, color = TerminalTextSecondary.copy(alpha = 0.55f), fontFamily = FontFamily.SansSerif, fontSize = 9.sp)
            Text(formatValue(hi) + unit, color = TerminalTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Spacer(Modifier.height(4.dp))
        TrendChart(series, color, minY = lo, maxY = hi)
    }
}

private const val TREND_REFRESH_MS = 10_000L
private const val TAG = "TrendCharts"
