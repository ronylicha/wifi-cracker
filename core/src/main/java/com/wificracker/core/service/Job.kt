package com.wificracker.core.service

import java.util.UUID

sealed interface Job {
    val id: String; val description: String
    data class ScanJob(override val id: String = UUID.randomUUID().toString(), override val description: String = "WiFi Scan", val interfaceName: String) : Job
    data class AttackJob(override val id: String = UUID.randomUUID().toString(), override val description: String, val attackType: String, val targetBssid: String, val interfaceName: String) : Job
    data class CrackJob(override val id: String = UUID.randomUUID().toString(), override val description: String = "Password Crack", val capturePath: String, val wordlistPath: String, val strategy: String) : Job
}
