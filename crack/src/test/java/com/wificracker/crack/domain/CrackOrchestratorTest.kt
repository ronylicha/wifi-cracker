package com.wificracker.crack.domain

import com.wificracker.core.logging.AuditLogger
import com.wificracker.crack.domain.strategies.*
import com.wificracker.crack.model.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CrackOrchestratorTest {
    private val dict = mockk<DictionaryAttack>()
    private val brute = mockk<BruteForceAttack>()
    private val rule = mockk<RuleBasedAttack>()
    private val combo = mockk<CombinatorAttack>()
    private val converter = mockk<HashConverter>()
    private val logger = mockk<AuditLogger>(relaxed = true)
    private val orchestrator = CrackOrchestrator(dict, brute, rule, combo, converter, logger)

    @Test fun `startCrack converts cap file if no hash path`() = runTest {
        every { converter.convertCapToHc22000(any()) } returns ConversionResult(true, "/tmp/hash.hc22000", "hc22000")
        every { dict.execute(any()) } returns flowOf(CrackProgress(status = CrackStatus.COMPLETED, currentKey = "password123"))
        val job = CrackJob(capturePath = "/tmp/capture.cap", strategy = CrackStrategy.DICTIONARY, wordlistPath = "/tmp/wordlist.txt")
        orchestrator.startCrack(job)
        verify { converter.convertCapToHc22000("/tmp/capture.cap") }
    }

    @Test fun `startCrack fails if conversion fails`() = runTest {
        every { converter.convertCapToHc22000(any()) } returns ConversionResult(false, error = "bad file")
        val job = CrackJob(capturePath = "/tmp/bad.cap", strategy = CrackStrategy.DICTIONARY, wordlistPath = "/tmp/wl.txt")
        orchestrator.startCrack(job)
        assertEquals(CrackStatus.FAILED, orchestrator.progress.value.status)
    }
}
