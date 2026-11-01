package com.droidscan.diagnostics

import com.droidscan.shared.core.MemoryPressure
import com.droidscan.shared.core.MetricSnapshot

/**
 * The symptom catalog: every telemetry-detectable condition the engine reasons
 * about, plus the corpus title-keywords that indicate the same complaint.
 *
 * A symptom's [condition] returns a human detail line when present, or `null`
 * when absent. The `null ≠ 0` convention is preserved end-to-end: we never
 * fabricate a symptom from *missing* telemetry — every branch tests a concrete
 * value (an explicit `false`, a `NOT REGISTERED`, a measured latency, …).
 *
 * Keyword lists are drawn from the actual EE corpus titles (see the corpus
 * analysis behind `docs/CODEBASE_AUDIT_2026-11-01.md`); they are matched after
 * lower-casing and punctuation-stripping, so apostrophes/hyphens are omitted
 * here on purpose ("won't" → "wont", "Wi-Fi" → "wifi").
 */
data class SymptomSpec(
    val id: String,
    val label: String,
    val severity: Severity,
    val keywords: List<String>,
    val condition: (MetricSnapshot) -> String?
)

object SymptomCatalog {

    // ── Signal ────────────────────────────────────────────────

    private val weakSignal = SymptomSpec(
        id = "weakSignal",
        label = "Weak cellular signal (RSRP)",
        severity = Severity.HIGH,
        keywords = listOf(
            "coverage in", "signal booster", "mobile signal", "signal poor",
            "phone signal poor", "poor signal", "very poor signal", "poor signal at home",
            "mast locations", "coverage map", "mast installed", "mobile phone mast",
            "service status checker", "no signal", "ee phone signal"
        ),
        condition = { s ->
            when {
                s.radio.rsrp == null -> null
                s.radio.rsrp!! <= -120 ->
                    "RSRP ${s.radio.rsrp} dBm is critically weak — at the edge of usable coverage."
                s.radio.rsrp!! <= -110 ->
                    "RSRP ${s.radio.rsrp} dBm is weak; calls may drop and data may crawl."
                else -> null
            }
        }
    )

    private val poorSignalQuality = SymptomSpec(
        id = "poorSignalQuality",
        label = "Poor signal quality (RSRQ)",
        severity = Severity.MEDIUM,
        keywords = listOf("interference", "signal quality", "noisy signal"),
        condition = { s ->
            s.radio.rsrq?.let {
                if (it <= -12) "RSRQ $it dB is poor — heavy interference or congestion." else null
            }
        }
    )

    private val poorSinr = SymptomSpec(
        id = "poorSinr",
        label = "Poor signal-to-noise (SINR)",
        severity = Severity.MEDIUM,
        keywords = listOf("slow data", "5g is so slow", "so slow", "unusable", "best speeds", "mbps", "congestion"),
        condition = { s ->
            s.radio.sinr?.let {
                if (it <= 3) "SINR $it dB is low — a noisy channel that throttles throughput." else null
            }
        }
    )

    // ── Connectivity ─────────────────────────────────────────

    private val noData = SymptomSpec(
        id = "noData",
        label = "Registered but no working data",
        severity = Severity.HIGH,
        keywords = listOf(
            "data does not work", "mobile data not working", "data not working",
            "no internet despite full 5g", "no internet", "could not activate mobile data network",
            "pdp authentication failure", "pdp authentication", "connected however no internet",
            "strong 4g", "doesnt have internet", "no internet connection",
            "mobile data does not work", "apn settings", "ee apn", "apn"
        ),
        condition = { s ->
            val registered = s.radio.dataNetworkType != null
            val active = s.networkConfig.activeNetworkType
            when {
                // Registered on mobile but the connectivity probe to 8.8.8.8 failed.
                registered && active == "MOBILE" && s.networkConfig.latencyMs == null ->
                    "Registered on ${s.radio.dataNetworkType} but the connectivity probe failed — no working data path."
                // Registered on mobile but no active data network is attached at all.
                registered && active == null ->
                    "Registered on ${s.radio.dataNetworkType} but no active data network is connected."
                else -> null
            }
        }
    )

    private val noService = SymptomSpec(
        id = "noService",
        label = "Not registered on network",
        severity = Severity.HIGH,
        keywords = listOf(
            "not registered on network", "not registered", "emergency calls only",
            "not connecting to network", "no sim no service", "no sim", "no service",
            "not connecting", "make or receive calls", "unable to make calls",
            "contact 150", "voicemail", "cant make", "receive calls"
        ),
        condition = { s ->
            val provisioned = s.simStatus.slots.any { it.isProvisioned }
            val denied = s.radio.cellInfoDenied == true
            if (provisioned && !denied && s.radio.dataNetworkType == null && s.radio.networkPlmn == null) {
                "A SIM is provisioned but the device is not registered on any network."
            } else null
        }
    )

    private val simNotProvisioned = SymptomSpec(
        id = "simNotProvisioned",
        label = "SIM / eSIM not provisioned",
        severity = Severity.HIGH,
        keywords = listOf(
            "sim card activation", "sim activation", "activating new sim", "activate sim",
            "activate simcard", "not provisioned", "sim 1 not provisioned", "sim not provisioned",
            "esim", "sim to esim", "esim conversion", "activate esim", "esim qr code",
            "qr code not working", "get my esim", "order esim", "esim download",
            "esim on two devices", "esim process", "order replacement sim", "sim card", "no sim"
        ),
        condition = { s ->
            if (s.simStatus.slots.any { it.carrierName != null && !it.isProvisioned }) {
                "A SIM slot is detected but not provisioned (no active subscription)."
            } else null
        }
    )

