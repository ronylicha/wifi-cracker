package com.wificracker.attack.domain

import com.wificracker.attack.model.AttackType
import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.RootChecker
import com.wificracker.core.wifi.MonitorModeManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class PrerequisiteCheckTest {
    private val rootChecker = mockk<RootChecker>()
    private val binaryInstaller = mockk<BinaryInstaller>()
    private val monitorModeManager = mockk<MonitorModeManager>()
    private val check = PrerequisiteCheck(rootChecker, binaryInstaller, monitorModeManager)

    @Test fun `returns satisfied when all prerequisites met`() {
        every { rootChecker.isRooted() } returns true
        every { monitorModeManager.isMonitorMode("wlan0") } returns true
        every { binaryInstaller.isBinaryInstalled(any()) } returns true
        val result = check.check(AttackType.DEAUTH, "wlan0")
        assertTrue(result.satisfied)
        assertTrue(result.missingPrerequisites.isEmpty())
    }

    @Test fun `fails when not rooted`() {
        every { rootChecker.isRooted() } returns false
        every { monitorModeManager.isMonitorMode(any()) } returns true
        every { binaryInstaller.isBinaryInstalled(any()) } returns true
        val result = check.check(AttackType.DEAUTH, "wlan0")
        assertFalse(result.satisfied)
        assertTrue(result.missingPrerequisites.any { it.contains("Root") })
    }

    @Test fun `fails when monitor mode not enabled`() {
        every { rootChecker.isRooted() } returns true
        every { monitorModeManager.isMonitorMode("wlan0") } returns false
        every { binaryInstaller.isBinaryInstalled(any()) } returns true
        val result = check.check(AttackType.DEAUTH, "wlan0")
        assertFalse(result.satisfied)
    }

    @Test fun `evil twin requires hostapd and dnsmasq`() {
        every { rootChecker.isRooted() } returns true
        every { monitorModeManager.isMonitorMode(any()) } returns true
        every { binaryInstaller.isBinaryInstalled("hostapd") } returns false
        every { binaryInstaller.isBinaryInstalled("dnsmasq") } returns false
        val result = check.check(AttackType.EVIL_TWIN, "wlan0")
        assertFalse(result.satisfied)
        assertEquals(2, result.missingPrerequisites.size)
    }
}
