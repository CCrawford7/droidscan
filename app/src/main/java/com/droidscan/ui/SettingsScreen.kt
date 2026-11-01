package com.droidscan.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.droidscan.DroidscanApp
import com.droidscan.TelemetryService
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.ui.theme.TerminalAccentBlue
import com.droidscan.ui.theme.TerminalAccentCyan
import com.droidscan.ui.theme.TerminalAccentGreen
import com.droidscan.ui.theme.TerminalAccentRed
import com.droidscan.ui.theme.TerminalBackground
import com.droidscan.ui.theme.TerminalTextSecondary

@Composable
fun SettingsScreen(snapshot: MetricSnapshot) {
    val context = LocalContext.current
    val proFeatures by DroidscanApp.instance.proFeaturesEnabled.collectAsState()
    val runtimePermissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION to "Location (cell metrics)",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Approximate location",
        Manifest.permission.NEARBY_WIFI_DEVICES to "Nearby devices",
        "android.permission.READ_SIM_STATE" to "SIM info",
        Manifest.permission.READ_PHONE_STATE to "Phone state",
        Manifest.permission.READ_BASIC_PHONE_STATE to "Basic phone state",
        Manifest.permission.READ_PRECISE_PHONE_STATE to "Precise phone (PCI/IMS)"
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Minimal "Pro features" gate (0.9.1): OFF by default → clean Standard
        // Mode dashboard. ON → the existing Shizuku-gated sections reappear so
        // we can still test their functionality alongside the rest of the app.
        TierCard("PRO FEATURES", liveReadout = if (proFeatures) "ON" else "OFF", liveColor = if (proFeatures) TerminalAccentCyan else TerminalTextSecondary) {
            val transport = DroidscanApp.instance.telemetryCollector.transport()
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRow(
                    "Shizuku-gated telemetry",
                    MetricValue(if (proFeatures) "ENABLED" else "DISABLED"),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = proFeatures,
                    onCheckedChange = { DroidscanApp.instance.setProFeaturesEnabled(it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRow("Transport", MetricValue(when (transport) {
                    "ROOT" -> "ROOT shell"
                    "SHIZUKU" -> "SHIZUKU"
                    else -> "NONE"
                }), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Chip(
                    when (transport) {
                        "ROOT" -> "ROOT"
                        "SHIZUKU" -> "POWER MODE"
                        else -> "STANDARD"
                    },
                    if (snapshot.shizukuAvailable) TerminalAccentCyan else TerminalTextSecondary
                )
            }
            if (proFeatures) {
                if (snapshot.shizukuAvailable) {
                    MetricRow("Status", MetricValue("Available — shell-level data"))
                } else {
                    Spacer(Modifier.height(4.dp))
                    MetricRow("Hint", MetricValue("Start Shizuku server (wireless debugging or root) → grant → return"))
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:moe.shizuku.privileged.api"))
                                )
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentCyan.copy(alpha = 0.18f), contentColor = TerminalAccentCyan)
                    ) {
                        Text("Open Shizuku", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                MetricRow("Note", MetricValue("Default dashboard is Standard Mode. Enable to reveal Shizuku sections for testing."))
            }
        }

        TierCard("BACKGROUND") {
            val serviceRunning = TelemetryService.isRunning(context)
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRow("Foreground service", MetricValue(if (serviceRunning) "RUNNING" else "STOPPED"), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Chip(if (serviceRunning) "ON" else "OFF", if (serviceRunning) TerminalAccentGreen else TerminalTextSecondary)
            }
            MetricRow("Battery exemption", MetricValue(if (batteryExempt) "GRANTED" else "NOT SET"))
            Spacer(Modifier.height(4.dp))
            MetricRow(
                "Hint",
                MetricValue("Keep collection alive in the background; disable battery optimization to survive OEM kills (OnePlus is aggressive).")
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (TelemetryService.isRunning(context)) TelemetryService.stop(context)
                        else TelemetryService.start(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serviceRunning) TerminalAccentRed.copy(alpha = 0.18f) else TerminalAccentGreen.copy(alpha = 0.18f),
                        contentColor = if (serviceRunning) TerminalAccentRed else TerminalAccentGreen
                    )
                ) {
                    Text(if (serviceRunning) "Stop" else "Start", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentBlue, contentColor = TerminalBackground)
                ) {
                    Text("Battery opt-out", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        TierCard("EXPORT") {
            MetricRow("Diagnostic report", MetricValue("Full telemetry snapshot as text"))
            MetricRow("Includes", MetricValue("radio · power · thermal · storage · GNSS · IMS"))
            Spacer(Modifier.height(4.dp))
            MetricRow("Privacy", MetricValue("Report contains location-derived data — review before sharing"))
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    try {
                        val report = DroidscanApp.instance.telemetryCollector.buildDebugDump(snapshot)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "droidscan diagnostic report — ${snapshot.systemInfo.deviceModel} ${snapshot.systemInfo.androidVersion}")
                            putExtra(Intent.EXTRA_TEXT, report)
                            clipData = android.content.ClipData.newPlainText("droidscan_report", report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share diagnostic report"))
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentCyan.copy(alpha = 0.18f), contentColor = TerminalAccentCyan)
            ) {
                Text("Share report", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        TierCard("PERMISSIONS") {
            runtimePermissions.forEach { (perm, label) ->
                val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricRow(label, MetricValue(if (granted) "GRANTED" else "DENIED"), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Chip(if (granted) "OK" else "OFF", if (granted) TerminalAccentGreen else TerminalAccentRed)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TerminalAccentBlue, contentColor = TerminalBackground)
            ) {
                Text("Open app settings", fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        TierCard("ABOUT") {
            MetricRow("Version", MetricValue("1.2.0"))
            MetricRow("Privacy", MetricValue("All data stays on-device"))
        }
    }
}
