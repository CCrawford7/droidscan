package com.droidscan.speedtest

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTestEngineTest {

    private val engine = SpeedTestEngine()

    @Test
    fun `download URL uses query params for cloudflare`() {
        assertEquals(
            "https://speed.cloudflare.com/__down?bytes=100&cacheBust=42",
            engine.buildDownloadUrl("https://speed.cloudflare.com/__down", 100L, 42L)
        )
    }

    @Test
    fun `download URL uses fixed file for ovh`() {
        assertEquals(
            "https://proof.ovh.net/files/100Mb.dat?cacheBust=42",
            engine.buildDownloadUrl("https://proof.ovh.net/files/", 100L, 42L)
        )
    }

    @Test
    fun `download URL defaults to query-param style`() {
        assertEquals(
            "https://example.com/dl?bytes=100&cacheBust=42",
            engine.buildDownloadUrl("https://example.com/dl", 100L, 42L)
        )
    }

    @Test
    fun `speed status is contract-relative`() {
        assertEquals(MetricStatus.GOOD, engine.calculateSpeedStatus(90.0, 100.0))   // 90%
        assertEquals(MetricStatus.WARN, engine.calculateSpeedStatus(60.0, 100.0))   // 60%
        assertEquals(MetricStatus.POOR, engine.calculateSpeedStatus(40.0, 100.0))   // 40%
    }

    @Test
    fun `latency and jitter statuses use absolute thresholds`() {
        assertEquals(MetricStatus.GOOD, engine.calculateLatencyStatus(30.0))
        assertEquals(MetricStatus.WARN, engine.calculateLatencyStatus(75.0))
        assertEquals(MetricStatus.POOR, engine.calculateLatencyStatus(150.0))

        assertEquals(MetricStatus.GOOD, engine.calculateJitterStatus(8.0))
        assertEquals(MetricStatus.WARN, engine.calculateJitterStatus(20.0))
        assertEquals(MetricStatus.POOR, engine.calculateJitterStatus(45.0))
    }

    @Test
    fun `endpoints fail over and reset per test`() {
        val endpoints = SpeedTestEndpoints(
            download = "https://d.primary", downloadFallback = "https://d.fallback",
            upload = "https://u.primary", uploadFallback = "https://u.fallback"
        )
        assertEquals("https://d.primary", endpoints.downloadEndpoint)
        endpoints.markDownloadFailed()
        assertEquals("https://d.fallback", endpoints.downloadEndpoint)
        endpoints.resetForNewTest()
        assertEquals("https://d.primary", endpoints.downloadEndpoint)
    }
}
