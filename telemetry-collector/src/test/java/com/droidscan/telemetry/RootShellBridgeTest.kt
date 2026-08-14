package com.droidscan.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RootShellBridgeTest {

    // On the JVM there is no /system/bin/su, so the bridge must degrade
    // gracefully: probe returns false, commands return null, no crash.
    @Test
    fun `root probe fails gracefully without su`() {
        val bridge = RootShellBridge()
        assertFalse(bridge.isAvailable())
    }

    @Test
    fun `runCommand returns null without root`() {
        val bridge = RootShellBridge()
        assertNull(bridge.runCommand("dumpsys power"))
    }

    @Test
    fun `readFile returns null without root`() {
        val bridge = RootShellBridge()
        assertNull(bridge.readFile("/proc/diskstats"))
    }

    @Test
    fun `probe result is cached`() {
        val bridge = RootShellBridge()
        bridge.isAvailable()
        bridge.isAvailable() // second call hits the cached flag, not the process
        assertFalse(bridge.isAvailable())
    }
}
