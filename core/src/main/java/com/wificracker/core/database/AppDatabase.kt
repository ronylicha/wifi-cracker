package com.wificracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wificracker.core.database.dao.VulnDao
import com.wificracker.core.database.entity.VulnEntity

@Database(entities = [VulnEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun vulnDao(): VulnDao }
