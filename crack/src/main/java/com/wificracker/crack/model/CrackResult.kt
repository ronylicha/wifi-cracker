package com.wificracker.crack.model

data class CrackResult(
    val jobId: String,
    val success: Boolean,
    val password: String = "",
    val duration: Long = 0,
    val keysTested: Long = 0,
    val keysPerSecond: Long = 0,
)
