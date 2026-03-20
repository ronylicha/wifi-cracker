package com.wificracker.scan.model

data class Client(
    val macAddress: String,
    val bssid: String,
    val signalStrength: Int,
    val vendor: String = "Unknown",
    val probeRequests: List<String> = emptyList(),
    val packets: Int = 0,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
)
