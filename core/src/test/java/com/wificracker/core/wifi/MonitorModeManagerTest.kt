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
    private val manager = MonitorModeManager(shellExecutor)

    @Test fun `enableMonitorMode runs commands in order`() {
        every { shellExecutor.executeAsRoot(any(), any()) } returns ShellResult(0, "", "")
        assertTrue(manager.enableMonitorMode("wlan0").isSuccess)
        verifyOrder { shellExecutor.executeAsRoot("ip link set wlan0 down"); shellExecutor.executeAsRoot("iw dev wlan0 set type monitor"); shellExecutor.executeAsRoot("ip link set wlan0 up") }
    }

    @Test fun `enableMonitorMode fails if interface down fails`() {
        every { shellExecutor.executeAsRoot("ip link set wlan0 down") } returns ShellResult(1, "", "error")
        assertFalse(manager.enableMonitorMode("wlan0").isSuccess)
    }

    @Test fun `disableMonitorMode restores managed mode`() {
        every { shellExecutor.executeAsRoot(any(), any()) } returns ShellResult(0, "", "")
        assertTrue(manager.disableMonitorMode("wlan0").isSuccess)
        verifyOrder { shellExecutor.executeAsRoot("ip link set wlan0 down"); shellExecutor.executeAsRoot("iw dev wlan0 set type managed"); shellExecutor.executeAsRoot("ip link set wlan0 up") }
    }
}
