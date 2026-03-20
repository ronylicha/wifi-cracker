package com.wificracker.attack.model

data class Capture(
    val id: String = java.util.UUID.randomUUID().toString(),
    val attackId: String,
    val type: CaptureType,
    val filePath: String,
    val fileSize: Long = 0,
    val targetBssid: String,
    val targetSsid: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

enum class CaptureType { HANDSHAKE, PMKID, PCAP, PROBE_LOG }
