package com.wificracker.core.di

import android.content.Context
import com.wificracker.core.logging.AuditLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides @Singleton
    fun provideAuditLogger(@ApplicationContext context: Context): AuditLogger =
        AuditLogger(context.filesDir.resolve("audit_logs").absolutePath)
}
