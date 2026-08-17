package com.droidscan.shared.core

import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────
// Metric data classes — single source of truth
// for all telemetry types displayed on the dashboard.
// ──────────────────────────────────────────────

@Serializable
data class RadioSnapshot(
    val rsrp: Int? = null,          // dBm  (-140..-44)
    val rsrq: Int? = null,          // dB   (-20..-3)
    val sinr: Int? = null,          // dB   (-20..+30)
    val activeBands: List<String> = emptyList(),  // "n71", "B41", "n78"...
    val cellId: Long? = null,
    val tac: Int? = null,            // Tracking Area Code
    val imsRegistered: Boolean? = null,
    val epdgTunnelActive: Boolean? = null,
    val dataNetworkType: String? = null,  // "NR", "LTE", "HSPA", "EDGE"
    val isRoaming: Boolean? = null,
    val operatorName: String? = null,
    // ── HSS / Subscriber fields ──
    val networkPlmn: String? = null,         // MCC-MNC of current serving network
    val callState: String? = null,           // IDLE | RINGING | OFFHOOK
    val dataRoamingEnabled: Boolean? = null, // Data roaming setting (device-level)
    val preferredNetworkType: String? = null, // e.g. "NR/LTE/GSM/WCDMA" (from Settings.Global)
    val imsCapable: Boolean? = null,         // Device IMS capability (from CarrierConfig)
    val volteEnabled: Boolean? = null,       // VoLTE specifically enabled
    val wifiCallingStatus: String? = null,   // WiFi Calling: "READY" | "ACTIVE" | "UNAVAILABLE" | null
    val radioPowerState: String? = null,     // "ON" | "OFF" | "UNAVAILABLE" (API 30+)
    val nrNsaAvailable: Boolean? = null,     // NR-NSA (EN-DC) capability
    val cellInfoDenied: Boolean? = null,     // true when allCellInfo threw SecurityException (location not granted)
    // ── Physical layer / frequency depth ──
    val pci: Int? = null,                    // Physical Cell ID (LTE + NR)
    val earfcn: Int? = null,                 // LTE E-UTRA ARFCN (3GPP 36.101)
    val nrarfcn: Int? = null,                // NR ARFCN (3GPP 38.101)
    val bandwidthKhz: Int? = null,           // channel bandwidth in kHz (LTE/NR)
    val dlFrequencyMhz: Double? = null,      // derived downlink carrier frequency
    val ssRsrp: Int? = null,                 // NR SS-RSRP (dBm)
    val ssRsrq: Int? = null,                 // NR SS-RSRQ (dB)
    val ssSinr: Int? = null,                 // NR SS-SINR (dB)
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class ThermalSnapshot(
    val zones: List<ThermalZone> = emptyList(),
    val isThrottling: Boolean? = null,
    val maxTemperature: Float? = null,
    val peakZoneName: String? = null,        // name of the zone at maxTemperature (e.g. "cpu-0")
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class ThermalZone(
    val name: String,           // e.g. "cpu-0", "bat", "modem"
    val temperatureCelsius: Float,
    val throttling: Boolean = false
)

@Serializable
data class StorageSnapshot(
    val internalUsedBytes: Long? = null,
    val internalFreeBytes: Long? = null,
    val systemBytes: Long? = null,
    val userBytes: Long? = null,
    val externalMounted: Boolean = false,
    val externalUsedBytes: Long? = null,
    val externalFreeBytes: Long? = null,
    val topConsumers: List<StorageConsumer> = emptyList(),
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class StorageConsumer(
    val packageName: String,
    val label: String,
    val sizeBytes: Long
)

/**
 * Deep storage / wear telemetry from `dumpsys diskstats` (Power Mode, UID 2000).
 * Standard Mode yields an empty snapshot — the dump is shell-privileged on this
 * device (even /proc/diskstats is root-gated). The user-space aggregation still
 * tells the real story: write latency, sustained write speed, and how the
 * partition is carved up by app/data/cache.
 */
@Serializable
data class StorageWearSnapshot(
    val writeLatencyMs: Long? = null,        // 512B random write latency
    val recentWriteKbps: Int? = null,        // recent sustained write speed (kB/s)
    val dataFreePercent: Int? = null,        // /data free as % of total
    val fileBasedEncryption: Boolean? = null,
    val appSizeBytes: Long? = null,
    val appDataSizeBytes: Long? = null,
    val appCacheSizeBytes: Long? = null,
    val photosSizeBytes: Long? = null,
    val videosSizeBytes: Long? = null,
    val audioSizeBytes: Long? = null,
    val powerMode: Boolean = false,          // true when read via Shizuku
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class PowerSnapshot(
    val batteryLevel: Int? = null,         // 0..100
    val batteryHealth: String? = null,     // "good", "overheat", "dead", "overvoltage", "cold"
    val batteryTemperature: Float? = null, // Celsius
    val chargeRateMicroamps: Int? = null,
    val estimatedTimeRemainingMs: Long? = null,
    val currentDrainMicroamps: Int? = null,
    val batteryCurrentAverageMicroamps: Int? = null, // rolling average current draw (µA)
    val batteryChargeCounterMicroAh: Long? = null,   // remaining capacity (µAh)
    val perProcessPower: Map<String, Double> = emptyMap(),
    val esimActive: Boolean = false,
    val bluetoothActive: Boolean = false,
    val hotspotActive: Boolean = false,
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class ProcessSnapshot(
    val perCoreLoad: List<Float> = emptyList(),     // 0.0..1.0 per core
    val topCpuProcesses: List<ProcessInfo> = emptyList(),
    val topMemoryProcesses: List<ProcessInfo> = emptyList(),
    val memoryPressure: MemoryPressure = MemoryPressure.UNKNOWN,
    val totalMemoryBytes: Long? = null,
    val usedMemoryBytes: Long? = null,
    val swapUsedBytes: Long? = null,
    val kernelWakelocks: List<String> = emptyList(), // Power Mode only
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class ProcessInfo(
    val pid: Int,
    val name: String,
    val cpuPercent: Float? = null,
    val memoryBytes: Long? = null
)

@Serializable
enum class MemoryPressure {
    NORMAL, MODERATE, CRITICAL, UNKNOWN
}

@Serializable
data class NetworkConfigSnapshot(
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val activeNetworkType: String? = null,     // "WIFI", "MOBILE", "VPN", "NONE"
    val vpnActive: Boolean = false,
    val vpnLabel: String? = null,
    val hotspotActive: Boolean = false,
    val apnName: String? = null,
    val wifiSsid: String? = null,
    val wifiRssi: Int? = null,          // dBm (-127..0), actual received signal
    val wifiLinkSpeedMbps: String? = null,
    val latencyMs: Int? = null,         // last TCP connect latency to well-known host (active probe)
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class SimStatusSnapshot(
    val slots: List<SimSlot> = emptyList(),
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class SimSlot(
    val slotIndex: Int,
    val carrierName: String? = null,
    val isRoaming: Boolean? = null,
    val esimProfileCount: Int = 0,
    val isProvisioned: Boolean = false,
    val iccidLast4: String? = null,  // last 4 digits only for privacy
    val subscriptionId: Int? = null,
    // ── Subscriber identity (HSS) ──
    val homePlmn: String? = null,          // MCC-MNC from SIM (e.g. "310410")
    val simOperatorName: String? = null,   // SPN (e.g. "T-Mobile")
    val msisdn: String? = null,            // Phone number — often null on modern Android
    val imsiPrefix: String? = null,        // First 5-6 digits of IMSI (MCC-MNC only) — experimental
    // ── Per-SIM radio capabilities (read via createForSubscriptionId) ──
    val volteEnabled: Boolean? = null,     // VoLTE specifically enabled for this sub
    val wifiCallingStatus: String? = null, // "ACTIVE" | "READY" | "UNAVAILABLE"
    val imsRegistered: Boolean? = null,    // IMS registration for this sub
    val imsCapable: Boolean? = null,       // CarrierConfig volte availability for this sub
    val nrNsaAvailable: Boolean? = null,   // CarrierConfig NR-NSA for this sub
    val preferredNetworkType: String? = null,
    val callState: String? = null,         // IDLE | RINGING | OFFHOOK
    val dataNetworkType: String? = null,   // "NR (5G)", "LTE (4G)", ...
    val rsrp: Int? = null,                 // registered-cell RSRP for this sub (dBm)
    // ── Per-SIM signal depth (registered cell, via createForSubscriptionId) ──
    val rsrq: Int? = null,                 // registered-cell RSRQ (dB)
    val sinr: Int? = null,                 // registered-cell SINR (dB) — LTE RSSNR / NR CSI-SINR
    val band: String? = null,              // serving band label ("B3", "n78") for this sub
    val pci: Int? = null,                  // physical cell id for this sub
    val tac: Int? = null                   // tracking area code for this sub
)

@Serializable
data class SystemInfoSnapshot(
    val deviceModel: String? = null,
    val manufacturer: String? = null,
    val androidVersion: String? = null,
    val buildNumber: String? = null,
    val securityPatch: String? = null,
    val kernelVersion: String? = null,
    val selinuxEnforced: Boolean? = null,
    val uptimeMs: Long = 0L,
    val resolution: String? = null,
    val densityDpi: String? = null,
    val timestampMs: Long = currentTimeMs()
)

// ──────────────────────────────────────────────
// GNSS satellite telemetry
// ──────────────────────────────────────────────

@Serializable
data class GnssSnapshot(
    val available: Boolean = false,
    val locationDenied: Boolean = false,
    val hasFix: Boolean = false,
    val satellites: List<GnssSatellite> = emptyList(),
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class GnssSatellite(
    val prn: Int,
    val constellation: String,        // "GPS", "GLONASS", "GALILEO", "BEIDOU", "QZSS", "SBAS", "IRNSS"
    val cn0DbHz: Double,              // Carrier-to-Noise density (dB-Hz)
    val carrierFrequencyHz: Double,   // L1 (1575.42) / L5 (1176.45) etc.
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
    val usedInFix: Boolean
)

// ──────────────────────────────────────────────
// IMS registration (ImsMmTelManager per sub)
// ──────────────────────────────────────────────

@Serializable
data class ImsSnapshot(
    val perSub: List<ImsRegistration> = emptyList(),
    val accessDenied: Boolean = false,
    val apiAvailable: Boolean = false,
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class ImsRegistration(
    val subscriptionId: Int,
    val registrationState: String? = null,   // REGISTERED | REGISTERING | NOT REGISTERED
    val transportType: String? = null,       // WWAN | WLAN
    val volteEnabled: Boolean? = null,       // VoLTE setting enabled
    val vtEnabled: Boolean? = null,          // Video Telephony setting enabled
    val wfcEnabled: Boolean? = null,         // VoWiFi setting enabled
    val wfcRoamingEnabled: Boolean? = null
)

// ──────────────────────────────────────────────
// Unified metric envelope
// ──────────────────────────────────────────────

@Serializable
data class MetricSnapshot(
    val radio: RadioSnapshot = RadioSnapshot(),
    val thermal: ThermalSnapshot = ThermalSnapshot(),
    val storage: StorageSnapshot = StorageSnapshot(),
    val storageWear: StorageWearSnapshot = StorageWearSnapshot(),
    val power: PowerSnapshot = PowerSnapshot(),
    val processes: ProcessSnapshot = ProcessSnapshot(),
    val networkConfig: NetworkConfigSnapshot = NetworkConfigSnapshot(),
    val simStatus: SimStatusSnapshot = SimStatusSnapshot(),
    val systemInfo: SystemInfoSnapshot = SystemInfoSnapshot(),
    val gnss: GnssSnapshot = GnssSnapshot(),
    val ims: ImsSnapshot = ImsSnapshot(),
    val shizukuAvailable: Boolean = false,
    val timestampMs: Long = currentTimeMs()
)

internal fun currentTimeMs(): Long = System.currentTimeMillis()
