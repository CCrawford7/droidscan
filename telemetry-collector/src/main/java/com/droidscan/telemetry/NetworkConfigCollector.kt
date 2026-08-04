package com.droidscan.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import com.droidscan.shared.core.NetworkConfigSnapshot
import java.net.InetSocketAddress
import java.net.Socket

class NetworkConfigCollector {
    private var cm: ConnectivityManager? = null
    private var tm: TelephonyManager? = null
    private var wm: WifiManager? = null

    private var lastLatencyMs: Int? = null
    private var lastProbeAtMs = 0L
    private val probeIntervalMs = 30_000L

    fun attach(context: Context) {
        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    fun read(): NetworkConfigSnapshot {
        val manager = cm ?: return NetworkConfigSnapshot()
        val activeNetwork = manager.activeNetwork ?: return NetworkConfigSnapshot()
        val caps = manager.getNetworkCapabilities(activeNetwork)
        val linkProps = manager.getLinkProperties(activeNetwork)

        val ipv4 = linkProps?.linkAddresses?.filter {
            it.address is java.net.Inet4Address
        }?.map { it.address.hostAddress } ?: emptyList()

        val ipv6 = linkProps?.linkAddresses?.filter {
            it.address is java.net.Inet6Address
        }?.map { it.address.hostAddress } ?: emptyList()

        val dns = linkProps?.dnsServers?.map { it.hostAddress } ?: emptyList()

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val networkType = when {
            isVpn -> "VPN"
            isWifi -> "WIFI"
            isCellular -> "MOBILE"
            else -> "NONE"
        }

        val vpnActive = isVpn || try {
            // Additional VPN detection via link props
            linkProps?.privateDnsServerName != null && isVpn
        } catch (_: Exception) { false }

        val hotspotActive = try {
            val method = wm?.javaClass?.getMethod("isWifiApEnabled")
            method?.invoke(wm) as? Boolean ?: false
        } catch (_: Exception) { false }

        val apnName = if (isCellular) {
            tm?.networkOperatorName?.takeIf { it.isNotEmpty() }
        } else null

        // WiFi details
        val wifiSsid = if (isWifi) {
            try {
                val wifiInfo = wm?.connectionInfo
                wifiInfo?.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
            } catch (_: Exception) { null }
        } else null

        // Actual received signal strength (dBm), not just the max link speed.
        val wifiRssi = if (isWifi) {
            try {
                wm?.connectionInfo?.rssi
                    ?.takeIf { it != Int.MIN_VALUE && it in -127..0 }
            } catch (_: Exception) { null }
        } else null

        val wifiLinkSpeedMbps = if (isWifi) {
            try {
                val wifiInfo = wm?.connectionInfo
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    wifiInfo?.maxSupportedRxLinkSpeedMbps?.toString()
                } else {
                    wifiInfo?.rssi?.let { rssi ->
                        // Approximate link quality from RSSI
                        when {
                            rssi > -50 -> ">100"
                            rssi > -60 -> "~80"
                            rssi > -70 -> "~40"
                            rssi > -80 -> "~15"
                            else -> "<5"
                        }
                    }
                }
            } catch (_: Exception) { null }
        } else null

        return NetworkConfigSnapshot(
            ipv4Addresses = ipv4,
            ipv6Addresses = ipv6,
            dnsServers = dns,
            activeNetworkType = networkType,
            vpnActive = vpnActive,
            vpnLabel = null,
            hotspotActive = hotspotActive,
            apnName = apnName,
            wifiSsid = wifiSsid,
            wifiRssi = wifiRssi,
            wifiLinkSpeedMbps = wifiLinkSpeedMbps,
            latencyMs = maybeProbeLatency()
        )
    }

    /**
     * Active latency probe (P1): measures TCP connect time to a well-known host.
     * Throttled to [probeIntervalMs]; returns the last measured value each read
     * until it is time to re-probe. Uses a TCP handshake (port 53) rather than
     * ICMP ping, which Android blocks for non-system apps.
     */
    private fun maybeProbeLatency(): Int? {
        val now = System.currentTimeMillis()
        if (now - lastProbeAtMs >= probeIntervalMs) {
            lastProbeAtMs = now
            lastLatencyMs = try {
                val start = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(PROBE_HOST, PROBE_PORT), PROBE_TIMEOUT_MS)
                }
                ((System.nanoTime() - start) / 1_000_000).toInt()
            } catch (e: Exception) {
                Log.w(TAG, "Latency probe to $PROBE_HOST:$PROBE_PORT failed: ${e.message}")
                null
            }
        }
        return lastLatencyMs
    }

    companion object {
        private const val PROBE_HOST = "8.8.8.8"
        private const val PROBE_PORT = 53
        private const val PROBE_TIMEOUT_MS = 3000
        private const val TAG = "NetworkConfigCollector"
    }
}
