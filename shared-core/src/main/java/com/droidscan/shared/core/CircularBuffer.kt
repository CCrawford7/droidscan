package com.droidscan.shared.core

// ──────────────────────────────────────────────
// Fixed-size ring buffer for sparkline data
// Thread-safe, never grows, never blocks.
// ──────────────────────────────────────────────

class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any>(capacity) as Array<T?>
    private var writeIndex = 0
    private var count = 0

    fun add(value: T) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) count++
    }

    fun toList(): List<T> {
        val result = mutableListOf<T>()
        val start = if (count < capacity) 0 else writeIndex
        for (i in 0 until count) {
            buffer[(start + i) % capacity]?.let { result.add(it) }
        }
        return result
    }

    fun latest(): T? = if (count == 0) null else buffer[(writeIndex - 1 + capacity) % capacity]

    fun average(selector: (T) -> Double): Double? {
        if (count == 0) return null
        return toList().map(selector).average()
    }

    val size: Int get() = count
    val isFull: Boolean get() = count == capacity
}
