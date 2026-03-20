package com.wificracker.report.domain

import com.wificracker.report.model.*
import org.junit.Assert.*
import org.junit.Test

class ReportGeneratorTest {
    private val generator = ReportGenerator(CvssCalculator(), RiskRating(CvssCalculator()), AutoRecommender())

    @Test fun `generateReport creates complete report`() {
        val findings = listOf(
            Finding(title = "Open Network", description = "Unencrypted", severity = Severity.CRITICAL, cvssScore = 10f, recommendation = "Enable encryption"),
            Finding(title = "WPS Enabled", description = "WPS brute-force", severity = Severity.HIGH, cvssScore = 7f, recommendation = "Disable WPS"),
        )
        val report = generator.generateReport(MissionInfo(title = "Test"), CompanyProfile(name = "TestCorp"), findings)
        assertEquals(2, report.findings.size)
        assertEquals("F", report.overallScore)
        assertEquals(ReportStatus.COMPLETED, report.status)
        assertTrue(report.executiveSummary.contains("Critical: 1"))
        assertTrue(report.recommendations.isNotEmpty())
    }

    @Test fun `generateReport sorts findings by CVSS descending`() {
        val findings = listOf(
            Finding(title = "Low", description = "", severity = Severity.LOW, cvssScore = 2f),
            Finding(title = "Critical", description = "", severity = Severity.CRITICAL, cvssScore = 9.8f),
        )
        val report = generator.generateReport(MissionInfo(), CompanyProfile(), findings)
        assertEquals(9.8f, report.findings.first().cvssScore)
    }

    @Test fun `empty findings produces grade A`() {
        val report = generator.generateReport(MissionInfo(), CompanyProfile(), emptyList())
        assertEquals("A", report.overallScore)
    }
}
