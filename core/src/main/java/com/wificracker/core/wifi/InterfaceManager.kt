package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterfaceManager @Inject constructor(private val shellExecutor: ShellExecutor, private val chipsetDetector: ChipsetDetector) {
    fun listInterfaces(): List<WifiInterface> {
        val result = shellExecutor.executeAsRoot("iw dev")
        if (!result.isSuccess) return emptyList()
        val interfaces = mutableListOf<WifiInterface>()
        var currentName: String? = null; var currentMac = ""; var currentType = ""
        for (line in result.stdout.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Interface ") -> currentName = trimmed.removePrefix("Interface ").trim()
                trimmed.startsWith("addr ") -> currentMac = trimmed.removePrefix("addr ").trim()
                trimmed.startsWith("type ") -> { currentType = trimmed.removePrefix("type ").trim(); currentName?.let { name -> val info = chipsetDetector.detect(name); interfaces.add(WifiInterface(name, currentMac, info.chipset, info.driver, info.supportsMonitor, currentType == "monitor")) } }
            }
        }
        return interfaces
    }
}
