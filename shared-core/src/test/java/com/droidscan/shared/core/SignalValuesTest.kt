package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalValuesTest {

    @Test
    fun `signalOrNull maps MAX sentinel to null`() {
        assertNull(Int.MAX_VALUE.signalOrNull())
        assertEquals(12, 12.signalOrNull())
        assertNull(null.signalOrNull())
    }

    @Test
    fun `rsrpOrNull accepts only plausible LTE NR RSRP`() {
        assertEquals(-81, (-81).rsrpOrNull())
        assertEquals(-140, (-140).rsrpOrNull()) // boundary: strongest accepted
        assertEquals(-44, (-44).rsrpOrNull())   // boundary: weakest accepted
        assertNull(Int.MAX_VALUE.rsrpOrNull())  // sentinel
        assertNull((-43).rsrpOrNull())          // out of range (too high)
        assertNull((-141).rsrpOrNull())         // out of range (too low)
    }

    @Test
    fun `rsrqOrNull accepts only plausible RSRQ`() {
        assertEquals(-15, (-15).rsrqOrNull())
        assertEquals(-20, (-20).rsrqOrNull())
        assertEquals(-3, (-3).rsrqOrNull())
        assertNull(Int.MAX_VALUE.rsrqOrNull())
        assertNull((-2).rsrqOrNull())
        assertNull((-21).rsrqOrNull())
    }

    @Test
    fun `sinrOrNull accepts LTE rssnr and NR csiSinr ranges`() {
        assertEquals(12, 12.sinrOrNull())
        assertEquals(19, 19.sinrOrNull())
        assertEquals(30, 30.sinrOrNull())  // LTE rssnr max
        assertEquals(40, 40.sinrOrNull())  // NR csiSinr max
        assertEquals(-20, (-20).sinrOrNull())
        assertEquals(-23, (-23).sinrOrNull()) // NR csiSinr min
        assertNull(Int.MAX_VALUE.sinrOrNull())
        assertNull(41.sinrOrNull())   // above NR csiSinr max
        assertNull((-24).sinrOrNull()) // below NR csiSinr min
    }

    @Test
    fun `realistic end-to-end snapshot values pass`() {
        // The values we observed on-device from the modem
        assertEquals(-81, (-81).rsrpOrNull())
        assertEquals(-11, (-11).rsrqOrNull())
        assertEquals(19, 19.sinrOrNull())
        assertEquals(9, 9.sinrOrNull())
    }
}
