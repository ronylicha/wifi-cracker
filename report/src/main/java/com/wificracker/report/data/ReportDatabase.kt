package com.wificracker.report.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wificracker.report.data.dao.CompanyProfileDao
import com.wificracker.report.data.dao.ClientProfileDao
import com.wificracker.report.data.entity.CompanyProfileEntity
import com.wificracker.report.data.entity.ClientProfileEntity

@Database(entities = [CompanyProfileEntity::class, ClientProfileEntity::class], version = 1, exportSchema = false)
abstract class ReportDatabase : RoomDatabase() {
    abstract fun companyProfileDao(): CompanyProfileDao
    abstract fun clientProfileDao(): ClientProfileDao
}
