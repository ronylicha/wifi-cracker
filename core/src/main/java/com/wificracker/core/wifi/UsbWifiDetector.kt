package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

data class UsbWifiAdapter(
    val interfaceName: String,
    val vendorId: String,
    val productId: String,
    val chipset: String,
    val driver: String,
    val supportsMonitor: Boolean,
)

@Singleton
class UsbWifiDetector @Inject constructor(
    private val shellExecutor: ShellExecutor,
) {

    // Known USB WiFi chipsets that support monitor mode
    private val knownChipsets = mapOf(
        "0bda:8812" to Triple("Realtek RTL8812AU", "rtl8812au", true),
        "0bda:881a" to Triple("Realtek RTL8812AU", "rtl8812au", true),
        "0bda:8811" to Triple("Realtek RTL8811AU", "rtl8811au", true),
        "0bda:b812" to Triple("Realtek RTL8812BU", "rtl88x2bu", true),
        "0bda:c811" to Triple("Realtek RTL8811CU", "rtl8811cu", true),
        "148f:3070" to Triple("Ralink RT3070", "rt2800usb", true),
        "148f:5370" to Triple("Ralink RT5370", "rt2800usb", true),
        "148f:5572" to Triple("Ralink RT5572", "rt2800usb", true),
        "0cf3:9271" to Triple("Atheros AR9271", "ath9k_htc", true),
        "057c:8501" to Triple("Atheros AR9271", "ath9k_htc", true),
        "0e8d:7961" to Triple("MediaTek MT7921AU", "mt7921u", true),
        "0e8d:7922" to Triple("MediaTek MT7922", "mt7921u", true),
        "0e8d:7612" to Triple("MediaTek MT7612U", "mt76x2u", true),
        "0e8d:7610" to Triple("MediaTek MT7610U", "mt76x0u", true),
    )

    fun detectUsbAdapters(): List<UsbWifiAdapter> {
        val adapters = mutableListOf<UsbWifiAdapter>()

        // List USB devices
        val usbResult = shellExecutor.executeAsRoot("cat /sys/bus/usb/devices/*/idVendor 2>/dev/null && echo '---' && cat /sys/bus/usb/devices/*/idProduct 2>/dev/null")

        // More reliable: check for USB network interfaces
        val netResult = shellExecutor.executeAsRoot("ls /sys/class/net/")
        if (!netResult.isSuccess) return emptyList()

        for (iface in netResult.stdout.lines().filter { it.isNotBlank() }) {
            // Check if this interface is a USB device
            val usbCheck = shellExecutor.executeAsRoot("readlink -f /sys/class/net/$iface/device 2>/dev/null")
            if (!usbCheck.isSuccess || !usbCheck.stdout.contains("usb")) continue

            // Skip the internal wlan0
            if (iface == "wlan0") continue

            // Get vendor:product ID
            val vendorResult = shellExecutor.executeAsRoot("cat /sys/class/net/$iface/device/../idVendor 2>/dev/null")
            val productResult = shellExecutor.executeAsRoot("cat /sys/class/net/$iface/device/../idProduct 2>/dev/null")
            val vendorId = vendorResult.stdout.trim()
            val productId = productResult.stdout.trim()
            val usbId = "$vendorId:$productId"

            // Get driver
            val driverResult = shellExecutor.executeAsRoot("readlink /sys/class/net/$iface/device/driver 2>/dev/null")
            val driver = driverResult.stdout.substringAfterLast("/").trim()

            // Look up in known chipsets
            val known = knownChipsets[usbId]
            val chipset = known?.first ?: "Unknown USB WiFi ($usbId)"
            val knownDriver = known?.second ?: driver
            val supportsMonitor = known?.third ?: false

            adapters.add(UsbWifiAdapter(
                interfaceName = iface,
                vendorId = vendorId,
                productId = productId,
                chipset = chipset,
                driver = knownDriver,
                supportsMonitor = supportsMonitor,
            ))
        }

        return adapters
    }
}
