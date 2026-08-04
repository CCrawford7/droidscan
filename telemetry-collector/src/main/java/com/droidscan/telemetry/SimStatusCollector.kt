package com.droidscan.telemetry

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.droidscan.shared.core.SimSlot
import com.droidscan.shared.core.SimStatusSnapshot

class SimStatusCollector {
    private var sm: SubscriptionManager? = null
    private var tm: TelephonyManager? = null
    private var ctx: Context? = null

    fun attach(context: Context) {
        ctx = context
        sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    // Log each capability-read failure at most once per subscription (they repeat
    // every poll cycle; spamming every 5s would drown logcat).
    private val loggedFailures = mutableSetOf<String>()
    private fun logOnce(subId: Int, key: String, msg: String) {
        if (loggedFailures.add("$subId:$key")) {
            Log.w(TAG, "sub[$subId] $key read failed: $msg")
        }
    }

    // ── Per-subscription radio capabilities ─────────────────────────────
    // Reads VoLTE / WiFi Calling / IMS / NR-NSA / call state / signal for a
    // SPECIFIC subscription via createForSubscriptionId(subId). This is what
    // makes SIM 2's capabilities reflect SIM 2's radio (not SIM 1's default).
    private fun readCapabilities(subTm: TelephonyManager?, subId: Int): PerSimCaps {
        if (subTm == null) return PerSimCaps()
        val ccm = ctx?.getSystemService(Context.CARRIER_CONFIG_SERVICE) as? CarrierConfigManager
        val cr = ctx?.contentResolver

        val imsCapable = try {
            if (ccm != null) ccm.getConfigForSubId(subId)?.getBoolean("carrier_volte_available_bool") else null
        } catch (e: Exception) {
            logOnce(subId, "volte_available", e.message ?: e.javaClass.simpleName)
            null
        }

        val nrNsaAvailable = try {
            if (ccm != null) ccm.getConfigForSubId(subId)?.getBoolean("carrier_nr_available_bool") else null
        } catch (e: Exception) {
            logOnce(subId, "nr_available", e.message ?: e.javaClass.simpleName)
            null
        }

        val volteEnabled = try {
            cr?.let {
                when (Settings.Global.getInt(it, "volte_vt_enabled")) {
                    1 -> true
                    0 -> false
                    else -> null
                }
            }
        } catch (e: Exception) {
            logOnce(subId, "volte_vt_enabled", e.message ?: e.javaClass.simpleName)
            null
        }

        val wifiCallingStatus = try {
            val wfcEnabled = cr?.let {
                try { Settings.Global.getInt(it, "wfc_ims_enabled") == 1 } catch (e: Exception) { null }
            }
            val wfcSupported = try {
                if (ccm != null) ccm.getConfigForSubId(subId)?.getBoolean("carrier_wfc_ims_available_bool") ?: false
                else false
            } catch (e: Exception) { false }
            when {
                wfcEnabled == true && wfcSupported -> "ACTIVE"
                wfcSupported -> "READY"
                else -> "UNAVAILABLE"
            }
        } catch (e: Exception) {
            logOnce(subId, "wifi-calling", e.message ?: e.javaClass.simpleName)
            null
        }

        val imsRegistered = try {
            val method = subTm.javaClass.getMethod("isImsRegistered")
            method.invoke(subTm) as? Boolean
        } catch (e: Exception) {
            logOnce(subId, "isImsRegistered-reflection", e.message ?: e.javaClass.simpleName)
            null
        }

        val preferredNetworkType = try {
            cr?.let {
                val code = Settings.Global.getInt(it, "preferred_network_mode")
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
            }
        } catch (e: Exception) {
            logOnce(subId, "preferred_network_mode", e.message ?: e.javaClass.simpleName)
            null
        }

        val callState = try {
            when (subTm.callState) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
                else -> null
            }
        } catch (e: Exception) {
            logOnce(subId, "callState", e.message ?: e.javaClass.simpleName)
            null
        }

        val dataNetworkType = try {
            when (subTm.dataNetworkType) {
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
                else -> "${subTm.dataNetworkType}"
            }
        } catch (e: Exception) {
            logOnce(subId, "dataNetworkType", e.message ?: e.javaClass.simpleName)
            null
        }

        val rsrp = try {
            subTm.allCellInfo?.firstOrNull { it.isRegistered }?.let { cell ->
                val raw = when (cell) {
                    is CellInfoLte -> (cell.cellSignalStrength as? CellSignalStrengthLte)?.rsrp
                    is CellInfoNr -> (cell.cellSignalStrength as? CellSignalStrengthNr)?.getCsiRsrp()
                    else -> null
                }
                // NR CSI returns Int.MAX_VALUE when not measurable — treat as unknown
                raw?.takeIf { it != Int.MAX_VALUE }
            }
        } catch (e: Exception) {
            logOnce(subId, "allCellInfo", e.message ?: e.javaClass.simpleName)
            null
        }

        return PerSimCaps(
            volteEnabled = volteEnabled,
            wifiCallingStatus = wifiCallingStatus,
            imsRegistered = imsRegistered,
            imsCapable = imsCapable,
            nrNsaAvailable = nrNsaAvailable,
            preferredNetworkType = preferredNetworkType,
            callState = callState,
            dataNetworkType = dataNetworkType,
            isRoaming = try { subTm.isNetworkRoaming } catch (e: Exception) { null },
            rsrp = rsrp
        )
    }

