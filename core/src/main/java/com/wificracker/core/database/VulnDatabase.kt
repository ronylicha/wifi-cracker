package com.wificracker.core.database

import android.content.Context
import com.wificracker.core.database.dao.VulnDao
import com.wificracker.core.database.entity.VulnEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class VulnJson(val cveId: String, val protocol: String, val title: String, val description: String, val severity: String, val cvssScore: Float, val recommendation: String, val affectedVersions: String)

@Singleton
class VulnDatabase @Inject constructor(private val vulnDao: VulnDao) {
    suspend fun seedOrUpdate(context: Context) {
        val json = context.assets.open("vulns.json").bufferedReader().readText()
        val vulns = Json.decodeFromString<List<VulnJson>>(json)
        val dbCount = vulnDao.count()
        if (dbCount >= vulns.size) return
        vulnDao.insertAll(vulns.map { v -> VulnEntity(v.cveId, v.protocol, v.title, v.description, v.severity, v.cvssScore, v.recommendation, v.affectedVersions) })
    }
}
