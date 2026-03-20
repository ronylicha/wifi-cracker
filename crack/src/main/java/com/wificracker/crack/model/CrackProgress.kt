package com.wificracker.crack.model

data class CrackProgress(
    val jobId: String = "",
    val status: CrackStatus = CrackStatus.PENDING,
    val keysPerSecond: Long = 0,
    val keysTested: Long = 0,
    val keysTotal: Long = 0,
    val progress: Float = 0f,
    val eta: String = "",
    val currentKey: String = "",
)
