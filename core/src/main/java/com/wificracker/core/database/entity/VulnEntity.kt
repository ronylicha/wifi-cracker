package com.wificracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vulnerabilities")
data class VulnEntity(@PrimaryKey val cveId: String, val protocol: String, val title: String, val description: String, val severity: String, val cvssScore: Float, val recommendation: String, val affectedVersions: String)
