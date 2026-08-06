package com.droidscan.telemetry

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.ims.ImsMmTelManager
import android.util.Log
import com.droidscan.shared.core.ImsRegistration
import com.droidscan.shared.core.ImsSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Per-subscription IMS registration telemetry.
 *
 * `ImsMmTelManager` is the public class, but its factory
 * (`ImsManager.getInstance(context, subId)` → `getImsMmTelManager(subId)`) is
 * hidden from the SDK stub on API 34/35. This collector reflects only that
 * factory (the same hidden-API pattern RadioCollector uses for `isImsRegistered`),
 * then drives the *public* methods on the returned instance:
 *
 *  - sync settings: `isAdvancedCallingSettingEnabled` (a.k.a. isVolteEnabled on
 *    older runtimes), `isVtSettingEnabled`, `isVoWiFiSettingEnabled`,
 *    `isVoWiFiRoamingSettingEnabled` — matched by reflection with name fallbacks
 *    so one APK spans Android 13 → 16 runtimes.
 *  - async registration state/transport via `getRegistrationState` /
 *    `getRegistrationTransportType` (Consumer-based) with a short latch.
 *
 * All getters require READ_PRECISE_PHONE_STATE (API 34+) or carrier privileges;
 * without them reads throw SecurityException and [ImsSnapshot.accessDenied] is
 * set so the UI can degrade to the legacy CarrierConfig fields on RadioSnapshot.
 *
 * API 30+ only.
 */
class ImsCollector {

    private var ctx: Context? = null

    // Dedicated thread for async IMS callbacks — the poller blocks on a latch,
    // so the callback must NOT run on the blocking thread (inline executor would
    // deadlock).
    private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()

    fun attach(context: Context) {
        ctx = context.applicationContext
    }

    fun read(): ImsSnapshot {
        val context = ctx ?: return ImsSnapshot()
        if (Build.VERSION.SDK_INT < 30) return ImsSnapshot(apiAvailable = false)

        val subIds = activeSubscriptionIds(context)
        if (subIds.isEmpty()) return ImsSnapshot(apiAvailable = true)

        var anyDenied = false
        val perSub = subIds.mapNotNull { subId ->
            readSub(context, subId).also { if (it == null) anyDenied = true }
        }
        return ImsSnapshot(
            perSub = perSub,
            accessDenied = anyDenied && perSub.isEmpty(),
            apiAvailable = true
        )
    }

    private fun activeSubscriptionIds(context: Context): List<Int> {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            sm?.activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /** Reflects the hidden ImsManager.getInstance(context, subId) → getImsMmTelManager(subId). */
    private fun mmTelFor(context: Context, subId: Int): ImsMmTelManager? {
        return try {
            val imsManagerClass = Class.forName("android.telephony.ims.ImsManager")
            val getInstance = imsManagerClass.getMethod(
                "getInstance", Context::class.java, Int::class.javaPrimitiveType
            )
            val imsManager = getInstance.invoke(null, context, subId)
            val getMmTel = imsManager.javaClass.getMethod("getImsMmTelManager", Int::class.javaPrimitiveType)
            getMmTel.invoke(imsManager, subId) as ImsMmTelManager
        } catch (e: Exception) {
            Log.w("ImsCollector", "ImsMmTelManager access failed (sub $subId): ${e.message}")
            null
        }
    }

    private fun readSub(context: Context, subId: Int): ImsRegistration? {
        val mmTel = mmTelFor(context, subId) ?: return null
        val clazz = mmTel.javaClass

        // Async registration state + transport (Consumer-based, public API).
        val regState = asyncInt(clazz, mmTel, "getRegistrationState")
        val transport = asyncInt(clazz, mmTel, "getRegistrationTransportType")

        // Sync boolean settings — try modern name first, fall back to legacy.
        val volte = invokeBool(clazz, mmTel, "isAdvancedCallingSettingEnabled", "isVolteEnabled")
        val vt = invokeBool(clazz, mmTel, "isVtSettingEnabled", "isVtEnabled")
        val wfc = invokeBool(clazz, mmTel, "isVoWiFiSettingEnabled", "isWfcEnabled")
        val wfcRoam = invokeBool(clazz, mmTel, "isVoWiFiRoamingSettingEnabled", "isWfcRoamingEnabled")

        return ImsRegistration(
            subscriptionId = subId,
            registrationState = regState?.let(::regStateLabel),
            transportType = transport?.let(::transportLabel),
            volteEnabled = volte,
            vtEnabled = vt,
            wfcEnabled = wfc,
            wfcRoamingEnabled = wfcRoam
        )
    }

    /** Invokes an async `void method(Executor, Consumer<Int>)`, returning the int or null. */
    private fun asyncInt(clazz: Class<*>, receiver: Any, methodName: String): Int? {
        return try {
            val method = clazz.getMethod(methodName, Executor::class.java, java.util.function.Consumer::class.java)
            val latch = CountDownLatch(1)
            val ref = AtomicReference<Int>()
            method.invoke(receiver, callbackExecutor, java.util.function.Consumer<Int> { v ->
                ref.set(v)
                latch.countDown()
            })
            if (latch.await(500, TimeUnit.MILLISECONDS)) ref.get() else null
        } catch (e: SecurityException) {
            null // READ_PRECISE_PHONE_STATE not granted
        } catch (e: Exception) {
            null
        }
    }

    private fun invokeBool(clazz: Class<*>, receiver: Any, vararg names: String): Boolean? {
        val method = names.firstNotNullOfOrNull { name ->
            try { clazz.getMethod(name) } catch (_: NoSuchMethodException) { null }
        } ?: return null
        return try {
            method.invoke(receiver) as? Boolean
        } catch (_: Exception) {
            null
        }
    }

    private fun regStateLabel(s: Int): String = when (s) {
        ImsMmTelManager.REGISTRATION_STATE_REGISTERED -> "REGISTERED"
        ImsMmTelManager.REGISTRATION_STATE_REGISTERING -> "REGISTERING"
        ImsMmTelManager.REGISTRATION_STATE_NOT_REGISTERED -> "NOT REGISTERED"
        else -> "UNKNOWN"
    }

    private fun transportLabel(t: Int): String = when (t) {
        0 -> "WWAN"
        1 -> "WLAN"
        else -> "OTHER"
    }
}
