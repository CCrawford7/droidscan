package com.droidscan.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.droidscan.shared.core.GnssSatellite
import com.droidscan.shared.core.GnssSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * GNSS satellite telemetry.
 *
 * GnssStatus is only delivered via a registered callback (there is no sync
 * getter), so this collector registers a one-shot listener, waits up to
 * [POLL_TIMEOUT_MS] for a status event, parses the satellite table, and
 * unregisters. On devices with an active GNSS engine the callback fires in
 * milliseconds; when the receiver is idle the poll degrades to an empty
 * snapshot ("searching") rather than blocking the dashboard.
 *
 * Requires ACCESS_FINE_LOCATION — if denied, [GnssSnapshot.locationDenied]
 * is surfaced so the UI can show a degraded state (same pattern as
 * RadioSnapshot.cellInfoDenied).
 */
class GnssCollector {

    private var lm: LocationManager? = null
    private var gnssAvailable = false
    private var hasFineLocation = false
    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        lm = manager
        gnssAvailable = try {
            manager?.allProviders?.any { it == LocationManager.GPS_PROVIDER } ?: false
        } catch (_: Exception) { false }
        hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun read(): GnssSnapshot {
        val manager = lm ?: return GnssSnapshot(available = gnssAvailable)
        if (!gnssAvailable) return GnssSnapshot(available = false)

        // Wake the GNSS receiver before sampling. An idle HAL delivers no
        // GnssStatus callbacks (the dashboard shows "Searching…"); requesting a
        // single GPS fix kicks the engine so onSatelliteStatusChanged fires.
        if (hasFineLocation) requestSingleFix(manager)

        val latch = CountDownLatch(1)
        val holder = AtomicReference<GnssStatus>()
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                holder.set(status)
                latch.countDown()
            }
        }

        try {
            // The main-looper overload works on all API levels (deprecated 34+).
            manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            return GnssSnapshot(available = gnssAvailable, locationDenied = true)
        } catch (e: Exception) {
            Log.w("GnssCollector", "registerGnssStatusCallback failed: ${e.message}")
            return GnssSnapshot(available = gnssAvailable)
        }

        val received = try {
            latch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        try { manager.unregisterGnssStatusCallback(callback) } catch (_: Exception) {}

        val status = holder.get()
            ?: return GnssSnapshot(available = gnssAvailable) // idle receiver, no status yet
        return parse(status)
    }

    /**
     * One-shot GPS wake: asks the LocationManager for a single fix (which
     * brings the GNSS HAL out of standby). Best-effort and permission-gated;
     * the no-op listener is only here because requestSingleUpdate requires one.
     * The radio modem serves the status callback independently once awake.
     */
    private fun requestSingleFix(manager: LocationManager) {
        try {
            val wakeListener = object : LocationListener {
                override fun onLocationChanged(location: android.location.Location) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, wakeListener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Location permission revoked mid-run — the status callback below
            // will report locationDenied properly.
        } catch (e: Exception) {
            Log.w("GnssCollector", "GNSS wake failed (HAL may be unavailable): ${e.message}")
        }
    }

    private fun parse(status: GnssStatus): GnssSnapshot {
        val n = status.satelliteCount
        val sats = ArrayList<GnssSatellite>(n)
        for (i in 0 until n) {
            sats.add(
                GnssSatellite(
                    prn = status.getSvid(i),
                    constellation = constellationName(status.getConstellationType(i)),
                    cn0DbHz = status.getCn0DbHz(i).toDouble(),
                    carrierFrequencyHz = status.getCarrierFrequencyHz(i).toDouble(),
                    hasEphemeris = status.hasEphemerisData(i),
                    hasAlmanac = status.hasAlmanacData(i),
                    usedInFix = status.usedInFix(i)
                )
            )
        }
        // hasFix() was removed from the GnssStatus stub on API 35. A satellite is
        // only marked usedInFix while the receiver is actively computing a fix,
        // so "any satellite used in fix" is a faithful live proxy.
        return GnssSnapshot(
            available = gnssAvailable,
            hasFix = sats.any { it.usedInFix },
            satellites = sats
        )
    }

    private fun constellationName(c: Int): String = when (c) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
        GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "UNKNOWN"
    }

    companion object {
        // Longer than a pure idle poll: a cold GNSS engine can take ~1.5s to
        // deliver the first satellite status after the wake request.
        private const val POLL_TIMEOUT_MS = 1_600L
    }
}
