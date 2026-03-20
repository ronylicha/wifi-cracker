package com.wificracker.report.model

data class MissionInfo(
    val title: String = "",
    val date: Long = System.currentTimeMillis(),
    val scope: String = "",
    val methodology: String = "WiFi security assessment using WiFi Cracker tool suite",
    val clientProfile: ClientProfile = ClientProfile(),
)
