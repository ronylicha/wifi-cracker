package com.wificracker.report.data.dao

import androidx.room.*
import com.wificracker.report.data.entity.ClientProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientProfileDao {
    @Query("SELECT * FROM client_profiles ORDER BY companyName ASC")
    fun getAllClients(): Flow<List<ClientProfileEntity>>

    @Query("SELECT * FROM client_profiles WHERE id = :id")
    suspend fun getById(id: Long): ClientProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ClientProfileEntity): Long

    @Update
    suspend fun update(profile: ClientProfileEntity)

    @Delete
    suspend fun delete(profile: ClientProfileEntity)
}
