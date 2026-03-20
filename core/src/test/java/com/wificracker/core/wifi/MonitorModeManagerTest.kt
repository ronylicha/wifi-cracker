package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Assert.*
import org.junit.Test

class MonitorModeManagerTest {
    private val shellExecutor = mockk<ShellExecutor>(relaxed = true)
    private val chipsetMonitorHelper = mockk<ChipsetMonitorHelper>(relaxed = true)
    private val manager = MonitorModeManager(shellExecutor, chipsetMonitorHelper)

    @Test fun `enableMonitorMode delegates to ChipsetMonitorHelper`() {
        every { chipsetMonitorHelper.enableMonitorMode("wlan0") } returns ShellResult(0, "OK", "")
        val result = manager.enableMonitorMode("wlan0")
        assertTrue(result.isSuccess)
    }

    @Test fun `enableMonitorMode returns failure when helper fails`() {
        every { chipsetMonitorHelper.enableMonitorMode("wlan0") } returns ShellResult(1, "", "error")
        assertFalse(manager.enableMonitorMode("wlan0").isSuccess)
    }

    @Test fun `disableMonitorMode delegates to ChipsetMonitorHelper`() {
        every { chipsetMonitorHelper.disableMonitorMode("wlan0") } returns ShellResult(0, "OK", "")
        val result = manager.disableMonitorMode("wlan0")
        assertTrue(result.isSuccess)
    }

    @Test fun `isMonitorMode returns true when stdout contains type monitor`() {
        every { shellExecutor.executeAsRoot(any(), any()) } returns ShellResult(0, "type monitor", "")
        assertTrue(manager.isMonitorMode("wlan0"))
    }

    @Test fun `isMonitorMode returns true when stdout is 803`() {
        every { shellExecutor.executeAsRoot(any(), any()) } returns ShellResult(0, "803", "")
        assertTrue(manager.isMonitorMode("wlan0"))
    }

    @Test fun `isMonitorMode returns false when not in monitor mode`() {
        every { shellExecutor.executeAsRoot(any(), any()) } returns ShellResult(0, "type managed", "")
        assertFalse(manager.isMonitorMode("wlan0"))
    }

    @Test fun `getChipsetInfo delegates to ChipsetMonitorHelper`() {
        val expected = ChipsetMonitorCapability(WifiChipVendor.QUALCOMM, "Qualcomm sm8450", true, "QCACLD con_mode")
        every { chipsetMonitorHelper.detectChipVendor() } returns expected
        val result = manager.getChipsetInfo()
        assertEquals(expected, result)
    }
}
