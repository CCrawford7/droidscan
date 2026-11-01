package com.droidscan.speedtest

// ──────────────────────────────────────────────
// Speed-test result models (ported from VitalPing,
// trimmed to what the engine actually measures).
// ──────────────────────────────────────────────

/** Network metrics from a completed speed test. */
data class Metrics(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMs: Double,
    val jitterMs: Double,

    // Actual byte counts from the engine (for data-usage awareness).
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0,
    val downloadDurationMs: Long = 0,
    val uploadDurationMs: Long = 0,

    val downloadStatus: MetricStatus = MetricStatus.NEUTRAL,
    val uploadStatus: MetricStatus = MetricStatus.NEUTRAL,
    val latencyStatus: MetricStatus = MetricStatus.NEUTRAL,
    val jitterStatus: MetricStatus = MetricStatus.NEUTRAL
)

/** Status for an individual metric, mapped to colour in the UI. */
enum class MetricStatus { GOOD, WARN, POOR, NEUTRAL }

/** Phase of the speed test, used for live progress UI. */
enum class TestPhase { IDLE, LATENCY, DOWNLOAD, UPLOAD, COMPLETE }
