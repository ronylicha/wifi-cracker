package com.wificracker.report.model

data class CompanyProfile(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val siret: String = "",
    val contactName: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val certifications: List<String> = emptyList(),
    val legalMention: String = "",
    val logoPath: String = "",
)
