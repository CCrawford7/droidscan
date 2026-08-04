package com.droidscan.telemetry

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.telephony.CarrierConfigManager
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.droidscan.shared.core.RadioSnapshot
import com.droidscan.shared.core.rsrpOrNull
import com.droidscan.shared.core.rsrqOrNull
import com.droidscan.shared.core.sinrOrNull

class RadioCollector {
    private var tm: TelephonyManager? = null
    private var ctx: Context? = null
    private var ccm: CarrierConfigManager? = null
    private var contentResolver: android.content.ContentResolver? = null
    // Cache for reflection results denied on API 34+
    private var imsAccessDenied = false
    private var imsCachedResult: Boolean? = null

    fun attach(telephonyManager: TelephonyManager) {
        tm = telephonyManager
    }

    fun attachContext(context: Context) {
        ctx = context
        ccm = context.getSystemService(Context.CARRIER_CONFIG_SERVICE) as? CarrierConfigManager
        contentResolver = context.contentResolver
    }

    fun read(): RadioSnapshot {
        val manager = tm ?: return RadioSnapshot()

        val cellInfoList = try {
            manager.allCellInfo
        } catch (e: SecurityException) {
            Log.w("RadioCollector", "allCellInfo denied (missing permission?): ${e.message}")
            null
        } catch (e: Exception) {
            Log.w("RadioCollector", "allCellInfo failed: ${e.message}", e)
            null
        }
        val activeCell = cellInfoList?.firstOrNull { it.isRegistered }

        // The allCellInfo path is the canonical source for RSRP/RSRQ/SINR, but on
        // some OEMs (verified OPPO/OnePlus) it can return the MAX_VALUE sentinel
        // or no registered cell — while the modem still reports the values via
        // getSignalStrength(). Fall back to the consolidated SignalStrength object
        // whenever allCellInfo comes up empty for a metric. All raw values are
        // passed through the shared signalOrNull/rsrpOrNull/rsrqOrNull/sinrOrNull
        // validators (sentinel guard + physical range check).
        fun strengthObj(): Pair<CellSignalStrengthLte?, CellSignalStrengthNr?> = try {
            val ss = manager.signalStrength
            if (ss == null) Pair(null, null) else {
                val lte = ss.getCellSignalStrengths().filterIsInstance<CellSignalStrengthLte>().firstOrNull()
                val nr = ss.getCellSignalStrengths().filterIsInstance<CellSignalStrengthNr>().firstOrNull()
                Pair(lte, nr)
            }
        } catch (_: Exception) { Pair(null, null) }

        val allCellInfoRsrp = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthLte)?.rsrp.rsrpOrNull() }
                is CellInfoNr ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthNr)?.getCsiRsrp().rsrpOrNull() }
                else -> null
            }
        }

        val allCellInfoRsrq = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthLte)?.rsrq.rsrqOrNull() }
                is CellInfoNr ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthNr)?.getCsiRsrq().rsrqOrNull() }
                else -> null
            }
        }

        val allCellInfoSinr = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthLte)?.rssnr.sinrOrNull() }
                is CellInfoNr ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthNr)?.getCsiSinr().sinrOrNull() }
                else -> null
            }
        }

        val (fallbackLte, fallbackNr) = strengthObj()
        val rsrp: Int? = allCellInfoRsrp
            ?: fallbackLte?.rsrp?.rsrpOrNull()
            ?: fallbackNr?.getCsiRsrp()?.rsrpOrNull()
        val rsrq: Int? = allCellInfoRsrq
            ?: fallbackLte?.rsrq?.rsrqOrNull()
            ?: fallbackNr?.getCsiRsrq()?.rsrqOrNull()
        val sinr: Int? = allCellInfoSinr
            ?: fallbackLte?.rssnr?.sinrOrNull()
            ?: fallbackNr?.getCsiSinr()?.sinrOrNull()

        val bands = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte -> {
                    val id = cell.cellIdentity
                    id?.bands?.map { "B$it" }
                }
                is CellInfoNr -> {
                    val id = cell.cellIdentity as? CellIdentityNr
                    id?.bands?.map { "n$it" }
                }
                else -> null
            }
        } ?: emptyList()

        val cellId = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte -> cell.cellIdentity?.ci?.toLong()
                is CellInfoNr -> {
                    val id = cell.cellIdentity as? CellIdentityNr
                    id?.nci
                }
                else -> null
            }
        }

        val tac = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte -> cell.cellIdentity?.tac
                is CellInfoNr -> {
                    val id = cell.cellIdentity as? CellIdentityNr
                    id?.tac
                }
                else -> null
            }
        }

        val imsRegistered = if (imsAccessDenied) {
            imsCachedResult
        } else try {
            val method = manager.javaClass.getMethod("isImsRegistered")
            method.invoke(manager) as? Boolean
        } catch (e: Exception) {
            if (e.toString().contains("denied")) {
                imsAccessDenied = true
            }
            imsCachedResult
        }

        // ── HSS / Subscriber fields ──

        // Network PLMN (MCC-MNC of current serving network)
        val networkPlmn = try {
            manager.networkOperator?.takeIf { it.length >= 5 }
        } catch (_: Exception) { null }

        // Call state
        val callState = try {
            when (manager.callState) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> null
            }
        } catch (_: Exception) { null }

        // Data roaming enabled
        val dataRoamingEnabled = try {
            val method = manager.javaClass.getMethod("isDataRoamingEnabled")
            method.invoke(manager) as? Boolean
        } catch (_: Exception) { null }

        // Preferred network type from Settings.Global
        val preferredNetworkType = contentResolver?.let { cr ->
            try {
                val code = Settings.Global.getInt(cr, "preferred_network_mode")
                when (code) {
                    0 -> "WCDMA preferred"
                    1 -> "GSM only"
                    2 -> "WCDMA only"
                    3 -> "GSM/WCDMA (auto)"
                    4 -> "CDMA/EVDO auto"
                    5 -> "CDMA only"
                    6 -> "EVDO only"
                    7 -> "GSM/WCDMA/CDMA/EVDO (auto)"
                    8 -> "LTE/CDMA/EVDO/GSM/WCDMA"
                    9 -> "LTE/GSM/WCDMA"
                    10 -> "LTE/GSM/WCDMA/CDMA/EVDO"
                    11 -> "LTE only"
                    12 -> "LTE/WCDMA"
                    13 -> "NR/LTE"
                    14 -> "NR/LTE/GSM/WCDMA"
                    15 -> "NR/LTE/CDMA/EVDO/GSM/WCDMA"
                    16 -> "NR only"
                    17 -> "NR/LTE/WCDMA"
                    18 -> "NR/LTE/GSM/WCDMA/CDMA/EVDO"
                    19 -> "WCDMA/GSM/LTE/NR (auto)"
                    else -> "Unknown ($code)"
                }
            } catch (_: Exception) { null }
        }

        // ── IMS / VoLTE / WiFi Calling / NR-NSA via CarrierConfigManager (public API!) ──

        val subscriptionId = try { manager.subscriptionId } catch (_: Exception) { -1 }

        val imsCapable = try {
            if (ccm != null && subscriptionId > 0) {
                ccm!!.getConfigForSubId(subscriptionId)?.getBoolean("carrier_volte_available_bool")
            } else null
        } catch (_: Exception) { null }

        val nrNsaAvailable = try {
            if (ccm != null && subscriptionId > 0) {
                ccm!!.getConfigForSubId(subscriptionId)?.getBoolean("carrier_nr_available_bool")
            } else null
        } catch (_: Exception) { null }

        // VoLTE enabled state — try Settings.Global (OEM-specific but widely supported)
        val volteEnabled = try {
            contentResolver?.let {
                when (Settings.Global.getInt(it, "volte_vt_enabled")) {
                    1 -> true
                    0 -> false
                    else -> null
                }
            }
        } catch (_: Exception) { null }

        // WiFi Calling state — try Settings.Global + CarrierConfig
        val wifiCallingStatus = try {
            val wfcEnabled = contentResolver?.let {
                try { Settings.Global.getInt(it, "wfc_ims_enabled") == 1 } catch (_: Exception) { null }
            }
            val wfcSupported = try {
                if (ccm != null && subscriptionId > 0) {
                    ccm!!.getConfigForSubId(subscriptionId)?.getBoolean("carrier_wfc_ims_available_bool") ?: false
                } else false
            } catch (_: Exception) { false }
            when {
                wfcEnabled == true && wfcSupported -> "ACTIVE"
                wfcSupported -> "READY"
                else -> "UNAVAILABLE"
            }
        } catch (_: Exception) { null }

        // Radio power state (API 30+, but hidden at compile time on some SDKs)
        val radioPowerState = if (Build.VERSION.SDK_INT >= 30) {
            try {
                val method = manager.javaClass.getMethod("getRadioPowerState")
                val state = method.invoke(manager)
                when (state?.toString()) {
                    "2" -> "ON"
                    "1" -> "OFF"
                    "0" -> "UNAVAILABLE"
                    else -> "state=$state"
                }
            } catch (_: Exception) { null }
        } else null

        val dataNetworkTypeStr = try {
            val netType = manager.dataNetworkType
            when (netType) {
                TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN (WiFi)"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3G)"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
                TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA (2G)"
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO (3G)"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
                else -> "$netType"
            }
        } catch (_: Exception) { null }

        return RadioSnapshot(
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            activeBands = bands,
            cellId = cellId,
            tac = tac,
            imsRegistered = imsRegistered,
            dataNetworkType = dataNetworkTypeStr,
            isRoaming = try { manager.isNetworkRoaming } catch (_: Exception) { null },
            operatorName = try { manager.networkOperatorName?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null },
            epdgTunnelActive = null, // requires deeper dumpsys access
            // HSS fields
            networkPlmn = networkPlmn,
            callState = callState,
            dataRoamingEnabled = dataRoamingEnabled,
            preferredNetworkType = preferredNetworkType,
            imsCapable = imsCapable,
            volteEnabled = volteEnabled,
            wifiCallingStatus = wifiCallingStatus,
            radioPowerState = radioPowerState,
            nrNsaAvailable = nrNsaAvailable
        )
    }
}
