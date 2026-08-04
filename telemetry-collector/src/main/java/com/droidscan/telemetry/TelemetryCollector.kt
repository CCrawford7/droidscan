package com.droidscan.telemetry

import android.util.Log
import com.droidscan.shared.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class TelemetryCollector(
    private val radioCollector: RadioCollector = RadioCollector(),
    private val thermalCollector: ThermalCollector = ThermalCollector(),
    private val storageCollector: StorageCollector = StorageCollector(),
    private val powerCollector: PowerCollector = PowerCollector(),
    private val processCollector: ProcessCollector = ProcessCollector(),
    private val networkCollector: NetworkConfigCollector = NetworkConfigCollector(),
    private val simCollector: SimStatusCollector = SimStatusCollector(),
    private val systemCollector: SystemInfoCollector = SystemInfoCollector(),
    private val shizukuDetector: ShizukuDetector = ShizukuDetector(),
    private val historyStore: MetricHistoryStore? = null
) {
    private val ringBuffer = CircularBuffer<MetricSnapshot>(60)

    fun observeRadio(): Flow<RadioSnapshot> = flow {
        while (true) { emit(radioCollector.read()); delay(1000L) }
    }.flowOn(Dispatchers.IO)

    fun observeThermal(): Flow<ThermalSnapshot> = flow {
        while (true) { emit(thermalCollector.read(shizukuDetector.isAvailable())); delay(5000L) }
    }.flowOn(Dispatchers.IO)

    fun observeStorage(): Flow<StorageSnapshot> = flow {
        while (true) { emit(storageCollector.read()); delay(5000L) }
    }.flowOn(Dispatchers.IO)

    fun observePower(): Flow<PowerSnapshot> = flow {
        while (true) { emit(powerCollector.read()); delay(10000L) }
    }.flowOn(Dispatchers.IO)

    fun observeProcesses(): Flow<ProcessSnapshot> = flow {
        while (true) { emit(processCollector.read(shizukuDetector.isAvailable())); delay(1000L) }
    }.flowOn(Dispatchers.IO)

    fun observeNetworkConfig(): Flow<NetworkConfigSnapshot> = flow {
        while (true) { emit(networkCollector.read()); delay(5000L) }
    }.flowOn(Dispatchers.IO)

    fun observeSimStatus(): Flow<SimStatusSnapshot> = flow {
        while (true) { emit(simCollector.read()); delay(5000L) }
    }.flowOn(Dispatchers.IO)

    fun observeSystemInfo(): Flow<SystemInfoSnapshot> = flow {
        emit(systemCollector.read())
    }.flowOn(Dispatchers.IO)

    private var firstDump = false // toggle to true for debugging snapshot output

    fun observeAll(): Flow<MetricSnapshot> = flow {
        while (true) {
            val snapshot = MetricSnapshot(
                radio = radioCollector.read(),
                thermal = thermalCollector.read(shizukuDetector.isAvailable()),
                storage = storageCollector.read(),
                power = powerCollector.read(),
                processes = processCollector.read(shizukuDetector.isAvailable()),
                networkConfig = networkCollector.read(),
                simStatus = simCollector.read(),
                systemInfo = systemCollector.read(),
                shizukuAvailable = shizukuDetector.isAvailable()
            )
            ringBuffer.add(snapshot)
            historyStore?.append(snapshot)
            if (firstDump) {
                debugDump(snapshot)
                firstDump = false
            }
            emit(snapshot)
            delay(1000L)
        }
    }.flowOn(Dispatchers.IO)

    /** Most-recent [n] persisted snapshots, oldest-first (empty if no store). */
    fun history(n: Int): List<MetricSnapshot> = historyStore?.recent(n) ?: emptyList()

    fun debugDump(snapshot: MetricSnapshot) {
        val sb = StringBuilder()
        sb.appendLine("==== DROIDSCAN SNAPSHOT ====")
        sb.appendLine("Device: ${snapshot.systemInfo.deviceModel} (${snapshot.systemInfo.manufacturer})")
        sb.appendLine("Android: ${snapshot.systemInfo.androidVersion} | Build: ${snapshot.systemInfo.buildNumber}")
        sb.appendLine("Kernel: ${snapshot.systemInfo.kernelVersion} | Security: ${snapshot.systemInfo.securityPatch}")
        sb.appendLine("SELinux: ${snapshot.systemInfo.selinuxEnforced} | Uptime: ${snapshot.systemInfo.uptimeMs}ms")
        sb.appendLine("Resolution: ${snapshot.systemInfo.resolution} | Density: ${snapshot.systemInfo.densityDpi}")
        sb.appendLine("")
        sb.appendLine("--- RADIO / SUBSCRIBER ---")
        val r = snapshot.radio
        sb.appendLine("RSRP: ${r.rsrp} dBm | RSRQ: ${r.rsrq} dB | SINR: ${r.sinr} dB")
        sb.appendLine("Bands: ${r.activeBands} | Cell: ${r.cellId} | TAC: ${r.tac}")
        sb.appendLine("Network: ${r.dataNetworkType} | Operator: ${r.operatorName}")
        sb.appendLine("PLMN (network): ${r.networkPlmn} | Roaming: ${r.isRoaming} | DataRoam: ${r.dataRoamingEnabled}")
        sb.appendLine("CallState: ${r.callState} | RadioPwr: ${r.radioPowerState}")
        sb.appendLine("IMS: ${r.imsRegistered} | IMS capable: ${r.imsCapable} | ePDG: ${r.epdgTunnelActive}")
        sb.appendLine("VoLTE: ${r.volteEnabled} | WiFiCall: ${r.wifiCallingStatus}")
        sb.appendLine("NR-NSA: ${r.nrNsaAvailable} | PrefNet: ${r.preferredNetworkType}")
        sb.appendLine("")
        sb.appendLine("--- NETWORK ---")
        val nw = snapshot.networkConfig
        sb.appendLine("Type: ${nw.activeNetworkType} | APN: ${nw.apnName}")
        sb.appendLine("IPv4: ${nw.ipv4Addresses} | IPv6: ${nw.ipv6Addresses}")
        sb.appendLine("DNS: ${nw.dnsServers} | WiFi: ${nw.wifiSsid} (${nw.wifiLinkSpeedMbps} Mbps, RSSI ${nw.wifiRssi} dBm)")
        sb.appendLine("Latency: ${nw.latencyMs} ms | VPN: ${nw.vpnActive} | Hotspot: ${nw.hotspotActive}")
        sb.appendLine("")
        sb.appendLine("--- SIM ---")
        snapshot.simStatus.slots.forEach { s ->
            sb.appendLine("  Slot ${s.slotIndex}: ${s.carrierName} | provisioned=${s.isProvisioned} | eSIM=${s.esimProfileCount} | ICCID=...${s.iccidLast4}")
            sb.appendLine("    homePLMN=${s.homePlmn} | SPN=${s.simOperatorName} | MSISDN=${s.msisdn} | IMSI=${s.imsiPrefix}")
        }
        sb.appendLine("")
        sb.appendLine("--- STORAGE ---")
        val st = snapshot.storage
        sb.appendLine("Internal used=${st.internalUsedBytes} free=${st.internalFreeBytes}")
        sb.appendLine("External: mounted=${st.externalMounted} free=${st.externalFreeBytes}")
        st.topConsumers.forEach { c -> sb.appendLine("  ${c.label}: ${c.sizeBytes}") }
        sb.appendLine("")
        sb.appendLine("--- THERMAL ---")
        snapshot.thermal.zones.forEach { z -> sb.appendLine("  ${z.name}: ${z.temperatureCelsius}°C throttling=${z.throttling}") }
        sb.appendLine("Max: ${snapshot.thermal.maxTemperature} | Throttling: ${snapshot.thermal.isThrottling}")
        sb.appendLine("")
        sb.appendLine("--- POWER ---")
        val p = snapshot.power
        sb.appendLine("Level: ${p.batteryLevel}% | Health: ${p.batteryHealth} | Temp: ${p.batteryTemperature}°C")
        sb.appendLine("Charge: ${p.chargeRateMicroamps} uA | Drain: ${p.currentDrainMicroamps} uA")
        sb.appendLine("Time remaining: ${p.estimatedTimeRemainingMs} | eSIM: ${p.esimActive} | BT: ${p.bluetoothActive} | Hotspot: ${p.hotspotActive}")
        sb.appendLine("")
        sb.appendLine("--- CPU / MEMORY ---")
        val pr = snapshot.processes
        sb.appendLine("Cores: ${pr.perCoreLoad.map { "%.0f%%".format(it * 100) }}")
        sb.appendLine("RAM: ${pr.usedMemoryBytes} / ${pr.totalMemoryBytes} | Pressure: ${pr.memoryPressure}")
        pr.topMemoryProcesses.forEach { proc -> sb.appendLine("  ${proc.name}: ${proc.memoryBytes} bytes") }
        if (pr.kernelWakelocks.isNotEmpty()) sb.appendLine("Wakelocks: ${pr.kernelWakelocks}")
        sb.appendLine("")
        sb.appendLine("--- SYSTEM ---")
        val sys = snapshot.systemInfo
        sb.appendLine("Model: ${sys.deviceModel} | Android: ${sys.androidVersion} | Build: ${sys.buildNumber}")
        sb.appendLine("Kernel: ${sys.kernelVersion} | Security: ${sys.securityPatch} | Resolution: ${sys.resolution}")
        sb.appendLine("")
        sb.appendLine("Shizuku: ${snapshot.shizukuAvailable}")
        sb.appendLine("==== END SNAPSHOT ====")

        val tag = "DroidscanSnapshot"
        sb.toString().lines().forEach { line ->
            if (line.isNotBlank()) {
                Log.d(tag, if (line.length > 4000) line.take(4000) + "..." else line)
            }
        }
    }

    fun getRingBuffer(): List<MetricSnapshot> = ringBuffer.toList()
    fun isShizukuAvailable(): Boolean = shizukuDetector.isAvailable()
}
