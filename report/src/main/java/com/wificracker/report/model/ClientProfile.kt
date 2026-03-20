package com.wificracker.report.model

data class ClientProfile(
    val id: Long = 0,
    val companyName: String = "",
    val address: String = "",
    val contactName: String = "",
    val contactTitle: String = "",
    val contactEmail: String = "",
    val contractReference: String = "",
    val logoPath: String = "",
)
