package com.droidscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscan.speedtest.MetricStatus
import com.droidscan.speedtest.Metrics
import com.droidscan.speedtest.SpeedTestConstants
import com.droidscan.speedtest.SpeedTestEngine
import com.droidscan.speedtest.TestPhase
import com.droidscan.ui.theme.TerminalAccentBlue
import com.droidscan.ui.theme.TerminalAccentCyan
import com.droidscan.ui.theme.TerminalAccentGreen
import com.droidscan.ui.theme.TerminalAccentRed
import com.droidscan.ui.theme.TerminalAccentYellow
import com.droidscan.ui.theme.TerminalBackground
import com.droidscan.ui.theme.TerminalBorder
import com.droidscan.ui.theme.TerminalTextPrimary
import com.droidscan.ui.theme.TerminalTextSecondary
import kotlinx.coroutines.launch

// ─── Speed test (VitalPing-derived, opt-in) ──────────────
// On-demand download/upload/latency/jitter measurement. This is the one feature
// that transmits data off-device, so it only runs on an explicit tap and the
// transfer is disclosed inline and in PRIVACY.md.

@Composable
fun SpeedTestCard() {
    val scope = rememberCoroutineScope()
    val engine = remember { SpeedTestEngine() }

    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var phase by remember { mutableStateOf(TestPhase.IDLE) }
    var currentMbps by remember { mutableStateOf<Double?>(null) }
    var result by remember { mutableStateOf<Metrics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runTest() {
        scope.launch {
            running = true
            result = null
            error = null
            progress = 0f
            currentMbps = null
            engine.runSpeedTest(
                contractDownloadMbps = SpeedTestConstants.DefaultContract.DOWNLOAD_MBPS,
                contractUploadMbps = SpeedTestConstants.DefaultContract.UPLOAD_MBPS
            ) { p, ph, mbps ->
                progress = p
                phase = ph
                currentMbps = mbps
            }
                .onSuccess { result = it }
                .onFailure { error = it.message ?: "Speed test failed" }
            running = false
        }
    }

    TierCard(
        title = "SPEED TEST",
        liveReadout = when {
            running -> "${(progress * 100).toInt()}%"
            result != null -> "COMPLETE"
            else -> null
        },
        liveColor = if (running) TerminalAccentCyan else TerminalAccentGreen,
        accent = TerminalAccentCyan
    ) {
        Text(
            "Measures download, upload, latency and jitter.",
            color = TerminalTextSecondary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp
        )
        Text(
            "On your tap it downloads ~10 MB and uploads ~1 MB to public test servers — nothing else leaves the device.",
            color = TerminalTextSecondary.copy(alpha = 0.75f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.5.sp
        )
        Spacer(Modifier.height(8.dp))

        if (running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    phase.label(),
                    color = TerminalAccentCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                currentMbps?.let {
                    Text(
                        formatMbps(it),
                        color = TerminalTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                val w = size.width
                drawRoundRect(TerminalBorder, size = Size(w, size.height), cornerRadius = CornerRadius(3f))
                drawRoundRect(
                    TerminalAccentCyan,
                    size = Size(w * progress.coerceIn(0f, 1f), size.height),
                    cornerRadius = CornerRadius(3f)
                )
            }
        }

        result?.let { m ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeedMetricRow("Download", formatMbps(m.downloadMbps), m.downloadStatus)
                SpeedMetricRow("Upload", formatMbps(m.uploadMbps), m.uploadStatus)
                SpeedMetricRow("Latency", "${m.latencyMs.toInt()} ms", m.latencyStatus)
                SpeedMetricRow("Jitter", "${m.jitterMs.toInt()} ms", m.jitterStatus)
            }
        }

        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = TerminalAccentRed, fontFamily = FontFamily.SansSerif, fontSize = 11.sp)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { runTest() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = TerminalAccentBlue,
                contentColor = TerminalBackground,
                disabledContainerColor = TerminalAccentBlue.copy(alpha = 0.3f)
            )
        ) {
            Text(
                if (running) "Testing…" else "Run test",
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SpeedMetricRow(label: String, value: String, status: MetricStatus) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TerminalTextSecondary, fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = status.color(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun MetricStatus.color(): Color = when (this) {
    MetricStatus.GOOD -> TerminalAccentGreen
    MetricStatus.WARN -> TerminalAccentYellow
    MetricStatus.POOR -> TerminalAccentRed
    MetricStatus.NEUTRAL -> TerminalTextSecondary
}

private fun TestPhase.label(): String = when (this) {
    TestPhase.IDLE -> "Idle"
    TestPhase.LATENCY -> "Measuring latency…"
    TestPhase.DOWNLOAD -> "Downloading…"
    TestPhase.UPLOAD -> "Uploading…"
    TestPhase.COMPLETE -> "Complete"
}

private fun formatMbps(v: Double): String =
    if (v >= 100) String.format("%.0f Mbps", v) else String.format("%.1f Mbps", v)
