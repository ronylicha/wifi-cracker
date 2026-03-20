package com.wificracker.attack.domain

import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.RootChecker
import com.wificracker.core.wifi.ChipsetMonitorHelper
import com.wificracker.core.wifi.MonitorModeManager
import com.wificracker.core.wifi.WifiChipVendor
import com.wificracker.attack.model.AttackType
import javax.inject.Inject
import javax.inject.Singleton

data class PrerequisiteResult(
    val satisfied: Boolean,
    val missingPrerequisites: List<String> = emptyList(),
)

@Singleton
class PrerequisiteCheck @Inject constructor(
    private val rootChecker: RootChecker,
    private val binaryInstaller: BinaryInstaller,
    private val monitorModeManager: MonitorModeManager,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
) {
    fun check(attackType: AttackType, interfaceName: String): PrerequisiteResult {
        val missing = mutableListOf<String>()

        if (!rootChecker.isRooted()) missing.add("Root access required")

        val chipInfo = chipsetMonitorHelper.detectChipVendor()
        val hasMtkPatch = chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled

        // MTK patched driver: no need for monitor mode or external binaries for deauth/handshake
        if (!hasMtkPatch) {
            if (!monitorModeManager.isMonitorMode(interfaceName)) {
                missing.add("Monitor mode not enabled on $interfaceName")
            }

            val requiredBinaries = when (attackType) {
                AttackType.DEAUTH -> listOf("aireplay-ng")
                AttackType.HANDSHAKE_CAPTURE -> listOf("airodump-ng")
                AttackType.PMKID_CAPTURE -> listOf("hcxdumptool")
                AttackType.EVIL_TWIN -> listOf("hostapd", "dnsmasq")
                AttackType.PROBE_SNIFF -> listOf("airodump-ng")
            }

            requiredBinaries.forEach { binary ->
                if (!binaryInstaller.isBinaryInstalled(binary)) missing.add("Binary not found: $binary")
            }
        } else {
            // MTK patched: just need wpa_driver and ics_enable
            when (attackType) {
                AttackType.DEAUTH, AttackType.HANDSHAKE_CAPTURE -> {
                    if (!binaryInstaller.isBinaryInstalled("wpa_driver") &&
                        !java.io.File("/data/local/tmp/wpa_driver").exists()) {
                        missing.add("wpa_driver binary not found in /data/local/tmp/")
                    }
                }
                AttackType.EVIL_TWIN -> {
                    listOf("hostapd", "dnsmasq").forEach { binary ->
                        if (!binaryInstaller.isBinaryInstalled(binary)) missing.add("Binary not found: $binary")
                    }
                }
                else -> {}
            }
        }

        return PrerequisiteResult(satisfied = missing.isEmpty(), missingPrerequisites = missing)
    }
}
