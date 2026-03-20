package com.wificracker.report.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_profiles")
data class CompanyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val siret: String = "",
    val contactName: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val certifications: String = "",
    val legalMention: String = "",
    val logoPath: String = "",
)
