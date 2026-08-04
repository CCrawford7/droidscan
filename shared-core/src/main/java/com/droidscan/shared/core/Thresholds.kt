package com.droidscan.shared.core

// ──────────────────────────────────────────────
// Threshold constants & status colour helpers
// Green = good, Yellow = caution, Red = critical
// ──────────────────────────────────────────────

enum class StatusTier { GREEN, YELLOW, RED }

data class ThresholdRange(
    val greenUpper: Double,   // values <= this are green
    val yellowUpper: Double,  // values <= this are yellow
    // values > yellowUpper are red
)

object RadioThresholds {
    // RSRP in dBm: higher (less negative) is better
    val rsrp = ThresholdRange(greenUpper = -100.0, yellowUpper = -120.0)
    // RSRQ in dB: higher (less negative) is better
    val rsrq = ThresholdRange(greenUpper = -10.0, yellowUpper = -15.0)
    // SINR in dB: higher is better
    val sinr = ThresholdRange(greenUpper = 13.0, yellowUpper = 5.0)
}

object ThermalThresholds {
    // Celsius
    val zoneTemp = ThresholdRange(greenUpper = 40.0, yellowUpper = 55.0)
}

object BatteryThresholds {
    val levelPercent = ThresholdRange(greenUpper = 20.0, yellowUpper = 5.0)
    val temperatureCelsius = ThresholdRange(greenUpper = 38.0, yellowUpper = 45.0)
}

object StorageThresholds {
    val freePercent = ThresholdRange(greenUpper = 20.0, yellowUpper = 5.0)
}

object MemoryThresholds {
    val pressure = ThresholdRange(greenUpper = 0.5, yellowUpper = 0.8) // 0..1 scale
}

/**
 * Evaluates a metric value against a [ThresholdRange].
 *
 * Two directional semantics exist, because a "green zone" is defined by the
 * threshold boundaries but the metric may be better when HIGHER *or* LOWER.
 *
 * Default ([inverted] = false): **lower values are better** (e.g. temperature,
 * memory pressure). A value is GREEN when it is *at or below* [ThresholdRange.greenUpper],
 * YELLOW when *at or below* [ThresholdRange.yellowUpper], otherwise RED.
 *
 * Inverted ([inverted] = true): **higher values are better** (e.g. RSRP, SINR,
 * battery level). A value is GREEN when it is *at or above* [ThresholdRange.greenUpper],
 * YELLOW when *at or above* [ThresholdRange.yellowUpper], otherwise RED.
 */
fun evaluateStatus(value: Double, range: ThresholdRange, inverted: Boolean = false): StatusTier =
    if (inverted) {
        when {
            value >= range.greenUpper -> StatusTier.GREEN
            value >= range.yellowUpper -> StatusTier.YELLOW
            else -> StatusTier.RED
        }
    } else {
        when {
            value <= range.greenUpper -> StatusTier.GREEN
            value <= range.yellowUpper -> StatusTier.YELLOW
            else -> StatusTier.RED
        }
    }

/** Alias for [evaluateStatus] with [inverted] = true — kept for call-site clarity. */
fun evaluateStatusInverted(value: Double, range: ThresholdRange): StatusTier =
    evaluateStatus(value, range, inverted = true)

fun StatusTier.toHexColor(): Long = when (this) {
    StatusTier.GREEN -> 0xFF4CAF50
    StatusTier.YELLOW -> 0xFFFFC107
    StatusTier.RED -> 0xFFF44336
}
