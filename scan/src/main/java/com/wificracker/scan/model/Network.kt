package com.wificracker.scan.model

data class Network(
    val bssid: String,
    val ssid: String,
    val channel: Int,
    val frequency: Int = 0,
    val signalStrength: Int,
    val encryption: EncryptionType,
    val cipher: String = "",
    val authentication: String = "",
    val wps: Boolean = false,
    val clients: List<Client> = emptyList(),
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
)

enum class EncryptionType(val label: String, val riskLevel: RiskLevel) {
    OPEN("Open", RiskLevel.CRITICAL),
    WEP("WEP", RiskLevel.CRITICAL),
    WPA("WPA", RiskLevel.HIGH),
    WPA2("WPA2", RiskLevel.MEDIUM),
    WPA3("WPA3", RiskLevel.LOW),
    UNKNOWN("Unknown", RiskLevel.HIGH),
}

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
