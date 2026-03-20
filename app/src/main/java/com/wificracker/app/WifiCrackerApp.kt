package com.wificracker.app

import android.app.Application
import com.wificracker.core.database.VulnDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WifiCrackerApp : Application() {

    @Inject lateinit var vulnDatabase: VulnDatabase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            vulnDatabase.seedIfEmpty(this@WifiCrackerApp)
        }
    }
}
