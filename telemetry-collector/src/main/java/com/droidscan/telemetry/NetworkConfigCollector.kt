package com.droidscan.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.droidscan.shared.core.NetworkConfigSnapshot

class NetworkConfigCollector {
    private var cm: ConnectivityManager? = null
    private var tm: TelephonyManager? = null
    private var wm: WifiManager? = null

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

        val networkType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "MOBILE"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "NONE"
        }

        val vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val hotspotActive = try {
            val method = wm?.javaClass?.getMethod("isWifiApEnabled")
            method?.invoke(wm) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }

        val apnName = if (networkType == "MOBILE") {
            tm?.networkOperatorName?.takeIf { it.isNotEmpty() }
        } else null

        return NetworkConfigSnapshot(
            ipv4Addresses = ipv4,
            ipv6Addresses = ipv6,
            dnsServers = dns,
            activeNetworkType = networkType,
            vpnActive = vpnActive,
            hotspotActive = hotspotActive,
            apnName = apnName
        )
    }
}
