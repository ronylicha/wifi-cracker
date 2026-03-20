package com.wificracker.scan.model

data class VulnMatch(
    val cveId: String,
    val title: String,
    val severity: String,
    val cvssScore: Float,
    val recommendation: String,
)
