package com.droidscan.widget

import android.content.Context
import android.util.Log
import com.droidscan.shared.core.MetricSnapshot
import java.io.File
import java.util.Properties

/**
 * The compact metric snapshot a home-screen widget can render. Kept deliberately
 * tiny (signal, battery, RAM) — a widget is a glance, not the full dashboard.
 */
data class WidgetData(
    val rsrp: Int?,
    val rat: String?,
    val battery: Int?,
    val batteryTemp: Float?,
    val ramUsedBytes: Long?,
    val ramTotalBytes: Long?
)

/**
 * Persists the latest widget-visible metrics to a `Properties` file in filesDir
 * so the Glance widget (composed in a separate pass) can read them without
 * touching the live collector. A plain file in filesDir is used instead of
 * SharedPreferences — filesDir is already the app's reliable persistence root
 * (the encrypted history store lives there). Write is throttled to avoid a disk
 * write per 1s snapshot tick.
 */
object WidgetMetricsStore {

    private const val FILE_NAME = "widget.properties"
    private const val THROTTLE_MS = 10_000L

    private var lastWriteMs = 0L

    /** Persists [s] if the throttle allows; returns true when a write happened. */
    fun write(context: Context, s: MetricSnapshot): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWriteMs < THROTTLE_MS) return false
        lastWriteMs = now
        Log.i("WidgetStore", "write rsrp=${s.radio.rsrp} battery=${s.power.batteryLevel}")

        val props = Properties().apply {
            s.radio.rsrp?.let { setProperty("rsrp", it.toString()) }
            s.radio.dataNetworkType?.let { setProperty("rat", it) }
            s.power.batteryLevel?.let { setProperty("battery", it.toString()) }
            s.power.batteryTemperature?.let { setProperty("batteryTemp", it.toString()) }
            s.processes.usedMemoryBytes?.let { setProperty("ramUsed", it.toString()) }
            s.processes.totalMemoryBytes?.let { setProperty("ramTotal", it.toString()) }
            setProperty("at", now.toString())
        }
        File(context.filesDir, FILE_NAME).writer().use { props.store(it, null) }
        return true
    }

    fun read(context: Context): WidgetData {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return WidgetData(null, null, null, null, null, null)
        val props = Properties().apply { file.reader().use { load(it) } }
        return WidgetData(
            rsrp = props.getProperty("rsrp")?.toIntOrNull(),
            rat = props.getProperty("rat")?.takeIf { it.isNotBlank() },
            battery = props.getProperty("battery")?.toIntOrNull(),
            batteryTemp = props.getProperty("batteryTemp")?.toFloatOrNull(),
            ramUsedBytes = props.getProperty("ramUsed")?.toLongOrNull(),
            ramTotalBytes = props.getProperty("ramTotal")?.toLongOrNull()
        )
    }
}
