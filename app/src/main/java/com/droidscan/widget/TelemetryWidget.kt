package com.droidscan.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

// Widget palette mirrors app/src/main/.../ui/theme/Theme.kt (fixed dark scheme).
private val Bg = Color(0xFF0B1220)
private val Surface = Color(0xFF101A2E)
private val Border = Color(0xFF1E2D4A)
private val Ink = Color(0xFFEAF1FA)
private val Ink2 = Color(0xFF8CA0BE)
private val Accent = Color(0xFF22D3EE)
private val Green = Color(0xFF34D399)
private val Yellow = Color(0xFFFBBF24)
private val Red = Color(0xFFF87171)

/** Home-screen widget: a compact "at a glance" card matching the dashboard. */
class TelemetryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(WidgetMetricsStore.read(context))
        }
    }
}

@Composable
private fun WidgetContent(d: WidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(Bg))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Hero: signal (RAT + RSRP), colored by tier, with a live dot on the right.
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            val (signal, signalColor) = signalText(d)
            Text(
                signal,
                style = TextStyle(
                    color = ColorProvider(signalColor),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "●",
                style = TextStyle(color = ColorProvider(Green), fontSize = 10.sp)
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // Secondary: battery + RAM.
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                "⚡ ${d.battery?.let { "$it%" } ?: "--"}",
                style = TextStyle(color = ColorProvider(Ink2), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            )
            Spacer(GlanceModifier.width(14.dp))
            Text(
                ramText(d),
                style = TextStyle(color = ColorProvider(Ink2), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        // Footer.
        Text(
            "droidscan",
            style = TextStyle(
                color = ColorProvider(Accent),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

private fun signalText(d: WidgetData): Pair<String, Color> {
    val rat = d.rat ?: "--"
    val rsrp = d.rsrp
    if (rsrp == null) return "$rat · -- dBm" to Ink2
    val color = when {
        rsrp >= -100 -> Green
        rsrp >= -120 -> Yellow
        else -> Red
    }
    return "$rat · $rsrp dBm" to color
}

private fun ramText(d: WidgetData): String {
    val used = d.ramUsedBytes
    val total = d.ramTotalBytes
    if (used == null || total == null || total <= 0L) return "RAM --"
    val pct = (used.toDouble() / total.toDouble() * 100).toInt()
    return "RAM $pct%"
}
