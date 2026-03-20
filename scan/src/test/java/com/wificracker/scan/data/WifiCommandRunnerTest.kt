package com.wificracker.scan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wificracker.core.root.ShellExecutor
import com.wificracker.scan.model.EncryptionType
import io.mockk.mockk

class WifiCommandRunnerTest {

    private val shellExecutor = mockk<ShellExecutor>(relaxed = true)
    private val runner = WifiCommandRunner(shellExecutor)

    private val sampleCsv = """
BSSID, First time seen, Last time seen, channel, Speed, Privacy, Cipher, Authentication, Power, # beacons, # IV, LAN IP, ID-length, ESSID, Key
AA:BB:CC:DD:EE:FF, 2024-01-01 00:00:00, 2024-01-01 00:01:00, 6, 54, WPA2, CCMP, PSK, -45, 100, 0, 0.0.0.0, 10, MyNetwork,
11:22:33:44:55:66, 2024-01-01 00:00:00, 2024-01-01 00:01:00, 1, 54, WEP, WEP, , -80, 50, 10, 0.0.0.0, 8, OldRouter,
77:88:99:AA:BB:CC, 2024-01-01 00:00:00, 2024-01-01 00:01:00, 11, 54, OPN, , , -60, 200, 0, 0.0.0.0, 0, ,

Station MAC, First time seen, Last time seen, Power, # packets, BSSID, Probed ESSIDs
DE:AD:BE:EF:00:01, 2024-01-01 00:00:00, 2024-01-01 00:01:00, -55, 150, AA:BB:CC:DD:EE:FF, HomeNet WorkNet
DE:AD:BE:EF:00:02, 2024-01-01 00:00:00, 2024-01-01 00:01:00, -70, 30, 11:22:33:44:55:66,
    """.trimIndent()

    @Test
    fun `parseCsvOutput extracts networks correctly`() {
        val update = runner.parseCsvOutput(sampleCsv)
        assertEquals(3, update.networks.size)

        val net1 = update.networks[0]
        assertEquals("AA:BB:CC:DD:EE:FF", net1.bssid)
        assertEquals("MyNetwork", net1.ssid)
        assertEquals(6, net1.channel)
        assertEquals(-45, net1.signalStrength)
        assertEquals(EncryptionType.WPA2, net1.encryption)
    }

    @Test
    fun `parseCsvOutput detects WEP encryption`() {
        val update = runner.parseCsvOutput(sampleCsv)
        assertEquals(EncryptionType.WEP, update.networks[1].encryption)
        assertEquals("OldRouter", update.networks[1].ssid)
    }

    @Test
    fun `parseCsvOutput detects OPEN networks`() {
        val update = runner.parseCsvOutput(sampleCsv)
        assertEquals(EncryptionType.OPEN, update.networks[2].encryption)
    }

    @Test
    fun `parseCsvOutput extracts clients correctly`() {
        val update = runner.parseCsvOutput(sampleCsv)
        assertEquals(2, update.clients.size)

        val client1 = update.clients[0]
        assertEquals("DE:AD:BE:EF:00:01", client1.macAddress)
        assertEquals("AA:BB:CC:DD:EE:FF", client1.bssid)
        assertEquals(-55, client1.signalStrength)
        assertEquals(150, client1.packets)
    }

    @Test
    fun `parseCsvOutput extracts probe requests`() {
        val update = runner.parseCsvOutput(sampleCsv)
        assertTrue(update.clients[0].probeRequests.contains("HomeNet"))
        assertTrue(update.clients[0].probeRequests.contains("WorkNet"))
    }

    @Test
    fun `parseCsvOutput handles empty input`() {
        val update = runner.parseCsvOutput("")
        assertTrue(update.networks.isEmpty())
        assertTrue(update.clients.isEmpty())
    }

    @Test
    fun `parseCsvOutput skips malformed lines`() {
        val badCsv = """
BSSID, First time seen, Last time seen, channel, Speed, Privacy, Cipher, Authentication, Power, # beacons, # IV, LAN IP, ID-length, ESSID, Key
not a valid line
AA:BB:CC:DD:EE:FF, 2024-01-01 00:00:00, 2024-01-01 00:01:00, 6, 54, WPA2, CCMP, PSK, -45, 100, 0, 0.0.0.0, 10, TestNet,
        """.trimIndent()
        val update = runner.parseCsvOutput(badCsv)
        assertEquals(1, update.networks.size)
        assertEquals("TestNet", update.networks[0].ssid)
    }
}
