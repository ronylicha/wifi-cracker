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
    private val chipsetMonitorHelper = mockk<ChipsetMonitorHelper>()
    private val manager = InterfaceManager(shellExecutor, chipsetDetector, chipsetMonitorHelper)

    @Test fun `listInterfaces parses iw dev output`() {
        every { shellExecutor.executeAsRoot("iw dev 2>/dev/null") } returns ShellResult(0, "phy#0\n    Interface wlan0\n        addr aa:bb:cc:dd:ee:ff\n        type managed", "")
        every { chipsetDetector.detect("wlan0") } returns ChipsetInfo("Qualcomm", "ath9k", true)
        every { chipsetMonitorHelper.detectChipVendor() } returns ChipsetMonitorCapability(WifiChipVendor.QUALCOMM, "Qualcomm", true, "QCACLD")
        val interfaces = manager.listInterfaces()
        assertEquals(1, interfaces.size); assertEquals("wlan0", interfaces[0].name); assertTrue(interfaces[0].supportsMonitor)
    }

    @Test fun `listInterfaces falls back to proc when iw fails`() {
        every { shellExecutor.executeAsRoot("iw dev 2>/dev/null") } returns ShellResult(1, "", "not found")
        every { shellExecutor.executeAsRoot("cat /proc/net/wireless") } returns ShellResult(0, "Inter-| sta-|\n face | tus |\n wlan0: 0000    0.  183.    0.", "")
        every { shellExecutor.executeAsRoot("ls /sys/class/net/ | grep -E '^wlan|^p2p'") } returns ShellResult(0, "wlan0\np2p0", "")
        every { chipsetMonitorHelper.detectChipVendor() } returns ChipsetMonitorCapability(WifiChipVendor.MEDIATEK, "MediaTek gen4m_6878", false, "Not supported")
        every { shellExecutor.executeAsRoot("cat /sys/class/net/wlan0/address 2>/dev/null") } returns ShellResult(0, "c2:66:4b:f2:b3:93", "")
        every { shellExecutor.executeAsRoot("cat /sys/class/net/wlan0/type 2>/dev/null") } returns ShellResult(0, "1", "")
        val interfaces = manager.listInterfaces()
        assertEquals(1, interfaces.size)
        assertEquals("wlan0", interfaces[0].name)
        assertEquals("c2:66:4b:f2:b3:93", interfaces[0].macAddress)
    }
}
