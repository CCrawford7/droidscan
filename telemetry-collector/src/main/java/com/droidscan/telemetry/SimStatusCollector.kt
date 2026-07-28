package com.droidscan.telemetry

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.droidscan.shared.core.SimSlot
import com.droidscan.shared.core.SimStatusSnapshot

class SimStatusCollector {
    private var sm: SubscriptionManager? = null
    private var tm: TelephonyManager? = null

    fun attach(context: Context) {
        sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    fun read(): SimStatusSnapshot {
        val subscriptionManager = sm ?: return SimStatusSnapshot()
        val telephonyManager = tm ?: return SimStatusSnapshot()

        val slots = subscriptionManager.activeSubscriptionInfoList?.mapIndexed { index, info ->
            SimSlot(
                slotIndex = index,
                carrierName = info.carrierName?.toString(),
                isRoaming = telephonyManager.isNetworkRoaming,
                esimProfileCount = if (info.isEmbedded) 1 else 0,
                isProvisioned = true, // active subscription = provisioned
                iccidLast4 = info.iccId?.takeLast(4),
                subscriptionId = info.subscriptionId
            )
        } ?: emptyList()

        return SimStatusSnapshot(slots = slots)
    }
}
