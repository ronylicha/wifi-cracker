package com.wificracker.report.domain

import com.wificracker.report.model.Finding
import com.wificracker.report.model.Severity
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.VulnMatch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataAggregator @Inject constructor(private val cvssCalculator: CvssCalculator) {

    fun networkToFindings(network: Network, vulnMatches: List<VulnMatch>): List<Finding> {
        val findings = mutableListOf<Finding>()

        // Encryption-based findings
        when (network.encryption) {
            EncryptionType.OPEN -> findings.add(Finding(title = "Unencrypted Network Detected", description = "Network '${network.ssid}' has no encryption. All traffic is visible to anyone in range.", severity = Severity.CRITICAL, cvssScore = 10.0f, networkBssid = network.bssid, networkSsid = network.ssid, recommendation = "Enable WPA3-SAE encryption immediately."))
            EncryptionType.WEP -> findings.add(Finding(title = "WEP Encryption (Broken)", description = "Network '${network.ssid}' uses WEP which can be cracked in minutes.", severity = Severity.CRITICAL, cvssScore = 9.5f, networkBssid = network.bssid, networkSsid = network.ssid, recommendation = "Migrate to WPA3-SAE or WPA2-CCMP."))
            EncryptionType.WPA -> findings.add(Finding(title = "Legacy WPA Detected", description = "Network '${network.ssid}' uses WPA-TKIP which is deprecated.", severity = Severity.HIGH, cvssScore = 7.0f, networkBssid = network.bssid, networkSsid = network.ssid, recommendation = "Upgrade to WPA2-CCMP or WPA3-SAE."))
            else -> {}
        }

        // VulnDB matches
        vulnMatches.forEach { vuln ->
            findings.add(Finding(title = vuln.title, description = "Matched CVE: ${vuln.cveId}", severity = cvssCalculator.scoreToCvss(vuln.cvssScore), cvssScore = vuln.cvssScore, networkBssid = network.bssid, networkSsid = network.ssid, recommendation = vuln.recommendation))
        }

        if (network.wps) {
            findings.add(Finding(title = "WPS Enabled", description = "WPS on '${network.ssid}' is vulnerable to brute-force.", severity = Severity.HIGH, cvssScore = 7.0f, networkBssid = network.bssid, networkSsid = network.ssid, recommendation = "Disable WPS."))
        }

        return findings.sortedByDescending { it.cvssScore }
    }

    fun crackedPasswordFinding(bssid: String, ssid: String, password: String): Finding {
        return Finding(title = "WiFi Password Cracked", description = "Password for '$ssid' was successfully recovered: password strength is insufficient.", severity = Severity.CRITICAL, cvssScore = 10.0f, evidence = "Password recovered via dictionary/brute-force attack.", networkBssid = bssid, networkSsid = ssid, recommendation = "Use a strong, unique password of at least 16 characters.")
    }
}
