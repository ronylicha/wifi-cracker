package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class InterfaceManagerTest {
    private val shellExecutor = mockk<ShellExecutor>()
    private val chipsetDetector = mockk<ChipsetDetector>()
    private val manager = InterfaceManager(shellExecutor, chipsetDetector)

    @Test fun `listInterfaces parses iw dev output`() {
        every { shellExecutor.executeAsRoot("iw dev") } returns ShellResult(0, "phy#0\n    Interface wlan0\n        addr aa:bb:cc:dd:ee:ff\n        type managed", "")
        every { chipsetDetector.detect("wlan0") } returns ChipsetInfo("Qualcomm", "ath9k", true)
        val interfaces = manager.listInterfaces()
        assertEquals(1, interfaces.size); assertEquals("wlan0", interfaces[0].name); assertTrue(interfaces[0].supportsMonitor)
    }

    @Test fun `listInterfaces returns empty on failure`() {
        every { shellExecutor.executeAsRoot("iw dev") } returns ShellResult(1, "", "error")
        assertTrue(manager.listInterfaces().isEmpty())
    }
}
