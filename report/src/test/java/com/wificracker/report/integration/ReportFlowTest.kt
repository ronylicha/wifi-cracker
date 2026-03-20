package com.wificracker.report.integration

import com.wificracker.report.domain.*
import com.wificracker.report.model.*
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.VulnMatch
import org.junit.Assert.*
import org.junit.Test

class ReportFlowTest {

    private val cvssCalculator = CvssCalculator()
    private val riskRating = RiskRating(cvssCalculator)
    private val autoRecommender = AutoRecommender()
    private val reportGenerator = ReportGenerator(cvssCalculator, riskRating, autoRecommender)
    private val dataAggregator = DataAggregator(cvssCalculator)

    @Test
    fun `full report flow from network scan to PDF report`() {
        // Simulate scan results
        val network = Network(bssid = "AA:BB:CC:DD:EE:FF", ssid = "VulnerableNet", channel = 6, signalStrength = -45, encryption = EncryptionType.WEP, wps = true)
        val vulnMatches = listOf(VulnMatch("WEP-DEPRECATED", "WEP Deprecated", "CRITICAL", 10.0f, "Migrate to WPA3"))

        // Aggregate findings
        val findings = dataAggregator.networkToFindings(network, vulnMatches)
        assertTrue(findings.isNotEmpty())
        assertTrue(findings.any { it.title.contains("WEP") })
        assertTrue(findings.any { it.title.contains("WPS") })

        // Generate report
        val report = reportGenerator.generateReport(
            MissionInfo(title = "Integration Test", scope = "WiFi audit"),
            CompanyProfile(name = "TestCorp"),
            findings,
        )

        assertEquals(ReportStatus.COMPLETED, report.status)
        assertEquals("F", report.overallScore)
        assertTrue(report.findings.isNotEmpty())
        assertTrue(report.recommendations.isNotEmpty())
        assertTrue(report.executiveSummary.contains("Critical"))
        assertEquals(report.findings.first().cvssScore, report.findings.maxOf { it.cvssScore })
    }

    @Test
    fun `cracked password generates critical finding`() {
        val finding = dataAggregator.crackedPasswordFinding("AA:BB:CC:DD:EE:FF", "MyNet", "password123")
        assertEquals(Severity.CRITICAL, finding.severity)
        assertEquals(10.0f, finding.cvssScore)
        assertTrue(finding.evidence.isNotBlank())

        val report = reportGenerator.generateReport(MissionInfo(), CompanyProfile(), listOf(finding))
        assertEquals("F", report.overallScore)
        assertTrue(report.recommendations.any { it.title.contains("Password") || it.title.contains("password") })
    }

    @Test
    fun `secure network produces good grade`() {
        val network = Network(bssid = "AA:BB:CC:DD:EE:FF", ssid = "SecureNet", channel = 6, signalStrength = -45, encryption = EncryptionType.WPA3)
        val findings = dataAggregator.networkToFindings(network, emptyList())

        val report = reportGenerator.generateReport(MissionInfo(), CompanyProfile(), findings)
        // WPA3 with no vulns should get good grade
        assertTrue(report.overallScore in listOf("A", "B"))
    }

    @Test
    fun `risk rating counts severities correctly`() {
        val findings = listOf(
            Finding(title = "F1", description = "", severity = Severity.CRITICAL, cvssScore = 9.5f),
            Finding(title = "F2", description = "", severity = Severity.CRITICAL, cvssScore = 10.0f),
            Finding(title = "F3", description = "", severity = Severity.HIGH, cvssScore = 7.5f),
            Finding(title = "F4", description = "", severity = Severity.MEDIUM, cvssScore = 5.0f),
            Finding(title = "F5", description = "", severity = Severity.LOW, cvssScore = 2.0f),
        )
        val summary = riskRating.computeSummary(findings)
        assertEquals(2, summary.criticalCount)
        assertEquals(1, summary.highCount)
        assertEquals(1, summary.mediumCount)
        assertEquals(1, summary.lowCount)
        assertEquals("D", summary.overallGrade)
    }
}
