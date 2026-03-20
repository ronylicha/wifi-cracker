package com.wificracker.report.domain

import com.wificracker.report.model.Severity
import org.junit.Assert.*
import org.junit.Test

class CvssCalculatorTest {
    private val calc = CvssCalculator()

    @Test fun `scoreToCvss returns CRITICAL for 9+`() { assertEquals(Severity.CRITICAL, calc.scoreToCvss(9.8f)) }
    @Test fun `scoreToCvss returns HIGH for 7-8_9`() { assertEquals(Severity.HIGH, calc.scoreToCvss(7.5f)) }
    @Test fun `scoreToCvss returns MEDIUM for 4-6_9`() { assertEquals(Severity.MEDIUM, calc.scoreToCvss(5.0f)) }
    @Test fun `scoreToCvss returns LOW for 0_1-3_9`() { assertEquals(Severity.LOW, calc.scoreToCvss(2.0f)) }
    @Test fun `scoreToGrade returns A for low risk`() { assertEquals("A", calc.scoreToGrade(0.5f)) }
    @Test fun `scoreToGrade returns F for high risk`() { assertEquals("F", calc.scoreToGrade(8.0f)) }
    @Test fun `calculateOverallScore returns max`() { assertEquals(9.8f, calc.calculateOverallScore(listOf(3.0f, 9.8f, 5.0f))) }
}
