package com.wificracker.report.model

data class Finding(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val severity: Severity,
    val cvssScore: Float,
    val cvssVector: String = "",
    val impact: String = "",
    val evidence: String = "",
    val recommendation: String = "",
    val networkBssid: String = "",
    val networkSsid: String = "",
)

enum class Severity(val label: String, val color: Long) {
    CRITICAL("Critical", 0xFFFF4444),
    HIGH("High", 0xFFFF8C00),
    MEDIUM("Medium", 0xFFFFD700),
    LOW("Low", 0xFF00C853),
    INFO("Info", 0xFF58A6FF),
}
