package com.wificracker.scan.domain

import com.wificracker.core.database.dao.VulnDao
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.VulnMatch
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VulnMatcher @Inject constructor(
    private val vulnDao: VulnDao,
) {

    suspend fun matchVulnerabilities(network: Network): List<VulnMatch> {
        val protocol = mapEncryptionToProtocol(network.encryption)
        val vulns = vulnDao.getByProtocol(protocol).first()

        val matches = vulns.map { vuln ->
            VulnMatch(
                cveId = vuln.cveId,
                title = vuln.title,
                severity = vuln.severity,
                cvssScore = vuln.cvssScore,
                recommendation = vuln.recommendation,
            )
        }.toMutableList()

        // Always flag OPEN and WEP networks
        if (network.encryption == EncryptionType.OPEN) {
            matches.add(0, VulnMatch(
                cveId = "OPEN-NETWORK",
                title = "Unencrypted Network",
                severity = "CRITICAL",
                cvssScore = 10.0f,
                recommendation = "Enable WPA3-SAE encryption immediately.",
            ))
        }

        if (network.wps) {
            matches.add(VulnMatch(
                cveId = "WPS-ENABLED",
                title = "WPS Enabled",
                severity = "HIGH",
                cvssScore = 7.0f,
                recommendation = "Disable WPS. It is vulnerable to brute-force PIN attacks.",
            ))
        }

        return matches.sortedByDescending { it.cvssScore }
    }

    suspend fun matchAllNetworks(networks: List<Network>): Map<String, List<VulnMatch>> {
        return networks.associate { network ->
            network.bssid to matchVulnerabilities(network)
        }
    }

    private fun mapEncryptionToProtocol(encryption: EncryptionType): String {
        return when (encryption) {
            EncryptionType.WEP -> "WEP"
            EncryptionType.WPA -> "WPA"
            EncryptionType.WPA2 -> "WPA2"
            EncryptionType.WPA3 -> "WPA3"
            EncryptionType.OPEN -> "OPEN"
            EncryptionType.UNKNOWN -> "UNKNOWN"
        }
    }
}
