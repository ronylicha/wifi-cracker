package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ChipsetDetectorTest {
    private val shellExecutor = mockk<ShellExecutor>()
    private val detector = ChipsetDetector(shellExecutor)

    @Test
    fun `detect returns monitor-capable for ath9k driver`() {
        every { shellExecutor.executeAsRoot("readlink /sys/class/net/wlan0/device/driver") } returns ShellResult(0, "/sys/bus/usb/drivers/ath9k_htc", "")
        every { shellExecutor.executeAsRoot("cat /sys/class/net/wlan0/device/uevent | grep DRIVER") } returns ShellResult(0, "DRIVER=ath9k_htc", "")
        val info = detector.detect("wlan0")
        assertEquals("ath9k_htc", info.driver)
        assertTrue(info.supportsMonitor)
    }

    @Test
    fun `detect returns non-monitor for unknown driver`() {
        every { shellExecutor.executeAsRoot("readlink /sys/class/net/wlan0/device/driver") } returns ShellResult(0, "/sys/bus/pci/drivers/iwlwifi", "")
        every { shellExecutor.executeAsRoot("cat /sys/class/net/wlan0/device/uevent | grep DRIVER") } returns ShellResult(0, "DRIVER=iwlwifi", "")
        val info = detector.detect("wlan0")
        assertFalse(info.supportsMonitor)
    }
}
