package com.wificracker.app

import android.app.Application
import com.wificracker.core.database.VulnDatabase
import com.wificracker.core.root.BinaryInstaller
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WifiCrackerApp : Application() {

    @Inject lateinit var vulnDatabase: VulnDatabase
    @Inject lateinit var binaryInstaller: BinaryInstaller

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            vulnDatabase.seedOrUpdate(this@WifiCrackerApp)
        }
        applicationScope.launch {
            binaryInstaller.installAllFromAssets(this@WifiCrackerApp)
        }
    }
}
