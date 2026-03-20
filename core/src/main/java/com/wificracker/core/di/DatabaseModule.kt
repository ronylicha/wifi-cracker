package com.wificracker.core.di

import android.content.Context
import androidx.room.Room
import com.wificracker.core.database.AppDatabase
import com.wificracker.core.database.dao.VulnDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wificracker.db").build()

    @Provides
    fun provideVulnDao(database: AppDatabase): VulnDao = database.vulnDao()
}
