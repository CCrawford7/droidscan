package com.droidscan.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircularBufferTest {

    @Test
    fun `empty buffer has size zero and null latest`() {
        val buf = CircularBuffer<Int>(4)
        assertEquals(0, buf.size)
        assertEquals(false, buf.isFull)
        assertNull(buf.latest())
    }

    @Test
    fun `add preserves insertion order`() {
        val buf = CircularBuffer<Int>(4)
        buf.add(1); buf.add(2); buf.add(3)
        assertEquals(listOf(1, 2, 3), buf.toList())
        assertEquals(3, buf.latest())
    }

    @Test
    fun `wrap around keeps most recent entries`() {
        val buf = CircularBuffer<Int>(4)
        (1..6).forEach { buf.add(it) }
        assertEquals(listOf(3, 4, 5, 6), buf.toList())
        assertEquals(4, buf.size)
        assertEquals(true, buf.isFull)
        assertEquals(6, buf.latest())
    }

    @Test
    fun `average uses selector`() {
        val buf = CircularBuffer<Double>(4)
        buf.add(1.0); buf.add(2.0); buf.add(3.0)
        assertEquals(2.0, buf.average { it }!!, 0.0001)
    }

    @Test
    fun `average is null when empty`() {
        assertNull(CircularBuffer<Int>(2).average { it.toDouble() })
    }
}