    // ── IMS / calling ────────────────────────────────────────

    private val imsNotRegistered = SymptomSpec(
        id = "imsNotRegistered",
        label = "IMS not registered",
        severity = Severity.MEDIUM,
        keywords = listOf("not registered on network", "wifi calling stopped", "volte", "ims"),
        condition = { s ->
            val explicitFalse = s.radio.imsRegistered == false
            val perSubNotRegistered = s.ims.perSub.any { it.registrationState == "NOT REGISTERED" }
            if (explicitFalse || perSubNotRegistered) {
                "IMS is not registered — VoLTE and Wi-Fi Calling cannot place calls."
            } else null
        }
    )

    private val volteOff = SymptomSpec(
        id = "volteOff",
        label = "VoLTE disabled",
        severity = Severity.MEDIUM,
        keywords = listOf("volte", "wifi calling", "not registered on network"),
        condition = { s ->
            if (s.radio.volteEnabled == false && s.radio.imsCapable == true) {
                "VoLTE is off though the device and carrier support it."
            } else null
        }
    )

    private val wifiCallingDown = SymptomSpec(
        id = "wifiCallingDown",
        label = "Wi-Fi Calling unavailable",
        severity = Severity.MEDIUM,
        keywords = listOf("wifi calling", "wifi calling stopped", "ports for wifi calling", "wifi calling wont work"),
        condition = { s ->
            val radioUnavailable = s.radio.wifiCallingStatus == "UNAVAILABLE"
            val slotUnavailable = s.simStatus.slots.any { it.wifiCallingStatus == "UNAVAILABLE" }
            if (radioUnavailable || slotUnavailable) {
                "Wi-Fi Calling reports UNAVAILABLE — calls will not route over Wi-Fi."
            } else null
        }
    )

    // ── Roaming ──────────────────────────────────────────────

    private val roamingIssue = SymptomSpec(
        id = "roamingIssue",
        label = "Roaming data blocked",
        severity = Severity.MEDIUM,
        keywords = listOf(
            "roaming not working", "roaming refresh", "roaming in", "roaming abroad",
            "roaming not working despite", "roaming on", "eu roaming", "switch roaming on",
            "using data in", "use my phone in", "international roaming"
        ),
        condition = { s ->
            if (s.radio.isRoaming == true && s.radio.dataRoamingEnabled == false) {
                "Roaming, but data roaming is disabled — mobile data will not work abroad."
            } else null
        }
    )

    // ── System ───────────────────────────────────────────────

    private val highLatency = SymptomSpec(
        id = "highLatency",
        label = "High network latency",
        severity = Severity.MEDIUM,
        keywords = listOf("slow", "lag", "latency"),
        condition = { s ->
            s.networkConfig.latencyMs?.let {
                if (it > 300) "Latency probe measured $it ms (> 300 ms) to 8.8.8.8." else null
            }
        }
    )

    private val memoryPressure = SymptomSpec(
        id = "memoryPressure",
        label = "Memory pressure",
        severity = Severity.MEDIUM,
        keywords = listOf("slow", "lag", "freezing"),
        condition = { s ->
            when (s.processes.memoryPressure) {
                MemoryPressure.MODERATE -> "Memory pressure is MODERATE — background apps are being reclaimed."
                MemoryPressure.CRITICAL -> "Memory pressure is CRITICAL — the system is thrashing."
                else -> null
            }
        }
    )

    private val thermalThrottling = SymptomSpec(
        id = "thermalThrottling",
        label = "Thermal throttling",
        severity = Severity.MEDIUM,
        keywords = listOf("hot", "overheat", "throttling"),
        condition = { s ->
            when {
                s.thermal.isThrottling == true -> "The device is thermally throttling."
                (s.thermal.maxTemperature ?: 0f) >= 48f ->
                    "Peak thermal zone is ${s.thermal.maxTemperature}°C — approaching throttle."
                else -> null
            }
        }
    )

    private val lowBattery = SymptomSpec(
        id = "lowBattery",
        label = "Low battery",
        severity = Severity.LOW,
        keywords = listOf("battery", "drain"),
        condition = { s ->
            s.power.batteryLevel?.let {
                if (it < 20) "Battery is at $it% — power-saving modes may throttle radios." else null
            }
        }
    )

    private val storageLow = SymptomSpec(
        id = "storageLow",
        label = "Low internal storage",
        severity = Severity.MEDIUM,
        keywords = listOf("storage full", "no space"),
        condition = { s ->
            val used = s.storage.internalUsedBytes
            val free = s.storage.internalFreeBytes
            val total = if (used != null && free != null) used + free else 0L
            if (total > 0 && free != null && free.toDouble() / total < 0.2) {
                val pct = (free.toDouble() / total * 100).toInt()
                "Only $pct% of internal storage is free — caches and apps may fail."
            } else null
        }
    )

    val specs: List<SymptomSpec> = listOf(
        weakSignal, poorSignalQuality, poorSinr,
        noData, noService, simNotProvisioned,
        imsNotRegistered, volteOff, wifiCallingDown,
        roamingIssue, highLatency, memoryPressure,
        thermalThrottling, lowBattery, storageLow
    )

    private val byId: Map<String, SymptomSpec> = specs.associateBy { it.id }

    fun specFor(id: String): SymptomSpec? = byId[id]

    fun keywordsFor(id: String): List<String> = byId[id]?.keywords ?: emptyList()
}
