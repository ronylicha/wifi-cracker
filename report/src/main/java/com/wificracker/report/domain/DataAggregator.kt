package com.wificracker.report.domain

import com.wificracker.core.service.SessionCollector
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

    fun attackRecordToFindings(record: SessionCollector.AttackRecord): List<Finding> {
        if (record.status != "COMPLETED") return emptyList()
        val duration = if (record.endTime > 0) (record.endTime - record.startTime) / 1000 else 0

        val finding = when (record.type) {
            "DEAUTH" -> Finding(
                title = "Deauthentication Attack Successful",
                description = "Network '${record.targetSsid}' is vulnerable to deauthentication attacks. Clients were disconnected successfully in ${duration}s.",
                severity = Severity.MEDIUM, cvssScore = 5.0f,
                evidence = "Deauthentication frames sent to ${record.targetBssid}. Attack duration: ${duration}s.",
                networkBssid = record.targetBssid, networkSsid = record.targetSsid,
                recommendation = "Enable 802.11w (Protected Management Frames) to mitigate deauthentication attacks.",
            )
            "HANDSHAKE_CAPTURE" -> Finding(
                title = "WPA Handshake Captured",
                description = "4-way handshake for '${record.targetSsid}' was captured. Password can be cracked offline.",
                severity = Severity.HIGH, cvssScore = 7.5f,
                evidence = "WPA 4-way handshake captured from ${record.targetBssid} in ${duration}s.",
                networkBssid = record.targetBssid, networkSsid = record.targetSsid,
                recommendation = "Use WPA3-SAE which is resistant to offline dictionary attacks. Use a strong, complex password.",
            )
            "PMKID_CAPTURE" -> Finding(
                title = "PMKID Hash Captured",
                description = "PMKID for '${record.targetSsid}' obtained without client interaction. Offline cracking possible.",
                severity = Severity.HIGH, cvssScore = 7.5f,
                evidence = "PMKID captured from ${record.targetBssid} in ${duration}s.",
                networkBssid = record.targetBssid, networkSsid = record.targetSsid,
                recommendation = "Migrate to WPA3-SAE. Use a password of 16+ characters to resist offline attacks.",
            )
            "EVIL_TWIN" -> Finding(
                title = "Evil Twin Attack Successful",
                description = "Rogue AP impersonating '${record.targetSsid}' attracted client connections. Credential interception possible.",
                severity = Severity.CRITICAL, cvssScore = 9.0f,
                evidence = "Evil twin AP created for ${record.targetBssid}. Clients connected to rogue AP.",
                networkBssid = record.targetBssid, networkSsid = record.targetSsid,
                recommendation = "Deploy 802.1X enterprise authentication. Educate users about rogue AP risks. Use WIDS/WIPS.",
            )
            "PROBE_SNIFF" -> Finding(
                title = "Probe Requests Captured",
                description = "Client probe requests near '${record.targetSsid}' reveal remembered network names.",
                severity = Severity.LOW, cvssScore = 3.0f,
                evidence = "Probe request capture performed for ${duration}s near ${record.targetBssid}.",
                networkBssid = record.targetBssid, networkSsid = record.targetSsid,
                recommendation = "Disable auto-connect on client devices. Clear saved network lists regularly.",
            )
            else -> return emptyList()
        }
        return listOf(finding)
    }
}
