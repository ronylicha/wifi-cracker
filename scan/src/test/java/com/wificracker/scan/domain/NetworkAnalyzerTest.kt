package com.wificracker.scan.domain

import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAnalyzerTest {

    private val analyzer = NetworkAnalyzer()

    private fun makeNetwork(
        encryption: EncryptionType = EncryptionType.WPA2,
        signal: Int = -50,
        wps: Boolean = false,
        ssid: String = "Test",
        cipher: String = "CCMP",
    ) = Network(bssid = "AA:BB:CC:DD:EE:FF", ssid = ssid, channel = 6, signalStrength = signal, encryption = encryption, wps = wps, cipher = cipher)

    @Test
    fun `assessSignalQuality returns EXCELLENT for strong signal`() {
        assertEquals(SignalQuality.EXCELLENT, analyzer.assessSignalQuality(-45))
    }

    @Test
    fun `assessSignalQuality returns VERY_WEAK for poor signal`() {
        assertEquals(SignalQuality.VERY_WEAK, analyzer.assessSignalQuality(-90))
    }

    @Test
    fun `identifyRiskFactors flags OPEN network`() {
        val factors = analyzer.identifyRiskFactors(makeNetwork(encryption = EncryptionType.OPEN))
        assertTrue(factors.any { it.contains("No encryption") })
    }

    @Test
    fun `identifyRiskFactors flags WEP`() {
        val factors = analyzer.identifyRiskFactors(makeNetwork(encryption = EncryptionType.WEP))
        assertTrue(factors.any { it.contains("WEP") })
    }

    @Test
    fun `identifyRiskFactors flags WPS`() {
        val factors = analyzer.identifyRiskFactors(makeNetwork(wps = true))
        assertTrue(factors.any { it.contains("WPS") })
    }

    @Test
    fun `identifyRiskFactors flags TKIP cipher`() {
        val factors = analyzer.identifyRiskFactors(makeNetwork(cipher = "TKIP"))
        assertTrue(factors.any { it.contains("TKIP") })
    }

    @Test
    fun `analyze returns CRITICAL risk for OPEN network`() {
        val analysis = analyzer.analyze(makeNetwork(encryption = EncryptionType.OPEN))
        assertEquals(RiskLevel.CRITICAL, analysis.riskLevel)
    }

    @Test
    fun `analyze returns LOW risk for WPA3 network`() {
        val analysis = analyzer.analyze(makeNetwork(encryption = EncryptionType.WPA3))
        assertEquals(RiskLevel.LOW, analysis.riskLevel)
    }

    @Test
    fun `assessChannelCongestion detects HIGH congestion`() {
        val networks = (1..6).map { makeNetwork().copy(bssid = "00:00:00:00:00:0$it") }
        val analysis = analyzer.analyze(makeNetwork(), networks)
        assertEquals(ChannelCongestion.HIGH, analysis.channelCongestion)
    }
}
