package com.droidscan.shared.core

/**
 * Shared presentation helpers for ring-based signal gauges (Overview rework).
 *
 * Kept in :shared-core (pure Kotlin) so the ring normalization and the spoken
 * accessibility labels are unit-testable without an Android runtime.
 */

/**
 * Normalizes a metric value to 0..1 for a circular gauge. [min] maps to 0,
 * [max] maps to 1, everything clamps into range. `null` → 0 (empty ring).
 */
fun ringProgress(value: Float?, min: Float, max: Float): Float {
    if (value == null) return 0f
    val range = max - min
    if (range == 0f) return 0f
    return ((value - min) / range).coerceIn(0f, 1f)
}

/** Spoken word for a status tier in accessibility labels ("fair", not "caution"). */
fun StatusTier.signalWord(): String = when (this) {
    StatusTier.GREEN -> "good"
    StatusTier.YELLOW -> "fair"
    StatusTier.RED -> "critical"
}

/**
 * Cohesive spoken label for a per-SIM ring cluster. Maps each live signal metric
 * to its tier word + raw value so TalkBack reads the rings as one sentence, e.g.
 *   "SIM 1 network health. Signal strength good, -85 dBm. Interference fair,
 *    -11 dB. Signal quality good, 18 dB."
 * Missing metrics are omitted; a fully-empty cluster degrades to "No signal data."
 */
fun signalAccessibilityLabel(simLabel: String, rsrp: Int?, rsrq: Int?, sinr: Int?): String {
    val sb = StringBuilder("$simLabel network health.")
    rsrp?.let { v ->
        val tier = evaluateStatusInverted(v.toDouble(), RadioThresholds.rsrp)
        sb.append(" Signal strength ${tier.signalWord()}, $v dBm.")
    }
    rsrq?.let { v ->
        val tier = evaluateStatusInverted(v.toDouble(), RadioThresholds.rsrq)
        sb.append(" Interference ${tier.signalWord()}, $v dB.")
    }
    sinr?.let { v ->
        val tier = evaluateStatusInverted(v.toDouble(), RadioThresholds.sinr)
        sb.append(" Signal quality ${tier.signalWord()}, $v dB.")
    }
    if (rsrp == null && rsrq == null && sinr == null) sb.append(" No signal data.")
    return sb.toString()
}
