package com.droidscan.speedtest

/**
 * Primary/fallback speed-test endpoints with automatic per-test failover.
 *
 * Download is Cloudflare's public speed-test endpoint; upload is the VitalPing
 * upload worker (serverless Cloudflare Worker) with a Render fallback. The same
 * endpoints VitalPing ships, so the upload endpoint's `serverMs` response shape
 * matches what the engine parses.
 */
class SpeedTestEndpoints(
    private val download: String,
    private val downloadFallback: String,
    private val upload: String,
    private val uploadFallback: String
) {
    private var downloadFailed = false
    private var uploadFailed = false

    val downloadEndpoint: String get() = if (downloadFailed) downloadFallback else download
    val uploadEndpoint: String get() = if (uploadFailed) uploadFallback else upload

    fun markDownloadFailed() { downloadFailed = true }
    fun markUploadFailed() { uploadFailed = true }

    /** Reset to primary at the start of each test so recovery is re-checked. */
    fun resetForNewTest() {
        downloadFailed = false
        uploadFailed = false
    }

    companion object {
        fun default() = SpeedTestEndpoints(
            download = "https://speed.cloudflare.com/__down",
            downloadFallback = "https://proof.ovh.net/files/",
            upload = "https://vitalping-upload.ccrawf99.workers.dev/upload",
            uploadFallback = "https://vitalping-s2ln.onrender.com/upload"
        )
    }
}
