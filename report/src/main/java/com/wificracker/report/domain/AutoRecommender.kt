package com.wificracker.report.domain

import com.wificracker.report.model.Finding
import com.wificracker.report.model.Recommendation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRecommender @Inject constructor() {

    private val recommendationMap = mapOf(
        "OPEN" to Recommendation(title = "Enable WiFi Encryption", description = "Configure WPA3-SAE or WPA2-CCMP encryption on all access points. Open networks expose all traffic to interception.", priority = 1),
        "WEP" to Recommendation(title = "Replace WEP with WPA3", description = "WEP encryption is fundamentally broken and can be cracked in minutes. Migrate immediately to WPA3-SAE or WPA2-CCMP.", priority = 1),
        "WPA-TKIP" to Recommendation(title = "Upgrade from WPA-TKIP", description = "WPA with TKIP cipher is deprecated and vulnerable. Upgrade to WPA2-CCMP or WPA3-SAE.", priority = 2),
        "WPS" to Recommendation(title = "Disable WPS", description = "WiFi Protected Setup is vulnerable to brute-force PIN attacks. Disable WPS on all access points.", priority = 2),
        "KRACK" to Recommendation(title = "Patch KRACK Vulnerability", description = "Update all WiFi clients and access points to firmware versions patched against CVE-2017-13077 (KRACK attack).", priority = 1),
        "WEAK_PASSWORD" to Recommendation(title = "Strengthen WiFi Password", description = "Use a strong, unique password of at least 16 characters with mixed case, numbers, and symbols. Avoid dictionary words.", priority = 1),
        "HIDDEN_SSID" to Recommendation(title = "Hidden SSID Not Effective", description = "Hidden SSIDs provide no real security benefit and can be easily discovered. Consider other security measures.", priority = 4),
        "DEFAULT_SSID" to Recommendation(title = "Change Default SSID", description = "Default SSIDs reveal router manufacturer information. Use a custom, non-identifying SSID.", priority = 3),
    )

    fun generateRecommendations(findings: List<Finding>): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        val addedTitles = mutableSetOf<String>()

        for (finding in findings) {
            val keys = inferRecommendationKeys(finding)
            for (key in keys) {
                recommendationMap[key]?.let { rec ->
                    if (rec.title !in addedTitles) {
                        recommendations.add(rec.copy(relatedFindings = listOf(finding.id)))
                        addedTitles.add(rec.title)
                    }
                }
            }
        }

        return recommendations.sortedBy { it.priority }
    }

    private fun inferRecommendationKeys(finding: Finding): List<String> {
        val keys = mutableListOf<String>()
        val text = "${finding.title} ${finding.description}".lowercase()
        if (text.contains("open") || text.contains("unencrypted")) keys.add("OPEN")
        if (text.contains("wep")) keys.add("WEP")
        if (text.contains("tkip")) keys.add("WPA-TKIP")
        if (text.contains("wps")) keys.add("WPS")
        if (text.contains("krack")) keys.add("KRACK")
        if (text.contains("password") && (text.contains("weak") || text.contains("found") || text.contains("cracked"))) keys.add("WEAK_PASSWORD")
        return keys
    }
}
