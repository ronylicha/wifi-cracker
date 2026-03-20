package com.wificracker.scan.domain

import com.wificracker.core.database.dao.VulnDao
import com.wificracker.core.database.entity.VulnEntity
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VulnMatcherTest {

    private val vulnDao = mockk<VulnDao>()
    private val matcher = VulnMatcher(vulnDao)

    private fun makeNetwork(
        bssid: String = "AA:BB:CC:DD:EE:FF",
        encryption: EncryptionType = EncryptionType.WPA2,
        wps: Boolean = false,
    ) = Network(bssid = bssid, ssid = "Test", channel = 6, signalStrength = -45, encryption = encryption, wps = wps)

    @Test
    fun `matchVulnerabilities returns vulns for WPA2 network`() = runTest {
        coEvery { vulnDao.getByProtocol("WPA2") } returns flowOf(listOf(
            VulnEntity("CVE-2017-13077", "WPA2", "KRACK", "desc", "CRITICAL", 9.8f, "Update firmware", "all")
        ))

        val matches = matcher.matchVulnerabilities(makeNetwork())
        assertEquals(1, matches.size)
        assertEquals("CVE-2017-13077", matches[0].cveId)
    }

    @Test
    fun `matchVulnerabilities adds OPEN-NETWORK for unencrypted`() = runTest {
        coEvery { vulnDao.getByProtocol("OPEN") } returns flowOf(emptyList())

        val matches = matcher.matchVulnerabilities(makeNetwork(encryption = EncryptionType.OPEN))
        assertTrue(matches.any { it.cveId == "OPEN-NETWORK" })
        assertEquals(10.0f, matches[0].cvssScore)
    }

    @Test
    fun `matchVulnerabilities flags WPS-ENABLED`() = runTest {
        coEvery { vulnDao.getByProtocol("WPA2") } returns flowOf(emptyList())

        val matches = matcher.matchVulnerabilities(makeNetwork(wps = true))
        assertTrue(matches.any { it.cveId == "WPS-ENABLED" })
    }

    @Test
    fun `matchAllNetworks returns map by BSSID`() = runTest {
        coEvery { vulnDao.getByProtocol(any()) } returns flowOf(emptyList())

        val networks = listOf(makeNetwork(bssid = "AA:AA:AA:AA:AA:AA"), makeNetwork(bssid = "BB:BB:BB:BB:BB:BB"))
        val result = matcher.matchAllNetworks(networks)
        assertEquals(2, result.size)
        assertTrue(result.containsKey("AA:AA:AA:AA:AA:AA"))
    }

    @Test
    fun `results are sorted by CVSS score descending`() = runTest {
        coEvery { vulnDao.getByProtocol("WPA2") } returns flowOf(listOf(
            VulnEntity("LOW", "WPA2", "Low vuln", "", "LOW", 3.0f, "", ""),
            VulnEntity("HIGH", "WPA2", "High vuln", "", "HIGH", 8.0f, "", ""),
        ))

        val matches = matcher.matchVulnerabilities(makeNetwork())
        assertEquals("HIGH", matches[0].cveId)
        assertEquals("LOW", matches[1].cveId)
    }
}
