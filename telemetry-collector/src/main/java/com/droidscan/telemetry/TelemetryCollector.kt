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
    private val storageWearCollector: StorageWearCollector = StorageWearCollector(),
    private val powerCollector: PowerCollector = PowerCollector(),
    private val processCollector: ProcessCollector = ProcessCollector(),
    private val networkCollector: NetworkConfigCollector = NetworkConfigCollector(),
    private val simCollector: SimStatusCollector = SimStatusCollector(),
    private val systemCollector: SystemInfoCollector = SystemInfoCollector(),
    private val gnssCollector: GnssCollector = GnssCollector(),
    private val imsCollector: ImsCollector = ImsCollector(),
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

    fun observeStorageWear(): Flow<StorageWearSnapshot> = flow {
        while (true) { emit(storageWearCollector.read(shizukuDetector)); delay(60_000L) }
    }.flowOn(Dispatchers.IO)

    fun observePower(): Flow<PowerSnapshot> = flow {
        while (true) { emit(powerCollector.read()); delay(10000L) }
    }.flowOn(Dispatchers.IO)

    fun observeProcesses(): Flow<ProcessSnapshot> = flow {
        while (true) { emit(processCollector.read(shizukuDetector)); delay(1000L) }
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

    fun observeGnss(): Flow<GnssSnapshot> = flow {
        while (true) { emit(gnssCollector.read()); delay(2000L) }
    }.flowOn(Dispatchers.IO)

    fun observeIms(): Flow<ImsSnapshot> = flow {
        while (true) { emit(imsCollector.read()); delay(2000L) }
    }.flowOn(Dispatchers.IO)

    private var firstDump = false // toggle to true for debugging snapshot output

    fun observeAll(): Flow<MetricSnapshot> = flow {
        while (true) {
            val snapshot = MetricSnapshot(
                radio = radioCollector.read(),
                thermal = thermalCollector.read(shizukuDetector.isAvailable()),
                storage = storageCollector.read(),
                storageWear = storageWearCollector.read(shizukuDetector),
                power = powerCollector.read(),
                processes = processCollector.read(shizukuDetector),
                networkConfig = networkCollector.read(),
                simStatus = simCollector.read(),
                systemInfo = systemCollector.read(),
                gnss = gnssCollector.read(),
                ims = imsCollector.read(),
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

    /** Builds the full human-readable diagnostic report for [snapshot]. */
    fun buildDebugDump(snapshot: MetricSnapshot): String {
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
        sb.appendLine("SS-RSRP: ${r.ssRsrp} | SS-RSRQ: ${r.ssRsrq} | SS-SINR: ${r.ssSinr}")
        sb.appendLine("Bands: ${r.activeBands} | PCI: ${r.pci} | Cell: ${r.cellId} | TAC: ${r.tac}")
        sb.appendLine("EARFCN: ${r.earfcn} | NR-ARFCN: ${r.nrarfcn} | BW: ${r.bandwidthKhz} kHz | DL: ${r.dlFrequencyMhz} MHz")
        sb.appendLine("Network: ${r.dataNetworkType} | Operator: ${r.operatorName}")
        sb.appendLine("PLMN (network): ${r.networkPlmn} | Roaming: ${r.isRoaming} | DataRoam: ${r.dataRoamingEnabled}")
        sb.appendLine("CallState: ${r.callState} | RadioPwr: ${r.radioPowerState}")
        sb.appendLine("IMS: ${r.imsRegistered} | IMS capable: ${r.imsCapable} | ePDG: ${r.epdgTunnelActive}")
        sb.appendLine("VoLTE: ${r.volteEnabled} | WiFiCall: ${r.wifiCallingStatus}")
        sb.appendLine("NR-NSA: ${r.nrNsaAvailable} | PrefNet: ${r.preferredNetworkType}")
        sb.appendLine("")
        sb.appendLine("--- IMS (ImsMmTelManager) ---")
        val ims = snapshot.ims
        if (ims.perSub.isEmpty()) {
            sb.appendLine("  apiAvailable=${ims.apiAvailable} | accessDenied=${ims.accessDenied} | (no per-sub data)")
        } else ims.perSub.forEach { reg ->
            sb.appendLine("  sub ${reg.subscriptionId}: ${reg.registrationState} | ${reg.transportType} | VoLTE=${reg.volteEnabled} | WFC=${reg.wfcEnabled} | VT=${reg.vtEnabled} | WFC-Roam=${reg.wfcRoamingEnabled}")
        }
        sb.appendLine("")
        sb.appendLine("--- GNSS ---")
        val g = snapshot.gnss
        sb.appendLine("Available: ${g.available} | LocationDenied: ${g.locationDenied} | Fix: ${g.hasFix} | Sats: ${g.satellites.size}")
        g.satellites.groupBy { it.constellation }.forEach { (const, list) ->
            sb.appendLine("  $const: ${list.size} visible, ${list.count { it.usedInFix }} in fix, peak ${list.maxOf { it.cn0DbHz }} dB-Hz")
        }
        g.satellites.sortedByDescending { it.cn0DbHz }.take(3).forEach {
            sb.appendLine("    PRN ${it.prn} @ ${"%.1f".format(it.cn0DbHz)} dB-Hz ${if (it.usedInFix) "*" else ""} ${it.carrierFrequencyHz / 1e6}MHz")
        }
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
        sb.appendLine("--- STORAGE WEAR (Power Mode) ---")
        val sw = snapshot.storageWear
        if (sw.powerMode) {
            sb.appendLine("512B write latency: ${sw.writeLatencyMs}ms | sustained write: ${sw.recentWriteKbps} kB/s")
            sb.appendLine("Data free: ${sw.dataFreePercent}% | FBE: ${sw.fileBasedEncryption}")
            sb.appendLine("App: ${sw.appSizeBytes} | Data: ${sw.appDataSizeBytes} | Cache: ${sw.appCacheSizeBytes}")
            sb.appendLine("Photos: ${sw.photosSizeBytes} | Videos: ${sw.videosSizeBytes} | Audio: ${sw.audioSizeBytes}")
        } else {
            sb.appendLine("  (requires Shizuku Power Mode)")
        }
        sb.appendLine("")
        sb.appendLine("--- THERMAL ---")
        snapshot.thermal.zones.forEach { z -> sb.appendLine("  ${z.name}: ${z.temperatureCelsius}°C throttling=${z.throttling}") }
        sb.appendLine("Max: ${snapshot.thermal.maxTemperature} | Throttling: ${snapshot.thermal.isThrottling}")
        sb.appendLine("")
        sb.appendLine("--- POWER ---")
        val p = snapshot.power
        sb.appendLine("Level: ${p.batteryLevel}% | Health: ${p.batteryHealth} | Temp: ${p.batteryTemperature}°C")
        sb.appendLine("Charge: ${p.chargeRateMicroamps} uA | Drain: ${p.currentDrainMicroamps} uA | AvgDraw: ${p.batteryCurrentAverageMicroamps} uA | Counter: ${p.batteryChargeCounterMicroAh} uAh")
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
        return sb.toString()
    }

    /** Legacy log-based dump (used by the "tap uptime" debug affordance). */
    fun debugDump(snapshot: MetricSnapshot) {
        val tag = "DroidscanSnapshot"
        buildDebugDump(snapshot).lines().forEach { line ->
            if (line.isNotBlank()) {
                Log.d(tag, if (line.length > 4000) line.take(4000) + "..." else line)
            }
        }
    }

    fun getRingBuffer(): List<MetricSnapshot> = ringBuffer.toList()
    fun isShizukuAvailable(): Boolean = shizukuDetector.isAvailable()
    fun transport(): String = shizukuDetector.transport()
}
