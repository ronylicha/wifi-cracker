package com.wificracker.attack.domain

import com.wificracker.attack.domain.attacks.*
import com.wificracker.attack.model.*
import com.wificracker.core.logging.AuditLogger
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AttackOrchestratorTest {
    private val deauth = mockk<DeauthAttack>()
    private val handshake = mockk<HandshakeCapture>()
    private val pmkid = mockk<PmkidCapture>()
    private val evil = mockk<EvilTwinAttack>()
    private val probe = mockk<ProbeSniff>()
    private val prereq = mockk<PrerequisiteCheck>()
    private val logger = mockk<AuditLogger>(relaxed = true)
    private val orchestrator = AttackOrchestrator(deauth, handshake, pmkid, evil, probe, prereq, logger)

    @Test fun `getAttackImpl returns correct implementation`() {
        assertEquals(deauth, orchestrator.getAttackImpl(AttackType.DEAUTH))
        assertEquals(handshake, orchestrator.getAttackImpl(AttackType.HANDSHAKE_CAPTURE))
        assertEquals(pmkid, orchestrator.getAttackImpl(AttackType.PMKID_CAPTURE))
        assertEquals(evil, orchestrator.getAttackImpl(AttackType.EVIL_TWIN))
        assertEquals(probe, orchestrator.getAttackImpl(AttackType.PROBE_SNIFF))
    }

    @Test fun `launchAttack fails when prerequisites not met`() = runTest {
        every { prereq.check(any(), any()) } returns PrerequisiteResult(false, listOf("Root required"))
        val attack = Attack(type = AttackType.DEAUTH, targetBssid = "AA:BB:CC:DD:EE:FF", interfaceName = "wlan0")
        orchestrator.launchAttack(attack)
        assertEquals(AttackStatus.FAILED, orchestrator.attackState.value.status)
    }
}
