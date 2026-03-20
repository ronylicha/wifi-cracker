package com.wificracker.scan.domain

import com.wificracker.scan.model.Client
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.RiskLevel
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkAnalysis(
    val signalQuality: SignalQuality,
    val riskLevel: RiskLevel,
    val riskFactors: List<String>,
    val channelCongestion: ChannelCongestion,
)

enum class SignalQuality(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak"),
    VERY_WEAK("Very Weak"),
}

enum class ChannelCongestion { LOW, MEDIUM, HIGH }

@Singleton
class NetworkAnalyzer @Inject constructor() {

    fun analyze(network: Network, allNetworks: List<Network> = emptyList()): NetworkAnalysis {
        val signalQuality = assessSignalQuality(network.signalStrength)
        val riskFactors = identifyRiskFactors(network)
        val riskLevel = assessRiskLevel(network, riskFactors)
        val channelCongestion = assessChannelCongestion(network.channel, allNetworks)

        return NetworkAnalysis(
            signalQuality = signalQuality,
            riskLevel = riskLevel,
            riskFactors = riskFactors,
            channelCongestion = channelCongestion,
        )
    }

    fun assessSignalQuality(dbm: Int): SignalQuality = when {
        dbm >= -50 -> SignalQuality.EXCELLENT
        dbm >= -60 -> SignalQuality.GOOD
        dbm >= -70 -> SignalQuality.FAIR
        dbm >= -80 -> SignalQuality.WEAK
        else -> SignalQuality.VERY_WEAK
    }

    fun identifyRiskFactors(network: Network): List<String> {
        val factors = mutableListOf<String>()

        when (network.encryption) {
            EncryptionType.OPEN -> factors.add("No encryption - all traffic visible")
            EncryptionType.WEP -> factors.add("WEP is broken - can be cracked in minutes")
            EncryptionType.WPA -> factors.add("WPA-TKIP is deprecated - vulnerable to attacks")
            else -> {}
        }

        if (network.wps) factors.add("WPS enabled - vulnerable to PIN brute-force")
        if (network.ssid.isBlank()) factors.add("Hidden SSID - may indicate security awareness but not effective protection")
        if (network.cipher.contains("TKIP", ignoreCase = true)) factors.add("TKIP cipher is deprecated")

        return factors
    }

    private fun assessRiskLevel(network: Network, riskFactors: List<String>): RiskLevel {
        if (network.encryption == EncryptionType.OPEN || network.encryption == EncryptionType.WEP) return RiskLevel.CRITICAL
        if (network.encryption == EncryptionType.WPA) return RiskLevel.HIGH
        if (riskFactors.size >= 2) return RiskLevel.HIGH
        if (riskFactors.isNotEmpty()) return RiskLevel.MEDIUM
        return RiskLevel.LOW
    }

    private fun assessChannelCongestion(channel: Int, allNetworks: List<Network>): ChannelCongestion {
        val sameChannel = allNetworks.count { it.channel == channel }
        return when {
            sameChannel >= 5 -> ChannelCongestion.HIGH
            sameChannel >= 3 -> ChannelCongestion.MEDIUM
            else -> ChannelCongestion.LOW
        }
    }
}
