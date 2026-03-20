package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChipsetDetector @Inject constructor(private val shellExecutor: ShellExecutor) {
    private val monitorCapableDrivers = setOf("ath9k", "ath9k_htc", "ath10k", "ath11k", "rt2800usb", "rt73usb", "rtl8187", "carl9170", "b43", "brcmfmac", "mt76", "mt7601u", "mt7921e")
    fun detect(interfaceName: String): ChipsetInfo {
        val driverResult = shellExecutor.executeAsRoot("readlink /sys/class/net/$interfaceName/device/driver")
        val driver = driverResult.stdout.substringAfterLast("/").trim()
        val chipsetResult = shellExecutor.executeAsRoot("cat /sys/class/net/$interfaceName/device/uevent | grep DRIVER")
        val chipset = chipsetResult.stdout.substringAfter("DRIVER=", "Unknown").trim().ifBlank { driver }
        return ChipsetInfo(chipset = chipset, driver = driver, supportsMonitor = driver in monitorCapableDrivers)
    }
}
