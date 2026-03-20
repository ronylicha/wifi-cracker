package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorModeManager @Inject constructor(private val shellExecutor: ShellExecutor) {
    fun enableMonitorMode(interfaceName: String): ShellResult = executeSteps(listOf("ip link set $interfaceName down", "iw dev $interfaceName set type monitor", "ip link set $interfaceName up"))
    fun disableMonitorMode(interfaceName: String): ShellResult = executeSteps(listOf("ip link set $interfaceName down", "iw dev $interfaceName set type managed", "ip link set $interfaceName up"))
    fun isMonitorMode(interfaceName: String): Boolean { val r = shellExecutor.executeAsRoot("iw dev $interfaceName info"); return r.isSuccess && r.stdout.contains("type monitor") }
    private fun executeSteps(steps: List<String>): ShellResult { for (s in steps) { val r = shellExecutor.executeAsRoot(s); if (!r.isSuccess) return r }; return ShellResult(0, "OK", "") }
}
