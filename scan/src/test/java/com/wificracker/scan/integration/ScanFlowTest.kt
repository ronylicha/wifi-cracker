package com.wificracker.scan.integration

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import com.wificracker.core.util.MacVendorLookup
import com.wificracker.core.wifi.ChipsetMonitorHelper
import com.wificracker.core.wifi.MonitorModeManager
import com.wificracker.scan.data.WifiCommandRunner
import com.wificracker.scan.domain.ScanEngine
import com.wificracker.scan.model.ScanStatus
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScanFlowTest {

    private val shellExecutor = mockk<ShellExecutor>(relaxed = true)
    private val chipsetHelper = mockk<ChipsetMonitorHelper>(relaxed = true)
    private val monitorModeManager = MonitorModeManager(shellExecutor, chipsetHelper)
    private val macVendorLookup = MacVendorLookup(mapOf("AA:BB:CC" to "TestVendor"))
    private val auditLogger = mockk<AuditLogger>(relaxed = true)
    private val commandRunner = WifiCommandRunner(shellExecutor)

    @Before
    fun setup() {
        every { chipsetHelper.enableMonitorMode(any()) } returns ShellResult(0, "OK", "")
        every { chipsetHelper.disableMonitorMode(any()) } returns ShellResult(0, "OK", "")
    }

    @Test
    fun `WifiCommandRunner parses airodump CSV correctly`() {
        val csv = """
BSSID, First time seen, Last time seen, channel, Speed, Privacy, Cipher, Authentication, Power, # beacons, # IV, LAN IP, ID-length, ESSID, Key
AA:BB:CC:DD:EE:FF, 2024-01-01 00:00:00, 2024-01-01 00:01:00, 6, 54, WPA2, CCMP, PSK, -45, 100, 0, 0.0.0.0, 10, TestNetwork,

Station MAC, First time seen, Last time seen, Power, # packets, BSSID, Probed ESSIDs
11:22:33:44:55:66, 2024-01-01 00:00:00, 2024-01-01 00:01:00, -55, 150, AA:BB:CC:DD:EE:FF,
        """.trimIndent()

        val update = commandRunner.parseCsvOutput(csv)
        assertEquals(1, update.networks.size)
        assertEquals("TestNetwork", update.networks[0].ssid)
        assertEquals("AA:BB:CC:DD:EE:FF", update.networks[0].bssid)
        assertEquals(6, update.networks[0].channel)
        assertEquals(1, update.clients.size)
        assertEquals("AA:BB:CC:DD:EE:FF", update.clients[0].bssid)
    }

    @Test
    fun `MonitorModeManager delegates to ChipsetMonitorHelper`() {
        monitorModeManager.enableMonitorMode("wlan0")
        verify { chipsetHelper.enableMonitorMode("wlan0") }
    }

    @Test
    fun `MonitorModeManager disable delegates correctly`() {
        monitorModeManager.disableMonitorMode("wlan0")
        verify { chipsetHelper.disableMonitorMode("wlan0") }
    }
}
