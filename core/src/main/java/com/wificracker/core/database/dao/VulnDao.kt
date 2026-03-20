package com.wificracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wificracker.core.database.entity.VulnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VulnDao {
    @Query("SELECT * FROM vulnerabilities ORDER BY cvssScore DESC") fun getAll(): Flow<List<VulnEntity>>
    @Query("SELECT * FROM vulnerabilities WHERE protocol = :protocol ORDER BY cvssScore DESC") fun getByProtocol(protocol: String): Flow<List<VulnEntity>>
    @Query("SELECT * FROM vulnerabilities WHERE severity = :severity") fun getBySeverity(severity: String): Flow<List<VulnEntity>>
    @Query("SELECT * FROM vulnerabilities WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'") fun search(query: String): Flow<List<VulnEntity>>
    @Query("SELECT * FROM vulnerabilities WHERE cveId = :cveId") suspend fun getByCveId(cveId: String): VulnEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(vulns: List<VulnEntity>)
    @Query("SELECT COUNT(*) FROM vulnerabilities") suspend fun count(): Int
}
