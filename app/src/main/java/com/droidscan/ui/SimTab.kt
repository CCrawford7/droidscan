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

// ─── SIM ─────────────────────────────────────────────────

@Composable
fun SimTab(
    snapshot: MetricSnapshot,
    glossary: List<GlossaryEntry>,
    padding: Modifier,
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
        // No-SIM = no slot has a card present (slots always emit per physical
        // slot, so an empty list is not the trigger).
        val anyCard = snapshot.simStatus.slots.any { s ->
            !s.carrierName.isNullOrEmpty() && s.carrierName != "Empty"
        }
        if (!anyCard) {
            TierCard("NO SIM") {
                MetricRow("Status", MetricValue("No SIM cards detected"))
            }
            return@Column
        }

        snapshot.simStatus.slots.forEachIndexed { i, slot ->
            val provisioned = slot.isProvisioned
            val disabled = !provisioned && slot.subscriptionId == null && slot.carrierName != "Empty"

            TierCard("SIM ${i + 1} — ${slot.carrierName ?: "Empty slot"}") {
                DualMetricRow("Carrier", MetricValue(slot.carrierName ?: "--"), "Provisioned", MetricValue(if (provisioned) "YES" else "NO"), "simProvisioning", "simProvisioning", onTap)
                MetricRow("eSIM Profiles", MetricValue("${slot.esimProfileCount}"), "esimProfiles", onTap)
                slot.iccidLast4?.let { MetricRow("ICCID (last 4)", MetricValue("•••• •••• •••• $it")) }
                if (disabled) {
                    Spacer(Modifier.height(2.dp))
                    Chip("DISABLED", TerminalAccentYellow)
                }
            }

            TierCard("IDENTITY") {
                MetricRow("Home PLMN", MetricValue(slot.homePlmn ?: "--"))
                MetricRow("SPN (SIM Name)", MetricValue(slot.simOperatorName ?: "--"))
                MetricRow("MSISDN", MetricValue(slot.msisdn ?: "N/A (carrier)"))
                MetricRow("IMSI", MetricValue(slot.imsiPrefix ?: "N/A (privilege-restricted)"))
            }

            TierCard("CAPABILITIES") {
                DualMetricRow("VoLTE", MetricValue(if (slot.volteEnabled == true) "ON" else if (slot.volteEnabled == false) "OFF" else "?"), "WiFi Call", MetricValue(slot.wifiCallingStatus ?: "?"))
                DualMetricRow("NR-NSA", MetricValue(if (slot.nrNsaAvailable == true) "YES" else if (slot.nrNsaAvailable == false) "NO" else "?"), "IMS Reg", MetricValue(if (slot.imsRegistered == true) "REG" else if (slot.imsRegistered == false) "NO" else "?"))
                DualMetricRow("IMS Capable", MetricValue(if (slot.imsCapable == true) "YES" else if (slot.imsCapable == false) "NO" else "?"), "Pref NW", MetricValue(slot.preferredNetworkType ?: "--"))
                DualMetricRow("Call State", MetricValue(slot.callState ?: "--"), "Roaming", MetricValue(if (slot.isRoaming == true) "YES" else if (slot.isRoaming == false) "NO" else "?"))
            }

            TierCard("SIGNAL") {
                MetricRow("Network", MetricValue(slot.dataNetworkType ?: "--"))
                MetricRow("RSRP", MetricValue(if (slot.rsrp != null) "${slot.rsrp} dBm" else "--", slot.rsrp?.toDouble(), RadioThresholds.rsrp, inverted = true), "rsrp", onTap)
                MetricRow("RSRQ", MetricValue(if (slot.rsrq != null) "${slot.rsrq} dB" else "--", slot.rsrq?.toDouble(), RadioThresholds.rsrq, inverted = true), "rsrq", onTap)
                MetricRow("SINR", MetricValue(if (slot.sinr != null) "${slot.sinr} dB" else "--", slot.sinr?.toDouble(), RadioThresholds.sinr, inverted = true), "sinr", onTap)
                MetricRow("Band", MetricValue(slot.band ?: "--"), "activeBands", onTap)
                DualMetricRow("PCI", MetricValue(slot.pci?.toString() ?: "--"), "TAC", MetricValue(slot.tac?.toString() ?: "--"), "pci", null, onTap)
            }
        }

        // Current connection
        TierCard("CURRENT CONNECTION") {
            MetricRow("Active Network", MetricValue(snapshot.networkConfig.activeNetworkType ?: "--"), "activeNetwork", onTap)
            MetricRow("APN", MetricValue(snapshot.networkConfig.apnName ?: "--"))
            MetricRow("DNS", MetricValue(snapshot.networkConfig.dnsServers.firstOrNull() ?: "--"))
            DualMetricRow("VPN", MetricValue(if (snapshot.networkConfig.vpnActive) snapshot.networkConfig.vpnLabel ?: "Active" else "Off"), "Hotspot", MetricValue(if (snapshot.networkConfig.hotspotActive) "ON" else "OFF"), null, null, onTap)
            DualMetricRow("Network PLMN", MetricValue(snapshot.radio.networkPlmn ?: "--"), "Roaming", MetricValue(if (snapshot.radio.isRoaming == true) "YES" else "NO"), null, "isRoaming", onTap)
        }
    }
}

