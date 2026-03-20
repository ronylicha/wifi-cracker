package com.wificracker.report.domain

import com.wificracker.report.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportGenerator @Inject constructor(
    private val cvssCalculator: CvssCalculator,
    private val riskRating: RiskRating,
    private val autoRecommender: AutoRecommender,
) {
    fun generateReport(
        missionInfo: MissionInfo,
        companyProfile: CompanyProfile,
        findings: List<Finding>,
        manualRecommendations: List<Recommendation> = emptyList(),
    ): Report {
        val autoRecs = autoRecommender.generateRecommendations(findings)
        val allRecs = (manualRecommendations + autoRecs).distinctBy { it.title }.sortedBy { it.priority }
        val summary = riskRating.computeSummary(findings)

        val executiveSummary = buildExecutiveSummary(missionInfo, findings, summary)

        return Report(
            missionInfo = missionInfo,
            companyProfile = companyProfile,
            findings = findings.sortedByDescending { it.cvssScore },
            recommendations = allRecs,
            overallScore = summary.overallGrade,
            executiveSummary = executiveSummary,
            status = ReportStatus.COMPLETED,
        )
    }

    private fun buildExecutiveSummary(missionInfo: MissionInfo, findings: List<Finding>, summary: RiskSummary): String {
        return buildString {
            appendLine("WiFi Security Assessment - Executive Summary")
            appendLine()
            appendLine("Assessment Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(missionInfo.date))}")
            appendLine("Scope: ${missionInfo.scope}")
            appendLine()
            appendLine("Overall Security Grade: ${summary.overallGrade}")
            appendLine("Average CVSS Score: ${"%.1f".format(summary.averageCvss)}/10.0")
            appendLine()
            appendLine("Findings Summary:")
            appendLine("  Critical: ${summary.criticalCount}")
            appendLine("  High: ${summary.highCount}")
            appendLine("  Medium: ${summary.mediumCount}")
            appendLine("  Low: ${summary.lowCount}")
            appendLine("  Info: ${summary.infoCount}")
            appendLine("  Total: ${findings.size}")
            appendLine()
            if (summary.criticalCount > 0) appendLine("IMMEDIATE ACTION REQUIRED: ${summary.criticalCount} critical vulnerabilities were identified that require urgent remediation.")
        }
    }
}
