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
    val timestampMs: Long = currentTimeMs()
)

@Serializable
data class ThermalSnapshot(
    val zones: List<ThermalZone> = emptyList(),
    val isThrottling: Boolean? = null,
    val maxTemperature: Float? = null,
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

@Serializable
data class PowerSnapshot(
    val batteryLevel: Int? = null,         // 0..100
    val batteryHealth: String? = null,     // "good", "overheat", "dead", "overvoltage", "cold"
    val batteryTemperature: Float? = null, // Celsius
    val chargeRateMicroamps: Int? = null,
    val estimatedTimeRemainingMs: Long? = null,
    val currentDrainMicroamps: Int? = null,
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
    val subscriptionId: Int? = null
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
    val timestampMs: Long = currentTimeMs()
)

// ──────────────────────────────────────────────
// Unified metric envelope
// ──────────────────────────────────────────────

@Serializable
data class MetricSnapshot(
    val radio: RadioSnapshot = RadioSnapshot(),
    val thermal: ThermalSnapshot = ThermalSnapshot(),
    val storage: StorageSnapshot = StorageSnapshot(),
    val power: PowerSnapshot = PowerSnapshot(),
    val processes: ProcessSnapshot = ProcessSnapshot(),
    val networkConfig: NetworkConfigSnapshot = NetworkConfigSnapshot(),
    val simStatus: SimStatusSnapshot = SimStatusSnapshot(),
    val systemInfo: SystemInfoSnapshot = SystemInfoSnapshot(),
    val shizukuAvailable: Boolean = false,
    val timestampMs: Long = currentTimeMs()
)

internal fun currentTimeMs(): Long = System.currentTimeMillis()
