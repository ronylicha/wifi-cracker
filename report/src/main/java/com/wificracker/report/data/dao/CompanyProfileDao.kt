package com.wificracker.report.data.dao

import androidx.room.*
import com.wificracker.report.data.entity.CompanyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyProfileDao {
    @Query("SELECT * FROM company_profiles LIMIT 1")
    fun getCompanyProfile(): Flow<CompanyProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: CompanyProfileEntity): Long

    @Update
    suspend fun update(profile: CompanyProfileEntity)
}
