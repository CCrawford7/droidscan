package com.droidscan.shared.core

/**
 * 3GPP band tables + downlink carrier frequency derivation.
 *
 * LTE (36.101 §5.7.3-1):  DL_Freq = DL_low + 0.1 × (EARFCN − EARFCN_low)
 *   — one EARFCN step = 100 kHz.
 * NR  (38.101 §5.4.2.1): F_REF = F_REF_Offs + ΔF_Global × (N_REF − N_REF_Offs)
 *   — FR1 ΔF = 5 kHz (0.005 MHz), FR2 ΔF = 60 kHz (0.06 MHz).
 *
 * A band may share an ARFCN window with another band (e.g. B1/B4, n77/n78,
 * n5/n26). The derived frequency is identical for same-offset overlaps, so the
 * frequency is always correct; the *band label* falls back to the first match
 * unless a known band (from CellIdentity.bands, API 30+) is supplied.
 */

data class BandInfo(
    val band: String,               // "B3", "n78", ...
    val dlFrequencyMhz: Double,     // downlink carrier frequency
    val isTdd: Boolean
)

private data class LteBand(
    val band: String, val low: Int, val high: Int, val dlStartMhz: Double, val isTdd: Boolean = false
)

private data class NrBand(
    val band: String, val low: Int, val high: Int, val refOffsMhz: Double, val stepMhz: Double, val isTdd: Boolean = false
)

// ── LTE E-UTRA bands (36.101 Table 5.7.3-1, downlink ranges) ──
private val LTE_BANDS = listOf(
    // FDD
    LteBand("B1", 1800, 2399, 2110.0),
    LteBand("B2", 600, 1199, 1930.0),
    LteBand("B3", 1200, 1949, 1805.0),
    LteBand("B4", 1950, 2399, 2110.0),
    LteBand("B5", 2400, 2649, 869.0),
    LteBand("B6", 2650, 2749, 875.0),
    LteBand("B7", 2750, 3449, 2620.0),
    LteBand("B8", 3450, 3799, 925.0),
    LteBand("B9", 3800, 4149, 1844.9),
    LteBand("B10", 4150, 4749, 2110.0),
    LteBand("B11", 4750, 4949, 1475.9),
    LteBand("B12", 5010, 5179, 729.0),
    LteBand("B13", 5180, 5279, 746.0),
    LteBand("B14", 5280, 5379, 758.0),
    LteBand("B17", 5730, 5849, 734.0),
    LteBand("B18", 5850, 5999, 860.0),
    LteBand("B19", 6000, 6149, 875.0),
    LteBand("B20", 6150, 6449, 791.0),
    LteBand("B21", 6450, 6599, 1495.9),
    LteBand("B22", 6600, 7399, 3510.0),
    LteBand("B23", 7500, 7699, 2180.0),
    LteBand("B24", 7700, 8039, 1525.0),
    LteBand("B25", 8040, 8689, 1930.0),
    LteBand("B26", 8690, 9039, 859.0),
    LteBand("B27", 9040, 9209, 852.0),
    LteBand("B28", 9210, 9659, 758.0),
    LteBand("B29", 9660, 9769, 717.0),
    LteBand("B30", 9770, 9869, 2350.0),
    LteBand("B31", 9870, 9919, 462.5),
    LteBand("B32", 9920, 10359, 1452.0),
    LteBand("B65", 65536, 66435, 2110.0),
    LteBand("B66", 66436, 67335, 2110.0),
    LteBand("B67", 67336, 67535, 738.0),
    LteBand("B68", 67536, 67835, 753.0),
    LteBand("B69", 67836, 68335, 2570.0),
    LteBand("B70", 68336, 68585, 1995.0),
    LteBand("B71", 68586, 68935, 617.0),
    LteBand("B72", 68936, 68985, 460.0),
    LteBand("B73", 68986, 69035, 460.0),
    LteBand("B74", 69036, 69435, 1475.0),
    LteBand("B85", 70366, 70535, 728.0),
    // TDD
    LteBand("B33", 36000, 36199, 1900.0, isTdd = true),
    LteBand("B34", 36200, 36349, 2010.0, isTdd = true),
    LteBand("B35", 36350, 36949, 1850.0, isTdd = true),
    LteBand("B36", 36950, 37549, 1930.0, isTdd = true),
    LteBand("B37", 37550, 37749, 1910.0, isTdd = true),
    LteBand("B38", 37750, 38249, 2570.0, isTdd = true),
    LteBand("B39", 38250, 38649, 1880.0, isTdd = true),
    LteBand("B40", 38650, 39649, 2300.0, isTdd = true),
    LteBand("B41", 39650, 41589, 2496.0, isTdd = true),
    LteBand("B42", 41590, 43589, 3400.0, isTdd = true),
    LteBand("B43", 43590, 45589, 3600.0, isTdd = true),
    LteBand("B44", 45590, 46589, 703.0, isTdd = true),
    LteBand("B46", 46790, 54539, 5150.0, isTdd = true),
    LteBand("B48", 55240, 56739, 3550.0, isTdd = true)
)

