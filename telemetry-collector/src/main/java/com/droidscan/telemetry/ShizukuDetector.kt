package com.droidscan.telemetry

// ──────────────────────────────────────────────
// Shizuku availability detector.
// In v1.0, this wraps the Shizuku API check.
// When Shizuku is not installed, returns false
// and all collectors fall back to Standard Mode.
// ──────────────────────────────────────────────

class ShizukuDetector {
    private var available = false
    private var checked = false

    /**
     * Call once at app startup to detect Shizuku.
     * In production this calls ShizukuProvider.ping().
     * Returns true if Shizuku API 13+ is available and permission granted.
     */
    fun initialize() {
        // This will use ShizukuProvider.ping() when the dependency is linked.
        // For now, detection is a compile-time constant — real integration
        // happens when dev.rikka.shizuku:api is available at runtime.
        available = try {
            // ShizukuProvider.ping() would go here
            false
        } catch (_: NoClassDefFoundError) {
            false
        }
        checked = true
    }

    /**
     * Force-set availability (useful for testing or when Shizuku is
     * detected through alternative means like Wireless ADB check).
     */
    fun setAvailable(available: Boolean) {
        this.available = available
        this.checked = true
    }

    fun isAvailable(): Boolean = available
    fun hasChecked(): Boolean = checked
}
