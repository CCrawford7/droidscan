package com.droidscan

import android.app.Application
import android.util.Log
import com.droidscan.shared.core.MetricSnapshot
import com.droidscan.telemetry.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

class DroidscanApp : Application() {

    lateinit var telemetryCollector: TelemetryCollector
        private set

    // Single collection loop for the whole process. Both the UI (MainActivity)
    // and the foreground TelemetryService observe this StateFlow, so there is
    // exactly one telemetry pass per tick — one sensor read, one history append.
    // Without this, activity + service each ran observeAll() (double reads,
    // double encrypted-history appends).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _liveSnapshot = MutableStateFlow(MetricSnapshot())
    val liveSnapshot: StateFlow<MetricSnapshot> = _liveSnapshot.asStateFlow()

    private var telemetryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Wire up collectors with system services
        val radioCollector = RadioCollector().also {
            val tm = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            it.attach(tm)
            it.attachContext(this)
        }

        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager

        val thermalCollector = ThermalCollector().also {
            it.attach(powerManager)
        }

        val powerCollector = PowerCollector().also {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            it.attach(bm, powerManager, this)
        }

        val processCollector = ProcessCollector().also {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            it.attach(am)
        }

        val networkCollector = NetworkConfigCollector().also {
            it.attach(this)
        }

        val simCollector = SimStatusCollector().also {
            it.attach(this)
        }

        val systemCollector = SystemInfoCollector().also {
            val wm = getSystemService(WINDOW_SERVICE) as? android.view.WindowManager
            it.attach(wm)
        }

        val gnssCollector = GnssCollector().also {
            it.attach(this)
        }

        val imsCollector = ImsCollector().also {
            it.attach(this)
        }

        // T2: Shizuku bridge. initialize() binds the UserService lifecycle and
        // requests permission when the server is running; collectors consult
        // isAvailable()/runCommand() to fetch shell-privileged telemetry.
        val shizukuDetector = ShizukuDetector().apply {
            initialize(this@DroidscanApp)
        }

        // File-backed metric history (P1): powers trend charts + the future
        // diagnostic engine. Bounded to ~1h of 1s snapshots. Encrypted at rest
        // (AES-GCM, key in Android Keystore); legacy plaintext auto-migrates.
        val historyStore = com.droidscan.shared.core.MetricHistoryStore(
            file = java.io.File(filesDir, "metrics_history.jsonl"),
            encryptor = com.droidscan.shared.core.KeystoreAesGcmLineEncryptor()
        )

        telemetryCollector = TelemetryCollector(
            radioCollector = radioCollector,
            thermalCollector = thermalCollector,
            storageCollector = StorageCollector(),
            powerCollector = powerCollector,
            processCollector = processCollector,
            networkCollector = networkCollector,
            simCollector = simCollector,
            systemCollector = systemCollector,
            gnssCollector = gnssCollector,
            imsCollector = imsCollector,
            shizukuDetector = shizukuDetector,
            historyStore = historyStore
        )

        // Kick the single collection loop. It runs for the process lifetime,
        // feeding liveSnapshot (UI + notification) and the encrypted history.
        restartTelemetry()

        // Startup health check for the encrypted history store — the trend
        // charts depend on it, so surface any decrypt/read failure immediately.
        appScope.launch {
            try {
                val recent = historyStore.recent(3)
                Log.i(
                    TAG,
                    "history: size=${historyStore.size()} encrypted=${historyStore.isEncrypted()} " +
                        "recent3=${recent.size} sampleCpu=${recent.lastOrNull()?.processes?.perCoreLoad?.size ?: -1}"
                )
            } catch (t: Throwable) {
                Log.e(TAG, "history health check failed: ${t.message}")
            }
        }
    }

    /**
     * Starts (or restarts) the single telemetry loop. Idempotent — called from
     * onCreate and from the UI "Restart" affordance when the loop dies. observeAll()
     * is a cold Flow, so each call launches a fresh collection pass; collectors are
     * stateless between passes (they hold system-service refs only).
     */
    @Synchronized
    fun restartTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = appScope.launch {
            try {
                telemetryCollector.observeAll().collect { _liveSnapshot.value = it }
            } catch (t: Throwable) {
                Log.e(TAG, "telemetry loop stopped: ${t.message}", t)
            }
        }
    }

    companion object {
        private const val TAG = "DroidscanApp"
        lateinit var instance: DroidscanApp
            private set
    }
}