// ── NR bands (38.101-1 Table 5.4.2.1-1) ──
// FR1 ΔF = 5 kHz; FR2 ΔF = 60 kHz. Ranges approximate the per-band N_REF window.
private val NR_BANDS = listOf(
    // FR1 FDD
    NrBand("n1", 422000, 434000, 2110.0, 0.005),
    NrBand("n2", 386000, 398000, 1930.0, 0.005),
    NrBand("n3", 361000, 376000, 1805.0, 0.005),
    NrBand("n5", 173800, 178800, 869.0, 0.005),
    NrBand("n7", 524000, 538000, 2620.0, 0.005),
    NrBand("n8", 185000, 192000, 925.0, 0.005),
    NrBand("n12", 141800, 146800, 729.0, 0.005),
    NrBand("n14", 151600, 156600, 758.0, 0.005),
    NrBand("n20", 158200, 164200, 791.0, 0.005),
    NrBand("n25", 386000, 399000, 1930.0, 0.005),
    NrBand("n26", 173800, 183800, 859.0, 0.005),
    NrBand("n28", 151600, 160600, 758.0, 0.005),
    NrBand("n29", 143000, 145000, 717.0, 0.005),
    NrBand("n30", 231000, 239000, 2350.0, 0.005),
    NrBand("n65", 422000, 440000, 2110.0, 0.005),
    NrBand("n66", 422000, 440000, 2110.0, 0.005),
    NrBand("n70", 399000, 404000, 1995.0, 0.005),
    NrBand("n71", 281000, 288000, 617.0, 0.005),
    NrBand("n74", 295000, 303000, 1475.0, 0.005),
    NrBand("n75", 286400, 303400, 1432.0, 0.005),
    NrBand("n76", 285400, 286400, 1427.0, 0.005),
    // FR1 TDD
    NrBand("n38", 514000, 524000, 2570.0, 0.005, isTdd = true),
    NrBand("n40", 460000, 480000, 2300.0, 0.005, isTdd = true),
    NrBand("n41", 499200, 537999, 2496.0, 0.005, isTdd = true),
    NrBand("n48", 636000, 646000, 3550.0, 0.005, isTdd = true),
    NrBand("n53", 496700, 499000, 2483.5, 0.005, isTdd = true),
    NrBand("n77", 620000, 680000, 3300.0, 0.005, isTdd = true),
    NrBand("n78", 620000, 680000, 3300.0, 0.005, isTdd = true),
    NrBand("n79", 620000, 680000, 4400.0, 0.005, isTdd = true),
    NrBand("n90", 499200, 537999, 2496.0, 0.005, isTdd = true),
    // FR2 (ΔF = 60 kHz)
    NrBand("n257", 2054167, 2104167, 26500.0, 0.06, isTdd = true),
    NrBand("n258", 2016667, 2070833, 24250.0, 0.06, isTdd = true),
    NrBand("n260", 2229167, 2279167, 37000.0, 0.06, isTdd = true),
    NrBand("n261", 2079167, 2129167, 27500.0, 0.06, isTdd = true)
)

/** Resolves an LTE EARFCN to a band + downlink frequency (null if unknown). */
fun lteBandInfo(earfcn: Int, knownBand: String? = null): BandInfo? {
    val matches = LTE_BANDS.filter { earfcn in it.low..it.high }
    if (matches.isEmpty()) return null
    val def = knownBand?.let { k -> matches.firstOrNull { it.band == k } } ?: matches.first()
    return BandInfo(def.band, def.dlStartMhz + 0.1 * (earfcn - def.low), def.isTdd)
}

/** Resolves an NR ARFCN to a band + downlink frequency (null if unknown). */
fun nrBandInfo(nrarfcn: Int, knownBand: String? = null): BandInfo? {
    val matches = NR_BANDS.filter { nrarfcn in it.low..it.high }
    if (matches.isEmpty()) return null
    val def = knownBand?.let { k -> matches.firstOrNull { it.band == k } } ?: matches.first()
    return BandInfo(def.band, def.refOffsMhz + def.stepMhz * (nrarfcn - def.low), def.isTdd)
}
