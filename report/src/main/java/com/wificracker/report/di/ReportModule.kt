package com.wificracker.report.di

import android.content.Context
import androidx.room.Room
import com.wificracker.report.data.ReportDatabase
import com.wificracker.report.data.dao.CompanyProfileDao
import com.wificracker.report.data.dao.ClientProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReportModule {
    @Provides @Singleton
    fun provideReportDatabase(@ApplicationContext context: Context): ReportDatabase =
        Room.databaseBuilder(context, ReportDatabase::class.java, "wificracker_reports.db").build()

    @Provides fun provideCompanyProfileDao(db: ReportDatabase): CompanyProfileDao = db.companyProfileDao()
    @Provides fun provideClientProfileDao(db: ReportDatabase): ClientProfileDao = db.clientProfileDao()
}
