package com.droidscan.shared.core

/**
 * Shared validation for cellular signal metrics.
 *
 * Android's radio stack uses Int.MAX_VALUE (on some paths Int.MIN_VALUE) as a
 * "not measurable" sentinel instead of returning null. These helpers map that
 * sentinel to null AND additionally reject values outside each metric's
 * physically meaningful range — so the UI only ever displays a value a real
 * radio could produce, and shows "--" for anything else.
 */

// Physically meaningful ranges (per AOSP CellSignalStrengthLte/Nr javadoc).
// LTE and NR share the RSRP/RSRQ windows; SINR uses the union of the LTE
// (rssnr: -20..30) and NR (csiSinr: -23..40) windows so one helper covers both.
const val RSRP_MIN_DBM = -140
const val RSRP_MAX_DBM = -44
const val RSRQ_MIN_DB = -20
const val RSRQ_MAX_DB = -3
const val SINR_MIN_DB = -23
const val SINR_MAX_DB = 40

/** Maps the "not measurable" sentinel (Int.MAX_VALUE) to null. */
fun Int?.signalOrNull(): Int? = this?.takeIf { it != Int.MAX_VALUE }

/** Sentinel-guarded RSRP in dBm that is physically plausible, else null. */
fun Int?.rsrpOrNull(): Int? = signalOrNull()?.takeIf { it in RSRP_MIN_DBM..RSRP_MAX_DBM }

/** Sentinel-guarded RSRQ in dB that is physically plausible, else null. */
fun Int?.rsrqOrNull(): Int? = signalOrNull()?.takeIf { it in RSRQ_MIN_DB..RSRQ_MAX_DB }

/** Sentinel-guarded SINR in dB that is physically plausible, else null. */
fun Int?.sinrOrNull(): Int? = signalOrNull()?.takeIf { it in SINR_MIN_DB..SINR_MAX_DB }
