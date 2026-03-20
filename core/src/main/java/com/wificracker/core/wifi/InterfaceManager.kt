package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterfaceManager @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val chipsetDetector: ChipsetDetector,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
    private val usbWifiDetector: UsbWifiDetector,
) {

    fun listInterfaces(): List<WifiInterface> {
        // Try iw dev first (available on devices with iw installed)
        val iwResult = shellExecutor.executeAsRoot("iw dev 2>/dev/null")
        if (iwResult.isSuccess && iwResult.stdout.contains("Interface")) {
            return parseIwDev(iwResult.stdout)
        }

        // Fallback: read /proc/net/wireless + /sys/class/net/ (works on all Android)
        return listInterfacesFallback()
    }

    private fun parseIwDev(output: String): List<WifiInterface> {
        val interfaces = mutableListOf<WifiInterface>()
        var currentName: String? = null
        var currentMac = ""
        var currentType = ""
        for (line in output.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Interface ") -> currentName = trimmed.removePrefix("Interface ").trim()
                trimmed.startsWith("addr ") -> currentMac = trimmed.removePrefix("addr ").trim()
                trimmed.startsWith("type ") -> {
                    currentType = trimmed.removePrefix("type ").trim()
                    currentName?.let { name ->
                        val info = chipsetDetector.detect(name)
                        val monitorSupport = info.supportsMonitor || chipsetMonitorHelper.detectChipVendor().supportsInternalMonitor
                        interfaces.add(WifiInterface(name, currentMac, info.chipset, info.driver, monitorSupport, currentType == "monitor"))
                    }
                }
            }
        }
        return interfaces
    }

    private fun listInterfacesFallback(): List<WifiInterface> {
        val interfaces = mutableListOf<WifiInterface>()

        // Read wireless interfaces from /proc/net/wireless
        val procResult = shellExecutor.executeAsRoot("cat /proc/net/wireless")
        val wirelessNames = mutableListOf<String>()
        if (procResult.isSuccess) {
            for (line in procResult.stdout.lines()) {
                val trimmed = line.trim()
                if (trimmed.contains(":") && !trimmed.startsWith("Inter") && !trimmed.startsWith("face")) {
                    val name = trimmed.substringBefore(":").trim()
                    if (name.isNotBlank()) wirelessNames.add(name)
                }
            }
        }

        // Also check /sys/class/net for wlan* interfaces
        val sysResult = shellExecutor.executeAsRoot("ls /sys/class/net/ | grep -E '^wlan|^p2p'")
        if (sysResult.isSuccess) {
            for (name in sysResult.stdout.lines().filter { it.isNotBlank() }) {
                if (name !in wirelessNames) wirelessNames.add(name)
            }
        }

        // Get chipset capability once
        val chipCapability = chipsetMonitorHelper.detectChipVendor()

        for (name in wirelessNames) {
            // Skip p2p interfaces
            if (name.startsWith("p2p")) continue

            // Get MAC address
            val macResult = shellExecutor.executeAsRoot("cat /sys/class/net/$name/address 2>/dev/null")
            val mac = if (macResult.isSuccess) macResult.stdout.trim() else ""

            // Get interface type (1=managed, 803=monitor)
            val typeResult = shellExecutor.executeAsRoot("cat /sys/class/net/$name/type 2>/dev/null")
            val typeNum = typeResult.stdout.trim().toIntOrNull() ?: 1
            val isMonitor = typeNum == 803

            interfaces.add(
                WifiInterface(
                    name = name,
                    macAddress = mac,
                    chipset = chipCapability.chipName,
                    driver = chipCapability.vendor.label,
                    supportsMonitor = chipCapability.supportsInternalMonitor,
                    isMonitorMode = isMonitor,
                )
            )
        }

        // Also detect USB WiFi adapters
        val usbAdapters = usbWifiDetector.detectUsbAdapters()
        for (usb in usbAdapters) {
            if (interfaces.none { it.name == usb.interfaceName }) {
                interfaces.add(WifiInterface(
                    name = usb.interfaceName,
                    macAddress = shellExecutor.executeAsRoot("cat /sys/class/net/${usb.interfaceName}/address 2>/dev/null").stdout.trim(),
                    chipset = usb.chipset,
                    driver = usb.driver,
                    supportsMonitor = usb.supportsMonitor,
                    isMonitorMode = false,
                ))
            }
        }

        return interfaces
    }
}
