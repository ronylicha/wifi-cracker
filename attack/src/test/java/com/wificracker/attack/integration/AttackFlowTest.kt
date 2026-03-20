package com.wificracker.attack.integration

import com.wificracker.attack.domain.PrerequisiteCheck
import com.wificracker.attack.domain.PrerequisiteResult
import com.wificracker.attack.model.AttackType
import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.RootChecker
import com.wificracker.core.wifi.MonitorModeManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class AttackFlowTest {

    private val rootChecker = mockk<RootChecker>()
    private val binaryInstaller = mockk<BinaryInstaller>()
    private val monitorModeManager = mockk<MonitorModeManager>()
    private val prereqCheck = PrerequisiteCheck(rootChecker, binaryInstaller, monitorModeManager)

    @Test
    fun `full prerequisite check passes when all conditions met`() {
        every { rootChecker.isRooted() } returns true
        every { monitorModeManager.isMonitorMode("wlan0") } returns true
        every { binaryInstaller.isBinaryInstalled(any()) } returns true

        AttackType.entries.forEach { type ->
            val result = prereqCheck.check(type, "wlan0")
            assertTrue("Prerequisites should be met for ${type.name}", result.satisfied)
        }
    }

    @Test
    fun `prerequisite check fails gracefully for each condition`() {
        // Not rooted
        every { rootChecker.isRooted() } returns false
        every { monitorModeManager.isMonitorMode(any()) } returns true
        every { binaryInstaller.isBinaryInstalled(any()) } returns true
        assertFalse(prereqCheck.check(AttackType.DEAUTH, "wlan0").satisfied)

        // No monitor mode
        every { rootChecker.isRooted() } returns true
        every { monitorModeManager.isMonitorMode(any()) } returns false
        assertFalse(prereqCheck.check(AttackType.DEAUTH, "wlan0").satisfied)

        // Missing binary
        every { monitorModeManager.isMonitorMode(any()) } returns true
        every { binaryInstaller.isBinaryInstalled("aireplay-ng") } returns false
        val result = prereqCheck.check(AttackType.DEAUTH, "wlan0")
        assertFalse(result.satisfied)
        assertTrue(result.missingPrerequisites.any { it.contains("aireplay-ng") })
    }

    @Test
    fun `evil twin requires both hostapd and dnsmasq`() {
        every { rootChecker.isRooted() } returns true
        every { monitorModeManager.isMonitorMode(any()) } returns true
        every { binaryInstaller.isBinaryInstalled(any()) } returns true
        every { binaryInstaller.isBinaryInstalled("hostapd") } returns false
        every { binaryInstaller.isBinaryInstalled("dnsmasq") } returns false

        val result = prereqCheck.check(AttackType.EVIL_TWIN, "wlan0")
        assertFalse(result.satisfied)
        assertEquals(2, result.missingPrerequisites.filter { it.contains("Binary") }.size)
    }
}
