package com.wificracker.report.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_profiles")
data class ClientProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyName: String = "",
    val address: String = "",
    val contactName: String = "",
    val contactTitle: String = "",
    val contactEmail: String = "",
    val contractReference: String = "",
    val logoPath: String = "",
)