    private companion object {
        const val TAG = "SimStatusCollector"
    }

    private data class PerSimCaps(
        val volteEnabled: Boolean? = null,
        val wifiCallingStatus: String? = null,
        val imsRegistered: Boolean? = null,
        val imsCapable: Boolean? = null,
        val nrNsaAvailable: Boolean? = null,
        val preferredNetworkType: String? = null,
        val callState: String? = null,
        val dataNetworkType: String? = null,
        val isRoaming: Boolean? = null,
        val rsrp: Int? = null
    )

    fun read(): SimStatusSnapshot {
        val telephonyManager = tm ?: return SimStatusSnapshot()
        val subscriptionManager = sm ?: return SimStatusSnapshot()

        // Physical slot count
        val slotCount = try { telephonyManager.phoneCount } catch (_: Exception) {
            telephonyManager.activeModemCount
        } catch (_: Exception) { 1 }

        val slots = mutableListOf<SimSlot>()

        // Strategy 1: Try per-slot subscription info (works on API 30+ with READ_SIM_STATE)
        for (i in 0 until slotCount) {
            val simState = try { telephonyManager.getSimState(i) } catch (_: Exception) { TelephonyManager.SIM_STATE_UNKNOWN }
            val isCardPresent = simState !in listOf(
                TelephonyManager.SIM_STATE_ABSENT,
                TelephonyManager.SIM_STATE_UNKNOWN
            )

            // Try to get subscription info for this slot
            var slotInfo: SimSlot? = null

            // Method A: getActiveSubscriptionInfoForSimSlotIndex (API 30+)
            slotInfo = try {
                val info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(i)
                if (info != null) {
                    val subId = info.subscriptionId
                    val subTm = try {
                        telephonyManager.createForSubscriptionId(subId)
                    } catch (_: Exception) { null }

                    val homePlmn = try {
                        subTm?.simOperator?.takeIf { it.length >= 5 }
                    } catch (_: Exception) { null }

                    val simOperatorName = try {
                        subTm?.simOperatorName?.takeIf { it.isNotEmpty() }
                    } catch (_: Exception) { null }

                    val msisdn = try {
                        if (Build.VERSION.SDK_INT >= 34) {
                            val num = sm?.getPhoneNumber(subId)
                            if (!num.isNullOrBlank() && num.length >= 3) num
                            else subTm?.line1Number?.takeIf { it.length >= 3 }
                        } else {
                            subTm?.line1Number?.takeIf { it.length >= 3 }
                        }
                    } catch (_: Exception) { null }

                    val imsiPrefix = try {
                        val imsi = subTm?.subscriberId
                        if (imsi != null && imsi.length >= 6) "[${imsi.take(5)}-XXXX-XXXX]" else null
                    } catch (_: Exception) { null }

                    // Per-SIM radio capabilities (this sub's own radio, not the default sub's)
                    val caps = readCapabilities(subTm, subId)

                    SimSlot(
                        slotIndex = i,
                        carrierName = simOperatorName ?: info.carrierName?.toString(),
                        isRoaming = caps.isRoaming,
                        esimProfileCount = if (info.isEmbedded) 1 else 0,
                        isProvisioned = true,
                        iccidLast4 = try { info.iccId?.takeLast(4) } catch (_: Exception) { null },
                        subscriptionId = info.subscriptionId,
                        homePlmn = homePlmn,
                        simOperatorName = simOperatorName,
                        msisdn = msisdn,
                        imsiPrefix = imsiPrefix,
                        volteEnabled = caps.volteEnabled,
                        wifiCallingStatus = caps.wifiCallingStatus,
                        imsRegistered = caps.imsRegistered,
                        imsCapable = caps.imsCapable,
                        nrNsaAvailable = caps.nrNsaAvailable,
                        preferredNetworkType = caps.preferredNetworkType,
                        callState = caps.callState,
                        dataNetworkType = caps.dataNetworkType,
                        rsrp = caps.rsrp
                    )
                } else null
            } catch (_: Exception) { null }

            // Method B: Try getCompleteActiveSubscriptionInfoList and match by slot (API 34+)
            if (slotInfo == null) {
                slotInfo = try {
                    val allSubs = subscriptionManager.getCompleteActiveSubscriptionInfoList()
                    val match = allSubs?.firstOrNull { sub ->
                        try { sub.simSlotIndex == i } catch (_: Exception) { false }
                    }
                    if (match != null) {
                        val subId = match.subscriptionId
                        val subTm = try {
                            telephonyManager.createForSubscriptionId(subId)
                        } catch (_: Exception) { null }

                        val homePlmn = try {
                            subTm?.simOperator?.takeIf { it.length >= 5 }
                        } catch (_: Exception) { null }

                        val simOperatorName = try {
                            subTm?.simOperatorName?.takeIf { it.isNotEmpty() }
                        } catch (_: Exception) { null }

                        val caps = readCapabilities(subTm, subId)

                        SimSlot(
                            slotIndex = i,
                            carrierName = simOperatorName ?: match.carrierName?.toString(),
                            isRoaming = caps.isRoaming,
                            esimProfileCount = if (match.isEmbedded) 1 else 0,
                            isProvisioned = true,
                            iccidLast4 = try { match.iccId?.takeLast(4) } catch (_: Exception) { null },
                            subscriptionId = match.subscriptionId,
                            homePlmn = homePlmn,
                            simOperatorName = simOperatorName,
                            volteEnabled = caps.volteEnabled,
                            wifiCallingStatus = caps.wifiCallingStatus,
                            imsRegistered = caps.imsRegistered,
                            imsCapable = caps.imsCapable,
                            nrNsaAvailable = caps.nrNsaAvailable,
                            preferredNetworkType = caps.preferredNetworkType,
                            callState = caps.callState,
                            dataNetworkType = caps.dataNetworkType,
                            rsrp = caps.rsrp
                        )
                    } else null
                } catch (_: Exception) { null }
            }

            // Method C: SIM present, but no ACTIVE subscription resolved for this slot
            // (getActiveSubscriptionInfoForSimSlotIndex failed or returned null).
            if (slotInfo == null && isCardPresent) {
                val stateLabel = when (simState) {
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN locked"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK locked"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
                    TelephonyManager.SIM_STATE_READY -> "Ready"
                    TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
                    TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
                    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "I/O error"
                    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restricted"
                    10 -> "Loaded"
                    else -> "Unknown"
                }

                // Try to get the per-subscription TM using subscription IDs for THIS slot.
                val subId = try {
                    val ids = subscriptionManager.getSubscriptionIds(i)
                    ids?.firstOrNull { it > 0 }
                } catch (_: Exception) { null }

                if (subId != null) {
                    // Slot has an active sub — resolve identity from the per-sub TM,
                    // never from the default TM (which may belong to a different slot).
                    val subTm = try {
                        telephonyManager.createForSubscriptionId(subId)
                    } catch (_: Exception) { null }

                    val perHomePlmn = try {
                        subTm?.simOperator?.takeIf { it.length >= 5 }
                    } catch (_: Exception) { null }

                    val perOpName = try {
                        subTm?.simOperatorName?.takeIf { it.isNotEmpty() }
                    } catch (_: Exception) { null }

                    val caps = readCapabilities(subTm, subId)

                    val iccid = try {
                        val info = subscriptionManager.getActiveSubscriptionInfo(subId)
                        if (info != null) info.iccId?.takeLast(4) else null
                    } catch (_: Exception) { null }

                    slotInfo = SimSlot(
                        slotIndex = i,
                        carrierName = perOpName ?: stateLabel,
                        isRoaming = caps.isRoaming,
                        esimProfileCount = 0,
                        isProvisioned = perHomePlmn != null,
                        iccidLast4 = iccid,
                        subscriptionId = subId,
                        homePlmn = perHomePlmn,
                        simOperatorName = perOpName,
                        volteEnabled = caps.volteEnabled,
                        wifiCallingStatus = caps.wifiCallingStatus,
                        imsRegistered = caps.imsRegistered,
                        imsCapable = caps.imsCapable,
                        nrNsaAvailable = caps.nrNsaAvailable,
                        preferredNetworkType = caps.preferredNetworkType,
                        callState = caps.callState,
                        dataNetworkType = caps.dataNetworkType,
                        rsrp = caps.rsrp
                    )
                } else {
                    // No active sub on this slot. Determine whether ANOTHER slot is active
                    // using getSubscriptionIds() per slot — this works WITHOUT READ_SIM_STATE
                    // (getActiveSubscriptionInfoList throws without it, which was the bug).
                    var otherSlotActive = false
                    for (j in 0 until slotCount) {
                        if (j == i) continue
                        val otherIds = try { subscriptionManager.getSubscriptionIds(j) } catch (_: Exception) { null }
                        if (otherIds?.any { it > 0 } == true) { otherSlotActive = true; break }
                    }

                    if (otherSlotActive) {
                        // Card present but no active sub for THIS slot while another slot is
                        // active → this SIM was deactivated by the user. Do NOT borrow the
                        // default sub's identity into this slot.
                        logOnce(i, "deactivated-slot", "card present, no active sub, other slot active")
                        slotInfo = SimSlot(
                            slotIndex = i,
                            carrierName = "Disabled",
                            isRoaming = null,
                            esimProfileCount = 0,
                            isProvisioned = false,
                            subscriptionId = null
                        )
                    } else {
                        // No other slot has an active sub (genuine single-SIM device, or a
                        // total permission gap): approximate identity from the default TM as
                        // a last resort.
                        val operatorName = try {
                            telephonyManager.simOperatorName?.takeIf { it.isNotEmpty() }
                        } catch (_: Exception) { null }
                        val homePlmn = try {
                            telephonyManager.simOperator?.takeIf { it.length >= 5 }
                        } catch (_: Exception) { null }
                        Log.d(TAG, "slot[$i]: no sub info; single-sub fallback op=$operatorName plmn=$homePlmn")
                        slotInfo = SimSlot(
                            slotIndex = i,
                            carrierName = operatorName ?: stateLabel,
                            isRoaming = null,
                            esimProfileCount = 0,
                            isProvisioned = homePlmn != null,
                            subscriptionId = null,
                            homePlmn = homePlmn,
                            simOperatorName = operatorName
                        )
                    }
                }
            }

            // Method D: No SIM present or all methods failed
            if (slotInfo == null) {
                val stateLabel = when (simState) {
                    TelephonyManager.SIM_STATE_ABSENT -> "Empty"
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN locked"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK locked"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
                    TelephonyManager.SIM_STATE_READY -> "Ready (deprovisioned)"
                    TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
                    TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
                    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "I/O error"
                    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restricted"
                    10 -> "Loaded (deprovisioned)"
                    else -> "Unknown"
                }
                slotInfo = SimSlot(
                    slotIndex = i,
                    carrierName = if (isCardPresent) stateLabel else "Empty",
                    isRoaming = false,
                    esimProfileCount = 0,
                    isProvisioned = false,
                    subscriptionId = -1
                )
            }

            slots.add(slotInfo)
        }

        return SimStatusSnapshot(slots = slots)
    }
}
