package com.wificracker.app.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("wificracker", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideOuiMap(@ApplicationContext context: Context): Map<String, String> {
        return try {
            context.assets.open("oui.tsv").bufferedReader().readLines()
                .filter { it.contains("\t") }
                .associate { line ->
                    val parts = line.split("\t", limit = 2)
                    parts[0].trim().uppercase() to parts[1].trim()
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
