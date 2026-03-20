package com.wificracker.attack.domain

import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.RootChecker
import com.wificracker.core.wifi.MonitorModeManager
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
) {
    fun check(attackType: AttackType, interfaceName: String): PrerequisiteResult {
        val missing = mutableListOf<String>()

        if (!rootChecker.isRooted()) missing.add("Root access required")
        if (!monitorModeManager.isMonitorMode(interfaceName)) missing.add("Monitor mode not enabled on $interfaceName")

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

        return PrerequisiteResult(satisfied = missing.isEmpty(), missingPrerequisites = missing)
    }
}
