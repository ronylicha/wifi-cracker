package com.wificracker.attack.model

data class Attack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AttackType,
    val targetBssid: String,
    val targetSsid: String = "",
    val interfaceName: String,
    val status: AttackStatus = AttackStatus.PENDING,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val output: String = "",
)

enum class AttackStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }
