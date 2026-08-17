package com.droidscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.droidscan.ui.theme.TerminalBorder

// ── Activity-ring primitives (Overview rework) ──────────────────────────
// Apple Watch-style concentric circular gauges drawn with Compose Canvas arcs.
// 270° sweep with a 90° gap centered at the bottom (6 o'clock) + rounded caps.

/** One ring in a cluster: its spoken label, 0..1 fill, and status color. */
data class RingData(
    val label: String,
    val progress: Float,
    val color: Color
)

/** A single progress ring (e.g. battery %). [progress] is 0..1. */
@Composable
fun ActivityRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    thickness: Dp = 10.dp,
    trackColor: Color = TerminalBorder,
    startAngle: Float = 135f,
    sweep: Float = 270f,
    cap: StrokeCap = StrokeCap.Round
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = thickness.toPx()
        val half = this.size.minDimension / 2f
        val centerlineRadius = half - stroke / 2f
        val diameter = centerlineRadius * 2f
        val topLeft = Offset(half - diameter / 2f, half - diameter / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(trackColor, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
        if (progress > 0f) {
            drawArc(
                color, startAngle, sweep * progress.coerceIn(0f, 1f), false,
                topLeft, arcSize, style = Stroke(stroke, cap = cap)
            )
        }
    }
}

/**
 * Nested concentric rings — first [RingData] is the OUTERMOST ring. Drawn in a
 * single Canvas so the rings stay perfectly concentric and the caller can apply
 * one merged semantics block (cohesive TalkBack label) to the whole cluster.
 */
@Composable
fun ConcentricRings(
    rings: List<RingData>,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    thickness: Dp = 8.dp,
    gap: Dp = 3.dp,
    trackColor: Color = TerminalBorder,
    startAngle: Float = 135f,
    sweep: Float = 270f,
    cap: StrokeCap = StrokeCap.Round
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = thickness.toPx()
        val gapPx = gap.toPx()
        val half = this.size.minDimension / 2f
        val outerRadius = half - stroke / 2f
        rings.forEachIndexed { i, ring ->
            val radius = outerRadius - i * (stroke + gapPx)
            if (radius <= stroke / 2f) return@forEachIndexed
            val diameter = radius * 2f
            val topLeft = Offset(half - diameter / 2f, half - diameter / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(trackColor, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
            if (ring.progress > 0f) {
                drawArc(
                    ring.color, startAngle, sweep * ring.progress.coerceIn(0f, 1f), false,
                    topLeft, arcSize, style = Stroke(stroke, cap = cap)
                )
            }
        }
    }
}
