package com.wificracker.scan.model

data class ScanResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val interfaceName: String,
    val duration: Long = 0,
    val networks: List<Network> = emptyList(),
    val clients: List<Client> = emptyList(),
    val status: ScanStatus = ScanStatus.IDLE,
)

enum class ScanStatus { IDLE, SCANNING, PAUSED, COMPLETED, FAILED }
