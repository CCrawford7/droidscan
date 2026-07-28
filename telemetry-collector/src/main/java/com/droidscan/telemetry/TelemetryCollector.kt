package com.droidscan.telemetry

import com.droidscan.shared.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

// ──────────────────────────────────────────────
// Central telemetry collector — exposes all
// metric streams as Kotlin Flows.
// ──────────────────────────────────────────────

class TelemetryCollector(
    private val radioCollector: RadioCollector = RadioCollector(),
    private val thermalCollector: ThermalCollector = ThermalCollector(),
    private val storageCollector: StorageCollector = StorageCollector(),
    private val powerCollector: PowerCollector = PowerCollector(),
    private val processCollector: ProcessCollector = ProcessCollector(),
    private val networkCollector: NetworkConfigCollector = NetworkConfigCollector(),
    private val simCollector: SimStatusCollector = SimStatusCollector(),
    private val systemCollector: SystemInfoCollector = SystemInfoCollector(),
    private val shizukuDetector: ShizukuDetector = ShizukuDetector()
) {
    private val ringBuffer = CircularBuffer<MetricSnapshot>(60)

    fun observeRadio(): Flow<RadioSnapshot> = flow {
        while (true) {
            emit(radioCollector.read())
            delay(1000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observeThermal(): Flow<ThermalSnapshot> = flow {
        while (true) {
            emit(thermalCollector.read(shizukuDetector.isAvailable()))
            delay(5000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observeStorage(): Flow<StorageSnapshot> = flow {
        while (true) {
            emit(storageCollector.read())
            delay(5000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observePower(): Flow<PowerSnapshot> = flow {
        while (true) {
            emit(powerCollector.read())
            delay(10000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observeProcesses(): Flow<ProcessSnapshot> = flow {
        while (true) {
            emit(processCollector.read(shizukuDetector.isAvailable()))
            delay(1000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observeNetworkConfig(): Flow<NetworkConfigSnapshot> = flow {
        while (true) {
            emit(networkCollector.read())
            delay(5000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observeSimStatus(): Flow<SimStatusSnapshot> = flow {
        while (true) {
            emit(simCollector.read())
            delay(5000L)
        }
    }.flowOn(Dispatchers.IO)

    fun observeSystemInfo(): Flow<SystemInfoSnapshot> = flow {
        emit(systemCollector.read())
        // System info rarely changes — read once at start
    }.flowOn(Dispatchers.IO)

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
            emit(snapshot)
            delay(1000L)
        }
    }.flowOn(Dispatchers.IO)

    fun getRingBuffer(): List<MetricSnapshot> = ringBuffer.toList()
    fun isShizukuAvailable(): Boolean = shizukuDetector.isAvailable()
}
