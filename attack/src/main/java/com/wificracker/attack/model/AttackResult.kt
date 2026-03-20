package com.wificracker.attack.model

data class AttackResult(
    val attackId: String,
    val success: Boolean,
    val message: String = "",
    val captures: List<Capture> = emptyList(),
    val duration: Long = 0,
)
