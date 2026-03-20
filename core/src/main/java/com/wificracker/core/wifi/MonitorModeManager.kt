package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorModeManager @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
) {
    fun enableMonitorMode(interfaceName: String): ShellResult {
        return chipsetMonitorHelper.enableMonitorMode(interfaceName)
    }

    fun disableMonitorMode(interfaceName: String): ShellResult {
        return chipsetMonitorHelper.disableMonitorMode(interfaceName)
    }

    fun isMonitorMode(interfaceName: String): Boolean {
        val result = shellExecutor.executeAsRoot("iw dev $interfaceName info 2>/dev/null || cat /sys/class/net/$interfaceName/type 2>/dev/null")
        return result.isSuccess && (result.stdout.contains("type monitor") || result.stdout.trim() == "803")
    }

    fun getChipsetInfo(): ChipsetMonitorCapability {
        return chipsetMonitorHelper.detectChipVendor()
    }
}
