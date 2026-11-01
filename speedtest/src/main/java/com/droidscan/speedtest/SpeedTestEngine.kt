package com.droidscan.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Speed-test engine using OkHttp, ported from the VitalPing project.
 *
 * Methodology:
 *  - Download timing excludes connection overhead (starts at first byte).
 *  - An AtomicBoolean warmup flag coordinates multi-stream timing.
 *  - Upload uses max(clientMs, serverMs) for accuracy; the server returns the
 *    time it spent receiving the body, parsed from the response JSON.
 *  - Progress callbacks are throttled to ~150 ms to avoid excessive recomposition.
 *  - Upload uses a streaming RequestBody (~32 KB heap) instead of a full buffer.
 *  - The speed client disables transparent gzip so byte counts are accurate.
 */
class SpeedTestEngine(
    private val endpoints: SpeedTestEndpoints = SpeedTestEndpoints.default()
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Dedicated client for throughput tests — disables transparent gzip. */
    private val speedTestClient = client.newBuilder()
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept-Encoding", "identity")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * Run a complete speed-test sequence.
     *
     * @param contractDownloadMbps User's contract download speed (status reference only).
     * @param contractUploadMbps User's contract upload speed.
     * @param onProgress Progress 0..1, current phase, and live Mbps (download/upload only).
     * @return [Result] with [Metrics] on success.
     */
    suspend fun runSpeedTest(
        contractDownloadMbps: Double,
        contractUploadMbps: Double,
        onProgress: (Float, TestPhase, Double?) -> Unit
    ): Result<Metrics> = withContext(Dispatchers.IO) {
        try {
            endpoints.resetForNewTest()

            // Phase 1: latency + jitter (10% of progress).
            onProgress(0f, TestPhase.LATENCY, null)
            val (latencyMs, jitterMs) = measureLatency()
            onProgress(0.1f, TestPhase.LATENCY, null)

            // Phase 2: download (40% of progress).
            onProgress(0.1f, TestPhase.DOWNLOAD, null)
            val downloadResult = try {
                measureDownload(endpoints.downloadEndpoint) { progress, currentMbps ->
                    onProgress(0.1f + progress * 0.4f, TestPhase.DOWNLOAD, currentMbps)
                }
            } catch (e: Exception) {
                endpoints.markDownloadFailed()
                measureDownload(endpoints.downloadEndpoint) { progress, currentMbps ->
                    onProgress(0.1f + progress * 0.4f, TestPhase.DOWNLOAD, currentMbps)
                }
            }

            // Phase 3: upload (40% of progress).
            onProgress(0.5f, TestPhase.UPLOAD, null)
            val uploadResult = try {
                measureUpload(endpoints.uploadEndpoint) { progress, currentMbps ->
                    onProgress(0.5f + progress * 0.4f, TestPhase.UPLOAD, currentMbps)
                }
            } catch (e: Exception) {
                endpoints.markUploadFailed()
                measureUpload(endpoints.uploadEndpoint) { progress, currentMbps ->
                    onProgress(0.5f + progress * 0.4f, TestPhase.UPLOAD, currentMbps)
                }
            }

            // Phase 4: statuses (10% of progress).
            onProgress(0.9f, TestPhase.COMPLETE, null)

            val downloadStatus = calculateSpeedStatus(downloadResult.mbps, contractDownloadMbps)
            val uploadStatus = calculateSpeedStatus(uploadResult.mbps, contractUploadMbps)
            val latencyStatus = calculateLatencyStatus(latencyMs)
            val jitterStatus = calculateJitterStatus(jitterMs)

            onProgress(1f, TestPhase.COMPLETE, null)

            Result.success(
                Metrics(
                    downloadMbps = downloadResult.mbps,
                    uploadMbps = uploadResult.mbps,
                    latencyMs = latencyMs,
                    jitterMs = jitterMs,
                    downloadBytes = downloadResult.bytes,
                    uploadBytes = uploadResult.bytes,
                    downloadDurationMs = downloadResult.durationMs,
                    uploadDurationMs = uploadResult.durationMs,
                    downloadStatus = downloadStatus,
                    uploadStatus = uploadStatus,
                    latencyStatus = latencyStatus,
                    jitterStatus = jitterStatus
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Measure latency with HEAD requests to Cloudflare's 1-byte endpoint.
     * Discards the first sample (cold start: DNS/TCP/TLS), then takes the
     * median latency and the mean-absolute-deviation jitter.
     */
    private suspend fun measureLatency(): Pair<Double, Double> {
        val samples = mutableListOf<Long>()
        val checkUrl = "https://speed.cloudflare.com/__down?bytes=1"

        repeat(11) { index ->
            try {
                val start = System.nanoTime()
                val request = Request.Builder().url(checkUrl).head().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && index > 0) {
                        samples.add(System.nanoTime() - start)
                    }
                }
            } catch (_: IOException) {
                // Skip failed samples.
            }
            delay(50)
        }

        if (samples.isEmpty()) throw IOException("Failed to measure latency")

        samples.sort()
        val medianNanos = if (samples.size % 2 == 0) {
            (samples[samples.size / 2 - 1] + samples[samples.size / 2]) / 2
        } else {
            samples[samples.size / 2]
        }
        val medianMs = medianNanos / 1_000_000.0
        val jitterMs = samples.map { abs(it - medianNanos) / 1_000_000.0 }.average()
        return Pair(medianMs, jitterMs)
    }

    /**
     * Multi-stream parallel download. Timing starts at first byte; an 8 MB
     * warmup is excluded; the client disables gzip for accurate byte counts.
     */
    private suspend fun measureDownload(
        downloadEndpoint: String,
        onProgress: (Float, Double?) -> Unit
    ): SpeedResult = coroutineScope {
        val totalBytes = AtomicLong(0)
        val testSize = SpeedTestConstants.TestSize.MEDIUM
        val streamCount = SpeedTestConstants.Streams.DOWNLOAD_STREAMS
        val warmupBytes = SpeedTestConstants.Warmup.WARMUP_BYTES

        val warmupComplete = AtomicBoolean(false)
        val measuredStartTime = AtomicLong(System.nanoTime())
        val lastProgressReport = AtomicLong(0)

        val jobs = (0 until streamCount).map { streamIndex ->
            async {
                var streamBytes = 0L
                val cacheBuster = System.currentTimeMillis() + streamIndex
                try {
                    val url = buildDownloadUrl(downloadEndpoint, testSize, cacheBuster)
                    val request = Request.Builder().url(url).get().build()

                    speedTestClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@async 0L
                        val responseStartTime = System.nanoTime()

                        val source = response.body?.source() ?: return@async 0L
                        val buffer = ByteArray(32768)
                        while (!source.exhausted()) {
                            val read = source.read(buffer)
                            if (read == -1) break

                            streamBytes += read
                            val currentTotal = totalBytes.addAndGet(read.toLong())

                            if (currentTotal >= warmupBytes && warmupComplete.compareAndSet(false, true)) {
                                measuredStartTime.set(System.nanoTime())
                            }

                            val now = System.nanoTime()
                            val lastReport = lastProgressReport.get()
                            if (now - lastReport >= 150_000_000L && lastProgressReport.compareAndSet(lastReport, now)) {
                                val currentMbps = if (warmupComplete.get()) {
                                    val elapsed = now - measuredStartTime.get()
                                    val measuredBytes = currentTotal - warmupBytes
                                    if (elapsed > 0) (measuredBytes * 8.0) / (elapsed / 1_000_000_000.0) / 1_000_000 else null
                                } else null
                                val progress = currentTotal.toFloat() / (testSize * streamCount)
                                onProgress(progress.coerceIn(0f, 1f), currentMbps)
                            }
                        }
                    }
                } catch (_: IOException) {
                    // Stream failed — contributes 0 bytes.
                }
                streamBytes
            }
        }

        val results = jobs.awaitAll()
        val endTime = System.nanoTime()
        val totalDownloaded = totalBytes.get()

        if (totalDownloaded == 0L) throw IOException("All download streams failed")

        val warmupActual = warmupBytes.coerceAtMost(totalDownloaded)
        val measuredBytes = totalDownloaded - warmupActual
        val measuredNanos = endTime - measuredStartTime.get()
        val effectiveSeconds = (measuredNanos / 1_000_000_000.0)
            .coerceAtLeast(SpeedTestConstants.Warmup.MIN_TEST_DURATION_MS / 1000.0)

        val mbps = (measuredBytes * 8) / effectiveSeconds / 1_000_000

        SpeedResult(
            mbps = mbps,
            bytes = totalDownloaded,
            durationMs = (measuredNanos / 1_000_000).toLong()
        )
    }

    /**
     * Multi-part parallel upload with a streaming body (~32 KB heap per part).
     * Parses the server's `serverMs` from the response and uses
     * max(clientMs, serverMs) as the effective duration.
     */
    private suspend fun measureUpload(
        uploadEndpoint: String,
        onProgress: (Float, Double?) -> Unit
    ): SpeedResult = coroutineScope {
        val totalBytes = AtomicLong(0)
        val partSize = SpeedTestConstants.TestSize.SMALL
        val partCount = SpeedTestConstants.Streams.UPLOAD_PARTS
        val lastProgressReport = AtomicLong(0)
        var maxServerMs = 0.0
        val startTime = System.nanoTime()

        val jobs = (0 until partCount).map {
            async {
                try {
                    val requestBody = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength() = partSize
                        override fun writeTo(sink: BufferedSink) {
                            val chunk = ByteArray(32768)
                            var remaining = partSize
                            while (remaining > 0) {
                                val n = minOf(remaining, 32768L).toInt()
                                Random.nextBytes(chunk, 0, n)
                                sink.write(chunk, 0, n)
                                remaining -= n
                            }
                        }
                    }

                    val request = Request.Builder().url(uploadEndpoint).post(requestBody).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val serverMs = parseServerMs(response.body?.string())
                            if (serverMs > 0) {
                                synchronized(this@SpeedTestEngine) {
                                    maxServerMs = max(maxServerMs, serverMs)
                                }
                            }

                            val currentTotal = totalBytes.addAndGet(partSize)
                            val now = System.nanoTime()
                            val lastReport = lastProgressReport.get()
                            if (now - lastReport >= 150_000_000L && lastProgressReport.compareAndSet(lastReport, now)) {
                                val progress = currentTotal.toFloat() / (partSize * partCount)
                                val elapsed = now - startTime
                                val currentMbps = if (elapsed > 0) {
                                    (currentTotal * 8.0) / (elapsed / 1_000_000_000.0) / 1_000_000
                                } else null
                                onProgress(progress.coerceIn(0f, 1f), currentMbps)
                            }
                            partSize
                        } else 0L
                    }
                } catch (_: IOException) {
                    0L
                }
            }
        }

        jobs.awaitAll()
        val endTime = System.nanoTime()
        val totalUploaded = totalBytes.get()

        if (totalUploaded == 0L) throw IOException("All upload parts failed")

        val clientMs = (endTime - startTime) / 1_000_000.0
        val effectiveMs = if (maxServerMs > 0) max(clientMs, maxServerMs) else clientMs
        val effectiveSeconds = (effectiveMs / 1000.0)
            .coerceAtLeast(SpeedTestConstants.Warmup.MIN_TEST_DURATION_MS / 1000.0)

        val mbps = (totalUploaded * 8) / effectiveSeconds / 1_000_000

        SpeedResult(mbps = mbps, bytes = totalUploaded, durationMs = effectiveMs.toLong())
    }

    /**
     * Build a download URL for the endpoint type: Cloudflare (query param),
     * OVH (fixed files), or default query-param style.
     */
    internal fun buildDownloadUrl(endpoint: String, bytes: Long, cacheBuster: Long): String = when {
        endpoint.contains("cloudflare.com") -> "$endpoint?bytes=$bytes&cacheBust=$cacheBuster"
        endpoint.contains("proof.ovh.net") -> "${endpoint}100Mb.dat?cacheBust=$cacheBuster"
        else -> "$endpoint?bytes=$bytes&cacheBust=$cacheBuster"
    }

    internal fun calculateSpeedStatus(actualMbps: Double, contractMbps: Double): MetricStatus {
        val percentage = actualMbps / contractMbps
        return when {
            percentage >= SpeedTestConstants.ContractThresholds.GOOD_PERCENT -> MetricStatus.GOOD
            percentage >= SpeedTestConstants.ContractThresholds.WARN_PERCENT -> MetricStatus.WARN
            else -> MetricStatus.POOR
        }
    }

    internal fun calculateLatencyStatus(latencyMs: Double): MetricStatus = when {
        latencyMs <= SpeedTestConstants.LatencyThresholds.GOOD -> MetricStatus.GOOD
        latencyMs <= SpeedTestConstants.LatencyThresholds.WARN -> MetricStatus.WARN
        else -> MetricStatus.POOR
    }

    internal fun calculateJitterStatus(jitterMs: Double): MetricStatus = when {
        jitterMs <= SpeedTestConstants.JitterThresholds.GOOD -> MetricStatus.GOOD
        jitterMs <= SpeedTestConstants.JitterThresholds.WARN -> MetricStatus.WARN
        else -> MetricStatus.POOR
    }

    private fun parseServerMs(body: String?): Double =
        body?.let {
            Regex("\"serverMs\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
                .find(it)?.groupValues?.get(1)?.toDoubleOrNull()
        } ?: 0.0

    data class SpeedResult(val mbps: Double, val bytes: Long, val durationMs: Long)
}
