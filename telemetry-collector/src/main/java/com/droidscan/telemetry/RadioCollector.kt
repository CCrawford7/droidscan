package com.droidscan.telemetry

import android.os.Build
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.droidscan.shared.core.RadioSnapshot

class RadioCollector {
    private var tm: TelephonyManager? = null

    fun attach(telephonyManager: TelephonyManager) {
        tm = telephonyManager
    }

    fun read(): RadioSnapshot {
        val manager = tm ?: return RadioSnapshot()

        val cellInfoList = try {
            manager.allCellInfo
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
        val activeCell = cellInfoList?.firstOrNull { it.isRegistered }

        val rsrp: Int? = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthLte)?.rsrp }
                is CellInfoNr ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthNr)?.getCsiRsrp() }
                else -> null
            }
        }

        val rsrq: Int? = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthLte)?.rsrq }
                else -> null
            }
        }

        val sinr: Int? = activeCell?.let { cell ->
            when (cell) {
                is CellInfoLte ->
                    cell.cellSignalStrength?.let { (it as? CellSignalStrengthLte)?.rssnr }
                else -> null
            }
        }

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

        val imsRegistered = try {
            val method = manager.javaClass.getMethod("isImsRegistered")
            method.invoke(manager) as? Boolean
        } catch (_: Exception) { null }

        return RadioSnapshot(
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            activeBands = bands,
            cellId = cellId,
            tac = tac,
            imsRegistered = imsRegistered,
            dataNetworkType = try { manager.dataNetworkType.toString() } catch (_: Exception) { null },
            isRoaming = try { manager.isNetworkRoaming } catch (_: Exception) { null },
            operatorName = try { manager.networkOperatorName?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null },
            epdgTunnelActive = null // requires deeper dumpsys access
        )
    }
}
