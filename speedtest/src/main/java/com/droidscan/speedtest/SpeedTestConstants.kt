package com.droidscan.speedtest

/**
 * Test configuration and thresholds, ported from VitalPing's `Constants`.
 * Download uses a 10 MB payload across 6 parallel streams with an 8 MB warmup;
 * upload uses 6 × 1 MB streaming parts. Thresholds are absolute for latency
 * and jitter, and contract-relative for speed.
 */
object SpeedTestConstants {

    object TestSize {
        const val SMALL = 1_000_000L        // 1 MB
        const val MEDIUM = 10_000_000L      // 10 MB
        const val LARGE = 25_000_000L       // 25 MB
        const val XLARGE = 100_000_000L     // 100 MB
    }

    object Streams {
        const val DOWNLOAD_STREAMS = 6
        const val UPLOAD_PARTS = 6
    }

    object Warmup {
        const val WARMUP_BYTES = 8_000_000L       // 8 MB (excluded from timing)
        const val MIN_TEST_DURATION_MS = 4_000L   // 4 s floor
    }

    object DefaultContract {
        const val DOWNLOAD_MBPS = 100.0
        const val UPLOAD_MBPS = 20.0
    }

    object LatencyThresholds {
        const val GOOD = 50.0   // ≤50 ms good
        const val WARN = 100.0  // ≤100 ms warn, else poor
    }

    object JitterThresholds {
        const val GOOD = 15.0
        const val WARN = 30.0
    }

    object ContractThresholds {
        const val GOOD_PERCENT = 0.80   // ≥80% of contract = good
        const val WARN_PERCENT = 0.50   // ≥50% = warn, else poor
    }
}
