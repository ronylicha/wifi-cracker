package com.wificracker.crack.model

data class CrackJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val capturePath: String,
    val hashPath: String = "",
    val targetBssid: String = "",
    val targetSsid: String = "",
    val strategy: CrackStrategy,
    val wordlistPath: String = "",
    val secondWordlistPath: String = "",
    val charset: String = "",
    val minLength: Int = 8,
    val maxLength: Int = 12,
    val ruleset: String = "",
    val status: CrackStatus = CrackStatus.PENDING,
    val startTime: Long = 0,
)

enum class CrackStatus { PENDING, CONVERTING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
